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
package ch.autumo.ifacex.ip.dropbox;

import java.io.FileOutputStream;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dropbox.core.DbxDownloader;
import com.dropbox.core.v2.files.DbxUserFilesRequests;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.ListFolderResult;
import com.dropbox.core.v2.files.Metadata;

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
 * Dropbox file in - reader prefix 'dbox_file_in'.
 * 
 * Reads files from a Dropbox path and per specific Dropbox application  
 * and creates them in the directory specified by 'gbox_file_in_temp_out_path';
 * you have to delete them yourself if necessary and if they are not deleted by
 * a file writer for example.
 */
public class DropboxReader extends AbstractDropbox implements Reader {

	private final static Logger LOG = LoggerFactory.getLogger(DropboxReader.class.getName());

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
		
		boolean first = true;		
		ListFolderResult result = null;
		
		// File requests
		final DbxUserFilesRequests request = client().files();
		
		String path = path();
		if (path == null || path.trim().length() == 0)
			path = "/";
		
		LOOP: while (true) {
			
			try {
				if (first) {
					result = request.listFolder(path);
					first = false;
				} else {
		            result = request.listFolderContinue(result.getCursor());
				}
			} catch (Exception e) {
				throw new IfaceXException("Couldn't list remote files from folder '"+path+"'!", e);
			}
			
        	currBatch = new BatchData(config);
			
			// Let Dropbox define our batch size
            for (Metadata metadata : result.getEntries()) {

                DbxDownloader<FileMetadata> downloader = null;
    			try {
    				downloader = client().files().download(metadata.getPathLower());
    			} catch (Exception e) {
    				throw new IfaceXException("Couldn't fetch meta data for remote file '"+metadata.getPathLower() +"'!", e);
				}

                final String filePath = tempOutputPath + metadata.getName();
                
				LOG.info("Processing (path: '" + path + "'): " + metadata.getName());	                
                
				FileOutputStream fos = null;
				try {
					fos = new FileOutputStream(filePath);
	                downloader.download(fos);
	                
				} catch (Exception e) {
					throw new IfaceXException("Couldn't fetch file '"+filePath+"' from Dropbox!", e);
				} finally {
					try {
						if (fos != null)
							fos.close();
					} catch (IOException e) {
					}					
				}
                
				final String values[] = new String[] {filePath};
				if (exFilter == null)
					currBatch.addRecordValues(values);
				else if (exFilter.addRecord(SourceEntity.FILES_SOURCE_FIELDS, values))
					currBatch.addRecordValues(values);
                
            }

            final boolean moreData = result.getHasMore();

            // We only have one pseudo NULL entity, there are no more entities following
			batchProcessor.processBatchData(currBatch, entity, moreData);

            if (!moreData) {
                break LOOP;
            }
        }
	}

}
