package com.example.clickgui.mixin;

import com.example.clickgui.ModuleManager;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Отменяет ванильную чёрную обводку блока при активном Block Outline Customizer. */
@Mixin(WorldRenderer.class)
public abstract class BlockOutlineMixin {
    @Inject(method = "drawBlockOutline", at = @At("HEAD"), cancellable = true, require = 0)
    private void clickgui$cancelOutline(CallbackInfo ci) {
        if (ModuleManager.get("Block Outline").enabled) {
            ci.cancel();
        }
    }
}
