package com.example.clickgui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** HUD editor: drag elements with LMB, RMB = reset position, ESC = save and back to menu. */
public class HudEditorScreen extends Screen {
    private static final int SNAP = 5;
    private String dragging;
    private double offX, offY;
    private int guideX = -1, guideY = -1;

    public HudEditorScreen() { super(Text.literal("HUD Editor")); }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        ctx.fill(0, 0, width, height, 0x55000000); // no blur, so you see the game
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta);
        int acc = Theme.accent();
        if (guideX >= 0) ctx.fill(guideX, 0, guideX + 1, height, acc);
        if (guideY >= 0) ctx.fill(0, guideY, width, guideY + 1, acc);

        if (client.player != null) Hud.renderAll(ctx);
        boolean any = false;
        for (String n : Hud.ELEMENTS) {
            if (!Hud.visible(n)) continue;
            any = true;
            int x = Hud.x(n), y = Hud.y(n), w = Hud.w(n), h = Hud.h(n);
            boolean on = n.equals(dragging) || inside(mx, my, n);
            int col = on ? acc : 0x80FFFFFF;
            outline(ctx, x - 2, y - 2, w + 4, h + 4, col);
            Hud.txt(ctx, n, x, y - 11 < 0 ? y + h + 4 : y - 11, col);
        }
        String hint = any ? "LMB - drag   RMB - reset   ESC - save" : "Enable HUD modules in the HUD tab first";
        Hud.txt(ctx, hint, (width - Hud.tw(hint)) / 2, height / 2 - 4, 0xCCFFFFFF);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        for (int i = Hud.ELEMENTS.length - 1; i >= 0; i--) {
            String n = Hud.ELEMENTS[i];
            if (!Hud.visible(n) || !inside(click.x(), click.y(), n)) continue;
            if (click.button() == 1) { Hud.reset(n); return true; }
            dragging = n;
            offX = click.x() - Hud.x(n);
            offY = click.y() - Hud.y(n);
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragging == null) return super.mouseDragged(click, dx, dy);
        int w = Hud.w(dragging), h = Hud.h(dragging);
        int x = (int) (click.x() - offX), y = (int) (click.y() - offY);
        guideX = guideY = -1;
        // snap to left / center / right and top / center / bottom
        if (Math.abs(x) < SNAP) { x = 0; guideX = 0; }
        else if (Math.abs(x + w / 2 - width / 2) < SNAP) { x = width / 2 - w / 2; guideX = width / 2; }
        else if (Math.abs(x + w - width) < SNAP) { x = width - w; guideX = width - 1; }
        if (Math.abs(y) < SNAP) { y = 0; guideY = 0; }
        else if (Math.abs(y + h / 2 - height / 2) < SNAP) { y = height / 2 - h / 2; guideY = height / 2; }
        else if (Math.abs(y + h - height) < SNAP) { y = height - h; guideY = height - 1; }
        Hud.setPos(dragging, x, y);
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = null;
        guideX = guideY = -1;
        return super.mouseReleased(click);
    }

    @Override
    public void close() { Hud.save(); client.setScreen(new ClickGuiScreen()); }

    @Override
    public void removed() { Hud.save(); }

    @Override public boolean shouldPause() { return false; }

    private static boolean inside(double mx, double my, String n) {
        int x = Hud.x(n), y = Hud.y(n);
        return mx >= x - 2 && mx < x + Hud.w(n) + 2 && my >= y - 2 && my < y + Hud.h(n) + 2;
    }

    private static void outline(DrawContext c, int x, int y, int w, int h, int col) {
        c.fill(x, y, x + w, y + 1, col);
        c.fill(x, y + h - 1, x + w, y + h, col);
        c.fill(x, y, x + 1, y + h, col);
        c.fill(x + w - 1, y, x + w, y + h, col);
    }
}
