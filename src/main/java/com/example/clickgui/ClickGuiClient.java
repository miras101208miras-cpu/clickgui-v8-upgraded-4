package com.example.clickgui;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class ClickGuiClient implements ClientModInitializer {
    private final Set<Integer> held = new HashSet<>();

    @Override
    public void onInitializeClient() {
        ConfigManager.load();
        Effects.init();
        Hud.init();
        Tweaks.init();
        SkyBox.init();
        PvpSave.init();
        ExtraQoL.init();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ConfigManager.save();
            Waypoints.saveNow();
        }));

        KeyBinding.Category cat = KeyBinding.Category.create(Identifier.of("clickgui", "main"));
        KeyBinding open = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.clickgui.open", GLFW.GLFW_KEY_RIGHT_SHIFT, cat));
        KeyBinding quickWp = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.clickgui.waypoint", GLFW.GLFW_KEY_B, cat));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            FreeLook.tick();
            FakePlayer.tick();
            MaceHelper.tick();
            PvpSave.tick();
            Waypoints.tick();
            PickupFeed.tick();
            HudWorld.tick();
            ExtraVisuals.tick();
            ExtraQoL.tick();
            ConfigManager.tickAutoSave();

            while (open.wasPressed()) client.setScreen(new ClickGuiScreen());
            while (quickWp.wasPressed()) {
                if (client.currentScreen == null && client.player != null) {
                    Waypoints.addHere(null, Theme.primary());
                }
            }
            if (client.currentScreen != null) return;
            Set<Integer> nextHeld = new HashSet<>();
            for (Module m : ModuleManager.MODULES) {
                if (m.key < 0) continue;
                boolean down = InputUtil.isKeyPressed(client.getWindow(), m.key);
                if (down) {
                    nextHeld.add(m.key);
                    if (!held.contains(m.key)) m.toggle();
                }
            }
            held.clear();
            held.addAll(nextHeld);
        });
    }
}
