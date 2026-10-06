package com.mrgregles.bsp_core.plasma;

import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A Plasma Battery ("Capsule"): a tank of Wave Plasma that also holds stamped powers. Placed on a
 * Plasma Extractor instead of a totem, it feeds the extractor until it runs dry. Its contents live in
 * the item and move with it.
 */
public class PlasmaBatteryBlock extends BaseEntityBlock {
    private static final VoxelShape X = Block.box(1, 2, 4, 15, 12, 12), Z = Block.box(4, 2, 1, 12, 12, 15);
    public final int tier;

    public PlasmaBatteryBlock(int tier) {
        super(Properties.of().mapColor(MapColor.COLOR_BLACK).requiresCorrectToolForDrops().strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion());
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.X));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_AXIS);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, ctx.getHorizontalDirection().getAxis());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(BlockStateProperties.HORIZONTAL_AXIS) == Direction.Axis.X ? X : Z;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlasmaBatteryBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof PlasmaBatteryBlockEntity battery) {
            battery.loadFrom(stack);
        }
    }

    /** The battery keeps its plasma and powers when mined: the drop is made here, not by a loot table. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative() && level.getBlockEntity(pos) instanceof PlasmaBatteryBlockEntity battery) {
            popResource(level, pos, battery.toStack());
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, Player player) {
        return level.getBlockEntity(pos) instanceof PlasmaBatteryBlockEntity battery ? battery.toStack() : new ItemStack(this);
    }

    /** The battery as an item: a block item that shows its charge bar and contents. */
    public static class Item extends BlockItem {
        public Item(PlasmaBatteryBlock block) {
            super(block, new net.minecraft.world.item.Item.Properties().stacksTo(1));
        }

        public PlasmaBatteryBlock block() {
            return (PlasmaBatteryBlock) getBlock();
        }

        @Override
        public boolean isBarVisible(ItemStack stack) {
            return true;
        }

        @Override
        public int getBarWidth(ItemStack stack) {
            return Math.round(13f * PlasmaItems.stored(stack) / Math.max(1, PlasmaItems.capacity(stack)));
        }

        @Override
        public int getBarColor(ItemStack stack) {
            return 0x4FB8FF;
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            PlasmaItems.tooltip(stack, tooltip);
        }
    }
}
