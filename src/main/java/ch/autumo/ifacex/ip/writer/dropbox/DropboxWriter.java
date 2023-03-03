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
package ch.autumo.ifacex.ip.writer.dropbox;

import java.io.File;
import java.io.FileInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.files.GetMetadataErrorException;
import com.dropbox.core.v2.files.LookupError;
import com.dropbox.core.v2.files.UploadUploader;

import ch.autumo.ifacex.IPC;
import ch.autumo.ifacex.IfaceXException;
import ch.autumo.ifacex.Processor;
import ch.autumo.ifacex.SourceEntity;
import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.ip.generic.AbstractDropbox;
import ch.autumo.ifacex.writer.Writer;


/**
 * Dropbox file out - writer prefix 'dbox_file_out'.
 * 
 * Writes files to a Dropbox path and per specific Dropbox application.
 * It overwrites files.
 */
public class DropboxWriter extends AbstractDropbox implements Writer {

	private final static Logger LOG = LoggerFactory.getLogger(DropboxWriter.class.getName());
	
	@Override
	public void initialize(String writerName, IPC config, Processor processor) throws IfaceXException {

		super.initialize(writerName, config, processor);
		try {
			client().files().getMetadata(path());
		} catch (GetMetadataErrorException e) {
            if (e.errorValue.isPath()) {
                final LookupError le = e.errorValue.getPathValue();
                if (le.isNotFound()) {
                	LOG.info("Folder '"+path()+"' does not exists, will be created.");                	
                    try {
                    	
                    	// final CreateFolderResult res =
                    	client().files().createFolderV2(path(), false);
                    	/** If we would allow auto-rename, we could check the new name folder 
                    	final String fName = res.getMetadata().getName();
                    	final String parts[] = folder().split("/");
                    	final String lastPart = parts[parts.length -1];
                    	if (!fName.equals(lastPart))
    	                	LOG.warn("Another folder has been chosen during a naming conflict: '" + fName + "'.");                	
                    	 */
                    	
                    } catch (DbxException e1) {
                        throw new IfaceXException("Couldn't create folder '"+path()+"'!", e1);
                    }
                }
            }
		} catch (DbxException e2) {
			throw new IfaceXException("Couldn't access file request API!", e2);
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
		String path = path();
		if (!path.endsWith("/"))
			path += "/";
		while (batch.hasNext()) {
			
			final String vals[] = batch.next();
			final File curr = new File(vals[0]); // absolute file path when reader 'FilePathReader/file_in' is used!
			
			LOG.info("Processing (path: '" + path() + "'): " + curr.getName());
			
			UploadUploader uploader;
			try {
				uploader = client().files().upload(path + curr.getName());
				uploader.uploadAndFinish(new FileInputStream(vals[0]));
			} catch (Exception e) {
				throw new IfaceXException("Couldn't upload file '" + vals[0] + "' to Dropbox path '" + path + "'!", e);
			}
		}		
	}

}
