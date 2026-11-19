package com.example.clickgui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

public final class RenderUtil {
    private static final int CORNER = 64, ATLAS = CORNER * 2, GLOW_SZ = 64;
    private static final Identifier ROUND_ID = Identifier.of("clickgui", "dynamic/sdf_round");
    private static final Identifier GLOW_ID = Identifier.of("clickgui", "dynamic/soft_glow");
    private static boolean texturesTried, texturesOk;

    private RenderUtil() {}

    private static void ensureTextures() {
        if (texturesTried) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getTextureManager() == null) return;
        texturesTried = true;
        try {
            NativeImageBackedTexture roundTex = new NativeImageBackedTexture(() -> "clickgui_sdf_round", ATLAS, ATLAS, true);
            NativeImage ri = roundTex.getImage();
            if (ri != null) {
                for (int y = 0; y < CORNER; y++) {
                    for (int x = 0; x < CORNER; x++) {
                        putSdfCorner(ri, x, y, CORNER - (x + 0.5f), CORNER - (y + 0.5f));
                        putSdfCorner(ri, CORNER + x, y, x + 0.5f, CORNER - (y + 0.5f));
                        putSdfCorner(ri, x, CORNER + y, CORNER - (x + 0.5f), y + 0.5f);
                        putSdfCorner(ri, CORNER + x, CORNER + y, x + 0.5f, y + 0.5f);
                    }
                }
                roundTex.upload();
                mc.getTextureManager().registerTexture(ROUND_ID, (AbstractTexture) roundTex);
            }
            NativeImageBackedTexture glowTex = new NativeImageBackedTexture(() -> "clickgui_soft_glow", GLOW_SZ, GLOW_SZ, true);
            NativeImage gi = glowTex.getImage();
            if (gi != null) {
                float half = GLOW_SZ * 0.5f;
                for (int y = 0; y < GLOW_SZ; y++) {
                    for (int x = 0; x < GLOW_SZ; x++) {
                        float dx = (x + 0.5f - half) / half, dy = (y + 0.5f - half) / half;
                        float d2 = dx * dx + dy * dy;
                        float a = d2 >= 1f ? 0f : (float) Math.exp(-d2 * 4.2) * (1f - d2);
                        gi.setColorArgb(x, y, (Math.max(0, Math.min(255, Math.round(a * 255f))) << 24) | 0x00FFFFFF);
                    }
                }
                glowTex.upload();
                mc.getTextureManager().registerTexture(GLOW_ID, (AbstractTexture) glowTex);
            }
            texturesOk = true;
        } catch (Throwable ignored) {
            texturesOk = false;
        }
    }

    private static void putSdfCorner(NativeImage img, int px, int py, float dx, float dy) {
        float dist = (float) Math.hypot(dx, dy);
        float cov = MathUtil.clamp01((CORNER + 0.5f - dist) * 0.75f);
        img.setColorArgb(px, py, (Math.max(0, Math.min(255, Math.round(cov * 255f))) << 24) | 0x00FFFFFF);
    }

    public static void roundRect(DrawContext c, int x, int y, int w, int h, int r, int col) {
        if (w <= 0 || h <= 0 || ((col >>> 24) & 255) == 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) { c.fill(x, y, x + w, y + h, col); return; }
        ensureTextures();
        if (!texturesOk) { c.fill(x, y, x + w, y + h, col); return; }
        if (w > 2 * r) c.fill(x + r, y, x + w - r, y + h, col);
        if (h > 2 * r) {
            c.fill(x, y + r, x + r, y + h - r, col);
            c.fill(x + w - r, y + r, x + w, y + h - r, col);
        }
        c.drawTexture(RenderPipelines.GUI_TEXTURED, ROUND_ID, x, y, 0f, 0f, r, r, CORNER, CORNER, ATLAS, ATLAS, col);
        c.drawTexture(RenderPipelines.GUI_TEXTURED, ROUND_ID, x + w - r, y, CORNER, 0f, r, r, CORNER, CORNER, ATLAS, ATLAS, col);
        c.drawTexture(RenderPipelines.GUI_TEXTURED, ROUND_ID, x, y + h - r, 0f, CORNER, r, r, CORNER, CORNER, ATLAS, ATLAS, col);
        c.drawTexture(RenderPipelines.GUI_TEXTURED, ROUND_ID, x + w - r, y + h - r, CORNER, CORNER, r, r, CORNER, CORNER, ATLAS, ATLAS, col);
    }

    public static void roundGradientH(DrawContext c, int x, int y, int w, int h, int r, int cLeft, int cRight) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        ensureTextures();
        if (r == 0 || !texturesOk) {
            int step = Math.max(2, w / 12);
            for (int dx = 0; dx < w; dx += step) {
                int sw = Math.min(step, w - dx);
                c.fill(x + dx, y, x + dx + sw, y + h, ColorUtil.lerp(cLeft, cRight, (dx + sw * 0.5f) / Math.max(1f, w)));
            }
            return;
        }
        if (h > 2 * r) {
            c.fill(x, y + r, x + r, y + h - r, cLeft);
            c.fill(x + w - r, y + r, x + w, y + h - r, cRight);
        }
        c.drawTexture(RenderPipelines.GUI_TEXTURED, ROUND_ID, x, y, 0f, 0f, r, r, CORNER, CORNER, ATLAS, ATLAS, cLeft);
        c.drawTexture(RenderPipelines.GUI_TEXTURED, ROUND_ID, x, y + h - r, 0f, CORNER, r, r, CORNER, CORNER, ATLAS, ATLAS, cLeft);
        c.drawTexture(RenderPipelines.GUI_TEXTURED, ROUND_ID, x + w - r, y, CORNER, 0f, r, r, CORNER, CORNER, ATLAS, ATLAS, cRight);
        c.drawTexture(RenderPipelines.GUI_TEXTURED, ROUND_ID, x + w - r, y + h - r, CORNER, CORNER, r, r, CORNER, CORNER, ATLAS, ATLAS, cRight);
        int midX = x + r, midW = w - 2 * r;
        if (midW > 0) {
            int step = Math.max(2, midW / 16);
            for (int dx = 0; dx < midW; dx += step) {
                int sw = Math.min(step, midW - dx);
                c.fill(midX + dx, y, midX + dx + sw, y + h, ColorUtil.lerp(cLeft, cRight, (r + dx + sw * 0.5f) / Math.max(1f, w)));
            }
        }
    }

    public static void glass(DrawContext c, int x, int y, int w, int h, int r, int bg, int border) {
        roundRect(c, x - 1, y - 1, w + 2, h + 2, r + 1, border);
        roundRect(c, x, y, w, h, r, bg);
    }

    public static void vertex3d(VertexConsumer vc, Matrix4f mat, double camX, double camY, double camZ,
                                double x, double y, double z, int argb) {
        vc.vertex(mat, (float) (x - camX), (float) (y - camY), (float) (z - camZ)).color(argb);
    }

    public static void quad3d(VertexConsumer vc, Matrix4f mat, double camX, double camY, double camZ,
                              double ax, double ay, double az, double bx, double by, double bz,
                              double cx, double cy, double cz, double dx, double dy, double dz,
                              int ca, int cb, int cc, int cd) {
        vertex3d(vc, mat, camX, camY, camZ, ax, ay, az, ca);
        vertex3d(vc, mat, camX, camY, camZ, bx, by, bz, cb);
        vertex3d(vc, mat, camX, camY, camZ, cx, cy, cz, cc);
        vertex3d(vc, mat, camX, camY, camZ, dx, dy, dz, cd);

        vertex3d(vc, mat, camX, camY, camZ, dx, dy, dz, cd);
        vertex3d(vc, mat, camX, camY, camZ, cx, cy, cz, cc);
        vertex3d(vc, mat, camX, camY, camZ, bx, by, bz, cb);
        vertex3d(vc, mat, camX, camY, camZ, ax, ay, az, ca);
    }

    public static void ribbon3d(VertexConsumer vc, Matrix4f mat, double camX, double camY, double camZ,
                                double ax, double ay, double az, double bx, double by, double bz,
                                double halfWidth, int colA, int colB) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double mx = (ax + bx) * 0.5 - camX, my = (ay + by) * 0.5 - camY, mz = (az + bz) * 0.5 - camZ;
        double sx = dy * mz - dz * my;
        double sy = dz * mx - dx * mz;
        double sz = dx * my - dy * mx;
        double lenSq = sx * sx + sy * sy + sz * sz;
        if (lenSq < 1e-9) return;
        double scale = halfWidth / Math.sqrt(lenSq);
        sx *= scale; sy *= scale; sz *= scale;
        quad3d(vc, mat, camX, camY, camZ,
                ax + sx, ay + sy, az + sz, bx + sx, by + sy, bz + sz,
                bx - sx, by - sy, bz - sz, ax - sx, ay - sy, az - sz,
                colA, colB, colB, colA);
    }
}
