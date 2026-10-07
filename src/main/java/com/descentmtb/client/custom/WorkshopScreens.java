package com.descentmtb.client.custom;

import com.descentmtb.custom.BikeBuild;
import com.descentmtb.custom.MotoBuild;
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
     * @param type  type of the bike on the stand (ENDURO / HARDTAIL / a motorbike), which decides the screen and the available parts
     * @param build the bike's current build, never null
     * @param moto  the paint and tuning of a motorbike on the stand (ignored for a bicycle), never null
     */
    public static void open(BlockPos stand, BikeType type, BikeBuild build, MotoBuild moto) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        mc.setScreen(type.motor() ? new MotoWorkshopScreen(stand, type, moto) : new WorkshopScreen(stand, type, build));
    }

    public static void motoResult(BlockPos stand, boolean accepted, MotoBuild moto) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof MotoWorkshopScreen workshop) workshop.saveResult(stand, accepted, moto);
        if (!accepted && mc.player != null) mc.player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("descentmtb.workshop.status.rejected"), true);
    }

    public static void result(BlockPos stand,boolean accepted,BikeBuild build) {
        var mc=net.minecraft.client.Minecraft.getInstance();
        if(mc.screen instanceof WorkshopScreen workshop) workshop.saveResult(stand,accepted,build);
        if(!accepted && mc.player!=null) mc.player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable("descentmtb.workshop.status.rejected"),true);
    }

    private WorkshopScreens() {}
}
