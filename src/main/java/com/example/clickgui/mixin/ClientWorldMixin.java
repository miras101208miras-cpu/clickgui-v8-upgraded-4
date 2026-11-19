package com.example.clickgui.mixin;

import com.example.clickgui.Module;
import com.example.clickgui.ModuleManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class ClientWorldMixin {
    @Inject(method = "addBlockBreakParticles", at = @At("HEAD"), cancellable = true, require = 0)
    private void clickgui$noMineBreak(BlockPos pos, BlockState state, CallbackInfo ci) {
        Module m = ModuleManager.get("Mine Particles Off");
        if (m.enabled && m.bool("Break")) ci.cancel();
    }
}
