/**
 * Copyright 2026 autumo GmbH, Michael Gasche.
 * All Rights Reserved.
 */
package ch.autumo.ifacex;

import static org.junit.jupiter.api.Assertions.assertTrue;

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

import ch.autumo.ifacex.batch.BatchData;
import ch.autumo.ifacex.ip.amazon.AWSS3FileWriter;
import ch.autumo.ifacex.utils.Helper;
import io.findify.s3mock.S3Mock;

/**
 * Functional regression test for the AWS S3 writer against S3Mock.
 */
class AWSS3WriterRegressionTest {

    private S3Mock s3mock;
    private int port = 8001;
    private Path ipcFile;
    private Path srcFile;
    private AmazonS3 seedClient;

    @BeforeEach
    void setUp() throws Exception {
        s3mock = new S3Mock.Builder().withPort(port).withInMemoryBackend().build();
        s3mock.start();

        seedClient = AmazonS3ClientBuilder.standard()
                .withPathStyleAccessEnabled(true)
                .withEndpointConfiguration(new EndpointConfiguration("http://localhost:" + port, "us-east-1"))
                .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("foo", "bar")))
                .build();

        srcFile = Files.createTempFile("src", ".txt");
        Files.writeString(srcFile, "upload me");

        final String ipc = "formatVersion: 1\n\n"
                + "general: {}\n\n"
                + "sourceInterface: null_in\n"
                + "destinationInterfaces: [aws_file_out]\n"
                + "processSourceEntities: [\"NULL\"]\n\n"
                + "sources:\n"
                + "  - name: null_in\n"
                + "    entities:\n"
                + "      \"NULL\": {}\n\n"
                + "destinations:\n"
                + "  - name: aws_file_out\n"
                + "    params:\n"
                + "      access_key: foo\n"
                + "      secret_key: bar\n"
                + "      url_endpoint: http://localhost:" + port + "\n"
                + "      region: us-east-1\n"
                + "      bucket_name: testbucket2\n"
                + "      key_prefix: \"\"\n";
        ipcFile = Files.createTempFile("s3w-test", ".ifcx");
        Files.writeString(ipcFile, ipc);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (seedClient != null) seedClient.shutdown();
        if (s3mock != null) s3mock.shutdown();
        if (ipcFile != null) Files.deleteIfExists(ipcFile);
        if (srcFile != null) Files.deleteIfExists(srcFile);
    }

    @Test
    void writerUploadsFileToMockServer() throws Exception {
        ch.autumo.ifacex.InterfaceFactory.initialize("./", ch.autumo.ifacex.App.MODE_STANDALONE, 0);
        final Properties procProps = Helper.loadConfiguration(ipcFile.toString());
        final Properties generalProps = Helper.loadConfiguration("cfg/ifacex.cfg");
        final Configuration config = new Configuration(null, generalProps, procProps, ipcFile.toString());

        final AWSS3FileWriter writer = new AWSS3FileWriter();
        writer.initialize("aws_file_out", config, null);

        final SourceEntity entity = new SourceEntity(config, "NULL", null);
        writer.initializeEntity("aws_file_out", config, entity);

        final BatchData batch = new BatchData(config);
        batch.addRecordValues(new String[] { srcFile.toString() });
        writer.writeBatchData("aws_file_out", config, batch, entity);

        assertTrue(seedClient.doesObjectExist("testbucket2", srcFile.getFileName().toString()),
                "Uploaded object should exist in the bucket");
    }
}
