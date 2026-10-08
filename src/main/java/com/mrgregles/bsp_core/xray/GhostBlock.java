package com.mrgregles.bsp_core.xray;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * The block X-ray puts in a client's copy of the world where a solid block stands: it draws nothing in the chunk and lets light
 * through, but still has the solid block's shape so the player walks and mines as before. Never placed on a server, never an item;
 * {@link com.mrgregles.bsp_core.client.XrayClient} swaps it in and out and draws the faded shell of the real block itself.
 */
public class GhostBlock extends Block {
    public GhostBlock() {
        super(Properties.of().mapColor(MapColor.NONE).strength(1.5F, 6.0F).sound(SoundType.STONE).noOcclusion().noLootTable()
                .isViewBlocking((st, l, p) -> false).isSuffocating((st, l, p) -> false).isRedstoneConductor((st, l, p) -> false));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, net.minecraft.world.level.BlockGetter level, net.minecraft.core.BlockPos pos) {
        return true;
    }
}
