package com.example.clickgui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

import java.util.*;
import java.util.regex.Pattern;

/** Scoreboard Customizer: тёмное стекло с закруглениями, скрытие IP-строк и красных цифр. */
public final class ScoreboardHud {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Pattern IP_PATTERN = Pattern.compile("(?i).*\\b([a-z0-9-]+\\.(ru|net|com|org|su|fun|gg|me|xyz|play))\\b.*");

    public static void render(DrawContext c, ScoreboardObjective obj) {
        if (obj == null || mc.player == null) return;
        Module m = ModuleManager.get("Scoreboard");
        Scoreboard sb = obj.getScoreboard();
        List<ScoreboardEntry> entries = new ArrayList<>(sb.getScoreboardEntries(obj));
        entries.removeIf(ScoreboardEntry::hidden);
        entries.sort(Comparator.comparingInt(ScoreboardEntry::value).reversed()
                .thenComparing(ScoreboardEntry::owner, String.CASE_INSENSITIVE_ORDER));
        if (entries.size() > 15) entries = entries.subList(0, 15);

        boolean hideIp = m.bool("Hide IP");
        boolean hideNums = m.bool("Hide Numbers");
        List<Text> lines = new ArrayList<>();
        List<String> nums = new ArrayList<>();
        for (ScoreboardEntry e : entries) {
            Team team = sb.getScoreHolderTeam(e.owner());
            Text formatted = Team.decorateName(team, e.name());
            String plain = formatted.getString();
            if (hideIp && IP_PATTERN.matcher(plain).matches()) continue;
            lines.add(formatted);
            nums.add(hideNums ? "" : String.valueOf(e.value()));
        }

        Text title = obj.getDisplayName();
        int maxW = mc.textRenderer.getWidth(title);
        for (int i = 0; i < lines.size(); i++) {
            int lw = mc.textRenderer.getWidth(lines.get(i)) + (nums.get(i).isEmpty() ? 0 : mc.textRenderer.getWidth(nums.get(i)) + 10);
            if (lw > maxW) maxW = lw;
        }
        int w = maxW + 14;
        int rowH = 10;
        int h = (lines.size() + 1) * rowH + 8;
        int sw = c.getScaledWindowWidth(), sh = c.getScaledWindowHeight();
        int x = sw - w - 6;
        int y = sh / 2 - h / 2;

        ClickGuiScreen.roundRect(c, x, y, w, h, 5, 0xA8101620);
        c.fill(x + 4, y, x + w - 4, y + 1, Theme.accent());
        int titleW = mc.textRenderer.getWidth(title);
        c.drawText(mc.textRenderer, title, x + (w - titleW) / 2, y + 4, 0xFFFFFFFF, false);

        for (int i = 0; i < lines.size(); i++) {
            int ry = y + 15 + i * rowH;
            c.drawText(mc.textRenderer, lines.get(i), x + 6, ry, 0xFFE2E8F0, false);
            if (!nums.get(i).isEmpty()) {
                String n = nums.get(i);
                c.drawText(mc.textRenderer, n, x + w - 6 - mc.textRenderer.getWidth(n), ry, 0xFFFB7185, false);
            }
        }
    }
}
