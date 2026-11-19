package com.example.clickgui;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.nio.file.*;
import java.util.*;

/**
 * ЧАСТЬ 3 — Инвентарь, Мир, Компас, Друзья, Low Health Виньетка:
 * - Inventory Viewer (9x3 мини-инвентарь)
 * - Compass Bar (N · NE · E · SE · S · SW · W · NW)
 * - Biome Badge (биом, измерение и время дня)
 * - Staff / Friends HUD (друзья из friends.json + дистанция и Middle Click)
 * - Low Health Vignette (< 6 HP пульсирующая рамка)
 */
public final class HudWorld {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Path FRIENDS_FILE = FabricLoader.getInstance().getConfigDir().resolve("clickgui/friends.json");
    private static final Set<String> FRIENDS = new LinkedHashSet<>();
    private static boolean mmbWas;
    private static boolean friendsLoaded;

    public static void tick() {
        if (!friendsLoaded) { loadFriends(); friendsLoaded = true; }
        if (mc.player == null || mc.currentScreen != null) { mmbWas = false; return; }
        Module m = ModuleManager.get("Staff & Friends");
        if (!m.enabled || !m.bool("Middle Click")) return;
        boolean down = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
        if (down && !mmbWas && mc.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() instanceof PlayerEntity pe) {
            String name = pe.getName().getString();
            if (FRIENDS.remove(name)) Notifications.push("Friend Removed", name, 0xFFEF4444);
            else { FRIENDS.add(name); Notifications.push("Friend Added", name, 0xFF4ADE80); }
            saveFriends();
        }
        mmbWas = down;
    }

    public static boolean isFriend(String name) { return FRIENDS.contains(name); }

    public static int[] draw(DrawContext c, String n, int x, int y) {
        return switch (n) {
            case "Inventory Viewer" -> inventoryViewer(c, x, y);
            case "Compass Bar" -> compassBar(c, x, y);
            case "Biome Badge" -> biomeBadge(c, x, y);
            case "Staff & Friends" -> friendsHud(c, x, y);
            case "Music Player" -> MusicHud.render(c, x, y);
            case "Pickup Feed" -> PickupFeed.render(c, x, y);
            case "Notifications" -> Notifications.render(c, x, y);
            default -> new int[]{0, 0};
        };
    }

    // ---------------- 1. Inventory Viewer (9x3) ----------------
    private static int[] inventoryViewer(DrawContext c, int x, int y) {
        int cols = 9, rows = 3, slot = 17;
        int w = cols * slot + 6, h = rows * slot + 6;
        Hud.island(c, x, y, w, h, 7, ColorUtil.withAlpha(Theme.primary(), 95));
        c.fill(x + 4, y, x + w - 4, y + 1, Theme.accent());
        var main = mc.player.getInventory().getMainStacks();
        for (int r = 0; r < rows; r++) {
            for (int col = 0; col < cols; col++) {
                int idx = 9 + r * cols + col;
                int sx = x + 3 + col * slot;
                int sy = y + 3 + r * slot;
                c.fill(sx, sy, sx + slot - 1, sy + slot - 1, 0x441E293B);
                if (idx < main.size()) {
                    ItemStack st = main.get(idx);
                    if (!st.isEmpty()) {
                        c.drawItem(st, sx, sy);
                        c.drawStackOverlay(mc.textRenderer, st, sx, sy);
                    }
                }
            }
        }
        return new int[]{w, h};
    }

    // ---------------- 2. Compass Bar ----------------
    private static final String[] DIRS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    private static int[] compassBar(DrawContext c, int x, int y) {
        int w = 156, h = 16;
        Hud.island(c, x, y, w, h, 7, ColorUtil.withAlpha(Theme.primary(), 95));
        float yaw = MathHelper.wrapDegrees(mc.player.getYaw()); // -180..180, 0 = South
        int center = x + w / 2;
        for (int i = 0; i < 8; i++) {
            float dirYaw = MathHelper.wrapDegrees(i * 45f);
            float diff = MathHelper.wrapDegrees(dirYaw - yaw);
            if (Math.abs(diff) > 78) continue;
            int px = center + Math.round(diff * 0.92f);
            String d = DIRS[i];
            int col = d.equals("N") ? 0xFFEF4444 : (d.length() == 1 ? Theme.accent() : 0xFFCBD5E1);
            Hud.txt(c, d, px - Hud.tw(d) / 2, y + 4, col);
        }
        c.fill(center, y + 1, center + 1, y + h - 1, 0xFFFFFFFF);
        return new int[]{w, h};
    }

    // ---------------- 3. Biome & Dimension Badge ----------------
    private static int[] biomeBadge(DrawContext c, int x, int y) {
        long tod = mc.world != null ? (mc.world.getTimeOfDay() % 24000L) : 0;
        int hours = (int) ((tod / 1000 + 6) % 24);
        int mins = (int) (((tod % 1000) * 60) / 1000);
        String val = String.format(Locale.ROOT, "%s · %s · %02d:%02d", ServerMetrics.biome(), ServerMetrics.dimension(), hours, mins);
        return Hud.labelPublic(c, x, y, "WORLD", val);
    }

    // ---------------- 4. Staff / Friends List HUD ----------------
    private static int[] friendsHud(DrawContext c, int x, int y) {
        List<String> lines = new ArrayList<>();
        if (mc.getNetworkHandler() != null && mc.world != null) {
            for (PlayerListEntry e : mc.getNetworkHandler().getPlayerList()) {
                String name = e.getProfile().name();
                if (!FRIENDS.contains(name) || name.equals(mc.player.getName().getString())) continue;
                PlayerEntity loaded = mc.world.getPlayerByUuid(e.getProfile().id());
                String dist = loaded != null ? String.format(Locale.ROOT, " · %.0fm", mc.player.distanceTo(loaded)) : "";
                lines.add(name + " (" + e.getLatency() + "ms)" + dist);
            }
        }
        if (lines.isEmpty() && Hud.isEditingHud()) {
            lines.add("Friend (24ms) · 18m");
        }
        int w = 112;
        for (String l : lines) w = Math.max(w, Hud.tw(l) + 12);
        int h = 15 + lines.size() * 11;
        if (lines.isEmpty()) return new int[]{112, 16};
        Hud.island(c, x, y, w, h, 7, ColorUtil.withAlpha(Theme.primary(), 95));
        c.fill(x + 4, y, x + w - 4, y + 1, Theme.accent());
        Hud.txt(c, "Friends (" + lines.size() + ")", x + 6, y + 3, Theme.accent());
        for (int i = 0; i < lines.size(); i++) {
            Hud.txt(c, lines.get(i), x + 6, y + 14 + i * 11, 0xFFFFFFFF);
        }
        return new int[]{w, h};
    }

    // ---------------- 5. Low Health Vignette ----------------
    public static void renderLowHealthVignette(DrawContext c) {
        Module m = ModuleManager.get("Low Health Vignette");
        if (!m.enabled || mc.player == null) return;
        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        float thr = (float) m.num("Threshold");
        if (hp >= thr || hp <= 0f) return;

        float ratio = 1f - MathHelper.clamp(hp / thr, 0f, 1f);
        float pulse = 0.65f + 0.35f * MathHelper.sin(System.currentTimeMillis() / 160f);
        int maxA = Math.min(130, Math.round(115 * ratio * pulse));
        int baseRgb = m.bool("Theme Color") ? (Theme.accent() & 0xFFFFFF) : 0xDC2626;
        int edge = (maxA << 24) | baseRgb;
        int clear = baseRgb; // alpha = 0

        int sw = c.getScaledWindowWidth(), sh = c.getScaledWindowHeight();
        int band = 28;
        c.fillGradient(0, 0, sw, band, edge, clear);
        c.fillGradient(0, sh - band, sw, sh, clear, edge);
        for (int i = 0; i < band; i += 4) {
            int a = Math.round(maxA * (1f - (float) i / band));
            int col = (a << 24) | baseRgb;
            c.fill(i, 0, i + 4, sh, col);
            c.fill(sw - i - 4, 0, sw - i, sh, col);
        }
    }

    private static void saveFriends() {
        try {
            Files.createDirectories(FRIENDS_FILE.getParent());
            Files.writeString(FRIENDS_FILE, new GsonBuilder().setPrettyPrinting().create().toJson(FRIENDS));
        } catch (Exception ignored) {}
    }

    private static void loadFriends() {
        if (!Files.exists(FRIENDS_FILE)) return;
        try {
            JsonArray arr = JsonParser.parseString(Files.readString(FRIENDS_FILE)).getAsJsonArray();
            for (JsonElement el : arr) FRIENDS.add(el.getAsString());
        } catch (Exception ignored) {}
    }
}
