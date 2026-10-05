package com.mrgregles.bsp_core.compat.jade;

import com.mrgregles.bsp_core.coin.CoinFactoryBlockEntity;
import com.mrgregles.bsp_core.decoy.DecoyTotemBlock;
import com.mrgregles.bsp_core.decoy.DecoyTotemBlockEntity;
import com.mrgregles.bsp_core.machine.MagneticCentrifugeBlockEntity;
import com.mrgregles.bsp_core.machine.MultiblockControllerBlockEntity;
import com.mrgregles.bsp_core.machine.StructurePartBlock;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.totem.ShatterTotemBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import snownee.jade.api.Accessor;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

import javax.annotation.Nullable;

/**
 * Jade ("what am I looking at") support. Jade already shows every block's name and its RF, and that is
 * all BSP-Core wants shown, so this adds no lines of its own. It only corrects what Jade points at:
 * <ul>
 *   <li>a working Decoy Totem reads as a Shatter Totem to everyone but its owner, like its renderer;</li>
 *   <li>the hidden part blocks of an assembled machine read as the machine itself.</li>
 * </ul>
 * Only loaded when Jade is installed.
 */
@WailaPlugin
public class BspJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addRayTraceCallback((hit, accessor, original) -> redirect(registration, accessor));
    }

    private static Accessor<?> redirect(IWailaClientRegistration registration, @Nullable Accessor<?> accessor) {
        if (!(accessor instanceof BlockAccessor target)) {
            return accessor;
        }
        BlockState state = target.getBlockState();
        if (target.getBlockEntity() instanceof DecoyTotemBlockEntity decoy) {
            if (decoy.isActive() && !decoy.isOwner(target.getPlayer())) {
                return registration.blockAccessor().from(target).blockState(ModBlocks.SHATTER_TOTEM.get().defaultBlockState()
                        .setValue(ShatterTotemBlock.FACING, state.getValue(DecoyTotemBlock.FACING))).build();
            }
            return accessor;
        }
        if (state.hasProperty(StructurePartBlock.FORMED) && state.getValue(StructurePartBlock.FORMED)) {
            BlockEntity controller = controllerOf(target.getLevel(), target.getPosition());
            if (controller != null && !controller.getBlockPos().equals(target.getPosition())) {
                BlockHitResult old = target.getHitResult();
                return registration.blockAccessor().from(target)
                        .hit(new BlockHitResult(old.getLocation(), old.getDirection(), controller.getBlockPos(), old.isInside()))
                        .blockState(controller.getBlockState()).blockEntity(controller).build();
            }
        }
        return accessor;
    }

    private static BlockPos lastPart = BlockPos.ZERO, lastController;
    private static long lastLook = Long.MIN_VALUE;

    /** The controller of the assembled machine that {@code part} belongs to. The answer is kept for a second, as Jade asks every tick. */
    @Nullable
    private static BlockEntity controllerOf(Level level, BlockPos part) {
        long now = level.getGameTime();
        if (!part.equals(lastPart) || now - lastLook >= 20 || now < lastLook) {
            lastPart = part.immutable();
            lastLook = now;
            lastController = null;
            search:
            for (int cx = (part.getX() >> 4) - 1; cx <= (part.getX() >> 4) + 1; cx++) {
                for (int cz = (part.getZ() >> 4) - 1; cz <= (part.getZ() >> 4) + 1; cz++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) {
                        continue;
                    }
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (owns(be, part)) {
                            lastController = be.getBlockPos();
                            break search;
                        }
                    }
                }
            }
        }
        return lastController == null ? null : level.getBlockEntity(lastController);
    }

    private static boolean owns(BlockEntity be, BlockPos part) {
        if (be.getBlockPos().equals(part)) {
            return be instanceof MultiblockControllerBlockEntity || be instanceof CoinFactoryBlockEntity;
        }
        if (be instanceof CoinFactoryBlockEntity factory) {
            return factory.isFormed() && factory.parts().stream().anyMatch(p -> p.pos().equals(part));
        }
        if (be instanceof MultiblockControllerBlockEntity machine && machine.isFormed()) {
            if (machine.parts().stream().anyMatch(p -> p.pos().equals(part))) {
                return true;
            }
            if (machine instanceof MagneticCentrifugeBlockEntity centrifuge) {
                for (int n = 1; n < centrifuge.layers(); n++) {
                    if (centrifuge.upperLayer(n).stream().anyMatch(p -> p.pos().equals(part))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
