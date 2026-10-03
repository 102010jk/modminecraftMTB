package com.descentmtb.trail;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WorkshopTest {
 @Test void rollerJoinsHaveZeroSlopeAndCurvature(){double e=.0001;for(int i=-4;i<=4;i++){double x=i*5;assertEquals(0,PumpMath.wave(x,5,.8),1e-10);assertEquals(0,(PumpMath.wave(x+e,5,.8)-PumpMath.wave(x-e,5,.8))/(2*e),1e-8);assertEquals(0,(PumpMath.wave(x+e,5,.8)+PumpMath.wave(x-e,5,.8))/(e*e),1e-6);}}
 @Test void featherHasSmoothBoundaries(){assertEquals(1,PumpMath.edge(0,3,.75));assertEquals(0,PumpMath.edge(3,3,.75));assertTrue(PumpMath.edge(2.99,3,.75)<.00001);}
 @Test void floodFillUndoAndPaletteBounds(){var art=new SignArt(new byte[256]);art.fill(0,0,1);for(byte b:art.pixels())assertEquals(1,b);art.undo();for(byte b:art.pixels())assertEquals(0,b);art.fill(0,0,16);art.fill(-1,0,3);for(byte b:art.pixels())assertEquals(0,b);}
 @Test void undoIsOneSnapshotPerStrokeAndCopyIndependent(){var art=new SignArt(new byte[256]);art.beginStroke();art.paint(1,1,3);art.paint(2,1,3);byte[] copied=art.pixels();art.undo();assertEquals(0,art.pixels()[17]);var other=new SignArt(copied);other.paint(1,1,4);assertEquals(3,copied[17]);}
 @Test void templatesAreDistinctAndCanBeReverted(){var art=new SignArt(new byte[256]);art.template(0);byte[] arrow=art.pixels();art.template(4);assertFalse(java.util.Arrays.equals(arrow,art.pixels()));art.undo();assertArrayEquals(arrow,art.pixels());}
}
