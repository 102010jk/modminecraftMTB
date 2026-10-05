package com.descentmtb.network;

import com.descentmtb.DescentMtb;
import com.descentmtb.trail.JumpBuilder;
import com.descentmtb.trail.JumpProfiles;
import com.descentmtb.trail.ShapeMode;
import com.descentmtb.trail.ShapeToolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server, from the jump screen: remember these jump settings in the Trail Shaper in the main hand and, when
 * {@code build} is set, build the jump at {@code pos} running towards {@code facing} (see {@link JumpBuilder#build}).
 * Everything is validated and clamped on the server.
 */
public record JumpBuildPayload(boolean build, BlockPos pos, Direction facing, JumpProfiles.Params params) implements CustomPacketPayload {
    public static final Type<JumpBuildPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "jump_build"));

    public static final StreamCodec<FriendlyByteBuf, JumpBuildPayload> CODEC = StreamCodec.ofMember(
            (m, buf) -> {
                buf.writeBoolean(m.build);
                buf.writeBlockPos(m.pos);
                buf.writeVarInt(m.facing.get2DDataValue());
                JumpProfiles.Params p = m.params;
                buf.writeVarInt(p.type().ordinal());
                buf.writeVarInt(p.length());
                buf.writeVarInt(p.width());
                buf.writeVarInt((int) Math.round(p.height() * 16));
                buf.writeVarInt(p.lip());
                buf.writeVarInt(p.deck());
                buf.writeVarInt(p.landing());
            },
            buf -> {
                boolean build = buf.readBoolean();
                BlockPos pos = buf.readBlockPos();
                Direction facing = Direction.from2DDataValue(buf.readVarInt());
                JumpProfiles.Type[] types = JumpProfiles.Type.values();
                int type = buf.readVarInt();
                JumpProfiles.Params params = new JumpProfiles.Params(types[Math.floorMod(type, types.length)],
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt() / 16.0, buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
                return new JumpBuildPayload(build, pos, facing, params);
            });

    /** Only remembers the settings in the tool (the screen was closed, or opened without a block). */
    public static JumpBuildPayload remember(JumpProfiles.Params params) {
        return new JumpBuildPayload(false, BlockPos.ZERO, Direction.NORTH, params);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(JumpBuildPayload message, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        var tool = player.getMainHandItem();
        if (!ShapeToolItem.usable(tool) || ShapeToolItem.mode(tool) != ShapeMode.JUMP_BUILD) {
            return;
        }
        if (message.build) {
            JumpBuilder.build(player, tool, message.pos, message.facing, message.params);
        } else {
            JumpBuilder.store(tool, message.params);
        }
    }
}
