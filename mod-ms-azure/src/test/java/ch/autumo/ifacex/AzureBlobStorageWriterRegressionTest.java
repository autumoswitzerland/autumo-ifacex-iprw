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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;

import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.ip.microsoft.AzureBlobStorageWriter;
import ch.autumo.ifacex.utils.Helper;

/**
 * Functional regression test for the Azure Blob Storage writer using a mocked
 * BlobServiceClient (no network, no credentials).
 */
class AzureBlobStorageWriterRegressionTest {

    private Path ipcFile;
    private Path srcFile;

    @BeforeEach
    void setUp() throws Exception {
        ch.autumo.ifacex.InterfaceFactory.initialize("./", ch.autumo.ifacex.App.MODE_STANDALONE, 0);

        srcFile = Files.createTempFile("src", ".txt");
        Files.writeString(srcFile, "upload me");

        final String ipc = "formatVersion: 1\n\n"
                + "general: {}\n\n"
                + "sourceInterface: null_in\n"
                + "destinationInterfaces: [azure_file_out]\n"
                + "processSourceEntities: [\"NULL\"]\n\n"
                + "sources:\n"
                + "  - name: null_in\n"
                + "    entities:\n"
                + "      \"NULL\": {}\n\n"
                + "destinations:\n"
                + "  - name: azure_file_out\n"
                + "    params:\n"
                + "      account: testaccount\n"
                + "      connection_string: DefaultEndpointsProtocol=https;AccountName=test;AccountKey=dGVzdA==;EndpointSuffix=core.windows.net\n"
                + "      container: mycontainer\n";
        ipcFile = Files.createTempFile("azw-test", ".ifcx");
        Files.writeString(ipcFile, ipc);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (ipcFile != null) Files.deleteIfExists(ipcFile);
        if (srcFile != null) Files.deleteIfExists(srcFile);
    }

    @Test
    void writerUploadsFileToMockClient() throws Exception {
        final Properties procProps = Helper.loadConfiguration(ipcFile.toString());
        final Properties generalProps = Helper.loadConfiguration("cfg/ifacex.cfg");
        final Configuration config = new Configuration(null, generalProps, procProps, ipcFile.toString());

        final BlobServiceClient client = Mockito.mock(BlobServiceClient.class);
        final BlobContainerClient contClient = Mockito.mock(BlobContainerClient.class);
        final BlobClient blobClient = Mockito.mock(BlobClient.class);
        when(client.getBlobContainerClient("mycontainer")).thenReturn(contClient);
        when(contClient.exists()).thenReturn(true);
        when(contClient.getBlobClient(srcFile.getFileName().toString())).thenReturn(blobClient);
        when(blobClient.exists()).thenReturn(false);

        final AzureBlobStorageWriter writer = new AzureBlobStorageWriter();
        writer.setClient(client);
        writer.initialize("azure_file_out", config, null);

        final SourceEntity entity = new SourceEntity(config, "NULL", null);
        writer.initializeEntity("azure_file_out", config, entity);

        final BatchData batch = new BatchData(config);
        batch.addRecordValues(new String[] { srcFile.toString() });
        assertDoesNotThrow(() -> writer.writeBatchData("azure_file_out", config, batch, entity));

        Mockito.verify(blobClient).uploadFromFile(srcFile.toString());
    }
}
