/**
 * Copyright 2026 autumo GmbH, Michael Gasche.
 * All Rights Reserved.
 */
package ch.autumo.ifacex;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import com.google.api.gax.paging.Page;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;

import ch.autumo.ifacex.batch.BatchProcessor;
import ch.autumo.ifacex.ip.google.GoogleStorageReader;
import ch.autumo.ifacex.utils.Helper;

/**
 * Functional regression test for the Google Cloud Storage reader using a mocked
 * Storage client (no network, no credentials).
 */
class GoogleStorageReaderRegressionTest {

    private Path tempOut;
    private Path ipcFile;

    @BeforeEach
    void setUp() throws Exception {
        ch.autumo.ifacex.InterfaceFactory.initialize("./", ch.autumo.ifacex.App.MODE_STANDALONE, 0);

        tempOut = Files.createTempDirectory("gcs-out");

        final String ipc = "formatVersion: 1\n\n"
                + "general: {}\n\n"
                + "sourceInterface: gstorage_file_in\n"
                + "destinationInterfaces: [file_out]\n"
                + "processSourceEntities: [mybucket]\n\n"
                + "sources:\n"
                + "  - name: gstorage_file_in\n"
                + "    params:\n"
                + "      credentials_file: /tmp/creds.json\n"
                + "      project_id: test-project\n"
                + "      temp_out_path: " + tempOut.toString() + "\n"
                + "    entities:\n"
                + "      mybucket: {}\n";
        ipcFile = Files.createTempFile("gcsr-test", ".ifcx");
        Files.writeString(ipcFile, ipc);
    }

    @AfterEach
    void tearDown() throws Exception {
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
    @SuppressWarnings({"unchecked", "rawtypes"})
    void readerDownloadsFileFromMockStorage() throws Exception {
        final Properties procProps = Helper.loadConfiguration(ipcFile.toString());
        final Properties generalProps = Helper.loadConfiguration("cfg/ifacex.cfg");
        final Configuration config = new Configuration(null, generalProps, procProps, ipcFile.toString());

        // mock Storage + Page + Blob
        final Storage storage = mock(Storage.class);
        final Page page = mock(Page.class);
        final Blob blob = mock(Blob.class);
        when(blob.getName()).thenReturn("foobar.txt");
        doAnswer((InvocationOnMock inv) -> {
            try (java.io.OutputStream os = (java.io.OutputStream) inv.getArgument(0)) {
				os.write("hello gcs".getBytes());
				os.flush();
			}
            return null;
        }).when(blob).downloadTo(any(java.io.OutputStream.class), any(com.google.cloud.storage.Blob.BlobSourceOption[].class));

        when(page.iterateAll()).thenReturn(java.util.Collections.singleton(blob));
        when(page.hasNextPage()).thenReturn(false);
        when(storage.list(org.mockito.ArgumentMatchers.eq("mybucket"), any(com.google.cloud.storage.Storage.BlobListOption[].class)))
                .thenReturn(page);

        final GoogleStorageReader reader = new GoogleStorageReader();
        reader.setStorage(storage);
        reader.initialize("gstorage_file_in", config, null);

        final SourceEntity entity = new SourceEntity(config, "mybucket", null);
        reader.initializeEntity("gstorage_file_in", config, entity);

        final BatchProcessor batchProcessor = mock(BatchProcessor.class);
        reader.read("gstorage_file_in", batchProcessor, config, entity, false);

        assertTrue(Files.exists(tempOut.resolve("foobar.txt")),
                "Downloaded file should exist in temp output path");
    }
}
