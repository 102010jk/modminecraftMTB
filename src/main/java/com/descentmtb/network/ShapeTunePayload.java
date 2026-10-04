package com.descentmtb.network;

import com.descentmtb.trail.ShapeMode;
import com.descentmtb.trail.ShapeToolItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client to server: the Trail Shaper in the player's hand switches to another {@link ShapeMode} (by ordinal). */
public record ShapeTunePayload(int mode) implements CustomPacketPayload {
    public static final Type<ShapeTunePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("descentmtb", "shape_tune"));

    public static final StreamCodec<FriendlyByteBuf, ShapeTunePayload> CODEC = StreamCodec.ofMember(
            (message, buf) -> buf.writeVarInt(message.mode),
            buf -> new ShapeTunePayload(buf.readVarInt()));

    public ShapeTunePayload(ShapeMode mode) {
        this(mode.ordinal());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ShapeTunePayload message, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !ShapeToolItem.usable(player.getMainHandItem())) {
            return;
        }
        ShapeMode[] modes = ShapeMode.values();
        if (message.mode >= 0 && message.mode < modes.length) {
            ShapeToolItem.mode(player.getMainHandItem(), modes[message.mode]);
        }
    }
}
