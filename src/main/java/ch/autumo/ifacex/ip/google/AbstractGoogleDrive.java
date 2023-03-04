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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;

import ch.autumo.commons.utils.OSUtils;
import ch.autumo.ifacex.IPC;
import ch.autumo.ifacex.IfaceXException;
import ch.autumo.ifacex.Processor;
import ch.autumo.ifacex.generic.Generic;
import ch.autumo.ifacex.writer.Writer;


/**
 * Abstract Google Drive.
 * 
 * See: {@link https://developers.google.com/drive/api/quickstart/java}.
 * See: {@link https://developers.google.com/drive/api/v3/reference/files}.
 * See: {@link https://developers.google.com/drive/api/v3/reference/files/list}.
 */
public abstract class AbstractGoogleDrive implements Generic {
	
	private final static Logger LOG = LoggerFactory.getLogger(AbstractGoogleDrive.class.getName());
	
	/**
	 * Global instance of the JSON factory.
	 */
	private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
	
	/**
	 * Directory to store authorization tokens for this application.
	 */
	private static final String TOKENS_DIRECTORY_PATH = ".ifacex-google-drive-tokens";

	/**
	 * Global instance of the scopes required by this quickstart. If modifying these
	 * scopes, delete your previously saved tokens/ folder.
	 */
	private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE);

	private LocalServerReceiver receiver = null;
	private Drive service = null;

	private String client_id = null;
	private String project_id = null;
	private String auth_uri = null;
	private String token_uri = null;
	private String auth_provider_x509_cert_url = null;
	private String client_secret = null;
	private String redirect_uris[] = null;

	@Override
	public void initialize(String rwName, IPC config, Processor processor) throws IfaceXException {

		String appName = null;
		
	    try {
	    	
			// IPC configurations
			if (this instanceof Writer) {
				client_id = config.getWriterConfig(rwName).getConfigDecodedIfNecessary("_client_id");
				project_id = config.getWriterConfig(rwName).getConfig("_project_id");
				auth_uri = config.getWriterConfig(rwName).getConfig("_auth_uri");
				token_uri = config.getWriterConfig(rwName).getConfig("_token_uri");
				auth_provider_x509_cert_url = config.getWriterConfig(rwName).getConfig("_auth_provider_x509_cert_url");
				client_secret = config.getWriterConfig(rwName).getConfigDecodedIfNecessary("_client_secret");
				redirect_uris = config.getWriterConfig(rwName).getSeparatedValues("_redirect_uris");
				appName = config.getWriterConfig(rwName).getConfig("_application_name");
			} else {
				client_id = config.getReaderConfig().getConfigDecodedIfNecessary("_client_id");
				project_id = config.getReaderConfig().getConfig("_project_id");
				auth_uri = config.getReaderConfig().getConfig("_auth_uri");
				token_uri = config.getReaderConfig().getConfig("_token_uri");
				auth_provider_x509_cert_url = config.getReaderConfig().getConfig("_auth_provider_x509_cert_url");
				client_secret = config.getReaderConfig().getConfigDecodedIfNecessary("_client_secret");
				redirect_uris = config.getReaderConfig().getSeparatedValues("_redirect_uris");
				appName = config.getReaderConfig().getConfig("_application_name");
			}
		
			// Build a new authorized API client service.
		    final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
		    service = new Drive.Builder(httpTransport, JSON_FACTORY, this.getCredentials(httpTransport))
		    		.setApplicationName(appName)
		    		.build();
	
		    LOG.info("Connected to app '" + appName + "' and project '" + project_id + "'.");
		    
	    } catch (Exception e) {
	    	throw new IfaceXException("Cannot create google drive service!", e);
	    }
	}

	@Override
	public void close(String rwName) throws IfaceXException {
		if (receiver != null)
			try {
				receiver.stop();
			} catch (IOException e) {
				throw new IfaceXException("Couldn't stop local receiver!", e);
			}
	}

	/**
	 * Get google drive service.
	 * 
	 * @return google drive service
	 */
	protected Drive service() {
		return service;
	}
	
	/**
	 * Creates an authorized Credential object.
	 *
	 * @param httpTransport The network HTTP Transport.
	 * @return An authorized Credential object.
	 * @throws IOException If the credentials.json file cannot be found.
	 */
	private Credential getCredentials(final NetHttpTransport httpTransport) throws IOException {
		
		String redirect_uris_str = "";
		for (int i = 0; i < redirect_uris.length; i++) {
			if (i + 1 == redirect_uris.length) 
				redirect_uris_str += redirect_uris[i];
			else
				redirect_uris_str += redirect_uris[i]+",";
		}
		
		String json =       "{"
							+ "\"installed\" : {"
							+ "  \"client_id\"                   : \""+client_id+"\","
							+ "  \"project_id\"                  : \""+project_id+"\","
							+ "  \"auth_uri\"                    : \""+auth_uri+"\","
							+ "  \"token_uri\"                   : \""+token_uri+"\","
							+ "  \"auth_provider_x509_cert_url\" : \""+auth_provider_x509_cert_url+"\","
							+ "  \"client_secret\"               : \""+client_secret+"\"";
		if (redirect_uris.length > 0)
			          json += " ,\"redirect_uris\"               : [\""+redirect_uris_str+"\"]";
			          
                      json += "}"
						  + "}";
		
		final InputStream in = IOUtils.toInputStream(json.trim(), Charset.defaultCharset());
		final GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
		final GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
				.setDataStoreFactory(new FileDataStoreFactory(new java.io.File(OSUtils.USER_HOME + OSUtils.FILE_SEPARATOR + TOKENS_DIRECTORY_PATH)))
				.setAccessType("offline").build();

		receiver = new LocalServerReceiver.Builder().setPort(8888).build();
		final Credential credential = new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
		in.close();
		
		return credential;
	}
	
}
