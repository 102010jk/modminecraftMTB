package com.descentmtb.network;

import com.descentmtb.DescentMtb;
import com.descentmtb.trail.BlockEditor;
import com.descentmtb.trail.ShapeToolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server, from the block editor screen: give the block at {@code pos} these corner heights (sixteenths
 * above the block's frame, NW NE SW SE), the deck flag and optionally the off-hand material. Validated and applied by
 * {@link BlockEditor#apply}; the player must hold a Trail Shaper in the main hand.
 */
public record BlockEditPayload(BlockPos pos, int[] sixteenths, boolean deck, boolean offHandMaterial) implements CustomPacketPayload {
    public static final Type<BlockEditPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "block_edit"));

    public static final StreamCodec<FriendlyByteBuf, BlockEditPayload> CODEC = StreamCodec.ofMember(
            (m, buf) -> {
                buf.writeBlockPos(m.pos);
                for (int i = 0; i < 4; i++) {
                    buf.writeVarInt(m.sixteenths[i]);
                }
                buf.writeBoolean(m.deck);
                buf.writeBoolean(m.offHandMaterial);
            },
            buf -> new BlockEditPayload(buf.readBlockPos(),
                    new int[]{buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()},
                    buf.readBoolean(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(BlockEditPayload message, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && ShapeToolItem.usable(player.getMainHandItem())) {
            BlockEditor.apply(player, message.pos, message.sixteenths, message.deck, message.offHandMaterial);
        }
    }
}
