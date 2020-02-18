package org.openstatic;

import java.awt.Color;
import java.util.Set;
import java.util.Iterator;

public class GeoColor extends Color
{
    public GeoColor(int r, int g, int b)
    {
        super(r, g, b);
    }
    
    public GeoColor(int rgb)
    {
        super(rgb);
    }
    
    /** return the distance between two colors on the color spectrum **/
    public double distanceFrom(Color b)
    {
        return Math.sqrt(Math.pow(this.getRed() - b.getRed(), 2) + Math.pow(this.getGreen() - b.getGreen(), 2) + Math.pow(this.getBlue() - b.getBlue(), 2));
    }
    
    /** Find the nearest color in the provided Set<Color> **/
    public GeoColor nearestColor(Set<GeoColor> colorSet)
    {
        double distance = Double.MAX_VALUE;
        GeoColor rc = null;
        for(Iterator<GeoColor> i = colorSet.iterator(); i.hasNext();)
        {
            GeoColor c = i.next();
            double td = this.distanceFrom(c);
            if (td < distance)
            {
                distance = td;
                rc = c;
            }
        }
        return rc;
    }
    
}
