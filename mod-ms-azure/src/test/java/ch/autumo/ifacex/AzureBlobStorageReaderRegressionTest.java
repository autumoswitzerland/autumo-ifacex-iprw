/**
 * Copyright 2026 autumo GmbH, Michael Gasche.
 * All Rights Reserved.
 */
package ch.autumo.ifacex;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.azure.core.http.rest.PagedIterable;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.ListBlobsOptions;

import ch.autumo.ifacex.batch.BatchProcessor;
import ch.autumo.ifacex.ip.microsoft.AzureBlobStorageReader;
import ch.autumo.ifacex.utils.Helper;

/**
 * Functional regression test for the Azure Blob Storage reader using a mocked
 * BlobServiceClient (no network, no credentials).
 */
class AzureBlobStorageReaderRegressionTest {

    private Path tempOut;
    private Path ipcFile;

    @BeforeEach
    void setUp() throws Exception {
        ch.autumo.ifacex.InterfaceFactory.initialize("./", ch.autumo.ifacex.App.MODE_STANDALONE, 0);

        tempOut = Files.createTempDirectory("azure-out");

        final String ipc = "formatVersion: 1\n\n"
                + "general: {}\n\n"
                + "sourceInterface: azure_file_in\n"
                + "destinationInterfaces: [file_out]\n"
                + "processSourceEntities: [mycontainer]\n\n"
                + "sources:\n"
                + "  - name: azure_file_in\n"
                + "    params:\n"
                + "      account: testaccount\n"
                + "      connection_string: DefaultEndpointsProtocol=https;AccountName=test;AccountKey=dGVzdA==;EndpointSuffix=core.windows.net\n"
                + "      temp_out_path: " + tempOut.toString() + "\n"
                + "    entities:\n"
                + "      mycontainer: {}\n";
        ipcFile = Files.createTempFile("azr-test", ".ifcx");
        Files.writeString(ipcFile, ipc);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempOut != null) {
            try (var s = Files.walk(tempOut)) {
                s.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (java.io.IOException ignored) {}
                });
            }
        }
        if (ipcFile != null) Files.deleteIfExists(ipcFile);
    }

    @Test
    @SuppressWarnings("unchecked")
    void readerHandlesEmptyContainer() throws Exception {
        final Properties procProps = Helper.loadConfiguration(ipcFile.toString());
        final Properties generalProps = Helper.loadConfiguration("cfg/ifacex.cfg");
        final Configuration config = new Configuration(null, generalProps, procProps, ipcFile.toString());

        final BlobServiceClient client = Mockito.mock(BlobServiceClient.class);
        final BlobContainerClient contClient = Mockito.mock(BlobContainerClient.class);
        final PagedIterable<?> paged = Mockito.mock(PagedIterable.class);
        when(client.getBlobContainerClient("mycontainer")).thenReturn(contClient);
        when(contClient.listBlobs(org.mockito.ArgumentMatchers.any(ListBlobsOptions.class), org.mockito.ArgumentMatchers.any()))
                .thenReturn((@Nullable PagedIterable<BlobItem>) paged);
        Mockito.doReturn(java.util.Collections.emptyList()).when(paged).iterableByPage(org.mockito.ArgumentMatchers.anyInt());

        final AzureBlobStorageReader reader = new AzureBlobStorageReader();
        reader.setClient(client);
        reader.initialize("azure_file_in", config, null);

        final SourceEntity entity = new SourceEntity(config, "mycontainer", null);
        reader.initializeEntity("azure_file_in", config, entity);

        final BatchProcessor batchProcessor = Mockito.mock(BatchProcessor.class);
        assertDoesNotThrow(() -> reader.read("azure_file_in", batchProcessor, config, entity, false));
    }
}
