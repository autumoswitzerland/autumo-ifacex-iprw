/**
 * Copyright 2026 autumo GmbH, Michael Gasche.
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
package ch.autumo.ifacex.ip.nifi;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.nifi.remote.client.KeystoreType;
import org.apache.nifi.remote.client.SiteToSiteClient;
import org.apache.nifi.remote.protocol.SiteToSiteTransportProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.autumo.commons.utils.UtilsException;
import ch.autumo.ifacex.IPC;
import ch.autumo.ifacex.IfaceXException;
import ch.autumo.ifacex.Processor;
import ch.autumo.ifacex.RWC;
import ch.autumo.ifacex.generic.Generic;

/**
 * Abstract Apache NiFi Site-to-Site (S2S) client.
 *
 * <p>The reader creates a separate client for each source entity. The entity
 * name is used as the remote NiFi port name. The writer continues to use its
 * configured port name or port ID.</p>
 *
 * <p>Supported parameters:</p>
 * <ul>
 *   <li><code>url</code> - NiFi URL</li>
 *   <li><code>port_name</code> or <code>port_id</code> - writer port</li>
 *   <li><code>transport_protocol</code> - <code>HTTP</code> (default) or <code>RAW</code></li>
 *   <li><code>ssl</code> - use TLS? Default is derived from the URL scheme</li>
 *   <li><code>ssl_verify</code> - verify the server certificate? Default: yes</li>
 *   <li><code>keystore_file</code>, <code>keystore_password</code>, <code>keystore_type</code></li>
 *   <li><code>truststore_file</code>, <code>truststore_password</code>, <code>truststore_type</code></li>
 *   <li><code>timeout</code> - communication timeout in seconds</li>
 *   <li><code>batch_count</code>, <code>batch_size</code>, <code>batch_duration</code></li>
 *   <li><code>use_compression</code> - compress transferred content?</li>
 *   <li><code>peer_persistence_file</code> - optional cluster node cache file</li>
 * </ul>
 */
public abstract class AbstractNiFi implements Generic {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractNiFi.class.getName());

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final int DEFAULT_BATCH_COUNT = 100;
    private static final long DEFAULT_BATCH_SIZE_BYTES = 1024L * 1024L;

    private SiteToSiteClient client = null;
    private SiteToSiteClient injectedClient = null;
    private boolean clientInjected = false;

    /** Test hook: inject a (mock) Site-to-Site client; skips creation in initializeEntityClient(). */
    public void setClient(SiteToSiteClient client) {
        this.injectedClient = client;
        this.clientInjected = true;
    }

    @Override
    public void initialize(String rwName, IPC config, Processor processor) throws IfaceXException {

        // No client here. The client is created per source/destination entity in
        // initializeEntity(...) via initializeEntityClient(...). The entity name is
        // used as the remote NiFi port name.
    }

    @Override
    public void close(String rwName) throws IfaceXException {
        closeClient(rwName);
    }

    /**
     * Initialize the Site-to-Site client for a source entity.
     *
     * @param readerName reader name
     * @param rwc reader configuration
     * @param entityName source entity name
     * @throws IfaceXException if the client cannot be created
     */
    protected void initializeEntityClient(String readerName, RWC rwc, String entityName)
            throws IfaceXException {

        closeClient(readerName);

        if (clientInjected) {
            this.client = injectedClient;
            return;
        }

        this.client = createClient(readerName, rwc, entityName, null);
    }

    /**
     * Close the current Site-to-Site client.
     *
     * @param rwName reader or writer name
     * @throws IfaceXException if the client cannot be closed
     */
    private void closeClient(String rwName) throws IfaceXException {

        if (this.client == null)
            return;

        try {
            this.client.close();
        } catch (IOException e) {
            throw new IfaceXException(
                    "Couldn't close NiFi Site-to-Site client '" + rwName + "'!", e);
        } finally {
            this.client = null;
        }
    }

    /**
     * Get the Site-to-Site client.
     *
     * @return client
     */
    protected SiteToSiteClient client() {
        return this.client;
    }

    /**
     * Create a Site-to-Site client.
     *
     * @param rwName reader or writer name
     * @param rwc reader or writer configuration
     * @param portName remote port name
     * @param portId remote port ID
     * @return client
     * @throws IfaceXException if the client cannot be created
     */
    private SiteToSiteClient createClient(String rwName, RWC rwc, String portName, String portId)
            throws IfaceXException {

        final String url = getValue(rwc, "_url");
        if (url == null)
            throw new IfaceXException(
                    "NiFi interface '" + rwName + "': No URL configured (parameter 'url')!");

        if (portName == null && portId == null)
            throw new IfaceXException(
                    "NiFi interface '" + rwName + "': Neither a port name nor a port ID is available!");

        final SiteToSiteClient.Builder builder = new SiteToSiteClient.Builder();
        builder.url(url);

        if (portId != null)
            builder.portIdentifier(portId);
        else
            builder.portName(portName);

        builder.transportProtocol(getTransportProtocol(rwName, rwc));
        builder.timeout(getLong(rwc, "_timeout", DEFAULT_TIMEOUT_SECONDS), TimeUnit.SECONDS);
        builder.requestBatchCount((int) getLong(rwc, "_batch_count", DEFAULT_BATCH_COUNT));
        builder.requestBatchSize(getLong(rwc, "_batch_size", DEFAULT_BATCH_SIZE_BYTES));

        final long batchDuration = getLong(rwc, "_batch_duration", 0);
        if (batchDuration > 0)
            builder.requestBatchDuration(batchDuration, TimeUnit.MILLISECONDS);

        builder.useCompression(rwc.isYes("_use_compression", false));

        final String peerPersistenceFile = getValue(rwc, "_peer_persistence_file");
        if (peerPersistenceFile != null)
            builder.peerPersistenceFile(new File(peerPersistenceFile));

        final long cacheExpiration = getLong(rwc, "_cache_expiration", 0);
        if (cacheExpiration > 0)
            builder.cacheExpiration(cacheExpiration, TimeUnit.SECONDS);

        final long idleExpiration = getLong(rwc, "_idle_expiration", 0);
        if (idleExpiration > 0)
            builder.idleExpiration(idleExpiration, TimeUnit.SECONDS);

        final long nodePenalization = getLong(rwc, "_node_penalization_period", 0);
        if (nodePenalization > 0)
            builder.nodePenalizationPeriod(nodePenalization, TimeUnit.SECONDS);

        final boolean ssl = rwc.isYes("_ssl", url.toLowerCase().startsWith("https://"));
        if (ssl)
            configureSsl(rwName, rwc, builder);

        try {
            final SiteToSiteClient result = builder.build();
            LOG.info("NiFi interface '{}' connected to '{}' (port: {}).",
                    rwName, url, portId != null ? portId : portName);
            return result;
        } catch (Exception e) {
            throw new IfaceXException(
                    "Couldn't create NiFi Site-to-Site client '" + rwName
                            + "' for '" + url + "'!", e);
        }
    }

    /**
     * Configure the SSL context of the client.
     *
     * @param rwName reader or writer name
     * @param rwc reader or writer configuration
     * @param builder client builder
     * @throws IfaceXException if the SSL context cannot be created
     */
	private void configureSsl(String rwName, RWC rwc, SiteToSiteClient.Builder builder)
	        throws IfaceXException {
	
	    final String keystoreFile = getValue(rwc, "_keystore_file");
	    final String truststoreFile = getValue(rwc, "_truststore_file");
	    final boolean verify = rwc.isYes("_ssl_verify", true);
	
	    try {
	        KeyManager[] keyManagers = null;
	
	        if (keystoreFile != null) {
	            final String password = getPassword(rwc, "_keystore_password");
	
	            if (password == null)
	                throw new IfaceXException(
	                        "NiFi interface '" + rwName + "': No 'keystore_password' configured!");
	
	            final KeyStore keystore = KeyStore.getInstance(
	                    getKeystoreType(rwName, rwc, "_keystore_type").name());
	
	            try (FileInputStream input = new FileInputStream(keystoreFile)) {
	                keystore.load(input, password.toCharArray());
	            }
	
	            final KeyManagerFactory kmf = KeyManagerFactory.getInstance(
	                    KeyManagerFactory.getDefaultAlgorithm());
	            kmf.init(keystore, password.toCharArray());
	            keyManagers = kmf.getKeyManagers();
	        }
	
	        TrustManager[] trustManagers;
	
	        if (verify) {
	            if (truststoreFile != null) {
	                final String password = getPassword(rwc, "_truststore_password");
	
	                if (password == null)
	                    throw new IfaceXException(
	                            "NiFi interface '" + rwName + "': No 'truststore_password' configured!");
	
	                final KeyStore truststore = KeyStore.getInstance(
	                        getKeystoreType(rwName, rwc, "_truststore_type").name());
	
	                try (FileInputStream input = new FileInputStream(truststoreFile)) {
	                    truststore.load(input, password.toCharArray());
	                }
	
	                final TrustManagerFactory tmf = TrustManagerFactory.getInstance(
	                        TrustManagerFactory.getDefaultAlgorithm());
	                tmf.init(truststore);
	                trustManagers = tmf.getTrustManagers();
	            } else {
	                trustManagers = null;
	            }
	        } else {
	            trustManagers = new TrustManager[] {
	                    new X509TrustManager() {
	                        @Override
	                        public X509Certificate[] getAcceptedIssuers() {
	                            return new X509Certificate[0];
	                        }
	
	                        @Override
	                        public void checkClientTrusted(
	                                X509Certificate[] certificates, String authType)
	                                throws CertificateException {
	                        }
	
	                        @Override
	                        public void checkServerTrusted(
	                                X509Certificate[] certificates, String authType)
	                                throws CertificateException {
	                        }
	                    }
	            };
	
	            LOG.warn("NiFi interface '{}': SSL server certificate verification is disabled.",
	                    rwName);
	        }
	
	        final SSLContext sslContext = SSLContext.getInstance("TLS");
	        sslContext.init(keyManagers, trustManagers, new SecureRandom());
	        builder.sslContext(sslContext);
	
	    } catch (IfaceXException e) {
	        throw e;
	    } catch (Exception e) {
	        throw new IfaceXException(
	                "NiFi interface '" + rwName + "': Couldn't create SSL context!", e);
	    }
	}


    private SiteToSiteTransportProtocol getTransportProtocol(String rwName, RWC rwc)
            throws IfaceXException {

        final String value = getValue(rwc, "_transport_protocol");
        if (value == null)
            return SiteToSiteTransportProtocol.HTTP;

        try {
            return SiteToSiteTransportProtocol.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IfaceXException(
                    "NiFi interface '" + rwName
                            + "': Invalid 'transport_protocol' value '" + value + "'!", e);
        }
    }

    private KeystoreType getKeystoreType(String rwName, RWC rwc, String key)
            throws IfaceXException {

        final String value = getValue(rwc, key);
        if (value == null)
            return KeystoreType.PKCS12;

        try {
            return KeystoreType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IfaceXException(
                    "NiFi interface '" + rwName
                            + "': Invalid key store type '" + value + "'!", e);
        }
    }

    protected static String getValue(RWC rwc, String key) {
        final String value = rwc.getConfig(key);
        if (value == null)
            return null;

        final String trimmed = value.trim();
        return trimmed.length() == 0 ? null : trimmed;
    }

    protected static String getPassword(RWC rwc, String key) throws IfaceXException {
        final String value = getValue(rwc, key);
        if (value == null)
            return null;

        try {
            return rwc.getPossiblyEncryptedValue(value);
        } catch (UtilsException e) {
            throw new IfaceXException(
                    "Couldn't decode configuration value '" + key + "'!", e);
        }
    }

    protected static long getLong(RWC rwc, String key, long defaultValue)
            throws IfaceXException {

        final String value = getValue(rwc, key);
        if (value == null)
            return defaultValue;

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new IfaceXException(
                    "Invalid number '" + value
                            + "' for configuration value '" + key + "'!", e);
        }
    }
}
