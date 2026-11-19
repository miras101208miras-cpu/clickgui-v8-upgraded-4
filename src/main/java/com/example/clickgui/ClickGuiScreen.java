package com.example.clickgui;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.function.IntConsumer;

public class ClickGuiScreen extends Screen {
    private static final int PAD = 8, TOP = 20, GAP = 6, SIDE_W = 108, ROW = 20, SUB = 16, RADIUS = 10;
    private static final int GLASS = 0xAA101620, BORDER = 0x24FFFFFF, TINT = 0xB8090D15,
            SIDE_BG = 0xC40C111A, CARD_OFF = 0x9E101620, CARD_ON = 0xD8152034,
            WHITE = 0xFFFFFFFF, SOFT = 0xFFD8E0EC, DARK = 0xFF1A1D24, GRAY = 0xFF94A3B8;

    private static final Identifier[] BG_TEXTURES = {
            Identifier.of("clickgui", "textures/gui/background.png"),
            Identifier.of("clickgui", "textures/gui/bg_desert.png"),
            Identifier.of("clickgui", "textures/gui/bg_jungle.png"),
            Identifier.of("clickgui", "textures/gui/bg_mushroom.png"),
            Identifier.of("clickgui", "textures/gui/bg_badlands.png")
    };
    private static final int[] BG_WIDTHS = {1600, 830, 830, 830, 830};
    private static final int[] BG_HEIGHTS = {900, 465, 465, 465, 465};

    private static final StyleSpriteSource FONT = new StyleSpriteSource.Font(Identifier.of("clickgui", "main"));
    private static final String[] ICONS = {"◉", "▣", "⌖", "☀", "◎", "✿"};

    private static final Set<String> PRESET_PVP = Set.of(
            "Target ESP", "Outlines", "Low Fire", "No Fog", "Hitbox Styler",
            "Target HUD", "Potion HUD", "Low Health Vignette", "Auto Sprint"
    );
    private static final Set<String> PRESET_FPS = Set.of(
            "FPS Booster", "Mine Particles Off", "Clear Weather Visual",
            "Unfocused FPS", "No Fog", "FPS Counter"
    );
    private static final Set<String> PRESET_CLEAN = Set.of(
            "Watermark", "Coordinates", "Clear Weather Visual", "Smooth Sneak", "No World Bobbing"
    );

    private static Category current = Category.VISUALS;
    private static boolean russian = false;
    private static String search = "";
    private static String activePreset = "";
    private static String wpNameInput = "";
    private static int wpColorIdx = 0;
    private static String copiedWpId;
    private static long copiedAtMs;

    private record Hit(int x, int y, int w, int h, IntConsumer action) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final List<Hit> hits = new ArrayList<>();
    private double scroll, targetScroll, clickX;
    private int contentH, clipTop, clipBottom;
    private Setting.Slider dragSlider;
    private int sliderX, sliderW;
    private Module binding;
    private boolean searchFocused, wpNameFocused;
    private int searchBoxX, searchBoxY, searchBoxW, searchBoxH;
    private int wpBoxX, wpBoxY, wpBoxW, wpBoxH;

    public ClickGuiScreen() { super(Text.literal("ClickGUI")); }
    public static boolean isRussian() { return russian; }
    public static void setRussian(boolean ru) { russian = ru; }

    private Text t(String s) { return Text.literal(s).setStyle(Style.EMPTY.withFont(FONT)); }
    private int tw(String s) { return textRenderer.getWidth(t(s)); }
    private void txt(DrawContext c, String s, int x, int y, int col) { c.drawText(textRenderer, t(s), x, y, col, false); }
    private void ctxt(DrawContext c, String s, int cx, int y, int col) { txt(c, s, cx - tw(s) / 2, y, col); }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        Animation.beginFrame();
        super.render(ctx, mx, my, delta);
        hits.clear();

        int winW = Math.min(484, Math.max(360, width - 16));
        int winH = Math.min(278, Math.max(210, height - 16));
        int x = (width - winW) / 2, y = (height - winH) / 2;

        int bgIdx = 0;
        Setting bgSet = ModuleManager.get("Theme").find("Background");
        if (bgSet instanceof Setting.Mode md) {
            bgIdx = Math.max(0, Math.min(BG_TEXTURES.length - 1, md.index));
        }

        roundRect(ctx, x - 1, y - 1, winW + 2, winH + 2, RADIUS + 1, BORDER);
        roundTexture(ctx, BG_TEXTURES[bgIdx], BG_WIDTHS[bgIdx], BG_HEIGHTS[bgIdx], x, y, winW, winH, RADIUS);
        roundRect(ctx, x, y, winW, winH, RADIUS, TINT);

        clipTop = 0;
        clipBottom = height;
        wpBoxW = 0;

        renderSidebar(ctx, x, y, SIDE_W, winH, mx, my);

        int mainX = x + SIDE_W + PAD;
        int mainY = y + PAD;
        int mainW = winW - SIDE_W - PAD * 2;
        int mainH = winH - PAD * 2;
        renderMainArea(ctx, mainX, mainY, mainW, mainH, mx, my);
    }

    private void renderSidebar(DrawContext ctx, int x, int y, int w, int h, int mx, int my) {
        int c1 = Theme.primary(), c2 = Theme.secondary();
        roundRect(ctx, x, y, w, h, RADIUS, SIDE_BG);
        ctx.fill(x + w - 1, y + 6, x + w, y + h - 6, 0x1AFFFFFF);

        // Brand Header
        int hx = x + 8, hy = y + 9;
        RenderUtil.glass(ctx, hx, hy, 18, 18, 5, 0x22FFFFFF, ColorUtil.withAlpha(c1, 110));
        ctxt(ctx, "✦", hx + 9, hy + 5, c1);
        txt(ctx, "ClickGUI", hx + 23, hy + 1, WHITE);
        txt(ctx, "138 · 1.21.11", hx + 23, hy + 10, GRAY);

        // Category List
        int cy = hy + 26;
        for (Category c : Category.values()) {
            boolean on = c == current && search.isEmpty();
            boolean hov = inside(mx, my, x + 6, cy, w - 12, 20);
            if (on) {
                RenderUtil.glass(ctx, x + 6, cy, w - 12, 20, 5, 0x24FFFFFF, ColorUtil.withAlpha(c1, 90));
                ctx.fillGradient(x + 6, cy + 4, x + 8, cy + 16, c1, c2);
            } else if (hov) {
                roundRect(ctx, x + 6, cy, w - 12, 20, 5, 0x12FFFFFF);
            }
            txt(ctx, ICONS[c.ordinal()], x + 12, cy + 6, on ? c1 : GRAY);
            txt(ctx, c.title(russian), x + 24, cy + 6, on ? WHITE : SOFT);

            int cnt = c == Category.WAYPOINTS ? Waypoints.scopedList().size() : ModuleManager.byCategory(c).size();
            String cntStr = String.valueOf(cnt);
            int bw = Math.max(14, tw(cntStr) + 6);
            roundRect(ctx, x + w - 9 - bw, cy + 4, bw, 12, 3, on ? ColorUtil.withAlpha(c1, 55) : 0x12FFFFFF);
            ctxt(ctx, cntStr, x + w - 9 - bw / 2, cy + 6, on ? c1 : GRAY);

            hit(x + 6, cy, w - 12, 20, b -> {
                current = c;
                search = "";
                wpNameFocused = false;
                targetScroll = scroll = 0;
            });
            cy += 22;
        }

        // Bottom Sidebar Controls (NO HUD Editor button — HUD edits in Chat via T / /)
        int bx = x + 8, bw = w - 16;
        int saveY = y + h - 22;
        int langY = saveY - 20;
        int bgY = langY - 20;
        int hintY = bgY - 22;

        // Subtle hint for Chat HUD editing
        RenderUtil.glass(ctx, bx, hintY, bw, 18, 4, 0x0CFFFFFF, 0x16FFFFFF);
        ctxt(ctx, russian ? "HUD: в чате (T / /)" : "HUD: in chat (T / /)", bx + bw / 2, hintY + 5, GRAY);

        // Quick Background Wallpaper Selector
        Setting bgSet = ModuleManager.get("Theme").find("Background");
        String bgName = bgSet instanceof Setting.Mode md ? md.get() : "Default";
        boolean bgHov = inside(mx, my, bx, bgY, bw, 17);
        RenderUtil.glass(ctx, bx, bgY, bw, 17, 4, bgHov ? 0x1EFFFFFF : 0x10FFFFFF, BORDER);
        String bgLbl = (russian ? "Фон: " : "BG: ") + bgName;
        ctxt(ctx, bgLbl, bx + bw / 2, bgY + 5, bgHov ? c1 : SOFT);
        hit(bx, bgY, bw, 17, b -> {
            if (bgSet instanceof Setting.Mode md) {
                md.index = (md.index + (b == 1 ? md.options.size() - 1 : 1)) % md.options.size();
                ConfigManager.markDirty();
            }
        });

        // Language RUS | ENG Pill
        glass(ctx, bx, langY, bw, 17, 4);
        int half = (bw - 4) / 2;
        RenderUtil.roundGradientH(ctx, russian ? bx + 2 : bx + 2 + half, langY + 2, half, 13, 3, c1, c2);
        ctxt(ctx, "RUS", bx + 2 + half / 2, langY + 5, russian ? DARK : WHITE);
        ctxt(ctx, "ENG", bx + 2 + half + half / 2, langY + 5, russian ? WHITE : DARK);
        hit(bx, langY, bw, 17, b -> {
            russian = !russian;
            ConfigManager.markDirty();
        });

        // Save config.json Button
        String cfgLabel = ConfigManager.savedRecently() ? (russian ? "Сохранено ✓" : "Saved ✓") : "Save config.json";
        glass(ctx, bx, saveY, bw, 17, 4);
        ctxt(ctx, cfgLabel, bx + bw / 2, saveY + 5, ConfigManager.savedRecently() ? c1 : GRAY);
        hit(bx, saveY, bw, 17, b -> {
            ConfigManager.save();
            Waypoints.saveNow();
        });
    }

    private void renderMainArea(DrawContext ctx, int x, int y, int w, int h, int mx, int my) {
        int c1 = Theme.primary(), c2 = Theme.secondary();

        // Top Bar: Search (Ctrl+K) + Presets (PvP / FPS / Clean)
        String[] presets = {"PvP", "FPS", "Clean"};
        int presetTotalW = 0;
        for (String p : presets) presetTotalW += tw(p) + 14 + 4;

        int sw = Math.max(100, w - presetTotalW);
        int barH = TOP;
        searchBoxX = x; searchBoxY = y; searchBoxW = sw; searchBoxH = barH;

        RenderUtil.glass(ctx, x, y, sw, barH, 5, 0xAA101620, searchFocused ? ColorUtil.withAlpha(c1, 180) : BORDER);
        txt(ctx, "⌕", x + 6, y + 6, GRAY);
        boolean blink = searchFocused && ((System.currentTimeMillis() / 500L) & 1L) == 0L;
        if (search.isEmpty()) {
            txt(ctx, (russian ? "Поиск (138)..." : "Search 138...") + (blink ? "|" : ""), x + 17, y + 6, GRAY);
            String kLbl = "Ctrl+K";
            int kw = tw(kLbl) + 8;
            roundRect(ctx, x + sw - kw - 4, y + 4, kw, 12, 3, 0x14FFFFFF);
            ctxt(ctx, kLbl, x + sw - kw / 2 - 4, y + 6, GRAY);
        } else {
            txt(ctx, search + (blink ? "|" : ""), x + 17, y + 6, WHITE);
            int clX = x + sw - 14;
            ctxt(ctx, "×", clX + 6, y + 6, GRAY);
            hit(clX, y, 14, barH, b -> { search = ""; targetScroll = scroll = 0; });
        }

        int px = x + sw + 4;
        for (String p : presets) {
            int pw = tw(p) + 14;
            boolean on = p.equals(activePreset);
            if (on) RenderUtil.roundGradientH(ctx, px, y, pw, barH, 5, c1, c2);
            else glass(ctx, px, y, pw, barH, 5);
            ctxt(ctx, p, px + pw / 2, y + 6, on ? DARK : WHITE);
            hit(px, y, pw, barH, b -> applyPreset(p));
            px += pw + 4;
        }

        // Scrollable Content Viewport
        int listTop = y + barH + GAP;
        int listH = h - barH - GAP;
        float smoothK = 1f - (float) Math.exp(-18f * Animation.dt());
        scroll += (targetScroll - scroll) * smoothK;

        clipTop = listTop;
        clipBottom = listTop + listH;
        ctx.enableScissor(x, clipTop, x + w, clipBottom);

        int start = listTop + 2 - (int) scroll;
        int endY;

        if (search.trim().isEmpty() && current == Category.WAYPOINTS) {
            endY = renderWaypointsTab(ctx, x, start, w - 5, mx, my);
        } else {
            List<Module> list = visibleModules();
            if (list.isEmpty()) {
                ctxt(ctx, russian ? "Ничего не найдено" : "No modules found", x + w / 2, start + 24, GRAY);
                endY = start + 52;
            } else {
                int colGap = 6;
                int availW = w - 5;
                int colW = (availW - colGap) / 2;
                int[] colY = {start, start};
                for (int i = 0; i < list.size(); i++) {
                    int col = i & 1;
                    int cx = x + col * (colW + colGap);
                    colY[col] = renderModuleCard(ctx, list.get(i), cx, colY[col], colW, mx, my);
                }
                endY = Math.max(colY[0], colY[1]);
            }
        }
        ctx.disableScissor();

        contentH = endY - start + 4;
        double maxScroll = Math.max(0, contentH - listH);
        if (targetScroll > maxScroll) targetScroll = maxScroll;
        if (scroll > maxScroll) scroll = maxScroll;
        if (contentH > listH) {
            int barSize = Math.max(20, listH * listH / contentH);
            int barY = listTop + (int) ((listH - barSize) * (scroll / Math.max(1, contentH - listH)));
            roundRect(ctx, x + w - 3, barY, 2, barSize, 1, ColorUtil.withAlpha(c1, 180));
        }
    }

    private void applyPreset(String preset) {
        activePreset = preset;
        Set<String> target = switch (preset) {
            case "PvP" -> PRESET_PVP;
            case "FPS" -> PRESET_FPS;
            default -> PRESET_CLEAN;
        };
        for (Module m : ModuleManager.MODULES) {
            if (m.name.equals("Theme")) continue;
            m.enabled = target.contains(m.name);
        }
        HudCombat.markArrayListDirty();
        ConfigManager.markDirty();
        Notifications.push("Preset Applied", preset, Theme.primary());
    }

    private int renderWaypointsTab(DrawContext ctx, int x, int y, int w, int mx, int my) {
        y = renderModuleCard(ctx, ModuleManager.get("Waypoints"), x, y, w, mx, my);
        int c1 = Theme.primary(), c2 = Theme.secondary();
        int boxH = 44;
        RenderUtil.glass(ctx, x, y, w, boxH, 6, 0x99101620, BORDER);

        String clearLbl = russian ? "Очистить" : "Clear", addLbl = russian ? "+ Добавить здесь" : "+ Add Here";
        int clearW = tw(clearLbl) + 12, addW = tw(addLbl) + 14;
        int inputW = Math.max(70, w - 16 - addW - clearW - 8);
        int iy = y + 5, ih = 16;

        wpBoxX = x + 6; wpBoxY = iy; wpBoxW = inputW; wpBoxH = ih;
        RenderUtil.glass(ctx, wpBoxX, wpBoxY, wpBoxW, wpBoxH, 4, 0xAA0D131C, wpNameFocused ? ColorUtil.withAlpha(c1, 200) : BORDER);
        boolean blink = wpNameFocused && ((System.currentTimeMillis() / 500L) & 1L) == 0L;
        txt(ctx, (wpNameInput.isEmpty() ? (russian ? "Имя точки..." : "Waypoint name...") : wpNameInput) + (blink ? "|" : ""),
                wpBoxX + 6, iy + 4, wpNameInput.isEmpty() ? GRAY : WHITE);

        int addX = wpBoxX + inputW + 4;
        RenderUtil.roundGradientH(ctx, addX, iy, addW, ih, 4, c1, c2);
        ctxt(ctx, addLbl, addX + addW / 2, iy + 4, DARK);
        hit(addX, iy, addW, ih, b -> {
            if (Waypoints.addHere(wpNameInput, wpColorIdx == 0 ? c1 : Waypoints.PALETTE[wpColorIdx])) {
                wpNameInput = ""; wpNameFocused = false;
            }
        });

        int clX = addX + addW + 4;
        glass(ctx, clX, iy, clearW, ih, 4);
        ctxt(ctx, clearLbl, clX + clearW / 2, iy + 4, 0xFFFF7A7A);
        hit(clX, iy, clearW, ih, b -> Waypoints.clearScope());

        int sy = y + 26;
        String colLbl = russian ? "Цвет:" : "Color:";
        txt(ctx, colLbl, x + 6, sy + 2, GRAY);
        int sx = x + 10 + tw(colLbl);
        for (int i = 0; i < Waypoints.PALETTE.length; i++) {
            int swCol = i == 0 ? c1 : Waypoints.PALETTE[i];
            if (wpColorIdx == i) roundRect(ctx, sx - 1, sy, 12, 12, 3, WHITE);
            roundRect(ctx, sx, sy + 1, 10, 10, 2, swCol);
            int idx = i;
            hit(sx - 1, sy, 12, 12, b -> wpColorIdx = idx);
            sx += 14;
        }
        String hint = (russian ? "Сервер: " : "Scope: ") + Waypoints.currentServerKey();
        if (tw(hint) < w - (sx - x) - 10) txt(ctx, hint, x + w - 6 - tw(hint), sy + 2, GRAY);
        y += boxH + 6;

        List<Waypoints.Waypoint> list = Waypoints.scopedList();
        String curDim = Waypoints.currentDim();
        if (list.isEmpty()) {
            ctxt(ctx, russian ? "Нет вайпоинтов — нажми «+ Добавить здесь» или B" : "No waypoints — click «+ Add Here» or press B",
                    x + w / 2, y + 18, GRAY);
            return y + 40;
        }

        int colGap = 6, colW = (w - colGap) / 2;
        int[] colY = {y, y};
        for (int i = 0; i < list.size(); i++) {
            Waypoints.Waypoint wp = list.get(i);
            int col = i & 1;
            int cx = x + col * (colW + colGap);
            int cy = colY[col];
            boolean sameDim = wp.dim().equals(curDim);
            int cardH = 26;
            boolean hov = inside(mx, my, cx, cy, colW, cardH);
            int borderCol = wp.enabled ? ColorUtil.withAlpha(wp.color, 95) : BORDER;
            RenderUtil.glass(ctx, cx, cy, colW, cardH, 6, wp.enabled ? (hov ? CARD_ON : 0xB8141C2B) : CARD_OFF, borderCol);
            roundRect(ctx, cx + 5, cy + 6, 4, 14, 2, wp.color);
            hit(cx + 3, cy + 4, 8, 18, b -> Waypoints.cycleColor(wp));

            String title = (wp.death ? "☠ " : "") + wp.name();
            if (tw(title) > colW - 88) title = title.substring(0, Math.min(title.length(), 10)) + "..";
            txt(ctx, title, cx + 13, cy + 4, wp.enabled ? WHITE : GRAY);

            String distStr = "";
            if (client != null && client.player != null && sameDim) {
                double dx = wp.x - client.player.getX(), dy = wp.y - client.player.getY(), dz = wp.z - client.player.getZ();
                distStr = " · " + Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz)) + "m";
            }
            txt(ctx, String.format(Locale.ROOT, "%.0f, %.0f, %.0f%s", wp.x, wp.y, wp.z, distStr), cx + 13, cy + 15, GRAY);

            int delX = cx + colW - 15;
            glass(ctx, delX, cy + 6, 11, 14, 3);
            ctxt(ctx, "×", delX + 5, cy + 9, 0xFFFF7A7A);
            hit(delX, cy + 5, 12, 16, b -> Waypoints.remove(wp));

            int swX = delX - 24;
            drawSwitch(ctx, swX, cy + 8, wp.enabled ? 1f : 0f);
            hit(swX - 2, cy + 4, 23, 18, b -> Waypoints.toggle(wp));

            boolean copiedNow = wp.id.equals(copiedWpId) && System.currentTimeMillis() - copiedAtMs < 1200L;
            String cpLbl = copiedNow ? "✓" : "CP";
            int cpW = tw(cpLbl) + 8, cpX = swX - cpW - 4;
            glass(ctx, cpX, cy + 6, cpW, 14, 3);
            ctxt(ctx, cpLbl, cpX + cpW / 2, cy + 9, copiedNow ? c1 : SOFT);
            hit(cpX, cy + 5, cpW, 16, b -> {
                if (client != null && client.keyboard != null)
                    client.keyboard.setClipboard((int) Math.round(wp.x) + " " + (int) Math.round(wp.y) + " " + (int) Math.round(wp.z));
                copiedWpId = wp.id; copiedAtMs = System.currentTimeMillis();
            });
            colY[col] += cardH + 5;
        }
        return Math.max(colY[0], colY[1]);
    }

    private List<Module> visibleModules() {
        String q = search.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return ModuleManager.byCategory(current);
        List<Module> res = new ArrayList<>();
        for (Module m : ModuleManager.MODULES) if (m.name.toLowerCase(Locale.ROOT).contains(q)) res.add(m);
        return res;
    }

    private int renderModuleCard(DrawContext ctx, Module m, int x, int y, int w, int mx, int my) {
        m.anim = MathUtil.expSmooth(m.anim, m.enabled ? 1f : 0f, 16f, Animation.dt());
        int c1 = Theme.primary();

        int cardH = ROW;
        if (m.expanded) {
            cardH += measureExpandedHeight(m, w - 8);
        }

        boolean hover = inside(mx, my, x, y, w, ROW);
        int bg = m.enabled ? CARD_ON : (hover ? 0xB8141C2B : CARD_OFF);
        int border = m.enabled ? ColorUtil.withAlpha(c1, 105) : BORDER;
        RenderUtil.glass(ctx, x, y, w, cardH, 6, bg, border);

        String title = m.name;
        int maxTitleW = w - (m.key >= 0 ? 68 : 48);
        if (tw(title) > maxTitleW && title.length() > 12) {
            while (title.length() > 8 && tw(title + "..") > maxTitleW) title = title.substring(0, title.length() - 1);
            title += "..";
        }
        txt(ctx, title, x + 8, y + 6, m.enabled ? WHITE : SOFT);

        if (m.key >= 0) {
            String kb = InputUtil.Type.KEYSYM.createFromCode(m.key).getLocalizedText().getString();
            int kx = x + 12 + tw(title), kw = tw(kb) + 6;
            if (kx + kw < x + w - 42) {
                roundRect(ctx, kx, y + 4, kw, 12, 3, 0x16FFFFFF);
                txt(ctx, kb, kx + 3, y + 6, GRAY);
            }
        }

        int sx = x + w - 26;
        drawSwitch(ctx, sx, y + 5, m.anim);
        drawChevron(ctx, sx - 13, y + 8, m.expanded, m.expanded ? c1 : GRAY);

        hit(sx - 15, y, 12, ROW, b -> m.expanded = !m.expanded);
        hit(sx - 2, y, 25, ROW, b -> m.toggle());
        hit(x, y, w, ROW, b -> {
            if (b == 1) m.expanded = !m.expanded;
            else m.toggle();
        });

        int cy = y + ROW;
        if (m.expanded) {
            ctx.fill(x + 6, cy, x + w - 6, cy + 1, 0x16FFFFFF);
            cy += 3;
            for (Setting s : m.settings) cy = renderSetting(ctx, s, x + 4, cy, w - 8);
            String key = binding == m ? "..." : m.key < 0 ? "-" :
                    InputUtil.Type.KEYSYM.createFromCode(m.key).getLocalizedText().getString();
            txt(ctx, "Keybind", x + 10, cy + 4, GRAY);
            txt(ctx, key, x + w - 10 - tw(key), cy + 4, WHITE);
            hit(x + 4, cy, w - 8, SUB, b -> binding = m);
            cy += SUB + 3;
        }
        return y + cardH + 5;
    }

    private int measureExpandedHeight(Module m, int w) {
        int h = 3; // divider + top gap
        for (Setting s : m.settings) {
            if (s instanceof Setting.Bool) h += SUB;
            else if (s instanceof Setting.Slider) h += SUB + 6;
            else if (s instanceof Setting.Multi mu) {
                int cx = 6, lines = 1;
                for (String o : mu.options) {
                    int cw = tw(o) + 12;
                    if (cx + cw > w - 4) { cx = 6; lines++; }
                    cx += cw + 4;
                }
                h += 13 + lines * 16 + 3;
            } else if (s instanceof Setting.Mode md) {
                int cx = 6, lines = 1;
                for (String o : md.options) {
                    int cw = tw(o) + 12;
                    if (cx + cw > w - 4) { cx = 6; lines++; }
                    cx += cw + 4;
                }
                h += 13 + lines * 16 + 3;
            }
        }
        return h + SUB + 3; // Keybind row
    }

    private int renderSetting(DrawContext ctx, Setting s, int x, int y, int w) {
        int c1 = Theme.primary(), c2 = Theme.secondary();
        if (s instanceof Setting.Bool b) {
            b.anim = MathUtil.expSmooth(b.anim, b.value ? 1f : 0f, 16f, Animation.dt());
            txt(ctx, b.name, x + 6, y + 4, GRAY);
            drawSwitch(ctx, x + w - 24, y + 3, b.anim);
            hit(x, y, w, SUB, k -> { b.value = !b.value; ConfigManager.markDirty(); });
            return y + SUB;
        }
        if (s instanceof Setting.Slider sl) {
            txt(ctx, sl.name, x + 6, y + 2, GRAY);
            String fmt = sl.step >= 1 ? "%.0f" : (sl.step < 0.1 ? "%.2f" : "%.1f");
            String val = String.format(Locale.ROOT, fmt, sl.value);
            txt(ctx, val, x + w - 6 - tw(val), y + 2, WHITE);
            int lx = x + 6, lw = w - 12, ly = y + 14;
            roundRect(ctx, lx, ly, lw, 2, 1, 0x33FFFFFF);
            RenderUtil.roundGradientH(ctx, lx, ly, Math.max(2, (int) (lw * sl.percent())), 2, 1, c1, c2);
            hit(lx, y, lw, SUB + 4, k -> {
                dragSlider = sl; sliderX = lx; sliderW = lw;
                sl.setFromPercent((clickX - lx) / lw);
                ConfigManager.markDirty();
            });
            return y + SUB + 6;
        }
        if (s instanceof Setting.Multi mu) {
            txt(ctx, mu.name, x + 6, y + 2, GRAY);
            String cnt = mu.selected.size() + "/" + mu.options.size();
            txt(ctx, cnt, x + w - 6 - tw(cnt), y + 2, WHITE);
            int cx = x + 6, cy = y + 13;
            for (String o : mu.options) {
                int cw = tw(o) + 12;
                if (cx + cw > x + w - 4) { cx = x + 6; cy += 16; }
                boolean on = mu.selected.contains(o);
                if (on) RenderUtil.roundGradientH(ctx, cx, cy, cw, 13, 4, c1, c2);
                else glass(ctx, cx, cy, cw, 13, 4);
                txt(ctx, o, cx + 6, cy + 3, on ? DARK : WHITE);
                hit(cx, cy, cw, 13, k -> { mu.toggle(o); ConfigManager.markDirty(); });
                cx += cw + 4;
            }
            return cy + 16 + 3;
        }
        if (s instanceof Setting.Mode md) {
            txt(ctx, md.name, x + 6, y + 2, GRAY);
            String val = md.get();
            txt(ctx, val, x + w - 6 - tw(val), y + 2, WHITE);
            int cx = x + 6, cy = y + 13;
            for (int i = 0; i < md.options.size(); i++) {
                String o = md.options.get(i);
                int cw = tw(o) + 12;
                if (cx + cw > x + w - 4) { cx = x + 6; cy += 16; }
                boolean on = md.index == i;
                int idx = i;
                if (on) RenderUtil.roundGradientH(ctx, cx, cy, cw, 13, 4, c1, c2);
                else glass(ctx, cx, cy, cw, 13, 4);
                txt(ctx, o, cx + 6, cy + 3, on ? DARK : WHITE);
                hit(cx, cy, cw, 13, k -> { md.index = idx; ConfigManager.markDirty(); });
                cx += cw + 4;
            }
            return cy + 16 + 3;
        }
        return y;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        clickX = click.x();
        double mx = click.x(), my = click.y();
        if (wpBoxW > 0 && inside((int) mx, (int) my, wpBoxX, wpBoxY, wpBoxW, wpBoxH)) {
            wpNameFocused = true; searchFocused = false; return true;
        }
        wpNameFocused = false;
        searchFocused = inside((int) mx, (int) my, searchBoxX, searchBoxY, searchBoxW, searchBoxH);
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.in(mx, my)) {
                h.action.accept(click.button());
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragSlider != null) {
            dragSlider.setFromPercent((click.x() - sliderX) / sliderW);
            ConfigManager.markDirty();
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragSlider != null) ConfigManager.markDirty();
        dragSlider = null;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        targetScroll = Math.max(0, targetScroll - v * 20);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int k = input.key();
        if (binding != null) {
            binding.key = (k == GLFW.GLFW_KEY_ESCAPE || k == GLFW.GLFW_KEY_DELETE) ? -1 : k;
            binding = null;
            ConfigManager.markDirty();
            return true;
        }
        if (client != null && (InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL)) && k == GLFW.GLFW_KEY_K) {
            searchFocused = true;
            wpNameFocused = false;
            return true;
        }
        if (wpNameFocused) {
            if (k == GLFW.GLFW_KEY_BACKSPACE && !wpNameInput.isEmpty()) {
                wpNameInput = wpNameInput.substring(0, wpNameInput.length() - 1);
                return true;
            }
            if (k == GLFW.GLFW_KEY_ENTER || k == GLFW.GLFW_KEY_KP_ENTER) {
                if (Waypoints.addHere(wpNameInput, wpColorIdx == 0 ? Theme.primary() : Waypoints.PALETTE[wpColorIdx])) {
                    wpNameInput = "";
                    wpNameFocused = false;
                }
                return true;
            }
            if (k == GLFW.GLFW_KEY_ESCAPE) { wpNameFocused = false; return true; }
        }
        if (k == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) {
            search = search.substring(0, search.length() - 1);
            targetScroll = scroll = 0;
            return true;
        }
        if (k == GLFW.GLFW_KEY_ESCAPE && !search.isEmpty()) {
            search = "";
            searchFocused = false;
            targetScroll = scroll = 0;
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        if (binding != null) return false;
        int cp = input.codepoint();
        if (!Character.isISOControl(cp)) {
            if (wpNameFocused) {
                if (wpNameInput.length() < 24) wpNameInput += Character.toString(cp);
                return true;
            }
            if (search.length() < 32) {
                searchFocused = true;
                search += Character.toString(cp);
                targetScroll = scroll = 0;
                return true;
            }
        }
        return super.charTyped(input);
    }

    @Override public void close() { ConfigManager.save(); Waypoints.saveNow(); super.close(); }
    @Override public void removed() { ConfigManager.save(); Waypoints.saveNow(); super.removed(); }
    @Override public boolean shouldPause() { return false; }

    private void hit(int x, int y, int w, int h, IntConsumer a) {
        int top = Math.max(y, clipTop), bottom = Math.min(y + h, clipBottom);
        if (bottom > top) hits.add(new Hit(x, top, w, bottom - top, a));
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    private void glass(DrawContext c, int x, int y, int w, int h, int r) { RenderUtil.glass(c, x, y, w, h, r, GLASS, BORDER); }

    private void drawSwitch(DrawContext c, int x, int y, float t) {
        int acc = ColorUtil.lerp(Theme.primary(), Theme.secondary(), t);
        roundRect(c, x - 1, y - 1, 22, 12, 6, ColorUtil.lerp(0x40FFFFFF, acc, t));
        roundRect(c, x, y, 20, 10, 5, ColorUtil.lerp(0xFF20283A, acc, t));
        roundRect(c, x + 1 + (int) (t * 10), y + 1, 8, 8, 4, ColorUtil.lerp(GRAY, WHITE, t));
    }

    private void drawChevron(DrawContext c, int x, int y, boolean open, int col) {
        if (open) {
            c.fill(x + 3, y, x + 4, y + 1, col);
            c.fill(x + 2, y + 1, x + 3, y + 2, col); c.fill(x + 4, y + 1, x + 5, y + 2, col);
            c.fill(x + 1, y + 2, x + 2, y + 3, col); c.fill(x + 5, y + 2, x + 6, y + 3, col);
            c.fill(x, y + 3, x + 1, y + 4, col);     c.fill(x + 6, y + 3, x + 7, y + 4, col);
        } else {
            c.fill(x, y, x + 1, y + 1, col);         c.fill(x + 6, y, x + 7, y + 1, col);
            c.fill(x + 1, y + 1, x + 2, y + 2, col); c.fill(x + 5, y + 1, x + 6, y + 2, col);
            c.fill(x + 2, y + 2, x + 3, y + 3, col); c.fill(x + 4, y + 2, x + 5, y + 3, col);
            c.fill(x + 3, y + 3, x + 4, y + 4, col);
        }
    }

    private void roundTexture(DrawContext c, Identifier id, int bgW, int bgH, int x, int y, int w, int h, int r) {
        float sa = (float) w / h, ta = (float) bgW / bgH;
        float rw = bgW, rh = bgH, u0 = 0, v0 = 0;
        if (ta > sa) { rw = bgH * sa; u0 = (bgW - rw) / 2f; } else { rh = bgW / sa; v0 = (bgH - rh) / 2f; }
        float kx = rw / w, ky = rh / h;
        blit(c, id, bgW, bgH, x, y + r, 0, r, w, h - 2 * r, u0, v0, kx, ky);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int dx = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            blit(c, id, bgW, bgH, x + dx, y + i, dx, i, w - 2 * dx, 1, u0, v0, kx, ky);
            blit(c, id, bgW, bgH, x + dx, y + h - i - 1, dx, h - i - 1, w - 2 * dx, 1, u0, v0, kx, ky);
        }
    }

    private void blit(DrawContext c, Identifier id, int bgW, int bgH, int sx, int sy, int lx, int ly, int w, int h,
                      float u0, float v0, float kx, float ky) {
        if (w <= 0 || h <= 0) return;
        c.drawTexture(RenderPipelines.GUI_TEXTURED, id, sx, sy, u0 + lx * kx, v0 + ly * ky, w, h,
                Math.max(1, Math.round(w * kx)), Math.max(1, Math.round(h * ky)), bgW, bgH);
    }

    public static void roundRect(DrawContext c, int x, int y, int w, int h, int r, int col) {
        RenderUtil.roundRect(c, x, y, w, h, r, col);
    }

    static int lerp(int a, int b, float t) { return ColorUtil.lerp(a, b, t); }
}
