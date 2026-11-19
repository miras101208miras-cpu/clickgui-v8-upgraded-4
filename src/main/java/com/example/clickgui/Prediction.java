package com.example.clickgui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Prediction: симулирует физику полёта снарядов (скорость, гравитация, сопротивление воздуха)
 * и рисует 3D-траекторию до столкновения с блоком или сущностью.
 */
public final class Prediction {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private record Params(double speed, double gravity, double drag) {}

    public static void render(VertexConsumer vc, float td) {
        Module m = ModuleManager.get("Prediction");
        if (!m.enabled || mc.player == null || mc.world == null) return;

        Params p = resolve(m);
        if (p == null) return;

        float yaw = mc.player.getYaw(td);
        float pitch = mc.player.getPitch(td);
        Vec3d eye = mc.player.getCameraPosVec(td);
        Vec3d dir = Vec3d.fromPolar(pitch, yaw).normalize();
        Vec3d right = dir.crossProduct(new Vec3d(0, 1, 0));
        if (right.lengthSquared() < 1e-6) right = new Vec3d(1, 0, 0);
        else right = right.normalize();

        Vec3d pos = eye.add(right.multiply(0.18)).add(0, -0.1, 0);
        Vec3d vel = dir.multiply(p.speed());

        List<Vec3d> points = new ArrayList<>();
        points.add(pos);

        Vec3d hitPos = null;
        boolean hitEntity = false;

        for (int tick = 0; tick < 120; tick++) {
            Vec3d next = pos.add(vel);
            HitResult blockHit = mc.world.raycast(new RaycastContext(
                    pos, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));

            Vec3d limit = blockHit.getType() != HitResult.Type.MISS ? blockHit.getPos() : next;
            EntityHitResult entHit = ProjectileUtil.raycast(
                    mc.player, pos, limit,
                    new Box(pos, limit).expand(0.35),
                    e -> !e.isSpectator() && e.canHit() && e != mc.player,
                    pos.squaredDistanceTo(limit)
            );

            if (entHit != null) {
                hitPos = entHit.getPos();
                hitEntity = true;
                points.add(hitPos);
                break;
            }
            if (blockHit.getType() != HitResult.Type.MISS) {
                hitPos = blockHit.getPos();
                points.add(hitPos);
                break;
            }

            points.add(next);
            pos = next;
            vel = vel.multiply(p.drag()).add(0, -p.gravity(), 0);
            if (pos.y < mc.world.getBottomY() - 16) break;
        }

        if (points.size() < 2) return;
        for (int i = 0; i < points.size() - 1; i++) {
            float f1 = (float) i / points.size();
            float f2 = (float) (i + 1) / points.size();
            int c1 = Effects.color(f1, 220);
            int c2 = Effects.color(f2, 220);
            Effects.ribbon(vc, points.get(i), points.get(i + 1), 0.022, c1, c2);
        }

        if (hitPos != null && m.bool("Landing")) {
            int col = hitEntity ? 0xFFFF5577 : Effects.color(0.35f, 240);
            double r = hitEntity ? 0.55 : 0.4;
            int seg = 24;
            for (int i = 0; i < seg; i++) {
                double a1 = Math.PI * 2 * i / seg;
                double a2 = Math.PI * 2 * (i + 1) / seg;
                Vec3d p1 = hitPos.add(Math.cos(a1) * r, 0.04, Math.sin(a1) * r);
                Vec3d p2 = hitPos.add(Math.cos(a2) * r, 0.04, Math.sin(a2) * r);
                Effects.ribbon(vc, p1, p2, 0.025, col, col);
            }
            Effects.orb(vc, hitPos.add(0, 0.06, 0), 0.14, col);
        }
    }

    private static Params resolve(Module m) {
        Params main = fromStack(m, mc.player.getMainHandStack(), true);
        if (main != null) return main;
        return fromStack(m, mc.player.getOffHandStack(), false);
    }

    private static Params fromStack(Module m, ItemStack st, boolean checkActive) {
        if (st.isEmpty()) return null;
        Item it = st.getItem();
        if (m.bool("Bow")) {
            if (it == Items.BOW) {
                if (!mc.player.isUsingItem() || !mc.player.getActiveItem().isOf(Items.BOW)) return null;
                float pull = BowItem.getPullProgress(mc.player.getItemUseTime());
                if (pull < 0.1f) return null;
                return new Params(pull * 3.0, 0.05, 0.99);
            }
            if (it == Items.CROSSBOW && CrossbowItem.isCharged(st)) {
                return new Params(3.15, 0.05, 0.99);
            }
            if (it == Items.TRIDENT) {
                if (checkActive && mc.player.isUsingItem() && mc.player.getActiveItem().isOf(Items.TRIDENT)) {
                    return new Params(2.5, 0.05, 0.99);
                }
                return new Params(2.5, 0.05, 0.99);
            }
        }
        if (m.bool("Pearls") && (it == Items.ENDER_PEARL || it == Items.SNOWBALL || it == Items.EGG)) {
            return new Params(1.5, 0.03, 0.99);
        }
        if (m.bool("Potions")) {
            if (it == Items.SPLASH_POTION || it == Items.LINGERING_POTION) {
                return new Params(0.5, 0.05, 0.99);
            }
            if (it == Items.EXPERIENCE_BOTTLE) {
                return new Params(0.7, 0.07, 0.99);
            }
        }
        return null;
    }
}
