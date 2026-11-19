package com.example.clickgui.mixin;

import com.example.clickgui.ModuleManager;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerMixin {
    @Shadow public float nauseaIntensity;
    @Shadow public float lastNauseaIntensity;

    @Inject(method = "tick()V", at = @At("TAIL"), require = 0)
    private void clickgui$noNausea(CallbackInfo ci) {
        if (ModuleManager.get("No Nausea Visual").enabled) {
            this.nauseaIntensity = 0f;
            this.lastNauseaIntensity = 0f;
        }
    }
}
