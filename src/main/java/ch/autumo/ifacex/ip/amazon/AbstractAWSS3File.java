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
import ch.autumo.ifacex.generic.Generic;
import ch.autumo.ifacex.writer.Writer;


/**
 * Abstract Amazon AWS S3 file.
 */
public abstract class AbstractAWSS3File implements Generic {
	
	private final static Logger LOG = LoggerFactory.getLogger(AbstractAWSS3File.class.getName());
	
	private AmazonS3 s3client = null;
	private AWSCredentials credentials = null;
	private EndpointConfiguration endpoint = null;
	private String urlEndpoint = null;
	private String region = null;
	private String keyPrefix = null;
	
	@Override
	public void initialize(String rwName, IPC config, Processor processor) throws IfaceXException {
		
		String accessKey = null;
		String secretKey = null;
		try {
			if (this instanceof Writer) {
				accessKey = config.getWriterConfig(rwName).getConfigDecodedIfNecessary("_access_key");
				secretKey = config.getWriterConfig(rwName).getConfigDecodedIfNecessary("_secret_key");
			} else {
				accessKey = config.getReaderConfig().getConfigDecodedIfNecessary("_access_key");
				secretKey = config.getReaderConfig().getConfigDecodedIfNecessary("_secret_key");
			}
		} catch (UtilsException e) {
			throw new IfaceXException("Couldn't read credentials!", e);
		}
		
		credentials = new BasicAWSCredentials(accessKey, secretKey);
		
		if (this instanceof Writer) {
			keyPrefix = config.getWriterConfig(rwName).getConfig("_key_prefix");
			urlEndpoint = config.getWriterConfig(rwName).getConfig("_url_endpoint");
			region = config.getWriterConfig(rwName).getConfig("_region");
		} else {
			urlEndpoint = config.getReaderConfig().getConfig("_url_endpoint");
			region = config.getReaderConfig().getConfig("_region");
		}
		
		if (keyPrefix != null && keyPrefix.length() > 0) {
			if (!keyPrefix.endsWith("/"))
				keyPrefix += "/";
		} else {
			keyPrefix = "";
		}
		
		
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

	    LOG.info("Connected to endpoint '" + urlEndpoint + "' and region '" + region + "'.");
	}

	@Override
	public void close(String rwName) throws IfaceXException {
		if (s3client != null) {
			s3client.shutdown();
		}
	}

	/**
	 * Get S3 client.
	 * @return client
	 */
	protected AmazonS3 client() {
		return s3client;
	}

	/**
	 * Get key prefix.
	 * @return key prefix
	 */
	protected String getKeyPrefix() {
		return keyPrefix;
	}
	
}
