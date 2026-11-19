package com.example.clickgui.mixin;

import com.example.clickgui.HudCombat;
import com.example.clickgui.ModuleManager;
import com.example.clickgui.ScoreboardHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true, require = 0)
    private void clickgui$hotbar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (ModuleManager.get("Hotbar Customizer").enabled) {
            HudCombat.renderCustomHotbar(context, tickCounter.getTickProgress(true));
            ci.cancel();
        }
    }

    @Inject(
            method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("HEAD"), cancellable = true, require = 0
    )
    private void clickgui$scoreboard(DrawContext context, ScoreboardObjective objective, CallbackInfo ci) {
        if (ModuleManager.get("Scoreboard").enabled) {
            ScoreboardHud.render(context, objective);
            ci.cancel();
        }
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void clickgui$potionOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        var m = ModuleManager.get("Potion HUD");
        if (m.enabled && m.bool("Hide Vanilla")) ci.cancel();
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void clickgui$crosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (ModuleManager.get("Custom Crosshair").enabled) ci.cancel();
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void clickgui$noPortal(DrawContext context, float tickDelta, CallbackInfo ci) {
        if (ModuleManager.get("No Portal Overlay").enabled) ci.cancel();
    }

    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true, require = 0)
    private void clickgui$noPumpkin(DrawContext context, Identifier texture, float opacity, CallbackInfo ci) {
        if (ModuleManager.get("No Pumpkin Overlay").enabled && texture != null && texture.getPath().contains("pumpkin")) {
            ci.cancel();
        }
    }
}
