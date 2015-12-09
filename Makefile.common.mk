#
# Note that PATHTOROOT must have been specified prior to including this file
#

PATHTOHOME = /Users/dclunie

JUNITJAR = ${PATHTOROOT}/lib/junit/junit-4.8.1.jar

ADDITIONALJARDIRINROOT = lib/additional

PATHTODCTOOLSUPPORTFROMROOT = ../../dctool.support

PATHTODCTOOLSFROMROOT = ../../dicom3tools

PATHTOSCPECGSAMPLESFROMROOT = ../../../Documents/Medical/stuff/ECG/OpenECG

PATHTOTESTFILESFROMROOT = ./testpaths

PATHTOTESTRESULTSFROMROOT = ./testresults

PATHTOADDITIONAL = ${PATHTOROOT}/${ADDITIONALJARDIRINROOT}

PATHTODCTOOLSUPPORT = ${PATHTOROOT}/${PATHTODCTOOLSUPPORTFROMROOT}

PATHTODCTOOLS = ${PATHTOROOT}/${PATHTODCTOOLSFROMROOT}

BZIP2ADDITIONALJAR = ${PATHTOADDITIONAL}/commons-compress-1.9.jar

JIIOADDITIONALJARS = ${PATHTOADDITIONAL}/jai_imageio.jar

VECMATHADDITIONALJAR = ${PATHTOADDITIONAL}/vecmath1.2-1.14.jar

# commons-compress for bzip2 not needed for compile, but useful for execution if available ...
# commons-codec not needed for compile, but useful for execution if available ...
DICOMADDITIONALJARS = ${BZIP2ADDITIONALJAR}:${PATHTOADDITIONAL}/commons-codec-1.3.jar:${VECMATHADDITIONALJAR}

DISPLAYADDITIONALJARS = ${DICOMADDITIONALJARS}:${JIIOADDITIONALJARS}

DATABASEADDITIONALJARS = ${PATHTOADDITIONAL}/hsqldb.jar

FTPADDITIONALJARS = ${PATHTOADDITIONAL}/commons-net-ftp-2.0.jar

NETWORKADDITIONALJARS = ${PATHTOADDITIONAL}/jmdns.jar

VIEWERADDITIONALJARS = ${DISPLAYADDITIONALJARS}:${DATABASEADDITIONALJARS}:${NETWORKADDITIONALJARS}

SERVERADDITIONALJARS = ${VIEWERADDITIONALJARS}

#JPEGBLOCKREDACTIONJAR = $${HOME}/work/codec/pixelmed_codec.jar
JPEGBLOCKREDACTIONJAR = ${PATHTOADDITIONAL}/pixelmed_codec.jar

#PIXELMEDIMAGEIOJAR = $${HOME}/work/codec/pixelmed_imageio.jar
PIXELMEDIMAGEIOJAR = ${PATHTOADDITIONAL}/pixelmed_imageio.jar

JAVAVERSIONTARGET=1.7

JAVACTARGETOPTIONS=-target ${JAVAVERSIONTARGET} -source ${JAVAVERSIONTARGET} -bootclasspath $${JAVAVERSIONTARGETJARFILE}

.SUFFIXES:	.class .java .ico .png

# -XDignore.symbol.file needed to find "package com.sun.image.codec.jpeg" ("http://stackoverflow.com/questions/1906673/import-com-sun-image-codec-jpeg")
JAVACOPTIONS = -O ${JAVACTARGETOPTIONS} -encoding "UTF8" -Xlint:deprecation -XDignore.symbol.file -Xdiags:verbose

.java.class:
	export JAVAVERSIONTARGETJARFILE=`/usr/libexec/java_home -v ${JAVAVERSIONTARGET} | tail -1`/jre/lib/rt.jar; javac ${JAVACOPTIONS} \
		-classpath ${PATHTOROOT}:${DICOMADDITIONALJARS}:${VIEWERADDITIONALJARS}:${FTPADDITIONALJARS}:${JUNITJAR} \
		-sourcepath ${PATHTOROOT} $<

.png.ico:
	# http://www.winterdrache.de/freeware/png2ico/
	png2ico $@ $<

clean:
	rm -f *~ *.class core *.bak test.*

