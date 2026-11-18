/**
 * Copyright 2026 autumo GmbH, Michael Gasche.
 * All Rights Reserved.
 */
package ch.autumo.ifacex;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;

import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.ip.google.GoogleStorageWriter;
import ch.autumo.ifacex.utils.Helper;

/**
 * Functional regression test for the Google Cloud Storage writer using a mocked
 * Storage client (no network, no credentials).
 */
class GoogleStorageWriterRegressionTest {

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
                + "destinationInterfaces: [gstorage_file_out]\n"
                + "processSourceEntities: [\"NULL\"]\n\n"
                + "sources:\n"
                + "  - name: null_in\n"
                + "    entities:\n"
                + "      \"NULL\": {}\n\n"
                + "destinations:\n"
                + "  - name: gstorage_file_out\n"
                + "    params:\n"
                + "      credentials_file: /tmp/creds.json\n"
                + "      project_id: test-project\n"
                + "      bucket_name: testbucket2\n"
                + "      storage_class: STANDARD\n"
                + "      region: US\n";
        ipcFile = Files.createTempFile("gcs-w-test", ".ifcx");
        Files.writeString(ipcFile, ipc);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (ipcFile != null) Files.deleteIfExists(ipcFile);
        if (srcFile != null) Files.deleteIfExists(srcFile);
    }

    @Test
    void writerUploadsFileToMockStorage() throws Exception {
        final Properties procProps = Helper.loadConfiguration(ipcFile.toString());
        final Properties generalProps = Helper.loadConfiguration("cfg/ifacex.cfg");
        final Configuration config = new Configuration(null, generalProps, procProps, ipcFile.toString());

        final Storage storage = mock(Storage.class);
        final Bucket bucket = mock(Bucket.class);
        when(storage.get(org.mockito.ArgumentMatchers.anyString(), any(com.google.cloud.storage.Storage.BucketGetOption[].class)))
                .thenReturn(bucket);
        when(bucket.exists()).thenReturn(false);
        when(storage.create(
                any(com.google.cloud.storage.BucketInfo.class),
                any(com.google.cloud.storage.Storage.BucketTargetOption[].class)))
                .thenReturn(bucket);
        when(bucket.get(any(String.class))).thenReturn(null);
        when(storage.createFrom(
                any(com.google.cloud.storage.BlobInfo.class),
                any(java.nio.file.Path.class),
                any(com.google.cloud.storage.Storage.BlobWriteOption[].class)))
                .thenReturn(mock(Blob.class));

        final GoogleStorageWriter writer = new GoogleStorageWriter();
        writer.setStorage(storage);
        writer.initialize("gstorage_file_out", config, null);

        final SourceEntity entity = new SourceEntity(config, "NULL", null);
        writer.initializeEntity("gstorage_file_out", config, entity);

        final BatchData batch = new BatchData(config);
        batch.addRecordValues(new String[] { srcFile.toString() });
        writer.writeBatchData("gstorage_file_out", config, batch, entity);

        // verify createFrom was invoked (file uploaded)
        org.mockito.Mockito.verify(storage).createFrom(
                any(com.google.cloud.storage.BlobInfo.class),
                any(java.nio.file.Path.class),
                any(com.google.cloud.storage.Storage.BlobWriteOption[].class));
    }
}
