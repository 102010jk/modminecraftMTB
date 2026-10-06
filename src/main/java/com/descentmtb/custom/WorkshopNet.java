package com.descentmtb.custom;

import com.descentmtb.network.WorkshopApplyPayload;
import com.descentmtb.network.WorkshopTakePayload;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * CONTRACT between the workshop screen (client) and the stand logic (server). Both calls only SEND a payload; the
 * server validates it ({@link BikeStands}: reach 6 blocks, build / interact permission, a bike on the stand) and the
 * stand block entity syncs the outcome back to every viewer. Call them from the client only.
 */
public final class WorkshopNet {
    /** Save {@code build} to the bike standing on the stand at {@code stand}. */
    public static void apply(BlockPos stand, BikeBuild build) {
        PacketDistributor.sendToServer(new WorkshopApplyPayload(stand, build));
    }

    /** Take the bike off the stand into the player's inventory. */
    public static void takeBike(BlockPos stand) {
        PacketDistributor.sendToServer(new WorkshopTakePayload(stand));
    }

    private WorkshopNet() {}
}
