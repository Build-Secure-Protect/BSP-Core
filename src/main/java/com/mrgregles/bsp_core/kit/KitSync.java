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

    /** Posts {@link com.mrgregles.bsp_core.api.event.KitEvent.Changed} on the server thread. */
    static void changed(MinecraftServer server, Kit kit, boolean deleted, boolean heard) {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.mrgregles.bsp_core.api.event.KitEvent.Changed(server, kit.id, kit.name, deleted, heard));
    }

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
        apply(server, fresh, only, true);
    }

    /**
     * As {@link #apply(MinecraftServer, List, String)}; {@code prune} false keeps kits only this server has (a server whose local
     * records are not in the database yet: {@code /bsp storage migrate} is still to copy them up).
     */
    public static void apply(MinecraftServer server, List<Kit> fresh, @Nullable String only, boolean prune) {
        apply(server, fresh, only, prune, Long.MAX_VALUE);
    }

    /** A count that moves on with every kit saved or deleted on this server, and the mark each kit was last changed at here. */
    private static final java.util.concurrent.atomic.AtomicLong MARK = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.Map<String, Long> CHANGED_HERE = new java.util.concurrent.ConcurrentHashMap<>();

    /** The mark now: a database read started now is older than any save or delete made here after it. */
    public static long mark() {
        return MARK.get();
    }

    /** Records that kit {@code id} was saved or deleted on this server now. */
    static void changedHere(String id) {
        CHANGED_HERE.put(id, MARK.incrementAndGet());
    }

    /** True when kit {@code id} was saved or deleted here after {@code mark}: a read made before then must not undo it. */
    static boolean changedHereSince(String id, long mark) {
        Long at = CHANGED_HERE.get(id);
        return at != null && at > mark;
    }

    /**
     * As {@link #apply(MinecraftServer, List, String, boolean)} for a read started at {@code asked} ({@link #mark}): a kit saved or
     * deleted on this server since is left as this server has it (the read came before this server's own write reached the
     * database; the next read brings the network's record).
     */
    public static void apply(MinecraftServer server, List<Kit> fresh, @Nullable String only, boolean prune, long asked) {
        KitLedger ledger = KitLedger.get(server);
        if (only != null && fresh.isEmpty()) {
            if (changedHereSince(only, asked)) {
                return; // the read found nothing because this server's own write had not reached the database yet
            }
            Kit gone = ledger.remove(only);
            if (gone != null) {
                changed(server, gone, true, true);
            }
            return;
        }
        for (Kit kit : fresh) {
            if (changedHereSince(kit.id, asked)) {
                continue;
            }
            Kit old = ledger.kit(kit.id);
            ledger.replace(kit);
            // only an edit is news: a re-read of the same record (this server's own write coming back, the five-minute catch-up,
            // a taken count moving on) carries the same stamp
            if (old == null || old.edited != kit.edited) {
                changed(server, kit, false, true);
            }
        }
        if (only == null && prune) {
            // a full load: kits the database no longer has are gone here too
            java.util.Set<String> keep = new java.util.HashSet<>();
            for (Kit k : fresh) {
                keep.add(k.id);
            }
            for (Kit k : new java.util.ArrayList<>(ledger.all())) {
                if (!keep.contains(k.id) && !changedHereSince(k.id, asked)) { // a kit made here a moment ago is not in the read yet
                    ledger.remove(k.id);
                    changed(server, k, true, true);
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
