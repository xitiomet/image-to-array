package org.openstatic;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.AffineTransformOp;
import java.awt.geom.AffineTransform;
import java.awt.Graphics2D;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;
import java.awt.Color;

import java.util.Base64;
import java.util.Vector;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Enumeration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.Comparator;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.cli.*;
import org.apache.commons.io.FilenameUtils;

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
    
    
    public static BufferedImage resizeImage(String amount, BufferedImage in_image, boolean alpha)
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
        if (!alpha)
        {
            AffineTransform at = new AffineTransform();
            at.scale(w/o_w, h/o_h);
            AffineTransformOp scaleOp = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
            BufferedImage rgbi = new BufferedImage(in_image.getWidth(), in_image.getHeight(), BufferedImage.TYPE_INT_RGB);
            rgbi.createGraphics().drawImage(in_image, 0, 0, Color.WHITE, null);
            BufferedImage ri = new BufferedImage((int)w, (int)h, BufferedImage.TYPE_INT_RGB);
            scaleOp.filter(rgbi, ri);
            return ri;
        } else {
            AffineTransform at = new AffineTransform();
            at.scale(w/o_w, h/o_h);
            AffineTransformOp scaleOp = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
            BufferedImage rgbi = new BufferedImage(in_image.getWidth(), in_image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            rgbi.createGraphics().drawImage(in_image, 0, 0, new Color(1f,1f,1f,0f), null);
            BufferedImage ri = new BufferedImage((int)w, (int)h, BufferedImage.TYPE_INT_ARGB);
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

    // Load a String from a url or path
    public static String loadText(String sourceTextPath)
    {
        byte[] data = loadBytes(sourceTextPath);
        if (data.length > 0)
        {
            return new String(data);
        } else {
            return null;
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
                //System.err.println("");
                //System.err.println("[src] FOUND: " + found);
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
                //System.err.println("");
                //System.err.println("[src] FOUND: " + found);
                byte[] imageBytes = loadBytes(found);
                if (imageBytes.length > 0)
                {
                    if (isSVG(imageBytes))
                    {
                        System.err.println("[src] Replacing URL (image/svg+xml): " + found);
                        m.appendReplacement(sb, prefix + base64Data(imageBytes, "image/svg+xml") + postfix);
                    } else {
                        ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
                        BufferedImage bImage = ImageIO.read(bais);
                        if (bImage.getType() != BufferedImage.TYPE_CUSTOM) // Makse sure its really an image.
                        {
                            if (found_lower.endsWith(".gif"))
                            {
                                System.err.println("[src] Replacing URL (image/gif): " + found);
                                m.appendReplacement(sb, prefix + base64Data(imageBytes, "image/gif") + postfix);
                            } else {
                                System.err.println("[src] Replacing URL (image/png): " + found);
                                m.appendReplacement(sb, prefix + base64image(bImage, "PNG") + postfix);
                            }
                        } else {
                            System.err.println("[src] Skipping URL (Not an Image): " + found);
                        }
                    }
                } else {
                    System.err.println("[src] Skipping URL (No Data): " + found);
                    m.appendReplacement(sb, prefix + found + postfix);
                }

            } catch (Exception e) {
                System.err.println("[src] Skipping URL (Not an Image): " + found);
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
                        System.err.println("[url] Replacing URL (image/svg+xml): " + found);
                        m.appendReplacement(sb, base64Data(imageBytes, "image/svg+xml"));
                    } else {
                        BufferedImage bImage = ImageIO.read(bais);
                        if (bImage.getType() != BufferedImage.TYPE_CUSTOM) // Makse sure its really an image.
                        {
                            if (found_lower.endsWith(".gif"))
                            {
                                System.err.println("[url] Replacing URL (image/gif): " + found);
                                m.appendReplacement(sb, base64Data(imageBytes, "image/gif"));
                            } else {
                                System.err.println("[url] Replacing URL (image/png): " + found);
                                m.appendReplacement(sb, base64image(bImage, "PNG"));
                            }
                        } else {
                            System.err.println("[url] Skipping URL (Not an Image): " + found);
                        }
                    }
                } else {
                    System.err.println("[url] Skipping URL (No Data): " + found);
                    m.appendReplacement(sb, found);
                }
            } catch (Exception e) {
                System.err.println("[url] Skipping URL (Not an Image): " + found);
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
        String formatUC = format.toUpperCase();
        String mime = null;
        if (formatUC.equals("PNG"))
            mime = "image/png";
        else if (formatUC.equals("GIF"))
            mime = "image/gif";
        else if (formatUC.equals("JPEG"))
            mime = "image/jpeg";
        if (mime != null)
        {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, formatUC, baos);
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
        try
        {
            Options options = new Options();
            CommandLineParser parser = new DefaultParser();
            boolean rowNumbers = false;
            StringBuffer output = new StringBuffer();

            options.addOption(new Option("d", "details", false, "Add image details to output"));
            options.addOption(new Option("?", "help", false, "Shows help"));
            options.addOption(new Option("i", "input", true, "Input file or URL (png,jpg,md,html,bmp,gif,txt)"));
            options.addOption(new Option("p", "input-palette", true, "Input image file for color palette filter"));
            options.addOption(new Option("s", "scale", true, "Scale image (ex: 320x240 or 0.5)"));
            options.addOption(new Option("o", "output", true, "Output file (txt,html,md,png,bmp,gif,jpg)"));
            options.addOption(new Option("c", "output-array", true, "Add a RGB C/C++ struct array to the output"));
            options.addOption(new Option("2", "output-2d-array", true, "Add a RGB two dimensional C/C++ struct array to the output"));
            options.addOption(new Option("6", "output-base64", true, "Add a base64 string to the output (argument is format JPEG,GIF,PNG)"));
            options.addOption(new Option("h", "output-html", false, "Add an html img tag with base64 encoded image to the output"));
            options.addOption(new Option("a", "output-ascii", false, "Add a 24-bit ASCII art image to the output"));
            options.addOption(new Option("r", "row-numbers", false, "Include row numbers on ASCII art"));
            options.addOption(new Option("b", "replace-urls", false, "Replace all image urls in a text file with base64 images"));
            options.addOption(new Option("t", "replace-tags", false, "Replace all image tage in an html file with base64 images"));

            cmd = parser.parse(options, args);

            if (cmd.hasOption("?") || cmd.getOptions().length == 0)
            {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp( "ita", "Image To Array: A tool for converting images to different forms of code" + System.lineSeparator() + "Project Page - https://openstatic.org/projects/imagetoarray/", options, "");
                System.exit(0);
            }

            String sourceImageParameter = cmd.getOptionValue('i',"input.png");
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
                String textBody = loadText(sourceImageParameter);
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
                    ImageArrayTool.sourceImage = resizeImage(cmd.getOptionValue('s',"48x48"), ImageArrayTool.sourceImage, false);
                }

                // All Filters should be bfore this line
                GeoColor[][] sourceImageArray = convertTo2DArray(ImageArrayTool.sourceImage);
                
                
                if (cmd.hasOption("a"))
                {
                    output.append(getAsciiArt(sourceImageArray, rowNumbers));
                }
                
                if (cmd.hasOption("d"))
                {
                    Set<GeoColor> palette = getPalette(ImageArrayTool.sourceImage);
                    Set<GeoColor> reducedPalette = reducePalette(palette, 256);
                    String cStrip = getColorStrip(reducedPalette);
                    int w = ImageArrayTool.sourceImage.getWidth();
                    int h = ImageArrayTool.sourceImage.getHeight();
                    output.append("\n");
                    output.append("// filename = " + ImageArrayTool.sourceImageFileName + "\n");
                    output.append("// basename = " + ImageArrayTool.sourceImageBaseName + "\n");
                    output.append("// path = " + ImageArrayTool.sourceImagePath + "\n");
                    output.append("// colors = " + String.valueOf(palette.size()) + "\n");
                    output.append("// width = " + String.valueOf(w) + "\n");
                    output.append("// height = " + String.valueOf(h) + "\n");
                    output.append("// pixels = " + String.valueOf(w*h) + "\n");
                    output.append("\n");
                    output.append("reduced palette [256]:\n" + cStrip + "\n\n");
                }
                
                if (cmd.hasOption("c"))
                {
                    output.append(getRGBArray(ImageArrayTool.sourceImageBaseName, cmd.getOptionValue('c',"CRGB"), sourceImageArray) + "\n");
                }
                
                if (cmd.hasOption("2"))
                {
                    output.append(get2DRGBArray(ImageArrayTool.sourceImageBaseName, cmd.getOptionValue('2',"CRGB"), sourceImageArray) + "\n");
                }
                
                if (cmd.hasOption("6"))
                {
                    
                    output.append(base64image(ImageArrayTool.sourceImage, cmd.getOptionValue("6","PNG")));
                }
                
                if (cmd.hasOption("h"))
                {
                    output.append("<img id=\"img" + ImageArrayTool.sourceImageBaseName + "\" src=\"" + base64image(ImageArrayTool.sourceImage, "PNG") + "\" />");
                }
            }

            if (cmd.hasOption("o"))
            {
                String filename = cmd.getOptionValue('o', ImageArrayTool.sourceImageBaseName + "_ita.png");
                if (output.length() == 0)
                {
                    String ext = filenameExtension(filename);
                    ImageIO.write(ImageArrayTool.sourceImage, ext, new File(filename));
                } else {
                    BufferedWriter bwr = new BufferedWriter(new FileWriter(new File(cmd.getOptionValue('o',"output.txt"))));
                    bwr.write(output.toString());
                    bwr.flush();
                    bwr.close();
                }
            } else {
                System.out.println(output.toString());
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        
        
    }
    
}
