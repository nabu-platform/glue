/*
* Copyright (C) 2014 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.glue.core;

import java.io.IOException;
import java.net.URI;

import be.nabu.glue.core.impl.methods.FileMethods;
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
