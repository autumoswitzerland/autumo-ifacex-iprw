/**
 * Copyright 2026 autumo GmbH, Michael Gasche.
 * All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * Bug fixes, suggestions and comments should be sent to:
 * code@autumo.ch
 *
 */
package ch.autumo.ifacex.ip.nifi;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.nifi.remote.Transaction;
import org.apache.nifi.remote.TransferDirection;
import org.apache.nifi.remote.protocol.DataPacket;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.autumo.commons.utils.UtilsException;
import ch.autumo.commons.utils.system.OSUtils;
import ch.autumo.commons.utils.web.RESTUtils;
import ch.autumo.ifacex.ExclusionFilter;
import ch.autumo.ifacex.IPC;
import ch.autumo.ifacex.IfaceXException;
import ch.autumo.ifacex.Processor;
import ch.autumo.ifacex.RWC;
import ch.autumo.ifacex.SourceEntity;
import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.batch.BatchProcessor;
import ch.autumo.ifacex.reader.Reader;
import ch.autumo.ifacex.reader.ReaderException;
import ch.autumo.ifacex.utils.JSONDataExtractor;

/**
 * NiFi Site-to-Site in - reader prefix 'nifi_in'.
 *
 * <p>Reads FlowFiles from a remote NiFi output port. The source entity name
 * determines the corresponding NiFi output port. Two content formats are
 * supported:</p>
 *
 * <ul>
 *   <li><code>json</code> (default) - the FlowFile content is a JSON object or
 *       a JSON array.</li>
 *   <li><code>file</code> - the FlowFile content is written to the configured
 *       temporary directory.</li>
 * </ul>
 */
public class NiFiReader extends AbstractNiFi implements Reader {

    private static final Logger LOG = LoggerFactory.getLogger(NiFiReader.class.getName());

    private static final String FORMAT_JSON = "json";
    private static final String FORMAT_FILE = "file";

    private String contentFormat = FORMAT_JSON;
    private String tempOutputPath = null;

    private String dataPath = null;
    private String subPath = null;
    private boolean allowAbsentFields = false;
    private String absentFieldPlaceholder = "";
    private boolean fieldPathAccess = false;
    private boolean includeAttributes = false;

    private int flowFileLimit = 0;
    private int batchSize = 1;

    private String fields[] = null;

    private ExclusionFilter exFilter = null;

    private long fileCounter = 0;

    @Override
    public void initialize(String readerName, IPC config, Processor processor)
            throws IfaceXException {

        super.initialize(readerName, config, processor);

        final RWC rwc = config.getReaderConfig();

        final String format = getValue(rwc, "_content_format");
        if (format != null)
            contentFormat = format.toLowerCase();

        if (!FORMAT_JSON.equals(contentFormat) && !FORMAT_FILE.equals(contentFormat))
            throw new IfaceXException(
                    "READER '" + readerName + "': Invalid 'content_format' value '"
                            + contentFormat + "'!");

        if (FORMAT_FILE.equals(contentFormat)) {
            tempOutputPath = getValue(rwc, "_temp_out_path");

            if (tempOutputPath == null)
                throw new IfaceXException(
                        "READER '" + readerName
                                + "': No 'temp_out_path' configured for content format 'file'!");

            if (!tempOutputPath.endsWith(OSUtils.FILE_SEPARATOR))
                tempOutputPath += OSUtils.FILE_SEPARATOR;

            final File folder = new File(tempOutputPath);

            if (!folder.exists() && !folder.mkdirs())
                throw new IfaceXException(
                        "READER '" + readerName
                                + "': Couldn't create temporary directory '"
                                + tempOutputPath + "'!");
        }

        includeAttributes = rwc.isYes("_include_attributes", false);

        flowFileLimit = (int) getLong(rwc, "_flowfile_limit", 0);
        if (flowFileLimit < 0)
            flowFileLimit = 0;
    }

    @Override
    public void initializeEntity(String readerName, IPC config, SourceEntity entity)
            throws ReaderException, IfaceXException {

        final RWC rwc = config.getReaderConfig();

        if (FORMAT_FILE.equals(contentFormat)) {
            entity.overwriteSourceFields(SourceEntity.FILES_SOURCE_FIELDS);
            fields = SourceEntity.FILES_SOURCE_FIELDS;
        } else {
            fields = rwc.getFields(entity);

            if (fields == null || fields.length == 0)
                fields = entity.getSourceFields();

            if (fields == null || fields.length == 0)
                throw new ReaderException(
                        "READER '" + readerName
                                + "': No source fields configured for entity '"
                                + entity.getEntity() + "'!");

            dataPath = rwc.getDataPath(entity);
            subPath = rwc.getSubPath(entity);
            allowAbsentFields = rwc.isAllowAbsentFields(entity);
            absentFieldPlaceholder = rwc.getAbsentFieldPlaceholder(entity);

            if (absentFieldPlaceholder == null)
                absentFieldPlaceholder = "";

            fieldPathAccess = rwc.isFieldPathAccess(entity);
        }

        batchSize = rwc.getFetchSize(entity);

        if (batchSize <= 0)
            batchSize = 1;

        exFilter = rwc.getExclusionFilter(entity);

        initializeEntityClient(readerName, rwc, entity.getEntity());
    }

    @Override
    public void read(String readerName, BatchProcessor batchProcessor, IPC config,
            SourceEntity entity, boolean hasMoreEntities) throws IfaceXException {

        BatchData currBatch = new BatchData(config);
        int currCount = 0;
        int flowFiles = 0;
        boolean limitReached = false;

        while (!limitReached) {

            Transaction transaction = null;

            try {
                transaction = client().createTransaction(TransferDirection.RECEIVE);
            } catch (Exception e) {
                throw new IfaceXException(
                        "READER '" + readerName
                                + "': Couldn't create NiFi receive transaction for entity '"
                                + entity.getEntity() + "'!", e);
            }

            if (transaction == null) {
                LOG.debug(
                        "READER '{}': No NiFi node available, stopping read for entity '{}'.",
                        readerName, entity.getEntity());
                break;
            }

            int packetsInTransaction = 0;

            try {
                DataPacket packet = null;

                while ((packet = transaction.receive()) != null) {

                    packetsInTransaction++;
                    flowFiles++;

                    final Map<String, String> attributes = packet.getAttributes();

                    final List<String[]> rows;

                    if (FORMAT_FILE.equals(contentFormat))
                        rows = storeFlowFile(readerName, packet, attributes);
                    else
                        rows = extractRows(readerName, config, packet, attributes);

                    for (Iterator<String[]> iterator = rows.iterator(); iterator.hasNext();) {
                        final String values[] = iterator.next();

                        if (currCount == batchSize) {
                            batchProcessor.processBatchData(currBatch, entity, true);
                            currBatch = new BatchData(config);
                            currCount = 0;
                        }

                        currBatch.addRecordValues(values);
                        currCount++;
                    }
                }

                transaction.confirm();
                transaction.complete();

            } catch (IfaceXException e) {
                transaction.error();
                throw e;

            } catch (Exception e) {
                transaction.error();

                throw new IfaceXException(
                        "READER '" + readerName
                                + "': NiFi Site-to-Site transaction failed for entity '"
                                + entity.getEntity() + "'!", e);
            }

            if (packetsInTransaction == 0)
                break;

            if (flowFileLimit > 0 && flowFiles >= flowFileLimit) {
                LOG.warn(
                        "READER '{}': Configured FlowFile limit of {} reached for entity '{}'; the current transaction has been completed.",
                        readerName, flowFileLimit, entity.getEntity());
                limitReached = true;
            }
        }

        if (currCount > 0) {
            batchProcessor.processBatchData(currBatch, entity, hasMoreEntities);
        } else if (!hasMoreEntities) {
            batchProcessor.noDataIsComing();
        }

        LOG.info(
                "READER '{}': Read {} FlowFile(s) for entity '{}'.",
                readerName, flowFiles, entity.getEntity());
    }

    /**
     * Write the content of a FlowFile to the configured temporary directory.
     *
     * @param readerName reader name
     * @param packet data packet
     * @param attributes FlowFile attributes
     * @return row with the absolute file path
     * @throws IfaceXException if the content cannot be stored
     */
    private List<String[]> storeFlowFile(String readerName, DataPacket packet,
            Map<String, String> attributes) throws IfaceXException {

        final String fileName = uniqueFileName(attributes);
        final File file = new File(tempOutputPath + fileName);

        try {
            Files.copy(packet.getData(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IfaceXException(
                    "READER '" + readerName
                            + "': Couldn't store FlowFile content to '"
                            + file.getAbsolutePath() + "'!", e);
        }

        final String values[] = new String[] { file.getAbsolutePath() };

        final List<String[]> rows = new ArrayList<String[]>(1);

        if (exFilter == null
                || exFilter.addRecord(SourceEntity.FILES_SOURCE_FIELDS, values))
            rows.add(values);

        return rows;
    }

    /**
     * Create a safe and unique file name for a FlowFile.
     *
     * @param attributes FlowFile attributes
     * @return file name
     */
    private String uniqueFileName(Map<String, String> attributes) {

        String name = null;

        if (attributes != null) {
            name = attributes.get("filename");

            if (name == null || name.trim().length() == 0)
                name = attributes.get("uuid");
        }

        if (name == null || name.trim().length() == 0)
            name = "nifi-" + System.currentTimeMillis() + "-" + (++fileCounter);

        name = new File(name).getName();
        name = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");

        if (name.trim().length() == 0)
            name = "nifi-" + System.currentTimeMillis() + "-" + (++fileCounter);

        final File file = new File(tempOutputPath + name);

        if (!file.exists())
            return name;

        String base = name;
        String extension = "";

        final int dot = name.lastIndexOf('.');

        if (dot > 0) {
            base = name.substring(0, dot);
            extension = name.substring(dot);
        }

        return base + "-" + UUID.randomUUID() + extension;
    }

    /**
     * Extract records from the JSON content of a FlowFile.
     *
     * @param readerName reader name
     * @param config IPC
     * @param packet data packet
     * @param attributes FlowFile attributes
     * @return rows
     * @throws IfaceXException if the content cannot be extracted
     */
    private List<String[]> extractRows(String readerName, IPC config,
            DataPacket packet, Map<String, String> attributes) throws IfaceXException {

        final byte[] content;

        try {
            content = packet.getData().readAllBytes();
        } catch (IOException e) {
            throw new IfaceXException(
                    "READER '" + readerName
                            + "': Couldn't read FlowFile content!", e);
        }

        final List<String[]> rows = new ArrayList<String[]>();

        if (content.length == 0) {
            LOG.warn(
                    "READER '{}': FlowFile with empty content received and skipped.",
                    readerName);
            return rows;
        }

        final JSONArray dataArr;

        try {
            final Object root = new JSONTokener(
                    new String(content, StandardCharsets.UTF_8)).nextValue();

            Object data = root;

            if (root instanceof JSONObject && dataPath != null
                    && dataPath.length() != 0
                    && !dataPath.equals(".")
                    && !dataPath.equals("FULLARRAY")
                    && !dataPath.endsWith(".FULLARRAY")) {

                data = RESTUtils.extractJsonObject((JSONObject) root, dataPath);
            }

            if (data instanceof JSONArray) {
                dataArr = (JSONArray) data;

            } else if (data instanceof JSONObject) {
                dataArr = new JSONArray();
                dataArr.put(data);

            } else {
                throw new ReaderException(
                        "READER '" + readerName
                                + "': FlowFile content at data path '"
                                + dataPath
                                + "' is neither a JSON object nor a JSON array!");
            }

            if (includeAttributes && attributes != null && attributes.size() > 0) {
                for (int i = 0; i < dataArr.length(); i++) {
                    final Object record = dataArr.get(i);

                    if (record instanceof JSONObject)
                        mergeAttributes((JSONObject) record, attributes);
                }
            }

            for (int i = 0; i < dataArr.length(); i++) {
                if (!(dataArr.get(i) instanceof JSONObject))
                    throw new ReaderException(
                            "READER '" + readerName
                                    + "': JSON records must be objects!");
            }

        } catch (UtilsException e) {
            throw new ReaderException(
                    "READER '" + readerName
                            + "': Couldn't access JSON data path '"
                            + dataPath + "'!", e);

        } catch (Exception e) {
            if (e instanceof IfaceXException)
                throw (IfaceXException) e;

            throw new ReaderException(
                    "READER '" + readerName
                            + "': Couldn't parse FlowFile JSON content!", e);
        }

        final BatchData tmpBatch = new BatchData(config);

        try {
            JSONDataExtractor.extract(
                    readerName,
                    dataArr,
                    tmpBatch,
                    fields,
                    subPath,
                    exFilter,
                    fieldPathAccess,
                    allowAbsentFields,
                    absentFieldPlaceholder);

        } catch (UtilsException e) {
            throw new ReaderException(
                    "READER '" + readerName
                            + "': Couldn't extract records from FlowFile JSON content!", e);
        }

        final Iterator<String[]> iterator = tmpBatch;

        while (iterator.hasNext())
            rows.add(iterator.next());

        return rows;
    }

    /**
     * Merge FlowFile attributes into a JSON record. Existing JSON values take
     * precedence.
     *
     * @param record record
     * @param attributes FlowFile attributes
     */
    private void mergeAttributes(JSONObject record, Map<String, String> attributes) {
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            if (!record.has(entry.getKey()))
                record.put(entry.getKey(), entry.getValue());
        }
    }
}