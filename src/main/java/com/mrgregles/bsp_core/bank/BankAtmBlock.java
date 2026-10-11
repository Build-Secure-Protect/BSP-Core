package com.mrgregles.bsp_core.bank;

import com.mrgregles.bsp_core.admin.Admins;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;

/**
 * The Bank: a two-block ATM ("Vault Pillar") that opens a player's bank account. Placed by admins
 * only (creative, {@link com.mrgregles.bsp_core.admin.AdminBlockItem}); cannot be mined, pushed or
 * blown up; breaking either half as an admin removes both. Either half opens the screen. The lower
 * half holds the block entity and the whole model; the upper half is empty and only keeps the space.
 */
public class BankAtmBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public BankAtmBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_GRAY).strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.METAL).noOcclusion()
                .pushReaction(PushReaction.BLOCK).lightLevel(s -> 7));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** The lower half's position for either half. */
    public static BlockPos lower(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new BankAtmBlockEntity(pos, state) : null;
    }

    // ------------------------------------------------------------------ two halves

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        Level level = ctx.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(ctx)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite()).setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        boolean other = half == DoubleBlockHalf.LOWER ? dir == Direction.UP : dir == Direction.DOWN;
        if (other && !(neighbour.is(this) && neighbour.getValue(HALF) != half)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, neighbour, level, pos, neighbourPos);
    }

    /** Even in creative mode, only an admin removes it; then both halves go. */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (!Admins.isAdmin(player)) {
            return false;
        }
        BlockPos otherPos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
        BlockState other = level.getBlockState(otherPos);
        if (other.is(this)) {
            level.setBlock(otherPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 35);
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    // ------------------------------------------------------------------ use

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp) {
            BlockPos lower = lower(state, pos);
            open(sp, lower);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Opens the banking screen at an ATM (or, with a null position, from the Portable Banker). */
    public static void open(ServerPlayer sp, @Nullable BlockPos atm) {
        Level level = sp.level();
        if (atm != null) {
            level.playSound(null, atm, SoundEvents.NOTE_BLOCK_BIT.get(), SoundSource.BLOCKS, 0.5f, 1.6f);
        }
        NetworkHooks.openScreen(sp, new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable(atm != null ? "block.bsp_core.bank_atm" : "item.bsp_core.portable_banker");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inv, Player p) {
                return new BankMenu(id, inv, atm);
            }
        }, buf -> {
            buf.writeBoolean(atm != null);
            if (atm != null) {
                buf.writeBlockPos(atm);
            }
        });
        if (atm != null && level.getBlockEntity(atm) instanceof BankAtmBlockEntity be) {
            be.userOpened();
        }
        Bank.refresh(sp.server, sp.getUUID());
        BankMenu.sync(sp, "", java.util.List.of(), null);
    }
}
