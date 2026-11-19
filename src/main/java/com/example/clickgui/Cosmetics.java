package com.example.clickgui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Custom Cape & Wing Cosmetics (видно в 3-м лице):
 * Рисует клиентский 3D-плащ с физикой наклона от скорости или светящиеся анимированные крылья
 * в одном батче quad-вершин без аллокаций и без конфликтов со скинами/сервером.
 */
public final class Cosmetics {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static void render(VertexConsumer vc, float td, long now) {
        Module m = ModuleManager.get("Custom Cosmetics");
        if (!m.enabled || mc.player == null || mc.options.getPerspective().isFirstPerson()) return;

        PlayerEntity p = mc.player;
        String mode = ((Setting.Mode) m.find("Style")).get();
        double scale = m.num("Scale");

        Vec3d pos = p.getLerpedPos(td);
        double yawRad = Math.toRadians(p.bodyYaw + 180.0);
        Vec3d back = new Vec3d(-Math.sin(yawRad), 0, Math.cos(yawRad));
        Vec3d right = new Vec3d(Math.cos(yawRad), 0, Math.sin(yawRad));
        double shoulderY = pos.y + (p.isInSneakingPose() ? 1.15 : 1.38);

        double speed = Math.hypot(p.getVelocity().x, p.getVelocity().z);
        double sway = Math.min(0.85, speed * 2.2 + (p.isGliding() ? 0.45 : 0.08) + 0.04 * Math.sin(now / 300.0));

        if (mode.equals("Cape") || mode.equals("Both")) {
            double halfW = 0.28 * scale;
            double len = 0.92 * scale;
            Vec3d topCenter = pos.add(0, shoulderY - pos.y, 0).add(back.multiply(0.16));
            Vec3d botCenter = topCenter.add(back.multiply(0.14 + sway * 0.65)).add(0, -len * Math.cos(sway * 0.5), 0);

            int strips = 6;
            for (int i = 0; i < strips; i++) {
                double f1 = (double) i / strips - 0.5;
                double f2 = (double) (i + 1) / strips - 0.5;
                Vec3d t1 = topCenter.add(right.multiply(f1 * 2 * halfW));
                Vec3d t2 = topCenter.add(right.multiply(f2 * 2 * halfW));
                Vec3d b1 = botCenter.add(right.multiply(f1 * 2.2 * halfW));
                Vec3d b2 = botCenter.add(right.multiply(f2 * 2.2 * halfW));
                int cTop = Theme.color(now, (float) (i / (double) strips), 215);
                int cBot = Theme.color(now, (float) ((i + 1) / (double) strips), 150);
                Effects.quad(vc, t1, t2, b2, b1, cTop, cTop, cBot, cBot);
            }
        }

        if (mode.equals("Wings") || mode.equals("Both")) {
            Vec3d origin = pos.add(0, shoulderY - pos.y - 0.12, 0).add(back.multiply(0.18));
            double flap = 0.26 * Math.sin(now / (speed > 0.08 ? 160.0 : 480.0));
            for (int side = -1; side <= 1; side += 2) {
                for (int feather = 0; feather < 5; feather++) {
                    double frac = feather / 4.0;
                    double reach = (1.15 - frac * 0.35) * scale;
                    double rise = (0.45 - frac * 0.72) * scale + flap * (1.0 - frac * 0.4);
                    Vec3d tip = origin.add(right.multiply(side * reach))
                            .add(back.multiply(0.32 + frac * 0.12 + flap * 0.2))
                            .add(0, rise, 0);
                    Vec3d rootA = origin.add(0, 0.08 - frac * 0.25, 0);
                    Vec3d rootB = rootA.add(0, -0.14 * scale, 0);
                    Vec3d tipB = tip.add(0, -0.09 * scale, 0).add(right.multiply(-side * 0.08));
                    int ca = Theme.color(now, (float) frac * 0.6f, 220);
                    int cb = Theme.color(now, (float) frac * 0.6f + 0.25f, 95);
                    Effects.quad(vc, rootA, tip, tipB, rootB, ca, cb, cb & 0xFFFFFF, ca);
                }
            }
        }
    }
}
