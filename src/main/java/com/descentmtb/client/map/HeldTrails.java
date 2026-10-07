package com.descentmtb.client.map;

import com.descentmtb.map.BikeparkMap;
import com.descentmtb.map.RouteMerge;
import com.descentmtb.map.TrailTrack;
import com.descentmtb.trail.TrailSignEntity;
import com.descentmtb.trail.TrailSignRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A map in the hand (and the opened map screen) shows every trail around, not only the ones clicked onto it: the routes
 * saved on the map plus the GPS trails of all trail signs the client has loaded (the server already sends those with
 * the sign, so nothing new is trusted or synced). A map on the wall keeps to the saved routes only. The combined map is
 * rebuilt about once a second; the routes of the signs in the player's dimension come first.
 */
final class HeldTrails {
    private static final int REFRESH_TICKS = 20, MAX_ENTRIES = 8;
    private record Entry(BikeparkMap merged, long tick, Level level) {}
    private record SignRoute(TrailTrack source, String name, BikeparkMap.Route route) {}
    private static final Map<BikeparkMap, Entry> CACHE = new LinkedHashMap<>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<BikeparkMap, Entry> eldest) { return size() > MAX_ENTRIES; }
    };
    private static final Map<TrailSignEntity, SignRoute> SIGN_ROUTES = new WeakHashMap<>();

    private HeldTrails() {}

    /** The routes saved on the held map plus those of every loaded trail sign. */
    static BikeparkMap all(BikeparkMap saved) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return saved;
        long now = level.getGameTime();
        Entry entry = CACHE.get(saved);
        if (entry != null && entry.level() == level && now >= entry.tick() && now - entry.tick() < REFRESH_TICKS) return entry.merged();
        BikeparkMap merged = compute(saved, level, mc.player == null ? null : mc.player.position());
        CACHE.put(saved, new Entry(merged, now, level));
        return merged;
    }

    private static BikeparkMap compute(BikeparkMap saved, Level level, Vec3 from) {
        var here = level.dimension().location();
        List<TrailSignEntity> signs = new ArrayList<>();
        for (TrailSignEntity sign : TrailSignRegistry.loaded(level)) if (sign.track() != null) signs.add(sign);
        int room = Math.max(0, BikeparkMap.MAX_VIEW_ROUTES - saved.routes().size());
        if (signs.size() > room && from != null) {
            // too many for the map: the nearest ones, so the picture around the player stays complete
            signs.sort(Comparator.comparingDouble(s -> s.getBlockPos().distToCenterSqr(from)));
            signs = new ArrayList<>(signs.subList(0, room));
        }
        // a fixed order, so an unchanged park gives an equal map (and keeps its drawn texture) from one refresh to the next
        signs.sort(Comparator.comparingLong(s -> s.getBlockPos().asLong()));
        List<BikeparkMap.Route> live = new ArrayList<>();
        for (TrailSignEntity sign : signs) live.add(routeOf(sign));
        List<BikeparkMap.Route> routes = RouteMerge.merge(List.of(live, saved.routes()), BikeparkMap::sameRoute, r -> r.dimension().equals(here), BikeparkMap.MAX_VIEW_ROUTES);
        return routes.equals(saved.routes()) ? saved : new BikeparkMap(routes);
    }

    private static BikeparkMap.Route routeOf(TrailSignEntity sign) {
        TrailTrack track = sign.track();
        String name = sign.content().name();
        SignRoute cached = SIGN_ROUTES.get(sign);
        if (cached != null && cached.source() == track && cached.name().equals(name)) return cached.route();
        BikeparkMap.Route route = new BikeparkMap.Route(name, sign.trackDimension(), track);
        SIGN_ROUTES.put(sign, new SignRoute(track, name, route));
        return route;
    }
}
