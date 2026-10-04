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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Which Shatter Coin Factories each player owns on this server, for the per-player limit. */
public class FactoryLedger extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_factories";
    private final Map<UUID, Set<GlobalPos>> owned = new HashMap<>();

    public static FactoryLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FactoryLedger::load, FactoryLedger::new, DATA_NAME);
    }

    /** Slices the player owns: those on this server plus, with network storage on, those on every other server. */
    public int count(UUID owner) {
        return owned.getOrDefault(owner, Set.of()).size() + com.mrgregles.bsp_core.storage.NetworkStorage.slicesElsewhere(owner);
    }

    /** Slices the player owns on this server only. */
    public int localCount(UUID owner) {
        return owned.getOrDefault(owner, Set.of()).size();
    }

    /** Every factory slice on this server, by owner. */
    public Map<UUID, Set<GlobalPos>> all() {
        return java.util.Collections.unmodifiableMap(owned);
    }

    public void add(UUID owner, GlobalPos pos) {
        remove(pos);
        owned.computeIfAbsent(owner, k -> new HashSet<>()).add(pos);
        setDirty();
        com.mrgregles.bsp_core.storage.NetworkStorage.sliceAdded(owner, pos);
    }

    public void remove(GlobalPos pos) {
        boolean changed = false;
        for (Set<GlobalPos> set : owned.values()) {
            changed |= set.remove(pos);
        }
        owned.values().removeIf(Set::isEmpty);
        if (changed) {
            setDirty();
            com.mrgregles.bsp_core.storage.NetworkStorage.sliceRemoved(pos);
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        owned.forEach((owner, set) -> set.forEach(pos -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Owner", owner);
            e.putString("Dim", pos.dimension().location().toString());
            e.putLong("Pos", pos.pos().asLong());
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
            ledger.owned.computeIfAbsent(e.getUUID("Owner"), k -> new HashSet<>())
                    .add(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(e.getLong("Pos"))));
        }
        return ledger;
    }
}
