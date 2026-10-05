package com.descentmtb.trail;

import com.descentmtb.ramp.RampBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Copy and paste of the Trail Shaper ({@link ShapeMode#COPY}). Click corner A and corner B: the box between them
 * (from the lower to the higher of the two blocks, plus {@link ClipboardMath#HEADROOM} blocks of air above) goes
 * into the player's clipboard together with the block entity data (shaped corners, ramp materials). Every
 * following right-click pastes the clipboard with its lowest north-west corner on top of the clicked block, as one
 * {@link TrailEdit} step. Shift + right-click in the air turns the clipboard by 90 degrees; Shift + right-click
 * on a block starts a new selection.
 *
 * <p>What the client needs to draw (corner A, the footprint of the clipboard) is kept in the tool's custom data.
 */
public final class ShapeClipboard {
    /** Custom data keys read by the client highlight. */
    public static final String FIRST_TAG = "CopyFirst", SIZE_TAG = "CopySize";

    private record Cell(int dx, int dy, int dz, BlockState state, CompoundTag data) {}

    private static final class Clipboard {
        final int sizeX, sizeY, sizeZ;
        final List<Cell> cells;
        int turns;

        Clipboard(int sizeX, int sizeY, int sizeZ, List<Cell> cells) {
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.cells = cells;
        }

        int[] footprint() {
            return ClipboardMath.rotatedSize(sizeX, sizeZ, turns);
        }
    }

    private record Corner(ResourceKey<Level> dimension, BlockPos pos) {}

    private static final Map<UUID, Clipboard> CLIPBOARDS = new HashMap<>();
    private static final Map<UUID, Corner> FIRST_CORNERS = new HashMap<>();

    public static void clearSession() {
        CLIPBOARDS.clear();
        FIRST_CORNERS.clear();
    }

    /** Forgets the clipboard and the first corner of a player who left. */
    static void forget(UUID player) {
        CLIPBOARDS.remove(player);
        FIRST_CORNERS.remove(player);
    }

    /**
     * A right-click on a block in copy mode.
     *
     * @param tool  the Trail Shaper stack that was used (it shows the selection to the client)
     * @param shift starts a new selection at this block
     * @return true when something was copied or pasted
     */
    public static boolean click(ServerPlayer player, ItemStack tool, BlockPos pos, boolean shift) {
        if (!TrailEdit.mayBulkEdit(player)) {
            message(player, "descentmtb.edit.creative_only");
            return false;
        }
        UUID id = player.getUUID();
        if (shift) {
            CLIPBOARDS.remove(id);
            return firstCorner(player, tool, pos);
        }
        Clipboard clipboard = CLIPBOARDS.get(id);
        if (clipboard != null) {
            return paste(player, tool, clipboard, pos);
        }
        Corner first = FIRST_CORNERS.get(id);
        if (first == null || !first.dimension().equals(player.serverLevel().dimension())) {
            return firstCorner(player, tool, pos);
        }
        return copy(player, tool, first.pos(), pos);
    }

    /** Shift + right-click in the air: turns the clipboard by 90 degrees clockwise. */
    public static void rotate(ServerPlayer player, ItemStack tool) {
        Clipboard clipboard = CLIPBOARDS.get(player.getUUID());
        if (clipboard == null) {
            message(player, "descentmtb.copy.empty");
            return;
        }
        clipboard.turns = (clipboard.turns + 1) % 4;
        publish(player, tool);
        message(player, "descentmtb.copy.rotated", clipboard.turns * 90);
    }

    private static boolean firstCorner(ServerPlayer player, ItemStack tool, BlockPos pos) {
        FIRST_CORNERS.put(player.getUUID(), new Corner(player.serverLevel().dimension(), pos.immutable()));
        publish(player, tool);
        message(player, "descentmtb.copy.corner", 1);
        return true;
    }

    /** Captures the box between two corners into the clipboard. */
    private static boolean copy(ServerPlayer player, ItemStack tool, BlockPos a, BlockPos b) {
        ServerLevel level = player.serverLevel();
        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        int sizeX = Math.abs(a.getX() - b.getX()) + 1;
        int sizeZ = Math.abs(a.getZ() - b.getZ()) + 1;
        int sizeY = ClipboardMath.boxHeight(a.getY(), b.getY());
        if (!ClipboardMath.fits(sizeX, sizeY, sizeZ)) {
            message(player, "descentmtb.copy.too_big", ClipboardMath.MAX_SIZE_X, ClipboardMath.MAX_SIZE_Z, ClipboardMath.MAX_HEIGHT);
            return false;
        }
        List<Cell> cells = new ArrayList<>();
        int blocks = 0;
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dy = 0; dy < sizeY; dy++) {
                for (int dz = 0; dz < sizeZ; dz++) {
                    BlockPos pos = min.offset(dx, dy, dz);
                    if (!level.isLoaded(pos)) {
                        message(player, "descentmtb.copy.not_loaded");
                        return false;
                    }
                    var entity = level.getBlockEntity(pos);
                    if (entity != null && !copyable(entity)) {
                        message(player, "descentmtb.copy.unsupported");
                        return false;
                    }
                    BlockState state = level.getBlockState(pos);
                    CompoundTag data = entity == null ? null : entity.saveWithoutMetadata(level.registryAccess());
                    cells.add(new Cell(dx, dy, dz, state, data));
                    if (!state.isAir()) {
                        blocks++;
                    }
                }
            }
        }
        CLIPBOARDS.put(player.getUUID(), new Clipboard(sizeX, sizeY, sizeZ, cells));
        FIRST_CORNERS.remove(player.getUUID());
        publish(player, tool);
        level.playSound(null, b, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, .5f, 1.5f);
        message(player, "descentmtb.copy.copied", blocks);
        return true;
    }

    /** Only shaped surfaces, ramps and trail signs are plain data; inventories and the like are never copied. */
    private static boolean copyable(net.minecraft.world.level.block.entity.BlockEntity entity) {
        return entity instanceof RampBlockEntity || entity instanceof TrailSignEntity;
    }

    /** Pastes the clipboard (in its current turn) with its north-west bottom corner on top of {@code clicked}. */
    private static boolean paste(ServerPlayer player, ItemStack tool, Clipboard clipboard, BlockPos clicked) {
        ServerLevel level = player.serverLevel();
        BlockPos origin = clicked.above();
        Rotation rotation = rotation(clipboard.turns);
        Map<BlockPos, TrailEdit.Change> plan = new LinkedHashMap<>();
        int blocks = 0;
        for (Cell cell : clipboard.cells) {
            int[] moved = ClipboardMath.rotateOffset(cell.dx(), cell.dz(), clipboard.sizeX, clipboard.sizeZ, clipboard.turns);
            BlockPos target = origin.offset(moved[0], cell.dy(), moved[1]);
            if (level.isOutsideBuildHeight(target)) {
                continue;
            }
            if (!level.isLoaded(target)) {
                message(player, "descentmtb.copy.not_loaded");
                return false;
            }
            BlockState there = level.getBlockState(target);
            if (there.getDestroySpeed(level, target) < 0) {
                continue;   // bedrock and the like are never cleared or replaced
            }
            if (cell.state().isAir()) {
                if (!there.isAir()) {
                    plan.put(target, TrailEdit.Change.block(cell.state()));   // the clipboard's air clears the way
                }
                continue;
            }
            if (cell.state().getDestroySpeed(level, target) < 0) {
                continue;   // an unbreakable block is never placed
            }
            CompoundTag data = cell.data() == null ? null : rotatedData(cell.data(), clipboard.turns);
            plan.put(target, new TrailEdit.Change(cell.state().rotate(rotation), data, null, null, false));
            blocks++;
        }
        if (plan.isEmpty()) {
            return false;
        }
        try {
            TrailEdit.apply(level, player, plan, false);
        } catch (IllegalArgumentException e) {
            player.displayClientMessage(TrailEdit.describe(e), true);
            return false;
        }
        level.playSound(null, clicked, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, .6f, .9f);
        message(player, "descentmtb.copy.pasted", blocks);
        return true;
    }

    /** A copy of the block entity data of a block that has been turned {@code turns} quarter turns. */
    private static CompoundTag rotatedData(CompoundTag data, int turns) {
        CompoundTag copy = data.copy();
        copy.remove("consumed");   // a pasted material was never paid for by anybody
        if (turns != 0 && copy.contains("Corner0")) {
            double[] corners = new double[4];
            for (int i = 0; i < 4; i++) {
                corners[i] = copy.getDouble("Corner" + i);
            }
            double[] turned = ClipboardMath.rotateCorners(corners, turns);
            for (int i = 0; i < 4; i++) {
                copy.putDouble("Corner" + i, turned[i]);
            }
        }
        return copy;
    }

    private static Rotation rotation(int turns) {
        return switch (Math.floorMod(turns, 4)) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** Writes what the client draws (corner A, the footprint of the clipboard) into the tool in the player's hand. */
    private static void publish(ServerPlayer player, ItemStack tool) {
        Corner first = FIRST_CORNERS.get(player.getUUID());
        Clipboard clipboard = CLIPBOARDS.get(player.getUUID());
        ShapeToolItem.editData(tool, tag -> {
            if (first == null) {
                tag.remove(FIRST_TAG);
            } else {
                tag.putLong(FIRST_TAG, first.pos().asLong());
            }
            if (clipboard == null) {
                tag.remove(SIZE_TAG);
            } else {
                int[] footprint = clipboard.footprint();
                tag.putIntArray(SIZE_TAG, new int[]{footprint[0], clipboard.sizeY, footprint[1]});
            }
        });
    }

    private static void message(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }

    private ShapeClipboard() {}
}
