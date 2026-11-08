package com.mrgregles.bsp_core.data;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Which Shatter Coin Factories each player owns on this server, for the per-player limit, and when
 * each was placed, so the team limit pauses the newest slices first.
 */
public class FactoryLedger extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_factories";
    /** Owner → slice → wall-clock ms it was placed (0 for slices placed before the time was kept). */
    private final Map<UUID, Map<GlobalPos, Long>> owned = new HashMap<>();

    /**
     * One slice anywhere on the network, in the order the team limit keeps them: oldest first, then
     * by server, dimension and position so every server ranks the same slices the same way.
     */
    public record Slice(long placedMs, String server, String dimension, long pos) implements Comparable<Slice> {
        public static Slice of(long placedMs, String server, GlobalPos at) {
            return new Slice(placedMs, server, at.dimension().location().toString(), at.pos().asLong());
        }

        @Override
        public int compareTo(Slice o) {
            int c = Long.compare(placedMs, o.placedMs);
            if (c == 0) {
                c = server.compareTo(o.server);
            }
            if (c == 0) {
                c = dimension.compareTo(o.dimension);
            }
            return c != 0 ? c : Long.compare(pos, o.pos);
        }
    }

    public static FactoryLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FactoryLedger::load, FactoryLedger::new, DATA_NAME);
    }

    /** Slices the player owns: those on this server plus, with network storage on, those on every other server. */
    public int count(UUID owner) {
        return localCount(owner) + com.mrgregles.bsp_core.storage.NetworkStorage.slicesElsewhere(owner);
    }

    /** Slices the player owns on this server only. */
    public int localCount(UUID owner) {
        return owned.getOrDefault(owner, Map.of()).size();
    }

    /** Every factory slice on this server, by owner. */
    public Map<UUID, Set<GlobalPos>> all() {
        Map<UUID, Set<GlobalPos>> out = new HashMap<>();
        owned.forEach((owner, slices) -> out.put(owner, Collections.unmodifiableSet(slices.keySet())));
        return Collections.unmodifiableMap(out);
    }

    /** Every factory slice on this server with the time it was placed, by owner. A copy. */
    public Map<UUID, Map<GlobalPos, Long>> allWithTimes() {
        Map<UUID, Map<GlobalPos, Long>> out = new HashMap<>();
        owned.forEach((owner, slices) -> out.put(owner, new HashMap<>(slices)));
        return out;
    }

    /** The player's slices on this server and, on a network, on every other server, oldest first. */
    public List<Slice> ranked(UUID owner) {
        String here = com.mrgregles.bsp_core.bank.Bank.serverId();
        List<Slice> out = new ArrayList<>(com.mrgregles.bsp_core.storage.NetworkStorage.slicesElsewhereList(owner));
        owned.getOrDefault(owner, Map.of()).forEach((pos, ms) -> out.add(Slice.of(ms, here, pos)));
        Collections.sort(out);
        return out;
    }

    /** When the slice at {@code pos} was placed, or -1 if it is not in the ledger. */
    public long placedAt(GlobalPos pos) {
        for (Map<GlobalPos, Long> slices : owned.values()) {
            Long ms = slices.get(pos);
            if (ms != null) {
                return ms;
            }
        }
        return -1;
    }

    public void add(UUID owner, GlobalPos pos) {
        add(owner, pos, System.currentTimeMillis());
    }

    public void add(UUID owner, GlobalPos pos, long placedMs) {
        remove(pos);
        owned.computeIfAbsent(owner, k -> new HashMap<>()).put(pos, placedMs);
        setDirty();
        com.mrgregles.bsp_core.storage.NetworkStorage.sliceAdded(owner, pos, placedMs);
    }

    public void remove(GlobalPos pos) {
        boolean changed = false;
        for (Map<GlobalPos, Long> slices : owned.values()) {
            changed |= slices.remove(pos) != null;
        }
        owned.values().removeIf(Map::isEmpty);
        if (changed) {
            setDirty();
            com.mrgregles.bsp_core.storage.NetworkStorage.sliceRemoved(pos);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        owned.forEach((owner, slices) -> slices.forEach((pos, ms) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Owner", owner);
            e.putString("Dim", pos.dimension().location().toString());
            e.putLong("Pos", pos.pos().asLong());
            e.putLong("Placed", ms);
            list.add(e);
        }));
        tag.put("Factories", list);
        return tag;
    }

    private static FactoryLedger load(CompoundTag tag) {
        FactoryLedger ledger = new FactoryLedger();
        for (Tag raw : tag.getList("Factories", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) raw;
            ResourceLocation dim = ResourceLocation.tryParse(e.getString("Dim"));
            if (dim == null || !e.hasUUID("Owner")) {
                continue;
            }
            ledger.owned.computeIfAbsent(e.getUUID("Owner"), k -> new HashMap<>())
                    .put(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(e.getLong("Pos"))), e.getLong("Placed"));
        }
        return ledger;
    }
}
