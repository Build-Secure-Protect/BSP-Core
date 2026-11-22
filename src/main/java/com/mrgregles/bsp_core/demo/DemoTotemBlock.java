package com.mrgregles.bsp_core.demo;

import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.totem.ShatterTotemBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * The Demo Totem: an ordinary block, for admins, that looks exactly like an owned Shatter Totem and may wear a look, and does
 * nothing a totem does. It is its own block, item and block entity, never the totem's, so no totem rule, count or lookup sees it.
 *
 * <p>Only admins ({@link DemoTotems#isAdmin}) place it ({@link DemoTotemBlockItem}) and mine it ({@link DemoTotemRules} refuses anyone
 * else's break, creative included). Mining gives back the item with its look; explosions, pistons and mobs cannot move or break it.
 */
public class DemoTotemBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14); // the Shatter Totem's
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Wears a look: the bare totem model is baked and the renderer draws the body, head and hat. */
    public static final BooleanProperty LOOKS = ShatterTotemBlock.LOOKS;

    public DemoTotemBlock() {
        // the totem's light, colour and toughness, so it stands among real totems unnoticed
        super(Properties.of()
                .mapColor(MapColor.GOLD)
                .strength(2.0F, 3_600_000.0F)
                .lightLevel(state -> 10)
                .noLootTable()
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LOOKS, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LOOKS);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DemoTotemBlockEntity(pos, state);
    }

    // --- the look travels on the item ---

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof DemoTotemBlockEntity demo) {
            demo.setLook(DemoTotemBlockEntity.lookOn(stack), DemoTotemBlockEntity.dresserOn(stack));
            if (demo.getLook() != null) {
                com.mrgregles.bsp_core.BSPCore.LOGGER.info("{} placed a demo totem with a look at {}: {}",
                        placer == null ? "something" : placer.getName().getString(), pos.toShortString(), demo.getLook());
            } else if (!DemoTotemBlockEntity.plainHatOn(stack).isEmpty()) {
                demo.setPlain(DemoTotemBlockEntity.plainHatOn(stack));
                com.mrgregles.bsp_core.BSPCore.LOGGER.info("{} placed a demo totem at {} wearing {} on its plain body",
                        placer == null ? "something" : placer.getName().getString(), pos.toShortString(), demo.getPlainHat());
            }
        }
    }

    /** The item for the demo at {@code pos}, carrying its look. */
    public static ItemStack toItemStack(BlockGetter level, BlockPos pos) {
        ItemStack stack = new ItemStack(ModItems.DEMO_TOTEM.get());
        if (level.getBlockEntity(pos) instanceof DemoTotemBlockEntity demo) {
            DemoTotemBlockEntity.putLook(stack, demo.getLook(), demo.getDresser());
            DemoTotemBlockEntity.putPlainHat(stack, demo.getPlainHat());
        }
        return stack;
    }

    /** Middle-click in creative: a copy with the same look, so a row of matching demos is quick to place. */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, Player player) {
        return toItemStack(level, pos);
    }

    /** No loot table: the item drops here, with its look or hat. In creative a bare one drops nothing, as the creative tab has it. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof DemoTotemBlockEntity demo
                && (!player.getAbilities().instabuild || demo.getLook() != null || !demo.getPlainHat().isEmpty())) {
            if (demo.getLook() != null || !demo.getPlainHat().isEmpty()) {
                com.mrgregles.bsp_core.BSPCore.LOGGER.info("{} mined a demo totem at {} ({})", player.getGameProfile().getName(), pos.toShortString(),
                        demo.getLook() != null ? demo.getLook() : "plain, wearing " + demo.getPlainHat());
            }
            Block.popResource(level, pos, toItemStack(level, pos));
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    /** Once after loading, the block state is checked against the look ({@link DemoTotemBlockEntity#serverTick}). */
    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                                                    net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, p, st, be) -> {
            if (be instanceof DemoTotemBlockEntity demo) {
                demo.serverTick();
            }
        };
    }

    // --- right-click: its LOOKS tab ---

    /** Opens the demo's screen, on the client (a demo holds nothing, so there is no menu): the LOOKS tab, read-only for non-admins. */
    @Override
    public net.minecraft.world.InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand,
                                                     net.minecraft.world.phys.BlockHitResult hit) {
        if (level.isClientSide && hand == net.minecraft.world.InteractionHand.MAIN_HAND) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.mrgregles.bsp_core.client.DemoTotemScreen.open(pos));
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    // --- only admins mine it; nothing else moves it ---

    /** Admins mine it as quickly as a totem; anyone else makes no progress (the break itself is refused in {@link DemoTotemRules}). */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return DemoTotems.isAdmin(player) ? super.getDestroyProgress(state, player, level, pos) : 0f;
    }

    @Override
    public boolean canEntityDestroy(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
        return false;
    }

    @Override
    public boolean canDropFromExplosion(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return false;
    }

    @Override
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return 3_600_000.0F;
    }
}
