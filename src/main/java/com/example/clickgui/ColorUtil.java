package com.example.clickgui;

public final class ColorUtil {
    private ColorUtil() {}

    public static int argb(int a, int r, int g, int b) {
        return ((a & 255) << 24) | ((r & 255) << 16) | ((g & 255) << 8) | (b & 255);
    }

    public static int withAlpha(int argb, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    public static int mulAlpha(int argb, float factor) {
        int a = (argb >>> 24) & 255;
        int na = Math.max(0, Math.min(255, Math.round(a * factor)));
        return (na << 24) | (argb & 0x00FFFFFF);
    }

    public static int lerp(int a, int b, float t) {
        t = MathUtil.clamp01(t);
        int aa = (a >>> 24) & 255, ab = (b >>> 24) & 255;
        int ra = (a >>> 16) & 255, rb = (b >>> 16) & 255;
        int ga = (a >>> 8) & 255,  gb = (b >>> 8) & 255;
        int ba = a & 255,          bb = b & 255;
        return ((int) (aa + (ab - aa) * t) << 24)
             | ((int) (ra + (rb - ra) * t) << 16)
             | ((int) (ga + (gb - ga) * t) << 8)
             |  (int) (ba + (bb - ba) * t);
    }

    public static int gradient(int c1, int c2, float t) { return lerp(c1, c2, t); }

    public static int wave(int c1, int c2, long now, float offset, double periodMs) {
        double phase = ((now % (long) Math.max(100.0, periodMs)) / periodMs + offset) * Math.PI * 2.0;
        float t = (float) (0.5 - 0.5 * Math.cos(phase));
        return lerp(c1, c2, t);
    }

    public static int hsbToRgb(float hue, float sat, float bri) {
        float h = hue - (float) Math.floor(hue);
        sat = MathUtil.clamp01(sat);
        bri = MathUtil.clamp01(bri);
        if (sat == 0f) {
            int v = Math.round(bri * 255f);
            return argb(255, v, v, v);
        }
        float sector = h * 6f;
        int i = (int) Math.floor(sector);
        float f = sector - i;
        float p = bri * (1f - sat);
        float q = bri * (1f - sat * f);
        float t = bri * (1f - sat * (1f - f));
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = bri; g = t;   b = p; }
            case 1 -> { r = q;   g = bri; b = p; }
            case 2 -> { r = p;   g = bri; b = t; }
            case 3 -> { r = p;   g = q;   b = bri; }
            case 4 -> { r = t;   g = p;   b = bri; }
            default -> { r = bri; g = p;  b = q; }
        }
        return argb(255, Math.round(r * 255f), Math.round(g * 255f), Math.round(b * 255f));
    }
}
