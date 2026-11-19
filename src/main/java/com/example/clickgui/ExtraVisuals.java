package com.example.clickgui;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Дополнительные легитные 3D/2D визуалы и боевые/мирные индикаторы. */
public final class ExtraVisuals {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final ItemStack SHIELD_STACK = new ItemStack(Items.SHIELD);
    private static final float[] PROJ = new float[2];

    // Damage Popups
    private record Popup(double x, double y, double z, String text, int color, long bornMs) {}
    private static final List<Popup> POPUPS = new CopyOnWriteArrayList<>();
    private static final Map<LivingEntity, Float> PREV_HP = new WeakHashMap<>();

    // Totem Pops
    private static final Map<UUID, Float> PREV_ABS = new HashMap<>();
    private static final Map<UUID, Integer> POPS = new HashMap<>();

    // Swing Trail
    private record SwingArc(double x, double y, double z, float yaw, float pitch, long bornMs) {}
    private static final List<SwingArc> SWINGS = new CopyOnWriteArrayList<>();
    private static boolean wasSwinging;

    // Light Level cached dark spots
    private static final List<BlockPos> DARK_SPOTS = new ArrayList<>();
    private static int lightScanTicks;

    // Reach Display & Hurt Border
    private static double lastReachDist;
    private static long lastReachMs;
    private static int sessionHits;
    private static float hurtBorderAlpha;
    private static int lastHurtTicks;

    private ExtraVisuals() {}

    public static double lastReach() { return lastReachDist; }
    public static long lastReachTime() { return lastReachMs; }
    public static int sessionHits() { return sessionHits; }

    public static void onAttack(Entity target) {
        if (mc.player == null || target == null || target == FakePlayer.entity()) return;
        sessionHits++;
        Vec3d eye = mc.player.getEyePos();
        var box = target.getBoundingBox();
        double cx = MathUtil.clamp(eye.x, box.minX, box.maxX);
        double cy = MathUtil.clamp(eye.y, box.minY, box.maxY);
        double cz = MathUtil.clamp(eye.z, box.minZ, box.maxZ);
        lastReachDist = Math.sqrt((eye.x - cx) * (eye.x - cx) + (eye.y - cy) * (eye.y - cy) + (eye.z - cz) * (eye.z - cz));
        lastReachMs = System.currentTimeMillis();

        Module crit = ModuleManager.get("Crit Sparkles");
        if (crit.enabled && mc.world != null && mc.player.fallDistance > 0.05f && !mc.player.isOnGround()
                && !mc.player.isTouchingWater() && !mc.player.isClimbing()) {
            int count = (int) crit.num("Count");
            Vec3d p = target.getEntityPos().add(0, target.getHeight() * 0.6, 0);
            for (int i = 0; i < count; i++) {
                double vx = (Math.random() - 0.5) * 0.35;
                double vy = Math.random() * 0.35 + 0.05;
                double vz = (Math.random() - 0.5) * 0.35;
                mc.particleManager.addParticle(ParticleTypes.CRIT, p.x, p.y, p.z, vx, vy, vz);
            }
        }
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        POPUPS.removeIf(p -> now - p.bornMs > 950L);
        SWINGS.removeIf(s -> now - s.bornMs > 280L);

        if (mc.player == null || mc.world == null) {
            PREV_HP.clear();
            PREV_ABS.clear();
            POPS.clear();
            DARK_SPOTS.clear();
            hurtBorderAlpha = 0f;
            return;
        }

        // Hurt Border tracker
        int ht = mc.player.hurtTime;
        if (ht > lastHurtTicks && ht > 0) hurtBorderAlpha = 1f;
        lastHurtTicks = ht;

        // Swing Trail trigger
        boolean sw = mc.player.handSwinging;
        if (sw && !wasSwinging && ModuleManager.get("Swing Trail").enabled) {
            Vec3d eye = mc.player.getEyePos();
            SWINGS.add(new SwingArc(eye.x, eye.y - 0.2, eye.z, mc.player.getYaw(), mc.player.getPitch(), now));
        }
        wasSwinging = sw;

        // Damage Popups & Totem Pop Counter
        boolean doDmg = ModuleManager.get("Damage Popups").enabled;
        boolean doPop = ModuleManager.get("Totem Pop Counter").enabled;
        if (doDmg || doPop) {
            Module dmgMod = ModuleManager.get("Damage Popups");
            Module popMod = ModuleManager.get("Totem Pop Counter");
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof LivingEntity le) || le == FakePlayer.entity()) continue;
                if (mc.player.distanceTo(le) > 48f) continue;
                float hp = le.getHealth();
                Float prev = PREV_HP.put(le, hp);
                if (doDmg && prev != null && le != mc.player) {
                    float diff = hp - prev;
                    if (diff <= -0.3f) {
                        POPUPS.add(new Popup(le.getX(), le.getY() + le.getHeight() + 0.25, le.getZ(),
                                String.format(Locale.ROOT, "-%.1f", -diff),
                                dmgMod.bool("Crit Color") && -diff >= 6f ? 0xFFFFD166 : 0xFFFF5555, now));
                    } else if (diff >= 0.4f && dmgMod.bool("Heal")) {
                        POPUPS.add(new Popup(le.getX(), le.getY() + le.getHeight() + 0.25, le.getZ(),
                                String.format(Locale.ROOT, "+%.1f", diff), 0xFF4ADE80, now));
                    }
                }
                if (doPop && le instanceof PlayerEntity pe && le != mc.player) {
                    float abs = pe.getAbsorptionAmount();
                    Float pAbs = PREV_ABS.put(pe.getUuid(), abs);
                    if (pAbs != null && pAbs < 3.5f && abs >= 7.5f && pe.hurtTime > 0) {
                        int total = POPS.merge(pe.getUuid(), 1, Integer::sum);
                        String nm = pe.getName().getString();
                        if (popMod.bool("Toast")) Notifications.push("Totem Pop", nm + " (" + total + ")", 0xFFFFD166);
                        if (popMod.bool("Chat Alert")) {
                            mc.player.sendMessage(net.minecraft.text.Text.literal(
                                    "\u00A76[Totem] \u00A7f" + nm + " \u00A77popped \u00A7e" + total + "\u00A77!"), false);
                        }
                    }
                    if (!pe.isAlive()) POPS.remove(pe.getUuid());
                }
            }
        }

        // Light Level cached scan every 5 ticks
        Module ll = ModuleManager.get("Light Level");
        if (ll.enabled) {
            if (lightScanTicks-- <= 0) {
                lightScanTicks = 5;
                DARK_SPOTS.clear();
                int r = (int) ll.num("Radius");
                BlockPos center = mc.player.getBlockPos();
                BlockPos.Mutable mut = new BlockPos.Mutable();
                BlockPos.Mutable above = new BlockPos.Mutable();
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        for (int dy = -3; dy <= 3; dy++) {
                            mut.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                            above.set(mut.getX(), mut.getY() + 1, mut.getZ());
                            BlockState st = mc.world.getBlockState(mut);
                            if (st.isFullCube(mc.world, mut) && mc.world.getBlockState(above).isAir()) {
                                if (mc.world.getLightLevel(LightType.BLOCK, above) == 0) {
                                    DARK_SPOTS.add(above.toImmutable());
                                }
                            }
                        }
                    }
                }
            }
        } else {
            DARK_SPOTS.clear();
        }
    }

    public static void render3D(VertexConsumer vc, Matrix4f mat, double camX, double camY, double camZ, float td) {
        if (vc == null || mat == null || mc.player == null || mc.world == null) return;
        long now = System.currentTimeMillis();

        // 1. Chunk Borders
        Module cb = ModuleManager.get("Chunk Borders");
        if (cb.enabled) {
            BlockPos p = mc.player.getBlockPos();
            double cx0 = (p.getX() >> 4) * 16.0, cz0 = (p.getZ() >> 4) * 16.0;
            double y0 = Math.floor(mc.player.getY()), y1 = y0 + 16.0;
            int col = Theme.color(now, 0.2f, 200);
            if (cb.bool("Vertical")) {
                RenderUtil.ribbon3d(vc, mat, camX, camY, camZ, cx0, y0 - 8, cz0, cx0, y1 + 8, cz0, 0.03, col, col);
                RenderUtil.ribbon3d(vc, mat, camX, camY, camZ, cx0 + 16, y0 - 8, cz0, cx0 + 16, y1 + 8, cz0, 0.03, col, col);
                RenderUtil.ribbon3d(vc, mat, camX, camY, camZ, cx0, y0 - 8, cz0 + 16, cx0, y1 + 8, cz0 + 16, 0.03, col, col);
                RenderUtil.ribbon3d(vc, mat, camX, camY, camZ, cx0 + 16, y0 - 8, cz0 + 16, cx0 + 16, y1 + 8, cz0 + 16, 0.03, col, col);
            }
            for (int h = 0; h <= (cb.bool("Grid") ? 4 : 0); h += 2) {
                double yy = y0 + h;
                int c2 = Theme.color(now, h * 0.1f, 150);
                RenderUtil.ribbon3d(vc, mat, camX, camY, camZ, cx0, yy, cz0, cx0 + 16, yy, cz0, 0.02, c2, c2);
                RenderUtil.ribbon3d(vc, mat, camX, camY, camZ, cx0 + 16, yy, cz0, cx0 + 16, yy, cz0 + 16, 0.02, c2, c2);
                RenderUtil.ribbon3d(vc, mat, camX, camY, camZ, cx0 + 16, yy, cz0 + 16, cx0, yy, cz0 + 16, 0.02, c2, c2);
                RenderUtil.ribbon3d(vc, mat, camX, camY, camZ, cx0, yy, cz0 + 16, cx0, yy, cz0, 0.02, c2, c2);
            }
        }

        // 2. Light Level (red X on dark spawnable blocks)
        if (!DARK_SPOTS.isEmpty()) {
            int col = 0xCCFF4D4D;
            for (BlockPos bp : DARK_SPOTS) {
                double x = bp.getX(), y = bp.getY() + 0.03, z = bp.getZ();
                RenderUtil.ribbon3d(vc, mat, camX, camY, camZ, x + 0.15, y, z + 0.15, x + 0.85, y, z + 0.85, 0.02, col, col);
                RenderUtil.ribbon3d(vc, mat, camX, camY, camZ, x + 0.85, y, z + 0.15, x + 0.15, y, z + 0.85, 0.02, col, col);
            }
        }

        // 3. Origin Marker (0, 0)
        if (ModuleManager.get("Origin Marker").enabled) {
            double py = mc.player.getY();
            double d = Math.hypot(mc.player.getX() - 0.5, mc.player.getZ() - 0.5);
            if (d < 5000.0) {
                int c1 = Theme.color(now, 0f, 200);
                int c2 = Theme.color(now, 0.5f, 0);
                RenderUtil.ribbon3d(vc, mat, camX, camY, camZ, 0.5, py - 10, 0.5, 0.5, py + 80, 0.5, 0.15, c1, c2);
            }
        }

        // 4. Safe Zone 24m (mob-spawn prevention ring)
        if (ModuleManager.get("Safe Zone 24m").enabled) {
            Vec3d p = mc.player.getLerpedPos(td);
            int col = Theme.color(now, 0.3f, 120);
            drawCircle3D(vc, mat, camX, camY, camZ, p.x, p.y + 0.05, p.z, 24.0, 48, 0.035, col);
        }

        // 5. Reach Circle (3.0m circle)
        if (ModuleManager.get("Reach Circle").enabled) {
            Vec3d p = mc.player.getLerpedPos(td);
            int col = Theme.color(now, 0.1f, 150);
            drawCircle3D(vc, mat, camX, camY, camZ, p.x, p.y + 0.04, p.z, 3.0, 36, 0.025, col);
        }

        // 6. Horizon Line (3D compass horizon ring)
        if (ModuleManager.get("Horizon Line").enabled) {
            Vec3d p = mc.player.getLerpedPos(td);
            int col = Theme.color(now, 0.4f, 90);
            drawCircle3D(vc, mat, camX, camY, camZ, p.x, p.y + mc.player.getStandingEyeHeight(), p.z, 32.0, 64, 0.04, col);
        }

        // 7. Crystal Preview
        Module cp = ModuleManager.get("Crystal Preview");
        if (cp.enabled && cp.bool("Radius Ring")) {
            for (Entity e : mc.world.getEntities()) {
                if (e instanceof EndCrystalEntity && mc.player.distanceTo(e) < 32f) {
                    Vec3d c = e.getLerpedPos(td);
                    drawCircle3D(vc, mat, camX, camY, camZ, c.x, c.y + 0.05, c.z, 6.0, 32, 0.03, 0xCCFF3B3B);
                    drawCircle3D(vc, mat, camX, camY, camZ, c.x, c.y + 0.05, c.z, 12.0, 40, 0.02, 0x77FF9F0A);
                }
            }
        }

        // 8. Swing Trail
        Module st = ModuleManager.get("Swing Trail");
        if (st.enabled && !SWINGS.isEmpty()) {
            double len = st.num("Length");
            double w = st.num("Width") * 0.03;
            for (SwingArc arc : SWINGS) {
                float age = (now - arc.bornMs) / 280f;
                int a = Math.max(0, Math.min(220, Math.round((1f - age) * 220f)));
                int col = Theme.color(now, age, a);
                double px = 0, py = 0, pz = 0;
                for (int i = 0; i <= 10; i++) {
                    double f = i / 10.0;
                    double yawRad = Math.toRadians(arc.yaw + (f - 0.5) * 90.0 * len);
                    double pitchRad = Math.toRadians(arc.pitch + (f - 0.5) * 25.0);
                    double r = 1.15;
                    double nx = arc.x - Math.sin(yawRad) * Math.cos(pitchRad) * r;
                    double ny = arc.y - Math.sin(pitchRad) * r;
                    double nz = arc.z + Math.cos(yawRad) * Math.cos(pitchRad) * r;
                    if (i > 0) RenderUtil.ribbon3d(vc, mat, camX, camY, camZ, px, py, pz, nx, ny, nz, w, col, col);
                    px = nx; py = ny; pz = nz;
                }
            }
        }
    }

    private static void drawCircle3D(VertexConsumer vc, Matrix4f mat, double camX, double camY, double camZ,
                                     double cx, double cy, double cz, double r, int seg, double w, int col) {
        for (int i = 0; i < seg; i++) {
            double a1 = Math.PI * 2.0 * i / seg, a2 = Math.PI * 2.0 * (i + 1) / seg;
            RenderUtil.ribbon3d(vc, mat, camX, camY, camZ,
                    cx + Math.cos(a1) * r, cy, cz + Math.sin(a1) * r,
                    cx + Math.cos(a2) * r, cy, cz + Math.sin(a2) * r, w, col, col);
        }
    }

    public static void renderHudOverlays(DrawContext c, int sw, int sh, float td) {
        if (mc.player == null || mc.world == null) return;
        int cx = sw / 2, cy = sh / 2;
        long now = System.currentTimeMillis();

        // 1. Hurt Border
        if (ModuleManager.get("Hurt Border").enabled && hurtBorderAlpha > 0.01f) {
            hurtBorderAlpha = MathUtil.expSmooth(hurtBorderAlpha, 0f, 8f, Animation.dt());
            int a = Math.min(110, Math.round(hurtBorderAlpha * 110f));
            int edge = ColorUtil.withAlpha(Theme.primary(), a);
            int clear = Theme.primary() & 0x00FFFFFF;
            c.fillGradient(0, 0, sw, 28, edge, clear);
            c.fillGradient(0, sh - 28, sw, sh, clear, edge);
        }

        // 2. Speed Lines
        Module sl = ModuleManager.get("Speed Lines");
        if (sl.enabled && ServerMetrics.bps() >= sl.num("Threshold")) {
            float k = MathUtil.clamp01((float) ((ServerMetrics.bps() - sl.num("Threshold")) / 8.0)) * (float) sl.num("Intensity");
            int col = ColorUtil.withAlpha(Theme.primary(), Math.round(k * 90f));
            int phase = (int) ((now / 45L) % 16);
            for (int i = 0; i < 12; i++) {
                int y = ((i * sh / 12) + phase * 3) % sh;
                int len = 18 + (i % 3) * 14;
                c.fill(0, y, len, y + 1, col);
                c.fill(sw - len, y, sw, y + 1, col);
            }
        }

        // 3. Custom Crosshair
        Module ch = ModuleManager.get("Custom Crosshair");
        if (ch.enabled && mc.currentScreen == null) {
            String mode = ((Setting.Mode) ch.find("Mode")).get();
            int size = (int) ch.num("Size");
            int thick = Math.max(1, (int) ch.num("Thickness"));
            int gap = (int) ch.num("Gap");
            if (ch.bool("Cooldown Gap")) {
                gap += Math.round((1f - mc.player.getAttackCooldownProgress(0.5f)) * 6f);
            }
            int col = Theme.primary();
            if ("Dot".equals(mode)) {
                RenderUtil.roundRect(c, cx - thick, cy - thick, thick * 2, thick * 2, thick, col);
            } else if ("Circle".equals(mode)) {
                int r = size + 2;
                c.fill(cx - r, cy - r, cx + r, cy - r + 1, col);
                c.fill(cx - r, cy + r - 1, cx + r, cy + r, col);
                c.fill(cx - r, cy - r, cx - r + 1, cy + r, col);
                c.fill(cx + r - 1, cy - r, cx + r, cy + r, col);
                c.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
            } else {
                int h = thick / 2;
                c.fill(cx - gap - size, cy - h, cx - gap, cy - h + thick, col);
                c.fill(cx + gap, cy - h, cx + gap + size, cy - h + thick, col);
                c.fill(cx - h, cy - gap - size, cx - h + thick, cy - gap, col);
                c.fill(cx - h, cy + gap, cx - h + thick, cy + gap + size, col);
                if ("Dynamic".equals(mode)) c.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFFFFFFFF);
            }
        }

        // 4. Attack Cooldown Bar
        if (ModuleManager.get("Attack Cooldown Bar").enabled && mc.currentScreen == null) {
            float prog = mc.player.getAttackCooldownProgress(0.5f);
            if (prog < 0.99f) {
                int bw = 36, bx = cx - bw / 2, by = cy + 12;
                RenderUtil.roundRect(c, bx - 1, by - 1, bw + 2, 4, 2, 0x99101620);
                RenderUtil.roundGradientH(c, bx, by, Math.max(2, Math.round(bw * prog)), 2, 1, Theme.primary(), Theme.secondary());
            }
        }

        // 5. Shield Indicator
        if (ModuleManager.get("Shield Indicator").enabled) {
            float cd = mc.player.getItemCooldownManager().getCooldownProgress(SHIELD_STACK, td);
            if (cd > 0f) {
                String txt = String.format(Locale.ROOT, "SHIELD %.1fs", cd * 5.0f);
                int w = Hud.tw(txt) + 10;
                RenderUtil.roundRect(c, cx - w / 2, cy + 20, w, 13, 3, 0xB02A0D12);
                Hud.txt(c, txt, cx - Hud.tw(txt) / 2, cy + 22, 0xFFFF6B6B);
            }
        }

        // 6. Enemy Pointer (arrows around crosshair)
        Module ep = ModuleManager.get("Enemy Pointer");
        if (ep.enabled) {
            double r = ep.num("Radius"), maxD = ep.num("Range");
            float yaw = mc.player.getYaw();
            for (PlayerEntity pe : mc.world.getPlayers()) {
                if (pe == mc.player || !pe.isAlive() || pe.isInvisible()) continue;
                double d = mc.player.distanceTo(pe);
                if (d > maxD) continue;
                double ang = Math.toDegrees(Math.atan2(pe.getZ() - mc.player.getZ(), pe.getX() - mc.player.getX())) - 90.0;
                double rel = Math.toRadians(MathHelper.wrapDegrees((float) ang - yaw));
                int px = (int) Math.round(cx + Math.sin(rel) * r);
                int py = (int) Math.round(cy - Math.cos(rel) * r);
                int col = HudWorld.isFriend(pe.getName().getString()) ? 0xFF4ADE80 : Theme.primary();
                RenderUtil.roundRect(c, px - 2, py - 2, 4, 4, 2, col);
            }
        }

        // 7. Damage Popups (2D projected)
        if (ModuleManager.get("Damage Popups").enabled && !POPUPS.isEmpty()) {
            float sc = (float) ModuleManager.get("Damage Popups").num("Scale");
            for (Popup p : POPUPS) {
                float age = (now - p.bornMs) / 950f;
                if (!MathUtil.worldToScreen(p.x, p.y + age * 0.85, p.z, sw, sh, PROJ)) continue;
                int a = Math.max(0, Math.min(255, Math.round((1f - age) * 255f)));
                int col = ColorUtil.withAlpha(p.color, a);
                c.getMatrices().pushMatrix();
                try {
                    c.getMatrices().translate(PROJ[0], PROJ[1]);
                    c.getMatrices().scale(sc, sc);
                    Hud.txt(c, p.text, -Hud.tw(p.text) / 2, -4, col);
                } finally {
                    c.getMatrices().popMatrix();
                }
            }
        }

        // 8. Item ESP Tags (only visible items)
        Module ies = ModuleManager.get("Item ESP Tags");
        if (ies.enabled) {
            double maxD = ies.num("Max Dist");
            boolean visOnly = ies.bool("Only Visible");
            Vec3d eye = mc.player.getEyePos();
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof ItemEntity ie) || ie.isRemoved()) continue;
                if (mc.player.distanceTo(ie) > maxD) continue;
                Vec3d pos = ie.getLerpedPos(td).add(0, 0.55, 0);
                if (visOnly && mc.world.raycast(new RaycastContext(eye, pos, RaycastContext.ShapeType.VISUAL,
                        RaycastContext.FluidHandling.NONE, mc.player)).getType() != HitResult.Type.MISS) continue;
                if (!MathUtil.worldToScreen(pos.x, pos.y, pos.z, sw, sh, PROJ)) continue;
                ItemStack st = ie.getStack();
                String nm = st.getName().getString() + (ies.bool("Count") && st.getCount() > 1 ? " x" + st.getCount() : "");
                int w = Hud.tw(nm) + 8;
                int lx = Math.round(PROJ[0] - w / 2f), ly = Math.round(PROJ[1] - 6);
                RenderUtil.roundRect(c, lx, ly, w, 12, 3, 0x99101620);
                Hud.txt(c, nm, lx + 4, ly + 2, 0xFFFFFFFF);
            }
        }

        // 9. Target Block Info (WAILA at top center)
        if (ModuleManager.get("Target Block Info").enabled && mc.crosshairTarget instanceof BlockHitResult bhr
                && bhr.getType() == HitResult.Type.BLOCK) {
            BlockState state = mc.world.getBlockState(bhr.getBlockPos());
            if (!state.isAir()) {
                String name = state.getBlock().getName().getString();
                int w = Hud.tw(name) + 16;
                int x = (sw - w) / 2, y = 8;
                RenderUtil.roundRect(c, x, y, w, 15, 4, 0xAA101620);
                c.fill(x + 4, y, x + w - 4, y + 1, Theme.primary());
                Hud.txt(c, name, x + 8, y + 4, 0xFFFFFFFF);
            }
        }

        // 10. Danger Alert (nearby TNT / End Crystal / ignited Creeper)
        if (ModuleManager.get("Danger Alert").enabled) {
            String warn = null;
            for (Entity e : mc.world.getEntities()) {
                double d = mc.player.distanceTo(e);
                if (d > 8.5) continue;
                if (e instanceof TntEntity) { warn = String.format(Locale.ROOT, "! TNT %.1fm !", d); break; }
                if (e instanceof EndCrystalEntity) { warn = String.format(Locale.ROOT, "! CRYSTAL %.1fm !", d); break; }
                if (e instanceof CreeperEntity cr && (cr.getFuseSpeed() > 0 || cr.isIgnited())) {
                    warn = String.format(Locale.ROOT, "! CREEPER %.1fm !", d); break;
                }
            }
            if (warn != null) {
                int w = Hud.tw(warn) + 14;
                int x = (sw - w) / 2, y = cy + 38;
                RenderUtil.roundRect(c, x, y, w, 14, 4, 0xCC3B0A0A);
                Hud.txt(c, warn, x + 7, y + 3, 0xFFFF6B6B);
            }
        }
    }

    public static boolean renderBossBars(DrawContext c, Map<UUID, net.minecraft.client.gui.hud.ClientBossBar> bars) {
        Module m = ModuleManager.get("Bossbar Customizer");
        if (!m.enabled || bars == null || bars.isEmpty()) return false;
        boolean hidePromo = m.bool("Hide Promo");
        boolean themeStyle = m.bool("Theme Style");
        if (!themeStyle && !hidePromo) return false;
        int sw = c.getScaledWindowWidth();
        int y = 6;
        int idx = 0;
        for (var bar : bars.values()) {
            String raw = bar.getName().getString();
            String low = raw.toLowerCase(Locale.ROOT);
            if (hidePromo && (low.contains("discord") || low.contains("store") || low.contains("shop")
                    || low.contains("http") || low.contains("t.me") || low.contains("vk.com")
                    || low.contains("донат") || low.contains("скид") || low.contains("промо")
                    || low.contains("сайт") || low.contains("mc."))) {
                continue;
            }
            if (++idx > 3) break;
            int w = 186, h = 15, x = (sw - w) / 2;
            RenderUtil.roundRect(c, x, y, w, h, 4, 0xAA101620);
            float pct = MathUtil.clamp01(bar.getPercent());
            int fw = Math.max(4, Math.round((w - 6) * pct));
            if (themeStyle) {
                RenderUtil.roundGradientH(c, x + 3, y + h - 4, fw, 2, 1, Theme.primary(), Theme.secondary());
            } else {
                RenderUtil.roundRect(c, x + 3, y + h - 4, fw, 2, 1, 0xFFE879F9);
            }
            String title = raw.length() > 38 ? raw.substring(0, 38) + "..." : raw;
            Hud.txt(c, title, x + (w - Hud.tw(title)) / 2, y + 2, 0xFFFFFFFF);
            y += 18;
        }
        return true;
    }

}
