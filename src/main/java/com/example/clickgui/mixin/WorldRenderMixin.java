package com.example.clickgui.mixin;

import com.example.clickgui.Module;
import com.example.clickgui.ModuleManager;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRenderMixin {
    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true, require = 0)
    private void clickgui$clearWeather(CallbackInfo ci) {
        Module cw = ModuleManager.get("Clear Weather Visual");
        Module fb = ModuleManager.get("FPS Booster");
        if (cw.enabled || (fb.enabled && fb.bool("No Weather"))) {
            ci.cancel();
        }
    }
}
