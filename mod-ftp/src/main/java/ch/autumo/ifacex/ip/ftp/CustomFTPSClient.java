/**
 * Copyright 2024 autumo GmbH, Michael Gasche.
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.Locale;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocket;

import org.apache.commons.net.ftp.FTPSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * FTP file out - writer prefix 'ftp_file_out'.
 * 
 * Writes files to FTP server into a specified folder.
 * It overwrites or creates new files, by determining existing matching
 * folder names and file names!
 * 
 * For reusable session you have to add JDK/JVM module parameters:
 * 
 *   --add-opens java.base/sun.security.util=ALL-UNNAMED --add-opens java.base/sun.security.ssl=ALL-UNNAMED
 */
public class CustomFTPSClient extends FTPSClient {
	
	private static final Logger LOG = LoggerFactory.getLogger(CustomFTPSClient.class.getName());
	
	private boolean reuseSession = false;

    public CustomFTPSClient(final SSLContext context, boolean reuseSession) {
        super(context);
        if (reuseSession) {
			System.setProperty("jdk.tls.allowLegacyResumption", "true");
			System.setProperty("jdk.tls.useExtendedMasterSecret", "false");
			System.setProperty("jdk.tls.client.enableSessionTicketExtension", "false");
        }
        this.reuseSession = reuseSession;
    }
	
    @Override
    protected void _prepareDataSocket_(final Socket socket) {
    	
        if (this.reuseSession && socket instanceof SSLSocket) {
            // Control socket is SSL
            final SSLSession session = ((SSLSocket) _socket_).getSession();
            if (session.isValid()) {
                final SSLSessionContext context = session.getSessionContext();
                try {
                    final Field sessionHostPortCache = context.getClass().getDeclaredField("sessionHostPortCache");
                    sessionHostPortCache.setAccessible(true);
                    final Object cache = sessionHostPortCache.get(context);
                    final Method putMethod = cache.getClass().getDeclaredMethod("put", Object.class, Object.class);
                    putMethod.setAccessible(true);
                    Method getHostMethod;
                    try {
                        getHostMethod = socket.getClass().getMethod("getPeerHost");
                    }
                    catch(NoSuchMethodException e) {
                        // Running in IKVM
                        getHostMethod = socket.getClass().getDeclaredMethod("getHost");
                    }
                    getHostMethod.setAccessible(true);
                    Object peerHost = getHostMethod.invoke(socket);
                    putMethod.invoke(cache, String.format("%s:%s", peerHost, socket.getPort()).toLowerCase(Locale.ROOT), session);
                }
                catch(NoSuchFieldException e) {
                    // Not running in expected JRE
                    LOG.warn("No field sessionHostPortCache in SSLSessionContext", e);
                }
                catch(Exception e) {
                    // Not running in expected JRE
                	LOG.warn(e.getMessage());
                }
            }
            else {
            	LOG.warn(String.format("SSL session %s for socket %s is not rejoinable", session, socket));
            }
        }
    }

}