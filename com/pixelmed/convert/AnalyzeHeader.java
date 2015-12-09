/* Copyright (c) 2001-2015, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package com.pixelmed.convert;

import com.pixelmed.dicom.BinaryInputStream;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

import java.nio.charset.Charset;

import java.util.HashMap;
import java.util.Map;

public class AnalyzeHeader {
	
	private static final String identString = "@(#) $Header: /userland/cvs/pixelmed/imgbook/com/pixelmed/convert/AnalyzeHeader.java,v 1.1 2015/05/31 11:41:57 dclunie Exp $";
	
	public static final int FIXED_HEADER_LENGTH = 348;
	
	enum DataType {
		NONE			((short)0),		// same value as UNKNOWN
		BINARY			((short)1),
		UNSIGNED_CHAR	((short)2),
		SIGNED_SHORT	((short)4),
		SIGNED_INT		((short)8),
		FLOAT			((short)16),
		COMPLEX			((short)32),
		DOUBLE			((short)64),
		RGB				((short)128),
		ALL				((short)255);
		
		private short datatype;
		
		DataType(short datatype) {
			this.datatype = datatype;
		}
		
		// http://stackoverflow.com/questions/443980/why-cant-enums-constructor-access-static-fields

		static final Map<Short,DataType> map = new HashMap<Short,DataType>();
		
		static final DataType getDataType(short datatype) {
			return map.get(new Short(datatype));
		}
		
		static {
			for (DataType d : DataType.values()) {
				map.put(new Short(d.datatype),d);
			}
		}
	}


	enum Orient {
		TRANSVERSE_UNFLIPPED	((byte)0),
		CORONAL_UNFLIPPED		((byte)1),
		SAGITTAL_UNFLIPPED		((byte)2),
		TRANSVERSE_FLIPPED		((byte)3),
		CORONAL_FLIPPED			((byte)4),
		SAGITTAL_FLIPPED		((byte)5);

		private short orient;
		
		Orient(short orient) {
			this.orient = orient;
		}
		
		// http://stackoverflow.com/questions/443980/why-cant-enums-constructor-access-static-fields

		static final Map<Short,Orient> map = new HashMap<Short,Orient>();
		
		static final Orient getOrient(short orient) {
			return map.get(new Short(orient));
		}
		
		static {
			for (Orient x : Orient.values()) {
				map.put(new Short(x.orient),x);
			}
		}
	}
	
	public byte[] bytes;
	
	public boolean bigEndian;
	
	// use (more or less) the same names as in "official" ANALYZE 7.5 specification from Mayo
	
	public int sizeof_hdr;
	public short[] dim = new short[8];
	public short unused8;
	public short unused9;
	public short unused10;
	public short unused11;
	public short unused12;
	public short unused13;
	public short unused14;
	public short datatype_code;
	public DataType datatype;
	public short bitpix;
	public short dim_un0;
	public float[] pixdim = new float[8];
	public float vox_offset;
	public float funused1;
	public float funused2;
	public float funused3;
	public float cal_max;
	public float cal_min;
	public float compressed;
	public float verified;
	public int glmax;
	public int glmin;

	public byte[] description = new byte[80];
	public byte[] aux_file = new byte[24];
	public byte orient_code;
	public Orient orient;
	public byte[] originator = new byte[10];
	public byte[] generated = new byte[10];
	public byte[] scannum = new byte[10];
	public byte[] patient_id = new byte[10];
	public byte[] exp_date = new byte[10];
	public byte[] exp_time = new byte[10];
	public byte[] hist_un0 = new byte[3];
	public int views;
	public int vols_added;
	public int start_field;
	public int field_skip;
	public int omax;
	public int omin;
	public int smax;
	public int smin;

	public static File getImageDataFile(File headerFile) {
		return new File(headerFile.getParent(),headerFile.getName().replaceFirst("[.][^.]*$",".img"));
	}

	public AnalyzeHeader(File inputFile) throws IOException, AnalyzeException {
		BinaryInputStream fhin = new BinaryInputStream(inputFile,false/*assume little endian to start*/);
		bytes = new byte[FIXED_HEADER_LENGTH];
		fhin.readInsistently(bytes,0,FIXED_HEADER_LENGTH);
		fhin.close();
//System.err.println("Entire header = "+com.pixelmed.utils.HexDump.dump(bytes));
//System.err.println("Magic number = "+com.pixelmed.utils.HexDump.dump(bytes,MAGIC_OFFSET,4));
		{
			BinaryInputStream hin = new BinaryInputStream(new ByteArrayInputStream(bytes),false/*assume little endian to start*/);
			hin.mark(4);
			// one is theoretically supposed to use dim[0] to determine byte order, but no reason not to use the earlier sizeof_hdr
			sizeof_hdr =  (int)hin.readUnsigned32();
			if (sizeof_hdr != FIXED_HEADER_LENGTH) {
				hin.setBigEndian();
				hin.reset();
				sizeof_hdr = (int)hin.readUnsigned32();
				if (sizeof_hdr != FIXED_HEADER_LENGTH) {
					throw new AnalyzeException("Cannot determine Analyze endianness from sizeof_hdr byte order since does not match required size");
				}
				bigEndian = true;
System.err.println("Is BigEndian");
			}
			else {
				bigEndian = false;
System.err.println("Is LittleEndian");
			}
System.err.println("Have Analyze header");
			
			hin.skip(36);	// ignore rest of "header_key" structure
			
			for (int i=0; i<dim.length; ++i) {
				dim[i] = (short)hin.readUnsigned16();
System.err.println("dim["+i+"] = "+dim[i]);
			}
			
			unused8 = (short)hin.readUnsigned16();
System.err.println("unused8 = "+((int)unused8&0xffff));
			unused9 = (short)hin.readUnsigned16();
System.err.println("unused9 = "+((int)unused9&0xffff));
			unused10 = (short)hin.readUnsigned16();
System.err.println("unused10 = "+((int)unused10&0xffff));
			unused11 = (short)hin.readUnsigned16();
System.err.println("unused11 = "+((int)unused11&0xffff));
			unused12 = (short)hin.readUnsigned16();
System.err.println("unused12 = "+((int)unused12&0xffff));
			unused13 = (short)hin.readUnsigned16();
System.err.println("unused13 = "+((int)unused13&0xffff));
			unused14 = (short)hin.readUnsigned16();
System.err.println("unused14 = "+((int)unused14&0xffff));

			datatype_code = (short)hin.readUnsigned16();
			datatype = DataType.getDataType(datatype_code);
System.err.println("datatype = "+((int)datatype_code&0xffff)+" "+datatype);

			bitpix = (short)hin.readUnsigned16();
System.err.println("bitpix = "+((int)bitpix&0xffff));

			dim_un0 = (short)hin.readUnsigned16();
System.err.println("dim_un0 = "+((int)dim_un0&0xffff));

			for (int i=0; i<pixdim.length; ++i) {
				pixdim[i] = hin.readFloat();
System.err.println("pixdim["+i+"] = "+pixdim[i]);
			}
			vox_offset = hin.readFloat();
System.err.println("vox_offset = "+vox_offset);

			funused1 = hin.readFloat();
System.err.println("funused1 = "+funused1);
			funused2 = hin.readFloat();
System.err.println("funused2 = "+funused2);
			funused3 = hin.readFloat();
System.err.println("funused3 = "+funused3);

			cal_max = hin.readFloat();
System.err.println("cal_max = "+cal_max);
			cal_min = hin.readFloat();
System.err.println("cal_min = "+cal_min);

			compressed = hin.readFloat();
System.err.println("compressed = "+compressed);
			verified = hin.readFloat();
System.err.println("verified = "+verified);

			glmax = (int)hin.readUnsigned32();
System.err.println("glmax = "+((long)glmax&0xffffffffl));
			glmin = (int)hin.readUnsigned32();
System.err.println("glmin = "+((long)glmin&0xffffffffl));
	
			hin.readInsistently(description,0,description.length);
System.err.println("description = "+new String(description,Charset.forName("US-ASCII")));
			hin.readInsistently(aux_file,0,aux_file.length);
System.err.println("aux_file = "+new String(aux_file,Charset.forName("US-ASCII")));

			orient_code = (byte)hin.readUnsigned8();
			orient = Orient.getOrient(orient_code);
System.err.println("orient = "+((int)orient_code&0xff)+" "+orient);

			hin.readInsistently(originator,0,originator.length);
System.err.println("originator = "+new String(originator,Charset.forName("US-ASCII")));
			hin.readInsistently(generated,0,generated.length);
System.err.println("generated = "+new String(generated,Charset.forName("US-ASCII")));
			hin.readInsistently(scannum,0,scannum.length);
System.err.println("scannum = "+new String(scannum,Charset.forName("US-ASCII")));
			hin.readInsistently(patient_id,0,patient_id.length);
System.err.println("patient_id = "+new String(patient_id,Charset.forName("US-ASCII")));
			hin.readInsistently(exp_date,0,exp_date.length);
System.err.println("exp_date = "+new String(exp_date,Charset.forName("US-ASCII")));
			hin.readInsistently(exp_time,0,exp_time.length);
System.err.println("exp_time = "+new String(exp_time,Charset.forName("US-ASCII")));
			hin.readInsistently(hist_un0,0,hist_un0.length);
System.err.println("hist_un0 = "+new String(hist_un0,Charset.forName("US-ASCII")));

			views = (int)hin.readUnsigned32();
System.err.println("views = "+((long)views&0xffffffffl));
			vols_added = (int)hin.readUnsigned32();
System.err.println("vols_added = "+((long)vols_added&0xffffffffl));
			start_field = (int)hin.readUnsigned32();
System.err.println("start_field = "+((long)start_field&0xffffffffl));
			field_skip = (int)hin.readUnsigned32();
System.err.println("field_skip = "+((long)field_skip&0xffffffffl));
			omax = (int)hin.readUnsigned32();
System.err.println("omax = "+((long)omax&0xffffffffl));
			omin = (int)hin.readUnsigned32();
System.err.println("omin = "+((long)omin&0xffffffffl));
			smax = (int)hin.readUnsigned32();
System.err.println("smax = "+((long)smax&0xffffffffl));
			smin = (int)hin.readUnsigned32();
System.err.println("smin = "+((long)smin&0xffffffffl));
		}
	}

	/**
	 * <p>Read an Analyze image input format files and dump header.</p>
	 *
	 * @param	arg	the inputFile,
	 */
	public static void main(String arg[]) {
		try {
			if (arg.length == 1) {
				new AnalyzeHeader(new File(arg[0]));
			}
			else {
				System.err.println("Error: Incorrect number of arguments");
				System.err.println("Usage: AnalyzeHeader inputFile");
				System.exit(1);
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}
}

