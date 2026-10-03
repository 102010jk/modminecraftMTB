package com.descentmtb.world;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Finds collision-free feet before detaching a rider, including a bike lying on its side. */
public final class SafeDismount {
    public static Vec3 find(Level level, LivingEntity rider, Vec3 wanted) {
        McColumns columns = new McColumns(level);
        for (int ring = 0; ring < 9; ring++) for (int i = 0; i < (ring == 0 ? 1 : 16); i++) {
            double a = i * Math.PI / 8;
            double x = wanted.x + Math.cos(a) * ring * .7, z = wanted.z + Math.sin(a) * ring * .7;
            double floor = Double.NEGATIVE_INFINITY;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                double h = columns.collisionTop(x + dx * .29, z + dz * .29, wanted.y + 1.5, wanted.y - 4);
                if (!Double.isNaN(h)) floor = Math.max(floor, h);
            }
            double y = Math.max(wanted.y, floor + .02);
            var hit = new com.descentmtb.physics.Terrain.GroundHit();
            if (columns.terrain().floor(x, z, wanted.y + 1.5, wanted.y - 4, hit)) y = Math.max(y, hit.height + .02);
            if (level.noCollision(rider, new AABB(x - .3, y, z - .3, x + .3, y + 1.8, z + .3)))
                return new Vec3(x, y, z);
        }
        // The launch may be in open air beside a wall; keep it above the bike rather than in a block.
        return rider.position(); // already verified by vanilla collision, never guess inside a ceiling
    }
    private SafeDismount() {}
}
