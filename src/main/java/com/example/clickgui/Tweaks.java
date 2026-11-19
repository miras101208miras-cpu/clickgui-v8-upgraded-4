package com.example.clickgui;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticlesMode;

public final class Tweaks {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static Double savedTilt;
    private static Boolean savedSmooth;
    private static Integer savedFps;
    private static Boolean savedShadows;
    private static ParticlesMode savedParticles;
    private static Integer savedBlend;

    public static void init() { ClientTickEvents.END_CLIENT_TICK.register(c -> tick()); }

    private static void tick() {
        // 1. No Hurt Cam / Hurt Cam Scale
        Module rt = ModuleManager.get("Render Tweaks");
        Module hc = ModuleManager.get("Hurt Cam Scale");
        var tilt = mc.options.getDamageTiltStrength();
        if (rt.enabled && rt.bool("No Hurt Cam")) {
            if (savedTilt == null) savedTilt = tilt.getValue();
            if (tilt.getValue() != 0.0) tilt.setValue(0.0);
        } else if (hc.enabled) {
            if (savedTilt == null) savedTilt = tilt.getValue();
            double want = hc.num("Strength") / 100.0;
            if (Math.abs(tilt.getValue() - want) > 0.01) tilt.setValue(want);
        } else if (savedTilt != null) {
            tilt.setValue(savedTilt);
            savedTilt = null;
        }

        // 2. Smooth Camera
        Module sc = ModuleManager.get("Smooth Camera");
        if (sc.enabled) {
            if (savedSmooth == null) savedSmooth = mc.options.smoothCameraEnabled;
            mc.options.smoothCameraEnabled = true;
        } else if (savedSmooth != null) {
            mc.options.smoothCameraEnabled = savedSmooth;
            savedSmooth = null;
        }

        // 3. Unfocused FPS
        Module uf = ModuleManager.get("Unfocused FPS");
        var fpsOpt = mc.options.getMaxFps();
        if (uf.enabled && !mc.isWindowFocused()) {
            if (savedFps == null) savedFps = fpsOpt.getValue();
            if (fpsOpt.getValue() != 20) fpsOpt.setValue(20);
        } else if (savedFps != null) {
            fpsOpt.setValue(savedFps);
            savedFps = null;
        }

        // 3b. FPS Booster (1-Click optimization preset with clean restore)
        Module fb = ModuleManager.get("FPS Booster");
        var shadOpt = mc.options.getEntityShadows();
        if (fb.enabled && fb.bool("No Shadows")) {
            if (savedShadows == null) savedShadows = shadOpt.getValue();
            if (Boolean.TRUE.equals(shadOpt.getValue())) shadOpt.setValue(false);
        } else if (savedShadows != null) {
            shadOpt.setValue(savedShadows);
            savedShadows = null;
        }
        var partOpt = mc.options.getParticles();
        if (fb.enabled && fb.bool("Fast Particles")) {
            if (savedParticles == null) savedParticles = partOpt.getValue();
            if (partOpt.getValue() != ParticlesMode.MINIMAL) partOpt.setValue(ParticlesMode.MINIMAL);
        } else if (savedParticles != null) {
            partOpt.setValue(savedParticles);
            savedParticles = null;
        }
        if (mc.world != null && mc.worldRenderer != null) {
            var blendOpt = mc.options.getBiomeBlendRadius();
            if (fb.enabled && fb.bool("Reduce Blend")) {
                if (savedBlend == null) savedBlend = blendOpt.getValue();
                if (blendOpt.getValue() != 0) blendOpt.setValue(0);
            } else if (savedBlend != null) {
                blendOpt.setValue(savedBlend);
                savedBlend = null;
            }
        }

        if (mc.world == null) return;

        // 4. Time Changer
        Module tc = ModuleManager.get("Time Changer");
        if (tc.enabled) {
            long ticks = (long) ((((tc.num("Time") - 6.0) * 1000) % 24000 + 24000) % 24000);
            mc.world.getLevelProperties().setTimeOfDay(ticks);
        }

        // 5. Weather Changer
        Module wc = ModuleManager.get("Weather Changer");
        if (wc.enabled) {
            String w = ((Setting.Mode) wc.find("Weather")).get();
            mc.world.setRainGradient(w.equals("Clear") ? 0f : 1f);
            mc.world.setThunderGradient(w.equals("Thunder") ? 1f : 0f);
        }
    }
}
