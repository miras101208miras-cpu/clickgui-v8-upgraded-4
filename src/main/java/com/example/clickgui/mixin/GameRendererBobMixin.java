package com.example.clickgui.mixin;

import com.example.clickgui.ModuleManager;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererBobMixin {
    @Inject(method = "bobView(Lnet/minecraft/client/util/math/MatrixStack;F)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void clickgui$noWorldBob(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (ModuleManager.get("No World Bobbing").enabled) ci.cancel();
    }
}
