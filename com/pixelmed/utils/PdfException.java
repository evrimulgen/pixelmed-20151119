/* Copyright (c) 2001-2015, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package com.pixelmed.utils;

/**
 * @author	dclunie
 */
public class PdfException extends Exception {

	private static final String identString = "@(#) $Header: /userland/cvs/pixelmed/imgbook/com/pixelmed/utils/PdfException.java,v 1.2 2015/08/26 15:44:26 dclunie Exp $";

	/**
	 * @param	msg
	 */
	public PdfException(String msg) {
		super(msg);
	}
}


