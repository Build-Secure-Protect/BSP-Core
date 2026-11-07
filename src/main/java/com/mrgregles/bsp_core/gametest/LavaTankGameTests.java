package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.machine.CombinationForgeBlockEntity;
import com.mrgregles.bsp_core.machine.LavaTankMachineBlockEntity;
import com.mrgregles.bsp_core.machine.TetriumCrucibleBlockEntity;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.BooleanSupplier;

/**
 * GameTests for the lava tanks of the Tetrium Crucible and the Combination Forge: pipes fill them from any side with lava only and
 * cannot drain them, they keep their lava through a save, a lava bucket used on the block pours in, a job runs on lava with no fuel
 * and pays the configured mB, lava comes before fuel, fuel takes over when the tank is short, a powered RF upgrade saves lava on the
 * crucible, and the forge on RF burns nothing. Jobs are waited for by polling, never after a fixed delay.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class LavaTankGameTests {
    private static final String EMPTY = "empty", BATCH = "lava_tank";
    private static final int CRUCIBLE_ORE = 0, CRUCIBLE_FUEL = 1, CRUCIBLE_NUGGETS = 2, FORGE_IN = 0, FORGE_OUT = 1, FORGE_FUEL = 3, RF = 4;

    private static TetriumCrucibleBlockEntity crucible(GameTestHelper h, BlockPos p) {
        h.setBlock(p, ModBlocks.TETRIUM_CRUCIBLE.get());
        return (TetriumCrucibleBlockEntity) h.getBlockEntity(p);
    }

    private static CombinationForgeBlockEntity forge(GameTestHelper h, BlockPos p) {
        h.setBlock(p, ModBlocks.COMBINATION_FORGE.get());
        return (CombinationForgeBlockEntity) h.getBlockEntity(p);
    }

    private static IFluidHandler pipe(LavaTankMachineBlockEntity be, Direction side) {
        return be.getCapability(ForgeCapabilities.FLUID_HANDLER, side).orElseThrow(IllegalStateException::new);
    }

    private static int pour(LavaTankMachineBlockEntity be, int mb) {
        return pipe(be, Direction.DOWN).fill(new FluidStack(Fluids.LAVA, mb), IFluidHandler.FluidAction.EXECUTE); // DOWN is an output face by default
    }

    private static ItemStack rawTetrium() {
        Item raw = ForgeRegistries.ITEMS.getValue(new ResourceLocation(BSPCore.MODID, "raw_tetrium"));
        return new ItemStack(raw);
    }

    private static void charge(LavaTankMachineBlockEntity be) {
        var energy = be.getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(IllegalStateException::new);
        for (int i = 0; i < 60 && energy.receiveEnergy(2_000, false) > 0; i++) {
            // the buffer takes 2,000 RF a call
        }
    }

    /** Runs {@code then} on the first tick {@code ok} holds, checking once a tick for up to {@code ticks}; fails with {@code why} after that. */
    private static void when(GameTestHelper h, int ticks, BooleanSupplier ok, Runnable then, String why) {
        if (ok.getAsBoolean()) {
            then.run();
        } else if (ticks <= 0) {
            h.fail(why);
        } else {
            h.runAfterDelay(1, () -> when(h, ticks - 1, ok, then, why));
        }
    }

    private static int jobWait(int workTicks) {
        return workTicks * 2 + 900; // room for the test area to start ticking
    }

    /** Pipes fill the tank from any face, output faces too, with lava only, up to the configured size; nothing drains it; it survives a save. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void lava_tank_takes_lava_from_any_side_and_cannot_be_drained(GameTestHelper h) {
        TetriumCrucibleBlockEntity be = crucible(h, new BlockPos(2, 1, 2));
        h.assertTrue(BSPConfig.TCRUC_LAVA_TANK.getDefault() == 8000 && BSPConfig.TCRUC_LAVA_PER_ORE.getDefault() == 100,
                "shipped defaults are an 8,000 mB tank and 100 mB per ore");
        int size = be.fluidCapacity();
        h.assertTrue(size == BSPConfig.TCRUC_LAVA_TANK.get(), "the tank is the configured size, got " + size);
        h.assertTrue(pipe(be, Direction.NORTH).fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) == 0, "water is refused");
        int in = 0;
        for (Direction d : Direction.values()) {
            in += pipe(be, d).fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);
        }
        h.assertTrue(in == Math.min(6000, size) && be.fluidAmount() == in, "every face takes lava, got " + in + " in, " + be.fluidAmount() + " held");
        h.assertTrue(pour(be, size) == size - in && be.fluidAmount() == size, "it fills to the brim and no further");
        h.assertTrue(pipe(be, Direction.DOWN).drain(1000, IFluidHandler.FluidAction.EXECUTE).isEmpty()
                && pipe(be, Direction.DOWN).drain(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE).isEmpty()
                && be.fluidAmount() == size, "pipes cannot drain it");
        CompoundTag saved = be.saveWithoutMetadata();
        h.setBlock(new BlockPos(4, 1, 2), ModBlocks.TETRIUM_CRUCIBLE.get());
        TetriumCrucibleBlockEntity copy = (TetriumCrucibleBlockEntity) h.getBlockEntity(new BlockPos(4, 1, 2));
        copy.load(saved);
        h.assertTrue(copy.fluidAmount() == size, "the lava survives a save, got " + copy.fluidAmount());
        h.succeed();
    }

    /** A lava bucket used on the block pours 1,000 mB in and leaves an empty bucket in a survival player's hand; a water bucket does nothing to the tank. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void lava_bucket_used_on_the_block_pours_into_the_tank(GameTestHelper h) {
        BlockPos rel = new BlockPos(2, 1, 2);
        TetriumCrucibleBlockEntity be = crucible(h, rel);
        BlockPos abs = h.absolutePos(rel);
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        p.setPos(abs.getX() + 0.5, abs.getY() + 1, abs.getZ() - 1.5);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        h.getBlockState(rel).use(h.getLevel(), p, InteractionHand.MAIN_HAND, hit);
        h.assertTrue(be.fluidAmount() == 0 && p.getItemInHand(InteractionHand.MAIN_HAND).is(Items.WATER_BUCKET), "a water bucket pours nothing");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LAVA_BUCKET));
        h.getBlockState(rel).use(h.getLevel(), p, InteractionHand.MAIN_HAND, hit);
        h.assertTrue(be.fluidAmount() == 1000, "the bucket pours 1,000 mB in, got " + be.fluidAmount());
        h.assertTrue(p.getItemInHand(InteractionHand.MAIN_HAND).is(Items.BUCKET), "an empty bucket is left, got " + p.getItemInHand(InteractionHand.MAIN_HAND));
        h.succeed();
    }

    /** With lava in the tank and no fuel at all, one ore is smelted and the tank pays the configured lava per ore. */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 2400)
    public void crucible_runs_on_lava_with_no_fuel(GameTestHelper h) {
        TetriumCrucibleBlockEntity be = crucible(h, new BlockPos(2, 1, 2));
        int cost = BSPConfig.TCRUC_LAVA_PER_ORE.get();
        pour(be, 1000);
        be.getItems().insertItem(CRUCIBLE_ORE, rawTetrium(), false);
        when(h, jobWait(be.workTime()), () -> !be.getItems().getStackInSlot(CRUCIBLE_NUGGETS).isEmpty(), () -> {
            h.assertTrue(be.fluidAmount() == 1000 - cost, "one ore took " + cost + " mB, the tank holds " + be.fluidAmount());
            h.assertTrue(be.getItems().getStackInSlot(CRUCIBLE_FUEL).isEmpty(), "no fuel was needed");
            h.succeed();
        }, "the crucible never smelted on lava alone");
    }

    /** Lava comes first: with lava and coal both in, the ore is paid from the tank and the coal stays in its slot. */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 2400)
    public void crucible_burns_lava_before_coal(GameTestHelper h) {
        TetriumCrucibleBlockEntity be = crucible(h, new BlockPos(2, 1, 2));
        pour(be, 1000);
        be.getItems().insertItem(CRUCIBLE_FUEL, new ItemStack(Items.COAL), false);
        be.getItems().insertItem(CRUCIBLE_ORE, rawTetrium(), false);
        when(h, jobWait(be.workTime()), () -> !be.getItems().getStackInSlot(CRUCIBLE_NUGGETS).isEmpty(), () -> {
            h.assertTrue(be.getItems().getStackInSlot(CRUCIBLE_FUEL).getCount() == 1, "the coal was not lit");
            h.assertTrue(be.fluidAmount() == 1000 - BSPConfig.TCRUC_LAVA_PER_ORE.get(), "the tank paid, it holds " + be.fluidAmount());
            h.succeed();
        }, "the crucible never smelted");
    }

    /** With less lava than one ore takes, the coal burns and the tank is left alone. */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 2400)
    public void crucible_falls_back_to_coal_when_the_tank_is_short(GameTestHelper h) {
        TetriumCrucibleBlockEntity be = crucible(h, new BlockPos(2, 1, 2));
        int little = Math.max(0, BSPConfig.TCRUC_LAVA_PER_ORE.get() - 1);
        pour(be, little);
        be.getItems().insertItem(CRUCIBLE_FUEL, new ItemStack(Items.COAL), false);
        be.getItems().insertItem(CRUCIBLE_ORE, rawTetrium(), false);
        when(h, jobWait(be.workTime()), () -> !be.getItems().getStackInSlot(CRUCIBLE_NUGGETS).isEmpty(), () -> {
            h.assertTrue(be.getItems().getStackInSlot(CRUCIBLE_FUEL).isEmpty(), "the coal was burned");
            h.assertTrue(be.fluidAmount() == little, "the short tank was not touched, it holds " + be.fluidAmount());
            h.succeed();
        }, "the crucible never smelted on coal");
    }

    /** A powered RF upgrade saves the same share of lava as it adds in speed: a real Mk III job takes 70 mB of 100 from the tank. */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 2400)
    public void rf_upgrade_saves_lava_on_the_crucible(GameTestHelper h) {
        TetriumCrucibleBlockEntity be = crucible(h, new BlockPos(2, 1, 2));
        int full = BSPConfig.TCRUC_LAVA_PER_ORE.get();
        h.assertTrue(be.lavaCost() == full, "unpowered, an ore takes " + full + " mB, got " + be.lavaCost());
        be.getItems().insertItem(RF, new ItemStack(ModItems.RF_UPGRADES.get(2).get()), false);
        charge(be);
        long expect = Math.round(full * (1.0 - be.rfBonus()));
        h.assertTrue(be.rfActive() && be.rfBonus() > 0 && be.lavaCost() == expect && expect < full, "powered Mk III takes " + expect + " mB, got " + be.lavaCost());
        pour(be, 1000);
        be.getItems().insertItem(CRUCIBLE_ORE, rawTetrium(), false);
        when(h, jobWait(be.workTime()), () -> !be.getItems().getStackInSlot(CRUCIBLE_NUGGETS).isEmpty(), () -> {
            h.assertTrue(be.fluidAmount() == 1000 - expect, "the powered job took " + expect + " mB, the tank holds " + be.fluidAmount());
            h.succeed();
        }, "the powered crucible never smelted");
    }

    /** The tank follows the configured size when filled (Forge's tank reads a stored size), and a tank over a lowered size keeps its lava but takes no more. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void lava_tank_follows_the_configured_size(GameTestHelper h) {
        int was = BSPConfig.TCRUC_LAVA_TANK.get();
        try {
            BSPConfig.TCRUC_LAVA_TANK.set(2000);
            TetriumCrucibleBlockEntity be = crucible(h, new BlockPos(2, 1, 2));
            h.assertTrue(pour(be, 5000) == 2000 && be.fluidAmount() == 2000 && be.fluidCapacity() == 2000, "a 2,000 mB tank takes 2,000, got " + be.fluidAmount());
            BSPConfig.TCRUC_LAVA_TANK.set(12000);
            h.assertTrue(pour(be, 20000) == 10000 && be.fluidAmount() == 12000, "raised to 12,000 it takes 10,000 more, holds " + be.fluidAmount());
            BSPConfig.TCRUC_LAVA_TANK.set(1000);
            h.assertTrue(pour(be, 500) == 0 && be.fluidAmount() == 12000, "lowered below its lava it keeps the lava and takes none, holds " + be.fluidAmount());
        } finally {
            BSPConfig.TCRUC_LAVA_TANK.set(was);
        }
        h.succeed();
    }

    /**
     * The multiblocks' shared tank (Illyrium Crucible, Refinery, Magnetic Centrifuge, Totem Forge) follows its configured size too:
     * above the 8,000 it was built with it fills to the new size, and a tank over a lowered size keeps its lava but takes no more.
     * The controller's own (null side) handler is used, so the structure need not be built.
     */
    @GameTest(template = EMPTY, batch = BATCH)
    public void multiblock_tank_follows_the_configured_size(GameTestHelper h) {
        int was = BSPConfig.ICRUC_TANK.get();
        try {
            BSPConfig.ICRUC_TANK.set(16000);
            BlockPos p = new BlockPos(2, 1, 2);
            h.setBlock(p, ModBlocks.ILLYRIUM_CRUCIBLE.get());
            var be = (com.mrgregles.bsp_core.machine.MachineBlockEntity) h.getBlockEntity(p);
            IFluidHandler tank = be.getCapability(ForgeCapabilities.FLUID_HANDLER, null).orElseThrow(IllegalStateException::new);
            int in = tank.fill(new FluidStack(Fluids.LAVA, 20000), IFluidHandler.FluidAction.EXECUTE);
            h.assertTrue(in == 16000 && be.fluidAmount() == 16000, "a 16,000 mB tank takes 16,000, took " + in + " and holds " + be.fluidAmount());
            BSPConfig.ICRUC_TANK.set(2000);
            h.assertTrue(tank.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE) == 0 && be.fluidAmount() == 16000,
                    "lowered below its lava it keeps the lava and takes none, holds " + be.fluidAmount());
        } finally {
            BSPConfig.ICRUC_TANK.set(was);
        }
        h.succeed();
    }

    /** The Combination Forge runs on its tank with no fuel: nine nuggets make an ingot and the tank pays the configured lava per job. */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 3000)
    public void forge_runs_on_lava_with_no_fuel(GameTestHelper h) {
        CombinationForgeBlockEntity be = forge(h, new BlockPos(2, 1, 2));
        h.assertTrue(BSPConfig.FORGE_LAVA_PER_JOB.getDefault() == 200 && BSPConfig.FORGE_LAVA_TANK.getDefault() == 8000, "shipped defaults are 200 mB a job and 8,000 mB");
        h.assertTrue(be.fluidCapacity() == BSPConfig.FORGE_LAVA_TANK.get(), "the forge's tank is its configured size");
        pour(be, 1000);
        be.getItems().insertItem(FORGE_IN, new ItemStack(ModItems.TETRIUM_NUGGET.get(), 9), false);
        when(h, jobWait(be.workTime()), () -> !be.getItems().getStackInSlot(FORGE_OUT).isEmpty(), () -> {
            h.assertTrue(be.getItems().getStackInSlot(FORGE_OUT).is(ModItems.TETRIUM_INGOT.get()), "an ingot came out");
            h.assertTrue(be.fluidAmount() == 1000 - BSPConfig.FORGE_LAVA_PER_JOB.get(), "one ingot took " + BSPConfig.FORGE_LAVA_PER_JOB.get() + " mB, the tank holds " + be.fluidAmount());
            h.assertTrue(be.getItems().getStackInSlot(FORGE_FUEL).isEmpty(), "no fuel was needed");
            h.succeed();
        }, "the forge never ran on lava alone");
    }

    /** On RF the forge burns nothing: the ingot comes out and the lava in its tank is untouched. */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 3000)
    public void forge_on_rf_leaves_its_lava_alone(GameTestHelper h) {
        CombinationForgeBlockEntity be = forge(h, new BlockPos(2, 1, 2));
        pour(be, 1000);
        be.getItems().insertItem(RF, new ItemStack(ModItems.RF_UPGRADES.get(0).get()), false);
        charge(be);
        be.getItems().insertItem(FORGE_IN, new ItemStack(ModItems.TETRIUM_NUGGET.get(), 9), false);
        when(h, jobWait(be.workTime()), () -> !be.getItems().getStackInSlot(FORGE_OUT).isEmpty(), () -> {
            h.assertTrue(be.fluidAmount() == 1000, "RF ran the job, the tank still holds " + be.fluidAmount());
            h.succeed();
        }, "the forge never ran on RF");
    }
}
