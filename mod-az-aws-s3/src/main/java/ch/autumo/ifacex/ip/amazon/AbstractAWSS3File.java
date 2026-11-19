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

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import ch.autumo.commons.utils.UtilsException;
import ch.autumo.ifacex.IPC;
import ch.autumo.ifacex.IfaceXException;
import ch.autumo.ifacex.Processor;
import ch.autumo.ifacex.generic.Generic;
import ch.autumo.ifacex.writer.Writer;


/**
 * Abstract Amazon AWS S3 file.
 * 
 * Uses the AWS SDK for Java 2.x.
 */
public abstract class AbstractAWSS3File implements Generic {
	
	private final static Logger LOG = LoggerFactory.getLogger(AbstractAWSS3File.class.getName());
	
	public static final String DEFAULT_REGION = "eu-central-2";
	
	private S3Client s3client = null;
	private String urlEndpoint = null;
	private String region = null;
	private String keyPrefix = null;
	private boolean clientInjected = false;
	
	/** Test hook: inject a (mock) S3 client; skips creation in initialize(). */
	public void setClient(S3Client client) {
		this.s3client = client;
		this.clientInjected = true;
	}
	
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
		
		final AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
		
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
		
		if (region == null || region.trim().length() == 0)
			region = DEFAULT_REGION;
	    
	    if (!clientInjected && s3client == null) {
	    	
	    	final S3ClientBuilder builder = S3Client.builder()
	    			.forcePathStyle(true)
	    			.region(Region.of(region))
	    			.credentialsProvider(StaticCredentialsProvider.create(credentials));
	    	
	    	if (urlEndpoint != null && urlEndpoint.length() > 0)
	    		builder.endpointOverride(URI.create(urlEndpoint));
	    	
	    	s3client = builder.build();
	    }

	    LOG.info("Connected to endpoint '" + urlEndpoint + "' and region '" + region + "'.");
	}

	@Override
	public void close(String rwName) throws IfaceXException {
		if (s3client != null) {
			s3client.close();
		}
	}

	/**
	 * Get S3 client.
	 * @return client
	 */
	protected S3Client client() {
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
