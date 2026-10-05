package com.descentmtb.trail;

import com.descentmtb.trail.DownhillShapes.Style;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleBinaryOperator;

/**
 * The downhill line of the Trail Shaper ({@link ShapeMode#DOWNHILL}): right-click the top of a slope and then its
 * bottom, and a line of berms, rollers and jumps is built between them, as one {@link TrailEdit} step (undo with Z).
 * Shift + right-click forgets the first point. Only creative players and operators may build downhill lines. The style and the width are chosen with the mouse wheel while the mode
 * is selected (see {@link Settings}); the line itself is planned by {@link DownhillShapes}, over the terrain read by
 * {@link HillGround}.
 *
 * <p>The first point lives in the tool's custom data (with its dimension; a start from another dimension is
 * forgotten), so the client can draw it.
 */
public final class DownhillBuilder {
    /** Custom data keys: the points (as packed block positions), the style (ordinal) and the width (m). */
    public static final String POINTS_TAG = "DownhillPoints", DIMENSION_TAG = "DownhillDim", STYLE_TAG = "DownhillStyle", WIDTH_TAG = "DownhillWidth";
    /** Start and finish. */
    public static final int POINTS = 2;
    /** The widths (m) the wheel steps through. */
    public static final int[] WIDTHS = {3, 4, 5};
    /** Air kept clear above the track (blocks). */
    private static final int HEADROOM = 2;

    /** The line chosen in the tool: what the features are and how wide the track is. */
    public record Settings(Style style, int width) {
        public static final Settings DEFAULT = new Settings(Style.MIXED, WIDTHS[0]);

        public static Settings read(ItemStack tool) {
            var data = ShapeToolItem.data(tool);
            if (!data.contains(STYLE_TAG)) {
                return DEFAULT;
            }
            Style[] all = Style.values();
            return new Settings(all[Math.max(0, Math.min(all.length - 1, data.getInt(STYLE_TAG)))], nearestWidth(data.getInt(WIDTH_TAG)));
        }

        public void store(ItemStack tool) {
            ShapeToolItem.editData(tool, tag -> {
                tag.putInt(STYLE_TAG, style.ordinal());
                tag.putInt(WIDTH_TAG, width);
            });
        }

        /** These settings with the width snapped to one of {@link #WIDTHS} (what a client sends is not trusted). */
        public Settings bounded() {
            return new Settings(style, nearestWidth(width));
        }

        /** These settings with the next (or previous, with a negative step) style. */
        public Settings styled(int step) {
            return new Settings(style.cycled(step), width);
        }

        /** These settings with the width moved by {@code step} entries of {@link #WIDTHS}. */
        public Settings wider(int step) {
            int index = 0;
            for (int i = 0; i < WIDTHS.length; i++) {
                if (WIDTHS[i] == width) {
                    index = i;
                }
            }
            return new Settings(style, WIDTHS[Math.max(0, Math.min(WIDTHS.length - 1, index + step))]);
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

    /** A right-click on a block in downhill mode: Shift forgets the start, otherwise the block is the start, then the finish. */
    public static boolean click(ServerPlayer player, ItemStack tool, BlockPos pos, boolean shift) {
        if (!TrailEdit.mayBulkEdit(player)) {
            message(player, "descentmtb.edit.creative_only");
            return false;
        }
        if (shift) {
            clear(player, tool);
            return true;
        }
        String dimension = player.serverLevel().dimension().location().toString();
        long[] placed = dimension.equals(ShapeToolItem.data(tool).getString(DIMENSION_TAG)) ? points(tool) : new long[0];
        if (placed.length >= POINTS || placed.length == 0) {
            store(tool, List.of(pos.immutable()), dimension);
            player.serverLevel().playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, .5f, 1.4f);
            message(player, "descentmtb.downhill.point", 1, POINTS);
            return true;
        }
        store(tool, List.of(), dimension);
        return build(player, BlockPos.of(placed[0]), pos, Settings.read(tool));
    }

    /** Forgets the points placed so far. */
    public static void clear(ServerPlayer player, ItemStack tool) {
        store(tool, List.of(), "");
        message(player, "descentmtb.downhill.cleared");
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

    /** Plans the line between the two clicked blocks and applies it. */
    private static boolean build(ServerPlayer player, BlockPos start, BlockPos finish, Settings settings) {
        ServerLevel level = player.serverLevel();
        try {
            HillGround ground = new HillGround(level);
            DownhillShapes.Params params = new DownhillShapes.Params(settings.style(), settings.width(), TrailConfig.MAX_DOWNHILL.get());
            DownhillShapes line = DownhillShapes.plan(ground::height, start.getX() + .5, start.getZ() + .5,
                    finish.getX() + .5, finish.getZ() + .5, params, start.asLong() * 31 + finish.asLong());
            int blocks = TrailEdit.apply(level, player, plan(level, ground, line), false);   // creative / operator only: free
            level.playSound(null, start, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, .8f, .8f);
            message(player, "descentmtb.downhill.built", (int) Math.round(line.length()), line.jumps(), line.berms());
            return blocks > 0;
        } catch (DownhillShapes.Rejected e) {
            message(player, e.key(), e.args());
            return false;
        } catch (IllegalArgumentException e) {
            player.displayClientMessage(TrailEdit.describe(e), true);
            return false;
        }
    }

    /**
     * The block changes that give the terrain the shape of {@code line}: dirt surface along the track and where it
     * visibly changes the ground, plants and trees cleared from the track and {@link #HEADROOM} blocks above it.
     */
    static Map<BlockPos, TrailEdit.Change> plan(ServerLevel level, HillGround ground, DownhillShapes line) {
        Map<Long, Double> remembered = new HashMap<>();
        DoubleBinaryOperator heights = line.heights();
        DoubleBinaryOperator height = (x, z) -> remembered.computeIfAbsent(BlockPos.asLong((int) Math.round(x), 0, (int) Math.round(z)),
                key -> heights.applyAsDouble(x, z));
        java.util.function.BiPredicate<Double, Double> changes = (cx, cz) -> {
            if (!line.contains(cx, cz) || Double.isNaN(ground.column((int) Math.floor(cx), (int) Math.floor(cz)))) {
                return false;
            }
            int bx = (int) Math.floor(cx), bz = (int) Math.floor(cz);
            boolean visible = line.onTrack(cx, cz);
            for (int i = 0; i < 4; i++) {
                double x = bx + i % 2, z = bz + i / 2, h = height.applyAsDouble(x, z);
                if (Double.isNaN(h)) {
                    return false;   // never build on the edge of water
                }
                visible |= Math.abs(h - ground.height(x, z)) > .04;
            }
            return visible;
        };
        int[] box = line.bounds();
        return SurfacePlans.hillside(level, box[0], box[1], box[2], box[3], (x, z) -> ground.column((int) Math.floor(x), (int) Math.floor(z)),
                height, changes, HEADROOM);
    }

    private static void message(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }

    private DownhillBuilder() {}
}
