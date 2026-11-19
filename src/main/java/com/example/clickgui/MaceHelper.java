package com.example.clickgui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundEvents;

public final class MaceHelper {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public static boolean ready;
    public static double fallDistance;
    private static boolean alertedThisFall;

    public static void tick() {
        Module m = ModuleManager.get("Mace Helper");
        if (!m.enabled || mc.player == null || mc.world == null) {
            ready = false; fallDistance = 0; alertedThisFall = false; return;
        }
        PlayerInventory inv = mc.player.getInventory();
        boolean hasMace = inv.getSelectedStack().isOf(Items.MACE) || mc.player.getOffHandStack().isOf(Items.MACE);
        if (!hasMace) {
            for (int i = 0; i < 9; i++) if (inv.getStack(i).isOf(Items.MACE)) { hasMace = true; break; }
        }
        if (mc.player.isOnGround() || mc.player.isGliding()) {
            ready = false; fallDistance = 0; alertedThisFall = false; return;
        }
        fallDistance = mc.player.fallDistance;
        ready = hasMace && fallDistance > m.num("Min Fall");
        if (ready && !alertedThisFall) {
            alertedThisFall = true;
            if (m.bool("Sound")) mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.35f);
        }
    }
}
