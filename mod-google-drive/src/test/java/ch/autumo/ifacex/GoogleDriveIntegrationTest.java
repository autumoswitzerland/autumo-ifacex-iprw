package ch.autumo.ifacex;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import ch.autumo.ifacex.batch.BatchProcessor;
import ch.autumo.ifacex.ip.google.GoogleDriveReader;
import ch.autumo.ifacex.utils.Helper;

@Tag("integration")
class GoogleDriveIntegrationTest {

    private Path tempOut;
    private Path ipcFile;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(System.getenv("GDRIVE_TEST_CLIENT_ID") != null && !System.getenv("GDRIVE_TEST_CLIENT_ID").isBlank(), "GDRIVE_TEST_CLIENT_ID not set");
        assumeTrue(System.getenv("GDRIVE_TEST_CLIENT_SECRET") != null && !System.getenv("GDRIVE_TEST_CLIENT_SECRET").isBlank(), "GDRIVE_TEST_CLIENT_SECRET not set");
        InterfaceFactory.initialize("./", App.MODE_STANDALONE, 0);
        tempOut = Files.createTempDirectory("gdrive-it-out");
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
    void readerReadsFromRealGoogleDrive() throws Exception {
        String clientId = System.getenv("GDRIVE_TEST_CLIENT_ID");
        String clientSecret = System.getenv("GDRIVE_TEST_CLIENT_SECRET");
        String folder = System.getenv().getOrDefault("GDRIVE_TEST_FOLDER", "");
        String appName = System.getenv().getOrDefault("GDRIVE_TEST_APP_NAME", "ifaceX");
        String projectId = System.getenv().getOrDefault("GDRIVE_TEST_PROJECT_ID", "test-project");
        String authUri = System.getenv().getOrDefault("GDRIVE_TEST_AUTH_URI", "https://accounts.google.com/o/oauth2/auth");
        String tokenUri = System.getenv().getOrDefault("GDRIVE_TEST_TOKEN_URI", "https://oauth2.googleapis.com/token");
        String authProviderCertUrl = System.getenv().getOrDefault("GDRIVE_TEST_AUTH_PROVIDER_CERT_URL", "https://www.googleapis.com/oauth2/v1/certs");
        String redirectUris = System.getenv().getOrDefault("GDRIVE_TEST_REDIRECT_URIS", "[\"urn:ietf:wg:oauth:2.0:oob\"]");
        String tempOutPath = tempOut.toString();

        String ipc = "formatVersion: 1\n\n"
                + "general: {}\n\n"
                + "sourceInterface: gdrive_file_in\n"
                + "destinationInterfaces: [file_out]\n"
                + "processSourceEntities: [drive]\n\n"
                + "sources:\n"
                + "  - name: gdrive_file_in\n"
                + "    params:\n"
                + "      application_name: " + appName + "\n"
                + "      project_id: " + projectId + "\n"
                + "      client_id: " + clientId + "\n"
                + "      client_secret: " + clientSecret + "\n"
                + "      auth_uri: " + authUri + "\n"
                + "      token_uri: " + tokenUri + "\n"
                + "      auth_provider_x509_cert_url: " + authProviderCertUrl + "\n"
                + "      redirect_uris: " + redirectUris + "\n"
                + "      folder: \"" + folder + "\"\n"
                + "      temp_out_path: " + tempOutPath + "\n"
                + "    entities:\n"
                + "      drive: {}\n";
        ipcFile = Files.createTempFile("gdrive-it", ".ifcx");
        Files.writeString(ipcFile, ipc);

        Properties procProps = Helper.loadConfiguration(ipcFile.toString());
        Properties generalProps = Helper.loadConfiguration("cfg/ifacex.cfg");
        Configuration config = new Configuration(null, generalProps, procProps, ipcFile.toString());

        GoogleDriveReader reader = new GoogleDriveReader();
        reader.initialize("gdrive_file_in", config, null);
        SourceEntity entity = new SourceEntity(config, "drive", null);
        reader.initializeEntity("gdrive_file_in", config, entity);
        BatchProcessor bp = Mockito.mock(BatchProcessor.class);
        reader.read("gdrive_file_in", bp, config, entity, false);
    }
}