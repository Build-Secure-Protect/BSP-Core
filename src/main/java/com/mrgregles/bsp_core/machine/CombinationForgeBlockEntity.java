package com.mrgregles.bsp_core.machine;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Nine nuggets in, one ingot out. Forges Tetrium as built; Illyrium only once an Illyrium Forge
 * Upgrade sits in the upgrade slot. Burns furnace fuel, or runs on RF at twice the speed with an RF upgrade.
 */
public class CombinationForgeBlockEntity extends MachineBlockEntity {
    public static final int NUGGETS_PER_INGOT = 9;
    private static final int IN = 0, OUT = 1, UPGRADE = 2, FUEL = 3;
    private static final List<Slot> LAYOUT = List.of(
            new Slot(Role.INPUT, 44, 22), new Slot(Role.OUTPUT, 120, 34), new Slot(Role.UPGRADE, 80, 60), new Slot(Role.FUEL, 44, 58), new Slot(Role.RF, 152, 60));

    public CombinationForgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COMBINATION_FORGE.get(), pos, state, LAYOUT);
    }

    public boolean hasIllyriumUpgrade() {
        return items.getStackInSlot(UPGRADE).is(ModItems.ILLYRIUM_FORGE_UPGRADE.get());
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return switch (slot) {
            case IN -> stack.is(ModItems.TETRIUM_NUGGET.get()) || stack.is(ModItems.ILLYRIUM_NUGGET.get());
            case UPGRADE -> stack.is(ModItems.ILLYRIUM_FORGE_UPGRADE.get());
            case FUEL -> net.minecraftforge.common.ForgeHooks.getBurnTime(stack, null) > 0;
            default -> false;
        };
    }

    /** The ingot the current input would make, or EMPTY if it cannot be forged right now. */
    private ItemStack result() {
        ItemStack in = items.getStackInSlot(IN);
        if (in.getCount() < NUGGETS_PER_INGOT) {
            return ItemStack.EMPTY;
        }
        if (in.is(ModItems.TETRIUM_NUGGET.get())) {
            return new ItemStack(ModItems.TETRIUM_INGOT.get());
        }
        if (in.is(ModItems.ILLYRIUM_NUGGET.get()) && hasIllyriumUpgrade()) {
            return new ItemStack(ModItems.ILLYRIUM_INGOT.get());
        }
        return ItemStack.EMPTY;
    }

    @Override
    protected boolean canWork() {
        ItemStack result = result();
        return !result.isEmpty() && fits(OUT, result);
    }

    @Override
    public int workTime() {
        return BSPConfig.FORGE_TICKS.get();
    }

    @Override
    protected void finishJob() {
        ItemStack result = result();
        ItemStack in = items.getStackInSlot(IN);
        items.setStackInSlot(IN, in.copyWithCount(in.getCount() - NUGGETS_PER_INGOT));
        addOutput(OUT, result);
    }

    /** Burns furnace fuel unless it is running on RF. */
    @Override
    protected boolean usesFuel() {
        return !rfActive();
    }

    /** On RF the forge runs faster than on fuel by the configured multiple, plus the upgrade's own bonus. */
    @Override
    protected double speed() {
        return rfActive() ? BSPConfig.FORGE_RF_MULTIPLIER.get() * (1.0 + rfBonus()) : 1.0;
    }

    @Override
    public String titleKey() {
        return "block.bsp_core.combination_forge";
    }
}
