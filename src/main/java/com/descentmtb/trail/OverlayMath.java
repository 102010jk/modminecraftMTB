package com.descentmtb.trail;

/** Small surface features measured above the underlying plane, never above an unrelated block grid. */
public final class OverlayMath {
    public static double amplitude(int kind){return kind==1?.10:kind==2?.17:0;}
    public static double bump(int kind,double x,double z){
        if(x<0||x>1||z<0||z>1)return 0;
        if(kind==1){
            double fade=PumpMath.edge(Math.abs(x-.5),.48,.35);
            double best=0;
            for(double centre:new double[]{.22,.51,.79}) {
                double distance=Math.abs(z-centre-.08*Math.sin(x*5+centre));
                double t=distance/.075;
                if(t<1)best=Math.max(best,.10*(1-t*t)*fade);
            }
            return best;
        }
        if(kind==2){
            double best=0;
            double[][] rocks={{.26,.32,.19,.14},{.67,.66,.22,.17},{.75,.23,.13,.10}};
            for(var r:rocks){double t=Math.hypot((x-r[0])/r[2],(z-r[1])/r[2]);if(t<1)best=Math.max(best,r[3]*(1-t));}
            return best;
        }
        return 0;
    }
    private OverlayMath(){}
}
