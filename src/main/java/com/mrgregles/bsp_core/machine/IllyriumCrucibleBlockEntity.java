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
            new Slot(Role.INPUT, 34, 31), new Slot(Role.INPUT, 54, 31), new Slot(Role.FUEL, 44, 58), new Slot(Role.OUTPUT, 120, 34), new Slot(Role.RF, 152, 60));
    // bottom: casing floor with the controller in the middle of the front edge. middle: Lava Pylons at the corners,
    // Item Hatches left and right, Illyrium Core in the centre.
    // top: pylons continue at the corners, joined by casing.
    private static final String[][] PATTERN = {
            {"CCC", "CCC", "CXC"},
            {"P P", "HOH", "P P"},
            {"PCP", "C C", "PCP"}};

    public IllyriumCrucibleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ILLYRIUM_CRUCIBLE.get(), pos, state, LAYOUT, Fluids.LAVA);
        // port 0 is the left hatch (the output tray of the model), port 1 the right hatch (the input hopper)
        setSideMode(0, com.mrgregles.bsp_core.coin.CoinFactoryBlockEntity.SideMode.OUTPUT);
        setSideMode(1, com.mrgregles.bsp_core.coin.CoinFactoryBlockEntity.SideMode.INPUT);
    }

    @Override
    protected String[][] pattern() {
        return PATTERN;
    }

    @Nullable
    @Override
    protected Block blockFor(char c) {
        return c == 'C' ? ModBlocks.ILLYRIUM_CASING.get() : c == 'P' ? ModBlocks.LAVA_PYLON.get() : c == 'H' ? ModBlocks.ITEM_HATCH.get() : c == 'O' ? ModBlocks.ILLYRIUM_CORE.get() : null;
    }

    @Override
    protected boolean isItemPort(Block block) {
        return block == ModBlocks.ITEM_HATCH.get();
    }

    @Override
    public String itemPortKey(int i) {
        return i == 0 ? "gui.bsp_core.port.hatch_left" : "gui.bsp_core.port.hatch_right";
    }

    // Lower pylon blocks (orange sockets) take lava only, upper pylon blocks (yellow sockets) take RF only.
    @Override
    protected boolean portTakesFluid(BlockPos port) {
        return port.getY() == worldPosition.getY() + 1;
    }

    @Override
    protected boolean portTakesEnergy(BlockPos port) {
        return port.getY() == worldPosition.getY() + 2;
    }

    @Override
    public String fluidPortKey() {
        return "gui.bsp_core.port.pylons";
    }

    @Override
    protected boolean hidesParts() {
        return true;
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

    /** True while the main input is Pure Illyrium Dust (the alloy job). Used by the renderer for the melt colour. */
    public boolean isAlloying() {
        return alloying();
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
    public List<Need> missing(int fluidMb, boolean burning) {
        List<Need> out = new java.util.ArrayList<>();
        if (!isFormed()) {
            out.add(need(ModBlocks.ILLYRIUM_CASING.get(), "need.bsp_core.structure"));
        }
        if (!smelting() && !alloying()) {
            out.add(need(ModBlocks.DEEPSLATE_ILLYRIUM_ORE.get(), "need.bsp_core.crucible_input"));
        } else {
            ItemStack add = items.getStackInSlot(ADDITIVE);
            boolean right = smelting() ? add.is(ModItems.TETRIUM_SLAG.get()) : add.is(ModItems.TETRIUM_DUST.get());
            if (items.getStackInSlot(MAIN).getCount() < mainNeeded()) {
                out.add(need(items.getStackInSlot(MAIN).getItem(), "need.bsp_core.more_input", mainNeeded()));
            }
            if (additiveNeeded() > 0 && (!right || add.getCount() < additiveNeeded())) {
                out.add(smelting() ? need(ModItems.TETRIUM_SLAG.get(), "need.bsp_core.slag", additiveNeeded())
                        : need(ModItems.TETRIUM_DUST.get(), "need.bsp_core.tetrium_dust", additiveNeeded()));
            }
            if (!fits(OUT, result())) {
                out.add(need(result().getItem(), "need.bsp_core.output_full"));
            }
        }
        if (fluidMb < BSPConfig.ICRUC_LAVA_PER_JOB.get()) {
            out.add(need(Items.LAVA_BUCKET, "need.bsp_core.lava"));
        }
        return out;
    }

    @Override
    public ItemStack slotIcon(int slot) {
        if (isRfSlot(slot)) {
            return new ItemStack(ModItems.RF_UPGRADES.get(0).get());
        }
        return switch (slot) {
            case MAIN -> new ItemStack(ModBlocks.DEEPSLATE_ILLYRIUM_ORE.get());
            case ADDITIVE -> new ItemStack(ModItems.TETRIUM_SLAG.get());
            case LAVA -> new ItemStack(Items.LAVA_BUCKET);
            default -> new ItemStack(ModItems.DIRTY_ILLYRIUM_INGOT.get());
        };
    }

    @Override
    public net.minecraft.network.chat.Component slotHint(int slot) {
        if (isRfSlot(slot)) {
            return net.minecraft.network.chat.Component.translatable("hint.bsp_core.rf");
        }
        return net.minecraft.network.chat.Component.translatable(switch (slot) {
            case MAIN -> "hint.bsp_core.crucible_main";
            case ADDITIVE -> "hint.bsp_core.crucible_additive";
            case LAVA -> "hint.bsp_core.lava";
            default -> "hint.bsp_core.crucible_out";
        });
    }

    @Override
    public String titleKey() {
        return "block.bsp_core.illyrium_crucible";
    }
}
