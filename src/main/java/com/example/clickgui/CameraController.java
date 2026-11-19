package com.example.clickgui;

public final class CameraController {
    private static float currentFov = 70f;
    private static boolean initialized;

    private CameraController() {}

    public static float apply(double vanillaFov) {
        float target = computeTarget(vanillaFov);
        if (!initialized) { currentFov = target; initialized = true; }
        else currentFov = MathUtil.expSmooth(currentFov, target, 18f, Animation.dt());
        return currentFov;
    }

    public static float effectiveFov(double vanillaFov) {
        return initialized ? currentFov : computeTarget(vanillaFov);
    }

    private static float computeTarget(double vanillaFov) {
        Module fovMod = ModuleManager.get("FOV Changer");
        double base = fovMod.enabled ? fovMod.num("FOV") : vanillaFov;
        Module zoom = ModuleManager.get("Zoom");
        if (zoom.enabled) base /= Math.max(1.0, zoom.num("Zoom"));
        return (float) MathUtil.clamp(base, 8.0, 150.0);
    }
}
