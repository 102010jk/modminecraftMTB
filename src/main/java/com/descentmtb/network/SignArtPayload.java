package com.descentmtb.network;
import com.descentmtb.DescentMtb;
import com.descentmtb.trail.TrailSignEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
/** Player sends one completed 256-byte canvas. Vanilla BE updates synchronize all viewers. */
public record SignArtPayload(BlockPos pos,byte[] pixels) implements CustomPacketPayload {
 public static final Type<SignArtPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID,"sign_art"));
 public static final StreamCodec<FriendlyByteBuf,SignArtPayload> CODEC=StreamCodec.ofMember((m,b)->{b.writeBlockPos(m.pos);b.writeByteArray(m.pixels);},b->new SignArtPayload(b.readBlockPos(),b.readByteArray(256)));
 public Type<? extends CustomPacketPayload> type(){return TYPE;}
 public static void handle(SignArtPayload m,IPayloadContext ctx){var p=ctx.player();if(m.pixels.length!=256||!p.mayBuild()||!p.level().mayInteract(p,m.pos)||p.position().distanceToSqr(dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.projectOutOfSubLevel(p.level(),net.minecraft.world.phys.Vec3.atCenterOf(m.pos)))>64)return;if(p.level().isLoaded(m.pos)&&p.level().getBlockEntity(m.pos) instanceof TrailSignEntity be)be.setPixels(m.pixels);}
}
