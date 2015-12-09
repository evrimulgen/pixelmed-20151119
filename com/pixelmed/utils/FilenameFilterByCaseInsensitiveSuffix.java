/* Copyright (c) 2001-2015, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package com.pixelmed.utils;

import java.io.File;
import java.io.FilenameFilter;

public class FilenameFilterByCaseInsensitiveSuffix implements FilenameFilter {
	
	private static final String identString = "@(#) $Header: /userland/cvs/pixelmed/imgbook/com/pixelmed/utils/FilenameFilterByCaseInsensitiveSuffix.java,v 1.3 2015/08/26 15:44:26 dclunie Exp $";

	private String suffix;
	
	public FilenameFilterByCaseInsensitiveSuffix(String suffix) {
		this.suffix = suffix.toUpperCase();
	}
	
	public boolean accept(File dir,String name) {
		return name != null && name.toUpperCase().trim().endsWith(suffix);
	}
}
	
