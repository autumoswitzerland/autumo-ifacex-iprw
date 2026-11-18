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

import org.apache.nifi.remote.Transaction;
import org.apache.nifi.remote.TransferDirection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import ch.autumo.ifacex.batch.BatchProcessor;
import ch.autumo.ifacex.ip.nifi.NiFiReader;
import ch.autumo.ifacex.utils.Helper;

/**
 * Functional regression test for the NiFi Site-to-Site reader using a mocked
 * SiteToSiteClient (no network, no NiFi instance).
 */
class NiFiReaderRegressionTest {

    private Path tempOut;
    private Path ipcFile;

    @BeforeEach
    void setUp() throws Exception {
        ch.autumo.ifacex.InterfaceFactory.initialize("./", ch.autumo.ifacex.App.MODE_STANDALONE, 0);

        tempOut = Files.createTempDirectory("nifi-out");

        final String ipc = "formatVersion: 1\n\n"
                + "general: {}\n\n"
                + "sourceInterface: nifi_in\n"
                + "destinationInterfaces: [console_out]\n"
                + "processSourceEntities: [outputPort]\n\n"
                + "sources:\n"
                + "  - name: nifi_in\n"
                + "    params:\n"
                + "      url: http://localhost:8080/nifi\n"
                + "      port_name: outputPort\n"
                + "      content_format: file\n"
                + "      temp_out_path: " + tempOut.toString() + "\n"
                + "    entities:\n"
                + "      outputPort: {}\n";
        ipcFile = Files.createTempFile("nifir-test", ".ifcx");
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
    void readerHandlesEmptyTransaction() throws Exception {
        final Properties procProps = Helper.loadConfiguration(ipcFile.toString());
        final Properties generalProps = Helper.loadConfiguration("cfg/ifacex.cfg");
        final Configuration config = new Configuration(null, generalProps, procProps, ipcFile.toString());

        final org.apache.nifi.remote.client.SiteToSiteClient client =
                Mockito.mock(org.apache.nifi.remote.client.SiteToSiteClient.class);
        final Transaction transaction = Mockito.mock(Transaction.class);
        when(client.createTransaction(TransferDirection.RECEIVE)).thenReturn(transaction);
        when(transaction.receive()).thenReturn(null);

        final NiFiReader reader = new NiFiReader();
        reader.setClient(client);
        reader.initialize("nifi_in", config, null);

        final SourceEntity entity = new SourceEntity(config, "outputPort", null);
        reader.initializeEntity("nifi_in", config, entity);

        final BatchProcessor batchProcessor = Mockito.mock(BatchProcessor.class);
        assertDoesNotThrow(() -> reader.read("nifi_in", batchProcessor, config, entity, false));
    }
}
