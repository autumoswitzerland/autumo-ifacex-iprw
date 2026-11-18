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

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.FileList;

import ch.autumo.ifacex.batch.BatchProcessor;
import ch.autumo.ifacex.ip.google.GoogleDriveReader;
import ch.autumo.ifacex.utils.Helper;

/**
 * Functional regression test for the Google Drive reader using a mocked
 * Drive service (no network, no OAuth).
 */
class GoogleDriveReaderRegressionTest {

    private Path tempOut;
    private Path ipcFile;

    @BeforeEach
    void setUp() throws Exception {
        ch.autumo.ifacex.InterfaceFactory.initialize("./", ch.autumo.ifacex.App.MODE_STANDALONE, 0);

        tempOut = Files.createTempDirectory("gdrive-out");

        final String ipc = "formatVersion: 1\n\n"
                + "general: {}\n\n"
                + "sourceInterface: gdrive_file_in\n"
                + "destinationInterfaces: [file_out]\n"
                + "processSourceEntities: [root]\n\n"
                + "sources:\n"
                + "  - name: gdrive_file_in\n"
                + "    params:\n"
                + "      application_name: ifaceX\n"
                + "      project_id: test-project\n"
                + "      client_id: dummy\n"
                + "      client_secret: dummy\n"
                + "      auth_uri: https://accounts.google.com/o/oauth2/auth\n"
                + "      token_uri: https://oauth2.googleapis.com/token\n"
                + "      auth_provider_x509_cert_url: https://www.googleapis.com/oauth2/v1/certs\n"
                + "      redirect_uris: [\"urn:ietf:wg:oauth:2.0:oob\"]\n"
                + "      folder: \"\"\n"
                + "      temp_out_path: " + tempOut.toString() + "\n"
                + "    entities:\n"
                + "      root: {}\n";
        ipcFile = Files.createTempFile("gdr-test", ".ifcx");
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
    void readerHandlesEmptyResult() throws Exception {
        final Properties procProps = Helper.loadConfiguration(ipcFile.toString());
        final Properties generalProps = Helper.loadConfiguration("cfg/ifacex.cfg");
        final Configuration config = new Configuration(null, generalProps, procProps, ipcFile.toString());

        final Drive drive = Mockito.mock(Drive.class);
        final Drive.Files files = Mockito.mock(Drive.Files.class);
        final Drive.Files.List listReq = Mockito.mock(Drive.Files.List.class, Mockito.RETURNS_SELF);
        final FileList fileList = new FileList();
        fileList.setFiles(java.util.Collections.emptyList());

        when(drive.files()).thenReturn(files);
        when(files.list()).thenReturn(listReq);
        when(listReq.execute()).thenReturn(fileList);

        final GoogleDriveReader reader = new GoogleDriveReader();
        reader.setService(drive);
        reader.initialize("gdrive_file_in", config, null);

        final SourceEntity entity = new SourceEntity(config, "root", null);
        reader.initializeEntity("gdrive_file_in", config, entity);

        final BatchProcessor batchProcessor = Mockito.mock(BatchProcessor.class);
        assertDoesNotThrow(() -> reader.read("gdrive_file_in", batchProcessor, config, entity, false));
    }
}
