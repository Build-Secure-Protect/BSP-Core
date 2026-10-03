package com.mrgregles.bsp_core.totem;

import com.mrgregles.bsp_core.menu.ShatterTotemMenu;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * The placed form of the Shatter Totem.
 *
 * <p>Indestructible by explosions, pistons and non-player entities. Mining it returns the totem
 * item with its owner intact. Who is allowed to mine it is enforced in a later task.
 */
public class ShatterTotemBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

    /** Body colour: gold when owned, turquoise when unclaimed, flashing gold/red while being stolen. */
    public enum Glow implements StringRepresentable {
        OWNED("owned"), UNCLAIMED("unclaimed"), STEALING("stealing");

        private final String name;

        Glow(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Glow> GLOW = EnumProperty.create("glow", Glow.class);
    /** Which way the statue faces; the model's face is on its north side at facing=north. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public ShatterTotemBlock() {
        super(Properties.of()
                .mapColor(MapColor.GOLD)
                .strength(2.0F, 3_600_000.0F)
                .lightLevel(state -> 10)
                .noLootTable()
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any()
                .setValue(GLOW, Glow.UNCLAIMED)
                .setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GLOW, FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        // Face the player who placed it.
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    // --- shape and rendering ---

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    // --- block entity ---

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShatterTotemBlockEntity(pos, state);
    }

    // --- placement: carry the owner from the item into the block entity ---

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || !(level.getBlockEntity(pos) instanceof ShatterTotemBlockEntity totem)) {
            return;
        }
        totem.loadFromStack(stack);
        if (totem.getOwner().isEmpty() && placer instanceof Player player) {
            // An unowned totem (e.g. from the creative menu) becomes the placer's.
            totem.setOwner(new TotemOwner(player.getUUID(), player.getGameProfile().getName()));
        }
    }

    // --- pickup: the block never drops loot; it hands back the exact totem item instead ---

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            ItemStack stack = toItemStack(level, pos);
            if (!player.getInventory().add(stack)) {
                ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                drop.setDefaultPickUpDelay();
                level.addFreshEntity(drop);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter level, BlockPos pos, Player player) {
        return toItemStack(level, pos);
    }

    /** Builds the item form of the totem at {@code pos}, including its owner. */
    public static ItemStack toItemStack(BlockGetter level, BlockPos pos) {
        ItemStack stack = new ItemStack(ModItems.SHATTER_TOTEM.get());
        if (level.getBlockEntity(pos) instanceof ShatterTotemBlockEntity totem) {
            CompoundTag tag = stack.getOrCreateTag();
            totem.writeToStackTag(tag);
        }
        return stack;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof ShatterTotemBlockEntity totem) {
            totem.onRemovedFromWorld();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    // --- interaction: open the totem panel ---

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof ShatterTotemBlockEntity) {
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider((id, inv, p) -> new ShatterTotemMenu(id, inv, pos), Component.translatable("block.bsp_core.shatter_totem")),
                    buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.SHATTER_TOTEM.get(), (lvl, pos, st, be) -> be.serverTick((ServerLevel) lvl));
    }

    // --- indestructibility ---

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
