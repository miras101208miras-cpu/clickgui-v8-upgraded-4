package com.example.clickgui.mixin;

import com.example.clickgui.ShulkerPreview;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Отрисовывает сетку 9x3 шалкера вместо стандартного текстового тултипа. */
@Mixin(HandledScreen.class)
public class HandledScreenMixin {
    @Inject(method = "drawMouseoverTooltip", at = @At("HEAD"), cancellable = true, require = 0)
    private void clickgui$shulkerTooltip(DrawContext context, int x, int y, CallbackInfo ci) {
        Slot slot = ((HandledScreenAccessor) this).clickgui$getFocusedSlot();
        if (slot != null && slot.hasStack() && ShulkerPreview.tryRender(context, slot.getStack(), x, y)) {
            ci.cancel();
        }
    }
}
