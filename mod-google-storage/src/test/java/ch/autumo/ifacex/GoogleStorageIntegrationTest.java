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

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.batch.BatchProcessor;
import ch.autumo.ifacex.ip.google.GoogleStorageReader;
import ch.autumo.ifacex.ip.google.GoogleStorageWriter;
import ch.autumo.ifacex.utils.Helper;

/**
 * Integration test against a real Google Cloud Storage bucket.
 *
 * Requires GCS_TEST_PROJECT_ID, GCS_TEST_CREDENTIALS_JSON (path to a service
 * account JSON file or the inline JSON content) and GCS_TEST_BUCKET.
 * The test is skipped when these variables are missing.
 */
@Tag("integration")
class GoogleStorageIntegrationTest {

    private Path tempOut;
    private Path tempCreds;
    private Path srcFile;
    private Path ipcFile;

    private String projectId;
    private String bucketName;
    private String credentialsFile;

    private Storage storage;
    private String blobName;

    @BeforeEach
    void setUp() throws Exception {
        projectId = System.getenv("GCS_TEST_PROJECT_ID");
        bucketName = System.getenv("GCS_TEST_BUCKET");
        final String credentials = System.getenv("GCS_TEST_CREDENTIALS_JSON");

        assumeTrue(projectId != null && !projectId.isBlank(), "GCS_TEST_PROJECT_ID not set");
        assumeTrue(bucketName != null && !bucketName.isBlank(), "GCS_TEST_BUCKET not set");
        assumeTrue(credentials != null && !credentials.isBlank(), "GCS_TEST_CREDENTIALS_JSON not set");

        InterfaceFactory.initialize("./", App.MODE_STANDALONE, 0);

        // The variable may point to a credentials file or contain the JSON itself
        if (credentials.trim().startsWith("{")) {
            tempCreds = Files.createTempFile("gcs-it-creds", ".json");
            Files.writeString(tempCreds, credentials);
            credentialsFile = tempCreds.toString();
        } else {
            credentialsFile = credentials;
        }

        storage = StorageOptions.newBuilder()
                .setCredentials(GoogleCredentials.fromStream(Files.newInputStream(Path.of(credentialsFile))))
                .setProjectId(projectId)
                .build()
                .getService();

        assumeTrue(storage.get(bucketName) != null, "GCS bucket '" + bucketName + "' not found");

        tempOut = Files.createTempDirectory("gcs-it-out");

        final String ipc = "formatVersion: 1\n\n"
                + "general: {}\n\n"
                + "sourceInterface: gstorage_file_in\n"
                + "destinationInterfaces: [gstorage_file_out]\n"
                + "processSourceEntities: [\"" + bucketName + "\"]\n\n"
                + "sources:\n"
                + "  - name: gstorage_file_in\n"
                + "    params:\n"
                + "      credentials_file: " + credentialsFile + "\n"
                + "      project_id: " + projectId + "\n"
                + "      temp_out_path: " + tempOut + "\n"
                + "    entities:\n"
                + "      \"" + bucketName + "\": {}\n\n"
                + "destinations:\n"
                + "  - name: gstorage_file_out\n"
                + "    params:\n"
                + "      credentials_file: " + credentialsFile + "\n"
                + "      project_id: " + projectId + "\n"
                + "      bucket_name: " + bucketName + "\n"
                + "      storage_class: STANDARD\n"
                + "      region: EU\n";
        ipcFile = Files.createTempFile("gcs-it", ".ifcx");
        Files.writeString(ipcFile, ipc);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (storage != null && blobName != null) {
            try {
                final Blob blob = storage.get(BlobId.of(bucketName, blobName));
                if (blob != null) {
                    blob.delete();
                }
            } catch (Exception ignored) {
            }
            try {
                storage.close();
            } catch (Exception ignored) {
            }
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
        if (tempCreds != null) Files.deleteIfExists(tempCreds);
    }

    @Test
    void writerUploadsAndReaderDownloadsFromRealBucket() throws Exception {
        final Properties procProps = Helper.loadConfiguration(ipcFile.toString());
        final Properties generalProps = Helper.loadConfiguration("cfg/ifacex.cfg");
        final Configuration config = new Configuration(null, generalProps, procProps, ipcFile.toString());

        // 1) Upload a uniquely named file through the writer
        final String content = "ifacex gcs integration test " + UUID.randomUUID();
        srcFile = Files.createTempFile("gcs-it-src-", ".txt");
        Files.writeString(srcFile, content);
        blobName = srcFile.getFileName().toString();

        final GoogleStorageWriter writer = new GoogleStorageWriter();
        writer.initialize("gstorage_file_out", config, null);
        final SourceEntity writerEntity = new SourceEntity(config, "NULL", null);
        writer.initializeEntity("gstorage_file_out", config, writerEntity);

        final BatchData batch = new BatchData(config);
        batch.addRecordValues(new String[] { srcFile.toString() });
        writer.writeBatchData("gstorage_file_out", config, batch, writerEntity);
        writer.close("gstorage_file_out");

        // 2) Read it back through the reader
        final GoogleStorageReader reader = new GoogleStorageReader();
        reader.initialize("gstorage_file_in", config, null);
        final SourceEntity readerEntity = new SourceEntity(config, bucketName, null);
        reader.initializeEntity("gstorage_file_in", config, readerEntity);

        final BatchProcessor batchProcessor = Mockito.mock(BatchProcessor.class);
        reader.read("gstorage_file_in", batchProcessor, config, readerEntity, false);
        reader.close("gstorage_file_in");

        final Path downloaded = tempOut.resolve(blobName);
        assertTrue(Files.exists(downloaded), "Uploaded blob should be downloaded by the reader");
        assertEquals(content, Files.readString(downloaded), "Downloaded content should match uploaded content");
    }
}
