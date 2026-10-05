package com.descentmtb.tape;

import com.descentmtb.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;

/**
 * The trail tape roll. Right-click a block: the first click puts a {@link BarrierPostBlock} there (or picks an
 * existing one) and remembers it in the stack's custom data; the second click does the same and strings tape
 * between the two. Sneak + right-click in the air forgets the first post. Every new post costs one roll item
 * in survival, the tape itself is free.
 */
public final class TrailTapeItem extends Item {
    /** Longest tape between two posts, in blocks (centre to centre). */
    public static final double MAX_LENGTH = 24;
    private static final String KEY_POS = "TapePos";
    private static final String KEY_DIM = "TapeDim";

    public TrailTapeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level instanceof ServerLevel server && context.getPlayer() != null) {
            return click(context.getPlayer(), server, context.getItemInHand(), context.getClickedPos(), context.getClickedFace());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Sneaking in the air drops the pending first post. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && pending(stack, level) != null) {
            if (!level.isClientSide) {
                setPending(stack, null, level);
                say(player, "descentmtb.tape.cancelled");
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    /**
     * One right-click on a block face, server side. Public so the dev tests can drive exactly the code path of a
     * player. Returns SUCCESS when something happened (post placed, post picked, tape strung).
     */
    public static InteractionResult click(Player player, ServerLevel level, ItemStack stack, BlockPos clicked, Direction face) {
        if (!player.mayBuild()) {
            return InteractionResult.FAIL;
        }
        if (!level.isLoaded(clicked) || !level.mayInteract(player, clicked)) {
            return InteractionResult.FAIL;
        }
        boolean isPost = level.getBlockState(clicked).is(ModBlocks.BARRIER_POST.get());
        BlockPos target = isPost || level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(face);
        if (!level.isInWorldBounds(target) || !level.isLoaded(target) || !level.mayInteract(player, target)) {
            say(player, "descentmtb.tape.blocked");
            return InteractionResult.FAIL;
        }
        boolean created = !level.getBlockState(target).is(ModBlocks.BARRIER_POST.get());
        if (created && !level.getBlockState(target).canBeReplaced()) {
            say(player, "descentmtb.tape.blocked");
            return InteractionResult.FAIL;
        }

        BlockPos first = pending(stack, level);
        if (first != null) {
            // look at the first post only if it can be a real one: close enough, and in a chunk that is loaded
            if (first.distSqr(target) > MAX_LENGTH * MAX_LENGTH) {
                say(player, "descentmtb.tape.too_far", (int) MAX_LENGTH);
                return InteractionResult.FAIL;
            }
            if (!level.isLoaded(first) || !level.getBlockState(first).is(ModBlocks.BARRIER_POST.get())) {
                first = null; // the first post was broken meanwhile: start over
            } else if (!level.mayInteract(player, first)) {
                say(player, "descentmtb.tape.blocked");   // the tape would change a post the player may not touch
                return InteractionResult.FAIL;
            }
        }

        if (first == null) {
            if (!created) {
                BarrierPostEntity post = (BarrierPostEntity) level.getBlockEntity(target);
                post.pruneStale();
                if (!post.hasFreeLink()) {
                    say(player, "descentmtb.tape.full");
                    return InteractionResult.FAIL;
                }
            } else if (!player.getAbilities().instabuild && stack.getCount() < 2) {
                // placing the last roll would empty the stack, and the first-post memory with it
                say(player, "descentmtb.tape.need_more");
                return InteractionResult.FAIL;
            }
            if (created) {
                placePost(level, target, player, stack);
            }
            setPending(stack, target, level);
            say(player, "descentmtb.tape.first");
            return InteractionResult.SUCCESS;
        }
        return connect(player, level, stack, first, target, created);
    }

    private static InteractionResult connect(Player player, ServerLevel level, ItemStack stack, BlockPos first, BlockPos target,
                                             boolean created) {
        if (first.equals(target)) {
            say(player, "descentmtb.tape.same");
            return InteractionResult.FAIL;
        }
        double length = Math.sqrt(first.distSqr(target));
        if (length > MAX_LENGTH) {
            say(player, "descentmtb.tape.too_far", (int) MAX_LENGTH);
            return InteractionResult.FAIL;
        }
        BarrierPostEntity a = (BarrierPostEntity) level.getBlockEntity(first);
        a.pruneStale();
        if (a.isLinkedTo(target)) {
            say(player, "descentmtb.tape.exists");
            return InteractionResult.FAIL;
        }
        BarrierPostEntity b = null;
        if (!created) {
            b = (BarrierPostEntity) level.getBlockEntity(target);
            b.pruneStale();
        }
        if (!a.hasFreeLink() || (b != null && !b.hasFreeLink())) {
            say(player, "descentmtb.tape.full");
            return InteractionResult.FAIL;
        }
        setPending(stack, null, level); // before the last roll may be used up by the new post
        if (created) {
            placePost(level, target, player, stack);
            b = (BarrierPostEntity) level.getBlockEntity(target);
        }
        a.addLink(target);
        b.addLink(first);
        level.playSound(null, target, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1f, 1.2f);
        say(player, "descentmtb.tape.linked", String.format(Locale.ROOT, "%.1f", length));
        return InteractionResult.SUCCESS;
    }

    private static void placePost(ServerLevel level, BlockPos pos, Player player, ItemStack stack) {
        BlockState post = ModBlocks.BARRIER_POST.get().defaultBlockState();
        level.setBlock(pos, post, 3);
        level.playSound(null, pos, post.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1f, .9f);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    /** The first post of an unfinished tape held in this stack, or null (also if it was set in another dimension). */
    public static BlockPos pending(ItemStack stack, Level level) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.getUnsafe();
        if (!tag.contains(KEY_POS) || !level.dimension().location().toString().equals(tag.getString(KEY_DIM))) {
            return null;
        }
        return BlockPos.of(tag.getLong(KEY_POS));
    }

    private static void setPending(ItemStack stack, BlockPos pos, Level level) {
        if (pos == null) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                tag.remove(KEY_POS);
                tag.remove(KEY_DIM);
            });
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data != null && data.isEmpty()) {
                stack.remove(DataComponents.CUSTOM_DATA);
            }
        } else {
            CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
                tag.putLong(KEY_POS, pos.asLong());
                tag.putString(KEY_DIM, level.dimension().location().toString());
            });
        }
    }

    private static void say(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("descentmtb.tape.hint"));
    }
}
