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

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.sardine.DavResource;

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
 * WebDAV file in - reader prefix 'webdav_file_in'.
 * 
 * Reads files from a web-dav server and folder URI paths (directories) as entities 
 * and creates/overwrites them in the directory specified by 'webdav_file_in_temp_out_path';
 * you have to delete them yourself if necessary and if they are not deleted by a 
 * file writer for example.
 */
public class WebDAVReader extends AbstractWebDAV implements Reader {

	private final static Logger LOG = LoggerFactory.getLogger(WebDAVReader.class.getName());
	
	private String tempOutputPath = null;
	private String entityUrl = null;

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
		
		String entityStr = entity.getEntity();
		entityStr = entityStr.replace(" ", "%20");
		
		if (!entityStr.startsWith("/"))
			entityStr = "/" + entityStr;
		
		this.entityUrl = baseUrl() + entityStr;
		if (!this.entityUrl.endsWith("/"))
			this.entityUrl += "/";
	}

	@Override
	public void read(String readerName, BatchProcessor batchProcessor, IPC config, SourceEntity entity,
			boolean hasMoreEntities) throws IfaceXException {
		
		// No matter what is defined, we use standard fields for files here
		entity.overwriteSourceFields(SourceEntity.FILES_SOURCE_FIELDS);
		
		List<DavResource> resources;
		try {
			resources = webDAV().list(entityUrl, 1); // -> 1: directory listing with parent resource/folder
		} catch (IOException e) {
			throw new IfaceXException("Couldn't read file list from '"+this.entityUrl+"'!", e);
		}
		
		// One directory listing = 1 batch, no sizes here
		currBatch = new BatchData(config);
		
		FILES: for (DavResource res : resources) {
			
			if (res.isDirectory())
				continue FILES;
			
			FileOutputStream fos = null;
			try {
				
				LOG.info("Processing (entity: '"+entity.getEntity()+"'): " + res.getPath());
				
				String name = res.getName();
				name = URLEncoder.encode(name, "UTF-8").replace("+", "%20");
				String fullUrlPath = this.entityUrl + name;
				
				final String fileOutPath = tempOutputPath + res.getName();
				fos = new FileOutputStream(fileOutPath);
				IOUtils.copy(webDAV().get(fullUrlPath), fos);
				
				final String values[] = new String[] {fileOutPath};
				if (exFilter == null)
					currBatch.addRecordValues(values);
				else if (exFilter.addRecord(SourceEntity.FILES_SOURCE_FIELDS, values))
					currBatch.addRecordValues(values);			
				
			} catch (IOException e) {
				throw new IfaceXException("Couldn't store file to '"+res.getPath()+"'!", e);
			} finally {
				try {
					if (fos != null)
						fos.close();
				} catch (IOException e) {
				}
			}
		}
		
		// In this case, more entities = more data
		batchProcessor.processBatchData(currBatch, entity, hasMoreEntities);
	}

}
