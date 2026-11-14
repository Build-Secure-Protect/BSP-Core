package com.mrgregles.bsp_core.chamber;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.tank.TankBlock;
import com.mrgregles.bsp_core.tank.TankPartBlockEntity;
import com.mrgregles.bsp_core.tank.TankStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * No mob spawns naturally inside a formed Plasma Tank, chamber or not: the inside is air on a full-block floor, which the game would
 * otherwise count as a fine place for a mob. A Mob Chamber spawns its own mobs on its own timer.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class ChamberSpawns {
    private ChamberSpawns() {}

    @SubscribeEvent
    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        MobSpawnType type = event.getSpawnType();
        if ((type == MobSpawnType.NATURAL || type == MobSpawnType.CHUNK_GENERATION) && insideFormedTank(event.getLevel(), event.getPos())) {
            event.setResult(Event.Result.DENY);
        }
    }

    /**
     * Whether {@code pos} is inside a formed tank: looking straight down (past anything a player put inside) the first tank block met is
     * part of a formed tank whose box holds {@code pos} strictly inside its shell.
     */
    public static boolean insideFormedTank(BlockGetter level, BlockPos pos) {
        BlockPos.MutableBlockPos q = pos.mutable();
        for (int i = 0; i < TankStructure.maxSide(); i++) {
            q.move(0, -1, 0);
            BlockState st = level.getBlockState(q);
            if (!(st.getBlock() instanceof TankBlock)) {
                continue;
            }
            if (!st.getValue(TankBlock.FORMED) || !(level.getBlockEntity(q) instanceof TankPartBlockEntity part) || part.master() == null
                    || !(level.getBlockEntity(part.master()) instanceof TankPartBlockEntity m) || m.master() == null) { // a copied block may point at a master that is not one
                return false;
            }
            BlockPos min = m.master();
            int x = pos.getX() - min.getX(), y = pos.getY() - min.getY(), z = pos.getZ() - min.getZ();
            return x > 0 && y > 0 && z > 0 && x < m.w() - 1 && y < m.h() - 1 && z < m.d() - 1;
        }
        return false;
    }
}
