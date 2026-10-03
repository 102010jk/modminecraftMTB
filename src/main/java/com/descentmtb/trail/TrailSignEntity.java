package com.descentmtb.trail;
import com.descentmtb.registry.ModBlocks;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public final class TrailSignEntity extends BlockEntity {
 private byte[] pixels=new byte[256];public byte[] pixels(){return pixels.clone();}
 public TrailSignEntity(BlockPos p,BlockState s){super(ModBlocks.SIGN_BE.get(),p,s);}
 public void setPixels(byte[] p){if(p.length!=256)throw new IllegalArgumentException("canvas size");pixels=p.clone();for(int i=0;i<256;i++)pixels[i]&=15;setChanged();if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
 @Override protected void saveAdditional(CompoundTag t,HolderLookup.Provider r){super.saveAdditional(t,r);t.putByteArray("Pixels",pixels);}
 @Override protected void loadAdditional(CompoundTag t,HolderLookup.Provider r){super.loadAdditional(t,r);byte[] p=t.getByteArray("Pixels");if(p.length==256){pixels=p;for(int i=0;i<256;i++)pixels[i]&=15;}}
 @Override public CompoundTag getUpdateTag(HolderLookup.Provider r){return saveWithoutMetadata(r);}
 @Override public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
