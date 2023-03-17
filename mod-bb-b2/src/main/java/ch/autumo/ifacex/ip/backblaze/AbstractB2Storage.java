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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.backblaze.b2.client.B2StorageClient;
import com.backblaze.b2.client.B2StorageClientFactory;
import com.backblaze.b2.client.exceptions.B2Exception;

import ch.autumo.ifacex.IPC;
import ch.autumo.ifacex.IfaceXException;
import ch.autumo.ifacex.Processor;
import ch.autumo.ifacex.generic.Generic;
import ch.autumo.ifacex.writer.Writer;


/**
 * Abstract BackBlaze B2 Storage.
 * 
 * See: https://github.com/Backblaze/b2-sdk-java
 * 
 */
public abstract class AbstractB2Storage implements Generic {
	
	private static final Logger LOG = LoggerFactory.getLogger(AbstractB2Storage.class.getName());
	
	private static final String USER_AGENT = "autumo-ifacex";
	
	private String app_key_id = null;
	private String app_key = null;

	private B2StorageClient client = null;

	
	@Override
	public void initialize(String rwName, IPC config, Processor processor) throws IfaceXException {

	    try {
			// IPC configurations
			if (this instanceof Writer) {
				app_key_id = config.getWriterConfig(rwName).getConfigDecodedIfNecessary("_api_key_id");
				app_key = config.getWriterConfig(rwName).getConfigDecodedIfNecessary("_api_key");
			} else {
				app_key_id = config.getReaderConfig().getConfigDecodedIfNecessary("_api_key_id");
				app_key = config.getReaderConfig().getConfigDecodedIfNecessary("_api_key");
			}
	    } catch (Exception e) {
	    	throw new IfaceXException("Couldn't read OpenStack credentials!", e);
	    }		
		
		client = B2StorageClientFactory
	             .createDefaultFactory()
	             .create(app_key_id, app_key, USER_AGENT);
		
	    try {
			LOG.info("Connected to B2 at end-point '"+client.getAccountAuthorization().getApiUrl()+"'.");
		} catch (B2Exception e) {
			LOG.info("Connected to B2.");
		}
	}

	@Override
	public void close(String rwName) throws IfaceXException {
		if (client != null)
			client.close();
	}

	/**
	 * Get B2 client.
	 * 
	 * @return B" client
	 */
	protected B2StorageClient client() {
		return this.client;
	}
	
}
