package com.mrgregles.bsp_core.totem;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;

/**
 * Harvest's eye: told by {@link com.mrgregles.bsp_core.mixin.HarvestChunkMixin} whenever a block changes in a loaded chunk, so a
 * BSP ore counts however it leaves its spot: a player's pickaxe, an ME Annihilation Plane or another machine (they call
 * {@code destroyBlock} or {@code setBlock} and never fire Forge's break event), an explosion or a piston.
 */
public final class HarvestWatch {
    private HarvestWatch() {}

    /** A BSP-Core ore: in {@code forge:ores} and registered by this mod. */
    public static boolean isBspOre(BlockState state) {
        return state.is(Tags.Blocks.ORES) && BSPCore.MODID.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace());
    }

    /** The block at {@code pos} went from {@code old} to {@code now}. */
    public static void changed(Level level, BlockPos pos, BlockState old, BlockState now) {
        if (!(level instanceof ServerLevel sl) || old.is(now.getBlock()) || !isBspOre(old) || !sl.getServer().isSameThread()) {
            return;
        }
        for (ShatterTotemBlockEntity totem : TotemAuras.totemsCovering(sl, pos, TotemUpgrades.Buff.HARVEST)) {
            totem.harvestRecord(pos, old);
        }
    }
}
