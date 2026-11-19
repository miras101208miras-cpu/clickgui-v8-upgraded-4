package com.example.clickgui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.LightType;

import java.time.LocalTime;
import java.util.*;

/** 15 новых передвигаемых HUD-виджетов + Radar Mini. */
public final class HudExtra {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final int BG = 0xAA101620, WHITE = 0xFFFFFFFF, GRAY = 0xFFB4BECB;

    private static final int[] FPS_RING = new int[28];
    private static final int[] CPS_RING = new int[28];
    private static int graphIdx, graphTick;
    private static final long SESSION_START = System.currentTimeMillis();
    private static final ItemStack PEARL_STACK = new ItemStack(Items.ENDER_PEARL);

    private HudExtra() {}

    public static void tick(int curCps) {
        if (graphTick++ % 4 == 0) {
            FPS_RING[graphIdx % FPS_RING.length] = mc.getCurrentFps();
            CPS_RING[graphIdx % CPS_RING.length] = curCps;
            graphIdx++;
        }
    }

    public static int[] draw(DrawContext c, String n, int x, int y, float td) {
        return switch (n) {
            case "Radar Mini" -> radarMini(c, x, y);
            case "Clock HUD" -> clockHud(c, x, y);
            case "Reach Display" -> reachDisplay(c, x, y);
            case "CPS Graph" -> graph(c, x, y, "CPS", CPS_RING, 18);
            case "FPS Graph" -> graph(c, x, y, "FPS", FPS_RING, 240);
            case "Memory HUD" -> memoryHud(c, x, y);
            case "Server IP HUD" -> serverIpHud(c, x, y);
            case "Direction HUD" -> directionHud(c, x, y);
            case "Saturation HUD" -> saturationHud(c, x, y);
            case "Elytra HUD" -> elytraHud(c, x, y);
            case "Players Nearby" -> playersNearby(c, x, y);
            case "Arrow Counter" -> arrowCounter(c, x, y);
            case "Consumables HUD" -> consumablesHud(c, x, y, td);
            case "Session Timer" -> sessionTimer(c, x, y);
            case "Biome & Light HUD" -> biomeLightHud(c, x, y);
            case "XP Progress HUD" -> xpProgressHud(c, x, y);
            case "Nether Coords HUD" -> netherCoordsHud(c, x, y);
            case "Target Armor Percent HUD" -> targetArmorHud(c, x, y);
            default -> new int[]{0, 0};
        };
    }

    private static int[] radarMini(DrawContext c, int x, int y) {
        Module m = ModuleManager.get("Radar Mini");
        int sz = (int) m.num("Size");
        double range = m.num("Range");
        Hud.island(c, x, y, sz, sz, 8, ColorUtil.withAlpha(Theme.primary(), 110));
        c.fill(x + 4, y, x + sz - 4, y + 1, Theme.primary());
        int cx = x + sz / 2, cy = y + sz / 2;
        c.fill(cx - 1, cy - 1, cx + 2, cy + 2, WHITE);
        double yawRad = Math.toRadians(mc.player.getYaw());
        double cos = Math.cos(yawRad), sin = Math.sin(yawRad);
        boolean mobs = m.bool("Mobs");
        if (mc.world != null) {
            for (var e : mc.world.getEntities()) {
                if (e == mc.player) continue;
                boolean isPl = e instanceof PlayerEntity;
                boolean isMb = mobs && e instanceof MobEntity;
                if (!isPl && !isMb) continue;
                double dx = e.getX() - mc.player.getX(), dz = e.getZ() - mc.player.getZ();
                double rx = -(dx * cos + dz * sin), rz = (dx * sin - dz * cos);
                double scale = (sz * 0.45) / Math.max(8.0, range);
                int px = (int) MathUtil.clamp(cx + rx * scale, x + 3, x + sz - 4);
                int py = (int) MathUtil.clamp(cy + rz * scale, y + 3, y + sz - 4);
                int col = isPl ? (HudWorld.isFriend(e.getName().getString()) ? 0xFF4ADE80 : Theme.primary()) : 0xFFFB7185;
                c.fill(px - 1, py - 1, px + 1, py + 1, col);
            }
        }
        return new int[]{sz, sz};
    }

    private static int[] clockHud(DrawContext c, int x, int y) {
        Module m = ModuleManager.get("Clock HUD");
        LocalTime lt = LocalTime.now();
        String s = m.bool("Seconds")
                ? String.format(Locale.ROOT, "%02d:%02d:%02d", lt.getHour(), lt.getMinute(), lt.getSecond())
                : String.format(Locale.ROOT, "%02d:%02d", lt.getHour(), lt.getMinute());
        if (m.bool("Game Time") && mc.world != null) {
            s += " · Day " + (mc.world.getTimeOfDay() / 24000L);
        }
        return Hud.labelPublic(c, x, y, "TIME", s);
    }

    private static int[] reachDisplay(DrawContext c, int x, int y) {
        boolean fresh = System.currentTimeMillis() - ExtraVisuals.lastReachTime() < 4000L;
        String val = fresh ? String.format(Locale.ROOT, "%.2f b", ExtraVisuals.lastReach()) : "0.00 b";
        return Hud.labelPublic(c, x, y, "REACH", val);
    }

    private static int[] graph(DrawContext c, int x, int y, String title, int[] ring, int floorMax) {
        int w = 92, h = 34;
        Hud.island(c, x, y, w, h, 7, ColorUtil.withAlpha(Theme.primary(), 90));
        int latest = ring[(Math.max(0, graphIdx - 1)) % ring.length];
        Hud.txt(c, title + " " + latest, x + 5, y + 3, WHITE);
        int max = floorMax;
        for (int v : ring) if (v > max) max = v;
        int gx = x + 4, gy = y + h - 4, bw = 2, gap = 1;
        for (int i = 0; i < ring.length; i++) {
            int val = ring[(graphIdx + i) % ring.length];
            int bh = Math.max(1, Math.round(16f * val / Math.max(1, max)));
            int col = Theme.gradient((float) i / ring.length);
            c.fill(gx + i * (bw + gap), gy - bh, gx + i * (bw + gap) + bw, gy, col);
        }
        return new int[]{w, h};
    }

    private static int[] memoryHud(DrawContext c, int x, int y) {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / (1024L * 1024L);
        long max = Math.max(1L, rt.maxMemory() / (1024L * 1024L));
        int pct = (int) Math.round(100.0 * used / max);
        String txt = pct + "% · " + used + "/" + max + "MB";
        if (!ModuleManager.get("Memory HUD").bool("Bar")) return Hud.labelPublic(c, x, y, "MEM", txt);
        int w = Math.max(96, Hud.tw("MEM " + txt) + 14), h = 19;
        Hud.island(c, x, y, w, h, 7, ColorUtil.withAlpha(Theme.primary(), 90));
        Hud.txt(c, "MEM", x + 5, y + 3, Theme.primary());
        Hud.txt(c, txt, x + 9 + Hud.tw("MEM"), y + 3, WHITE);
        RenderUtil.roundRect(c, x + 5, y + 14, w - 10, 2, 1, 0x33FFFFFF);
        RenderUtil.roundGradientH(c, x + 5, y + 14, Math.max(2, Math.round((w - 10) * pct / 100f)), 2, 1, Theme.primary(), Theme.secondary());
        return new int[]{w, h};
    }

    private static int[] serverIpHud(DrawContext c, int x, int y) {
        var entry = mc.getCurrentServerEntry();
        String ip = (entry != null && entry.address != null) ? entry.address : "Singleplayer";
        int online = mc.getNetworkHandler() == null ? 1 : mc.getNetworkHandler().getPlayerList().size();
        return Hud.labelPublic(c, x, y, "SRV", ip + " (" + online + ")");
    }

    private static int[] directionHud(DrawContext c, int x, int y) {
        float yaw = MathHelper.wrapDegrees(mc.player.getYaw());
        float deg = (yaw + 360f) % 360f;
        String[] dirs = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        String d = dirs[Math.round(deg / 45f) % 8];
        return Hud.labelPublic(c, x, y, "DIR", String.format(Locale.ROOT, "%s · %.0f°", d, deg));
    }

    private static int[] saturationHud(DrawContext c, int x, int y) {
        var hm = mc.player.getHungerManager();
        String s = hm.getFoodLevel() + "/20 · Sat " + String.format(Locale.ROOT, "%.1f", hm.getSaturationLevel());
        return Hud.labelPublic(c, x, y, "FOOD", s);
    }

    private static int[] elytraHud(DrawContext c, int x, int y) {
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        boolean has = chest.isOf(Items.ELYTRA) && chest.getMaxDamage() > 0;
        if (!has && !Hud.isEditingHud()) return new int[]{84, 14};
        int pct = has ? (int) Math.round(100.0 * (chest.getMaxDamage() - chest.getDamage()) / chest.getMaxDamage()) : 78;
        int col = pct <= ModuleManager.get("Elytra HUD").num("Warn %") ? 0xFFFF5555 : WHITE;
        int w = 86, h = 14;
        Hud.island(c, x, y, w, h, 7, ColorUtil.withAlpha(Theme.primary(), 90));
        Hud.txt(c, "ELYTRA", x + 5, y + 3, Theme.primary());
        Hud.txt(c, pct + "%", x + w - Hud.tw(pct + "%") - 5, y + 3, col);
        return new int[]{w, h};
    }

    private static int[] playersNearby(DrawContext c, int x, int y) {
        double maxR = ModuleManager.get("Players Nearby").num("Range");
        List<String> lines = new ArrayList<>();
        if (mc.world != null) {
            for (PlayerEntity pe : mc.world.getPlayers()) {
                if (pe == mc.player || !pe.isAlive() || pe.isInvisible()) continue;
                double d = mc.player.distanceTo(pe);
                if (d <= maxR) lines.add(pe.getName().getString() + " · " + Math.round(d) + "m");
                if (lines.size() >= 4) break;
            }
        }
        if (lines.isEmpty() && Hud.isEditingHud()) lines.add("Alex · 14m");
        if (lines.isEmpty()) return new int[]{92, 16};
        int w = 96;
        for (String s : lines) w = Math.max(w, Hud.tw(s) + 12);
        int h = 15 + lines.size() * 10;
        Hud.island(c, x, y, w, h, 7, ColorUtil.withAlpha(Theme.primary(), 90));
        c.fill(x + 4, y, x + w - 4, y + 1, Theme.primary());
        Hud.txt(c, "Nearby (" + lines.size() + ")", x + 6, y + 3, Theme.primary());
        for (int i = 0; i < lines.size(); i++) Hud.txt(c, lines.get(i), x + 6, y + 14 + i * 10, WHITE);
        return new int[]{w, h};
    }

    private static int[] arrowCounter(DrawContext c, int x, int y) {
        int arrows = 0, rockets = 0;
        PlayerInventory inv = mc.player.getInventory();
        for (ItemStack st : inv.getMainStacks()) {
            if (st.isOf(Items.ARROW) || st.isOf(Items.TIPPED_ARROW) || st.isOf(Items.SPECTRAL_ARROW)) arrows += st.getCount();
            else if (st.isOf(Items.FIREWORK_ROCKET)) rockets += st.getCount();
        }
        if (mc.player.getOffHandStack().isOf(Items.FIREWORK_ROCKET)) rockets += mc.player.getOffHandStack().getCount();
        return Hud.labelPublic(c, x, y, "AMMO", arrows + " arr · " + rockets + " rkt");
    }

    private static int[] consumablesHud(DrawContext c, int x, int y, float td) {
        int pearls = 0, gaps = 0, egaps = 0;
        for (ItemStack st : mc.player.getInventory().getMainStacks()) {
            if (st.isOf(Items.ENDER_PEARL)) pearls += st.getCount();
            else if (st.isOf(Items.GOLDEN_APPLE)) gaps += st.getCount();
            else if (st.isOf(Items.ENCHANTED_GOLDEN_APPLE)) egaps += st.getCount();
        }
        float pcd = mc.player.getItemCooldownManager().getCooldownProgress(PEARL_STACK, td);
        String s = "P:" + pearls + (pcd > 0 ? "(" + Math.round(pcd * 100) + "%)" : "") + " G:" + gaps + " E:" + egaps;
        return Hud.labelPublic(c, x, y, "ITEMS", s);
    }

    private static int[] sessionTimer(DrawContext c, int x, int y) {
        long sec = (System.currentTimeMillis() - SESSION_START) / 1000L;
        String t = String.format(Locale.ROOT, "%02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60);
        return Hud.labelPublic(c, x, y, "SESS", t + " · " + ExtraVisuals.sessionHits() + " hits");
    }

    private static int[] biomeLightHud(DrawContext c, int x, int y) {
        BlockPos bp = mc.player.getBlockPos();
        int bl = mc.world == null ? 0 : mc.world.getLightLevel(LightType.BLOCK, bp);
        return Hud.labelPublic(c, x, y, "WORLD", ServerMetrics.biome() + " · L" + bl + " Y" + bp.getY());
    }

    private static int[] xpProgressHud(DrawContext c, int x, int y) {
        int lvl = mc.player.experienceLevel;
        float prog = MathUtil.clamp01(mc.player.experienceProgress);
        int pct = Math.round(prog * 100f);
        String txt = "Lvl " + lvl + " · " + pct + "%";
        if (!ModuleManager.get("XP Progress HUD").bool("Bar")) return Hud.labelPublic(c, x, y, "XP", txt);
        int w = Math.max(88, Hud.tw("XP " + txt) + 14), h = 19;
        Hud.island(c, x, y, w, h, 7, ColorUtil.withAlpha(Theme.primary(), 90));
        Hud.txt(c, "XP", x + 5, y + 3, Theme.primary());
        Hud.txt(c, txt, x + 9 + Hud.tw("XP"), y + 3, WHITE);
        RenderUtil.roundRect(c, x + 5, y + 14, w - 10, 2, 1, 0x33FFFFFF);
        RenderUtil.roundGradientH(c, x + 5, y + 14, Math.max(2, Math.round((w - 10) * prog)), 2, 1, Theme.primary(), Theme.secondary());
        return new int[]{w, h};
    }

    private static int[] netherCoordsHud(DrawContext c, int x, int y) {
        String dim = Waypoints.currentDim();
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        boolean round = ModuleManager.get("Nether Coords HUD").bool("Rounded");
        String tag;
        String val;
        if (dim.contains("nether")) {
            tag = "OW";
            val = round ? String.format(Locale.ROOT, "%d, %d, %d", Math.round(px * 8.0), Math.round(py), Math.round(pz * 8.0))
                    : String.format(Locale.ROOT, "%.1f, %.0f, %.1f", px * 8.0, py, pz * 8.0);
        } else if (dim.contains("end")) {
            tag = "NET";
            val = "End (no link)";
        } else {
            tag = "NET";
            val = round ? String.format(Locale.ROOT, "%d, %d, %d", Math.round(px / 8.0), Math.round(py), Math.round(pz / 8.0))
                    : String.format(Locale.ROOT, "%.1f, %.0f, %.1f", px / 8.0, py, pz / 8.0);
        }
        return Hud.labelPublic(c, x, y, tag, val);
    }

    private static int[] targetArmorHud(DrawContext c, int x, int y) {
        var le = Effects.lastTarget();
        if (le == null && Hud.isEditingHud()) le = mc.player;
        if (le == null) return new int[]{118, 26};
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        int count = 0;
        for (EquipmentSlot s : slots) if (!le.getEquippedStack(s).isEmpty()) count++;
        if (count == 0 && !Hud.isEditingHud()) return new int[]{118, 26};
        int w = 118, h = 26;
        Hud.island(c, x, y, w, h, 7, ColorUtil.withAlpha(Theme.primary(), 90));
        c.fill(x + 4, y, x + w - 4, y + 1, Theme.primary());
        boolean showIcons = ModuleManager.get("Target Armor Percent HUD").bool("Icons");
        for (int i = 0; i < slots.length; i++) {
            ItemStack st = le.getEquippedStack(slots[i]);
            int sx = x + 5 + i * 28;
            if (!st.isEmpty()) {
                if (showIcons) c.drawItem(st, sx + 4, y + 2);
                int pct = st.getMaxDamage() > 0
                        ? Math.max(0, Math.round(100f * (st.getMaxDamage() - st.getDamage()) / st.getMaxDamage()))
                        : 100;
                int col = pct > 55 ? 0xFF4ADE80 : pct > 25 ? 0xFFFBBF24 : 0xFFFF5555;
                String p = pct + "%";
                Hud.txt(c, p, sx + 12 - Hud.tw(p) / 2, y + 17, col);
            } else {
                Hud.txt(c, "-", sx + 10, y + 10, GRAY);
            }
        }
        return new int[]{w, h};
    }
}
