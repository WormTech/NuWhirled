//
// $Id$

package com.threerings.msoy.swiftly.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import junit.framework.TestCase;

public class SwiftlyDocumentUnitTest extends TestCase
{
    public SwiftlyDocumentUnitTest (String name)
    {
        super(name);
    }

    public void testInstantiate ()
        throws Exception
    {
        File inputFile = File.createTempFile("source", ".swiftly-storage");
        inputFile.deleteOnExit();

        OutputStream output = new FileOutputStream(inputFile);
        InputStream input = new FileInputStream(inputFile);
        output.write("Hello, World".getBytes("UTF8"));
        
        SwiftlyDocument doc = new SwiftlyDocument(input, null, "utf8");
        assertEquals("Hello, World", doc.getText());
    }
}
