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

import java.io.FileInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import ch.autumo.ifacex.IPC;
import ch.autumo.ifacex.IfaceXException;
import ch.autumo.ifacex.Processor;
import ch.autumo.ifacex.generic.Generic;
import ch.autumo.ifacex.writer.Writer;

/**
 * Abstract Google Cloud Storage.
 */
public class AbstractGoogleStorage implements Generic {

	private final static Logger LOG = LoggerFactory.getLogger(AbstractGoogleStorage.class.getName());
	
	private Storage storage = null;
	
	private String json_credentials_file = null;
	private String project_id = null;
	
	@Override
	public void initialize(String rwName, IPC config, Processor processor) throws IfaceXException {
	    	
		if (this instanceof Writer) {
			json_credentials_file = config.getWriterConfig(rwName).getConfig("_credentials_file");
			project_id = config.getWriterConfig(rwName).getConfig("_project_id");
		} else {
			json_credentials_file = config.getReaderConfig().getConfig("_credentials_file");
			project_id = config.getReaderConfig().getConfig("_project_id");
		}
		
		Credentials credentials = null;
		try {
			credentials = GoogleCredentials.fromStream(new FileInputStream(json_credentials_file));
		} catch (Exception e) {
		}
		
		storage = StorageOptions.newBuilder().setCredentials(credentials).setProjectId(project_id).build().getService();
		
	    LOG.info("Connected to project '" + project_id + "'.");
	}

	@Override
	public void close(String rwName) throws IfaceXException {
		if (storage != null)
			try {
				storage.close();
			} catch (Exception e) {
				throw new IfaceXException("Couldn't close cloud storage!", e);
			}
	}

	/**
	 * Get cloud storage.
	 * 
	 * @return cloud storage
	 */
	protected Storage storage() {
		return storage;
	}

	/**
	 * Get project ID.
	 * 
	 * @return project ID
	 */
	protected String getProjectId() {
		return project_id;
	}
	
}
