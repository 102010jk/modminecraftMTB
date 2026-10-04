package com.descentmtb.trail;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OverlayMathTest {
    @Test void rootsAndRocksAreLowFeaturesAndMeetTheSurfaceAtTheTileEdges(){
        for(int kind:new int[]{1,2})for(int i=0;i<=100;i++){
            double f=i/100.0;
            assertEquals(0,OverlayMath.bump(kind,0,f),1e-9);assertEquals(0,OverlayMath.bump(kind,1,f),1e-9);
            assertEquals(0,OverlayMath.bump(kind,f,0),1e-9);assertEquals(0,OverlayMath.bump(kind,f,1),1e-9);
            for(int j=0;j<=100;j++)assertTrue(OverlayMath.bump(kind,f,j/100.0)<=OverlayMath.amplitude(kind));
        }
        assertTrue(OverlayMath.bump(1,.5,.51+.08*Math.sin(3.01))>.09);
        assertTrue(OverlayMath.bump(2,.67,.66)>.16);
    }
    @Test void raisedOverlayRetainsTheUnderlyingBankSlope(){
        double[] bank={100.2,100.7,100.2,100.7};double z=.51+.08*Math.sin(3.01);
        double world=ColumnShaper.surfaceAt(bank,.5,z)+OverlayMath.bump(1,.5,z);
        assertEquals(100.55,world,1e-9);
        assertEquals(0,OverlayMath.bump(0,.5,.5));
    }
}
