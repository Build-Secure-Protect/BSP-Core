package com.mrgregles.bsp_core.kit;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * This server's kits and who took which, saved with the overworld. The record on a single server;
 * with network storage it becomes the local copy of the shared tables (a later task). A redeem row
 * is one per player, kit and scope: scope {@code ""} is the network-wide row that the once rule, the
 * cooldown and the owed count live on; a server id is the row of a first-login grant on that server.
 */
public class KitLedger extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_kits";

    /** One player's record for one kit in one scope. */
    public static final class Redeem {
        public final String kit;
        public final String scope;
        /** Times the kit was handed to them (redeems and grants). */
        public int count;
        /** When it last was, wall-clock ms; the cooldown counts from here. */
        public long lastMs;
        /** Kits owed to them that have not been delivered yet (a reward, a grant or a redeem that did not fit). */
        public int owed;

        Redeem(String kit, String scope) {
            this.kit = kit;
            this.scope = scope;
        }

        /** A row as read from the database. */
        public static Redeem of(String kit, String scope, int count, long lastMs, int owed) {
            Redeem r = new Redeem(kit, scope);
            r.count = count;
            r.lastMs = lastMs;
            r.owed = owed;
            return r;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("Kit", kit);
            t.putString("Scope", scope);
            t.putInt("Count", count);
            t.putLong("Last", lastMs);
            t.putInt("Owed", owed);
            return t;
        }

        static Redeem load(CompoundTag t) {
            Redeem r = new Redeem(t.getString("Kit"), t.getString("Scope"));
            r.count = t.getInt("Count");
            r.lastMs = t.getLong("Last");
            r.owed = t.getInt("Owed");
            return r;
        }
    }

    private final Map<String, Kit> kits = new LinkedHashMap<>();
    private final Map<UUID, List<Redeem>> redeems = new HashMap<>();

    public static KitLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(KitLedger::load, KitLedger::new, DATA_NAME);
    }

    @Nullable
    public Kit kit(String id) {
        return kits.get(id);
    }

    /** Every kit, in the order they were made. */
    public Collection<Kit> all() {
        return kits.values();
    }

    /** Puts a kit in place of the one with its id, or adds it. */
    void put(Kit kit) {
        kits.put(kit.id, kit);
        setDirty();
    }

    @Nullable
    Kit remove(String id) {
        Kit k = kits.remove(id);
        if (k != null) {
            for (List<Redeem> rows : redeems.values()) {
                rows.removeIf(r -> r.kit.equals(id));
            }
            setDirty();
        }
        return k;
    }

    void changed() {
        setDirty();
    }

    /** Puts a kit read from the database in place of the local copy, keeping nothing of the old one. */
    void replace(Kit kit) {
        kits.put(kit.id, kit);
        setDirty();
    }

    /** Puts a player's rows read from the database in place of the local ones. */
    void replaceRows(UUID player, List<Redeem> rows) {
        if (rows.isEmpty()) {
            redeems.remove(player);
        } else {
            redeems.put(player, new ArrayList<>(rows));
        }
        setDirty();
    }

    /** Every player with rows (the migration). */
    public Map<UUID, List<Redeem>> allRedeems() {
        return redeems;
    }

    /** The player's row for a kit and scope, or null. */
    @Nullable
    public Redeem redeem(UUID player, String kit, String scope) {
        List<Redeem> rows = redeems.get(player);
        if (rows == null) {
            return null;
        }
        for (Redeem r : rows) {
            if (r.kit.equals(kit) && r.scope.equals(scope)) {
                return r;
            }
        }
        return null;
    }

    /** The player's row for a kit and scope, made if there was none. */
    Redeem redeemOrNew(UUID player, String kit, String scope) {
        Redeem r = redeem(player, kit, scope);
        if (r == null) {
            r = new Redeem(kit, scope);
            redeems.computeIfAbsent(player, k -> new ArrayList<>()).add(r);
            setDirty();
        }
        return r;
    }

    /** All of a player's rows. */
    public List<Redeem> redeems(UUID player) {
        List<Redeem> rows = redeems.get(player);
        return rows == null ? List.of() : List.copyOf(rows);
    }

    /** Forgets a player's rows for one kit, or for every kit when {@code kit} is null. Returns how many went. */
    int reset(UUID player, @Nullable String kit) {
        List<Redeem> rows = redeems.get(player);
        if (rows == null) {
            return 0;
        }
        int before = rows.size();
        rows.removeIf(r -> kit == null || r.kit.equals(kit));
        if (rows.isEmpty()) {
            redeems.remove(player);
        }
        if (rows.size() != before) {
            setDirty();
        }
        return before - rows.size();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Kit k : kits.values()) {
            list.add(k.save());
        }
        tag.put("Kits", list);
        ListTag players = new ListTag();
        for (var e : redeems.entrySet()) {
            CompoundTag p = new CompoundTag();
            p.putUUID("Id", e.getKey());
            ListTag rows = new ListTag();
            for (Redeem r : e.getValue()) {
                rows.add(r.save());
            }
            p.put("Rows", rows);
            players.add(p);
        }
        tag.put("Redeems", players);
        return tag;
    }

    public static KitLedger load(CompoundTag tag) {
        KitLedger l = new KitLedger();
        for (Tag x : tag.getList("Kits", Tag.TAG_COMPOUND)) {
            Kit k = Kit.load((CompoundTag) x);
            if (!k.id.isEmpty()) {
                l.kits.put(k.id, k);
            }
        }
        for (Tag x : tag.getList("Redeems", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) x;
            List<Redeem> rows = new ArrayList<>();
            for (Tag y : p.getList("Rows", Tag.TAG_COMPOUND)) {
                rows.add(Redeem.load((CompoundTag) y));
            }
            l.redeems.put(p.getUUID("Id"), rows);
        }
        return l;
    }
}
