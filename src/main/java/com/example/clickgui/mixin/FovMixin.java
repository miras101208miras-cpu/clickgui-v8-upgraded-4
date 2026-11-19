package com.example.clickgui.mixin;

import com.example.clickgui.CameraController;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class FovMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void clickgui$modifyFov(Camera camera, float tickProgress, boolean changingFov,
                                    CallbackInfoReturnable<Float> cir) {
        if (!changingFov) return;
        Float ret = cir.getReturnValue();
        if (ret != null) cir.setReturnValue(CameraController.apply(ret));
    }
}
