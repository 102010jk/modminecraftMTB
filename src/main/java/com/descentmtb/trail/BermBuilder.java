package com.descentmtb.trail;

import com.descentmtb.trail.BermShapes.Steepness;
import com.descentmtb.trail.TrailMath.Point;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleBinaryOperator;

/**
 * The berm creator of the Trail Shaper ({@link ShapeMode#BERM_BUILD}): right-click three places (entry, apex, exit)
 * and the berm is built through them straight away, as one {@link TrailEdit} step (undo with Z). Shift + right-click
 * forgets the points. Only creative players and operators may build berms. The steepness and the width are chosen with the mouse wheel while the mode is selected (see
 * {@link Settings}); the shape itself is {@link BermShapes}.
 *
 * <p>The points placed so far live in the tool's custom data (with the dimension they were clicked in; points from
 * another dimension are forgotten), so the client can draw and count them.
 */
public final class BermBuilder {
    /** Custom data keys: the points (as packed block positions), the steepness (ordinal) and the width (m). */
    public static final String POINTS_TAG = "BermPoints", DIMENSION_TAG = "BermDim", STEEPNESS_TAG = "BermSteepness", WIDTH_TAG = "BermWidth";
    /** Entry, apex and exit. */
    public static final int POINTS = 3;
    /** The widths (m) the wheel steps through. */
    public static final int[] WIDTHS = {3, 4, 5, 6};

    /** The berm chosen in the tool: how steep the bank gets and how wide the track is. */
    public record Settings(Steepness steepness, int width) {
        public static final Settings DEFAULT = new Settings(Steepness.MEDIUM, 4);

        public static Settings read(ItemStack tool) {
            var data = ShapeToolItem.data(tool);
            if (!data.contains(STEEPNESS_TAG)) {
                return DEFAULT;
            }
            Steepness[] all = Steepness.values();
            Steepness steepness = all[Math.max(0, Math.min(all.length - 1, data.getInt(STEEPNESS_TAG)))];
            return new Settings(steepness, nearestWidth(data.getInt(WIDTH_TAG)));
        }

        public void store(ItemStack tool) {
            ShapeToolItem.editData(tool, tag -> {
                tag.putInt(STEEPNESS_TAG, steepness.ordinal());
                tag.putInt(WIDTH_TAG, width);
            });
        }

        /** These settings with the width snapped to one of {@link #WIDTHS} (what a client sends is not trusted). */
        public Settings bounded() {
            return new Settings(steepness, nearestWidth(width));
        }

        /** These settings with the steepness moved by {@code step} levels (stops at the ends). */
        public Settings steeper(int step) {
            return new Settings(steepness.shifted(step), width);
        }

        /** These settings with the width moved by {@code step} entries of {@link #WIDTHS}. */
        public Settings wider(int step) {
            int index = 0;
            for (int i = 0; i < WIDTHS.length; i++) {
                if (WIDTHS[i] == width) {
                    index = i;
                }
            }
            return new Settings(steepness, WIDTHS[Math.max(0, Math.min(WIDTHS.length - 1, index + step))]);
        }

        private static int nearestWidth(int width) {
            int best = WIDTHS[0];
            for (int candidate : WIDTHS) {
                if (Math.abs(candidate - width) < Math.abs(best - width)) {
                    best = candidate;
                }
            }
            return best;
        }
    }

    /** The points placed so far (packed block positions), in the order they were clicked. */
    public static long[] points(ItemStack tool) {
        return ShapeToolItem.data(tool).getLongArray(POINTS_TAG);
    }

    /**
     * A right-click on a block in berm mode: Shift forgets the points, otherwise the block becomes the next point,
     * and the third point builds the berm.
     */
    public static boolean click(ServerPlayer player, ItemStack tool, BlockPos pos, boolean shift) {
        if (!TrailEdit.mayBulkEdit(player)) {
            message(player, "descentmtb.edit.creative_only");
            return false;
        }
        if (shift) {
            clear(player, tool);
            return true;
        }
        List<BlockPos> placed = new ArrayList<>();
        String dimension = player.serverLevel().dimension().location().toString();
        if (dimension.equals(ShapeToolItem.data(tool).getString(DIMENSION_TAG))) {
            for (long packed : points(tool)) {
                placed.add(BlockPos.of(packed));
            }
        }
        if (placed.size() >= POINTS) {
            placed.clear();
        }
        placed.add(pos.immutable());
        if (placed.size() < POINTS) {
            store(tool, placed, dimension);
            player.serverLevel().playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, .5f, 1.2f + .2f * placed.size());
            message(player, "descentmtb.berm.point", placed.size(), POINTS);
            return true;
        }
        store(tool, List.of(), dimension);
        return build(player, placed, Settings.read(tool));
    }

    /** Forgets the points placed so far. */
    public static void clear(ServerPlayer player, ItemStack tool) {
        store(tool, List.of(), "");
        message(player, "descentmtb.berm.cleared");
    }

    private static void store(ItemStack tool, List<BlockPos> positions, String dimension) {
        long[] packed = positions.stream().mapToLong(BlockPos::asLong).toArray();
        ShapeToolItem.editData(tool, tag -> {
            if (packed.length == 0) {
                tag.remove(POINTS_TAG);
                tag.remove(DIMENSION_TAG);
            } else {
                tag.putLongArray(POINTS_TAG, packed);
                tag.putString(DIMENSION_TAG, dimension);
            }
        });
    }

    /** Plans the berm through three clicked blocks and applies it. */
    private static boolean build(ServerPlayer player, List<BlockPos> clicked, Settings settings) {
        ServerLevel level = player.serverLevel();
        try {
            Point entry = groundPoint(level, clicked.get(0));
            Point apex = groundPoint(level, clicked.get(1));
            Point exit = groundPoint(level, clicked.get(2));
            BermShapes berm = BermShapes.of(entry, apex, exit,
                    new BermShapes.Params(settings.width(), settings.steepness()), TrailConfig.MAX_LENGTH.get());
            int[] box = berm.bounds();
            DoubleBinaryOperator ground = (x, z) -> SurfacePlans.terrain(level, x, z, apex.y());
            Map<BlockPos, TrailEdit.Change> plan = SurfacePlans.surface(level, box[0], box[1], box[2], box[3],
                    apex.y(), berm.heights(ground), berm::contains, false);
            int blocks = TrailEdit.apply(level, player, plan, false);   // only creative players and operators get here: free
            level.playSound(null, clicked.get(1), SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, .8f, .8f);
            message(player, "descentmtb.berm.built", blocks);
            return true;
        } catch (BermShapes.Rejected e) {
            message(player, e.key(), e.args());
            return false;
        } catch (IllegalArgumentException e) {
            player.displayClientMessage(TrailEdit.describe(e), true);
            return false;
        }
    }

    /** The top of the ground at the clicked block, at the middle of the block. */
    private static Point groundPoint(ServerLevel level, BlockPos clicked) {
        double x = clicked.getX() + .5, z = clicked.getZ() + .5;
        return new Point(x, SurfacePlans.terrain(level, x, z, clicked.getY() + 1.0), z);
    }

    private static void message(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }

    private BermBuilder() {}
}
