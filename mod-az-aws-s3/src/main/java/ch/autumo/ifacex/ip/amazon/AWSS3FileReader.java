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
package ch.autumo.ifacex.ip.amazon;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;

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
 * Amazon AWS S3 file in - reader prefix 'aws_file_in'.
 * 
 * Reads files from a bucket and creates them in the directory
 * specified by 'aws_file_in_temp_out_path'; you have to delete
 * them yourself if necessary and if they are not deleted by
 * a file writer for example.
 */
public class AWSS3FileReader extends AbstractAWSS3File implements Reader {

	private final static Logger LOG = LoggerFactory.getLogger(AWSS3FileReader.class.getName());
	
	private String tempOutputPath = null;
	
	private ObjectListing objectListing = null;
	private int amount = 0;
	private int totalEntityFileCounter = 0;

	private ExclusionFilter exFilter = null;
	
	private int batchSize = 0;
	private BatchData currBatch = null;

	
	@Override
	public void initialize(String readerName, IPC config, Processor processor) throws IfaceXException {
		
		tempOutputPath = config.getReaderConfig().getConfig("_temp_out_path");
		if (!tempOutputPath.endsWith(OSUtils.FILE_SEPARATOR)) 
			tempOutputPath += OSUtils.FILE_SEPARATOR;
		
		super.initialize(readerName, config, processor);
		
		batchSize = config.getReaderConfig().getFetchSize(SourceEntity.WILDCARD_SOURCE_ENTITY);
		exFilter = config.getReaderConfig().getExclusionFilter(SourceEntity.WILDCARD_SOURCE_ENTITY);
	}

	@Override
	public void initializeEntity(String readerName, IPC config, SourceEntity entity)
			throws ReaderException, IfaceXException {

		// Source entity names are bucket names here!
		objectListing = client().listObjects(entity.getEntity());

		amount = objectListing.getObjectSummaries().size();
		totalEntityFileCounter = 0;
	}

	@Override
	public void read(String readerName, BatchProcessor batchProcessor, IPC config, SourceEntity entity,
			boolean hasMoreEntities) throws IfaceXException {
		
		// No matter what is defined, we use standard fields for files here
		entity.overwriteSourceFields(SourceEntity.FILES_SOURCE_FIELDS);
		
		int batchFileCounter = 0;
		
		for (S3ObjectSummary os : objectListing.getObjectSummaries()) {

			if (batchFileCounter == 0)
				currBatch = new BatchData(config);
			
			final String key = os.getKey();
			LOG.info("Processing (bucket: '" + entity.getEntity() + "'): " + key);
			
			/* Using key as directory: Only creates problems when deleting temporary directories */
			String fileName = null;
			if (key.indexOf("/") == -1) {
				fileName = key;
			} else {
				final String parts[] = key.split("/");
				fileName = parts[parts.length - 1];
			}
			
			final S3Object s3object = client().getObject(entity.getEntity(), key);
			final S3ObjectInputStream inputStream = s3object.getObjectContent();
			File file = null;
			try {
				
				file = new File(tempOutputPath + fileName);

				// NOPE: make directories from AWS key-prefix
				//file.mkdirs();
				
				final Path path = file.toPath();
				Files.copy(inputStream, path, StandardCopyOption.REPLACE_EXISTING);
				
			} catch (IOException e) {
				throw new IfaceXException("Couldn't create file '"+fileName+"'!", e);
			} finally {
				try {
					inputStream.close();
				} catch (IOException e) {
				}
			}
			
			final String values[] = new String [] {
					file.getAbsolutePath()
				};
			
			if (exFilter == null)
				currBatch.addRecordValues(values);
			else if (exFilter.addRecord(SourceEntity.FILES_SOURCE_FIELDS, values))
				currBatch.addRecordValues(values);
			
			batchFileCounter++;
			totalEntityFileCounter++;

			// More data?
			final boolean moreData = totalEntityFileCounter < amount || hasMoreEntities;
			if (batchFileCounter == batchSize || !moreData) {
				// process batch
				batchProcessor.processBatchData(currBatch, entity, moreData);
				batchFileCounter = 0;
			}
		}
	}

}
