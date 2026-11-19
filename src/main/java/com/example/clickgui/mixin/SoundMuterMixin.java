package com.example.clickgui.mixin;

import com.example.clickgui.ExtraQoL;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundSystem.class)
public class SoundMuterMixin {
    @Inject(
            method = "play(Lnet/minecraft/client/sound/SoundInstance;)Lnet/minecraft/client/sound/SoundSystem$PlayResult;",
            at = @At("HEAD"), cancellable = true, require = 0
    )
    private void clickgui$muteSounds(SoundInstance sound, CallbackInfoReturnable<SoundSystem.PlayResult> cir) {
        if (ExtraQoL.shouldMuteSound(sound)) {
            cir.setReturnValue(SoundSystem.PlayResult.NOT_STARTED);
        }
    }
}
