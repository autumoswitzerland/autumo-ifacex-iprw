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

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.autumo.ifacex.IPC;
import ch.autumo.ifacex.IfaceXException;
import ch.autumo.ifacex.Processor;
import ch.autumo.ifacex.generic.Generic;
import ch.autumo.ifacex.writer.Writer;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.StatefulSFTPClient;


/**
 * Abstract SFTP (SSH FTP).
 * 
 * Tested with the Wing FTP Server,
 * https://www.wftpserver.com/
 */
public class AbstractSFTP implements Generic {

	private final static Logger LOG = LoggerFactory.getLogger(AbstractSFTP.class.getName());
	
	public static final int DEFAULT_TRANSPORT_TIMEOUT = 3000; 
	public static final int DEFAULT_PORT = 22; 
	
	private SSHClient client = null;
	private StatefulSFTPClient sftpClient = null;
	
	private String known_hosts = null;
	private String host = null;
	private int port = -1;
	
	private String user = null;
	private String pass = null;

	
	@Override
	public void initialize(String rwName, IPC config, Processor processor) throws IfaceXException {
		try {
			if (this instanceof Writer) {
				known_hosts = config.getWriterConfig(rwName).getConfig("_known_hosts");
				host = config.getWriterConfig(rwName).getHost();
				port = config.getWriterConfig(rwName).getNumber(rwName, DEFAULT_PORT);
				user = config.getWriterConfig(rwName).getUser();
				pass = config.getWriterConfig(rwName).getPassword();
			} else {
				known_hosts = config.getReaderConfig().getConfig("_known_hosts");
				host = config.getReaderConfig().getHost();
				port = config.getReaderConfig().getNumber(rwName, DEFAULT_PORT);
				user = config.getReaderConfig().getUser();
				pass = config.getReaderConfig().getPassword();
			}
		} catch (Exception e) {
			throw new IfaceXException("Couldn't read credentials!", e);
		}
		
		client = new SSHClient();
		client.getTransport().setTimeoutMs(DEFAULT_TRANSPORT_TIMEOUT);
		
	    try {
			//client.addHostKeyVerifier(new PromiscuousVerifier());
	    	if (known_hosts == null || known_hosts.trim().length() == 0)
				client.loadKnownHosts();
	    	else
	    		client.loadKnownHosts(new File(known_hosts));

	        client.connect(host);
			// the SFTP (OS) user in the target system
		    client.authPassword(user, pass);
		    sftpClient = (StatefulSFTPClient) client.newStatefulSFTPClient();
		    
		} catch (Exception e) {
			throw new IfaceXException("Couldn't create SFTP client!", e);
		}
	    
	    LOG.info("Connected to '" + host + ":" + port + "'.");
	}

	@Override
	public void close(String rwName) throws IfaceXException {
		try {
			if (sftpClient != null)
				sftpClient.close();
			if (sftpClient != null)
				client.disconnect();
		} catch (IOException e) {
		}
	}

	/**
	 * Get SFTP client.
	 * 
	 * @return SFTP client
	 */
	protected StatefulSFTPClient client() {
		return this.sftpClient;
	}
	
}
