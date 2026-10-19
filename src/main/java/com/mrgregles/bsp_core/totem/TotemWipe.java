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
        MinecraftServer server = player.server;
        UUID id = player.getUUID();
        TotemLedger ledger = TotemLedger.get(server);
        // carried totems
        List<ItemStack> carried = TotemInventories.removeAll(player);
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
        int logoutRecords = PlayerPersistent.takeLogoutPlaced(player).size();
        TransferLedger.get(server).remove(id);
        boolean owed = com.mrgregles.bsp_core.admin.SeasonData.get(server).pendingItems.remove(id) != null;
        if (owed) {
            com.mrgregles.bsp_core.admin.SeasonData.get(server).setDirty();
        }
        NetworkStorage.clearPending(id);
        ledger.clearGranted(id);
        NetworkStorage.clearGrant(id);
        PlayerPersistent.setTotemGranted(player, false);
        com.mrgregles.bsp_core.score.ScoreService.markDirty();
        FirstJoinHandler.grant(player, ledger);
        BSPCore.LOGGER.info("Totem wipe for {}: {} carried, {} placed, {} records forgotten, {} logout placements, owed items {}; a fresh totem given",
                player.getGameProfile().getName(), carried.size(), placed, forgotten, logoutRecords, owed ? "dropped" : "none");
        return new Result(carried.size(), placed, forgotten, logoutRecords, owed, true);
    }
}
