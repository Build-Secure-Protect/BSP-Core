package com.mrgregles.bsp_core.totem;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.data.PlayerPersistent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;

/** Places a Shatter Totem block at or near a position, carrying the item's data into the block. */
public final class TotemPlacer {
    private static final int HORIZONTAL_RADIUS = 3;
    private static final int VERTICAL_RADIUS = 3;

    private TotemPlacer() {}

    /**
     * Places the totem as close to {@code around} as possible.
     *
     * @return true if the block was placed; false if no replaceable spot exists in range
     */
    public static boolean place(ServerLevel level, BlockPos around, ItemStack totem) {
        return placeAt(level, around, totem) != null;
    }

    /** As {@link #place}, returning where the block went, or null when no spot was free. */
    @Nullable
    public static BlockPos placeAt(ServerLevel level, BlockPos around, ItemStack totem) {
        // never inside an Anti Totem zone: the nearest spot outside it instead
        BlockPos pos = findSpot(level, com.mrgregles.bsp_core.zone.ZoneLedger.get(level.getServer()).outside(level, around));
        if (pos == null) {
            return null;
        }
        level.setBlock(pos, ModBlocks.SHATTER_TOTEM.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof ShatterTotemBlockEntity totemEntity) {
            totemEntity.loadFromStack(totem);
        }
        BSPCore.LOGGER.info("Shatter Totem owned by {} placed at {} in {}",
                TotemOwner.fromStack(totem).map(TotemOwner::name).orElse("nobody"), pos, level.dimension().location());
        return pos;
    }

    /** Where a totem leaving {@code player} should go: their position if the dimension allows it, else their last main-dimension spot. */
    public record Target(ServerLevel level, BlockPos pos) {}

    public static Target safeTargetFor(ServerPlayer player) {
        if (BSPConfig.isDimensionAllowed(player.level().dimension().location())) {
            return new Target(player.serverLevel(), grounded(player.serverLevel(), player.blockPosition()));
        }
        PlayerPersistent.LastMainPosition last = PlayerPersistent.getLastMainPosition(player).orElse(null);
        ServerLevel lastLevel = last == null ? null : last.level(player.server);
        if (lastLevel != null) {
            return new Target(lastLevel, last.pos());
        }
        ServerLevel overworld = player.server.overworld();
        return new Target(overworld, overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, overworld.getSharedSpawnPos()));
    }

    /**
     * The spot straight below {@code from} that stands on something: the first block above solid ground or a liquid's surface. A
     * player who logs out in the air (flying, gliding, levitating, falling) must not leave their totem hanging where nobody walks;
     * it goes to the ground below them instead. A position already on the ground is returned as it is. Over nothing at all (the
     * void) the position is kept.
     */
    public static BlockPos grounded(ServerLevel level, BlockPos from) {
        BlockPos.MutableBlockPos p = from.mutable();
        while (p.getY() > level.getMinBuildHeight()) {
            BlockPos below = p.below();
            BlockState under = level.getBlockState(below);
            if (!under.getCollisionShape(level, below).isEmpty() || !under.getFluidState().isEmpty()) {
                return p.immutable();
            }
            p.move(0, -1, 0);
        }
        return from;
    }

    @Nullable
    static BlockPos findSpot(ServerLevel level, BlockPos around) {
        if (isFree(level, around)) {
            return around;
        }
        // Nearest first: expand the vertical offset slowly, prefer going up over down.
        for (int dist = 1; dist <= Math.max(HORIZONTAL_RADIUS, VERTICAL_RADIUS); dist++) {
            for (int dy : new int[]{0, dist, -dist}) {
                if (Math.abs(dy) > VERTICAL_RADIUS) continue;
                for (int dx = -dist; dx <= dist; dx++) {
                    for (int dz = -dist; dz <= dist; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != dist && dy == 0) continue;
                        if (Math.abs(dx) > HORIZONTAL_RADIUS || Math.abs(dz) > HORIZONTAL_RADIUS) continue;
                        BlockPos p = around.offset(dx, dy, dz);
                        if (isFree(level, p)) {
                            return p;
                        }
                    }
                }
            }
        }
        // Last resort: straight up until something is free.
        BlockPos.MutableBlockPos p = around.mutable();
        while (p.getY() < level.getMaxBuildHeight() - 1) {
            p.move(0, 1, 0);
            if (isFree(level, p)) {
                return p.immutable();
            }
        }
        return null;
    }

    private static boolean isFree(ServerLevel level, BlockPos pos) {
        if (!level.isInWorldBounds(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.canBeReplaced() && !state.hasBlockEntity();
    }
}
