package com.descentmtb.item;

import com.descentmtb.custom.BikeBuild;
import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.entity.BikeType;
import com.descentmtb.registry.ModComponents;
import com.descentmtb.registry.ModEntities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Right-click a block face to deploy a {@link MountainBikeEntity} at that spot,
 * facing the player. Then right-click the bike to ride it.
 */
public class MountainBikeItem extends Item {
    private final BikeType bikeType;
    public MountainBikeItem(Properties props) {
        this(props, BikeType.ENDURO);
    }
    public MountainBikeItem(Properties props, BikeType type) {
        super(props);
        this.bikeType = type;
    }

    public BikeType bikeType() {
        return bikeType;
    }

    @Override public net.minecraft.network.chat.Component getName(ItemStack stack) {
        String name=buildOf(stack).name();
        return name.isBlank() ? super.getName(stack) : net.minecraft.network.chat.Component.literal(name);
    }

    /** The build a bike item carries, sanitized for its type; the stock look when the stack has none (old bikes, commands). */
    public static BikeBuild buildOf(ItemStack stack) {
        BikeType type = stack.getItem() instanceof MountainBikeItem bike ? bike.bikeType : BikeType.ENDURO;
        boolean enduro = type == BikeType.ENDURO;
        BikeBuild build = stack.get(ModComponents.BIKE_BUILD.get());
        return build == null ? BikeBuild.defaultFor(enduro) : build.sanitized(enduro);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Vec3 pos = dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.projectOutOfSubLevel(level, ctx.getClickLocation());
        MountainBikeEntity bike = new MountainBikeEntity(ModEntities.MOUNTAIN_BIKE.get(), level);
        bike.setBikeType(bikeType);
        bike.applyFromItem(ctx.getItemInHand());
        bike.setPos(pos.x, pos.y, pos.z);
        float yaw = ctx.getPlayer() != null ? ctx.getPlayer().getYRot() : 0f;
        bike.setYRot(yaw);
        bike.yRotO = yaw;
        level.addFreshEntity(bike);
        if (ctx.getPlayer() != null && !ctx.getPlayer().getAbilities().instabuild) {
            ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }
}
