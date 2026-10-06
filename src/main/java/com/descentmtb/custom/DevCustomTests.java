package com.descentmtb.custom;

import com.descentmtb.DescentMtb;
import com.descentmtb.custom.BikeParts.Bell;
import com.descentmtb.custom.BikeParts.FrameShape;
import com.descentmtb.custom.BikeParts.StickerDesign;
import com.descentmtb.custom.BikeParts.Tube;
import com.descentmtb.entity.BikeType;
import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.item.MountainBikeItem;
import com.descentmtb.network.WorkshopApplyPayload;
import com.descentmtb.registry.ModBlocks;
import com.descentmtb.registry.ModComponents;
import com.descentmtb.registry.ModEntities;
import com.descentmtb.registry.ModItems;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Development-only server checks of the customisation backend: the build travelling item -> bike entity -> item, the
 * bike stand (mount, take, break, apply) with its validation, the payload codecs, the bell and the bike lights.
 * {@code /mtbdevcustom} runs them around the player; registered only with {@code -Ddescentmtb.autopilot=true}
 * (the autopilot sends the command and waits for {@link #PASSED}).
 */
public final class DevCustomTests {
    public static volatile boolean PASSED, FAILED;

    public static void register(RegisterCommandsEvent e) {
        if (!Boolean.getBoolean("descentmtb.autopilot")) {
            return;
        }
        e.getDispatcher().register(net.minecraft.commands.Commands.literal("mtbdevcustom").requires(s -> s.hasPermission(2))
                .executes(c -> run(c.getSource().getPlayerOrException())));
    }

    /**
     * Headless run for a dedicated dev server ({@code -Ddescentmtb.customtest.server=true}): runs everything with a fake
     * player high above the spawn once the server is up, logs the result and stops the server.
     */
    public static void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent e) {
        if (!Boolean.getBoolean("descentmtb.customtest.server")) {
            return;
        }
        ServerLevel level = e.getServer().overworld();
        var fake = rider(level);
        fake.setPos(0.5, 200, 0.5);
        level.getChunkAt(BlockPos.ZERO);
        level.getChunkAt(new BlockPos(16, 200, 16));
        for (int x = -16; x <= 32; x += 16) {
            for (int z = -16; z <= 32; z += 16) {
                level.getChunkAt(new BlockPos(x, 200, z));
            }
        }
        run(fake);
        DescentMtb.LOG.info("[customtest] headless result: {}", PASSED ? "PASSED" : "FAILED");
        e.getServer().halt(false);
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new IllegalStateException(what);
        }
        DescentMtb.LOG.info("[customtest] PASS: {}", what);
    }

    private static int run(ServerPlayer p) {
        PASSED = FAILED = false;
        ServerLevel level = p.serverLevel();
        ItemStack savedHand = p.getMainHandItem().copy();
        GameType savedMode = p.gameMode.getGameModeForPlayer();
        List<BlockPos> blocks = new ArrayList<>();
        List<net.minecraft.world.entity.Entity> entities = new ArrayList<>();
        BlockPos base = p.blockPosition().offset(2, 3, 2);
        try {
            p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            p.setShiftKeyDown(false);
            defaults();
            codecs(level);
            itemEntityItem(p, level, base.offset(0, 0, 4), entities);
            stand(p, level, base, blocks);
            bell(level, base.offset(8, 0, 0), entities);
            lights(level, base.offset(8, 0, 3), blocks, entities);
            PASSED = true;
            DescentMtb.LOG.info("[customtest] ALL PASSED");
        } catch (Exception | AssertionError e) {
            FAILED = true;
            DescentMtb.LOG.error("[customtest] FAIL", e);
        } finally {
            p.setShiftKeyDown(false);
            if (p.gameMode.getGameModeForPlayer() != savedMode) {
                p.setGameMode(savedMode);
            }
            for (var entity : entities) {
                entity.discard();
            }
            for (BlockPos pos : blocks) {
                level.removeBlock(pos, false);
            }
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(20))) {
                if (item.getItem().getItem() instanceof MountainBikeItem || item.getItem().is(ModBlocks.BIKE_STAND_ITEM.get())) {
                    item.discard();
                }
            }
            p.setItemInHand(InteractionHand.MAIN_HAND, savedHand);
        }
        return PASSED ? 1 : 0;
    }

    // ------------------------------------------------------------------ helpers

    private static final BikeBuild CUSTOM = BikeBuild.ENDURO_DEFAULT.with(b -> b.frameColor(0x123456).bell(Bell.RUBBER_DUCK)
            .frontLight(true).rearLight(true));

    private static ItemStack bike(BikeType type, BikeBuild build, float psi) {
        ItemStack stack = new ItemStack(ModItems.itemFor(type));
        if (build != null) {
            stack.set(ModComponents.BIKE_BUILD.get(), build);
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putFloat("FrontPsi", psi);
            tag.putFloat("RearPsi", psi + 2);
            tag.putFloat("ForkPsi", 90);
        });
        return stack;
    }

    private static BlockHitResult hit(BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    private static MountainBikeEntity nearestBike(ServerLevel level, BlockPos at) {
        MountainBikeEntity best = null;
        for (MountainBikeEntity b : level.getEntitiesOfClass(MountainBikeEntity.class, new AABB(at).inflate(3))) {
            if (best == null || b.distanceToSqr(Vec3.atCenterOf(at)) < best.distanceToSqr(Vec3.atCenterOf(at))) {
                best = b;
            }
        }
        return best;
    }

    /** The first bike item in the inventory or on the ground whose pressure marker is {@code psi}, or null. */
    private static ItemStack find(ServerPlayer p, ServerLevel level, BlockPos near, float psi) {
        List<ItemStack> all = new ArrayList<>(p.getInventory().items);
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(near).inflate(8))) {
            all.add(e.getItem());
        }
        for (ItemStack s : all) {
            if (s.getItem() instanceof MountainBikeItem
                    && s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getFloat("FrontPsi") == psi) {
                return s;
            }
        }
        return null;
    }

    private static void drop(ServerPlayer p, ServerLevel level, BlockPos near, float psi) {
        ItemStack found = find(p, level, near, psi);
        if (found != null) {
            found.setCount(0);
        }
    }

    // ------------------------------------------------------------------ checks

    private static void defaults() {
        check(BikeBuild.ENDURO_DEFAULT.equals(new ItemStack(ModItems.MOUNTAIN_BIKE.get()).get(ModComponents.BIKE_BUILD.get())),
                "an enduro bike item carries the stock enduro build");
        check(BikeBuild.HARDTAIL_DEFAULT.equals(new ItemStack(ModItems.HARDTAIL_BIKE.get()).get(ModComponents.BIKE_BUILD.get())),
                "a hardtail item carries the stock hardtail build");
        ItemStack bare = new ItemStack(ModItems.HARDTAIL_BIKE.get());
        bare.remove(ModComponents.BIKE_BUILD.get());
        check(BikeBuild.HARDTAIL_DEFAULT.equals(MountainBikeItem.buildOf(bare)), "an old bike item without data reads as the stock build");
    }

    private static void codecs(ServerLevel level) {
        for (float bad : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            var sticker = new BikeBuild.Sticker(StickerDesign.STAR, Tube.DOWN, bad, 0, bad, bad, 0xFFFFFF).sanitized();
            check(Float.isFinite(sticker.t()) && Float.isFinite(sticker.rotation()) && Float.isFinite(sticker.scale()),
                    "non-finite sticker transforms are sanitized: " + bad);
        }
        BikeBuild many = CUSTOM.with(b -> {
            for (int i = 0; i < 5; i++) {
                b.stickers().add(new BikeBuild.Sticker(StickerDesign.FLAME, Tube.DOWN, .3f + i * .1f, 1, 10, 1, 0xFF8800));
            }
            return b;
        });
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        WorkshopApplyPayload.CODEC.encode(buf, new WorkshopApplyPayload(new BlockPos(1, 2, 3), many));
        WorkshopApplyPayload back = WorkshopApplyPayload.CODEC.decode(buf);
        check(back.pos().equals(new BlockPos(1, 2, 3)) && back.build().equals(many), "the apply payload round-trips a build with stickers");

        var rbuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        ItemStack stack = bike(BikeType.ENDURO, many, 21);
        ItemStack.STREAM_CODEC.encode(rbuf, stack);
        ItemStack decoded = ItemStack.STREAM_CODEC.decode(rbuf);
        check(many.equals(decoded.get(ModComponents.BIKE_BUILD.get())), "the build component survives the network (item stream codec)");
    }

    /** Item -> placed bike -> saved / loaded -> picked up -> item, plus break and the adventure refusal. */
    private static void itemEntityItem(ServerPlayer p, ServerLevel level, BlockPos at, List<net.minecraft.world.entity.Entity> entities) {
        ItemStack item = bike(BikeType.ENDURO, CUSTOM, 20);
        p.setItemInHand(InteractionHand.MAIN_HAND, item);
        check(item.getItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, hit(at))) == InteractionResult.CONSUME, "the bike item places a bike");
        MountainBikeEntity bike = nearestBike(level, at);
        check(bike != null, "the placed bike exists");
        entities.add(bike);
        check(CUSTOM.equals(bike.build()), "placing copies the build to the bike");
        check(bike.frontPsi() == 20 && bike.rearPsi() == 22 && bike.forkPsi() == 90, "placing copies the pressures to the bike");

        // saved and loaded again (a world restart)
        CompoundTag saved = new CompoundTag();
        bike.saveWithoutId(saved);
        MountainBikeEntity loaded = new MountainBikeEntity(ModEntities.MOUNTAIN_BIKE.get(), level);
        loaded.load(saved);
        check(CUSTOM.equals(loaded.build()) && loaded.bikeType() == BikeType.ENDURO && loaded.frontPsi() == 20, "the build is saved and loaded with the bike");
        saved.remove("Build");
        MountainBikeEntity old = new MountainBikeEntity(ModEntities.MOUNTAIN_BIKE.get(), level);
        old.load(saved);
        check(BikeBuild.ENDURO_DEFAULT.equals(old.build()), "an old bike without a saved build gets the stock look");
        MountainBikeEntity hard = new MountainBikeEntity(ModEntities.MOUNTAIN_BIKE.get(), level);
        hard.setBikeType(BikeType.HARDTAIL);
        hard.setBuild(CUSTOM);   // enduro parts on a hardtail are clamped
        check(!hard.build().shape().fullSuspension && !hard.build().fork().enduro, "a hardtail clamps enduro parts of a build");

        // sneak + click: into the inventory
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.setShiftKeyDown(true);
        p.setGameMode(GameType.ADVENTURE);
        check(bike.interact(p, InteractionHand.MAIN_HAND) == InteractionResult.FAIL && !bike.isRemoved(), "an adventure player cannot pick a bike up");
        p.setGameMode(GameType.SURVIVAL);
        check(bike.interact(p, InteractionHand.MAIN_HAND) == InteractionResult.CONSUME, "sneak-click picks up a parked bike");
        check(bike.isRemoved(), "the picked-up bike is gone from the world");
        ItemStack back = find(p, level, at, 20);
        check(back != null && back.is(ModItems.MOUNTAIN_BIKE.get()), "the bike item is in the inventory");
        check(CUSTOM.equals(MountainBikeItem.buildOf(back)), "the picked-up item carries the build");
        check(back.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getFloat("RearPsi") == 22, "the picked-up item carries the pressures");
        drop(p, level, at, 20);

        // breaking it (survival) gives the same item back
        p.setItemInHand(InteractionHand.MAIN_HAND, bike(BikeType.HARDTAIL, null, 24));
        p.getMainHandItem().getItem().useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, hit(at)));
        MountainBikeEntity hardtail = nearestBike(level, at);
        check(hardtail != null && hardtail.bikeType() == BikeType.HARDTAIL && BikeBuild.HARDTAIL_DEFAULT.equals(hardtail.build()),
                "a hardtail item without a build places a stock hardtail");
        entities.add(hardtail);
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        hardtail.setBuild(BikeBuild.HARDTAIL_DEFAULT.with(b -> b.frameColor(0x445566)));
        hardtail.hurt(p.damageSources().playerAttack(p), 1);
        check(hardtail.isRemoved(), "hitting a parked bike breaks it");
        ItemStack dropped = find(p, level, at, 24);
        check(dropped != null && dropped.is(ModItems.HARDTAIL_BIKE.get()) && MountainBikeItem.buildOf(dropped).frameColor() == 0x445566,
                "the broken bike drops an item with its build");
        dropped.setCount(0);
        p.setShiftKeyDown(false);
    }

    /** The stand: mount, workshop click, apply with validation, take, break. */
    private static void stand(ServerPlayer p, ServerLevel level, BlockPos pos, List<BlockPos> blocks) {
        BlockPos far = pos.offset(0, 0, 9), empty = pos.offset(3, 0, 0);
        for (BlockPos s : new BlockPos[]{pos, far, empty}) {
            level.setBlock(s, ModBlocks.BIKE_STAND.get().defaultBlockState(), 3);
            blocks.add(s);
        }
        BlockState state = level.getBlockState(pos);
        var stand = (BikeStandBlockEntity) level.getBlockEntity(pos);
        var farStand = (BikeStandBlockEntity) level.getBlockEntity(far);
        check(stand != null && !stand.hasBike(), "a placed stand is empty");

        // a non-bike item is not mounted; a bike is
        ItemStack stone = new ItemStack(Blocks.STONE);
        state.useItemOn(stone, level, p, InteractionHand.MAIN_HAND, hit(pos));
        check(!stand.hasBike(), "a stone does not go on the stand");
        ItemStack held = bike(BikeType.ENDURO, CUSTOM, 30);
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        state.useItemOn(held, level, p, InteractionHand.MAIN_HAND, hit(pos));
        check(stand.hasBike() && held.isEmpty(), "right-click with a bike mounts it (the item moves into the stand)");
        check(CUSTOM.equals(stand.build()) && stand.bikeType() == BikeType.ENDURO, "the stand knows the bike's build and type");

        // the stand block entity syncs through its update tag (what a renderer sees on the client)
        var tag = stand.getUpdateTag(level.registryAccess());
        var clientCopy = new BikeStandBlockEntity(pos, state);
        clientCopy.loadWithComponents(tag, level.registryAccess());
        check(CUSTOM.equals(clientCopy.build()) && clientCopy.hasBike(), "the stand's update tag carries the bike");

        // a bare click opens the workshop on the client; on the server it changes nothing
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(state.useWithoutItem(level, p, hit(pos)) == InteractionResult.CONSUME && stand.hasBike(), "a bare click leaves the bike on the stand");

        // apply: stored, clamped
        BikeBuild wanted = CUSTOM.with(b -> b.frameColor(0xABCDEF).bell(Bell.AIR_HORN));
        check(BikeStands.apply(p, pos, wanted) && wanted.equals(stand.build()), "apply stores the build on the stand's bike");
        check(stand.bike().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getFloat("FrontPsi") == 30, "apply keeps the bike's pressures");
        BikeBuild evil = CUSTOM.with(b -> {
            b.shape(FrameShape.DJ_CLASSIC);
            for (int i = 0; i < 60; i++) {
                b.stickers().add(new BikeBuild.Sticker(StickerDesign.STAR, Tube.TOP, 5f, 9, 0, 99f, 0x12FFFFFF));
            }
            return b;
        });
        check(BikeStands.apply(p, pos, evil), "apply accepts an out-of-range build ...");
        BikeBuild stored = stand.build();
        check(stored.shape().fullSuspension && stored.stickers().size() == BikeBuild.MAX_STICKERS
                        && stored.stickers().get(0).t() == 1f && stored.stickers().get(0).scale() <= 2.5f && stored.stickers().get(0).side() == 1,
                "... but clamps it (shape of the type, 24 stickers, ranges)");

        // validation
        farStand.setBike(bike(BikeType.ENDURO, null, 31));
        check(!BikeStands.apply(p, far, wanted) && BikeBuild.ENDURO_DEFAULT.equals(farStand.build()), "apply is refused beyond 6 blocks");
        check(!BikeStands.take(p, far) && farStand.hasBike(), "take is refused beyond 6 blocks");
        BikeStandBlockEntity emptyStand = (BikeStandBlockEntity) level.getBlockEntity(empty);
        check(!BikeStands.apply(p, empty, wanted) && !emptyStand.hasBike(), "apply is refused on a stand without a bike");
        check(!BikeStands.apply(p, pos.offset(0, 0, 4), wanted), "apply is refused where there is no stand");
        p.setGameMode(GameType.ADVENTURE);
        BikeBuild before = stand.build();
        check(!BikeStands.apply(p, pos, wanted.with(b -> b.frameColor(0x111111))) && stand.build().equals(before), "apply is refused for an adventure player");
        check(!BikeStands.take(p, pos) && stand.hasBike(), "take is refused for an adventure player");
        p.setGameMode(GameType.SURVIVAL);

        // take: sneak + bare click
        p.setShiftKeyDown(true);
        check(state.useWithoutItem(level, p, hit(pos)) == InteractionResult.CONSUME && !stand.hasBike(), "sneak + bare click takes the bike off the stand");
        ItemStack taken = find(p, level, pos, 30);
        check(taken != null && before.equals(MountainBikeItem.buildOf(taken)), "the taken bike keeps its (applied) build");
        taken.setCount(0);
        p.setShiftKeyDown(false);

        // break: the bike drops with the stand
        stand.setBike(bike(BikeType.ENDURO, CUSTOM, 32));
        level.destroyBlock(pos, true);
        ItemStack fromBreak = find(p, level, pos, 32);
        check(fromBreak != null && CUSTOM.equals(MountainBikeItem.buildOf(fromBreak)), "breaking the stand drops its bike");
        fromBreak.setCount(0);
        check(level.getBlockState(pos).isAir(), "the stand is gone");
    }

    private static void bell(ServerLevel level, BlockPos at, List<net.minecraft.world.entity.Entity> entities) {
        var fake = rider(level);
        MountainBikeEntity bike = spawn(level, at, CUSTOM.with(b -> b.bell(Bell.CLASSIC)), entities);
        check(!BikeBells.ring(fake), "no bell rings for a player who is not riding");
        check(fake.startRiding(bike, true) && bike.getControllingPassenger() == fake, "a player can ride the test bike");
        check(BikeBells.ring(fake), "the bell rings while riding");
        check(!BikeBells.ring(fake), "the bell has a cooldown");
        bike.setBuild(CUSTOM.with(b -> b.bell(Bell.NONE)));
        fake.stopRiding();
        fake.startRiding(bike, true);
        check(!BikeBells.ring(fake), "a bike without a bell does not ring");
        fake.stopRiding();
    }

    private static MountainBikeEntity spawn(ServerLevel level, BlockPos at, BikeBuild build, List<net.minecraft.world.entity.Entity> entities) {
        MountainBikeEntity bike = new MountainBikeEntity(ModEntities.MOUNTAIN_BIKE.get(), level);
        bike.setBikeType(BikeType.ENDURO);
        bike.setBuild(build);
        bike.setPos(at.getX() + .5, at.getY(), at.getZ() + .5);
        bike.setYRot(0);   // heading +z
        level.addFreshEntity(bike);
        entities.add(bike);
        return bike;
    }

    private static int lightsAround(ServerLevel level, BlockPos at) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-5, -4, -6), at.offset(5, 4, 6))) {
            if (level.getBlockState(p).is(Blocks.LIGHT)) {
                n++;
            }
        }
        return n;
    }

    private static void lights(ServerLevel level, BlockPos at, List<BlockPos> blocks, List<net.minecraft.world.entity.Entity> entities) {
        var fake = rider(level);
        MountainBikeEntity bike = spawn(level, at, CUSTOM, entities);   // front and rear light
        BlockPos ideal = BlockPos.containing(bike.getX(), bike.getY() + 1.0, bike.getZ() + 2.5);
        level.setBlock(ideal, Blocks.STONE.defaultBlockState(), 3);   // the ideal spot is not air
        blocks.add(ideal);

        BikeLights.update(bike, true, true);
        check(lightsAround(level, at) == 0, "a bike nobody rides gives no light");
        check(fake.startRiding(bike, true), "the light-test rider mounts");
        BikeLights.update(bike, true, false);
        check(lightsAround(level, at) == 2, "first ordinary light update works without force");
        BikeLights.update(bike, false, true);
        check(lightsAround(level, at) == 0, "no light in daylight");
        BikeLights.update(bike, true, true);
        check(lightsAround(level, at) == 2, "front and rear light blocks appear in the dark");
        check(level.getBlockState(ideal).is(Blocks.STONE), "a light never replaces a non-air block");
        boolean frontOk = false, rearOk = false;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-5, -4, -6), at.offset(5, 4, 6))) {
            BlockState s = level.getBlockState(p);
            if (s.is(Blocks.LIGHT)) {
                int lvl = s.getValue(LightBlock.LEVEL);
                frontOk |= lvl == BikeLights.FRONT_LEVEL && p.getZ() > at.getZ();
                rearOk |= lvl == BikeLights.REAR_LEVEL && p.getZ() < at.getZ();
                check(level.getBlockState(p).getCollisionShape(level, p).isEmpty(), "a light block has no collision");
            }
        }
        check(frontOk && rearOk, "front light (12) ahead of the bike, rear light (6) behind it");
        check(BikeLights.recorded(level) == 2, "the light blocks are recorded for crash cleanup");

        // moving: the bike rolls on, the lights follow and none stay behind
        bike.setPos(bike.getX(), bike.getY(), bike.getZ() + 3);
        BikeLights.update(bike, true, true);
        check(lightsAround(level, at) == 2 && BikeLights.recorded(level) == 2, "the lights follow the bike without leaving any behind");
        bike.setPos(bike.getX(), bike.getY(), bike.getZ() - 3);

        // front light only: one block
        bike.setBuild(CUSTOM.with(b -> b.rearLight(false)));
        BikeLights.update(bike, true, true);
        check(lightsAround(level, at) == 1, "switching the rear light off removes its block");

        // a stale record (crash leftover) is swept; a light of a live bike is not
        BlockPos stale = at.offset(-4, 2, 0);
        level.setBlock(stale, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 12), 2);
        BikeLights.Store.of(level).add(stale);
        BikeLights.sweep(level);
        check(level.getBlockState(stale).isAir() && lightsAround(level, at) == 1, "the sweep removes stale lights and keeps the live one");

        // the rider leaves: lights go
        fake.stopRiding();
        BikeLights.update(bike, true, true);
        check(lightsAround(level, at) == 0 && BikeLights.recorded(level) == 0, "no rider, no light");

        // removing the bike while lit also clears
        fake.startRiding(bike, true);
        BikeLights.update(bike, true, true);
        check(lightsAround(level, at) == 1, "lit again");
        bike.discard();
        check(lightsAround(level, at) == 0 && BikeLights.recorded(level) == 0, "a removed bike takes its light with it");
        fake.stopRiding();
    }

    /** FakePlayer itself refuses mount; use a normal server rider with its no-op test connection. */
    private static ServerPlayer rider(ServerLevel level) {
        var player = new ServerPlayer(level.getServer(),level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"MTBCustomRider"),
                net.minecraft.server.level.ClientInformation.createDefault());
        player.connection = FakePlayerFactory.getMinecraft(level).connection;
        return player;
    }

    private DevCustomTests() {}
}
