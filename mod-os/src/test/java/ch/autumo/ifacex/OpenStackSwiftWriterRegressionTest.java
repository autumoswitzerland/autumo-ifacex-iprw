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

import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.ip.openstack.OpenStackSwiftWriter;
import ch.autumo.ifacex.utils.Helper;

/**
 * Functional regression test for the OpenStack Swift writer using a mocked
 * OSClient (no network, no credentials).
 */
class OpenStackSwiftWriterRegressionTest {

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
                + "destinationInterfaces: [os_swift_file_out]\n"
                + "processSourceEntities: [\"NULL\"]\n\n"
                + "sources:\n"
                + "  - name: null_in\n"
                + "    entities:\n"
                + "      \"NULL\": {}\n\n"
                + "destinations:\n"
                + "  - name: os_swift_file_out\n"
                + "    params:\n"
                + "      user: admin\n      user_id: \"\"\n"
                + "      password: secret\n"
                + "      domain: default\n"
                + "      project: admin\n"
                + "      url_endpoint: http://localhost:5000/v3\n"
                + "      ssl: \"no\"\n"
                + "      version: \"3\"\n"
                + "      container: mycontainer\n";
        ipcFile = Files.createTempFile("osw-test", ".ifcx");
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

        final org.openstack4j.api.OSClient<?> client = Mockito.mock(org.openstack4j.api.OSClient.class);
        final org.openstack4j.api.storage.ObjectStorageService oss = Mockito.mock(org.openstack4j.api.storage.ObjectStorageService.class);
        final org.openstack4j.api.storage.ObjectStorageContainerService oscs = Mockito.mock(org.openstack4j.api.storage.ObjectStorageContainerService.class);
        final org.openstack4j.api.storage.ObjectStorageObjectService osos = Mockito.mock(org.openstack4j.api.storage.ObjectStorageObjectService.class);
        final org.openstack4j.model.common.ActionResponse resp = Mockito.mock(org.openstack4j.model.common.ActionResponse.class);

        when(client.objectStorage()).thenReturn(oss);
        when(oss.containers()).thenReturn(oscs);
        when(oscs.list()).thenReturn(java.util.Collections.emptyList());
        when(resp.isSuccess()).thenReturn(true);
        when(oscs.create("mycontainer")).thenReturn(resp);
        when(oss.objects()).thenReturn(osos);

        final OpenStackSwiftWriter writer = new OpenStackSwiftWriter();
        writer.setClient(client);
        writer.initialize("os_swift_file_out", config, null);

        final SourceEntity entity = new SourceEntity(config, "NULL", null);
        writer.initializeEntity("os_swift_file_out", config, entity);

        final BatchData batch = new BatchData(config);
        batch.addRecordValues(new String[] { srcFile.toString() });
        assertDoesNotThrow(() -> writer.writeBatchData("os_swift_file_out", config, batch, entity));

        Mockito.verify(osos).put(org.mockito.ArgumentMatchers.eq("mycontainer"),
                org.mockito.ArgumentMatchers.eq(srcFile.getFileName().toString()),
                org.mockito.ArgumentMatchers.any());
    }
}
