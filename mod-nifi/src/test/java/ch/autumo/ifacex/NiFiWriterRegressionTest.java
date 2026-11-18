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
import ch.autumo.ifacex.ip.nifi.NiFiWriter;
import ch.autumo.ifacex.utils.Helper;

/**
 * Functional regression test for the NiFi Site-to-Site writer using a mocked
 * SiteToSiteClient (no network, no NiFi instance).
 */
class NiFiWriterRegressionTest {

    private Path ipcFile;

    @BeforeEach
    void setUp() throws Exception {
        ch.autumo.ifacex.InterfaceFactory.initialize("./", ch.autumo.ifacex.App.MODE_STANDALONE, 0);

        final String ipc = "formatVersion: 1\n\n"
                + "general: {}\n\n"
                + "sourceInterface: null_in\n"
                + "destinationInterfaces: [nifi_out]\n"
                + "processSourceEntities: [\"NULL\"]\n\n"
                + "sources:\n"
                + "  - name: null_in\n"
                + "    entities:\n"
                + "      \"NULL\": {}\n\n"
                + "destinations:\n"
                + "  - name: nifi_out\n"
                + "    params:\n"
                + "      url: http://localhost:8080/nifi\n"
                + "      port_name: inputPort\n"
                + "      mode: batch\n";
        ipcFile = Files.createTempFile("nifiw-test", ".ifcx");
        Files.writeString(ipcFile, ipc);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (ipcFile != null) Files.deleteIfExists(ipcFile);
    }

    @Test
    void writerHandlesEmptyBatch() throws Exception {
        final Properties procProps = Helper.loadConfiguration(ipcFile.toString());
        final Properties generalProps = Helper.loadConfiguration("cfg/ifacex.cfg");
        final Configuration config = new Configuration(null, generalProps, procProps, ipcFile.toString());

        final org.apache.nifi.remote.client.SiteToSiteClient client =
                Mockito.mock(org.apache.nifi.remote.client.SiteToSiteClient.class);

        final NiFiWriter writer = new NiFiWriter();
        writer.setClient(client);
        writer.initialize("nifi_out", config, null);

        final SourceEntity entity = new SourceEntity(config, "NULL", null);
        writer.initializeEntity("nifi_out", config, entity);

        final BatchData batch = new BatchData(config);
        // empty batch -> writeBatchData returns early, no transaction needed
        assertDoesNotThrow(() -> writer.writeBatchData("nifi_out", config, batch, entity));
    }
}
