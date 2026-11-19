package com.example.clickgui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundInstanceListener;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.sound.SoundCategory;

import java.util.Locale;

/**
 * Music / Media Player HUD:
 * Слушает SoundInstanceListener нативно (без миксинов!) и показывает текущий трек BGM или пластинку.
 */
public final class MusicHud implements SoundInstanceListener {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final MusicHud INSTANCE = new MusicHud();
    private static SoundInstance current;
    private static String trackTitle = "Idle";
    private static boolean registered;

    public static void ensureRegistered() {
        if (registered || mc.getSoundManager() == null) return;
        mc.getSoundManager().registerListener(INSTANCE);
        registered = true;
    }

    @Override
    public void onSoundPlayed(SoundInstance sound, WeightedSoundSet soundSet, float rangeMultiplier) {
        if (sound == null) return;
        SoundCategory cat = sound.getCategory();
        if (cat != SoundCategory.MUSIC && cat != SoundCategory.RECORDS) return;
        current = sound;
        String path = sound.getId().getPath();
        int slash = path.lastIndexOf('/');
        String raw = slash >= 0 ? path.substring(slash + 1) : path;
        raw = raw.replace("music_disc.", "").replace("music.", "").replace('_', ' ').replace('.', ' ');
        trackTitle = raw.isEmpty() ? "Music" : Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
        if (cat == SoundCategory.RECORDS) trackTitle = "Disc: " + trackTitle;
    }

    public static int[] render(DrawContext c, int x, int y) {
        ensureRegistered();
        boolean playing = current != null && mc.getSoundManager().isPlaying(current);
        if (!playing && !Hud.isEditingHud()) {
            return new int[]{118, 22};
        }
        String label = playing ? trackTitle : "No Music Playing";
        if (label.length() > 22) label = label.substring(0, 21) + "..";
        int w = Math.max(96, Hud.tw(label) + 28);
        int h = 22;
        Hud.island(c, x, y, w, h, 7, ColorUtil.withAlpha(Theme.accent(), 95));

        // Mini equalizer bars
        long now = System.currentTimeMillis();
        int acc = Theme.accent();
        for (int i = 0; i < 4; i++) {
            int bh = playing ? 3 + (int) Math.round(7 * (0.5 + 0.5 * Math.sin(now / 140.0 + i * 1.3))) : 2;
            c.fill(x + 5 + i * 3, y + 15 - bh, x + 7 + i * 3, y + 15, acc);
        }
        Hud.txt(c, playing ? "NOW PLAYING" : "MEDIA", x + 20, y + 3, 0xFF94A3B8);
        Hud.txt(c, label, x + 20, y + 11, 0xFFFFFFFF);
        return new int[]{w, h};
    }
}
