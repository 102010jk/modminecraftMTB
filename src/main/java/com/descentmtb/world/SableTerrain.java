package com.descentmtb.world;

import com.descentmtb.physics.*;
import com.descentmtb.ramp.RampBlock;
import dev.ryanhcode.sable.companion.SableCompanion;
import dev.ryanhcode.sable.companion.SubLevelAccess;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;

/** Global-coordinate bike queries on moving decks; safe empty fallback without Sable. */
public final class SableTerrain implements Terrain {
    private final Terrain base;
    private final McColumns columns;
    private final Level level;
    /** Without the Sable mod there are no moving decks: skip every lookup. */
    private static final boolean SABLE_PRESENT = net.neoforged.fml.ModList.get().isLoaded("sable");
    private final it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap nearby = new it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap();
    public SableTerrain(Terrain base, McColumns columns, Level level) { this.base = base; this.columns = columns; this.level = level; }
    public void newTick() { nearby.clear(); }
    private boolean nearby(double x, double y, double z) {
        if (!SABLE_PRESENT) {
            return false;
        }
        int cx = (int) Math.floor(x / 8), cy = (int) Math.floor(y / 8), cz = (int) Math.floor(z / 8);
        long key = ((long) cx & 0x1FFFFF) << 42 | ((long) cy & 0xFFFFF) << 21 | ((long) cz & 0x1FFFFF);
        if (nearby.containsKey(key)) {
            return nearby.get(key);
        }
        boolean found = SableCompanion.INSTANCE
                .getAllIntersecting(level, new BoundingBox3d(cx * 8 - 2, cy * 8 - 8, cz * 8 - 2,
                        cx * 8 + 10, cy * 8 + 16, cz * 8 + 10)).iterator().hasNext();
        nearby.put(key, found);
        return found;
    }
    public boolean ground(double x, double z, double top, double bottom, GroundHit out) { return query(x, z, top, bottom, out, false); }
    public boolean floor(double x, double z, double top, double bottom, GroundHit out) { return query(x, z, top, bottom, out, true); }
    @Override
    public boolean raycast(V3 from, V3 to, RayHit out) {
        var hit = level.clip(new ClipContext(new Vec3(from.x, from.y, from.z), new Vec3(to.x, to.y, to.z),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS) return false;
        if (hit.isInside()) return insideHit(from, to, hit.getBlockPos(), out);
        SubLevelAccess sub = SABLE_PRESENT ? SableCompanion.INSTANCE.getContaining(level, hit.getBlockPos()) : null;
        // Shaped blocks have a continuous riding surface, rather than the walking collider's steps.
        if (RampBlock.isRamp(level.getBlockState(hit.getBlockPos()))) {
            if (!Terrain.super.raycast(from, to, out)) return false;
            if (sub != null) {
                Vec3 localPoint = sub.logicalPose().transformPositionInverse(new Vec3(out.point.x, out.point.y, out.point.z));
                Vec3 v = SableCompanion.INSTANCE.getVelocity(level, sub, localPoint);
                out.velocity = new V3(v.x, v.y, v.z);
            }
            return true;
        }
        Vec3 local = hit.getLocation(), world = sub == null ? local : sub.logicalPose().transformPosition(local);
        Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        Vec3 velocity = Vec3.ZERO;
        if (sub != null) {
            normal = sub.logicalPose().transformNormal(normal).normalize();
            velocity = SableCompanion.INSTANCE.getVelocity(level, sub, local);
        }
        V3 point = new V3(world.x, world.y, world.z);
        out.set(point, new V3(normal.x, normal.y, normal.z), point.sub(from).length(),
                new V3(velocity.x, velocity.y, velocity.z));
        return true;
    }
    /**
     * The probe already starts inside a solid cube (a fast step ended a few centimetres into a wall). Ignoring that
     * lets every following step slide deeper until the bike is through the rock face; instead report the wall right
     * where the probe is, facing back against the motion, so its velocity into the block is cancelled. Shaped trail
     * blocks are left to their own surface query: riding over them keeps probes inside their walking collider.
     */
    private boolean insideHit(V3 from, V3 to, net.minecraft.core.BlockPos pos, RayHit out) {
        var state = level.getBlockState(pos);
        if (RampBlock.isRamp(state) || !state.isCollisionShapeFullBlock(level, pos)) return false;
        if (SABLE_PRESENT && SableCompanion.INSTANCE.getContaining(level, pos) != null) return false;
        V3 d = to.sub(from);
        double ax = Math.abs(d.x), ay = Math.abs(d.y), az = Math.abs(d.z);
        if (Math.max(ax, Math.max(ay, az)) < 1e-9) return false;
        V3 normal = ax >= ay && ax >= az ? new V3(-Math.signum(d.x), 0, 0)
                : az >= ay ? new V3(0, 0, -Math.signum(d.z)) : new V3(0, -Math.signum(d.y), 0);
        out.set(from, normal, 0, V3.ZERO);
        return true;
    }

    private boolean query(double x, double z, double top, double bottom, GroundHit out, boolean floor) {
        boolean found = floor ? base.floor(x, z, top, bottom, out) : base.ground(x, z, top, bottom, out);
        if (!nearby(x, top, z)) return found;
        var hit = level.clip(new ClipContext(new Vec3(x, top, z), new Vec3(x, bottom, z),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS) return found;
        SubLevelAccess sub = SableCompanion.INSTANCE.getContaining(level, hit.getBlockPos());
        if (sub == null) return found;
        Vec3 local = hit.getLocation();
        Vec3 world = sub.logicalPose().transformPosition(local);
        Vec3 localNormal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        var state = level.getBlockState(hit.getBlockPos());
        if (RampBlock.isRamp(state)) {
            Vec3 derivative = sub.logicalPose().transformNormalInverse(new Vec3(0, 1, 0));
            double y = world.y;
            for (int i = 0; i < 6; i++) {
                local = sub.logicalPose().transformPositionInverse(new Vec3(x, y, z));
                double fx = local.x - hit.getBlockPos().getX(), fz = local.z - hit.getBlockPos().getZ();
                double sx = com.descentmtb.trail.TrailSurfaces.slopeX(state,level,hit.getBlockPos(),fx,fz), sz = com.descentmtb.trail.TrailSurfaces.slopeZ(state,level,hit.getBlockPos(),fx,fz);
                double dy = derivative.y - sx * derivative.x - sz * derivative.z;
                if (Math.abs(dy) < .1) break;
                y -= (local.y - hit.getBlockPos().getY() - com.descentmtb.trail.TrailSurfaces.height(state,level,hit.getBlockPos(),fx,fz)) / dy;
                localNormal = new Vec3(-sx, 1, -sz).normalize();
            }
            double fx=local.x-hit.getBlockPos().getX(),fz=local.z-hit.getBlockPos().getZ();
            // A vertical line can leave the initially hit tile on a sharply rotated deck.
            // Reject the voxel candidate rather than extrapolating another tile's plane.
            if(fx>=0&&fx<=1&&fz>=0&&fz<=1 && (!(level.getBlockEntity(hit.getBlockPos()) instanceof com.descentmtb.trail.TrailSurfaceEntity shaped)||shaped.hasSurface(fx,fz)))world = new Vec3(x, y, z);
            else return found;
        }
        Vec3 normal = sub.logicalPose().transformNormal(localNormal).normalize();
        if (normal.y < .15 || world.y > top + .02 || world.y < bottom || (found && world.y <= out.height)) return found;
        Vec3 v = SableCompanion.INSTANCE.getVelocity(level, sub, local);
        out.set(world.y, new V3(normal.x, normal.y, normal.z), columns.surface(hit.getBlockPos().getX(), hit.getBlockPos().getZ(), local.y));
        out.velocity = new V3(v.x, v.y, v.z);
        return true;
    }
    @Override
    public double waterSurface(double x, double z, double yTop, double yBottom) {
        return base.waterSurface(x, z, yTop, yBottom);
    }

    public boolean solidAt(double x, double y, double z) {
        if (base.solidAt(x, y, z)) return true;
        if (!nearby(x, y, z)) return false;
        for (SubLevelAccess sub : SableCompanion.INSTANCE.getAllIntersecting(level,
                new BoundingBox3d(x - .05, y - .05, z - .05, x + .05, y + .05, z + .05))) {
            Vec3 local = sub.logicalPose().transformPositionInverse(new Vec3(x, y, z));
            if (columns.solid(local.x, local.y, local.z)) return true;
        }
        return false;
    }
}
