package com.descentmtb.client.custom;

import com.descentmtb.custom.BikeBuild;
import com.descentmtb.entity.BikeType;
import net.minecraft.core.BlockPos;

/**
 * Client entry point of the workshop: {@link com.descentmtb.custom.BikeStandBlock} calls {@link #open} on the client
 * when a player right-clicks a stand that holds a bike. CONTRACT with the workshop screen: the GUI work fills the body
 * (open the screen with these three values, save through {@code WorkshopNet.apply(stand, build)} and take the bike
 * back through {@code WorkshopNet.takeBike(stand)}). It stays free of client-only types in its signature so the common
 * block class can call it.
 */
public final class WorkshopScreens {
    /**
     * @param stand position of the stand block (pass it back to WorkshopNet)
     * @param type  type of the bike on the stand (ENDURO / HARDTAIL), which decides the available parts
     * @param build the bike's current build, never null
     */
    public static void open(BlockPos stand, BikeType type, BikeBuild build) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new WorkshopScreen(stand, type, build));
    }

    private WorkshopScreens() {}
}
