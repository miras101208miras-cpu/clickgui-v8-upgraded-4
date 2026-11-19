package com.example.clickgui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.*;

/**
 * Item Pickup Feed:
 * Сверяет инвентарь ТОЛЬКО при изменении PlayerInventory.getChangeCount() (0 нагрузки в простое),
 * фиксирует прирост предметов и выводит ленту (+3 Golden Apple).
 */
public final class PickupFeed {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private record Entry(Item item, String name, int count, long at) {}
    private static final ArrayDeque<Entry> FEED = new ArrayDeque<>();
    private static final Map<Item, Integer> LAST = new HashMap<>();
    private static int lastChange = -1;

    public static void tick() {
        if (mc.player == null) { LAST.clear(); FEED.clear(); lastChange = -1; return; }
        PlayerInventory inv = mc.player.getInventory();
        int cc = inv.getChangeCount();
        if (cc == lastChange) return;

        Map<Item, Integer> nowMap = new HashMap<>();
        for (ItemStack st : inv.getMainStacks()) {
            if (!st.isEmpty()) nowMap.merge(st.getItem(), st.getCount(), Integer::sum);
        }
        ItemStack off = mc.player.getOffHandStack();
        if (!off.isEmpty()) nowMap.merge(off.getItem(), off.getCount(), Integer::sum);

        if (lastChange != -1 && ModuleManager.get("Pickup Feed").enabled) {
            long t = System.currentTimeMillis();
            for (var e : nowMap.entrySet()) {
                int diff = e.getValue() - LAST.getOrDefault(e.getKey(), 0);
                if (diff > 0) {
                    String n = new ItemStack(e.getKey()).getName().getString();
                    FEED.addFirst(new Entry(e.getKey(), n, diff, t));
                    while (FEED.size() > 5) FEED.removeLast();
                }
            }
        }
        LAST.clear();
        LAST.putAll(nowMap);
        lastChange = cc;
    }

    public static int[] render(DrawContext c, int x, int y) {
        long now = System.currentTimeMillis();
        FEED.removeIf(e -> now - e.at() > 3000);
        if (FEED.isEmpty() && Hud.isEditingHud()) {
            String sample = "+3 Golden Apple";
            int w = 24 + Hud.tw(sample);
            Hud.island(c, x, y, w, 18, 7, ColorUtil.withAlpha(0xFF4ADE80, 95));
            c.drawItem(new ItemStack(Items.GOLDEN_APPLE), x + 3, y + 1);
            Hud.txt(c, sample, x + 21, y + 5, 0xFF4ADE80);
            return new int[]{w, 18};
        }
        int maxW = 90, ry = y;
        for (Entry e : FEED) {
            String txt = "+" + e.count() + " " + e.name();
            if (txt.length() > 24) txt = txt.substring(0, 23) + ".";
            int w = 24 + Hud.tw(txt);
            if (w > maxW) maxW = w;
            Hud.island(c, x, ry, w, 18, 7, ColorUtil.withAlpha(0xFF4ADE80, 95));
            c.drawItem(new ItemStack(e.item()), x + 3, ry + 1);
            Hud.txt(c, txt, x + 21, ry + 5, 0xFF4ADE80);
            ry += 20;
        }
        return new int[]{maxW, Math.max(18, ry - y)};
    }
}
