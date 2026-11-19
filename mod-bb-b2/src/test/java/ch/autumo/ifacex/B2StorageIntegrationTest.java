/**
 * Copyright 2026 autumo GmbH, Michael Gasche.
 * All Rights Reserved.
 *
 * Integration test for the Backblaze B2 reader and writer against a real
 * Backblaze B2 account. Skipped unless B2_TEST_KEY_ID, B2_TEST_APP_KEY and
 * B2_TEST_BUCKET are set.
 *
 * The application key needs at least the capabilities listFiles, readFiles,
 * writeFiles and deleteFiles. If the bucket does not exist yet, the key also
 * needs listBuckets and writeBuckets, because the writer creates it.
 *
 * Use a dedicated, empty bucket for this test: the reader downloads every
 * file of the bucket, not only the one uploaded by this test.
 */
package ch.autumo.ifacex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.backblaze.b2.client.B2StorageClient;
import com.backblaze.b2.client.B2StorageClientFactory;
import com.backblaze.b2.client.structures.B2FileVersion;

import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.batch.BatchProcessor;
import ch.autumo.ifacex.ip.backblaze.B2StorageReader;
import ch.autumo.ifacex.ip.backblaze.B2StorageWriter;
import ch.autumo.ifacex.utils.Helper;

@Tag("integration")
class B2StorageIntegrationTest {

    private static final String USER_AGENT = "autumo-ifacex-it";

    private String keyId;
    private String appKey;
    private String bucket;

    private B2StorageClient client;
    private Path tempOut;
    private Path ipcFile;
    private String objectName;

    @BeforeEach
    void setUp() throws Exception {
        keyId = System.getenv("B2_TEST_KEY_ID");
        appKey = System.getenv("B2_TEST_APP_KEY");
        bucket = System.getenv("B2_TEST_BUCKET");

        assumeTrue(notBlank(keyId), "B2_TEST_KEY_ID not set");
        assumeTrue(notBlank(appKey), "B2_TEST_APP_KEY not set");
        assumeTrue(notBlank(bucket), "B2_TEST_BUCKET not set");

        InterfaceFactory.initialize("./", App.MODE_STANDALONE, 0);

        client = B2StorageClientFactory
                .createDefaultFactory()
                .create(keyId, appKey, USER_AGENT);

        tempOut = Files.createTempDirectory("b2-it-out");

        final String ipc = "formatVersion: 1\n\n"
                + "general: {}\n\n"
                + "sourceInterface: b2_file_in\n"
                + "destinationInterfaces: [b2_file_out]\n"
                + "processSourceEntities: [\"" + bucket + "\"]\n\n"
                + "sources:\n"
                + "  - name: b2_file_in\n"
                + "    params:\n"
                + "      api_key_id: " + keyId + "\n"
                + "      api_key: " + appKey + "\n"
                + "      temp_out_path: " + tempOut + "\n"
                + "    entities:\n"
                + "      \"" + bucket + "\": {}\n\n"
                + "destinations:\n"
                + "  - name: b2_file_out\n"
                + "    params:\n"
                + "      api_key_id: " + keyId + "\n"
                + "      api_key: " + appKey + "\n"
                + "      bucket: " + bucket + "\n";
        ipcFile = Files.createTempFile("b2-it", ".ifcx");
        Files.writeString(ipcFile, ipc);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            if (objectName != null) {
                try {
                    client.deleteFileVersion(bucket, objectName);
                } catch (Exception ignored) {
                }
            }
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
        if (tempOut != null) {
            try (var s = Files.walk(tempOut)) {
                s.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
        if (ipcFile != null) Files.deleteIfExists(ipcFile);
    }

    @Test
    void writerUploadsAndReaderDownloadsFromB2() throws Exception {
        final String content = "ifacex b2 integration test " + UUID.randomUUID();

        final Path source = Files.createTempFile("b2-it-src", ".txt");
        try {
            Files.writeString(source, content);
            objectName = source.getFileName().toString();

            final Properties procProps = Helper.loadConfiguration(ipcFile.toString());
            final Properties generalProps = Helper.loadConfiguration("cfg/ifacex.cfg");
            final Configuration config = new Configuration(null, generalProps, procProps, ipcFile.toString());

            // writer: upload
            final B2StorageWriter writer = new B2StorageWriter();
            writer.initialize("b2_file_out", config, null);

            final SourceEntity writerEntity = new SourceEntity(config, "NULL", null);
            writer.initializeEntity("b2_file_out", config, writerEntity);

            final BatchData batch = new BatchData(config);
            batch.addRecordValues(new String[] { source.toString() });
            writer.writeBatchData("b2_file_out", config, batch, writerEntity);
            writer.close("b2_file_out");

            final B2FileVersion uploaded = client.getFileInfoByName(bucket, objectName);
            assertTrue(uploaded != null, "Uploaded file should exist in bucket '" + bucket + "'");

            // reader: download
            final B2StorageReader reader = new B2StorageReader();
            reader.initialize("b2_file_in", config, null);

            final SourceEntity readerEntity = new SourceEntity(config, bucket, null);
            reader.initializeEntity("b2_file_in", config, readerEntity);

            final BatchProcessor batchProcessor = Mockito.mock(BatchProcessor.class);
            reader.read("b2_file_in", batchProcessor, config, readerEntity, false);
            reader.close("b2_file_in");

            final Path downloaded = tempOut.resolve(objectName);
            assertTrue(Files.exists(downloaded), "Uploaded file should be downloaded by the reader");
            assertEquals(content, Files.readString(downloaded), "Downloaded content should match uploaded content");
        } finally {
            Files.deleteIfExists(source);
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
