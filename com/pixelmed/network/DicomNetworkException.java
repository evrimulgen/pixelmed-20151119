/* Copyright (c) 2001-2015, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package com.pixelmed.network;

/**
 * @author	dclunie
 */
public class DicomNetworkException extends Exception {

	private static final String identString = "@(#) $Header: /userland/cvs/pixelmed/imgbook/com/pixelmed/network/DicomNetworkException.java,v 1.5 2015/08/26 15:44:25 dclunie Exp $";

	/**
	 * @param	msg
	 */
	public DicomNetworkException(String msg) {
		super(msg);
	}
}



