package com.descentmtb.trail;
/** C2 profile: zero height, slope and curvature at every roller join. */
public final class PumpMath {
 public static double wave(double distance,double spacing,double height){double s=Math.sin(Math.PI*distance/spacing);return height*s*s*s*s;}
 public static double smooth(double v){v=Math.max(0,Math.min(1,v));return v*v*v*(v*(v*6-15)+10);}
 public static double edge(double distance,double radius,double softness){if(distance>=radius)return 0;double core=radius*(1-softness);return distance<=core?1:1-smooth((distance-core)/Math.max(.001,radius-core));}
 private PumpMath(){}
}
