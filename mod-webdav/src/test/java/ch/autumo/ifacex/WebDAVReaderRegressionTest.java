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

import com.github.sardine.Sardine;

import ch.autumo.ifacex.batch.BatchProcessor;
import ch.autumo.ifacex.ip.webdav.WebDAVReader;
import ch.autumo.ifacex.utils.Helper;

/**
 * Functional regression test for the WebDAV reader using a mocked Sardine client.
 */
class WebDAVReaderRegressionTest {

    private Path tempOut;
    private Path ipcFile;

    @BeforeEach
    void setUp() throws Exception {
        ch.autumo.ifacex.InterfaceFactory.initialize("./", ch.autumo.ifacex.App.MODE_STANDALONE, 0);

        tempOut = Files.createTempDirectory("webdav-out");

        final String ipc = "formatVersion: 1\n\n"
                + "general: {}\n\n"
                + "sourceInterface: webdav_file_in\n"
                + "destinationInterfaces: [file_out]\n"
                + "processSourceEntities: [upload]\n\n"
                + "sources:\n"
                + "  - name: webdav_file_in\n"
                + "    params:\n"
                + "      url: http://localhost:8080/webdav\n"
                + "      user: autumo\n"
                + "      password: autumo\n"
                + "      ssl: \"no\"\n"
                + "      temp_out_path: " + tempOut.toString() + "\n"
                + "    entities:\n"
                + "      upload: {}\n";
        ipcFile = Files.createTempFile("wdr-test", ".ifcx");
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
    void readerHandlesEmptyDirectory() throws Exception {
        final Properties procProps = Helper.loadConfiguration(ipcFile.toString());
        final Properties generalProps = Helper.loadConfiguration("cfg/ifacex.cfg");
        final Configuration config = new Configuration(null, generalProps, procProps, ipcFile.toString());

        final Sardine client = Mockito.mock(Sardine.class);
        when(client.list(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.eq(1)))
                .thenReturn(java.util.Collections.emptyList());

        final WebDAVReader reader = new WebDAVReader();
        reader.setWebdav(client);
        reader.initialize("webdav_file_in", config, null);

        final SourceEntity entity = new SourceEntity(config, "upload", null);
        reader.initializeEntity("webdav_file_in", config, entity);

        final BatchProcessor batchProcessor = Mockito.mock(BatchProcessor.class);
        assertDoesNotThrow(() -> reader.read("webdav_file_in", batchProcessor, config, entity, false));
    }
}
