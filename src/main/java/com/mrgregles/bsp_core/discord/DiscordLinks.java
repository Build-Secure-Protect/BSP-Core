package com.mrgregles.bsp_core.discord;

import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.DiscordStatusPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Linking a player's Discord. The game makes a six-character code ({@code /bsp discord link} or LINK on Herald's card); the
 * player types {@code /link CODE} in the BSP Discord, and the bot, told who they are by Discord itself, takes the code and
 * marks them linked. The player never types a Discord name, id or password, and the game never holds one: it only asks
 * whether the player is linked. A code lasts {@link #CODE_SECONDS}, works once, replaces the player's last code, and a
 * player may make {@link #CODES_PER_HOUR} an hour. Off wherever {@link Herald#available()} is false. Server thread only.
 */
@Mod.EventBusSubscriber(modid = com.mrgregles.bsp_core.BSPCore.MODID)
public final class DiscordLinks {
    public static final int CODE_SECONDS = 600, CODES_PER_HOUR = 5;
    /** No 0/O, 1/I/L: easy to read off the screen and type. */
    public static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final LinkStore REDIS = new RedisLinkStore();
    @Nullable
    private static LinkStore testStore;

    private record Pending(String code, long expiresAt) {}

    private static final Map<UUID, Pending> pending = new HashMap<>();
    private static final Map<UUID, ArrayDeque<Long>> made = new HashMap<>();

    public enum State { OFF, UNKNOWN, NOT_LINKED, CODE, LINKED, TOO_MANY, FAILED }

    public enum Action { STATUS, LINK, UNLINK }

    public record Status(State state, String code, int secondsLeft) {
        static Status of(State state) {
            return new Status(state, "", 0);
        }
    }

    private DiscordLinks() {}

    public static boolean on() {
        return testStore != null || Herald.available();
    }

    private static LinkStore store() {
        return testStore != null ? testStore : REDIS;
    }

    public static String newCode() {
        char[] c = new char[6];
        for (int i = 0; i < c.length; i++) {
            c[i] = ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length()));
        }
        return new String(c);
    }

    public static boolean isCode(String s) {
        return s != null && s.length() == 6 && s.chars().allMatch(ch -> ALPHABET.indexOf(ch) >= 0);
    }

    // ------------------------------------------------------------------ the rules

    public static void link(UUID player, String name, long now, Consumer<Status> then) {
        if (!on()) {
            then.accept(Status.of(State.OFF));
            return;
        }
        ArrayDeque<Long> times = made.computeIfAbsent(player, k -> new ArrayDeque<>());
        while (!times.isEmpty() && now - times.peekFirst() >= 3_600_000L) {
            times.pollFirst();
        }
        if (times.size() >= CODES_PER_HOUR) {
            then.accept(Status.of(State.TOO_MANY));
            return;
        }
        times.addLast(now);
        String code = newCode();
        Pending before = pending.get(player);
        Pending mine = new Pending(code, now + CODE_SECONDS * 1000L);
        pending.put(player, mine);
        store().putCode(code, before == null ? null : before.code(), player, name, CODE_SECONDS, ok -> {
            if (ok) {
                then.accept(new Status(State.CODE, code, CODE_SECONDS));
            } else {
                pending.remove(player, mine);
                then.accept(Status.of(State.FAILED));
            }
        });
    }

    public static void status(UUID player, long now, Consumer<Status> then) {
        if (!on()) {
            then.accept(Status.of(State.OFF));
            return;
        }
        store().isLinked(player, linked -> {
            Pending p = pending.get(player);
            if (p != null && p.expiresAt() <= now) {
                pending.remove(player);
                p = null;
            }
            if (Boolean.TRUE.equals(linked)) {
                pending.remove(player); // the bot took the code
                then.accept(Status.of(State.LINKED));
            } else if (p != null) {
                then.accept(new Status(State.CODE, p.code(), (int) ((p.expiresAt() - now + 999) / 1000)));
            } else {
                then.accept(Status.of(linked == null ? State.UNKNOWN : State.NOT_LINKED));
            }
        });
    }

    public static void unlink(UUID player, Consumer<Status> then) {
        if (!on()) {
            then.accept(Status.of(State.OFF));
            return;
        }
        Pending p = pending.remove(player);
        store().unlink(player, p == null ? null : p.code(), ok -> then.accept(Status.of(ok ? State.NOT_LINKED : State.FAILED)));
    }

    // ------------------------------------------------------------------ for a player: the commands and Herald's card

    /** Runs {@code action} for {@code player}; the answer goes to their Herald card, and to chat when {@code chat} (a command, or a new code). */
    public static void act(ServerPlayer player, Action action, boolean chat) {
        UUID id = player.getUUID();
        long now = System.currentTimeMillis();
        Consumer<Status> answer = s -> {
            if (player.hasDisconnected()) {
                return;
            }
            BSPNetwork.sendTo(player, new DiscordStatusPacket(s.state(), s.code(), s.secondsLeft()));
            if (chat || s.state() == State.CODE && action == Action.LINK || s.state() == State.TOO_MANY || s.state() == State.FAILED) {
                player.sendSystemMessage(message(s, action));
            }
        };
        switch (action) {
            case STATUS -> status(id, now, answer);
            case LINK -> link(id, player.getGameProfile().getName(), now, answer);
            case UNLINK -> unlink(id, answer);
        }
    }

    static Component message(Status s, Action action) {
        return switch (s.state()) {
            case OFF -> Component.translatable("gui.bsp_core.tree.why.discord").withStyle(ChatFormatting.RED);
            case TOO_MANY -> Component.translatable("message.bsp_core.discord.too_many", CODES_PER_HOUR).withStyle(ChatFormatting.RED);
            case FAILED -> Component.translatable("message.bsp_core.discord.failed").withStyle(ChatFormatting.RED);
            case LINKED -> Component.translatable("message.bsp_core.discord.status.linked").withStyle(ChatFormatting.AQUA);
            case NOT_LINKED -> Component.translatable(action == Action.UNLINK ? "message.bsp_core.discord.unlinked" : "message.bsp_core.discord.status.not_linked").withStyle(ChatFormatting.GRAY);
            case UNKNOWN -> Component.translatable("message.bsp_core.discord.status.unknown").withStyle(ChatFormatting.GRAY);
            case CODE -> action == Action.LINK ? codeLine(s.code()) : Component.translatable("message.bsp_core.discord.status.code",
                    Component.literal(s.code()).withStyle(ChatFormatting.GOLD), (s.secondsLeft() + 59) / 60).withStyle(ChatFormatting.GRAY);
        };
    }

    /** The new code, with a [Copy] that puts {@code /link CODE} on the clipboard for pasting into Discord. */
    private static Component codeLine(String code) {
        String command = "/link " + code;
        return Component.translatable("message.bsp_core.discord.code", Component.literal(code).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                        Component.literal(command).withStyle(ChatFormatting.WHITE), CODE_SECONDS / 60).withStyle(ChatFormatting.YELLOW)
                .append(" ")
                .append(Component.translatable("message.bsp_core.discord.copy").withStyle(s -> s.withColor(ChatFormatting.AQUA).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.bsp_core.discord.copy.hover")))));
    }

    // ------------------------------------------------------------------ at login

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            atLogin(player.getUUID(), line -> {
                if (!player.hasDisconnected()) {
                    player.sendSystemMessage(line);
                }
            });
        }
    }

    /** If the bot could not deliver a direct message to {@code player} while they were away, tell them once how to fix it (decision 15: nothing public). */
    public static void atLogin(UUID player, Consumer<Component> tell) {
        if (!on()) {
            return;
        }
        store().takeUndelivered(player, had -> {
            if (Boolean.TRUE.equals(had)) {
                tell.accept(Component.translatable("message.bsp_core.discord.undelivered").withStyle(ChatFormatting.YELLOW));
            }
        });
    }

    // ------------------------------------------------------------------ GameTests

    /** GameTests: use {@code store} instead of Redis (and count Discord as on) until {@code useStore(null)}. */
    public static void useStore(@Nullable LinkStore store) {
        testStore = store;
    }

    /** GameTests: forget a test player's codes and their count. */
    public static void forget(UUID player) {
        pending.remove(player);
        made.remove(player);
    }
}
