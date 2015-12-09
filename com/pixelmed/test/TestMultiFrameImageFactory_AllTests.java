/* Copyright (c) 2001-2015, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package com.pixelmed.test;

import com.pixelmed.dicom.*;

import junit.framework.*;

public class TestMultiFrameImageFactory_AllTests extends TestCase {
	
	public static Test suite() {
		TestSuite suite = new TestSuite("All JUnit Tests");
		suite.addTest(TestMultiFrameImageFactoryDateTime.suite());
		return suite;
	}
	
}
