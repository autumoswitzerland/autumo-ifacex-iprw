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
package ch.autumo.ifacex.ip.dropbox;

import java.nio.file.InvalidPathException;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.users.FullAccount;

import ch.autumo.ifacex.Constants;
import ch.autumo.ifacex.IPC;
import ch.autumo.ifacex.IfaceXException;
import ch.autumo.ifacex.Processor;
import ch.autumo.ifacex.generic.Generic;
import ch.autumo.ifacex.writer.Writer;


/**
 * Abstract Dropbox.
 * 
 * See: {@link https://github.com/dropbox/dropbox-sdk-java#dropbox-for-java-tutorial}.
 */
public abstract class AbstractDropbox implements Generic {

	private final static Logger LOG = LoggerFactory.getLogger(AbstractDropbox.class.getName());

	private static String SOFTWARE_NAME = "ifaceX-IP-Dropbox";
	private static String SOFTWARE_VERSION = Constants.APP_VERSION;
	
	private DbxClientV2 client= null;
	
	private String oAuthAccessToken = null;
	
	private String softwareName = null;
	private String softwareVersion = null;

	private String path = null;
	
	
	@Override
	public void initialize(String rwName, IPC config, Processor processor) throws IfaceXException {
		
	    try {
			// IPC configurations
			if (this instanceof Writer) {
				oAuthAccessToken = config.getWriterConfig(rwName).getConfigDecodedIfNecessary("_oauth_access_token");
				softwareName = config.getWriterConfig(rwName).getConfig("_app_name", SOFTWARE_NAME);
				softwareVersion = config.getWriterConfig(rwName).getConfig("_app_version", SOFTWARE_VERSION);
				path = config.getWriterConfig(rwName).getConfig("_path");
			} else {
				oAuthAccessToken = config.getReaderConfig().getConfigDecodedIfNecessary("_oauth_access_token");
				softwareName = config.getReaderConfig().getConfig("_app_name", SOFTWARE_NAME);
				softwareVersion = config.getReaderConfig().getConfig("_app_version", SOFTWARE_VERSION);
				path = config.getReaderConfig().getConfig("_path");
			}
			
			final DbxRequestConfig dbconfig = DbxRequestConfig.newBuilder(softwareName + "/" + softwareVersion).build();
			client = new DbxClientV2(dbconfig, oAuthAccessToken);

	    } catch (Exception e) {
	    	throw new IfaceXException("Cannot create google drive service!", e);
	    }

	    if (path != null && path.trim().length() > 0) {
	    	if (!isValidPath(path))
	    		throw new IfaceXException("Path '"+path+"' is invalid!");
	    } else {
	    	path = "/";
	    }
	    
		FullAccount account = null;
		String accountName = null;
		try {
			account = client.users().getCurrentAccount();
			accountName = account.getName().getDisplayName();
		} catch (Exception e) {
			throw new IfaceXException("Couldn't access Dropbox API!", e);
		}
		
	    LOG.info("Connected to account '" + accountName + ".");
	}

	@Override
	public void close(String rwName) throws IfaceXException {
		if (client != null)
			client = null;
	}

	/**
	 * Get Dropbox client.
	 * 
	 * @return Dropbox client
	 */
	protected DbxClientV2 client() {
		return client;
	}

	/**
	 * Get input/output path.
	 * 
	 * @return input/output path
	 */
	protected String path() {
		return path;
	}
	
	private static boolean isValidPath(String path) {
		if (path.indexOf("\\") != -1 || path.indexOf(":") != -1)
			return false;
	    try {
	        Paths.get(path);
	    } catch (InvalidPathException | NullPointerException ex) {
	        return false;
	    }
	    return true;
	}
	
}
