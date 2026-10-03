package com.descentmtb.trail;

/** Pure construction geometry: shared samples keep adjacent berm and boardwalk tiles seamless. */
public final class TrailMath {
    public record Point(double x, double y, double z) {}
    public static double bilerp(double[] h, double x, double z) {
        x = Math.max(0, Math.min(1, x)); z = Math.max(0, Math.min(1, z));
        return (h[0] * (1 - x) + h[1] * x) * (1 - z) + (h[2] * (1 - x) + h[3] * x) * z;
    }
    public static Point curve(Point a, Point b, Point c, double t) {
        double u = 1 - t;
        return new Point(u*u*a.x+2*u*t*b.x+t*t*c.x, u*u*a.y+2*u*t*b.y+t*t*c.y, u*u*a.z+2*u*t*b.z+t*t*c.z);
    }
    public static double nearest(Point a, Point b, Point c, double x, double z) {
        double best = Double.MAX_VALUE, t = 0;
        for (int i = 0; i <= 128; i++) {
            double q = i / 128.0; Point p = curve(a,b,c,q);
            double d = (p.x-x)*(p.x-x)+(p.z-z)*(p.z-z);
            if (d < best) { best=d; t=q; }
        }
        return t;
    }
    public static double side(Point a, Point b, Point c, double t, double x, double z) {
        Point p = curve(a,b,c,t);
        double dx = 2*((1-t)*(b.x-a.x)+t*(c.x-b.x)), dz = 2*((1-t)*(b.z-a.z)+t*(c.z-b.z));
        return ((x-p.x)*-dz+(z-p.z)*dx)/Math.max(.01, Math.hypot(dx,dz));
    }
    public static double turn(Point a, Point b, Point c) { return Math.signum((b.x-a.x)*(c.z-b.z)-(b.z-a.z)*(c.x-b.x)); }
    public static double bankHeight(double side, double halfWidth, double t, double turn, double height, boolean shark) {
        double outside = Math.max(0, Math.min(1, -side * turn / halfWidth));
        double taper = Math.sin(Math.PI * t);
        double lift = shark ? Math.exp(-Math.pow((t-.78)/.15,2)) * .8 : 0;
        return outside * outside * height * taper * taper + lift * outside;
    }
    private TrailMath() {}
}
