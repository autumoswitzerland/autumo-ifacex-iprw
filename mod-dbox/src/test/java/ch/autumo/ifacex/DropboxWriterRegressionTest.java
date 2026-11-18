/**
 * Copyright 2026 autumo GmbH, Michael Gasche.
 * All Rights Reserved.
 */
package ch.autumo.ifacex;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.dropbox.core.v2.files.DbxUserFilesRequests;
import com.dropbox.core.v2.files.UploadUploader;

import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.ip.dropbox.DropboxWriter;
import ch.autumo.ifacex.utils.Helper;

/**
 * Functional regression test for the Dropbox writer using a mocked DbxClientV2.
 */
class DropboxWriterRegressionTest {

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
                + "destinationInterfaces: [dbox_file_out]\n"
                + "processSourceEntities: [\"NULL\"]\n\n"
                + "sources:\n"
                + "  - name: null_in\n"
                + "    entities:\n"
                + "      \"NULL\": {}\n\n"
                + "destinations:\n"
                + "  - name: dbox_file_out\n"
                + "    params:\n"
                + "      oauth_access_token: dummy-token\n"
                + "      app_name: ifaceX\n"
                + "      app_version: 1.0\n"
                + "      path: /\n";
        ipcFile = Files.createTempFile("dboxw-test", ".ifcx");
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

        final com.dropbox.core.v2.DbxClientV2 client = mock(com.dropbox.core.v2.DbxClientV2.class);
        final DbxUserFilesRequests files = mock(DbxUserFilesRequests.class);
        final UploadUploader uploader = mock(UploadUploader.class);
        when(client.files()).thenReturn(files);
        when(files.getMetadata("/")).thenReturn(mock(com.dropbox.core.v2.files.Metadata.class));
        when(files.upload(org.mockito.ArgumentMatchers.anyString())).thenReturn(uploader);
        when(uploader.uploadAndFinish(org.mockito.ArgumentMatchers.any(java.io.InputStream.class)))
                .thenReturn(mock(com.dropbox.core.v2.files.FileMetadata.class));

        final DropboxWriter writer = new DropboxWriter();
        writer.setClient(client);
        writer.initialize("dbox_file_out", config, null);

        final SourceEntity entity = new SourceEntity(config, "NULL", null);
        writer.initializeEntity("dbox_file_out", config, entity);

        final BatchData batch = new BatchData(config);
        batch.addRecordValues(new String[] { srcFile.toString() });
        writer.writeBatchData("dbox_file_out", config, batch, entity);

        org.mockito.Mockito.verify(files).upload("/" + srcFile.getFileName().toString());
    }
}
