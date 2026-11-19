package com.example.clickgui.mixin;

import com.example.clickgui.Hud;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void clickgui$renderChatHudOverlay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        try {
            Screen self = (Screen) (Object) this;
            Hud.renderChatHudOverlay(context, mouseX, mouseY, self.width, self.height);
        } catch (Throwable ignored) {}
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void clickgui$chatMouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        try {
            Screen self = (Screen) (Object) this;
            if (Hud.handleChatMouseClick(click.x(), click.y(), click.button(), self.width, self.height)) {
                cir.setReturnValue(true);
            }
        } catch (Throwable ignored) {}
    }

    @Inject(method = "removed", at = @At("HEAD"), require = 0)
    private void clickgui$chatRemoved(CallbackInfo ci) {
        try {
            Hud.finishChatDrag();
        } catch (Throwable ignored) {}
    }
}
