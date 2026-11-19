package com.example.clickgui.mixin;

import com.example.clickgui.Module;
import com.example.clickgui.ModuleManager;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleManager.class)
public class ParticlesFilterMixin {
    @Inject(method = "addBlockBreakingParticles", at = @At("HEAD"), cancellable = true, require = 0)
    private void clickgui$noMineHit(BlockPos pos, Direction direction, CallbackInfo ci) {
        Module m = ModuleManager.get("Mine Particles Off");
        if (m.enabled && m.bool("Hit Block")) ci.cancel();
    }

    @Inject(method = "addBlockBreakParticles", at = @At("HEAD"), cancellable = true, require = 0)
    private void clickgui$noMineBreak(BlockPos pos, BlockState state, CallbackInfo ci) {
        Module m = ModuleManager.get("Mine Particles Off");
        if (m.enabled && m.bool("Break")) ci.cancel();
    }

    @Inject(
            method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("RETURN"), require = 0
    )
    private void clickgui$noPotionSwirls(ParticleEffect parameters, double x, double y, double z,
                                         double vx, double vy, double vz, CallbackInfoReturnable<Particle> cir) {
        Particle ret = cir.getReturnValue();
        if (ret == null || parameters == null || parameters.getType() != ParticleTypes.ENTITY_EFFECT) return;
        Module m = ModuleManager.get("Potion Swirl Remover");
        if (!m.enabled) return;
        if (!m.bool("Self Only")) {
            ret.markDead();
            return;
        }
        var p = MinecraftClient.getInstance().player;
        if (p != null && Math.abs(x - p.getX()) < 1.5 && Math.abs(z - p.getZ()) < 1.5 && Math.abs(y - p.getY()) < 2.6) {
            ret.markDead();
        }
    }
}
