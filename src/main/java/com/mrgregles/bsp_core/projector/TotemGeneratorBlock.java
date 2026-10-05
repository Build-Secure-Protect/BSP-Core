package com.mrgregles.bsp_core.projector;

import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.GeneratorArrayPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * The Totem Generator block ("Siphon Plinth", joined as "One Slab"). Its four side properties say
 * which neighbours are also generators, so the model can run the slab straight through and put
 * the three cable ports only on outer edges. The rules are in {@link TotemGeneratorBlockEntity}.
 */
public class TotemGeneratorBlock extends BaseEntityBlock {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH, SOUTH = BlockStateProperties.SOUTH, EAST = BlockStateProperties.EAST, WEST = BlockStateProperties.WEST;

    public TotemGeneratorBlock() {
        super(Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(3.5F, 6.0F).sound(SoundType.METAL).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(SOUTH, false).setValue(EAST, false).setValue(WEST, false));
    }

    private static BooleanProperty side(Direction d) {
        return switch (d) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            default -> WEST;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST);
    }

    private BlockState joined(BlockGetter level, BlockPos pos) {
        BlockState state = defaultBlockState();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            state = state.setValue(side(d), level.getBlockState(pos.relative(d)).getBlock() instanceof TotemGeneratorBlock);
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return joined(ctx.getLevel(), ctx.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return dir.getAxis().isHorizontal() ? state.setValue(side(dir), neighbour.getBlock() instanceof TotemGeneratorBlock) : state;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TotemGeneratorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, pos, st, be) -> {
            if (be instanceof TotemGeneratorBlockEntity generator) {
                generator.serverTick((ServerLevel) lvl);
            }
        };
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TotemGeneratorBlockEntity generator) {
            for (ItemStack stack : generator.drops()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    /** Opens the screen of the generator at {@code pos} for {@code player}. */
    public static void open(ServerPlayer player, BlockPos pos) {
        if (player.level().getBlockEntity(pos) instanceof TotemGeneratorBlockEntity generator && player.distanceToSqr(pos.getCenter()) <= 100 && generator.mayOpen(player)) {
            NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inv, p) -> new GeneratorMenu(id, inv, generator), Component.translatable("block.bsp_core.totem_generator")), pos);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof TotemGeneratorBlockEntity generator) {
            if (!generator.mayOpen(sp)) {
                sp.displayClientMessage(Component.translatable("message.bsp_core.generator.not_owner").withStyle(net.minecraft.ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            BlockPos centre = generator.findCentre();
            Set<BlockPos> members = centre == null ? Set.of() : generator.members(centre);
            if (members.size() > 1) {
                // several joined: show the three by three first, and let the player pick which one to open
                int mask = 0;
                for (BlockPos m : members) {
                    mask |= 1 << ((m.getZ() - centre.getZ() + 1) * 3 + (m.getX() - centre.getX() + 1));
                }
                BSPNetwork.sendTo(sp, new GeneratorArrayPacket(centre, mask, pos));
            } else {
                open(sp, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
