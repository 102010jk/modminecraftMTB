package com.descentmtb.trail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.DoubleBinaryOperator;

/**
 * Pure (Minecraft-free) route finding of the downhill line: a smoothed path from the start to the finish that
 * follows the terrain. A* runs on the 1 m block grid inside a corridor around the straight line; stepping uphill
 * and slopes steeper than about 33 degrees are expensive, a steep cross slope is mildly expensive, so the path
 * prefers the fall line but still heads for the finish. The grid path is then relaxed into a smooth curve and
 * resampled every half metre.
 *
 * <p>With a grade limit ({@link #find(DoubleBinaryOperator, double, double, double, double, double, int)}) the cost of a
 * step also rises steeply with the grade above the limit, measured on a smoothed copy of the ground (single block
 * steps would otherwise count as 45 degrees), so the cheapest way down a steep slope traverses it.
 */
public final class DownhillRoute {
    /** Distance between the points of the result (m). */
    public static final double STEP = .5;
    /** Slope (rise over run) above which going down costs extra: about 33 degrees. */
    private static final double STEEP = .65;
    /** Steps with a steeper slope than this are not passable at all. */
    private static final double CLIFF = 3.5;
    /** Extra cost per metre for every unit of grade (rise over run) above the limit. */
    private static final double OVER_LIMIT = 50;
    /** Radius (cells) of the averaging that gives the grade a step is measured on. */
    private static final int SOFT = 2;
    /** The most cells the search may expand before the terrain is declared too complex (keeps a server tick short). */
    public static final int MAX_EXPANDED = 60_000;
    private static final int[][] MOVES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
            {2, 1}, {2, -1}, {-2, 1}, {-2, -1}, {1, 2}, {1, -2}, {-1, 2}, {-1, -2}};

    /**
     * @param ground terrain height at (x, z) (absolute Y); NaN where nothing can be ridden
     * @return the centre line as {@code {x, z}} points {@link #STEP} apart, from the start to the finish
     * @throws DownhillShapes.Rejected when the search needs more than {@link #MAX_EXPANDED} cells
     */
    public static double[][] find(DoubleBinaryOperator ground, double startX, double startZ, double finishX, double finishZ) {
        return find(ground, startX, startZ, finishX, finishZ, 0, 0);
    }

    /**
     * Like {@link #find(DoubleBinaryOperator, double, double, double, double)}, with the trail's grade limited.
     *
     * @param slopeLimit the steepest grade (rise over run) the route should have, 0 for none (the terrain is followed)
     * @param corridor   how far (m) the route may stray from the straight line, 0 to choose it from the length
     */
    public static double[][] find(DoubleBinaryOperator ground, double startX, double startZ, double finishX, double finishZ,
                                  double slopeLimit, int corridor) {
        double length = Math.hypot(finishX - startX, finishZ - startZ);
        if (corridor <= 0) {
            corridor = (int) Math.max(slopeLimit > 0 ? 20 : 12, Math.min(60, length * .5));
        }
        int minX = (int) Math.floor(Math.min(startX, finishX)) - corridor, maxX = (int) Math.floor(Math.max(startX, finishX)) + corridor;
        int minZ = (int) Math.floor(Math.min(startZ, finishZ)) - corridor, maxZ = (int) Math.floor(Math.max(startZ, finishZ)) + corridor;
        Grid grid = new Grid(ground, minX, minZ, maxX - minX + 1, maxZ - minZ + 1, slopeLimit);

        int start = grid.index((int) Math.floor(startX), (int) Math.floor(startZ));
        int finish = grid.index((int) Math.floor(finishX), (int) Math.floor(finishZ));
        List<double[]> cells = new ArrayList<>();
        int[] path = grid.search(start, finish, startX, startZ, finishX, finishZ, corridor);
        if (path == null) {
            cells.add(new double[]{startX, startZ});
            cells.add(new double[]{finishX, finishZ});
        } else {
            for (int node : path) {
                cells.add(new double[]{grid.x(node) + .5, grid.z(node) + .5});
            }
            cells.set(0, new double[]{startX, startZ});
            cells.set(cells.size() - 1, new double[]{finishX, finishZ});
        }
        return smooth(cells);
    }

    /** Relaxes the grid path into a smooth curve, resampled every {@link #STEP}. */
    static double[][] smooth(List<double[]> corners) {
        double[][] even = resample(corners.toArray(new double[0][]), 1.0);
        int n = even.length;
        for (int pass = 0; pass < 28 && n > 4; pass++) {
            double[][] next = new double[n][];
            next[0] = even[0];
            next[n - 1] = even[n - 1];
            for (int i = 1; i < n - 1; i++) {
                next[i] = new double[]{.5 * even[i][0] + .25 * (even[i - 1][0] + even[i + 1][0]),
                        .5 * even[i][1] + .25 * (even[i - 1][1] + even[i + 1][1])};
            }
            even = next;
        }
        return resample(catmullRom(even), STEP);
    }

    /** A Catmull-Rom curve through the points, ten samples per segment. */
    private static double[][] catmullRom(double[][] p) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < p.length - 1; i++) {
            double[] a = p[Math.max(0, i - 1)], b = p[i], c = p[i + 1], d = p[Math.min(p.length - 1, i + 2)];
            for (int k = 0; k < 10; k++) {
                double t = k / 10.0, t2 = t * t, t3 = t2 * t;
                out.add(new double[]{
                        .5 * (2 * b[0] + (-a[0] + c[0]) * t + (2 * a[0] - 5 * b[0] + 4 * c[0] - d[0]) * t2 + (-a[0] + 3 * b[0] - 3 * c[0] + d[0]) * t3),
                        .5 * (2 * b[1] + (-a[1] + c[1]) * t + (2 * a[1] - 5 * b[1] + 4 * c[1] - d[1]) * t2 + (-a[1] + 3 * b[1] - 3 * c[1] + d[1]) * t3)});
            }
        }
        out.add(p[p.length - 1]);
        return out.toArray(new double[0][]);
    }

    /** The polyline resampled so that consecutive points are {@code step} apart along it (the last one is the end). */
    public static double[][] resample(double[][] line, double step) {
        List<double[]> out = new ArrayList<>();
        out.add(line[0]);
        double carried = 0;
        for (int i = 1; i < line.length; i++) {
            double dx = line[i][0] - line[i - 1][0], dz = line[i][1] - line[i - 1][1], seg = Math.hypot(dx, dz);
            double at = step - carried;
            while (at <= seg) {
                out.add(new double[]{line[i - 1][0] + dx * at / seg, line[i - 1][1] + dz * at / seg});
                at += step;
            }
            carried = seg - (at - step);
        }
        double[] last = line[line.length - 1], tail = out.get(out.size() - 1);
        if (Math.hypot(last[0] - tail[0], last[1] - tail[1]) > step * .25) {
            out.add(last);
        } else {
            out.set(out.size() - 1, last);
        }
        return out.toArray(new double[0][]);
    }

    /** Total length of a polyline (m). */
    public static double length(double[][] line) {
        double sum = 0;
        for (int i = 1; i < line.length; i++) {
            sum += Math.hypot(line[i][0] - line[i - 1][0], line[i][1] - line[i - 1][1]);
        }
        return sum;
    }

    /** The cost map and the A* search over the 1 m cells of the corridor. */
    private static final class Grid {
        final DoubleBinaryOperator ground;
        final int minX, minZ, width, depth;
        final double[] height, soft;
        /** The steepest wanted grade (rise over run), 0 for none. */
        final double limit;

        Grid(DoubleBinaryOperator ground, int minX, int minZ, int width, int depth, double limit) {
            this.ground = ground;
            this.limit = limit;
            this.soft = new double[width * depth];
            Arrays.fill(soft, Double.POSITIVE_INFINITY);
            this.minX = minX;
            this.minZ = minZ;
            this.width = width;
            this.depth = depth;
            this.height = new double[width * depth];
            Arrays.fill(height, Double.POSITIVE_INFINITY);   // not looked at yet
        }

        int index(int x, int z) {
            return (z - minZ) * width + (x - minX);
        }

        int x(int node) {
            return node % width + minX;
        }

        int z(int node) {
            return node / width + minZ;
        }

        boolean inside(int x, int z) {
            return x >= minX && z >= minZ && x < minX + width && z < minZ + depth;
        }

        /** Height of the ground at the centre of a cell, NaN when it cannot be ridden. */
        double at(int x, int z) {
            int node = index(x, z);
            if (height[node] == Double.POSITIVE_INFINITY) {
                height[node] = ground.applyAsDouble(x + .5, z + .5);
            }
            return height[node];
        }

        /** The ground at a cell averaged over {@link #SOFT} cells around it: the grade a rider feels, not the single block steps. */
        double smooth(int x, int z) {
            int node = index(x, z);
            if (soft[node] == Double.POSITIVE_INFINITY) {
                double sum = 0;
                int count = 0;
                for (int dx = -SOFT; dx <= SOFT; dx++) {
                    for (int dz = -SOFT; dz <= SOFT; dz++) {
                        double h = at(Math.max(minX, Math.min(minX + width - 1, x + dx)), Math.max(minZ, Math.min(minZ + depth - 1, z + dz)));
                        if (!Double.isNaN(h)) {
                            sum += h;
                            count++;
                        }
                    }
                }
                soft[node] = count == 0 || Double.isNaN(at(x, z)) ? Double.NaN : sum / count;
            }
            return soft[node];
        }

        /** Cost factor (1 = flat ground) of the step from one cell to the next, infinity if it cannot be taken. */
        double factor(int x0, int z0, int x1, int z1) {
            double h0 = at(x0, z0), h1 = at(x1, z1);
            if (Double.isNaN(h0) || Double.isNaN(h1)) {
                return Double.POSITIVE_INFINITY;
            }
            double run = Math.hypot(x1 - x0, z1 - z0), slope = (h1 - h0) / run;
            if (Math.abs(slope) > CLIFF) {
                return Double.POSITIVE_INFINITY;
            }
            double factor = 1 + 8 * Math.max(0, slope + .015) + 10 * Math.max(0, -slope - STEEP);
            if (limit > 0) {
                double soft = (smooth(x1, z1) - smooth(x0, z0)) / run;
                factor = 1 + 8 * Math.max(0, soft + .015) + OVER_LIMIT * Math.max(0, Math.abs(soft) - .95 * limit);
            }
            // a steep slope across the direction of travel needs a deep cut
            double gx = (at(Math.min(x1 + 1, minX + width - 1), z1) - at(Math.max(x1 - 1, minX), z1)) / 2;
            double gz = (at(x1, Math.min(z1 + 1, minZ + depth - 1)) - at(x1, Math.max(z1 - 1, minZ))) / 2;
            double across = Math.abs(gx * -(z1 - z0) / run + gz * (x1 - x0) / run);
            if (!Double.isNaN(across)) {
                factor += 2 * Math.max(0, across - (limit > 0 ? .8 : .3));
            }
            return factor;
        }

        /** A* from {@code start} to {@code finish}; null when the corridor has no way through. */
        int[] search(int start, int finish, double sx, double sz, double fx, double fz, int corridor) {
            double[] cost = new double[width * depth];
            int[] parent = new int[width * depth];
            Arrays.fill(cost, Double.POSITIVE_INFINITY);
            Arrays.fill(parent, -1);
            PriorityQueue<double[]> open = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
            int expanded = 0;
            cost[start] = 0;
            open.add(new double[]{heuristic(start, fx, fz), start});
            double dx = fx - sx, dz = fz - sz, len = Math.max(1e-6, Math.hypot(dx, dz));
            while (!open.isEmpty()) {
                double[] top = open.poll();
                int node = (int) top[1];
                if (node == finish) {
                    List<Integer> nodes = new ArrayList<>();
                    for (int at = finish; at != -1; at = parent[at]) {
                        nodes.add(at);
                    }
                    int[] path = new int[nodes.size()];
                    for (int i = 0; i < path.length; i++) {
                        path[i] = nodes.get(path.length - 1 - i);
                    }
                    return path;
                }
                if (top[0] - heuristic(node, fx, fz) > cost[node] + 1e-9) {
                    continue;   // a stale queue entry
                }
                if (++expanded > MAX_EXPANDED) {
                    throw new DownhillShapes.Rejected("descentmtb.downhill.too_complex");
                }
                int x = x(node), z = z(node);
                for (int[] move : MOVES) {
                    int nx = x + move[0], nz = z + move[1];
                    if (!inside(nx, nz)) {
                        continue;
                    }
                    double off = Math.abs((nx + .5 - sx) * dz - (nz + .5 - sz) * dx) / len;
                    if (off > corridor) {
                        continue;
                    }
                    double factor = factor(x, z, nx, nz);
                    if (Double.isInfinite(factor)) {
                        continue;
                    }
                    int next = index(nx, nz);
                    double total = cost[node] + factor * Math.hypot(move[0], move[1]) * (1 + (limit > 0 ? .15 : .01) * off / corridor);
                    if (total < cost[next]) {
                        cost[next] = total;
                        parent[next] = node;
                        open.add(new double[]{total + heuristic(next, fx, fz), next});
                    }
                }
            }
            return null;
        }

        private double heuristic(int node, double fx, double fz) {
            return 1.05 * Math.hypot(x(node) + .5 - fx, z(node) + .5 - fz);
        }
    }

    private DownhillRoute() {}
}
