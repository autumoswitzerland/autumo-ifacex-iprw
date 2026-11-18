/**
 * Copyright 2026 autumo GmbH, Michael Gasche.
 * All Rights Reserved.
 */
package ch.autumo.ifacex;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import ch.autumo.ifacex.batch.BatchProcessor;
import ch.autumo.ifacex.ip.amazon.AWSS3FileReader;
import ch.autumo.ifacex.utils.Helper;
import io.findify.s3mock.S3Mock;

/**
 * Functional regression test for the AWS S3 reader against S3Mock.
 */
class AWSS3ReaderRegressionTest {

    private S3Mock s3mock;
    private int port = 8001;
    private Path tempOut;
    private Path ipcFile;
    private AmazonS3 seedClient;

    @BeforeEach
    void setUp() throws Exception {
        s3mock = new S3Mock.Builder().withPort(port).withInMemoryBackend().build();
        s3mock.start();

        // seed bucket + object
        seedClient = AmazonS3ClientBuilder.standard()
                .withPathStyleAccessEnabled(true)
                .withEndpointConfiguration(new EndpointConfiguration("http://localhost:" + port, "us-east-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("foo", "bar")))
                .build();
        if (!seedClient.doesBucketExistV2("testbucket")) seedClient.createBucket("testbucket");
        seedClient.putObject("testbucket", "foobar.txt", "hello s3");

        tempOut = Files.createTempDirectory("s3-out");

        final String ipc = "formatVersion: 1\n\n"
                + "general: {}\n\n"
                + "sourceInterface: aws_file_in\n"
                + "destinationInterfaces: [file_out]\n"
                + "processSourceEntities: [testbucket]\n\n"
                + "sources:\n"
                + "  - name: aws_file_in\n"
                + "    params:\n"
                + "      access_key: foo\n"
                + "      secret_key: bar\n"
                + "      url_endpoint: http://localhost:" + port + "\n"
                + "      region: us-east-1\n"
                + "      temp_out_path: " + tempOut.toString() + "\n"
                + "    entities:\n"
                + "      testbucket: {}\n";
        ipcFile = Files.createTempFile("s3r-test", ".ifcx");
        Files.writeString(ipcFile, ipc);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (seedClient != null) seedClient.shutdown();
        if (s3mock != null) s3mock.shutdown();
        if (tempOut != null) {
            try (var s = Files.walk(tempOut)) {
                s.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
            }
        }
        if (ipcFile != null) Files.deleteIfExists(ipcFile);
    }

    @Test
    void readerDownloadsFileFromMockServer() throws Exception {
        ch.autumo.ifacex.InterfaceFactory.initialize("./", ch.autumo.ifacex.App.MODE_STANDALONE, 0);
        final Properties procProps = Helper.loadConfiguration(ipcFile.toString());
        final Properties generalProps = Helper.loadConfiguration("cfg/ifacex.cfg");
        final Configuration config = new Configuration(null, generalProps, procProps, ipcFile.toString());

        final BatchProcessor batchProcessor = mock(BatchProcessor.class);

        final AWSS3FileReader reader = new AWSS3FileReader();
        reader.initialize("aws_file_in", config, null);

        final SourceEntity entity = new SourceEntity(config, "testbucket", null);
        reader.initializeEntity("aws_file_in", config, entity);
        reader.read("aws_file_in", batchProcessor, config, entity, false);

        assertTrue(Files.exists(tempOut.resolve("foobar.txt")),
                "Downloaded file should exist in temp output path");
    }
}
