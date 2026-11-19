package com.example.clickgui.mixin;

import com.example.clickgui.Module;
import com.example.clickgui.ModuleManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Outlines: включает ванильный пост-процессный контур для игроков, мобов и предметов. */
@Mixin(MinecraftClient.class)
public abstract class OutlinesMixin {
    @Inject(method = "hasOutline", at = @At("HEAD"), cancellable = true, require = 0)
    private void clickgui$hasOutline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        Module m = ModuleManager.get("Outlines");
        if (!m.enabled || entity == null || entity == MinecraftClient.getInstance().player) return;
        if ((entity instanceof PlayerEntity && m.bool("Players"))
                || (entity instanceof MobEntity && m.bool("Mobs"))
                || (entity instanceof ItemEntity && m.bool("Items"))) {
            cir.setReturnValue(true);
        }
    }
}
