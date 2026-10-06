package com.descentmtb.mixin;

import com.descentmtb.client.BikeCamera;
import com.descentmtb.client.BikeClientController;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The rider's yaw is re-set to the bike's every tick, so mouse look would be lost; it is handed to
 * {@link BikeCamera} instead, which orbits the follow cameras with it.
 */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {
    @Inject(method = "turn", at = @At("HEAD"))
    private void descentmtb$orbitCamera(double yRot, double xRot, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer && BikeClientController.riding() != null) BikeCamera.onMouseLook(yRot, xRot);
    }
}
