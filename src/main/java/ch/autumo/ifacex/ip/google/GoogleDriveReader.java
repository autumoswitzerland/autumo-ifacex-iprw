/**
 * Copyright 2023 autumo GmbH, Michael Gasche.
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
package ch.autumo.ifacex.ip.google;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import ch.autumo.commons.utils.OSUtils;
import ch.autumo.ifacex.ExclusionFilter;
import ch.autumo.ifacex.IPC;
import ch.autumo.ifacex.IfaceXException;
import ch.autumo.ifacex.Processor;
import ch.autumo.ifacex.SourceEntity;
import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.batch.BatchProcessor;
import ch.autumo.ifacex.reader.Reader;
import ch.autumo.ifacex.reader.ReaderException;


/**
 * Google Drive file in - reader prefix 'gdrive_file_in'.
 * 
 * Reads files from a google drive (with or without spaces specification)
 * and creates them in the directory specified by 'gdrive_file_in_temp_out_path';
 * you have to delete them yourself if necessary and if they are not deleted by
 * a file writer for example.
 */
public class GoogleDriveReader extends AbstractGoogleDrive implements Reader {
	
	private final static Logger LOG = LoggerFactory.getLogger(GoogleDriveReader.class.getName());
	
	/** Google drive fields that are requested. */
	private static final String DRIVE_FIELDS = "nextPageToken,files(id,name,capabilities)";
	
	private String tempOutputPath = null;
	
	private String corpora = null;
	private String driveId = null;
	
	private int batchSize = 0;
	private ExclusionFilter exFilter = null;
	
	private BatchData currBatch = null;

	@Override
	public void initialize(String readerName, IPC config, Processor processor) throws IfaceXException {

		tempOutputPath = config.getReaderConfig().getConfig("_temp_out_path");
		if (!tempOutputPath.endsWith(OSUtils.FILE_SEPARATOR)) 
			tempOutputPath += OSUtils.FILE_SEPARATOR;

		corpora = config.getReaderConfig().getConfig("_corpora");
		driveId = config.getReaderConfig().getConfig("_drive_id");
		
		super.initialize(readerName, config, processor);
		
		batchSize = config.getReaderConfig().getFetchSize(SourceEntity.WILDCARD_SOURCE_ENTITY);
		exFilter = config.getReaderConfig().getExclusionFilter(SourceEntity.WILDCARD_SOURCE_ENTITY);
	}

	@Override
	public void initializeEntity(String readerName, IPC config, SourceEntity entity)
			throws ReaderException, IfaceXException {
	}

	@Override
	public void read(String readerName, BatchProcessor batchProcessor, IPC config, SourceEntity entity,
			boolean hasMoreEntities) throws IfaceXException {
		
		// No matter what is defined, we use standard fields for files here
		entity.overwriteSourceFields(SourceEntity.FILES_SOURCE_FIELDS);
		
		String nextToken = null;
		LOOP: while (true) {
			
			currBatch = new BatchData(config);
			com.google.api.services.drive.Drive.Files.List workingSet = null;
			
			FileList result = null;
			boolean nullEntity = false;
			
		    try {
		    	
		    	if (nextToken != null)
		    		workingSet = service().files().list().setPageToken(nextToken);
		    	else
		    		workingSet = service().files().list();

		    	// Corpora and drive ID if any.
		    	if (corpora != null && corpora.length() > 0) {
		    		workingSet = workingSet.setCorpora(corpora);
		    		if (driveId != null && driveId.length() > 0)
			    		workingSet = workingSet.setDriveId(driveId);
		    	}
		    	
		    	// source entities are Google drive spaces here !
		    	nullEntity = entity.getEntity().toUpperCase().equals(SourceEntity.NULL_ENTITY_NAME);
		    	if (!nullEntity)
		    		workingSet = workingSet.setSpaces(entity.getEntity());
		    	
		    	workingSet = workingSet.setPageSize(batchSize).setFields(DRIVE_FIELDS);

		    	// Get result file set
		    	result = workingSet.execute();
		    	
		    } catch (Exception e) {
		    	throw new IfaceXException("Cannot read files from google drive!", e);
		    }		    	
		    	
		    final List<File> files = result.getFiles();
			if (files == null || files.isEmpty()) {
				
				LOG.error("No files found! Stop reading from Google drive!");
				batchProcessor.noDataIsComing();
				break LOOP;
				
			} else {
				
				for (File file : files) {
					
					if (nullEntity)
						LOG.info("Processing (space: 'default'): " + file.getName());
					else
						LOG.info("Processing (space: '" + entity.getEntity() + "'): " + file.getName());
					
					//final String kind = file.getKind(); 'kind'
					
					boolean isFolder = false;
					final Object caps = file.get("capabilities");
					if (caps != null) {
						final JSONObject jso = new JSONObject(caps.toString());
						Object v = jso.get("canAddChildren");
						if (v != null)
							isFolder = Boolean.valueOf(v.toString()).booleanValue();
					}
					
					final String filePath = tempOutputPath + file.getName();
					final java.io.File f = new java.io.File(filePath);
					
					if (!isFolder) {
						
						FileOutputStream fos = null;
						try {
							
							fos = new FileOutputStream(f);
							service().files().get(file.getId()).executeMediaAndDownloadTo(fos);
							
						} catch (Exception e) {
					    	throw new IfaceXException("Cannot store file '"+filePath+"' from google drive!", e);
						} finally {
							try {
								if (fos != null)
									fos.close();
							} catch (IOException e) {
							}
						}
						
						final String values[] = new String[] {filePath};
						if (exFilter == null)
							currBatch.addRecordValues(values);
						else if (exFilter.addRecord(SourceEntity.FILES_SOURCE_FIELDS, values))
							currBatch.addRecordValues(values);
					
					} else {
						
						// Nope, we don't take folders; this makes the
						// batch smaller than the batch size
						//f.mkdirs();
					}
				}
			}	
			
			// Another token for another batch?
			nextToken = result.getNextPageToken();
				
			// Do we have more data?
			final boolean moreData = hasMoreEntities || nextToken != null;
			// We always have a full batch here
			batchProcessor.processBatchData(currBatch, entity, moreData);
			
			// No more tokens -> no more batches, no more entities
			if (!moreData)
				break LOOP;
		}
	}

}
