package com.descentmtb.network;

import com.descentmtb.DescentMtb;
import com.descentmtb.trail.TrailEdit;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: take back the player's last shaping edit (see {@link TrailEdit#undo}). */
public record TrailUndoPayload() implements CustomPacketPayload {
    public static final Type<TrailUndoPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "trail_undo"));

    public static final StreamCodec<FriendlyByteBuf, TrailUndoPayload> CODEC =
            StreamCodec.unit(new TrailUndoPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(TrailUndoPayload message, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        try {
            int restored = TrailEdit.undo(player.level(), player);
            player.displayClientMessage(Component.translatable("descentmtb.shape.undone", restored), true);
        } catch (IllegalArgumentException e) {
            player.displayClientMessage(Component.literal(e.getMessage()), true);
        }
    }
}
