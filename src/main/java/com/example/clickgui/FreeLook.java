package com.example.clickgui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;

/**
 * Free Look: отвязывает углы поворота камеры (cameraYaw, cameraPitch)
 * от углов поворота тела игрока (player.yaw, player.pitch).
 */
public final class FreeLook {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public static float cameraYaw;
    public static float cameraPitch;
    private static boolean active;
    private static Perspective savedPerspective;

    public static boolean isActive() {
        return active && mc.player != null && ModuleManager.get("Free Look").enabled;
    }

    public static void tick() {
        Module m = ModuleManager.get("Free Look");
        if (mc.player == null || mc.world == null) {
            disable();
            return;
        }
        if (m.enabled && !active) {
            active = true;
            cameraYaw = mc.player.getYaw();
            cameraPitch = mc.player.getPitch();
            if (m.bool("Auto 3rd Person") && mc.options.getPerspective() == Perspective.FIRST_PERSON) {
                savedPerspective = Perspective.FIRST_PERSON;
                mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            }
        } else if (!m.enabled && active) {
            disable();
        }
    }

    private static void disable() {
        active = false;
        if (savedPerspective != null) {
            mc.options.setPerspective(savedPerspective);
            savedPerspective = null;
        }
    }

    public static void onMouseMove(double dx, double dy) {
        Module m = ModuleManager.get("Free Look");
        double sign = m.bool("Invert Pitch") ? -1.0 : 1.0;
        cameraYaw += (float) (dx * 0.15);
        cameraPitch = (float) Math.max(-90.0, Math.min(90.0, cameraPitch + dy * 0.15 * sign));
    }
}
