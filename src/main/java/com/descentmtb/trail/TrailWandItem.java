package com.descentmtb.trail;

import com.descentmtb.network.TrailActionPayload;
import com.descentmtb.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.descentmtb.trail.TrailMath.Point;

/**
 * Trail Builder (tool 1): one item, every construction mode. The mode is picked in the radial menu
 * (hold G), guide points are clicked on the ground, the result is a ghost preview that is confirmed
 * with Enter / click (see {@link TrailDraft}).
 */
public final class TrailWandItem extends Item {
    private static final String GUIDES = "WandGuides";
    private static final String RAMP_SUB = "RampSub";

    public TrailWandItem(Properties properties) {
        super(properties);
    }

    /** How many clicked points a mode needs before it can produce a preview. */
    public static int requiredPoints(WandMode mode) {
        return switch (mode) {
            case SUPPORT, ROOTS, ROCKS, ROCK_GARDEN, AIRBAG, SIGN, TEMPLATE, RAISE,LOWER,SMOOTH,FLATTEN -> 1;
            case PUMP_LOOP, BOARDWALK, WOOD_KICKER, WOOD_DROP, DROP_EDGE, BARRIER, MEASURE -> 2;
            default -> 3;
        };
    }

    // ------------------------------------------------------------------ ramp tuning sub-action

    public static RampTuning.SubAction rampSubAction(ItemStack stack) {
        int stored = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(RAMP_SUB);
        return RampTuning.SubAction.values()[Math.floorMod(stored, RampTuning.SubAction.values().length)];
    }

    public static RampTuning.SubAction cycleRampSubAction(ItemStack stack, int step) {
        RampTuning.SubAction next = rampSubAction(stack).next(step);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(RAMP_SUB, next.ordinal()));
        return next;
    }

    // ------------------------------------------------------------------ use

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        if (!TrailPermissions.allowed(player)) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        WandSettings settings = WandSettings.read(stack);
        Level level = player.serverLevel();

        switch (settings.mode()) {
            case CLONE:
                return TrailClone.handleClone(context);
            case UNDO:
                TrailDraft.action(player, new TrailActionPayload(TrailActionPayload.UNDO, new CompoundTag(), "", 0, 0, 0));
                return InteractionResult.CONSUME;
            case RAMP_TUNE:
                RampTuning.tune(level, player, context.getClickedPos(), rampSubAction(stack), player.isShiftKeyDown());
                return InteractionResult.CONSUME;
            default:
                break;
        }

        if (player.isShiftKeyDown()) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(GUIDES));
            player.displayClientMessage(Component.translatable("descentmtb.builder.cleared"), true);
            return InteractionResult.CONSUME;
        }

        Point clicked = clickedPoint(context, level);
        int required = requiredPoints(settings.mode());
        CompoundTag guides = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(GUIDES);
        boolean sameSession = guides.getString("Mode").equals(settings.mode().name())
                && guides.getString("Dimension").equals(level.dimension().location().toString());
        int count = sameSession ? guides.getInt("Count") : 0;

        if (count < required - 1) {
            storeGuide(stack, settings, level, count, clicked);
            player.displayClientMessage(Component.translatable("descentmtb.builder.guide", count + 1, required), true);
            return InteractionResult.CONSUME;
        }

        Point first = required > 1 ? readPoint(guides, "p0") : clicked;
        Point middle = required > 2 ? readPoint(guides, "p1") : TrailBuilder.middle(first, clicked);
        try {
            if (settings.mode() == WandMode.MEASURE) {
                player.displayClientMessage(Component.literal(measure(first, clicked)), false);
            } else {
                Map<BlockPos, TrailEdit.Change> plan = plan(level, player, settings, first, middle, clicked);
                TrailDraft.preview(player, plan, BlockPos.containing(first.x(), first.y(), first.z()));
            }
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(GUIDES));
        } catch (IllegalArgumentException e) {
            player.displayClientMessage(Component.literal(e.getMessage()), true);
            return InteractionResult.FAIL;
        }
        return InteractionResult.CONSUME;
    }

    private static Point clickedPoint(UseOnContext context, Level level) {
        BlockPos pos = context.getClickedPos();
        if (level.getBlockState(pos).is(ModBlocks.TRAIL_STAKE.get())) {
            return new Point(pos.getX() + .5, pos.getY(), pos.getZ() + .5);   // snap to a stake
        }
        var hit = context.getClickLocation();
        return new Point(hit.x, hit.y + .01, hit.z);
    }

    private static void storeGuide(ItemStack stack, WandSettings settings, Level level, int index, Point p) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> {
            CompoundTag guides = data.getCompound(GUIDES);
            guides.putDouble("p" + index + "x", p.x());
            guides.putDouble("p" + index + "y", p.y());
            guides.putDouble("p" + index + "z", p.z());
            guides.putInt("Count", index + 1);
            guides.putString("Mode", settings.mode().name());
            guides.putString("Dimension", level.dimension().location().toString());
            data.put(GUIDES, guides);
        });
    }

    private static Point readPoint(CompoundTag tag, String key) {
        return new Point(tag.getDouble(key + "x"), tag.getDouble(key + "y"), tag.getDouble(key + "z"));
    }

    private static String measure(Point a, Point b) {
        double distance = Math.hypot(b.x() - a.x(), b.z() - a.z());
        double rise = b.y() - a.y();
        return String.format(Locale.ROOT, "%.1f m • Δ %.1f m • %.1f°", distance, rise, Math.toDegrees(Math.atan2(rise, distance)));
    }

    private static Map<BlockPos, TrailEdit.Change> plan(Level level, ServerPlayer player, WandSettings s,
                                                        Point a, Point b, Point c) {
        Map<BlockPos, TrailEdit.Change> plan = switch (s.mode()) {
            case RAISE,LOWER,SMOOTH,FLATTEN -> SurfacePlans.brush(level,c,s);
            case PUMP_LINE, PUMP_LOOP -> SurfacePlans.pump(level, a, b, c, s);
            case SUPPORT, ROOTS, ROCKS, ROCK_GARDEN, BARRIER, AIRBAG, SIGN, DROP_EDGE ->
                    EquipmentPlans.plan(level, a, c, s, player.getDirection());
            case TEMPLATE -> TrailLibrary.get(player.serverLevel()).recall(player.serverLevel(), player.getUUID(), "trail",
                    BlockPos.containing(c.x(), c.y(), c.z()));
            default -> TrailBuilder.plan(level, a, b, c, shapeOf(s.mode()), s);
        };
        if (s.mode() == WandMode.DIRT_JUMP) {
            plan = filledWithDirt(plan);   // a dirt jump is solid dirt, not a thin deck
        }
        return plan;
    }

    private static TrailBuilder.Shape shapeOf(WandMode mode) {
        return switch (mode) {
            case BERM -> TrailBuilder.Shape.BERM;
            case ENDURO -> TrailBuilder.Shape.ENDURO;
            case SHARKFIN -> TrailBuilder.Shape.SHARKFIN;
            case BOARDWALK -> TrailBuilder.Shape.BOARDWALK;
            case WOOD_KICKER -> TrailBuilder.Shape.KICKER;
            case DIRT_JUMP -> TrailBuilder.Shape.DIRT_JUMP;
            case WOOD_DROP -> TrailBuilder.Shape.DROP;
            default -> TrailBuilder.Shape.FLOW;
        };
    }

    private static Map<BlockPos, TrailEdit.Change> filledWithDirt(Map<BlockPos, TrailEdit.Change> plan) {
        Map<BlockPos, TrailEdit.Change> dirt = new LinkedHashMap<>();
        plan.forEach((pos, change) -> dirt.put(pos, new TrailEdit.Change(change.state(), change.tag(), change.heights(),
                Blocks.COARSE_DIRT.defaultBlockState(), false)));
        return dirt;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(WandSettings.read(stack).mode().key()));
        lines.add(Component.translatable("descentmtb.wand.hint"));
    }
}
