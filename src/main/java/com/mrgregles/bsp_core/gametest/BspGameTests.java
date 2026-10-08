package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.plasma.PlasmaExtractorBlockEntity;
import com.mrgregles.bsp_core.plasma.PlasmaNetwork;
import com.mrgregles.bsp_core.plasma.ProjectorBaseBlockEntity;
import com.mrgregles.bsp_core.projector.TotemCableBlock;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.tank.TankBlock;
import com.mrgregles.bsp_core.tank.TankPartBlockEntity;
import com.mrgregles.bsp_core.tank.TankPortBlockEntity;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import com.mrgregles.bsp_core.totem.TotemOwner;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Forge GameTests: checks that run on a headless server with {@code ./gradlew runGameTestServer}, so the rules can be tested
 * without the client. Every test builds what it needs inside the empty template {@code bsp_core:empty} (24 x 10 x 24 of air,
 * from tools/gen_gametest_structures.py). Positions are relative to the template's lowest corner.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class BspGameTests {
    private static final String EMPTY = "empty";
    private static final BlockPos TANK = new BlockPos(2, 1, 2);

    // ------------------------------------------------------------------ helpers

    private static Block cable(TotemCableBlock.Kind kind) {
        return ModBlocks.TOTEM_CABLES.get(kind).get();
    }

    private static <T extends BlockEntity> T entity(GameTestHelper h, BlockPos p, Class<T> type) {
        BlockEntity be = h.getBlockEntity(p);
        if (!type.isInstance(be)) {
            h.fail("expected " + type.getSimpleName() + " at " + p + ", found " + (be == null ? "nothing" : be.getClass().getSimpleName()));
        }
        return type.cast(be);
    }

    /** Every position of a w x h x d shell with its lowest corner at {@code o}. */
    private static List<BlockPos> shell(BlockPos o, int w, int hh, int d) {
        List<BlockPos> out = new ArrayList<>();
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < hh; y++) {
                for (int z = 0; z < d; z++) {
                    if (x == 0 || y == 0 || z == 0 || x == w - 1 || y == hh - 1 || z == d - 1) {
                        out.add(o.offset(x, y, z));
                    }
                }
            }
        }
        return out;
    }

    /** Builds a hollow tank: casing on the edges, glass on the faces, ports where listed, and one wrong block if {@code glassOnEdge} is set. */
    private static void buildTank(GameTestHelper h, BlockPos o, int w, int hh, int d, Set<BlockPos> ports, BlockPos glassOnEdge) {
        for (BlockPos p : shell(o, w, hh, d)) {
            int x = p.getX() - o.getX(), y = p.getY() - o.getY(), z = p.getZ() - o.getZ();
            int edges = (x == 0 || x == w - 1 ? 1 : 0) + (y == 0 || y == hh - 1 ? 1 : 0) + (z == 0 || z == d - 1 ? 1 : 0);
            Block b = ports.contains(p) ? ModBlocks.TANK_PORT.get() : edges >= 2 && !p.equals(glassOnEdge) ? ModBlocks.TANK_CASING.get() : ModBlocks.TANK_GLASS.get();
            h.setBlock(p, b);
        }
    }

    /** A totem with an owner on top of an extractor at {@code extractor}, at the given Output level. */
    private static ShatterTotemBlockEntity totemOnExtractor(GameTestHelper h, BlockPos extractor, int outputLevel) {
        h.setBlock(extractor, ModBlocks.PLASMA_EXTRACTOR.get());
        h.setBlock(extractor.above(), ModBlocks.SHATTER_TOTEM.get());
        ShatterTotemBlockEntity totem = entity(h, extractor.above(), ShatterTotemBlockEntity.class);
        totem.setOwner(new TotemOwner(UUID.nameUUIDFromBytes("gametest".getBytes()), "Tester"));
        totem.setUpgradeLevel(TotemUpgrades.Buff.OUTPUT, outputLevel);
        return totem;
    }

    /** Extractor and totem at {@code start}, an interface east of it, then {@code cables} cables of {@code kind} east, and a base after them. Returns the base's position. */
    private static BlockPos lineToBase(GameTestHelper h, BlockPos start, TotemCableBlock.Kind kind, int cables, int outputLevel) {
        totemOnExtractor(h, start, outputLevel);
        h.setBlock(start.east(), ModBlocks.PLASMA_INTERFACE.get());
        BlockPos p = start.east(2);
        for (int i = 0; i < cables; i++) {
            h.setBlock(p, cable(kind));
            p = p.east();
        }
        h.setBlock(p, ModBlocks.PROJECTOR_BASE.get());
        return p;
    }

    // ------------------------------------------------------------------ the tank

    @GameTest(template = EMPTY)
    public void tank_forms_when_complete(GameTestHelper h) {
        buildTank(h, TANK, 3, 3, 3, Set.of(TANK.offset(1, 2, 1)), null);
        h.succeedWhen(() -> {
            for (BlockPos p : shell(TANK, 3, 3, 3)) {
                h.assertBlockProperty(p, TankBlock.FORMED, true);
            }
            TankPartBlockEntity master = entity(h, TANK, TankPartBlockEntity.class);
            h.assertTrue(master.isMaster(), "lowest corner is the master");
            h.assertTrue(master.w() == 3 && master.h() == 3 && master.d() == 3, "size 3 x 3 x 3");
            h.assertTrue(master.capacity() == 26L * TankPartBlockEntity.perBlock(), "capacity is 26 shell blocks");
            // formed look: an edge along x at the bottom front gets a rail along x on its low corner; the far top corner gets all three
            BlockPos edge = TANK.offset(1, 0, 0), corner = TANK.offset(2, 2, 2);
            h.assertBlockProperty(edge, TankBlock.ALONG_X, true);
            h.assertBlockProperty(edge, TankBlock.ALONG_Y, false);
            h.assertBlockProperty(edge, TankBlock.HI_Y, false);
            h.assertBlockProperty(corner, TankBlock.ALONG_Y, true);
            h.assertBlockProperty(corner, TankBlock.HI_X, true);
            h.assertBlockProperty(corner, TankBlock.HI_Z, true);
            TankPortBlockEntity port = entity(h, TANK.offset(1, 2, 1), TankPortBlockEntity.class);
            h.assertTrue(port.master() != null && port.master().equals(h.absolutePos(TANK)), "the port knows the master");
        });
    }

    @GameTest(template = EMPTY)
    public void tank_rejects_glass_on_an_edge(GameTestHelper h) {
        buildTank(h, TANK, 3, 3, 3, Set.of(), TANK.offset(1, 0, 0));
        h.runAfterDelay(5, () -> {
            for (BlockPos p : shell(TANK, 3, 3, 3)) {
                h.assertBlockProperty(p, TankBlock.FORMED, false);
            }
            h.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public void tank_keeps_plasma_while_a_block_is_missing(GameTestHelper h) {
        buildTank(h, TANK, 3, 3, 3, Set.of(), null);
        TankPartBlockEntity master = entity(h, TANK, TankPartBlockEntity.class);
        h.assertTrue(master.isMaster(), "formed");
        master.add(1_000_000);
        BlockPos face = TANK.offset(1, 1, 0);
        h.setBlock(face, Blocks.AIR);
        h.runAfterDelay(2, () -> {
            h.assertBlockProperty(TANK, TankBlock.FORMED, false);
            h.assertBlockProperty(TANK, TankBlock.ALONG_X, false);
            h.setBlock(face, ModBlocks.TANK_GLASS.get());
            h.runAfterDelay(2, () -> {
                h.assertBlockProperty(TANK, TankBlock.FORMED, true);
                TankPartBlockEntity again = entity(h, TANK, TankPartBlockEntity.class);
                h.assertTrue(again.stored() == 1_000_000, "plasma remembered: " + again.stored());
                h.succeed();
            });
        });
    }

    // ------------------------------------------------------------------ the network

    @GameTest(template = EMPTY)
    public void allocate_is_max_min_fair(GameTestHelper h) {
        BlockPos trunk = new BlockPos(0, 0, 0), a = new BlockPos(1, 0, 0), b = new BlockPos(2, 0, 0), c = new BlockPos(3, 0, 0);
        List<List<BlockPos>> paths = List.of(List.of(a, trunk), List.of(b, trunk), List.of(c, trunk));
        Map<BlockPos, Integer> caps = new HashMap<>();
        caps.put(trunk, 1000);
        caps.put(a, 250);
        double[] s = PlasmaNetwork.allocate(5000, paths, caps);
        h.assertTrue(Math.abs(s[0] - 250) < 1e-6, "the capped run gets its cap: " + s[0]);
        h.assertTrue(Math.abs(s[1] - 375) < 1e-6 && Math.abs(s[2] - 375) < 1e-6, "the others share the rest of the trunk: " + s[1] + ", " + s[2]);
        double[] t = PlasmaNetwork.allocate(600, paths, caps);
        h.assertTrue(Math.abs(t[0] - 200) < 1e-6 && Math.abs(t[1] - 200) < 1e-6, "with little supply every run gets an equal share: " + t[0] + ", " + t[1]);
        h.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public void plasma_reaches_a_base(GameTestHelper h) {
        BlockPos start = new BlockPos(1, 1, 1);
        BlockPos base = lineToBase(h, start, TotemCableBlock.Kind.TETRIUM, 3, 0);
        h.runAfterDelay(100, () -> {
            PlasmaExtractorBlockEntity extractor = entity(h, start, PlasmaExtractorBlockEntity.class);
            h.assertTrue(extractor.flow() == 100, "an Output 0 totem gives 100 mB/t, got " + extractor.flow());
            ProjectorBaseBlockEntity b = entity(h, base, ProjectorBaseBlockEntity.class);
            h.assertTrue(b.tank() > 0, "the base received plasma, tank " + b.tank());
            h.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public void tetrium_reaches_fifteen_cables(GameTestHelper h) {
        BlockPos base = lineToBase(h, new BlockPos(1, 1, 1), TotemCableBlock.Kind.TETRIUM, 15, 0);
        h.runAfterDelay(100, () -> {
            h.assertTrue(entity(h, base, ProjectorBaseBlockEntity.class).tank() > 0, "15 Tetrium cables are within reach");
            h.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public void tetrium_stops_at_sixteen_cables(GameTestHelper h) {
        BlockPos base = lineToBase(h, new BlockPos(1, 1, 1), TotemCableBlock.Kind.TETRIUM, 16, 0);
        h.runAfterDelay(100, () -> {
            h.assertTrue(entity(h, base, ProjectorBaseBlockEntity.class).tank() == 0, "16 Tetrium cables are out of reach");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ the totem stays in play

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public void dropped_totem_ignores_hoppers(GameTestHelper h) {
        // a hopper over a chest: a dropped stone goes through; a dropped totem lies on the hopper untouched
        BlockPos chest = new BlockPos(2, 1, 2), hopper = chest.above();
        h.setBlock(chest, Blocks.CHEST);
        h.setBlock(hopper, Blocks.HOPPER);
        h.spawnItem(net.minecraft.world.item.Items.STONE, 2.5f, 3.5f, 2.5f);
        net.minecraft.world.item.ItemStack totem = new net.minecraft.world.item.ItemStack(com.mrgregles.bsp_core.registry.ModItems.SHATTER_TOTEM.get());
        new TotemOwner(UUID.nameUUIDFromBytes("gametest".getBytes()), "Tester").applyTo(totem);
        BlockPos abs = h.absolutePos(hopper.above());
        h.getLevel().addFreshEntity(new com.mrgregles.bsp_core.totem.ShatterTotemItemEntity(h.getLevel(), abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5, totem));
        h.runAfterDelay(80, () -> {
            h.assertContainerContains(chest, net.minecraft.world.item.Items.STONE);
            if (h.getBlockEntity(chest) instanceof net.minecraft.world.Container c) {
                for (int i = 0; i < c.getContainerSize(); i++) {
                    h.assertTrue(!c.getItem(i).is(com.mrgregles.bsp_core.registry.ModItems.SHATTER_TOTEM.get()), "the hopper must not move a totem into the chest");
                }
            }
            h.assertEntityPresent(com.mrgregles.bsp_core.registry.ModEntities.SHATTER_TOTEM_ITEM.get());
            h.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public void totem_identity_reissues_a_lost_totem(GameTestHelper h) {
        var ledger = com.mrgregles.bsp_core.data.TotemLedger.get(h.getLevel().getServer());
        UUID id = UUID.randomUUID(), instance = UUID.randomUUID(), holder = UUID.nameUUIDFromBytes("holder".getBytes());
        long now = 1000;
        ledger.seen(id, instance, holder, com.mrgregles.bsp_core.data.TotemLedger.HELD, null, now);
        h.assertTrue(instance.equals(ledger.currentInstance(id)), "the first sighting sets the instance");
        ledger.seen(id, UUID.randomUUID(), holder, com.mrgregles.bsp_core.data.TotemLedger.HELD, null, now + 1);
        h.assertTrue(instance.equals(ledger.currentInstance(id)), "a stale copy's sighting is ignored");
        h.assertTrue(ledger.lostHeld(now + 30, 60).isEmpty(), "not lost yet");
        h.assertTrue(ledger.lostHeld(now + 100, 60).contains(id), "lost after a minute unseen in the hands");
        ledger.seen(id, instance, null, com.mrgregles.bsp_core.data.TotemLedger.PLACED, null, now + 50);
        h.assertTrue(ledger.lostHeld(now + 1000, 60).isEmpty(), "a placed totem is never lost");
        ledger.seen(id, instance, holder, com.mrgregles.bsp_core.data.TotemLedger.HELD, null, now + 60);
        var fresh = ledger.reissue(id, now + 200);
        h.assertTrue(!fresh.instance().equals(instance) && fresh.instance().equals(ledger.currentInstance(id)), "reissue makes a new current instance");
        h.assertTrue(holder.equals(fresh.holder()), "the holder is remembered");
        ledger.forget(id);
        h.succeed();
    }

    // ------------------------------------------------------------------ cloaking

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public void cloak_shows_generated_land(GameTestHelper h) {
        // a totem with Cloaking in the middle of the plot and a gold block built nearby (the generator never makes one): outsiders must
        // see natural land there, not the gold. (The plot sits at the bottom of the test world on a floor the framework lays, so the
        // land shown is bedrock and deepslate; the real ground cannot be compared.)
        BlockPos totem = new BlockPos(12, 1, 12), built = totem.offset(3, 2, 0);
        h.setBlock(totem, ModBlocks.SHATTER_TOTEM.get());
        ShatterTotemBlockEntity be = entity(h, totem, ShatterTotemBlockEntity.class);
        be.setOwner(new TotemOwner(UUID.nameUUIDFromBytes("gametest".getBytes()), "Tester"));
        be.setUpgradeLevel(TotemUpgrades.Buff.CLOAKING, 1);
        h.setBlock(built, Blocks.GOLD_BLOCK);
        h.succeedWhen(() -> {
            h.assertTrue(be.cloak() != null, "snapshot made");
            h.assertTrue(be.cloak().contains(h.absolutePos(built)), "the built block is inside the cube");
            net.minecraft.world.level.block.state.BlockState over = be.cloak().at(h.absolutePos(built));
            h.assertTrue(!over.is(Blocks.GOLD_BLOCK), "the built gold block is replaced by generated land, got " + over);
            h.assertTrue(!over.hasBlockEntity(), "what is shown is natural land without machines, got " + over);
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 240)
    public void extractor_moves_the_full_output(GameTestHelper h) {
        // Output 10 totem (6,000 mB/t), interface, one Illyrium cable into a port of a 3 x 3 x 3 tank: the face gives 1,000 mB/t, 20,000 mB a second
        BlockPos start = new BlockPos(1, 1, 2);
        totemOnExtractor(h, start, 10);
        h.setBlock(start.east(), ModBlocks.PLASMA_INTERFACE.get());
        h.setBlock(start.east(2), cable(TotemCableBlock.Kind.ILLYRIUM));
        BlockPos tank = new BlockPos(4, 0, 1), port = tank.offset(0, 1, 1);
        buildTank(h, tank, 3, 3, 3, Set.of(port), null);
        TankPartBlockEntity master = entity(h, tank, TankPartBlockEntity.class);
        h.assertTrue(master.isMaster(), "tank formed");
        h.runAfterDelay(100, () -> {
            long before = master.stored();
            h.assertTrue(before > 0, "plasma arrived, stored " + before);
            h.runAfterDelay(20, () -> {
                long gained = master.stored() - before;
                TankPortBlockEntity p = entity(h, port, TankPortBlockEntity.class);
                h.assertTrue(p.in() == 1000, "the port reads 1,000 mB/t, got " + p.in());
                h.assertTrue(gained == 20_000, "one second moved 20,000 mB, got " + gained);
                h.succeed();
            });
        });
    }
}
