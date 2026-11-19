/**
 * Copyright 2026 autumo GmbH, Michael Gasche.
 * All Rights Reserved.
 *
 * Integration test for the Dropbox reader against a real Dropbox account.
 * Skipped unless the DROPBOX_TEST_TOKEN environment variable is set.
 * 
 * This test requires DROPBOX_TEST_PATH to point to a folder that contains
 * at least one file (not just subfolders). The test path must be a folder
 * that contains actual files, not just subfolders, because DropboxReader
 * attempts to download each entry as a file.
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
import ch.autumo.ifacex.ip.dropbox.DropboxReader;
import ch.autumo.ifacex.utils.Helper;

@Tag("integration")
class DropboxIntegrationTest {

    private Path tempOut;
    private Path ipcFile;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(System.getenv("DROPBOX_TEST_TOKEN") != null && !System.getenv("DROPBOX_TEST_TOKEN").isBlank(), "DROPBOX_TEST_TOKEN not set");
        assumeTrue(System.getenv("DROPBOX_TEST_PATH") != null && !System.getenv("DROPBOX_TEST_PATH").isBlank(), "DROPBOX_TEST_PATH not set");
        InterfaceFactory.initialize("./", App.MODE_STANDALONE, 0);
        tempOut = Files.createTempDirectory("dbox-it-out");
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
    void readerReadsFromRealDropboxAccount() throws Exception {
        String token = System.getenv("DROPBOX_TEST_TOKEN");
        String path = System.getenv().getOrDefault("DROPBOX_TEST_PATH", "/public/specific-folder-with-files");
        String appName = System.getenv().getOrDefault("DROPBOX_TEST_APP_NAME", "ifaceX");
        String appVersion = System.getenv().getOrDefault("DROPBOX_TEST_APP_VERSION", "1.0");
        String tempOutPath = tempOut.toString();

        String ipc = "formatVersion: 1\n\n"
                + "general: {}\n\n"
                + "sourceInterface: dbox_file_in\n"
                + "destinationInterfaces: [file_out]\n"
                + "processSourceEntities: [\"" + path + "\"]\n\n"
                + "sources:\n"
                + "  - name: dbox_file_in\n"
                + "    params:\n"
                + "      oauth_access_token: " + token + "\n"
                + "      app_name: " + appName + "\n"
                + "      app_version: " + appVersion + "\n"
                + "      path: \"" + path + "\"\n"
                + "      temp_out_path: " + tempOutPath + "\n"
                + "    entities:\n"
                + "      \"" + path + "\": {}\n";
        ipcFile = Files.createTempFile("dbox-it", ".ifcx");
        Files.writeString(ipcFile, ipc);

        Properties procProps = Helper.loadConfiguration(ipcFile.toString());
        Properties generalProps = Helper.loadConfiguration("cfg/ifacex.cfg");
        Configuration config = new Configuration(null, generalProps, procProps, ipcFile.toString());

        DropboxReader reader = new DropboxReader();
        reader.initialize("dbox_file_in", config, null);
        SourceEntity entity = new SourceEntity(config, path, null);
        reader.initializeEntity("dbox_file_in", config, entity);
        BatchProcessor bp = Mockito.mock(BatchProcessor.class);
        reader.read("dbox_file_in", bp, config, entity, false);
    }
}