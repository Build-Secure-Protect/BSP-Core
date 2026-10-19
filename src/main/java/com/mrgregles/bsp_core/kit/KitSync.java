package com.mrgregles.bsp_core.kit;

import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Kits across the network: every kit an admin saves and every redeem row a server writes goes to the
 * shared tables and is announced over Redis (kind {@code kit}); a change elsewhere comes back as a
 * fresh copy that replaces the local one. Without network storage none of this runs and the local
 * ledger is the record.
 */
public final class KitSync {
    private KitSync() {}

    static void written(Kit kit) {
        NetworkStorage.kitWrite(kit);
    }

    static void removed(String id) {
        NetworkStorage.kitDelete(id);
    }

    static void taken(Kit kit) {
        NetworkStorage.kitTaken(kit.id, kit.lastTakenBy, kit.lastTakenMs);
    }

    static void row(UUID player, KitLedger.Redeem row) {
        NetworkStorage.kitRedeem(player, row);
    }

    /** Applies kits read from the database (the server thread). {@code only} names the one asked for; an empty list for it means it was deleted elsewhere. */
    public static void apply(MinecraftServer server, List<Kit> fresh, @Nullable String only) {
        KitLedger ledger = KitLedger.get(server);
        if (only != null && fresh.isEmpty()) {
            ledger.remove(only);
            return;
        }
        for (Kit kit : fresh) {
            ledger.replace(kit);
        }
        if (only == null) {
            // a full load: kits the database no longer has are gone here too
            java.util.Set<String> keep = new java.util.HashSet<>();
            for (Kit k : fresh) {
                keep.add(k.id);
            }
            for (Kit k : new java.util.ArrayList<>(ledger.all())) {
                if (!keep.contains(k.id)) {
                    ledger.remove(k.id);
                }
            }
        }
    }

    /** Applies a player's rows read from the database (the server thread), then delivers anything owed to them if they are settled in here. */
    public static void applyRows(MinecraftServer server, UUID player, List<KitLedger.Redeem> rows, boolean deliver) {
        KitLedger.get(server).replaceRows(player, rows);
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (deliver && online != null && !KitGrants.waitsForRestore() && !KitGrants.pending(player)) {
            KitGrants.deliver(online);
        }
    }
}
