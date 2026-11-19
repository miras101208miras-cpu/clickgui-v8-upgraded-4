package com.example.clickgui.mixin;

import com.example.clickgui.ExtraVisuals;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

@Mixin(BossBarHud.class)
public class BossBarHudMixin {
    @Shadow private Map<UUID, ClientBossBar> bossBars;

    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void clickgui$bossbar(DrawContext context, CallbackInfo ci) {
        if (ExtraVisuals.renderBossBars(context, bossBars)) ci.cancel();
    }
}
