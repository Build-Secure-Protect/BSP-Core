package com.mrgregles.bsp_core.chunk;

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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-wide record of the chunks players have chosen to keep loaded: per placed Shatter Totem, and
 * per Totem Projector that is passing a totem's chunk loading on. Kept apart from the block entities
 * so the rules ({@link ChunkLoading}) can be worked out while those blocks are not loaded.
 *
 * <p>Stored in the overworld's {@code data/bsp_core_chunks.dat}.
 */
public class ChunkLedger extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_chunks";

    /** A placed totem with an owner. {@code chunks} are packed chunk positions in the totem's dimension, in the order they were picked. */
    public static final class Totem {
        public UUID owner;
        /** When the owner got this totem (ms); the one held longest is their main totem. */
        public long since;
        public int anchor, survey;
        public final Set<Long> chunks = new LinkedHashSet<>();
    }

    /** A projector that has been fed chunk loading by the generator of the totem at {@code totem}. */
    public static final class Projector {
        public GlobalPos totem;
        /** Being fed right now; its chunks are only kept loaded while this is true. */
        public boolean on;
        public final Set<Long> chunks = new LinkedHashSet<>();
    }

    public final Map<GlobalPos, Totem> totems = new HashMap<>();
    public final Map<GlobalPos, Projector> projectors = new HashMap<>();

    public static ChunkLedger get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Overworld not loaded; cannot access the chunk ledger");
        }
        return overworld.getDataStorage().computeIfAbsent(ChunkLedger::load, ChunkLedger::new, DATA_NAME);
    }

    private static CompoundTag write(GlobalPos pos, Set<Long> chunks) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dim", pos.dimension().location().toString());
        tag.putLong("Pos", pos.pos().asLong());
        tag.putLongArray("Chunks", chunks.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    @Nullable
    private static GlobalPos pos(CompoundTag tag, String dim, String pos) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(dim));
        return id == null ? null : GlobalPos.of(ResourceKey.create(Registries.DIMENSION, id), BlockPos.of(tag.getLong(pos)));
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag totemList = new ListTag();
        totems.forEach((pos, t) -> {
            CompoundTag e = write(pos, t.chunks);
            e.putUUID("Owner", t.owner);
            e.putLong("Since", t.since);
            e.putInt("Anchor", t.anchor);
            e.putInt("Survey", t.survey);
            totemList.add(e);
        });
        tag.put("Totems", totemList);
        ListTag projectorList = new ListTag();
        projectors.forEach((pos, p) -> {
            CompoundTag e = write(pos, p.chunks);
            e.putString("TotemDim", p.totem.dimension().location().toString());
            e.putLong("TotemPos", p.totem.pos().asLong());
            e.putBoolean("On", p.on);
            projectorList.add(e);
        });
        tag.put("Projectors", projectorList);
        return tag;
    }

    private static ChunkLedger load(CompoundTag tag) {
        ChunkLedger ledger = new ChunkLedger();
        for (Tag raw : tag.getList("Totems", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) raw;
            GlobalPos pos = pos(e, "Dim", "Pos");
            if (pos == null || !e.hasUUID("Owner")) {
                continue;
            }
            Totem t = new Totem();
            t.owner = e.getUUID("Owner");
            t.since = e.getLong("Since");
            t.anchor = e.getInt("Anchor");
            t.survey = e.getInt("Survey");
            for (long c : e.getLongArray("Chunks")) {
                t.chunks.add(c);
            }
            ledger.totems.put(pos, t);
        }
        for (Tag raw : tag.getList("Projectors", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) raw;
            GlobalPos pos = pos(e, "Dim", "Pos"), totem = pos(e, "TotemDim", "TotemPos");
            if (pos == null || totem == null) {
                continue;
            }
            Projector p = new Projector();
            p.totem = totem;
            p.on = e.getBoolean("On");
            for (long c : e.getLongArray("Chunks")) {
                p.chunks.add(c);
            }
            ledger.projectors.put(pos, p);
        }
        return ledger;
    }
}
