package com.descentmtb.trail;

import com.descentmtb.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The data of one trail sign: its {@link SignContent}, saved as NBT and synchronised to every viewer.
 * On the client, loaded signs also register themselves in {@link TrailSignRegistry} so the trail timer can
 * find START and FINISH signs near the rider.
 */
public final class TrailSignEntity extends BlockEntity {
    private static final String TYPE = "Type";
    private static final String NAME = "Name";
    private static final String DIFFICULTY = "Difficulty";
    private static final String ARROW = "Arrow";
    private static final String WARNING = "Warning";
    private static final String TEXT = "Text";
    private static final String PIXELS = "Pixels";

    private SignContent content = SignContent.blank();
    private com.descentmtb.map.TrailTrack track;
    private net.minecraft.resources.ResourceLocation trackDimension=net.minecraft.resources.ResourceLocation.withDefaultNamespace("overworld");
    public com.descentmtb.map.TrailTrack track(){return track;}
    public net.minecraft.resources.ResourceLocation trackDimension(){return trackDimension;}
    public void setTrack(com.descentmtb.map.TrailTrack track,net.minecraft.resources.ResourceLocation dimension) {
        this.track=new com.descentmtb.map.TrailTrack(com.descentmtb.map.TrackGeometry.simplifyTo(track.pts(),.15,512));
        this.trackDimension=dimension;setContent(content);
    }

    public TrailSignEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SIGN_BE.get(), pos, state);
    }

    public SignContent content() {
        return content;
    }

    /** Replaces the content and tells every viewer. */
    public void setContent(SignContent newContent) {
        content = newContent;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            TrailSignRegistry.add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        TrailSignRegistry.remove(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(TYPE, content.type().name());
        tag.putString(NAME, content.name());
        tag.putString(DIFFICULTY, content.difficulty().name());
        tag.putString(ARROW, content.arrow().name());
        tag.putString(WARNING, content.warning().name());
        tag.putString(TEXT, content.text());
        tag.putByteArray(PIXELS, content.pixels());
        if(track!=null){tag.putIntArray("GpsTrack",track.pts());tag.putString("TrackDimension",trackDimension.toString());}
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        int[] points=tag.getIntArray("GpsTrack");
        track=com.descentmtb.map.TrackGeometry.valid(points)&&points.length>=6?new com.descentmtb.map.TrailTrack(com.descentmtb.map.TrackGeometry.simplifyTo(points,.15,512)):null;
        trackDimension=java.util.Optional.ofNullable(net.minecraft.resources.ResourceLocation.tryParse(tag.getString("TrackDimension"))).orElse(net.minecraft.resources.ResourceLocation.withDefaultNamespace("overworld"));
        byte[] pixels = tag.getByteArray(PIXELS);
        if (!tag.contains(TYPE, Tag.TAG_STRING)) {
            // saved before sign types existed: it was only pixel art
            content = pixels.length == SignContent.PIXELS ? SignContent.legacy(pixels) : SignContent.blank();
            return;
        }
        content = new SignContent(
                SignContent.parse(SignContent.Type.class, tag.getString(TYPE), SignContent.Type.TRAIL),
                tag.getString(NAME),
                SignContent.parse(SignContent.Difficulty.class, tag.getString(DIFFICULTY), SignContent.Difficulty.BLUE),
                SignContent.parse(SignContent.Arrow.class, tag.getString(ARROW), SignContent.Arrow.NONE),
                SignContent.parse(SignContent.Warning.class, tag.getString(WARNING), SignContent.Warning.CAUTION),
                tag.getString(TEXT),
                pixels);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
