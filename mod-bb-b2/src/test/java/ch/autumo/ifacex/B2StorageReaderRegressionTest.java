/**
 * Copyright 2026 autumo GmbH, Michael Gasche.
 * All Rights Reserved.
 */
package ch.autumo.ifacex;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ch.autumo.ifacex.batch.BatchProcessor;
import ch.autumo.ifacex.ip.backblaze.B2StorageReader;
import ch.autumo.ifacex.utils.Helper;

/**
 * Functional regression test for the Backblaze B2 reader using a mocked
 * B2StorageClient (no network, no credentials).
 */
class B2StorageReaderRegressionTest {

    private Path tempOut;
    private Path ipcFile;

    @BeforeEach
    void setUp() throws Exception {
        ch.autumo.ifacex.InterfaceFactory.initialize("./", ch.autumo.ifacex.App.MODE_STANDALONE, 0);

        tempOut = Files.createTempDirectory("b2-out");

        final String ipc = "formatVersion: 1\n\n"
                + "general: {}\n\n"
                + "sourceInterface: b2_file_in\n"
                + "destinationInterfaces: [file_out]\n"
                + "processSourceEntities: [mybucket]\n\n"
                + "sources:\n"
                + "  - name: b2_file_in\n"
                + "    params:\n"
                + "      api_key_id: keyid\n"
                + "      api_key: appkey\n"
                + "      temp_out_path: " + tempOut.toString() + "\n"
                + "    entities:\n"
                + "      mybucket: {}\n";
        ipcFile = Files.createTempFile("b2r-test", ".ifcx");
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
    void readerHandlesEmptyBucket() throws Exception {
        final Properties procProps = Helper.loadConfiguration(ipcFile.toString());
        final Properties generalProps = Helper.loadConfiguration("cfg/ifacex.cfg");
        final Configuration config = new Configuration(null, generalProps, procProps, ipcFile.toString());

        final com.backblaze.b2.client.B2StorageClient client = mock(com.backblaze.b2.client.B2StorageClient.class);
        final com.backblaze.b2.client.structures.B2Bucket bucket = mock(com.backblaze.b2.client.structures.B2Bucket.class);
        when(client.getBucketOrNullByName("mybucket")).thenReturn(bucket);
        when(bucket.getBucketId()).thenReturn("bucketid");
        when(bucket.getBucketName()).thenReturn("mybucket");
        final com.backblaze.b2.client.B2ListFilesIterable iterable = mock(com.backblaze.b2.client.B2ListFilesIterable.class);
        when(iterable.iterator()).thenReturn(java.util.Collections.emptyIterator());
        when(client.fileVersions(org.mockito.ArgumentMatchers.any(com.backblaze.b2.client.structures.B2ListFileVersionsRequest.class)))
                .thenReturn(iterable);

        final B2StorageReader reader = new B2StorageReader();
        reader.setClient(client);
        reader.initialize("b2_file_in", config, null);

        final SourceEntity entity = new SourceEntity(config, "mybucket", null);
        reader.initializeEntity("b2_file_in", config, entity);

        final BatchProcessor batchProcessor = mock(BatchProcessor.class);
        assertDoesNotThrow(() -> reader.read("b2_file_in", batchProcessor, config, entity, false));
    }
}
