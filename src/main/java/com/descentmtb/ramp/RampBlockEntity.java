package com.descentmtb.ramp;

import com.descentmtb.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Stores the copycat material of a {@link RampBlock} and syncs it to clients. */
public class RampBlockEntity extends BlockEntity {
    private BlockState material = Blocks.COARSE_DIRT.defaultBlockState();
    /** True when the material item was taken from a survival player and must drop again on break. */
    private boolean consumed;

    public RampBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlocks.RAMP_BE.get(), pos, state);
    }
    protected RampBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }

    /** What the baked model needs to know about this block (material, and for trail surfaces the corners). */
    public static final net.neoforged.neoforge.client.model.data.ModelProperty<ShapeKey> SHAPE =
            new net.neoforged.neoforge.client.model.data.ModelProperty<>();

    private ShapeKey shapeKey;

    protected ShapeKey buildShapeKey() { return ShapeKey.ramp(material); }

    /** Drops the cached key; call after anything that changes the shape or the material. */
    protected void shapeChanged() {
        shapeKey = null;
        if (level != null && level.isClientSide) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);   // 8 = re-mesh on the main thread
        }
    }

    @Override
    public net.neoforged.neoforge.client.model.data.ModelData getModelData() {
        if (shapeKey == null) shapeKey = buildShapeKey();
        return net.neoforged.neoforge.client.model.data.ModelData.builder().with(SHAPE, shapeKey).build();
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        super.onDataPacket(net, pkt, registries);
        shapeChanged();
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        shapeChanged();
    }

    public BlockState getMaterial() { return material; }

    public boolean isConsumed() { return consumed; }

    public void setMaterial(BlockState mat, boolean consumed) {
        this.material = mat;
        this.consumed = consumed;
        shapeKey = null;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void setMaterial(BlockState mat) { setMaterial(mat, this.consumed); }

    void setConsumedQuiet(boolean consumed) {
        this.consumed = consumed;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("material", NbtUtils.writeBlockState(material));
        tag.putBoolean("consumed", consumed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("material")) {
            BlockState s = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("material"));
            material = s.isAir() ? Blocks.COARSE_DIRT.defaultBlockState() : s;
        }
        consumed = tag.getBoolean("consumed");
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
