package com.example.clickgui;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * Fake Player: создаёт клиентский клон игрока (OtherClientPlayerEntity)
 * со скином, бронёй и координатами для визуального тестирования модулей.
 */
public final class FakePlayer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final int FAKE_ID = -99_999;
    private static OtherClientPlayerEntity fake;
    private static ClientWorld fakeWorld;

    public static OtherClientPlayerEntity entity() {
        return fake;
    }

    public static void tick() {
        Module m = ModuleManager.get("Fake Player");
        if (!m.enabled || mc.player == null || mc.world == null) {
            remove();
            return;
        }
        if (fake == null || fake.isRemoved() || fakeWorld != mc.world) {
            spawn(m);
            return;
        }
        if (fake.hurtTime > 0) fake.hurtTime--;
        if (m.bool("Look At Me")) {
            Vec3d d = mc.player.getEyePos().subtract(fake.getEyePos());
            double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            float pitch = (float) Math.toDegrees(-Math.atan2(d.y, horiz));
            fake.setYaw(yaw);
            fake.setPitch(pitch);
            fake.setHeadYaw(yaw);
            fake.setBodyYaw(yaw);
        }
    }

    public static void onLocalHit() {
        if (fake == null || mc.world == null) return;
        fake.hurtTime = 10;
        fake.maxHurtTime = 10;
        mc.world.playSoundClient(fake.getX(), fake.getY(), fake.getZ(),
                SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 0.8f, 1.0f, false);
    }

    private static void spawn(Module m) {
        remove();
        GameProfile profile = new GameProfile(UUID.randomUUID(), mc.player.getName().getString());
        fake = new OtherClientPlayerEntity(mc.world, profile) {
            @Override
            public SkinTextures getSkin() {
                return mc.player != null ? mc.player.getSkin() : super.getSkin();
            }
        };
        fake.setId(FAKE_ID);
        fake.refreshPositionAndAngles(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                mc.player.getYaw(), mc.player.getPitch());
        fake.setHeadYaw(mc.player.getHeadYaw());
        fake.setBodyYaw(mc.player.bodyYaw);
        fake.setMainArm(mc.player.getMainArm());
        if (m.bool("Copy Armor")) {
            EquipmentSlot[] slots = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
            };
            for (EquipmentSlot s : slots) {
                fake.equipStack(s, mc.player.getEquippedStack(s).copy());
            }
        }
        fakeWorld = mc.world;
        mc.world.addEntity(fake);
    }

    private static void remove() {
        if (fake != null && fakeWorld != null) {
            fakeWorld.removeEntity(fake.getId(), Entity.RemovalReason.DISCARDED);
        }
        fake = null;
        fakeWorld = null;
    }
}
