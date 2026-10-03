package com.descentmtb.network;
import com.descentmtb.DescentMtb;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
/** Release the short collision pose when the visual get-up finishes; retain crash damage grace. */
public record RagdollRecoveryPayload() implements CustomPacketPayload {
 public static final Type<RagdollRecoveryPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID,"ragdoll_recover"));
 public static final StreamCodec<FriendlyByteBuf,RagdollRecoveryPayload> CODEC=StreamCodec.unit(new RagdollRecoveryPayload());
 public Type<? extends CustomPacketPayload> type(){return TYPE;}
 public static void handle(RagdollRecoveryPayload m,IPayloadContext ctx){if(ctx.player() instanceof ServerPlayer p)Ragdolls.recover(p);}
}
