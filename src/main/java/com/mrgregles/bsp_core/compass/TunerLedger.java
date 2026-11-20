package com.mrgregles.bsp_core.compass;

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
import java.util.Map;
import java.util.UUID;

/** Every placed Compass Tuner on this server and its owner, for the per-player limit ({@code compassTuner.maxPerPlayer}). */
public class TunerLedger extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_compass_tuners";

    private final Map<GlobalPos, UUID> tuners = new HashMap<>();

    public static TunerLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TunerLedger::load, TunerLedger::new, DATA_NAME);
    }

    public void put(GlobalPos pos, UUID owner) {
        if (!owner.equals(tuners.put(pos, owner))) {
            setDirty();
        }
    }

    public void remove(GlobalPos pos) {
        if (tuners.remove(pos) != null) {
            setDirty();
        }
    }

    /**
     * Tuners {@code owner} has placed on this server, first dropping any entry whose spot is loaded and holds no tuner (a block
     * removed without its own removal running, by a world edit for example), so a lost entry never blocks a player for good.
     */
    public int count(MinecraftServer server, UUID owner) {
        tuners.entrySet().removeIf(e -> {
            if (!e.getValue().equals(owner)) {
                return false;
            }
            var level = server.getLevel(e.getKey().dimension());
            boolean gone = level == null || level.isLoaded(e.getKey().pos())
                    && !(level.getBlockEntity(e.getKey().pos()) instanceof CompassTunerBlockEntity);
            if (gone) {
                setDirty();
            }
            return gone;
        });
        return count(owner);
    }

    /** Tuners {@code owner} has placed on this server, as recorded. */
    public int count(UUID owner) {
        int n = 0;
        for (UUID o : tuners.values()) {
            if (o.equals(owner)) {
                n++;
            }
        }
        return n;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        tuners.forEach((pos, owner) -> {
            CompoundTag e = new CompoundTag();
            e.putString("Dim", pos.dimension().location().toString());
            e.putLong("Pos", pos.pos().asLong());
            e.putUUID("Owner", owner);
            list.add(e);
        });
        tag.put("Tuners", list);
        return tag;
    }

    private static TunerLedger load(CompoundTag tag) {
        TunerLedger ledger = new TunerLedger();
        for (Tag raw : tag.getList("Tuners", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) raw;
            ResourceLocation dim = ResourceLocation.tryParse(e.getString("Dim"));
            if (dim != null && e.hasUUID("Owner")) {
                ledger.tuners.put(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(e.getLong("Pos"))), e.getUUID("Owner"));
            }
        }
        return ledger;
    }
}
