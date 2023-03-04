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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage.BlobWriteOption;
import com.google.cloud.storage.Storage.BucketGetOption;
import com.google.cloud.storage.Storage.BucketTargetOption;
import com.google.cloud.storage.StorageClass;

import ch.autumo.ifacex.IPC;
import ch.autumo.ifacex.IfaceXException;
import ch.autumo.ifacex.Processor;
import ch.autumo.ifacex.SourceEntity;
import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.writer.Writer;


/**
 * Google Storage file out - writer prefix 'gstorage_file_out'.
 * 
 * Writes files into a bucket and creates the bucket first, if it
 * doesn't exist.
 * 
 * Region should be provided for faster access!
 *  
 */
public class GoogleStorageWriter extends AbstractGoogleStorage implements Writer {
	
	private final static Logger LOG = LoggerFactory.getLogger(GoogleStorageWriter.class.getName());
	
	public static final String DEFAULT_REGION = "EU"; // Multi-region; others: US, ASIA
	
	private String storageClass = null;
	private String bucketName = null;
	private String region = null;

	private Bucket bucket = null; 
	
	@Override
	public void initialize(String writerName, IPC config, Processor processor) throws IfaceXException {

		super.initialize(writerName, config, processor);
		
		storageClass = config.getWriterConfig(writerName).getConfig("_storage_class");
		bucketName = config.getWriterConfig(writerName).getConfig("_bucket_name");
		region = config.getWriterConfig(writerName).getConfig("_region");
		
		if (region == null || region.trim().length() == 0)
			region = DEFAULT_REGION;
		
	    // Only create bucket if it doens't exist!
		bucket = storage().get(bucketName, BucketGetOption.userProject(super.getProjectId()));
		if (!bucket.exists()) {
			bucket = storage().create(
							BucketInfo.newBuilder(bucketName)
							// See here for possible values: http://g.co/cloud/storage/docs/storage-classes
							.setStorageClass(StorageClass.valueOfStrict(storageClass))
							// Possible values: http://g.co/cloud/storage/docs/bucket-locations
							.setLocation(region)
							.build(),
							BucketTargetOption.userProject(super.getProjectId())
						);
			LOG.info("Google Storage bucket '"+bucketName+"' created!");
		}
	}

	@Override
	public void writeHeader(String writerName, IPC config, SourceEntity entity) throws IfaceXException {
	}
	
	@Override
	public void initializeEntity(String writerName, IPC config, SourceEntity entity) throws IfaceXException {
	}

	@Override
	public void writeBatchData(String writerName, IPC config, BatchData batch, SourceEntity entity)
			throws IfaceXException {
		
		while (batch.hasNext()) {
			
			final String vals[] = batch.next();
			final File curr = new File(vals[0]); // absolute file path when reader 'FilePathReader/file_in' is used!
			
			LOG.info("Processing (bucket: '" + bucketName + "'): " + curr.getName());
			
			final Blob blob = bucket.get(curr.getName());
			try {
				if (blob != null) {
					// UPDATE: delete first, we use no versioning here atm.
					if (!storage().delete(blob.getBlobId())) {
						throw new IfaceXException("Couldn't update blob; delete failed for blob '"+curr.getName()+"/"+blob.getBlobId()+"'!");
					} else 
						LOG.info("Deleted blob: '"+curr.getName()+"/"+blob.getBlobId()+"'!");
				}
				
				// CREATE
				// Blob newBlob = 
				storage().createFrom(
						BlobInfo.newBuilder(bucketName, curr.getName()).build(),
						Path.of(vals[0]),
						BlobWriteOption.userProject(super.getProjectId())
					);
				
			} catch (IOException e) {
				throw new IfaceXException("Couldn't update/insert new blob '"+curr.getName()+"'!", e);
			}
		}		
	}

}
