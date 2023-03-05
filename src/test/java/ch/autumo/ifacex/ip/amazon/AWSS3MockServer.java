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

import java.io.IOException;

import io.findify.s3mock.S3Mock;

/**
 * Amazon AWS S3 Mock Server.
 */
public class AWSS3MockServer {
    
	public static final int PORT = 8001;
	public static final String DEFAULT_S3_FILE_PATH = "/Volumes/Fastdrive/tmp/s3";
	
	/**
	 * Main.
	 * @param args none or file-path
	 */
	public static void main(String args[]) {

		String filePath = null;
		
		// Server with file system!
		if (args.length == 1)
			filePath = args[0].trim();
		
		boolean inMemory = filePath == null;
		
		
		S3Mock api = null;
		S3Mock mock = null;
		
		if (inMemory) {
			
	        System.out.println("S3 mock server mode: Memory");
		    api = new S3Mock.Builder().withPort(PORT).withInMemoryBackend().build();
		    api.start();
		    
		} else {
			
			// Server with file system!
	        System.out.println("S3 mock server mode: FilePath["+filePath+"]");
			mock = S3Mock.create(PORT, filePath);
			mock.start();
		}

        System.out.println("");
        System.out.println("S3 mock server running on port " + PORT + ".");
	    
        waitForEnter();
        System.out.println("Shutting down...");

		if (inMemory)
			api.shutdown();          
		else
			mock.stop();
        
        System.out.println("Done.");
        System.out.println("");
        
        System.exit(0);
	}

	private static void waitForEnter() {
        System.out.println("\nPress ENTER to end S3 mock server.");
	    try {
			System.in.read();
		} catch (IOException e) {
		}
	}	
	
}
