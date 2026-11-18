/**
 * Copyright 2026 autumo GmbH, Michael Gasche.
 * All Rights Reserved.
 *
 * Integration test for the FTP reader/writer against a real FTP server.
 * Skipped unless the FTP_TEST_* environment variables are set.
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
import ch.autumo.ifacex.ip.ftp.FTPReader;
import ch.autumo.ifacex.utils.Helper;

@Tag("integration")
class FTPIntegrationTest {

    private Path tempOut;
    private Path srcFile;
    private Path ipcFile;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(System.getenv("FTP_TEST_HOST") != null, "FTP_TEST_HOST not set");
        InterfaceFactory.initialize("./", App.MODE_STANDALONE, 0);
        tempOut = Files.createTempDirectory("ftp-it-out");
        srcFile = Files.createTempFile("src", ".txt");
        Files.writeString(srcFile, "integration test upload");
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
        if (srcFile != null) Files.deleteIfExists(srcFile);
        if (ipcFile != null) Files.deleteIfExists(ipcFile);
    }

    @Test
    void readerConnectsAndLists() throws Exception {
        String host = System.getenv("FTP_TEST_HOST");
        String port = System.getenv().getOrDefault("FTP_TEST_PORT", "21");
        String user = System.getenv("FTP_TEST_USER");
        String pass = System.getenv("FTP_TEST_PASSWORD");
        String dir = System.getenv().getOrDefault("FTP_TEST_DIR", "/");
        String tempOutPath = tempOut.toString();

        String ipc = "formatVersion: 1\n\n"
                + "general: {}\n\n"
                + "sourceInterface: ftp_file_in\n"
                + "destinationInterfaces: [file_out]\n"
                + "processSourceEntities: [" + dir + "]\n\n"
                + "sources:\n"
                + "  - name: ftp_file_in\n"
                + "    params:\n"
                + "      host: " + host + "\n"
                + "      port: \"" + port + "\"\n"
                + "      user: " + user + "\n"
                + "      password: " + pass + "\n"
                + "      ssl: \"no\"\n"
                + "      temp_out_path: " + tempOutPath + "\n"
                + "    entities:\n"
                + "      \"" + dir + "\": {}\n";
        ipcFile = Files.createTempFile("ftp-it", ".ifcx");
        Files.writeString(ipcFile, ipc);

        Properties procProps = Helper.loadConfiguration(ipcFile.toString());
        Properties generalProps = Helper.loadConfiguration("cfg/ifacex.cfg");
        Configuration config = new Configuration(null, generalProps, procProps, ipcFile.toString());

        FTPReader reader = new FTPReader();
        reader.initialize("ftp_file_in", config, null);
        SourceEntity entity = new SourceEntity(config, dir, null);
        reader.initializeEntity("ftp_file_in", config, entity);
        BatchProcessor bp = Mockito.mock(BatchProcessor.class);
        reader.read("ftp_file_in", bp, config, entity, false);
    }
}
