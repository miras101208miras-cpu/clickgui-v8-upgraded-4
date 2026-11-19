package com.example.clickgui;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;

import java.util.List;

/** Предпросмотр всех 27 слотов шалкера при наведении мыши в инвентаре или сундуке. */
public final class ShulkerPreview {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static boolean tryRender(DrawContext c, ItemStack st, int mx, int my) {
        if (!ModuleManager.get("Shulker Preview").enabled || st == null || st.isEmpty()) return false;
        if (!(st.getItem() instanceof BlockItem bi) || !(bi.getBlock() instanceof ShulkerBoxBlock)) return false;

        ContainerComponent comp = st.get(DataComponentTypes.CONTAINER);
        List<ItemStack> stacks = comp != null ? comp.stream().toList() : List.of();

        int cols = 9, rows = 3, slot = 18;
        int w = cols * slot + 8, h = rows * slot + 20;
        int sw = c.getScaledWindowWidth(), sh = c.getScaledWindowHeight();
        int x = Math.min(sw - w - 4, mx + 12);
        int y = Math.max(4, my - h - 8);
        if (y + h > sh - 4) y = Math.max(4, sh - h - 4);

        c.getMatrices().pushMatrix();
        try {
        c.getMatrices().translate(0f, 0f);
        ClickGuiScreen.roundRect(c, x, y, w, h, 5, 0xE0101620);
        c.fill(x + 4, y, x + w - 4, y + 1, Theme.accent());
        Hud.txt(c, st.getName().getString(), x + 5, y + 4, 0xFFFFFFFF);

        for (int r = 0; r < rows; r++) {
            for (int col = 0; col < cols; col++) {
                int idx = r * cols + col;
                int sx = x + 4 + col * slot;
                int sy = y + 16 + r * slot;
                c.fill(sx, sy, sx + slot - 1, sy + slot - 1, 0x661E293B);
                if (idx < stacks.size()) {
                    ItemStack inner = stacks.get(idx);
                    if (!inner.isEmpty()) {
                        c.drawItem(inner, sx + 1, sy + 1);
                        c.drawStackOverlay(mc.textRenderer, inner, sx + 1, sy + 1);
                    }
                }
            }
        }
        } finally {
            c.getMatrices().popMatrix();
        }
        return true;
    }
}
