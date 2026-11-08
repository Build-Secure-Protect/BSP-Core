package com.mrgregles.bsp_core.shield;

import com.mrgregles.bsp_core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Totem Radiation Coating on the radiation shielding set. A Radiation Shielding Panel, Window or Door with {@link #COATED} weakens
 * a totem's radiation when it stands between the totem and the world ({@link com.mrgregles.bsp_core.totem.TotemRadiation}). The
 * coating is applied with {@link TotemCoatingItem}, one tin per block (a door takes one for both halves), cannot be taken off, and
 * stays with the block when it is broken (the loot tables copy the state into the item's {@code BlockStateTag}). It changes nothing
 * about Voltaic's shielding.
 */
public final class TotemCoating {
    public static final BooleanProperty COATED = BooleanProperty.create("coated");

    private TotemCoating() {}

    /** Whether {@code state} is a shielding panel, window or door, coated or not. */
    public static boolean coatable(BlockState state) {
        return state.is(ModBlocks.RADIATION_SHIELDING_PANEL.get()) || state.is(ModBlocks.RADIATION_SHIELDING_WINDOW.get())
                || state.is(ModBlocks.RADIATION_SHIELDING_DOOR.get());
    }

    public static boolean coated(BlockState state) {
        return state.hasProperty(COATED) && state.getValue(COATED);
    }

    /** Coats the block at {@code pos}, and a door's other half with it. Returns false when there is nothing to coat. */
    public static boolean coat(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!coatable(state) || coated(state)) {
            return false;
        }
        level.setBlock(pos, state.setValue(COATED, true), 3);
        if (state.getBlock() instanceof DoorBlock) {
            BlockPos other = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState o = level.getBlockState(other);
            if (o.is(state.getBlock()) && o.hasProperty(COATED)) {
                level.setBlock(other, o.setValue(COATED, true), 3);
            }
        }
        return true;
    }

    /** Whether a shielding item carries the coating (its {@code BlockStateTag}). */
    public static boolean coated(ItemStack stack) {
        CompoundTag tag = stack.getTag() == null ? null : stack.getTag().getCompound(BlockItem.BLOCK_STATE_TAG);
        return tag != null && "true".equals(tag.getString(COATED.getName()));
    }

    /** Makes {@code stack} (a panel, window or door) a coated one. */
    public static ItemStack coat(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTagElement(BlockItem.BLOCK_STATE_TAG);
        tag.putString(COATED.getName(), "true");
        return stack;
    }
}
