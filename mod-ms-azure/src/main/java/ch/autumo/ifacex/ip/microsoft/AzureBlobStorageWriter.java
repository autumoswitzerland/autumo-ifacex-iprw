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

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;

import ch.autumo.ifacex.IPC;
import ch.autumo.ifacex.IfaceXException;
import ch.autumo.ifacex.Processor;
import ch.autumo.ifacex.SourceEntity;
import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.writer.Writer;


/**
 * Microsoft Azure Blob Storage file out - writer prefix 'azure_file_out'.
 * 
 * Writes files into a container and creates the container first, if it
 * doesn't exist. Existing files are overwritten!
 *  
 */
public class AzureBlobStorageWriter extends AbstractAzureBlobStorage implements Writer {

	private final static Logger LOG = LoggerFactory.getLogger(AzureBlobStorageWriter.class.getName());
	
	private String container = null;
	
	private BlobContainerClient contClient = null;
	
	
	@Override
	public void initialize(String writerName, IPC config, Processor processor) throws IfaceXException {
		
		container = config.getWriterConfig(writerName).getConfig("_container");

		super.initialize(writerName, config, processor);
		
	    // Only create container if it doesn't exists
		contClient = client().getBlobContainerClient(container);
		if (!contClient.exists()) {
			contClient = client().createBlobContainerIfNotExists(container);
			LOG.info("Azure container '"+container+"' created!");
		}
	}

	@Override
	public void writeHeader(String writerName, IPC config, SourceEntity entity) throws IfaceXException {
	}
	
	@Override
	public void initializeEntity(String writerName, IPC config, SourceEntity entity) throws IfaceXException {
	}

	@Override
	public void writeBatchData(String writerName, IPC config, BatchData batch, SourceEntity entity)
			throws IfaceXException {
		
		while (batch.hasNext()) {
			
			final String vals[] = batch.next();
			final File curr = new File(vals[0]); // absolute file path when reader 'FilePathReader/file_in' is used!
			
			LOG.info("Processing (container: '" + container + "'): " + curr.getName());

			final BlobClient client = contClient.getBlobClient(curr.getName());
			
			if (client.exists())
				client.deleteIfExists();
			
			client.uploadFromFile(curr.getAbsolutePath());
		}		
	}

}
