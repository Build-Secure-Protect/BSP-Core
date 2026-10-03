package com.mrgregles.bsp_core.machine;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;

import java.util.List;

/** Tetrium Ore in, Tetrium Nuggets and Tetrium Slag out. Burns any furnace fuel. */
public class TetriumCrucibleBlockEntity extends MachineBlockEntity {
    public static final TagKey<Item> TETRIUM_ORES = TagKey.create(Registries.ITEM, new ResourceLocation(BSPCore.MODID, "tetrium_ores"));
    private static final int ORE = 0, FUEL = 1, NUGGETS = 2, SLAG = 3;
    private static final List<Slot> LAYOUT = List.of(
            new Slot(Role.INPUT, 44, 22), new Slot(Role.FUEL, 44, 58), new Slot(Role.OUTPUT, 110, 30), new Slot(Role.OUTPUT, 132, 30), new Slot(Role.RF, 152, 60));

    public TetriumCrucibleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TETRIUM_CRUCIBLE.get(), pos, state, LAYOUT);
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return switch (slot) {
            case ORE -> stack.is(TETRIUM_ORES);
            case FUEL -> ForgeHooks.getBurnTime(stack, null) > 0;
            default -> false;
        };
    }

    private ItemStack nuggets() {
        return new ItemStack(ModItems.TETRIUM_NUGGET.get(), BSPConfig.TCRUC_NUGGETS.get());
    }

    private ItemStack slag() {
        return new ItemStack(ModItems.TETRIUM_SLAG.get(), BSPConfig.TCRUC_SLAG.get());
    }

    @Override
    protected boolean canWork() {
        return items.getStackInSlot(ORE).is(TETRIUM_ORES) && fits(NUGGETS, nuggets()) && fits(SLAG, slag());
    }

    @Override
    public int workTime() {
        return BSPConfig.TCRUC_TICKS.get();
    }

    @Override
    protected void finishJob() {
        ItemStack ore = items.getStackInSlot(ORE);
        items.setStackInSlot(ORE, ore.copyWithCount(ore.getCount() - 1));
        addOutput(NUGGETS, nuggets());
        addOutput(SLAG, slag());
    }

    @Override
    protected boolean usesFuel() {
        return true;
    }

    @Override
    public String titleKey() {
        return "block.bsp_core.tetrium_crucible";
    }
}
