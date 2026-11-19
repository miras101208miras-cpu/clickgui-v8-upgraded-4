package com.example.clickgui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.client.util.math.MatrixStack;

/**
 * ЧАСТЬ 1 — First-Person и предметы:
 * - ViewModel (смещение X/Y/Z, масштаб, поворот для Mainhand и Offhand)
 * - Swing Animation (Smooth, Spin, Side Slash, Power + скорость)
 * - Eat / Drink Animation (плавное поднесение + кольцо прогресса на HUD)
 * - Low Shield (опускание щита при удержании ПКМ)
 * - Bow Pull Color (индикатор заряда лука/арбалета 0%..100%)
 * - Held Item Glow (мягкое свечение вокруг меча, тотема или булавы)
 */
public final class HandVisuals {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    /** Вызывается перед HeldItemRenderer.renderItem: применяет ViewModel и Low Shield без аллокаций. */
    public static void transformHeld(PlayerEntity player, Hand hand, ItemStack stack, MatrixStack matrices) {
        if (player == null || stack.isEmpty()) return;

        // Low Shield: опускает щит при блокировании
        Module shield = ModuleManager.get("Low Shield");
        if (shield.enabled && stack.isOf(Items.SHIELD) && player.isUsingItem() && player.getActiveHand() == hand) {
            matrices.translate(0.0f, (float) -shield.num("Drop Y"), 0.0f);
        }

        // ViewModel: X/Y/Z, Rotate, Scale для основной и второй руки
        Module vm = ModuleManager.get("ViewModel");
        if (vm.enabled) {
            boolean main = hand == Hand.MAIN_HAND;
            float side = (main ? 1f : -1f) * (player.getMainArm() == Arm.RIGHT ? 1f : -1f);
            float tx = (float) (main ? vm.num("Main X") : vm.num("Off X")) * side;
            float ty = (float) (main ? vm.num("Main Y") : vm.num("Off Y"));
            float tz = (float) (main ? vm.num("Main Z") : vm.num("Off Z"));
            matrices.translate(tx, ty, tz);

            float rot = (float) vm.num("Rotate");
            if (Math.abs(rot) > 0.1f) {
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rot * side));
            }
            float sc = (float) vm.num("Scale");
            if (Math.abs(sc - 1.0f) > 0.005f) {
                matrices.scale(sc, sc, sc);
            }
        }
    }

    /** Кастомная анимация удара. Возвращает true, если ванильный applySwingOffset нужно отменить. */
    public static boolean applySwing(MatrixStack matrices, Arm arm, float swingProgress) {
        Module m = ModuleManager.get("Swing Animation");
        if (!m.enabled) return false;
        float speed = (float) m.num("Speed");
        float p = MathHelper.clamp(swingProgress * speed, 0f, 1f);
        int side = arm == Arm.RIGHT ? 1 : -1;
        String style = ((Setting.Mode) m.find("Style")).get();

        switch (style) {
            case "Smooth" -> {
                float s = MathHelper.sin(p * (float) Math.PI);
                matrices.translate(side * -0.14f * s, -0.08f * s, -0.16f * s);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-42f * s));
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 22f * s));
            }
            case "Spin" -> {
                float s = MathHelper.sin(p * (float) Math.PI);
                matrices.translate(0f, 0.08f * s, -0.12f * s);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-360f * p));
            }
            case "Side Slash" -> {
                float s = MathHelper.sin(p * (float) Math.PI);
                matrices.translate(side * (-0.34f * p + 0.16f), -0.06f * s, -0.15f * s);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 65f * s));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * -55f * s));
            }
            case "Power" -> {
                float s = MathHelper.sin(MathHelper.sqrt(p) * (float) Math.PI);
                matrices.translate(side * -0.22f * s, 0.04f * s, -0.24f * s);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-75f * s));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * 30f * s));
            }
        }
        return true;
    }

    /** Плавная анимация еды и питья зелий. */
    public static boolean applyEatOrDrink(MatrixStack matrices, float tickProgress, Arm arm, ItemStack stack, PlayerEntity player) {
        Module m = ModuleManager.get("Eat Animation");
        if (!m.enabled) return false;
        int side = arm == Arm.RIGHT ? 1 : -1;
        float left = player.getItemUseTimeLeft() - tickProgress + 1.0f;
        int maxUse = stack.getMaxUseTime(player);
        float progress = maxUse > 0 ? MathHelper.clamp(1.0f - left / maxUse, 0f, 1f) : 0f;
        float bob = MathHelper.sin(left * 1.15f) * 0.025f;
        float intro = MathHelper.clamp(progress * 4.5f, 0f, 1f);

        matrices.translate(side * -0.16f * intro, 0.14f * intro + bob, -0.18f * intro);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-18f * intro));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(side * 26f * intro));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side * -14f * intro));
        return true;
    }

    /** 2D HUD-элементы первой части: кольцо еды, прогресс натяжения лука/арбалета и свечение активного предмета. */
    public static void renderHudOverlays(DrawContext c, int sw, int sh) {
        if (mc.player == null) return;
        int cx = sw / 2, cy = sh / 2;

        // 1. Eat / Drink Progress Ring вокруг прицела
        Module eat = ModuleManager.get("Eat Animation");
        if (eat.enabled && eat.bool("Progress Ring") && mc.player.isUsingItem()) {
            ItemStack active = mc.player.getActiveItem();
            UseAction act = active.getUseAction();
            if (act == UseAction.EAT || act == UseAction.DRINK) {
                int max = active.getMaxUseTime(mc.player);
                if (max > 0) {
                    float pct = MathHelper.clamp(1f - (float) mc.player.getItemUseTimeLeft() / max, 0f, 1f);
                    int bw = 38, x = cx - bw / 2, y = cy + 16;
                    ClickGuiScreen.roundRect(c, x - 2, y - 2, bw + 4, 7, 3, 0x99101620);
                    int fw = Math.round(bw * pct);
                    if (fw > 0) c.fill(x, y, x + fw, y + 3, Theme.accent());
                }
            }
        }

        // 2. Bow Pull Color (0%..100% от красного к изумрудному)
        Module bow = ModuleManager.get("Bow Pull Color");
        if (bow.enabled) {
            ItemStack active = mc.player.isUsingItem() ? mc.player.getActiveItem() : mc.player.getMainHandStack();
            float pull = -1f;
            if (active.isOf(Items.BOW) && mc.player.isUsingItem()) {
                int used = active.getMaxUseTime(mc.player) - mc.player.getItemUseTimeLeft();
                pull = BowItem.getPullProgress(used);
            } else if (active.isOf(Items.CROSSBOW)) {
                if (CrossbowItem.isCharged(active)) pull = 1f;
                else if (mc.player.isUsingItem()) {
                    int used = active.getMaxUseTime(mc.player) - mc.player.getItemUseTimeLeft();
                    int pullTime = CrossbowItem.getPullTime(active, mc.player);
                    pull = pullTime > 0 ? (float) used / (float) pullTime : 1f;
                }
            }
            if (pull >= 0f) {
                pull = MathHelper.clamp(pull, 0f, 1f);
                int col = pullColor(pull);
                int bw = 42, x = cx - bw / 2, y = cy - 16;
                ClickGuiScreen.roundRect(c, x - 2, y - 2, bw + 4, 7, 3, 0x99101620);
                int fw = Math.round(bw * pull);
                if (fw > 0) c.fill(x, y, x + fw, y + 3, col);
                String txt = Math.round(pull * 100) + "%";
                Hud.txt(c, txt, cx - Hud.tw(txt) / 2, y - 10, col);
            }
        }

        // 3. Held Item Glow (мягкое свечение в углу под рукой на экране для меча/тотема/булавы)
        Module glow = ModuleManager.get("Held Item Glow");
        if (glow.enabled && mc.options.getPerspective().isFirstPerson()) {
            ItemStack main = mc.player.getMainHandStack();
            ItemStack off = mc.player.getOffHandStack();
            if (isHighlightItem(main)) drawHandGlow(c, (int) (sw * 0.78f), (int) (sh * 0.80f), (int) glow.num("Radius"));
            if (isHighlightItem(off)) drawHandGlow(c, (int) (sw * 0.22f), (int) (sh * 0.80f), (int) (glow.num("Radius") * 0.75));
        }
    }

    private static boolean isHighlightItem(ItemStack st) {
        if (st.isEmpty()) return false;
        String id = st.getItem().toString();
        return st.isOf(Items.TOTEM_OF_UNDYING) || st.isOf(Items.MACE)
                || id.contains("sword") || id.contains("axe") || st.isOf(Items.ENDER_PEARL);
    }

    private static void drawHandGlow(DrawContext c, int cx, int cy, int r) {
        int base = Theme.accent() & 0xFFFFFF;
        for (int i = 4; i >= 1; i--) {
            int rad = r * i / 4;
            int a = (18 * (5 - i));
            c.fill(cx - rad, cy - rad, cx + rad, cy + rad, (a << 24) | base);
        }
    }

    public static int pullColor(float pull) {
        if (pull < 0.5f) {
            float t = pull * 2f;
            int g = (int) (80 + 175 * t);
            return 0xFF000000 | (255 << 16) | (g << 8) | 60;
        }
        float t = (pull - 0.5f) * 2f;
        int r = (int) (255 * (1f - t) + 57 * t);
        int g = (int) (255 * (1f - t) + 231 * t);
        int b = (int) (60 * (1f - t) + 95 * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}