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

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Inject(method="calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at=@At("RETURN"), cancellable=true)
    private void descentmtb$headphones(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (!(sound instanceof DeviceSound) && sound.getSource() != SoundSource.MASTER && sound.getSource() != SoundSource.VOICE)
            cir.setReturnValue(cir.getReturnValue() * AudioClient.worldVolume());
    }
}
