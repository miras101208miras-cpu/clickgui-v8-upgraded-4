package com.example.clickgui.mixin;

import com.example.clickgui.ModuleManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Скрывает ванильные ники над игроками, когда активен Nametags Pro. */
@Mixin(EntityRenderer.class)
public abstract class NametagCancelMixin {
    @Inject(method = "hasLabel", at = @At("HEAD"), cancellable = true, require = 0)
    private void clickgui$hideVanillaName(Entity entity, double squaredDistanceToCamera, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof PlayerEntity && ModuleManager.get("Nametags Pro").enabled) {
            cir.setReturnValue(false);
        }
    }
}
