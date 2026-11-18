/**
 * Copyright 2026 autumo GmbH, Michael Gasche.
 * All Rights Reserved.
 */
package ch.autumo.ifacex;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.DirectoryEntry;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;

import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.ip.ftp.FTPWriter;
import ch.autumo.ifacex.utils.Helper;

/**
 * Functional regression test for the FTP writer against a mock FTP server.
 */
class FTPWriterRegressionTest {

    private FakeFtpServer server;
    private int port;
    private Path ipcFile;
    private Path srcFile;

    @BeforeEach
    void setUp() throws Exception {
        ch.autumo.ifacex.InterfaceFactory.initialize("./", ch.autumo.ifacex.App.MODE_STANDALONE, 0);

        final String root = "/ftp";
        server = new FakeFtpServer();
        server.addUserAccount(new UserAccount("autumo", "autumo", root));
        final UnixFakeFileSystem fs = new UnixFakeFileSystem();
        fs.add(new DirectoryEntry(root));
        fs.add(new DirectoryEntry(root + "/upload"));
        server.setFileSystem(fs);
        server.setServerControlPort(2122);
        server.start();
        port = server.getServerControlPort();

        srcFile = Files.createTempFile("src", ".txt");
        Files.writeString(srcFile, "upload me");

        final String ipc = "formatVersion: 1\n\n"
                + "general: {}\n\n"
                + "sourceInterface: null_in\n"
                + "destinationInterfaces: [ftp_file_out]\n"
                + "processSourceEntities: [\"NULL\"]\n\n"
                + "sources:\n"
                + "  - name: null_in\n"
                + "    entities:\n"
                + "      \"NULL\": {}\n\n"
                + "destinations:\n"
                + "  - name: ftp_file_out\n"
                + "    params:\n"
                + "      host: localhost\n"
                + "      port: \"" + port + "\"\n"
                + "      user: autumo\n"
                + "      password: autumo\n"
                + "      ssl: \"no\"\n"
                + "      directory: upload\n";
        ipcFile = Files.createTempFile("ftpw-test", ".ifcx");
        Files.writeString(ipcFile, ipc);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) server.stop();
        if (ipcFile != null) Files.deleteIfExists(ipcFile);
        if (srcFile != null) Files.deleteIfExists(srcFile);
    }

    @Test
    void writerUploadsFileToMockServer() throws Exception {
        final Properties procProps = Helper.loadConfiguration(ipcFile.toString());
        final Properties generalProps = Helper.loadConfiguration("cfg/ifacex.cfg");
        final Configuration config = new Configuration(null, generalProps, procProps, ipcFile.toString());

        final FTPWriter writer = new FTPWriter();
        writer.initialize("ftp_file_out", config, null);

        final SourceEntity entity = new SourceEntity(config, "NULL", null);
        writer.initializeEntity("ftp_file_out", config, entity);

        final BatchData batch = new BatchData(config);
        batch.addRecordValues(new String[] { srcFile.toString() });
        writer.writeBatchData("ftp_file_out", config, batch, entity);

        // verify file landed on mock server
        final org.mockftpserver.fake.filesystem.FileSystem fs = server.getFileSystem();
        assertTrue(fs.exists("/ftp/upload/" + srcFile.getFileName().toString()),
                "Uploaded file should exist on mock FTP server");
    }
}
