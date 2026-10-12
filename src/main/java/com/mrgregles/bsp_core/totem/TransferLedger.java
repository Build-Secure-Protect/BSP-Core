package com.mrgregles.bsp_core.totem;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Where players left from when a teleport mod sent them to another server with a totem stored for
 * the journey. If the totem is still waiting in the database after the timeout (they never logged
 * in anywhere), this server places it back at the departure point. Kept in this server's save.
 */
public class TransferLedger extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_transfers";

    /** One departure: when, and the safe spot beside where they stood. */
    public record Departure(long sinceMs, GlobalPos where) {}

    private final Map<UUID, Departure> departures = new HashMap<>();

    public static TransferLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TransferLedger::load, TransferLedger::new, DATA_NAME);
    }

    public void put(UUID player, long sinceMs, GlobalPos where) {
        departures.put(player, new Departure(sinceMs, where));
        setDirty();
    }

    public void remove(UUID player) {
        if (departures.remove(player) != null) {
            setDirty();
        }
    }

    public boolean has(UUID player) {
        return departures.containsKey(player);
    }

    /** Players whose departure is older than {@code ageMs} at {@code nowMs}. */
    public List<UUID> due(long nowMs, long ageMs) {
        List<UUID> out = new ArrayList<>();
        departures.forEach((id, d) -> {
            if (nowMs - d.sinceMs >= ageMs) {
                out.add(id);
            }
        });
        return out;
    }

    public Departure departure(UUID player) {
        return departures.get(player);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        departures.forEach((id, d) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("Player", id);
            t.putLong("Since", d.sinceMs);
            t.putString("Dim", d.where.dimension().location().toString());
            t.putLong("Pos", d.where.pos().asLong());
            list.add(t);
        });
        tag.put("Departures", list);
        return tag;
    }

    public static TransferLedger load(CompoundTag tag) {
        TransferLedger ledger = new TransferLedger();
        for (Tag t : tag.getList("Departures", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            ResourceKey<Level> dim = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, new ResourceLocation(c.getString("Dim")));
            ledger.departures.put(c.getUUID("Player"), new Departure(c.getLong("Since"), GlobalPos.of(dim, BlockPos.of(c.getLong("Pos")))));
        }
        return ledger;
    }
}
