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

import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.net.ftp.FTPFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.autumo.commons.utils.OSUtils;
import ch.autumo.ifacex.ExclusionFilter;
import ch.autumo.ifacex.IPC;
import ch.autumo.ifacex.IfaceXException;
import ch.autumo.ifacex.Processor;
import ch.autumo.ifacex.SourceEntity;
import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.batch.BatchProcessor;
import ch.autumo.ifacex.reader.Reader;
import ch.autumo.ifacex.reader.ReaderException;


/**
 * FTP file in - reader prefix 'ftp_file_in'.
 * 
 * Reads files from a FTP server and folders (directories) as entities 
 * and creates/overwrites them in the directory specified by 'ftp_file_in_temp_out_path';
 * you have to delete them yourself if necessary and if they are not deleted by a 
 * file writer for example.
 */
public class FTPReader extends AbstractFTP implements Reader {

	private final static Logger LOG = LoggerFactory.getLogger(FTPReader.class.getName());
	
	private String tempOutputPath = null;
	
	private ExclusionFilter exFilter = null;
	
	private BatchData currBatch = null;
	
	
	@Override
	public void initialize(String readerName, IPC config, Processor processor) throws IfaceXException {
		
		tempOutputPath = config.getReaderConfig().getConfig("_temp_out_path");
		if (!tempOutputPath.endsWith(OSUtils.FILE_SEPARATOR)) 
			tempOutputPath += OSUtils.FILE_SEPARATOR;
		
		super.initialize(readerName, config, processor);
		
		exFilter = config.getReaderConfig().getExclusionFilter(SourceEntity.WILDCARD_SOURCE_ENTITY);
	}	
	
	@Override
	public void initializeEntity(String readerName, IPC config, SourceEntity entity)
			throws ReaderException, IfaceXException {
	}

	@Override
	public void read(String readerName, BatchProcessor batchProcessor, IPC config, SourceEntity entity,
			boolean hasMoreEntities) throws IfaceXException {

		// No matter what is defined, we use standard fields for files here
		entity.overwriteSourceFields(SourceEntity.FILES_SOURCE_FIELDS);
		
		final String directory = entity.getEntity().trim();

		FTPFile files[] = null;
		try {
			 files = client().listFiles(directory);
		} catch (IOException e) {
			throw new IfaceXException("Couldn't list files in remote directory '"+directory+"'!", e);
		}
		
		// One batch per (entity) directory path
		currBatch = new BatchData(config);
		
		FILES: for (int i = 0; i < files.length; i++) {
			
			final FTPFile remoteFile = files[i];
			if (remoteFile.isDirectory())
				continue FILES;
			if (!remoteFile.isFile())
				continue FILES;
			
			LOG.info("Processing (entity: '"+directory+"'): " + remoteFile.getName());
			
			final String fileOutPath = tempOutputPath + remoteFile.getName();
			try {
				
				final FileOutputStream fos = new FileOutputStream(fileOutPath);
				client().retrieveFile(remoteFile.getName(), fos);
				fos.close();
				
			} catch (Exception e) {
				throw new IfaceXException("Couldn't retriev file '"+remoteFile.getName()+"' from remote directory '"+directory+"'!", e);
			}
			
			final String values[] = new String[] {fileOutPath};
			if (exFilter == null)
				currBatch.addRecordValues(values);
			else if (exFilter.addRecord(SourceEntity.FILES_SOURCE_FIELDS, values))
				currBatch.addRecordValues(values);			
		}
		
		// In this case, more entities = more data
		batchProcessor.processBatchData(currBatch, entity, hasMoreEntities);
	}

}
