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
package ch.autumo.ifacex.ip.microsoft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;

import ch.autumo.commons.utils.UtilsException;
import ch.autumo.ifacex.IPC;
import ch.autumo.ifacex.IfaceXException;
import ch.autumo.ifacex.Processor;
import ch.autumo.ifacex.generic.Generic;
import ch.autumo.ifacex.writer.Writer;


/**
 * Abstract Microsoft Azure Blob Storage.
 * 
 * See: https://learn.microsoft.com/en-us/azure/storage/blobs/storage-quickstart-blobs-java#authenticate-to-azure-and-authorize-access-to-blob-data
 */
public abstract class AbstractAzureBlobStorage implements Generic {

	private final static Logger LOG = LoggerFactory.getLogger(AbstractAzureBlobStorage.class.getName());
	
	public static int CONNECTION_TIMEOUT = 3000;
	
	private String account = null;
	private String connection_string = null;
	
	private BlobServiceClient client = null;
	
	
	@Override
	public void initialize(String rwName, IPC config, Processor processor) throws IfaceXException {

		try {
			if (this instanceof Writer) {
				account = config.getWriterConfig(rwName).getConfig("_account");
				connection_string = config.getWriterConfig(rwName).getConfigDecodedIfNecessary("_connection_string");
			} else {
				account = config.getReaderConfig().getConfig("_account");
				connection_string = config.getReaderConfig().getConfigDecodedIfNecessary("_connection_string");
			}
		} catch (UtilsException e) {
			throw new IfaceXException("Couldn't read connection string!", e);
		}
		
		// Azure connection string from access keys; not recommended - test purposes only
		if (connection_string != null && connection_string.trim().length() > 24 ) {
			
			client = new BlobServiceClientBuilder()
				    .connectionString(connection_string)
				    .buildClient();
		
		// Default and secure authentication chain
		} else {
			
			final DefaultAzureCredential defaultCredential = new DefaultAzureCredentialBuilder().build();
			client = new BlobServiceClientBuilder()
					.endpoint("https://" + account + ".blob.core.windows.net/")
					.credential(defaultCredential)
					.buildClient();
		}
		
	    LOG.info("Connected to Azure account '" + account + "'.");
	}

	@Override
	public void close(String rwName) throws IfaceXException {
		if (client != null)
			client = null;
	}

	/**
	 * Return client.
	 * 
	 * @return client
	 */
	protected BlobServiceClient client() {
		return this.client;
	}
	
}
