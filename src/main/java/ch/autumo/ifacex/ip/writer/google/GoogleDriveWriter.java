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
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.client.http.AbstractInputStreamContent;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

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
 * It updates or creates new files, by determining existing matching
 * folder names and file names!
 */
public class GoogleDriveWriter extends AbstractGoogleDrive implements Writer {

	private final static Logger LOG = LoggerFactory.getLogger(GoogleDriveWriter.class.getName());
	
	private String folder = null;
	private String folderId = null;
	private String folderIdParent = null;
	
	@Override
	public void initialize(String writerName, IPC config, Processor processor) throws IfaceXException {

		super.initialize(writerName, config, processor);
		
		folder = config.getWriterConfig(writerName).getConfig("_folder");
		folderIdParent = config.getWriterConfig(writerName).getConfig("_folder_id_parent");
		
		if (folder != null && folder.trim().length() > 0) {
	        
			try {
				
				final FileList existingFolders = service().files().list().setQ( "mimeType='application/vnd.google-apps.folder' and name='"+folder+"' and trashed=false").execute();
				
				FIND: for (Iterator<File> iterator = existingFolders.getFiles().iterator(); iterator.hasNext();) {
					
					final File curr = iterator.next();
					
					if (folderIdParent != null && folderIdParent.length() > 0) {
						
						final List<String> parents = curr.getParents();
						
						for (Iterator<String> iterator2 = parents.iterator(); iterator2.hasNext();) {
							final String pid = iterator2.next();
							if (pid.equals(folderIdParent) && curr.getName().equals(folder)) {
								folderId = curr.getId();
								break FIND;
							}
						}
						
					} else {
						
						if (curr.getName().equals(folder)) {
							// First match!
							folderId = curr.getId();
							break FIND;
						}
					}
				}
				
				if (folderId == null) { // still not found
				
					final File fileMetadata = new File();
			        fileMetadata.setName(folder);
			        fileMetadata.setMimeType("application/vnd.google-apps.folder");
					
					if (folderIdParent != null && folderIdParent.length() > 0) {
			            final List<String> parents = Arrays.asList(folderIdParent);
			            fileMetadata.setParents(parents);
			        }
					final File fld = service().files().create(fileMetadata).setFields("id,name").execute();
					folderId = fld.getId();
				}
			
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
	        	
	        	FileList existingFiles = null;
	        	if (fid != null)
	        		existingFiles = service().files().list().setQ( "parents in '"+fid+"' and name='"+curr.getName()+"' and trashed=false").execute();
	        	else
	        		existingFiles = service().files().list().setQ( "name='"+curr.getName()+"' and trashed=false").execute();
	        	
	        	final List<File> files = existingFiles.getFiles();
	        	if (files.size() > 0) {
	        		// UPDATE
	        		fileMetadata.setParents(null); // if we have a specific file id, it is not allowed to have a parent set!
	        		file = service().files().update( files.get(0).getId(), fileMetadata, uploadStreamContent).setFields("id,name").execute();
	        	} else {
	        		// CREATE
	        		file = service().files().create( fileMetadata, uploadStreamContent).setFields("id,name").execute();
	        	}
	        	
				// Further fields, e.g., "id,name,webContentLink,webViewLink,parents"
	        	
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
