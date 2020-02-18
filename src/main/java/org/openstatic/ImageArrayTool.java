package org.openstatic;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.AffineTransformOp;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;

import javax.imageio.ImageIO;
import java.awt.Color;

import java.util.Vector;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Enumeration;
import java.util.Arrays;

import java.io.IOException;
import java.util.regex.Pattern;

import org.apache.commons.cli.*;

public class ImageArrayTool
{
    public static String sourceImageName;
    public static File sourceImageFile;
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
    
    public static String getAsciiArt(GeoColor[][] ary, boolean lineNumbers)
    {
        StringBuffer sb = new StringBuffer();
        for (int row = 0; row < ary.length; row++)
        {
            if (lineNumbers)
                sb.append( String.format("%03d" ,row) + ": ");
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
        sb.append(" };");
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
        sb.append(" };");
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

            options.addOption(new Option("d", "debug", false, "Turn on debug."));
            options.addOption(new Option("?", "help", false, "Shows help"));
            options.addOption(new Option("i", "input", true, "Input image file"));
            options.addOption(new Option("o", "output", true, "Output file"));
            options.addOption(new Option("c", "output-array", true, "Output a RGB C++ style struct array"));
            options.addOption(new Option("2", "output-2d-array", true, "Output a RGB two dimensional C++ style struct array"));
            options.addOption(new Option("a", "output-ascii", false, "Output an ascii art image"));
            options.addOption(new Option("r", "row-numbers", false, "Include row numbers on ascii art"));
            
            options.addOption(new Option("s", "scale", true, "Scale image (ex: 320x240 or 0.5)"));

            cmd = parser.parse(options, args);

            if (cmd.hasOption("?") || cmd.getOptions().length == 0)
            {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp( "ita", options );
                System.exit(0);
            }
            
            ImageArrayTool.sourceImageFile = new File(cmd.getOptionValue('i',"input.png"));
            ImageArrayTool.sourceImage = ImageIO.read(ImageArrayTool.sourceImageFile);
            
            String fn = ImageArrayTool.sourceImageFile.getName();
            ImageArrayTool.sourceImageName = fn.substring(0, fn.lastIndexOf('.'));
            
            
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
            
            if (cmd.hasOption("c"))
            {
                output.append(getRGBArray(ImageArrayTool.sourceImageName, cmd.getOptionValue('c',"CRGB"), sourceImageArray));
            }
            
            if (cmd.hasOption("2"))
            {
                output.append(get2DRGBArray(ImageArrayTool.sourceImageName, cmd.getOptionValue('2',"CRGB"), sourceImageArray));
            }
            
            if (cmd.hasOption("o"))
            {
                BufferedWriter bwr = new BufferedWriter(new FileWriter(new File(cmd.getOptionValue('o',"output.txt"))));
                bwr.write(output.toString());
                bwr.flush();
                bwr.close();
            } else {
                System.out.println(output.toString());
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        
        
    }
    
}
