package ch.autumo.ifacex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.batch.BatchProcessor;
import ch.autumo.ifacex.ip.amazon.AWSS3FileReader;
import ch.autumo.ifacex.ip.amazon.AWSS3FileWriter;
import ch.autumo.ifacex.utils.Helper;

/**
 * Integration test against a real S3 service or the local S3Mock emulator.
 *
 * Real AWS requires AWS_TEST_ACCESS_KEY, AWS_TEST_SECRET_KEY, AWS_TEST_REGION
 * and AWS_TEST_BUCKET (optional AWS_TEST_URL_ENDPOINT).
 *
 * Local emulator (no AWS account/credit card needed):
 *   mod-az-aws-s3/test/docker.sh start
 *   AWS_TEST_EMULATOR=1
 */
@Tag("integration")
class AWSS3IntegrationTest {

    private static final String EMULATOR_ENDPOINT = "http://localhost:9090";
    private static final String EMULATOR_ACCESS_KEY = "minioadmin";
    private static final String EMULATOR_SECRET_KEY = "minioadmin";
    private static final String EMULATOR_REGION = "us-east-1";
    private static final String EMULATOR_BUCKET = "ifacex-it";

    private Path tempOut;
    private Path srcFile;
    private Path ipcFile;

    private String accessKey;
    private String secretKey;
    private String region;
    private String bucket;
    private String endpoint;

    private S3Client s3;
    private String objectKey;

    @BeforeEach
    void setUp() throws Exception {
        final boolean emulator = isTrue(System.getenv("AWS_TEST_EMULATOR"));

        accessKey = envOrDefault("AWS_TEST_ACCESS_KEY", emulator ? EMULATOR_ACCESS_KEY : null);
        secretKey = envOrDefault("AWS_TEST_SECRET_KEY", emulator ? EMULATOR_SECRET_KEY : null);
        region = envOrDefault("AWS_TEST_REGION", emulator ? EMULATOR_REGION : null);
        bucket = envOrDefault("AWS_TEST_BUCKET", emulator ? EMULATOR_BUCKET : null);
        endpoint = envOrDefault("AWS_TEST_URL_ENDPOINT", emulator ? EMULATOR_ENDPOINT : null);

        assumeTrue(notBlank(accessKey), "AWS_TEST_ACCESS_KEY not set");
        assumeTrue(notBlank(secretKey), "AWS_TEST_SECRET_KEY not set");
        assumeTrue(notBlank(region), "AWS_TEST_REGION not set");
        assumeTrue(notBlank(bucket), "AWS_TEST_BUCKET not set");

        InterfaceFactory.initialize("./", App.MODE_STANDALONE, 0);

        s3 = buildClient();

        if (emulator) {
            try {
                s3.listBuckets();
            } catch (Exception e) {
                assumeTrue(false, "S3 emulator not reachable at " + endpoint
                        + " (start it with mod-az-aws-s3/test/docker.sh start)");
            }
        }

        tempOut = Files.createTempDirectory("s3-it-out");

        final String endpointLine = notBlank(endpoint) ? "      url_endpoint: " + endpoint + "\n" : "";

        final String ipc = "formatVersion: 1\n\n"
                + "general: {}\n\n"
                + "sourceInterface: aws_file_in\n"
                + "destinationInterfaces: [aws_file_out]\n"
                + "processSourceEntities: [\"" + bucket + "\"]\n\n"
                + "sources:\n"
                + "  - name: aws_file_in\n"
                + "    params:\n"
                + "      access_key: " + accessKey + "\n"
                + "      secret_key: " + secretKey + "\n"
                + endpointLine
                + "      region: " + region + "\n"
                + "      temp_out_path: " + tempOut + "\n"
                + "    entities:\n"
                + "      \"" + bucket + "\": {}\n\n"
                + "destinations:\n"
                + "  - name: aws_file_out\n"
                + "    params:\n"
                + "      access_key: " + accessKey + "\n"
                + "      secret_key: " + secretKey + "\n"
                + endpointLine
                + "      region: " + region + "\n"
                + "      bucket_name: " + bucket + "\n"
                + "      key_prefix: \"\"\n";
        ipcFile = Files.createTempFile("s3-it", ".ifcx");
        Files.writeString(ipcFile, ipc);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (s3 != null) {
            if (objectKey != null) {
                try {
                    s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
                } catch (Exception ignored) {
                }
            }
            try {
                s3.close();
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
    }

    @Test
    void writerUploadsAndReaderDownloadsFromS3() throws Exception {
        final Properties procProps = Helper.loadConfiguration(ipcFile.toString());
        final Properties generalProps = Helper.loadConfiguration("cfg/ifacex.cfg");
        final Configuration config = new Configuration(null, generalProps, procProps, ipcFile.toString());

        // 1) Upload a uniquely named file through the writer
        final String content = "ifacex s3 integration test " + UUID.randomUUID();
        srcFile = Files.createTempFile("s3-it-src-", ".txt");
        Files.writeString(srcFile, content);
        objectKey = srcFile.getFileName().toString();

        final AWSS3FileWriter writer = new AWSS3FileWriter();
        writer.initialize("aws_file_out", config, null);
        final SourceEntity writerEntity = new SourceEntity(config, "NULL", null);
        writer.initializeEntity("aws_file_out", config, writerEntity);

        final BatchData batch = new BatchData(config);
        batch.addRecordValues(new String[] { srcFile.toString() });
        writer.writeBatchData("aws_file_out", config, batch, writerEntity);
        writer.close("aws_file_out");

        // 2) Read it back through the reader
        final AWSS3FileReader reader = new AWSS3FileReader();
        reader.initialize("aws_file_in", config, null);
        final SourceEntity readerEntity = new SourceEntity(config, bucket, null);
        reader.initializeEntity("aws_file_in", config, readerEntity);

        final BatchProcessor batchProcessor = Mockito.mock(BatchProcessor.class);
        reader.read("aws_file_in", batchProcessor, config, readerEntity, false);
        reader.close("aws_file_in");

        final Path downloaded = tempOut.resolve(objectKey);
        assertTrue(Files.exists(downloaded), "Uploaded object should be downloaded by the reader");
        assertEquals(content, Files.readString(downloaded), "Downloaded content should match uploaded content");
    }

    private S3Client buildClient() {
        final S3ClientBuilder builder = S3Client.builder()
                .forcePathStyle(true)
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)));
        if (notBlank(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
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
