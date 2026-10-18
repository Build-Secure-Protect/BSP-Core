package com.mrgregles.bsp_core.link;

import com.mrgregles.bsp_core.machine.StructurePartBlock;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Decelerator Core: the front block of a Plasma Decelerator, two blocks long. Placed facing the player, with a Decelerator Port
 * directly behind it. It receives the plasma an Accelerator sends it (linked with a Link Card) and hands it to the port.
 */
public class DeceleratorCoreBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public DeceleratorCoreBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_ORANGE).requiresCorrectToolForDrops().strength(3.5F, 6.0F).sound(SoundType.COPPER).noOcclusion()
                .lightLevel(s -> s.getValue(StructurePartBlock.FORMED) ? 5 : 0));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(StructurePartBlock.FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, StructurePartBlock.FORMED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(StructurePartBlock.FORMED) ? RenderShape.INVISIBLE : RenderShape.MODEL; // formed: the controller draws the whole machine
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DeceleratorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.DECELERATOR_CORE.get(), (lvl, pos, st, be) -> be.serverTick((ServerLevel) lvl));
    }

    @Override
    public net.minecraft.world.InteractionResult use(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof DeceleratorBlockEntity core)) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof LinkCardItem) {
            if (level.isClientSide) {
                return net.minecraft.world.InteractionResult.SUCCESS;
            }
            Links.Plug result = core.plug(held);
            if (result == Links.Plug.LINKED) {
                AcceleratorIntakeBlock.say(player, Component.translatable(result.key(), Links.describe(core.accelerator())), ChatFormatting.AQUA);
            } else {
                AcceleratorIntakeBlock.say(player, Component.translatable(result.key()), ChatFormatting.RED);
            }
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        if (held.isEmpty() && player.isShiftKeyDown()) {
            if (level.isClientSide) {
                return net.minecraft.world.InteractionResult.SUCCESS;
            }
            ItemStack card = core.unplug();
            if (card.isEmpty()) {
                AcceleratorIntakeBlock.say(player, Component.translatable("message.bsp_core.link.no_card"), ChatFormatting.RED);
            } else {
                if (!player.getInventory().add(card)) {
                    player.drop(card, false);
                }
                AcceleratorIntakeBlock.say(player, Component.translatable("message.bsp_core.link.unlinked"), ChatFormatting.AQUA);
            }
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        if (held.isEmpty()) {
            if (!level.isClientSide) {
                if (core.isLinked()) {
                    AcceleratorIntakeBlock.say(player, core.isFormed()
                            ? Component.translatable("message.bsp_core.link.core_status", Links.describe(core.accelerator()), core.inRate(), core.outRate(), String.format("%,d", core.energy().getEnergyStored()), String.format("%,d", LinkEnergy.capacity()))
                            : Component.translatable("message.bsp_core.link.core_status_unformed", Links.describe(core.accelerator())), ChatFormatting.AQUA);
                } else {
                    AcceleratorIntakeBlock.say(player, Component.translatable(core.isFormed() ? "message.bsp_core.link.core_unlinked" : "message.bsp_core.link.core_unformed"), ChatFormatting.AQUA);
                }
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
        }
        return net.minecraft.world.InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof DeceleratorBlockEntity core) {
            ItemStack card = core.unplug();
            if (!card.isEmpty()) {
                popResource(level, pos, card);
            }
            core.unform();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bsp_core.decelerator_core").withStyle(ChatFormatting.GRAY));
    }
}
