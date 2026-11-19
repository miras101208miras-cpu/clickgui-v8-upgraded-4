package com.example.clickgui.mixin;

import com.example.clickgui.Module;
import com.example.clickgui.ModuleManager;
import com.example.clickgui.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Outlines: окрашивает контур сущностей в цвет темы или по типу сущности. */
@Mixin(Entity.class)
public abstract class OutlinesColorMixin {
    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true, require = 0)
    private void clickgui$outlineColor(CallbackInfoReturnable<Integer> cir) {
        Module m = ModuleManager.get("Outlines");
        Entity self = (Entity) (Object) this;
        if (!m.enabled || self == MinecraftClient.getInstance().player) return;
        boolean match = (self instanceof PlayerEntity && m.bool("Players"))
                || (self instanceof MobEntity && m.bool("Mobs"))
                || (self instanceof ItemEntity && m.bool("Items"));
        if (!match) return;
        if (m.bool("Theme Color")) {
            cir.setReturnValue(Theme.accent() & 0x00FFFFFF);
        } else if (self instanceof PlayerEntity) {
            cir.setReturnValue(0x38BDF8);
        } else if (self instanceof ItemEntity) {
            cir.setReturnValue(0xFBBF24);
        } else {
            cir.setReturnValue(0xFB7185);
        }
    }
}
