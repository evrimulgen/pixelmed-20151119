/* Copyright (c) 2001-2015, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package com.pixelmed.display;

import java.awt.*; 
import java.awt.event.*;
import java.awt.image.*; 
import java.awt.color.*; 
import java.util.*; 
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.ShortBuffer;
import javax.swing.*; 
import javax.swing.event.*;

import com.pixelmed.dicom.*;

/**
 * <p>A class that encapsulates the pixel data and features and values from an image source
 * (such as a DICOM image), usually for the purpose of displaying it.</p>
 *
 * @see com.pixelmed.display.SingleImagePanel
 *
 * @author	dclunie
 */
public class SourceImage {

	/***/
	private static final String identString = "@(#) $Header: /userland/cvs/pixelmed/imgbook/com/pixelmed/display/SourceImage.java,v 1.102 2015/10/23 15:57:03 dclunie Exp $";
	
	protected static boolean allowMemoryMapping = true;
	protected static boolean allowMemoryMappingFromMultiplePerFrameFiles = false;
	protected static boolean allowDeferredReadFromFileIfNotMemoryMapped = true;
	
	/**
	 * <p>Static method to set whether or not to allow memory mapping to be used at all when subsequently
	 * constructing SourceImage objects from attributes whose pixel data has been left on disk
	 * rather than read or constructed in memory.</p>
	 *
	 * <p>Default is to allow memory mapping.</p>
	 *
	 * <p>Performance will generally be suprior with memory mapping turned on, and less heap
	 * will be used, but apparent issues with native memory allocation and revovery after a failed
	 * mapping on some platforms may require disabling it.</p>
	 *
	 * @param	allowMemoryMapping		true or false to allow or disallow memory mapping, respectively
	 */
	public static void setAllowMemoryMapping(boolean allowMemoryMapping) {
		SourceImage.allowMemoryMapping = allowMemoryMapping;
	}
	
	/**
	 * <p>Static method to set whether or not to allow memory mapping to be used in the special
	 * case that the images on disk have their frames spread across separate files rather
	 * than one large contiguous file.</p>
	 *
	 * <p>Default is to to disable memory mapping in this case.</p>
	 *
	 * @param	allowMemoryMappingFromMultiplePerFrameFiles		true or false to allow or disallow memory mapping of per-frame files, respectively
	 */
	public static void setAllowMemoryMappingFromMultiplePerFrameFiles(boolean allowMemoryMappingFromMultiplePerFrameFiles) {
		SourceImage.allowMemoryMappingFromMultiplePerFrameFiles = allowMemoryMappingFromMultiplePerFrameFiles;
	}
	
	/**
	 * <p>Static method to set whether or not to reading of per-frame data from a file to be deferred when subsequently
	 * constructing SourceImage objects from attributes whose pixel data has been left on disk
	 * rather than all frames to be read and constructed in memory.</p>
	 *
	 * <p>Does not apply if memory mapping is enabled and succeeds.</p>
	 *
	 * <p>Default is to to allow deferred reading.</p>
	 *
	 * <p>Reading of the pixel data for a frame (and allocation of memory for it) is deferred until {@link com.pixelmed.display.SourceImage#getBufferedImage(int) SourceImage.getBufferedImage(int)} is called.</p>
	 *
	 * @param	allowDeferredReadFromFileIfNotMemoryMapped		true or false to allow or disallow deferred read of per-frame data from file, respectively
	 */
	public static void setAllowDeferredReadFromFileIfNotMemoryMapped(boolean allowDeferredReadFromFileIfNotMemoryMapped) {
		SourceImage.allowDeferredReadFromFileIfNotMemoryMapped = allowDeferredReadFromFileIfNotMemoryMapped;
	}

	/***/
	BufferedImage[] imgs;
	/***/
	int width;
	/***/
	int height;
	/***/
	int nframes;
	/***/
	double imgMin;
	/***/
	double imgMax;
	/***/
	double imgMean;
	/***/
	double imgStandardDeviation;
	/***/
	boolean signed;
	/***/
	boolean inverted;
	/***/
	int mask;
	/***/
	boolean isGrayscale;
	/***/
	boolean isPaletteColor;
	/***/
	boolean isYBR;
	/***/
	int pad;
	/***/
	int padRangeLimit;
	/***/
	boolean hasPad;
	/***/
	boolean useMaskedPadRange;
	/***/
	int useMaskedPadRangeStart;
	/***/
	int useMaskedPadRangeEnd;
	/***/
	boolean useNonMaskedSinglePadValue;
	/***/
	int nonMaskedSinglePadValue;
	/***/
	int backgroundValue;
	/***/
	String title;

	// stuff for (supplemental) palette color LUT
	/***/
	private int largestGray;
	/***/
	private int firstValueMapped;
	/***/
	private int numberOfEntries;
	/***/
	private int bitsPerEntry;
	/***/
	private short redTable[];
	/***/
	private short greenTable[];
	/***/
	private short blueTable[];
	
	/***/
	private SUVTransform suvTransform;
	
	/***/
	private RealWorldValueTransform realWorldValueTransform;

	/***/
	private ModalityTransform modalityTransform;

	/***/
	private VOITransform voiTransform;

	/***/
	private DisplayShutter displayShutter;

	/***/
	private Overlay overlay;

	/***/
	private ColorSpace srcColorSpace;
	/***/
	private ColorSpace dstColorSpace;

	/**
	 * @param	w
	 * @param	h
	 * @param	data
	 * @param	offset
	 */
	private static BufferedImage createUnsignedShortGrayscaleImage(int w,int h,short data[],int offset) {
//System.err.println("createUnsignedShortGrayscaleImage");
		ComponentColorModel cm=new ComponentColorModel(
			ColorSpace.getInstance(ColorSpace.CS_GRAY),
			new int[] {16},
			false,		// has alpha
			false,		// alpha premultipled
			Transparency.OPAQUE,
			DataBuffer.TYPE_USHORT
		);

		ComponentSampleModel sm = new ComponentSampleModel(
			DataBuffer.TYPE_USHORT,
			w,
			h,
			1,
			w,
			new int[] {0}
		);

		DataBuffer buf = new DataBufferUShort(data,w,offset);

		WritableRaster wr = Raster.createWritableRaster(sm,buf,new Point(0,0));

		return new BufferedImage(cm,wr,true,null);	// no properties hash table
	}

	/**
	 * @param	w
	 * @param	h
	 * @param	data
	 * @param	offset
	 */
	private static BufferedImage createSignedShortGrayscaleImage(int w,int h,short data[],int offset) {
//System.err.println("createSignedShortGrayscaleImage");
		// DataBufferUShort and DataBuffer.TYPE_USHORT are used here,
		// otherwise lookup table operations for windowing fail;
		// concept of signedness is conveyed separately;
		// causes issues with JAI operations expecting signed shorts
		ComponentColorModel cm=new ComponentColorModel(
			ColorSpace.getInstance(ColorSpace.CS_GRAY),
			new int[] {16},
			false,		// has alpha
			false,		// alpha premultipled
			Transparency.OPAQUE,
			/*DataBuffer.TYPE_SHORT*/DataBuffer.TYPE_USHORT
		);

		ComponentSampleModel sm = new ComponentSampleModel(
			/*DataBuffer.TYPE_SHORT*/DataBuffer.TYPE_USHORT,
			w,
			h,
			1,
			w,
			new int[] {0}
		);

		DataBuffer buf = new /*DataBufferShort*/DataBufferUShort(data,w,offset);

		WritableRaster wr = Raster.createWritableRaster(sm,buf,new Point(0,0));

		return new BufferedImage(cm,wr,true,null);	// no properties hash table
	}

	/**
	 * @param	w
	 * @param	h
	 * @param	data
	 * @param	offset
	 */
	private static BufferedImage createByteGrayscaleImage(int w,int h,byte data[],int offset) {
//System.err.println("createByteGrayscaleImage");
		ComponentColorModel cm=new ComponentColorModel(
			ColorSpace.getInstance(ColorSpace.CS_GRAY),
			new int[] {8},
			false,		// has alpha
			false,		// alpha premultipled
			Transparency.OPAQUE,
			DataBuffer.TYPE_BYTE
		);

		ComponentSampleModel sm = new ComponentSampleModel(
			DataBuffer.TYPE_BYTE,
			w,
			h,
			1,
			w,
			new int[] {0}
		);

		DataBuffer buf = new DataBufferByte(data,w,offset);
                
		WritableRaster wr = Raster.createWritableRaster(sm,buf,new Point(0,0));
                
		return new BufferedImage(cm,wr,true,null);	// no properties hash table
	}

	/**
	 * @param	w
	 * @param	h
	 * @param	data
	 * @param	offset
	 */
	private static BufferedImage createPixelInterleavedByteRGBImage(int w,int h,byte data[],int offset,ColorSpace colorSpace) {
//System.err.println("createPixelInterleavedByteRGBImage");
		ComponentColorModel cm=new ComponentColorModel(
			colorSpace,
			new int[] {8,8,8},
			false,		// has alpha
			false,		// alpha premultipled
			Transparency.OPAQUE,
			DataBuffer.TYPE_BYTE
		);

		ComponentSampleModel sm = new ComponentSampleModel(
			DataBuffer.TYPE_BYTE,
			w,
			h,
			3,
			w*3,
			new int[] {0,1,2}
		);
		//PixelInterleavedSampleModel sm = new PixelInterleavedSampleModel(
		//	DataBuffer.TYPE_BYTE,
		//	w,
		//	h,
		//	3,
		//	w*3,
		//	new int[] {0,1,2}
		//);

		DataBuffer buf = new DataBufferByte(data,w,offset);

		WritableRaster wr = Raster.createWritableRaster(sm,buf,new Point(0,0));

		return new BufferedImage(cm,wr,true,null);	// no properties hash table
	}

	/**
	 * @param	w
	 * @param	h
	 * @param	data
	 * @param	offset
	 */
	private static BufferedImage createBandInterleavedByteRGBImage(int w,int h,byte data[],int offset,ColorSpace colorSpace) {
//System.err.println("createBandInterleavedByteRGBImage");
		ComponentColorModel cm=new ComponentColorModel(
			colorSpace,
			new int[] {8,8,8},
			false,		// has alpha
			false,		// alpha premultipled
			Transparency.OPAQUE,
			DataBuffer.TYPE_BYTE
		);

		ComponentSampleModel sm = new ComponentSampleModel(
			DataBuffer.TYPE_BYTE,
			w,
			h,
			1,
			w,
			new int[] {0,w*h,w*h*2}
		);
		//BandedSampleModel sm = new BandedSampleModel(		// doesn't work
		//	DataBuffer.TYPE_BYTE,
		//	w,
		//	h,
		//	w,
		//	new int[] {0,1,2},				// what should this be ?
		//	new int[] {0,w*h,w*h*2}
		//);

		DataBuffer buf = new DataBufferByte(data,w,offset);

		WritableRaster wr = Raster.createWritableRaster(sm,buf,new Point(0,0));

		return new BufferedImage(cm,wr,true,null);	// no properties hash table
	}

	/**
	 * @param	w
	 * @param	h
	 * @param	data
	 * @param	offset
	 */
	private static BufferedImage createFloatGrayscaleImage(int w,int h,float data[],int offset) {
//System.err.println("createFloatGrayscaleImage");
		ComponentColorModel cm=new ComponentColorModel(
			ColorSpace.getInstance(ColorSpace.CS_GRAY),
			false,		// has alpha
			false,		// alpha premultipled
			Transparency.OPAQUE,
			DataBuffer.TYPE_FLOAT
		);

		ComponentSampleModel sm = new ComponentSampleModel(
			DataBuffer.TYPE_FLOAT,
			w,
			h,
			1,
			w,
			new int[] {0}
		);

		DataBuffer buf = new DataBufferFloat(data,w,offset);

		WritableRaster wr = Raster.createWritableRaster(sm,buf,new Point(0,0));

		return new BufferedImage(cm,wr,true,null);	// no properties hash table
	}

	/**
	 * @param	w
	 * @param	h
	 * @param	data
	 * @param	offset
	 */
	private static BufferedImage createDoubleGrayscaleImage(int w,int h,double data[],int offset) {
//System.err.println("createDoubleGrayscaleImage");
		ComponentColorModel cm=new ComponentColorModel(
			ColorSpace.getInstance(ColorSpace.CS_GRAY),
			false,		// has alpha
			false,		// alpha premultipled
			Transparency.OPAQUE,
			DataBuffer.TYPE_DOUBLE
		);

		ComponentSampleModel sm = new ComponentSampleModel(
			DataBuffer.TYPE_DOUBLE,
			w,
			h,
			1,
			w,
			new int[] {0}
		);

		DataBuffer buf = new DataBufferDouble(data,w,offset);

		WritableRaster wr = Raster.createWritableRaster(sm,buf,new Point(0,0));

		return new BufferedImage(cm,wr,true,null);	// no properties hash table
	}

	/**
	 * <p>Allow subclasses to have their own constructors.</p>
	 */
	protected SourceImage() {
	}

	/**
	 * <p>Construct a single-frame image from an unsigned raw image (no header) on
	 * an input stream with the specified width, height and bit depth.</p>
	 *
	 * @param	i		the input stream
	 * @param	w		image width
	 * @param	h		image height
	 * @param	depth		bit depth
	 * @throws	IOException
	 */
	public SourceImage(InputStream i,int w,int h,int depth) throws IOException {
		constructSourceImage(i,w,h,depth,1);
	}

	/**
	 * <p>Construct a multi-frame image from an unsigned raw image (no header) on
	 * an input stream with the specified width, height and bit depth.</p>
	 *
	 * @param	i		the input stream
	 * @param	w		image width
	 * @param	h		image height
	 * @param	depth		bit depth
	 * @param	frames		number of frames
	 * @throws	IOException
	 */
	public SourceImage(InputStream i,int w,int h,int depth,int frames) throws IOException {
		constructSourceImage(i,w,h,depth,frames);
	}

	/**
	 * @param	i
	 * @param	w
	 * @param	h
	 * @param	depth
	 * @param	frames
	 * @throws	IOException
	 */
	private void constructSourceImage(InputStream i,int w,int h,int depth,int frames) throws IOException {

		width = w;
		height = h;
		nframes=frames;
		if (nframes < 1) return;

		imgs=new BufferedImage[nframes];

		imgMin=65536;
		imgMax=0;
		

		for (int frame=0; frame < nframes; ++frame) {
			if (depth > 8) {
				largestGray=65535;
				short data[] = new short[width*height];
				byte buffer[] = new byte[width*height*2];
				i.read(buffer,0,width*height*2);
				int count=0;
				for (int row=0; row<height; ++row) {
					for (int col=0; col<width; ++col) {
						//int byte1=i.read();
						//int byte2=i.read();
						//if (byte1 == -1 || byte2 == -1) throw new EOFException();
						int byte1=((int)buffer[count++])&0xff;
						int byte2=((int)buffer[count++])&0xff;
						short value=(short)((byte2<<8) + byte1);
						data[row*w+col]=(short)value;
						//if (value > imgMax) imgMax=value;
						if (value > imgMax && value <= largestGray) imgMax=value;
						if (value < imgMin) imgMin=value;
					}
				}
				imgs[frame]=createUnsignedShortGrayscaleImage(width,height,data,0);
			}
			else {
				largestGray=255;
				byte data[] = new byte[width*height];
				i.read(data,0,width*height);
				int count=0;
				for (int row=0; row<height; ++row) {
					for (int col=0; col<width; ++col) {
						//int value=i.read();
						//if (value == -1) throw new EOFException();
						//data[row*w+col]=(byte)value;
						int value=((int)data[count++])&0xff;
						//if (value > imgMax) imgMax=value;
						if (value > imgMax && value <= largestGray) imgMax=value;
						if (value < imgMin) imgMin=value;
					}
				}
				imgs[frame]=createByteGrayscaleImage(width,height,data,0);
			}
		}
//System.err.println("SourceImage.constructSourceImage(): imgMin = "+imgMin);
//System.err.println("SourceImage.constructSourceImage(): imgMax = "+imgMax);
	}

	/**
	 * <p>Construct an image from a single or multi-frame DICOM image from
	 * a file.</p>
	 *
	 * @param		filename
	 * @throws	IOException
	 * @throws	DicomException
	 */
	public SourceImage(String filename) throws IOException, DicomException {
		AttributeList list = new AttributeList();
		list.read(filename);
//System.err.println(list);

		if (list.getPixelData() != null) constructSourceImage(list);
	}

	/**
	 * <p>Construct an image from a single or multi-frame DICOM image from
	 * an input stream (such as from a file or the network).</p>
	 *
	 * @param	i		the input stream
	 * @throws	IOException
	 * @throws	DicomException
	 */
	public SourceImage(DicomInputStream i) throws IOException, DicomException {
		AttributeList list = new AttributeList();
		list.read(i);
//System.err.println(list);

		if (list.getPixelData() != null) constructSourceImage(list);
	}

	/**
	 * <p>Construct an image from a single or multi-frame DICOM image from
	 * a list of DICOM attributes.</p>
	 *
	 * @param	list		the list of attributes that include the description and values of the pixel data
	 * @throws	DicomException
	 */
	public SourceImage(AttributeList list) throws DicomException {
		if (list.getPixelData() != null) constructSourceImage(list);
	}

	private static byte[] getByteArrayForFrameFromMultiFrameShortArray(int index,short[] shortData,int nframesamples) {
//System.err.println("SourceImage.getByteArrayForFrameFromMultiFrameShortArray(): getting frame "+index+" as byte array from contiguous short allocated memory for all frames");
		byte[] useData = new byte[nframesamples];
		int slen=nframesamples/2;
		int scount=0;
		int soffset=slen*index;
		int count=0;
		while (scount<slen) {
			int value=((int)shortData[soffset++])&0xffff;	// the endianness of the TS has already been accounted for
			int value1=value&0xff;							// now just unpack from low part of word first
			useData[count++]=(byte)value1;
			int value2=(value>>8)&0xff;
			useData[count++]=(byte)value2;
			++scount;
		}
		return useData;
	}

	private static byte[] getByteArrayForFrameFromSingleFrameShortArray(short[] shortData,int nframesamples) {
//System.err.println("SourceImage.getByteArrayForFrameFromMultiFrameShortArray(): byte array from short array for individual frame");
		byte[] useData = new byte[nframesamples];
		int slen=nframesamples/2;
		int scount=0;
		int count=0;
		while (scount<slen) {
			int value=((int)shortData[scount++])&0xffff;	// the endianness of the TS has already been accounted for
			int value1=value&0xff;						// now just unpack from low part of word first
			useData[count++]=(byte)value1;
			int value2=(value>>8)&0xff;
			useData[count++]=(byte)value2;
		}
		return useData;
	}
	
	private abstract class BufferedImageSource {
		protected int nframesamples;
		private int cachedIndex;
		BufferedImage cachedBufferedImage;

		BufferedImageSource(int nframesamples) {
			this.nframesamples=nframesamples;
			cachedIndex=-1;
			cachedBufferedImage = null;
		}
		
		protected void finalize() throws Throwable {
//System.err.println("BufferedImageSource.finalize()");
			cachedBufferedImage=null;
			super.finalize();
		}
		
		public BufferedImage getBufferedImage(int index) {
			if (index != cachedIndex) {
				cachedBufferedImage = getUncachedBufferedImage(index);
				if (cachedBufferedImage != null) {
					cachedIndex = index;
				}
				else {
					cachedIndex=-1;
				}
			}
			return cachedBufferedImage;
		}

		abstract public BufferedImage getUncachedBufferedImage(int index);

		public double getMinimumPixelValueOfMostRecentBufferedImage(double oldMin) { return oldMin; }
		public double getMaximumPixelValueOfMostRecentBufferedImage(double oldMax) { return oldMax; }
	}

	private abstract class ShortBufferedImageSource extends BufferedImageSource {
		protected short data[];
		protected short dataPerFrame[][];
		protected ShortBuffer[] buffers;
		protected File file;
		protected long[] byteOffsets;
		protected boolean bigEndian;
		protected CompressedFrameDecoder decoder;
		protected boolean minMaxSet;
		protected int imgMin;
		protected int imgMax;

		public double getMinimumPixelValueOfMostRecentBufferedImage(double oldMin) { return minMaxSet ? ((double)imgMin) : oldMin; }
		public double getMaximumPixelValueOfMostRecentBufferedImage(double oldMax) { return minMaxSet ? ((double)imgMax) : oldMax; }

		ShortBufferedImageSource(short data[],int width,int height,int samples) {
			super(width*height*samples);
			this.data=data;
			minMaxSet=false;
		}
		
		ShortBufferedImageSource(short dataPerFrame[][],int width,int height,int samples) {
			super(width*height*samples);
			this.dataPerFrame=dataPerFrame;
		}

		ShortBufferedImageSource(ShortBuffer[] buffers,int width,int height,int samples) {
			super(width*height*samples);
			this.buffers=buffers;
			minMaxSet=false;
		}

		ShortBufferedImageSource(File file,long[] byteOffsets,boolean bigEndian,int width,int height,int samples) {
			super(width*height*samples);
			this.file=file;
			this.byteOffsets=byteOffsets;
			this.bigEndian=bigEndian;
			minMaxSet=false;
		}

		ShortBufferedImageSource(byte[][] compressedData,int width,int height,int samples,String compressedDataTransferSyntaxUID,ColorSpace colorSpace) {
			super(width*height*samples);
			try {
				decoder = new CompressedFrameDecoder(compressedDataTransferSyntaxUID,compressedData,2/*bytesPerSample*/,colorSpace);
			}
			catch (DicomException e) {
				e.printStackTrace(System.err);
				decoder = null;
			}
			minMaxSet=false;
		}

		protected short[] getDataForFrameIfNotOffsetInContiguousAllocation(int index) {
			short[] useData = null;
			if (decoder != null) {
				try {
//System.err.println("SourceImage.ShortBufferedImageSource.getDataForFrameIfNotOffsetInContiguousAllocation(): getting frame "+index+" from deferred decompression");
					BufferedImage bufferedImageFromReader = decoder.getDecompressedFrameAsBufferedImage(index);
					Raster raster = bufferedImageFromReader.getData();
					int transferType = raster.getTransferType();
					if (transferType == DataBuffer.TYPE_SHORT
					 || transferType == DataBuffer.TYPE_USHORT) {
//System.err.println("SourceImage.ShortBufferedImageSource.getDataForFrameIfNotOffsetInContiguousAllocation(): have correct TYPE_SHORT or TYPE_USHORT transferType");
						useData = (short[])(raster.getDataElements(0,0,width,height,null));
					 }
				}
				catch (DicomException e) {
					e.printStackTrace(System.err);
				}
				catch (IOException e) {
					e.printStackTrace(System.err);
				}
			}
			else if (buffers != null) {
//System.err.println("SourceImage.ShortBufferedImageSource.getDataForFrameIfNotOffsetInContiguousAllocation(): getting frame "+index+" from  memory mapped file");
//System.err.println("SourceImage.SignedShortGrayscaleBufferedImageSource.getUncachedBufferedImage(): nframesamples = "+nframesamples);
				useData = new short[nframesamples];
				ShortBuffer buffer = buffers[index];
				buffer.position(0);	// each per-frame buffer starts at nframesamples*index already
				buffer.get(useData);
			}
			else if (dataPerFrame != null) {
//System.err.println("SourceImage.ShortBufferedImageSource.getDataForFrameIfNotOffsetInContiguousAllocation(): getting frame "+index+" from per-frame byte allocated memory");
				useData = dataPerFrame[index];
			}
			else if (file != null) {
//System.err.println("SourceImage.ShortBufferedImageSource.getDataForFrameIfNotOffsetInContiguousAllocation(): getting frame "+index+" from single large multi-frame file");
				useData = new short[nframesamples];
				try {
					BinaryInputStream i = new BinaryInputStream(new FileInputStream(file),bigEndian);
					i.skipInsistently(byteOffsets[index]);
					i.readUnsigned16(useData,nframesamples);
					i.close();
				}
				catch (IOException e) {
					e.printStackTrace(System.err);
System.err.println("SourceImage.ShortBufferedImageSource.getDataForFrameIfNotOffsetInContiguousAllocation(): failed to read bytes for frame "+index+" from file "+file);
				}
			}
			return useData;
		}
		
		protected void finalize() throws Throwable {
//System.err.println("ShortBufferedImageSource.finalize()");
			data=null;
			if (dataPerFrame != null) {
				for (int f=0; f<dataPerFrame.length; ++f) {	// not sure if this is really necessary if going to null the array reference anyway :(
					dataPerFrame[f]=null;
				}
				dataPerFrame=null;
			}
			if (buffers != null) {
				for (int f=0; f<buffers.length; ++f) {	// not sure if this is really necessary if going to null the array reference anyway :(
					buffers[f]=null;
				}
				buffers=null;
			}
			if (decoder != null) {
				decoder.dispose();
			}
			decoder=null;
			super.finalize();
		}
	}
	
	private abstract class ByteBufferedImageSource extends BufferedImageSource {
		protected byte byteData[];
		protected byte byteDataPerFrame[][];
		protected short shortData[];
		protected short shortDataPerFrame[][];
		protected ByteBuffer[] byteBuffers;
		protected ShortBuffer[] shortBuffers;
		protected File file;
		protected File[] filesPerFrame;
		protected long[] byteOffsets;
		protected CompressedFrameDecoder decoder;
		
		ByteBufferedImageSource(byte byteData[],int width,int height,int samples) {
			super(width*height*samples);
			this.byteData=byteData;
		}
		
		ByteBufferedImageSource(byte byteDataPerFrame[][],int width,int height,int samples) {
			super(width*height*samples);
			this.byteDataPerFrame=byteDataPerFrame;
		}
		
		ByteBufferedImageSource(short shortData[],int width,int height,int samples) {
			super(width*height*samples);
			this.shortData=shortData;
		}
		
		ByteBufferedImageSource(short shortDataPerFrame[][],int width,int height,int samples) {
			super(width*height*samples);
			this.shortDataPerFrame=shortDataPerFrame;
		}
		
		ByteBufferedImageSource(ByteBuffer[] byteBuffers,int width,int height,int samples) {
			super(width*height*samples);
			this.byteBuffers=byteBuffers;
		}
		
		ByteBufferedImageSource(ShortBuffer[] shortBuffers,int width,int height,int samples) {
			super(width*height*samples);
			this.shortBuffers=shortBuffers;
		}
		
		ByteBufferedImageSource(File[] filesPerFrame,long[] byteOffsets,int width,int height,int samples) {
			super(width*height*samples);
			this.filesPerFrame=filesPerFrame;
			this.byteOffsets=byteOffsets;
		}
		
		ByteBufferedImageSource(File file,long[] byteOffsets,int width,int height,int samples) {
			super(width*height*samples);
			this.file=file;
			this.byteOffsets=byteOffsets;
		}

		ByteBufferedImageSource(byte[][] compressedData,int width,int height,int samples,String compressedDataTransferSyntaxUID,ColorSpace colorSpace) {
			super(width*height*samples);
			try {
				decoder = new CompressedFrameDecoder(compressedDataTransferSyntaxUID,compressedData,2/*bytesPerSample*/,colorSpace);
			}
			catch (DicomException e) {
				e.printStackTrace(System.err);
				decoder = null;
			}
		}
		
		protected byte[] getDataForFrameIfNotOffsetInContiguousAllocation(int index) {
			byte[] useData = null;
			if (decoder != null) {
				try {
//System.err.println("SourceImage.ByteBufferedImageSource.getDataForFrameIfNotOffsetInContiguousAllocation(): getting frame "+index+" from deferred decompression");
					BufferedImage bufferedImageFromReader = decoder.getDecompressedFrameAsBufferedImage(index);
					Raster raster = bufferedImageFromReader.getData();
					int transferType = raster.getTransferType();
					if (transferType == DataBuffer.TYPE_BYTE) {
//System.err.println("SourceImage.ByteBufferedImageSource.getDataForFrameIfNotOffsetInContiguousAllocation(): have correct TYPE_BYTE transferType");
						useData = (byte[])(raster.getDataElements(0,0,width,height,null));
					 }
				}
				catch (DicomException e) {
					e.printStackTrace(System.err);
				}
				catch (IOException e) {
					e.printStackTrace(System.err);
				}
			}
			else if (byteBuffers != null) {
//System.err.println("SourceImage.ByteBufferedImageSource.getDataForFrameIfNotOffsetInContiguousAllocation(): getting frame "+index+" from memory mapped byte buffers");
				useData = new byte[nframesamples];
				ByteBuffer buffer = byteBuffers[index];
				buffer.position(0);	// each per-frame buffer starts at nframesamples*index already
				buffer.get(useData);
			}
			else if (shortBuffers != null) {
//System.err.println("SourceImage.ByteBufferedImageSource.getDataForFrameIfNotOffsetInContiguousAllocation(): getting frame "+index+" from memory mapped short buffers");
				short[] sdataforframe = new short[nframesamples/2];
				ShortBuffer buffer = shortBuffers[index];
				buffer.position(0);	// each per-frame buffer starts at nframesamples*index already
				buffer.get(sdataforframe);
				useData = getByteArrayForFrameFromSingleFrameShortArray(sdataforframe,nframesamples);
			}
			else if (byteDataPerFrame != null) {
//System.err.println("SourceImage.ByteBufferedImageSource.getDataForFrameIfNotOffsetInContiguousAllocation(): getting frame "+index+" from per-frame byte allocated memory");
				useData = byteDataPerFrame[index];
			}
			else if (shortDataPerFrame != null) {
//System.err.println("SourceImage.ByteBufferedImageSource.getDataForFrameIfNotOffsetInContiguousAllocation(): getting frame "+index+" from per-frame short allocated memory");
				short[] sdataforframe = shortDataPerFrame[index];
				useData = getByteArrayForFrameFromSingleFrameShortArray(sdataforframe,nframesamples);
			}
			else if (filesPerFrame != null) {
//System.err.println("SourceImage.ByteBufferedImageSource.getDataForFrameIfNotOffsetInContiguousAllocation(): getting frame "+index+" from per-frame file");
				useData = new byte[nframesamples];
				try {
					BinaryInputStream i = new BinaryInputStream(new FileInputStream(filesPerFrame[index]),false/*bigEndian - byte order is irrelevant*/);
					i.skipInsistently(byteOffsets[index]);
					i.readInsistently(useData,0,nframesamples);
					i.close();
				}
				catch (IOException e) {
					e.printStackTrace(System.err);
//System.err.println("SourceImage.ByteBufferedImageSource.getDataForFrameIfNotOffsetInContiguousAllocation(): failed to read bytes for frame "+index+" from file "+filesPerFrame[index]);
				}
			}
			else if (file != null) {
//System.err.println("SourceImage.ByteBufferedImageSource.getDataForFrameIfNotOffsetInContiguousAllocation(): getting frame "+index+" from single large multi-frame file");
				useData = new byte[nframesamples];
				try {
					BinaryInputStream i = new BinaryInputStream(new FileInputStream(file),false/*bigEndian - byte order is irrelevant*/);
					i.skipInsistently(byteOffsets[index]);
					i.readInsistently(useData,0,nframesamples);
					i.close();
				}
				catch (IOException e) {
					e.printStackTrace(System.err);
//System.err.println("SourceImage.ByteBufferedImageSource.getDataForFrameIfNotOffsetInContiguousAllocation(): failed to read bytes for frame "+index+" from file "+file);
				}
			}
			return useData;
		}
		
		protected void finalize() throws Throwable {
//System.err.println("ByteBufferedImageSource.finalize()");
			byteData=null;
			if (byteDataPerFrame != null) {
				for (int f=0; f<byteDataPerFrame.length; ++f) {	// not sure if this is really necessary if going to null the array reference anyway :(
					byteDataPerFrame[f]=null;
				}
				byteDataPerFrame=null;
			}
			shortData=null;
			if (shortDataPerFrame != null) {
				for (int f=0; f<shortDataPerFrame.length; ++f) {	// not sure if this is really necessary if going to null the array reference anyway :(
					shortDataPerFrame[f]=null;
				}
				shortDataPerFrame=null;
			}
			if (byteBuffers != null) {
				for (int f=0; f<byteBuffers.length; ++f) {	// not sure if this is really necessary if going to null the array reference anyway :(
					byteBuffers[f]=null;
				}
				byteBuffers=null;
			}
			if (shortBuffers != null) {
				for (int f=0; f<shortBuffers.length; ++f) {	// not sure if this is really necessary if going to null the array reference anyway :(
					shortBuffers[f]=null;
				}
				shortBuffers=null;
			}
			if (decoder != null) {
				decoder.dispose();
			}
			decoder=null;
			super.finalize();
		}
	}

	private class SignedByteGrayscaleBufferedImageSource extends ByteBufferedImageSource {
		protected int mask;
		protected int signbit;
		protected int extend;
		protected int largestGray;
		protected boolean minMaxSet;

		SignedByteGrayscaleBufferedImageSource(byte byteData[],int width,int height,int mask,int signbit,int extend,int largestGray) {
			super(byteData,width,height,1);
			doCommonConstructorStuff(mask,signbit,extend,largestGray);
		}
		
		SignedByteGrayscaleBufferedImageSource(byte byteDataPerFrame[][],int width,int height,int mask,int signbit,int extend,int largestGray) {
			super(byteDataPerFrame,width,height,1);
			doCommonConstructorStuff(mask,signbit,extend,largestGray);
		}
		
		SignedByteGrayscaleBufferedImageSource(short shortData[],int width,int height,int mask,int signbit,int extend,int largestGray) {
			super(shortData,width,height,1);
			doCommonConstructorStuff(mask,signbit,extend,largestGray);
		}
		
		SignedByteGrayscaleBufferedImageSource(short shortDataPerFrame[][],int width,int height,int mask,int signbit,int extend,int largestGray) {
			super(shortDataPerFrame,width,height,1);
			doCommonConstructorStuff(mask,signbit,extend,largestGray);
		}
		
		SignedByteGrayscaleBufferedImageSource(ShortBuffer[] byteBuffers,int width,int height,int mask,int signbit,int extend,int largestGray) {
			super(byteBuffers,width,height,1);
			doCommonConstructorStuff(mask,signbit,extend,largestGray);
		}
		
		SignedByteGrayscaleBufferedImageSource(ByteBuffer[] shortBuffers,int width,int height,int mask,int signbit,int extend,int largestGray) {
			super(shortBuffers,width,height,1);
			doCommonConstructorStuff(mask,signbit,extend,largestGray);
		}
		
		SignedByteGrayscaleBufferedImageSource(byte[][] compressedData,int width,int height,int mask,int signbit,int extend,int largestGray,String compressedDataTransferSyntaxUID) {
			super(compressedData,width,height,1,compressedDataTransferSyntaxUID,ColorSpace.getInstance(ColorSpace.CS_GRAY));
			doCommonConstructorStuff(mask,signbit,extend,largestGray);
		}
		
		SignedByteGrayscaleBufferedImageSource(File[] filesPerFrame,long[] byteOffsets,int width,int height,int mask,int signbit,int extend,int largestGray) {
			super(filesPerFrame,byteOffsets,width,height,1);
			doCommonConstructorStuff(mask,signbit,extend,largestGray);
		}
		
		SignedByteGrayscaleBufferedImageSource(File file,long[] byteOffsets,int width,int height,int mask,int signbit,int extend,int largestGray) {
			super(file,byteOffsets,width,height,1);
			doCommonConstructorStuff(mask,signbit,extend,largestGray);
		}
		
		private void doCommonConstructorStuff(int mask,int signbit,int extend,int largestGray) {
			this.mask=mask;
			this.signbit=signbit;
			this.extend=extend;
			this.largestGray=largestGray;
			imgMin = 0x0000007f;		// i.e. start with the largest  possible 16 bit +ve value, sign extended to the full Java int 32 bits
			imgMax = 0xffffff80;		// i.e. start with the smallest possible 16 bit -ve value, sign extended to the full Java int 32 bits
		}

		public BufferedImage getUncachedBufferedImage(int index) {
//System.err.println("SourceImage.SignedByteGrayscaleBufferedImageSource.getUncachedBufferedImage(): index="+index);
			byte[] useData = getDataForFrameIfNotOffsetInContiguousAllocation(index);
			int useOffset = 0;
			if (useData == null) {
				if (shortData != null) {
//System.err.println("SourceImage.ByteGrayscaleBufferedImageSource.getUncachedBufferedImage(): getting frame "+index+" from contiguous short allocated memory for all frames");
					useData = getByteArrayForFrameFromMultiFrameShortArray(index,shortData,nframesamples);
				}
				else if (byteData != null) {
//System.err.println("SourceImage.ByteGrayscaleBufferedImageSource.getUncachedBufferedImage(): getting frame "+index+" from contiguous byte allocated memory for all frames");
					useData = byteData;
					useOffset = nframesamples*index;
				}
			}

			// now copy the data for just one frame, masking and sign extending it
			byte[] newData = new byte[nframesamples];
			for (int i=useOffset,j=0; j<nframesamples; ++i,++j) {
				boolean isPaddingValue = false;
				byte unmaskedValue=useData[i];
				if (useNonMaskedSinglePadValue && unmaskedValue == ((byte)nonMaskedSinglePadValue)) {
					isPaddingValue = true;
				}
				int value=((int)unmaskedValue)&mask;
				int nonextendedvalue = value;
				if ((value & signbit) != 0) value=value | extend;
				newData[j]=(byte)value;
				if (useMaskedPadRange && (nonextendedvalue >= useMaskedPadRangeStart && nonextendedvalue <= useMaskedPadRangeEnd)) {
					isPaddingValue = true;
				}
				if (!isPaddingValue) {
					if (value > imgMax && value <= largestGray) {
//System.err.println("SourceImage.SignedByteGrayscaleBufferedImageSource.getUncachedBufferedImage(): imgMax was = "+imgMax+" now setting to "+value);
						imgMax=value;
					}
					if (value < imgMin) {
//System.err.println("SourceImage.SignedByteGrayscaleBufferedImageSource.getUncachedBufferedImage(): imgMin was = "+imgMin+" now setting to "+value);
						imgMin=value;
					}
					//imgSum+=value;
					//imgSumOfSquares+=value*value;
				}
			}
			minMaxSet=true;
			return createByteGrayscaleImage(width,height,newData,0);
		}
	}
	
	private class UnsignedByteGrayscaleBufferedImageSource extends ByteBufferedImageSource {
		protected int mask;
		protected int largestGray;
		protected boolean minMaxSet;

		UnsignedByteGrayscaleBufferedImageSource(byte byteData[],int width,int height,int mask,int largestGray) {
			super(byteData,width,height,1);
//System.err.println("SourceImage.UnsignedByteGrayscaleBufferedImageSource():");
			doCommonConstructorStuff(mask,largestGray);
		}

		UnsignedByteGrayscaleBufferedImageSource(byte byteDataPerFrame[][],int width,int height,int mask,int largestGray) {
			super(byteDataPerFrame,width,height,1);
//System.err.println("SourceImage.UnsignedByteGrayscaleBufferedImageSource():");
			doCommonConstructorStuff(mask,largestGray);
		}

		UnsignedByteGrayscaleBufferedImageSource(short shortData[],int width,int height,int mask,int largestGray) {
			super(shortData,width,height,1);
//System.err.println("SourceImage.UnsignedByteGrayscaleBufferedImageSource():");
			doCommonConstructorStuff(mask,largestGray);
		}

		UnsignedByteGrayscaleBufferedImageSource(short shortDataPerFrame[][],int width,int height,int mask,int largestGray) {
			super(shortDataPerFrame,width,height,1);
//System.err.println("SourceImage.UnsignedByteGrayscaleBufferedImageSource():");
			doCommonConstructorStuff(mask,largestGray);
		}
		
		UnsignedByteGrayscaleBufferedImageSource(ByteBuffer[] byteBuffers,int width,int height,int mask,int largestGray) {
			super(byteBuffers,width,height,1);
//System.err.println("SourceImage.UnsignedByteGrayscaleBufferedImageSource():");
			doCommonConstructorStuff(mask,largestGray);
		}
		
		UnsignedByteGrayscaleBufferedImageSource(ShortBuffer[] shortBuffers,int width,int height,int mask,int largestGray) {
			super(shortBuffers,width,height,1);
//System.err.println("SourceImage.UnsignedByteGrayscaleBufferedImageSource():");
			doCommonConstructorStuff(mask,largestGray);
		}
		
		UnsignedByteGrayscaleBufferedImageSource(byte[][] compressedData,int width,int height,int mask,int largestGray,String compressedDataTransferSyntaxUID) {
			super(compressedData,width,height,1,compressedDataTransferSyntaxUID,ColorSpace.getInstance(ColorSpace.CS_GRAY));
			doCommonConstructorStuff(mask,largestGray);
		}
		
		UnsignedByteGrayscaleBufferedImageSource(File[] filesPerFrame,long[] byteOffsets,int width,int mask,int largestGray) {
			super(filesPerFrame,byteOffsets,width,height,1);
			doCommonConstructorStuff(mask,largestGray);
		}
		
		UnsignedByteGrayscaleBufferedImageSource(File file,long[] byteOffsets,int width,int height,int mask,int largestGray) {
			super(file,byteOffsets,width,height,1);
			doCommonConstructorStuff(mask,largestGray);
		}


		private void doCommonConstructorStuff(int mask,int largestGray) {
			this.mask=mask;
			this.largestGray=largestGray;
			imgMin = 0x000000ff;	// i.e. start with the largest  possible 16 bit +ve value, sign extended to the full Java int 32 bits
			imgMax = 0x00000000;	// i.e. start with the smallest possible 16 bit -ve value, sign extended to the full Java int 32 bits
		}
		
		public BufferedImage getUncachedBufferedImage(int index) {
//System.err.println("SourceImage.UnsignedByteGrayscaleBufferedImageSource.getUncachedBufferedImage(): index="+index);
			byte[] useData = getDataForFrameIfNotOffsetInContiguousAllocation(index);
			int useOffset = 0;
			if (useData == null) {
				if (shortData != null) {
//System.err.println("SourceImage.UnsignedByteGrayscaleBufferedImageSource.getUncachedBufferedImage(): getting frame "+index+" from contiguous short allocated memory for all frames");
					useData = getByteArrayForFrameFromMultiFrameShortArray(index,shortData,nframesamples);
				}
				else if (byteData != null) {
//System.err.println("SourceImage.UnsignedByteGrayscaleBufferedImageSource.getUncachedBufferedImage(): getting frame "+index+" from contiguous byte allocated memory for all frames");
					useData = byteData;
					useOffset = nframesamples*index;
				}
			}

			// now copy the data for just one frame, masking it
			byte[] newData = new byte[nframesamples];
			for (int i=useOffset,j=0; j<nframesamples; ++i,++j) {
				boolean isPaddingValue = false;
				short unmaskedValue=useData[i];
				if (useNonMaskedSinglePadValue && unmaskedValue == ((byte)nonMaskedSinglePadValue)) {
					isPaddingValue = true;
				}
				int value=((int)unmaskedValue)&mask;
//System.err.println("SourceImage.UnsignedByteGrayscaleBufferedImageSource.getUncachedBufferedImage(): value ["+i+"] = "+value);
				// unsigned so no need to check for sign extension
				newData[j]=(byte)value;
				if (useMaskedPadRange && (value >= useMaskedPadRangeStart && value <= useMaskedPadRangeEnd)) {
					isPaddingValue = true;
				}
				if (!isPaddingValue) {
					if (value > imgMax && value <= largestGray) {
//System.err.println("SourceImage.UnsignedByteGrayscaleBufferedImageSource.getUncachedBufferedImage(): imgMax was = "+imgMax+" now setting to "+value);
						imgMax=value;
					}
					if (value < imgMin) {
//System.err.println("SourceImage.UnsignedByteGrayscaleBufferedImageSource.getUncachedBufferedImage(): imgMin was = "+imgMin+" now setting to "+value);
						imgMin=value;
					}
					//imgSum+=value;
					//imgSumOfSquares+=value*value;
				}
			}
			minMaxSet=true;
			return createByteGrayscaleImage(width,height,newData,0);
		}
	}

	private class SignedShortGrayscaleBufferedImageSource extends ShortBufferedImageSource {
		protected int mask;
		protected int signbit;
		protected int extend;
		protected int largestGray;

		SignedShortGrayscaleBufferedImageSource(short data[],int width,int height,int mask,int signbit,int extend,int largestGray) {
			super(data,width,height,1);
			doCommonConstructorStuff(mask,signbit,extend,largestGray);
		}
		
		SignedShortGrayscaleBufferedImageSource(short dataPerFrame[][],int width,int height,int mask,int signbit,int extend,int largestGray) {
			super(dataPerFrame,width,height,1);
			doCommonConstructorStuff(mask,signbit,extend,largestGray);
		}
		
		SignedShortGrayscaleBufferedImageSource(ShortBuffer[] buffers,int width,int height,int mask,int signbit,int extend,int largestGray) {
			super(buffers,width,height,1);
			doCommonConstructorStuff(mask,signbit,extend,largestGray);
		}
		
		SignedShortGrayscaleBufferedImageSource(byte[][] compressedData,int width,int height,int mask,int signbit,int extend,int largestGray,String compressedDataTransferSyntaxUID) {
			super(compressedData,width,height,1,compressedDataTransferSyntaxUID,ColorSpace.getInstance(ColorSpace.CS_GRAY));
			doCommonConstructorStuff(mask,signbit,extend,largestGray);
		}
		
		SignedShortGrayscaleBufferedImageSource(File file,long[] byteOffsets,boolean bigEndian,int width,int height,int mask,int signbit,int extend,int largestGray) {
			super(file,byteOffsets,bigEndian,width,height,1);
			doCommonConstructorStuff(mask,signbit,extend,largestGray);
		}

		private void doCommonConstructorStuff(int mask,int signbit,int extend,int largestGray) {
			this.mask=mask;
			this.signbit=signbit;
			this.extend=extend;
			this.largestGray=largestGray;
			imgMin = 0x00007fff;		// i.e. start with the largest  possible 16 bit +ve value, sign extended to the full Java int 32 bits
			imgMax = 0xffff8000;		// i.e. start with the smallest possible 16 bit -ve value, sign extended to the full Java int 32 bits
		}
		
		public BufferedImage getUncachedBufferedImage(int index) {
//System.err.println("SourceImage.SignedShortGrayscaleBufferedImageSource.getUncachedBufferedImage(): index="+index);
			short[] useData = getDataForFrameIfNotOffsetInContiguousAllocation(index);
			int useOffset = 0;
			if (useData ==  null) {
//System.err.println("SourceImage.SignedShortGrayscaleBufferedImageSource.getUncachedBufferedImage(): getting frame "+index+" from contiguous short allocated memory for all frames");
				useData = data;
				useOffset = nframesamples*index;
			}
			// now copy the data for just one frame, masking and sign extending it
			short[] newData = new short[nframesamples];
			for (int i=useOffset,j=0; j<nframesamples; ++i,++j) {
				boolean isPaddingValue = false;
				short unmaskedValue=useData[i];
				if (useNonMaskedSinglePadValue && unmaskedValue == ((short)nonMaskedSinglePadValue)) {
					isPaddingValue = true;
				}
				int value=((int)unmaskedValue)&mask;
				int nonextendedvalue = value;
				if ((value & signbit) != 0) value=value | extend;
				newData[j]=(short)value;
				if (useMaskedPadRange && (nonextendedvalue >= useMaskedPadRangeStart && nonextendedvalue <= useMaskedPadRangeEnd)) {
					isPaddingValue = true;
				}
				if (!isPaddingValue) {
					if (value > imgMax && value <= largestGray) {
//System.err.println("SourceImage.SignedShortGrayscaleBufferedImageSource.getUncachedBufferedImage(): imgMax was = "+imgMax+" now setting to "+value);
						imgMax=value;
					}
					if (value < imgMin) {
//System.err.println("SourceImage.SignedShortGrayscaleBufferedImageSource.getUncachedBufferedImage(): imgMin was = "+imgMin+" now setting to "+value);
						imgMin=value;
					}
					//imgSum+=value;
					//imgSumOfSquares+=value*value;
				}
			}
			minMaxSet=true;
			return createSignedShortGrayscaleImage(width,height,newData,0);
		}
	}
	
	private class UnsignedShortGrayscaleBufferedImageSource extends ShortBufferedImageSource {
		protected int mask;
		protected int largestGray;

		UnsignedShortGrayscaleBufferedImageSource(short data[],int width,int height,int mask,int largestGray) {
			super(data,width,height,1);
//System.err.println("SourceImage.UnsignedShortGrayscaleBufferedImageSource():");
			doCommonConstructorStuff(mask,largestGray);
		}

		UnsignedShortGrayscaleBufferedImageSource(short dataPerFrame[][],int width,int height,int mask,int largestGray) {
			super(dataPerFrame,width,height,1);
//System.err.println("SourceImage.UnsignedShortGrayscaleBufferedImageSource():");
			doCommonConstructorStuff(mask,largestGray);
		}
		
		UnsignedShortGrayscaleBufferedImageSource(ShortBuffer[] buffers,int width,int height,int mask,int largestGray) {
			super(buffers,width,height,1);
//System.err.println("SourceImage.UnsignedShortGrayscaleBufferedImageSource():");
			doCommonConstructorStuff(mask,largestGray);
		}
		
		UnsignedShortGrayscaleBufferedImageSource(byte[][] compressedData,int width,int height,int mask,int largestGray,String compressedDataTransferSyntaxUID) {
			super(compressedData,width,height,1,compressedDataTransferSyntaxUID,ColorSpace.getInstance(ColorSpace.CS_GRAY));
			doCommonConstructorStuff(mask,largestGray);
		}
		
		UnsignedShortGrayscaleBufferedImageSource(File file,long[] byteOffsets,boolean bigEndian,int width,int height,int mask,int largestGray) {
			super(file,byteOffsets,bigEndian,width,height,1);
			doCommonConstructorStuff(mask,largestGray);
		}

		private void doCommonConstructorStuff(int mask,int largestGray) {
			this.mask=mask;
			this.largestGray=largestGray;
			imgMin = 0x0000ffff;	// i.e. start with the largest  possible 16 bit +ve value, sign extended to the full Java int 32 bits
			imgMax = 0x00000000;	// i.e. start with the smallest possible 16 bit -ve value, sign extended to the full Java int 32 bits
		}
		
		public BufferedImage getUncachedBufferedImage(int index) {
//System.err.println("SourceImage.UnsignedShortGrayscaleBufferedImageSource.getUncachedBufferedImage(): index="+index);
			short[] useData = getDataForFrameIfNotOffsetInContiguousAllocation(index);
			int useOffset = 0;
			if (useData ==  null) {
//System.err.println("UnsignedShortGrayscaleBufferedImageSource(): getting frame "+index+" from contiguous short allocated memory for all frames");
				useData = data;
				useOffset = nframesamples*index;
			}
			// now copy the data for just one frame, masking it
			short[] newData = new short[nframesamples];
			for (int i=useOffset,j=0; j<nframesamples; ++i,++j) {
				boolean isPaddingValue = false;
				short unmaskedValue=useData[i];
				if (useNonMaskedSinglePadValue && unmaskedValue == ((short)nonMaskedSinglePadValue)) {
					isPaddingValue = true;
				}
				int value=((int)unmaskedValue)&mask;
//System.err.println("SourceImage.UnsignedShortGrayscaleBufferedImageSource.getUncachedBufferedImage(): value ["+i+"] = "+value);
				// unsigned so no need to check for sign extension
				newData[j]=(short)value;
				if (useMaskedPadRange && (value >= useMaskedPadRangeStart && value <= useMaskedPadRangeEnd)) {
					isPaddingValue = true;
				}
				if (!isPaddingValue) {
					if (value > imgMax && value <= largestGray) {
//System.err.println("SourceImage.UnsignedShortGrayscaleBufferedImageSource.getUncachedBufferedImage(): imgMax was = "+imgMax+" now setting to "+value);
						imgMax=value;
					}
					if (value < imgMin) {
//System.err.println("SourceImage.UnsignedShortGrayscaleBufferedImageSource.getUncachedBufferedImage(): imgMin was = "+imgMin+" now setting to "+value);
						imgMin=value;
					}
					//imgSum+=value;
					//imgSumOfSquares+=value*value;
				}
			}
			minMaxSet=true;
			return createUnsignedShortGrayscaleImage(width,height,newData,0);
		}
	}
	
	private class ByteGrayscaleBufferedImageSource extends ByteBufferedImageSource {
		ByteGrayscaleBufferedImageSource(byte data[],int width,int height) {
			super(data,width,height,1);
		}
		
		ByteGrayscaleBufferedImageSource(byte dataPerFrame[][],int width,int height) {
			super(dataPerFrame,width,height,1);
		}
		
		ByteGrayscaleBufferedImageSource(ByteBuffer[] buffers,int width,int height) {
			super(buffers,width,height,1);
		}
		
		public BufferedImage getUncachedBufferedImage(int index) {
//System.err.println("SourceImage.ByteGrayscaleBufferedImageSource.getUncachedBufferedImage(): index="+index);
			byte[] useData = getDataForFrameIfNotOffsetInContiguousAllocation(index);
			int useOffset = 0;
			if (useData == null) {
				if (shortData != null) {
//System.err.println("SourceImage.ByteGrayscaleBufferedImageSource.getUncachedBufferedImage(): getting frame "+index+" from contiguous short allocated memory for all frames");
					useData = getByteArrayForFrameFromMultiFrameShortArray(index,shortData,nframesamples);
				}
				else if (byteData != null) {
//System.err.println("SourceImage.ByteGrayscaleBufferedImageSource.getUncachedBufferedImage(): getting frame "+index+" from contiguous byte allocated memory for all frames");
					useData = byteData;
					useOffset = nframesamples*index;
				}
			}
			return createByteGrayscaleImage(width,height,useData,useOffset);
		}
	}
	
	private class BandInterleavedByteRGBBufferedImageSource extends ByteBufferedImageSource {
		ColorSpace colorSpace;
		
		BandInterleavedByteRGBBufferedImageSource(byte data[],int width,int height,ColorSpace colorSpace) {
			super(data,width,height,3);
			this.colorSpace = colorSpace;
		}
		
		BandInterleavedByteRGBBufferedImageSource(byte dataPerFrame[][],int width,int height,ColorSpace colorSpace) {
			super(dataPerFrame,width,height,3);
			this.colorSpace = colorSpace;
		}
		
		BandInterleavedByteRGBBufferedImageSource(ByteBuffer[] buffers,int width,int height,ColorSpace colorSpace) {
			super(buffers,width,height,3);
			this.colorSpace = colorSpace;
		}
		
		BandInterleavedByteRGBBufferedImageSource(ShortBuffer[] buffers,int width,int height,ColorSpace colorSpace) {
			super(buffers,width,height,3);
			this.colorSpace = colorSpace;
		}
		
		BandInterleavedByteRGBBufferedImageSource(File[] filesPerFrame,long[] byteOffsets,int width,int height,ColorSpace colorSpace) {
			super(filesPerFrame,byteOffsets,width,height,3);
			this.colorSpace = colorSpace;
		}
		
		BandInterleavedByteRGBBufferedImageSource(File file,long[] byteOffsets,int width,int height,ColorSpace colorSpace) {
			super(file,byteOffsets,width,height,3);
			this.colorSpace = colorSpace;
		}
		
		public BufferedImage getUncachedBufferedImage(int index) {
//System.err.println("SourceImage.BandInterleavedByteRGBBufferedImageSource.getUncachedBufferedImage(): index="+index);
//System.err.println("SourceImage.BandInterleavedByteRGBBufferedImageSource.getUncachedBufferedImage(): nframesamples = "+nframesamples);
			byte[] useData = getDataForFrameIfNotOffsetInContiguousAllocation(index);
			int useOffset = 0;
			if (useData == null) {
//System.err.println("SourceImage.BandInterleavedByteRGBBufferedImageSource.getUncachedBufferedImage(): getting frame "+index+" from contiguous allocated memory for all frames");
				useData = byteData;
				useOffset = nframesamples*index;
			}
			return createBandInterleavedByteRGBImage(width,height,useData,useOffset,colorSpace);
		}

		// Min and max are fixed at full range for all channels ...
		public double getMinimumPixelValueOfMostRecentBufferedImage(double oldMin) { return 0; }
		public double getMaximumPixelValueOfMostRecentBufferedImage(double oldMax) { return 255; }
	}
	
	private class PixelInterleavedByteRGBBufferedImageSource extends ByteBufferedImageSource {
		ColorSpace colorSpace;
		
		PixelInterleavedByteRGBBufferedImageSource(byte data[],int width,int height,ColorSpace colorSpace) {
			super(data,width,height,3);
			this.colorSpace = colorSpace;
		}
		
		PixelInterleavedByteRGBBufferedImageSource(byte dataPerFrame[][],int width,int height,ColorSpace colorSpace) {
			super(dataPerFrame,width,height,3);
			this.colorSpace = colorSpace;
		}
		
		PixelInterleavedByteRGBBufferedImageSource(ByteBuffer[] buffers,int width,int height,ColorSpace colorSpace) {
			super(buffers,width,height,3);
			this.colorSpace = colorSpace;
		}
		
		PixelInterleavedByteRGBBufferedImageSource(ShortBuffer[] buffers,int width,int height,ColorSpace colorSpace) {
			super(buffers,width,height,3);
			this.colorSpace = colorSpace;
		}
		
		PixelInterleavedByteRGBBufferedImageSource(File[] filesPerFrame,long[] byteOffsets,int width,int height,ColorSpace colorSpace) {
			super(filesPerFrame,byteOffsets,width,height,3);
			this.colorSpace = colorSpace;
		}
		
		PixelInterleavedByteRGBBufferedImageSource(File file,long[] byteOffsets,int width,int height,ColorSpace colorSpace) {
			super(file,byteOffsets,width,height,3);
			this.colorSpace = colorSpace;
		}
		
		public BufferedImage getUncachedBufferedImage(int index) {
//System.err.println("SourceImage.PixelInterleavedByteRGBBufferedImageSource.getUncachedBufferedImage(): index="+index);
//System.err.println("SourceImage.PixelInterleavedByteRGBBufferedImageSource.getUncachedBufferedImage(): nframesamples = "+nframesamples);
			byte[] useData = getDataForFrameIfNotOffsetInContiguousAllocation(index);
			int useOffset = 0;
			if (useData == null) {
//System.err.println("SourceImage.PixelInterleavedByteRGBBufferedImageSource.getUncachedBufferedImage(): getting frame "+index+" from contiguous byte allocated memory for all frames");
				useData = byteData;
				useOffset = nframesamples*index;
			}
			return createPixelInterleavedByteRGBImage(width,height,useData,useOffset,colorSpace);
		}

		// Min and max are fixed at full range for all channels ...
		public double getMinimumPixelValueOfMostRecentBufferedImage(double oldMin) { return 0; }
		public double getMaximumPixelValueOfMostRecentBufferedImage(double oldMax) { return 255; }
	}
	
	private class CompressedByteRGBBufferedImageSource extends BufferedImageSource /* extending BufferedImageSource also leverages its last frame caching mechanism */ {
		CompressedFrameDecoder decoder;
		ColorSpace colorSpace;
		
		CompressedByteRGBBufferedImageSource(byte[][] compressedData,int width,int height,ColorSpace colorSpace,String compressedDataTransferSyntaxUID) {
			super(width*height*3/*samples*/);	// nframesamples is actually irrelevant ... BufferedImageSource does nothing with this information :(
			try {
				decoder = new CompressedFrameDecoder(compressedDataTransferSyntaxUID,compressedData,1/*bytesPerSample*/,colorSpace);
			}
			catch (DicomException e) {
				e.printStackTrace(System.err);
				decoder = null;
			}
			this.colorSpace = colorSpace;
		}
		
		public boolean getColorSpaceConvertedToRGBDuringDecompression() {
			return decoder == null ? false : decoder.getColorSpaceConvertedToRGBDuringDecompression();
		}
		
		public BufferedImage getUncachedBufferedImage(int index) {
//System.err.println("SourceImage.CompressedByteRGBBufferedImageSource.getUncachedBufferedImage(): index="+index);
			BufferedImage bufferedImage = null;
			if (decoder != null) {
				try {
					bufferedImage = decoder.getDecompressedFrameAsBufferedImage(index);
				}
				catch (DicomException e) {
					e.printStackTrace(System.err);
				}
				catch (IOException e) {
					e.printStackTrace(System.err);
				}
			}
			return bufferedImage;
		}

		// Min and max are fixed at full range for all channels ...
		public double getMinimumPixelValueOfMostRecentBufferedImage(double oldMin) { return 0; }
		public double getMaximumPixelValueOfMostRecentBufferedImage(double oldMax) { return 255; }

		protected void finalize() throws Throwable {
//System.err.println("SourceImage.CompressedByteRGBBufferedImageSource.finalize()");
			if (decoder != null) {
				try {
					decoder.dispose();
				}
				catch (Exception e) {
					e.printStackTrace(System.err);
				}
			}
			super.finalize();
		}
	}
	
	private class FloatGrayscaleBufferedImageSource extends BufferedImageSource {
		protected float data[];
		protected float imgMin;
		protected float imgMax;
		protected boolean minMaxSet;

		public double getMinimumPixelValueOfMostRecentBufferedImage(double oldMin) { return minMaxSet ? ((double)imgMin) : oldMin; }
		public double getMaximumPixelValueOfMostRecentBufferedImage(double oldMax) { return minMaxSet ? ((double)imgMax) : oldMax; }

		protected void finalize() throws Throwable {
//System.err.println("FloatGrayscaleBufferedImageSource.finalize()");
			data=null;
			super.finalize();
		}

		FloatGrayscaleBufferedImageSource(float data[],int width,int height) {
			super(width*height);
			this.data=data;
			minMaxSet=false;
			imgMin = Float.MAX_VALUE;
			imgMax = Float.MIN_VALUE;
		}
		
		public BufferedImage getUncachedBufferedImage(int index) {
//System.err.println("SourceImage.FloatGrayscaleBufferedImageSource.getUncachedBufferedImage(): index="+index);
			float[] useData;
			int useOffset;
			useData = data;
			useOffset = nframesamples*index;
			// now copy the data for just one frame, masking and sign extending it
			float[] newData = new float[nframesamples];
			for (int i=useOffset,j=0; j<nframesamples; ++i,++j) {
				float value=useData[i];
				newData[j]=value;
				{
					if (value > imgMax) {
//System.err.println("SourceImage.FloatGrayscaleBufferedImageSource.getUncachedBufferedImage(): imgMax was = "+imgMax+" now setting to "+value);
						imgMax=value;
					}
					if (value < imgMin) {
//System.err.println("SourceImage.FloatGrayscaleBufferedImageSource.getUncachedBufferedImage(): imgMin was = "+imgMin+" now setting to "+value);
						imgMin=value;
					}
					//imgSum+=value;
					//imgSumOfSquares+=value*value;
				}
			}
			minMaxSet=true;
			return createFloatGrayscaleImage(width,height,newData,0);
		}
	}
	
	private class DoubleGrayscaleBufferedImageSource extends BufferedImageSource {
		protected double data[];
		protected double imgMin;
		protected double imgMax;
		protected boolean minMaxSet;

		public double getMinimumPixelValueOfMostRecentBufferedImage(double oldMin) { return minMaxSet ? ((double)imgMin) : oldMin; }
		public double getMaximumPixelValueOfMostRecentBufferedImage(double oldMax) { return minMaxSet ? ((double)imgMax) : oldMax; }

		protected void finalize() throws Throwable {
//System.err.println("DoubleGrayscaleBufferedImageSource.finalize()");
			data=null;
			super.finalize();
		}

		DoubleGrayscaleBufferedImageSource(double data[],int width,int height) {
			super(width*height);
			this.data=data;
			minMaxSet=false;
			imgMin = Double.MAX_VALUE;
			imgMax = Double.MIN_VALUE;
		}
		
		public BufferedImage getUncachedBufferedImage(int index) {
//System.err.println("SourceImage.DoubleGrayscaleBufferedImageSource.getUncachedBufferedImage(): index="+index);
			double[] useData;
			int useOffset;
			useData = data;
			useOffset = nframesamples*index;
			// now copy the data for just one frame, masking and sign extending it
			double[] newData = new double[nframesamples];
			for (int i=useOffset,j=0; j<nframesamples; ++i,++j) {
				double value=useData[i];
				newData[j]=value;
				{
					if (value > imgMax) {
//System.err.println("SourceImage.DoubleGrayscaleBufferedImageSource.getUncachedBufferedImage(): imgMax was = "+imgMax+" now setting to "+value);
						imgMax=value;
					}
					if (value < imgMin) {
//System.err.println("SourceImage.DoubleGrayscaleBufferedImageSource.getUncachedBufferedImage(): imgMin was = "+imgMin+" now setting to "+value);
						imgMin=value;
					}
					//imgSum+=value;
					//imgSumOfSquares+=value*value;
				}
			}
			minMaxSet=true;
			return createDoubleGrayscaleImage(width,height,newData,0);
		}
	}
	
	// See "http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4724038" Bug ID: 4724038 (fs) Add unmap method to MappedByteBuffer
	//
	protected static void clean(final Object object) throws Exception {
		java.security.AccessController.doPrivileged(new java.security.PrivilegedAction() {
				public Object run() {
//System.err.println("SourceImage.clean.run()");
					try {
						java.lang.reflect.Method getCleanerMethod = object.getClass().getMethod("cleaner",new Class[0]);
						getCleanerMethod.setAccessible(true);
						sun.misc.Cleaner cleaner = (sun.misc.Cleaner)getCleanerMethod.invoke(object,new Object[0]);
						cleaner.clean();
					}
					catch(Exception e) {
						e.printStackTrace(System.err);
					}
					return null;
				}
			});
	}

	protected BufferedImageSource bufferedImageSource = null;
	protected FileInputStream memoryMappedFileInputStream = null;
	protected FileChannel memoryMappedFileChannel = null;
	protected FileInputStream[] memoryMappedFileInputStreams = null;
	protected FileChannel[] memoryMappedFileChannels = null;
	protected MappedByteBuffer[] memoryMappedByteBuffers = null;
	
	public void close() throws Throwable {
//System.err.println("SourceImage.close()");
		bufferedImageSource=null;
		if (memoryMappedByteBuffers != null) {
			for (int f=0; f<memoryMappedByteBuffers.length; ++f) {
				ByteBuffer memoryMappedByteBuffer = memoryMappedByteBuffers[f];
				if (memoryMappedByteBuffer != null) {
					clean(memoryMappedByteBuffer);
					memoryMappedByteBuffers[f] = null;
				}
			}
			memoryMappedByteBuffers=null;
		}
		if (memoryMappedFileChannels != null) {
			for (int f=0; f<memoryMappedFileChannels.length; ++f) {
				memoryMappedFileChannels[f].close();
				memoryMappedFileChannels[f] = null;
			}
			memoryMappedFileChannels=null;
		}
		if (memoryMappedFileInputStreams != null) {
			for (int f=0; f<memoryMappedFileInputStreams.length; ++f) {
				memoryMappedFileInputStreams[f].close();
				memoryMappedFileInputStreams[f] = null;
			}
			memoryMappedFileInputStreams=null;
		}
		if (memoryMappedFileChannel != null) {
			memoryMappedFileChannel.close();
			memoryMappedFileChannel = null;
		}
		if (memoryMappedFileInputStream != null) {
			memoryMappedFileInputStream.close();
			memoryMappedFileInputStream = null;
		}
	}
	
	protected void finalize() throws Throwable {
//System.err.println("SourceImage.finalize()");
		close();
		super.finalize();
	}
	
	protected static int memoryMapperNumberOfRetries = 100;
	protected static int memoryMapperSleepTimeBetweenRetries = 1000;	// ms
	protected static int memoryMapperRetriesBeforeSleeping = 10;
	
	/**
	 * <p>Get memory mapped file buffers for the specified attribute.</p>
	 *
	 * @param		oad				the attribute on disk to memory map
	 * @return						an array of byte buffers, one per frame
	 * @throws	DicomException		if cannot memory map file
	 * @throws	IOException			of cannot open file
	 * @throws	Throwable			if problem closing memory mapped file
	 */
	
	protected ByteBuffer[] getByteBuffersFromOtherByteAttributeMultipleFilesOnDisk(OtherByteAttributeMultipleFilesOnDisk oad,int nframes) throws DicomException, IOException, Throwable {
		// keep track of all the intermediaries so we can explicitly close them later (e.g., on finalization)
		memoryMappedFileInputStreams = new FileInputStream[nframes];
		memoryMappedFileChannels = new FileChannel[nframes];
		memoryMappedByteBuffers = new MappedByteBuffer[nframes];		// allocated at class level to make sure are release during close() ... ? necessary ? :(
		boolean success = true;
		File[] files = oad.getFiles();
		long[] byteOffsets =  oad.getByteOffsets();
		long[] lengths =  oad.getLengths();
		Exception einside = null;
		for (int f=0; f<nframes; ++f) {
			// Why repeatedly retry ? See "http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5092131"
			// The key is to make sure that the finalize() method for any uncollected and unfinalized
			// memory mapped buffers is called (and of course that we have specified finalize methods
			// in our BufferedImageSource classes for those that need to null any buffer references)
			// before we try to map another large file; this is presumed to be needed because reaping
			// the small heap objects that are associated with mapped buffers is not a priority for
			// the garbage collector
			MappedByteBuffer memoryMappedByteBufferForFrame = null;
			File file = files[f];
//System.err.println("SourceImage.getByteBuffersFromOtherByteAttributeMultipleFilesOnDisk(): File "+file);
			FileInputStream memoryMappedFileInputStreamForFrame = null;
			FileChannel memoryMappedFileChannelForFrame = null;
			try {
				memoryMappedFileInputStreamForFrame = new FileInputStream(file);
				memoryMappedFileChannelForFrame = memoryMappedFileInputStream.getChannel();
				if (memoryMappedFileChannelForFrame == null) {										// not sure how this can happen, but sometimes it does
System.err.println("SourceImage.getByteBuffersFromOtherByteAttributeMultipleFilesOnDisk(): memoryMappedFileInputStream.getChannel() returned null ! - try again");
					memoryMappedFileChannelForFrame = memoryMappedFileInputStream.getChannel();		// so try once more
				}
			}
			catch (FileNotFoundException e) {
				throw new DicomException("Cannot find file to memory map "+file+" "+e);
			}
			int retryCount = 0;
			int retryBeforeSleeping = 0;
			while (memoryMappedByteBufferForFrame == null && retryCount < memoryMapperNumberOfRetries) {	// often only takes once or twice, may take more than 10
				long byteOffset = byteOffsets[f];
//System.err.println("SourceImage.getByteBuffersFromOtherByteAttributeMultipleFilesOnDisk(): Byte offset for frame "+f+" is "+byteOffset);
				long length = lengths[f];
//System.err.println("SourceImage.getByteBuffersFromOtherByteAttributeMultipleFilesOnDisk(): Frame size for frame "+f+" is "+length);
				memoryMappedByteBufferForFrame = memoryMappedFileChannelForFrame.map(FileChannel.MapMode.READ_ONLY,byteOffset,length);
				if (memoryMappedByteBufferForFrame == null) {
System.err.println("SourceImage.getByteBuffersFromOtherByteAttributeMultipleFilesOnDisk(): Attempt to memory map frame "+f+" failed");
					if (retryBeforeSleeping >= memoryMapperRetriesBeforeSleeping) {
						retryBeforeSleeping=0;
						try {
System.err.println("SourceImage.getByteBuffersFromOtherByteAttributeMultipleFilesOnDisk(): sleeping");
							Thread.currentThread().sleep(memoryMapperSleepTimeBetweenRetries);
System.err.println("SourceImage.getByteBuffersFromOtherByteAttributeMultipleFilesOnDisk(): back from sleep");
						}
						catch (InterruptedException e) {
							e.printStackTrace(System.err);
						}
					}
System.err.println("SourceImage.getByteBuffersFromOtherByteAttributeMultipleFilesOnDisk(): retrycount = "+retryCount);
System.err.println("SourceImage.getByteBuffersFromOtherByteAttributeMultipleFilesOnDisk(): free  memory = "+Runtime.getRuntime().freeMemory());
System.err.println("SourceImage.getByteBuffersFromOtherByteAttributeMultipleFilesOnDisk(): max   memory = "+Runtime.getRuntime().maxMemory());
System.err.println("SourceImage.getByteBuffersFromOtherByteAttributeMultipleFilesOnDisk(): total memory = "+Runtime.getRuntime().totalMemory());
					try {
System.err.println("SourceImage.getByteBuffersFromOtherByteAttributeMultipleFilesOnDisk(): requesting gc and runFinalization");
						System.gc();
						System.runFinalization();	// OK to run finalization, as long as we don't call close, since we will be hanging on to earlier successfully mapped frames
System.err.println("SourceImage.getByteBuffersFromOtherByteAttributeMultipleFilesOnDisk(): back from gc and runFinalization");
					}
					catch (Exception e) {
						e.printStackTrace(System.err);
						einside=e;
					}
					++retryCount;
					++retryBeforeSleeping;
				}
			}
			if (memoryMappedByteBufferForFrame == null) {
				success=false;
				break;
			}
			else {
				memoryMappedByteBuffers[f] = memoryMappedByteBufferForFrame;
			}
		}
		if (!success) {
			close();	// cleans memoryMappedByteBuffers and closes all associated channels and streams
			throw new DicomException("Cannot memory map files"+einside);
		}
		return memoryMappedByteBuffers;
	}

	/**
	 * <p>Get memory mapped file buffers for the specified attribute.</p>
	 *
	 * @param		oad				the attribute on disk to memory map
	 * @return						an array of byte buffers, one per frame
	 * @throws	DicomException		if cannot memory map file
	 * @throws	IOException			of cannot open file
	 * @throws	Throwable			if problem closing memory mapped file
	 */
	
	protected ByteBuffer[] getByteBuffersFromOtherAttributeOnDisk(OtherAttributeOnDisk oad,int nframes) throws DicomException, IOException, Throwable {
		// keep track of all the intermediaries so we can explicitly close them later (e.g., on finalization)
//System.err.println("SourceImage.getByteBuffersFromOtherAttributeOnDisk(): File "+oad.getFile());
		memoryMappedFileInputStream = null;
		memoryMappedFileChannel = null;
		try {
			memoryMappedFileInputStream = new FileInputStream(oad.getFile());
			memoryMappedFileChannel = memoryMappedFileInputStream.getChannel();
		}
		catch (FileNotFoundException e) {
			throw new DicomException("Cannot find file to memory map "+oad.getFile()+" "+e);
		}
		memoryMappedByteBuffers = new MappedByteBuffer[nframes];		// allocated at class level to make sure are release during close() ... ? necessary ? :(
		boolean success = true;
		int framesizebytes = (int)(oad.getVL()/nframes);
//System.err.println("SourceImage.getByteBuffersFromOtherAttributeOnDisk(): framesizebytes "+framesizebytes);
		Exception einside = null;
		for (int f=0; f<nframes; ++f) {
			// Why repeatedly retry ? See "http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5092131"
			// The key is to make sure that the finalize() method for any uncollected and unfinalized
			// memory mapped buffers is called (and of course that we have specified finalize methods
			// in our BufferedImageSource classes for those that need to null any buffer references)
			// before we try to map another large file; this is presumed to be needed because reaping
			// the small heap objects that are associated with mapped buffers is not a priority for
			// the garbage collector
			MappedByteBuffer memoryMappedByteBuffer = null;
			int retryCount = 0;
			int retryBeforeSleeping = 0;
			while (memoryMappedByteBuffer == null && retryCount < memoryMapperNumberOfRetries) {	// often only takes once or twice, may take more than 10
				long byteOffset = oad.getByteOffset()+f*framesizebytes;
//System.err.println("SourceImage.constructSourceImage(): Byte offset for frame "+f+" is "+byteOffset);
				memoryMappedByteBuffer = memoryMappedFileChannel.map(FileChannel.MapMode.READ_ONLY,byteOffset,framesizebytes);
				if (memoryMappedByteBuffer == null) {
System.err.println("SourceImage.getByteBuffersFromOtherAttributeOnDisk(): Attempt to memory map frame "+f+" failed");
					if (retryBeforeSleeping >= memoryMapperRetriesBeforeSleeping) {
						retryBeforeSleeping=0;
						try {
System.err.println("SourceImage.getByteBuffersFromOtherAttributeOnDisk(): sleeping");
							Thread.currentThread().sleep(memoryMapperSleepTimeBetweenRetries);
System.err.println("SourceImage.getByteBuffersFromOtherAttributeOnDisk(): back from sleep");
						}
						catch (InterruptedException e) {
							e.printStackTrace(System.err);
						}
					}
System.err.println("SourceImage.getByteBuffersFromOtherAttributeOnDisk(): retrycount = "+retryCount);
System.err.println("SourceImage.getByteBuffersFromOtherAttributeOnDisk(): free  memory = "+Runtime.getRuntime().freeMemory());
System.err.println("SourceImage.getByteBuffersFromOtherAttributeOnDisk(): max   memory = "+Runtime.getRuntime().maxMemory());
System.err.println("SourceImage.getByteBuffersFromOtherAttributeOnDisk(): total memory = "+Runtime.getRuntime().totalMemory());
					try {
System.err.println("SourceImage.getByteBuffersFromOtherAttributeOnDisk(): requesting gc and runFinalization");
						System.gc();
						System.runFinalization();	// OK to run finalization, as long as we don't call close, since we will be hanging on to earlier successfully mapped frames
System.err.println("SourceImage.getByteBuffersFromOtherAttributeOnDisk(): back from gc and runFinalization");
					}
					catch (Exception e) {
						e.printStackTrace(System.err);
						einside=e;
					}
					++retryCount;
					++retryBeforeSleeping;
				}
			}
			if (memoryMappedByteBuffer == null) {
				success=false;
				break;
			}
			else {
				memoryMappedByteBuffers[f] = memoryMappedByteBuffer;
			}
		}
		if (!success) {
			close();	// cleans memoryMappedByteBuffers and closes all associated channels and streams
			throw new DicomException("Cannot memory map file "+oad.getFile()+" "+einside);
		}
		return memoryMappedByteBuffers;
	}

	/**
	 * <p>Get a memory mapped file buffer for value of the specified attribute.</p>
	 *
	 * @param		oad				the attribute on disk to memory map
	 * @param		nframes			the number of frames
	 * @return						an array of short buffers
	 * @throws	DicomException		if cannot memory map file
	 * @throws	IOException			of cannot open file
	 * @throws	Throwable			if problem closing memory mapped file
	 */
	
	protected ShortBuffer[] getShortBuffersFromOtherWordAttributeOnDisk(OtherWordAttributeOnDisk oad,int nframes) throws DicomException, IOException, Throwable {
		ShortBuffer[] shortBuffers = new ShortBuffer[nframes];
		ByteBuffer[] memoryMappedByteBuffers = getByteBuffersFromOtherAttributeOnDisk(oad,nframes);		// sets memoryMappedByteBuffers
		for (int f=0; f<nframes; ++f) {
			ByteBuffer memoryMappedByteBuffer = memoryMappedByteBuffers[f];
			memoryMappedByteBuffer.order(oad.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
			ShortBuffer shortBuffer = memoryMappedByteBuffer.asShortBuffer();
			shortBuffers[f]=shortBuffer;
		}
		return shortBuffers;
	}

	/**
	 * @param	list
	 * @throws	DicomException
	 */
	void constructSourceImage(AttributeList list) throws DicomException {
//System.err.println("constructSourceImage - start");

		//double imgSum=0;
		//double imgSumOfSquares=0;

		title=AttributeList.buildInstanceTitleFromAttributeList(list);

		width		= Attribute.getSingleIntegerValueOrDefault(list,TagFromName.Columns,0);
//System.err.println("SourceImage.constructSourceImage(): width="+width);
		height		= Attribute.getSingleIntegerValueOrDefault(list,TagFromName.Rows,0);
//System.err.println("SourceImage.constructSourceImage(): height="+height);
		int depth 	= Attribute.getSingleIntegerValueOrDefault(list,TagFromName.BitsAllocated,0);
//System.err.println("SourceImage.constructSourceImage(): depth="+depth);
		int samples	= Attribute.getSingleIntegerValueOrDefault(list,TagFromName.SamplesPerPixel,1);
//System.err.println("SourceImage.constructSourceImage(): samples="+samples);
		boolean byplane	= (samples > 1 && Attribute.getSingleIntegerValueOrDefault(list,TagFromName.PlanarConfiguration,0) == 1);
//System.err.println("SourceImage.constructSourceImage(): byplane="+byplane);
		nframes		= Attribute.getSingleIntegerValueOrDefault(list,TagFromName.NumberOfFrames,1);
//System.err.println("SourceImage.constructSourceImage(): nframes="+nframes);

		mask = 0;
		int extend = 0;
		int signbit = 1;
		int stored = Attribute.getSingleIntegerValueOrDefault(list,TagFromName.BitsStored,depth);
//System.err.println("SourceImage.constructSourceImage(): stored="+stored);
		if (depth < stored) {
			throw new DicomException("Unsupported Bits Allocated "+depth+"\" less then Bits Stored "+stored);
		}
		{
			int s = stored;
			while (s-- > 0) {
				mask=mask<<1 | 1;
				signbit=signbit<<1;
			}
			signbit=signbit>>1;
			extend=~mask;
		}
//System.err.println("SourceImage.constructSourceImage(): mask=0x"+Integer.toHexString(mask));
//System.err.println("SourceImage.constructSourceImage(): extend=0x"+Integer.toHexString(extend));
//System.err.println("SourceImage.constructSourceImage(): signbit=0x"+Integer.toHexString(signbit));

		signed	= Attribute.getSingleIntegerValueOrDefault(list,TagFromName.PixelRepresentation,0) == 1;

		imgMin=signed ? 0x00007fff : 0x0000ffff;	// i.e. start with the largest  possible 16 bit +ve value, sign extended to the full Java int 32 bits
		imgMax=signed ? 0xffff8000 : 0x00000000;	// i.e. start with the smallest possible 16 bit -ve value, sign extended to the full Java int 32 bits

//System.err.println("SourceImage.constructSourceImage(): signed="+signed);

		pad=0;
		hasPad=false;
		useMaskedPadRange=false;
		useNonMaskedSinglePadValue=false;
		Attribute aPixelPaddingValue = list.get(TagFromName.PixelPaddingValue);
		if (aPixelPaddingValue != null) {
			hasPad=true;
			pad=aPixelPaddingValue.getSingleIntegerValueOrDefault(0);
			padRangeLimit=Attribute.getSingleIntegerValueOrDefault(list,TagFromName.PixelPaddingRangeLimit,pad);
//System.err.println("SourceImage.constructSourceImage(): hasPad="+hasPad);
//System.err.println("SourceImage.constructSourceImage(): pad=0x"+Integer.toHexString(pad)+" ("+pad+" dec)");
//System.err.println("SourceImage.constructSourceImage(): padRangeLimit=0x"+Integer.toHexString(padRangeLimit)+" ("+padRangeLimit+" dec)");
			useMaskedPadRangeStart = pad&mask;
			useMaskedPadRangeEnd = padRangeLimit&mask;
			if (useMaskedPadRangeStart == (pad & 0x0000ffff) && useMaskedPadRangeEnd == (padRangeLimit & 0x0000ffff)) {
//System.err.println("SourceImage.constructSourceImage(): Padding values are within mask range and hence valid");
				useMaskedPadRange = true;
				if (useMaskedPadRangeStart > useMaskedPadRangeEnd) {
					int tmp = useMaskedPadRangeEnd;
					useMaskedPadRangeEnd = useMaskedPadRangeStart;
					useMaskedPadRangeStart = tmp;
				}
//System.err.println("SourceImage.constructSourceImage(): useMaskedPadRangeStart="+useMaskedPadRangeStart);
//System.err.println("SourceImage.constructSourceImage(): useMaskedPadRangeEnd="+useMaskedPadRangeEnd);
			}
			else {
//System.err.println("SourceImage.constructSourceImage(): Padding values are outside mask range and theoretically invalid - ignore any range and just use fixed value of PixelPaddingValue");
				useNonMaskedSinglePadValue = true;
				nonMaskedSinglePadValue = pad;
			}
		}

		String vPhotometricInterpretation = Attribute.getSingleStringValueOrDefault(list,TagFromName.PhotometricInterpretation,"MONOCHROME2");

//System.err.println("SourceImage.constructSourceImage(): vPhotometricInterpretation="+vPhotometricInterpretation);
		if (vPhotometricInterpretation.equals("MONOCHROME2")) {
			isGrayscale = true;
		}
		else if (vPhotometricInterpretation.equals("MONOCHROME1")) {
			isGrayscale = true;
			inverted = true;
		}
		else if (vPhotometricInterpretation.equals("PALETTE COLOR")) {
			isPaletteColor = true;
		}
		else if (vPhotometricInterpretation.equals("YBR_FULL")) {
			isYBR = !list.getDecompressedPhotometricInterpretation(vPhotometricInterpretation).equals("RGB");	// i.e., if not already handled by codec
		}

//System.err.println("SourceImage.constructSourceImage(): inverted="+inverted);
//System.err.println("SourceImage.constructSourceImage(): isGrayscale="+isGrayscale);
//System.err.println("SourceImage.constructSourceImage(): isPaletteColor="+isPaletteColor);
//System.err.println("SourceImage.constructSourceImage(): isYBR="+isYBR);

		// Get palette color LUT stuff, if present ...
		
		Attribute aLargestMonochromePixelValue  = list.get(TagFromName.LargestMonochromePixelValue);
		Attribute aRedPaletteColorLookupTableDescriptor = list.get(TagFromName.RedPaletteColorLookupTableDescriptor);
		Attribute aGreenPaletteColorLookupTableDescriptor = list.get(TagFromName.GreenPaletteColorLookupTableDescriptor);
		Attribute aBluePaletteColorLookupTableDescriptor = list.get(TagFromName.BluePaletteColorLookupTableDescriptor);
	
		largestGray = signed ? 0x00007fff : 0x0000ffff;			// default to largest possible in case nothing found
		boolean usedLargestMonochromePixelValue=false;
		if (aLargestMonochromePixelValue != null && aLargestMonochromePixelValue.getVM() == 1) {
			usedLargestMonochromePixelValue=true;
			largestGray=aLargestMonochromePixelValue.getIntegerValues()[0];
		}
		boolean usedLargestImagePixelValue=false;
		if (usedLargestMonochromePixelValue == false) {			// encountered this in an old MR SOP Class Siemens MR image
			Attribute aLargestImagePixelValue  = list.get(TagFromName.LargestImagePixelValue);
			if (aLargestImagePixelValue != null && aLargestImagePixelValue.getVM() == 1) {
				usedLargestImagePixelValue=true;
				largestGray=aLargestImagePixelValue.getIntegerValues()[0];
			}
		}
                
 		//boolean usedFirstValueMapped=false;
 		if (aRedPaletteColorLookupTableDescriptor != null && aGreenPaletteColorLookupTableDescriptor != null && aBluePaletteColorLookupTableDescriptor != null) {
			// the descriptors should all be the same; should check but let's be lazy and just use one ...
			if (aRedPaletteColorLookupTableDescriptor != null && aRedPaletteColorLookupTableDescriptor.getVM() == 3) {
				numberOfEntries = aRedPaletteColorLookupTableDescriptor.getIntegerValues()[0];
				if (numberOfEntries == 0) numberOfEntries=65536;
				firstValueMapped = aRedPaletteColorLookupTableDescriptor.getIntegerValues()[1];
				String pixelPresentation = Attribute.getSingleStringValueOrEmptyString(list,TagFromName.PixelPresentation);
				if ((usedLargestMonochromePixelValue == false && usedLargestImagePixelValue == false)
				 || list.get(TagFromName.PhotometricInterpretation).getStringValues()[0].equals("PALETTE COLOR")	// [bugs.mrmf] (000102) Palette Color image displays as gray when Largest Pixel Value present
				 || !(pixelPresentation.equals("COLOR") || pixelPresentation.equals("MIXED"))) {					// override largestGray when there is no specific indication that goal is Supplemental Palette Color
					//usedFirstValueMapped=true;
					largestGray=firstValueMapped-1;			// if a pure color image then firstValueMapped will be 0, and largestGray will be -1
//System.err.println("SourceImage.constructSourceImage(): not treating palette as supplemental, using firstValueMapped "+firstValueMapped+" to set largestGray = "+largestGray);
				}
				else {
//System.err.println("SourceImage.constructSourceImage(): treating palette as supplemental, largestGray = "+largestGray);
				}
				bitsPerEntry = aRedPaletteColorLookupTableDescriptor.getIntegerValues()[2];
				if (bitsPerEntry > 0) {
					Attribute aRedPaletteColorLookupTableData = list.get(TagFromName.RedPaletteColorLookupTableData);
//System.err.println("SourceImage.constructSourceImage(): aRedPaletteColorLookupTableData = "+aRedPaletteColorLookupTableData);
					Attribute aGreenPaletteColorLookupTableData = list.get(TagFromName.GreenPaletteColorLookupTableData);
					Attribute aBluePaletteColorLookupTableData = list.get(TagFromName.BluePaletteColorLookupTableData);
					if (aRedPaletteColorLookupTableData != null && aGreenPaletteColorLookupTableData != null && aBluePaletteColorLookupTableData != null) {
//System.err.println("SourceImage.constructSourceImage(): setting color palette tables");
						redTable = aRedPaletteColorLookupTableData.getShortValues();
//System.err.println("SourceImage.constructSourceImage(): redTable = "+redTable);
						greenTable = aGreenPaletteColorLookupTableData.getShortValues();
						blueTable = aBluePaletteColorLookupTableData.getShortValues();
						if (redTable == null || greenTable == null || blueTable == null
						 || redTable.length == 0 || greenTable.length == 0 || blueTable.length == 0) {
							// occasionally see an incorrect image with attribute present but nothing in it ... (000570)
System.err.println("SourceImage.constructSourceImage(): bad color palette (empty data), ignoring");
							redTable=null;
							greenTable=null;
							blueTable=null;
						}
					}
				}
				else {
System.err.println("SourceImage.constructSourceImage(): bad color palette (zero value for bitsPerEntry), ignoring");
					// else was just bad ... bitsPerEntry is a better flag than numberOfEntries and firstValueMapped, since the last two may legitimately be zero, not not the first ... (000570)
				}
			}
		}
//System.err.println("SourceImage.constructSourceImage(): largestGray="+largestGray);


		{
			// not sure if this ever returns anything other than sRGB ... :(
			try {
				for (GraphicsDevice gd : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
					for (GraphicsConfiguration gc : gd.getConfigurations()) {
						dstColorSpace = gc.getColorModel().getColorSpace();
//System.err.println("SourceImage.constructSourceImage(): Using GraphicsEnvironment derived dstColorSpace="+dstColorSpace+" which is "+(dstColorSpace.isCS_sRGB() ? "" : "not ")+"sRGB");
					}
				}
			}
			catch (java.awt.HeadlessException e) {
				dstColorSpace = ColorSpace.getInstance(ColorSpace.CS_sRGB);
//System.err.println("SourceImage.constructSourceImage(): Using default sRGB for dstColorSpace because Headless");
			}
				
			if (dstColorSpace == null) {
				dstColorSpace = ColorSpace.getInstance(ColorSpace.CS_sRGB);
//System.err.println("SourceImage.constructSourceImage(): Using default sRGB for dstColorSpace");
			}
		}
		
		{
			Attribute aICCProfile = list.get(TagFromName.ICCProfile);
			if (aICCProfile != null) {
				byte[] iccProfileBytes = aICCProfile.getByteValues();
				ICC_Profile iccProfile = ICC_Profile.getInstance(iccProfileBytes);
//System.err.println("SourceImage.constructSourceImage(): read ICC Profile="+iccProfile);
				srcColorSpace = new ICC_ColorSpace(iccProfile);
			}
			else {
//System.err.println("SourceImage.constructSourceImage(): Using sRGB as source color space");
				srcColorSpace = ColorSpace.getInstance(ColorSpace.CS_sRGB);
			}
		}

		bufferedImageSource = null;
		imgs = null;
		
		int nframepixels=width*height;
		int nframesamples=nframepixels*samples;
//System.err.println("SourceImage.constructSourceImage(): nframesamples="+nframesamples);
		int nsamples=nframesamples*nframes;
		int npixels=nframepixels*nframes;
//System.err.println("SourceImage.constructSourceImage(): isGrayscale="+isGrayscale);
//System.err.println("SourceImage.constructSourceImage(): samples="+samples);
//System.err.println("SourceImage.constructSourceImage(): depth="+depth);
//System.err.println("SourceImage.constructSourceImage(): stored="+stored);
		// The following assumes that BitsStored (aka. stored) is always <= BitsAllocated (aka. depth) (checked earlier)
		if ((isGrayscale || isPaletteColor) && samples == 1 && depth > 8 && depth <= 16) {
//System.err.println("SourceImage.constructSourceImage(): grayscale or palette color 9-16 bits");
			// note that imgMin and imgMax are populated on demand when BufferedImages are actually created
			Attribute a = list.getPixelData();
			if (a instanceof OtherByteAttributeMultipleCompressedFrames) {
//System.err.println("SourceImage.constructSourceImage(): one or more compressed frames");
				byte[][] compressedData = ((OtherByteAttributeMultipleCompressedFrames)a).getFrames();
				String compressedDataTransferSyntaxUID = Attribute.getSingleStringValueOrEmptyString(list,TagFromName.TransferSyntaxUID);
				if (signed) {
					bufferedImageSource = new SignedShortGrayscaleBufferedImageSource(compressedData,width,height,mask,signbit,extend,largestGray,compressedDataTransferSyntaxUID);
				}
				else {
					bufferedImageSource = new UnsignedShortGrayscaleBufferedImageSource(compressedData,width,height,mask,largestGray,compressedDataTransferSyntaxUID);
				}
			}
			else if (allowMemoryMapping && a instanceof OtherWordAttributeOnDisk) {
//System.err.println("SourceImage.constructSourceImage(): OW left on disk ... attempting memory mapping");
				try {
					ShortBuffer[] shortBuffers = getShortBuffersFromOtherWordAttributeOnDisk((OtherWordAttributeOnDisk)a,nframes);
					if (signed) {
						bufferedImageSource = new SignedShortGrayscaleBufferedImageSource(shortBuffers,width,height,mask,signbit,extend,largestGray);
					}
					else {
						bufferedImageSource = new UnsignedShortGrayscaleBufferedImageSource(shortBuffers,width,height,mask,largestGray);
					}
				}
				catch (Throwable e) {
					e.printStackTrace(System.err);
					bufferedImageSource = null;
				}
			}
			if (bufferedImageSource == null) {	// did not attempt to memory map or attempt failed
				if (a instanceof OtherWordAttributeOnDisk) {
//System.err.println("SourceImage.constructSourceImage(): OW left on disk ... memory mapping "+(allowMemoryMapping ? "failed" : "disabled"));
					if (allowDeferredReadFromFileIfNotMemoryMapped) {
//System.err.println("SourceImage.constructSourceImage(): OW left on disk ... using deferred read of shorts from multi-frame file");
						String transferSyntaxUID = Attribute.getSingleStringValueOrEmptyString(list,TagFromName.TransferSyntaxUID);
						boolean bigEndian = TransferSyntax.isBigEndian(transferSyntaxUID);
						File file = ((OtherWordAttributeOnDisk)a).getFile();
						long[] byteOffsetsPerFrame = new long[nframes];
						long byteOffsetToStartOfPixelDataValue = ((OtherWordAttributeOnDisk)a).getByteOffset();
						for (int f=0; f<nframes; ++f) {
							byteOffsetsPerFrame[f] = byteOffsetToStartOfPixelDataValue + f * nframesamples * 2;		// don't forget there are two bytes per sample!
						}
						if (signed) {
							bufferedImageSource = new SignedShortGrayscaleBufferedImageSource(file,byteOffsetsPerFrame,bigEndian,width,height,mask,signbit,extend,largestGray);
						}
						else {
							bufferedImageSource = new UnsignedShortGrayscaleBufferedImageSource(file,byteOffsetsPerFrame,bigEndian,width,height,mask,largestGray);
						}
					}
					else {
//System.err.println("SourceImage.constructSourceImage(): OW left on disk ... deferred read disabled, so reading into per frame short arrays rather than contiguous array for all frames");
						short[][] dataPerFrame = new short[nframes][];
						for (int f=0; f<nframes; ++f) {
							dataPerFrame[f] = ((OtherWordAttributeOnDisk)a).getShortValuesForSelectedFrame(f,nframes);
						}
						if (signed) {
							bufferedImageSource = new SignedShortGrayscaleBufferedImageSource(dataPerFrame,width,height,mask,signbit,extend,largestGray);
						}
						else {
							bufferedImageSource = new UnsignedShortGrayscaleBufferedImageSource(dataPerFrame,width,height,mask,largestGray);
						}
					}
				}
				else {
//System.err.println("SourceImage.constructSourceImage(): not deferred decompression, was not left on disk or memory mapping "+(allowMemoryMapping ? "failed" : "disabled")+" so using conventional heap allocated values");
					short data[] = a.getShortValues();
					if (signed) {
						bufferedImageSource = new SignedShortGrayscaleBufferedImageSource(data,width,height,mask,signbit,extend,largestGray);
					}
					else {
						bufferedImageSource = new UnsignedShortGrayscaleBufferedImageSource(data,width,height,mask,largestGray);
					}
				}
			}
		}
		else if ((isGrayscale || isPaletteColor) && samples == 1 && depth <= 8 && depth > 1) {
//System.err.println("SourceImage.constructSourceImage(): grayscale or palette color <= 8 bits");
			Attribute a = list.getPixelData();
			if (a instanceof OtherByteAttributeMultipleCompressedFrames) {
//System.err.println("SourceImage.constructSourceImage(): one or more compressed frames");
				byte[][] compressedData = ((OtherByteAttributeMultipleCompressedFrames)a).getFrames();
				String compressedDataTransferSyntaxUID = Attribute.getSingleStringValueOrEmptyString(list,TagFromName.TransferSyntaxUID);
				if (signed) {
					bufferedImageSource = new SignedByteGrayscaleBufferedImageSource(compressedData,width,height,mask,signbit,extend,largestGray,compressedDataTransferSyntaxUID);
				}
				else {
					bufferedImageSource = new UnsignedByteGrayscaleBufferedImageSource(compressedData,width,height,mask,largestGray,compressedDataTransferSyntaxUID);
				}
			}
			else if (allowMemoryMapping && a instanceof OtherWordAttributeOnDisk) {
//System.err.println("SourceImage.constructSourceImage(): OW left on disk ... attempting memory mapping");
				try {
					ShortBuffer[] shortBuffers = getShortBuffersFromOtherWordAttributeOnDisk((OtherWordAttributeOnDisk)a,nframes);
					if (signed) {
						bufferedImageSource = new SignedByteGrayscaleBufferedImageSource(shortBuffers,width,height,mask,signbit,extend,largestGray);
					}
					else {
						bufferedImageSource = new UnsignedByteGrayscaleBufferedImageSource(shortBuffers,width,height,mask,largestGray);
					}
				}
				catch (Throwable e) {
					e.printStackTrace(System.err);
					bufferedImageSource = null;
				}
			}
			else if (allowMemoryMapping && a instanceof OtherByteAttributeOnDisk) {
//System.err.println("SourceImage.constructSourceImage(): OB left on disk ... attempting memory mapping");
				try {
					ByteBuffer[] byteBuffers = getByteBuffersFromOtherAttributeOnDisk((OtherByteAttributeOnDisk)a,nframes);
					if (signed) {
						bufferedImageSource = new SignedByteGrayscaleBufferedImageSource(byteBuffers,width,height,mask,signbit,extend,largestGray);
					}
					else {
						bufferedImageSource = new UnsignedByteGrayscaleBufferedImageSource(byteBuffers,width,height,mask,largestGray);
					}
				}
				catch (Throwable e) {
					e.printStackTrace(System.err);
					bufferedImageSource = null;
				}
			}
			if (bufferedImageSource == null) {	// did not attempt to memory map or attempt failed
//System.err.println("SourceImage.constructSourceImage(): not deferred decompression, was not left on disk or memory mapping "+(allowMemoryMapping ? "failed" : "disabled"));
				if (a instanceof OtherByteAttributeOnDisk) {
//System.err.println("SourceImage.constructSourceImage(): OB left on disk");
					if (allowDeferredReadFromFileIfNotMemoryMapped) {
//System.err.println("SourceImage.constructSourceImage(): OB left on disk so using deferred read from from multi-frame file");
						File file = ((OtherByteAttributeOnDisk)a).getFile();
						long[] byteOffsetsPerFrame = new long[nframes];
						long byteOffsetToStartOfPixelDataValue = ((OtherByteAttributeOnDisk)a).getByteOffset();
						for (int f=0; f<nframes; ++f) {
							byteOffsetsPerFrame[f] = byteOffsetToStartOfPixelDataValue + f * nframesamples;
						}
						if (signed) {
							bufferedImageSource = new SignedByteGrayscaleBufferedImageSource(file,byteOffsetsPerFrame,width,height,mask,signbit,extend,largestGray);
						}
						else {
							bufferedImageSource = new UnsignedByteGrayscaleBufferedImageSource(file,byteOffsetsPerFrame,width,height,mask,largestGray);
						}
					}
					else {
//System.err.println("SourceImage.constructSourceImage(): OB left on disk and deferred read disabled so reading into separate per frame byte arrays");
						byte[][] dataPerFrame = ((OtherByteAttributeOnDisk)a).getByteValuesPerFrame(nframes);
						if (signed) {
							bufferedImageSource = new SignedByteGrayscaleBufferedImageSource(dataPerFrame,width,height,mask,signbit,extend,largestGray);
						}
						else {
							bufferedImageSource = new UnsignedByteGrayscaleBufferedImageSource(dataPerFrame,width,height,mask,largestGray);
						}
					}
				}
				else if (a instanceof OtherWordAttributeOnDisk) {
//System.err.println("SourceImage.constructSourceImage(): OW left on disk");
					String transferSyntaxUID = Attribute.getSingleStringValueOrEmptyString(list,TagFromName.TransferSyntaxUID);
					if (allowDeferredReadFromFileIfNotMemoryMapped && TransferSyntax.isLittleEndian(transferSyntaxUID)) {
//System.err.println("SourceImage.constructSourceImage(): OW left on disk ... little-endian so using deferred read of bytes (i.e., as if OB) from multi-frame file");
						File file = ((OtherWordAttributeOnDisk)a).getFile();
						long[] byteOffsetsPerFrame = new long[nframes];
						long byteOffsetToStartOfPixelDataValue = ((OtherWordAttributeOnDisk)a).getByteOffset();
						for (int f=0; f<nframes; ++f) {
							byteOffsetsPerFrame[f] = byteOffsetToStartOfPixelDataValue + f * nframesamples;
						}
						if (signed) {
							bufferedImageSource = new SignedByteGrayscaleBufferedImageSource(file,byteOffsetsPerFrame,width,height,mask,signbit,extend,largestGray);
						}
						else {
							bufferedImageSource = new UnsignedByteGrayscaleBufferedImageSource(file,byteOffsetsPerFrame,width,height,mask,largestGray);
						}
					}
					else {
//System.err.println("SourceImage.constructSourceImage(): OW left on disk ... deferred read disabled, so reading into per frame short arrays");
						short[][] dataPerFrame = new short[nframes][];
						for (int f=0; f<nframes; ++f) {
							dataPerFrame[f] = ((OtherWordAttributeOnDisk)a).getShortValuesForSelectedFrame(f,nframes);
						}
						if (signed) {
							bufferedImageSource = new SignedByteGrayscaleBufferedImageSource(dataPerFrame,width,height,mask,signbit,extend,largestGray);
						}
						else {
							bufferedImageSource = new UnsignedByteGrayscaleBufferedImageSource(dataPerFrame,width,height,mask,largestGray);
						}
					}
				}
				else if (a instanceof OtherByteAttributeMultipleFrameArrays) {
//System.err.println("SourceImage.constructSourceImage(): have separate per frame byte arrays");
					// do not make this dependent on allowDeferredReadFromFileIfNotMemoryMapped ... i.e., always do it, since is special case in support of ImageEditUtilities.blackout() :(
					byte[][] dataPerFrame = ((OtherByteAttributeMultipleFrameArrays)a).getByteValuesPerFrame();
					if (signed) {
						bufferedImageSource = new SignedByteGrayscaleBufferedImageSource(dataPerFrame,width,height,mask,signbit,extend,largestGray);
					}
					else {
						bufferedImageSource = new UnsignedByteGrayscaleBufferedImageSource(dataPerFrame,width,height,mask,largestGray);
					}
				}
				else if (a instanceof OtherByteAttribute) {
//System.err.println("SourceImage.constructSourceImage(): contiguous byte array");
					byte[] data = a.getByteValues();
					if (signed) {
						bufferedImageSource = new SignedByteGrayscaleBufferedImageSource(data,width,height,mask,signbit,extend,largestGray);
					}
					else {
						bufferedImageSource = new UnsignedByteGrayscaleBufferedImageSource(data,width,height,mask,largestGray);
					}
				}
				else if (a instanceof OtherWordAttribute) {
//System.err.println("SourceImage.constructSourceImage(): contiguous short array");
					short[] data = a.getShortValues();
					if (signed) {
						bufferedImageSource = new SignedByteGrayscaleBufferedImageSource(data,width,height,mask,signbit,extend,largestGray);
					}
					else {
						bufferedImageSource = new UnsignedByteGrayscaleBufferedImageSource(data,width,height,mask,largestGray);
					}
				}
				else {
System.err.println("SourceImage.constructSourceImage(): "+a.getClass()+" is not recognized Attribute class from which to construct SourceImage");
				}
			}
		}
		else if (isGrayscale && samples == 1 && depth == 1) {
//System.err.println("SourceImage.constructSourceImage(): single bit");
			// see also com.pixelmed.dicom.Overlay for similar pattern of extracting bits from OB or OW and making BufferedImage of TYPE_BYTE_BINARY
			imgs=new BufferedImage[nframes];
			IndexColorModel colorModel = null;
			{
				byte[] r = { (byte)0, (byte)255 };
				byte[] g = { (byte)0, (byte)255 };
				byte[] b = { (byte)0, (byte)255 };
				colorModel = new IndexColorModel(
							1 /* bits */,
							2 /* size */,
							r,g,b/*,
							java.awt.Transparency.OPAQUE*/);
			}
			imgMin=0;	// set these to allow correct default window values to be chosen (000706)
			imgMax=1;
			Attribute a = list.getPixelData();
			int wi=0;
			int bitsRemaining=0;
			int word = 0;
			boolean badBitOrder = false;
			//int onBitCount = 0;
//System.err.println("SourceImage.constructSourceImage(): "+a);
			if (ValueRepresentation.isOtherByteVR(a.getVR())) {
//System.err.println("SourceImage.constructSourceImage(): single bit from OB with "+(badBitOrder ? "bad" : "standard")+" bit order");
				byte data[] = a.getByteValues();
				for (int f=0; f<nframes; ++f) {
					imgs[f] = new BufferedImage(width,height,BufferedImage.TYPE_BYTE_BINARY,colorModel);
					Raster raster = imgs[f].getData();
					SampleModel sampleModel = raster.getSampleModel();
					DataBuffer dataBuffer = raster.getDataBuffer();
					for (int row=0; row<height; ++row) {
						for (int column=0; column<width; ++column) {
							if (bitsRemaining <= 0) {
								word = data[wi++] & 0xff;
								bitsRemaining=8;
							}
							int bit = badBitOrder ? (word & 0x0080) : (word & 0x0001);
							if (bit != 0) {
//System.err.println("SourceImage.constructSourceImage(): got a bit set at frame "+f+" row "+row+" column "+column);
								sampleModel.setSample(column,row,0/*bank*/,1,dataBuffer);
								//++onBitCount;
							}
							word = badBitOrder ? (word << 1) : (word >>> 1);
							--bitsRemaining;
						}
					}
					imgs[f].setData(raster);
				}
			}
			else {
//System.err.println("SourceImage.constructSourceImage(): single bit from OW with "+(badBitOrder ? "bad" : "standard")+" bit order");
				short data[] = a.getShortValues();
				for (int f=0; f<nframes; ++f) {
					imgs[f] = new BufferedImage(width,height,BufferedImage.TYPE_BYTE_BINARY,colorModel);
					Raster raster = imgs[f].getData();
					SampleModel sampleModel = raster.getSampleModel();
					DataBuffer dataBuffer = raster.getDataBuffer();
					for (int row=0; row<height; ++row) {
						for (int column=0; column<width; ++column) {
							if (bitsRemaining <= 0) {
								word = data[wi++] & 0xffff;
								bitsRemaining=16;
							}
							int bit = badBitOrder ? (word & 0x8000) : (word & 0x0001);
							if (bit != 0) {
								sampleModel.setSample(column,row,0/*bank*/,1,dataBuffer);
								//++onBitCount;
							}
							word = badBitOrder ? (word << 1) : (word >>> 1);
							--bitsRemaining;
						}
					}
					imgs[f].setData(raster);
				}
			}
//System.err.println("SourceImage.constructSourceImage(): onBitCount =  "+onBitCount);
		}
		else if (!isGrayscale && samples == 3 && depth <= 8 && depth > 1) {
//System.err.println("SourceImage.constructSourceImage(): not grayscale, is 3 channel and <= 8 bits");
			byte data[] = null;
			byte dataPerFrame[][] = null;
			ByteBuffer[] byteBuffers = null;
			ShortBuffer[] shortBuffers = null;
			File file = null;
			File[] filesPerFrame = null;
			long[] byteOffsetsPerFrame = null;
			byte[][] compressedData = null;
			String compressedDataTransferSyntaxUID = null;
			Attribute a = list.getPixelData();
			
			if (a instanceof OtherByteAttributeMultipleCompressedFrames) {
//System.err.println("SourceImage.constructSourceImage(): one or more compressed frames");
				compressedData = ((OtherByteAttributeMultipleCompressedFrames)a).getFrames();
				compressedDataTransferSyntaxUID = Attribute.getSingleStringValueOrEmptyString(list,TagFromName.TransferSyntaxUID);
			}
			else if (ValueRepresentation.isOtherByteVR(a.getVR())) {
				if (allowMemoryMapping && a instanceof OtherByteAttributeOnDisk) {
//System.err.println("SourceImage.constructSourceImage(): OB left on disk ... attempting memory mapping from single large file");
					try {
						byteBuffers = getByteBuffersFromOtherAttributeOnDisk((OtherByteAttributeOnDisk)a,nframes);
					}
					catch (Throwable e) {
						e.printStackTrace(System.err);
					}
				}
				else if (a instanceof OtherByteAttributeMultipleFilesOnDisk) {
					if (allowMemoryMapping && allowMemoryMappingFromMultiplePerFrameFiles) {
//System.err.println("SourceImage.constructSourceImage(): OB left on disk ... attempting memory mapping from individual files per frame");
						try {
							byteBuffers = getByteBuffersFromOtherByteAttributeMultipleFilesOnDisk((OtherByteAttributeMultipleFilesOnDisk)a,nframes);	// this doesn't work well ... runs out of memmory
						}
						catch (Throwable e) {
							e.printStackTrace(System.err);
						}
					}
					if (byteBuffers == null) {
//System.err.println("SourceImage.constructSourceImage(): OB left on disk ... memory mapping "+(allowMemoryMapping ? "failed" : "disabled")+", using deferred read from individual files per frame");
						// do not make this dependent on allowDeferredReadFromFileIfNotMemoryMapped ... i.e., always do it, since is special case in support of ImageEditUtilities.blackout() :(
						filesPerFrame = ((OtherByteAttributeMultipleFilesOnDisk)a).getFiles();
						byteOffsetsPerFrame = ((OtherByteAttributeMultipleFilesOnDisk)a).getByteOffsets();
					}
				}
				if (byteBuffers == null && filesPerFrame == null) {	// did not attempt to memory map or attempt failed or do not have individual files per frame
//System.err.println("SourceImage.constructSourceImage(): OB not deferred decompression, was not left on disk or memory mapping "+(allowMemoryMapping ? "failed" : "disabled")+" so using conventional read or heap allocated values");
					if (a instanceof OtherByteAttributeMultipleFrameArrays) {	// e.g., after application of ImageEditUtilities.blackout()
//System.err.println("SourceImage.constructSourceImage(): have separate per frame byte arrays");
						// do not make this dependent on allowDeferredReadFromFileIfNotMemoryMapped ... i.e., always do it, since is special case in support of ImageEditUtilities.blackout() :(
						dataPerFrame = ((OtherByteAttributeMultipleFrameArrays)a).getByteValuesPerFrame();
					}
					else if (a instanceof OtherByteAttributeOnDisk) {	// if memory mapping fails or is disabled
						if (allowDeferredReadFromFileIfNotMemoryMapped) {
//System.err.println("SourceImage.constructSourceImage(): OB left on disk so using deferred read from from multi-frame file");
							file = ((OtherByteAttributeOnDisk)a).getFile();
							byteOffsetsPerFrame = new long[nframes];
							long byteOffsetToStartOfPixelDataValue = ((OtherByteAttributeOnDisk)a).getByteOffset();
							for (int f=0; f<nframes; ++f) {
								byteOffsetsPerFrame[f] = byteOffsetToStartOfPixelDataValue + f * nframesamples;
							}
						}
						else {
//System.err.println("SourceImage.constructSourceImage(): OB left on disk and deferred read disabled so reading into separate per frame byte arrays");
							dataPerFrame = ((OtherByteAttributeOnDisk)a).getByteValuesPerFrame(nframes);
						}
					}
					else {
//System.err.println("SourceImage.constructSourceImage(): have contiguous byte array");
						data = a.getByteValues();
					}
				}
			}
			else {
//System.err.println("SourceImage.constructSourceImage(): OW rather than OB");
				if (a instanceof OtherWordAttributeOnDisk) {
					if (allowMemoryMapping) {
//System.err.println("SourceImage.constructSourceImage(): OW left on disk ... attempting memory mapping");
						try {
							shortBuffers = getShortBuffersFromOtherWordAttributeOnDisk((OtherWordAttributeOnDisk)a,nframes);
						}
						catch (Throwable e) {
							e.printStackTrace(System.err);
							shortBuffers = null;
						}
					}
					if (shortBuffers == null) {
//System.err.println("SourceImage.constructSourceImage(): OW left on disk ... memory mapping "+(allowMemoryMapping ? "failed" : "disabled"));
						String transferSyntaxUID = Attribute.getSingleStringValueOrEmptyString(list,TagFromName.TransferSyntaxUID);
						if (allowDeferredReadFromFileIfNotMemoryMapped && TransferSyntax.isLittleEndian(transferSyntaxUID)) {
//System.err.println("SourceImage.constructSourceImage(): OW left on disk ... little-endian so using deferred read of bytes (i.e., as if OB) from multi-frame file");
							file = ((OtherWordAttributeOnDisk)a).getFile();
							byteOffsetsPerFrame = new long[nframes];
							long byteOffsetToStartOfPixelDataValue = ((OtherWordAttributeOnDisk)a).getByteOffset();
							for (int f=0; f<nframes; ++f) {
								byteOffsetsPerFrame[f] = byteOffsetToStartOfPixelDataValue + f * nframesamples;
							}
						}
						else {
//System.err.println("SourceImage.constructSourceImage(): OW left on disk ... not little endian or deferred read disabled, so reading into per frame byte arrays rather than contiguous array for all frames");
							dataPerFrame = new byte[nframes][];
							for (int f=0; f<nframes; ++f) {
								short sdataforframe[] = ((OtherWordAttributeOnDisk)a).getShortValuesForSelectedFrame(f,nframes);	// NB. do not need to read them all at once, since only keeping extracted byte arrays !
								dataPerFrame[f] = getByteArrayForFrameFromSingleFrameShortArray(sdataforframe,nframesamples);
							}
						}
					}
				}
				else {
//System.err.println("SourceImage.constructSourceImage(): OW was not left on disk ... is contiguous array of shorts but split into per frame byte arrays rather than contiguous array for all frames");
					short sdata[] = a.getShortValues();
					dataPerFrame = new byte[nframes][];
					for (int f=0; f<nframes; ++f) {
						dataPerFrame[f] = getByteArrayForFrameFromMultiFrameShortArray(f,sdata,nframesamples);
					}
				}
			}
			
			// Note that we are really lying at this point if Photometric Interpretation is not RGB,		??? is this related to (000785) :(
			// e.g., YBR_FULL, in that the ColorModel created next will claim to be RGB (and will need
			// to be converted on display, etc., but this prevents us having to update the source
			// AttributeList to change the Photometric Interpretation attribute.
			if (compressedData != null) {
				CompressedByteRGBBufferedImageSource compressedSource = new CompressedByteRGBBufferedImageSource(compressedData,width,height,srcColorSpace,compressedDataTransferSyntaxUID);
				bufferedImageSource = compressedSource;
			}
			else if (byplane) {
				if (byteBuffers != null) {
					bufferedImageSource = new BandInterleavedByteRGBBufferedImageSource(byteBuffers,width,height,srcColorSpace);
				}
				else if (shortBuffers != null) {
					bufferedImageSource = new BandInterleavedByteRGBBufferedImageSource(shortBuffers,width,height,srcColorSpace);
				}
				else if (dataPerFrame != null) {
					bufferedImageSource = new BandInterleavedByteRGBBufferedImageSource(dataPerFrame,width,height,srcColorSpace);
				}
				else if (filesPerFrame != null) {
					bufferedImageSource = new BandInterleavedByteRGBBufferedImageSource(filesPerFrame,byteOffsetsPerFrame,width,height,srcColorSpace);
				}
				else if (file != null) {
					bufferedImageSource = new BandInterleavedByteRGBBufferedImageSource(file,byteOffsetsPerFrame,width,height,srcColorSpace);
				}
				else {
					bufferedImageSource = new BandInterleavedByteRGBBufferedImageSource(data,width,height,srcColorSpace);
				}
			}
			else {
				if (byteBuffers != null) {
					bufferedImageSource = new PixelInterleavedByteRGBBufferedImageSource(byteBuffers,width,height,srcColorSpace);
				}
				else if (shortBuffers != null) {
					bufferedImageSource = new PixelInterleavedByteRGBBufferedImageSource(shortBuffers,width,height,srcColorSpace);
				}
				else if (dataPerFrame != null) {
					bufferedImageSource = new PixelInterleavedByteRGBBufferedImageSource(dataPerFrame,width,height,srcColorSpace);
				}
				else if (filesPerFrame != null) {
					bufferedImageSource = new PixelInterleavedByteRGBBufferedImageSource(filesPerFrame,byteOffsetsPerFrame,width,height,srcColorSpace);
				}
				else if (file != null) {
					bufferedImageSource = new PixelInterleavedByteRGBBufferedImageSource(file,byteOffsetsPerFrame,width,height,srcColorSpace);
				}
				else {
					bufferedImageSource = new PixelInterleavedByteRGBBufferedImageSource(data,width,height,srcColorSpace);
				}
			}
		}
		else if (isGrayscale && samples == 1 && depth == 32) {
//System.err.println("SourceImage.constructSourceImage(): 32 bit image");
			Attribute a = list.getPixelData();
			if (a != null && a instanceof OtherFloatAttribute) {
				float data[] = a.getFloatValues();
				// mask,signbit,extend,largestGray are irrelevant ...
				bufferedImageSource = new FloatGrayscaleBufferedImageSource(data,width,height);
			}
			else {
				throw new DicomException("Unsupported 32 bit grayscale image encoding");
			}
		}
		else if (isGrayscale && samples == 1 && depth == 64) {
//System.err.println("SourceImage.constructSourceImage(): 64 bit image");
			Attribute a = list.getPixelData();
			if (a != null && a instanceof OtherDoubleAttribute) {
				double data[] = a.getDoubleValues();
				// mask,signbit,extend,largestGray are irrelevant ...
				bufferedImageSource = new DoubleGrayscaleBufferedImageSource(data,width,height);
			}
			else {
				throw new DicomException("Unsupported 64 bit grayscale image encoding");
			}
		}
		else {
			throw new DicomException("Unsupported image encoding: Photometric Interpretation = \""+vPhotometricInterpretation+"\", samples = "+samples+"\", Bits Allocated = "+depth+", Bits Stored = "+stored);
		}
		
		if (bufferedImageSource == null) {
			throw new DicomException("Failed to created a BufferedImage source");
		}
		
		//BufferedImageUtilities.describeImage(imgs[0],System.err);
                
		//imgMean=imgSum/nsamples;
		//imgStandardDeviation=Math.sqrt((imgSumOfSquares-imgSum*imgSum/nsamples)/nsamples);
//System.err.println("SourceImage.constructSourceImage(): imgMin="+imgMin);
//System.err.println("SourceImage.constructSourceImage(): imgMax="+imgMax);
//System.err.println("SourceImage.constructSourceImage(): imgSum="+imgSum);
//System.err.println("SourceImage.constructSourceImage(): imgSumOfSquares="+imgSumOfSquares);
//System.err.println("SourceImage.constructSourceImage(): imgMean="+imgMean);
//System.err.println("SourceImage.constructSourceImage(): imgStandardDeviation="+imgStandardDeviation);

		suvTransform = new SUVTransform(list);
		realWorldValueTransform = new RealWorldValueTransform(list);
//System.err.println("SourceImage.constructSourceImage(): realWorldValueTransform="+realWorldValueTransform);
		modalityTransform = new ModalityTransform(list);
		voiTransform = new VOITransform(list);
		displayShutter = new DisplayShutter(list);
		overlay = new Overlay(list);

		// Establish a suitable background value to be used during resizing operations PRIOR to windowing
		if (hasPad) {
			backgroundValue = pad;
		}
		else if (isGrayscale) {
			backgroundValue = inverted
				? (signed ? (mask>>1) : mask)					// largest value (will always be +ve)
				: (signed ? (((mask>>1)+1)|extend) : 0);		// smallest value (will be -ve if signed so extend into integer)
			// do NOT anticipate rescale values
		}
		else {
			backgroundValue = 0;
		}
//System.err.println("backgroundValue="+backgroundValue);

//System.err.println("constructSourceImage - end");
	}

	/**
	 * <p>Make a BufferedImage for the first or only frame.</p>
	 *
	 * <p>The BufferedImage will have the bit depth and photometric interpretation of the original SourceImage.</p>
	 *
	 * <p>If it is an RGB photometric interpretation, and an ICC profile is present, it will be applied.</p>
	 *
	 * @return		a BufferedImage for the first or only frame
	 */
	public BufferedImage getBufferedImage() { return getBufferedImage(0); }

	/**
	 * <p>Make a BufferedImage for the selected frame.</p>
	 *
	 * <p>The BufferedImage will have the bit depth and photometric interpretation of the original SourceImage.</p>
	 *
	 * <p>If it is an RGB photometric interpretation, and an ICC profile is present, it will be applied.</p>
	 *
	 * @param	i	frame number (from 0)
	 * @return		a BufferedImage for the selected frame
	 */
	public BufferedImage getBufferedImage(int i) {
		BufferedImage img = null;
		if (bufferedImageSource == null) {
//System.err.println("SourceImage.getBufferedImage(): from array not source");
			img = (imgs == null || i < 0 || i >= imgs.length) ? null : imgs[i];
		}
		else {
//System.err.println("SourceImage.getBufferedImage(): from bufferedImageSource");
			img = bufferedImageSource.getBufferedImage(i);

			if (img.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_RGB && srcColorSpace != null && dstColorSpace != null && srcColorSpace != dstColorSpace) {
System.err.println("SourceImage.getBufferedImage(): have color image with different source and destination color spaces - converting");
				try {
System.err.println("SourceImage.getBufferedImage(): System.getProperty(\"sun.java2d.cmm\") = "+System.getProperty("sun.java2d.cmm"));
					ColorConvertOp cco = new ColorConvertOp(srcColorSpace,dstColorSpace,new RenderingHints(RenderingHints.KEY_COLOR_RENDERING,RenderingHints.VALUE_COLOR_RENDER_QUALITY));
					BufferedImage convertedImg = cco.filter(img,null);
//System.err.println("SourceImage.getBufferedImage(): have converted color image ="+convertedImg);
					img = convertedImg;
				}
				catch (Exception e) {
					e.printStackTrace(System.err);
				}
			}
			
			// Why reset the min and max ? They may not previously have been set of frame not fetched from memory mapped file yet
			imgMin = bufferedImageSource.getMinimumPixelValueOfMostRecentBufferedImage(imgMin);
			imgMax = bufferedImageSource.getMaximumPixelValueOfMostRecentBufferedImage(imgMax);
//System.err.println("SourceImage.getBufferedImage(): resetting imgMin="+imgMin);
//System.err.println("SourceImage.getBufferedImage(): resetting imgMax="+imgMax);
		}
		return img;
	}

	/***/
	public int getNumberOfBufferedImages() { return nframes; }

	/***/
	public boolean isImage() { return bufferedImageSource != null; }

	/***/
	public int getWidth() { return width; }
        
	/***/
	public int getHeight() { return height; }
        
	/***/
	public Dimension getDimension() { return new Dimension(width,height); }
    
	/**
	 * @return	the minimum pixel value, excluding any pixels in the padding value range
	 */
	public double getMinimum() { return imgMin; }

	/**
	 * @return	the maximum pixel value, excluding any pixels in the padding value range
	 */
	public double getMaximum() { return imgMax; }

	//public double getMean() { return imgMean; }

	//public double getStandardDeviation() { return imgStandardDeviation; }

	/***/
	public int getMaskValue() { return mask; }

	/***/
	public boolean isSigned() { return signed; }

	/***/
	public boolean isInverted() { return inverted; }

	/***/
	public boolean isPadded() { return hasPad; }

	/***/
	public int getPadValue() { return pad; }

	/***/
	public int getPadRangeLimit() { return padRangeLimit; }

	/***/
	public int getBackgroundValue() { return backgroundValue; }

	/***/
	public boolean isGrayscale() { return isGrayscale; }

	/***/
	public boolean isYBR() { return isYBR; }

	/***/
	public String getTitle() { return title; }

	/***/
	public int getNumberOfFrames() { return nframes; }

	/***/
	public int getPaletteColorLargestGray() { return largestGray; }
	
	/***/
	public int getPaletteColorFirstValueMapped() { return firstValueMapped; }
	
	/***/
	public int getPaletteColorNumberOfEntries() { return numberOfEntries; }
	
	/***/
	public int getPaletteColorBitsPerEntry() { return bitsPerEntry; }
	
	/***/
	public short[] getPaletteColorRedTable() { return redTable; }
	
	/***/
	public short[] getPaletteColorGreenTable() { return greenTable; }
	
	/***/
	public short[] getPaletteColorBlueTable() { return blueTable; }
	
	/***/
	public SUVTransform getSUVTransform() { return suvTransform; }
	
	/***/
	public RealWorldValueTransform getRealWorldValueTransform() { return realWorldValueTransform; }
	
	/***/
	public ModalityTransform getModalityTransform() { return modalityTransform; }
	
	/***/
	public VOITransform getVOITransform() { return voiTransform; }
	
	/***/
	public DisplayShutter getDisplayShutter() { return displayShutter; }
	
	/***/
	public Overlay getOverlay() { return overlay; }
}
