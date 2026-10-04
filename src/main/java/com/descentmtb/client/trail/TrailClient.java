package com.descentmtb.client.trail;

import com.descentmtb.client.ModKeyMappings;
import com.descentmtb.trail.ShapeToolItem;
import com.descentmtb.trail.TrailSignBlock;
import com.descentmtb.network.TrailUndoPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client side of the Trail Shaper: opens the radial menu and the sign editor. */
public final class TrailClient {

    public static void setup() {
        TrailSignBlock.editor = be -> Minecraft.getInstance().setScreen(new SignEditorScreen(be));
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        boolean holdingShaper = mc.player != null && mc.screen == null && ShapeToolItem.usable(mc.player.getMainHandItem());
        if (!holdingShaper) {
            return;
        }
        if (ModKeyMappings.TRAIL_MENU.consumeClick()) {
            mc.setScreen(new ShapeRadialScreen());
        }
        if (ModKeyMappings.TRAIL_UNDO.consumeClick()) {
            PacketDistributor.sendToServer(new TrailUndoPayload());
        }
    }

    private TrailClient() {}
}
