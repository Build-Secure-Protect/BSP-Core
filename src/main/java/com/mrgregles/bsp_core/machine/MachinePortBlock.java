package com.mrgregles.bsp_core.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import javax.annotation.Nullable;

/** Structural multiblock part that passes pipes through to the controller: Lava Pylon, Refinery Pump, Item Hatch. */
public class MachinePortBlock extends BaseEntityBlock {
    public MachinePortBlock() {
        super(Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(4.0F, 8.0F).sound(SoundType.METAL).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(StructurePartBlock.FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(StructurePartBlock.FORMED);
    }

    /** Invisible while part of a formed structure; the controller draws the whole machine. */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(StructurePartBlock.FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    /**
     * On a Totem Forge's bottom layer a port takes mould pieces like the controller does (the hollow above it is air, so the
     * click lands here). On a set forge only an empty hand takes the totem out or starts a steal (owner, audit D3): any other
     * click passes on, so a wrench works and a block is placed as usual.
     */
    @Override
    @SuppressWarnings("deprecation")
    public net.minecraft.world.InteractionResult use(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                     net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof MachinePortBlockEntity port && port.controller() != null
                && level.getBlockEntity(port.controller()) instanceof TotemForgeBlockEntity forge) {
            ItemStack held = player.getItemInHand(hand);
            boolean piece = held.getItem() instanceof com.mrgregles.bsp_core.material.TotemMouldItem || held.is(com.mrgregles.bsp_core.registry.ModItems.TOTEM_CORE.get());
            // a set totem comes out whether or not the structure still stands; the mould only works on a formed forge
            if ((forge.isSet() && held.isEmpty()) || (forge.isFormed() && (piece || (player.isShiftKeyDown() && held.isEmpty() && forge.mouldCount() > 0)))) {
                if (!level.isClientSide) {
                    forge.use(player, hand);
                }
                return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return net.minecraft.world.InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MachinePortBlockEntity(pos, state);
    }
}
