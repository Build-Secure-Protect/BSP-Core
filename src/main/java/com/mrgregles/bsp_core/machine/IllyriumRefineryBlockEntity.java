package com.mrgregles.bsp_core.machine;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.material.FilterItem;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Illyrium Refinery controller. Dirty Illyrium Dust plus water, through a filter, becomes Pure
 * Illyrium Dust. Each dust uses the filter once; a spent filter is consumed.
 */
public class IllyriumRefineryBlockEntity extends MultiblockControllerBlockEntity {
    private static final int IN = 0, WATER = 1, FILTER = 2, OUT = 3;
    private static final List<Slot> LAYOUT = List.of(
            new Slot(Role.INPUT, 44, 22), new Slot(Role.FUEL, 44, 58), new Slot(Role.UPGRADE, 80, 58), new Slot(Role.OUTPUT, 120, 34), new Slot(Role.RF, 152, 60));
    // bottom: casing floor. middle: glass tank core with casing behind, Refinery Pump on the left, Item Hatch on the right,
    // controller in front. top: glass tank cap.
    private static final String[][] PATTERN = {
            {"CCC", "CCC", "CCC"},
            {" C ", "UGH", " X "},
            {"   ", " G ", "   "}};

    public IllyriumRefineryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ILLYRIUM_REFINERY.get(), pos, state, LAYOUT, Fluids.WATER);
    }

    @Override
    protected String[][] pattern() {
        return PATTERN;
    }

    @Nullable
    @Override
    protected Block blockFor(char c) {
        return c == 'C' ? ModBlocks.ILLYRIUM_CASING.get() : c == 'G' ? ModBlocks.ILLYRIUM_GLASS.get()
                : c == 'U' ? ModBlocks.REFINERY_PUMP.get() : c == 'H' ? ModBlocks.ITEM_HATCH.get() : null;
    }

    @Override
    protected int tankCapacity() {
        return BSPConfig.REFINERY_TANK.get();
    }

    @Override
    protected Fluid fluid() {
        return Fluids.WATER;
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return switch (slot) {
            case IN -> stack.is(ModItems.DIRTY_ILLYRIUM_DUST.get());
            case WATER -> stack.is(Items.WATER_BUCKET);
            case FILTER -> stack.getItem() instanceof FilterItem;
            default -> false;
        };
    }

    private ItemStack result() {
        return new ItemStack(ModItems.PURE_ILLYRIUM_DUST.get());
    }

    @Override
    protected boolean canWork() {
        ItemStack filter = items.getStackInSlot(FILTER);
        return items.getStackInSlot(IN).is(ModItems.DIRTY_ILLYRIUM_DUST.get())
                && filter.getItem() instanceof FilterItem f && f.usesLeft(filter) > 0
                && tank.getFluidAmount() >= BSPConfig.REFINERY_WATER.get()
                && fits(OUT, result());
    }

    @Override
    public int workTime() {
        return BSPConfig.REFINERY_TICKS.get();
    }

    @Override
    protected void finishJob() {
        drain(BSPConfig.REFINERY_WATER.get());
        ItemStack in = items.getStackInSlot(IN);
        items.setStackInSlot(IN, in.copyWithCount(in.getCount() - 1));
        ItemStack filter = items.getStackInSlot(FILTER).copy();
        if (filter.getItem() instanceof FilterItem f) {
            items.setStackInSlot(FILTER, f.useOnce(filter) ? ItemStack.EMPTY : filter);
        }
        addOutput(OUT, result());
    }

    @Override
    protected void refill() {
        fillFrom(WATER, new ItemStack(Items.WATER_BUCKET), bucket(), 1000);
    }

    @Override
    public String titleKey() {
        return "block.bsp_core.illyrium_refinery";
    }
}
