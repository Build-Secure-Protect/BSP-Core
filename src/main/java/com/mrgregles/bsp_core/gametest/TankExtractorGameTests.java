package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.plasma.ProjectorBaseBlockEntity;
import com.mrgregles.bsp_core.projector.TotemCableBlock;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.tank.TankBlock;
import com.mrgregles.bsp_core.tank.TankExtractorBlock;
import com.mrgregles.bsp_core.tank.TankExtractorBlockEntity;
import com.mrgregles.bsp_core.tank.TankPartBlockEntity;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The Tank Extractor: against a Tank Port, it sends the one pool it is set to, with that pool's power (a mob pool at its highest level),
 * and nothing else; through an Accelerator the power reaches the far world.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class TankExtractorGameTests {
    private static final String EMPTY = "empty", BATCH = "tank_extractor";
    /** A 3-wide tank; its port in the middle of the west face; the extractor west of the port, its front facing west. */
    private static final BlockPos TANK = new BlockPos(10, 1, 10), PORT = TANK.offset(0, 1, 1), EXTRACTOR = PORT.west();
    private static final int GROWTH_POOL = 2 + TankExtractorBlockEntity.MOB_POOLS.indexOf(Buff.GROWTH);

    private static void build(GameTestHelper h) {
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    int edges = (x == 0 || x == 2 ? 1 : 0) + (y == 0 || y == 2 ? 1 : 0) + (z == 0 || z == 2 ? 1 : 0);
                    BlockPos p = TANK.offset(x, y, z);
                    if (edges > 0) {
                        Block b = p.equals(PORT) ? ModBlocks.TANK_PORT.get() : edges >= 2 ? ModBlocks.TANK_CASING.get() : ModBlocks.TANK_GLASS.get();
                        h.setBlock(p, b);
                    }
                }
            }
        }
        h.setBlock(EXTRACTOR, ModBlocks.TANK_EXTRACTOR.get().defaultBlockState().setValue(TankExtractorBlock.FACING, Direction.WEST));
    }

    private static TankPartBlockEntity master(GameTestHelper h) {
        h.assertTrue(h.getLevel().getBlockState(h.absolutePos(TANK)).getValue(TankBlock.FORMED), "the tank formed");
        TankPartBlockEntity m = h.getBlockEntity(TANK) instanceof TankPartBlockEntity t ? t.masterEntity() : null;
        h.assertTrue(m != null && m.isMaster(), "the tank has a master");
        return m;
    }

    private static TankExtractorBlockEntity extractor(GameTestHelper h) {
        return (TankExtractorBlockEntity) h.getBlockEntity(EXTRACTOR);
    }

    /** Plain 100,000; totem 100,000 with Ward II; Growth III 200,000 and Growth I 50,000. */
    private static void fill(TankPartBlockEntity m) {
        m.drain(m.stored());
        for (Buff b : Buff.values()) {
            m.drainMob(b, Long.MAX_VALUE);
        }
        m.add(100_000);
        int[] ward = new int[Buff.values().length];
        ward[Buff.WARD.ordinal()] = 2;
        m.add(100_000, ward);
        m.addMob(Buff.GROWTH, 3, 200_000);
        m.addMob(Buff.GROWTH, 1, 50_000);
    }

    /** Runs {@code then} on the first tick {@code ok} holds, for up to {@code ticks}; fails with {@code why} after that. */
    private static void pollThen(GameTestHelper h, int ticks, java.util.function.BooleanSupplier ok, Runnable then, java.util.function.Supplier<String> why) {
        if (ok.getAsBoolean()) {
            then.run();
        } else if (ticks <= 0) {
            h.fail(why.get());
        } else {
            h.runAfterDelay(1, () -> pollThen(h, ticks - 1, ok, then, why));
        }
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void each_pool_leaves_with_its_own_power(GameTestHelper h) {
        build(h);
        h.succeedWhen(() -> {
            TankPartBlockEntity m = master(h);
            fill(m);
            TankExtractorBlockEntity ex = extractor(h);
            h.assertTrue(ex.attachedTank() == m, "the extractor finds the tank through its port");
            ex.setPool(TankExtractorBlockEntity.PLAIN);
            h.assertTrue(ex.tank() == 100_000 && java.util.Arrays.stream(ex.offered()).allMatch(l -> l == 0), "plain: 100,000 mB, no power");
            ex.setPool(TankExtractorBlockEntity.TOTEM);
            h.assertTrue(ex.tank() == 100_000 && ex.offered()[Buff.WARD.ordinal()] == 2 && ex.offered()[Buff.GROWTH.ordinal()] == 0, "totem: Ward II");
            ex.setPool(GROWTH_POOL);
            ex.nameLevel();
            h.assertTrue(ex.tank() == 200_000, "Growth: its highest level only, 200,000 mB at III");
            int[] o = ex.offered();
            h.assertTrue(o[Buff.GROWTH.ordinal()] == 3 && o[Buff.WARD.ordinal()] == 0, "Growth III, nothing else");
            h.assertTrue(ex.drain(250_000) == 200_000 && m.pools().mbAt(Buff.GROWTH, 1) == 50_000, "it drains level III only");
            h.assertTrue(ex.drain(10) == 0 && ex.offered()[Buff.GROWTH.ordinal()] == 3, "for the rest of the second it names III, so level I plasma does not leave as III");
            ex.nameLevel();
            h.assertTrue(ex.offered()[Buff.GROWTH.ordinal()] == 1 && ex.tank() == 50_000, "the next second level I is next");
            java.util.UUID placer = java.util.UUID.randomUUID();
            ex.setOwner(placer);
            h.assertTrue(ex.access().wardSafe.contains(placer) && ex.access().editors.contains(placer), "the player who placed it is the team of what it sends");
            var port = (com.mrgregles.bsp_core.tank.TankPortBlockEntity) h.getBlockEntity(PORT);
            port.setMode(com.mrgregles.bsp_core.tank.TankPortBlockEntity.Mode.INPUT);
            h.assertTrue(ex.attachedTank() == null, "a port set to Input gives nothing out, to an extractor either");
            port.setMode(com.mrgregles.bsp_core.tank.TankPortBlockEntity.Mode.BOTH);
            h.assertTrue(m.pools().plain() == 100_000 && m.pools().totem() == 100_000, "the other pools untouched");
            fill(m);
            m.drain(m.stored());
            m.drainMob(Buff.GROWTH, Long.MAX_VALUE);
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void the_buttons_step_through_the_pools(GameTestHelper h) {
        build(h);
        h.succeedWhen(() -> {
            ServerLevel sl = h.getLevel();
            BlockPos at = h.absolutePos(EXTRACTOR);
            var state = sl.getBlockState(at);
            // the front faces west: standing west of it and looking east, your left is north
            Vec3 left = Vec3.atCenterOf(at).add(-0.5, 0, -0.3), right = Vec3.atCenterOf(at).add(-0.5, 0, 0.3);
            h.assertTrue(TankExtractorBlock.step(state, at, left, Direction.WEST) == -1, "the left button steps back");
            h.assertTrue(TankExtractorBlock.step(state, at, right, Direction.WEST) == 1, "the right button steps on");
            h.assertTrue(TankExtractorBlock.step(state, at, right, Direction.UP) == 1, "anywhere else steps on");
            TankExtractorBlockEntity ex = extractor(h);
            ex.setPool(0);
            ex.setPool(ex.pool() - 1);
            h.assertTrue(ex.pool() == TankExtractorBlockEntity.POOLS - 1 && TankExtractorBlockEntity.POOLS == 15, "back from plain goes round to the last mob power: 15 pools");
            h.assertTrue(sl.getBlockState(at).getValue(TankExtractorBlock.POOL) == ex.pool(), "the sight glass shows it (block state)");
            ex.setPool(ex.pool() + 1);
            h.assertTrue(ex.pool() == 0 && ex.saveWithoutMetadata().getByte("Pool") == 0, "and on to plain again, saved");
        });
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 300)
    public void a_projector_base_by_the_extractor_gets_growth_and_nothing_else(GameTestHelper h) {
        build(h);
        BlockPos base = EXTRACTOR.north();
        h.setBlock(base, ModBlocks.PROJECTOR_BASE.get());
        boolean[] set = {false};
        h.onEachTick(() -> {
            if (!set[0]) {
                if (!h.getLevel().getBlockState(h.absolutePos(TANK)).getValue(TankBlock.FORMED)) {
                    return;
                }
                fill(master(h));
                extractor(h).setPool(GROWTH_POOL);
                set[0] = true;
                return;
            }
            var pb = (ProjectorBaseBlockEntity) h.getBlockEntity(base);
            if (pb.mob(Buff.GROWTH, 3) > 0) {
                TankPartBlockEntity m = master(h);
                h.assertTrue(pb.offered()[Buff.WARD.ordinal()] == 0 && pb.offered()[Buff.GROWTH.ordinal()] == 0, "no Ward with it, and Growth only by amount");
                h.assertTrue(m.pools().mbAt(Buff.GROWTH, 3) < 200_000, "Growth III left the tank");
                h.assertTrue(m.pools().plain() == 100_000 && m.pools().totem() == 100_000 && m.pools().mbAt(Buff.GROWTH, 1) == 50_000, "nothing else did");
                m.drain(m.stored());
                m.drainMob(Buff.GROWTH, Long.MAX_VALUE);
                h.succeed();
            }
        });
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 1800)
    public void a_mob_power_crosses_to_the_nether_through_an_accelerator(GameTestHelper h) {
        build(h);
        // extractor -> cable -> intake (facing west, plasma in at its back), the ring of frames round the space above the intake
        BlockPos cable = EXTRACTOR.west(), intake = cable.west();
        h.setBlock(cable, ModBlocks.TOTEM_CABLES.get(TotemCableBlock.Kind.ILLYRIUM).get());
        h.setBlock(intake, ModBlocks.ACCELERATOR_INTAKE.get().defaultBlockState().setValue(com.mrgregles.bsp_core.link.AcceleratorIntakeBlock.FACING, Direction.WEST));
        for (int[] c : new int[][]{{-1, 0}, {1, 0}, {-1, 1}, {1, 1}, {-1, 2}, {0, 2}, {1, 2}}) {
            h.setBlock(intake.relative(Direction.SOUTH, c[0]).above(c[1]), ModBlocks.ACCELERATOR_FRAME.get());
        }
        var a = (com.mrgregles.bsp_core.link.AcceleratorBlockEntity) h.getBlockEntity(intake);
        a.energy().fill(com.mrgregles.bsp_core.link.LinkEnergy.capacity());
        ServerLevel nether = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        h.assertTrue(nether != null, "the server has a Nether");
        BlockPos far = new BlockPos(1200, 100, 1200);
        nether.setChunkForced(far.getX() >> 4, far.getZ() >> 4, true);
        nether.setBlock(far, Blocks.AIR.defaultBlockState(), 3);
        nether.setBlock(far.south(), Blocks.AIR.defaultBlockState(), 3);
        nether.setBlock(far, ModBlocks.DECELERATOR_CORE.get().defaultBlockState().setValue(com.mrgregles.bsp_core.link.DeceleratorCoreBlock.FACING, Direction.NORTH), 3);
        nether.setBlock(far.south(), ModBlocks.DECELERATOR_PORT.get().defaultBlockState(), 3);
        var core = (com.mrgregles.bsp_core.link.DeceleratorBlockEntity) nether.getBlockEntity(far);
        core.energy().fill(com.mrgregles.bsp_core.link.LinkEnergy.capacity());
        ItemStack card = new ItemStack(com.mrgregles.bsp_core.registry.ModItems.LINK_CARD.get());
        com.mrgregles.bsp_core.link.LinkCardItem.write(card, a.here());
        h.assertTrue(core.plug(card) == com.mrgregles.bsp_core.link.Links.Plug.LINKED, "the Nether core is linked");
        Runnable cleanUp = () -> {
            nether.setBlock(far, Blocks.AIR.defaultBlockState(), 3);
            nether.setBlock(far.south(), Blocks.AIR.defaultBlockState(), 3);
            nether.setChunkForced(far.getX() >> 4, far.getZ() >> 4, false);
        };
        boolean[] filled = {false};
        pollThen(h, 1700, () -> {
            if (!filled[0] && h.getLevel().getBlockState(h.absolutePos(TANK)).getValue(TankBlock.FORMED)) {
                TankPartBlockEntity m = master(h);
                m.drain(m.stored());
                m.addMob(Buff.GROWTH, 2, 2_000_000);
                extractor(h).setPool(GROWTH_POOL);
                filled[0] = true;
            }
            return core.store().mob(Buff.GROWTH, 2) > 0;
        }, () -> {
            h.assertTrue(core.store().mob(Buff.GROWTH, 2) > 0 && core.store().mob(Buff.GROWTH, 2) <= core.store().stored(), "Growth II arrived in the Nether, by amount: " + core.store().mob(Buff.GROWTH, 2));
            h.assertTrue(core.store().offered()[Buff.WARD.ordinal()] == 0, "and nothing else");
            cleanUp.run();
            master(h).drainMob(Buff.GROWTH, Long.MAX_VALUE);
            h.succeed();
        }, () -> {
            cleanUp.run();
            return "no Growth reached the Nether: ring " + a.isFormed() + ", intake " + a.inRate() + ", extractor out " + extractor(h).out() + ", core " + core.isFormed() + " " + core.store().stored();
        });
    }
}
