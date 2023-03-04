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

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.Iterator;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Blob.BlobSourceOption;
import com.google.cloud.storage.Storage.BlobListOption;

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
 * @UNTESTED
 * 
 * Google Cloud Storage file in - reader prefix 'gcloud_file_in'.
 * 
 * Reads files from a google cloud storage buckets as entities and creates them 
 * in the directory specified by 'gcloud_file_in_temp_out_path'; you have to delete
 * them yourself if necessary and if they are not deleted by a file writer for example.
 */
public class GoogleStorageReader extends AbstractGoogleStorage implements Reader {

	private String tempOutputPath = null;
	
	private int batchSize = 0;
	private ExclusionFilter exFilter = null;
	
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
		
	}

	@Override
	public void read(String readerName, BatchProcessor batchProcessor, IPC config, SourceEntity entity,
			boolean hasMoreEntities) throws IfaceXException {

		// No matter what is defined, we use standard fields for files here
		entity.overwriteSourceFields(SourceEntity.FILES_SOURCE_FIELDS);
		
		final Page<Blob> page = storage().list(entity.getEntity(), BlobListOption.pageSize(batchSize));
		
		boolean hastNextPage = true;
		while (hastNextPage) {
			
			currBatch = new BatchData(config);
			
			final Iterable<Blob> iterable = page.iterateAll();
			for (Iterator<Blob> iterator = iterable.iterator(); iterator.hasNext();) {
				
				final Blob blob = iterator.next();
				//final BlobId blobId = blob.getBlobId();
				
				String filePath = null;
				try {
					filePath = tempOutputPath + entity.getEntity() + OSUtils.FILE_SEPARATOR + blob.getName();
					blob.downloadTo(new FileOutputStream(filePath), BlobSourceOption.userProject(super.getProjectId()));
				} catch (FileNotFoundException e) {
					throw new IfaceXException("Cannot store blob locally to '" + filePath + "'!", e);
				}
				
				final String values[] = new String[] {filePath};
				if (exFilter == null)
					currBatch.addRecordValues(values);
				else if (exFilter.addRecord(SourceEntity.FILES_SOURCE_FIELDS, values))
					currBatch.addRecordValues(values);			
			}

			// We have a next page for this bucket / entity?
			hastNextPage = page.hasNextPage();
			// Do we have more data?
			final boolean moreData = hasMoreEntities || hastNextPage;
			// We always have a full batch here
			batchProcessor.processBatchData(currBatch, entity, moreData);
		}
	}

}
