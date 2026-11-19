package com.example.clickgui.mixin;

import com.example.clickgui.ModuleManager;
import com.example.clickgui.SkyBox;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Цвет тумана под выбранное небо + No Fog. */
@Mixin(targets = "net.minecraft.client.render.fog.FogRenderer")
public abstract class FogColorMixin {
    @Inject(method = "getFogColor", at = @At("RETURN"), require = 0)
    private void clickgui$fogColor(CallbackInfoReturnable<Object> cir) {
        try {
            float[] c = SkyBox.fogColor();
            if (c != null && cir.getReturnValue() instanceof Vector4f v) v.set(c[0], c[1], c[2], v.w);
        } catch (Throwable ignored) {}
    }

    @ModifyVariable(method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V", at = @At("HEAD"), ordinal = 0, argsOnly = true, require = 0)
    private float clickgui$noFogEnvStart(float v) { return ModuleManager.get("No Fog").enabled ? 10000f : v; }

    @ModifyVariable(method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V", at = @At("HEAD"), ordinal = 1, argsOnly = true, require = 0)
    private float clickgui$noFogEnvEnd(float v) { return ModuleManager.get("No Fog").enabled ? 10001f : v; }

    @ModifyVariable(method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V", at = @At("HEAD"), ordinal = 2, argsOnly = true, require = 0)
    private float clickgui$noFogDistStart(float v) { return ModuleManager.get("No Fog").enabled ? 10000f : v; }

    @ModifyVariable(method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V", at = @At("HEAD"), ordinal = 3, argsOnly = true, require = 0)
    private float clickgui$noFogDistEnd(float v) { return ModuleManager.get("No Fog").enabled ? 10001f : v; }

    @ModifyVariable(method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V", at = @At("HEAD"), ordinal = 4, argsOnly = true, require = 0)
    private float clickgui$noFogSkyEnd(float v) { return ModuleManager.get("No Fog").enabled ? 10001f : v; }

    @ModifyVariable(method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V", at = @At("HEAD"), ordinal = 5, argsOnly = true, require = 0)
    private float clickgui$noFogCloudEnd(float v) { return ModuleManager.get("No Fog").enabled ? 10001f : v; }
}
