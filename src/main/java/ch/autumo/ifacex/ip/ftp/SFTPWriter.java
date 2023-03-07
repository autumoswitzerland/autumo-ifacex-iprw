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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.autumo.ifacex.IPC;
import ch.autumo.ifacex.IfaceXException;
import ch.autumo.ifacex.Processor;
import ch.autumo.ifacex.SourceEntity;
import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.writer.Writer;


/**
 * SFTP file out - writer prefix 'sftp_file_out'.
 * 
 * Writes files to SFTP server into a specified folder.
 * It overwrites or creates new files, by determining existing matching
 * folder names and file names!
 */
public class SFTPWriter extends AbstractSFTP implements Writer {

	private final static Logger LOG = LoggerFactory.getLogger(SFTPWriter.class.getName());
	
	private String directory = null;

	
	@Override
	public void initialize(String writerName, IPC config, Processor processor) throws IfaceXException {

		super.initialize(writerName, config, processor);
		
		// Absolute or relative
		this.directory = config.getWriterConfig(writerName).getConfig("_directory");
		if (directory != null && directory.length() > 0) {
			directory = directory.trim();
			if (directory.length() == 0)
				directory = null;
		}

		if (directory != null && directory.length() > 0) {
			try {
				
				if (client().statExistence(directory) == null)
					client().mkdirs(directory);
				
			    LOG.info("Remote directory '" + directory + "' created.");
				
			} catch (Exception e) {
				throw new IfaceXException("Couldn't create directory '"+directory+"'!");
			}
			try {
				client().cd(directory);
			} catch (Exception e) {
				throw new IfaceXException("Couldn't change to working directory '"+directory+"'!");
			}
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
		
		String dirName = "/";
		if (directory != null && directory.length() > 0)
			dirName = directory;
		
		while (batch.hasNext()) {
			
			final String vals[] = batch.next();
			final File curr = new File(vals[0]); // absolute file path when reader 'FilePathReader/file_in' is used!
			
			LOG.info("Processing (path: '" + dirName + "'): " + curr.getName());
			
			FileInputStream fis = null;
			try {
				
				fis = new FileInputStream(curr);
				client().put(vals[0], curr.getName());
				
			} catch (Exception e) {
				throw new IfaceXException("Couldn't upload file '"+curr.getName()+"' to working directory '"+dirName+"'!", e);
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
