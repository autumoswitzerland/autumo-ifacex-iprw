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
package ch.autumo.ifacex.ip.writer.google;


import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.model.File;

import ch.autumo.ifacex.IPC;
import ch.autumo.ifacex.IfaceXException;
import ch.autumo.ifacex.Processor;
import ch.autumo.ifacex.SourceEntity;
import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.ip.generic.AbstractGoogleDrive;
import ch.autumo.ifacex.writer.Writer;


/**
 * Google Drive file out - writer prefix 'gdrive_file_out'.
 * 
 * Writes files to a google drive (with or without a folder specified).
 */
public class GoogleDriveWriter extends AbstractGoogleDrive implements Writer {

	private final static Logger LOG = LoggerFactory.getLogger(GoogleDriveWriter.class.getName());
	
	private String folder = null;
	private String folderId = null;
	private String folderIdParent = null;
	
	@Override
	public void initialize(String rwName, IPC config, Processor processor) throws IfaceXException {

		super.initialize(rwName, config, processor);
		
		folder = config.getWriterConfig(rwName).getConfig("_folder");
		folderIdParent = config.getWriterConfig(rwName).getConfig("_folder_id_parent");
		
		if (folder != null && folder.length() > 0) {
			final File fileMetadata = new File();
	        fileMetadata.setName(folder);
	        fileMetadata.setMimeType("application/vnd.google-apps.folder");
			if (folderIdParent != null && folderIdParent.length() > 0) {
	            final List<String> parents = Arrays.asList(folderIdParent);
	            fileMetadata.setParents(parents);
	        }
			
			//TODO: check if folder with same name already exists!
			
			try {
				final File fld = service().files().create(fileMetadata).setFields("id,name").execute();
				folderId = fld.getId();
			} catch (IOException e) {
				throw new IfaceXException("Couldn't create folder '"+folder+"'!", e);
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
		
		while (batch.hasNext()) {
			
			final String vals[] = batch.next();
			final java.io.File curr = new java.io.File(vals[0]); // absolute file path when reader 'FilePathReader/file_in' is used!
			
			final File fileMetadata = new File();
	        fileMetadata.setName(curr.getName());

	        String fid = folderId;
	        if (fid == null && folderIdParent != null && folderIdParent.length() > 0)
	        	fid = folderIdParent;
	        
	        if (fid != null) {
	        	final List<String> parents = Arrays.asList(fid);
	            fileMetadata.setParents(parents);	        	
	        }
	        
	        final AbstractInputStreamContent uploadStreamContent = new FileContent(null, curr);
	        File file = null;
	        try {
				file = service().files().create( fileMetadata, uploadStreamContent).setFields("id,name").execute();
				//file = service().files().create( fileMetadata, uploadStreamContent).setFields("id,name,webContentLink,webViewLink,parents").execute();
			} catch (IOException e) {
				throw new IfaceXException("Couldn't upload file '"+curr.getName()+"'!", e);
			}
	        
	        
	        if (fid == null)
	        	LOG.info("Processed (folder: 'NONE'): " + file.getName() + "["+file.getId()+"]");
	        else {
	        	if (folderId != null)
	        		LOG.info("Processed (folder: '"+folder+"["+folderId+"]'): " + file.getName() + "["+file.getId()+"]");
	        	else
	        		LOG.info("Processed (folder: '"+folderIdParent+"'): " + file.getName() + "["+file.getId()+"]");
	        }
		}		
	}

}
