package com.descentmtb.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.descentmtb.registry.ModItems;

/** C2S requests a film; S2C acknowledges consumption, avoiding client-only infinite tear-offs. */
public record TearOffPayload() implements CustomPacketPayload {
    public static final Type<TearOffPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("descentmtb","tear_off"));
    public static final StreamCodec<FriendlyByteBuf,TearOffPayload> CODEC=StreamCodec.unit(new TearOffPayload());
    public static Runnable client=()->{};
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void handle(TearOffPayload m,IPayloadContext ctx){
        if(!(ctx.player() instanceof ServerPlayer p)||p.getCooldowns().isOnCooldown(ModItems.TEAR_OFF.get()))return;
        for(int i=0;i<p.getInventory().getContainerSize();i++){
            var stack=p.getInventory().getItem(i);if(!stack.is(ModItems.TEAR_OFF.get()))continue;
            if(!p.getAbilities().instabuild)stack.shrink(1);p.getCooldowns().addCooldown(ModItems.TEAR_OFF.get(),12);cleaned(p);return;
        }
    }
    public static void cleaned(ServerPlayer p){PacketDistributor.sendToPlayer(p,new TearOffPayload());}
}
