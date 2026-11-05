package com.mrgregles.bsp_core.totem;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.data.PlayerPersistent;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.event.FirstJoinHandler;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The admin's clean slate for one player ({@code /bsp totem wipe}): their carried totems are taken,
 * their placed totems removed from the world, every ledger record of their totems forgotten, their
 * logout placements, transfer and owed items dropped, their first-join grant and any score set by hand
 * ({@code /bsp score set}) cleared, and then a
 * fresh Tier I totem handed to them as on a first join. For testing accounts and support cases.
 */
public final class TotemWipe {
    private TotemWipe() {}

    /** What the wipe did, for the admin's eyes. */
    public record Result(int carried, int placed, int forgotten, int logoutRecords, boolean owedDropped, boolean granted, boolean scoreCleared) {}

    public static Result wipe(ServerPlayer player) {
        Result r = clear(player.server, player.getUUID(), player);
        FirstJoinHandler.grant(player, TotemLedger.get(player.server));
        BSPCore.LOGGER.info("Totem wipe for {}: {} carried, {} placed, {} records forgotten, {} logout placements, owed items {}; a fresh totem given",
                player.getGameProfile().getName(), r.carried(), r.placed(), r.forgotten(), r.logoutRecords(), r.owedDropped() ? "dropped" : "none");
        return new Result(r.carried(), r.placed(), r.forgotten(), r.logoutRecords(), r.owedDropped(), true, r.scoreCleared());
    }

    /**
     * Leaves nothing of a player's totems and gives nothing back ({@code /bsp totem forget}, BSP-Stress Test request ST-C1): the
     * wipe without the fresh totem, for an online or an offline player. An offline player's saved inventory is not opened; they
     * normally carry no totem (a logout places them), except one kept by a logout on a no-totem server, which comes back with them.
     * Their first-join grant is cleared too, so a returning player is given a totem again as on a first join.
     */
    public static Result forget(MinecraftServer server, UUID id) {
        Result r = clear(server, id, server.getPlayerList().getPlayer(id));
        BSPCore.LOGGER.info("Totem forget for {}: {} carried, {} placed, {} records forgotten, {} logout placements, owed items {}; nothing given",
                id, r.carried(), r.placed(), r.forgotten(), r.logoutRecords(), r.owedDropped() ? "dropped" : "none");
        return r;
    }

    /**
     * One totem taken out of play for good by an admin ({@code BspTotems.destroyed}, BSP-InventorySync request R8): retired in the
     * ledger, here and on the network's other servers, so no lost-totem rule ever gives it back and any copy of it is a husk; and
     * dropped from every record that could bring it back: its holder's logout placements and totems kept through a death (when they
     * are online here), a transfer under way, the items owed on this server and in the database. False for a husk or anything that
     * is not a live totem (nothing is changed).
     */
    public static boolean destroyed(MinecraftServer server, ItemStack totem, String by) {
        return destroyed(server, totem, by, null);
    }

    /** As {@link #destroyed(MinecraftServer, ItemStack, String)}; {@code holderHere} is the holder when known (tests pass a mock player, not in the player list). */
    public static boolean destroyed(MinecraftServer server, ItemStack totem, String by, @javax.annotation.Nullable ServerPlayer holderHere) {
        if (!TotemInventories.isTotem(totem)) {
            return false;
        }
        UUID id = TotemIdentity.id(totem), instance = TotemIdentity.instance(totem);
        if (id == null || instance == null) {
            return false;
        }
        TotemLedger ledger = TotemLedger.get(server);
        var sighting = ledger.sighting(id);
        UUID holder = sighting != null && sighting.holder() != null ? sighting.holder() : TotemOwner.fromStack(totem).map(TotemOwner::uuid).orElse(null);
        ledger.retire(id);
        dropRecords(server, id, holder, holderHere, by);
        return true;
    }

    /**
     * The rest of {@link #destroyed}, for a totem already retired here: dropped from every record that could bring it back (its
     * holder's logout placements and totems kept through a death when they are online here, a transfer under way, the items owed
     * here and in the database), and the network's other servers told to retire it.
     */
    public static void dropRecords(MinecraftServer server, UUID id, @javax.annotation.Nullable UUID holder, @javax.annotation.Nullable ServerPlayer holderHere, String by) {
        int records = 0;
        if (holder != null) {
            ServerPlayer online = holderHere != null && holderHere.getUUID().equals(holder) ? holderHere : server.getPlayerList().getPlayer(holder);
            if (online != null) {
                records += PlayerPersistent.removeLogoutPlaced(online, id);
                records += com.mrgregles.bsp_core.zone.ZoneHandler.dropHeld(online, id);
            }
            records += com.mrgregles.bsp_core.api.BspTransfer.forgetTotem(holder, id);
        }
        var season = com.mrgregles.bsp_core.admin.SeasonData.get(server);
        for (var owed : season.pendingItems.values()) {
            int before = owed.size();
            owed.removeIf(st -> id.equals(TotemIdentity.id(st)));
            records += before - owed.size();
        }
        season.pendingItems.values().removeIf(List::isEmpty);
        season.setDirty();
        NetworkStorage.totemDestroyed(id);
        com.mrgregles.bsp_core.score.ScoreService.markDirty();
        BSPCore.LOGGER.info("Shatter Totem {} destroyed by {}: retired on this server and the network, {} record(s) dropped here", id, by, records);
    }

    /** Another server destroyed a totem (its Redis notice): retired here too, and dropped from the items owed on this server. */
    public static void destroyedElsewhere(MinecraftServer server, UUID id) {
        TotemLedger.get(server).retire(id);
        var season = com.mrgregles.bsp_core.admin.SeasonData.get(server);
        boolean changed = false;
        for (var owed : season.pendingItems.values()) {
            changed |= owed.removeIf(st -> id.equals(TotemIdentity.id(st)));
        }
        if (changed) {
            season.pendingItems.values().removeIf(List::isEmpty);
            season.setDirty();
        }
    }

    private static Result clear(MinecraftServer server, UUID id, @javax.annotation.Nullable ServerPlayer player) {
        TotemLedger ledger = TotemLedger.get(server);
        // carried totems
        List<ItemStack> carried = player == null ? List.of() : TotemInventories.removeAll(player);
        for (ItemStack stack : carried) {
            UUID totemId = TotemIdentity.id(stack);
            if (totemId != null) {
                ledger.forget(totemId);
            }
        }
        // placed totems, out of the world
        int placed = 0;
        for (GlobalPos where : new ArrayList<>(ledger.placedFor(id))) {
            ServerLevel level = server.getLevel(where.dimension());
            if (level == null) {
                ledger.recordRemoved(where);
                continue;
            }
            LedgerCheck.loadIfSaved(level, where.pos()); // a chunk that is not on disk is not generated: nothing stands there
            if (level.getBlockEntity(where.pos()) instanceof ShatterTotemBlockEntity totem && totem.isOwner(id)) {
                UUID totemId = TotemIdentity.id(ShatterTotemBlock.toItemStack(level, where.pos()));
                level.removeBlock(where.pos(), false); // onRemove tells the ledger and the chunk loading, and fires TotemEvent.Removed
                if (totemId != null) {
                    ledger.forget(totemId);
                }
                placed++;
            } else {
                ledger.recordRemoved(where);
            }
        }
        int forgotten = ledger.forgetHeldBy(id);
        int logoutRecords = player == null ? 0 : PlayerPersistent.takeLogoutPlaced(player).size();
        TransferLedger.get(server).remove(id);
        boolean owed = com.mrgregles.bsp_core.admin.SeasonData.get(server).pendingItems.remove(id) != null;
        if (owed) {
            com.mrgregles.bsp_core.admin.SeasonData.get(server).setDirty();
        }
        NetworkStorage.clearPending(id);
        NetworkStorage.clearTotemSpots(id);
        ledger.clearGranted(id);
        NetworkStorage.clearGrant(id);
        if (player != null) {
            PlayerPersistent.setTotemGranted(player, false);
        }
        // a wipe starts the player over: no score set by hand rides on the fresh totem. On a network the copy here may be older than
        // a score set on another server since the last read, so the database is always asked (clearing nothing is harmless).
        boolean scoreCleared = NetworkStorage.enabled() || com.mrgregles.bsp_core.score.ScoreAdjust.of(server, id) != 0;
        if (scoreCleared) {
            com.mrgregles.bsp_core.score.ScoreAdjust.clear(server, id, "totem wipe", r -> {
                if (!r.ok()) {
                    BSPCore.LOGGER.error("Totem wipe for {}: the score set by hand could not be cleared (the database is unreachable); run /bsp score clear", id);
                }
            });
        }
        com.mrgregles.bsp_core.score.ScoreService.markDirty();
        return new Result(carried.size(), placed, forgotten, logoutRecords, owed, false, scoreCleared);
    }
}
