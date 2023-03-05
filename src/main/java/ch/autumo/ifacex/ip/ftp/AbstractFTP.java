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
package ch.autumo.ifacex.ip.ftp;

import java.io.IOException;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.autumo.beetroot.BeetRootConfigurationManager;
import ch.autumo.beetroot.utils.security.SSLUtils;
import ch.autumo.ifacex.IPC;
import ch.autumo.ifacex.IfaceXException;
import ch.autumo.ifacex.Processor;
import ch.autumo.ifacex.generic.Generic;
import ch.autumo.ifacex.writer.Writer;


/**
 * Abstract FTP (with SSL if configured).
 */
public class AbstractFTP implements Generic {

	private final static Logger LOG = LoggerFactory.getLogger(AbstractFTP.class.getName());
	
	public static final int DEFAULT_PORT = 21; 
	
	private FTPClient client = null;
	
	private String host = null;
	private int port = -1;
	
	private String user = null;
	private String pass = null;
	
	private boolean ssl = false;
	
	
	@Override
	public void initialize(String rwName, IPC config, Processor processor) throws IfaceXException {
		
		try {
			if (this instanceof Writer) {
				host = config.getWriterConfig(rwName).getHost();
				port = config.getWriterConfig(rwName).getNumber(rwName, DEFAULT_PORT);
				user = config.getWriterConfig(rwName).getUser();
				pass = config.getWriterConfig(rwName).getPassword();
				ssl = config.getWriterConfig(rwName).isYes("_ssl", false);
			} else {
				host = config.getReaderConfig().getHost();
				port = config.getReaderConfig().getNumber(rwName, DEFAULT_PORT);
				user = config.getReaderConfig().getUser();
				pass = config.getReaderConfig().getPassword();
				ssl = config.getReaderConfig().isYes("_ssl", false);
			}
		} catch (Exception e) {
			throw new IfaceXException("Couldn't read credentials!", e);
		}

		try {
			if (ssl) {
				// Necessary for the SSLUtils!
				BeetRootConfigurationManager.getInstance().initialize("cfg/ifacex-server.cfg");
				client = new FTPSClient(SSLUtils.makeSSLContext(SSLUtils.getKeystoreFile(), SSLUtils.getKeystorePw()));
				
			} else {
				
				client = new FTPClient();
				
			}
		} catch (Exception e) {
			throw new IfaceXException("Couldn't create SSL context!", e);
		}
			
		try {
			client.connect(host.trim(), port);
			client.setKeepAlive(true);
			if (ssl)
				((FTPSClient) client).execPROT("P"); 
			client.login(user, pass);
		} catch (Exception e) {
			throw new IfaceXException("Couldn't create FTP client!", e);
		}
		
	    LOG.info("Connected to '" + host + ":" + port + "'.");
	}

	@Override
	public void close(String rwName) throws IfaceXException {
		if (client != null)
			try {
				client.logout();
				client.disconnect();
			} catch (IOException e) {
				LOG.info("Couldn't disconnect FTP client!", e);
			}
	}

	/**
	 * Get FTP client.
	 * 
	 * @return FTP client
	 */
	protected FTPClient client() {
		return this.client;
	}
	
}
