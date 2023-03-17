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
package ch.autumo.ifacex.ip.webdav;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import javax.net.ssl.SSLSocketFactory;

import org.apache.http.HttpHost;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.github.sardine.impl.SardineImpl;

import ch.autumo.beetroot.BeetRootConfigurationManager;
import ch.autumo.beetroot.transport.SecureSocketFactory;
import ch.autumo.beetroot.utils.security.SSLUtils;
import ch.autumo.ifacex.IPC;
import ch.autumo.ifacex.IfaceXException;
import ch.autumo.ifacex.Processor;
import ch.autumo.ifacex.generic.Generic;
import ch.autumo.ifacex.writer.Writer;

/**
 * Abstract Web DAV (with SSL if configured).
 */
public abstract class AbstractWebDAV implements Generic {

	private final static Logger LOG = LoggerFactory.getLogger(AbstractWebDAV.class.getName());
	
	private Sardine webdav = null;
	
	private SSLSocketFactory socketFactory = null;

	private String url = null;
	
	private String user = null;
	private String pass = null;
	
	private boolean ssl = false;
	
	@Override
	public void initialize(String rwName, IPC config, Processor processor) throws IfaceXException {
		
		try {
			if (this instanceof Writer) {
				url = config.getWriterConfig(rwName).getConfig("_url");
				user = config.getWriterConfig(rwName).getUser();
				pass = config.getWriterConfig(rwName).getPassword();
				ssl = config.getWriterConfig(rwName).isYes("_ssl", false);
			} else {
				url = config.getReaderConfig().getConfig("_url");
				user = config.getReaderConfig().getUser();
				pass = config.getReaderConfig().getPassword();
				ssl = config.getReaderConfig().isYes("_ssl", false);
			}
		} catch (Exception e) {
			throw new IfaceXException("Couldn't read credentials!", e);
		}
		
		url = url.trim();
		if (url.endsWith("/"))
			url = url.substring(0, url.length() - 1);
		
		if (ssl) {
			
			try {
				// Necessary for the SSLUtils!
				BeetRootConfigurationManager.getInstance().initialize("cfg/ifacex-server.cfg");
				final SecureSocketFactory factory = new SecureSocketFactory(SSLUtils.makeSSLSocketFactory(SSLUtils.getKeystoreFile(), SSLUtils.getKeystorePw()), null);
				socketFactory = factory.getBaseSocketFactory();
			} catch (Exception e) {
				throw new IfaceXException("Couldn't create SSL socket factory!", e);
			}
			
			webdav = new SardineImpl(user, pass) {
				@Override
				protected ConnectionSocketFactory createDefaultSecureSocketFactory() {
					return new ConnectionSocketFactory() {
						@Override
						public Socket createSocket(HttpContext context) throws IOException {
							return socketFactory.createSocket();
						}
						@Override
						public Socket connectSocket(int connectTimeout,
													Socket socket, HttpHost host,
													InetSocketAddress remoteAddress, InetSocketAddress localAddress,
													HttpContext context)
													throws IOException {
							
							final Socket sock = socket != null ? socket : createSocket(context);
					        if (localAddress != null) {
					            sock.bind(localAddress);
					        }
					        try {
					            sock.connect(remoteAddress, connectTimeout);
					        } catch (final IOException ex) {
					            try {
					                sock.close();
					            } catch (final IOException ignore) {
					            }
					            throw ex;
					        }
					        return sock;
						}
					};
				}			
			};	

		} else {
			webdav = SardineFactory.begin(user, pass);
		}
		
	    LOG.info("Connected to '" + url + "'.");
	}

	@Override
	public void close(String rwName) throws IfaceXException {
		if (webdav != null)
			try {
				webdav.shutdown();
			} catch (IOException e) {
				throw new IfaceXException("Couldn't close Web DAV service!");
			}
	}

	/**
	 * Get Web DAV service.
	 * 
	 * @return Web DAV service
	 */
	protected Sardine webDAV() {
		return webdav;
	}

	/**
	 * Get base url.
	 * 
	 * @return base url
	 */
	protected String baseUrl() {
		return url;
	}
	
}
