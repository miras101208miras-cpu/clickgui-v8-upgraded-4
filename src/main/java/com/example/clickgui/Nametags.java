package com.example.clickgui;

import com.example.clickgui.mixin.InteractionAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.TntEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.SpectralArrowEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/**
 * 3D -> 2D проекция на HUD:
 * Self Nametag, Arrow Nametag, Nametags Pro (броня/чары/пинг/HP),
 * Health Bar 3D (со шлейфом урона), Potion Ring, Pearl Tracker,
 * Fuse Radius таймеры, Break Progress % и Waypoints 3D.
 */
public final class Nametags {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Map<Integer, Double> hpTrail = new HashMap<>();

    public static void render(DrawContext ctx, float td) {
        if (mc.player == null || mc.world == null || !Effects.camReady) return;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();

        // 1. Self Nametag
        Module self = ModuleManager.get("Self Nametag");
        if (self.enabled && !mc.options.getPerspective().isFirstPerson()) {
            Vec3d p = mc.player.getLerpedPos(td).add(0, mc.player.getHeight() + 0.5, 0);
            label(ctx, p, mc.player.getName().getString(), Theme.accent());
        }

        // 2. PvP Save 5x5 таймеры
        if (ModuleManager.get("PvP Save").enabled && !PvpSave.ZONES.isEmpty()) {
            long now = System.currentTimeMillis();
            for (PvpSave.TrapZone z : PvpSave.ZONES) {
                long rem = z.expiresAt() - now;
                if (rem <= 0) continue;
                Vec3d top = new Vec3d(z.center().getX() + 0.5, z.center().getY() + 4.35, z.center().getZ() + 0.5);
                String txt = String.format(Locale.ROOT, "%s  %.1fs", z.label(), rem / 1000.0);
                label(ctx, top, txt, rem < 3000 ? 0xFFFF5555 : Theme.accent());
            }
        }

        // 3. Pearl Tracker метки
        Module pearlMod = ModuleManager.get("Pearl Tracker");
        if (pearlMod.enabled && !PearlTracker.ACTIVE.isEmpty()) {
            for (PearlTracker.TrackedPearl tp : PearlTracker.ACTIVE) {
                double dist = mc.player.getEntityPos().distanceTo(tp.landing());
                String s = pearlMod.bool("Air Time")
                        ? String.format(Locale.ROOT, "%s · %.1fs · %.0fm", tp.owner(), tp.airSec(), dist)
                        : String.format(Locale.ROOT, "%s · %.0fm", tp.owner(), dist);
                label(ctx, tp.pos().add(0, 0.35, 0), s, Theme.accent());
                label(ctx, tp.landing().add(0, 0.25, 0), String.format(Locale.ROOT, "Impact · %.1fm", dist), 0xFFFFFFFF);
            }
        }

        // 4. Waypoints 3D метки
        Module wpMod = ModuleManager.get("Waypoints");
        if (wpMod.enabled && wpMod.bool("2D Tags") && !Waypoints.LIST.isEmpty()) {
            String dim = Waypoints.currentDim(), srv = Waypoints.currentServerKey();
            double maxDist = wpMod.num("Max Dist");
            Vec3d playerPos = mc.player.getEntityPos();
            for (Waypoints.Waypoint wp : Waypoints.LIST) {
                if (!Waypoints.isRenderVisible(wp, wpMod, dim, srv)) continue;
                double dx = wp.x - playerPos.x, dy = wp.y - playerPos.y, dz = wp.z - playerPos.z;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (!Double.isFinite(dist) || dist > maxDist) continue;
                String tag = (wp.death ? "\u2620 " : "") + String.format(Locale.ROOT, "%s · %.0fm", wp.name(), dist);
                label(ctx, new Vec3d(wp.x, wp.y + 2.2, wp.z), tag, wp.color);
            }
        }

        // 5. Break Progress %
        Module bpMod = ModuleManager.get("Break Progress");
        if (bpMod.enabled && bpMod.bool("Percent") && mc.interactionManager != null && mc.interactionManager.isBreakingBlock()) {
            InteractionAccessor acc = (InteractionAccessor) mc.interactionManager;
            BlockPos bp = acc.clickgui$getBreakingPos();
            float prog = acc.clickgui$getBreakingProgress();
            if (bp != null && prog > 0f) {
                int pct = Math.min(100, Math.round(prog * 100f));
                label(ctx, Vec3d.ofCenter(bp), pct + "%", Theme.accent());
            }
        }

        // 6. Сущности (Arrow Nametag, Fuse Radius таймеры, Nametags Pro, Health Bar 3D, Potion Ring)
        Module ar = ModuleManager.get("Arrow Nametag");
        Module fuseMod = ModuleManager.get("Fuse Radius");
        Module ntPro = ModuleManager.get("Nametags Pro");
        Module hbMod = ModuleManager.get("Health Bar 3D");
        Module potMod = ModuleManager.get("Potion Ring");

        Set<Integer> seenLiving = new HashSet<>();

        for (Entity e : mc.world.getEntities()) {
            double dist = mc.player.distanceTo(e);

            // Arrow Nametag
            if (ar.enabled && e instanceof PersistentProjectileEntity && e.getVelocity().lengthSquared() >= 0.01 && dist <= 48) {
                Vec3d p = e.getLerpedPos(td).add(0, 0.35, 0);
                if (visible(p)) {
                    String type = e instanceof TridentEntity ? "Trident" : e instanceof SpectralArrowEntity ? "Spectral" : "Arrow";
                    StringBuilder sb = new StringBuilder();
                    if (ar.bool("Type")) sb.append(type);
                    if (ar.bool("Distance")) {
                        if (sb.length() > 0) sb.append("  ");
                        sb.append(String.format(Locale.ROOT, "%.1fm", dist));
                    }
                    if (sb.length() > 0) label(ctx, p, sb.toString(), 0xFFFFFFFF);
                }
            }

            // TNT & Creeper Fuse таймер
            if (fuseMod.enabled && fuseMod.bool("Timer") && dist <= 48) {
                if (e instanceof TntEntity tnt) {
                    label(ctx, e.getLerpedPos(td).add(0, 1.1, 0),
                            String.format(Locale.ROOT, "TNT  %.1fs", tnt.getFuse() / 20.0), 0xFFFF5555);
                } else if (e instanceof CreeperEntity cr && (cr.getFuseSpeed() > 0 || cr.isIgnited())) {
                    double rem = Math.max(0, (1.0 - cr.getLerpedFuseTime(td)) * 1.5);
                    label(ctx, e.getLerpedPos(td).add(0, cr.getHeight() + 0.4, 0),
                            String.format(Locale.ROOT, "BOOM  %.1fs", rem), 0xFFFF5555);
                }
            }

            if (!(e instanceof LivingEntity le) || !le.isAlive() || dist > 64) continue;
            seenLiving.add(le.getId());

            boolean isSelf = le == mc.player;
            if (isSelf && mc.options.getPerspective().isFirstPerson()) continue;

            // Health Bar 3D (со шлейфом урона и абсорбцией)
            if (hbMod.enabled && !isSelf) {
                boolean match = (le instanceof PlayerEntity && hbMod.bool("Players"))
                        || (le instanceof MobEntity && hbMod.bool("Mobs"));
                if (match && dist <= 36) {
                    renderHealthBar(ctx, le, td, sw, sh, hbMod.bool("Damage Trail"));
                }
            }

            // Potion Ring над головой
            if (potMod.enabled) {
                boolean show = (isSelf && potMod.bool("Self")) || (!isSelf && le instanceof PlayerEntity && potMod.bool("Others"));
                if (show && !le.getStatusEffects().isEmpty()) {
                    renderPotionRing(ctx, le, td, sw, sh);
                }
            }

            // Nametags Pro (игроки)
            if (ntPro.enabled && le instanceof PlayerEntity pe && !isSelf) {
                renderNametagPro(ctx, pe, td, sw, sh, ntPro);
            }
        }
        hpTrail.keySet().retainAll(seenLiving);
    }

    // ---------------- Nametags Pro ----------------
    private static void renderNametagPro(DrawContext ctx, PlayerEntity pe, float td, int sw, int sh, Module m) {
        Vec3d world = pe.getLerpedPos(td).add(0, pe.getHeight() + 0.55, 0);
        float[] sp = project(world, sw, sh);
        if (sp == null) return;

        double dist = world.distanceTo(Effects.camPos());
        float scale = (float) Math.max(0.55, Math.min(1.05, 7.5 / Math.max(dist, 0.5)));

        StringBuilder header = new StringBuilder(pe.getName().getString());
        if (m.bool("Ping") && mc.getNetworkHandler() != null) {
            PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(pe.getUuid());
            if (entry != null) header.append("  ").append(entry.getLatency()).append("ms");
        }
        if (m.bool("Health")) {
            double hp = pe.getHealth() + pe.getAbsorptionAmount();
            header.append(String.format(Locale.ROOT, "  %.1f", hp));
        }

        String text = header.toString();
        int tw = Hud.tw(text);
        int boxW = Math.max(tw + 12, 68);

        var mat = ctx.getMatrices();
        mat.pushMatrix();
        try {
        mat.translate(sp[0], sp[1]);
        mat.scale(scale, scale);

        ClickGuiScreen.roundRect(ctx, -boxW / 2, -14, boxW, 13, 3, 0xBB101620);
        Hud.txt(ctx, text, -tw / 2, -11, 0xFFFFFFFF);

        // Полоска HP прямо под плашкой
        if (m.bool("Health")) {
            double frac = Math.max(0, Math.min(1, pe.getHealth() / Math.max(1f, pe.getMaxHealth())));
            int bw = boxW - 4;
            int fillW = Math.max(1, (int) Math.round(bw * frac));
            int hpCol = frac > 0.55 ? 0xFF4ADE80 : frac > 0.25 ? 0xFFFACC15 : 0xFFEF4444;
            ctx.fill(-bw / 2, -2, bw / 2, -1, 0x55FFFFFF);
            ctx.fill(-bw / 2, -2, -bw / 2 + fillW, -1, hpCol);
        }

        // Броня и чары над ником
        if (m.bool("Armor")) {
            EquipmentSlot[] slots = {EquipmentSlot.MAINHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND};
            List<ItemStack> items = new ArrayList<>();
            for (EquipmentSlot s : slots) {
                ItemStack st = pe.getEquippedStack(s);
                if (!st.isEmpty()) items.add(st);
            }
            if (!items.isEmpty()) {
                int step = 14;
                int startX = -(items.size() * step) / 2;
                for (int i = 0; i < items.size(); i++) {
                    ItemStack st = items.get(i);
                    int ix = startX + i * step;
                    mat.pushMatrix();
                    mat.translate(ix, -30);
                    mat.scale(0.75f, 0.75f);
                    ctx.drawItem(st, 0, 0);
                    mat.popMatrix();

                    if (m.bool("Enchants")) {
                        String ench = shortEnchants(st);
                        if (!ench.isEmpty()) {
                            mat.pushMatrix();
                            mat.translate(ix + 6, -37);
                            mat.scale(0.65f, 0.65f);
                            int ew = Hud.tw(ench);
                            Hud.txt(ctx, ench, -ew / 2, 0, Theme.accent());
                            mat.popMatrix();
                        }
                    }
                }
            }
        }

        } finally {
            mat.popMatrix();
        }
    }

    private static String shortEnchants(ItemStack st) {
        ItemEnchantmentsComponent comp = EnchantmentHelper.getEnchantments(st);
        if (comp.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (RegistryEntry<Enchantment> entry : comp.getEnchantments()) {
            String id = entry.getIdAsString();
            int lvl = comp.getLevel(entry);
            String code = switch (id) {
                case "minecraft:protection" -> "P";
                case "minecraft:sharpness" -> "S";
                case "minecraft:unbreaking" -> "U";
                case "minecraft:mending" -> "M";
                case "minecraft:feather_falling" -> "FF";
                case "minecraft:power" -> "Pw";
                case "minecraft:density" -> "Dn";
                case "minecraft:breach" -> "Br";
                default -> "";
            };
            if (!code.isEmpty()) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(code).append(code.equals("M") ? "" : lvl);
                if (sb.length() >= 8) break;
            }
        }
        return sb.toString();
    }

    // ---------------- Health Bar 3D ----------------
    private static void renderHealthBar(DrawContext ctx, LivingEntity le, float td, int sw, int sh, boolean trail) {
        double yOff = le instanceof PlayerEntity && ModuleManager.get("Nametags Pro").enabled
                ? le.getHeight() + 0.25
                : le.getHeight() + 0.38;
        Vec3d world = le.getLerpedPos(td).add(0, yOff, 0);
        float[] sp = project(world, sw, sh);
        if (sp == null) return;

        double maxHp = Math.max(1.0, le.getMaxHealth());
        double cur = Math.max(0.0, Math.min(1.0, le.getHealth() / maxHp));
        double abs = Math.max(0.0, Math.min(0.6, le.getAbsorptionAmount() / maxHp));
        double tr = hpTrail.getOrDefault(le.getId(), cur);
        if (tr < cur) tr = cur;
        else tr += (cur - tr) * 0.08;
        hpTrail.put(le.getId(), tr);

        int bw = 42, bh = 4;
        var mat = ctx.getMatrices();
        mat.pushMatrix();
        try {
        mat.translate(sp[0], sp[1]);
        ClickGuiScreen.roundRect(ctx, -bw / 2 - 1, -1, bw + 2, bh + 2, 2, 0xAA101620);
        if (trail && tr > cur) {
            ctx.fill(-bw / 2, 0, -bw / 2 + (int) (bw * tr), bh, 0xCCFFFFFF);
        }
        int col = cur > 0.55 ? 0xFF4ADE80 : cur > 0.25 ? 0xFFFACC15 : 0xFFEF4444;
        ctx.fill(-bw / 2, 0, -bw / 2 + Math.max(1, (int) (bw * cur)), bh, col);
        if (abs > 0.01) {
            ctx.fill(-bw / 2, -2, -bw / 2 + (int) (bw * Math.min(1.0, abs)), -1, 0xFFFBBF24);
        }
        } finally {
            mat.popMatrix();
        }
    }

    // ---------------- Potion Ring ----------------
    private static void renderPotionRing(DrawContext ctx, LivingEntity le, float td, int sw, int sh) {
        Vec3d world = le.getLerpedPos(td).add(0, le.getHeight() + 1.05, 0);
        float[] sp = project(world, sw, sh);
        if (sp == null) return;

        List<String> badges = new ArrayList<>();
        for (StatusEffectInstance eff : le.getStatusEffects()) {
            String id = eff.getEffectType().getIdAsString();
            String shortName = switch (id) {
                case "minecraft:strength" -> "STR";
                case "minecraft:speed" -> "SPD";
                case "minecraft:fire_resistance" -> "FIRE";
                case "minecraft:regeneration" -> "REG";
                case "minecraft:resistance" -> "RES";
                case "minecraft:invisibility" -> "INV";
                case "minecraft:absorption" -> "ABS";
                default -> { String clean = id.replace("minecraft:", "").toUpperCase(Locale.ROOT); yield clean.substring(0, Math.min(3, clean.length())); }
            };
            int lvl = eff.getAmplifier() + 1;
            badges.add(lvl > 1 ? shortName + " " + lvl : shortName);
            if (badges.size() >= 4) break;
        }
        if (badges.isEmpty()) return;
        label(ctx, world, String.join(" · ", badges), Theme.accent());
    }

    private static boolean visible(Vec3d p) {
        return mc.world.raycast(new RaycastContext(Effects.camPos(), p, RaycastContext.ShapeType.VISUAL,
                RaycastContext.FluidHandling.NONE, mc.player)).getType() == HitResult.Type.MISS;
    }

    private static void label(DrawContext ctx, Vec3d world, String s, int col) {
        float[] sp = project(world, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight());
        if (sp == null) return;
        double dist = world.distanceTo(Effects.camPos());
        float scale = (float) Math.max(0.5, Math.min(1.0, 6.0 / Math.max(dist, 0.1)));
        int w = Hud.tw(s);
        var m = ctx.getMatrices();
        m.pushMatrix();
        try {
            m.translate(sp[0], sp[1]);
            m.scale(scale, scale);
            ClickGuiScreen.roundRect(ctx, -w / 2 - 4, -6, w + 8, 12, 3, 0x99101620);
            Hud.txt(ctx, s, -w / 2, -4, col);
        } finally {
            m.popMatrix();
        }
    }

    private static final float[] PROJ_TMP = new float[2];
    static float[] project(Vec3d p, int sw, int sh) {
        if (p == null) return null;
        return MathUtil.worldToScreen(p.x, p.y, p.z, sw, sh, PROJ_TMP) ? PROJ_TMP : null;
    }
}
