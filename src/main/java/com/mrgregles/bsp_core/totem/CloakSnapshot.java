package com.mrgregles.bsp_core.totem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a Cloaking cube shows outsiders: the land as the world generated it, made by {@link Builder} from the
 * level's own generator when Cloaking is first switched on (or its radius changes), kept as a palette and an
 * index per position. {@link #capture} copies what stands there instead, the fallback when generating fails.
 */
public final class CloakSnapshot {
    /**
     * Raised when what a snapshot should hold changes; a loaded snapshot of an older version is made again. 2: generated land;
     * 3: with the decoration step (trees, plants, ores, structures, snow and ice). Bump it with every change to the builder.
     */
    public static final int VERSION = 3;
    public final BlockPos origin;
    public final int radius;
    public final int version;
    private final List<BlockState> palette;
    private final short[] cells;

    private CloakSnapshot(BlockPos origin, int radius, List<BlockState> palette, short[] cells) {
        this(origin, radius, palette, cells, VERSION);
    }

    private CloakSnapshot(BlockPos origin, int radius, List<BlockState> palette, short[] cells, int version) {
        this.origin = origin;
        this.radius = radius;
        this.version = version;
        this.palette = palette;
        this.cells = cells;
    }

    public int side() {
        return radius * 2 + 1;
    }

    public boolean contains(BlockPos pos) {
        return Math.abs(pos.getX() - origin.getX()) <= radius && Math.abs(pos.getY() - origin.getY()) <= radius && Math.abs(pos.getZ() - origin.getZ()) <= radius;
    }

    private int index(int dx, int dy, int dz) {
        int s = side();
        return ((dy + radius) * s + (dz + radius)) * s + (dx + radius);
    }

    public BlockState at(BlockPos pos) {
        return palette.get(cells[index(pos.getX() - origin.getX(), pos.getY() - origin.getY(), pos.getZ() - origin.getZ())]);
    }

    /** Copies the cube around {@code origin} out of the level. Block entities are not copied: their blocks show, their contents do not. */
    public static CloakSnapshot capture(Level level, BlockPos origin, int radius) {
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> ids = new HashMap<>();
        int s = radius * 2 + 1;
        short[] cells = new short[s * s * s];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int i = 0;
        for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    p.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState st = level.isLoaded(p) ? level.getBlockState(p) : Blocks.AIR.defaultBlockState();
                    Integer id = ids.get(st);
                    if (id == null) {
                        id = palette.size();
                        palette.add(st);
                        ids.put(st, id);
                    }
                    cells[i++] = (short) (int) id;
                }
            }
        }
        return new CloakSnapshot(origin.immutable(), radius, palette, cells);
    }

    /**
     * Makes the snapshot from the world's own generator: the land as it was generated (biomes, noise terrain, surface, then the
     * decoration step with trees, plants, ores, structures, snow and ice; no caves), so outsiders see what the world would have
     * put where the base stands. One chunk column at a time is noise-filled on a worker
     * thread and polled each {@link #step()}, never waited for (the worker asks the server thread for structure data, so waiting
     * would deadlock); surfaces are built one chunk a step on the server thread; {@link #result()} is set when every step is done. Falls back to a copy of what stands there if the
     * generator refuses (a modded generator, say).
     */
    public static final class Builder {
        private final net.minecraft.server.level.ServerLevel level;
        private final BlockPos origin;
        private final int radius;
        private final int cx0, cx1, cz0, cz1;
        private final Map<Long, net.minecraft.world.level.chunk.ProtoChunk> pool = new HashMap<>();
        private final List<net.minecraft.world.level.ChunkPos> noiseTodo = new ArrayList<>(), surfaceTodo = new ArrayList<>(), decorateTodo = new ArrayList<>();
        private boolean decorationWarned;
        /** The chunk column being noise-filled on a worker thread; polled, never joined, because the worker may ask the server thread for structure data. */
        @Nullable
        private java.util.concurrent.CompletableFuture<net.minecraft.world.level.chunk.ChunkAccess> pending;
        @Nullable
        private CloakSnapshot result;
        private boolean failed;

        public Builder(net.minecraft.server.level.ServerLevel level, BlockPos origin, int radius) {
            this.level = level;
            this.origin = origin.immutable();
            this.radius = radius;
            cx0 = net.minecraft.core.SectionPos.blockToSectionCoord(origin.getX() - radius);
            cx1 = net.minecraft.core.SectionPos.blockToSectionCoord(origin.getX() + radius);
            cz0 = net.minecraft.core.SectionPos.blockToSectionCoord(origin.getZ() - radius);
            cz1 = net.minecraft.core.SectionPos.blockToSectionCoord(origin.getZ() + radius);
            for (int cz = cz0 - 1; cz <= cz1 + 1; cz++) {
                for (int cx = cx0 - 1; cx <= cx1 + 1; cx++) {
                    noiseTodo.add(new net.minecraft.world.level.ChunkPos(cx, cz)); // the ring of one is there for biome sampling at the edges
                }
            }
            for (int cz = cz0; cz <= cz1; cz++) {
                for (int cx = cx0; cx <= cx1; cx++) {
                    surfaceTodo.add(new net.minecraft.world.level.ChunkPos(cx, cz));
                    decorateTodo.add(new net.minecraft.world.level.ChunkPos(cx, cz));
                }
            }
        }

        /** The 3 x 3 of pool chunks around {@code cp}, in the order a region wants them. */
        private List<net.minecraft.world.level.chunk.ChunkAccess> around(net.minecraft.world.level.ChunkPos cp) {
            List<net.minecraft.world.level.chunk.ChunkAccess> cache = new ArrayList<>();
            for (int cz = cp.z - 1; cz <= cp.z + 1; cz++) {
                for (int cx = cp.x - 1; cx <= cp.x + 1; cx++) {
                    cache.add(pool.get(net.minecraft.world.level.ChunkPos.asLong(cx, cz)));
                }
            }
            return cache;
        }

        /**
         * The generator's decoration step on one chunk: trees, plants, ores, the structures that stood there, and the top layer of
         * snow and ice, all from the seed, so the land looks like its surroundings. If the step refuses (a modded feature, say), the
         * top layer alone is tried, so at least snow and ice match; failing that the chunk stays bare.
         */
        private void decorate(net.minecraft.world.level.ChunkPos cp, net.minecraft.world.level.chunk.ChunkGenerator gen, net.minecraft.world.level.StructureManager structures) {
            var chunk = pool.get(cp.toLong());
            var region = new net.minecraft.server.level.WorldGenRegion(level, around(cp), net.minecraft.world.level.chunk.ChunkStatus.FEATURES, 1);
            net.minecraft.world.level.levelgen.Heightmap.primeHeightmaps(chunk, java.util.EnumSet.of(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE));
            try {
                gen.applyBiomeDecoration(region, chunk, structures);
                return;
            } catch (RuntimeException e) {
                if (!decorationWarned) {
                    decorationWarned = true;
                    com.mrgregles.bsp_core.BSPCore.LOGGER.warn("Cloaking could not decorate the regenerated land at {} ({}); placing snow and ice only", cp, e.toString());
                }
            }
            try {
                var random = net.minecraft.util.RandomSource.create(level.getSeed() ^ cp.toLong());
                var origin = new BlockPos(cp.getMinBlockX(), level.getMinBuildHeight(), cp.getMinBlockZ());
                net.minecraft.world.level.levelgen.feature.Feature.FREEZE_TOP_LAYER.place(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.INSTANCE, region, gen, random, origin);
            } catch (RuntimeException ignored) {
                // bare land for this chunk
            }
        }

        public BlockPos origin() {
            return origin;
        }

        public int radius() {
            return radius;
        }

        /** Does one chunk's work; returns true when the snapshot is ready. */
        public boolean step() {
            if (result != null) {
                return true;
            }
            try {
                var source = level.getChunkSource();
                var gen = source.getGenerator();
                var random = source.randomState();
                var structures = level.structureManager();
                if (pending != null) {
                    if (!pending.isDone()) {
                        return false; // still on the worker
                    }
                    var done = (net.minecraft.world.level.chunk.ProtoChunk) pending.join(); // done: join cannot block; an exception lands in the catch below
                    pending = null;
                    done.setStatus(net.minecraft.world.level.chunk.ChunkStatus.NOISE);
                    pool.put(done.getPos().toLong(), done);
                    return false;
                }
                if (!noiseTodo.isEmpty()) {
                    net.minecraft.world.level.ChunkPos cp = noiseTodo.remove(noiseTodo.size() - 1);
                    var biomes = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
                    var proto = new net.minecraft.world.level.chunk.ProtoChunk(cp, net.minecraft.world.level.chunk.UpgradeData.EMPTY, level, biomes, null);
                    var blender = net.minecraft.world.level.levelgen.blending.Blender.empty();
                    pending = gen.createBiomes(Runnable::run, random, blender, structures, proto)
                            .thenCompose(c -> gen.fillFromNoise(Runnable::run, blender, random, structures, c));
                    return false;
                }
                if (!surfaceTodo.isEmpty()) {
                    net.minecraft.world.level.ChunkPos cp = surfaceTodo.remove(surfaceTodo.size() - 1);
                    var region = new net.minecraft.server.level.WorldGenRegion(level, around(cp), net.minecraft.world.level.chunk.ChunkStatus.SURFACE, 0);
                    gen.buildSurface(region, structures, random, pool.get(cp.toLong()));
                    return false;
                }
                if (!decorateTodo.isEmpty()) {
                    decorate(decorateTodo.remove(decorateTodo.size() - 1), gen, structures);
                    return false;
                }
                result = assemble();
            } catch (RuntimeException e) {
                pending = null;
                if (!failed) {
                    failed = true;
                    com.mrgregles.bsp_core.BSPCore.LOGGER.warn("Cloaking could not regenerate the land at {} ({}); showing a copy of what stands there instead", origin, e.toString());
                }
                result = capture(level, origin, radius);
            }
            return true;
        }

        @Nullable
        public CloakSnapshot result() {
            return result;
        }

        private CloakSnapshot assemble() {
            List<BlockState> palette = new ArrayList<>();
            Map<BlockState, Integer> ids = new HashMap<>();
            int s = radius * 2 + 1;
            short[] cells = new short[s * s * s];
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            int i = 0;
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    for (int dx = -radius; dx <= radius; dx++) {
                        p.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                        var proto = pool.get(net.minecraft.world.level.ChunkPos.asLong(net.minecraft.core.SectionPos.blockToSectionCoord(p.getX()), net.minecraft.core.SectionPos.blockToSectionCoord(p.getZ())));
                        BlockState st = proto == null || level.isOutsideBuildHeight(p) ? Blocks.AIR.defaultBlockState() : proto.getBlockState(p);
                        if (st.is(Blocks.VOID_AIR) || st.is(Blocks.CAVE_AIR)) {
                            st = Blocks.AIR.defaultBlockState();
                        }
                        Integer id = ids.get(st);
                        if (id == null) {
                            id = palette.size();
                            palette.add(st);
                            ids.put(st, id);
                        }
                        cells[i++] = (short) (int) id;
                    }
                }
            }
            return new CloakSnapshot(origin, radius, palette, cells);
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Origin", origin.asLong());
        tag.putInt("Radius", radius);
        tag.putInt("Version", version);
        ListTag list = new ListTag();
        for (BlockState st : palette) {
            list.add(NbtUtils.writeBlockState(st));
        }
        tag.put("Palette", list);
        int[] raw = new int[cells.length];
        for (int i = 0; i < cells.length; i++) {
            raw[i] = cells[i];
        }
        tag.putIntArray("Cells", raw);
        return tag;
    }

    @Nullable
    public static CloakSnapshot load(CompoundTag tag, HolderGetter<Block> blocks) {
        if (!tag.contains("Cells")) {
            return null;
        }
        List<BlockState> palette = new ArrayList<>();
        for (Tag t : tag.getList("Palette", Tag.TAG_COMPOUND)) {
            palette.add(NbtUtils.readBlockState(blocks, (CompoundTag) t));
        }
        int radius = tag.getInt("Radius");
        int[] raw = tag.getIntArray("Cells");
        int s = radius * 2 + 1;
        if (palette.isEmpty() || raw.length != s * s * s) {
            return null;
        }
        short[] cells = new short[raw.length];
        for (int i = 0; i < raw.length; i++) {
            cells[i] = (short) Math.max(0, Math.min(palette.size() - 1, raw[i]));
        }
        return new CloakSnapshot(BlockPos.of(tag.getLong("Origin")), radius, palette, cells, tag.contains("Version") ? tag.getInt("Version") : 1);
    }

    public static HolderGetter<Block> blocks(Level level) {
        return level.holderLookup(Registries.BLOCK);
    }
}
