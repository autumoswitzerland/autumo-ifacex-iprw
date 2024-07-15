package ch.autumo.ifacex.ip.microsoft;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.PagedResponse;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.ListBlobsOptions;

import ch.autumo.commons.utils.system.OSUtils;
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
 * Azure Blob file in - writer prefix 'azure_file_in'.
 * 
 * Reads files from Azure Blob Storage containers as entities and creates them 
 * in the directory specified by 'azure_file_in_temp_out_path'; you have to delete
 * them yourself if necessary and if they are not deleted by a file writer for example.
 * 
 */
public class AzureBlobStorageReader extends AbstractAzureBlobStorage implements Reader {

	private final static Logger LOG = LoggerFactory.getLogger(AzureBlobStorageReader.class.getName());
	
	private String tempOutputPath = null;
	
	private ExclusionFilter exFilter = null;
	
	private int batchSize = 0;
	private BatchData currBatch = null;	

	private BlobContainerClient contClient = null;
	
	
	@Override
	public void initialize(String readerName, IPC config, Processor processor) throws IfaceXException {
		
		tempOutputPath = config.getReaderConfig().getConfig("_temp_out_path");
		if (!tempOutputPath.endsWith(OSUtils.FILE_SEPARATOR)) 
			tempOutputPath += OSUtils.FILE_SEPARATOR;
		
		super.initialize(readerName, config, processor);
		
		batchSize = config.getReaderConfig().getFetchSize(SourceEntity.WILDCARD_SOURCE_ENTITY);
		exFilter = config.getReaderConfig().getExclusionFilter(SourceEntity.WILDCARD_SOURCE_ENTITY);
	}
	
	@Override
	public void initializeEntity(String readerName, IPC config, SourceEntity entity)
			throws ReaderException, IfaceXException {

		contClient = client().getBlobContainerClient(entity.getEntity());
	}

	@Override
	public void read(String readerName, BatchProcessor batchProcessor, IPC config, SourceEntity entity,
			boolean hasMoreEntities) throws IfaceXException {
		
		// No matter what is defined, we use standard fields for files here
		entity.overwriteSourceFields(SourceEntity.FILES_SOURCE_FIELDS);
		
		int batchFileCounter = 0;
		
		final ListBlobsOptions options = new ListBlobsOptions();
		// Overall max. results per batch, so a page should be equal to this size,
		// but it is not guaranteed!
		options.setMaxResultsPerPage(batchSize);
		
		// List all blobs of the entity container
		final PagedIterable<BlobItem> blobs = contClient.listBlobs(options, Duration.ofMillis(CONNECTION_TIMEOUT));
		String continueToken = null;
		
		// First page
		Iterator<PagedResponse<BlobItem>> pages = blobs.iterableByPage(batchSize).iterator();
	
		// Necessary additional loop, if page sizes are different from batch sizes!
		LOOP: while (true) {
		
			// Go through pages
			while (pages.hasNext()) {
	
				// The page
				final PagedResponse<BlobItem> page = pages.next();
	
				// Save continue token every time a page of blob-items is processed.
				continueToken = page.getContinuationToken();
				
				// Iterate the blobs
				for (BlobItem blob: page.getElements()) {
			
					if (batchFileCounter == 0)
						currBatch = new BatchData(config);
					
					final String fileName = blob.getName();
					
					LOG.info("Processing (conatiner: '" + entity.getEntity() + "'): " + fileName);
					
					final BlobClient client = contClient.getBlobClient(fileName);
					FileOutputStream fos = null;
					try {
						
						fos = new FileOutputStream(tempOutputPath + fileName);
						client.downloadStream(fos);
						
					} catch (IOException e) {
						throw new IfaceXException("Couldn't create file '" + fileName + "'!", e);
					} finally {
						try {
							fos.close();
						} catch (IOException e) {
						}
					}	
					
					final String values[] = new String [] {
							tempOutputPath + fileName
						};
					
					if (exFilter == null)
						currBatch.addRecordValues(values);
					else if (exFilter.addRecord(SourceEntity.FILES_SOURCE_FIELDS, values))
						currBatch.addRecordValues(values);
					
					batchFileCounter++;
					
					// More data?
					final boolean moreData = continueToken != null || hasMoreEntities;
					if (batchFileCounter == batchSize || !moreData) {
						// process batch
						batchProcessor.processBatchData(currBatch, entity, moreData);
						batchFileCounter = 0;
					}					
				}
			}
	
			// At this point we may be finished, if the batch size was
			// equal to the page size, but this might not have been the case,
			// so we might have more pages waiting!
			
			// All data for this entity, break additional LOOP
			if (continueToken == null)
				break LOOP;
			else
				// We have more pages, so go on!
				pages = blobs.iterableByPage(continueToken, batchSize).iterator();
		}
	}

}
