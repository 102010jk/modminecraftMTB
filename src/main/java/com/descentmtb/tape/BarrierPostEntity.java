package com.descentmtb.tape;

import com.descentmtb.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The tape links of one {@link BarrierPostBlock}: up to {@link #MAX_LINKS} positions of the posts it is
 * strung to. A link is stored on both posts; only the post with the smaller position draws it.
 */
public final class BarrierPostEntity extends BlockEntity {
    /** A post holds at most this many tapes, so a fence can run through it. */
    public static final int MAX_LINKS = 2;
    private static final String LINKS = "Links";

    private final List<BlockPos> links = new ArrayList<>(MAX_LINKS);

    public BarrierPostEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.POST_BE.get(), pos, state);
    }

    public List<BlockPos> links() {
        return Collections.unmodifiableList(links);
    }

    public boolean isLinkedTo(BlockPos other) {
        return links.contains(other);
    }

    public boolean hasFreeLink() {
        return links.size() < MAX_LINKS;
    }

    /** Adds a link; false if it exists already or the post is full. */
    public boolean addLink(BlockPos other) {
        if (!hasFreeLink() || links.contains(other) || other.equals(worldPosition)) {
            return false;
        }
        links.add(other.immutable());
        changed();
        return true;
    }

    public void removeLink(BlockPos other) {
        if (links.remove(other)) {
            changed();
        }
    }

    /** Forgets links to posts that are gone (their chunk was unloaded when they were broken). */
    public void pruneStale() {
        if (level != null && links.removeIf(p -> level.isLoaded(p) && !level.getBlockState(p).is(ModBlocks.BARRIER_POST.get()))) {
            changed();
        }
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLongArray(LINKS, links.stream().mapToLong(BlockPos::asLong).toArray());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        links.clear();
        for (long packed : tag.getLongArray(LINKS)) {
            if (links.size() < MAX_LINKS) {
                links.add(BlockPos.of(packed));
            }
        }
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
