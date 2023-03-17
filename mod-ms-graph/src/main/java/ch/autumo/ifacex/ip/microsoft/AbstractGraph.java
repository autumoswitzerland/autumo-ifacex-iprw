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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.ClientCredentialParameters;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.IClientCredential;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.requests.GraphServiceClient;

import ch.autumo.commons.utils.UtilsException;
import ch.autumo.ifacex.IPC;
import ch.autumo.ifacex.IfaceXException;
import ch.autumo.ifacex.Processor;
import ch.autumo.ifacex.generic.Generic;
import ch.autumo.ifacex.writer.Writer;
import okhttp3.Request;
import reactor.core.publisher.Mono;


/**
 * Abstract Microsoft Graph API access.
 */
public abstract class AbstractGraph implements Generic {

	private final static Logger LOG = LoggerFactory.getLogger(AbstractGraph.class.getName());
	
    private final static String DEFAULT_AUTHORITY = "https://login.microsoftonline.com/organizations/oauth2/v2.0/authorize";
	private final static String DEFAULT_GRAPH_SCOPE_URL = "https://graph.microsoft.com/.default";
	
    private boolean tokenAuth = false;
    private String authorisation_url = null;
    private String default_graph_scope_url = null;
    private String tenant_id = null; 
    private String client_id = null;
    private String client_secret = null;

    private List<String> scopes = null;
    
    private GraphServiceClient<Request> client = null;
    
    
	@Override
	public void initialize(String rwName, IPC config, Processor processor) throws IfaceXException {
		
		try {
			if (this instanceof Writer) {
				tokenAuth = config.getWriterConfig(rwName).isYes("_token_auth", false);
				default_graph_scope_url = config.getWriterConfig(rwName).getConfig("_default_graph_scope_url", DEFAULT_GRAPH_SCOPE_URL);
				authorisation_url = config.getWriterConfig(rwName).getConfig("_authorisation_url", DEFAULT_AUTHORITY);
				tenant_id = config.getWriterConfig(rwName).getConfigDecodedIfNecessary("_tenant_id");
				client_id = config.getWriterConfig(rwName).getConfigDecodedIfNecessary("_client_id");
				client_secret = config.getWriterConfig(rwName).getConfigDecodedIfNecessary("_client_secret");
			} else {
				tokenAuth = config.getReaderConfig().isYes("_token_auth", false);
				default_graph_scope_url = config.getReaderConfig().getConfig("_default_graph_scope_url", DEFAULT_GRAPH_SCOPE_URL);
				authorisation_url = config.getReaderConfig().getConfig("_authorisation_url", DEFAULT_AUTHORITY);
				tenant_id = config.getReaderConfig().getConfigDecodedIfNecessary("_tenant_id");
				client_id = config.getReaderConfig().getConfigDecodedIfNecessary("_client_id");
				client_secret = config.getReaderConfig().getConfigDecodedIfNecessary("_client_secret");
			}
		} catch (UtilsException e) {
			throw new IfaceXException("Couldn't read connection string!", e);
		}
		
		scopes = Collections.singletonList(default_graph_scope_url);
		
		client = this.createClient();
		
	    LOG.info("Connected to graph API account.");
		
		/*
		DriveRequest dr = client. drive().buildRequest(); // new QueryOption("name", "test")
		Drive drive = dr.get();
		System.err.println(drive.description);
		*/
		/*
		DirectoryRequest dire = client.directory().buildRequest();
		Directory dir = dire.get();
		System.err.println(dir.id);
		*/
		/*
		AppCatalogs appCatLogs = client.appCatalogs().buildRequest().get();
		System.err.println(appCatLogs.id);
		*/
		/*
		SubscriptionCollectionPage page = client.subscriptions().buildRequest().get();
		Iterator<Subscription> iter = page.getCurrentPage().iterator();
		while (iter.hasNext()) {
			System.err.println(iter.next().applicationId);
		}
		*/
	}

	@Override
	public void close(String rwName) throws IfaceXException {
		if (client != null)
			client = null;
	}

	/**
	 * Get graph client.
	 * 
	 * @return graph client
	 */
	protected GraphServiceClient<Request> client() {
		return this.client;
	}
	
	private GraphServiceClient<Request> createClient() {
		GraphServiceClient<Request> client = null;
		if (!tokenAuth) {
			final ClientSecretCredential clientSecretCredential = new ClientSecretCredentialBuilder()
			        .clientId(client_id)
			        .clientSecret(client_secret)
			        .tenantId(tenant_id)
			        .build();
			final TokenCredentialAuthProvider tokenCredentialAuthProvider = new TokenCredentialAuthProvider(scopes, clientSecretCredential);
			client = GraphServiceClient
					.builder()
					.authenticationProvider(tokenCredentialAuthProvider)
					.buildClient();
			
		} else {
			final TokenCredential tokenCredential = new TokenCredential() {
				@Override
				public AccessToken getTokenSync(TokenRequestContext request) {
					IAuthenticationResult result = null;
					try {
						result = AbstractGraph.this.acquireToken();
					} catch (Exception e) {
						throw new RuntimeException("Couldn't acquire token!", e);
					}
					final OffsetDateTime offsetDateTime = result.expiresOnDate().toInstant().atOffset(ZoneOffset.UTC);
					final AccessToken at = new AccessToken(result.accessToken(), offsetDateTime);
					return at;
				}
				@Override
				public Mono<AccessToken> getToken(TokenRequestContext request) {
					return Mono.just(this.getTokenSync(request));
				}
			};
			final TokenCredentialAuthProvider bap = new TokenCredentialAuthProvider(scopes, tokenCredential); 
			client = GraphServiceClient
					    .builder()
					    .authenticationProvider(bap)
					    .buildClient();			
		}
		return client;
	} 
	
	private IAuthenticationResult acquireToken() throws Exception {
        // This is the secret that is created in the Azure portal when registering the application
        final IClientCredential credential = ClientCredentialFactory.createFromSecret(client_secret);
        final ConfidentialClientApplication cca = ConfidentialClientApplication
                        .builder(client_id, credential)
                        .authority(authorisation_url)
                        .build();
        // Client credential requests will by default try to look for a valid token in the
        // in-memory token cache. If found, it will return this token. If a token is not found, or the
        // token is not valid, it will fall back to acquiring a token from the AAD service. Although
        // not recommended unless there is a reason for doing so, you can skip the cache lookup
        // by using .skipCache(true) in ClientCredentialParameters.
        final ClientCredentialParameters parameters = ClientCredentialParameters
                        .builder(Set.copyOf(scopes))
                        .build();
        return cca.acquireToken(parameters).join();	
	}
	
}

