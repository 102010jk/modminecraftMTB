package com.descentmtb.trail;

import com.descentmtb.ramp.RampBlockEntity;
import com.descentmtb.registry.ModBlocks;
import com.descentmtb.tape.BarrierPostEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.*;

/**
 * Bounded, preflighted world edit with undo. Every edit of the Trail Shaper goes through here, so this is where
 * the rules live:
 *
 * <ul>
 *   <li><b>Protection.</b> Nothing is touched unless the whole plan is allowed: the player may build there, the
 *       blocks are loaded, none is unbreakable, none is a fluid source, none is a trail sign or tape post that
 *       would be deleted, and no block holds an inventory or other foreign data. NeoForge's break and place events
 *       are posted for the player first, so claim mods can veto an edit.</li>
 *   <li><b>Economy.</b> In survival the edit is paid for with trail dirt / trail deck, see {@link TrailEconomy}.</li>
 *   <li><b>Materials.</b> A material the player paid for (right-click on a ramp with a block) is refunded exactly
 *       once: {@link RampBlockEntity#isConsumed()} survives an edit only on the very block it was paid on, the
 *       break handler of the ramp stays silent while the edit runs ({@link #isBeingEdited}), and a material that is
 *       lost in an edit is handed to the player at once. Undo never brings back a paid flag that was refunded.</li>
 *   <li><b>Undo.</b> The last {@link TrailConfig#UNDO_DEPTH} edits of a player are kept, but at most twice
 *       {@link TrailConfig#MAX_BLOCKS} changed blocks in total.</li>
 * </ul>
 */
public final class TrailEdit {
    /** One block of a plan. A null {@code heights} keeps the shape the block entity already has. */
    public record Change(BlockState state, CompoundTag tag, double[] heights, BlockState material, boolean deck) {
        public static Change block(BlockState s) { return new Change(s,null,null,null,false); }
    }

    /** An edit that is refused; {@link #key()} is a language key and the arguments fill its placeholders. */
    public static final class Rejected extends IllegalArgumentException {
        private final Object[] args;

        public Rejected(String key, Object... args) {
            super(key);
            this.args = args;
        }

        public String key() {
            return getMessage();
        }

        public Object[] args() {
            return args;
        }
    }

    private record Saved(BlockState before, CompoundTag beforeTag, BlockState after, CompoundTag afterTag) {}
    private record Undo(ResourceKey<Level> dimension, Map<BlockPos,Saved> blocks, boolean charged) {}

    private static final Map<UUID,ArrayDeque<Undo>> HISTORY = new HashMap<>();
    /** Positions whose blocks this class is replacing right now (server thread only). */
    private static final Set<Long> EDITING = new HashSet<>();

    public static void clearSession() { HISTORY.clear(); RampTuning.clearSession(); ShapeClipboard.clearSession(); TrailRecords.clearSession(); }

    /** Forgets everything the tools remember about one player (called when the player logs out). */
    public static void forget(UUID player) {
        HISTORY.remove(player);
        RampTuning.forget(player);
        ShapeClipboard.forget(player);
    }

    /** True while an edit or an undo replaces the block at {@code pos}: a ramp must not refund its material then. */
    public static boolean isBeingEdited(BlockPos pos) {
        return !EDITING.isEmpty() && EDITING.contains(pos.asLong());
    }

    /** Creative players and operators may build berms and downhill lines and copy blocks; survival players may not. */
    public static boolean mayBulkEdit(Player player) {
        return player.getAbilities().instabuild || player.hasPermissions(2);
    }

    /** The message to show a player for a refused edit (any {@link IllegalArgumentException} thrown by the tools). */
    public static Component describe(IllegalArgumentException e) {
        return e instanceof Rejected r ? Component.translatable(r.key(), r.args()) : Component.literal(String.valueOf(e.getMessage()));
    }

    private static CompoundTag data(Level l, BlockPos p) {
        var be = l.getBlockEntity(p); return be == null ? null : be.saveWithoutMetadata(l.registryAccess());
    }

    /** Applies a plan; in survival it is paid for. */
    public static int apply(Level level, Player player, Map<BlockPos,Change> plan) {
        return apply(level, player, plan, !player.getAbilities().instabuild);
    }

    /**
     * Applies a plan as one undoable step.
     *
     * @param charge true to take the price from the inventory (and refund removed blocks); builders that only
     *               creative players and operators may use pass false
     * @return the number of blocks changed
     * @throws Rejected when the plan is not allowed; nothing has been changed then
     */
    public static int apply(Level level, Player player, Map<BlockPos,Change> plan, boolean charge) {
        if (plan.size() > TrailConfig.MAX_BLOCKS.get()) throw new Rejected("descentmtb.edit.too_big", TrailConfig.MAX_BLOCKS.get());
        preflight(level, player, plan);
        Map<Item,Integer> price = charge ? TrailEconomy.balance(level, plan) : Map.of();
        Item missing = TrailEconomy.missing(player, price);
        if (missing != null) {
            throw new Rejected("descentmtb.edit.need_items", price.get(missing), new ItemStack(missing).getHoverName());
        }

        Map<BlockPos,Saved> undo = new LinkedHashMap<>();
        Map<Block,Integer> lost = new HashMap<>();
        plan.keySet().forEach(p -> EDITING.add(p.asLong()));
        try {
            plan.forEach((pos, change) -> {
                BlockState old = level.getBlockState(pos); CompoundTag oldTag=data(level,pos);
                Block paid = level.getBlockEntity(pos) instanceof RampBlockEntity r && r.isConsumed() ? r.getMaterial().getBlock() : null;
                level.setBlock(pos,change.state,3);
                var be=level.getBlockEntity(pos);
                if(change.tag!=null && be!=null) { be.loadWithComponents(change.tag,level.registryAccess()); be.setChanged(); }
                if(be instanceof TrailSurfaceEntity shaped && change.heights!=null) shaped.setShape(change.heights,change.deck);
                if(be instanceof RampBlockEntity ramp) {
                    if(change.material!=null) ramp.setMaterial(change.material);   // keeps the refund flag
                    // a paid material stays paid only on the block it was paid on; copies of it are never paid
                    boolean keep = paid != null && ramp.getMaterial().getBlock() == paid;
                    if (ramp.isConsumed() != keep) ramp.setConsumedQuiet(keep);
                    if (paid != null && !keep) lost.merge(paid, 1, Integer::sum);
                } else if (paid != null) {
                    lost.merge(paid, 1, Integer::sum);
                }
                if(be!=null) level.sendBlockUpdated(pos,change.state,change.state,3);
                undo.put(pos.immutable(),new Saved(old,oldTag,level.getBlockState(pos),data(level,pos)));
            });
        } finally {
            EDITING.clear();
        }
        lost.forEach((block, count) -> TrailEconomy.give(player, block.asItem(), count));
        TrailEconomy.settle(player, price);

        var history=HISTORY.computeIfAbsent(player.getUUID(),id->new ArrayDeque<>());
        history.addLast(new Undo(level.dimension(),undo,charge));
        trim(history);
        return undo.size();
    }

    /** Everything that can refuse a plan, checked before the first block changes. */
    private static void preflight(Level level, Player player, Map<BlockPos,Change> plan) {
        for (var entry : plan.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState replacement = entry.getValue().state();
            if(!player.mayBuild()||!level.mayInteract(player,pos)) throw new Rejected("descentmtb.edit.not_allowed");
            if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos)) throw new Rejected("descentmtb.edit.not_loaded");
            BlockState old = level.getBlockState(pos);
            // Terrain planners may read a nearby ramp as a boundary, but cannot turn it into terrain.
            if(old.is(ModBlocks.RAMP.get()) && !replacement.is(ModBlocks.RAMP.get())) throw new Rejected("descentmtb.edit.old_ramp");
            if (old.getDestroySpeed(level, pos) < 0) throw new Rejected("descentmtb.edit.unbreakable");
            if (old.getFluidState().isSource()) throw new Rejected("descentmtb.edit.fluid");
            var be = level.getBlockEntity(pos);
            if (be instanceof TrailSignEntity || be instanceof BarrierPostEntity) {
                if (!replacement.is(old.getBlock())) throw new Rejected("descentmtb.edit.protected");
            } else if (be != null && !(be instanceof RampBlockEntity)) {
                throw new Rejected("descentmtb.edit.has_data");
            }
        }
        askOthers(level, player, plan);
    }

    /** Lets other mods (claims, protection) veto the edit: break events for what is there, place events for what comes. */
    private static void askOthers(Level level, Player player, Map<BlockPos,Change> plan) {
        if (!(player instanceof ServerPlayer)) {
            return;
        }
        for (var entry : plan.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState old = level.getBlockState(pos);
            if (!old.isAir() && NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, old, player)).isCanceled()) {
                throw new Rejected("descentmtb.edit.denied");
            }
            if (!entry.getValue().state().isAir()) {
                var snapshot = BlockSnapshot.create(level.dimension(), level, pos);
                if (NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snapshot, level.getBlockState(pos.below()), player)).isCanceled()) {
                    throw new Rejected("descentmtb.edit.denied");
                }
            }
        }
    }

    /** Keeps at most {@link TrailConfig#UNDO_DEPTH} steps and {@code 2 x MAX_BLOCKS} changed blocks (the newest step always stays). */
    private static void trim(ArrayDeque<Undo> history) {
        int budget = 2 * TrailConfig.MAX_BLOCKS.get();
        int stored = history.stream().mapToInt(u -> u.blocks.size()).sum();
        while (history.size() > 1 && (history.size() > TrailConfig.UNDO_DEPTH.get() || stored > budget)) {
            stored -= history.removeFirst().blocks.size();
        }
    }

    /** Undoes the player's last edit where the blocks are still as the edit left them. @return the blocks restored */
    public static int undo(Level level, Player player) {
        var h=HISTORY.get(player.getUUID()); if(h==null||h.isEmpty()) return 0;
        Undo edit=h.peekLast(); if(edit.dimension!=level.dimension()) throw new Rejected("descentmtb.edit.undo_dimension");
        if(!player.mayBuild()||edit.blocks.keySet().stream().anyMatch(p->!level.isLoaded(p)||!level.mayInteract(player,p))) throw new Rejected("descentmtb.edit.undo_not_loaded");

        Map<BlockPos,Saved> restorable = new LinkedHashMap<>();
        Map<Item,Integer> price = new HashMap<>();
        for (var e : edit.blocks.entrySet()) {
            BlockPos p = e.getKey(); Saved s = e.getValue();
            if (!level.getBlockState(p).equals(s.after) || !Objects.equals(data(level,p),s.afterTag)) continue;
            restorable.put(p, s);
            if (edit.charged) TrailEconomy.undoStep(price, s.before, s.beforeTag, s.after, s.afterTag);
        }
        Item missing = TrailEconomy.missing(player, price);
        if (missing != null) throw new Rejected("descentmtb.edit.undo_need_items", price.get(missing), new ItemStack(missing).getHoverName());

        h.removeLast();
        restorable.keySet().forEach(p -> EDITING.add(p.asLong()));
        try {
            restorable.forEach((p, s) -> {
                level.setBlock(p,s.before,3);
                var be=level.getBlockEntity(p);
                if(be!=null&&s.beforeTag!=null){
                    be.loadWithComponents(unpaid(s),level.registryAccess());be.setChanged();level.sendBlockUpdated(p,s.before,s.before,3);
                }
            });
        } finally {
            EDITING.clear();
        }
        TrailEconomy.settle(player, price);
        return restorable.size();
    }

    /**
     * The saved block entity data to restore. A paid material is only brought back as paid if the edit kept it paid;
     * if the edit refunded it, the player already has the item and the restored block is a free copy.
     */
    private static CompoundTag unpaid(Saved s) {
        CompoundTag tag = s.beforeTag.copy();
        if (tag.getBoolean("consumed") && (s.afterTag == null || !s.afterTag.getBoolean("consumed"))) {
            tag.putBoolean("consumed", false);
        }
        return tag;
    }

    private TrailEdit() {}
}
