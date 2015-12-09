/* Copyright (c) 2001-2015, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package com.pixelmed.ftp;

/**
 * @author	dclunie
 */
public class FTPException extends Exception {

	private static final String identString = "@(#) $Header: /userland/cvs/pixelmed/imgbook/com/pixelmed/ftp/FTPException.java,v 1.4 2015/08/26 15:44:24 dclunie Exp $";
	
	/**
	 * @param	msg
	 */
	public FTPException(String msg) {
		super(msg);
	}
	
	/**
	 * @param	e
	 */
	public FTPException(FTPException e) {
		super(e);
	}
}



