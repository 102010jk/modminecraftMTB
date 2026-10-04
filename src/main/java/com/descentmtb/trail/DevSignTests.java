package com.descentmtb.trail;

import com.descentmtb.DescentMtb;
import com.descentmtb.network.SignContentPayload;
import com.descentmtb.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Development-only server checks of the trail signs, run from {@link DevTrailTests}: every sign type is
 * placed, filled through the same handler the editor packet uses, and must survive an NBT round trip. Also
 * covers the old-format sign, the reach check and the personal-best bookkeeping.
 */
final class DevSignTests {
    private static final String TEST_TRAIL = "devtest trail";

    static void run(ServerPlayer player, ServerLevel level, int x, int y, int z) {
        Vec3 saved = player.position();
        try {
            BlockPos pos = new BlockPos(x + 4, y, z + 5);
            player.setPos(pos.getX() + 2.5, pos.getY(), pos.getZ() + .5);
            for (SignContent content : samples()) {
                roundTrip(player, level, pos, content);
            }
            reach(player, level, pos);
            legacy(level, pos);
            states(level, pos);
            records(player);
        } finally {
            player.setPos(saved);
        }
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new IllegalStateException(what);
        }
        DescentMtb.LOG.info("[trailtest] PASS: {}", what);
    }

    private static SignContent[] samples() {
        byte[] pixels = new byte[SignContent.PIXELS];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (byte) (i * 7 % 16);
        }
        return new SignContent[]{
                new SignContent(SignContent.Type.TRAIL, "Black Forest", SignContent.Difficulty.DOUBLE_BLACK,
                        SignContent.Arrow.LEFT, null, "", null),
                new SignContent(SignContent.Type.START, TEST_TRAIL, SignContent.Difficulty.PRO, null, null, "", null),
                new SignContent(SignContent.Type.FINISH, TEST_TRAIL, null, null, null, "", null),
                new SignContent(SignContent.Type.WARNING, "", null, null, SignContent.Warning.ROCKS, "Loose rocks", null),
                new SignContent(SignContent.Type.CUSTOM, "", null, null, null, "", pixels),
        };
    }

    /** Places a sign, sets the content like the editor packet does, then saves and reloads the block entity. */
    private static void roundTrip(ServerPlayer player, ServerLevel level, BlockPos pos, SignContent content) {
        level.setBlock(pos, ModBlocks.TRAIL_SIGN.get().defaultBlockState(), 3);
        check(SignContentPayload.apply(player, new SignContentPayload(pos, content)),
                content.type() + " sign accepts the editor packet");
        var sign = (TrailSignEntity) level.getBlockEntity(pos);
        check(sign.content().equals(content), content.type() + " sign stores exactly what was sent");

        CompoundTag tag = sign.saveWithoutMetadata(level.registryAccess());
        var reloaded = new TrailSignEntity(pos, sign.getBlockState());
        reloaded.loadWithComponents(tag, level.registryAccess());
        check(reloaded.content().equals(content), content.type() + " sign survives NBT serialization");
    }

    private static void reach(ServerPlayer player, ServerLevel level, BlockPos pos) {
        var sign = (TrailSignEntity) level.getBlockEntity(pos);
        SignContent before = sign.content();
        SignContent other = new SignContent(SignContent.Type.FINISH, "far away", null, null, null, "", null);
        Vec3 here = player.position();
        player.setPos(here.add(SignContentPayload.REACH + 3, 0, 0));
        boolean accepted = SignContentPayload.apply(player, new SignContentPayload(pos, other));
        player.setPos(here);
        check(!accepted && sign.content().equals(before), "a sign too far away is not edited");
    }

    /** Signs saved before the types existed hold only "Pixels" and must load as custom art. */
    private static void legacy(ServerLevel level, BlockPos pos) {
        byte[] pixels = new byte[SignContent.PIXELS];
        pixels[17] = 5;
        CompoundTag old = new CompoundTag();
        old.putByteArray("Pixels", pixels);
        var sign = new TrailSignEntity(pos, ModBlocks.TRAIL_SIGN.get().defaultBlockState());
        sign.loadWithComponents(old, level.registryAccess());
        check(sign.content().type() == SignContent.Type.CUSTOM && sign.content().pixels()[17] == 5,
                "an old pixel-only sign loads as a custom sign");
    }

    /** Wall placement state and the collision shapes of both variants. */
    private static void states(ServerLevel level, BlockPos pos) {
        var block = ModBlocks.TRAIL_SIGN.get();
        var standing = block.defaultBlockState().setValue(TrailSignBlock.FACING, Direction.EAST);
        var onWall = standing.setValue(TrailSignBlock.WALL, true);
        check(!standing.getShape(level, pos).isEmpty() && !onWall.getShape(level, pos).isEmpty(),
                "both sign variants have a collision shape");
        check(standing.getShape(level, pos).max(Direction.Axis.Y) > onWall.getShape(level, pos).max(Direction.Axis.Y),
                "a standing sign is taller than a wall sign");
        check(TrailSignBlock.rideHeading(standing) == Direction.WEST && TrailSignBlock.rideHeading(onWall) == Direction.NORTH,
                "riders head away from a standing sign and along a wall sign");
    }

    private static void records(ServerPlayer player) {
        TrailRecords.forget(player, TEST_TRAIL);
        check(TrailRecords.best(player, TEST_TRAIL) == -1, "no personal best on a new trail");
        check(TrailRecords.record(player, TEST_TRAIL, 90_000).record(), "the first run is a personal best");
        check(!TrailRecords.record(player, TEST_TRAIL, 95_000).record() && TrailRecords.best(player, TEST_TRAIL) == 90_000,
                "a slower run keeps the personal best");
        check(TrailRecords.record(player, "  DevTest Trail ", 80_000).record() && TrailRecords.best(player, TEST_TRAIL) == 80_000,
                "a faster run on the same trail name (any case) replaces it");
        TrailRecords.forget(player, TEST_TRAIL);
    }

    private DevSignTests() {}
}
