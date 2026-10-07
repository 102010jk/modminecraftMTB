package com.descentmtb.custom;

import com.descentmtb.entity.BikeType;
import com.descentmtb.item.MountainBikeItem;
import com.descentmtb.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The bike on a {@link BikeStandBlock}: one bike item (with its {@link BikeBuild} and pressures), saved with the
 * block and synchronised to every viewer so a renderer can draw the bike on the stand.
 */
public final class BikeStandBlockEntity extends BlockEntity {
    private static final String BIKE = "Bike";

    private ItemStack bike = ItemStack.EMPTY;

    public BikeStandBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.BIKE_STAND_BE.get(), pos, state);
    }

    /** The bike item on the stand (a copy is NOT made: do not modify it, use {@link #setBike}); EMPTY when none. */
    public ItemStack bike() {
        return bike;
    }

    public boolean hasBike() {
        return !bike.isEmpty();
    }

    /** Type of the bike on the stand (ENDURO when empty). */
    public BikeType bikeType() {
        return bike.getItem() instanceof MountainBikeItem item ? item.bikeType() : BikeType.ENDURO;
    }

    /** Build of the bike on the stand: the stock look of its type when the stand is empty or the stack carries none. */
    public BikeBuild build() {
        return bike.getItem() instanceof MountainBikeItem ? MountainBikeItem.buildOf(bike) : BikeBuild.ENDURO_DEFAULT;
    }

    /** Paint and tuning of the motorbike on the stand: stock for a bicycle or an empty stand. */
    public MotoBuild moto() {
        return bike.getItem() instanceof MountainBikeItem ? MountainBikeItem.motoOf(bike) : MotoBuild.DEFAULT;
    }

    /** Server: replaces the bike (EMPTY to clear) and tells every viewer. */
    public void setBike(ItemStack stack) {
        bike = stack.getItem() instanceof MountainBikeItem ? stack.copyWithCount(1) : ItemStack.EMPTY;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // NeoForge ignores an empty update tag. Always send a state, including when taking the bike off.
        tag.putBoolean("HasBike", !bike.isEmpty());
        if (!bike.isEmpty()) {
            tag.put(BIKE, bike.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ItemStack loaded = tag.contains(BIKE, Tag.TAG_COMPOUND)
                ? ItemStack.parse(registries, tag.getCompound(BIKE)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        bike = loaded.getItem() instanceof MountainBikeItem ? loaded : ItemStack.EMPTY;
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
