package com.mrgregles.bsp_core.plasma;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Wrench: turns blocks in place. Right-click a block with a facing (a Plasma Repeater, a totem,
 * a battery, most machines) to turn it to the next direction; sneak and right-click to turn it the
 * other way (a repeater turns straight round). Blocks with no facing are left alone.
 */
public class WrenchItem extends Item {
    public WrenchItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = ctx.getPlayer();
        boolean back = player != null && player.isShiftKeyDown();
        if (state.getBlock() instanceof com.mrgregles.bsp_core.projector.TotemCableBlock) {
            return onCable(ctx, state, back);
        }
        if (state.getBlock() instanceof com.mrgregles.bsp_core.tank.TankBlock tank) {
            if (tank.part != com.mrgregles.bsp_core.tank.TankBlock.Part.PORT) {
                return InteractionResult.PASS;
            }
            if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                com.mrgregles.bsp_core.tank.TankStructure.cycleMode(sl, pos, back);
                var port = com.mrgregles.bsp_core.tank.TankStructure.portAt(sl, pos);
                if (player != null && port != null) {
                    player.displayClientMessage(Component.translatable("message.bsp_core.tank.port", Component.translatable(port.mode().key())).withStyle(ChatFormatting.AQUA), true);
                }
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_OPEN, net.minecraft.sounds.SoundSource.BLOCKS, 0.4f, 1.8f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        BlockState turned = turn(state, back);
        if (turned == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, turned, 3);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_OPEN, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.6f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The end of the cable at {@code pos} the hit at {@code where} points at. */
    public static Direction endHit(BlockPos pos, net.minecraft.world.phys.Vec3 where) {
        net.minecraft.world.phys.Vec3 off = where.subtract(net.minecraft.world.phys.Vec3.atCenterOf(pos));
        return Direction.getNearest(off.x, off.y, off.z);
    }

    /** Whether the hit is on the cable's core rather than one of its arms. */
    public static boolean coreHit(BlockPos pos, net.minecraft.world.phys.Vec3 where) {
        net.minecraft.world.phys.Vec3 off = where.subtract(net.minecraft.world.phys.Vec3.atCenterOf(pos));
        return Math.max(Math.abs(off.x), Math.max(Math.abs(off.y), Math.abs(off.z))) <= 0.2;
    }

    /**
     * On a cable the wrench sets ends: a click steps the end you point at through Normal, Output, Input and Off (and Links it first
     * where another colour meets it); sneak + click puts it back to Normal; sneak + click on the core picks the cable up with its settings.
     */
    private static InteractionResult onCable(UseOnContext ctx, BlockState state, boolean back) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Player player = ctx.getPlayer();
        if (!(level.getBlockEntity(pos) instanceof com.mrgregles.bsp_core.projector.PlasmaCableBlockEntity cable)) {
            return InteractionResult.PASS;
        }
        Direction side = endHit(pos, ctx.getClickLocation());
        boolean core = coreHit(pos, ctx.getClickLocation());
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (back && core) {
            ItemStack drop = new ItemStack(state.getBlock());
            if (cable.anyEndSet()) {
                drop.getOrCreateTag().putByteArray(com.mrgregles.bsp_core.projector.PlasmaCableBlockEntity.TAG_ENDS, cable.ends());
            }
            level.destroyBlock(pos, false);
            net.minecraft.world.level.block.Block.popResource(level, pos, drop);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_OPEN, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.2f);
            return InteractionResult.CONSUME;
        }
        com.mrgregles.bsp_core.projector.PlasmaCableBlockEntity.End cur = cable.end(side), next;
        if (back) {
            next = com.mrgregles.bsp_core.projector.PlasmaCableBlockEntity.End.NORMAL;
        } else {
            next = switch (cur) {
                case NORMAL -> com.mrgregles.bsp_core.projector.TotemCableBlock.foreignCable(level, pos, side) ? com.mrgregles.bsp_core.projector.PlasmaCableBlockEntity.End.LINK : com.mrgregles.bsp_core.projector.PlasmaCableBlockEntity.End.OUTPUT;
                case LINK -> com.mrgregles.bsp_core.projector.PlasmaCableBlockEntity.End.OUTPUT;
                case OUTPUT -> com.mrgregles.bsp_core.projector.PlasmaCableBlockEntity.End.INPUT;
                case INPUT -> com.mrgregles.bsp_core.projector.PlasmaCableBlockEntity.End.OFF;
                case OFF -> com.mrgregles.bsp_core.projector.PlasmaCableBlockEntity.End.NORMAL;
            };
        }
        cable.setEnd(side, next);
        level.setBlock(pos, com.mrgregles.bsp_core.projector.TotemCableBlock.refresh(level, pos), 3);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_OPEN, net.minecraft.sounds.SoundSource.BLOCKS, 0.4f, 1.8f);
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.bsp_core.wrench.end", Component.translatable("gui.bsp_core.wrench.side." + side.getSerializedName()), Component.translatable(next.key())).withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResult.CONSUME;
    }

    /** The block turned one step, or null if it has nothing to turn. */
    @Nullable
    public static BlockState turn(BlockState state, boolean back) {
        if (state.getBlock() instanceof PlasmaRepeaterBlock) {
            Direction f = state.getValue(PlasmaRepeaterBlock.FACING);
            return state.setValue(PlasmaRepeaterBlock.FACING, back ? f.getOpposite() : next(f));
        }
        for (DirectionProperty prop : new DirectionProperty[]{BlockStateProperties.HORIZONTAL_FACING, BlockStateProperties.FACING}) {
            if (state.hasProperty(prop)) {
                Direction f = state.getValue(prop);
                Direction n = prop == BlockStateProperties.HORIZONTAL_FACING ? (back ? f.getCounterClockWise() : f.getClockWise()) : (back ? previous(f) : next(f));
                return prop.getPossibleValues().contains(n) ? state.setValue(prop, n) : null;
            }
        }
        for (EnumProperty<Direction.Axis> prop : List.of(BlockStateProperties.HORIZONTAL_AXIS, BlockStateProperties.AXIS)) {
            if (state.hasProperty(prop)) {
                Direction.Axis a = state.getValue(prop);
                Direction.Axis n = prop == BlockStateProperties.HORIZONTAL_AXIS ? (a == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X)
                        : a == Direction.Axis.X ? Direction.Axis.Y : a == Direction.Axis.Y ? Direction.Axis.Z : Direction.Axis.X;
                return state.setValue(prop, n);
            }
        }
        return null;
    }

    /** The six directions in a fixed round: east, south, west, north, up, down. */
    private static final Direction[] ROUND = {Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH, Direction.UP, Direction.DOWN};

    private static Direction next(Direction d) {
        for (int i = 0; i < ROUND.length; i++) {
            if (ROUND[i] == d) {
                return ROUND[(i + 1) % ROUND.length];
            }
        }
        return d;
    }

    private static Direction previous(Direction d) {
        for (int i = 0; i < ROUND.length; i++) {
            if (ROUND[i] == d) {
                return ROUND[(i + ROUND.length - 1) % ROUND.length];
            }
        }
        return d;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bsp_core.wrench").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.bsp_core.wrench.sneak").withStyle(ChatFormatting.DARK_GRAY));
    }
}
