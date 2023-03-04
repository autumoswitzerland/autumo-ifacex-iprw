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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.autumo.ifacex.IPC;
import ch.autumo.ifacex.IfaceXException;
import ch.autumo.ifacex.Processor;
import ch.autumo.ifacex.SourceEntity;
import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.writer.Writer;


/**
 * Amazon AWS S3 file out - writer prefix 'aws_file_out'.
 * 
 * Writes files into a bucket and creates the bucket first, if it
 * doesn't exist.
 * 
 * Uses path-style access and puts the 'aws_file_out_key_prefix'
 * in front of the file name, separated by a '/' in any case.
 * URL end-point and region should be provided for faster access!
 *  
 */
public class AWSS3FileWriter extends AbstractAWSS3File implements Writer {

	private final static Logger LOG = LoggerFactory.getLogger(AWSS3FileReader.class.getName());

	private String bucketName = null;

	
	@Override
	public void initialize(String writerName, IPC config, Processor processor) throws IfaceXException {
		
		bucketName = config.getWriterConfig(writerName).getConfig("_bucket_name");

		super.initialize(writerName, config, processor);
		
	    // Only create bucket when writing!
		if (this instanceof Writer && !client().doesBucketExistV2(bucketName)) {
			client().createBucket(bucketName);
			LOG.info("AWS bucket '"+bucketName+"' created!");
		}
	}

	@Override
	public void writeHeader(String writerName, IPC config, SourceEntity entity) throws IfaceXException {
	}
	
	@Override
	public void initializeEntity(String writerName, IPC config, SourceEntity entity) throws IfaceXException {
	}

	@Override
	public void writeBatchData(String writerName, IPC config, BatchData batch, SourceEntity entity) throws IfaceXException {
	
		while (batch.hasNext()) {
			
			final String vals[] = batch.next();
			final File curr = new File(vals[0]); // absolute file path when reader 'FilePathReader/file_in' is used!
			
			LOG.info("Processing (bucket: '" + bucketName + "'): " + getKeyPrefix() + curr.getName());
			
			client().putObject(bucketName, getKeyPrefix() + curr.getName(), curr);
		}
	}
	
}
