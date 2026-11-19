package com.example.clickgui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public final class Waypoints {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("clickgui/waypoints.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final int[] PALETTE = {
            0xFFFF4FCB, 0xFFFF4D4D, 0xFFFF9F0A, 0xFFFFD166,
            0xFF39E75F, 0xFF00D2FF, 0xFFA855F7, 0xFFFFFFFF
    };

    public static final class Waypoint {
        public String id;
        public String name;
        public double x, y, z;
        public int color;
        public String dim;
        public String server;
        public boolean enabled = true;
        public boolean death = false;

        public Waypoint() {}

        public Waypoint(String id, String name, double x, double y, double z, int color,
                        String dim, String server, boolean enabled, boolean death) {
            this.id = id; this.name = name; this.x = x; this.y = y; this.z = z;
            this.color = color; this.dim = dim; this.server = server;
            this.enabled = enabled; this.death = death;
            sanitize();
        }

        public void sanitize() {
            if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
            if (name == null || name.isBlank()) name = "Point";
            if (dim == null || dim.isBlank()) dim = "minecraft:overworld";
            if (server == null || server.isBlank()) server = "*";
            if (!Double.isFinite(x)) x = 0.0;
            if (!Double.isFinite(y)) y = 64.0;
            if (!Double.isFinite(z)) z = 0.0;
            color = (color & 0x00FFFFFF) | 0xFF000000;
        }

        public String name() { return name == null ? "Point" : name; }
        public String dim() { return dim == null ? "minecraft:overworld" : dim; }
    }

    public static final List<Waypoint> LIST = new CopyOnWriteArrayList<>();
    private static boolean loaded, dirty, wasDead;
    private static long dirtyAtMs;

    private Waypoints() {}

    public static void tick() {
        if (!loaded) { load(); loaded = true; }
        if (dirty && System.currentTimeMillis() - dirtyAtMs >= 500L) saveNow();
        Module m = ModuleManager.get("Waypoints");
        if (mc.player == null || mc.world == null) { wasDead = false; return; }

        boolean dead = mc.player.isDead() || mc.player.getHealth() <= 0f;
        if (m.enabled && m.bool("Auto Death") && dead && !wasDead) {
            String dim = currentDim(), srv = currentServerKey();
            double px = round1(mc.player.getX()), py = round1(mc.player.getY()), pz = round1(mc.player.getZ());
            if (Double.isFinite(px) && Double.isFinite(py) && Double.isFinite(pz)) {
                LIST.removeIf(w -> w != null && w.death && Objects.equals(w.server, srv) && Objects.equals(w.dim, dim));
                LIST.add(0, new Waypoint(UUID.randomUUID().toString(), "Death", px, py, pz,
                        0xFFFF4D4D, dim, srv, true, true));
                markDirty();
                Notifications.push("Death Waypoint", String.format(Locale.ROOT, "%.0f, %.0f, %.0f", px, py, pz), 0xFFFF4D4D);
            }
        }
        wasDead = dead;
    }

    public static boolean addHere(String rawName, int color) {
        if (mc.player == null || mc.world == null) return false;
        String dim = currentDim(), srv = currentServerKey();
        String clean = (rawName == null || rawName.trim().isEmpty()) ? "Point #" + (scopedList().size() + 1) : rawName.trim();
        double px = round1(mc.player.getX()), py = round1(mc.player.getY()), pz = round1(mc.player.getZ());
        if (!Double.isFinite(px) || !Double.isFinite(py) || !Double.isFinite(pz)) return false;
        LIST.add(new Waypoint(UUID.randomUUID().toString(), clean, px, py, pz, color, dim, srv, true, false));
        saveNow();
        Notifications.push("Waypoint Added", clean + " (" + (int) px + ", " + (int) py + ", " + (int) pz + ")", color);
        return true;
    }

    public static void toggle(Waypoint wp) { if (wp != null) { wp.enabled = !wp.enabled; markDirty(); } }
    public static void remove(Waypoint wp) { if (wp != null) { LIST.remove(wp); markDirty(); } }

    public static void cycleColor(Waypoint wp) {
        if (wp == null) return;
        int idx = 0;
        for (int i = 0; i < PALETTE.length; i++) {
            if ((PALETTE[i] & 0x00FFFFFF) == (wp.color & 0x00FFFFFF)) { idx = (i + 1) % PALETTE.length; break; }
        }
        wp.color = PALETTE[idx];
        markDirty();
    }

    public static void clearScope() {
        Module m = ModuleManager.get("Waypoints");
        if (!m.bool("Server Scope")) LIST.clear();
        else {
            String srv = currentServerKey();
            LIST.removeIf(w -> w == null || "*".equals(w.server) || srv.equals(w.server));
        }
        saveNow();
    }

    public static List<Waypoint> scopedList() {
        Module m = ModuleManager.get("Waypoints");
        boolean scoped = m.bool("Server Scope");
        String srv = currentServerKey();
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint wp : LIST) {
            if (wp == null) continue;
            wp.sanitize();
            if (!scoped || "*".equals(wp.server) || srv.equals(wp.server)) out.add(wp);
        }
        return out;
    }

    public static boolean isRenderVisible(Waypoint wp, Module m, String dim, String srv) {
        if (wp == null || !wp.enabled) return false;
        wp.sanitize();
        if (!wp.dim.equals(dim)) return false;
        return !m.bool("Server Scope") || "*".equals(wp.server) || srv.equals(wp.server);
    }

    public static String currentDim() {
        try { return mc.world == null ? "minecraft:overworld" : mc.world.getRegistryKey().getValue().toString(); }
        catch (Throwable ignored) { return "minecraft:overworld"; }
    }

    public static String shortDim(String dim) {
        if (dim == null) return "Overworld";
        if (dim.contains("nether")) return "Nether";
        if (dim.contains("end")) return "End";
        return "Overworld";
    }

    public static String currentServerKey() {
        try {
            ServerInfo entry = mc.getCurrentServerEntry();
            if (entry != null && entry.address != null && !entry.address.isBlank()) return "mp:" + entry.address.trim().toLowerCase(Locale.ROOT);
            var srv = mc.getServer();
            if (srv != null && srv.getSaveProperties() != null) {
                String lvl = srv.getSaveProperties().getLevelName();
                if (lvl != null && !lvl.isBlank()) return "sp:" + lvl.trim();
            }
        } catch (Throwable ignored) {}
        return "local";
    }

    public static void render(VertexConsumer vc, Matrix4f mat, double camX, double camY, double camZ) {
        if (vc == null || mat == null) return;
        try {
            Module m = ModuleManager.get("Waypoints");
            if (!m.enabled || !m.bool("Beacon") || mc.player == null || mc.world == null || LIST.isEmpty()) return;
            String dim = currentDim(), srv = currentServerKey();
            double maxDist = m.num("Max Dist");
            Vec3d playerPos = mc.player.getEntityPos();
            double t = System.currentTimeMillis() / 1000.0;
            for (Waypoint wp : LIST) {
                if (!isRenderVisible(wp, m, dim, srv)) continue;
                double dx = wp.x - playerPos.x, dy = wp.y - playerPos.y, dz = wp.z - playerPos.z;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (!Double.isFinite(dist) || dist > maxDist) continue;
                double proxFade = MathUtil.clamp((dist - 0.6) / 2.2, 0.2, 1.0);
                int rgb = wp.color & 0x00FFFFFF;
                int outerBot = ColorUtil.withAlpha(rgb, (int) (160 * proxFade));
                int innerBot = ColorUtil.withAlpha(rgb, (int) (235 * proxFade));
                RenderUtil.ribbon3d(vc, mat, camX, camY, camZ, wp.x, wp.y, wp.z, wp.x, wp.y + 72.0, wp.z, 0.16, outerBot, rgb);
                RenderUtil.ribbon3d(vc, mat, camX, camY, camZ, wp.x, wp.y, wp.z, wp.x, wp.y + 44.0, wp.z, 0.055, innerBot, rgb);
                double radius = 0.58 + 0.07 * Math.sin(t * 2.4);
                int ringCol = ColorUtil.withAlpha(rgb, (int) (220 * proxFade));
                for (int i = 0; i < 24; i++) {
                    double a1 = Math.PI * 2.0 * i / 24, a2 = Math.PI * 2.0 * (i + 1) / 24;
                    RenderUtil.ribbon3d(vc, mat, camX, camY, camZ,
                            wp.x + Math.cos(a1) * radius, wp.y + 0.06, wp.z + Math.sin(a1) * radius,
                            wp.x + Math.cos(a2) * radius, wp.y + 0.06, wp.z + Math.sin(a2) * radius, 0.024, ringCol, ringCol);
                }
                double spin = t * 1.4, dr = 0.34;
                for (int i = 0; i < 4; i++) {
                    double a1 = spin + Math.PI * 0.5 * i, a2 = spin + Math.PI * 0.5 * (i + 1);
                    RenderUtil.ribbon3d(vc, mat, camX, camY, camZ,
                            wp.x + Math.cos(a1) * dr, wp.y + 0.14, wp.z + Math.sin(a1) * dr,
                            wp.x + Math.cos(a2) * dr, wp.y + 0.14, wp.z + Math.sin(a2) * dr, 0.02, innerBot, innerBot);
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void render(VertexConsumer vc) {}
    public static void markDirty() { dirty = true; dirtyAtMs = System.currentTimeMillis(); }

    public static synchronized void saveNow() {
        dirty = false;
        try { Files.createDirectories(FILE.getParent()); Files.writeString(FILE, GSON.toJson(LIST)); }
        catch (Exception ignored) {}
    }

    private static synchronized void load() {
        if (!Files.exists(FILE)) return;
        try {
            List<Waypoint> read = GSON.fromJson(Files.readString(FILE), new TypeToken<List<Waypoint>>() {}.getType());
            if (read != null) {
                LIST.clear();
                for (Waypoint wp : read) { if (wp != null) { wp.sanitize(); LIST.add(wp); } }
            }
        } catch (Exception ignored) {}
    }

    private static double round1(double v) { return Double.isFinite(v) ? Math.round(v * 10.0) / 10.0 : 0.0; }
}
