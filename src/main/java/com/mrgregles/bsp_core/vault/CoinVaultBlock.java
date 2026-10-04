package com.mrgregles.bsp_core.vault;

import com.mrgregles.bsp_core.BSPConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Coin Vault: a safe for Shatter Coins. The rules are in {@link CoinVaultBlockEntity}.
 * Right-click opens it for the owner and the players on its access list; anyone else gets the
 * lockpicking screen. Anyone who is not the owner mines it in a fixed time whatever the tool.
 */
public class CoinVaultBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Part of a joined vault: the block's own model is hidden and the vault is drawn as one by {@code CoinVaultRenderer}. */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty FORMED = com.mrgregles.bsp_core.machine.StructurePartBlock.FORMED;

    public CoinVaultBlock() {
        super(Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(5.0F, 1200.0F).sound(SoundType.NETHERITE_BLOCK).pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FORMED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CoinVaultBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, pos, st, be) -> {
            if (be instanceof CoinVaultBlockEntity vault) {
                vault.serverTick((ServerLevel) lvl);
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof CoinVaultBlockEntity vault) {
            vault.setOwner(player);
            CoinVaultBlockEntity.reform(vault);
        }
    }

    /** Not the owner: a fixed time with any tool or none, so a vault is never a better wall than it is a safe. */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CoinVaultBlockEntity vault && vault.getOwner() != null && !vault.isOwner(player)) {
            return 1f / (Math.max(1, BSPConfig.getOr(BSPConfig.VAULT_INTRUDER_BREAK_SECONDS, 30)) * 20f);
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    /** Anyone may harvest it: the fixed break time above replaces the tool check for non-owners. */
    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        return level.getBlockEntity(pos) instanceof CoinVaultBlockEntity vault && vault.getOwner() != null && !vault.isOwner(player)
                || super.canHarvestBlock(state, level, pos, player);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level.getBlockEntity(pos) instanceof CoinVaultBlockEntity vault) {
            vault.brokenBy(player);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl && level.getBlockEntity(pos) instanceof CoinVaultBlockEntity vault) {
            vault.onBroken(sl);
        }
        super.onRemove(state, level, pos, newState, moving);
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            // what is left of the vault this block belonged to works out its shape again
            for (Direction d : Direction.values()) {
                if (level.getBlockEntity(pos.relative(d)) instanceof CoinVaultBlockEntity near) {
                    CoinVaultBlockEntity.reform(near);
                }
            }
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof CoinVaultBlockEntity vault) {
            if (vault.getOwner() == null) {
                vault.setOwner(sp); // placed by a command or a machine: the first player to open it owns it
                CoinVaultBlockEntity.reform(vault);
            }
            boolean locked = !vault.mayOpen(sp);
            if (vault.isOwner(sp)) {
                CoinVaultBlockEntity.collectRecovered(sp);
            }
            List<CoinVaultBlockEntity> group = vault.group();
            NetworkHooks.openScreen(sp, new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("block.bsp_core.coin_vault");
                }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                    return new CoinVaultMenu(id, inv, vault, group, locked);
                }
            }, buf -> {
                buf.writeBlockPos(pos);
                buf.writeBoolean(locked);
                buf.writeVarInt(group.size());
                buf.writeVarInt(group.indexOf(vault));
            });
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
