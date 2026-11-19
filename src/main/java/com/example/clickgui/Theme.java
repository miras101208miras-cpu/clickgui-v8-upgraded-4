package com.example.clickgui;

public final class Theme {
    public static final String[] NAMES = {"Sakura", "Ocean", "Amethyst", "Inferno", "Emerald", "Custom"};
    private static final int[] HEX1 = {0xFFFF4FCB, 0xFF00BFFF, 0xFFA855F7, 0xFFFF453A, 0xFF39E75F};
    private static final int[] HEX2 = {0xFFFF9AEE, 0xFF38BDF8, 0xFF6366F1, 0xFFFF9F0A, 0xFF10B981};

    private Theme() {}

    public static int primary() {
        long now = System.currentTimeMillis();
        Module m = ModuleManager.get("Theme");
        if (m.bool("Rainbow")) return ColorUtil.hsbToRgb((now % 4000L) / 4000f, 0.72f, 1f);
        int idx = ((Setting.Mode) m.find("Theme")).index;
        if (idx >= 0 && idx < HEX1.length) return HEX1[idx];
        float h1 = m.find("Hue 1") != null ? (float) m.num("Hue 1") : (float) m.num("Hue");
        return ColorUtil.hsbToRgb(h1, 0.72f, 1f);
    }

    public static int secondary() {
        long now = System.currentTimeMillis();
        Module m = ModuleManager.get("Theme");
        if (m.bool("Rainbow")) return ColorUtil.hsbToRgb(((now % 4000L) / 4000f) + 0.16f, 0.72f, 1f);
        int idx = ((Setting.Mode) m.find("Theme")).index;
        if (idx >= 0 && idx < HEX2.length) return HEX2[idx];
        float h2 = m.find("Hue 2") != null ? (float) m.num("Hue 2") : ((float) m.num("Hue") + 0.12f);
        return ColorUtil.hsbToRgb(h2, 0.72f, 1f);
    }

    public static int accent() { return primary(); }
    public static int gradient(float t) { return ColorUtil.gradient(primary(), secondary(), t); }

    public static int color(long now, float offset, int alpha) {
        Module m = ModuleManager.get("Theme");
        int rgb = m.bool("Rainbow")
                ? ColorUtil.hsbToRgb(((now % 4000L) / 4000f) + offset, 0.72f, 1f)
                : ColorUtil.wave(primary(), secondary(), now, offset, 3000.0);
        return ColorUtil.withAlpha(rgb, alpha);
    }
}
