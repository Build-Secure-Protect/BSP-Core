package com.mrgregles.bsp_core.data;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-wide record of Shatter Totems: who has been granted one, and where each owner's totems
 * currently stand as blocks.
 *
 * <p>Stored in the overworld's {@code data/bsp_core_totems.dat}. This is the on-server source of
 * truth until the cross-network MySQL ledger exists.
 */
public class TotemLedger extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_totems";
    private static final String TAG_GRANTED = "Granted";
    private static final String TAG_PLACED = "Placed";

    private final Set<UUID> granted = new HashSet<>();
    private final Map<UUID, Set<GlobalPos>> placed = new HashMap<>();
    /** Tier (0 = I) of each placed totem, so scores can be worked out while its chunk is unloaded. */
    private final Map<GlobalPos, Integer> tiers = new HashMap<>();
    /** Upgrade levels of each placed totem (non-zero only), so other mods can read them without loading the totem's chunk. */
    private final Map<GlobalPos, Map<String, Integer>> levels = new HashMap<>();

    // --- identity: where each totem (by id) was last seen, and which instance of it is the real one ---

    /** Where a totem was last seen. IN_TRANSIT: taken from its holder for a server transfer and travelling through the database; never "lost". */
    public static final int HELD = 0, PLACED = 1, DROPPED = 2, IN_TRANSIT = 3;
    /** A totem that no longer exists (destroyed in creative, wiped, forgotten, a season ended): any copy of it that turns up is a husk. */
    public static final int RETIRED = 4;
    private static final String TAG_TOTEMS = "Totems";

    /** The last sighting of a totem: its current instance, the player who had it last, when, as what, and a copy of its item tag. */
    public record Sighting(UUID instance, @Nullable UUID holder, long lastSeen, int where, @Nullable CompoundTag copy) {
    }

    private final Map<UUID, Sighting> totems = new HashMap<>();

    @Nullable
    /** Totems owned by {@code owner} lying in the world on this server, seen within {@code maxAge} ticks (a dropped totem reports every second until it places itself). */
    public int droppedFor(UUID owner, long now, long maxAge) {
        int n = 0;
        for (Sighting s : totems.values()) {
            if (s.where() == DROPPED && owner.equals(s.holder()) && now - s.lastSeen() <= maxAge) {
                n++;
            }
        }
        return n;
    }

    public UUID currentInstance(UUID id) {
        Sighting s = totems.get(id);
        return s == null ? null : s.instance();
    }

    @Nullable
    public Sighting sighting(UUID id) {
        return totems.get(id);
    }

    /**
     * A totem was seen: in a player's inventory (HELD, holder = that player), as a block (PLACED) or lying in the world (DROPPED).
     * A sighting of an instance that is not the current one is ignored: that copy is stale and the caller spends it.
     */
    public void seen(UUID id, UUID instance, @Nullable UUID holder, int where, @Nullable CompoundTag copy, long now) {
        Sighting old = totems.get(id);
        if (old != null && !old.instance().equals(instance)) {
            return;
        }
        UUID keep = holder != null ? holder : old == null ? null : old.holder();
        // a held totem is seen every tick: its tag is copied only when it differs from the copy kept (comparing allocates nothing)
        CompoundTag tagCopy = copy == null ? old == null ? null : old.copy() : old != null && copy.equals(old.copy()) ? old.copy() : copy.copy();
        totems.put(id, new Sighting(instance, keep, now, where, tagCopy));
        if (old == null || old.where() != where || !java.util.Objects.equals(old.holder(), keep)) {
            setDirty();
        }
    }

    /** A transfer took the totem from {@code holder}: it is on its way to another server (or held for a cancelled teleport), not lost. */
    public void inTransit(UUID id, UUID instance, @Nullable UUID holder, @Nullable CompoundTag copy, long now) {
        Sighting old = totems.get(id);
        totems.put(id, new Sighting(instance, holder != null ? holder : old == null ? null : old.holder(), now, IN_TRANSIT, copy != null ? copy.copy() : old == null ? null : old.copy()));
        setDirty();
    }

    /**
     * A totem arrived through the database (a transfer's hand-back): the instance it carries is the real one, whatever this
     * server's ledger remembered, because another server may have reissued it meanwhile. Counts as held by {@code holder} now.
     */
    public boolean arrived(UUID id, UUID instance, UUID holder, @Nullable CompoundTag copy, long now) {
        Sighting old = totems.get(id);
        if (old != null && old.where() == RETIRED) {
            return false; // retired here (wiped, a season ended): the copy that arrived is a husk, never live again
        }
        totems.put(id, new Sighting(instance, holder, now, HELD, copy != null ? copy.copy() : null));
        setDirty();
        return true;
    }

    /** Totems last seen in a player's hands more than {@code maxAge} ticks ago and nowhere since: stored away, or carried off. */
    public List<UUID> lostHeld(long now, long maxAge) {
        List<UUID> out = new ArrayList<>();
        totems.forEach((id, s) -> {
            if (s.where() == HELD && s.holder() != null && now - s.lastSeen() > maxAge) {
                out.add(id);
            }
        });
        return out;
    }

    /** Gives a lost totem a new instance, so the copy that went missing is spent, and counts it as held again now. */
    public Sighting reissue(UUID id, long now) {
        Sighting old = totems.get(id);
        Sighting fresh = new Sighting(UUID.randomUUID(), old == null ? null : old.holder(), now, HELD, old == null ? null : old.copy());
        totems.put(id, fresh);
        setDirty();
        return fresh;
    }

    /**
     * Whether a copy carrying {@code instance} is stale: the ledger knows the totem and its current instance is another one (it was
     * reissued, or the totem was retired). Such a copy is spent wherever it turns up: in an inventory, on the ground, being placed.
     */
    public boolean stale(@Nullable UUID id, @Nullable UUID instance) {
        if (id == null || instance == null) {
            return false;
        }
        Sighting s = totems.get(id);
        return s != null && !s.instance().equals(instance);
    }

    /** Retires every totem last seen with this player (held, dropped by them, in transit); returns how many. */
    public int forgetHeldBy(UUID player) {
        int n = 0;
        for (var e : totems.entrySet()) {
            if (player.equals(e.getValue().holder()) && e.getValue().where() != RETIRED) {
                e.setValue(retired());
                n++;
            }
        }
        if (n > 0) {
            setDirty();
        }
        return n;
    }

    /**
     * Retires a totem: it no longer exists. Kept as a marker with a fresh instance rather than deleted, so a copy of it left in a
     * chest, on the ground or in an old inventory is a husk when it turns up, never a live totem again.
     */
    public void forget(UUID id) {
        Sighting old = totems.get(id);
        if (old != null && old.where() != RETIRED) {
            totems.put(id, retired());
            setDirty();
        }
    }

    /** Drops what the ledger knows of a totem, so the next copy seen is taken as it is. */
    public void drop(UUID id) {
        if (totems.remove(id) != null) {
            setDirty();
        }
    }

    /** A totem destroyed in creative mode: what was destroyed (its instance, holder and tag) and the retired marker it left. */
    private record CreativeGone(Sighting gone, UUID marker) {
    }

    /** Totems destroyed in creative mode this run. Never saved. */
    private final Map<UUID, CreativeGone> creativeGone = new HashMap<>();

    /**
     * A creative player's totem was destroyed (it was nowhere for a while: the creative screen's bin or item browser): retired, so
     * every copy of it is a husk, but remembered until the server stops, because the creative screen keeps a stack on the cursor
     * only on the client and the same player may put it back ({@link #creativeReturn}).
     */
    public void creativeDestroyed(UUID id, UUID holder) {
        Sighting old = totems.get(id);
        retire(id);
        if (old != null && old.where() != RETIRED) {
            creativeGone.put(id, new CreativeGone(new Sighting(old.instance(), holder, old.lastSeen(), HELD, old.copy()), totems.get(id).instance()));
        }
    }

    /**
     * Whether {@code player}'s copy carrying {@code instance} is the totem they destroyed in creative mode coming back off their
     * cursor; if so it is live and held by them again. Only the same instance in the same player's hands, and only while the ledger
     * still holds the marker the deletion left (nothing else has happened to the totem since): any other copy stays a husk.
     */
    public boolean creativeReturn(UUID id, @Nullable UUID instance, UUID player, long now) {
        CreativeGone entry = creativeGone.get(id);
        if (entry == null || !entry.gone().instance().equals(instance) || !player.equals(entry.gone().holder())) {
            return false;
        }
        creativeGone.remove(id);
        Sighting marker = totems.get(id);
        if (marker == null || marker.where() != RETIRED || !marker.instance().equals(entry.marker())) {
            return false;
        }
        Sighting gone = entry.gone();
        totems.put(id, new Sighting(gone.instance(), player, now, HELD, gone.copy()));
        setDirty();
        return true;
    }

    /**
     * A placed totem's block went without anyone taking the totem (a world edit, a command, a mod removing blocks): it counts as
     * lost from its owner's hands, so they get it back (now if they are online, else at their next login), with {@code copy} (its
     * owner and upgrades as they stood). A pickup or a steal that follows at once is seen in a hand first and nothing is reissued.
     */
    public void lostFromPlace(UUID id, UUID instance, UUID owner, @Nullable CompoundTag copy) {
        Sighting old = totems.get(id);
        if (old == null || !old.instance().equals(instance) || old.where() != PLACED) {
            return; // picked up, dropped, retired or reissued already: not this block's to give back
        }
        totems.put(id, new Sighting(instance, owner, 0, HELD, copy != null ? copy.copy() : old.copy()));
        setDirty();
    }

    /** Retires one totem whether or not this server has seen it (a totem destroyed on another server: its copies are husks here too). */
    public void retire(UUID id) {
        Sighting old = totems.get(id);
        if (old == null || old.where() != RETIRED) {
            totems.put(id, retired());
            setDirty();
        }
    }

    /** Retires every totem this server knows (the season's end removes them all). */
    public void retireAll() {
        totems.replaceAll((id, s) -> s.where() == RETIRED ? s : retired());
        setDirty();
    }

    private static Sighting retired() {
        return new Sighting(UUID.randomUUID(), null, 0, RETIRED, null);
    }

    public static TotemLedger get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Overworld not loaded; cannot access the totem ledger");
        }
        return overworld.getDataStorage().computeIfAbsent(TotemLedger::load, TotemLedger::new, DATA_NAME);
    }

    // --- grants ---

    public boolean hasBeenGranted(UUID player) {
        return granted.contains(player);
    }

    public void markGranted(UUID player) {
        if (granted.add(player)) {
            setDirty();
        }
    }

    /** Every player this server has recorded a grant for. */
    public Set<UUID> allGranted() {
        return Collections.unmodifiableSet(granted);
    }

    /** Admin use: allow a player to be granted again. */
    public boolean clearGranted(UUID player) {
        boolean removed = granted.remove(player);
        if (removed) {
            setDirty();
        }
        return removed;
    }

    // --- placed totems ---

    public void recordPlaced(UUID owner, GlobalPos pos) {
        recordPlaced(owner, pos, tierAt(pos));
    }

    /** Tier of the placed totem at {@code pos}, 0 (I) if unknown. */
    public int tierAt(GlobalPos pos) {
        return tiers.getOrDefault(pos, 0);
    }

    public void recordPlaced(UUID owner, GlobalPos pos, int tier) {
        if (placedFor(owner).contains(pos) && tierAt(pos) == tier) {
            return; // nothing changed
        }
        recordRemoved(pos);
        tiers.put(pos, tier);
        com.mrgregles.bsp_core.score.ScoreService.markDirty();
        placed.computeIfAbsent(owner, k -> new HashSet<>()).add(pos);
        setDirty();
        com.mrgregles.bsp_core.storage.NetworkStorage.totemPlaced(owner, pos);
    }

    /** Keeps the upgrade levels of the placed totem at {@code pos} (non-zero levels by upgrade key). */
    public void recordLevels(GlobalPos pos, Map<String, Integer> now) {
        if (!tiers.containsKey(pos) || now.equals(levels.get(pos))) {
            return;
        }
        levels.put(pos, Map.copyOf(now));
        setDirty();
        UUID owner = ownerAt(pos);
        if (owner != null) {
            com.mrgregles.bsp_core.storage.NetworkStorage.totemLevels(owner, pos, now); // the network's servers see them too (request R11)
        }
    }

    /** Who owns the placed totem at {@code pos}, or null when none is recorded there. */
    @javax.annotation.Nullable
    public UUID ownerAt(GlobalPos pos) {
        for (Map.Entry<UUID, Set<GlobalPos>> e : placed.entrySet()) {
            if (e.getValue().contains(pos)) {
                return e.getKey();
            }
        }
        return null;
    }

    /** Every placed totem whose levels are known, with its owner and levels: written to the network database at connect and after a migration. */
    public java.util.List<com.mrgregles.bsp_core.storage.NetworkStorage.TotemLevels> levelRows() {
        java.util.List<com.mrgregles.bsp_core.storage.NetworkStorage.TotemLevels> out = new java.util.ArrayList<>();
        placed.forEach((owner, set) -> {
            for (GlobalPos pos : set) {
                Map<String, Integer> l = levels.get(pos);
                if (l != null) {
                    out.add(new com.mrgregles.bsp_core.storage.NetworkStorage.TotemLevels(owner, pos, com.mrgregles.bsp_core.storage.RemoteTotemLevels.encode(l)));
                }
            }
        });
        return out;
    }

    /** Whether the levels of the totem at {@code pos} are known (false only for totems not loaded since the levels were first kept). */
    public boolean hasLevels(GlobalPos pos) {
        return levels.containsKey(pos);
    }

    /** The level of upgrade {@code key} on the placed totem at {@code pos}; 0 when it has none or is unknown. */
    public int levelAt(GlobalPos pos, String key) {
        Map<String, Integer> l = levels.get(pos);
        return l == null ? 0 : l.getOrDefault(key, 0);
    }

    public void recordRemoved(GlobalPos pos) {
        boolean changed = false;
        for (Set<GlobalPos> positions : placed.values()) {
            changed |= positions.remove(pos);
        }
        placed.values().removeIf(Set::isEmpty);
        if (changed) {
            tiers.remove(pos);
            levels.remove(pos);
            com.mrgregles.bsp_core.score.ScoreService.markDirty();
            setDirty();
            com.mrgregles.bsp_core.storage.NetworkStorage.totemRemoved(pos);
        }
    }

    /** Every placed totem on this server, by owner. */
    public Map<UUID, Set<GlobalPos>> allPlaced() {
        return Collections.unmodifiableMap(placed);
    }

    public Set<GlobalPos> placedFor(UUID owner) {
        return Collections.unmodifiableSet(placed.getOrDefault(owner, Set.of()));
    }

    // --- persistence ---

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag grantedList = new ListTag();
        for (UUID id : granted) {
            grantedList.add(NbtUtils.createUUID(id));
        }
        tag.put(TAG_GRANTED, grantedList);

        ListTag placedList = new ListTag();
        placed.forEach((owner, positions) -> positions.forEach(pos -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Owner", owner);
            entry.putString("Dim", pos.dimension().location().toString());
            entry.putLong("Pos", pos.pos().asLong());
            entry.putInt("Tier", tierAt(pos));
            Map<String, Integer> l = levels.get(pos);
            if (l != null) {
                CompoundTag lt = new CompoundTag();
                l.forEach(lt::putInt);
                entry.put("Levels", lt);
            }
            placedList.add(entry);
        }));
        tag.put(TAG_PLACED, placedList);
        ListTag totemList = new ListTag();
        totems.forEach((id, s) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            entry.putUUID("Instance", s.instance());
            if (s.holder() != null) {
                entry.putUUID("Holder", s.holder());
            }
            entry.putLong("LastSeen", s.lastSeen());
            entry.putInt("Where", s.where());
            if (s.copy() != null) {
                entry.put("Copy", s.copy());
            }
            totemList.add(entry);
        });
        tag.put(TAG_TOTEMS, totemList);
        return tag;
    }

    private static TotemLedger load(CompoundTag tag) {
        TotemLedger ledger = new TotemLedger();
        for (Tag entry : tag.getList(TAG_GRANTED, Tag.TAG_INT_ARRAY)) {
            ledger.granted.add(NbtUtils.loadUUID(entry));
        }
        for (Tag raw : tag.getList(TAG_PLACED, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            ResourceLocation dim = ResourceLocation.tryParse(entry.getString("Dim"));
            if (dim == null || !entry.hasUUID("Owner")) {
                continue;
            }
            GlobalPos pos = GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(entry.getLong("Pos")));
            ledger.placed.computeIfAbsent(entry.getUUID("Owner"), k -> new HashSet<>()).add(pos);
            ledger.tiers.put(pos, entry.getInt("Tier"));
            if (entry.contains("Levels", Tag.TAG_COMPOUND)) {
                CompoundTag lt = entry.getCompound("Levels");
                Map<String, Integer> l = new HashMap<>();
                for (String key : lt.getAllKeys()) {
                    l.put(key, lt.getInt(key));
                }
                ledger.levels.put(pos, Map.copyOf(l));
            }
        }
        for (Tag raw : tag.getList(TAG_TOTEMS, Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            if (!entry.hasUUID("Id") || !entry.hasUUID("Instance")) {
                continue;
            }
            ledger.totems.put(entry.getUUID("Id"), new Sighting(entry.getUUID("Instance"), entry.hasUUID("Holder") ? entry.getUUID("Holder") : null,
                    entry.getLong("LastSeen"), entry.getInt("Where"), entry.contains("Copy") ? entry.getCompound("Copy") : null));
        }
        return ledger;
    }
}
