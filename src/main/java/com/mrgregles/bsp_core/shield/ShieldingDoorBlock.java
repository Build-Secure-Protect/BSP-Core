package com.mrgregles.bsp_core.shield;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Radiation Shielding Door: an iron door to mobs (its block set does not open by hand, so zombies cannot break it and villagers do
 * not path through it), that players open by hand as well as by redstone. The hand toggle is vanilla's own for a wooden door.
 */
public class ShieldingDoorBlock extends DoorBlock {
    public ShieldingDoorBlock(Properties properties, BlockSetType type) {
        super(properties, type);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        state = state.cycle(OPEN);
        level.setBlock(pos, state, 10);
        boolean open = state.getValue(OPEN);
        level.playSound(player, pos, open ? type().doorOpen() : type().doorClose(), SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
        level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
