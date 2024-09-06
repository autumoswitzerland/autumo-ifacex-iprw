package test;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

public class GoogleDriveServiceTest {

    @Mock
    private Drive driveService;

    @Mock
    private Drive.Files driveFiles;

    @InjectMocks
    private GoogleDriveService googleDriveService;  // Your service class that interacts with Google Drive API

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testListFiles() throws IOException {
        // Mock the Drive.Files.List call
        Drive.Files.List listRequest = Mockito.mock(Drive.Files.List.class);
        when(driveService.files()).thenReturn(driveFiles);
        when(driveFiles.list()).thenReturn(listRequest);

        // Mock the response
        File file1 = new File().setId("1").setName("File 1");
        File file2 = new File().setId("2").setName("File 2");
        FileList fileList = new FileList().setFiles(Arrays.asList(file1, file2));
        when(listRequest.execute()).thenReturn(fileList);

        // Call the method to test
        java.util.List<File> files = googleDriveService.listFiles();

        // Assert the results
        assertEquals(2, files.size());
        assertEquals("File 1", files.get(0).getName());
        assertEquals("File 2", files.get(1).getName());
    }
}
