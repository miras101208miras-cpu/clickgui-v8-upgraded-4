package com.example.clickgui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class MathUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Vector3f TMP_VEC = new Vector3f();
    private static final Quaternionf TMP_QUAT = new Quaternionf();

    private MathUtil() {}

    public static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }
    public static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    public static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
    public static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    public static double lerp(double a, double b, double t) { return a + (b - a) * t; }

    public static float expSmooth(float cur, float target, float speed, float dt) {
        float k = 1f - (float) Math.exp(-Math.max(0.01f, speed) * Math.max(0f, dt));
        float next = cur + (target - cur) * k;
        return Math.abs(target - next) < 0.0005f ? target : next;
    }

    public static double expSmooth(double cur, double target, double speed, double dt) {
        double k = 1.0 - Math.exp(-Math.max(0.01, speed) * Math.max(0.0, dt));
        double next = cur + (target - cur) * k;
        return Math.abs(target - next) < 0.0005 ? target : next;
    }

    public static boolean worldToScreen(double wx, double wy, double wz, int sw, int sh, float[] out) {
        if (!Effects.camReady || sw <= 0 || sh <= 0) return false;
        Vec3d cam = Effects.camPos();
        TMP_VEC.set((float) (wx - cam.x), (float) (wy - cam.y), (float) (wz - cam.z));
        TMP_QUAT.set(Effects.camRot).conjugate().transform(TMP_VEC);
        if (TMP_VEC.z > -0.05f) return false;
        double fov = CameraController.effectiveFov(mc.options.getFov().getValue());
        double f = 1.0 / Math.tan(Math.toRadians(fov) * 0.5);
        double aspect = (double) sw / sh;
        double nx = TMP_VEC.x * f / aspect / -TMP_VEC.z;
        double ny = TMP_VEC.y * f / -TMP_VEC.z;
        if (Math.abs(nx) > 1.25 || Math.abs(ny) > 1.25) return false;
        out[0] = (float) ((nx + 1.0) * 0.5 * sw);
        out[1] = (float) ((1.0 - ny) * 0.5 * sh);
        return true;
    }
}
