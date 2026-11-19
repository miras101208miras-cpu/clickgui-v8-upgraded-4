package com.example.clickgui.mixin;

import com.example.clickgui.Module;
import com.example.clickgui.ModuleManager;
import com.example.clickgui.Setting;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Low Fire / No Fire Overlay. */
@Mixin(targets = "net.minecraft.client.gui.hud.InGameOverlayRenderer")
public abstract class FireOverlayMixin {
    @Unique
    private static final ThreadLocal<Boolean> CLICKGUI_FIRE_PUSHED = ThreadLocal.withInitial(() -> false);

    @Inject(
            method = "renderFireOverlay(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
            at = @At("HEAD"), cancellable = true, order = 1500, require = 0
    )
    private static void clickgui$fireHead(MatrixStack matrices, VertexConsumerProvider vcs, CallbackInfo ci) {
        CLICKGUI_FIRE_PUSHED.set(false);
        Module rt = ModuleManager.get("Render Tweaks");
        Module lf = ModuleManager.get("Low Fire");
        if ((rt.enabled && rt.bool("No Fire Overlay"))
                || (lf.enabled && "Off".equals(((Setting.Mode) lf.find("Mode")).get()))) {
            ci.cancel();
            return;
        }
        if (lf.enabled && matrices != null) {
            matrices.push();
            matrices.translate(0.0, lf.num("Offset"), 0.0);
            CLICKGUI_FIRE_PUSHED.set(true);
        }
    }

    @Inject(
            method = "renderFireOverlay(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V",
            at = @At("RETURN"), require = 0
    )
    private static void clickgui$fireReturn(MatrixStack matrices, VertexConsumerProvider vcs, CallbackInfo ci) {
        if (Boolean.TRUE.equals(CLICKGUI_FIRE_PUSHED.get())) {
            CLICKGUI_FIRE_PUSHED.set(false);
            if (matrices != null) {
                try { matrices.pop(); } catch (Throwable ignored) {}
            }
        }
    }
}
