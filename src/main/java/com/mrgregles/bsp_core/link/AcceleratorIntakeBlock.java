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
 * Accelerator Intake: the bottom middle block of a Plasma Accelerator and its controller. Placed facing the player; the ring stands
 * upright in the plane across that facing, seven Accelerator Frames round an empty middle. Plasma cables feed the intake; the
 * intake sends what arrives to the Decelerators it is linked to, in other worlds or this one.
 */
public class AcceleratorIntakeBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** 0 dark, 1 RF connected, 2 a link delivering: the intake's light (3 or 7) and what the renderer animates. */
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty GLOW = net.minecraft.world.level.block.state.properties.IntegerProperty.create("glow", 0, 2);

    public AcceleratorIntakeBlock() {
        super(Properties.of().mapColor(MapColor.COLOR_ORANGE).requiresCorrectToolForDrops().strength(3.5F, 6.0F).sound(SoundType.COPPER).noOcclusion()
                .lightLevel(s -> switch (s.getValue(GLOW)) { case 2 -> 7; case 1 -> 3; default -> 0; }));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(StructurePartBlock.FORMED, false).setValue(GLOW, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, StructurePartBlock.FORMED, GLOW);
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
        return new AcceleratorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.ACCELERATOR.get(), (lvl, pos, st, be) -> be.serverTick((ServerLevel) lvl));
    }

    @Override
    public net.minecraft.world.InteractionResult use(BlockState state, Level level, BlockPos pos, net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof AcceleratorBlockEntity intake)) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof LinkCardItem) {
            if (level.isClientSide) {
                return net.minecraft.world.InteractionResult.SUCCESS;
            }
            if (LinkCardItem.isWritten(held)) {
                say(player, Component.translatable("message.bsp_core.link.card_written_already", Links.describe(LinkCardItem.target(held))), ChatFormatting.RED);
            } else if (!intake.mayLink(player)) {
                say(player, Component.translatable("message.bsp_core.link.no_access"), ChatFormatting.RED);
            } else {
                ItemStack card = held.getCount() == 1 ? held : held.split(1);
                LinkCardItem.write(card, intake.here());
                if (card != held && !player.getInventory().add(card)) {
                    player.drop(card, false);
                }
                say(player, Component.translatable("message.bsp_core.link.card_written", Links.describe(intake.here())), ChatFormatting.AQUA);
            }
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        if (held.getItem() instanceof LinkExpanderItem) {
            if (level.isClientSide) {
                return net.minecraft.world.InteractionResult.SUCCESS;
            }
            if (intake.fitExpander()) {
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                }
                say(player, Component.translatable("message.bsp_core.link.expander_fitted", intake.expanders(), AcceleratorBlockEntity.expanderMax(), intake.maxLinks()), ChatFormatting.AQUA);
            } else {
                say(player, Component.translatable("message.bsp_core.link.expander_full", AcceleratorBlockEntity.expanderMax()), ChatFormatting.RED);
            }
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        if (held.isEmpty() && player.isShiftKeyDown()) {
            if (level.isClientSide) {
                return net.minecraft.world.InteractionResult.SUCCESS;
            }
            if (intake.takeExpander()) {
                ItemStack out = new ItemStack(com.mrgregles.bsp_core.registry.ModItems.LINK_EXPANDER.get());
                if (!player.getInventory().add(out)) {
                    player.drop(out, false);
                }
                say(player, Component.translatable("message.bsp_core.link.expander_taken", intake.expanders(), intake.maxLinks()), ChatFormatting.AQUA);
            } else {
                say(player, Component.translatable(intake.expanders() > 0 ? "message.bsp_core.link.expander_in_use" : "message.bsp_core.link.expander_none"), ChatFormatting.RED);
            }
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        if (held.isEmpty()) {
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                com.mrgregles.bsp_core.network.BSPNetwork.sendTo(sp, intake.view()); // opens the Worlds board
            }
            return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
        }
        return net.minecraft.world.InteractionResult.PASS;
    }

    static void say(net.minecraft.world.entity.player.Player player, net.minecraft.network.chat.MutableComponent text, ChatFormatting colour) {
        player.displayClientMessage(text.withStyle(colour), true);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof AcceleratorBlockEntity intake) {
            for (ItemStack drop : intake.takeAllExpanders()) {
                popResource(level, pos, drop);
            }
            intake.unform();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bsp_core.accelerator_intake").withStyle(ChatFormatting.GRAY));
    }
}
