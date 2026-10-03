package com.descentmtb.item;

import com.descentmtb.entity.MountainBikeEntity;
import com.descentmtb.entity.BikeType;
import com.descentmtb.registry.ModEntities;
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

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Vec3 pos = dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.projectOutOfSubLevel(level, ctx.getClickLocation());
        MountainBikeEntity bike = new MountainBikeEntity(ModEntities.MOUNTAIN_BIKE.get(), level);
        bike.setBikeType(bikeType);
        var tune = ctx.getItemInHand().getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        if (tune.contains("FrontPsi")) bike.setPressure(tune.getFloat("FrontPsi"), tune.getFloat("RearPsi"), tune.getFloat("ForkPsi"));
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
