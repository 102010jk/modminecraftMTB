package com.descentmtb.trail;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.descentmtb.trail.TrailMath.*;
class TrailMathTest {
    @Test void fourCornersMakeAContinuousBankWithSharedEdges() {
        double[] a={0,.5,.2,.7},b={.5,1,.7,1.2};
        for(int i=0;i<=100;i++){double t=i/100.0;assertEquals(bilerp(a,1,t),bilerp(b,0,t),1e-9);}
    }
    @Test void guideCurveHasCorrectEndpointsAndTurnDirection() {
        Point a=new Point(0,64,0),b=new Point(0,63,10),c=new Point(10,62,10);
        assertEquals(a,curve(a,b,c,0));assertEquals(c,curve(a,b,c,1));assertEquals(-1,turn(a,b,c));
        Point mid=curve(a,b,c,.5);assertEquals(.5,nearest(a,b,c,mid.x(),mid.z()),.01);
    }
    @Test void bankIsOnTheOutsideAndTapersAtEntryAndExit() {
        assertEquals(0,bankHeight(2,2,0,-1,2,false),1e-9);
        assertEquals(0,bankHeight(2,2,1,-1,2,false),1e-9);
        assertTrue(bankHeight(2,2,.5,-1,2,false)>1.9);assertEquals(0,bankHeight(-2,2,.5,-1,2,false));
        assertTrue(bankHeight(2,2,.78,-1,2,true)>bankHeight(2,2,.78,-1,2,false));
    }
}
