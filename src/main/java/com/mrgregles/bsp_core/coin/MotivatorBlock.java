package com.mrgregles.bsp_core.coin;

import com.mrgregles.bsp_core.machine.StructurePartBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Motivator: a half-block pylon placed on top of a factory slice (up to three per slice). Each one
 * shortens that slice's press time. On a complete slice its cube is hidden and the factory draws
 * it pulsing and linked to its neighbours.
 */
public class MotivatorBlock extends StructurePartBlock {
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 8, 13);

    public MotivatorBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_PURPLE).requiresCorrectToolForDrops().strength(3.0F, 6.0F).sound(SoundType.METAL).lightLevel(s -> 7).noOcclusion());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bsp_core.motivator").withStyle(ChatFormatting.GRAY));
    }
}
