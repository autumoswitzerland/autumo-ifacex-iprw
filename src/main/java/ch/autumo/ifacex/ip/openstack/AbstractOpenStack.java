/**
s * Copyright 2023 autumo GmbH, Michael Gasche.
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
package ch.autumo.ifacex.ip.openstack;

import org.openstack4j.api.OSClient;
import org.openstack4j.api.client.IOSClientBuilder;
import org.openstack4j.core.transport.Config;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.openstack.OSFactory;
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
 * Abstract OpenStack, KeyStone v2.0 or v3.
 * 
 * See: https://github.com/openstack4j/openstack4j
 */
public class AbstractOpenStack implements Generic {

	private final static Logger LOG = LoggerFactory.getLogger(AbstractOpenStack.class.getName());
	
	public static int CONNECTION_TIMEOUT = 3000;
	
	private OSClient<?> client = null;
	
	private String user = null;
	private String pass = null;
	private String project = null;
	private String domain = null;
	private String url_endpoint = null;
	private boolean ssl = false;
	private int version = 3;
	
	
	@Override
	public void initialize(String rwName, IPC config, Processor processor) throws IfaceXException {
		
	    try {
	    	
			// IPC configurations
			if (this instanceof Writer) {
				user = config.getWriterConfig(rwName).getUser();
				pass = config.getWriterConfig(rwName).getPassword();
				domain = config.getWriterConfig(rwName).getConfig("domain");
				project = config.getWriterConfig(rwName).getConfig("_project");
				url_endpoint = config.getWriterConfig(rwName).getConfig("_url_endpoint");
				ssl  = config.getWriterConfig(rwName).isYes("_ssl", false);
				version  = config.getWriterConfig(rwName).getNumber("_version", 3);
			} else {
				user = config.getReaderConfig().getUser();
				pass = config.getReaderConfig().getPassword();
				domain = config.getReaderConfig().getConfig("domain");
				project = config.getReaderConfig().getConfig("_project");
				url_endpoint = config.getReaderConfig().getConfig("_url_endpoint");
				ssl  = config.getReaderConfig().isYes("_ssl", false);
				version  = config.getReaderConfig().getNumber("_version", 3);
			}
	    } catch (Exception e) {
	    	throw new IfaceXException("Couldn't read OpenStack credentials!", e);
	    }
	    
	    Config os_config = Config.newConfig()
	    		.withConnectionTimeout(CONNECTION_TIMEOUT)
	    		.withReadTimeout(CONNECTION_TIMEOUT);
	    
		try {
			if (ssl) {
				// Necessary for the SSLUtils!
				BeetRootConfigurationManager.getInstance().initialize("cfg/ifacex-server.cfg");
				os_config = os_config.withSSLContext(SSLUtils.makeSSLContext());
			}
		} catch (Exception e) {
			throw new IfaceXException("Couldn't create SSL context!", e);
		}	    
	    
		if (version == 3) {
			
			// KeyStone V3
		    IOSClientBuilder.V3 builder = OSFactory.builderV3()
		            .endpoint(url_endpoint)
		            .withConfig(os_config)
		            .credentials(user, pass);
		    
		    if (domain != null && domain.trim().length() > 0)
		    	builder = builder.scopeToDomain(Identifier.byName(domain));
		    if (project != null && project.trim().length() > 0)
		    	builder = builder.scopeToProject(Identifier.byName(project));
		    
	    	client = builder.authenticate();
	    	
		} else {
			
			// KeyStone V2
		    IOSClientBuilder.V2 builder = OSFactory.builderV2()
		            .endpoint(url_endpoint)
		            .withConfig(os_config)
		            .credentials(user, pass);
		    
		    if (project != null && project.trim().length() > 0)
		    	builder = builder.tenantName(project);
		    
	    	client = builder.authenticate();
			
		}
		
	    LOG.info("Connected to end-point '" + url_endpoint + "'.");
	}

	@Override
	public void close(String rwName) throws IfaceXException {
		if (client != null)
			client = null;
	}

	/**
	 * Get OpenStack client.
	 * 
	 * @return open stack client
	 */
	protected OSClient<?> client() {
		return this.client;
	}
	
}
