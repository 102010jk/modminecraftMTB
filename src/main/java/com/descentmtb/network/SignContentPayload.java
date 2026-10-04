package com.descentmtb.network;

import com.descentmtb.DescentMtb;
import com.descentmtb.trail.SignContent;
import com.descentmtb.trail.TrailSignEntity;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Player to server: the sign editor was saved. One packet carries the type and every field; the server
 * checks that the player may edit that sign and then stores it, which synchronises all viewers.
 */
public record SignContentPayload(BlockPos pos, SignContent content) implements CustomPacketPayload {
    public static final Type<SignContentPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID, "sign_content"));

    public static final StreamCodec<FriendlyByteBuf, SignContentPayload> CODEC = StreamCodec.ofMember(
            SignContentPayload::write, SignContentPayload::read);

    /** How far from the sign a player may still edit it (blocks). */
    public static final double REACH = 8;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void write(SignContentPayload message, FriendlyByteBuf buf) {
        SignContent c = message.content;
        buf.writeBlockPos(message.pos);
        buf.writeEnum(c.type());
        buf.writeUtf(c.name(), SignContent.NAME_MAX);
        buf.writeEnum(c.difficulty());
        buf.writeEnum(c.arrow());
        buf.writeEnum(c.warning());
        buf.writeUtf(c.text(), SignContent.TEXT_MAX);
        buf.writeByteArray(c.pixels());
    }

    private static SignContentPayload read(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        SignContent content = new SignContent(
                buf.readEnum(SignContent.Type.class),
                buf.readUtf(SignContent.NAME_MAX),
                buf.readEnum(SignContent.Difficulty.class),
                buf.readEnum(SignContent.Arrow.class),
                buf.readEnum(SignContent.Warning.class),
                buf.readUtf(SignContent.TEXT_MAX),
                buf.readByteArray(SignContent.PIXELS));
        return new SignContentPayload(pos, content);
    }

    static void handle(SignContentPayload message, IPayloadContext context) {
        apply(context.player(), message);
    }

    /**
     * Stores the content on the sign if the player may edit it: loaded block entity, allowed to build there
     * and within {@link #REACH} blocks. Returns whether the sign was changed.
     */
    public static boolean apply(Player player, SignContentPayload message) {
        var level = player.level();
        BlockPos pos = message.pos;
        if (!player.mayBuild() || !level.isLoaded(pos) || !level.mayInteract(player, pos)) {
            return false;
        }
        Vec3 centre = SableCompanion.INSTANCE.projectOutOfSubLevel(level, Vec3.atCenterOf(pos));
        if (player.position().distanceToSqr(centre) > REACH * REACH) {
            return false;
        }
        if (!(level.getBlockEntity(pos) instanceof TrailSignEntity sign)) {
            return false;
        }
        sign.setContent(message.content);
        return true;
    }
}
