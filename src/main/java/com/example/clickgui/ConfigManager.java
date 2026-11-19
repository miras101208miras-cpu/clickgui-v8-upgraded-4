package com.example.clickgui;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("clickgui/config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static boolean loaded, dirty;
    private static long dirtyAtMs, lastSavedAtMs;

    private ConfigManager() {}

    public static void markDirty() { dirty = true; dirtyAtMs = System.currentTimeMillis(); }
    public static void tickAutoSave() { if (dirty && System.currentTimeMillis() - dirtyAtMs >= 600L) save(); }
    public static boolean savedRecently() { return lastSavedAtMs > 0 && System.currentTimeMillis() - lastSavedAtMs < 1600L; }

    public static synchronized void save() {
        if (!loaded) return;
        dirty = false;
        try {
            JsonObject root = new JsonObject();
            root.addProperty("russian", ClickGuiScreen.isRussian());
            JsonObject mods = new JsonObject();
            for (Module m : ModuleManager.MODULES) {
                JsonObject mo = new JsonObject();
                mo.addProperty("enabled", m.enabled);
                mo.addProperty("key", m.key);
                JsonObject sets = new JsonObject();
                for (Setting s : m.settings) {
                    if (s instanceof Setting.Bool b) sets.addProperty(b.name, b.value);
                    else if (s instanceof Setting.Slider sl) sets.addProperty(sl.name, sl.value);
                    else if (s instanceof Setting.Mode md) sets.addProperty(md.name, md.get());
                    else if (s instanceof Setting.Multi mu) {
                        JsonArray arr = new JsonArray();
                        for (String opt : mu.selected) arr.add(opt);
                        sets.add(mu.name, arr);
                    }
                }
                mo.add("settings", sets);
                mods.add(m.name, mo);
            }
            root.add("modules", mods);
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(root));
            lastSavedAtMs = System.currentTimeMillis();
        } catch (Exception ex) {
            System.err.println("[ClickGUI] Config save failed: " + ex);
        }
    }

    public static synchronized void load() {
        loaded = true;
        if (!Files.exists(FILE)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
            if (root.has("russian") && root.get("russian").isJsonPrimitive()) {
                ClickGuiScreen.setRussian(root.get("russian").getAsBoolean());
            }
            if (!root.has("modules") || !root.get("modules").isJsonObject()) return;
            JsonObject mods = root.getAsJsonObject("modules");
            for (Module m : ModuleManager.MODULES) {
                if (!mods.has(m.name) || !mods.get(m.name).isJsonObject()) continue;
                JsonObject mo = mods.getAsJsonObject(m.name);
                if (mo.has("enabled")) { m.enabled = mo.get("enabled").getAsBoolean(); m.anim = m.enabled ? 1f : 0f; }
                if (mo.has("key")) m.key = mo.get("key").getAsInt();
                if (!mo.has("settings") || !mo.get("settings").isJsonObject()) continue;
                JsonObject sets = mo.getAsJsonObject("settings");
                for (Setting s : m.settings) {
                    if (!sets.has(s.name)) continue;
                    JsonElement el = sets.get(s.name);
                    if (s instanceof Setting.Bool b && el.isJsonPrimitive()) { b.value = el.getAsBoolean(); b.anim = b.value ? 1f : 0f; }
                    else if (s instanceof Setting.Slider sl && el.isJsonPrimitive()) {
                        double v = el.getAsDouble();
                        if (Double.isFinite(v)) sl.value = MathUtil.clamp(v, sl.min, sl.max);
                    } else if (s instanceof Setting.Mode md && el.isJsonPrimitive()) {
                        int idx = md.options.indexOf(el.getAsString());
                        if (idx >= 0) md.index = idx;
                    } else if (s instanceof Setting.Multi mu && el.isJsonArray()) {
                        mu.selected.clear();
                        for (JsonElement item : el.getAsJsonArray()) {
                            String opt = item.getAsString();
                            if (mu.options.contains(opt)) mu.selected.add(opt);
                        }
                    }
                }
            }
            HudCombat.markArrayListDirty();
        } catch (Exception ex) {
            System.err.println("[ClickGUI] Config load failed: " + ex);
        }
    }
}
