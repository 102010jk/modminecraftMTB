package com.descentmtb.network;
import com.descentmtb.DescentMtb;
import com.descentmtb.trail.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
public record TrailActionPayload(int action,CompoundTag settings,String name,int dx,int dy,int dz) implements CustomPacketPayload {
 public static final int CONFIGURE=0,CONFIRM=1,CANCEL=2,ROTATE=3,MOVE=4,UNDO=5,SAVE=6,LOAD=7,TUNE_SUB=8;
 public static final Type<TrailActionPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID,"trail_action"));
 public static final StreamCodec<FriendlyByteBuf,TrailActionPayload> CODEC=StreamCodec.ofMember((m,b)->{b.writeVarInt(m.action);b.writeNbt(m.settings);b.writeUtf(m.name,24);b.writeInt(m.dx);b.writeInt(m.dy);b.writeInt(m.dz);},b->new TrailActionPayload(b.readVarInt(),b.readNbt(),b.readUtf(24),b.readInt(),b.readInt(),b.readInt()));
 public Type<? extends CustomPacketPayload> type(){return TYPE;}
 public static void handle(TrailActionPayload m,IPayloadContext ctx){if(ctx.player() instanceof ServerPlayer p){
  if(m.action()==TUNE_SUB&&p.getMainHandItem().getItem() instanceof com.descentmtb.ramp.TrailToolItem){
   if(m.dx()==1||m.dx()==-1)com.descentmtb.ramp.TrailToolItem.cycle(p,p.getMainHandItem(),m.dx());return;
  }
  TrailDraft.action(p,m);
 }}
}
