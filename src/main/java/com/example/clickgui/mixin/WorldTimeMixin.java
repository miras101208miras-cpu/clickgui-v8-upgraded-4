package com.example.clickgui.mixin;

import com.example.clickgui.ServerMetrics;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Слушает WorldTimeUpdateS2CPacket для точного клиентского расчёта Server TPS. */
@Mixin(ClientPlayNetworkHandler.class)
public class WorldTimeMixin {
    @Inject(method = "onWorldTimeUpdate", at = @At("HEAD"), require = 0)
    private void clickgui$onWorldTime(WorldTimeUpdateS2CPacket packet, CallbackInfo ci) {
        ServerMetrics.onTimePacket(packet.time());
    }
}
