package com.mrgregles.bsp_core.tracking;

import com.mrgregles.bsp_core.registry.ModBlocks;
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
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every Remote Control Transmitter placed on this server and its owner: for the per-player limit ({@code transmittersPerPlayer}) and for
 * finding the transmitter that lets a player operate a machine. An entry whose block is gone (checked where its chunk is loaded) is
 * dropped when next counted.
 */
public final class TransmitterLedger extends SavedData {
    private static final String DATA_NAME = "bsp_core_transmitters";
    private final Map<GlobalPos, UUID> placed = new HashMap<>();

    public static TransmitterLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TransmitterLedger::load, TransmitterLedger::new, DATA_NAME);
    }

    public void add(ServerLevel level, BlockPos pos, UUID owner) {
        placed.put(GlobalPos.of(level.dimension(), pos.immutable()), owner);
        setDirty();
    }

    public void remove(ServerLevel level, BlockPos pos) {
        if (placed.remove(GlobalPos.of(level.dimension(), pos)) != null) {
            setDirty();
        }
    }

    /** The owner's transmitters here that still stand (gone ones are dropped as a side effect). */
    public List<GlobalPos> of(MinecraftServer server, UUID owner) {
        prune(server);
        List<GlobalPos> out = new ArrayList<>();
        placed.forEach((pos, o) -> {
            if (o.equals(owner)) {
                out.add(pos);
            }
        });
        return out;
    }

    /** Every transmitter here, with its owner (read-only, not a copy: do not keep it). */
    public Map<GlobalPos, UUID> all(MinecraftServer server) {
        long now = System.currentTimeMillis();
        if (now - prunedMs > 1000) {
            prunedMs = now;
            prune(server);
        }
        return java.util.Collections.unmodifiableMap(placed);
    }

    private long prunedMs;

    private void prune(MinecraftServer server) {
        if (placed.keySet().removeIf(g -> {
            ServerLevel level = server.getLevel(g.dimension());
            return level == null || level.isLoaded(g.pos()) && !level.getBlockState(g.pos()).is(ModBlocks.REMOTE_TRANSMITTER.get());
        })) {
            setDirty();
        }
    }

    public static TransmitterLedger load(CompoundTag tag) {
        TransmitterLedger l = new TransmitterLedger();
        ListTag list = tag.getList("placed", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag c = list.getCompound(i);
            ResourceLocation dim = ResourceLocation.tryParse(c.getString("dim"));
            if (dim != null && c.hasUUID("owner")) {
                l.placed.put(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), NbtUtils.readBlockPos(c.getCompound("pos"))), c.getUUID("owner"));
            }
        }
        return l;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        placed.forEach((g, o) -> {
            CompoundTag c = new CompoundTag();
            c.putString("dim", g.dimension().location().toString());
            c.put("pos", NbtUtils.writeBlockPos(g.pos()));
            c.putUUID("owner", o);
            list.add(c);
        });
        tag.put("placed", list);
        return tag;
    }
}
