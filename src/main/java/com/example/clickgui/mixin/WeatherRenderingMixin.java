package com.example.clickgui.mixin;

import com.example.clickgui.Module;
import com.example.clickgui.ModuleManager;
import net.minecraft.client.render.WeatherRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherRendering.class)
public class WeatherRenderingMixin {
    @Inject(method = "buildPrecipitation", at = @At("HEAD"), cancellable = true, require = 0)
    private void clickgui$noBuildPrecip(CallbackInfo ci) {
        Module cw = ModuleManager.get("Clear Weather Visual");
        Module fb = ModuleManager.get("FPS Booster");
        if (cw.enabled || (fb.enabled && fb.bool("No Weather"))) ci.cancel();
    }

    @Inject(method = "renderPrecipitation", at = @At("HEAD"), cancellable = true, require = 0)
    private void clickgui$noRenderPrecip(CallbackInfo ci) {
        Module cw = ModuleManager.get("Clear Weather Visual");
        Module fb = ModuleManager.get("FPS Booster");
        if (cw.enabled || (fb.enabled && fb.bool("No Weather"))) ci.cancel();
    }

    @Inject(method = "addParticlesAndSound", at = @At("HEAD"), cancellable = true, require = 0)
    private void clickgui$noWeatherParticles(CallbackInfo ci) {
        Module fb = ModuleManager.get("FPS Booster");
        if (fb.enabled && fb.bool("No Weather")) ci.cancel();
    }
}
