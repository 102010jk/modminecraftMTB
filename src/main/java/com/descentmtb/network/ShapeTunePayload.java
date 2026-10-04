package com.descentmtb.network;

import com.descentmtb.trail.ShapeToolItem;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Bounded precision edits; all world changes and item settings are owned by the server. */
public record ShapeTunePayload(boolean select,int value,BlockPos pos,Vec3 hit) implements CustomPacketPayload {
    public static final Type<ShapeTunePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("descentmtb","shape_tune"));
    public static final StreamCodec<FriendlyByteBuf,ShapeTunePayload> CODEC=StreamCodec.ofMember((m,b)->{
        b.writeBoolean(m.select);b.writeInt(m.value);b.writeBlockPos(m.pos);b.writeDouble(m.hit.x);b.writeDouble(m.hit.y);b.writeDouble(m.hit.z);
    },b->new ShapeTunePayload(b.readBoolean(),b.readInt(),b.readBlockPos(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble())));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    static void handle(ShapeTunePayload m,IPayloadContext c) {
        if(!(c.player() instanceof ServerPlayer p)||!ShapeToolItem.usable(p.getMainHandItem()))return;
        if(m.select){ShapeToolItem.mode(p.getMainHandItem(),m.value);return;}
        if(!Double.isFinite(m.hit.x)||!Double.isFinite(m.hit.y)||!Double.isFinite(m.hit.z)||Math.abs(m.value)!=1
                ||m.hit.distanceTo(Vec3.atCenterOf(m.pos))>2||!p.level().isLoaded(m.pos))return;
        Vec3 global=SableCompanion.INSTANCE.projectOutOfSubLevel(p.level(),m.hit);
        if(p.distanceToSqr(global)>64)return;
        ShapeToolItem.tune(p,m.pos,m.hit,m.value);
    }
}
