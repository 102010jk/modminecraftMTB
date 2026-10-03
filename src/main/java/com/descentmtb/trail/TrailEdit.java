package com.descentmtb.trail;

import com.descentmtb.ramp.RampBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Bounded, preflighted world edit with undo. No containers are overwritten or cloned. */
public final class TrailEdit {
    public record Change(BlockState state, CompoundTag tag, double[] heights, BlockState material, boolean deck) {
        public static Change block(BlockState s) { return new Change(s,null,null,null,false); }
    }
    private record Saved(BlockState before, CompoundTag beforeTag, BlockState after, CompoundTag afterTag) {}
    private record Undo(ResourceKey<Level> dimension, Map<BlockPos,Saved> blocks) {}
    private static final Map<UUID,ArrayDeque<Undo>> HISTORY = new HashMap<>();
    public static void clearSession() { HISTORY.clear(); }
    private static CompoundTag data(Level l, BlockPos p) {
        var be = l.getBlockEntity(p); return be == null ? null : be.saveWithoutMetadata(l.registryAccess());
    }
    public static int apply(Level level, Player player, Map<BlockPos,Change> plan) {
        if (plan.size() > TrailConfig.MAX_BLOCKS.get()) throw new IllegalArgumentException("Úprava je příliš velká");
        for (BlockPos pos : plan.keySet()) {
            if(!player.mayBuild()||!level.mayInteract(player,pos))throw new IllegalArgumentException("V tomto místě nelze stavět");
            if (!level.isLoaded(pos) || pos.getY()<level.getMinBuildHeight() || pos.getY()>=level.getMaxBuildHeight())
                throw new IllegalArgumentException("Úsek není načtený nebo leží mimo výšku světa");
            var be = level.getBlockEntity(pos);
            if (be != null && !(be instanceof RampBlockEntity) && !(be instanceof TrailSignEntity)) throw new IllegalArgumentException("Úsek obsahuje chráněný blok s inventářem nebo daty");
        }
        Map<BlockPos,Saved> undo = new LinkedHashMap<>();
        plan.forEach((pos, change) -> {
            BlockState old = level.getBlockState(pos); CompoundTag oldTag=data(level,pos);
            level.setBlock(pos,change.state,3);
            var be=level.getBlockEntity(pos);
            if(change.tag!=null && be!=null) { be.loadWithComponents(change.tag,level.registryAccess()); be.setChanged(); }
            if(be instanceof TrailSurfaceEntity shaped && change.heights!=null) shaped.setShape(change.heights,change.deck);
            if(be instanceof RampBlockEntity ramp && change.material!=null) ramp.setMaterial(change.material,false);
            if(be!=null) level.sendBlockUpdated(pos,change.state,change.state,3);
            undo.put(pos.immutable(),new Saved(old,oldTag,level.getBlockState(pos),data(level,pos)));
        });
        var history=HISTORY.computeIfAbsent(player.getUUID(),id->new ArrayDeque<>());
        history.addLast(new Undo(level.dimension(),undo));
        while(history.size()>TrailConfig.UNDO_DEPTH.get()) history.removeFirst();
        return undo.size();
    }
    public static int undo(Level level, Player player) {
        var h=HISTORY.get(player.getUUID()); if(h==null||h.isEmpty()) return 0;
        Undo edit=h.peekLast(); if(edit.dimension!=level.dimension()) throw new IllegalArgumentException("Poslední úprava je v jiném světě");
        if(edit.blocks.keySet().stream().anyMatch(p->!level.isLoaded(p)||!player.mayBuild()||!level.mayInteract(player,p))) throw new IllegalArgumentException("Úsek pro vrácení není načtený");
        h.removeLast(); int count=0;
        for(var e:edit.blocks.entrySet()) {
            BlockPos p=e.getKey(); Saved s=e.getValue();
            if(!level.getBlockState(p).equals(s.after)||!Objects.equals(data(level,p),s.afterTag)) continue;
            level.setBlock(p,s.before,3);
            var be=level.getBlockEntity(p); if(be!=null&&s.beforeTag!=null){be.loadWithComponents(s.beforeTag,level.registryAccess());be.setChanged();level.sendBlockUpdated(p,s.before,s.before,3);}
            count++;
        }
        return count;
    }
    private TrailEdit() {}
}
