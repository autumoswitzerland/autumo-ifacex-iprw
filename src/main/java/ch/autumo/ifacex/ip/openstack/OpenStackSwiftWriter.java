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
package ch.autumo.ifacex.ip.openstack;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.openstack4j.api.storage.ObjectStorageContainerService;
import org.openstack4j.model.common.Payloads;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.autumo.ifacex.IPC;
import ch.autumo.ifacex.IfaceXException;
import ch.autumo.ifacex.Processor;
import ch.autumo.ifacex.SourceEntity;
import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.writer.Writer;


/**
 * OpenStack Swift ObjectStorage file out - writer prefix 'os_swift_file_out'.
 * 
 * Writes files into a container and creates the container first, if it
 * doesn't exist.
 * 
 * @WIP - Work in Progress
 */
public class OpenStackSwiftWriter extends AbstractOpenStack implements Writer {

	private final static Logger LOG = LoggerFactory.getLogger(OpenStackSwiftWriter.class.getName());
	
	private String container = null;
	
	@Override
	public void initialize(String writerName, IPC config, Processor processor) throws IfaceXException {
		
		if (true)
			throw new UnsupportedOperationException("Not yet tested!");

		super.initialize(writerName, config, processor);
		
		// Absolute or relative
		this.container = config.getWriterConfig(writerName).getConfig("_container");
		if (container != null && container.length() > 0) {
			container = container.trim();
			if (container.length() == 0)
				container = null;
		}
		
		final ObjectStorageContainerService service = client().objectStorage().containers();
		if (service.getMetadata(container) == null)
			service.create(container);
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
			
			LOG.info("Processing (conatainer: '" + container + "'): " + curr.getName());
			
			FileInputStream fis = null;
			try {
				
				fis = new FileInputStream(curr);
				client().objectStorage().objects().put(container, curr.getName(), Payloads.create(fis));
				
			} catch (Exception e) {
				throw new IfaceXException("Couldn't upload file '"+curr.getName()+"' to working container '"+container+"'!", e);
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
