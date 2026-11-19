package com.example.clickgui.mixin;

import com.example.clickgui.Module;
import com.example.clickgui.ModuleManager;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraClipMixin {
    @Shadow private float cameraY;
    @Shadow private float lastCameraY;
    @Shadow private Entity focusedEntity;

    @ModifyVariable(method = "clipToSpace(D)D", at = @At("HEAD"), argsOnly = true, require = 0)
    private double clickgui$cameraDistance(double desired) {
        Module m = ModuleManager.get("Camera Distance");
        return m.enabled ? m.num("Distance") : desired;
    }

    @Inject(method = "clipToSpace(D)D", at = @At("HEAD"), cancellable = true, require = 0)
    private void clickgui$cameraClip(double desired, CallbackInfoReturnable<Double> cir) {
        if (ModuleManager.get("Camera Clip").enabled) {
            cir.setReturnValue(desired);
        }
    }

    @Inject(method = "updateEyeHeight", at = @At("HEAD"), cancellable = true, require = 0)
    private void clickgui$smoothSneak(CallbackInfo ci) {
        Module m = ModuleManager.get("Smooth Sneak");
        if (!m.enabled || this.focusedEntity == null) return;
        this.lastCameraY = this.cameraY;
        this.cameraY += (this.focusedEntity.getStandingEyeHeight() - this.cameraY) * (float) m.num("Speed");
        ci.cancel();
    }
}
