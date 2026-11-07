package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.machine.TetriumCrucibleBlockEntity;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.BooleanSupplier;

/**
 * GameTests for the Crucible Yield Upgrade: a Tetrium Crucible makes the base nuggets per ore plus the configured amount for each
 * upgrade fitted (4, 6, 8, 10 by default), slag doubles only with all three, the three slots take only the upgrade and one each,
 * pipes cannot put upgrades in or take them out, a crucible saved with five slots loads with all eight and its items in place, and
 * a saved nuggetsPerOre of 2 moves to 4 once. Jobs run on lava and are waited for by polling.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class CrucibleYieldGameTests {
    private static final String EMPTY = "empty", BATCH = "yield";
    private static final int ORE = 0, NUGGETS = 2, SLAG = 3, RF = 4, FIRST_YIELD = 5;

    private static TetriumCrucibleBlockEntity crucible(GameTestHelper h, BlockPos p) {
        h.setBlock(p, ModBlocks.TETRIUM_CRUCIBLE.get());
        return (TetriumCrucibleBlockEntity) h.getBlockEntity(p);
    }

    private static ItemStack rawTetrium(int n) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(BSPCore.MODID, "raw_tetrium")), n);
    }

    private static ItemStack upgrade() {
        return new ItemStack(ModItems.CRUCIBLE_YIELD_UPGRADE.get());
    }

    private static void when(GameTestHelper h, int ticks, BooleanSupplier ok, Runnable then, String why) {
        if (ok.getAsBoolean()) {
            then.run();
        } else if (ticks <= 0) {
            h.fail(why);
        } else {
            h.runAfterDelay(1, () -> when(h, ticks - 1, ok, then, why));
        }
    }

    /** One ore smelted on lava with {@code fitted} upgrades in: base + per-upgrade x fitted nuggets, and slag doubled only at three. */
    private static void smeltWith(GameTestHelper h, int fitted) {
        TetriumCrucibleBlockEntity be = crucible(h, new BlockPos(2, 1, 2));
        for (int i = 0; i < fitted; i++) {
            h.assertTrue(be.getItems().insertItem(FIRST_YIELD + i, upgrade(), false).isEmpty(), "yield slot " + i + " takes an upgrade");
        }
        h.assertTrue(be.yieldUpgrades() == fitted, fitted + " upgrades counted, got " + be.yieldUpgrades());
        be.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new)
                .fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);
        be.getItems().insertItem(ORE, rawTetrium(1), false);
        int nuggets = BSPConfig.TCRUC_NUGGETS.get() + BSPConfig.TCRUC_PER_UPGRADE.get() * fitted;
        int slag = BSPConfig.TCRUC_SLAG.get() * (fitted == 3 ? 2 : 1);
        when(h, be.workTime() * 2 + 900, () -> !be.getItems().getStackInSlot(NUGGETS).isEmpty(), () -> {
            ItemStack n = be.getItems().getStackInSlot(NUGGETS), s = be.getItems().getStackInSlot(SLAG);
            h.assertTrue(n.is(ModItems.TETRIUM_NUGGET.get()) && n.getCount() == nuggets, "with " + fitted + " upgrades one ore gives " + nuggets + " nuggets, got " + n);
            h.assertTrue(s.is(ModItems.TETRIUM_SLAG.get()) && s.getCount() == slag, "with " + fitted + " upgrades one ore gives " + slag + " slag, got " + s);
            h.succeed();
        }, "the crucible never smelted with " + fitted + " upgrades");
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 2400)
    public void crucible_yield_with_no_upgrade(GameTestHelper h) {
        h.assertTrue(BSPConfig.TCRUC_NUGGETS.getDefault() == 4 && BSPConfig.TCRUC_PER_UPGRADE.getDefault() == 2,
                "shipped defaults are 4 nuggets per ore and 2 more per upgrade");
        smeltWith(h, 0);
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 2400)
    public void crucible_yield_with_one_upgrade(GameTestHelper h) {
        smeltWith(h, 1);
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 2400)
    public void crucible_yield_with_two_upgrades(GameTestHelper h) {
        smeltWith(h, 2);
    }

    /** All three: 10 nuggets by default, still nuggets and never an ingot, and the slag doubles. */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 2400)
    public void crucible_yield_with_three_upgrades_doubles_the_slag(GameTestHelper h) {
        smeltWith(h, 3);
    }

    /** The yield slots take only the upgrade, one each; other slots refuse it; pipes from any side cannot insert or extract upgrades. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void yield_slots_take_only_the_upgrade_by_hand(GameTestHelper h) {
        TetriumCrucibleBlockEntity be = crucible(h, new BlockPos(2, 1, 2));
        h.assertTrue(be.getItems().getSlots() == 8, "the crucible has eight slots, got " + be.getItems().getSlots());
        h.assertTrue(!be.getItems().insertItem(FIRST_YIELD, new ItemStack(Items.COAL), false).isEmpty(), "a yield slot refuses coal");
        h.assertTrue(!be.getItems().insertItem(ORE, upgrade(), false).isEmpty() && !be.getItems().insertItem(RF, upgrade(), false).isEmpty(),
                "the ore and RF slots refuse the upgrade");
        ItemStack two = new ItemStack(ModItems.CRUCIBLE_YIELD_UPGRADE.get(), 2);
        ItemStack left = be.getItems().insertItem(FIRST_YIELD, two, false);
        h.assertTrue(left.getCount() == 1 && be.getItems().getStackInSlot(FIRST_YIELD).getCount() == 1, "a yield slot holds one upgrade");
        for (Direction d : Direction.values()) {
            IItemHandler pipe = be.getCapability(ForgeCapabilities.ITEM_HANDLER, d).orElseThrow(IllegalStateException::new);
            for (int i = FIRST_YIELD; i < FIRST_YIELD + 3; i++) {
                h.assertTrue(!pipe.insertItem(i, upgrade(), true).isEmpty(), "a pipe on " + d + " cannot put an upgrade in slot " + i);
            }
            h.assertTrue(pipe.extractItem(FIRST_YIELD, 1, true).isEmpty(), "a pipe on " + d + " cannot take an upgrade out");
        }
        h.succeed();
    }

    /** A crucible saved before the yield slots (five slots) loads with eight, its ore, nuggets and RF upgrade where they were. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void crucible_saved_with_five_slots_loads_with_eight(GameTestHelper h) {
        TetriumCrucibleBlockEntity be = crucible(h, new BlockPos(2, 1, 2));
        CompoundTag tag = be.saveWithoutMetadata();
        ItemStackHandler old = new ItemStackHandler(5);
        old.setStackInSlot(ORE, rawTetrium(7));
        old.setStackInSlot(NUGGETS, new ItemStack(ModItems.TETRIUM_NUGGET.get(), 5));
        old.setStackInSlot(RF, new ItemStack(ModItems.RF_UPGRADES.get(0).get()));
        tag.put("Items", old.serializeNBT());
        h.assertTrue(tag.getCompound("Items").getInt("Size") == 5, "the old save says five slots");
        TetriumCrucibleBlockEntity copy = crucible(h, new BlockPos(4, 1, 2));
        copy.load(tag);
        h.assertTrue(copy.getItems().getSlots() == 8, "it loads with eight slots, got " + copy.getItems().getSlots());
        h.assertTrue(copy.getItems().getStackInSlot(ORE).getCount() == 7 && copy.getItems().getStackInSlot(NUGGETS).getCount() == 5
                && copy.getItems().getStackInSlot(RF).is(ModItems.RF_UPGRADES.get(0).get()), "the saved items are where they were");
        h.assertTrue(copy.getItems().getStackInSlot(FIRST_YIELD + 2).isEmpty() && copy.yieldUpgrades() == 0, "the new yield slots are empty and usable");
        h.assertTrue(copy.getItems().insertItem(FIRST_YIELD + 2, upgrade(), false).isEmpty() && copy.yieldUpgrades() == 1, "an upgrade fits the last new slot");
        h.succeed();
    }

    /** A saved nuggetsPerOre of 2 (the old default) moves to 4 once; a 2 set after that stays; any other value is left alone. */
    @GameTest(template = EMPTY, batch = "yield_config") // its own batch: it saves the config file, which other tests read
    public void old_default_of_two_nuggets_moves_to_four_once(GameTestHelper h) {
        int was = BSPConfig.TCRUC_NUGGETS.get();
        boolean moved = BSPConfig.TCRUC_NUGGETS_MOVED.get();
        try {
            h.assertTrue(moved, "the step already ran when the server loaded its config");
            BSPConfig.TCRUC_NUGGETS.set(2);
            BSPConfig.TCRUC_NUGGETS_MOVED.set(false);
            h.assertTrue(BSPConfig.moveOldCrucibleYield() && BSPConfig.TCRUC_NUGGETS.get() == 4 && BSPConfig.TCRUC_NUGGETS_MOVED.get(), "2 moves to 4 and is marked");
            BSPConfig.TCRUC_NUGGETS.set(2);
            h.assertTrue(!BSPConfig.moveOldCrucibleYield() && BSPConfig.TCRUC_NUGGETS.get() == 2, "an admin's 2 after the step is kept");
            BSPConfig.TCRUC_NUGGETS.set(6);
            BSPConfig.TCRUC_NUGGETS_MOVED.set(false);
            h.assertTrue(!BSPConfig.moveOldCrucibleYield() && BSPConfig.TCRUC_NUGGETS.get() == 6 && BSPConfig.TCRUC_NUGGETS_MOVED.get(), "a 6 is left alone and marked");
        } finally {
            BSPConfig.TCRUC_NUGGETS.set(was);
            BSPConfig.TCRUC_NUGGETS_MOVED.set(moved);
            BSPConfig.SPEC.save();
        }
        h.succeed();
    }

    /** The menu's UPGRADES row: the crucible's three yield slots touch from x 196 (196, 214, 232) and its RF slot follows a gap at 262; the forge's one upgrade sits at 196 and its RF at 226; a machine with only an RF slot keeps it at 196. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void upgrades_row_groups_the_yield_slots_then_rf_after_a_gap(GameTestHelper h) {
        var player = MockPlayers.serverPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);
        TetriumCrucibleBlockEntity crucible = crucible(h, new BlockPos(2, 1, 2));
        var menu = new com.mrgregles.bsp_core.machine.MachineMenu(0, player.getInventory(), crucible, crucible.data);
        int[][] want = {{FIRST_YIELD, 196}, {FIRST_YIELD + 1, 214}, {FIRST_YIELD + 2, 232}, {RF, 262}};
        for (int[] w : want) {
            var slot = menu.slots.get(w[0]);
            h.assertTrue(slot.x == w[1] && slot.y == 124, "crucible slot " + w[0] + " sits at " + w[1] + ",124, got " + slot.x + "," + slot.y);
        }
        h.setBlock(new BlockPos(4, 1, 2), ModBlocks.COMBINATION_FORGE.get());
        var forge = (com.mrgregles.bsp_core.machine.MachineBlockEntity) h.getBlockEntity(new BlockPos(4, 1, 2));
        var forgeMenu = new com.mrgregles.bsp_core.machine.MachineMenu(0, player.getInventory(), forge, forge.data);
        h.assertTrue(forgeMenu.slots.get(2).x == 196 && forgeMenu.slots.get(4).x == 226, "the forge's upgrade at 196 and RF at 226, got "
                + forgeMenu.slots.get(2).x + " and " + forgeMenu.slots.get(4).x);
        h.setBlock(new BlockPos(6, 1, 2), ModBlocks.ILLYRIUM_CRUCIBLE.get());
        var multi = (com.mrgregles.bsp_core.machine.MachineBlockEntity) h.getBlockEntity(new BlockPos(6, 1, 2));
        var multiMenu = new com.mrgregles.bsp_core.machine.MachineMenu(0, player.getInventory(), multi, multi.data);
        for (int i = 0; i < multi.layout().size(); i++) {
            if (multi.layout().get(i).role() == com.mrgregles.bsp_core.machine.MachineBlockEntity.Role.RF) {
                h.assertTrue(multiMenu.slots.get(i).x == 196, "a machine with only an RF slot keeps it at 196, got " + multiMenu.slots.get(i).x);
            }
        }
        h.succeed();
    }

    /** The recipe: Tetrium Plates in the corners, Tetrium Ingots on the sides, a Basic Control Circuit in the middle, one upgrade out. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void crucible_yield_upgrade_recipe(GameTestHelper h) {
        var level = h.getLevel();
        var grid = new net.minecraft.world.inventory.TransientCraftingContainer(new net.minecraft.world.inventory.AbstractContainerMenu(null, -1) {
            @Override
            public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player p, int i) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(net.minecraft.world.entity.player.Player p) {
                return true;
            }
        }, 3, 3);
        String[] rows = {"PTP", "TCT", "PTP"};
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                char k = rows[r].charAt(c);
                grid.setItem(r * 3 + c, new ItemStack(k == 'P' ? ModItems.TETRIUM_PLATE.get() : k == 'T' ? ModItems.TETRIUM_INGOT.get() : ModItems.COMPONENTS.get("basic_control_circuit").get()));
            }
        }
        var found = level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, grid, level);
        h.assertTrue(found.isPresent(), "plates, ingots and a circuit make something");
        ItemStack out = found.get().assemble(grid, level.registryAccess());
        h.assertTrue(out.is(ModItems.CRUCIBLE_YIELD_UPGRADE.get()) && out.getCount() == 1, "they make one Crucible Yield Upgrade, got " + out);
        h.assertTrue(new ItemStack(ModItems.CRUCIBLE_YIELD_UPGRADE.get()).getMaxStackSize() == 16, "it stacks to 16");
        h.succeed();
    }

    /** One job never makes more than a stack of either output, whatever the config says (a stack's count is saved in one byte, so 128 would read back as nothing). */
    @GameTest(template = EMPTY, batch = BATCH)
    public void one_ore_never_makes_more_than_a_stack(GameTestHelper h) {
        int nug = BSPConfig.TCRUC_NUGGETS.get(), slag = BSPConfig.TCRUC_SLAG.get();
        try {
            TetriumCrucibleBlockEntity be = crucible(h, new BlockPos(2, 1, 2));
            for (int i = 0; i < 3; i++) {
                be.getItems().insertItem(FIRST_YIELD + i, upgrade(), false);
            }
            BSPConfig.TCRUC_NUGGETS.set(60);
            BSPConfig.TCRUC_SLAG.set(40);
            h.assertTrue(be.nuggetsPerOre() == 64 && be.slagPerOre() == 64, "60 + 3 x 2 nuggets and 40 x 2 slag are each capped at 64, got "
                    + be.nuggetsPerOre() + " and " + be.slagPerOre());
        } finally {
            BSPConfig.TCRUC_NUGGETS.set(nug);
            BSPConfig.TCRUC_SLAG.set(slag);
        }
        h.succeed();
    }

    /** Upgrades fitted after a job has started do not count for that job, so one set cannot be carried to whichever crucible is about to finish. */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 2400)
    public void upgrades_fitted_mid_job_do_not_count_for_it(GameTestHelper h) {
        TetriumCrucibleBlockEntity be = crucible(h, new BlockPos(2, 1, 2));
        be.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new)
                .fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);
        be.getItems().insertItem(ORE, rawTetrium(1), false);
        int base = BSPConfig.TCRUC_NUGGETS.get(), slag = BSPConfig.TCRUC_SLAG.get();
        when(h, 900, () -> be.progressFraction() > 0, () -> {
            for (int i = 0; i < 3; i++) {
                be.getItems().insertItem(FIRST_YIELD + i, upgrade(), false);
            }
            h.assertTrue(be.yieldUpgrades() == 3 && be.countedUpgrades() == 0, "three fitted mid-job, none counted, got " + be.countedUpgrades());
            when(h, be.workTime() * 2 + 300, () -> !be.getItems().getStackInSlot(NUGGETS).isEmpty(), () -> {
                h.assertTrue(be.getItems().getStackInSlot(NUGGETS).getCount() == base && be.getItems().getStackInSlot(SLAG).getCount() == slag,
                        "the job started with none gives " + base + " nuggets and " + slag + " slag, got " + be.getItems().getStackInSlot(NUGGETS) + " " + be.getItems().getStackInSlot(SLAG));
                h.succeed();
            }, "the job never finished");
        }, "the job never started");
    }
}
