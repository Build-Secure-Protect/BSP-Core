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

    // --- identity: where each totem (by id) was last seen, and which instance of it is the real one ---

    public static final int HELD = 0, PLACED = 1, DROPPED = 2;
    private static final String TAG_TOTEMS = "Totems";

    /** The last sighting of a totem: its current instance, the player who had it last, when, as what, and a copy of its item tag. */
    public record Sighting(UUID instance, @Nullable UUID holder, long lastSeen, int where, @Nullable CompoundTag copy) {
    }

    private final Map<UUID, Sighting> totems = new HashMap<>();

    @Nullable
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
        CompoundTag tagCopy = copy != null ? copy.copy() : old == null ? null : old.copy();
        totems.put(id, new Sighting(instance, keep, now, where, tagCopy));
        if (old == null || old.where() != where || !java.util.Objects.equals(old.holder(), keep)) {
            setDirty();
        }
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

    public void forget(UUID id) {
        if (totems.remove(id) != null) {
            setDirty();
        }
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

    public void recordRemoved(GlobalPos pos) {
        boolean changed = false;
        for (Set<GlobalPos> positions : placed.values()) {
            changed |= positions.remove(pos);
        }
        placed.values().removeIf(Set::isEmpty);
        if (changed) {
            tiers.remove(pos);
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
