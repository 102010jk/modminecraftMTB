package com.descentmtb.trail;

import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side list of the trail signs that are currently loaded, so the trail timer can look for START and
 * FINISH signs near the rider without scanning the world. Filled by {@link TrailSignEntity#onLoad()}.
 */
public final class TrailSignRegistry {
    private static final Set<TrailSignEntity> LOADED = ConcurrentHashMap.newKeySet();

    static void add(TrailSignEntity sign) {
        LOADED.add(sign);
    }

    static void remove(TrailSignEntity sign) {
        LOADED.remove(sign);
    }

    /** Loaded signs of the given type in {@code level} whose block is within {@code range} blocks of the point. */
    public static List<TrailSignEntity> near(Level level, double x, double y, double z, double range, SignContent.Type... types) {
        List<TrailSignEntity> found = new ArrayList<>();
        double rangeSq = range * range;
        for (TrailSignEntity sign : LOADED) {
            if (sign.isRemoved() || sign.getLevel() != level || !hasType(sign, types)) {
                continue;
            }
            var pos = sign.getBlockPos();
            double dx = pos.getX() + .5 - x, dy = pos.getY() + .5 - y, dz = pos.getZ() + .5 - z;
            if (dx * dx + dy * dy + dz * dz <= rangeSq) {
                found.add(sign);
            }
        }
        return found;
    }

    private static boolean hasType(TrailSignEntity sign, SignContent.Type[] types) {
        for (SignContent.Type type : types) {
            if (sign.content().type() == type) {
                return true;
            }
        }
        return false;
    }

    /** Forgets signs that belong to another level (called when the client enters a new world). */
    public static void retainOnly(Level level) {
        LOADED.removeIf(sign -> sign.getLevel() != level);
    }

    private TrailSignRegistry() {}
}
