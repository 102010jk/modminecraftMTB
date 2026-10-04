package com.descentmtb.network;

import com.descentmtb.trail.BermBuilder;
import com.descentmtb.trail.BermShapes;
import com.descentmtb.trail.DownhillBuilder;
import com.descentmtb.trail.DownhillShapes;
import com.descentmtb.trail.ShapeMode;
import com.descentmtb.trail.ShapeToolItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server: the Trail Shaper in the player's hand switches to another {@link ShapeMode} (by ordinal) and, for
 * the berm mode, takes the chosen berm steepness (ordinal) and width (m); for the downhill mode the second number is the
 * style (ordinal). A negative steepness means "no settings".
 */
public record ShapeTunePayload(int mode, int steepness, int width) implements CustomPacketPayload {
    public static final Type<ShapeTunePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("descentmtb", "shape_tune"));

    public static final StreamCodec<FriendlyByteBuf, ShapeTunePayload> CODEC = StreamCodec.ofMember(
            (message, buf) -> {
                buf.writeVarInt(message.mode);
                buf.writeVarInt(message.steepness + 1);
                buf.writeVarInt(message.width);
            },
            buf -> new ShapeTunePayload(buf.readVarInt(), buf.readVarInt() - 1, buf.readVarInt()));

    public ShapeTunePayload(ShapeMode mode) {
        this(mode.ordinal(), -1, 0);
    }

    /** A switch to the berm mode with the given settings. */
    public ShapeTunePayload(ShapeMode mode, BermBuilder.Settings settings) {
        this(mode.ordinal(), settings.steepness().ordinal(), settings.width());
    }

    /** A switch to the downhill mode with the given settings. */
    public ShapeTunePayload(ShapeMode mode, DownhillBuilder.Settings settings) {
        this(mode.ordinal(), settings.style().ordinal(), settings.width());
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
            ShapeToolItem.changeMode(player, modes[message.mode]);
        }
        if (ShapeToolItem.mode(player.getMainHandItem()).kind == ShapeMode.Kind.DOWNHILL) {
            DownhillShapes.Style[] styles = DownhillShapes.Style.values();
            if (message.steepness >= 0 && message.steepness < styles.length) {
                new DownhillBuilder.Settings(styles[message.steepness], message.width).bounded().store(player.getMainHandItem());
            }
            return;
        }
        BermShapes.Steepness[] steepnesses = BermShapes.Steepness.values();
        if (message.steepness >= 0 && message.steepness < steepnesses.length) {
            new BermBuilder.Settings(steepnesses[message.steepness], message.width).bounded().store(player.getMainHandItem());
        }
    }
}
