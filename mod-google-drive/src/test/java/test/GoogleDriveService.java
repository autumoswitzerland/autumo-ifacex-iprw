package test;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import java.io.IOException;
import java.util.List;

public class GoogleDriveService {

    private final Drive driveService;

    public GoogleDriveService(Drive driveService) {
        this.driveService = driveService;
    }

    public List<File> listFiles() throws IOException {
        Drive.Files.List request = driveService.files().list();
        FileList result = request.execute();
        return result.getFiles();
    }
}
