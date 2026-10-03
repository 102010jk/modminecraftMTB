package com.descentmtb.trail;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Who may use the construction tools (server config: creative only by default). */
public final class TrailPermissions {

    public static boolean allowed(Player player) {
        if (TrailConfig.CREATIVE_ONLY.get() && !player.isCreative()) {
            player.displayClientMessage(Component.translatable("descentmtb.builder.creative"), true);
            return false;
        }
        return true;
    }

    private TrailPermissions() {}
}
