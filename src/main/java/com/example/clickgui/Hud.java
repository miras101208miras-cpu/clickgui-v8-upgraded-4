package com.example.clickgui;

import com.google.gson.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.nio.file.*;
import java.time.LocalTime;
import java.util.*;

/** HUD: rendering of HUD-tab modules (Liquid Island style) + movable positions via Chat (T / /) or config/clickgui/hud.json. */
public final class Hud {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final StyleSpriteSource FONT = new StyleSpriteSource.Font(Identifier.of("clickgui", "main"));
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("clickgui/hud.json");
    public static final int ISLAND_BG = 0xDC080B12;
    private static final int WHITE = 0xFFFFFFFF, GRAY = 0xFF94A3B8, DARK = 0xFF1A1D24;

    /** Elements that can be moved. Name = module name in ModuleManager. */
    public static final String[] ELEMENTS = {
            "Watermark", "Coordinates", "FPS Counter", "Armor HUD", "Keystrokes", "Armor Notifier",
            "Target HUD", "Cooldowns HUD", "Potion HUD", "ArrayList", "Totem Counter", "Armor HUD Pro",
            "Ping & TPS", "Speedometer", "Kill & Combo", "Inventory Viewer", "Compass Bar",
            "Biome Badge", "Staff & Friends", "Music Player", "Pickup Feed", "Notifications",
            "Radar Mini", "Clock HUD", "Reach Display", "CPS Graph", "FPS Graph",
            "Memory HUD", "Server IP HUD", "Direction HUD", "Saturation HUD", "Elytra HUD",
            "Players Nearby", "Arrow Counter", "Consumables HUD", "Session Timer", "Biome & Light HUD",
            "XP Progress HUD", "Nether Coords HUD", "Target Armor Percent HUD"
    };
    private static final float[][] DEFAULTS = {
            {0.01f, 0.01f}, {0.01f, 0.08f}, {0.01f, 0.14f}, {0.5f, 0.86f}, {0.01f, 0.75f}, {0.5f, 0.18f},
            {0.62f, 0.56f}, {0.01f, 0.34f}, {0.01f, 0.52f}, {0.99f, 0.02f}, {0.59f, 0.88f}, {0.01f, 0.22f},
            {0.18f, 0.01f}, {0.01f, 0.19f}, {0.34f, 0.01f}, {0.72f, 0.72f}, {0.50f, 0.02f},
            {0.01f, 0.65f}, {0.84f, 0.24f}, {0.82f, 0.08f}, {0.98f, 0.66f}, {0.98f, 0.88f},
            {0.88f, 0.36f}, {0.01f, 0.28f}, {0.01f, 0.38f}, {0.01f, 0.44f}, {0.14f, 0.44f},
            {0.01f, 0.58f}, {0.26f, 0.01f}, {0.42f, 0.01f}, {0.58f, 0.01f}, {0.45f, 0.82f},
            {0.84f, 0.50f}, {0.72f, 0.88f}, {0.72f, 0.82f}, {0.18f, 0.07f}, {0.18f, 0.13f},
            {0.50f, 0.78f}, {0.01f, 0.12f}, {0.62f, 0.64f}
    };
    private static final Set<EquipmentSlot> lowArmorWarned = EnumSet.noneOf(EquipmentSlot.class);

    private static final Map<String, float[]> POS = new LinkedHashMap<>();
    private static final Map<String, int[]> SIZE = new HashMap<>();
    private static final ArrayDeque<Long> lmb = new ArrayDeque<>(), rmb = new ArrayDeque<>();
    private static float lastTd;
    private static boolean lmbWas, rmbWas;

    // Chat-based HUD drag state
    private static final int SNAP = 5;
    private static String chatDragging;
    private static double chatOffX, chatOffY;
    private static int chatGuideX = -1, chatGuideY = -1;
    private static int resetBtnX, resetBtnY, resetBtnW, resetBtnH;

    public static void init() {
        for (int i = 0; i < ELEMENTS.length; i++) POS.put(ELEMENTS[i], DEFAULTS[i].clone());
        load();
        HudElementRegistry.addLast(Identifier.of("clickgui", "hud"), (ctx, tick) -> {
            Animation.beginFrame();
            if (mc.player == null || mc.options.hudHidden) return;
            float td = tick.getTickProgress(true);
            lastTd = td;
            try { HudWorld.renderLowHealthVignette(ctx); } catch (Throwable ignored) {}
            try { Nametags.render(ctx, td); } catch (Throwable ignored) {}
            try { HandVisuals.renderHudOverlays(ctx, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight()); } catch (Throwable ignored) {}
            try { ExtraVisuals.renderHudOverlays(ctx, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(), td); } catch (Throwable ignored) {}
            if (mc.currentScreen instanceof HudEditorScreen) return;
            renderAll(ctx);
        });
    }

    public static boolean isEditingHud() {
        return mc.currentScreen instanceof HudEditorScreen || mc.currentScreen instanceof ChatScreen;
    }

    public static void island(DrawContext c, int x, int y, int w, int h, int r, int outlineCol) {
        RenderUtil.glass(c, x, y, w, h, r, ISLAND_BG, outlineCol);
    }

    public static void dot(DrawContext c, int x, int y, int col) {
        RenderUtil.roundRect(c, x, y, 4, 4, 2, col);
    }

    // ---------------- Chat HUD Editing Overlay ----------------
    public static void renderChatHudOverlay(DrawContext ctx, int mx, int my, int sw, int sh) {
        if (mc.player == null) return;
        boolean lmbDown = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (chatDragging != null) {
            if (lmbDown) {
                int ew = w(chatDragging), eh = h(chatDragging);
                int nx = (int) Math.round(mx - chatOffX);
                int ny = (int) Math.round(my - chatOffY);
                chatGuideX = chatGuideY = -1;
                if (Math.abs(nx) < SNAP) { nx = 0; chatGuideX = 0; }
                else if (Math.abs(nx + ew / 2 - sw / 2) < SNAP) { nx = sw / 2 - ew / 2; chatGuideX = sw / 2; }
                else if (Math.abs(nx + ew - sw) < SNAP) { nx = sw - ew; chatGuideX = sw - 1; }
                if (Math.abs(ny) < SNAP) { ny = 0; chatGuideY = 0; }
                else if (Math.abs(ny + eh / 2 - sh / 2) < SNAP) { ny = sh / 2 - eh / 2; chatGuideY = sh / 2; }
                else if (Math.abs(ny + eh - sh) < SNAP) { ny = sh - eh; chatGuideY = sh - 1; }
                setPos(chatDragging, nx, ny);
            } else {
                chatDragging = null;
                chatGuideX = chatGuideY = -1;
                save();
            }
        }

        int acc = Theme.accent();
        if (chatGuideX >= 0) ctx.fill(chatGuideX, 0, chatGuideX + 1, sh, acc);
        if (chatGuideY >= 0) ctx.fill(0, chatGuideY, sw, chatGuideY + 1, acc);

        boolean any = false;
        for (String n : ELEMENTS) {
            if (!visible(n)) continue;
            any = true;
            int ex = x(n), ey = y(n), ew = w(n), eh = h(n);
            boolean active = n.equals(chatDragging) || (mx >= ex - 2 && mx < ex + ew + 2 && my >= ey - 2 && my < ey + eh + 2);
            int col = active ? acc : 0x70FFFFFF;
            outline(ctx, ex - 2, ey - 2, ew + 4, eh + 4, col);
            if (active) {
                int ly = ey - 11 < 2 ? ey + eh + 3 : ey - 11;
                txt(ctx, n, ex, ly, acc);
            }
        }

        boolean ru = ClickGuiScreen.isRussian();
        String hint = any
                ? (ru ? "HUD: ЛКМ — двигать · ПКМ — сброс" : "HUD: LMB drag · RMB reset")
                : (ru ? "Включи HUD-модули в ClickGUI (RShift)" : "Enable HUD modules in ClickGUI (RShift)");
        String resetLbl = ru ? "Сбросить всё" : "Reset HUD";
        int hw = tw(hint), rw = tw(resetLbl) + 12;
        int pillW = hw + (any ? rw + 18 : 16);
        int px = (sw - pillW) / 2, py = 6, ph = 18;
        island(ctx, px, py, pillW, ph, 8, ColorUtil.withAlpha(acc, 130));
        txt(ctx, hint, px + 8, py + 5, 0xFFE2E8F0);
        if (any) {
            resetBtnX = px + 8 + hw + 6;
            resetBtnY = py + 2;
            resetBtnW = rw;
            resetBtnH = 14;
            boolean hov = mx >= resetBtnX && mx < resetBtnX + resetBtnW && my >= resetBtnY && my < resetBtnY + resetBtnH;
            RenderUtil.roundRect(ctx, resetBtnX, resetBtnY, resetBtnW, resetBtnH, 5, hov ? ColorUtil.withAlpha(acc, 90) : 0x22FFFFFF);
            txt(ctx, resetLbl, resetBtnX + 6, resetBtnY + 3, acc);
        } else {
            resetBtnW = 0;
        }
    }

    public static boolean handleChatMouseClick(double mx, double my, int button, int sw, int sh) {
        if (resetBtnW > 0 && mx >= resetBtnX && mx < resetBtnX + resetBtnW && my >= resetBtnY && my < resetBtnY + resetBtnH) {
            for (String n : ELEMENTS) reset(n);
            save();
            return true;
        }
        if (my >= sh - 24) return false; // leave chat input bar untouched
        for (int i = ELEMENTS.length - 1; i >= 0; i--) {
            String n = ELEMENTS[i];
            if (!visible(n)) continue;
            int ex = x(n), ey = y(n), ew = w(n), eh = h(n);
            if (mx >= ex - 2 && mx < ex + ew + 2 && my >= ey - 2 && my < ey + eh + 2) {
                if (button == 1) {
                    reset(n);
                    save();
                    return true;
                }
                if (button == 0) {
                    chatDragging = n;
                    chatOffX = mx - ex;
                    chatOffY = my - ey;
                    return true;
                }
            }
        }
        return false;
    }

    public static void finishChatDrag() {
        if (chatDragging != null) {
            chatDragging = null;
            chatGuideX = chatGuideY = -1;
            save();
        }
    }

    private static void outline(DrawContext c, int x, int y, int w, int h, int col) {
        c.fill(x, y, x + w, y + 1, col);
        c.fill(x, y + h - 1, x + w, y + h, col);
        c.fill(x, y, x + 1, y + h, col);
        c.fill(x + w - 1, y, x + w, y + h, col);
    }

    // ---------------- positions ----------------
    public static int w(String n) { int[] s = SIZE.get(n); return s == null ? 40 : s[0]; }
    public static int h(String n) { int[] s = SIZE.get(n); return s == null ? 12 : s[1]; }
    public static int x(String n) { return Math.round(POS.get(n)[0] * Math.max(0, mc.getWindow().getScaledWidth() - w(n))); }
    public static int y(String n) { return Math.round(POS.get(n)[1] * Math.max(0, mc.getWindow().getScaledHeight() - h(n))); }

    public static void setPos(String n, int x, int y) {
        int mw = Math.max(1, mc.getWindow().getScaledWidth() - w(n)), mh = Math.max(1, mc.getWindow().getScaledHeight() - h(n));
        POS.put(n, new float[]{clamp((float) x / mw), clamp((float) y / mh)});
    }

    public static void reset(String n) {
        for (int i = 0; i < ELEMENTS.length; i++) if (ELEMENTS[i].equals(n)) POS.put(n, DEFAULTS[i].clone());
    }

    public static boolean visible(String n) { return ModuleManager.get(n).enabled; }

    // ---------------- rendering ----------------
    private static void updateClicks() {
        var o = mc.options;
        long now = System.currentTimeMillis();
        boolean l = o.attackKey.isPressed(), r = o.useKey.isPressed();
        if (l && !lmbWas) lmb.add(now);
        if (r && !rmbWas) rmb.add(now);
        lmbWas = l; rmbWas = r;
        while (!lmb.isEmpty() && now - lmb.peekFirst() > 1000) lmb.pollFirst();
        while (!rmb.isEmpty() && now - rmb.peekFirst() > 1000) rmb.pollFirst();
        HudExtra.tick(lmb.size());
    }

    public static void renderAll(DrawContext ctx) {
        try { updateClicks(); } catch (Throwable ignored) {}
        for (String n : ELEMENTS) {
            if (!visible(n)) continue;
            try {
                int[] wh = draw(ctx, n, x(n), y(n));
                if (wh != null) SIZE.put(n, wh);
            } catch (Throwable ignored) {}
        }
        if (!isEditingHud()) {
            try { renderHitRange(ctx); } catch (Throwable ignored) {}
            try { renderMaceAndTraps(ctx); } catch (Throwable ignored) {}
        }
    }

    private static int[] draw(DrawContext c, String n, int x, int y) {
        return switch (n) {
            case "Watermark" -> watermark(c, x, y);
            case "Coordinates" -> {
                var p = mc.player.getBlockPos();
                yield label(c, x, y, "XYZ", p.getX() + " · " + p.getY() + " · " + p.getZ());
            }
            case "FPS Counter" -> label(c, x, y, "FPS", String.valueOf(mc.getCurrentFps()));
            case "Armor HUD" -> armor(c, x, y);
            case "Keystrokes" -> keystrokes(c, x, y);
            case "Armor Notifier" -> armorNotifier(c, x, y);
            default -> {
                int[] combat = HudCombat.draw(c, n, x, y, lastTd);
                if (combat[0] > 0 || combat[1] > 0) yield combat;
                int[] world = HudWorld.draw(c, n, x, y);
                yield (world[0] > 0 || world[1] > 0) ? world : HudExtra.draw(c, n, x, y, lastTd);
            }
        };
    }

    private static int[] watermark(DrawContext c, int x, int y) {
        Module m = ModuleManager.get("Watermark");
        int c1 = Theme.primary();
        String s = "ClickGUI  ·  " + mc.getCurrentFps() + " fps  ·  " + ServerMetrics.ping() + " ms";
        if (m.bool("Time")) {
            LocalTime t = LocalTime.now();
            s += String.format("  ·  %02d:%02d", t.getHour(), t.getMinute());
        }
        int w = tw(s) + 22, h = 15;
        island(c, x, y, w, h, 7, ColorUtil.withAlpha(c1, 125));
        dot(c, x + 6, y + 6, c1);
        txt(c, s, x + 14, y + 4, WHITE);
        return new int[]{w, h};
    }

    public static int[] labelPublic(DrawContext c, int x, int y, String key, String val) { return label(c, x, y, key, val); }
    private static int[] label(DrawContext c, int x, int y, String key, String val) {
        int c1 = Theme.primary();
        int w = tw(key) + tw(val) + 24, h = 15;
        island(c, x, y, w, h, 7, ColorUtil.withAlpha(c1, 80));
        dot(c, x + 6, y + 6, c1);
        txt(c, key, x + 13, y + 4, c1);
        txt(c, val, x + 17 + tw(key), y + 4, WHITE);
        return new int[]{w, h};
    }

    private static int[] armor(DrawContext c, int x, int y) {
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
        int w = slots.length * 18 + 6, h = 22;
        island(c, x, y, w, h, 7, ColorUtil.withAlpha(Theme.primary(), 90));
        for (int i = 0; i < slots.length; i++) {
            ItemStack st = mc.player.getEquippedStack(slots[i]);
            if (st.isEmpty()) continue;
            int ix = x + 4 + i * 18, iy = y + 3;
            c.drawItem(st, ix, iy);
            c.drawStackOverlay(mc.textRenderer, st, ix, iy);
        }
        return new int[]{w, h};
    }

    private static int[] keystrokes(DrawContext c, int x, int y) {
        var o = mc.options;
        boolean l = o.attackKey.isPressed(), r = o.useKey.isPressed();
        boolean cps = ModuleManager.get("Keystrokes").bool("CPS");

        int k = 19, g = 2, w = k * 3 + g * 2;
        key(c, x + k + g, y, k, k, "W", o.forwardKey.isPressed());
        key(c, x, y + k + g, k, k, "A", o.leftKey.isPressed());
        key(c, x + k + g, y + k + g, k, k, "S", o.backKey.isPressed());
        key(c, x + (k + g) * 2, y + k + g, k, k, "D", o.rightKey.isPressed());
        int my = y + (k + g) * 2, half = (w - g) / 2;
        key(c, x, my, half, k, cps ? lmb.size() + " CPS" : "LMB", l);
        key(c, x + half + g, my, half, k, cps ? rmb.size() + " CPS" : "RMB", r);
        key(c, x, my + k + g, w, 10, "---", o.jumpKey.isPressed());
        return new int[]{w, (k + g) * 3 + 10};
    }

    private static void key(DrawContext c, int x, int y, int w, int h, String s, boolean down) {
        int c1 = Theme.primary(), c2 = Theme.secondary();
        int r = Math.min(6, h / 2);
        if (down) {
            RenderUtil.roundRect(c, x - 1, y - 1, w + 2, h + 2, r + 1, ColorUtil.withAlpha(c1, 160));
            RenderUtil.roundGradientH(c, x, y, w, h, r, c1, c2);
        } else {
            island(c, x, y, w, h, r, ColorUtil.withAlpha(c1, 55));
        }
        txt(c, s, x + (w - tw(s)) / 2, y + (h - 8) / 2, down ? DARK : WHITE);
    }

    private static int[] armorNotifier(DrawContext c, int x, int y) {
        Module m = ModuleManager.get("Armor Notifier");
        int threshold = (int) m.num("Threshold %");
        EquipmentSlot[] armorSlots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        String[] labels = {"Helmet", "Chestplate", "Leggings", "Boots"};
        List<String> low = new ArrayList<>();
        for (int i = 0; i < armorSlots.length; i++) {
            EquipmentSlot slot = armorSlots[i];
            ItemStack st = mc.player.getEquippedStack(slot);
            if (!st.isEmpty() && st.isDamageable() && st.getMaxDamage() > 0) {
                int pct = (int) Math.round(100.0 * (st.getMaxDamage() - st.getDamage()) / st.getMaxDamage());
                if (pct <= threshold) {
                    low.add(labels[i] + " " + pct + "%");
                    if (lowArmorWarned.add(slot)) {
                        Notifications.push("LOW ARMOR", labels[i] + " " + pct + "%", 0xFFFF5555);
                        if (m.bool("Sound") && mc.world != null) {
                            mc.world.playSoundClient(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                                    SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 0.8f, 1.5f, false);
                        }
                    }
                } else {
                    lowArmorWarned.remove(slot);
                }
            } else {
                lowArmorWarned.remove(slot);
            }
        }
        if (low.isEmpty()) {
            if (isEditingHud()) {
                String demo = "LOW ARMOR: Helmet 12%";
                int w = tw(demo) + 18, h = 15;
                island(c, x, y, w, h, 7, 0x70FF5555);
                txt(c, demo, x + 9, y + 4, 0xFFFF6B6B);
                return new int[]{w, h};
            }
            return new int[]{90, 15};
        }
        String text = "LOW ARMOR: " + String.join(", ", low);
        int w = tw(text) + 18, h = 15;
        RenderUtil.glass(c, x, y, w, h, 7, 0xDC2A0D12, 0xBBFF5555);
        dot(c, x + 6, y + 6, 0xFFFF5555);
        txt(c, text, x + 13, y + 4, 0xFFFF7A7A);
        return new int[]{w, h};
    }

    private static void renderHitRange(DrawContext c) {
        Module m = ModuleManager.get("Hit Range");
        if (!m.enabled || mc.world == null) return;
        LivingEntity target = findRangeTarget();
        if (target == null) return;

        Vec3d eye = mc.player.getEyePos();
        Box box = target.getBoundingBox();
        double cx = Math.max(box.minX, Math.min(eye.x, box.maxX));
        double cy = Math.max(box.minY, Math.min(eye.y, box.maxY));
        double cz = Math.max(box.minZ, Math.min(eye.z, box.maxZ));
        double dist = eye.distanceTo(new Vec3d(cx, cy, cz));

        double reach = m.num("Reach");
        boolean inRange = dist <= reach;
        int col = inRange ? 0xFF4ADE80 : 0xFFF59E0B;
        String mode = ((Setting.Mode) m.find("Mode")).get();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int mx = sw / 2, my = sh / 2;

        if (mode.equals("Crosshair") || mode.equals("Both")) {
            int g = 5, l = 4;
            c.fill(mx - g - l, my, mx - g, my + 1, col);
            c.fill(mx + g + 1, my, mx + g + l + 1, my + 1, col);
            c.fill(mx, my - g - l, mx + 1, my - g, col);
            c.fill(mx, my + g + 1, mx + 1, my + g + l + 1, col);
        }
        if (mode.equals("Text") || mode.equals("Both")) {
            String s = String.format(Locale.ROOT, "%.2fm", dist);
            int w = tw(s);
            island(c, mx - w / 2 - 5, my + 10, w + 10, 13, 6, ColorUtil.withAlpha(col, 140));
            txt(c, s, mx - w / 2, my + 13, col);
        }
    }

    private static LivingEntity findRangeTarget() {
        if (mc.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le
                && le != mc.player && le.isAlive()) {
            return le;
        }
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVector().normalize();
        LivingEntity best = null;
        double bestDist = 6.5;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || le == mc.player || !le.isAlive()) continue;
            Vec3d center = le.getBoundingBox().getCenter();
            double d = eye.distanceTo(center);
            if (d > bestDist) continue;
            Vec3d dir = center.subtract(eye).normalize();
            if (dir.dotProduct(look) > 0.85) {
                bestDist = d;
                best = le;
            }
        }
        return best;
    }

    private static void renderMaceAndTraps(DrawContext c) {
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int y = sh / 2 + 26;
        if (MaceHelper.ready) {
            String s = String.format(Locale.ROOT, "MACE SMASH  %.1fm", MaceHelper.fallDistance);
            int w = tw(s) + 18;
            int x = (sw - w) / 2;
            island(c, x, y, w, 15, 7, ColorUtil.withAlpha(Theme.accent(), 140));
            dot(c, x + 5, y + 6, Theme.accent());
            txt(c, s, x + 12, y + 4, Theme.accent());
            y += 18;
        }
        Module pvp = ModuleManager.get("PvP Save");
        if (pvp.enabled && pvp.bool("HUD Timer") && !PvpSave.ZONES.isEmpty()) {
            long now = System.currentTimeMillis();
            for (PvpSave.TrapZone z : PvpSave.ZONES) {
                long rem = z.expiresAt() - now;
                if (rem <= 0) continue;
                String s = String.format(Locale.ROOT, "%s 5x5  %.1fs", z.label(), rem / 1000.0);
                int w = tw(s) + 16;
                int x = (sw - w) / 2;
                int col = rem < 3000 ? 0xFFFF5555 : Theme.accent();
                island(c, x, y, w, 15, 7, ColorUtil.withAlpha(col, 130));
                txt(c, s, x + 8, y + 4, col);
                y += 18;
            }
        }
    }

    // ---------------- save / load ----------------
    public static void save() {
        try {
            JsonObject root = new JsonObject();
            for (var e : POS.entrySet()) {
                JsonObject o = new JsonObject();
                o.addProperty("x", e.getValue()[0]);
                o.addProperty("y", e.getValue()[1]);
                root.add(e.getKey(), o);
            }
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private static void load() {
        if (!Files.exists(FILE)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
            for (String n : ELEMENTS) if (root.has(n)) {
                JsonObject o = root.getAsJsonObject(n);
                POS.put(n, new float[]{clamp(o.get("x").getAsFloat()), clamp(o.get("y").getAsFloat())});
            }
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    // ---------------- utils ----------------
    static Text t(String s) { return Text.literal(s).setStyle(Style.EMPTY.withFont(FONT)); }
    static int tw(String s) { return mc.textRenderer.getWidth(t(s)); }
    static void txt(DrawContext c, String s, int x, int y, int col) { c.drawText(mc.textRenderer, t(s), x, y, col, false); }
    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }
}
