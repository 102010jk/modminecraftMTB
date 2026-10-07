package com.descentmtb.mixin;

import com.descentmtb.client.BikeCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands the camera to {@link BikeCamera} while the local player rides a bike. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yaw, float pitch, float roll);

    @Shadow
    protected abstract void setPosition(Vec3 pos);

    @Shadow
    private boolean detached;

    @Shadow @Final
    private Quaternionf rotation;

    @Shadow @Final
    private Vector3f forwards;

    @Shadow @Final
    private Vector3f up;

    @Shadow @Final
    private Vector3f left;

    @Inject(method = "setup", at = @At("TAIL"))
    private void descentmtb$bikeCamera(BlockGetter level, Entity entity, boolean detached, boolean mirror,
                                       float partialTick, CallbackInfo ci) {
        BikeCamera.View v = BikeCamera.compute(partialTick);
        if (v != null) {
            setRotation(v.yaw(), v.pitch(), v.roll());
            double[] q = v.rotation();
            if (q != null) {
                // a flip: the exact orientation, not the Euler angles above (they cannot go past +-90 degrees of
                // pitch); the direction vectors are what sound, fog and culling read, so keep them in step
                rotation.set((float) q[0], (float) q[1], (float) q[2], (float) q[3]);
                forwards.set(0f, 0f, -1f).rotate(rotation);
                up.set(0f, 1f, 0f).rotate(rotation);
                left.set(-1f, 0f, 0f).rotate(rotation);
            }
            setPosition(v.pos());
            // "detached" makes the level renderer draw the local player, so in the helmet
            // cam you see your own arms on the bars and legs on the pedals (head hidden)
            detached = true;

        }
    }
}
