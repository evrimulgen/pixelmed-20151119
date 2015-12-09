/* Copyright (c) 2001-2015, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package com.pixelmed.web;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;

import com.pixelmed.database.DatabaseInformationModel;
import com.pixelmed.database.PatientStudySeriesConcatenationInstanceModel;
import com.pixelmed.dicom.AttributeList;
import com.pixelmed.dicom.DicomException;
import com.pixelmed.dicom.DicomInputStream;
import com.pixelmed.dicom.TagFromName;
import com.pixelmed.network.DicomNetworkException;
import com.pixelmed.network.ReceivedObjectHandler;
import com.pixelmed.network.StorageSOPClassSCPDispatcher;

/**
 * <p>The {@link WadoServer WadoServer} implements an HTTP server that responds to WADO GET requests
 * as defined by DICOM PS 3.18 (ISO 17432), which provides a standard web (http) interface through which to retrieve DICOM objects either
 * as DICOM files or as derived JPEG images.</p>
 *
 * <p>It does not respond to any other type of request. See also the {@link com.pixelmed.web.RequestTypeServer RequestTypeServer}
 * which in addition to servicing WADO requests, can provide lists of patients, studies and series that link to WADO URLs.</p>
 *
 * <p>It extends extends {@link com.pixelmed.web.HttpServer HttpServer} and implements
 * {@link HttpServer.Worker#generateResponseToGetRequest(String,OutputStream) generateResponseToGetRequest()}.</p>
 *
 * @see com.pixelmed.web.RequestTypeServer
 *
 * @author	dclunie
 */
public class WadoServer extends HttpServer {
	private static final String identString = "@(#) $Header: /userland/cvs/pixelmed/imgbook/com/pixelmed/web/WadoServer.java,v 1.23 2015/06/03 16:18:37 dclunie Exp $";

	private DatabaseInformationModel databaseInformationModel;
	
	protected class WADOWorker extends Worker {
		private WadoRequestHandler wadoRequestHandler = null;
				
		protected void generateResponseToGetRequest(String requestURI,OutputStream out) throws IOException {
			try {
				WebRequest request = new WebRequest(requestURI);
				String requestType = request.getRequestType();
				if (requestType != null && requestType.equals("WADO")) {
					if (wadoRequestHandler == null) {
						wadoRequestHandler = new WadoRequestHandler(null,webServerDebugLevel);
					}
					wadoRequestHandler.generateResponseToGetRequest(databaseInformationModel,null,null,request,null,out);
				}
				else {
					throw new Exception("Unrecognized requestType \""+requestType+"\"");
				}
			}
			catch (Exception e) {
				e.printStackTrace(System.err);
if (webServerDebugLevel > 0) System.err.println("WadoServer.WADOWorker.generateResponseToGetRequest(): Sending 404 Not Found");
				RequestHandler.send404NotFound(out,e.getMessage());
			}
		}
	}
	
	/***/
	protected Worker createWorker() {
		return new WADOWorker();
	}
	
	/***/
	private class OurReceivedObjectHandler extends ReceivedObjectHandler {
		/**
		 * @param	dicomFileName
		 * @param	transferSyntax
		 * @param	callingAETitle
		 * @throws	IOException
		 * @throws	DicomException
		 * @throws	DicomNetworkException
		 */
		public void sendReceivedObjectIndication(String dicomFileName,String transferSyntax,String callingAETitle)
				throws DicomNetworkException, DicomException, IOException {
			if (dicomFileName != null) {
if (webServerDebugLevel > 0) System.err.println("Received: "+dicomFileName+" from "+callingAETitle+" in "+transferSyntax);
				try {
					FileInputStream fis = new FileInputStream(dicomFileName);
					DicomInputStream i = new DicomInputStream(new BufferedInputStream(fis));
					AttributeList list = new AttributeList();
					list.read(i,TagFromName.PixelData);
					i.close();
					fis.close();
					databaseInformationModel.insertObject(list,dicomFileName,DatabaseInformationModel.FILE_COPIED);
				} catch (Exception e) {
					e.printStackTrace(System.err);
				}
			}

		}
	}
	
	public WadoServer(String dataBaseFileName,String savedImagesFolderName,int dicomPort,String calledAETitle,int storageSCPDebugLevel,int wadoPort,int webServerDebugLevel) {
		super(webServerDebugLevel);
		try {
			databaseInformationModel = new PatientStudySeriesConcatenationInstanceModel(dataBaseFileName);
			File savedImagesFolder = new File(savedImagesFolderName);
			if (!savedImagesFolder.exists()) {
				savedImagesFolder.mkdirs();
			}
			new Thread(new StorageSOPClassSCPDispatcher(dicomPort,calledAETitle,savedImagesFolder,new OurReceivedObjectHandler(),null,null,null,false,storageSCPDebugLevel)).start();
			super.initializeThreadPool(wadoPort);
		}
		catch (Exception e) {
			e.printStackTrace(System.err);
		}
	}
	
	/***/
	public static void main(String arg[]) {
		String dataBaseFileName = "/tmp/testwadodb";
		String savedImagesFolderName = "/tmp/testwadoimages";
		int dicomPort = 4007;
		String calledAETitle = "WADOTEST";
		int storageSCPDebugLevel = 0;
		int webServerDebugLevel = 0;
		int wadoPort = 7091;
		new Thread(new WadoServer(dataBaseFileName,savedImagesFolderName,dicomPort,calledAETitle,storageSCPDebugLevel,wadoPort,webServerDebugLevel)).start();
	}
}

