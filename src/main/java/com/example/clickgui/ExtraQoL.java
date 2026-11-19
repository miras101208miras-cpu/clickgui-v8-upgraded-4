package com.example.clickgui;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.time.LocalTime;
import java.util.Locale;

/** Легитные QoL-утилиты: Auto Sprint, Chat Timestamps, Anti-Spam, Auto GG, Mention Ping, Sound Muter, Death Coords. */
public final class ExtraQoL {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean sprintManaged;
    private static Perspective savedFrontPersp;
    private static boolean frontActive;
    private static boolean wasDead;
    private static long lowDurAlertMs;
    private static String lastMsg = "";
    private static long lastMsgAtMs;
    private static long pendingGgAtMs;
    private static long lastGgSentMs;
    private static long lastMentionMs;

    private ExtraQoL() {}

    public static void init() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            Module m = ModuleManager.get("Item Info Tooltip");
            if (!m.enabled || stack == null || stack.isEmpty()) return;
            if (m.bool("Durability") && stack.isDamageable() && stack.getMaxDamage() > 0) {
                int rem = stack.getMaxDamage() - stack.getDamage();
                lines.add(Text.literal("\u00A77Durability: \u00A7f" + rem + " / " + stack.getMaxDamage()));
            }
            if (m.bool("Item ID")) {
                lines.add(Text.literal("\u00A78ID: " + Registries.ITEM.getId(stack.getItem())));
            }
        });

        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay || message == null) return true;
            String raw = message.getString();
            checkAutoGg(raw);
            return checkAntiSpam(raw);
        });

        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, ts) -> {
            if (message == null) return true;
            return checkAntiSpam(message.getString());
        });
    }

    /** Вызывается из ChatHudMixin для всех входящих сообщений (чат игроков + системные). */
    public static Text decorateIncomingMessage(Text message) {
        if (message == null) return null;
        Text out = message;
        String raw = message.getString();
        Module mp = ModuleManager.get("Chat Mention Ping");
        if (mp.enabled && isMention(raw)) {
            triggerMentionPing(mp);
            out = Text.literal("\u00A76\u2605 \u00A7e").append(out);
        }
        if (ModuleManager.get("Chat Timestamps").enabled) {
            LocalTime t = LocalTime.now();
            String stamp = String.format(Locale.ROOT, "\u00A78[%02d:%02d] \u00A7r", t.getHour(), t.getMinute());
            out = Text.literal(stamp).append(out);
        }
        return out;
    }

    private static boolean isMention(String raw) {
        if (raw == null || mc.player == null) return false;
        String me = mc.player.getName().getString();
        if (me == null || me.length() < 2) return false;
        return raw.toLowerCase(Locale.ROOT).contains(me.toLowerCase(Locale.ROOT));
    }

    private static void triggerMentionPing(Module m) {
        long now = System.currentTimeMillis();
        if (now - lastMentionMs < 1500L) return;
        lastMentionMs = now;
        if (m.bool("Toast")) Notifications.push("Chat Mention", "Вас упомянули в чате", 0xFFFBBF24);
        if (m.bool("Sound")) {
            mc.execute(() -> {
                if (mc.world != null && mc.player != null) {
                    mc.world.playSoundClient(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                            SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.MASTER, 0.7f, 1.35f, false);
                }
            });
        }
    }

    public static boolean shouldMuteSound(SoundInstance sound) {
        if (sound == null) return false;
        Module m = ModuleManager.get("Sound Muter");
        if (!m.enabled || sound.getId() == null) return false;
        String p = sound.getId().getPath();
        if (m.bool("Portal") && p.contains("portal")) return true;
        if (m.bool("Rain") && (p.startsWith("weather.rain") || p.startsWith("weather.above"))) return true;
        if (m.bool("Pistons") && p.contains("block.piston")) return true;
        if (m.bool("Minecarts") && p.contains("entity.minecart")) return true;
        if (m.bool("XP Orbs") && (p.contains("experience_orb") || p.contains("experience_bottle"))) return true;
        if (m.bool("Endermen") && p.contains("entity.enderman")) return true;
        return false;
    }

    private static boolean checkAntiSpam(String raw) {
        if (!ModuleManager.get("Anti-Spam Chat").enabled || raw == null || raw.isBlank()) return true;
        long now = System.currentTimeMillis();
        if (raw.equals(lastMsg) && now - lastMsgAtMs < 4000L) return false;
        lastMsg = raw;
        lastMsgAtMs = now;
        return true;
    }

    private static void checkAutoGg(String raw) {
        if (!ModuleManager.get("Auto GG").enabled || raw == null) return;
        String l = raw.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        if (now - lastGgSentMs < 15000L) return;
        if (l.contains("victory!") || l.contains("won the game") || l.contains("game over")
                || l.contains("победа") || l.contains("выиграл")) {
            pendingGgAtMs = now + (ModuleManager.get("Auto GG").bool("Delay") ? 450L : 50L);
        }
    }

    public static void tick() {
        if (mc.player == null || mc.world == null) {
            wasDead = false;
            if (sprintManaged) { mc.options.sprintKey.setPressed(false); sprintManaged = false; }
            if (frontActive && savedFrontPersp != null) { mc.options.setPerspective(savedFrontPersp); frontActive = false; savedFrontPersp = null; }
            return;
        }

        // 1. Auto Sprint (легитный Toggle Sprint при движении вперёд)
        Module spr = ModuleManager.get("Auto Sprint");
        if (spr.enabled && mc.currentScreen == null && mc.options.forwardKey.isPressed()
                && !mc.player.isSneaking() && !mc.player.horizontalCollision) {
            mc.options.sprintKey.setPressed(true);
            sprintManaged = true;
        } else if (sprintManaged) {
            mc.options.sprintKey.setPressed(false);
            sprintManaged = false;
        }

        // 2. Quick Look Front
        Module qlf = ModuleManager.get("Quick Look Front");
        if (qlf.enabled && !frontActive) {
            frontActive = true;
            savedFrontPersp = mc.options.getPerspective();
            mc.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
        } else if (!qlf.enabled && frontActive) {
            frontActive = false;
            if (savedFrontPersp != null) mc.options.setPerspective(savedFrontPersp);
            savedFrontPersp = null;
        }

        // 3. Death Coords Chat
        boolean dead = mc.player.isDead() || mc.player.getHealth() <= 0f;
        if (ModuleManager.get("Death Coords Chat").enabled && dead && !wasDead) {
            String msg = String.format(Locale.ROOT, "\u00A7c[Death] \u00A7fX: %d  Y: %d  Z: %d \u00A77(%s)",
                    Math.round(mc.player.getX()), Math.round(mc.player.getY()), Math.round(mc.player.getZ()),
                    Waypoints.shortDim(Waypoints.currentDim()));
            mc.player.sendMessage(Text.literal(msg), false);
        }
        wasDead = dead;

        // 4. Low Durability Alert
        Module ld = ModuleManager.get("Low Durability Alert");
        if (ld.enabled) {
            ItemStack main = mc.player.getMainHandStack();
            if (!main.isEmpty() && main.isDamageable() && main.getMaxDamage() > 0) {
                int rem = main.getMaxDamage() - main.getDamage();
                double pct = 100.0 * rem / main.getMaxDamage();
                if (pct <= ld.num("Threshold %") && System.currentTimeMillis() - lowDurAlertMs > 8000L) {
                    lowDurAlertMs = System.currentTimeMillis();
                    Notifications.push("LOW DURABILITY", main.getName().getString() + " (" + rem + " left)", 0xFFFF5555);
                    if (ld.bool("Sound")) {
                        mc.world.playSoundClient(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                                SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.4f, 1.8f, false);
                    }
                }
            }
        }

        // 5. Delayed Auto GG
        if (pendingGgAtMs > 0 && System.currentTimeMillis() >= pendingGgAtMs) {
            pendingGgAtMs = 0;
            lastGgSentMs = System.currentTimeMillis();
            if (mc.getNetworkHandler() != null) {
                String txt = ((Setting.Mode) ModuleManager.get("Auto GG").find("Text")).get();
                mc.getNetworkHandler().sendChatMessage(txt);
            }
        }
    }
}
