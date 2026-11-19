package com.example.clickgui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Кэшированные метрики и трекеры без аллокаций на рендер-кадре:
 * - Server TPS (кольцевой буфер по WorldTimeUpdateS2CPacket)
 * - Ping (из PlayerListEntry)
 * - Speedometer (b/s, горизонталь или 3D на элитрах)
 * - Kill / Combo Counter (серия ударов и K/D)
 * - Totem Count (пересчитывается ТОЛЬКО при изменении PlayerInventory.getChangeCount())
 * - Biome Cache (обновляется раз в 10 тиков / при смене блока)
 */
public final class ServerMetrics {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    // TPS ring buffer
    private static final float[] tpsSamples = new float[10];
    private static int tpsIdx, tpsCount;
    private static long lastPacketMs = -1;
    private static long lastWorldTime = -1;
    private static float avgTps = 20.0f;

    // Speedometer
    private static double prevX, prevY, prevZ;
    private static boolean hasPrevPos;
    private static double smoothBps;

    // Kill / Combo
    private static int combo, maxCombo, kills, deaths;
    private static int lastHurtTime;
    private static long lastHitMs;
    private static boolean wasDead;

    // Totem O(1) frame cache
    private static int cachedInvChange = -1;
    private static int cachedTotems;

    // Biome cache
    private static BlockPos lastBiomePos;
    private static String cachedBiome = "Plains";
    private static String cachedDim = "Overworld";
    private static int biomeTickCooldown;

    // Low HP notification cooldown
    private static long lastLowHpAlertMs;

    public static void onTimePacket(long worldTime) {
        long now = System.currentTimeMillis();
        if (lastPacketMs > 0 && worldTime > lastWorldTime) {
            long dtMs = now - lastPacketMs;
            long dTicks = worldTime - lastWorldTime;
            if (dtMs > 50 && dtMs < 15000 && dTicks > 0 && dTicks < 400) {
                float sample = Math.max(0f, Math.min(20f, (float) (dTicks / (dtMs / 1000.0))));
                tpsSamples[tpsIdx % tpsSamples.length] = sample;
                tpsIdx++;
                if (tpsCount < tpsSamples.length) tpsCount++;
                float sum = 0;
                for (int i = 0; i < tpsCount; i++) sum += tpsSamples[i];
                avgTps = sum / tpsCount;
            }
        }
        lastPacketMs = now;
        lastWorldTime = worldTime;
    }

    public static void tick() {
        if (mc.player == null || mc.world == null) {
            hasPrevPos = false;
            lastPacketMs = -1;
            tpsCount = 0;
            avgTps = 20.0f;
            cachedInvChange = -1;
            return;
        }

        // 1. Speedometer
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        if (hasPrevPos) {
            double dx = x - prevX, dy = y - prevY, dz = z - prevZ;
            boolean use3d = mc.player.isGliding() || ModuleManager.get("Speedometer").bool("3D / Elytra");
            double raw = (use3d ? Math.sqrt(dx * dx + dy * dy + dz * dz) : Math.hypot(dx, dz)) * 20.0;
            if (raw < 120.0) { // фильтр от телепортаций
                smoothBps = smoothBps * 0.72 + raw * 0.28;
            }
        }
        prevX = x; prevY = y; prevZ = z;
        hasPrevPos = true;

        // 2. Combo reset on taking damage or timeout + death counter
        int ht = mc.player.hurtTime;
        if (ht > lastHurtTime && ht > 0) combo = 0;
        lastHurtTime = ht;
        if (combo > 0 && System.currentTimeMillis() - lastHitMs > 4500) combo = 0;

        boolean dead = mc.player.isDead() || mc.player.getHealth() <= 0f;
        if (dead && !wasDead) { deaths++; combo = 0; }
        wasDead = dead;

        // Low HP notification (< 6 HP)
        float hp = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        long now = System.currentTimeMillis();
        if (hp > 0 && hp < 6.0f && now - lastLowHpAlertMs > 5000) {
            lastLowHpAlertMs = now;
            Notifications.push("LOW HEALTH", String.format(java.util.Locale.ROOT, "%.1f HP!", hp), 0xFFFF5555);
        }

        // 3. Cached Totem Counter (O(1) unless inventory changed)
        PlayerInventory inv = mc.player.getInventory();
        int cc = inv.getChangeCount();
        if (cc != cachedInvChange) {
            cachedInvChange = cc;
            int cnt = 0;
            for (ItemStack st : inv.getMainStacks()) {
                if (st.isOf(Items.TOTEM_OF_UNDYING)) cnt += st.getCount();
            }
            if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
                cnt += mc.player.getOffHandStack().getCount();
            }
            cachedTotems = cnt;
        }

        // 4. Cached Biome + Dimension lookup (max 2x/sec)
        if (biomeTickCooldown-- <= 0) {
            biomeTickCooldown = 10;
            BlockPos bp = mc.player.getBlockPos();
            if (lastBiomePos == null || !lastBiomePos.equals(bp)) {
                lastBiomePos = bp;
                cachedBiome = mc.world.getBiome(bp).getKey()
                        .map(k -> formatId(k.getValue().getPath()))
                        .orElse("Unknown");
                var dim = mc.world.getRegistryKey();
                cachedDim = dim == World.NETHER ? "Nether" : dim == World.END ? "End" : "Overworld";
            }
        }
    }

    public static void onAttack(LivingEntity target) {
        if (target == FakePlayer.entity()) return;
        long now = System.currentTimeMillis();
        if (now - lastHitMs > 180) {
            combo++;
            if (combo > maxCombo) maxCombo = combo;
            lastHitMs = now;
        }
    }

    public static void onKill(LivingEntity target) {
        if (target == FakePlayer.entity()) return;
        kills++;
    }

    public static float tps() { return avgTps; }
    public static int ping() {
        if (mc.player == null || mc.getNetworkHandler() == null) return 0;
        PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return e == null ? 0 : e.getLatency();
    }
    public static double bps() { return smoothBps; }
    public static int combo() { return combo; }
    public static int kills() { return kills; }
    public static int deaths() { return deaths; }
    public static int totems() { return cachedTotems; }
    public static String biome() { return cachedBiome; }
    public static String dimension() { return cachedDim; }

    private static String formatId(String path) {
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }
}
