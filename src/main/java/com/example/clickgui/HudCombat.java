package com.example.clickgui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.*;

/**
 * ЧАСТЬ 2 — Боевые, статусные и системные HUD-виджеты в стиле Liquid Island:
 * Target HUD, Cooldowns HUD, Potion HUD, ArrayList (кэшированная сортировка),
 * Totem Counter, Armor HUD Pro, Hotbar Customizer, Ping & TPS, Speedometer, Kill & Combo.
 */
public final class HudCombat {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final int WHITE = 0xFFFFFFFF, GRAY = 0xFF94A3B8;

    // Target HUD animation state
    private static double smoothTargetHp = 20, trailTargetHp = 20;
    private static int lastTargetId = -1;

    // Hotbar smooth highlight slot
    private static float smoothSlot = 0f;

    // Cached ArrayList sorting (dirty-flag)
    private static final List<Module> sortedModules = new ArrayList<>();
    private static boolean arrayListDirty = true;

    // Pre-allocated Cooldown items (0 heap alloc per frame)
    private static final ItemStack[] CD_STACKS = {
            new ItemStack(Items.ENDER_PEARL),
            new ItemStack(Items.CHORUS_FRUIT),
            new ItemStack(Items.GOLDEN_APPLE),
            new ItemStack(Items.SHIELD),
            new ItemStack(Items.WIND_CHARGE)
    };
    private static final String[] CD_NAMES = {"Pearl", "Chorus", "Gapple", "Shield", "Wind"};

    public static void markArrayListDirty() { arrayListDirty = true; }

    public static int[] draw(DrawContext c, String n, int x, int y, float td) {
        return switch (n) {
            case "Target HUD" -> targetHud(c, x, y);
            case "Cooldowns HUD" -> cooldownsHud(c, x, y, td);
            case "Potion HUD" -> potionHud(c, x, y);
            case "ArrayList" -> arrayList(c, x, y);
            case "Totem Counter" -> totemCounter(c, x, y);
            case "Armor HUD Pro" -> armorPro(c, x, y);
            case "Ping & TPS" -> pingTps(c, x, y);
            case "Speedometer" -> speedometer(c, x, y);
            case "Kill & Combo" -> killCombo(c, x, y);
            default -> new int[]{0, 0};
        };
    }

    // ---------------- 1. Target HUD (Liquid Island) ----------------
    private static int[] targetHud(DrawContext c, int x, int y) {
        LivingEntity le = Effects.lastTarget();
        boolean editor = Hud.isEditingHud();
        if (le == null) {
            if (!editor) return new int[]{156, 46};
            le = mc.player;
        }
        int c1 = Theme.primary(), c2 = Theme.secondary();
        int w = 156, h = 46;
        Hud.island(c, x, y, w, h, 9, ColorUtil.withAlpha(c1, 130));

        // Portrait frame with theme outline
        RenderUtil.glass(c, x + 6, y + 6, 34, 34, 6, 0xFF1E293B, ColorUtil.withAlpha(c1, 145));
        if (le instanceof PlayerEntity pe && mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(pe.getUuid());
            if (entry != null) {
                PlayerSkinDrawer.draw(c, entry.getSkinTextures(), x + 7, y + 7, 32);
            }
        } else {
            String raw = le.getName().getString();
            String ini = raw.substring(0, Math.min(2, raw.length())).toUpperCase(Locale.ROOT);
            Hud.txt(c, ini, x + 17, y + 19, c1);
        }

        double max = Math.max(1.0, le.getMaxHealth());
        double cur = Math.max(0.0, le.getHealth());
        double abs = Math.max(0.0, le.getAbsorptionAmount());
        if (le.getId() != lastTargetId) {
            lastTargetId = le.getId();
            smoothTargetHp = cur;
            trailTargetHp = cur;
        } else {
            smoothTargetHp += (cur - smoothTargetHp) * 0.25;
            if (trailTargetHp < cur) trailTargetHp = cur;
            else trailTargetHp = Math.max(cur, trailTargetHp - 0.08);
        }

        // Nick + Win/Lose pill badge
        String name = le.getName().getString();
        if (name.length() > 12) name = name.substring(0, 11) + "..";
        Hud.txt(c, name, x + 45, y + 7, WHITE);

        float myTotal = mc.player.getHealth() + mc.player.getAbsorptionAmount() + mc.player.getArmor() * 0.5f;
        float enTotal = (float) (cur + abs + le.getArmor() * 0.5f);
        String status = myTotal > enTotal + 1f ? "WIN" : (enTotal > myTotal + 1f ? "LOSE" : "EVEN");
        int stCol = status.equals("WIN") ? 0xFF4ADE80 : status.equals("LOSE") ? 0xFFEF4444 : 0xFFFACC15;
        int stW = Hud.tw(status) + 8;
        RenderUtil.roundRect(c, x + w - stW - 6, y + 5, stW, 12, 5, ColorUtil.withAlpha(stCol, 42));
        Hud.txt(c, status, x + w - stW - 2, y + 7, stCol);

        // Armor mini-row + HP text
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        int ax = x + 45;
        for (EquipmentSlot s : slots) {
            ItemStack st = le.getEquippedStack(s);
            if (!st.isEmpty()) {
                c.getMatrices().pushMatrix();
                try {
                    c.getMatrices().translate(ax, y + 18);
                    c.getMatrices().scale(0.65f, 0.65f);
                    c.drawItem(st, 0, 0);
                } finally {
                    c.getMatrices().popMatrix();
                }
                ax += 12;
            }
        }
        String hpStr = String.format(Locale.ROOT, "%.1f HP", cur + abs);
        Hud.txt(c, hpStr, x + w - Hud.tw(hpStr) - 6, y + 20, abs > 0 ? 0xFFFBBF24 : GRAY);

        // Smooth pill HP bar + damage delay trail + absorption
        int bx = x + 45, by = y + 34, bw = w - 51, bh = 5;
        RenderUtil.roundRect(c, bx, by, bw, bh, 2, 0x33FFFFFF);
        int tw = (int) Math.round(bw * Math.min(1.0, trailTargetHp / max));
        int hw = (int) Math.round(bw * Math.min(1.0, smoothTargetHp / max));
        if (tw > hw) RenderUtil.roundRect(c, bx, by, tw, bh, 2, 0xAAFFFFFF);
        if (hw > 1) RenderUtil.roundGradientH(c, bx, by, Math.max(4, hw), bh, 2, c1, c2);
        if (abs > 0) {
            int aw = (int) Math.round(bw * Math.min(1.0, abs / max));
            RenderUtil.roundRect(c, bx, by - 2, Math.max(3, aw), 2, 1, 0xFFFBBF24);
        }
        return new int[]{w, h};
    }

    // ---------------- 2. Cooldowns HUD ----------------
    private static int[] cooldownsHud(DrawContext c, int x, int y, float td) {
        ItemCooldownManager cd = mc.player.getItemCooldownManager();
        boolean editor = Hud.isEditingHud();
        int row = 0, w = 94, c1 = Theme.primary(), c2 = Theme.secondary();
        int startY = y;
        for (int i = 0; i < CD_STACKS.length; i++) {
            float prog = cd.getCooldownProgress(CD_STACKS[i], td);
            if (prog <= 0f && !(editor && i < 2)) continue;
            if (editor && prog <= 0f) prog = 0.65f - i * 0.25f;
            int ry = startY + row * 19;
            Hud.island(c, x, ry, w, 17, 7, ColorUtil.withAlpha(c1, 85));
            c.getMatrices().pushMatrix();
            try {
                c.getMatrices().translate(x + 4, ry + 2);
                c.getMatrices().scale(0.75f, 0.75f);
                c.drawItem(CD_STACKS[i], 0, 0);
            } finally {
                c.getMatrices().popMatrix();
            }
            Hud.txt(c, CD_NAMES[i], x + 19, ry + 4, WHITE);
            String pct = Math.round(prog * 100) + "%";
            Hud.txt(c, pct, x + w - Hud.tw(pct) - 6, ry + 4, c1);
            int barW = Math.max(3, Math.round((w - 10) * prog));
            RenderUtil.roundGradientH(c, x + 5, ry + 14, barW, 2, 1, c1, c2);
            row++;
        }
        return new int[]{w, Math.max(17, row * 19)};
    }

    // ---------------- 3. Potion Effects HUD ----------------
    private static int[] potionHud(DrawContext c, int x, int y) {
        Collection<StatusEffectInstance> effects = mc.player.getStatusEffects();
        boolean editor = Hud.isEditingHud();
        int c1 = Theme.primary();
        if (effects.isEmpty() && !editor) return new int[]{116, 15};
        int w = 120, row = 0;
        if (effects.isEmpty()) {
            Hud.island(c, x, y, w, 15, 7, ColorUtil.withAlpha(c1, 90));
            Hud.dot(c, x + 6, y + 6, c1);
            Hud.txt(c, "Strength II", x + 13, y + 4, WHITE);
            Hud.txt(c, "0:42", x + w - Hud.tw("0:42") - 6, y + 4, c1);
            return new int[]{w, 15};
        }
        for (StatusEffectInstance se : effects) {
            int ry = y + row * 17;
            String raw = se.getEffectType().getIdAsString().replace("minecraft:", "").replace('_', ' ');
            String name = raw.isEmpty() ? "Effect" : Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
            if (se.getAmplifier() > 0) name += " " + roman(se.getAmplifier() + 1);
            if (name.length() > 14) name = name.substring(0, 13) + ".";

            int ticks = se.getDuration();
            boolean inf = se.isInfinite();
            String dur = inf ? "**:**" : String.format(Locale.ROOT, "%d:%02d", (ticks / 20) / 60, (ticks / 20) % 60);
            boolean warn = !inf && ticks < 200;
            int tCol = warn ? (((System.currentTimeMillis() / 250) % 2 == 0) ? 0xFFFF5555 : 0xFFFCA5A5) : GRAY;
            int dotCol = warn ? 0xFFFF5555 : c1;

            Hud.island(c, x, ry, w, 15, 7, ColorUtil.withAlpha(dotCol, 90));
            Hud.dot(c, x + 6, ry + 6, dotCol);
            Hud.txt(c, name, x + 13, ry + 4, WHITE);
            Hud.txt(c, dur, x + w - Hud.tw(dur) - 6, ry + 4, tCol);
            row++;
        }
        return new int[]{w, Math.max(15, row * 17)};
    }

    // ---------------- 4. ArrayList (Liquid Island Pills) ----------------
    private static int[] arrayList(DrawContext c, int x, int y) {
        if (arrayListDirty) {
            sortedModules.clear();
            for (Module m : ModuleManager.MODULES) {
                if (m.enabled && !m.name.equals("ArrayList") && !m.name.equals("Theme")) sortedModules.add(m);
            }
            sortedModules.sort((a, b) -> Integer.compare(Hud.tw(b.name), Hud.tw(a.name)));
            arrayListDirty = false;
        }
        int sw = mc.getWindow().getScaledWidth();
        boolean alignRight = x + 40 > sw / 2;
        int maxW = 64, ry = y;
        long now = System.currentTimeMillis();
        for (int i = 0; i < sortedModules.size(); i++) {
            Module m = sortedModules.get(i);
            int mw = Hud.tw(m.name) + 18;
            if (mw > maxW) maxW = mw;
            int rx = alignRight ? (x + maxW - mw) : x;
            int col = Theme.color(now, i * 0.06f, 255);
            Hud.island(c, rx, ry, mw, 14, 6, ColorUtil.withAlpha(col, 85));
            if (alignRight) {
                Hud.dot(c, rx + mw - 8, ry + 5, col);
                Hud.txt(c, m.name, rx + 6, ry + 3, WHITE);
            } else {
                Hud.dot(c, rx + 5, ry + 5, col);
                Hud.txt(c, m.name, rx + 12, ry + 3, WHITE);
            }
            ry += 16;
        }
        return new int[]{maxW, Math.max(15, ry - y)};
    }

    // ---------------- 5. Totem Counter ----------------
    private static int[] totemCounter(DrawContext c, int x, int y) {
        int count = ServerMetrics.totems();
        String s = String.valueOf(count);
        int w = 26 + Hud.tw(s), h = 20;
        boolean low = count <= 1;
        boolean blink = low && (System.currentTimeMillis() / 320) % 2 == 0;
        if (blink) RenderUtil.glass(c, x, y, w, h, 7, 0xCC450A0A, 0xCCEF4444);
        else Hud.island(c, x, y, w, h, 7, ColorUtil.withAlpha(Theme.primary(), 90));
        c.drawItem(new ItemStack(Items.TOTEM_OF_UNDYING), x + 4, y + 2);
        Hud.txt(c, s, x + 22, y + 6, low ? 0xFFFF5555 : WHITE);
        return new int[]{w, h};
    }

    // ---------------- 6. Armor HUD Pro ----------------
    private static int[] armorPro(DrawContext c, int x, int y) {
        Module m = ModuleManager.get("Armor HUD Pro");
        boolean vert = m.find("Layout") instanceof Setting.Mode md && md.get().equals("Vertical");
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        int w = vert ? 56 : 114;
        int h = vert ? 76 : 28;
        Hud.island(c, x, y, w, h, 8, ColorUtil.withAlpha(Theme.primary(), 95));
        for (int i = 0; i < slots.length; i++) {
            ItemStack st = mc.player.getEquippedStack(slots[i]);
            int ix = vert ? x + 5 : x + 5 + i * 26;
            int iy = vert ? y + 4 + i * 18 : y + 3;
            if (st.isEmpty()) continue;
            c.drawItem(st, ix, iy);
            if (st.isDamageable() && st.getMaxDamage() > 0) {
                double pct = Math.max(0.0, 1.0 - (double) st.getDamage() / st.getMaxDamage());
                int col = pct > 0.5 ? 0xFF4ADE80 : (pct > 0.2 ? 0xFFFACC15 : 0xFFEF4444);
                String pTxt = Math.round(pct * 100) + "%";
                if (vert) {
                    Hud.txt(c, pTxt, ix + 19, iy + 4, col);
                    RenderUtil.roundRect(c, ix + 19, iy + 13, Math.max(2, (int) Math.round(28 * pct)), 2, 1, col);
                } else {
                    RenderUtil.roundRect(c, ix + 1, iy + 17, Math.max(2, (int) Math.round(14 * pct)), 2, 1, col);
                    Hud.txt(c, pTxt, ix, iy + 20, col);
                }
            }
        }
        return new int[]{w, h};
    }

    // ---------------- 7. Hotbar Customizer ----------------
    public static void renderCustomHotbar(DrawContext c, float td) {
        if (mc.player == null) return;
        int sw = c.getScaledWindowWidth(), sh = c.getScaledWindowHeight();
        int bw = 184, bh = 24;
        int x = (sw - bw) / 2, y = sh - bh - 4;
        int c1 = Theme.primary(), c2 = Theme.secondary();

        Hud.island(c, x, y, bw, bh, 8, ColorUtil.withAlpha(c1, 110));

        PlayerInventory inv = mc.player.getInventory();
        int sel = inv.getSelectedSlot();
        smoothSlot += (sel - smoothSlot) * 0.35f;
        int hx = x + 3 + Math.round(smoothSlot * 20);
        RenderUtil.glass(c, hx, y + 2, 18, 20, 6, 0x33FFFFFF, ColorUtil.withAlpha(c1, 160));
        RenderUtil.roundGradientH(c, hx + 3, y + 20, 12, 2, 1, c1, c2);

        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getStack(i);
            if (!st.isEmpty()) {
                int ix = x + 4 + i * 20;
                int iy = y + 4;
                c.drawItem(st, ix, iy);
                c.drawStackOverlay(mc.textRenderer, st, ix, iy);
            }
        }

        ItemStack off = mc.player.getOffHandStack();
        if (!off.isEmpty()) {
            int ox = x - 28;
            Hud.island(c, ox, y, 24, 24, 8, ColorUtil.withAlpha(c1, 110));
            c.drawItem(off, ox + 4, y + 4);
            c.drawStackOverlay(mc.textRenderer, off, ox + 4, y + 4);
        }
    }

    // ---------------- 8. Ping & TPS / Speedometer / Kill & Combo ----------------
    private static int[] pingTps(DrawContext c, int x, int y) {
        float tps = ServerMetrics.tps();
        int ping = ServerMetrics.ping();
        String s = String.format(Locale.ROOT, "%dms · %.1f TPS", ping, tps);
        int w = Hud.tw(s) + 20, h = 15;
        int col = tps >= 18.5f ? 0xFF4ADE80 : (tps >= 15f ? 0xFFFACC15 : 0xFFEF4444);
        Hud.island(c, x, y, w, h, 7, ColorUtil.withAlpha(col, 95));
        Hud.dot(c, x + 6, y + 6, col);
        Hud.txt(c, s, x + 13, y + 4, WHITE);
        return new int[]{w, h};
    }

    private static int[] speedometer(DrawContext c, int x, int y) {
        String val = String.format(Locale.ROOT, "%.2f b/s", ServerMetrics.bps());
        return Hud.labelPublic(c, x, y, "BPS", val);
    }

    private static int[] killCombo(DrawContext c, int x, int y) {
        int k = ServerMetrics.kills(), d = ServerMetrics.deaths(), cb = ServerMetrics.combo();
        double kd = d == 0 ? k : (double) k / d;
        String val = String.format(Locale.ROOT, "%dC · %dK · %.2f K/D", cb, k, kd);
        return Hud.labelPublic(c, x, y, "PVP", val);
    }

    private static String roman(int n) {
        return switch (n) {
            case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            default -> n > 5 ? String.valueOf(n) : "I";
        };
    }
}
