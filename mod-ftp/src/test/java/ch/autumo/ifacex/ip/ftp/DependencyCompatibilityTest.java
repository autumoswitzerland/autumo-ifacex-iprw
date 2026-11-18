/**
 * Copyright 2026 autumo GmbH, Michael Gasche.
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
package ch.autumo.ifacex.ip.ftp;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import ch.autumo.ifacex.reader.Reader;
import ch.autumo.ifacex.writer.Writer;

/**
 * Compatibility test of this module against the ifaceX API and the third-party library versions
 * declared in its POM.
 *
 * <p>It fails when a dependency upgrade changes the classes or interfaces this module is built
 * on - e.g. a major version of a cloud SDK that moves or renames the classes the reader/writer
 * extends. No network access and no credentials are required.</p>
 */
class DependencyCompatibilityTest {

    /** The reader must still implement the ifaceX reader contract. */
    @Test
    void readerImplementsIfaceXReader() throws Exception {
        final Class<?> c = Class.forName("ch.autumo.ifacex.ip.ftp.FTPReader");
        assertInstanceOf(Reader.class, c.getDeclaredConstructor().newInstance());
    }

    /** The writer must still implement the ifaceX writer contract. */
    @Test
    void writerImplementsIfaceXWriter() throws Exception {
        final Class<?> c = Class.forName("ch.autumo.ifacex.ip.ftp.FTPWriter");
        assertInstanceOf(Writer.class, c.getDeclaredConstructor().newInstance());
    }

    /** org.apache.commons.net.ftp.FTPClient must still be available in the declared library version. */
    @Test
    void libraryClassExistsFTPClient() throws Exception {
        assertNotNull(Class.forName("org.apache.commons.net.ftp.FTPClient"));
    }

    /** org.apache.commons.net.ftp.FTPSClient must still be available in the declared library version. */
    @Test
    void libraryClassExistsFTPSClient() throws Exception {
        assertNotNull(Class.forName("org.apache.commons.net.ftp.FTPSClient"));
    }
}
