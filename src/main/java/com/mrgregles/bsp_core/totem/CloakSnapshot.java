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
 * What a Cloaking cube shows outsiders: a copy of every block in the cube, taken when Cloaking was
 * first switched on, kept as a palette and an index per position. First version: the copy is of what
 * stood there at that moment, not the land as generated.
 */
public final class CloakSnapshot {
    public final BlockPos origin;
    public final int radius;
    private final List<BlockState> palette;
    private final short[] cells;

    private CloakSnapshot(BlockPos origin, int radius, List<BlockState> palette, short[] cells) {
        this.origin = origin;
        this.radius = radius;
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

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Origin", origin.asLong());
        tag.putInt("Radius", radius);
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
        return new CloakSnapshot(BlockPos.of(tag.getLong("Origin")), radius, palette, cells);
    }

    public static HolderGetter<Block> blocks(Level level) {
        return level.holderLookup(Registries.BLOCK);
    }
}
