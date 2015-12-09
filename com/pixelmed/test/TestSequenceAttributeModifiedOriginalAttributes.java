/* Copyright (c) 2001-2015, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package com.pixelmed.test;

import com.pixelmed.dicom.Attribute;
import com.pixelmed.dicom.AttributeList;
import com.pixelmed.dicom.DicomException;
import com.pixelmed.dicom.SequenceAttribute;
import com.pixelmed.dicom.SequenceItem;
import com.pixelmed.dicom.TagFromName;
import com.pixelmed.dicom.UniqueIdentifierAttribute;

import java.util.Iterator;

import junit.framework.*;

public class TestSequenceAttributeModifiedOriginalAttributes extends TestCase {
	
	// constructor to support adding tests to suite ...
	
	public TestSequenceAttributeModifiedOriginalAttributes(String name) {
		super(name);
	}
	
	// add tests to suite manually, rather than depending on default of all test...() methods
	// in order to allow adding TestSequenceAttributeModifiedOriginalAttributes.suite() in AllTests.suite()
	// see Johannes Link. Unit Testing in Java pp36-47
	
	public static Test suite() {
		TestSuite suite = new TestSuite("TestSequenceAttributeModifiedOriginalAttributes");
		
		suite.addTest(new TestSequenceAttributeModifiedOriginalAttributes("TestSequenceAttributeModifiedOriginalAttributes_StudyInstanceUID"));
		
		return suite;
	}
	
	protected void setUp() {
	}
	
	protected void tearDown() {
	}
	
	public void TestSequenceAttributeModifiedOriginalAttributes_StudyInstanceUID() throws DicomException {
//System.err.println("TestSequenceAttributeModifiedOriginalAttributes_StudyInstanceUID");
		String originalStudyInstanceUID = "1.2.3.4";
	
		// create it ...
		
		AttributeList list = new AttributeList();
		{
			SequenceAttribute oas = new SequenceAttribute(TagFromName.OriginalAttributesSequence);
			list.put(oas);
			{
				AttributeList oalist = new AttributeList();
				oas.addItem(oalist);
				{
					SequenceAttribute mas = new SequenceAttribute(TagFromName.ModifiedAttributesSequence);
					oalist.put(mas);
					AttributeList malist = new AttributeList();
					mas.addItem(malist);
					{ Attribute a = new UniqueIdentifierAttribute(TagFromName.StudyInstanceUID); a.addValue(originalStudyInstanceUID); malist.put(a); }
				}
			}
		}
		
		// test recovery of it (walking sequence items without convenience methods) ...
		
		{
			String studyInstanceUID = "";
			SequenceAttribute oas = (SequenceAttribute)(list.get(TagFromName.OriginalAttributesSequence));
			if (oas != null) {
				Iterator i = oas.iterator();
				while (i.hasNext()) {
					SequenceItem oai = (SequenceItem)i.next();
					if (oai != null) {
						AttributeList oalist = oai.getAttributeList();
						SequenceAttribute mas = (SequenceAttribute)(oalist.get(TagFromName.ModifiedAttributesSequence));
						if (mas != null && mas.getNumberOfItems() > 0) {
							SequenceItem mai = mas.getItem(0);
							if (mai != null) {
								AttributeList malist = mai.getAttributeList();
								studyInstanceUID = Attribute.getSingleStringValueOrEmptyString(malist,TagFromName.StudyInstanceUID);
								if (studyInstanceUID.length() > 0) {
									break; // quit once we have a value ... assume there is no need to look at other items of OriginalAttributesSequence, if any
								}
							}
						}
					}
				}
			}
			assertEquals("Checking StudyInstanceUID",originalStudyInstanceUID,studyInstanceUID);
		}

	}
}
