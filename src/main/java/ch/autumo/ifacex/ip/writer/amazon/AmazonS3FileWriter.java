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
package ch.autumo.ifacex.ip.writer.amazon;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import ch.autumo.commons.utils.UtilsException;
import ch.autumo.ifacex.IPC;
import ch.autumo.ifacex.IfaceXException;
import ch.autumo.ifacex.Processor;
import ch.autumo.ifacex.SourceEntity;
import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.writer.Writer;


/**
 * Amazon AWS file out - writer prefix 'aws_file_out'.
 * 
 * Writes files into a bucket and creates the bucket first, if it
 * doesn't exist.
 * 
 * Uses path-style access and puts the 'aws_file_out_key_prefix'
 * in front of the file name, separated by a '/' in any case.
 * URL end-point and region should be provided for faster access!
 *  
 */
public class AmazonS3FileWriter implements Writer {

	private final static Logger LOG = LoggerFactory.getLogger(AmazonS3FileWriter.class.getName());
	
	private AmazonS3 s3client = null;
	private AWSCredentials credentials = null;
	private EndpointConfiguration endpoint = null;
	private String urlEndpoint = null;
	private String region = null;
	private String bucketName = null;
	private String keyPrefix = null;

	@Override
	public void initialize(String rwName, IPC config, Processor processor) throws IfaceXException {
		
		String accessKey = null;
		String secretKey = null;
		try {
			accessKey = config.getWriterConfig(rwName).getConfigDecodedIfNecessary("_access_key");
			secretKey = config.getWriterConfig(rwName).getConfigDecodedIfNecessary("_secret_key");
		} catch (UtilsException e) {
			throw new IfaceXException("Couldn't read credentials!", e);
		}
		credentials = new BasicAWSCredentials(accessKey, secretKey);
		
		keyPrefix = config.getWriterConfig(rwName).getConfig("_key_prefix");
		if (!keyPrefix.endsWith("/"))
			keyPrefix += "/";
		
		bucketName = config.getWriterConfig(rwName).getConfig("_bucket_name");
		
		urlEndpoint = config.getWriterConfig(rwName).getConfig("_url_endpoint");
		region = config.getWriterConfig(rwName).getConfig("_region");
		
		if (urlEndpoint != null && urlEndpoint.length() > 0) {
			if (region != null && region.length() > 0)
				endpoint = new EndpointConfiguration(urlEndpoint, region); // "http://localhost:8001", e.g., "eu-central-2"
			else
				endpoint = new EndpointConfiguration(urlEndpoint, Regions.getCurrentRegion().getName()); // "http://localhost:8001", "?"
		}
	    
	    if (endpoint != null)
			s3client = AmazonS3ClientBuilder
			  .standard()
		      .withPathStyleAccessEnabled(true)  
		      .withEndpointConfiguration(endpoint)
			  .withCredentials(new AWSStaticCredentialsProvider(credentials))
			  .build();	
	    else
			s3client = AmazonS3ClientBuilder
			  .standard()
		      .withPathStyleAccessEnabled(true)  
			  .withCredentials(new AWSStaticCredentialsProvider(credentials))
			  .withRegion(Regions.EU_CENTRAL_2)
			  .build();	

		if (!s3client.doesBucketExistV2(bucketName)) {
			s3client.createBucket(bucketName);
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
			s3client.putObject(bucketName, keyPrefix + curr.getName(), curr);
		}
	}

	@Override
	public void close(String rwName) throws IfaceXException {
		if (s3client != null) {
			s3client.shutdown();
		}
	}
	
}
