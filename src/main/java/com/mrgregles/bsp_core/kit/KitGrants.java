package com.mrgregles.bsp_core.kit;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.api.event.KitEvent;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Kits at login. On every login the first-join kits the player has no record of become owed to them
 * (NETWORK: one row for the whole network; SERVERS: one row per server that hands it out), and then
 * everything owed is delivered once the inventory is settled: on BSP-InventorySync's
 * {@code InventoryRestoredEvent} when that mod is loaded and {@code kit.deliverOnRestore} is on, else
 * {@code kit.grantDelayTicks} after the login. Nothing is put in the inventory at login time itself,
 * because a sync mod's restore would wipe it. A kit that does not fit stays owed and the player is
 * told; it waits on the WAITING card of their browser.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class KitGrants {
    private KitGrants() {}

    static final String SYNC_MOD = "bsp_inventorysync";
    static final String RESTORE_EVENT = "com.mrgregles.bsp_inventorysync.api.event.InventoryRestoredEvent";

    /** Players whose owed kits are due in so many ticks (the path without a sync mod). */
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    /** Kits granted at this login, so their delivery says FIRST_JOIN. */
    private static final Map<UUID, Set<String>> GRANTED_NOW = new HashMap<>();
    /** Players logged in since their last restore event: kits wait for it, even past a respawn. */
    private static final Set<UUID> AWAITING_RESTORE = new HashSet<>();
    private static boolean hooked;

    private record Pending(ServerPlayer player, int[] ticks) {}

    /** Whether deliveries wait for the sync mod's restore event instead of a tick delay. */
    public static boolean waitsForRestore() {
        return BSPConfig.getOr(BSPConfig.KIT_DELIVER_ON_RESTORE, true) && ModList.get().isLoaded(SYNC_MOD);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (waitsForRestore()) {
                AWAITING_RESTORE.add(player.getUUID());
            }
            login(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
        GRANTED_NOW.remove(event.getEntity().getUUID());
        AWAITING_RESTORE.remove(event.getEntity().getUUID());
    }

    /**
     * A respawn (not the trip home from the End): kits that waited because the player was dead are delivered to the respawned
     * player, unless their login still waits for the sync mod's restore, which comes after the respawn and delivers them then.
     */
    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player) || AWAITING_RESTORE.contains(player.getUUID())) {
            return;
        }
        PENDING.remove(player.getUUID());
        if (!Kits.owed(player.server, player.getUUID()).isEmpty()) {
            deliver(player);
        }
    }

    /** Marks the player's login as still waiting for the sync mod's restore (tests). */
    public static void awaitRestore(UUID player, boolean waiting) {
        if (waiting) {
            AWAITING_RESTORE.add(player);
        } else {
            AWAITING_RESTORE.remove(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        List<Pending> due = new ArrayList<>();
        for (var it = PENDING.values().iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (p.player.hasDisconnected()) {
                it.remove();
            } else if (--p.ticks[0] <= 0) {
                due.add(p);
                it.remove();
            }
        }
        for (Pending p : due) {
            deliver(p.player);
        }
    }

    /** What a login does: record the first-join grants, then schedule the delivery. Public for the tests. */
    public static void login(ServerPlayer player) {
        if (!Kits.enabled()) {
            return;
        }
        if (com.mrgregles.bsp_core.storage.NetworkStorage.enabled()) {
            // the network's rows first (a reward given elsewhere, a grant made on another server), then the grants
            com.mrgregles.bsp_core.storage.NetworkStorage.kitRedeemsReload(player.getUUID(), () -> {
                if (!player.hasDisconnected()) {
                    grantAndSchedule(player);
                }
            });
            return;
        }
        grantAndSchedule(player);
    }

    private static void grantAndSchedule(ServerPlayer player) {
        int granted = grantFirstJoin(player);
        if (granted > 0 || !Kits.owed(player.server, player.getUUID()).isEmpty()) {
            schedule(player);
        }
    }

    /**
     * Makes every first-join kit the player has no record of owed to them. Returns how many. A kit
     * granted on the network counts as taken once (the once rule and the cooldown see it); a kit
     * granted per server leaves a row in that server's scope and is owed on the network row.
     */
    public static int grantFirstJoin(ServerPlayer player) {
        MinecraftServer server = player.server;
        KitLedger ledger = KitLedger.get(server);
        String here = Kits.serverId();
        int n = 0;
        for (Kit kit : new ArrayList<>(Kits.all(server))) {
            if (!kit.enabled || kit.grant == Kit.Grant.NONE) {
                continue;
            }
            String scope = kit.grant == Kit.Grant.NETWORK ? Kits.NETWORK : here;
            if (kit.grant == Kit.Grant.SERVERS && !kit.grantsOn(here)) {
                continue;
            }
            KitLedger.Redeem row = ledger.redeem(player.getUUID(), kit.id, scope);
            if (row != null && (row.count > 0 || row.owed > 0 || row.lastMs > 0)) {
                continue;
            }
            KitLedger.Redeem mark = ledger.redeemOrNew(player.getUUID(), kit.id, scope);
            mark.lastMs = Kits.now();
            if (kit.grant == Kit.Grant.SERVERS) {
                mark.count++;
            }
            ledger.changed();
            GRANTED_NOW.computeIfAbsent(player.getUUID(), k -> new HashSet<>()).add(kit.id);
            // on a network the mark is a claim: the first server whose insert lands grants; a loser takes its grant back
            com.mrgregles.bsp_core.storage.NetworkStorage.kitRedeemClaim(player.getUUID(), mark, won -> {
                if (!won) {
                    KitLedger.Redeem net = ledger.redeem(player.getUUID(), kit.id, Kits.NETWORK);
                    if (net != null && net.owed > 0) {
                        net.owed--;
                        ledger.changed();
                    }
                    Set<String> now = GRANTED_NOW.get(player.getUUID());
                    if (now != null) {
                        now.remove(kit.id);
                    }
                    com.mrgregles.bsp_core.storage.NetworkStorage.kitRedeemsReload(player.getUUID(), null);
                }
            });
            Kits.give(server, player.getUUID(), kit.id, KitEvent.How.FIRST_JOIN, false);
            n++;
        }
        return n;
    }

    /** Delivery once the inventory is settled: now (the restore event), or after the delay. */
    public static void schedule(ServerPlayer player) {
        if (waitsForRestore()) {
            return;
        }
        PENDING.put(player.getUUID(), new Pending(player, new int[] {Math.max(1, BSPConfig.getOr(BSPConfig.KIT_GRANT_DELAY_TICKS, 40))}));
    }

    /**
     * Delivers what is owed, FIRST_JOIN for the kits granted at this login. Never to a dead player (they logged in on the death
     * screen): a dead entity's inventory does not pass to the respawned one, so the kits stay owed and {@link #onRespawn} delivers
     * them. Public for the tests.
     */
    public static int deliver(ServerPlayer player) {
        if (player.isDeadOrDying()) {
            return 0;
        }
        Set<String> first = GRANTED_NOW.remove(player.getUUID());
        return Kits.deliverOwed(player, id -> first != null && first.contains(id) ? KitEvent.How.FIRST_JOIN : KitEvent.How.REWARD);
    }

    /** How many kits are waiting to be delivered by the tick path (tests). */
    public static boolean pending(UUID player) {
        return PENDING.containsKey(player);
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
                if (e instanceof PlayerEvent pe && pe.getEntity() instanceof ServerPlayer player && BSPConfig.getOr(BSPConfig.KIT_DELIVER_ON_RESTORE, true)) {
                    AWAITING_RESTORE.remove(player.getUUID());
                    PENDING.remove(player.getUUID());
                    deliver(player);
                }
            });
            hooked = true;
            BSPCore.LOGGER.info("Kits are delivered on BSP-InventorySync's restore event");
        } catch (ClassNotFoundException | LinkageError e) {
            BSPCore.LOGGER.warn("BSP-InventorySync is loaded but its restore event was not found; kits are delivered {} ticks after login instead", BSPConfig.getOr(BSPConfig.KIT_GRANT_DELAY_TICKS, 40));
        }
    }

    @SubscribeEvent
    public static void onServerStarting(net.minecraftforge.event.server.ServerStartingEvent event) {
        hookRestore();
    }
}
