package com.example.clickgui;

import com.example.clickgui.mixin.InteractionAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

/**
 * 3D-рендер для:
 * - 3D Hitboxes (рамки сущностей, уровень глаз и вектор взгляда)
 * - TNT & Creeper Fuse Radius (сфера/кольца радиуса взрыва)
 * - Block Outline Customizer (цветная/градиентная рамка и заливка блока)
 * - Break Progress Overlay (анимационный куб прогресса разрушения)
 */
public final class WorldVisuals {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public static void renderGlow(VertexConsumer glow, float td) {
        if (mc.player == null || mc.world == null || glow == null) return;
        renderHitboxes(glow, td);
        renderFuseRadius(glow, td);
        renderBlockOutlineGlow(glow);
        renderBreakProgressGlow(glow);
    }

    public static void renderFill(VertexConsumer fill) {
        if (mc.player == null || mc.world == null || fill == null) return;
        renderBlockOutlineFill(fill);
        renderBreakProgressFill(fill);
    }

    // ---------------- 3D Hitboxes ----------------
    private static void renderHitboxes(VertexConsumer vc, float td) {
        Module m = ModuleManager.get("3D Hitboxes");
        Module hs = ModuleManager.get("Hitbox Styler");
        if (!m.enabled && !hs.enabled) return;
        boolean players = !m.enabled || m.bool("Players");
        boolean mobs = !m.enabled || m.bool("Mobs");
        boolean items = m.enabled && m.bool("Items");
        boolean look = (m.enabled && m.bool("Look Vector")) || (hs.enabled && hs.bool("Look Vector"));
        double lw = hs.enabled ? hs.num("Width") * 0.01 : 0.012;
        boolean themeCol = hs.enabled && hs.bool("Theme Color");

        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player && mc.options.getPerspective().isFirstPerson()) continue;
            if (mc.player.distanceTo(e) > 48) continue;

            boolean match = (e instanceof PlayerEntity && players)
                    || (e instanceof MobEntity && mobs)
                    || (e instanceof ItemEntity && items);
            if (!match) continue;

            Vec3d delta = e.getLerpedPos(td).subtract(e.getEntityPos());
            Box box = e.getBoundingBox().offset(delta);

            int col = themeCol ? Effects.color(e instanceof PlayerEntity ? 0.15f : 0.55f, 235)
                    : (e instanceof PlayerEntity ? Effects.color(0.15f, 230)
                    : e instanceof ItemEntity ? 0xFFFBBF24
                    : 0xFFFB7185);
            wireBox(vc, box, lw, col);

            if (e instanceof PlayerEntity || e instanceof MobEntity) {
                double eyeY = box.minY + e.getStandingEyeHeight();
                wireRectY(vc, box.minX, box.maxX, box.minZ, box.maxZ, eyeY, 0.01, 0xFFEF4444);
                if (look) {
                    Vec3d eye = e.getLerpedPos(td).add(0, e.getStandingEyeHeight(), 0);
                    Vec3d end = eye.add(e.getRotationVec(td).multiply(2.0));
                    Effects.ribbon(vc, eye, end, 0.014, 0xFF38BDF8, 0xFF38BDF8);
                }
            }
        }
    }

    // ---------------- TNT & Creeper Fuse Radius ----------------
    private static void renderFuseRadius(VertexConsumer vc, float td) {
        Module m = ModuleManager.get("Fuse Radius");
        if (!m.enabled) return;

        for (Entity e : mc.world.getEntities()) {
            if (mc.player.distanceTo(e) > 48) continue;

            double radius = 0;
            double frac = 0; // 1.0 = только зажжён, 0.0 = вот-вот взорвётся
            if (e instanceof TntEntity tnt) {
                radius = 4.5;
                frac = Math.max(0, Math.min(1, tnt.getFuse() / 80.0));
            } else if (e instanceof CreeperEntity cr && (cr.getFuseSpeed() > 0 || cr.isIgnited())) {
                radius = cr.isCharged() ? 6.0 : 3.0;
                frac = Math.max(0, Math.min(1, 1.0 - cr.getLerpedFuseTime(td)));
            } else {
                continue;
            }

            Vec3d c = e.getLerpedPos(td).add(0, e.getHeight() * 0.5, 0);
            int col = frac < 0.3 ? 0xFFFF3B3B : frac < 0.6 ? 0xFFF59E0B : Effects.color(0.2f, 220);

            int seg = 40;
            // Горизонтальный круг радиуса поражения
            for (int i = 0; i < seg; i++) {
                double a1 = Math.PI * 2 * i / seg, a2 = Math.PI * 2 * (i + 1) / seg;
                Vec3d p1 = new Vec3d(c.x + Math.cos(a1) * radius, c.y, c.z + Math.sin(a1) * radius);
                Vec3d p2 = new Vec3d(c.x + Math.cos(a2) * radius, c.y, c.z + Math.sin(a2) * radius);
                Effects.ribbon(vc, p1, p2, 0.025, col, col);
            }
            // Два вертикальных меридиана сферы
            if (m.bool("Sphere")) {
                for (int k = 0; k < 2; k++) {
                    double yaw = k * Math.PI * 0.5;
                    double cx = Math.cos(yaw), cz = Math.sin(yaw);
                    for (int i = 0; i < seg; i++) {
                        double a1 = Math.PI * 2 * i / seg, a2 = Math.PI * 2 * (i + 1) / seg;
                        Vec3d p1 = new Vec3d(c.x + Math.cos(a1) * radius * cx, c.y + Math.sin(a1) * radius, c.z + Math.cos(a1) * radius * cz);
                        Vec3d p2 = new Vec3d(c.x + Math.cos(a2) * radius * cx, c.y + Math.sin(a2) * radius, c.z + Math.cos(a2) * radius * cz);
                        Effects.ribbon(vc, p1, p2, 0.015, (col & 0x00FFFFFF) | 0x88000000, (col & 0x00FFFFFF) | 0x88000000);
                    }
                }
            }
        }
    }

    // ---------------- Block Outline Customizer ----------------
    private static void renderBlockOutlineGlow(VertexConsumer glow) {
        Module m = ModuleManager.get("Block Outline");
        if (!m.enabled || !(mc.crosshairTarget instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = bhr.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) return;

        VoxelShape shape = state.getOutlineShape(mc.world, pos);
        if (shape.isEmpty()) return;

        double w = m.num("Width") * 0.008;
        int c1 = Effects.color(0.1f, 240);
        int c2 = Effects.color(0.65f, 240);

        shape.forEachEdge((x1, y1, z1, x2, y2, z2) -> {
            Vec3d a = new Vec3d(pos.getX() + x1, pos.getY() + y1, pos.getZ() + z1);
            Vec3d b = new Vec3d(pos.getX() + x2, pos.getY() + y2, pos.getZ() + z2);
            Effects.ribbon(glow, a, b, w, c1, c2);
        });
    }

    private static void renderBlockOutlineFill(VertexConsumer fill) {
        Module m = ModuleManager.get("Block Outline");
        if (!m.enabled || !m.bool("Fill") || !(mc.crosshairTarget instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = bhr.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) return;

        VoxelShape shape = state.getOutlineShape(mc.world, pos);
        if (shape.isEmpty()) return;

        int fcol = (Effects.color(0.1f, 240) & 0x00FFFFFF) | 0x33000000;
        shape.forEachBox((x1, y1, z1, x2, y2, z2) -> {
            Box box = new Box(pos.getX() + x1, pos.getY() + y1, pos.getZ() + z1,
                    pos.getX() + x2, pos.getY() + y2, pos.getZ() + z2).expand(0.002);
            fillBox(fill, box, fcol);
        });
    }

    // ---------------- Break Progress Overlay ----------------
    private static void renderBreakProgressGlow(VertexConsumer glow) {
        Module m = ModuleManager.get("Break Progress");
        if (!m.enabled || mc.interactionManager == null || !mc.interactionManager.isBreakingBlock()) return;

        InteractionAccessor acc = (InteractionAccessor) mc.interactionManager;
        BlockPos pos = acc.clickgui$getBreakingPos();
        float prog = Math.max(0f, Math.min(1f, acc.clickgui$getBreakingProgress()));
        if (pos == null || prog <= 0f) return;

        double half = 0.5 * prog;
        Vec3d center = Vec3d.ofCenter(pos);
        Box box = new Box(
                center.x - half, center.y - half, center.z - half,
                center.x + half, center.y + half, center.z + half
        );

        int col = Effects.color(prog, 235);
        wireBox(glow, box, 0.015, col);
    }

    private static void renderBreakProgressFill(VertexConsumer fill) {
        Module m = ModuleManager.get("Break Progress");
        if (!m.enabled || mc.interactionManager == null || !mc.interactionManager.isBreakingBlock()) return;

        InteractionAccessor acc = (InteractionAccessor) mc.interactionManager;
        BlockPos pos = acc.clickgui$getBreakingPos();
        float prog = Math.max(0f, Math.min(1f, acc.clickgui$getBreakingProgress()));
        if (pos == null || prog <= 0f) return;

        double half = 0.5 * prog;
        Vec3d center = Vec3d.ofCenter(pos);
        Box box = new Box(
                center.x - half, center.y - half, center.z - half,
                center.x + half, center.y + half, center.z + half
        );

        int col = Effects.color(prog, 235);
        int fcol = (col & 0x00FFFFFF) | 0x48000000;
        fillBox(fill, box, fcol);
    }

    // ---------------- Geometry Helpers ----------------
    static void wireBox(VertexConsumer vc, Box b, double w, int col) {
        Vec3d c000 = new Vec3d(b.minX, b.minY, b.minZ), c100 = new Vec3d(b.maxX, b.minY, b.minZ);
        Vec3d c101 = new Vec3d(b.maxX, b.minY, b.maxZ), c001 = new Vec3d(b.minX, b.minY, b.maxZ);
        Vec3d c010 = new Vec3d(b.minX, b.maxY, b.minZ), c110 = new Vec3d(b.maxX, b.maxY, b.minZ);
        Vec3d c111 = new Vec3d(b.maxX, b.maxY, b.maxZ), c011 = new Vec3d(b.minX, b.maxY, b.maxZ);

        Effects.ribbon(vc, c000, c100, w, col, col);
        Effects.ribbon(vc, c100, c101, w, col, col);
        Effects.ribbon(vc, c101, c001, w, col, col);
        Effects.ribbon(vc, c001, c000, w, col, col);

        Effects.ribbon(vc, c010, c110, w, col, col);
        Effects.ribbon(vc, c110, c111, w, col, col);
        Effects.ribbon(vc, c111, c011, w, col, col);
        Effects.ribbon(vc, c011, c010, w, col, col);

        Effects.ribbon(vc, c000, c010, w, col, col);
        Effects.ribbon(vc, c100, c110, w, col, col);
        Effects.ribbon(vc, c101, c111, w, col, col);
        Effects.ribbon(vc, c001, c011, w, col, col);
    }

    private static void wireRectY(VertexConsumer vc, double x0, double x1, double z0, double z1, double y, double w, int col) {
        Vec3d a = new Vec3d(x0, y, z0), b = new Vec3d(x1, y, z0), c = new Vec3d(x1, y, z1), d = new Vec3d(x0, y, z1);
        Effects.ribbon(vc, a, b, w, col, col);
        Effects.ribbon(vc, b, c, w, col, col);
        Effects.ribbon(vc, c, d, w, col, col);
        Effects.ribbon(vc, d, a, w, col, col);
    }

    static void fillBox(VertexConsumer vc, Box b, int col) {
        Vec3d c000 = new Vec3d(b.minX, b.minY, b.minZ), c100 = new Vec3d(b.maxX, b.minY, b.minZ);
        Vec3d c101 = new Vec3d(b.maxX, b.minY, b.maxZ), c001 = new Vec3d(b.minX, b.minY, b.maxZ);
        Vec3d c010 = new Vec3d(b.minX, b.maxY, b.minZ), c110 = new Vec3d(b.maxX, b.maxY, b.minZ);
        Vec3d c111 = new Vec3d(b.maxX, b.maxY, b.maxZ), c011 = new Vec3d(b.minX, b.maxY, b.maxZ);

        Effects.quad(vc, c000, c100, c101, c001, col, col, col, col);
        Effects.quad(vc, c010, c110, c111, c011, col, col, col, col);
        Effects.quad(vc, c000, c100, c110, c010, col, col, col, col);
        Effects.quad(vc, c001, c101, c111, c011, col, col, col, col);
        Effects.quad(vc, c000, c001, c011, c010, col, col, col, col);
        Effects.quad(vc, c100, c101, c111, c110, col, col, col, col);
    }
}
