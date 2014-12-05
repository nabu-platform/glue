package be.nabu.glue;

import java.io.IOException;
import java.net.URI;

import be.nabu.glue.impl.methods.FileMethods;
import junit.framework.TestCase;

public class TestFileHandling extends TestCase {
	public void testFile() throws IOException {
		// local file
		URI uri = FileMethods.uri("testing.txt");
		assertTrue(uri.toString().startsWith("file:/"));
		
		uri = FileMethods.uri("classpath:/package.glue");
		assertEquals("classpath:/package.glue", uri.toString());
	}
}
