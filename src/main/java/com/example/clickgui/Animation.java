package com.example.clickgui;

public final class Animation {
    private static long lastFrameNs = System.nanoTime();
    private static float frameDt = 1f / 60f;

    private Animation() {}

    public static void beginFrame() {
        long now = System.nanoTime();
        float dt = (now - lastFrameNs) / 1_000_000_000f;
        lastFrameNs = now;
        frameDt = MathUtil.clamp(dt, 0.0005f, 0.05f);
    }

    public static float dt() { return frameDt; }
}
