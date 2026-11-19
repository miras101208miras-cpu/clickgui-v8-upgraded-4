package com.example.clickgui;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Custom Sky / World Customizer.
 * Небо = заранее отрисованная панорама 360 (PNG), натянутая на купол вокруг камеры.
 * Один draw-call и ~1000 полигонов за кадр, поэтому FPS почти не меняется.
 * Рисуется в самом начале кадра без записи глубины: мир потом рисуется поверх.
 */
public final class SkyBox {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final String[] FILES = {"aurora_night", "galaxy", "sakura_sunset", "blood_moon", "neon_dusk", "cotton_candy", "black_hole", "ice_dawn", "thunderstorm", "golden_hour", "moonlit_night", "pink_nebula", "violet_cosmos", "pastel_planets", "anime_clouds"};
    /** Цвет горизонта каждого неба (для цвета тумана). */
    private static final float[][] HORIZON = {
            {0.107f, 0.240f, 0.293f},
            {0.161f, 0.278f, 0.216f},
            {0.958f, 0.764f, 0.594f},
            {0.357f, 0.084f, 0.084f},
            {0.954f, 0.545f, 0.555f},
            {1.000f, 0.984f, 1.000f},
            {0.055f, 0.038f, 0.095f},
            {1.000f, 1.000f, 0.979f},
            {0.345f, 0.360f, 0.416f},
            {0.997f, 0.915f, 0.634f},
            {0.172f, 0.267f, 0.408f},
            {0.113f, 0.044f, 0.130f},
            {0.090f, 0.039f, 0.220f},
            {0.796f, 0.914f, 0.976f},
            {0.494f, 0.729f, 0.906f}};
    private static final Identifier WHITE = Identifier.of("clickgui", "textures/sky/white.png");
    private static final int LON = 32, LAT = 16;
    private static final float R = 50f;

    public static void init() {
        WorldRenderEvents.START_MAIN.register(ctx -> render(ctx.worldState().cameraRenderState.orientation));
    }

    private static boolean active() {
        return mc.world != null && mc.world.getRegistryKey() != World.NETHER
                && (ModuleManager.get("Custom Sky").enabled || ModuleManager.get("World Customizer").enabled);
    }

    /** Цвет тумана r,g,b (0..1) или null, если не меняем. */
    public static float[] fogColor() {
        if (!active()) return null;
        Module sky = ModuleManager.get("Custom Sky");
        if (sky.enabled) return HORIZON[((Setting.Mode) sky.find("Sky")).index];
        Module wc = ModuleManager.get("World Customizer");
        return new float[]{(float) wc.num("Fog R") / 255f, (float) wc.num("Fog G") / 255f, (float) wc.num("Fog B") / 255f};
    }

    private static void render(Quaternionf orientation) {
        if (!active()) return;
        Module sky = ModuleManager.get("Custom Sky"), wc = ModuleManager.get("World Customizer");
        boolean tex = sky.enabled;
        Identifier id = tex ? Identifier.of("clickgui", "textures/sky/" + FILES[((Setting.Mode) sky.find("Sky")).index] + ".png") : WHITE;
        int top = tex ? 0xFFFFFFFF : rgb(wc, "Sky"), hor = tex ? 0xFFFFFFFF : rgb(wc, "Fog");
        double spin = (tex && sky.num("Spin") > 0) ? (System.currentTimeMillis() / 1000.0 * sky.num("Spin") * 0.02) % (Math.PI * 2) : 0;

        RenderLayer layer = RenderLayers.textSeeThrough(id);   // без тумана и без записи глубины
        VertexConsumerProvider.Immediate imm = mc.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer vc = imm.getBuffer(layer);
        // поворот камеры игра уже применяет сама, иначе небо "ездит" за взглядом
        Matrix4f m = new Matrix4f();

        for (int j = 0; j < LAT; j++) {
            float v1 = (float) j / LAT, v2 = (float) (j + 1) / LAT;
            int c1 = tex ? 0xFFFFFFFF : shade(top, hor, v1), c2 = tex ? 0xFFFFFFFF : shade(top, hor, v2);
            for (int i = 0; i < LON; i++) {
                float u1 = (float) i / LON, u2 = (float) (i + 1) / LON;
                float[] a = pos(u1, v1, spin), b = pos(u2, v1, spin), c = pos(u2, v2, spin), d = pos(u1, v2, spin);
                // двусторонний quad
                vtx(vc, m, a, u1, v1, c1); vtx(vc, m, b, u2, v1, c1); vtx(vc, m, c, u2, v2, c2); vtx(vc, m, d, u1, v2, c2);
                vtx(vc, m, d, u1, v2, c2); vtx(vc, m, c, u2, v2, c2); vtx(vc, m, b, u2, v1, c1); vtx(vc, m, a, u1, v1, c1);
            }
        }
        imm.draw(layer);
    }

    private static float[] pos(float u, float v, double spin) {
        double lon = u * Math.PI * 2 + spin, lat = Math.PI * (0.5 - v);
        double cl = Math.cos(lat);
        return new float[]{(float) (R * cl * Math.cos(lon)), (float) (R * Math.sin(lat)), (float) (R * cl * Math.sin(lon))};
    }

    private static void vtx(VertexConsumer vc, Matrix4f m, float[] p, float u, float v, int col) {
        vc.vertex(m, p[0], p[1], p[2]).color(col).texture(u, v).light(0xF000F0);
    }

    private static int rgb(Module m, String p) {
        return 0xFF000000 | ((int) m.num(p + " R") << 16) | ((int) m.num(p + " G") << 8) | (int) m.num(p + " B");
    }

    /** Градиент для World Customizer: сверху Sky, у горизонта Fog, ниже темнее. */
    private static int shade(int top, int hor, float v) {
        if (v <= 0.5f) return ClickGuiScreen.lerp(top, hor, (float) Math.pow(v / 0.5f, 1.5));
        return ClickGuiScreen.lerp(hor, 0xFF000000 | ((hor >> 1) & 0x7F7F7F), (v - 0.5f) / 0.5f);
    }
}
