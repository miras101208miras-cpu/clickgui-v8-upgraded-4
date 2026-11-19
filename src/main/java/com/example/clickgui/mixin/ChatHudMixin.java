package com.example.clickgui.mixin;

import com.example.clickgui.ExtraQoL;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatHud.class)
public class ChatHudMixin {
    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0
    )
    private Text clickgui$decorateChat(Text message) {
        try {
            return ExtraQoL.decorateIncomingMessage(message);
        } catch (Throwable ignored) {
            return message;
        }
    }
}
