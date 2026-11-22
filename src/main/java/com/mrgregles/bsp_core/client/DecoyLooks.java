package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.decoy.DecoyTotemBlockEntity;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Which real totem a working decoy copies for the players it fools: its owner's nearest totem that this client has loaded (within
 * {@link #CHUNKS} chunks), looked up again every two seconds, so the decoy wears the same look and hat as the totem beside it and
 * changes with it (a VIP lapse, a new look, a steal). With none of the owner's totems near, the decoy is a plain owned totem.
 * Each look is already in plain sight on the real totem, so the copy tells the viewer nothing new.
 */
public final class DecoyLooks {
    private DecoyLooks() {}

    private static final int CHUNKS = 4, EVERY_TICKS = 40;

    private record Found(WeakReference<ShatterTotemBlockEntity> totem, long at) {
    }

    private static final Map<DecoyTotemBlockEntity, Found> FOUND = new WeakHashMap<>();

    @Nullable
    public static ShatterTotemBlockEntity copied(DecoyTotemBlockEntity decoy) {
        Level level = decoy.getLevel();
        UUID owner = decoy.getOwner();
        if (level == null || owner == null) {
            return null;
        }
        long now = level.getGameTime();
        Found f = FOUND.get(decoy);
        if (f == null || now - f.at() >= EVERY_TICKS || now < f.at()) {
            f = new Found(new WeakReference<>(nearest(level, decoy.getBlockPos(), owner)), now);
            FOUND.put(decoy, f);
        }
        ShatterTotemBlockEntity t = f.totem().get();
        return t != null && !t.isRemoved() && t.isOwner(owner) ? t : null;
    }

    @Nullable
    private static ShatterTotemBlockEntity nearest(Level level, BlockPos at, UUID owner) {
        ShatterTotemBlockEntity best = null;
        double bestD = Double.MAX_VALUE;
        int cx = at.getX() >> 4, cz = at.getZ() >> 4;
        for (int dx = -CHUNKS; dx <= CHUNKS; dx++) {
            for (int dz = -CHUNKS; dz <= CHUNKS; dz++) {
                if (!(level.getChunkSource().getChunk(cx + dx, cz + dz, ChunkStatus.FULL, false) instanceof LevelChunk chunk)) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof ShatterTotemBlockEntity t && t.isOwner(owner)) {
                        double d = t.getBlockPos().distSqr(at);
                        if (d < bestD) {
                            bestD = d;
                            best = t;
                        }
                    }
                }
            }
        }
        return best;
    }
}
