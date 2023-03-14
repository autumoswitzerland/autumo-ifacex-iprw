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
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.backblaze.b2.client.contentSources.B2ContentTypes;
import com.backblaze.b2.client.contentSources.B2FileContentSource;
import com.backblaze.b2.client.exceptions.B2Exception;
import com.backblaze.b2.client.structures.B2Bucket;
import com.backblaze.b2.client.structures.B2FileVersion;
import com.backblaze.b2.client.structures.B2UploadFileRequest;

import ch.autumo.ifacex.IPC;
import ch.autumo.ifacex.IfaceXException;
import ch.autumo.ifacex.Processor;
import ch.autumo.ifacex.SourceEntity;
import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.writer.Writer;


/**
 * BackBlaze B2 Storage file out - writer prefix 'b2_file_out'.
 * 
 * Writes files into a container and creates the container first, if it
 * doesn't exist. Existing files are overwritten!
 * 
 * Since this writer doesn't deal with versions, set your bucket to
 * 'Keep only the last version of the file' in the life-cycle settings;
 * this cannot be set programmatically, change it in the B2 administration
 * web console.
 *  
 */
public class B2StorageWriter extends AbstractB2Storage implements Writer {

	private static final Logger LOG = LoggerFactory.getLogger(B2StorageWriter.class.getName());
	
	private static final long MAX_SIZE_SMALL_FILES = 5368709120L;
	private static final String BUCKET_TYPE = "allPrivate";
	
	private String bucket = null;
	private B2Bucket b2Bucket = null;
	
	
	@Override
	public void initialize(String writerName, IPC config, Processor processor) throws IfaceXException {
		
		bucket = config.getWriterConfig(writerName).getConfig("_bucket");

		super.initialize(writerName, config, processor);
		
		try {
			b2Bucket = client().getBucketOrNullByName(bucket);
		} catch (B2Exception e) {
			throw new IfaceXException("Couldn't access bucket '"+bucket+"'!", e);
		}
	    // Only create bucket if it doesn't exists
		if (b2Bucket == null) {
			try {
				b2Bucket = client().createBucket(bucket, BUCKET_TYPE);
			} catch (B2Exception e) {
				throw new IfaceXException("Couldn't create bucket '"+bucket+"'!", e);
			}
			LOG.info("B2 bucket '"+bucket+"' created!");
		}
	}
	
	@Override
	public void initializeEntity(String writerName, IPC config, SourceEntity entity) throws IfaceXException {
	}

	@Override
	public void writeHeader(String writerName, IPC config, SourceEntity entity) throws IfaceXException {
	}

	@Override
	public void writeBatchData(String writerName, IPC config, BatchData batch, SourceEntity entity)
			throws IfaceXException {
		
		while (batch.hasNext()) {
			
			final String vals[] = batch.next();
			final File curr = new File(vals[0]); // absolute file path when reader 'FilePathReader/file_in' is used!
			
			LOG.info("Processing (bucket: '" + bucket + "'): " + curr.getName());

			// PS: Very strange API, but easy
			
			final String bucketId = b2Bucket.getBucketId();
			
			final B2UploadFileRequest request = B2UploadFileRequest
				    .builder(bucketId, curr.getName(), B2ContentTypes.APPLICATION_OCTET, B2FileContentSource.build(curr))
				    .setCustomField("color", "green")
				    .build();
			
			boolean found = false;
			B2FileVersion version = null;
			try {
				version = client().getFileInfoByName(bucket, curr.getName());
				if (version != null)
					found = true;
			} catch (B2Exception e) {
				//LOG.info("No file '"+curr.getName()+"' in bucket '"+bucket+"' found!");
				found = false;
			}
			// No versioning used!
			if (found) {
				try {
					client().deleteFileVersion(version);
				} catch (B2Exception e) {
					throw new IfaceXException("Couldn't delete file version with file name '"+version.getFileName()+"' in bucket '"+bucket+"'!", e);
				}
			}
			
			try {
				if (curr.length() < MAX_SIZE_SMALL_FILES)
					client().uploadSmallFile(request);
				else
					client().uploadLargeFile(request, Executors.newSingleThreadExecutor());
			} catch (B2Exception e) {
				throw new IfaceXException("Couldn't upload file '"+curr.getName()+"'!", e);
			}
		}			
	}

}
