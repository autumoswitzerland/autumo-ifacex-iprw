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

import com.dropbox.core.v2.files.DbxUserFilesRequests;
import com.dropbox.core.v2.files.ListFolderResult;

import ch.autumo.ifacex.batch.BatchProcessor;
import ch.autumo.ifacex.ip.dropbox.DropboxReader;
import ch.autumo.ifacex.utils.Helper;

/**
 * Functional regression test for the Dropbox reader using a mocked DbxClientV2.
 */
class DropboxReaderRegressionTest {

    private Path tempOut;
    private Path ipcFile;

    @BeforeEach
    void setUp() throws Exception {
        ch.autumo.ifacex.InterfaceFactory.initialize("./", ch.autumo.ifacex.App.MODE_STANDALONE, 0);

        tempOut = Files.createTempDirectory("dbox-out");

        final String ipc = "formatVersion: 1\n\n"
                + "general: {}\n\n"
                + "sourceInterface: dbox_file_in\n"
                + "destinationInterfaces: [file_out]\n"
                + "processSourceEntities: [/]\n\n"
                + "sources:\n"
                + "  - name: dbox_file_in\n"
                + "    params:\n"
                + "      oauth_access_token: dummy-token\n"
                + "      app_name: ifaceX\n"
                + "      app_version: 1.0\n"
                + "      path: /\n"
                + "      temp_out_path: " + tempOut.toString() + "\n"
                + "    entities:\n"
                + "      \"/\": {}\n";
        ipcFile = Files.createTempFile("dboxr-test", ".ifcx");
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
    void readerHandlesEmptyFolder() throws Exception {
        final Properties procProps = Helper.loadConfiguration(ipcFile.toString());
        final Properties generalProps = Helper.loadConfiguration("cfg/ifacex.cfg");
        final Configuration config = new Configuration(null, generalProps, procProps, ipcFile.toString());

        final com.dropbox.core.v2.DbxClientV2 client = Mockito.mock(com.dropbox.core.v2.DbxClientV2.class);
        final DbxUserFilesRequests files = Mockito.mock(DbxUserFilesRequests.class);
        final ListFolderResult result = Mockito.mock(ListFolderResult.class);
        when(client.files()).thenReturn(files);
        when(files.listFolder("/")).thenReturn(result);
        when(result.getEntries()).thenReturn(java.util.Collections.emptyList());
        when(result.getCursor()).thenReturn("cursor");
        when(result.getHasMore()).thenReturn(false);

        final DropboxReader reader = new DropboxReader();
        reader.setClient(client);
        reader.initialize("dbox_file_in", config, null);

        final SourceEntity entity = new SourceEntity(config, "/", null);
        reader.initializeEntity("dbox_file_in", config, entity);

        final BatchProcessor batchProcessor = Mockito.mock(BatchProcessor.class);
        assertDoesNotThrow(() -> reader.read("dbox_file_in", batchProcessor, config, entity, false));
    }
}
