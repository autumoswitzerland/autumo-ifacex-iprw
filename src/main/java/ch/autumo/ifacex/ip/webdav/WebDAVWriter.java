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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.autumo.ifacex.IPC;
import ch.autumo.ifacex.IfaceXException;
import ch.autumo.ifacex.Processor;
import ch.autumo.ifacex.SourceEntity;
import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.writer.Writer;


/**
 * WebDAV file out - writer prefix 'webdav_file_out'.
 * 
 * Writes files to web-dav server into a specified folder.
 * It overwrites or creates new files, by determining existing matching
 * folder names and file names!
 */
public class WebDAVWriter extends AbstractWebDAV implements Writer {

	private final static Logger LOG = LoggerFactory.getLogger(WebDAVWriter.class.getName());
	
	private String origDestinationUri = null;
	private String destinationUrl = null;
	
	private boolean allowDelete = true;
	
	@Override
	public void initialize(String writerName, IPC config, Processor processor) throws IfaceXException {

		super.initialize(writerName, config, processor);
		
		this.origDestinationUri = config.getWriterConfig(writerName).getConfig("_dest_uri");
		this.allowDelete = config.getWriterConfig(writerName).isYes("_allow_delete", true);
		
		String destinationUri = origDestinationUri.replace(" ", "%20");
		
		if (!destinationUri.startsWith("/"))
			destinationUri = "/" + destinationUri;
		if (!destinationUri.endsWith("/"))
			destinationUri += "/";
		
		this.destinationUrl = baseUrl() + destinationUri;
		
		try {
			if (!webDAV().exists(this.destinationUrl)) {
				webDAV().createDirectory(this.destinationUrl);
			}
		} catch (Exception e) {
			throw new IfaceXException("Couldn't create directory '" + this.origDestinationUri + "'!", e);
		}
	}

	@Override
	public void initializeEntity(String writerName, IPC config, SourceEntity entity) throws IfaceXException {
	}

	@Override
	public void writeHeader(String writerName, IPC config, SourceEntity entity) throws IfaceXException {
	}

	@Override
	public void writeBatchData(String writerName, IPC config, BatchData batch, SourceEntity entity)
			throws IfaceXException {
		
		while (batch.hasNext()) {
			
			final String vals[] = batch.next();
			final File curr = new File(vals[0]); // absolute file path when reader 'FilePathReader/file_in' is used!
			
			LOG.info("Processing (path: '" + this.origDestinationUri + "'): " + curr.getName());
			
			FileInputStream fis = null;
			try {
				
				String name = curr.getName();
				name = URLEncoder.encode(name, "UTF-8").replace("+", "%20");
				String fullUrlPath = this.destinationUrl + name;
				
				if (webDAV().exists(fullUrlPath) && this.allowDelete)
					webDAV().delete(fullUrlPath);
				
				fis = new FileInputStream(curr);
				webDAV().put(fullUrlPath, fis);

			} catch (Exception e) {
				throw new IfaceXException("Couldn't upload file '" + vals[0] + "' to Web DAV path '" + this.destinationUrl + "'!", e);
			} finally {
				try {
					if (fis != null)
						fis.close();
				} catch (IOException e) {
				}
			}		
		}	
	}

}
