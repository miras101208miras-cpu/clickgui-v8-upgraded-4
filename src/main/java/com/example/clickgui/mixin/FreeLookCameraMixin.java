package com.example.clickgui.mixin;

import com.example.clickgui.FreeLook;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Подмена углов камеры в Camera.update при активном Free Look. */
@Mixin(Camera.class)
public abstract class FreeLookCameraMixin {
    @ModifyArgs(
        method = "update",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"),
        require = 0
    )
    private void clickgui$freeLookCamera(Args args) {
        if (FreeLook.isActive()) {
            args.set(0, FreeLook.cameraYaw);
            args.set(1, FreeLook.cameraPitch);
        }
    }
}
