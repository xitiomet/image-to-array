package org.openstatic;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.AffineTransformOp;
import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.io.File;
import javax.imageio.ImageIO;

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
    
    private static Color[][] convertTo2D(BufferedImage image)
    {
      int width = image.getWidth();
      int height = image.getHeight();
      Color[][] result = new Color[height][width];

      for (int row = 0; row < height; row++)
      {
         for (int col = 0; col < width; col++)
         {
            result[row][col] = new Color(image.getRGB(col, row));
         }
      }

      return result;
    }
   
    public static double colorDistance(Color a, Color b)
    {
        return Math.sqrt(Math.pow(a.getRed() - b.getRed(), 2) + Math.pow(a.getGreen() - b.getGreen(), 2) + Math.pow(a.getBlue() - b.getBlue(), 2));
    }
    
    public static Color nearestColor(Set<Color> colorSet, Color color)
    {
        double distance = Double.MAX_VALUE;
        Color rc = null;
        for(Iterator<Color> i = colorSet.iterator(); i.hasNext();)
        {
            Color c = i.next();
            double td = colorDistance(c, color);
            if (td < distance)
            {
                distance = td;
                rc = c;
            }
        }
        return rc;
    }
    
    public static String getAnsiCodeFromRGB(Color col)
    {        
        LinkedHashMap<Color, String> ansi = new LinkedHashMap();
        ansi.put(new Color(0, 0, 0), "\u001B[30m");
        ansi.put(new Color(0, 0, 170), "\u001B[34m");
        ansi.put(new Color(0, 170 ,0), "\u001B[32m");
        ansi.put(new Color(0, 170, 170), "\u001B[36m");
        ansi.put(new Color(170, 0, 0), "\u001B[31m");
        ansi.put(new Color(170, 0, 170), "\u001B[35m");
        ansi.put(new Color(170, 170, 0), "\u001B[33m");
        ansi.put(new Color(170,170,170), "\u001B[37m");
        
        ansi.put(new Color(85,85,85), "\u001B[90m");
        ansi.put(new Color(255,85,85), "\u001B[91m");
        ansi.put(new Color(85,255,85), "\u001B[92m");
        ansi.put(new Color(255,255,85), "\u001B[93m");
        ansi.put(new Color(85,85,255), "\u001B[94m");
        ansi.put(new Color(255,85,255), "\u001B[95m");
        ansi.put(new Color(85,255,255), "\u001B[96m");
        ansi.put(new Color(255,255,255), "\u001B[97m");
        
        
        Set<Color> colors = ansi.keySet();
        
        Color colour = nearestColor(colors, col);
        
        String name = ansi.get(colour);
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
    
    public static void print2DAsciiArt(Color[][] ary)
    {
        for (int row = 0; row < ary.length; row++)
        {
            System.out.print( String.format("%03d" ,row) + ": ");
            for (int col = 0; col < ary[row].length; col++)
            {
                System.out.print(getAnsiCodeFromRGB(ary[row][col]) + "\u2588\u2588");
            }
            System.out.println("\u001B[0m");
        }
    }
    
    public static int getHueFromColor(Color c)
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
    
    public static void print2DRGBArray(String n, String fun, Color[][] ary)
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
        System.out.println(sb.toString());
    }

    public static void printRGBArray(String n, String fun, Color[][] ary)
    {
        int total_size = ary.length * ary[0].length;
        StringBuffer sb = new StringBuffer(fun + " " + n + "[" + String.valueOf(total_size) + "] = { ");
        for (int row = 0; row < ary.length; row++)
        {
            for (int col = 0; col < ary[row].length; col++)
            {
                Color pix = ary[row][col];
                sb.append(fun+"(" + String.valueOf(pix.getRed()) + ", " + String.valueOf(pix.getGreen()) + ", " + String.valueOf(pix.getBlue()) + ")");
                if (col+1 < ary[row].length || row+1 < ary.length) sb.append(", ");
            }
        }
        sb.append(" };");
        System.out.println(sb.toString());
    }

    public static void main(String[] args) throws IOException 
    {
        CommandLine cmd = null;
        try
        {
            Options options = new Options();
            CommandLineParser parser = new DefaultParser();

            options.addOption(new Option("d", "debug", false, "Turn on debug."));
            options.addOption(new Option("?", "help", false, "Shows help"));
            options.addOption(new Option("i", "input", true, "Input image file"));
            options.addOption(new Option("c", "output-array", true, "Output a RGB C++ style struct array"));
            options.addOption(new Option("2", "output-2d-array", true, "Output a RGB two dimensional C++ style struct array"));
            options.addOption(new Option("a", "output-ascii", false, "Output an ascii art image"));
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
            Color[][] sourceImageArray = convertTo2D(ImageArrayTool.sourceImage);

            
            if (cmd.hasOption("a"))
            {
                print2DAsciiArt(sourceImageArray);
            }
            
            if (cmd.hasOption("c"))
            {
                printRGBArray(ImageArrayTool.sourceImageName, cmd.getOptionValue('c',"CRGB"), sourceImageArray);
            }
            
            if (cmd.hasOption("2"))
            {
                print2DRGBArray(ImageArrayTool.sourceImageName, cmd.getOptionValue('2',"CRGB"), sourceImageArray);
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        
        
    }
    
}
