/**
 * Copyright 2026 autumo GmbH, Michael Gasche.
 * All Rights Reserved.
 */
package ch.autumo.ifacex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import ch.autumo.ifacex.batch.BatchProcessor;
import ch.autumo.ifacex.ip.amazon.AWSS3FileReader;
import ch.autumo.ifacex.utils.Helper;

/**
 * Functional regression test for the AWS S3 reader using a mocked S3 client
 * (no network, no credentials).
 */
class AWSS3ReaderRegressionTest {

    private Path tempOut;
    private Path ipcFile;

    @BeforeEach
    void setUp() throws Exception {
        ch.autumo.ifacex.InterfaceFactory.initialize("./", ch.autumo.ifacex.App.MODE_STANDALONE, 0);

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
                + "      url_endpoint: http://localhost:8001\n"
                + "      region: us-east-1\n"
                + "      temp_out_path: " + tempOut.toString() + "\n"
                + "    entities:\n"
                + "      testbucket: {}\n";
        ipcFile = Files.createTempFile("s3r-test", ".ifcx");
        Files.writeString(ipcFile, ipc);
    }

    @AfterEach
    void tearDown() throws Exception {
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
    void readerDownloadsFileFromMockClient() throws Exception {
        final Properties procProps = Helper.loadConfiguration(ipcFile.toString());
        final Properties generalProps = Helper.loadConfiguration("cfg/ifacex.cfg");
        final Configuration config = new Configuration(null, generalProps, procProps, ipcFile.toString());

        final S3Client client = mock(S3Client.class);
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder()
                        .contents(S3Object.builder().key("foobar.txt").build())
                        .isTruncated(false)
                        .build());
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(
                new ResponseInputStream<>(
                        GetObjectResponse.builder().build(),
                        AbortableInputStream.create(new ByteArrayInputStream("hello s3".getBytes()))));

        final BatchProcessor batchProcessor = mock(BatchProcessor.class);

        final AWSS3FileReader reader = new AWSS3FileReader();
        reader.setClient(client);
        reader.initialize("aws_file_in", config, null);

        final SourceEntity entity = new SourceEntity(config, "testbucket", null);
        reader.initializeEntity("aws_file_in", config, entity);
        reader.read("aws_file_in", batchProcessor, config, entity, false);

        assertTrue(Files.exists(tempOut.resolve("foobar.txt")),
                "Downloaded file should exist in temp output path");
        assertEquals("hello s3", Files.readString(tempOut.resolve("foobar.txt")),
                "Downloaded content should match");
    }
}
