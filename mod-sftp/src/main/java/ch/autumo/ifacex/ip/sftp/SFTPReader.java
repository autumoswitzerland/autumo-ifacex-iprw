package ch.autumo.ifacex.ip.sftp;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

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
import net.schmizz.sshj.sftp.RemoteResourceInfo;


/**
 * SFTP file in - reader prefix 'sftp_file_in'.
 * 
 * Reads files from a SFTP server and folders (directories) as entities 
 * and creates/overwrites them in the directory specified by 'sftp_file_in_temp_out_path';
 * you have to delete them yourself if necessary and if they are not deleted by a 
 * file writer for example.
 */
public class SFTPReader extends AbstractSFTP implements Reader {

	private final static Logger LOG = LoggerFactory.getLogger(SFTPReader.class.getName());
	
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
		
		List<RemoteResourceInfo> infos = null;
		try {
			client().cd(directory);
			infos =  client().ls();
		} catch (IOException e) {
			throw new IfaceXException("Couldn't list files in remote directory '"+directory+"'!", e);
		}		
		
		// One batch per (entity) directory path
		currBatch = new BatchData(config);
		
		FILES: for (RemoteResourceInfo remoteResourceInfo: infos) {
			 
			if (remoteResourceInfo.isDirectory())
				continue FILES;
			if (!remoteResourceInfo.isRegularFile())
				continue FILES;
			
			LOG.info("Processing (entity: '"+directory+"'): " + remoteResourceInfo.getName());
			
			final String fileOutPath = tempOutputPath + remoteResourceInfo.getName();
			FileOutputStream fos = null;
			try {
				
				client().get(remoteResourceInfo.getName(), fileOutPath);
				
			} catch (Exception e) {
				throw new IfaceXException("Couldn't retrieve file '"+remoteResourceInfo.getName()+"' from remote directory '"+directory+"'!", e);
			} finally {
				try {
					if (fos != null)
						fos.close();
				} catch (IOException e) {
				}
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
