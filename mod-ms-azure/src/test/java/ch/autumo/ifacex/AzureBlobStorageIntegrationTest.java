package ch.autumo.ifacex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;

import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.batch.BatchProcessor;
import ch.autumo.ifacex.ip.microsoft.AzureBlobStorageReader;
import ch.autumo.ifacex.ip.microsoft.AzureBlobStorageWriter;
import ch.autumo.ifacex.utils.Helper;

/**
 * Integration test against a real Azure Blob Storage account or the local
 * Azurite emulator.
 *
 * Real Azure requires AZURE_TEST_CONNECTION_STRING and AZURE_TEST_CONTAINER.
 *
 * Local emulator (no Azure account/credit card needed):
 *   mod-ms-azure/test/docker.sh start
 *   AZURE_TEST_EMULATOR=1
 */
@Tag("integration")
class AzureBlobStorageIntegrationTest {

    /** Well-known Azurite development connection string. */
    private static final String EMULATOR_CONNECTION_STRING =
            "DefaultEndpointsProtocol=http;"
            + "AccountName=devstoreaccount1;"
            + "AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;"
            + "BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;";

    private static final String EMULATOR_ACCOUNT = "devstoreaccount1";
    private static final String EMULATOR_CONTAINER = "ifacex-it";

    private Path tempOut;
    private Path srcFile;
    private Path ipcFile;

    private String connectionString;
    private String container;
    private String account;

    private BlobServiceClient client;
    private String blobName;

    @BeforeEach
    void setUp() throws Exception {
        final boolean emulator = isTrue(System.getenv("AZURE_TEST_EMULATOR"));

        connectionString = envOrDefault("AZURE_TEST_CONNECTION_STRING",
                emulator ? EMULATOR_CONNECTION_STRING : null);
        container = envOrDefault("AZURE_TEST_CONTAINER", emulator ? EMULATOR_CONTAINER : null);
        account = envOrDefault("AZURE_TEST_ACCOUNT", emulator ? EMULATOR_ACCOUNT : "");

        assumeTrue(notBlank(connectionString), "AZURE_TEST_CONNECTION_STRING not set");
        assumeTrue(notBlank(container), "AZURE_TEST_CONTAINER not set");

        InterfaceFactory.initialize("./", App.MODE_STANDALONE, 0);

        client = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();

        if (emulator) {
            try {
                client.getBlobContainerClient(container).exists();
            } catch (Exception e) {
                assumeTrue(false, "Azure emulator not reachable (start it with mod-ms-azure/test/docker.sh start)");
            }
        }

        tempOut = Files.createTempDirectory("azure-it-out");

        final String ipc = "formatVersion: 1\n\n"
                + "general: {}\n\n"
                + "sourceInterface: azure_file_in\n"
                + "destinationInterfaces: [azure_file_out]\n"
                + "processSourceEntities: [\"" + container + "\"]\n\n"
                + "sources:\n"
                + "  - name: azure_file_in\n"
                + "    params:\n"
                + "      account: " + account + "\n"
                + "      connection_string: \"" + connectionString + "\"\n"
                + "      temp_out_path: " + tempOut + "\n"
                + "    entities:\n"
                + "      \"" + container + "\": {}\n\n"
                + "destinations:\n"
                + "  - name: azure_file_out\n"
                + "    params:\n"
                + "      account: " + account + "\n"
                + "      connection_string: \"" + connectionString + "\"\n"
                + "      container: " + container + "\n";
        ipcFile = Files.createTempFile("azure-it", ".ifcx");
        Files.writeString(ipcFile, ipc);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            if (blobName != null) {
                try {
                    client.getBlobContainerClient(container).getBlobClient(blobName).deleteIfExists();
                } catch (Exception ignored) {
                }
            }
            client = null;
        }
        if (tempOut != null) {
            try (var s = Files.walk(tempOut)) {
                s.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
        if (srcFile != null) Files.deleteIfExists(srcFile);
        if (ipcFile != null) Files.deleteIfExists(ipcFile);
    }

    @Test
    void writerUploadsAndReaderDownloadsFromAzureBlob() throws Exception {
        final Properties procProps = Helper.loadConfiguration(ipcFile.toString());
        final Properties generalProps = Helper.loadConfiguration("cfg/ifacex.cfg");
        final Configuration config = new Configuration(null, generalProps, procProps, ipcFile.toString());

        // 1) Upload a uniquely named file through the writer
        final String content = "ifacex azure integration test " + UUID.randomUUID();
        srcFile = Files.createTempFile("azure-it-src-", ".txt");
        Files.writeString(srcFile, content);
        blobName = srcFile.getFileName().toString();

        final AzureBlobStorageWriter writer = new AzureBlobStorageWriter();
        writer.initialize("azure_file_out", config, null);
        final SourceEntity writerEntity = new SourceEntity(config, "NULL", null);
        writer.initializeEntity("azure_file_out", config, writerEntity);

        final BatchData batch = new BatchData(config);
        batch.addRecordValues(new String[] { srcFile.toString() });
        writer.writeBatchData("azure_file_out", config, batch, writerEntity);
        writer.close("azure_file_out");

        // 2) Read it back through the reader
        final AzureBlobStorageReader reader = new AzureBlobStorageReader();
        reader.initialize("azure_file_in", config, null);
        final SourceEntity readerEntity = new SourceEntity(config, container, null);
        reader.initializeEntity("azure_file_in", config, readerEntity);

        final BatchProcessor batchProcessor = Mockito.mock(BatchProcessor.class);
        reader.read("azure_file_in", batchProcessor, config, readerEntity, false);
        reader.close("azure_file_in");

        final Path downloaded = tempOut.resolve(blobName);
        assertTrue(Files.exists(downloaded), "Uploaded blob should be downloaded by the reader");
        assertEquals(content, Files.readString(downloaded), "Downloaded content should match uploaded content");
    }

    private static boolean isTrue(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private static String envOrDefault(String name, String defaultValue) {
        final String value = System.getenv(name);
        return notBlank(value) ? value : defaultValue;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
