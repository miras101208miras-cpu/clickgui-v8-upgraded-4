package com.example.clickgui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Toast-уведомления о биндах, захвате/потере цели и низком HP/броне. */
public final class Notifications {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private record Toast(String title, String body, int accent, long createdAt, long durationMs) {}
    private static final ConcurrentLinkedDeque<Toast> QUEUE = new ConcurrentLinkedDeque<>();

    public static void push(String title, String body, int accent) {
        if (!ModuleManager.get("Notifications").enabled) return;
        QUEUE.addFirst(new Toast(title, body, accent, System.currentTimeMillis(), 2400));
        while (QUEUE.size() > 5) QUEUE.pollLast();
    }

    public static void onModuleToggle(Module m) {
        if (m.name.equals("Notifications") || mc.player == null) return;
        if (!ModuleManager.get("Notifications").bool("Keybinds")) return;
        push(m.name, m.enabled ? "Enabled" : "Disabled", m.enabled ? 0xFF4ADE80 : 0xFFEF4444);
    }

    public static void onTargetAcquire(LivingEntity le) {
        if (!ModuleManager.get("Notifications").bool("Target")) return;
        push("Target Locked", le.getName().getString(), Theme.accent());
    }

    public static void onTargetLost(LivingEntity le) {
        if (!ModuleManager.get("Notifications").bool("Target")) return;
        push("Target Lost", le.getName().getString(), 0xFF94A3B8);
    }

    public static int[] render(DrawContext c, int x, int y) {
        long now = System.currentTimeMillis();
        Iterator<Toast> it = QUEUE.iterator();
        while (it.hasNext()) {
            Toast t = it.next();
            if (now - t.createdAt() > t.durationMs()) it.remove();
        }
        int maxW = 128, ry = y;
        if (QUEUE.isEmpty() && Hud.isEditingHud()) {
            Hud.island(c, x, y, maxW, 22, 7, ColorUtil.withAlpha(Theme.accent(), 110));
            c.fill(x, y + 3, x + 2, y + 19, Theme.accent());
            Hud.txt(c, "Notifications", x + 6, y + 3, 0xFFFFFFFF);
            Hud.txt(c, "Toggle & Alerts", x + 6, y + 12, 0xFF94A3B8);
            return new int[]{maxW, 22};
        }
        for (Toast t : QUEUE) {
            double age = (double) (now - t.createdAt()) / t.durationMs();
            float alpha = age < 0.12 ? (float) (age / 0.12) : (age > 0.8 ? (float) ((1.0 - age) / 0.2) : 1f);
            int a = Math.max(0, Math.min(255, Math.round(alpha * 210)));
            int bg = (Math.round(alpha * 218) << 24) | 0x080B12;
            int w = Math.max(115, Math.max(Hud.tw(t.title()), Hud.tw(t.body())) + 14);
            if (w > maxW) maxW = w;
            RenderUtil.glass(c, x, ry, w, 22, 7, bg, (a << 24) | (t.accent() & 0xFFFFFF));
            c.fill(x, ry + 3, x + 2, ry + 19, (a << 24) | (t.accent() & 0xFFFFFF));
            Hud.txt(c, t.title(), x + 6, ry + 3, (a << 24) | 0xFFFFFF);
            Hud.txt(c, t.body(), x + 6, ry + 12, (a << 24) | 0xCBD5E1);
            ry += 25;
        }
        return new int[]{maxW, Math.max(22, ry - y)};
    }
}
