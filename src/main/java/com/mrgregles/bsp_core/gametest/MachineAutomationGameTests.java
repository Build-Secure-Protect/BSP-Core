package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.machine.CombinationForgeBlockEntity;
import com.mrgregles.bsp_core.machine.IllyriumCrucibleBlockEntity;
import com.mrgregles.bsp_core.machine.MachineBlock;
import com.mrgregles.bsp_core.machine.MachineBlockEntity;
import com.mrgregles.bsp_core.machine.MagneticCentrifugeBlockEntity;
import com.mrgregles.bsp_core.machine.TetriumCrucibleBlockEntity;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.BooleanSupplier;

/**
 * GameTests for automation on BSP machines. They act as a pipe or an AE2 bus would:
 * <ul>
 * <li>Every tick for many ticks they ask the block for its item handler, push an input in and pull the outputs out. The handler
 * must always be there, an input must always go in while its slot has room, and the outputs must come out.</li>
 * <li>A multiblock's Item Hatch and pylons keep the handler they gave out: it reaches the machine while the structure stands and
 * nothing while it is broken.</li>
 * <li>Handlers come back after Forge invalidates and revives a machine's capabilities.</li>
 * <li>An input the machine cannot use goes back out through an output face: slag while the Illyrium Crucible alloys, Illyrium
 * Nuggets in a Combination Forge with no upgrade, a Magnatite Ingot in a Magnetic Centrifuge with no coil.</li>
 * </ul>
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class MachineAutomationGameTests {
    private static final String EMPTY = "empty", BATCH = "automation";
    private static final int TICKS = 1000, FORM_TICKS = 60;

    private static void when(GameTestHelper h, int ticks, BooleanSupplier ok, Runnable then, String why) {
        if (ok.getAsBoolean()) {
            then.run();
        } else if (ticks <= 0) {
            h.fail(why);
        } else {
            h.runAfterDelay(1, () -> when(h, ticks - 1, ok, then, why));
        }
    }

    private static IItemHandler handler(GameTestHelper h, BlockPos absolute, Direction side) {
        BlockEntity be = h.getLevel().getBlockEntity(absolute);
        return be == null ? null : be.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
    }

    private static void fillLava(BlockEntity be, Direction side) {
        be.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElseThrow(IllegalStateException::new)
                .fill(new FluidStack(Fluids.LAVA, 8000), IFluidHandler.FluidAction.EXECUTE);
    }

    /**
     * For {@link #TICKS} ticks, ask the block at {@code inAt} for its handler on {@code inSide} and push {@code feed} into it, then ask
     * the block at {@code outAt} on {@code outSide} and pull every output. Fails on the first tick a handler is missing or an input is
     * refused while its slot has room for it, and at the end if nothing came out.
     */
    private static void pushAndPull(GameTestHelper h, String name, MachineBlockEntity m, BlockPos inAt, Direction inSide,
                                    BlockPos outAt, Direction outSide, ItemStack feed, int feedSlot) {
        // every tick is scheduled here, from the test method: 1.20.1's runner breaks when a delayed task schedules many more
        int[] pulled = {0};
        for (int t = 1; t <= TICKS; t++) {
            int tick = t;
            h.runAfterDelay(t, () -> {
                if (!m.isFormed()) {
                    h.assertTrue(tick < FORM_TICKS, name + ": never formed");
                    return; // a multiblock's first structure check
                }
                IItemHandler in = handler(h, inAt, inSide);
                h.assertTrue(in != null, name + ": no item handler on the input side at tick " + tick);
                ItemStack slot = m.getItems().getStackInSlot(feedSlot);
                boolean room = slot.isEmpty() || (ItemStack.isSameItemSameTags(slot, feed) && slot.getCount() + feed.getCount() <= slot.getMaxStackSize());
                ItemStack left = feed.copy();
                for (int s = 0; s < in.getSlots() && !left.isEmpty(); s++) {
                    left = in.insertItem(s, left, false);
                }
                h.assertTrue(!room || left.isEmpty(), name + ": " + feed + " refused at tick " + tick + " with room in the slot (" + slot + ")");
                IItemHandler out = handler(h, outAt, outSide);
                h.assertTrue(out != null, name + ": no item handler on the output side at tick " + tick);
                for (int s = 0; s < out.getSlots(); s++) {
                    if (m.layout().get(s).role() == MachineBlockEntity.Role.OUTPUT && !out.getStackInSlot(s).isEmpty()) {
                        ItemStack got = out.extractItem(s, 64, false);
                        h.assertTrue(!got.isEmpty(), name + ": output " + out.getStackInSlot(s) + " would not come out at tick " + tick);
                        pulled[0] += got.getCount();
                    }
                }
            });
        }
        h.runAfterDelay(TICKS + 1, () -> {
            h.assertTrue(pulled[0] > 0, name + ": nothing came out in " + TICKS + " ticks (progress " + m.progressFraction() + ", "
                    + m.missing(m.fluidAmount(), m.isBurning()).stream().map(n -> n.text().getString()).toList() + ")");
            h.succeed();
        });
    }

    private static ItemStack item(String id, int count) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(BSPCore.MODID, id)), count);
    }

    /** An Illyrium Crucible facing north at {@code at}, with every part placed. */
    private static IllyriumCrucibleBlockEntity buildCrucible(GameTestHelper h, BlockPos at) {
        h.setBlock(at, ModBlocks.ILLYRIUM_CRUCIBLE.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        IllyriumCrucibleBlockEntity c = (IllyriumCrucibleBlockEntity) h.getBlockEntity(at);
        for (var part : c.parts()) {
            h.getLevel().setBlockAndUpdate(part.pos(), part.block().defaultBlockState());
        }
        return c;
    }

    // ------------------------------------------------------------------ push and pull every tick

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = TICKS + 300)
    public void automation_tetrium_crucible_every_tick(GameTestHelper h) {
        BlockPos p = new BlockPos(2, 1, 2);
        h.setBlock(p, ModBlocks.TETRIUM_CRUCIBLE.get());
        TetriumCrucibleBlockEntity be = (TetriumCrucibleBlockEntity) h.getBlockEntity(p);
        fillLava(be, Direction.UP);
        pushAndPull(h, "Tetrium Crucible", be, h.absolutePos(p), Direction.NORTH, h.absolutePos(p), Direction.DOWN, item("raw_tetrium", 1), 0);
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = TICKS + 300)
    public void automation_combination_forge_every_tick(GameTestHelper h) {
        BlockPos p = new BlockPos(2, 1, 2);
        h.setBlock(p, ModBlocks.COMBINATION_FORGE.get());
        CombinationForgeBlockEntity be = (CombinationForgeBlockEntity) h.getBlockEntity(p);
        fillLava(be, Direction.UP);
        pushAndPull(h, "Combination Forge", be, h.absolutePos(p), Direction.EAST, h.absolutePos(p), Direction.DOWN, new ItemStack(ModItems.TETRIUM_NUGGET.get(), 3), 0);
    }

    /** Slag pushed into the right hatch and Dirty Illyrium Ingots pulled from the left hatch every tick, once the crucible has formed. */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = TICKS + 400)
    public void automation_illyrium_crucible_hatches_every_tick(GameTestHelper h) {
        IllyriumCrucibleBlockEntity c = buildCrucible(h, new BlockPos(3, 1, 3));
        fillLava(c, null);
        c.getItems().setStackInSlot(0, new ItemStack(ModBlocks.ILLYRIUM_ORE.get(), 64));
        BlockPos in = c.itemPorts().get(1), out = c.itemPorts().get(0); // right hatch takes inputs, left hatch gives outputs
        pushAndPull(h, "Illyrium Crucible", c, in, Direction.UP, out, Direction.UP, new ItemStack(ModItems.TETRIUM_SLAG.get(), 1), 1);
    }

    // ------------------------------------------------------------------ hatches follow the structure

    /**
     * A hatch of an unfinished crucible reaches nothing; once formed it reaches the machine; when a casing goes, the handler a pipe holds
     * stays (so no conduit drops its settings) but reaches nothing; formed again, that same handler works.
     */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 400)
    public void automation_hatch_follows_the_structure(GameTestHelper h) {
        BlockPos at = new BlockPos(3, 1, 3);
        h.setBlock(at, ModBlocks.ILLYRIUM_CRUCIBLE.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        IllyriumCrucibleBlockEntity c = (IllyriumCrucibleBlockEntity) h.getBlockEntity(at);
        BlockPos casing = c.parts().stream().filter(p -> p.block() == ModBlocks.ILLYRIUM_CASING.get()).findFirst().orElseThrow().pos();
        for (var part : c.parts()) {
            if (part.block() == ModBlocks.ITEM_HATCH.get()) {
                h.getLevel().setBlockAndUpdate(part.pos(), part.block().defaultBlockState());
            }
        }
        BlockPos in = c.itemPorts().get(1);
        ItemStack slag = new ItemStack(ModItems.TETRIUM_SLAG.get(), 4);
        h.runAfterDelay(25, () -> {
            LazyOptional<IItemHandler> held = h.getLevel().getBlockEntity(in).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP);
            h.assertTrue(held.isPresent(), "a hatch that has joined its controller hands pipes a handler at once");
            IItemHandler pipe = held.orElseThrow(IllegalStateException::new);
            h.assertTrue(pipe.getSlots() == 0 && pipe.insertItem(0, slag.copy(), false).getCount() == 4, "while the crucible is unfinished it reaches nothing");
            h.assertTrue(!h.getLevel().getBlockEntity(in).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).isPresent(), "an Item Hatch carries no fluid");
            for (var part : c.parts()) {
                h.getLevel().setBlockAndUpdate(part.pos(), part.block().defaultBlockState());
            }
            when(h, 60, c::isFormed, () -> {
                h.assertTrue(h.getLevel().getBlockEntity(in).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN) == held,
                        "the hatch hands every pipe the same handler");
                h.assertTrue(pipe.getSlots() == c.getItems().getSlots() && pipe.insertItem(1, slag.copy(), false).isEmpty()
                        && c.getItems().getStackInSlot(1).getCount() == 4, "formed, the handler a pipe already held reaches the machine");
                h.getLevel().setBlockAndUpdate(casing, Blocks.AIR.defaultBlockState());
                when(h, 60, () -> !c.isFormed(), () -> {
                    h.assertTrue(held.isPresent(), "broken, the handler stays, so conduits keep their settings");
                    h.assertTrue(pipe.getSlots() == 0 && pipe.insertItem(1, slag.copy(), false).getCount() == 4 && pipe.extractItem(1, 64, false).isEmpty(),
                            "but it reaches nothing: the broken machine's items are out of reach");
                    h.getLevel().setBlockAndUpdate(casing, ModBlocks.ILLYRIUM_CASING.get().defaultBlockState());
                    when(h, 60, c::isFormed, () -> {
                        h.assertTrue(pipe.insertItem(1, slag.copy(), false).isEmpty() && c.getItems().getStackInSlot(1).getCount() == 8,
                                "formed again, the same handler reaches the machine");
                        h.succeed();
                    }, "the crucible never formed again");
                }, "the crucible stayed formed without a casing");
            }, "the crucible never formed");
        });
    }

    /** The pylons carry lava and RF the same way: a handler that stays, reaching the tank and buffer only while the structure stands. */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 300)
    public void automation_pylons_follow_the_structure(GameTestHelper h) {
        IllyriumCrucibleBlockEntity c = buildCrucible(h, new BlockPos(3, 1, 3));
        BlockPos lower = c.parts().stream().filter(p -> p.block() == ModBlocks.LAVA_PYLON.get() && p.pos().getY() == c.getBlockPos().getY() + 1).findFirst().orElseThrow().pos();
        BlockPos casing = c.parts().stream().filter(p -> p.block() == ModBlocks.ILLYRIUM_CASING.get()).findFirst().orElseThrow().pos();
        when(h, 100, c::isFormed, () -> {
            LazyOptional<IFluidHandler> held = h.getLevel().getBlockEntity(lower).getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP);
            IFluidHandler pipe = held.orElseThrow(IllegalStateException::new);
            h.assertTrue(pipe.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE) == 1000 && c.fluidAmount() == 1000, "formed, a lower pylon fills the tank");
            h.getLevel().setBlockAndUpdate(casing, Blocks.AIR.defaultBlockState());
            when(h, 60, () -> !c.isFormed(), () -> {
                h.assertTrue(held.isPresent() && pipe.getTanks() == 0 && pipe.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE) == 0,
                        "broken, the pylon's handler stays but fills nothing");
                h.getLevel().setBlockAndUpdate(casing, ModBlocks.ILLYRIUM_CASING.get().defaultBlockState());
                when(h, 60, c::isFormed, () -> {
                    h.assertTrue(pipe.fill(new FluidStack(Fluids.LAVA, 500), IFluidHandler.FluidAction.EXECUTE) == 500 && c.fluidAmount() == 1500, "formed again, it fills the tank");
                    h.succeed();
                }, "the crucible never formed again");
            }, "the crucible stayed formed without a casing");
        }, "the crucible never formed");
    }

    // ------------------------------------------------------------------ revive

    /** Forge invalidates and revives a machine's capabilities: the old handler is dead, a fresh one works, through the face and through a hatch. */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public void automation_handlers_come_back_after_revive(GameTestHelper h) {
        BlockPos p = new BlockPos(1, 1, 1);
        h.setBlock(p, ModBlocks.TETRIUM_CRUCIBLE.get());
        TetriumCrucibleBlockEntity be = (TetriumCrucibleBlockEntity) h.getBlockEntity(p);
        LazyOptional<IItemHandler> held = be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH);
        LazyOptional<IFluidHandler> lava = be.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH);
        be.invalidateCaps();
        h.assertTrue(!held.isPresent() && !lava.isPresent(), "invalidated, the old handlers are dead");
        be.reviveCaps();
        IItemHandler fresh = be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).orElse(null);
        h.assertTrue(fresh != null && fresh.insertItem(0, item("raw_tetrium", 2), false).isEmpty(), "revived, a fresh item handler works");
        h.assertTrue(be.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).isPresent(), "revived, the lava tank is there again");

        IllyriumCrucibleBlockEntity c = buildCrucible(h, new BlockPos(5, 1, 4));
        BlockPos in = c.itemPorts().get(1);
        when(h, 100, c::isFormed, () -> {
            LazyOptional<IItemHandler> viaHatch = h.getLevel().getBlockEntity(in).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP);
            h.assertTrue(viaHatch.isPresent(), "formed, the hatch has a handler");
            IItemHandler pipe = viaHatch.orElseThrow(IllegalStateException::new);
            c.invalidateCaps();
            h.assertTrue(viaHatch.isPresent() && pipe.getSlots() == 0, "while the controller's handlers are invalidated, the hatch's handler stays but reaches nothing");
            c.reviveCaps();
            h.assertTrue(pipe.insertItem(1, new ItemStack(ModItems.TETRIUM_DUST.get(), 3), false).isEmpty() && c.getItems().getStackInSlot(1).getCount() == 3,
                    "revived, the same hatch handler reaches the machine again");
            h.succeed();
        }, "the crucible never formed");
    }

    // ------------------------------------------------------------------ inputs a machine cannot use go back out

    /** Slag left in the additive slot while the crucible alloys comes out of the output hatch; then Tetrium Dust goes in. While smelting, slag stays. */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public void automation_leftover_slag_goes_back(GameTestHelper h) {
        IllyriumCrucibleBlockEntity c = buildCrucible(h, new BlockPos(3, 1, 3));
        BlockPos in = c.itemPorts().get(1), out = c.itemPorts().get(0);
        when(h, 100, c::isFormed, () -> {
            c.getItems().setStackInSlot(0, new ItemStack(ModBlocks.ILLYRIUM_ORE.get(), 4));
            c.getItems().setStackInSlot(1, new ItemStack(ModItems.TETRIUM_SLAG.get(), 1));
            h.assertTrue(handler(h, out, Direction.UP).extractItem(1, 64, false).isEmpty(), "while smelting, the slag it needs stays in");
            h.assertTrue(handler(h, in, Direction.UP).insertItem(1, new ItemStack(ModItems.TETRIUM_DUST.get(), 1), false).getCount() == 1,
                    "Tetrium Dust cannot join slag in the slot");

            c.getItems().setStackInSlot(0, new ItemStack(ModItems.PURE_ILLYRIUM_DUST.get(), 4));
            h.assertTrue(handler(h, in, Direction.UP).extractItem(1, 64, true).isEmpty(), "the input-only hatch gives nothing back");
            ItemStack sim = handler(h, out, Direction.UP).extractItem(1, 64, true);
            ItemStack got = handler(h, out, Direction.UP).extractItem(1, 64, false);
            h.assertTrue(sim.getCount() == 1 && got.is(ModItems.TETRIUM_SLAG.get()) && got.getCount() == 1, "while alloying, leftover slag comes out of the output hatch, got " + got);
            h.assertTrue(handler(h, in, Direction.UP).insertItem(1, new ItemStack(ModItems.TETRIUM_DUST.get(), 5), false).isEmpty()
                    && c.getItems().getStackInSlot(1).is(ModItems.TETRIUM_DUST.get()), "then Tetrium Dust goes in");
            h.assertTrue(handler(h, out, Direction.UP).extractItem(1, 64, false).isEmpty(), "the dust the alloy job needs stays in");
            h.assertTrue(handler(h, out, Direction.UP).extractItem(0, 64, false).isEmpty(), "the main input never comes out");
            h.succeed();
        }, "the crucible never formed");
    }

    /** Illyrium Nuggets in a Combination Forge with no upgrade come back out; with the upgrade fitted they stay. Tetrium Nuggets always stay. */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 100)
    public void automation_forge_returns_nuggets_it_cannot_use(GameTestHelper h) {
        BlockPos p = new BlockPos(1, 1, 1);
        h.setBlock(p, ModBlocks.COMBINATION_FORGE.get());
        CombinationForgeBlockEntity be = (CombinationForgeBlockEntity) h.getBlockEntity(p);
        IItemHandler pipe = be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElseThrow(IllegalStateException::new);
        be.getItems().setStackInSlot(0, new ItemStack(ModItems.TETRIUM_NUGGET.get(), 5));
        h.assertTrue(pipe.extractItem(0, 64, false).isEmpty(), "Tetrium Nuggets waiting for more stay in");
        be.getItems().setStackInSlot(0, new ItemStack(ModItems.ILLYRIUM_NUGGET.get(), 7));
        ItemStack got = pipe.extractItem(0, 64, false);
        h.assertTrue(got.is(ModItems.ILLYRIUM_NUGGET.get()) && got.getCount() == 7, "with no upgrade, Illyrium Nuggets come back out, got " + got);
        be.getItems().setStackInSlot(0, new ItemStack(ModItems.ILLYRIUM_NUGGET.get(), 7));
        be.getItems().setStackInSlot(2, new ItemStack(ModItems.ILLYRIUM_FORGE_UPGRADE.get()));
        h.assertTrue(pipe.extractItem(0, 64, false).isEmpty(), "with the upgrade fitted, Illyrium Nuggets stay in");
        IItemHandler top = be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
        be.getItems().setStackInSlot(2, ItemStack.EMPTY);
        h.assertTrue(top.extractItem(0, 64, false).isEmpty(), "an input-only face gives nothing back");
        h.succeed();
    }

    // ------------------------------------------------------------------ one answer for every question a pipe asks

    /** Items a pipe might offer any machine: every machine's inputs and fuels, their containers, upgrades, and things no machine takes. */
    private static java.util.List<ItemStack> offers() {
        java.util.List<ItemStack> out = new java.util.ArrayList<>(java.util.List.of(
                item("raw_tetrium", 1), item("raw_illyrium", 1), item("raw_magnatite", 1), new ItemStack(ModBlocks.ILLYRIUM_ORE.get()),
                new ItemStack(ModItems.TETRIUM_NUGGET.get()), new ItemStack(ModItems.ILLYRIUM_NUGGET.get()), new ItemStack(ModItems.MAGNATITE_NUGGET.get()),
                new ItemStack(ModItems.TETRIUM_INGOT.get()), new ItemStack(ModItems.MAGNATITE_INGOT.get()), new ItemStack(ModItems.TETRIUM_DUST.get()),
                new ItemStack(ModItems.TETRIUM_SLAG.get()), new ItemStack(ModItems.PURE_ILLYRIUM_DUST.get()), new ItemStack(ModItems.DIRTY_ILLYRIUM_DUST.get()),
                new ItemStack(ModItems.RESONANCE_CRYSTAL.get()), new ItemStack(ModItems.COPPER_TETRIUM_COIL.get()),
                new ItemStack(ModItems.CRUCIBLE_YIELD_UPGRADE.get()), new ItemStack(ModItems.ILLYRIUM_FORGE_UPGRADE.get()), new ItemStack(ModItems.RF_UPGRADES.get(0).get()),
                new ItemStack(net.minecraft.world.item.Items.COAL), new ItemStack(net.minecraft.world.item.Items.LAVA_BUCKET), new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET),
                new ItemStack(net.minecraft.world.item.Items.BUCKET), new ItemStack(net.minecraft.world.item.Items.MAGMA_BLOCK),
                new ItemStack(net.minecraft.world.item.Items.IRON_INGOT), new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT), new ItemStack(net.minecraft.world.item.Items.COPPER_INGOT),
                new ItemStack(net.minecraft.world.item.Items.COBBLESTONE)));
        ModItems.FILTERS.values().forEach(f -> out.add(new ItemStack(f.get())));
        out.removeIf(ItemStack::isEmpty);
        return out;
    }

    /**
     * On an empty machine, through {@code side}: for every slot and every offer, "is it valid here" says yes exactly when an insert takes
     * it, and taking it in leaves a stack that a simulated and a real take-out answer alike.
     */
    private static void sameAnswers(GameTestHelper h, String name, MachineBlockEntity m, @javax.annotation.Nullable Direction side) {
        IItemHandler pipe = m.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null);
        h.assertTrue(pipe != null, name + " " + side + ": no handler");
        for (int slot = 0; slot < pipe.getSlots(); slot++) {
            for (ItemStack offer : offers()) {
                boolean valid = pipe.isItemValid(slot, offer);
                boolean taken = pipe.insertItem(slot, offer.copy(), true).getCount() < offer.getCount();
                h.assertTrue(valid == taken, name + " " + side + " slot " + slot + " " + offer + ": isItemValid " + valid + " but a simulated insert " + (taken ? "takes it" : "refuses it"));
                ItemStack before = m.getItems().getStackInSlot(slot).copy();
                m.getItems().setStackInSlot(slot, offer.copy());
                ItemStack sim = pipe.extractItem(slot, 64, true);
                MachineBlockEntity.Role role = m.layout().get(slot).role();
                boolean faceTakesIn = side == null || m.getSide(side).allowsInput();
                boolean fillable = faceTakesIn && (role == MachineBlockEntity.Role.INPUT || role == MachineBlockEntity.Role.FUEL);
                boolean shown = !pipe.getStackInSlot(slot).isEmpty();
                h.assertTrue(shown == (fillable || !sim.isEmpty()), name + " " + side + " slot " + slot + " " + offer + ": shown " + shown
                        + ", but the face " + (fillable ? "fills it" : "cannot fill it") + " and " + (sim.isEmpty() ? "cannot empty it" : "empties it"));
                ItemStack real = pipe.extractItem(slot, 64, false);
                h.assertTrue(ItemStack.matches(sim, real), name + " " + side + " slot " + slot + " " + offer + ": a simulated take-out gives " + sim + ", the real one " + real);
                m.getItems().setStackInSlot(slot, before);
            }
        }
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 100)
    public void automation_every_machine_answers_pipes_alike(GameTestHelper h) {
        java.util.List<net.minecraft.world.level.block.Block> singles = java.util.List.of(ModBlocks.TETRIUM_CRUCIBLE.get(), ModBlocks.COMBINATION_FORGE.get());
        java.util.List<net.minecraft.world.level.block.Block> multis = java.util.List.of(ModBlocks.ILLYRIUM_CRUCIBLE.get(), ModBlocks.ILLYRIUM_REFINERY.get(),
                ModBlocks.MAGNETIC_CENTRIFUGE.get(), ModBlocks.TOTEM_FORGE.get());
        int x = 1;
        for (var block : singles) {
            BlockPos p = new BlockPos(x, 1, 1);
            h.setBlock(p, block.defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
            MachineBlockEntity m = (MachineBlockEntity) h.getBlockEntity(p);
            for (Direction d : Direction.values()) {
                sameAnswers(h, block.getName().getString(), m, d);
            }
            sameAnswers(h, block.getName().getString(), m, null);
            x += 2;
        }
        for (var block : multis) {
            BlockPos p = new BlockPos(x, 1, 1);
            h.setBlock(p, block.defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
            MachineBlockEntity m = (MachineBlockEntity) h.getBlockEntity(p);
            for (Direction d : Direction.values()) {
                h.assertTrue(!m.getCapability(ForgeCapabilities.ITEM_HANDLER, d).isPresent(), block.getName().getString() + ": the controller's " + d + " face connects to nothing");
            }
            sameAnswers(h, block.getName().getString(), m, null); // what its hatches hand on, in the machine's own mode
            x += 2;
        }
        h.succeed();
    }

    /** Through a face that only takes items in, nothing is valid for an output slot or an upgrade; through an output-only face nothing is valid at all. */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 100)
    public void automation_valid_follows_the_face(GameTestHelper h) {
        BlockPos p = new BlockPos(1, 1, 1);
        h.setBlock(p, ModBlocks.TETRIUM_CRUCIBLE.get());
        MachineBlockEntity be = (MachineBlockEntity) h.getBlockEntity(p);
        IItemHandler top = be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
        IItemHandler bottom = be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElseThrow(IllegalStateException::new);
        h.assertTrue(top.isItemValid(0, item("raw_tetrium", 1)), "the input face takes ore in the ore slot");
        h.assertTrue(!bottom.isItemValid(0, item("raw_tetrium", 1)), "the output-only face says ore is not valid there");
        h.assertTrue(!top.isItemValid(5, new ItemStack(ModItems.CRUCIBLE_YIELD_UPGRADE.get())), "pipes are told upgrades do not go in");
        h.assertTrue(!top.isItemValid(4, new ItemStack(ModItems.RF_UPGRADES.get(0).get())), "nor RF upgrades");

        be.getItems().setStackInSlot(0, item("raw_tetrium", 5));
        be.getItems().setStackInSlot(5, new ItemStack(ModItems.CRUCIBLE_YIELD_UPGRADE.get()));
        h.assertTrue(top.getStackInSlot(0).getCount() == 5, "the input face shows the ore it tops up");
        h.assertTrue(bottom.getStackInSlot(0).isEmpty(), "the output-only face shows the ore slot empty: nothing there for it to take");
        h.assertTrue(top.getStackInSlot(5).isEmpty() && bottom.getStackInSlot(5).isEmpty(), "no face shows the upgrades");
        be.getItems().setStackInSlot(2, new ItemStack(ModItems.TETRIUM_NUGGET.get(), 4));
        h.assertTrue(bottom.getStackInSlot(2).getCount() == 4 && top.getStackInSlot(2).isEmpty(), "the nuggets show on the output face only");

        ItemStack held = item("raw_tetrium", 10);
        h.assertTrue(top.insertItem(0, held, false).isEmpty(), "ore goes in");
        held.setCount(1);
        h.assertTrue(be.getItems().getStackInSlot(0).getCount() == 15, "the machine keeps its own copy: changing the stack a pipe handed over changes nothing");
        h.succeed();
    }

    /** The Totem Forge's input slot tells pipes the most it holds of the metal in it: only the ingots its tank still needs. */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 100)
    public void automation_slot_limit_is_the_real_cap(GameTestHelper h) {
        BlockPos p = new BlockPos(2, 1, 2);
        h.setBlock(p, ModBlocks.TOTEM_FORGE.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        com.mrgregles.bsp_core.machine.TotemForgeBlockEntity forge = (com.mrgregles.bsp_core.machine.TotemForgeBlockEntity) h.getBlockEntity(p);
        IItemHandler own = forge.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElseThrow(IllegalStateException::new);
        ItemStack ingot = offers().stream().filter(o -> com.mrgregles.bsp_core.machine.TotemForgeBlockEntity.metalFor(o) != null).findFirst().orElse(ItemStack.EMPTY);
        h.assertTrue(!ingot.isEmpty(), "one of the offered ingots is on the forge's bill");
        var metal = com.mrgregles.bsp_core.machine.TotemForgeBlockEntity.metalFor(ingot);
        h.assertTrue(own.getSlotLimit(0) == 64, "an empty input slot says 64");
        forge.getItems().setStackInSlot(0, ingot.copy());
        int cap = Math.min(ingot.getMaxStackSize(), forge.ingotsNeeded(metal));
        h.assertTrue(own.getSlotLimit(0) == cap, "with " + ingot + " in it, the slot says " + cap + " (the ingots still needed), got " + own.getSlotLimit(0));
        h.succeed();
    }

    /** Magma blocks and lava buckets in the Illyrium Crucible's lava slot, and water buckets in the Refinery's, stay in; the empty bucket they leave comes out. */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 100)
    public void automation_fluid_containers_stay_until_used(GameTestHelper h) {
        h.setBlock(new BlockPos(1, 1, 1), ModBlocks.ILLYRIUM_CRUCIBLE.get());
        h.setBlock(new BlockPos(4, 1, 1), ModBlocks.ILLYRIUM_REFINERY.get());
        MachineBlockEntity crucible = (MachineBlockEntity) h.getBlockEntity(new BlockPos(1, 1, 1));
        MachineBlockEntity refinery = (MachineBlockEntity) h.getBlockEntity(new BlockPos(4, 1, 1));
        IItemHandler c = crucible.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElseThrow(IllegalStateException::new);
        IItemHandler r = refinery.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElseThrow(IllegalStateException::new);
        crucible.getItems().setStackInSlot(2, new ItemStack(net.minecraft.world.item.Items.MAGMA_BLOCK, 8));
        h.assertTrue(c.extractItem(2, 64, false).isEmpty(), "magma blocks waiting to melt stay in");
        crucible.getItems().setStackInSlot(2, new ItemStack(net.minecraft.world.item.Items.LAVA_BUCKET));
        h.assertTrue(c.extractItem(2, 64, false).isEmpty(), "a lava bucket waiting to pour stays in");
        crucible.getItems().setStackInSlot(2, new ItemStack(net.minecraft.world.item.Items.BUCKET));
        h.assertTrue(c.extractItem(2, 64, false).is(net.minecraft.world.item.Items.BUCKET), "the empty bucket comes out");
        refinery.getItems().setStackInSlot(1, new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET));
        h.assertTrue(r.extractItem(1, 64, false).isEmpty(), "a water bucket waiting to pour stays in");
        refinery.getItems().setStackInSlot(1, new ItemStack(net.minecraft.world.item.Items.BUCKET));
        h.assertTrue(r.extractItem(1, 64, false).is(net.minecraft.world.item.Items.BUCKET), "the empty bucket comes out of the Refinery");
        h.succeed();
    }

    /** A Magnatite Ingot in a Magnetic Centrifuge with no coil comes back out; with the coil it stays. Ore always stays. */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 100)
    public void automation_centrifuge_returns_ingots_it_cannot_use(GameTestHelper h) {
        BlockPos p = new BlockPos(2, 1, 2);
        h.setBlock(p, ModBlocks.MAGNETIC_CENTRIFUGE.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        MagneticCentrifugeBlockEntity be = (MagneticCentrifugeBlockEntity) h.getBlockEntity(p);
        IItemHandler own = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElseThrow(IllegalStateException::new);
        be.getItems().setStackInSlot(0, new ItemStack(ModItems.MAGNATITE_INGOT.get(), 3));
        ItemStack got = own.extractItem(0, 64, false);
        h.assertTrue(got.is(ModItems.MAGNATITE_INGOT.get()) && got.getCount() == 3, "with no coil, the ingots come back out, got " + got);
        be.getItems().setStackInSlot(0, new ItemStack(ModItems.MAGNATITE_INGOT.get(), 3));
        be.getItems().setStackInSlot(1, new ItemStack(ModItems.COPPER_TETRIUM_COIL.get()));
        h.assertTrue(own.extractItem(0, 64, false).isEmpty(), "with the coil fitted, the ingots stay in");
        be.getItems().setStackInSlot(0, item("raw_magnatite", 3));
        h.assertTrue(be.getItems().getStackInSlot(0).getCount() == 3 && own.extractItem(0, 64, false).isEmpty(), "ore stays in");
        h.succeed();
    }
}
