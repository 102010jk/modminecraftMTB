package com.descentmtb.network;

import com.descentmtb.trail.BermBuilder;
import com.descentmtb.trail.BermShapes;
import com.descentmtb.trail.CornerEdits;
import com.descentmtb.trail.CursorSettings;
import com.descentmtb.trail.DownhillBuilder;
import com.descentmtb.trail.DownhillShapes;
import com.descentmtb.trail.LineSettings;
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
 * style (ordinal) and {@code grade} the grade level (ordinal); for the cursor ({@link ShapeMode#AUTO}) the two numbers are its
 * sub-type and its step (ordinals); for the line tools ({@link ShapeMode.Kind#CLEAR}, {@link ShapeMode.Kind#LINE}) the width
 * is their width in blocks. A negative steepness means "no settings", a negative grade "no grade".
 */
public record ShapeTunePayload(int mode, int steepness, int width, int grade) implements CustomPacketPayload {
    public static final Type<ShapeTunePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("descentmtb", "shape_tune"));

    public static final StreamCodec<FriendlyByteBuf, ShapeTunePayload> CODEC = StreamCodec.ofMember(
            (message, buf) -> {
                buf.writeVarInt(message.mode);
                buf.writeVarInt(message.steepness + 1);
                buf.writeVarInt(message.width);
                buf.writeVarInt(message.grade + 1);
            },
            buf -> new ShapeTunePayload(buf.readVarInt(), buf.readVarInt() - 1, buf.readVarInt(), buf.readVarInt() - 1));

    public ShapeTunePayload(ShapeMode mode) {
        this(mode.ordinal(), -1, 0, -1);
    }

    /** A switch to the berm mode with the given settings. */
    public ShapeTunePayload(ShapeMode mode, BermBuilder.Settings settings) {
        this(mode.ordinal(), settings.steepness().ordinal(), settings.width(), -1);
    }

    /** A switch to the downhill mode with the given settings. */
    public ShapeTunePayload(ShapeMode mode, DownhillBuilder.Settings settings) {
        this(mode.ordinal(), settings.style().ordinal(), settings.width(), settings.grade().ordinal());
    }

    /** A switch to a line tool (clear path, straight line) with the given width. */
    public ShapeTunePayload(ShapeMode mode, LineSettings settings) {
        this(mode.ordinal(), -1, settings.width(), -1);
    }

    /** A switch to the cursor with the given sub-type and step. */
    public ShapeTunePayload(ShapeMode mode, CursorSettings settings) {
        this(mode.ordinal(), settings.pick().ordinal(), settings.step().ordinal(), -1);
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
        if (ShapeToolItem.mode(player.getMainHandItem()) == ShapeMode.AUTO) {
            CornerEdits.Pick[] picks = CornerEdits.Pick.values();
            CornerEdits.Step[] steps = CornerEdits.Step.values();
            if (message.steepness >= 0 && message.steepness < picks.length && message.width >= 0 && message.width < steps.length) {
                new CursorSettings(picks[message.steepness], steps[message.width]).store(player.getMainHandItem());
            }
            return;
        }
        if (ShapeToolItem.mode(player.getMainHandItem()).kind == ShapeMode.Kind.DOWNHILL) {
            DownhillShapes.Style[] styles = DownhillShapes.Style.values();
            DownhillShapes.Grade[] grades = DownhillShapes.Grade.values();
            if (message.steepness >= 0 && message.steepness < styles.length && message.grade >= 0 && message.grade < grades.length) {
                new DownhillBuilder.Settings(styles[message.steepness], message.width, grades[message.grade]).bounded().store(player.getMainHandItem());
            }
            return;
        }
        ShapeMode.Kind kind = ShapeToolItem.mode(player.getMainHandItem()).kind;
        if (kind == ShapeMode.Kind.CLEAR || kind == ShapeMode.Kind.LINE) {
            if (message.width > 0) {
                new LineSettings(message.width).bounded().store(player.getMainHandItem());
            }
            return;
        }
        BermShapes.Steepness[] steepnesses = BermShapes.Steepness.values();
        if (message.steepness >= 0 && message.steepness < steepnesses.length) {
            new BermBuilder.Settings(steepnesses[message.steepness], message.width).bounded().store(player.getMainHandItem());
        }
    }
}
