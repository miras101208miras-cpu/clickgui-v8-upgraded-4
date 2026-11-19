package com.example.clickgui;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * PvP Save / Ловушки: обнаруживает активацию трапок / появление структур блоков
 * (обсидиан, плачущий обсидиан, паутина, якоря), подсвечивает зону 5x5 и запускает таймер.
 */
public final class PvpSave {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public record TrapZone(BlockPos center, long expiresAt, String label) {}

    public static final List<TrapZone> ZONES = new ArrayList<>();
    private static final Set<Long> knownBlocks = new HashSet<>();
    private static int tickCounter;

    public static void init() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            Module m = ModuleManager.get("PvP Save");
            if (world.isClient() && m.enabled && mc.player != null && player == mc.player) {
                Item it = player.getStackInHand(hand).getItem();
                if (it == Items.NETHERITE_SCRAP || it == Items.TRIPWIRE_HOOK || it == Items.DRIED_KELP
                        || it == Items.OBSIDIAN || it == Items.COBWEB || it == Items.RESPAWN_ANCHOR) {
                    addOrRefresh(player.getBlockPos(), labelForItem(it), (long) (m.num("Time") * 1000L));
                }
            }
            return ActionResult.PASS;
        });
    }

    public static void tick() {
        Module m = ModuleManager.get("PvP Save");
        long now = System.currentTimeMillis();
        ZONES.removeIf(z -> now >= z.expiresAt());

        if (!m.enabled || mc.player == null || mc.world == null) {
            knownBlocks.clear();
            ZONES.clear();
            return;
        }

        if (++tickCounter % 4 != 0) return;

        int r = Math.min(14, Math.max(4, (int) m.num("Radius")));
        long life = (long) (m.num("Time") * 1000L);
        BlockPos base = mc.player.getBlockPos();
        BlockPos.Mutable mut = new BlockPos.Mutable();
        Set<Long> current = new HashSet<>();
        boolean firstScan = knownBlocks.isEmpty();

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -2; dy <= 5; dy++) {
                    mut.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    Block b = mc.world.getBlockState(mut).getBlock();
                    String lbl = trapLabel(b);
                    if (lbl != null) {
                        long key = mut.asLong();
                        current.add(key);
                        if (!firstScan && !knownBlocks.contains(key)) {
                            addOrRefresh(mut.toImmutable(), lbl, life);
                        }
                    }
                }
            }
        }
        knownBlocks.clear();
        knownBlocks.addAll(current);
    }

    private static String trapLabel(Block b) {
        if (b == Blocks.OBSIDIAN || b == Blocks.CRYING_OBSIDIAN) return "TRAP";
        if (b == Blocks.COBWEB) return "WEB";
        if (b == Blocks.RESPAWN_ANCHOR) return "ANCHOR";
        return null;
    }

    private static String labelForItem(Item it) {
        if (it == Items.COBWEB) return "WEB";
        if (it == Items.RESPAWN_ANCHOR) return "ANCHOR";
        return "TRAP";
    }

    private static void addOrRefresh(BlockPos pos, String label, long lifeMs) {
        long exp = System.currentTimeMillis() + lifeMs;
        for (int i = 0; i < ZONES.size(); i++) {
            TrapZone z = ZONES.get(i);
            if (z.center().getSquaredDistance(pos) <= 12.0) {
                ZONES.set(i, new TrapZone(z.center(), Math.max(z.expiresAt(), exp), label));
                return;
            }
        }
        ZONES.add(new TrapZone(pos, exp, label));
    }

    public static void render(VertexConsumer vc) {
        if (!ModuleManager.get("PvP Save").enabled || ZONES.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (TrapZone z : ZONES) {
            long rem = z.expiresAt() - now;
            if (rem <= 0) continue;
            int col = rem < 3000 ? 0xFFFF5555 : Effects.color(0.25f, 220);
            int fillCol = (col & 0x00FFFFFF) | 0x26000000;

            // 5x5x5 зона вокруг центра ловушки
            double x0 = z.center().getX() - 2.0, x1 = z.center().getX() + 3.0;
            double y0 = z.center().getY() - 1.0, y1 = z.center().getY() + 4.0;
            double z0 = z.center().getZ() - 2.0, z1 = z.center().getZ() + 3.0;

            Vec3d c000 = new Vec3d(x0, y0, z0), c100 = new Vec3d(x1, y0, z0);
            Vec3d c101 = new Vec3d(x1, y0, z1), c001 = new Vec3d(x0, y0, z1);
            Vec3d c010 = new Vec3d(x0, y1, z0), c110 = new Vec3d(x1, y1, z0);
            Vec3d c111 = new Vec3d(x1, y1, z1), c011 = new Vec3d(x0, y1, z1);

            // Полупрозрачный пол 5x5
            Effects.quad(vc, c000, c100, c101, c001, fillCol, fillCol, fillCol, fillCol);

            // 12 рёбер куба 5x5x5
            double w = 0.025;
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
    }
}
