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

import com.github.sardine.Sardine;

import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.ip.webdav.WebDAVWriter;
import ch.autumo.ifacex.utils.Helper;

/**
 * Functional regression test for the WebDAV writer using a mocked Sardine client.
 */
class WebDAVWriterRegressionTest {

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
                + "destinationInterfaces: [webdav_file_out]\n"
                + "processSourceEntities: [\"NULL\"]\n\n"
                + "sources:\n"
                + "  - name: null_in\n"
                + "    entities:\n"
                + "      \"NULL\": {}\n\n"
                + "destinations:\n"
                + "  - name: webdav_file_out\n"
                + "    params:\n"
                + "      url: http://localhost:8080/webdav\n"
                + "      user: autumo\n"
                + "      password: autumo\n"
                + "      ssl: \"no\"\n"
                + "      dest_uri: upload\n"
                + "      allow_delete: \"yes\"\n";
        ipcFile = Files.createTempFile("webdavw-test", ".ifcx");
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

        final Sardine client = Mockito.mock(Sardine.class);
        when(client.exists(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);

        final WebDAVWriter writer = new WebDAVWriter();
        writer.setWebdav(client);
        writer.initialize("webdav_file_out", config, null);

        final SourceEntity entity = new SourceEntity(config, "NULL", null);
        writer.initializeEntity("webdav_file_out", config, entity);

        final BatchData batch = new BatchData(config);
        batch.addRecordValues(new String[] { srcFile.toString() });
        assertDoesNotThrow(() -> writer.writeBatchData("webdav_file_out", config, batch, entity));

        Mockito.verify(client).put(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(java.io.File.class),
                org.mockito.ArgumentMatchers.anyString());
    }
}
