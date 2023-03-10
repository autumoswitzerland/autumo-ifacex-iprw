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
import java.util.List;

import org.openstack4j.model.common.DLPayload;
import org.openstack4j.model.storage.object.SwiftObject;
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
 * OpenStack Swift ObjectStorage file in - writer prefix 'os_swift_file_in'.
 * 
 * Reads files from a OpenStack containers as entities and creates them 
 * in the directory specified by 'os_swfit_file_in_temp_out_path'; you have to delete
 * them yourself if necessary and if they are not deleted by a file writer for example.
 * 
 * @WIP - Work in Progress
 */
public class OpenStackSwiftReader extends AbstractOpenStack implements Reader {

	private final static Logger LOG = LoggerFactory.getLogger(OpenStackSwiftReader.class.getName());
	
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
		
		//final ObjectListOptions options = ObjectListOptions.create();
		// options = options.limit(10);
		
		List<? extends SwiftObject> page = client().objectStorage().objects().list(entity.getEntity());
		currBatch = new BatchData(config);
		
		for (SwiftObject o : page) {
			
			if (o.isDirectory())
				continue;
			
			LOG.info("Processing (container: '" + entity.getEntity() + "'): " + o.getName());
			
			String filePath = tempOutputPath + o.getName();
			try {
				
				final DLPayload payload = o.download();
				payload.writeToFile(new File(filePath));
				
			} catch (Exception e) {
				throw new IfaceXException("Cannot store download locally to '" + filePath + "'!", e);
			}
			
			
			final String values[] = new String[] {filePath};
			if (exFilter == null)
				currBatch.addRecordValues(values);
			else if (exFilter.addRecord(SourceEntity.FILES_SOURCE_FIELDS, values))
				currBatch.addRecordValues(values);					
		}
		
		// The batch is the container for an entity
		batchProcessor.processBatchData(currBatch, entity, hasMoreEntities);
	}

}
