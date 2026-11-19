package com.example.clickgui.mixin;

import com.example.clickgui.FreeLook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Перехват вращения мыши на локальном игроке при активном Free Look. */
@Mixin(Entity.class)
public abstract class FreeLookMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true, require = 0)
    private void clickgui$freeLookMouse(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if ((Object) this == MinecraftClient.getInstance().player && FreeLook.isActive()) {
            FreeLook.onMouseMove(cursorDeltaX, cursorDeltaY);
            ci.cancel();
        }
    }
}
