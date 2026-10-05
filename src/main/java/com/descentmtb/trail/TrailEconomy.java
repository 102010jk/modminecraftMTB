package com.descentmtb.trail;

import com.descentmtb.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * The survival price of shaping. Every block the Trail Shaper adds to the world costs one {@code trail_dirt}
 * (one {@code trail_deck} for the layers of a wooden deck) from the player's inventory, and every block it removes
 * gives one back. Terrain that is turned into a shaped copy of itself costs nothing, so reshaping a block without
 * making it taller is free and nothing can be created or destroyed for free by shaping, undoing and shaping again.
 *
 * <p>The price of an edit is the <em>balance</em>: for each item the number of units after the edit minus the number
 * before it, positive when the player pays and negative when the player is refunded.
 */
final class TrailEconomy {
    /** The item a block counts as, or null when it is air or something the shaper may overwrite for free (plants, water). */
    static Item unit(BlockState state, boolean deck) {
        if (state.is(ModBlocks.TRAIL_SURFACE.get())) {
            return deck ? ModBlocks.TRAIL_DECK.get() : ModBlocks.TRAIL_DIRT.get();
        }
        return !state.isAir() && !state.canBeReplaced() ? ModBlocks.TRAIL_DIRT.get() : null;
    }

    /** The unit of the block that is in the world now. */
    private static Item unitInWorld(Level level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        return unit(level.getBlockState(pos), entity instanceof TrailSurfaceEntity shaped && shaped.deck());
    }

    /** What the plan costs ({@code > 0}) or refunds ({@code < 0}) per item. */
    static Map<Item, Integer> balance(Level level, Map<BlockPos, TrailEdit.Change> plan) {
        Map<Item, Integer> net = new HashMap<>();
        plan.forEach((pos, change) -> {
            Item before = unitInWorld(level, pos);
            // a change without new corner heights keeps the block entity, so a surface stays what it was
            Item after = change.state().is(ModBlocks.TRAIL_SURFACE.get()) && change.heights() == null
                    ? before : unit(change.state(), change.deck());
            move(net, before, after);
        });
        return net;
    }

    /** The balance of putting a block described by (state, tag) back where {@code current} stands. */
    static void undoStep(Map<Item, Integer> net, BlockState restored, CompoundTag restoredTag,
                         BlockState current, CompoundTag currentTag) {
        move(net, unit(current, currentTag != null && currentTag.getBoolean("Deck")),
                unit(restored, restoredTag != null && restoredTag.getBoolean("Deck")));
    }

    private static void move(Map<Item, Integer> net, Item before, Item after) {
        if (before == after) {
            return;
        }
        if (after != null) {
            net.merge(after, 1, Integer::sum);
        }
        if (before != null) {
            net.merge(before, -1, Integer::sum);
        }
    }

    /** The first item the player cannot pay, or null when the balance is affordable. */
    static Item missing(Player player, Map<Item, Integer> net) {
        for (var entry : net.entrySet()) {
            if (entry.getValue() > 0 && player.getInventory().countItem(entry.getKey()) < entry.getValue()) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** Takes what the balance costs from the inventory and hands out what it refunds. */
    static void settle(Player player, Map<Item, Integer> net) {
        net.forEach((item, count) -> {
            if (count > 0) {
                take(player, item, count);
            } else if (count < 0) {
                give(player, item, -count);
            }
        });
    }

    private static void take(Player player, Item item, int count) {
        Inventory inventory = player.getInventory();
        int left = count;
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
        inventory.setChanged();
    }

    /** Puts items into the inventory, dropping what does not fit at the player's feet. */
    static void give(Player player, Item item, int count) {
        for (int left = count; left > 0; ) {
            ItemStack stack = new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize()));
            left -= stack.getCount();
            if (!player.getInventory().add(stack) && !stack.isEmpty()) {
                player.drop(stack, false);
            }
        }
    }

    private TrailEconomy() {}
}
