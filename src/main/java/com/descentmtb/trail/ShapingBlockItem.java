package com.descentmtb.trail;

import com.descentmtb.ramp.RampBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shaping Dirt / Shaping Deck: a block that is sculpted by hand, no tool needed.
 *
 * <ul>
 *   <li>Right-click a plain block: places a shaping block (it matches the shaped neighbours).</li>
 *   <li>Right-click a shaped block or ramp: raises the corner / edge / whole block under the cursor
 *       (the click position decides which, see {@link ColumnEditor#pickVertices}); Shift lowers.</li>
 *   <li>Left-click anything with the item in hand: lowers it (plain terrain turns into a shaped copy).</li>
 * </ul>
 * Neighbouring blocks share their corners, so the surface is always continuous.
 */
public final class ShapingBlockItem extends BlockItem {
    /** One sculpting step: 2/16 of a block. */
    public static final double STEP = 2.0 / 16;
    /** In survival one item pays for this many raise steps. */
    private static final int STEPS_PER_ITEM = 4;
    private static final String SPENT = "SculptSteps";
    private static final double MAX_RISE = 12, MAX_DIG = 6;

    private final boolean deck;

    public ShapingBlockItem(Block block, Properties properties, boolean deck) {
        super(block, properties);
        this.deck = deck;
    }

    @Override
    public String getDescriptionId() {
        return net.minecraft.Util.makeDescriptionId("item", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(this));
    }

    public boolean isDeck() {
        return deck;
    }

    // ------------------------------------------------------------------ placing

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockState clicked = context.getLevel().getBlockState(context.getClickedPos());
        if (!(clicked.getBlock() instanceof RampBlock)) {
            return super.useOn(context);   // plain block: place a new shaping block
        }
        if (context.getLevel().isClientSide || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        sculpt(player, context.getItemInHand(), context.getClickedPos(), context.getClickLocation(), player.isShiftKeyDown());
        return InteractionResult.CONSUME;
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        boolean placed = super.placeBlock(context, state);
        if (placed && !context.getLevel().isClientSide
                && context.getLevel().getBlockEntity(context.getClickedPos()) instanceof TrailSurfaceEntity shaped) {
            shaped.setShape(ColumnEditor.initialCorners(context.getLevel(), context.getClickedPos()), deck);
            shaped.setMaterial(deck ? net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState()
                    : net.minecraft.world.level.block.Blocks.COARSE_DIRT.defaultBlockState(), false);
        }
        return placed;
    }

    // ------------------------------------------------------------------ sculpting

    /** Called for right clicks (server) and for the left-click payload. */
    public static void sculpt(ServerPlayer player, ItemStack stack, BlockPos pos, Vec3 hit, boolean lower) {
        Level level = player.level();
        if (!level.isLoaded(pos) || !level.mayInteract(player, pos)) {
            return;
        }
        boolean survival = !player.getAbilities().instabuild;
        if (!lower && survival && !payRaiseStep(player, stack)) {
            player.displayClientMessage(Component.translatable("descentmtb.shaping.empty"), true);
            return;
        }
        try {
            double fx = hit.x - pos.getX(), fz = hit.z - pos.getZ();
            fx = Math.max(0, Math.min(1, fx));
            fz = Math.max(0, Math.min(1, fz));
            double base = pos.getY();
            Map<BlockPos, TrailEdit.Change> changes = ColumnEditor.sculpt(level, pos.getX(), pos.getZ(), pos.getY(), fx, fz,
                    lower ? -STEP : STEP, base - MAX_DIG, base + MAX_RISE);
            TrailEdit.apply(level, player, changes);
            var column = ColumnEditor.read(level, pos.getX(), pos.getZ(), pos.getY());
            if (column != null) {
                double top = Math.max(Math.max(column.abs()[0], column.abs()[1]), Math.max(column.abs()[2], column.abs()[3]));
                player.displayClientMessage(Component.translatable("descentmtb.shaping.height",
                        String.format(Locale.ROOT, "%.2f", top)), true);
            }
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, .5f, lower ? .8f : 1.1f);
        } catch (IllegalArgumentException e) {
            player.displayClientMessage(Component.literal(e.getMessage()), true);
        }
    }

    /** Survival: every {@value #STEPS_PER_ITEM}th raise step eats one item. */
    private static boolean payRaiseStep(ServerPlayer player, ItemStack stack) {
        int spent = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(SPENT) + 1;
        if (spent < STEPS_PER_ITEM) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(SPENT, spent));
            return true;
        }
        if (stack.getCount() <= 1) {
            return false;   // keep the last one so the stack does not vanish mid-sculpt
        }
        stack.shrink(1);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(SPENT, 0));
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("descentmtb.shaping.hint.place"));
        lines.add(Component.translatable("descentmtb.shaping.hint.raise"));
        lines.add(Component.translatable("descentmtb.shaping.hint.lower"));
    }
}
