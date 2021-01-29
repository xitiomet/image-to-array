package org.openstatic;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.AffineTransformOp;
import java.awt.geom.AffineTransform;
import java.awt.Graphics2D;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.ByteArrayOutputStream;
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
import java.util.regex.Pattern;

import org.apache.commons.cli.*;
import org.apache.commons.io.FilenameUtils;

public class ImageArrayTool
{
    public static String sourceImageName;
    public static String sourceImageFilename;
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
    
    public static String base64image(BufferedImage image) throws Exception
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        String res = "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        return res.trim();
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

            options.addOption(new Option("d", "details", false, "Output image details"));
            options.addOption(new Option("?", "help", false, "Shows help"));
            options.addOption(new Option("i", "input", true, "Input image file or URL"));
            options.addOption(new Option("p", "input-palette", true, "Input image file for color palette filter"));
            options.addOption(new Option("s", "scale", true, "Scale image (ex: 320x240 or 0.5)"));
            options.addOption(new Option("o", "output", true, "Output file (.txt or .png)"));
            options.addOption(new Option("c", "output-array", true, "Output a RGB C/C++ struct array"));
            options.addOption(new Option("2", "output-2d-array", true, "Output a RGB two dimensional C/C++ struct array"));
            options.addOption(new Option("6", "output-base64", false, "Output a base64 png string"));
            options.addOption(new Option("h", "output-html", false, "Output an html img tag with base64 encoded image"));
            options.addOption(new Option("a", "output-ascii", false, "Output a 24-bit ASCII art image"));
            options.addOption(new Option("r", "row-numbers", false, "Include row numbers on ASCII art"));

            cmd = parser.parse(options, args);

            if (cmd.hasOption("?") || cmd.getOptions().length == 0)
            {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp( "ita", options );
                System.exit(0);
            }

            String sourceImagePath = cmd.getOptionValue('i',"input.png");
            ImageArrayTool.sourceImageFilename = FilenameUtils.getName(sourceImagePath);

            if (sourceImagePath.startsWith("http://") || sourceImagePath.startsWith("https://"))
            {
                URL u = new URL(sourceImagePath);
                try (InputStream in = u.openStream()) {
                    ImageArrayTool.sourceImage = ImageIO.read(in);
                }
            } else {
                File sourceImageFile = new File(sourceImagePath);
                ImageArrayTool.sourceImage = ImageIO.read(sourceImageFile);
                ImageArrayTool.sourceImageFilename = sourceImageFile.getName();
            }
            
            ImageArrayTool.sourceImageName = FilenameUtils.getBaseName(sourceImageFilename);
            
            if (cmd.hasOption("p"))
            {
                BufferedImage paletteImage = ImageIO.read(new File(cmd.getOptionValue('p',"palette.png")));
                Set<GeoColor> newPalette = reducePalette(getPalette(paletteImage), 256);
                ImageArrayTool.sourceImage = applyPalette(ImageArrayTool.sourceImage, newPalette);
            }
            
            if (cmd.hasOption("s"))
            {
                ImageArrayTool.sourceImage = resizeImage(cmd.getOptionValue('s',"48x48"), ImageArrayTool.sourceImage, false);
            }
            
            // All Filters should be bfore this line
            GeoColor[][] sourceImageArray = convertTo2DArray(ImageArrayTool.sourceImage);
            
            if (cmd.hasOption("r"))
                rowNumbers = true;
            
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
                output.append("// filename = " + ImageArrayTool.sourceImageFilename + "\n");
                output.append("// colors = " + String.valueOf(palette.size()) + "\n");
                output.append("// width = " + String.valueOf(w) + "\n");
                output.append("// height = " + String.valueOf(h) + "\n");
                output.append("// pixels = " + String.valueOf(w*h) + "\n");
                output.append("\n");
                output.append("reduced palette [256]:\n" + cStrip + "\n\n");
            }
            
            if (cmd.hasOption("c"))
            {
                output.append(getRGBArray(ImageArrayTool.sourceImageName, cmd.getOptionValue('c',"CRGB"), sourceImageArray) + "\n");
            }
            
            if (cmd.hasOption("2"))
            {
                output.append(get2DRGBArray(ImageArrayTool.sourceImageName, cmd.getOptionValue('2',"CRGB"), sourceImageArray) + "\n");
            }
            
            if (cmd.hasOption("6"))
            {
                
                output.append(base64image(ImageArrayTool.sourceImage));
            }
            
            if (cmd.hasOption("h"))
            {
                output.append("<img id=\"img" + ImageArrayTool.sourceImageName + "\" src=\"" + base64image(ImageArrayTool.sourceImage) + "\" />");
            }
            
            if (cmd.hasOption("o"))
            {
                String filename = cmd.getOptionValue('o',"output.png");
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
