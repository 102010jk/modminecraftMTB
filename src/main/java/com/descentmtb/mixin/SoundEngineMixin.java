package com.descentmtb.mixin;

import com.descentmtb.client.audio.AudioClient;
import com.descentmtb.client.audio.DeviceSound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Worn headphones playing music muffle the world. Runs on the sound thread: it only reads factors the client thread
 * has already computed ({@link AudioClient#worldVolume()}), never the player.
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Inject(method="calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at=@At("RETURN"), cancellable=true)
    private void descentmtb$headphones(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (sound instanceof DeviceSound) return;
        SoundSource source = sound.getSource();
        if (source == SoundSource.MASTER || source == SoundSource.VOICE) return;
        float factor = source == SoundSource.PLAYERS ? AudioClient.playerVolume() : AudioClient.worldVolume();
        if (factor < 1) cir.setReturnValue(cir.getReturnValue() * factor);
    }
}
