package com.example.clickgui;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.awt.Color;
import java.util.*;

/** Визуальные эффекты. Рисуются с проверкой глубины: сквозь стены не видны. */
public final class Effects {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private record Circle(Vec3d pos, long start) {}
    private record TrailPoint(Vec3d pos, long time) {}

    private static final List<Circle> circles = new ArrayList<>();
    private static final ArrayDeque<TrailPoint> trail = new ArrayDeque<>();
    private static LivingEntity target;
    private static long targetTime;
    private static boolean wasOnGround = true;
    private static Vec3d cam = Vec3d.ZERO;
    /** Поворот камеры последнего кадра (для неймтегов на HUD). */
    static final Quaternionf camRot = new Quaternionf();
    static boolean camReady;
    static Vec3d camPos() { return cam; }
    static LivingEntity lastTarget() { return target; }
    private static Matrix4f mat;
    private static long now;
    private static float fade = 1f; // плавное затухание Target ESP
    private static final Map<LivingEntity, Long> attacked = new WeakHashMap<>();
    private static boolean fbApplied;
    private static int boltId = -1_000_000;

    /** Целью может быть только видимое существо: игроков в инвизе не подсвечиваем. */
    private static boolean canTarget(LivingEntity le) {
        return le.isAlive() && !le.isRemoved() && !le.isInvisible() && !le.hasStatusEffect(StatusEffects.INVISIBILITY);
    }

    public static void init() {
        // цель = существо, которое ты ударил или на которое смотришь
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (entity instanceof LivingEntity le) {
                if (canTarget(le)) { if (target != le) Notifications.onTargetAcquire(le); target = le; targetTime = System.currentTimeMillis(); }
                attacked.put(le, System.currentTimeMillis());
                FxParticles.onHit(le);
                ServerMetrics.onAttack(le);
                ExtraVisuals.onAttack(le);
                if (entity == FakePlayer.entity()) {
                    FakePlayer.onLocalHit();
                    return ActionResult.FAIL;
                }
            }
            return ActionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(c -> tick());
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(Effects::render);
    }

    private static void tick() {
        ClientPlayerEntity p = mc.player;
        if (p == null) { circles.clear(); trail.clear(); target = null; return; }
        long t = System.currentTimeMillis();

        boolean ground = p.isOnGround();
        if (ModuleManager.get("Jump Circles").enabled && wasOnGround && !ground && p.getVelocity().y > 0)
            circles.add(new Circle(p.getEntityPos(), t));
        wasOnGround = ground;

        Module tr = ModuleManager.get("Trails");
        if (tr.enabled) {
            Vec3d pos = p.getEntityPos();
            if (!trail.isEmpty() && trail.peekLast().pos().distanceTo(pos) > 4) trail.clear();
            trail.addLast(new TrailPoint(pos, t));
            long life = (long) (tr.num("Length") * 1000);
            while (!trail.isEmpty() && t - trail.peekFirst().time() > life) trail.pollFirst();
        } else trail.clear();

        if (mc.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le && canTarget(le)) {
            target = le; targetTime = t;
        }
        if (target != null && (!canTarget(target) || t - targetTime > 3000)) { Notifications.onTargetLost(target); target = null; }

        HitColor.update();
        EnchantGlint.tick();
        ServerMetrics.tick();

        // ---- Full Bright: ночное зрение без иконки, только на клиенте ----
        if (ModuleManager.get("Full Bright").enabled) {
            StatusEffectInstance cur = p.getStatusEffect(StatusEffects.NIGHT_VISION);
            if (cur == null || !cur.isInfinite()) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, StatusEffectInstance.INFINITE, 0, false, false, false));
                fbApplied = true;
            }
        } else if (fbApplied) {
            p.removeStatusEffect(StatusEffects.NIGHT_VISION);
            fbApplied = false;
        }

        // ---- Kill Effect: смерть существа, которое ты бил последние 5 сек ----
        Iterator<Map.Entry<LivingEntity, Long>> it = attacked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Long> en = it.next();
            LivingEntity le = en.getKey();
            if (le.isDead() || le.deathTime > 0) { ServerMetrics.onKill(le); killEffect(le); it.remove(); }
            else if (le.isRemoved() || t - en.getValue() > 5000) it.remove();
        }
    }

    private static void killEffect(LivingEntity le) {
        Module m = ModuleManager.get("Kill Effect");
        if (!m.enabled || mc.world == null) return;
        Vec3d pos = le.getEntityPos();
        if (m.has("Effect", "Lightning")) {
            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(mc.world, SpawnReason.TRIGGERED);
            if (bolt != null) {
                bolt.setId(boltId--); // отрицательный id, чтобы не пересечься с серверными
                bolt.refreshPositionAfterTeleport(pos);
                bolt.setCosmetic(true);
                mc.world.addEntity(bolt);
            }
        }
        if (m.has("Effect", "Particles")) {
            FxParticles.burst(pos.add(0, le.getHeight() * 0.5, 0), 40, null, 5.0);
            mc.particleManager.addParticle(ParticleTypes.EXPLOSION, pos.x, pos.y + 1, pos.z, 0, 0, 0);
        }
    }

    private static void safe(Runnable r) {
        try { r.run(); } catch (Throwable ignored) {}
    }

    private static void render(WorldRenderContext ctx) {
        if (mc.player == null || mc.world == null || ctx == null) return;
        VertexConsumerProvider vcp = ctx.consumers();
        if (vcp == null || ctx.matrices() == null || ctx.worldState() == null || ctx.worldState().cameraRenderState == null) return;
        now = System.currentTimeMillis();
        float td = mc.getRenderTickCounter().getTickProgress(true);
        cam = ctx.worldState().cameraRenderState.pos;
        camRot.set(ctx.worldState().cameraRenderState.orientation);
        camReady = true;
        mat = ctx.matrices().peek().getPositionMatrix();

        // PASS 1: строго один RenderLayers.lightning() буфер (без переключения vcp.getBuffer внутри!)
        VertexConsumer glow = vcp.getBuffer(RenderLayers.lightning());
        Module esp = ModuleManager.get("Target ESP");
        if (esp.enabled && target != null) {
            fade = (float) Math.max(0, Math.min(1, (3000 - (now - targetTime)) / 400.0));
            safe(() -> drawTarget(glow, esp, target, td));
        }
        if (ModuleManager.get("Trails").enabled) safe(() -> drawTrail(glow, td));
        safe(() -> Prediction.render(glow, td));
        safe(() -> PvpSave.render(glow));
        safe(() -> WorldVisuals.renderGlow(glow, td));
        safe(() -> PearlTracker.render(glow, td));
        safe(() -> Waypoints.render(glow, mat, cam.x, cam.y, cam.z));
        safe(() -> ExtraVisuals.render3D(glow, mat, cam.x, cam.y, cam.z, td));
        safe(() -> drawCircles(glow));

        // PASS 2: строго один RenderLayers.debugQuads() буфер
        VertexConsumer dq = vcp.getBuffer(RenderLayers.debugQuads());
        if (esp.enabled && target != null && esp.has("Style", "Health")) {
            safe(() -> drawHealth(dq, target, td));
        }
        safe(() -> WorldVisuals.renderFill(dq));
        safe(() -> Cosmetics.render(dq, td, now));
        Module hat = ModuleManager.get("China Hat");
        if (hat.enabled && !mc.options.getPerspective().isFirstPerson()) {
            safe(() -> drawHat(dq, hat, mc.player, td));
        }

        // PASS 3: текстурные партиклы (каждая текстура открывается последовательно)
        safe(() -> FxParticles.render(vcp, cam, mat, ctx.worldState().cameraRenderState.orientation, now));
    }

    // ---------------- Target ESP ----------------
    private static int al(int a) { return (int) (a * fade); }

    private static void drawTarget(VertexConsumer vc, Module m, LivingEntity e, float td) {
        Vec3d c = e.getLerpedPos(td);
        double h = e.getHeight(), r = (e.getWidth() * 0.75 + 0.15) * m.num("Size");
        double t = now / 1000.0 * m.num("Speed");
        int seg = 48;
        if (m.has("Style", "Ring")) {
            double y = c.y + (Math.sin(t * 2.5) + 1) / 2 * h;
            double tail = -Math.cos(t * 2.5) * 0.35; // шлейф тянется за движением кольца
            for (int i = 0; i < seg; i++) {
                double a1 = Math.PI * 2 * i / seg, a2 = Math.PI * 2 * (i + 1) / seg;
                Vec3d p1 = new Vec3d(c.x + Math.cos(a1) * r, y, c.z + Math.sin(a1) * r);
                Vec3d p2 = new Vec3d(c.x + Math.cos(a2) * r, y, c.z + Math.sin(a2) * r);
                int c1 = color(off(a1), al(220)), c2 = color(off(a2), al(220));
                quad(vc, p1, p2, p2.add(0, tail, 0), p1.add(0, tail, 0), c1, c2, c2 & 0xFFFFFF, c1 & 0xFFFFFF);
            }
        }
        if (m.has("Style", "Orbit")) {
            for (int k = 0; k < 3; k++) {
                double base = t * 3 + k * Math.PI * 2 / 3;
                double y = c.y + h * (0.5 + 0.3 * Math.sin(t * 2 + k * 2));
                int steps = 16;
                for (int i = 0; i < steps; i++) {
                    double a1 = base - i * 0.12, a2 = base - (i + 1) * 0.12;
                    double f1 = 1 - (double) i / steps, f2 = 1 - (double) (i + 1) / steps;
                    double w1 = 0.06 * f1 + 0.01, w2 = 0.06 * f2 + 0.01;
                    Vec3d p1 = new Vec3d(c.x + Math.cos(a1) * r, y, c.z + Math.sin(a1) * r);
                    Vec3d p2 = new Vec3d(c.x + Math.cos(a2) * r, y, c.z + Math.sin(a2) * r);
                    int c1 = color(k / 3f, al((int) (230 * f1))), c2 = color(k / 3f, al((int) (230 * f2)));
                    quad(vc, p1.add(0, w1, 0), p2.add(0, w2, 0), p2.add(0, -w2, 0), p1.add(0, -w1, 0), c1, c2, c2, c1);
                }
            }
        }
        if (m.has("Style", "Glow"))     drawGlow(vc, e, td, t);
        if (m.has("Style", "Skeleton")) drawSkeleton(vc, e, td, t);
        if (m.has("Style", "Halo"))      drawHalo(vc, c, h, r, t);
        if (m.has("Style", "Lock-On"))   drawLockOn(vc, e, c, h, t);
        if (m.has("Style", "Circle"))    drawGroundCircle(vc, c, r, t);
        if (m.has("Style", "Frost"))     drawFrost(vc, c, h, r, t);
        if (m.has("Style", "Souls")) drawSouls(vc, c, h, r, t);
        if (m.has("Style", "Pulse")) drawPulse(vc, c, h, r, t);
        if (m.has("Style", "DNA Spiral")) drawDna(vc, c, h, r, t);
        if (m.has("Style", "Pentagram")) drawPentagram(vc, c, r, t);
        if (m.has("Style", "Prism")) drawPrism(vc, c, h, r, t);
        if (m.has("Style", "Vortex")) drawVortex(vc, c, h, r, t);
        if (m.has("Style", "Box 3D")) drawBox3d(vc, e, td);
    }


    /** DNA Spiral: двойная закрученная спираль вокруг цели. */
    private static void drawDna(VertexConsumer vc, Vec3d c, double h, double r, double t) {
        int steps = 42;
        for (int s = 0; s < 2; s++) {
            double shift = s * Math.PI;
            Vec3d prev = null;
            for (int i = 0; i <= steps; i++) {
                double f = (double) i / steps;
                double a = t * 2.5 + f * Math.PI * 4 + shift;
                Vec3d p = new Vec3d(c.x + Math.cos(a) * r, c.y + f * h, c.z + Math.sin(a) * r);
                if (prev != null) ribbon(vc, prev, p, 0.025, color((float) f, al(230)), color((float) f, al(230)));
                if (i % 6 == 0 && s == 0) {
                    Vec3d opp = new Vec3d(c.x - Math.cos(a) * r, c.y + f * h, c.z - Math.sin(a) * r);
                    ribbon(vc, p, opp, 0.014, color((float) f, al(130)), color((float) f + 0.5f, al(130)));
                }
                prev = p;
            }
        }
    }

    /** Pentagram: вращающаяся 5-конечная звезда на земле с ореолом вершин. */
    private static void drawPentagram(VertexConsumer vc, Vec3d c, double r, double t) {
        double y = c.y + 0.03, rr = r * 1.25;
        Vec3d[] pts = new Vec3d[5];
        for (int i = 0; i < 5; i++) {
            double a = t * 0.8 + i * Math.PI * 2 / 5;
            pts[i] = new Vec3d(c.x + Math.cos(a) * rr, y, c.z + Math.sin(a) * rr);
            orb(vc, pts[i], 0.09, color(i / 5f, al(240)));
        }
        int[] order = {0, 2, 4, 1, 3, 0};
        for (int i = 0; i < 5; i++) {
            ribbon(vc, pts[order[i]], pts[order[i + 1]], 0.026, color(i / 5f, al(230)), color((i + 1) / 5f, al(230)));
        }
        for (int i = 0; i < 40; i++) {
            double a1 = Math.PI * 2 * i / 40, a2 = Math.PI * 2 * (i + 1) / 40;
            ring(vc, c, y, 0, a1, a2, rr - 0.02, rr + 0.02, color(off(a1), al(200)), color(off(a1), al(200)), color(off(a2), al(200)), color(off(a2), al(200)));
        }
    }

    /** Prism: вращающийся светящийся октаэдр (кристалл) вокруг цели. */
    private static void drawPrism(VertexConsumer vc, Vec3d c, double h, double r, double t) {
        Vec3d top = c.add(0, h + 0.25, 0);
        Vec3d bot = c.add(0, -0.05, 0);
        Vec3d[] eq = new Vec3d[4];
        double midY = c.y + h * 0.52;
        double rr = r * 1.15;
        for (int i = 0; i < 4; i++) {
            double a = t * 1.4 + i * Math.PI / 2;
            eq[i] = new Vec3d(c.x + Math.cos(a) * rr, midY, c.z + Math.sin(a) * rr);
        }
        for (int i = 0; i < 4; i++) {
            Vec3d a = eq[i], b = eq[(i + 1) % 4];
            int c1 = color(i / 4f, al(230)), c2 = color((i + 1) / 4f, al(230));
            ribbon(vc, top, a, 0.022, color(0.1f, al(250)), c1);
            ribbon(vc, bot, a, 0.022, color(0.7f, al(250)), c1);
            ribbon(vc, a, b, 0.024, c1, c2);
        }
    }

    /** Vortex: поднимающийся вихрь-воронка из 3 лент вокруг цели. */
    private static void drawVortex(VertexConsumer vc, Vec3d c, double h, double r, double t) {
        int steps = 28;
        for (int k = 0; k < 3; k++) {
            double base = t * 3.0 + k * Math.PI * 2 / 3;
            Vec3d prev = null;
            for (int i = 0; i <= steps; i++) {
                double f = (double) i / steps;
                double rr = r * (0.45 + 0.9 * f);
                double a = base + f * Math.PI * 2.5;
                Vec3d p = new Vec3d(c.x + Math.cos(a) * rr, c.y + f * (h + 0.25), c.z + Math.sin(a) * rr);
                if (prev != null) {
                    ribbon(vc, prev, p, 0.03 * (1.1 - f * 0.5), color(k / 3f, al((int) (220 * f))), color(k / 3f, al((int) (220 * f))));
                }
                prev = p;
            }
        }
    }

    /** Box 3D: 3D-рамка хитбокса в цвете темы вокруг цели. */
    private static void drawBox3d(VertexConsumer vc, LivingEntity e, float td) {
        Vec3d delta = e.getLerpedPos(td).subtract(e.getEntityPos());
        WorldVisuals.wireBox(vc, e.getBoundingBox().offset(delta).expand(0.04), 0.02, color(0.35f, al(235)));
    }

    // ---- скелет цели (общий для Glow и Skeleton) ----
    // 0 таз, 1 шея, 2 голова, 3-4 левая рука, 5-6 правая рука, 7-8 левая нога, 9-10 правая нога
    private static final int[][] BONES = {{0, 1}, {1, 2}, {3, 5}, {3, 4}, {5, 6}, {7, 9}, {7, 8}, {9, 10}};

    private static Vec3d[] joints(LivingEntity e, float td) {
        Vec3d c = e.getLerpedPos(td);
        double s = e.getHeight() / 1.8;
        double yaw = Math.toRadians(e.getBodyYaw());
        Vec3d fwd = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw)), right = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
        double limb = e.limbAnimator.getAnimationProgress(td), amp = Math.min(1, e.limbAnimator.getAmplitude(td));
        double sn = e.isInSneakingPose() ? 0.25 : 0;
        double arm = Math.cos(limb * 0.6662) * amp * 0.7, leg = Math.cos(limb * 0.6662) * amp * 0.6;
        Vec3d[] j = new Vec3d[11];
        j[0] = at(c, fwd, right, s, 0, 0.75 - sn * 0.4, 0);
        j[1] = at(c, fwd, right, s, 0, 1.40 - sn, sn * 0.5);
        j[2] = j[1].add(0, 0.25 * s, 0).add(Vec3d.fromPolar(e.getPitch(td), e.getHeadYaw()).multiply(0.06 * s));
        j[3] = at(c, fwd, right, s, 0.36, 1.35 - sn, sn * 0.5);
        j[5] = at(c, fwd, right, s, -0.36, 1.35 - sn, sn * 0.5);
        j[4] = j[3].add(limbVec(fwd, s, 0.65, arm));
        j[6] = j[5].add(limbVec(fwd, s, 0.65, -arm));
        j[7] = at(c, fwd, right, s, 0.12, 0.75 - sn * 0.4, 0);
        j[9] = at(c, fwd, right, s, -0.12, 0.75 - sn * 0.4, 0);
        j[8] = j[7].add(limbVec(fwd, s, 0.75 - sn * 0.4, -leg));
        j[10] = j[9].add(limbVec(fwd, s, 0.75 - sn * 0.4, leg));
        return j;
    }

    private static Vec3d at(Vec3d c, Vec3d fwd, Vec3d right, double s, double x, double y, double z) {
        return c.add(right.multiply(x * s)).add(0, y * s, 0).add(fwd.multiply(z * s));
    }

    private static Vec3d limbVec(Vec3d fwd, double s, double len, double ang) {
        return fwd.multiply(Math.sin(ang) * len * s).add(0, -Math.cos(ang) * len * s, 0);
    }

    /** Glow: мягкое пульсирующее свечение по всей фигуре цели. С проверкой глубины. */
    private static void drawGlow(VertexConsumer vc, LivingEntity e, float td, double t) {
        Vec3d[] j = joints(e, td);
        double s = e.getHeight() / 1.8;
        int a = al((int) (55 + 25 * Math.sin(t * 3)));
        for (int i = 0; i < BONES.length; i++) {
            Vec3d p1 = j[BONES[i][0]], p2 = j[BONES[i][1]];
            double rad = (i == 0 ? 0.32 : 0.17) * s;
            int n = Math.max(2, (int) (p1.distanceTo(p2) / (0.12 * s)));
            for (int k = 0; k <= n; k++) {
                double f = (double) k / n;
                orb(vc, p1.lerp(p2, f), rad, color((float) (i / (double) BONES.length + f * 0.2), a));
            }
        }
        orb(vc, j[2], 0.36 * s, color(0.1f, a));
    }

    /** Skeleton: светящийся скелет цели, двигается вместе с её ходьбой и приседанием. */
    private static void drawSkeleton(VertexConsumer vc, LivingEntity e, float td, double t) {
        Vec3d[] j = joints(e, td);
        double s = e.getHeight() / 1.8;
        for (int i = 0; i < BONES.length; i++) {
            int col = color(i / (float) BONES.length, al(235));
            ribbon(vc, j[BONES[i][0]], j[BONES[i][1]], 0.028 * s, col, col);
        }
        for (Vec3d p : j) orb(vc, p, 0.07 * s, color(0.5f, al(255)));
        circle(vc, j[2], 0.2 * s, 0.022 * s, color(0.2f, al(235))); // голова
    }

    /** Кольцо, повёрнутое к камере. */
    private static void circle(VertexConsumer vc, Vec3d p, double rad, double w, int col) {
        Vec3d view = p.subtract(cam).normalize();
        Vec3d right = view.crossProduct(new Vec3d(0, 1, 0));
        right = right.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : right.normalize();
        Vec3d up = right.crossProduct(view).normalize();
        int n = 24;
        for (int i = 0; i < n; i++) {
            double a1 = Math.PI * 2 * i / n, a2 = Math.PI * 2 * (i + 1) / n;
            Vec3d d1 = right.multiply(Math.cos(a1)).add(up.multiply(Math.sin(a1)));
            Vec3d d2 = right.multiply(Math.cos(a2)).add(up.multiply(Math.sin(a2)));
            quad(vc, p.add(d1.multiply(rad - w)), p.add(d1.multiply(rad + w)), p.add(d2.multiply(rad + w)), p.add(d2.multiply(rad - w)), col, col, col, col);
        }
    }

    /** Halo: светящийся нимб над головой, мягко покачивается. */
    private static void drawHalo(VertexConsumer vc, Vec3d c, double h, double r, double t) {
        double top = c.y + h + 0.3 + Math.sin(t * 2) * 0.05, tilt = Math.sin(t * 1.3) * 0.06;
        double hr = r * 0.55, w = 0.035;
        int seg = 48;
        for (int i = 0; i < seg; i++) {
            double a1 = Math.PI * 2 * i / seg, a2 = Math.PI * 2 * (i + 1) / seg;
            int c1 = color(off(a1 + t), al(240)), c2 = color(off(a2 + t), al(240));
            int t1 = c1 & 0xFFFFFF, t2 = c2 & 0xFFFFFF;
            ring(vc, c, top, tilt, a1, a2, hr - w, hr + w, c1, c1, c2, c2);          // яркое кольцо
            ring(vc, c, top, tilt, a1, a2, hr + w, hr + w * 5, c1, t1, t2, c2);      // ореол снаружи
            ring(vc, c, top, tilt, a1, a2, hr - w * 5, hr - w, t1, c1, c2, t2);      // ореол внутри
        }
    }

    /** Lock-On: угловые скобки прицела вокруг цели + пульсирующие ромбы по бокам. */
    private static void drawLockOn(VertexConsumer vc, LivingEntity e, Vec3d c, double h, double t) {
        Vec3d mid = c.add(0, h / 2, 0);
        Vec3d view = mid.subtract(cam).normalize();
        Vec3d right = view.crossProduct(new Vec3d(0, 1, 0));
        right = right.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : right.normalize();
        Vec3d up = right.crossProduct(view).normalize();
        double k = 1 + 0.06 * Math.sin(t * 4);
        double hw = (e.getWidth() * 0.5 + 0.35) * k, hh = (h * 0.5 + 0.2) * k, len = Math.min(hw, hh) * 0.45, w = 0.022;
        int col = color(0.5f, al(240));
        for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2) {
            Vec3d corner = mid.add(right.multiply(sx * hw)).add(up.multiply(sy * hh));
            ribbon(vc, corner, corner.subtract(right.multiply(sx * len)), w, col, col);
            ribbon(vc, corner, corner.subtract(up.multiply(sy * len)), w, col, col);
        }
        double ds = 0.07 + 0.015 * Math.sin(t * 6);
        int dc = color(0.2f, al(255));
        for (int sx = -1; sx <= 1; sx += 2) {
            Vec3d p = mid.add(right.multiply(sx * (hw + 0.14)));
            quad(vc, p.add(up.multiply(ds)), p.add(right.multiply(ds)), p.subtract(up.multiply(ds)), p.subtract(right.multiply(ds)), dc, dc, dc, dc);
        }
    }

    /** Circle: светящееся кольцо на земле под целью с мягкой заливкой и бегущими дугами. */
    private static void drawGroundCircle(VertexConsumer vc, Vec3d c, double r, double t) {
        double y = c.y + 0.02, rr = r * 1.2, w = 0.035;
        int seg = 64;
        for (int i = 0; i < seg; i++) {
            double a1 = Math.PI * 2 * i / seg, a2 = Math.PI * 2 * (i + 1) / seg;
            int c1 = color(off(a1), al(230)), c2 = color(off(a2), al(230));
            int f1 = (al(60) << 24) | (c1 & 0xFFFFFF), f2 = (al(60) << 24) | (c2 & 0xFFFFFF);
            ring(vc, c, y, 0, a1, a2, rr - w, rr + w, c1, c1, c2, c2);
            ring(vc, c, y, 0, a1, a2, rr * 0.5, rr - w, c1 & 0xFFFFFF, f1, f2, c2 & 0xFFFFFF);
        }
        for (int k = 0; k < 3; k++) {
            double base = t * 1.5 + k * Math.PI * 2 / 3;
            int col = color(k / 3f, al(255));
            for (int i = 0; i < 10; i++) {
                double a1 = base + i * 0.06, a2 = base + (i + 1) * 0.06;
                ring(vc, c, y + 0.005, 0, a1, a2, rr + w * 2, rr + w * 3.5, col, col, col, col);
            }
        }
    }

    // ---------------- Frost ----------------
    private static final int ICE = 0xA8E6FF, ICE_W = 0xEAF8FF;

    /** Frost: ледяные кристаллы кружатся вокруг цели, падает снег, иней на земле. */
    private static void drawFrost(VertexConsumer vc, Vec3d c, double h, double r, double t) {
        for (int k = 0; k < 6; k++) {
            double a = t * 0.8 + k * Math.PI / 3;
            double y = c.y + h * (0.3 + 0.25 * (k % 3)) + Math.sin(t * 2 + k) * 0.08;
            Vec3d p = new Vec3d(c.x + Math.cos(a) * r * 1.15, y, c.z + Math.sin(a) * r * 1.15);
            crystal(vc, p, 0.2 + 0.06 * (k % 2), 0.055, t * 2 + k);
        }
        int n = 28;
        for (int i = 0; i < n; i++) {
            double s1 = hash(i * 7 + 1), s2 = hash(i * 13 + 5), s3 = hash(i * 31 + 9);
            double ph = (t * (0.25 + 0.15 * s3) + s1) % 1.0;
            double a = s2 * Math.PI * 2 + t * 0.3, rr = r * (0.6 + 0.9 * s3);
            double y = c.y + (h + 0.5) * (1 - ph);
            Vec3d p = new Vec3d(c.x + Math.cos(a) * rr + Math.sin(t * 2 + i) * 0.05, y, c.z + Math.sin(a) * rr);
            int alpha = al((int) (230 * Math.sin(ph * Math.PI)));
            orb(vc, p, 0.035 + 0.02 * s1, (alpha << 24) | ICE_W);
        }
        int seg = 48;
        double rr = r * 1.15;
        int edge = (al(110) << 24) | ICE;
        for (int i = 0; i < seg; i++) {
            double a1 = Math.PI * 2 * i / seg, a2 = Math.PI * 2 * (i + 1) / seg;
            ring(vc, c, c.y + 0.02, 0, a1, a2, rr * 0.3, rr, ICE, edge, edge, ICE);
        }
    }

    /** Кристалл льда: две пирамиды с общим основанием + мягкое свечение. */
    private static void crystal(VertexConsumer vc, Vec3d p, double len, double wid, double spin) {
        Vec3d top = p.add(0, len, 0), bot = p.add(0, -len * 0.6, 0);
        int cTip = (al(240) << 24) | ICE_W, cMid = (al(150) << 24) | ICE;
        Vec3d[] eq = new Vec3d[4];
        for (int i = 0; i < 4; i++) { double a = spin + i * Math.PI / 2; eq[i] = p.add(Math.cos(a) * wid, 0, Math.sin(a) * wid); }
        for (int i = 0; i < 4; i++) {
            Vec3d e1 = eq[i], e2 = eq[(i + 1) % 4];
            quad(vc, top, e1, e2, e2, cTip, cMid, cMid, cMid);
            quad(vc, bot, e2, e1, e1, cTip, cMid, cMid, cMid);
        }
        orb(vc, p, wid * 2.5, (al(90) << 24) | ICE);
    }

    private static double hash(int n) { double x = Math.sin(n * 12.9898) * 43758.5453; return x - Math.floor(x); }

    // ---------------- Health ----------------
    private static final String[] HEART = {".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X..."};

    /** Health: пиксельные сердечки над ником цели (золотые = поглощение). */
    private static void drawHealth(VertexConsumer vc, LivingEntity e, float td) {
        Vec3d p = e.getLerpedPos(td).add(0, e.getHeight() + 0.85, 0);
        Vec3d view = p.subtract(cam).normalize();
        Vec3d right = view.crossProduct(new Vec3d(0, 1, 0));
        right = right.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : right.normalize();
        Vec3d up = right.crossProduct(view).normalize();
        double px = 0.016, step = px * 8;
        int max = Math.max(1, Math.min(10, (int) Math.ceil(e.getMaxHealth() / 2)));
        double hp = e.getHealth() / Math.max(1, e.getMaxHealth()) * max * 2;
        int gold = Math.min(5, (int) Math.ceil(e.getAbsorptionAmount() / 2));
        int total = max + gold;
        double startX = -total * step / 2;
        for (int i = 0; i < total; i++) {
            int fill, col;
            if (i < max) { double v = hp - i * 2; fill = v >= 1.5 ? 2 : v >= 0.5 ? 1 : 0; col = 0xFF2A2A; }
            else { fill = 2; col = 0xFFD43B; }
            heart(vc, p, right, up, startX + i * step, px, fill, col);
        }
    }

    private static void heart(VertexConsumer vc, Vec3d p, Vec3d right, Vec3d up, double ox, double px, int fill, int col) {
        int full = (al(255) << 24) | col, empty = (al(200) << 24) | 0x2B2B2B;
        for (int y = 0; y < 6; y++) for (int x = 0; x < 7; x++) {
            if (HEART[y].charAt(x) != 'X') continue;
            boolean filled = fill == 2 || (fill == 1 && x < 4);
            Vec3d a = p.add(right.multiply(ox + x * px)).add(up.multiply((5 - y) * px));
            Vec3d b = a.add(right.multiply(px)), d = a.add(up.multiply(px)), cc = b.add(up.multiply(px));
            int k = filled ? full : empty;
            quad(vc, a, b, cc, d, k, k, k, k);
        }
    }

    /** Сегмент кольца вокруг центра c (tilt = наклон). */
    private static void ring(VertexConsumer vc, Vec3d c, double y, double tilt, double a1, double a2,
                             double in, double out, int ci1, int co1, int co2, int ci2) {
        double y1 = y + Math.cos(a1) * tilt, y2 = y + Math.cos(a2) * tilt;
        Vec3d i1 = new Vec3d(c.x + Math.cos(a1) * in, y1, c.z + Math.sin(a1) * in);
        Vec3d o1 = new Vec3d(c.x + Math.cos(a1) * out, y1, c.z + Math.sin(a1) * out);
        Vec3d o2 = new Vec3d(c.x + Math.cos(a2) * out, y2, c.z + Math.sin(a2) * out);
        Vec3d i2 = new Vec3d(c.x + Math.cos(a2) * in, y2, c.z + Math.sin(a2) * in);
        quad(vc, i1, o1, o2, i2, ci1, co1, co2, ci2);
    }

    /** Souls: четыре светящихся духа летают вокруг цели с длинными хвостами. */
    private static void drawSouls(VertexConsumer vc, Vec3d c, double h, double r, double t) {
        int tailN = 16;
        for (int k = 0; k < 4; k++) {
            Vec3d prev = null, headPos = null;
            for (int i = 0; i <= tailN; i++) {
                double tt = t - i * 0.035;
                double a = tt * 2.2 + k * Math.PI / 2;
                double y = c.y + h * (0.5 + 0.38 * Math.sin(tt * 3 + k * 1.7));
                double rr = r * (1.0 + 0.18 * Math.sin(tt * 4 + k));
                Vec3d p = new Vec3d(c.x + Math.cos(a) * rr, y, c.z + Math.sin(a) * rr);
                if (i == 0) headPos = p;
                float f1 = 1 - (float) (i - 1) / tailN, f2 = 1 - (float) i / tailN;
                if (prev != null)
                    ribbon(vc, prev, p, 0.055 * f2 + 0.006, color(k / 4f, al((int) (200 * f1))), color(k / 4f, al((int) (200 * f2))));
                prev = p;
            }
            orb(vc, headPos, 0.2, color(k / 4f, al(200)));
            orb(vc, headPos, 0.07, (al(255) << 24) | 0xFFFFFF);
        }
    }

    /** Pulse: волны поднимаются от ног к голове, вокруг крутятся 4 луча-столба. */
    private static void drawPulse(VertexConsumer vc, Vec3d c, double h, double r, double t) {
        int seg = 48, waves = 3;
        for (int k = 0; k < waves; k++) {
            double ph = ((t / 1.2) + (double) k / waves) % 1.0;
            double y = c.y + 0.03 + ph * h;
            double rr = r * (1.3 - 0.45 * ph), in = rr * 0.86;
            int alpha = al((int) (230 * Math.sin(ph * Math.PI)));
            for (int i = 0; i < seg; i++) {
                double a1 = Math.PI * 2 * i / seg, a2 = Math.PI * 2 * (i + 1) / seg;
                Vec3d o1 = new Vec3d(c.x + Math.cos(a1) * rr, y, c.z + Math.sin(a1) * rr);
                Vec3d o2 = new Vec3d(c.x + Math.cos(a2) * rr, y, c.z + Math.sin(a2) * rr);
                Vec3d i1 = new Vec3d(c.x + Math.cos(a1) * in, y, c.z + Math.sin(a1) * in);
                Vec3d i2 = new Vec3d(c.x + Math.cos(a2) * in, y, c.z + Math.sin(a2) * in);
                int c1 = color(off(a1), alpha), c2 = color(off(a2), alpha);
                quad(vc, i1, o1, o2, i2, c1 & 0xFFFFFF, c1, c2, c2 & 0xFFFFFF);
            }
        }
        for (int i = 0; i < 4; i++) {
            double a = -t * 1.5 + i * Math.PI / 2;
            Vec3d b = new Vec3d(c.x + Math.cos(a) * r * 1.3, c.y, c.z + Math.sin(a) * r * 1.3);
            ribbon(vc, b, b.add(0, h * 1.1, 0), 0.025, color(i / 4f, al(200)), color(i / 4f, 0));
            orb(vc, b.add(0, h * (0.5 + 0.45 * Math.sin(t * 3 + i)), 0), 0.1, color(i / 4f, al(255)));
        }
    }

    /** Линия-лента, всегда повёрнутая к камере. */
    static void ribbon(VertexConsumer vc, Vec3d a, Vec3d b, double w, int ca, int cb) {
        Vec3d dir = b.subtract(a), view = a.add(b).multiply(0.5).subtract(cam);
        Vec3d side = dir.crossProduct(view);
        if (side.lengthSquared() < 1e-9) return;
        side = side.normalize().multiply(w);
        quad(vc, a.add(side), b.add(side), b.subtract(side), a.subtract(side), ca, cb, cb, ca);
    }

    /** Мягкий светящийся шар (billboard): яркий центр, прозрачные края. */
    static void orb(VertexConsumer vc, Vec3d p, double s, int col) {
        Vec3d view = p.subtract(cam).normalize();
        Vec3d right = view.crossProduct(new Vec3d(0, 1, 0));
        right = right.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : right.normalize();
        Vec3d up = right.crossProduct(view).normalize();
        int edge = col & 0xFFFFFF, n = 10;
        for (int i = 0; i < n; i++) {
            double a1 = Math.PI * 2 * i / n, a2 = Math.PI * 2 * (i + 1) / n;
            Vec3d e1 = p.add(right.multiply(Math.cos(a1) * s)).add(up.multiply(Math.sin(a1) * s));
            Vec3d e2 = p.add(right.multiply(Math.cos(a2) * s)).add(up.multiply(Math.sin(a2) * s));
            quad(vc, p, e1, e2, e2, col, edge, edge, edge);
        }
    }

    // ---------------- Jump Circles ----------------
    private static void drawCircles(VertexConsumer vc) {
        Module m = ModuleManager.get("Jump Circles");
        double life = m.num("Time") * 1000, maxR = m.num("Radius");
        circles.removeIf(cl -> now - cl.start() > life);
        int seg = 48;
        for (Circle cl : circles) {
            double age = (now - cl.start()) / life;
            double r = maxR * (1 - Math.pow(1 - age, 3)), in = r * 0.75;
            int alpha = (int) (220 * (1 - age));
            Vec3d c = cl.pos();
            double y = c.y + 0.02;
            for (int i = 0; i < seg; i++) {
                double a1 = Math.PI * 2 * i / seg, a2 = Math.PI * 2 * (i + 1) / seg;
                Vec3d o1 = new Vec3d(c.x + Math.cos(a1) * r, y, c.z + Math.sin(a1) * r);
                Vec3d o2 = new Vec3d(c.x + Math.cos(a2) * r, y, c.z + Math.sin(a2) * r);
                Vec3d i1 = new Vec3d(c.x + Math.cos(a1) * in, y, c.z + Math.sin(a1) * in);
                Vec3d i2 = new Vec3d(c.x + Math.cos(a2) * in, y, c.z + Math.sin(a2) * in);
                int c1 = color(off(a1), alpha), c2 = color(off(a2), alpha);
                quad(vc, i1, o1, o2, i2, c1 & 0xFFFFFF, c1, c2, c2 & 0xFFFFFF);
            }
        }
    }

    // ---------------- Trails ----------------
    private static void drawTrail(VertexConsumer vc, float td) {
        Module m = ModuleManager.get("Trails");
        double life = m.num("Length") * 1000, hgt = m.num("Height");
        List<TrailPoint> pts = new ArrayList<>(trail);
        pts.add(new TrailPoint(mc.player.getLerpedPos(td), now));
        for (int i = 0; i < pts.size() - 1; i++) {
            TrailPoint a = pts.get(i), b = pts.get(i + 1);
            if (a.pos().squaredDistanceTo(b.pos()) < 1e-6) continue;
            double fa = clamp(1 - (now - a.time()) / life), fb = clamp(1 - (now - b.time()) / life);
            int ta = color(timeOff(a.time()), (int) (200 * fa)), tb = color(timeOff(b.time()), (int) (200 * fb));
            int ba = (ta & 0xFFFFFF) | ((int) (50 * fa) << 24), bb = (tb & 0xFFFFFF) | ((int) (50 * fb) << 24);
            quad(vc, a.pos().add(0, 0.05, 0), b.pos().add(0, 0.05, 0), b.pos().add(0, hgt, 0), a.pos().add(0, hgt, 0),
                    ba, bb, tb, ta);
        }
    }

    // ---------------- China Hat (5 стилей: Classic Cone, Halo Ring, Horns, Cat Ears, Crown) ----------------
    private static void drawHat(VertexConsumer vc, Module m, Entity e, float td) {
        Vec3d c = e.getLerpedPos(td);
        double top = c.y + e.getHeight() + 0.02, r = m.num("Radius"), h = m.num("Height");
        String style = m.find("Style") instanceof Setting.Mode md ? md.get() : "Classic Cone";
        double spin = now / 2000.0;
        double yawRad = Math.toRadians(e.getYaw());
        Vec3d right = new Vec3d(Math.cos(yawRad), 0, Math.sin(yawRad));
        Vec3d fwd = new Vec3d(-Math.sin(yawRad), 0, Math.cos(yawRad));

        switch (style) {
            case "Halo Ring" -> {
                double hy = top + 0.18 + Math.sin(now / 450.0) * 0.03;
                double outer = r * 0.85, inner = r * 0.68;
                int seg = 32;
                for (int i = 0; i < seg; i++) {
                    double a1 = spin + Math.PI * 2 * i / seg, a2 = spin + Math.PI * 2 * (i + 1) / seg;
                    Vec3d o1 = new Vec3d(c.x + Math.cos(a1) * outer, hy, c.z + Math.sin(a1) * outer);
                    Vec3d o2 = new Vec3d(c.x + Math.cos(a2) * outer, hy, c.z + Math.sin(a2) * outer);
                    Vec3d i1 = new Vec3d(c.x + Math.cos(a1) * inner, hy + 0.03, c.z + Math.sin(a1) * inner);
                    Vec3d i2 = new Vec3d(c.x + Math.cos(a2) * inner, hy + 0.03, c.z + Math.sin(a2) * inner);
                    int c1 = color(off(a1 - spin), 210), c2 = color(off(a2 - spin), 210);
                    quad(vc, i1, o1, o2, i2, c1, c1, c2, c2);
                }
            }
            case "Horns" -> {
                for (int side = -1; side <= 1; side += 2) {
                    Vec3d base = new Vec3d(c.x, top - 0.04, c.z).add(right.multiply(side * 0.22));
                    Vec3d mid = base.add(right.multiply(side * 0.16)).add(fwd.multiply(0.06)).add(0, h * 0.65, 0);
                    Vec3d tip = mid.add(right.multiply(-side * 0.08)).add(fwd.multiply(0.14)).add(0, h * 0.75, 0);
                    int cb = color(0.15f, 190), cm = color(0.45f, 220), ct = color(0.85f, 240);
                    Vec3d s1 = fwd.multiply(0.055), s2 = fwd.multiply(-0.055);
                    quad(vc, base.add(s1), base.add(s2), mid.add(s2.multiply(0.5)), mid.add(s1.multiply(0.5)), cb, cb, cm, cm);
                    quad(vc, mid.add(s1.multiply(0.5)), mid.add(s2.multiply(0.5)), tip, tip, cm, cm, ct, ct);
                }
            }
            case "Cat Ears" -> {
                for (int side = -1; side <= 1; side += 2) {
                    Vec3d center = new Vec3d(c.x, top - 0.03, c.z).add(right.multiply(side * 0.20));
                    Vec3d pL = center.add(right.multiply(-0.11));
                    Vec3d pR = center.add(right.multiply(0.11));
                    Vec3d tip = center.add(right.multiply(side * 0.05)).add(0, h * 0.95, 0);
                    int ca = color(0.2f, 180), ct = color(0.6f, 235);
                    quad(vc, pL, pR, tip, tip, ca, ca, ct, ct);
                }
            }
            case "Crown" -> {
                int pts = 5, seg = pts * 2;
                double cr = r * 0.56;
                for (int i = 0; i < seg; i++) {
                    double a1 = spin + Math.PI * 2 * i / seg, a2 = spin + Math.PI * 2 * (i + 1) / seg;
                    double h1 = (i % 2 == 0) ? h * 0.95 : h * 0.42;
                    double h2 = ((i + 1) % 2 == 0) ? h * 0.95 : h * 0.42;
                    Vec3d b1 = new Vec3d(c.x + Math.cos(a1) * cr, top, c.z + Math.sin(a1) * cr);
                    Vec3d b2 = new Vec3d(c.x + Math.cos(a2) * cr, top, c.z + Math.sin(a2) * cr);
                    Vec3d t1 = b1.add(0, h1, 0), t2 = b2.add(0, h2, 0);
                    int c1 = color((float) i / seg, 195), c2 = color((float) (i + 1) / seg, 195);
                    quad(vc, b1, b2, t2, t1, c1, c2, c2, c1);
                }
            }
            default -> {
                Vec3d apex = new Vec3d(c.x, top + h, c.z);
                int seg = 32, ca = color(0.5f, 170);
                for (int i = 0; i < seg; i++) {
                    double a1 = spin + Math.PI * 2 * i / seg, a2 = spin + Math.PI * 2 * (i + 1) / seg;
                    Vec3d p1 = new Vec3d(c.x + Math.cos(a1) * r, top, c.z + Math.sin(a1) * r);
                    Vec3d p2 = new Vec3d(c.x + Math.cos(a2) * r, top, c.z + Math.sin(a2) * r);
                    int c1 = color(off(a1 - spin), 110), c2 = color(off(a2 - spin), 110);
                    quad(vc, apex, p2, p1, p1, ca, c2, c1, c1);
                }
            }
        }
    }

    // ---------------- утилиты ----------------
    private static void v(VertexConsumer vc, Vec3d p, int argb) {
        vc.vertex(mat, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z)).color(argb);
    }

    /** Двусторонний quad. */
    static void quad(VertexConsumer vc, Vec3d a, Vec3d b, Vec3d c, Vec3d d, int ca, int cb, int cc, int cd) {
        v(vc, a, ca); v(vc, b, cb); v(vc, c, cc); v(vc, d, cd);
        v(vc, d, cd); v(vc, c, cc); v(vc, b, cb); v(vc, a, ca);
    }

    private static float off(double angle) { return (float) (0.5 - 0.5 * Math.cos(angle)); }
    private static float timeOff(long t) { return (float) (0.5 + 0.5 * Math.sin(t / 500.0)); }
    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }

    /** Цвет из текущей темы. */
    static int color(float offset, int alpha) { return Theme.color(now, offset, alpha); }
}
