package com.descentmtb.client;

import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Sable returns block hits in plot coordinates; camera positions must remain in the visible world. */
public final class ClipCoordinates {
    public static Vec3 world(BlockHitResult hit,float partialTick) {
        var sub=SableCompanion.INSTANCE.getContainingClient(hit.getBlockPos());
        return sub==null?hit.getLocation():sub.renderPose(partialTick).transformPosition(hit.getLocation());
    }
    private ClipCoordinates(){}
}
