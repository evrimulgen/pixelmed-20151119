/* Copyright (c) 2001-2015, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package com.pixelmed.database;

import com.pixelmed.query.QueryResponseGenerator;
import com.pixelmed.query.QueryResponseGeneratorFactory;

class DicomDatabaseQueryResponseGeneratorFactory implements QueryResponseGeneratorFactory {
	/***/
	private static final String identString = "@(#) $Header: /userland/cvs/pixelmed/imgbook/com/pixelmed/database/DicomDatabaseQueryResponseGeneratorFactory.java,v 1.2 2015/08/26 15:44:24 dclunie Exp $";
	/***/
	private int debugLevel;
	/***/
	private DatabaseInformationModel databaseInformationModel;

	DicomDatabaseQueryResponseGeneratorFactory(DatabaseInformationModel databaseInformationModel,int debugLevel) {
//System.err.println("DicomDatabaseQueryResponseGeneratorFactory():");
		this.debugLevel=debugLevel;
		this.databaseInformationModel=databaseInformationModel;
	}
	
	public QueryResponseGenerator newInstance() {
		return new DicomDatabaseQueryResponseGenerator(databaseInformationModel,debugLevel);
	}
}

