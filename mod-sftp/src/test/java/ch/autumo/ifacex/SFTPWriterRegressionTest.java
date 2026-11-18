/**
 * Copyright 2026 autumo GmbH, Michael Gasche.
 * All Rights Reserved.
 */
package ch.autumo.ifacex;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.ip.sftp.SFTPWriter;
import ch.autumo.ifacex.utils.Helper;
import net.schmizz.sshj.sftp.StatefulSFTPClient;

/**
 * Functional regression test for the SFTP writer using a mocked SFTP client.
 */
class SFTPWriterRegressionTest {

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
                + "destinationInterfaces: [sftp_file_out]\n"
                + "processSourceEntities: [\"NULL\"]\n\n"
                + "sources:\n"
                + "  - name: null_in\n"
                + "    entities:\n"
                + "      \"NULL\": {}\n\n"
                + "destinations:\n"
                + "  - name: sftp_file_out\n"
                + "    params:\n"
                + "      host: localhost\n"
                + "      port: \"22\"\n"
                + "      user: autumo\n"
                + "      password: autumo\n"
                + "      known_hosts: \"\"\n";
        ipcFile = Files.createTempFile("sftpw-test", ".ifcx");
        Files.writeString(ipcFile, ipc);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (ipcFile != null) Files.deleteIfExists(ipcFile);
        if (srcFile != null) Files.deleteIfExists(srcFile);
    }

    @Test
    void writerUploadsFileToMockClient() throws Exception {
        final Properties procProps = Helper.loadConfiguration(ipcFile.toString());
        final Properties generalProps = Helper.loadConfiguration("cfg/ifacex.cfg");
        final Configuration config = new Configuration(null, generalProps, procProps, ipcFile.toString());

        final StatefulSFTPClient client = Mockito.mock(StatefulSFTPClient.class);

        final SFTPWriter writer = new SFTPWriter();
        writer.setClient(client);
        writer.initialize("sftp_file_out", config, null);

        final SourceEntity entity = new SourceEntity(config, "NULL", null);
        writer.initializeEntity("sftp_file_out", config, entity);

        final BatchData batch = new BatchData(config);
        batch.addRecordValues(new String[] { srcFile.toString() });
        assertDoesNotThrow(() -> writer.writeBatchData("sftp_file_out", config, batch, entity));

        Mockito.verify(client).put(srcFile.toString(), srcFile.getFileName().toString());
    }
}
