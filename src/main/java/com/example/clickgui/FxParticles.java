package com.example.clickgui;

import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

import java.util.*;

/** Свои 2D-билборды (снежинки, сердца, звёзды) с гравитацией и разлётом. */
final class FxParticles {
    private static final String[] TYPES = {"Snow", "Hearts", "Stars"};
    private static final Identifier[] TEX = {tex("snow"), tex("heart"), tex("star")};
    private static Identifier tex(String n) { return Identifier.of("clickgui", "textures/particle/" + n + ".png"); }

    private static final class P {
        double x, y, z, vx, vy, vz;
        float age, life, size, spin, rot;
        int type;
        boolean world;
    }

    private static final List<P> list = new ArrayList<>();
    private static final Random rnd = new Random();
    private static long last;

    private static int pick(Module m) {
        if (m == null) return 2;
        List<Integer> sel = new ArrayList<>();
        for (int i = 0; i < TYPES.length; i++) if (m.has("Type", TYPES[i])) sel.add(i);
        return sel.isEmpty() ? 1 : sel.get(rnd.nextInt(sel.size()));
    }

    static void onHit(Entity e) {
        Module m = ModuleManager.get("Hit Particles");
        if (!m.enabled) return;
        burst(e.getEntityPos().add(0, e.getHeight() * 0.6, 0), (int) m.num("Count"), m, 3.0);
    }

    static void burst(Vec3d c, int n, Module typeSrc, double speed) {
        for (int i = 0; i < n; i++) {
            P p = new P();
            p.x = c.x; p.y = c.y; p.z = c.z;
            double th = rnd.nextDouble() * Math.PI * 2, ph = Math.acos(rnd.nextDouble() * 2 - 1);
            double s = speed * (0.4 + rnd.nextDouble() * 0.6);
            p.vx = Math.sin(ph) * Math.cos(th) * s;
            p.vy = Math.abs(Math.cos(ph)) * s * 0.8 + 1.0;
            p.vz = Math.sin(ph) * Math.sin(th) * s;
            p.life = 0.8f + rnd.nextFloat() * 0.6f;
            p.size = 0.12f + rnd.nextFloat() * 0.08f;
            p.spin = (rnd.nextFloat() - 0.5f) * 6;
            p.type = pick(typeSrc);
            list.add(p);
        }
    }

    static void render(VertexConsumerProvider vcp, Vec3d cam, Matrix4f mat, Quaternionfc q, long now) {
        float dt = last == 0 ? 0 : Math.min(0.05f, (now - last) / 1000f);
        last = now;
        Module wm = ModuleManager.get("World Particles");
        double r = wm.num("Radius");
        int worldCount = 0;

        Iterator<P> it = list.iterator();
        while (it.hasNext()) {
            P p = it.next();
            p.age += dt;
            double dx = p.x - cam.x, dz = p.z - cam.z;
            if (p.age >= p.life || (p.world && (!wm.enabled || dx * dx + dz * dz > r * r * 2.25))) { it.remove(); continue; }
            if (p.world) {
                p.vx += Math.sin(p.age * 1.3 + p.spin * 10) * 0.4 * dt; // покачивание
                worldCount++;
            } else {
                p.vy -= 9.0 * dt;
                double drag = Math.exp(-2.0 * dt);
                p.vx *= drag; p.vy *= drag; p.vz *= drag;
            }
            p.x += p.vx * dt; p.y += p.vy * dt; p.z += p.vz * dt;
            p.rot += p.spin * dt;
        }

        if (wm.enabled) {
            for (int i = worldCount; i < (int) wm.num("Count"); i++) {
                P p = new P();
                p.world = true;
                double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * r;
                p.x = cam.x + Math.cos(a) * d;
                p.z = cam.z + Math.sin(a) * d;
                p.y = cam.y + (rnd.nextDouble() * 1.5 - 0.3) * r * 0.5;
                p.vx = (rnd.nextDouble() - 0.5) * 0.3;
                p.vy = -0.3 - rnd.nextDouble() * 0.4;
                p.vz = (rnd.nextDouble() - 0.5) * 0.3;
                p.life = 6 + rnd.nextFloat() * 6;
                p.size = 0.08f + rnd.nextFloat() * 0.08f;
                p.spin = (rnd.nextFloat() - 0.5f) * 1.5f;
                p.type = pick(wm);
                list.add(p);
            }
        }
        if (list.isEmpty()) return;

        for (int t = 0; t < TEX.length; t++) {
            VertexConsumer vc = null;
            for (P p : list) {
                if (p.type != t) continue;
                if (vc == null) vc = vcp.getBuffer(RenderLayers.entityTranslucentEmissive(TEX[t]));
                float a = 1 - p.age / p.life;
                if (p.world) a = Math.min(a, p.age / 0.5f);
                billboard(vc, mat, cam, q, p, Effects.color(t * 0.3f, (int) (255 * a)));
            }
        }
    }

    private static final float[][] CORNERS = {{-1, -1}, {-1, 1}, {1, 1}, {1, -1}};
    private static final float[][] UV = {{1, 1}, {1, 0}, {0, 0}, {0, 1}};

    private static void billboard(VertexConsumer vc, Matrix4f mat, Vec3d cam, Quaternionfc q, P p, int col) {
        float cos = (float) Math.cos(p.rot), sin = (float) Math.sin(p.rot);
        float cx = (float) (p.x - cam.x), cy = (float) (p.y - cam.y), cz = (float) (p.z - cam.z);
        Vector3f[] v = new Vector3f[4];
        for (int i = 0; i < 4; i++) {
            float x = CORNERS[i][0], y = CORNERS[i][1];
            v[i] = new Vector3f(x * cos - y * sin, x * sin + y * cos, 0).rotate(q).mul(p.size).add(cx, cy, cz);
        }
        for (int i = 0; i < 4; i++) put(vc, mat, v[i], UV[i], col);
        for (int i = 3; i >= 0; i--) put(vc, mat, v[i], UV[i], col); // обратная сторона
    }

    private static void put(VertexConsumer vc, Matrix4f mat, Vector3f v, float[] uv, int col) {
        vc.vertex(mat, v.x, v.y, v.z).color(col).texture(uv[0], uv[1])
                .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0, 1, 0);
    }
}
