package com.descentmtb.trail;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Development-only: coarse vertex sculpting to build big test terrain quickly. Unlike the Trail Shaper this
 * moves a vertex shared by up to four blocks, so it is never reachable by players.
 */
final class DevFixtures {
    /** One sculpting step: 2/16 of a block. */
    static final double STEP = 2.0 / 16;

    /** Raises (or lowers) the corner / edge / block under {@code hit} by one {@link #STEP}, neighbours included. */
    static void sculpt(ServerPlayer player, BlockPos pos, Vec3 hit, boolean lower) {
        var level = player.serverLevel();
        double fx = Math.max(0, Math.min(1, hit.x - pos.getX())), fz = Math.max(0, Math.min(1, hit.z - pos.getZ()));
        var vertices = ColumnShaper.pickVertices(pos.getX(), pos.getZ(), fx, fz);
        TrailEdit.apply(level, player, sculptVertices(level, pos, vertices, lower ? -STEP : STEP));
    }

    private static Map<BlockPos, TrailEdit.Change> sculptVertices(net.minecraft.world.level.Level level, BlockPos pos,
                                                                  java.util.List<ColumnShaper.Vertex> vertices, double delta) {
        int bx = pos.getX(), bz = pos.getZ();
        ColumnEditor.Column clicked = ColumnEditor.read(level, bx, bz, pos.getY());
        if (clicked == null) {
            throw new IllegalArgumentException("Tady není co tvarovat");
        }
        Map<Long, ColumnEditor.Column> columns = new HashMap<>();
        Map<Long, double[]> heights = new HashMap<>();
        for (ColumnShaper.Vertex v : vertices) {
            double next = Math.max(pos.getY() - 6, Math.min(pos.getY() + 12, clicked.abs()[(v.z() - bz) * 2 + (v.x() - bx)] + delta));
            // the four columns around this vertex share it
            for (int dz = 0; dz <= 1; dz++) {
                for (int dx = 0; dx <= 1; dx++) {
                    int cx = v.x() - 1 + dx, cz = v.z() - 1 + dz;
                    long key = BlockPos.asLong(cx, 0, cz);
                    ColumnEditor.Column col = cx == bx && cz == bz ? clicked
                            : columns.computeIfAbsent(key, k -> ColumnEditor.read(level, cx, cz, pos.getY()));
                    if (col == null || col.copycat() || clicked.deck() && !col.deck()) {
                        continue;
                    }
                    columns.put(key, col);
                    heights.computeIfAbsent(key, k -> col.abs().clone())[(1 - dz) * 2 + (1 - dx)] = next;
                }
            }
        }
        Map<BlockPos, TrailEdit.Change> changes = new LinkedHashMap<>();
        heights.forEach((key, abs) -> changes.putAll(ColumnEditor.rebuild(level, columns.get(key), abs)));
        return changes;
    }

    private DevFixtures() {}
}
