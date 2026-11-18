/**
 * Copyright 2026 autumo GmbH, Michael Gasche.
 * All Rights Reserved.
 *
 * Integration test for the WebDAV reader against a local WebDAV server
 * (e.g. ownCloud/Nextcloud on localhost:5000). Skipped unless the
 * WEBDAV_TEST_URL environment variable is set.
 */
package ch.autumo.ifacex;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import ch.autumo.ifacex.batch.BatchProcessor;
import ch.autumo.ifacex.ip.webdav.WebDAVReader;
import ch.autumo.ifacex.utils.Helper;

@Tag("integration")
class WebDAVIntegrationTest {

    private Path tempOut;
    private Path ipcFile;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(System.getenv("WEBDAV_TEST_URL") != null && !System.getenv("WEBDAV_TEST_URL").isBlank(), "WEBDAV_TEST_URL not set");
        InterfaceFactory.initialize("./", App.MODE_STANDALONE, 0);
        tempOut = Files.createTempDirectory("webdav-it-out");
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
    }

    @Test
    void readerReadsFromLocalWebDAVServer() throws Exception {
        String url = System.getenv("WEBDAV_TEST_URL");
        String user = System.getenv("WEBDAV_TEST_USER");
        String pass = System.getenv("WEBDAV_TEST_PASSWORD");
        String ssl = System.getenv().getOrDefault("WEBDAV_TEST_SSL", "no");
        String tempOutPath = tempOut.toString();

        assumeTrue(user != null && pass != null, "WEBDAV_TEST_USER/PASSWORD not set");

        String ipc = "formatVersion: 1\n\n"
                + "general: {}\n\n"
                + "sourceInterface: webdav_file_in\n"
                + "destinationInterfaces: [file_out]\n"
                + "processSourceEntities: [upload]\n\n"
                + "sources:\n"
                + "  - name: webdav_file_in\n"
                + "    params:\n"
                + "      url: " + url + "\n"
                + "      user: " + user + "\n"
                + "      password: " + pass + "\n"
                + "      ssl: \"" + ssl + "\"\n"
                + "      temp_out_path: " + tempOutPath + "\n"
                + "    entities:\n"
                + "      upload: {}\n";
        ipcFile = Files.createTempFile("webdav-it", ".ifcx");
        Files.writeString(ipcFile, ipc);

        Properties procProps = Helper.loadConfiguration(ipcFile.toString());
        Properties generalProps = Helper.loadConfiguration("cfg/ifacex.cfg");
        Configuration config = new Configuration(null, generalProps, procProps, ipcFile.toString());

        WebDAVReader reader = new WebDAVReader();
        reader.initialize("webdav_file_in", config, null);
        SourceEntity entity = new SourceEntity(config, "upload", null);
        reader.initializeEntity("webdav_file_in", config, entity);
        BatchProcessor bp = Mockito.mock(BatchProcessor.class);
        reader.read("webdav_file_in", bp, config, entity, false);
    }
}
