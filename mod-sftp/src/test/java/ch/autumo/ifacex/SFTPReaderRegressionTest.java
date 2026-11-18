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

import ch.autumo.ifacex.batch.BatchProcessor;
import ch.autumo.ifacex.ip.sftp.SFTPReader;
import ch.autumo.ifacex.utils.Helper;
import net.schmizz.sshj.sftp.StatefulSFTPClient;

/**
 * Functional regression test for the SFTP reader using a mocked SFTP client.
 */
class SFTPReaderRegressionTest {

    private Path tempOut;
    private Path ipcFile;

    @BeforeEach
    void setUp() throws Exception {
        ch.autumo.ifacex.InterfaceFactory.initialize("./", ch.autumo.ifacex.App.MODE_STANDALONE, 0);

        tempOut = Files.createTempDirectory("sftp-out");

        final String ipc = "formatVersion: 1\n\n"
                + "general: {}\n\n"
                + "sourceInterface: sftp_file_in\n"
                + "destinationInterfaces: [file_out]\n"
                + "processSourceEntities: [upload]\n\n"
                + "sources:\n"
                + "  - name: sftp_file_in\n"
                + "    params:\n"
                + "      host: localhost\n"
                + "      port: \"22\"\n"
                + "      user: autumo\n"
                + "      password: autumo\n"
                + "      known_hosts: \"\"\n"
                + "      temp_out_path: " + tempOut.toString() + "\n"
                + "    entities:\n"
                + "      upload: {}\n";
        ipcFile = Files.createTempFile("sftr-test", ".ifcx");
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

        final StatefulSFTPClient client = Mockito.mock(StatefulSFTPClient.class);
        when(client.ls()).thenReturn(java.util.Collections.emptyList());

        final SFTPReader reader = new SFTPReader();
        reader.setClient(client);
        reader.initialize("sftp_file_in", config, null);

        final SourceEntity entity = new SourceEntity(config, "upload", null);
        reader.initializeEntity("sftp_file_in", config, entity);

        final BatchProcessor batchProcessor = Mockito.mock(BatchProcessor.class);
        assertDoesNotThrow(() -> reader.read("sftp_file_in", batchProcessor, config, entity, false));
    }
}
