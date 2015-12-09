package com.pixelmed.utils;

import com.pixelmed.dicom.*;
import com.pixelmed.display.ConsumerFormatImageMaker;
import com.pixelmed.display.SourceImage;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * @author <a href="mailto:k1.tehrani@gmail.com">k1</a>
 * @version $Rev$ $Date$ $Author$
 */
public class LosslessCompressDICOMSample {
    // Transfer Syntax UID = "1.2.840.10008.1.2.4.50 " is used for 8-bit images
    // Transfer Syntax UID = "1.2.840.10008.1.2.4.51 " is used for 12-bit images

    protected static DicomDictionary dictionary = new DicomDictionary();
    private static String DICOM_FILE = "1.2.410.200028.456.20151027.10647.1.1";
    private static String DICOM_EXT = ".dcm";
    private String SOURCE_FILE_FULL_PATH;
    private String OUTPUT_PATH;
    private static String TRANSFER_SYNTAX_UID = "1.2.840.10008.1.2.4.80";
    //    private static String TRANSFER_SYNTAX_UID = "1.2.840.10008.1.2.4.51";
//    private static String TRANSFER_SYNTAX = TransferSyntax.JPEGExtended;
    private static String TRANSFER_SYNTAX = TransferSyntax.JPEGLS;
    private static String CONVERTED_EXT = "jpeg";
    protected SpecificCharacterSet specificCharacterSet;
    private AttributeList list = new AttributeList();
    private File dicomOutputFolder;
    private String sopClassUID;
    private String sopInstanceUID;
    private String mediaStorageSOPClassUID;
    private String mediaStorageSOPInstanceUID;
    private String sourceApplicationEntityTitle;

    protected Attribute newAttribute(AttributeTag tag) throws DicomException {
        byte[] vr = dictionary.getValueRepresentationFromTag(tag);
        return AttributeFactory.newAttribute(tag, vr, specificCharacterSet);
    }

    public void displayImage() throws IOException, DicomException {
        System.out.println(list.toString());
        System.out.println("SOPClassUID:" + sopClassUID);
        System.out.println("SOPInstanceUID:" + sopInstanceUID);
        System.out.println("MediaStorageSOPInstanceUID:" + mediaStorageSOPInstanceUID);
        System.out.println("mediaStorageSOPClassUID:" + mediaStorageSOPClassUID);
        System.out.println("sourceApplicationEntityTitle:" + sourceApplicationEntityTitle);
        System.out.println("Transfer Syntax UID:" + Attribute.getDelimitedStringValuesOrEmptyString(list, TagFromName.TransferSyntaxUID));
        System.out.println("Modality:" + Attribute.getDelimitedStringValuesOrEmptyString(list, TagFromName.Modality));
        System.out.println("PhotometricInterpretation:" + Attribute.getDelimitedStringValuesOrEmptyString(list, TagFromName.PhotometricInterpretation));
        System.out.println("PixelSpacing:" + Attribute.getDelimitedStringValuesOrEmptyString(list, TagFromName.PixelSpacing));
        System.out.println("Samples per pixel:" + Attribute.getDelimitedStringValuesOrEmptyString(list, TagFromName.SamplesPerPixel));
        System.out.println("Bits Allocated:" + Attribute.getDelimitedStringValuesOrEmptyString(list, TagFromName.BitsAllocated));
        System.out.println("Bits stored:" + Attribute.getDelimitedStringValuesOrEmptyString(list, TagFromName.BitsStored));
        System.out.println("HighBit:" + Attribute.getDelimitedStringValuesOrEmptyString(list, TagFromName.HighBit));
        SourceImage img = new SourceImage(list);
        System.out.println("Number of frames:" + img.getNumberOfFrames());
        System.out.println("Width:" + img.getWidth());
        System.out.println("Height:" + img.getHeight());
        System.out.println("IsGreyscale:" + img.isGrayscale());
        System.out.println("Pixel Data Present:" + (list.getPixelData() != null));
        Attribute pixelAttribute = list.get(TagFromName.PixelData);
        if (pixelAttribute instanceof OtherWordAttribute) {
            short[] data = pixelAttribute.getShortValues();
            System.out.println("16 bit pixel data length:" + data.length);
        } else {
            System.out.println("pixel attribute class is " + pixelAttribute.getClass());
        }
        System.out.println(list.toString());
    }


    public File compressLossyDicomFile(String resultPath) throws DicomException, IOException {
        OUTPUT_PATH = resultPath;
        dicomOutputFolder = new File(OUTPUT_PATH);
        SourceImage img = new SourceImage(list);
        int numberOfFrames = img.getNumberOfFrames();

        File[] frameFiles = new File[numberOfFrames];
        BufferedImage[] bufferedImages = ConsumerFormatImageMaker.makeEightBitImages(list, 0);
        if (frameFiles.length != bufferedImages.length) {
            throw new RuntimeException("frameFiles size:" + frameFiles.length + " ,must match buffered images size" + bufferedImages.length);
        }
        for (int f = 0; f < numberOfFrames; ++f) {
            frameFiles[f] = File.createTempFile("tmpImage", ".jpeg");
            createImage(bufferedImages[f],frameFiles[f]);
        }

        list.remove(TagFromName.PixelData);
        list.removeGroupLengthAttributes();
        list.removeMetaInformationHeaderAttributes();
        {
            Attribute a = newAttribute(TagFromName.LossyImageCompression);
            a.addValue("01");
            list.put(a);
        }
        {
            Attribute a = newAttribute(TagFromName.LossyImageCompressionMethod);
            a.addValue("ISO_15444_1");
            list.put(a);
        }
        {
            Attribute a = newAttribute(TagFromName.LossyImageCompressionRatio);
            a.addValue(100);
            list.put(a);
        }
        {
            Attribute a = newAttribute(TagFromName.AllowLossyCompression);
            a.addValue("Allow Lossy Compression");
            list.put(a);
        }
        {
            Attribute a = newAttribute(TagFromName.TransferSyntaxUID);
            a.addValue(TRANSFER_SYNTAX_UID);
            list.put(a);
        }
        list.remove(TagFromName.BitsAllocated);
        list.remove(TagFromName.BitsStored);
        list.remove(TagFromName.HighBit);
        {
            Attribute a = newAttribute(TagFromName.BitsAllocated);
            a.addValue(8);
            list.put(a);
        }
        {
            Attribute a = newAttribute(TagFromName.BitsStored);
            a.addValue(8);
            list.put(a);
        }
        {
            Attribute a = newAttribute(TagFromName.HighBit);
            a.addValue(7);
            list.put(a);
        }

        list.remove(TagFromName.DataSetTrailingPadding);
        list.remove(TagFromName.WindowCenter);
        list.remove(TagFromName.WindowCenterWidthExplanation);
        list.remove(TagFromName.WindowWidth);

        OtherByteAttributeMultipleCompressedFrames aPixelData = new OtherByteAttributeMultipleCompressedFrames(TagFromName.PixelData, frameFiles);
        list.put(aPixelData);
        FileMetaInformation.addFileMetaInformation(list, mediaStorageSOPClassUID, mediaStorageSOPInstanceUID, TRANSFER_SYNTAX_UID, sourceApplicationEntityTitle);
        File outputFullPath = new File(dicomOutputFolder, Attribute.getSingleStringValueOrNull(list, TagFromName.SOPInstanceUID) + ".dcm");
        list.write(outputFullPath, TRANSFER_SYNTAX, true/*useMeta*/, true/*useBufferedStream*/);
        System.out.println("Compression finished");
        System.out.println("deleting temporary images...");
        for (File frameFile : frameFiles) {
            System.out.println("file:" + frameFile.getAbsolutePath() + " deleted? " + frameFile.delete());
        }
        return outputFullPath;
    }

    private void createImage(BufferedImage bufferedImage, File frameFile) throws IOException {
//        ImageIO.write(bufferedImages[f], CONVERTED_EXT, frameFiles[f]);
        ImageWriter writer= (ImageWriter) ImageIO.getImageWritersByFormatName(CONVERTED_EXT).next();
        ImageWriteParam param= writer.getDefaultWriteParam();
        param.setCompressionMode(param.MODE_EXPLICIT);
        param.setCompressionType("JPEG-LS");
        writer.setOutput(ImageIO.createImageOutputStream(frameFile));
        writer.write(null, new IIOImage(bufferedImage, null, null), param);
    }


    public void read(DicomInputStream in) throws IOException, DicomException {
//        list.setDecompressPixelData(false);
        list.clear();
        list.read(in);
        sopClassUID = Attribute.getDelimitedStringValuesOrEmptyString(list, TagFromName.SOPClassUID);
        sopInstanceUID = Attribute.getDelimitedStringValuesOrEmptyString(list, TagFromName.SOPInstanceUID);
        mediaStorageSOPInstanceUID = Attribute.getDelimitedStringValuesOrEmptyString(list, TagFromName.MediaStorageSOPInstanceUID);
        mediaStorageSOPClassUID = Attribute.getDelimitedStringValuesOrEmptyString(list, TagFromName.MediaStorageSOPClassUID);
        sourceApplicationEntityTitle = Attribute.getDelimitedStringValuesOrEmptyString(list, TagFromName.SourceApplicationEntityTitle);
    }

    public static void main(String[] args) throws IOException, DicomException {
        String source = "/home/k1/peyvand/tmp/" + DICOM_FILE + DICOM_EXT;
        String resultPath = "/home/k1/peyvand/convert/";
        LosslessCompressDICOMSample displayImageToConsole = new LosslessCompressDICOMSample();
        FileInputStream fis = new FileInputStream(source);
        DicomInputStream in = new DicomInputStream(new BufferedInputStream(fis));
        System.out.println("Reading DICOM file :" + source);
        displayImageToConsole.read(in);
        displayImageToConsole.displayImage();
        File resultFile = displayImageToConsole.compressLossyDicomFile(resultPath);
//        displayImageToConsole.createDICOMFromImage();
        in.close();
        fis.close();
        fis = new FileInputStream(resultPath + DICOM_FILE + DICOM_EXT);
        in = new DicomInputStream(new BufferedInputStream(fis));
        System.out.println("Reading DICOM file :" + resultPath+ DICOM_FILE + DICOM_EXT);
        displayImageToConsole.read(in);
        displayImageToConsole.displayImage();
        in.close();
        fis.close();
    }
}
