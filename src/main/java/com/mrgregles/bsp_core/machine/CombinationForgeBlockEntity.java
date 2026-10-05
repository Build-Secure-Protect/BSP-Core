package com.mrgregles.bsp_core.machine;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Nine nuggets in, one ingot out; or one Tetrium Ingot in, one Tetrium Plate out. Forges Tetrium as
 * built; Illyrium only once an Illyrium Forge Upgrade sits in the upgrade slot. Burns furnace fuel, or runs on RF at twice the speed with an RF upgrade.
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
            case IN -> stack.is(ModItems.TETRIUM_NUGGET.get()) || stack.is(ModItems.ILLYRIUM_NUGGET.get()) || stack.is(ModItems.MAGNATITE_NUGGET.get())
                    || stack.is(ModItems.TETRIUM_INGOT.get());
            case UPGRADE -> stack.is(ModItems.ILLYRIUM_FORGE_UPGRADE.get());
            case FUEL -> net.minecraftforge.common.ForgeHooks.getBurnTime(stack, null) > 0;
            default -> false;
        };
    }

    /** How many of the input one job uses: one ingot for a plate, nine nuggets for an ingot. */
    private int inputCost() {
        return items.getStackInSlot(IN).is(ModItems.TETRIUM_INGOT.get()) ? 1 : NUGGETS_PER_INGOT;
    }

    /** What the current input would make, or EMPTY if it cannot be forged right now. */
    private ItemStack result() {
        ItemStack in = items.getStackInSlot(IN);
        if (in.is(ModItems.TETRIUM_INGOT.get())) {
            return new ItemStack(ModItems.TETRIUM_PLATE.get());
        }
        if (in.getCount() < NUGGETS_PER_INGOT) {
            return ItemStack.EMPTY;
        }
        if (in.is(ModItems.TETRIUM_NUGGET.get())) {
            return new ItemStack(ModItems.TETRIUM_INGOT.get());
        }
        if (in.is(ModItems.MAGNATITE_NUGGET.get())) {
            return new ItemStack(ModItems.MAGNATITE_INGOT.get());
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
        items.setStackInSlot(IN, in.copyWithCount(in.getCount() - inputCost()));
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
    public List<Need> missing(int fluidMb, boolean burning) {
        List<Need> out = new java.util.ArrayList<>();
        ItemStack in = items.getStackInSlot(IN);
        if (in.getCount() < inputCost()) {
            out.add(need(ModItems.TETRIUM_NUGGET.get(), "need.bsp_core.nine_nuggets"));
        } else if (in.is(ModItems.ILLYRIUM_NUGGET.get()) && !hasIllyriumUpgrade()) {
            out.add(need(ModItems.ILLYRIUM_FORGE_UPGRADE.get(), "need.bsp_core.forge_upgrade"));
        }
        if (!rfActive() && !burning && net.minecraftforge.common.ForgeHooks.getBurnTime(items.getStackInSlot(FUEL), null) <= 0) {
            out.add(need(net.minecraft.world.item.Items.COAL, "need.bsp_core.fuel_or_rf"));
        }
        ItemStack result = result();
        if (!result.isEmpty() && !fits(OUT, result)) {
            out.add(need(ModItems.TETRIUM_INGOT.get(), "need.bsp_core.output_full"));
        }
        return out;
    }

    @Override
    public ItemStack slotIcon(int slot) {
        if (isRfSlot(slot)) {
            return new ItemStack(ModItems.RF_UPGRADES.get(0).get());
        }
        return switch (slot) {
            case IN -> new ItemStack(ModItems.TETRIUM_NUGGET.get(), 9);
            case OUT -> new ItemStack(ModItems.TETRIUM_INGOT.get());
            case UPGRADE -> new ItemStack(ModItems.ILLYRIUM_FORGE_UPGRADE.get());
            default -> new ItemStack(net.minecraft.world.item.Items.COAL);
        };
    }

    @Override
    public net.minecraft.network.chat.Component slotHint(int slot) {
        if (isRfSlot(slot)) {
            return net.minecraft.network.chat.Component.translatable("hint.bsp_core.rf");
        }
        return net.minecraft.network.chat.Component.translatable(switch (slot) {
            case IN -> "hint.bsp_core.nuggets";
            case OUT -> "hint.bsp_core.out_ingot";
            case UPGRADE -> "hint.bsp_core.forge_upgrade";
            default -> "hint.bsp_core.fuel";
        });
    }

    @Override
    public String titleKey() {
        return "block.bsp_core.combination_forge";
    }
}
