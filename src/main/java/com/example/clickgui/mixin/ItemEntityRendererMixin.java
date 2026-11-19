package com.example.clickgui.mixin;

import com.example.clickgui.Module;
import com.example.clickgui.ModuleManager;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {
    @Unique
    private static final ThreadLocal<Boolean> CLICKGUI_ITEM_PUSHED = ThreadLocal.withInitial(() -> false);

    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V",
            at = @At("TAIL"), require = 0
    )
    private void clickgui$itemState(ItemEntity entity, ItemEntityRenderState state, float tickProgress, CallbackInfo ci) {
        if (state != null && ModuleManager.get("Item Physics").enabled) {
            state.uniqueOffset = 0f;
            state.age = 0f;
        }
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD"), order = 1500, require = 0
    )
    private void clickgui$itemHead(ItemEntityRenderState state, MatrixStack matrices,
                                   OrderedRenderCommandQueue queue, CameraRenderState cam, CallbackInfo ci) {
        CLICKGUI_ITEM_PUSHED.set(false);
        Module m = ModuleManager.get("Item Physics");
        if (m.enabled && m.bool("Flat Drops") && matrices != null) {
            matrices.push();
            matrices.translate(0.0, 0.03, -0.18);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));
            CLICKGUI_ITEM_PUSHED.set(true);
        }
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("RETURN"), require = 0
    )
    private void clickgui$itemReturn(ItemEntityRenderState state, MatrixStack matrices,
                                     OrderedRenderCommandQueue queue, CameraRenderState cam, CallbackInfo ci) {
        if (Boolean.TRUE.equals(CLICKGUI_ITEM_PUSHED.get())) {
            CLICKGUI_ITEM_PUSHED.set(false);
            if (matrices != null) {
                try { matrices.pop(); } catch (Throwable ignored) {}
            }
        }
    }
}
