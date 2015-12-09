/* Copyright (c) 2001-2015, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package com.pixelmed.dicom;

import java.awt.Point;
import java.awt.Transparency;

import java.awt.color.ColorSpace;

import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import java.util.Iterator;
import java.util.Locale;

import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.spi.*;
import javax.imageio.event.IIOReadProgressListener;
import javax.imageio.metadata.IIOMetadata;

/**
 * <p>The {@link com.pixelmed.dicom.CompressedFrameDecoder CompressedFrameDecoder} class implements decompression of selected frames
 * in various supported Transfer Syntaxes once already extracted from DICOM encapsulated images.</p>
 *
 *
 * @author	dclunie
 */
public class CompressedFrameDecoder {

	/***/
	private static final String identString = "@(#) $Header: /userland/cvs/pixelmed/imgbook/com/pixelmed/dicom/CompressedFrameDecoder.java,v 1.9 2015/11/06 23:42:42 dclunie Exp $";
	
	private String transferSyntaxUID;
	private byte[][] frames;
	private int bytesPerSample;
	private ColorSpace colorSpace;
	
	private static boolean haveScannedForCodecs;

	public static void scanForCodecs() {
//System.err.println("CompressedFrameDecoder.scanForCodecs(): Scanning for ImageIO plugin codecs");
		ImageIO.scanForPlugins();
		ImageIO.setUseCache(false);		// disk caches are too slow :(
		haveScannedForCodecs=true;
	}
	
	//private boolean pixelDataWasLossy = false;			// set if decompressed from lossy transfer syntax during reading of Pixel Data attribute in this AttributeList instance
	//private String lossyMethod = null;
	private IIOMetadata[] iioMetadata = null;			// will be set during compression if reader is capable of it
	private boolean colorSpaceWillBeConvertedToRGBDuringDecompression = false;	// set if color space will be converted to RGB during compression
	
	private String readerWanted;

	private ImageReader reader = null;
	
	private int lastFrameDecompressed = -1;
	private IIOMetadata iioMetadataForLastFrameDecompressed = null;
	
	/**
	 * <p>Returns a whether or not a DICOM file contains pixel data that can be decompressed using this class.</p>
	 *
	 * @param	file	the file
	 * @return	true if file can be decompressed using this class
	 */
	public static boolean canDecompress(File file) {
//System.err.println("CompressedFrameDecoder.canDecompress(): file "+file);
		boolean canDecompressPixelData = false;
		AttributeList list = new AttributeList();
		try {
			list.readOnlyMetaInformationHeader(file);
			String transferSyntaxUID = Attribute.getSingleStringValueOrEmptyString(list,TagFromName.TransferSyntaxUID);
//System.err.println("CompressedFrameDecoder.canDecompress(): transferSyntaxUID "+transferSyntaxUID);
			if (transferSyntaxUID.equals(TransferSyntax.JPEGBaseline)
			 || transferSyntaxUID.equals(TransferSyntax.JPEGLossless)
			 || transferSyntaxUID.equals(TransferSyntax.JPEGLosslessSV1)
			) {		// Currently only JPEG Baseline in .50 TransferSyntax, because we haven't done 16 bit copy or BufferedImage (000786), and haven't factored out RLE here yet (000787)
				canDecompressPixelData = true;
			}
		}
		catch (DicomException e) {
			e.printStackTrace(System.err);
		}
		catch (IOException e) {
			e.printStackTrace(System.err);
		}
//System.err.println("CompressedFrameDecoder.canDecompress(): returns "+canDecompressPixelData);
		return canDecompressPixelData;
	}

	/**
	 * <p>Returns a reference to the {@link javax.imageio.metadata.IIOMetadata IIOMetadata} object for the selected frame, or null if none was available during reading. </p>
	 *
	 * @param	frame	the frame number, from 0
	 * @return	an {@link javax.imageio.metadata.IIOMetadata IIOMetadata} object, or null.
	 */
	public IIOMetadata getIIOMetadata(int frame) {
		return frame == lastFrameDecompressed ? iioMetadataForLastFrameDecompressed : null;
	}

	/**
	 * <p>Returns a whether or not the color space will be converted to RGB during compression if it was YBR in the first place.</p>
	 *
	 * @return	true if RGB after compression
	 */
	public boolean getColorSpaceConvertedToRGBDuringDecompression() {
		return colorSpaceWillBeConvertedToRGBDuringDecompression;
	}
	
	// compare this to AttributeList.extractCompressedPixelDataCharacteristics(), which handles RLE too, whereas here we handle JPEG as well
	private void chooseReaderWantedBasedOnTransferSyntax() {
		colorSpaceWillBeConvertedToRGBDuringDecompression = false;		// do not set this generally ... be specific to each scheme (00704)
		//pixelDataWasLossy=false;
		//lossyMethod=null;
		readerWanted = null;
//System.err.println("CompressedFrameDecoder.chooseReader(): TransferSyntax = "+transferSyntaxUID);
		if (transferSyntaxUID.equals(TransferSyntax.JPEGBaseline) || transferSyntaxUID.equals(TransferSyntax.JPEGExtended)) {
			readerWanted="JPEG";
			colorSpaceWillBeConvertedToRGBDuringDecompression = true;
			//pixelDataWasLossy=true;
			//lossyMethod="ISO_10918_1";
//System.err.println("CompressedFrameDecoder.chooseReader(): Undefined length encapsulated Pixel Data in JPEG Lossy");
		}
		else if (transferSyntaxUID.equals(TransferSyntax.JPEG2000)) {
			readerWanted="JPEG2000";
			colorSpaceWillBeConvertedToRGBDuringDecompression = true;
			//pixelDataWasLossy=true;
			//lossyMethod="ISO_15444_1";
//System.err.println("CompressedFrameDecoder.chooseReader(): Undefined length encapsulated Pixel Data in JPEG 2000");
		}
		else if (transferSyntaxUID.equals(TransferSyntax.JPEG2000Lossless)) {
			readerWanted="JPEG2000";
			colorSpaceWillBeConvertedToRGBDuringDecompression = true;
//System.err.println("CompressedFrameDecoder.chooseReader(): Undefined length encapsulated Pixel Data in JPEG 2000");
		}
		else if (transferSyntaxUID.equals(TransferSyntax.JPEGLossless) || transferSyntaxUID.equals(TransferSyntax.JPEGLosslessSV1)) {
			readerWanted="jpeg-lossless";
			colorSpaceWillBeConvertedToRGBDuringDecompression = false;		// NB. (00704)
//System.err.println("CompressedFrameDecoder.chooseReader(): Undefined length encapsulated Pixel Data in JPEG Lossless");
		}
		else if (transferSyntaxUID.equals(TransferSyntax.JPEGLS)) {
			readerWanted="jpeg-ls";
			colorSpaceWillBeConvertedToRGBDuringDecompression = false;		// Hmmm :(
//System.err.println("CompressedFrameDecoder.chooseReader(): Undefined length encapsulated Pixel Data in JPEG-LS");
		}
		else if (transferSyntaxUID.equals(TransferSyntax.JPEGNLS)) {
			readerWanted="jpeg-ls";
			colorSpaceWillBeConvertedToRGBDuringDecompression = false;		// Hmmm :(
			//pixelDataWasLossy=true;
			//lossyMethod="ISO_14495_1";
//System.err.println("CompressedFrameDecoder.chooseReader(): Undefined length encapsulated Pixel Data in JPEG-LS");
		}
		else {
			readerWanted="JPEG";
			colorSpaceWillBeConvertedToRGBDuringDecompression = true;
//System.err.println("CompressedFrameDecoder.chooseReader(): Unrecognized Transfer Syntax "+transferSyntaxUID+" for encapsulated PixelData - guessing "+readerWanted);
		}
//System.err.println("CompressedFrameDecoder.chooseReader(): Based on Transfer Syntax, colorSpaceWillBeConvertedToRGBDuringDecompression = "+colorSpaceWillBeConvertedToRGBDuringDecompression);
	}
	
	public static boolean isStandardJPEGReader(ImageReader reader) {
		return reader.getOriginatingProvider().getDescription(Locale.US).equals("Standard JPEG Image Reader") && (reader.getOriginatingProvider().getVendorName().equals("Sun Microsystems, Inc.") || reader.getOriginatingProvider().getVendorName().equals("Oracle Corporation"));
	}
	
	public static boolean isPixelMedLosslessJPEGReader(ImageReader reader) {
		return reader.getOriginatingProvider().getDescription(Locale.US).equals("PixelMed JPEG Lossless Image Reader");		// cannot reference com.pixelmed.imageio.JPEGLosslessImageReaderSpi.getDescription() because may not be available at compile time
	}
	
	public static ImageReader selectReaderFromCodecsAvailable(String readerWanted,String transferSyntaxUID,int bytesPerSample) throws DicomException {
		ImageReader reader = null;
		// Do NOT assume that first reader found is the best one ... check them all and make explicit choices ...
		// Cannot assume that they are returned in any particular order ...
		// Cannot assume that there are only two that match ...
		// Cannot assume that all of them are available on any platform or configuration ...
		//try {
		Iterator<ImageReader> it = ImageIO.getImageReadersByFormatName(readerWanted);
		while (it.hasNext()) {
			if (reader == null) {
				reader = it.next();
System.err.println("CompressedFrameDecoder.selectReaderFromCodecsAvailable(): First reader found is "+reader.getOriginatingProvider().getDescription(Locale.US)+" "+reader.getOriginatingProvider().getVendorName()+" "+reader.getOriginatingProvider().getVersion());
			}
			else {
				ImageReader otherReader = it.next();
System.err.println("CompressedFrameDecoder.selectReaderFromCodecsAvailable(): Found another reader "+otherReader.getOriginatingProvider().getDescription(Locale.US)+" "+otherReader.getOriginatingProvider().getVendorName()+" "+otherReader.getOriginatingProvider().getVersion());
				
				if (isStandardJPEGReader(reader)) {
					// prefer any other reader to the standard one, since the standard one is limited, and any other is most likely JAI JIIO
					reader = otherReader;
System.err.println("CompressedFrameDecoder.selectReaderFromCodecsAvailable(): Choosing reader "+reader.getOriginatingProvider().getDescription(Locale.US)+" "+reader.getOriginatingProvider().getVendorName()+" "+reader.getOriginatingProvider().getVersion()+" over Standard JPEG Image Reader");
				}
				else if (isPixelMedLosslessJPEGReader(reader)) {
System.err.println("CompressedFrameDecoder.selectReaderFromCodecsAvailable(): Choosing reader "+reader.getOriginatingProvider().getDescription(Locale.US)+" "+reader.getOriginatingProvider().getVendorName()+" "+reader.getOriginatingProvider().getVersion()+" over any other reader");
					break;
				}
				else if (isPixelMedLosslessJPEGReader(otherReader)) {
					reader = otherReader;
System.err.println("CompressedFrameDecoder.selectReaderFromCodecsAvailable(): Choosing reader "+reader.getOriginatingProvider().getDescription(Locale.US)+" "+reader.getOriginatingProvider().getVendorName()+" "+reader.getOriginatingProvider().getVersion()+" over any other reader");
					break;
				}
			}
		}
		if (reader != null) {
			// The JAI JIIO JPEG reader is OK since it handles both 8 and 12 bit JPEGExtended, but the "standard" reader that comes with the JRE only supports 8 bit
			// Arguably 8 bits in JPEGExtended is not valid (PS3.5 10.2) but since it is sometimes encountered, deal with it if we can, else throw specific exception ...
			if (transferSyntaxUID.equals(TransferSyntax.JPEGExtended) && bytesPerSample > 1 && reader.getOriginatingProvider().getDescription(Locale.US).equals("Standard JPEG Image Reader") && (reader.getOriginatingProvider().getVendorName().equals("Sun Microsystems, Inc.") || reader.getOriginatingProvider().getVendorName().equals("Oracle Corporation"))) {
				throw new DicomException("Reader "+reader.getOriginatingProvider().getDescription(Locale.US)+" "+reader.getOriginatingProvider().getVendorName()+" "+reader.getOriginatingProvider().getVersion()+" does not support extended lossy JPEG Transfer Syntax "+transferSyntaxUID+" other than for 8 bit data");
			}
System.err.println("CompressedFrameDecoder.selectReaderFromCodecsAvailable(): Using reader "+reader.getOriginatingProvider().getDescription(Locale.US)+" "+reader.getOriginatingProvider().getVendorName()+" "+reader.getOriginatingProvider().getVersion());
		}
		else {
			//CapabilitiesAvailable.dumpListOfAllAvailableReaders(System.err);
			throw new DicomException("No reader for "+readerWanted+" available for Transfer Syntax "+transferSyntaxUID);
		}
		//}
		//catch (Exception e) {
		//	e.printStackTrace(System.err);
		//	CapabilitiesAvailable.dumpListOfAllAvailableReaders(System.err);
		//	throw new DicomException("No reader for "+readerWanted+" available for Transfer Syntax "+transferSyntaxUID+"\nCaused by: "+e);
		//}
		return reader;
	}
	
	public CompressedFrameDecoder(String transferSyntaxUID,byte[][] frames,int bytesPerSample,ColorSpace colorSpace) throws DicomException {
		if (frames == null)  {
			throw new DicomException("no array of compressed data per frame supplied to decompress");
		}
		this.transferSyntaxUID = transferSyntaxUID;
		this.frames = frames;
		this.bytesPerSample = bytesPerSample;
		this.colorSpace = colorSpace;

		scanForCodecs();
		
		chooseReaderWantedBasedOnTransferSyntax();
//System.err.println("CompressedFrameDecoder(): Based on Transfer Syntax, colorSpaceWillBeConvertedToRGBDuringDecompression = "+colorSpaceWillBeConvertedToRGBDuringDecompression);
		if (readerWanted != null) {
			reader = selectReaderFromCodecsAvailable(readerWanted,transferSyntaxUID,bytesPerSample);
		}
		else {
//System.err.println("CompressedFrameDecoder(): Unrecognized Transfer Syntax "+transferSyntaxUID+" for encapsulated PixelData");
			throw new DicomException("Unrecognized Transfer Syntax "+transferSyntaxUID+" for encapsulated PixelData");
		}
	}

	public BufferedImage getDecompressedFrameAsBufferedImage(int f) throws DicomException, IOException {
//System.err.println("CompressedFrameDecoder.getDecompressedFrameAsBufferedImage(): Starting frame "+f);
		BufferedImage image = null;
		ImageInputStream iiois = ImageIO.createImageInputStream(new ByteArrayInputStream(frames[f]));
		reader.setInput(iiois,true/*seekForwardOnly*/,true/*ignoreMetadata*/);
										
//System.err.println("CompressedFrameDecoder.getDecompressedFrameAsBufferedImage(): Calling reader.readAll()");
		IIOImage iioImage = null;
		try {
			iioImage = reader.readAll(0,null/*ImageReadParam*/);
		}
		catch (IIOException e) {
			e.printStackTrace(System.err);
//System.err.println("CompressedFrameDecoder.getDecompressedFrameAsBufferedImage(): \""+e.toString()+"\"");
			if (transferSyntaxUID.equals(TransferSyntax.JPEGBaseline) && reader.getOriginatingProvider().getDescription(Locale.US).equals("Standard JPEG Image Reader") && (reader.getOriginatingProvider().getVendorName().equals("Sun Microsystems, Inc.") || reader.getOriginatingProvider().getVendorName().equals("Oracle Corporation"))
			 && e.toString().equals("javax.imageio.IIOException: Inconsistent metadata read from stream")) {
				throw new DicomException("Reader "+reader.getOriginatingProvider().getDescription(Locale.US)+" "+reader.getOriginatingProvider().getVendorName()+" "+reader.getOriginatingProvider().getVersion()+" does not support JPEG images with components numbered from 0");
			}
		}
//System.err.println("CompressedFrameDecoder.getDecompressedFrameAsBufferedImage(): Back from frame reader.readAll()");
		if (iioImage == null) {
			throw new DicomException("Reader "+reader.getOriginatingProvider().getDescription(Locale.US)+" "+reader.getOriginatingProvider().getVendorName()+" "+reader.getOriginatingProvider().getVersion()
				+" returned null image for Transfer Syntax "+transferSyntaxUID);
		}
		else {
			lastFrameDecompressed = f;
			iioMetadataForLastFrameDecompressed = iioImage.getMetadata();
			image = (BufferedImage)(iioImage.getRenderedImage());
//System.err.println("CompressedFrameDecoder.getDecompressedFrameAsBufferedImage(): Back from frame "+f+" reader.read(), BufferedImage="+image);
			if (image == null) {
				throw new DicomException("Reader "+reader.getOriginatingProvider().getDescription(Locale.US)+" "+reader.getOriginatingProvider().getVendorName()+" "+reader.getOriginatingProvider().getVersion()
					+" returned null image for Transfer Syntax "+transferSyntaxUID);
			}
			else {
				image = makeNewBufferedImageIfNecessary(image,colorSpace);		// not really sure why we have to do this, but works around different YBR result from standard versus native JPEG codec (000785) :(
			}
		}
//System.err.println("CompressedFrameDecoder.getDecompressedFrameAsBufferedImage(): returning image = "+image);
		return image;
	}
	
	private BufferedImage makeNewBufferedImageIfNecessary(BufferedImage image,ColorSpace colorSpace) {
//System.err.print("CompressedFrameDecoder.makeNewBufferedImage(): starting with BufferedImage: ");
//com.pixelmed.display.BufferedImageUtilities.describeImage(image,System.err);
		BufferedImage newImage = null;
		Raster raster = image.getData();
		if (raster.getTransferType() == DataBuffer.TYPE_BYTE && raster.getNumBands() > 1) {		// we only need to do this for color not grayscale, and the models we are about to create contain 3 bands
			int w = raster.getWidth();
			int h = raster.getHeight();
			byte[] data = (byte[])(raster.getDataElements(0,0,w,h,null));	// do NOT use form without width and height
			if (data != null) {
				ComponentColorModel cm=new ComponentColorModel(colorSpace,
															   new int[] {8,8,8},
															   false,		// has alpha
															   false,		// alpha premultipled
															   Transparency.OPAQUE,
															   DataBuffer.TYPE_BYTE
															   );
				
				// pixel interleaved
				ComponentSampleModel sm = new ComponentSampleModel(DataBuffer.TYPE_BYTE,
																   w,
																   h,
																   3,
																   w*3,
																   new int[] {0,1,2}
																   );

				// band interleaved
				//ComponentSampleModel sm = new ComponentSampleModel(DataBuffer.TYPE_BYTE,
				//												   w,
				//												   h,
				//												   1,
				//												   w,
				//												   new int[] {0,w*h,w*h*2}
				//											   );
																
				DataBuffer buf = new DataBufferByte(data,w,0/*offset*/);
				
				WritableRaster wr = Raster.createWritableRaster(sm,buf,new Point(0,0));
				
				newImage = new BufferedImage(cm,wr,true,null);	// no properties hash table
//System.err.print("CompressedFrameDecoder.makeNewBufferedImage(): returns new BufferedImage: ");
//com.pixelmed.display.BufferedImageUtilities.describeImage(newImage,System.err);
			}
		}
		return newImage == null ? image : newImage;
	}

		public void dispose() throws Throwable {
//System.err.println("CompressedFrameDecoder.dispose()");
			if (reader != null) {
				try {
//System.err.println("CompressedFrameDecoder.dispose(): Calling dispose() on reader");
					reader.dispose();	// http://info.michael-simons.eu/2012/01/25/the-dangers-of-javas-imageio/
				}
				catch (Exception e) {
					e.printStackTrace(System.err);
				}
			}
		}

}
