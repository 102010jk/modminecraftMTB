package com.descentmtb.custom;

import com.descentmtb.entity.BikeType;
import com.descentmtb.item.MountainBikeItem;
import com.descentmtb.registry.ModComponents;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side rules of the bike work stand: who may put a bike on it, take it off and change its build. Every call
 * (block clicks and the workshop payloads alike) goes through here, so a modified client gets no more than a player.
 */
public final class BikeStands {
    /** How far from the stand (blocks) a player may still use it. */
    public static final double REACH = 6;

    /** Loaded stand with a block entity, the player allowed to build there and within reach. */
    public static BikeStandBlockEntity usable(Player player, BlockPos pos) {
        Level level = player.level();
        if (level.isClientSide || !player.mayBuild() || player.isSpectator() || !level.isLoaded(pos) || !level.mayInteract(player, pos)) {
            return null;
        }
        Vec3 centre = SableCompanion.INSTANCE.projectOutOfSubLevel(level, Vec3.atCenterOf(pos));
        if (player.position().distanceToSqr(centre) > REACH * REACH) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof BikeStandBlockEntity stand ? stand : null;
    }

    /** Moves one bike from the stack onto an empty stand. Returns whether it was mounted. */
    public static boolean mount(Player player, BlockPos pos, ItemStack held) {
        BikeStandBlockEntity stand = usable(player, pos);
        if (stand == null || stand.hasBike() || !(held.getItem() instanceof MountainBikeItem bike) || bike.bikeType().ski()) {
            return false;   // skis never go on the bike stand
        }
        stand.setBike(held.copyWithCount(1));
        held.shrink(1);
        player.level().playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, .8f, 1.2f);
        return true;
    }

    /** Takes the bike off the stand into the player's inventory (the feet when it is full). Returns whether there was one. */
    public static boolean take(Player player, BlockPos pos) {
        BikeStandBlockEntity stand = usable(player, pos);
        if (stand == null || !stand.hasBike()) {
            return false;
        }
        ItemStack bike = stand.bike().copy();
        stand.setBike(ItemStack.EMPTY);
        if (!player.getInventory().add(bike)) {
            player.spawnAtLocation(bike);
        }
        player.level().playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, .8f, 1.2f);
        return true;
    }

    /**
     * Stores a build (clamped to what this bike type allows) on the bicycle of the stand. Free for now. Returns whether
     * it was stored. A motorbike is edited with {@link #applyMoto} instead.
     */
    public static boolean apply(Player player, BlockPos pos, BikeBuild build) {
        BikeStandBlockEntity stand = usable(player, pos);
        if (stand == null || !stand.hasBike() || build == null || stand.bikeType().motor()) {
            return false;
        }
        ItemStack bike = stand.bike().copy();
        BikeBuild clean = build.sanitized(stand.bikeType() == BikeType.ENDURO);
        if (clean.equals(MountainBikeItem.buildOf(bike))) {
            return true;   // nothing to change
        }
        bike.set(ModComponents.BIKE_BUILD.get(), clean);
        stand.setBike(bike);
        player.level().playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, .5f, 1.1f);
        return true;
    }

    /**
     * Stores paint and tuning on the motorbike of the stand. The build is already clamped by its constructor. Free for
     * now. Returns whether it was stored (false: no usable stand, no bike, or the bike is a bicycle).
     */
    public static boolean applyMoto(Player player, BlockPos pos, MotoBuild moto) {
        BikeStandBlockEntity stand = usable(player, pos);
        if (stand == null || !stand.hasBike() || moto == null || !stand.bikeType().motor()) {
            return false;
        }
        if (moto.equals(stand.moto())) {
            return true;   // nothing to change
        }
        ItemStack bike = stand.bike().copy();
        bike.set(ModComponents.MOTO_BUILD.get(), moto);
        stand.setBike(bike);
        player.level().playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, .5f, 1.1f);
        return true;
    }

    private BikeStands() {}
}
