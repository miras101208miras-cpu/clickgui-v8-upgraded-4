package com.example.clickgui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

import java.lang.reflect.Field;

/** Перекрашивает красную вспышку урона (верхние 8 строк OverlayTexture). */
final class HitColor {
    private static final int VANILLA = 0xB2FF0000;
    private static int applied = VANILLA;
    private static Field texField;

    static void update() {
        Module m = ModuleManager.get("Hit Color");
        int want = m.enabled
                ? ((int) m.num("Alpha") << 24) | ((int) m.num("Red") << 16) | ((int) m.num("Green") << 8) | (int) m.num("Blue")
                : VANILLA;
        if (want == applied) return;
        applied = want;
        try {
            OverlayTexture overlay = MinecraftClient.getInstance().gameRenderer.getOverlayTexture();
            if (texField == null) {
                for (Field f : OverlayTexture.class.getDeclaredFields()) {
                    if (NativeImageBackedTexture.class.isAssignableFrom(f.getType())) { f.setAccessible(true); texField = f; break; }
                }
            }
            if (texField == null) return;
            NativeImageBackedTexture tex = (NativeImageBackedTexture) texField.get(overlay);
            NativeImage img = tex.getImage();
            if (img == null) return;
            for (int y = 0; y < 8; y++) for (int x = 0; x < 16; x++) img.setColorArgb(x, y, want);
            tex.upload();
        } catch (Exception ex) {
            System.err.println("[ClickGUI] Hit Color failed: " + ex);
        }
    }
}
