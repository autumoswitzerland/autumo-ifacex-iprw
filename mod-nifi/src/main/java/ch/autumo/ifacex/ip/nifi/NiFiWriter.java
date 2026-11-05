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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.nifi.remote.Transaction;
import org.apache.nifi.remote.TransactionCompletion;
import org.apache.nifi.remote.TransferDirection;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.autumo.ifacex.IPC;
import ch.autumo.ifacex.IfaceXException;
import ch.autumo.ifacex.Processor;
import ch.autumo.ifacex.RWC;
import ch.autumo.ifacex.SourceEntity;
import ch.autumo.ifacex.WriterMapping;
import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.writer.Writer;
import ch.autumo.ifacex.writer.WriterException;


/**
 * NiFi Site-to-Site out - writer prefix 'nifi_out'.
 *
 * <p>Sends records to a remote NiFi input port as JSON FlowFiles. The
 * destination entity name determines the target NiFi port. Two output
 * modes are supported:</p>
 *
 * <ul>
 *   <li><code>batch</code> (default) - one FlowFile per ifaceX batch; the
 *       FlowFile content is a JSON array of records.</li>
 *   <li><code>record</code> - one FlowFile per record; the FlowFile content is
 *       a single JSON object.</li>
 * </ul>
 *
 * <p>In record mode, selected record values can additionally be sent as NiFi
 * FlowFile attributes. Configure them comma-separated with the
 * <code>attributes</code> parameter; the names must match destination field
 * names.</p>
 */
public class NiFiWriter extends AbstractNiFi implements Writer {

	private static final Logger LOG = LoggerFactory.getLogger(NiFiWriter.class.getName());

	private static final String MODE_BATCH = "batch";
	private static final String MODE_RECORD = "record";

	private static final String ATTR_MIME_TYPE = "mime.type";
	private static final String MIME_TYPE_JSON = "application/json";

	private String mode = MODE_BATCH;
	private String attributeFields[] = new String[0];

	private WriterMapping mapping = null;


	@Override
	public void initialize(String writerName, IPC config, Processor processor) throws IfaceXException {

		super.initialize(writerName, config, processor);

		final RWC rwc = config.getWriterConfig(writerName);

		final String configuredMode = getValue(rwc, "_mode");
		if (configuredMode != null)
			mode = configuredMode.toLowerCase();

		if (!MODE_BATCH.equals(mode) && !MODE_RECORD.equals(mode))
			throw new WriterException("WRITER '" + writerName + "': Invalid 'mode' value '" + mode + "'!");

		attributeFields = rwc.getSeparatedValues("_attributes");
	}

	@Override
	public void initializeEntity(String writerName, IPC config, SourceEntity entity) throws IfaceXException {

		mapping = config.getWriterConfig(writerName).getMapping(entity);

		final RWC rwc = config.getWriterConfig(writerName);
		initializeEntityClient(writerName, rwc, entity.getEntity());
	}

	@Override
	public void writeHeader(String writerName, IPC config, SourceEntity entity) throws IfaceXException {
		// no header
	}

	@Override
	public void writeBatchData(String writerName, IPC config, BatchData batch, SourceEntity entity) throws IfaceXException {

		final List<JSONObject> records = new ArrayList<JSONObject>();
		final List<Map<String, String>> valueMaps = new ArrayList<Map<String, String>>();

		while (batch.hasNext()) {
			final String values[] = batch.next();
			final Map<String, String> valueMap = createValueMap(values, entity);
			records.add(new JSONObject(valueMap));
			valueMaps.add(valueMap);
		}

		if (records.isEmpty())
			return;

		Transaction transaction = null;
		try {
			transaction = client().createTransaction(TransferDirection.SEND);
		} catch (Exception e) {
			throw new WriterException("WRITER '" + writerName + "': Couldn't create NiFi send transaction for entity '"
					+ entity.getEntity() + "'!", e);
		}

		// All nodes are penalized; there is currently no node available.
		if (transaction == null)
			throw new WriterException("WRITER '" + writerName + "': No NiFi node available for entity '" + entity.getEntity() + "'!");

		try {

			if (MODE_RECORD.equals(mode)) {

				for (int i = 0; i < records.size(); i++) {
					final Map<String, String> attributes = new HashMap<String, String>();
					attributes.put(ATTR_MIME_TYPE, MIME_TYPE_JSON);
					addAttributes(attributes, valueMaps.get(i));
					transaction.send(records.get(i).toString().getBytes(StandardCharsets.UTF_8), attributes);
				}

			} else {

				final JSONArray array = new JSONArray(records);
				final Map<String, String> attributes = new HashMap<String, String>();
				attributes.put(ATTR_MIME_TYPE, MIME_TYPE_JSON);
				transaction.send(array.toString().getBytes(StandardCharsets.UTF_8), attributes);
			}

			transaction.confirm();
			final TransactionCompletion completion = transaction.complete();

			if (completion.isBackoff())
				LOG.warn("WRITER '{}': NiFi requested a backoff after sending {} FlowFile(s) for entity '{}'.",
						writerName, completion.getDataPacketsTransferred(), entity.getEntity());

			if (LOG.isDebugEnabled())
				LOG.debug("WRITER '{}': Sent {} FlowFile(s) with {} byte(s) for entity '{}'.",
						writerName, completion.getDataPacketsTransferred(), completion.getBytesTransferred(), entity.getEntity());

		} catch (Exception e) {
			transaction.error();
			throw new WriterException("WRITER '" + writerName + "': NiFi Site-to-Site transaction failed for entity '"
					+ entity.getEntity() + "'!", e);
		}
	}

	/**
	 * Create the destination field/value map for one record.
	 *
	 * @param values source values
	 * @param entity source entity
	 * @return field/value map
	 * @throws IfaceXException if the mapping cannot be applied
	 */
	private Map<String, String> createValueMap(String values[], SourceEntity entity) throws IfaceXException {

		if (!mapping.hasFieldMappings())
			return mapping.getMapForUpdateAndInsertWithoutMapping(values, entity);

		final Map<String, String> valueMap = new LinkedHashMap<String, String>();
		final String destFields[] = mapping.getFieldsForInsert();
		final String destValues[] = mapping.getValuesForInsert(values);
		for (int i = 0; i < destFields.length; i++)
			valueMap.put(destFields[i], destValues[i]);
		return valueMap;
	}

	/**
	 * Add the configured record values as FlowFile attributes.
	 *
	 * @param attributes FlowFile attributes
	 * @param valueMap record values
	 */
	private void addAttributes(Map<String, String> attributes, Map<String, String> valueMap) {

		for (int i = 0; i < attributeFields.length; i++) {
			final String value = valueMap.get(attributeFields[i]);
			if (value != null)
				attributes.put(attributeFields[i], sanitizeAttributeValue(value));
		}
	}

	/**
	 * Sanitize an attribute value. Control characters would break the
	 * Site-to-Site attribute encoding.
	 *
	 * @param value value
	 * @return sanitized value
	 */
	private String sanitizeAttributeValue(String value) {
		return value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').replace('\0', ' ');
	}

}