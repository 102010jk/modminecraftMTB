package com.descentmtb.mixin;

import com.descentmtb.client.RiderPose;
import com.descentmtb.entity.MountainBikeEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Re-poses the player on a bike after vanilla animated it (see {@link RiderPose}). */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void descentmtb$ridePose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                     float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (entity.getVehicle() instanceof MountainBikeEntity bike) {
            float pt = ageInTicks - entity.tickCount;
            RiderPose.apply((PlayerModel<?>) (Object) this, entity, bike, Math.max(0f, Math.min(1f, pt)));
        } else {
            RiderPose.reset((PlayerModel<?>) (Object) this);
        }
    }
}
