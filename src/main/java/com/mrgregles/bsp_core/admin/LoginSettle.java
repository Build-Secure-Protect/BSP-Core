package com.mrgregles.bsp_core.admin;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Holds back the part of a login that touches the inventory until the inventory is settled and the player is alive.
 *
 * <p>BSP-InventorySync request R3: with that mod loaded and {@code storage.deliverOnRestore} on, a player who logs in is
 * waiting until that mod's {@code InventoryRestoredEvent} (read by class name, as the kits do); only then does
 * {@link AdminService#settle} strip coins from an older epoch, run the season check and hand over the items owed to them, since
 * a restore replaces the inventory and the player's saved data. A wait that runs past {@code storage.restoreWaitSeconds} is
 * ended by itself. Without the mod the login settles at once.
 *
 * <p>Request R4: a player can log in dead (they left on the death screen), and a dead entity's inventory does not pass to the
 * respawned player unless keepInventory is on. So a login is never settled on a dead player: the countdown stands still while
 * the waiting player is dead and starts again from the respawn, a player still waiting at the respawn stays waiting (the sync
 * mod's restore comes after the respawn and releases them), and a dead player who is not waiting is settled on the respawned
 * entity. Every settle goes to the player's current entity, never one remembered from before a respawn.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class LoginSettle {
    static final String SYNC_MOD = "bsp_inventorysync";
    static final String RESTORE_EVENT = "com.mrgregles.bsp_inventorysync.api.event.InventoryRestoredEvent";

    /** Players waiting for their restore: the latest entity seen, the ticks left and the limit the countdown restarts from. */
    private static final Map<UUID, Waiting> WAITING = new HashMap<>();
    /** Players to be settled at their respawn: they were dead when their login would have settled. */
    private static final Map<UUID, ServerPlayer> AFTER_RESPAWN = new HashMap<>();
    private static boolean hooked;

    private static final class Waiting {
        ServerPlayer player;
        int ticks;
        final int limit;

        Waiting(ServerPlayer player, int limit) {
            this.player = player;
            this.ticks = limit;
            this.limit = limit;
        }
    }

    private LoginSettle() {}

    /** Whether a login waits for the sync mod's restore before touching the inventory. */
    public static boolean waitsForRestore() {
        return hooked && BSPConfig.getOr(BSPConfig.STORAGE_DELIVER_ON_RESTORE, true) && ModList.get().isLoaded(SYNC_MOD);
    }

    /** Whether the player's login is not settled yet (waiting for a restore or a respawn); their pending items stay put meanwhile. */
    public static boolean isWaiting(UUID player) {
        return WAITING.containsKey(player) || AFTER_RESPAWN.containsKey(player);
    }

    /** Whether items may be put into this player's inventory now: settled and alive. */
    public static boolean canReceive(ServerPlayer player) {
        return !isWaiting(player.getUUID()) && !player.isDeadOrDying();
    }

    /** Starts the wait for a player who just logged in, for up to {@code storage.restoreWaitSeconds} alive. */
    public static void hold(ServerPlayer player) {
        hold(player, Math.max(1, BSPConfig.getOr(BSPConfig.STORAGE_RESTORE_WAIT_SECONDS, 30)) * 20);
    }

    /** As {@link #hold(ServerPlayer)} with the limit in ticks (tests). */
    public static void hold(ServerPlayer player, int ticks) {
        WAITING.put(player.getUUID(), new Waiting(player, Math.max(1, ticks)));
    }

    /** Settles the login later, on the entity that respawns; for a player who is dead when it would have settled. */
    static void afterRespawn(ServerPlayer player) {
        AFTER_RESPAWN.put(player.getUUID(), player);
    }

    /**
     * Ends the wait (the restore event, or the countdown) and settles the login on the player's current entity; when that
     * entity is dead, at the respawn instead. False when the player was not waiting (nothing is done twice).
     */
    public static boolean release(ServerPlayer player) {
        Waiting w = WAITING.remove(player.getUUID());
        if (w == null) {
            return false;
        }
        ServerPlayer current = current(player.getServer(), player.getUUID(), player);
        if (current != null) {
            AdminService.settle(current); // a dead entity is passed on to the respawn there
        }
        return true;
    }

    /**
     * The entity a player has now: the server's player list first (fresh after a respawn); else the latest entity seen here
     * while it is still connected (test players are not in the list). Null when they have left.
     */
    @Nullable
    private static ServerPlayer current(@Nullable MinecraftServer server, UUID id, ServerPlayer known) {
        ServerPlayer listed = server == null ? null : server.getPlayerList().getPlayer(id);
        if (listed != null) {
            return listed;
        }
        return known.hasDisconnected() ? null : known;
    }

    /**
     * A respawn (not the trip home from the End): a player still waiting for their restore stays waiting, with the countdown
     * started again from now on the new entity; a player whose login waited only for the respawn is settled now; anyone else
     * collects what was kept for them while they were dead.
     */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        Waiting w = WAITING.get(id);
        if (w != null) {
            w.player = player;
            w.ticks = w.limit;
            return;
        }
        if (AFTER_RESPAWN.remove(id) != null) {
            AdminService.settle(player);
            return;
        }
        AdminService.handOverOwed(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        WAITING.remove(event.getEntity().getUUID()); // nothing was taken for them yet: it waits for their next login
        AFTER_RESPAWN.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || WAITING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        List<ServerPlayer> overdue = new ArrayList<>();
        for (var it = WAITING.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Waiting w = e.getValue();
            ServerPlayer now = current(server, e.getKey(), w.player);
            if (now == null) {
                it.remove();
                continue;
            }
            w.player = now;
            if (now.isDeadOrDying()) {
                continue; // the countdown stands still on the death screen; the respawn starts it again
            }
            if (--w.ticks <= 0) {
                overdue.add(now);
            }
        }
        for (ServerPlayer player : overdue) {
            BSPCore.LOGGER.warn("No inventory restore for {} within storage.restoreWaitSeconds; handing over what is owed now", player.getGameProfile().getName());
            release(player);
        }
    }

    /** Hooks the sync mod's restore event once, by name, when the mod is there. Called at server start. */
    public static void hookRestore() {
        if (hooked || !ModList.get().isLoaded(SYNC_MOD)) {
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Class<Event> type = (Class<Event>) Class.forName(RESTORE_EVENT);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, type, e -> {
                if (e instanceof PlayerEvent pe && pe.getEntity() instanceof ServerPlayer player) {
                    release(player);
                }
            });
            hooked = true;
            BSPCore.LOGGER.info("Coins, the season check and owed items wait for BSP-InventorySync's restore event at login");
        } catch (ClassNotFoundException | LinkageError e) {
            BSPCore.LOGGER.warn("BSP-InventorySync is loaded but its restore event was not found; logins settle at once");
        }
    }

    @SubscribeEvent
    public static void onServerStarting(net.minecraftforge.event.server.ServerStartingEvent event) {
        hookRestore();
    }
}
