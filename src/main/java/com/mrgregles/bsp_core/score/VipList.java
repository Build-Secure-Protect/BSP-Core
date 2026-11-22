package com.mrgregles.bsp_core.score;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.compat.LuckPermsBridge;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.VipListPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The server's VIPs for the Score Screen's VIPs board: every member of the LuckPerms groups in {@code vipBoard.groups} (or of a
 * group that inherits one), online or not, sorted by name. LuckPerms keeps its storage shared across the network, so every
 * server finds the same list. It is asked when the server starts, every five minutes, and shortly after a player logs in (a
 * store purchase usually comes with a login); a failed answer keeps the last list.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class VipList {
    /** One VIP: their id and the name LuckPerms (or this server) last saw. */
    public record Vip(UUID id, String name) {}

    public static final int REFRESH_TICKS = 5 * 60 * 20;
    /** A login asks again, but never sooner than this after the last ask (ms). */
    public static final long LOGIN_GAP_MS = 30_000;
    /** An answer from LuckPerms that takes longer than this counts as failed. */
    public static final long ANSWER_TIMEOUT_S = 60;

    /** Test hook: answers (groups) in place of LuckPerms; null for LuckPerms. */
    @Nullable
    public static Function<Set<String>, CompletableFuture<Map<UUID, String>>> source;
    /** Told on the server thread whenever the list changes, after every player online was sent it (clients hear through {@link VipListPacket}; the tests listen here). */
    public static final List<Consumer<MinecraftServer>> CHANGED = new ArrayList<>();

    private static volatile List<Vip> vips = List.of();
    private static boolean asking, again, loginPending;
    private static long lastAskMs = Long.MIN_VALUE / 2;
    private static int ticks;
    /** Callers of {@link #refresh(MinecraftServer, Consumer)} waiting for the answer in flight, and those who came during it (they wait for the next). */
    private static final Deque<Consumer<Boolean>> WAITING = new ArrayDeque<>(), NEXT = new ArrayDeque<>();

    private VipList() {}

    /** The VIPs as last found, sorted by name (ignoring case). Never null. */
    public static List<Vip> vips() {
        return vips;
    }

    /** The groups to look in, lower-cased, blanks dropped. */
    public static Set<String> groups() {
        Set<String> out = new LinkedHashSet<>();
        for (String g : BSPConfig.getOr(BSPConfig.VIP_BOARD_GROUPS, List.<String>of("vip"))) {
            String t = g == null ? "" : g.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /**
     * {@code groups} and every group that inherits one of them, directly or through others. {@code parents} maps each group to the
     * groups it inherits (lower-case).
     */
    public static Set<String> withHeirs(Set<String> groups, Map<String, Set<String>> parents) {
        Set<String> out = new TreeSet<>(groups);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Map.Entry<String, Set<String>> e : parents.entrySet()) {
                if (!out.contains(e.getKey()) && e.getValue().stream().anyMatch(out::contains)) {
                    out.add(e.getKey());
                    grew = true;
                }
            }
        }
        return out;
    }

    /** Asks for the list now (or once more after the answer in flight). */
    public static void refresh(MinecraftServer server) {
        refresh(server, null);
    }

    /** Asks for the list; {@code done} hears on the server thread whether the answer came (false when LuckPerms failed). */
    public static void refresh(MinecraftServer server, @Nullable Consumer<Boolean> done) {
        loginPending = false;
        if (asking) {
            if (done != null) {
                NEXT.add(done);
            }
            again = true;
            return;
        }
        if (done != null) {
            WAITING.add(done);
        }
        asking = true;
        lastAskMs = System.currentTimeMillis();
        CompletableFuture<Map<UUID, String>> answer;
        try {
            answer = source != null ? source.apply(groups()) : LuckPermsBridge.membersOf(groups()).thenComposeAsync(ids -> named(server, ids), server);
        } catch (RuntimeException e) {
            answer = CompletableFuture.failedFuture(e);
        }
        // an answer that never comes (stuck storage) must not stop every later refresh
        answer.orTimeout(ANSWER_TIMEOUT_S, java.util.concurrent.TimeUnit.SECONDS).whenComplete((found, error) -> {
            if (server.isSameThread()) {
                answered(server, found, error);
            } else {
                server.execute(() -> answered(server, found, error));
            }
        });
    }

    private static void answered(MinecraftServer server, @Nullable Map<UUID, String> found, @Nullable Throwable error) {
        asking = false;
        boolean ok = error == null && found != null;
        if (ok) {
            apply(server, found);
        } else {
            BSPCore.LOGGER.warn("VIP board: LuckPerms could not list the VIPs, keeping the last list", error);
        }
        List<Consumer<Boolean>> told = new ArrayList<>(WAITING);
        WAITING.clear();
        WAITING.addAll(NEXT);
        NEXT.clear();
        told.forEach(c -> c.accept(ok));
        if (again) {
            again = false;
            refresh(server);
        }
    }

    /** This server's name for a player, as they spell it (from its cache of profiles), or "". */
    private static String cachedName(MinecraftServer server, UUID id) {
        return server.getProfileCache() == null ? "" : server.getProfileCache().get(id).map(com.mojang.authlib.GameProfile::getName).orElse("");
    }

    /**
     * Names for the members, on the server thread: this server's cache first (LuckPerms keeps names in lower case), and LuckPerms
     * asked only for players this server has never seen.
     */
    private static CompletableFuture<Map<UUID, String>> named(MinecraftServer server, Set<UUID> ids) {
        Map<UUID, String> out = new java.util.HashMap<>();
        Set<UUID> unknown = new java.util.HashSet<>();
        for (UUID id : ids) {
            String name = cachedName(server, id);
            if (name.isEmpty()) {
                unknown.add(id);
            } else {
                out.put(id, name);
            }
        }
        return LuckPermsBridge.usernames(unknown).thenApply(found -> {
            found.forEach(out::putIfAbsent);
            return out;
        });
    }

    /** Takes a fresh answer: this server's spelling of each name when it has one, else the answer's; players with no name are left out. */
    static void apply(MinecraftServer server, Map<UUID, String> found) {
        List<Vip> list = new ArrayList<>();
        for (Map.Entry<UUID, String> e : found.entrySet()) {
            String name = cachedName(server, e.getKey());
            if (name.isEmpty()) {
                name = e.getValue() == null ? "" : e.getValue().trim();
            }
            if (!name.isEmpty()) {
                list.add(new Vip(e.getKey(), name));
            }
        }
        list.sort(Comparator.comparing((Vip v) -> v.name().toLowerCase(Locale.ROOT)).thenComparing(v -> v.id().toString()));
        if (!list.equals(vips)) {
            vips = List.copyOf(list);
            VipListPacket packet = VipListPacket.of(vips);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                BSPNetwork.sendTo(player, packet);
            }
            for (Consumer<MinecraftServer> c : CHANGED) {
                c.accept(server);
            }
        }
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        ticks = 0;
        refresh(event.getServer());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        vips = List.of();
        asking = again = loginPending = false;
        WAITING.clear();
        NEXT.clear();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BSPNetwork.sendTo(player, VipListPacket.of(vips));
        }
        loginPending = true;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        if (++ticks >= REFRESH_TICKS || loginPending && System.currentTimeMillis() - lastAskMs >= LOGIN_GAP_MS) {
            ticks = 0;
            refresh(server);
        }
    }
}
