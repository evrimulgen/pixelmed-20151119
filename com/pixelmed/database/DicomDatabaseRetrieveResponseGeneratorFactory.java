/* Copyright (c) 2001-2015, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package com.pixelmed.database;

import com.pixelmed.query.RetrieveResponseGenerator;
import com.pixelmed.query.RetrieveResponseGeneratorFactory;

class DicomDatabaseRetrieveResponseGeneratorFactory implements RetrieveResponseGeneratorFactory {
	/***/
	private static final String identString = "@(#) $Header: /userland/cvs/pixelmed/imgbook/com/pixelmed/database/DicomDatabaseRetrieveResponseGeneratorFactory.java,v 1.2 2015/08/26 15:44:24 dclunie Exp $";
	/***/
	private int debugLevel;
	/***/
	private DatabaseInformationModel databaseInformationModel;

	DicomDatabaseRetrieveResponseGeneratorFactory(DatabaseInformationModel databaseInformationModel,int debugLevel) {
//System.err.println("DicomDatabaseRetrieveResponseGeneratorFactory():");
		this.debugLevel=debugLevel;
		this.databaseInformationModel=databaseInformationModel;
	}
	
	public RetrieveResponseGenerator newInstance() {
		return new DicomDatabaseRetrieveResponseGenerator(databaseInformationModel,debugLevel);
	}

}

