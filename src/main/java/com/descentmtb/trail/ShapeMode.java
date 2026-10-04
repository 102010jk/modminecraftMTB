package com.descentmtb.trail;

import java.util.*;
public enum ShapeMode {
    AUTO, NW, NE, SW, SE, NORTH, EAST, SOUTH, WEST, WHOLE, CURVE;
    public String key(){return "descentmtb.shape."+name().toLowerCase(Locale.ROOT);}
    public List<ColumnShaper.Vertex> vertices(int x,int z,double fx,double fz) {
        int[] corners=switch(this) {
            case NW->new int[]{0};case NE->new int[]{1};case SW->new int[]{2};case SE->new int[]{3};
            case NORTH->new int[]{0,1};case EAST->new int[]{1,3};case SOUTH->new int[]{2,3};case WEST->new int[]{0,2};
            default->new int[]{0,1,2,3};
        };
        if(this==AUTO)return ColumnShaper.pickVertices(x,z,fx,fz);
        return Arrays.stream(corners).mapToObj(i->new ColumnShaper.Vertex(x+i%2,z+i/2)).toList();
    }
}
