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
 * logout placements, transfer and owed items dropped, their first-join grant cleared, and then a
 * fresh Tier I totem handed to them as on a first join. For testing accounts and support cases.
 */
public final class TotemWipe {
    private TotemWipe() {}

    /** What the wipe did, for the admin's eyes. */
    public record Result(int carried, int placed, int forgotten, int logoutRecords, boolean owedDropped, boolean granted) {}

    public static Result wipe(ServerPlayer player) {
        Result r = clear(player.server, player.getUUID(), player);
        FirstJoinHandler.grant(player, TotemLedger.get(player.server));
        BSPCore.LOGGER.info("Totem wipe for {}: {} carried, {} placed, {} records forgotten, {} logout placements, owed items {}; a fresh totem given",
                player.getGameProfile().getName(), r.carried(), r.placed(), r.forgotten(), r.logoutRecords(), r.owedDropped() ? "dropped" : "none");
        return new Result(r.carried(), r.placed(), r.forgotten(), r.logoutRecords(), r.owedDropped(), true);
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
            level.getChunk(where.pos());
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
        com.mrgregles.bsp_core.score.ScoreService.markDirty();
        return new Result(carried.size(), placed, forgotten, logoutRecords, owed, false);
    }
}
