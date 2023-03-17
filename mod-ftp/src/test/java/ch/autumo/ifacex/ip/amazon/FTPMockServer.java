/**
 * Copyright 2023 autumo GmbH, Michael Gasche.
 * All Rights Reserved.
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * Bug fixes, suggestions and comments should be sent to:
 * code@autumo.ch
 * 
 */
package ch.autumo.ifacex.ip.amazon;

import org.apache.commons.net.ftp.FTPClient;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.DirectoryEntry;
import org.mockftpserver.fake.filesystem.FileEntry;
import org.mockftpserver.fake.filesystem.FileSystem;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;
//import org.junit.runners.model.Statement;


/**
 * FTP Mock Server.
 */
public class FTPMockServer {

	//private static final Lock lock = new Lock();
	
	/**
	 * Main.
	 * @param args none or file-path
	 */
    public static void main(String args[]) throws Throwable {
    	
		String filePath = null;
		
		// Server with file system!
		if (args.length == 1)
			filePath = args[0].trim();
		else 
			filePath = "/Volumes/Fastdrive/tmp/ftp";
		
		// FTP
		final FakeFtpServer fakeFtpServer = new FakeFtpServer();
        fakeFtpServer.addUserAccount(new UserAccount("autumo", "autumo", filePath));

        final FileSystem fileSystem = new UnixFakeFileSystem();
        
        fileSystem.add(new DirectoryEntry(filePath));
        fileSystem.add(new FileEntry(filePath + "/foobar.txt", "abcdef 1234567890"));
                
        fakeFtpServer.setFileSystem(fileSystem);
        fakeFtpServer.setServerControlPort(21);
        fakeFtpServer.start();

        final int port = fakeFtpServer.getServerControlPort();
        
        
        // Test
        //final FTPSClient sftpClient = new FTPSClient();
        final FTPClient ftpClient = new FTPClient();
        ftpClient.connect("localhost", port);
        ftpClient.login("autumo", "autumo");
        ftpClient.disconnect();
        
        
        System.out.println("");
        System.out.println("FTP mock server running on port " + port + ".");
	    
        waitForEnter();
        System.out.println("Shutting down...");
        
        fakeFtpServer.stop();

        System.out.println("Done.");
        System.out.println("");
        
        System.exit(0);
    }

	private static void waitForEnter() {
        System.out.println("\nPress ENTER to end FTP mock server.");
	    try {
			System.in.read();
		} catch (Exception e) {
		}
	}

	/**
	 * Lock object.
	 */
	public static final class Lock {
	}
		
}
