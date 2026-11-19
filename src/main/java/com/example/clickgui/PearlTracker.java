package com.example.clickgui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.*;

/**
 * Pearl & Projectile Tracker:
 * отслеживает летящие эндер-перлы, определяет владельца, время в полёте и точку приземления.
 */
public final class PearlTracker {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public record TrackedPearl(int id, Vec3d pos, Vec3d landing, String owner, double airSec) {}

    private static final Map<Integer, Long> spawnTimes = new HashMap<>();
    public static final List<TrackedPearl> ACTIVE = new ArrayList<>();

    public static void render(VertexConsumer vc, float td) {
        ACTIVE.clear();
        Module m = ModuleManager.get("Pearl Tracker");
        if (!m.enabled || mc.player == null || mc.world == null) {
            spawnTimes.clear();
            return;
        }

        long now = System.currentTimeMillis();
        Set<Integer> seen = new HashSet<>();

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof EnderPearlEntity pearl)) continue;
            seen.add(e.getId());
            long spawn = spawnTimes.computeIfAbsent(e.getId(), k -> now);
            double airSec = (now - spawn) / 1000.0;

            Vec3d p = pearl.getLerpedPos(td);
            Vec3d v = pearl.getVelocity();
            String owner = resolveOwner(pearl);

            List<Vec3d> arc = new ArrayList<>();
            arc.add(p);
            Vec3d cur = p;
            Vec3d vel = v;
            Vec3d landing = p;

            for (int tick = 0; tick < 80; tick++) {
                Vec3d next = cur.add(vel);
                HitResult hr = mc.world.raycast(new RaycastContext(
                        cur, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, pearl));
                if (hr.getType() != HitResult.Type.MISS) {
                    landing = hr.getPos();
                    arc.add(landing);
                    break;
                }
                arc.add(next);
                landing = next;
                cur = next;
                vel = vel.multiply(0.99).add(0, -0.03, 0);
                if (cur.y < mc.world.getBottomY() - 16) break;
            }

            if (m.bool("Trajectory") && arc.size() >= 2) {
                for (int i = 0; i < arc.size() - 1; i++) {
                    int c1 = Effects.color((float) i / arc.size(), 220);
                    int c2 = Effects.color((float) (i + 1) / arc.size(), 220);
                    Effects.ribbon(vc, arc.get(i), arc.get(i + 1), 0.022, c1, c2);
                }
                int ringCol = Effects.color(0.4f, 240);
                int seg = 20;
                for (int i = 0; i < seg; i++) {
                    double a1 = Math.PI * 2 * i / seg, a2 = Math.PI * 2 * (i + 1) / seg;
                    Vec3d r1 = landing.add(Math.cos(a1) * 0.42, 0.04, Math.sin(a1) * 0.42);
                    Vec3d r2 = landing.add(Math.cos(a2) * 0.42, 0.04, Math.sin(a2) * 0.42);
                    Effects.ribbon(vc, r1, r2, 0.025, ringCol, ringCol);
                }
                Effects.orb(vc, landing.add(0, 0.06, 0), 0.14, ringCol);
            }

            ACTIVE.add(new TrackedPearl(e.getId(), p, landing, owner, airSec));
        }

        spawnTimes.keySet().retainAll(seen);
    }

    private static String resolveOwner(ProjectileEntity proj) {
        Entity direct = proj.getOwner();
        if (direct != null) return direct.getName().getString();
        return "Pearl";
    }
}