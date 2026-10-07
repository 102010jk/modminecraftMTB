package com.descentmtb.client.map;

import com.descentmtb.map.BikeparkMap;
import com.descentmtb.map.RouteMerge;
import com.descentmtb.map.TrailMapItem;
import com.descentmtb.map.WallLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds out which item frames of a wall make up one big trail map: the frames that hold a trail map, hang on the same
 * wall plane facing the same way and form a filled rectangle (see {@link WallLayout}, at most 8 by 8) with this frame.
 * Each frame asks on every draw; the answer is kept per frame and worked out again about once a second, or when the
 * frame's own map changes. The big map shows the routes saved on all the maps of the rectangle together.
 */
final class WallMaps {
    private static final int REFRESH_TICKS = 20;

    /** The frame's place in its wall: {@code wall} frames across and down, this one at {@code column}/{@code row} (from the top left), and the routes the whole wall shows. */
    record Wall(int across, int down, int column, int row, BikeparkMap data) {
        boolean single() { return across == 1 && down == 1; }
    }

    private record Entry(Wall wall, BikeparkMap own, long tick, Level level) {}
    private static final Map<Integer, Entry> CACHE = new HashMap<>();

    private WallMaps() {}

    static Wall of(ItemFrame frame, BikeparkMap own) {
        Level level = frame.level();
        long now = level.getGameTime();
        Entry entry = CACHE.get(frame.getId());
        if (entry != null && entry.level() == level && now >= entry.tick() && now - entry.tick() < REFRESH_TICKS
                && (entry.own() == own || entry.own().equals(own))) return entry.wall();
        Wall wall = compute(frame, own);
        if (CACHE.size() > 256) CACHE.values().removeIf(e -> e.level() != level || now - e.tick() > 200 || now < e.tick());
        CACHE.put(frame.getId(), new Entry(wall, own, now, level));
        return wall;
    }

    private static Wall compute(ItemFrame frame, BikeparkMap own) {
        Direction facing = frame.getDirection();
        Wall alone = new Wall(1, 1, 0, 0, own);
        if (facing.getAxis() == Direction.Axis.Y) return alone;
        Level level = frame.level();
        BlockPos origin = frame.getPos();
        Direction right = facing.getCounterClockWise(); // the viewer's right while looking at the wall the frame hangs on
        Map<WallLayout.Cell, ItemFrame> grid = new HashMap<>();
        for (ItemFrame other : level.getEntitiesOfClass(ItemFrame.class, new AABB(origin).inflate(WallLayout.MAX_SIDE + 1),
                f -> !f.isRemoved() && f.getDirection() == facing && f.getItem().getItem() instanceof TrailMapItem)) {
            BlockPos p = other.getPos();
            if (!samePlane(p, origin, facing)) continue;
            int u = (p.getX() - origin.getX()) * right.getStepX() + (p.getZ() - origin.getZ()) * right.getStepZ();
            grid.putIfAbsent(new WallLayout.Cell(u, p.getY() - origin.getY()), other);
        }
        WallLayout.Cell me = new WallLayout.Cell(0, 0);
        grid.putIfAbsent(me, frame);
        WallLayout.Rect rect = WallLayout.around(grid.keySet(), me, WallLayout.MAX_SIDE, WallLayout.MAX_SIDE);
        if (rect.count() == 1) return alone;
        // the saved routes of every map in the rectangle, in a fixed order (top row first, left to right) so all its frames agree
        List<List<BikeparkMap.Route>> lists = new ArrayList<>();
        for (int v = rect.v0() + rect.h() - 1; v >= rect.v0(); v--) {
            for (int u = rect.u0(); u < rect.u0() + rect.w(); u++) {
                ItemFrame f = grid.get(new WallLayout.Cell(u, v));
                if (f != null) lists.add(TrailMapItem.data(f.getItem()).routes());
            }
        }
        var here = level.dimension().location();
        List<BikeparkMap.Route> routes = RouteMerge.merge(lists, BikeparkMap::sameRoute, r -> r.dimension().equals(here), BikeparkMap.MAX_VIEW_ROUTES);
        return new Wall(rect.w(), rect.h(), rect.column(0), rect.rowFromTop(0), new BikeparkMap(routes));
    }

    private static boolean samePlane(BlockPos a, BlockPos b, Direction facing) {
        return switch (facing.getAxis()) {
            case X -> a.getX() == b.getX();
            case Z -> a.getZ() == b.getZ();
            case Y -> a.getY() == b.getY();
        };
    }
}
