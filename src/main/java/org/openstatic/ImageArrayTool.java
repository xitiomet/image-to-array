package org.openstatic;

import java.awt.image.BufferedImage;
import java.awt.image.AffineTransformOp;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;
import java.awt.Color;

import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.ArrayList;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.*;
import org.apache.commons.io.FilenameUtils;

import net.ifok.image.image4j.codec.ico.ICOEncoder;
import com.github.gino0631.icns.*;

public class ImageArrayTool
{
    public static String sourceImageBaseName;
    public static String sourceImagePath;
    public static String sourceImageFileName;
    public static byte[] sourceData;
    public static BufferedImage sourceImage;
    public static LinkedHashMap<GeoColor, String> ansiColors;
    
    private static GeoColor[][] convertTo2DArray(BufferedImage image)
    {
      int width = image.getWidth();
      int height = image.getHeight();
      GeoColor[][] result = new GeoColor[height][width];
      for (int row = 0; row < height; row++)
      {
         for (int col = 0; col < width; col++)
         {
            result[row][col] = new GeoColor(image.getRGB(col, row));
         }
      }
      return result;
    }
    
    private static BufferedImage applyPalette(BufferedImage image, Set<GeoColor> palette)
    {
        BufferedImage newImage = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());
        int width = image.getWidth();
        int height = image.getHeight();
        for (int row = 0; row < height; row++)
        {
            for (int col = 0; col < width; col++)
            {
                //System.err.println("x=" + String.valueOf(col) + " y=" + String.valueOf(row));
                GeoColor oldColor = new GeoColor(image.getRGB(col, row));
                GeoColor newColor = oldColor.nearestColor(palette);
                if (newColor != null)
                {
                    newImage.setRGB(col, row, newColor.getRGB());
                }
            }
        }
        return newImage;
    }
    
    private static Set<GeoColor> getPalette(BufferedImage image)
    {
        ArrayList<Integer> colors = new ArrayList<Integer>();
        int width = image.getWidth();
        int height = image.getHeight();
        for (int row = 0; row < height; row++)
        {
            for (int col = 0; col < width; col++)
            {
                Integer rgb = Integer.valueOf(image.getRGB(col, row) | 0xFF000000);
                if (!colors.contains(rgb))
                {
                    colors.add(rgb);
                }
            }
        }
        colors.sort((a, b) -> { return a.intValue() - b.intValue(); });
        LinkedHashSet<GeoColor> geoColors = new LinkedHashSet<GeoColor>();
        Iterator<Integer> colorsIterator = colors.iterator();
        while(colorsIterator.hasNext())
        {
            GeoColor pixel = new GeoColor(colorsIterator.next().intValue());
            geoColors.add(pixel);
        }
        return geoColors;
    }
    
    public static Set<GeoColor> reducePalette(Set<GeoColor> palette, int newSize)
    {
        int ps = palette.size();
        if (ps > newSize)
        {
            int m = Math.round(((float)ps) / ((float)newSize));
            int i = 0;
            LinkedHashSet<GeoColor> geoColors = new LinkedHashSet<GeoColor>();
            Iterator<GeoColor> colorsIterator = palette.iterator();
            while(colorsIterator.hasNext())
            {
                GeoColor nc = colorsIterator.next();
                if (i % m == 0)
                {
                    geoColors.add(nc);
                }
                i++;
            }
            return geoColors;
        } else {
            return palette;
        }
    }
    
    private static Set<GeoColor> getGeoColorSet(BufferedImage image, double distanceFilter)
    {
        LinkedHashSet<GeoColor> colors = new LinkedHashSet<GeoColor>();
        int width = image.getWidth();
        int height = image.getHeight();
        for (int row = 0; row < height; row++)
        {
            for (int col = 0; col < width; col++)
            {
                GeoColor pixel = new GeoColor(image.getRGB(col, row));
                GeoColor nearest = pixel.nearestColor(colors);
                
                if (nearest == null)
                {
                    colors.add(pixel);
                } else {
                    double d = nearest.distanceFrom(pixel);
                    if (d > distanceFilter)
                    {
                        colors.add(pixel);
                    }
                }
            }
        }
        ArrayList<GeoColor> geoColors = new ArrayList<GeoColor>(colors);
        geoColors.sort((a, b) -> { return a.getRGB() - b.getRGB(); });
        return new LinkedHashSet<GeoColor>(geoColors);
    }
    
    public static String getAnsiCodeFromRGB(GeoColor col)
    {        
        Set<GeoColor> colors = ImageArrayTool.ansiColors.keySet();
        GeoColor colour = col.nearestColor(colors);
        String name = ImageArrayTool.ansiColors.get(colour);
        return name;
    }

    public static String bufferedImageTypeName(int biType)
    {
        switch(biType)
        {
            case BufferedImage.TYPE_3BYTE_BGR:
                return "3BYTE_BGR";
            case BufferedImage.TYPE_4BYTE_ABGR:
                return "4BYTE_ABGR";
            case BufferedImage.TYPE_4BYTE_ABGR_PRE:
                return "4BYTE_ABGR_PRE";
            case BufferedImage.TYPE_BYTE_BINARY:
                return "BYTE_BINARY";
            case BufferedImage.TYPE_BYTE_GRAY:
                return "BYTE_GRAY";
            case BufferedImage.TYPE_BYTE_INDEXED:
                return "BYTE_INDEXED";
            case BufferedImage.TYPE_CUSTOM:
                return "CUSTOM";
            case BufferedImage.TYPE_INT_ARGB:
                return "INT_ARGB";
            case BufferedImage.TYPE_INT_ARGB_PRE:
                return "INT_ARGB_PRE";
            case BufferedImage.TYPE_INT_BGR:
                return "INT_BGR";
            case BufferedImage.TYPE_INT_RGB:
                return "INT_RGB";
            case BufferedImage.TYPE_USHORT_555_RGB:
                return "USHORT_555_RGB";
            case BufferedImage.TYPE_USHORT_565_RGB:
                return "USHORT_565_RGB";
            case BufferedImage.TYPE_USHORT_GRAY:
                return "USHORT_GRAY";
            default:
                return "UNKNOWN";
        }
    }
    
    
    public static BufferedImage resizeImage(String amount, BufferedImage in_image)
    {
        float scale_to_float = 0;
        float w = 0;
        float h = 0;
        float o_w = (float) in_image.getWidth();
        float o_h = (float) in_image.getHeight();
        if (amount.contains("x"))
        {
            String[] spl = amount.split("x");
            w = Float.valueOf(spl[0]).floatValue();
            h = Float.valueOf(spl[1]).floatValue();
        } else {
            scale_to_float = Float.valueOf(amount).floatValue();
            w = (o_w * scale_to_float);
            h = (o_h * scale_to_float);
        }
        int iType = in_image.getType();
        if (iType == BufferedImage.TYPE_INT_ARGB || iType == BufferedImage.TYPE_4BYTE_ABGR)
        {
            //debugMessage("Alpha Channel Detected on Scaling");
            AffineTransform at = new AffineTransform();
            at.scale(w/o_w, h/o_h);
            AffineTransformOp scaleOp = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
            BufferedImage rgbi = new BufferedImage(in_image.getWidth(), in_image.getHeight(), iType);
            rgbi.createGraphics().drawImage(in_image, 0, 0, new Color(1f,1f,1f,0f), null);
            BufferedImage ri = new BufferedImage((int)w, (int)h, iType);
            scaleOp.filter(rgbi, ri);
            return ri;
        } else {
            AffineTransform at = new AffineTransform();
            at.scale(w/o_w, h/o_h);
            AffineTransformOp scaleOp = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
            BufferedImage rgbi = new BufferedImage(in_image.getWidth(), in_image.getHeight(), BufferedImage.TYPE_INT_RGB);
            rgbi.createGraphics().drawImage(in_image, 0, 0, Color.WHITE, null);
            BufferedImage ri = new BufferedImage((int)w, (int)h, BufferedImage.TYPE_INT_RGB);
            scaleOp.filter(rgbi, ri);
            return ri;
        }
    }
    
    // Produce Ascii art string of all the colors in the set
    public static String getColorStrip(Set<GeoColor> colors)
    {
        StringBuffer sb = new StringBuffer();
        Iterator<GeoColor> colorIterator = colors.iterator();
        int i = 0;
        while(colorIterator.hasNext())
        {
            GeoColor col = colorIterator.next();
            sb.append("\u001B[38;2;" + String.valueOf(col.getRed()) + ";" + String.valueOf(col.getGreen()) + ";" + String.valueOf(col.getBlue()) + "m\u2588");
            if (i >= 20)
            {
                sb.append("\n");
                i=0;
            } else {
                i++;
            }
        }
        return sb.toString() + "\u001B[0m";
    }
    
    // Convert 2 dimensional GeoColor array into ascii art
    public static String getAsciiArt(GeoColor[][] ary, boolean lineNumbers)
    {
        StringBuffer sb = new StringBuffer();
        for (int row = 0; row < ary.length; row++)
        {
            if (lineNumbers)
                sb.append( "\u001B[1m\u001B[97m" + String.format("%03d" ,row) + ":\u001B[0m ");
            for (int col = 0; col < ary[row].length; col++)
            {
                sb.append("\u001B[38;2;" + String.valueOf(ary[row][col].getRed()) + ";" + String.valueOf(ary[row][col].getGreen()) + ";" + String.valueOf(ary[row][col].getBlue()) + "m\u2588\u2588");
                //System.out.print(getAnsiCodeFromRGB(ary[row][col]) + "\u2588\u2588");
            }
            sb.append("\n");
        }
        sb.append("\u001B[0m");
        return sb.toString();
    }
    
    public static int getHueFromColor(GeoColor c)
    {
        int red = c.getRed();
        int green = c.getGreen();
        int blue = c.getBlue();
        float[] hsb = Color.RGBtoHSB(red, green, blue, null);
        float hue = hsb[0]; 
        float saturation = hsb[1];
        float brightness = hsb[2];
        //System.err.println("[r=" + String.valueOf(red) + " g=" + String.valueOf(green) + " b=" + String.valueOf(blue) + "] hue=" + String.valueOf(hue) + " saturation=" + String.valueOf(saturation) + " brightness=" + String.valueOf(brightness));

        return Math.round(hue * 360f);
    }

    public static boolean isImage(byte[] bytes)
    {
        try
        {
            BufferedImage bi = ImageIO.read(new ByteArrayInputStream(bytes));
            return bi.getType() != BufferedImage.TYPE_CUSTOM;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isSVG(byte[] bytes)
    {
        String data = (new String(bytes)).toLowerCase().trim();
        return data.startsWith("<svg") || (data.startsWith("<?xml") && data.contains("<svg"));
    }
    
    // Load a bufferedimage from a url or path
    public static BufferedImage loadImage(String sourceImagePath)
    {
        try
        {
            byte[] bytes = loadBytes(sourceImagePath);
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            return ImageIO.read(bais);
        } catch (Exception e) {
            return null;
        }
    }

    // Load a byte array from a url or path
    public static byte[] loadBytes(String sourceTextPath)
    {
        try
        {
            String lcSourceTextPath = sourceTextPath.toLowerCase();
            if (lcSourceTextPath.startsWith("http:/") || lcSourceTextPath.startsWith("https:/"))
            {
                URL u = new URL(sourceTextPath);
                try (InputStream in = u.openStream()) {
                    BufferedInputStream bis = new BufferedInputStream(in);
                    byte[] rBytes = bis.readAllBytes();
                    bis.close();
                    in.close();
                    return rBytes;
                }
            } else {
                File sourceImageFile = new File(sourceTextPath);
                FileInputStream fis = new FileInputStream(sourceImageFile);
                byte[] rBytes = fis.readAllBytes();
                fis.close();
                return rBytes;
            }
        } catch(Exception e) {
            return new byte[]{};
        }
    }

    public static String getProtocolAndAuthority(String urlString) throws MalformedURLException
    {
        URL url = new URL(urlString);
        String protocol = url.getProtocol();
        String host = url.getHost();
        int port = url.getPort();

        // if the port is not explicitly specified in the input, it will be -1.
        if (port == -1) {
            return String.format("%s://%s", protocol, host);
        } else {
            return String.format("%s://%s:%d", protocol, host, port);
        }
    }

    // Search an html document for any src="" and replace images with base64 encoded images.
    public static String transformSrcIntoBase64(String text)
    {
        Pattern p = Pattern.compile("(<img\\b[^>]*\\bsrc\\s*=\\s*)([\"\'])((?:(?!\\2)[^>])*)\\2(\\s*[^>]*>)",
            Pattern.CASE_INSENSITIVE & Pattern.MULTILINE);
        Matcher m = p.matcher(text);
        StringBuffer sb = new StringBuffer();
        while(m.find())
        {
            String prefix = m.group(1) + m.group(2);
            String found = m.group(3);
            String postfix = m.group(2) + m.group(4);
            String found_lower = found.toLowerCase();
            try 
            {
                if (!found.startsWith("/") && !found_lower.startsWith("http:/") && !found_lower.startsWith("https:/"))
                {
                    // This image path is relative
                    found = ImageArrayTool.sourceImagePath + found;
                } else if (found_lower.startsWith("//")) {
                    // This image path is a specific url but doesnt contain protocol
                    String lcPath = ImageArrayTool.sourceImagePath.toLowerCase();
                    if (lcPath.startsWith("https"))
                    {
                        found = "https:" + found;
                    } else if (lcPath.startsWith("http")) {
                        found = "http:" + found;
                    }
                } else if (found_lower.startsWith("/")) {
                    // this image path is relative to the root of the domain
                    String lcPath = ImageArrayTool.sourceImagePath.toLowerCase();
                    if (lcPath.startsWith("http:/") || lcPath.startsWith("https:/"))
                    {
                        found = getProtocolAndAuthority(ImageArrayTool.sourceImagePath) + found;
                    }
                } else if (found_lower.startsWith("http:/") || found_lower.startsWith("https:/")) {
                    // this url path is a full url do nothing to change it!
                } else {
                    found = (ImageArrayTool.sourceImagePath + "/" + found).replaceAll(Pattern.quote("//"),"/");
                }
                byte[] imageBytes = loadBytes(found);
                if (imageBytes.length > 0)
                {
                    if (isSVG(imageBytes))
                    {
                        debugMessage("[src] Replacing URL (image/svg+xml): " + found);
                        m.appendReplacement(sb, prefix + base64Data(imageBytes, "image/svg+xml") + postfix);
                    } else {
                        ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
                        BufferedImage bImage = ImageIO.read(bais);
                        if (bImage.getType() != BufferedImage.TYPE_CUSTOM) // Makse sure its really an image.
                        {
                            if (found_lower.endsWith(".gif"))
                            {
                                debugMessage("[src] Replacing URL (image/gif): " + found);
                                m.appendReplacement(sb, prefix + base64Data(imageBytes, "image/gif") + postfix);
                            } else {
                                debugMessage("[src] Replacing URL (image/png): " + found);
                                m.appendReplacement(sb, prefix + base64image(bImage, "PNG") + postfix);
                            }
                        } else {
                            debugMessage("[src] Skipping URL (Not an Image): " + found);
                        }
                    }
                } else {
                    debugMessage("[src] Skipping URL (No Data): " + found);
                    m.appendReplacement(sb, prefix + found + postfix);
                }

            } catch (Exception e) {
                debugMessage("[src] Skipping URL (Not an Image): " + found);
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static String transformURLsIntoBase64(String text)
    {
        String urlValidationRegex = "\\b((?:https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:, .;]*[-a-zA-Z0-9+&@#/%=~_|])";
        Pattern p = Pattern.compile(urlValidationRegex);
        Matcher m = p.matcher(text);
        StringBuffer sb = new StringBuffer();
        while(m.find())
        {
            String found = m.group(0);
            String found_lower = found.toLowerCase();
            try 
            {
                byte[] imageBytes = loadBytes(found);
                if (imageBytes.length > 0)
                {
                    ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
                    if (isSVG(imageBytes))
                    {
                        debugMessage("[url] Replacing URL (image/svg+xml): " + found);
                        m.appendReplacement(sb, base64Data(imageBytes, "image/svg+xml"));
                    } else {
                        BufferedImage bImage = ImageIO.read(bais);
                        if (bImage.getType() != BufferedImage.TYPE_CUSTOM) // Makse sure its really an image.
                        {
                            if (found_lower.endsWith(".gif"))
                            {
                                debugMessage("[url] Replacing URL (image/gif): " + found);
                                m.appendReplacement(sb, base64Data(imageBytes, "image/gif"));
                            } else {
                                debugMessage("[url] Replacing URL (image/png): " + found);
                                m.appendReplacement(sb, base64image(bImage, "PNG"));
                            }
                        } else {
                            debugMessage("[url] Skipping URL (Not an Image): " + found);
                        }
                    }
                } else {
                    debugMessage("[url] Skipping URL (No Data): " + found);
                    m.appendReplacement(sb, found);
                }
            } catch (Exception e) {
                debugMessage("[url] Skipping URL (Not an Image): " + found);
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static String filenameExtension(String filename)
    {
        String extension = "";
        int i = filename.lastIndexOf('.');
        if (i > 0)
        {
            extension = filename.substring(i+1);
        }
        return extension;
    }
    
    public static String base64image(BufferedImage image, String format) throws Exception
    {
        String formatLC = format.toLowerCase();
        String mime = null;
        if (formatLC.equals("png"))
            mime = "image/png";
        else if (formatLC.equals("gif"))
            mime = "image/gif";
        else if (formatLC.equals("jpeg"))
            mime = "image/jpeg";
        else if (formatLC.equals("webp"))
            mime = "image/webp";
        if (mime != null)
        {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, formatLC, baos);
            return base64Data(baos.toByteArray(), mime);
        } else {
            return null;
        }
    }
    

    public static String base64Data(byte[] bytes, String mime) throws Exception
    {
        if (mime != null)
        {
            String res = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
            return res.trim();
        } else {
            return null;
        }
    }


    public static String get2DRGBArray(String n, String fun, GeoColor[][] ary)
    {
        int rows = ary.length;
        int cols = ary[0].length;
        StringBuffer sb = new StringBuffer(fun + " " + n + "[" + String.valueOf(rows) + "][" + String.valueOf(cols) + "] = { ");
        for (int row = 0; row < ary.length; row++)
        {
            sb.append("{");
            for (int col = 0; col < ary[row].length; col++)
            {
                Color pix = ary[row][col];
                sb.append(fun+"(" + String.valueOf(pix.getRed()) + ", " + String.valueOf(pix.getGreen()) + ", " + String.valueOf(pix.getBlue()) + ")");
                if (col+1 < ary[row].length) sb.append(", ");
            }
            sb.append("}");
            if (row+1 < ary.length) sb.append(", ");
        }
        sb.append(" };\n");
        return sb.toString();
    }

    public static String getRGBArray(String n, String fun, GeoColor[][] ary)
    {
        int total_size = ary.length * ary[0].length;
        StringBuffer sb = new StringBuffer(fun + " " + n + "[" + String.valueOf(total_size) + "] = { ");
        for (int row = 0; row < ary.length; row++)
        {
            for (int col = 0; col < ary[row].length; col++)
            {
                GeoColor pix = ary[row][col];
                sb.append(fun+"(" + String.valueOf(pix.getRed()) + ", " + String.valueOf(pix.getGreen()) + ", " + String.valueOf(pix.getBlue()) + ")");
                if (col+1 < ary[row].length || row+1 < ary.length) sb.append(", ");
            }
        }
        sb.append(" };\n");
        return sb.toString();
    }

    public static void debugMessage(String text)
    {
        System.err.println(text);
    }

    public static InputStream readablePNG(String size, BufferedImage img) throws IOException
    {
        ByteArrayOutputStream bArrayOutputStream = new ByteArrayOutputStream();
        ImageIO.write(resizeImage(size, img), "png", bArrayOutputStream);
        return new ByteArrayInputStream(bArrayOutputStream.toByteArray());
    }

    public static void outputImage(File file, String type)
    {
        try
        {
            if (type.equals("ico"))
            {
                ArrayList<BufferedImage> icons = new ArrayList<BufferedImage>();
                icons.add(resizeImage("16x16",ImageArrayTool.sourceImage));
                icons.add(resizeImage("32x32",ImageArrayTool.sourceImage));
                icons.add(resizeImage("48x48",ImageArrayTool.sourceImage));
                icons.add(resizeImage("64x64",ImageArrayTool.sourceImage));
                icons.add(resizeImage("128x128",ImageArrayTool.sourceImage));
                int[] bpps = new int[icons.size()];
                for(int i = 0; i<icons.size(); i++) bpps[i] = 32;
                ICOEncoder.write(icons, bpps, file);
                debugMessage("Wrote (" + type + "): " + file.toString());
            } else if (type.equals("icns")) {
                try (IcnsBuilder builder = IcnsBuilder.getInstance())
                {
                    builder.add(IcnsType.ICNS_16x16_JPEG_PNG_IMAGE, readablePNG("16x16", ImageArrayTool.sourceImage));
                    builder.add(IcnsType.ICNS_32x32_JPEG_PNG_IMAGE, readablePNG("32x32", ImageArrayTool.sourceImage));
                    builder.add(IcnsType.ICNS_64x64_JPEG_PNG_IMAGE, readablePNG("64x64", ImageArrayTool.sourceImage));
                    builder.add(IcnsType.ICNS_128x128_JPEG_PNG_IMAGE, readablePNG("128x128", ImageArrayTool.sourceImage));
                    builder.add(IcnsType.ICNS_256x256_JPEG_PNG_IMAGE, readablePNG("256x256", ImageArrayTool.sourceImage));
                    builder.add(IcnsType.ICNS_512x512_JPEG_PNG_IMAGE, readablePNG("512x512", ImageArrayTool.sourceImage));
                    IcnsIcons builtIcons = builder.build();        
                    Path output = file.toPath();
                    OutputStream os = Files.newOutputStream(output);
                    builtIcons.writeTo(os);
                    debugMessage("Wrote (" + type + "): " + file.toString());
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                }
            } else if (type.equals("html")) {
                try
                {
                    String htmlData = "<img id=\"ita_" + ImageArrayTool.sourceImageBaseName + "\" src=\"" + base64image(ImageArrayTool.sourceImage, "PNG") + "\" />" + System.lineSeparator();
                    BufferedWriter bwr = new BufferedWriter(new FileWriter(file));
                    bwr.write(htmlData);
                    bwr.flush();
                    bwr.close();
                    debugMessage("Wrote (" + type + "): " + file.toString());
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                }
            } else if (type.equals("md")) {
                try
                {
                    String htmlData = "![ita_" + ImageArrayTool.sourceImageBaseName + "](" + base64image(ImageArrayTool.sourceImage, "PNG") + ")" + System.lineSeparator();
                    BufferedWriter bwr = new BufferedWriter(new FileWriter(file));
                    bwr.write(htmlData);
                    bwr.flush();
                    bwr.close();
                    debugMessage("Wrote (" + type + "): " + file.toString());
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                }
            } else {
                ImageIO.write(ImageArrayTool.sourceImage, type, file);
                debugMessage("Wrote (" + type + "): " + file.toString());
            }
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
    }

    public static void main(String[] args) throws IOException 
    {
        ImageArrayTool.ansiColors = new LinkedHashMap();
        
        // Regular Colors
        ImageArrayTool.ansiColors.put(new GeoColor(0, 0, 0), "\u001B[30m");
        ImageArrayTool.ansiColors.put(new GeoColor(0, 0, 170), "\u001B[34m");
        ImageArrayTool.ansiColors.put(new GeoColor(0, 170 ,0), "\u001B[32m");
        ImageArrayTool.ansiColors.put(new GeoColor(0, 170, 170), "\u001B[36m");
        ImageArrayTool.ansiColors.put(new GeoColor(170, 0, 0), "\u001B[31m");
        ImageArrayTool.ansiColors.put(new GeoColor(170, 0, 170), "\u001B[35m");
        ImageArrayTool.ansiColors.put(new GeoColor(170, 170, 0), "\u001B[33m");
        ImageArrayTool.ansiColors.put(new GeoColor(170,170,170), "\u001B[37m");
        
        // Bright Colors
        ImageArrayTool.ansiColors.put(new GeoColor(85,85,85), "\u001B[90m");
        ImageArrayTool.ansiColors.put(new GeoColor(255,85,85), "\u001B[91m");
        ImageArrayTool.ansiColors.put(new GeoColor(85,255,85), "\u001B[92m");
        ImageArrayTool.ansiColors.put(new GeoColor(255,255,85), "\u001B[93m");
        ImageArrayTool.ansiColors.put(new GeoColor(85,85,255), "\u001B[94m");
        ImageArrayTool.ansiColors.put(new GeoColor(255,85,255), "\u001B[95m");
        ImageArrayTool.ansiColors.put(new GeoColor(85,255,255), "\u001B[96m");
        ImageArrayTool.ansiColors.put(new GeoColor(255,255,255), "\u001B[97m");
        
        CommandLine cmd = null;
        Options options = new Options();
        try
        {
            CommandLineParser parser = new DefaultParser();
            boolean rowNumbers = false;
            StringBuffer output = new StringBuffer();

            options.addOption(new Option("d", "details", false, "Add image details to output"));
            options.addOption(new Option("?", "help", false, "Shows help"));

            Option inputOption = Option.builder("i").desc("Input files or URLs (png,jpg,md,html,bmp,gif,txt,webp)").hasArgs().longOpt("input").valueSeparator(' ').build();
            options.addOption(inputOption);
            
            options.addOption(new Option("p", "input-palette", true, "Input image file for color palette filter"));
            options.addOption(new Option("s", "scale", true, "Scale image (ex: 320x240 or 0.5)"));

            Option outputOption = Option.builder("o").desc("Output a file instead of STDOUT (txt,html,md,png,bmp,gif,jpg,webp,ico,icns)").hasArgs().longOpt("output").valueSeparator(' ').build();
            options.addOption(outputOption);
            options.addOption(new Option("c", "output-rgb-array", true, "Add a RGB C/C++ struct array to the output"));
            options.addOption(new Option("x", "output-rgb-2d-array", true, "Add a RGB two dimensional C/C++ struct array to the output"));
            options.addOption(new Option("e", "output-base64", true, "Add a base64 string to the output (argument is format JPEG,GIF,PNG,WEBP)"));
            Option asciiOption = new Option("a", "output-ascii", true, "Add a 24-bit ASCII art image to the output. Optional arg is \"auto_scale\" which will reduce the image to fit in an 80x24 terminal");
            asciiOption.setOptionalArg(true);
            options.addOption(asciiOption);
            options.addOption(new Option("r", "row-numbers", false, "Include row numbers on ASCII art"));
            options.addOption(new Option("b", "replace-urls", false, "Replace all image urls in a text file with base64 images"));
            options.addOption(new Option("t", "replace-tags", false, "Replace all image tags in an html file with base64 images"));

            cmd = parser.parse(options, args);

            if (cmd.hasOption("?") || cmd.getOptions().length == 0)
            {
                showHelp(options);
            }

            String[] sourceImageParameters = cmd.getOptionValues('i');
            debugMessage("Input Files: " + Arrays.asList(sourceImageParameters).toString());
            for(int i = 0; i < sourceImageParameters.length; i++)
            {
                String sourceImageParameter = sourceImageParameters[i];
                ImageArrayTool.sourceImageFileName = FilenameUtils.getName(sourceImageParameter);
                ImageArrayTool.sourceImagePath = FilenameUtils.getPath(sourceImageParameter);
                ImageArrayTool.sourceImageBaseName = FilenameUtils.getBaseName(sourceImageParameter);
                ImageArrayTool.sourceData = loadBytes(sourceImageParameter);
                try
                {
                    ByteArrayInputStream bais = new ByteArrayInputStream(ImageArrayTool.sourceData);
                    ImageArrayTool.sourceImage = ImageIO.read(bais);
                    if (ImageArrayTool.sourceImage != null)
                    {
                        if (ImageArrayTool.sourceImage.getType() == BufferedImage.TYPE_CUSTOM)
                        {
                            ImageArrayTool.sourceImage = null;
                        }
                    }
                } catch (Exception e) {
                    ImageArrayTool.sourceImage = null;
                }
                
                if (cmd.hasOption("t") || cmd.hasOption("b"))
                {
                    String textBody = new String(ImageArrayTool.sourceData);
                    if (cmd.hasOption("t"))
                        textBody = transformSrcIntoBase64(textBody);
                    if (cmd.hasOption("b"))
                        textBody = transformURLsIntoBase64(textBody);
                    output.append(textBody);
                }

                if (cmd.hasOption("r"))
                {
                    rowNumbers = true;
                }

                if (ImageArrayTool.sourceImage != null)
                {
                    if (cmd.hasOption("p") && ImageArrayTool.sourceImage != null)
                    {
                        BufferedImage paletteImage = ImageIO.read(new File(cmd.getOptionValue('p',"palette.png")));
                        Set<GeoColor> newPalette = reducePalette(getPalette(paletteImage), 256);
                        ImageArrayTool.sourceImage = applyPalette(ImageArrayTool.sourceImage, newPalette);
                    }
                    
                    if (cmd.hasOption("s") && ImageArrayTool.sourceImage != null)
                    {
                        ImageArrayTool.sourceImage = resizeImage(cmd.getOptionValue('s',"48x48"), ImageArrayTool.sourceImage);
                    }

                    // All Filters should be bfore this line
                    GeoColor[][] sourceImageArray = convertTo2DArray(ImageArrayTool.sourceImage);
                    
                    
                    if (cmd.hasOption("a"))
                    {
                        String asciiOptions = cmd.getOptionValue('a',"");
                        if (asciiOptions.contains("auto_scale"))
                        {
                            debugMessage("Auto-Scaling for ASCII ART");
                            float w = (float) ImageArrayTool.sourceImage.getWidth();
                            float h = (float) ImageArrayTool.sourceImage.getHeight();
                            BufferedImage scaledSourceImage = ImageArrayTool.sourceImage;
                            if (h > w)
                            {   // We're going to get cut off on height
                                if (h > 24)
                                {
                                    float new_height = 24;
                                    float new_width = w * (new_height / h);
                                    String new_dim = String.valueOf((int) new_width) + "x" + String.valueOf((int) new_height);
                                    debugMessage(" * Terminal Height Exceeded - New size = " + new_dim);
                                    scaledSourceImage = resizeImage(new_dim, ImageArrayTool.sourceImage);
                                }
                            } else {
                                // We're going to get cut off on width
                                if (w > 80)
                                {
                                    float new_width = 80;
                                    float new_height = h * (new_width / w);
                                    String new_dim = String.valueOf((int) new_width) + "x" + String.valueOf((int) new_height);
                                    debugMessage(" * Terminal Width Exceeded - New size = " + new_dim);
                                    scaledSourceImage = resizeImage(new_dim, ImageArrayTool.sourceImage);
                                }
                            }
                            GeoColor[][] sourceImageArrayScaled = convertTo2DArray(scaledSourceImage);
                            output.append(getAsciiArt(sourceImageArrayScaled, rowNumbers));
                        } else {
                            output.append(getAsciiArt(sourceImageArray, rowNumbers));
                        }
                    }
                    
                    if (cmd.hasOption("d"))
                    {
                        Set<GeoColor> palette = getPalette(ImageArrayTool.sourceImage);
                        Set<GeoColor> reducedPalette = reducePalette(palette, 256);
                        String cStrip = getColorStrip(reducedPalette);
                        int w = ImageArrayTool.sourceImage.getWidth();
                        int h = ImageArrayTool.sourceImage.getHeight();
                        output.append(System.lineSeparator());
                        output.append("// filename = " + ImageArrayTool.sourceImageFileName + System.lineSeparator());
                        output.append("// basename = " + ImageArrayTool.sourceImageBaseName + System.lineSeparator());
                        output.append("// path = " + ImageArrayTool.sourceImagePath + System.lineSeparator());
                        output.append("// colors = " + String.valueOf(palette.size()) + System.lineSeparator());
                        output.append("// image type = " + bufferedImageTypeName(ImageArrayTool.sourceImage.getType()) + System.lineSeparator());
                        output.append("// width = " + String.valueOf(w) + System.lineSeparator());
                        output.append("// height = " + String.valueOf(h) + System.lineSeparator());
                        output.append("// pixels = " + String.valueOf(w*h) + System.lineSeparator());
                        output.append(System.lineSeparator());
                        output.append("reduced palette [256]:" + System.lineSeparator() + cStrip + System.lineSeparator() + System.lineSeparator());
                    }
                    
                    if (cmd.hasOption("c"))
                    {
                        output.append(getRGBArray(ImageArrayTool.sourceImageBaseName, cmd.getOptionValue('c',"CRGB"), sourceImageArray) + System.lineSeparator());
                    }
                    
                    if (cmd.hasOption("x"))
                    {
                        output.append(get2DRGBArray(ImageArrayTool.sourceImageBaseName, cmd.getOptionValue('x',"CRGB"), sourceImageArray) + System.lineSeparator());
                    }

                    if (cmd.hasOption("e"))
                    {
                        output.append(base64image(ImageArrayTool.sourceImage, cmd.getOptionValue("e","PNG")));
                    }

                    if (cmd.hasOption("o") && output.length() == 0)
                    {
                        String[] outputFilenames = cmd.getOptionValues('o');
                        if (outputFilenames.length == 1)
                        {
                            String filename = outputFilenames[0];
                            if (outputFilenames[0].startsWith("."))
                            {
                                filename = ImageArrayTool.sourceImageBaseName + outputFilenames[0];
                            }
                            String ext = filenameExtension(filename).toLowerCase();
                            outputImage(new File(filename), ext);
                        } else {
                            for (int n = 0; n < outputFilenames.length; n++)
                            {
                                String filename = outputFilenames[n];
                                if (filename.startsWith("."))
                                {
                                    filename = ImageArrayTool.sourceImageBaseName + filename;
                                    String ext = filenameExtension(filename).toLowerCase();
                                    outputImage(new File(filename), ext);
                                } else if (n == i) {
                                    String ext = filenameExtension(filename).toLowerCase();
                                    outputImage(new File(filename), ext);
                                }
                            }
                        }
                    }
                }
            }

            if (output.length() > 0)
            {
                if (cmd.hasOption("o"))
                {
                    String filename = cmd.getOptionValue('o',"output.txt");
                    if (filename.startsWith("."))
                    {
                        filename = ImageArrayTool.sourceImageBaseName + filename;
                    }
                    BufferedWriter bwr = new BufferedWriter(new FileWriter(new File(filename)));
                    bwr.write(output.toString());
                    bwr.flush();
                    bwr.close();
                    debugMessage("Wrote: " + filename);
                } else {
                    System.out.println(output.toString());
                }
            }
        } catch (Exception e) {
            showHelp(options);
        }
        
        
    }
    
    public static void showHelp(Options options)
    {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp( "ita", "Image To Array: A tool for converting images to different forms of code" + System.lineSeparator() + "Project Page - https://openstatic.org/projects/imagetoarray/", options, "");
        System.exit(0);
    }
}
