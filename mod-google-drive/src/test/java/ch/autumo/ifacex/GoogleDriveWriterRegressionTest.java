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
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.ip.google.GoogleDriveWriter;
import ch.autumo.ifacex.utils.Helper;

/**
 * Functional regression test for the Google Drive writer using a mocked
 * Drive service (no network, no OAuth).
 */
class GoogleDriveWriterRegressionTest {

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
                + "destinationInterfaces: [gdrive_file_out]\n"
                + "processSourceEntities: [\"NULL\"]\n\n"
                + "sources:\n"
                + "  - name: null_in\n"
                + "    entities:\n"
                + "      \"NULL\": {}\n\n"
                + "destinations:\n"
                + "  - name: gdrive_file_out\n"
                + "    params:\n"
                + "      application_name: ifaceX\n"
                + "      project_id: test-project\n"
                + "      client_id: dummy\n"
                + "      client_secret: dummy\n"
                + "      auth_uri: https://accounts.google.com/o/oauth2/auth\n"
                + "      token_uri: https://oauth2.googleapis.com/token\n"
                + "      auth_provider_x509_cert_url: https://www.googleapis.com/oauth2/v1/certs\n"
                + "      redirect_uris: [\"urn:ietf:wg:oauth:2.0:oob\"]\n"
                + "      folder: \"\"\n";
        ipcFile = Files.createTempFile("gdrw-test", ".ifcx");
        Files.writeString(ipcFile, ipc);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (ipcFile != null) Files.deleteIfExists(ipcFile);
        if (srcFile != null) Files.deleteIfExists(srcFile);
    }

    @Test
    void writerUploadsFileToMockService() throws Exception {
        final Properties procProps = Helper.loadConfiguration(ipcFile.toString());
        final Properties generalProps = Helper.loadConfiguration("cfg/ifacex.cfg");
        final Configuration config = new Configuration(null, generalProps, procProps, ipcFile.toString());

        final Drive drive = Mockito.mock(Drive.class);
        final Drive.Files files = Mockito.mock(Drive.Files.class);
        final Drive.Files.List listReq = Mockito.mock(Drive.Files.List.class, Mockito.RETURNS_SELF);
        final Drive.Files.Create createReq = Mockito.mock(Drive.Files.Create.class, Mockito.RETURNS_SELF);
        final FileList emptyList = new FileList();
        emptyList.setFiles(java.util.Collections.emptyList());
        final File createdFile = new File();
        createdFile.setId("file-id");
        createdFile.setName(srcFile.getFileName().toString());

        when(drive.files()).thenReturn(files);
        when(files.list()).thenReturn(listReq);
        when(listReq.execute()).thenReturn(emptyList);
        when(files.create(org.mockito.ArgumentMatchers.any(File.class),
                org.mockito.ArgumentMatchers.any(com.google.api.client.http.AbstractInputStreamContent.class)))
                .thenReturn(createReq);
        when(createReq.execute()).thenReturn(createdFile);

        final GoogleDriveWriter writer = new GoogleDriveWriter();
        writer.setService(drive);
        writer.initialize("gdrive_file_out", config, null);

        final SourceEntity entity = new SourceEntity(config, "NULL", null);
        writer.initializeEntity("gdrive_file_out", config, entity);

        final BatchData batch = new BatchData(config);
        batch.addRecordValues(new String[] { srcFile.toString() });
        assertDoesNotThrow(() -> writer.writeBatchData("gdrive_file_out", config, batch, entity));

        Mockito.verify(files).create(org.mockito.ArgumentMatchers.any(File.class),
                org.mockito.ArgumentMatchers.any(com.google.api.client.http.AbstractInputStreamContent.class));
    }
}
