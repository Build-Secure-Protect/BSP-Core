package com.mrgregles.bsp_core.decoy;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every Decoy Totem on this server: who owns it, how far it reaches and whether it is working
 * right now. Used for the per-player limit and by Totem Compasses, which must be fooled whether
 * or not the decoy's chunk is loaded on the tracker's side of the map.
 */
public class DecoyLedger extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_decoys";

    public record Entry(UUID owner, int range, boolean active) {
    }

    private final Map<GlobalPos, Entry> decoys = new HashMap<>();

    public static DecoyLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(DecoyLedger::load, DecoyLedger::new, DATA_NAME);
    }

    public void put(GlobalPos pos, UUID owner, int range, boolean active) {
        Entry now = new Entry(owner, range, active);
        if (!now.equals(decoys.put(pos, now))) {
            setDirty();
        }
    }

    public void remove(GlobalPos pos) {
        if (decoys.remove(pos) != null) {
            setDirty();
        }
    }

    public int count(UUID owner) {
        int n = 0;
        for (Entry e : decoys.values()) {
            if (e.owner.equals(owner)) {
                n++;
            }
        }
        return n;
    }

    /**
     * The decoy a rival-tracking compass should point at instead of a real totem: the nearest
     * working decoy, not owned by the tracker, whose range the tracker is standing in. Null if none.
     */
    @Nullable
    public BlockPos fool(ResourceKey<Level> dimension, BlockPos tracker, UUID trackerId) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (Map.Entry<GlobalPos, Entry> e : decoys.entrySet()) {
            Entry d = e.getValue();
            if (!d.active || d.owner.equals(trackerId) || e.getKey().dimension() != dimension) {
                continue;
            }
            double dist = e.getKey().pos().distSqr(tracker);
            if (dist <= (double) d.range * d.range && dist < bestDist) {
                bestDist = dist;
                best = e.getKey().pos();
            }
        }
        return best;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        decoys.forEach((pos, d) -> {
            CompoundTag e = new CompoundTag();
            e.putString("Dim", pos.dimension().location().toString());
            e.putLong("Pos", pos.pos().asLong());
            e.putUUID("Owner", d.owner);
            e.putInt("Range", d.range);
            e.putBoolean("Active", d.active);
            list.add(e);
        });
        tag.put("Decoys", list);
        return tag;
    }

    private static DecoyLedger load(CompoundTag tag) {
        DecoyLedger ledger = new DecoyLedger();
        for (Tag raw : tag.getList("Decoys", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) raw;
            ResourceLocation dim = ResourceLocation.tryParse(e.getString("Dim"));
            if (dim != null && e.hasUUID("Owner")) {
                ledger.decoys.put(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(e.getLong("Pos"))),
                        new Entry(e.getUUID("Owner"), e.getInt("Range"), e.getBoolean("Active")));
            }
        }
        return ledger;
    }
}
