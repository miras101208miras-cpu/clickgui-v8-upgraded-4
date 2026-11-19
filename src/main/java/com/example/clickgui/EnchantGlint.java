package com.example.clickgui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.ResourceTexture;
import net.minecraft.util.Identifier;

import java.awt.Color;
import java.io.InputStream;

/**
 * Enchant Glint Color (Theme, Rainbow, Neon, Gold):
 * Читает маску яркости ванильных текстур глинта один раз, кэширует её в int[]
 * и перекрашивает только при изменении цвета (раз в 100мс для анимаций).
 */
public final class EnchantGlint {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static int[] itemMask, entityMask;
    private static int itemW, itemH, entityW, entityH;
    private static NativeImageBackedTexture itemTex, entityTex;
    private static boolean activeApplied;
    private static int lastColor = -1;
    private static long lastUpdateMs;

    public static void tick() {
        if (mc.getTextureManager() == null || mc.getResourceManager() == null) return;
        Module m = ModuleManager.get("Enchant Glint");
        if (!m.enabled) {
            if (activeApplied) {
                restore(ItemRenderer.ITEM_ENCHANTMENT_GLINT);
                restore(ItemRenderer.ENTITY_ENCHANTMENT_GLINT);
                activeApplied = false;
                lastColor = -1;
            }
            return;
        }

        long now = System.currentTimeMillis();
        String mode = ((Setting.Mode) m.find("Mode")).get();
        boolean animated = mode.equals("Rainbow") || (mode.equals("Theme") && ModuleManager.get("Theme").bool("Rainbow"));
        if (activeApplied && animated && now - lastUpdateMs < 100) return;

        int col = computeColor(mode, now);
        if (activeApplied && col == lastColor) return;
        lastColor = col;
        lastUpdateMs = now;

        if (itemMask == null) {
            itemMask = loadMask(ItemRenderer.ITEM_ENCHANTMENT_GLINT, true);
            entityMask = loadMask(ItemRenderer.ENTITY_ENCHANTMENT_GLINT, false);
        }
        applyMask(ItemRenderer.ITEM_ENCHANTMENT_GLINT, itemMask, itemW, itemH, col, true);
        applyMask(ItemRenderer.ENTITY_ENCHANTMENT_GLINT, entityMask, entityW, entityH, col, false);
        activeApplied = true;
    }

    private static int computeColor(String mode, long now) {
        return switch (mode) {
            case "Rainbow" -> 0xFF000000 | (Color.HSBtoRGB((now % 3000) / 3000f, 0.85f, 1f) & 0xFFFFFF);
            case "Neon" -> 0xFF00F5D4;
            case "Gold" -> 0xFFFFD166;
            default -> Theme.accent();
        };
    }

    private static int[] loadMask(Identifier id, boolean isItem) {
        try {
            var res = mc.getResourceManager().getResource(id);
            if (res.isEmpty()) return new int[0];
            try (InputStream in = res.get().getInputStream(); NativeImage img = NativeImage.read(in)) {
                int w = img.getWidth(), h = img.getHeight();
                if (isItem) { itemW = w; itemH = h; } else { entityW = w; entityH = h; }
                int[] mask = new int[w * h];
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int argb = img.getColorArgb(x, y);
                        int lum = (((argb >> 16) & 255) + ((argb >> 8) & 255) + (argb & 255)) / 3;
                        mask[y * w + x] = ((argb >>> 24) << 8) | lum;
                    }
                }
                return mask;
            }
        } catch (Exception ex) {
            return new int[0];
        }
    }

    private static void applyMask(Identifier id, int[] mask, int w, int h, int rgb, boolean isItem) {
        if (mask == null || mask.length == 0 || w <= 0 || h <= 0) return;
        NativeImageBackedTexture tex = isItem ? itemTex : entityTex;
        if (tex == null) {
            tex = new NativeImageBackedTexture(() -> "clickgui_glint_" + (isItem ? "item" : "entity"), w, h, true);
            if (isItem) itemTex = tex; else entityTex = tex;
        }
        NativeImage img = tex.getImage();
        if (img == null) return;
        int cr = (rgb >> 16) & 255, cg = (rgb >> 8) & 255, cb = rgb & 255;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int packed = mask[y * w + x];
                int a = (packed >>> 8) & 255;
                int lum = packed & 255;
                int r = (cr * lum) / 255;
                int g = (cg * lum) / 255;
                int b = (cb * lum) / 255;
                img.setColorArgb(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        tex.upload();
        mc.getTextureManager().registerTexture(id, (AbstractTexture) tex);
    }

    private static void restore(Identifier id) {
        mc.getTextureManager().registerTexture(id, new ResourceTexture(id));
    }
}
