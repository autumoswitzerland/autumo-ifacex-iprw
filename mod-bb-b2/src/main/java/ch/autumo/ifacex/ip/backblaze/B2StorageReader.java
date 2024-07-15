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
package ch.autumo.ifacex.ip.backblaze;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.backblaze.b2.client.B2ListFilesIterable;
import com.backblaze.b2.client.contentHandlers.B2ContentFileWriter;
import com.backblaze.b2.client.exceptions.B2Exception;
import com.backblaze.b2.client.structures.B2Bucket;
import com.backblaze.b2.client.structures.B2DownloadByNameRequest;
import com.backblaze.b2.client.structures.B2FileVersion;
import com.backblaze.b2.client.structures.B2ListFileVersionsRequest;

import ch.autumo.commons.utils.system.OSUtils;
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
 * BackBlaze B2 Storage file in - writer prefix 'b2_file_in'.
 * 
 * Reads files from a B2 buckets as entities and creates them in the directory 
 * specified by 'b2_file_in_temp_out_path'; you have to delete them yourself 
 * if necessary and if they are not deleted by a file writer for example.
 * 
 * Since this writer doesn't deal with versions, set your bucket to
 * 'Keep only the last version of the file' in the life-cycle settings;
 * this cannot be set programmatically, change it in the B2 administration
 * web console.
 * 
 */
public class B2StorageReader extends AbstractB2Storage implements Reader {

	private final static Logger LOG = LoggerFactory.getLogger(B2StorageReader.class.getName());
	
	private String tempOutputPath = null;
	
	private ExclusionFilter exFilter = null;

	private BatchData currBatch = null;
	private B2Bucket currBucket = null;
	
	
	@Override
	public void initialize(String readerName, IPC config, Processor processor) throws IfaceXException {

		tempOutputPath = config.getReaderConfig().getConfig("_temp_out_path");
		if (!tempOutputPath.endsWith(OSUtils.FILE_SEPARATOR)) 
			tempOutputPath += OSUtils.FILE_SEPARATOR;

		super.initialize(readerName, config, processor);
		
		//batchSize = config.getReaderConfig().getFetchSize(SourceEntity.WILDCARD_SOURCE_ENTITY);
		exFilter = config.getReaderConfig().getExclusionFilter(SourceEntity.WILDCARD_SOURCE_ENTITY);
	}
	
	@Override
	public void initializeEntity(String readerName, IPC config, SourceEntity entity)
			throws ReaderException, IfaceXException {

		try {
			currBucket = client().getBucketOrNullByName(entity.getEntity());
		} catch (B2Exception e) {
			throw new IfaceXException("Couldn't read bucket '"+entity.getEntity()+"'!", e);
		}

		if (currBucket == null)
			throw new IfaceXException("Bucket '"+entity.getEntity()+"' not found!");
	}

	@Override
	public void read(String readerName, BatchProcessor batchProcessor, IPC config, SourceEntity entity,
			boolean hasMoreEntities) throws IfaceXException {

		// No matter what is defined, we use standard fields for files here
		entity.overwriteSourceFields(SourceEntity.FILES_SOURCE_FIELDS);

		// One page = one batch
		currBatch = new BatchData(config);

		final String bucketName = currBucket.getBucketName();
		
		final B2ListFileVersionsRequest request  = B2ListFileVersionsRequest
			    .builder(currBucket.getBucketId())
			    .build();
		
		B2ListFilesIterable iterable = null;
		try {
			iterable = client().fileVersions(request);
		} catch (B2Exception e) {
			throw new IfaceXException("Couldn't get files from bucket '"+bucketName+"'!", e);
		}
		
		for (B2FileVersion fileVersion : iterable) {
			
			if (fileVersion.isFolder())
				continue;
			
			LOG.info("Processing (bucket: '" + entity.getEntity() + "'): " + fileVersion.getFileName());
			
			final String filePath = tempOutputPath + fileVersion.getFileName();
			
			final B2DownloadByNameRequest downRequest = B2DownloadByNameRequest
					.builder(bucketName, fileVersion.getFileName())
					.build();
			
			final B2ContentFileWriter writer = B2ContentFileWriter.builder(new File(filePath))
					.build(); 
			
			try {
				client().downloadByName(downRequest, writer);
			} catch (B2Exception e) {
				// TODO Auto-generated catch block
				throw new IfaceXException("Couldn't download file '"+fileVersion.getFileName()+"' from bucket '"+bucketName+"'!", e);
			}
			
			final String values[] = new String[] {filePath};
			if (exFilter == null)
				currBatch.addRecordValues(values);
			else if (exFilter.addRecord(SourceEntity.FILES_SOURCE_FIELDS, values))
				currBatch.addRecordValues(values);	
		}
		
		// The batch is the container for an entity
		batchProcessor.processBatchData(currBatch, entity, hasMoreEntities);
	}

}
