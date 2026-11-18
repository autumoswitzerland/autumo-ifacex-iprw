/**
 * Copyright 2026 autumo GmbH, Michael Gasche.
 * All Rights Reserved.
 *
 * Integration test for the SFTP reader/writer against a real SFTP server.
 * Skipped unless the SFTP_TEST_* environment variables are set AND the server
 * actually supports the SFTP subsystem (SSH alone is not enough).
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
import ch.autumo.ifacex.ip.sftp.SFTPReader;
import ch.autumo.ifacex.utils.Helper;

@Tag("integration")
class SFTPIntegrationTest {

    private Path tempOut;
    private Path srcFile;
    private Path ipcFile;
    private Path knownHosts;

    @BeforeEach
    void setUp() throws Exception {
        String host = System.getenv("SFTP_TEST_HOST");
        assumeTrue(host != null && !host.isBlank(), "SFTP_TEST_HOST not set");

        InterfaceFactory.initialize("./", App.MODE_STANDALONE, 0);
        tempOut = Files.createTempDirectory("sftp-it-out");
        srcFile = Files.createTempFile("src", ".txt");
        Files.writeString(srcFile, "integration test upload");

        String port = System.getenv().getOrDefault("SFTP_TEST_PORT", "22");

        // Scan the host key dynamically so SSHJ can verify it.
        knownHosts = Files.createTempFile("sftp-it-known-hosts", "");
        ProcessBuilder pb = new ProcessBuilder("ssh-keyscan", "-p", port, "-t", "ed25519,rsa,ecdsa", host);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String output = new String(proc.getInputStream().readAllBytes());
        int exit = proc.waitFor();
        Files.writeString(knownHosts, (exit == 0 && output.contains("ssh-")) ? output : "");

        // Skip the test if the server does not offer the SFTP subsystem
        // (SSH reachable but SFTP unavailable -> Connection closed / Stream closed).
        String user = System.getenv("SFTP_TEST_USER");
        String pass = System.getenv("SFTP_TEST_PASSWORD");
        assumeTrue(user != null && pass != null, "SFTP_TEST_USER/PASSWORD not set");
        assumeTrue(supportsSftp(host, port, user, pass), "Server does not support the SFTP subsystem");
    }

    private boolean supportsSftp(String host, String port, String user, String pass) {
        ProcessBuilder pb = new ProcessBuilder("sshpass", "-p", pass, "sftp",
                "-o", "BatchMode=no",
                "-o", "StrictHostKeyChecking=no",
                "-o", "UserKnownHostsFile=" + knownHosts,
                "-P", port, user + "@" + host);
        pb.redirectErrorStream(true);
        try {
            Process proc = pb.start();
            String out = new String(proc.getInputStream().readAllBytes());
            proc.waitFor();
            return out.contains("sftp>");
        } catch (Exception e) {
            return false;
        }
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
        if (knownHosts != null) Files.deleteIfExists(knownHosts);
    }

    @Test
    void readerConnectsAndLists() throws Exception {
        String host = System.getenv("SFTP_TEST_HOST");
        String port = System.getenv().getOrDefault("SFTP_TEST_PORT", "22");
        String user = System.getenv("SFTP_TEST_USER");
        String pass = System.getenv("SFTP_TEST_PASSWORD");
        String dir = System.getenv().getOrDefault("SFTP_TEST_DIR", "/");
        String tempOutPath = tempOut.toString();
        String knownHostsPath = knownHosts.toString();

        String ipc = "formatVersion: 1\n\n"
                + "general: {}\n\n"
                + "sourceInterface: sftp_file_in\n"
                + "destinationInterfaces: [file_out]\n"
                + "processSourceEntities: [" + dir + "]\n\n"
                + "sources:\n"
                + "  - name: sftp_file_in\n"
                + "    params:\n"
                + "      host: " + host + "\n"
                + "      port: \"" + port + "\"\n"
                + "      user: " + user + "\n"
                + "      password: " + pass + "\n"
                + "      known_hosts: " + knownHostsPath + "\n"
                + "      temp_out_path: " + tempOutPath + "\n"
                + "    entities:\n"
                + "      \"" + dir + "\": {}\n";
        ipcFile = Files.createTempFile("sftp-it", ".ifcx");
        Files.writeString(ipcFile, ipc);

        Properties procProps = Helper.loadConfiguration(ipcFile.toString());
        Properties generalProps = Helper.loadConfiguration("cfg/ifacex.cfg");
        Configuration config = new Configuration(null, generalProps, procProps, ipcFile.toString());

        SFTPReader reader = new SFTPReader();
        reader.initialize("sftp_file_in", config, null);
        SourceEntity entity = new SourceEntity(config, dir, null);
        reader.initializeEntity("sftp_file_in", config, entity);
        BatchProcessor bp = Mockito.mock(BatchProcessor.class);
        reader.read("sftp_file_in", bp, config, entity, false);
    }
}