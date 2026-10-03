package com.mrgregles.bsp_core.machine;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Illyrium Crucible controller. Two jobs, chosen by what is in the inputs:
 * smelt Illyrium Ore with Tetrium Slag into a Dirty Illyrium Ingot, or alloy Pure Illyrium Dust
 * with Tetrium Dust into Illyrium Nuggets. Runs only on lava (buckets, magma blocks or piped in).
 */
public class IllyriumCrucibleBlockEntity extends MultiblockControllerBlockEntity {
    public static final TagKey<Item> ILLYRIUM_ORES = TagKey.create(Registries.ITEM, new ResourceLocation(BSPCore.MODID, "illyrium_ores"));
    private static final int MAIN = 0, ADDITIVE = 1, LAVA = 2, OUT = 3;
    private static final List<Slot> LAYOUT = List.of(
            new Slot(Role.INPUT, 40, 22), new Slot(Role.INPUT, 58, 22), new Slot(Role.FUEL, 49, 58), new Slot(Role.OUTPUT, 120, 34), new Slot(Role.RF, 152, 60));
    // bottom: casing floor. middle: Lava Pylons at the corners, Item Hatches left and right, controller front-centre.
    // top: pylons continue at the corners, joined by casing.
    private static final String[][] PATTERN = {
            {"CCC", "CCC", "CCC"},
            {"P P", "H H", "PXP"},
            {"PCP", "C C", "PCP"}};

    public IllyriumCrucibleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ILLYRIUM_CRUCIBLE.get(), pos, state, LAYOUT, Fluids.LAVA);
    }

    @Override
    protected String[][] pattern() {
        return PATTERN;
    }

    @Nullable
    @Override
    protected Block blockFor(char c) {
        return c == 'C' ? ModBlocks.ILLYRIUM_CASING.get() : c == 'P' ? ModBlocks.LAVA_PYLON.get() : c == 'H' ? ModBlocks.ITEM_HATCH.get() : null;
    }

    @Override
    protected int tankCapacity() {
        return BSPConfig.ICRUC_TANK.get();
    }

    @Override
    protected Fluid fluid() {
        return Fluids.LAVA;
    }

    @Override
    public int fluidColor() {
        return 0xFFFF7A1A;
    }

    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        return switch (slot) {
            case MAIN -> stack.is(ILLYRIUM_ORES) || stack.is(ModItems.PURE_ILLYRIUM_DUST.get());
            case ADDITIVE -> stack.is(ModItems.TETRIUM_SLAG.get()) || stack.is(ModItems.TETRIUM_DUST.get());
            case LAVA -> stack.is(Items.LAVA_BUCKET) || stack.is(Items.MAGMA_BLOCK);
            default -> false;
        };
    }

    private boolean smelting() {
        return items.getStackInSlot(MAIN).is(ILLYRIUM_ORES);
    }

    private boolean alloying() {
        return items.getStackInSlot(MAIN).is(ModItems.PURE_ILLYRIUM_DUST.get());
    }

    private int mainNeeded() {
        return smelting() ? BSPConfig.ICRUC_ORE_IN.get() : BSPConfig.ICRUC_PURE_IN.get();
    }

    private int additiveNeeded() {
        return smelting() ? BSPConfig.ICRUC_SLAG_IN.get() : BSPConfig.ICRUC_TDUST_IN.get();
    }

    private ItemStack result() {
        if (smelting()) {
            return new ItemStack(ModItems.DIRTY_ILLYRIUM_INGOT.get());
        }
        if (alloying()) {
            return new ItemStack(ModItems.ILLYRIUM_NUGGET.get(), BSPConfig.ICRUC_NUGGETS_OUT.get());
        }
        return ItemStack.EMPTY;
    }

    @Override
    protected boolean canWork() {
        ItemStack result = result();
        if (result.isEmpty() || !fits(OUT, result)) {
            return false;
        }
        ItemStack add = items.getStackInSlot(ADDITIVE);
        boolean rightAdditive = smelting() ? add.is(ModItems.TETRIUM_SLAG.get()) : add.is(ModItems.TETRIUM_DUST.get());
        return items.getStackInSlot(MAIN).getCount() >= mainNeeded()
                && (additiveNeeded() == 0 || (rightAdditive && add.getCount() >= additiveNeeded()))
                && tank.getFluidAmount() >= BSPConfig.ICRUC_LAVA_PER_JOB.get();
    }

    @Override
    public int workTime() {
        return alloying() ? BSPConfig.ICRUC_ALLOY_TICKS.get() : BSPConfig.ICRUC_SMELT_TICKS.get();
    }

    @Override
    protected void finishJob() {
        ItemStack result = result();
        int main = mainNeeded(), add = additiveNeeded();
        drain((int) Math.round(BSPConfig.ICRUC_LAVA_PER_JOB.get() * lavaFactor()));
        ItemStack m = items.getStackInSlot(MAIN), a = items.getStackInSlot(ADDITIVE);
        items.setStackInSlot(MAIN, m.copyWithCount(m.getCount() - main));
        items.setStackInSlot(ADDITIVE, a.copyWithCount(a.getCount() - add));
        addOutput(OUT, result);
    }

    @Override
    protected void refill() {
        if (!fillFrom(LAVA, new ItemStack(Items.LAVA_BUCKET), bucket(), 1000)) {
            fillFrom(LAVA, new ItemStack(Items.MAGMA_BLOCK), ItemStack.EMPTY, BSPConfig.ICRUC_LAVA_PER_MAGMA.get());
        }
    }

    @Override
    public String titleKey() {
        return "block.bsp_core.illyrium_crucible";
    }
}
