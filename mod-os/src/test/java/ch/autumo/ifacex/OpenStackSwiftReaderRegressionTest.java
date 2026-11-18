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

import ch.autumo.ifacex.batch.BatchProcessor;
import ch.autumo.ifacex.ip.openstack.OpenStackSwiftReader;
import ch.autumo.ifacex.utils.Helper;

/**
 * Functional regression test for the OpenStack Swift reader using a mocked
 * OSClient (no network, no credentials).
 */
class OpenStackSwiftReaderRegressionTest {

    private Path tempOut;
    private Path ipcFile;

    @BeforeEach
    void setUp() throws Exception {
        ch.autumo.ifacex.InterfaceFactory.initialize("./", ch.autumo.ifacex.App.MODE_STANDALONE, 0);

        tempOut = Files.createTempDirectory("os-out");

        final String ipc = "formatVersion: 1\n\n"
                + "general: {}\n\n"
                + "sourceInterface: os_swift_file_in\n"
                + "destinationInterfaces: [file_out]\n"
                + "processSourceEntities: [mycontainer]\n\n"
                + "sources:\n"
                + "  - name: os_swift_file_in\n"
                + "    params:\n"
                + "      user: admin\n      user_id: \"\"\n"
                + "      password: secret\n"
                + "      domain: default\n"
                + "      project: admin\n"
                + "      url_endpoint: http://localhost:5000/v3\n"
                + "      ssl: \"no\"\n"
                + "      version: \"3\"\n"
                + "      temp_out_path: " + tempOut.toString() + "\n"
                + "    entities:\n"
                + "      mycontainer: {}\n";
        ipcFile = Files.createTempFile("osr-test", ".ifcx");
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
    void readerHandlesEmptyContainer() throws Exception {
        final Properties procProps = Helper.loadConfiguration(ipcFile.toString());
        final Properties generalProps = Helper.loadConfiguration("cfg/ifacex.cfg");
        final Configuration config = new Configuration(null, generalProps, procProps, ipcFile.toString());

        final org.openstack4j.api.OSClient<?> client = Mockito.mock(org.openstack4j.api.OSClient.class);
        final org.openstack4j.api.storage.ObjectStorageService oss = Mockito.mock(org.openstack4j.api.storage.ObjectStorageService.class);
        final org.openstack4j.api.storage.ObjectStorageObjectService osos = Mockito.mock(org.openstack4j.api.storage.ObjectStorageObjectService.class);
        when(client.objectStorage()).thenReturn(oss);
        when(oss.objects()).thenReturn(osos);
        when(osos.list(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Collections.emptyList());

        final OpenStackSwiftReader reader = new OpenStackSwiftReader();
        reader.setClient(client);
        reader.initialize("os_swift_file_in", config, null);

        final SourceEntity entity = new SourceEntity(config, "mycontainer", null);
        reader.initializeEntity("os_swift_file_in", config, entity);

        final BatchProcessor batchProcessor = Mockito.mock(BatchProcessor.class);
        assertDoesNotThrow(() -> reader.read("os_swift_file_in", batchProcessor, config, entity, false));
    }
}
