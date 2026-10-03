package com.descentmtb.mixin;
import com.descentmtb.client.RagdollClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
 @Inject(method="setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",at=@At("HEAD"),cancellable=true)
 private void descentmtb$crashRotation(AbstractClientPlayer p,PoseStack pose,float age,float yaw,float pt,float scale,CallbackInfo ci){if(RagdollClient.active(p)){pose.mulPose(Axis.YP.rotationDegrees(180-yaw));ci.cancel();}}
}
