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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
        recordRemoved(pos);
        placed.computeIfAbsent(owner, k -> new HashSet<>()).add(pos);
        setDirty();
    }

    public void recordRemoved(GlobalPos pos) {
        boolean changed = false;
        for (Set<GlobalPos> positions : placed.values()) {
            changed |= positions.remove(pos);
        }
        placed.values().removeIf(Set::isEmpty);
        if (changed) {
            setDirty();
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
            placedList.add(entry);
        }));
        tag.put(TAG_PLACED, placedList);
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
        }
        return ledger;
    }
}
