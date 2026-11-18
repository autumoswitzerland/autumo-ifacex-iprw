/**
 * Copyright 2026 autumo GmbH, Michael Gasche.
 * All Rights Reserved.
 */
package ch.autumo.ifacex;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.DirectoryEntry;
import org.mockftpserver.fake.filesystem.FileEntry;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;

import ch.autumo.ifacex.batch.BatchProcessor;
import ch.autumo.ifacex.ip.ftp.FTPReader;
import ch.autumo.ifacex.utils.Helper;

/**
 * Functional regression test for the FTP reader against a mock FTP server
 * (MockFtpServer). Exercises the real reader code path including IPC loading.
 */
class FTPReaderRegressionTest {

    private FakeFtpServer server;
    private int port;
    private Path tempOut;
    private Path ipcFile;

    @BeforeEach
    void setUp() throws Exception {
        ch.autumo.ifacex.InterfaceFactory.initialize("./", ch.autumo.ifacex.App.MODE_STANDALONE, 0);

        final String root = "/ftp";
        server = new FakeFtpServer();
        server.addUserAccount(new UserAccount("autumo", "autumo", root));
        final UnixFakeFileSystem fs = new UnixFakeFileSystem();
        fs.add(new DirectoryEntry(root));
        fs.add(new DirectoryEntry(root + "/upload"));
        fs.add(new FileEntry(root + "/upload/foobar.txt", "hello ftp"));
        server.setFileSystem(fs);
        server.setServerControlPort(2121);
        server.start();
        port = server.getServerControlPort();

        tempOut = Files.createTempDirectory("ftp-out");

        final String ipc = "formatVersion: 1\n\n"
                + "general: {}\n\n"
                + "sourceInterface: ftp_file_in\n"
                + "destinationInterfaces: [file_out]\n"
                + "processSourceEntities: [upload]\n\n"
                + "sources:\n"
                + "  - name: ftp_file_in\n"
                + "    params:\n"
                + "      host: localhost\n"
                + "      port: \"" + port + "\"\n"
                + "      user: autumo\n"
                + "      password: autumo\n"
                + "      ssl: \"no\"\n"
                + "      temp_out_path: " + tempOut.toString() + "\n"
                + "    entities:\n"
                + "      upload: {}\n";
        ipcFile = Files.createTempFile("ftp-test", ".ifcx");
        Files.writeString(ipcFile, ipc);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server != null) server.stop();
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
    void readerDownloadsFileFromMockServer() throws Exception {
        final Properties procProps = Helper.loadConfiguration(ipcFile.toString());
        final Properties generalProps = Helper.loadConfiguration("cfg/ifacex.cfg");
        final Configuration config =
                new Configuration(null, generalProps, procProps, ipcFile.toString());

        final BatchProcessor batchProcessor = mock(BatchProcessor.class);

        final FTPReader reader = new FTPReader();
        reader.initialize("ftp_file_in", config, null);

        final SourceEntity entity = new SourceEntity(config, "upload", null);
        reader.initializeEntity("ftp_file_in", config, entity);
        reader.read("ftp_file_in", batchProcessor, config, entity, false);

        assertTrue(Files.exists(tempOut.resolve("foobar.txt")),
                "Downloaded file should exist in temp output path");
    }
}
