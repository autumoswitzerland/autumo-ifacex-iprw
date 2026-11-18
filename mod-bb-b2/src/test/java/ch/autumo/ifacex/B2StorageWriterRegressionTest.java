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

import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.ip.backblaze.B2StorageWriter;
import ch.autumo.ifacex.utils.Helper;

/**
 * Functional regression test for the Backblaze B2 writer using a mocked
 * B2StorageClient (no network, no credentials).
 */
class B2StorageWriterRegressionTest {

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
                + "destinationInterfaces: [b2_file_out]\n"
                + "processSourceEntities: [\"NULL\"]\n\n"
                + "sources:\n"
                + "  - name: null_in\n"
                + "    entities:\n"
                + "      \"NULL\": {}\n\n"
                + "destinations:\n"
                + "  - name: b2_file_out\n"
                + "    params:\n"
                + "      api_key_id: keyid\n"
                + "      api_key: appkey\n"
                + "      bucket: mybucket\n";
        ipcFile = Files.createTempFile("b2w-test", ".ifcx");
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

        final com.backblaze.b2.client.B2StorageClient client = Mockito.mock(com.backblaze.b2.client.B2StorageClient.class);
        final com.backblaze.b2.client.structures.B2Bucket bucket = Mockito.mock(com.backblaze.b2.client.structures.B2Bucket.class);
        when(client.getBucketOrNullByName("mybucket")).thenReturn(bucket);
        when(bucket.getBucketId()).thenReturn("bucketid");
        when(client.getFileInfoByName("mybucket", srcFile.getFileName().toString()))
                .thenThrow(new com.backblaze.b2.client.exceptions.B2Exception("not found", 404, null, "not_found"));

        final B2StorageWriter writer = new B2StorageWriter();
        writer.setClient(client);
        writer.initialize("b2_file_out", config, null);

        final SourceEntity entity = new SourceEntity(config, "NULL", null);
        writer.initializeEntity("b2_file_out", config, entity);

        final BatchData batch = new BatchData(config);
        batch.addRecordValues(new String[] { srcFile.toString() });
        assertDoesNotThrow(() -> writer.writeBatchData("b2_file_out", config, batch, entity));

        Mockito.verify(client).uploadSmallFile(org.mockito.ArgumentMatchers.any(com.backblaze.b2.client.structures.B2UploadFileRequest.class));
    }
}
