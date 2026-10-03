package com.descentmtb.network;
import com.descentmtb.DescentMtb;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
/** Bounded ghost geometry only; the server retains the actual editable design. */
public record TrailPreviewPayload(List<Cell> cells) implements CustomPacketPayload {
 public record Cell(BlockPos pos,float nw,float ne,float sw,float se,boolean remove){}
 public static java.util.function.Consumer<TrailPreviewPayload> clientHandler=m->{};
 public static final Type<TrailPreviewPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(DescentMtb.MODID,"trail_preview"));
 public static final StreamCodec<FriendlyByteBuf,TrailPreviewPayload> CODEC=StreamCodec.ofMember((m,b)->{b.writeVarInt(m.cells.size());for(Cell c:m.cells){b.writeBlockPos(c.pos);b.writeFloat(c.nw);b.writeFloat(c.ne);b.writeFloat(c.sw);b.writeFloat(c.se);b.writeBoolean(c.remove);}},b->{int n=b.readVarInt();if(n<0||n>32768)throw new IllegalArgumentException("preview length");var cells=new ArrayList<Cell>(n);for(int i=0;i<n;i++)cells.add(new Cell(b.readBlockPos(),b.readFloat(),b.readFloat(),b.readFloat(),b.readFloat(),b.readBoolean()));return new TrailPreviewPayload(cells);});
 public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
