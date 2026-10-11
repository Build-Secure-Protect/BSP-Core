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

    // ------------------------------------------------------------------ the 1.0 buffs

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public void overclock_travels_with_the_plasma(GameTestHelper h) {
        // Output 0 totem with Overclock IV, interface, two cables into an injector standing on a Tetrium Crucible: the injector's factor is the plain curve scaled by 2
        BlockPos start = new BlockPos(1, 1, 1);
        ShatterTotemBlockEntity totem = totemOnExtractor(h, start, 0);
        totem.setUpgradeLevel(TotemUpgrades.Buff.OVERCLOCK, 4);
        // the injector points down at the crucible and takes its cable at the other end, so the run climbs and comes in from above
        h.setBlock(start.east(), ModBlocks.PLASMA_INTERFACE.get());
        h.setBlock(start.east(2), cable(TotemCableBlock.Kind.TETRIUM));
        h.setBlock(start.east(3), cable(TotemCableBlock.Kind.TETRIUM));
        h.setBlock(start.east(3).above(), cable(TotemCableBlock.Kind.TETRIUM));
        h.setBlock(start.east(4).above(), cable(TotemCableBlock.Kind.TETRIUM));
        BlockPos injector = start.east(4);
        h.setBlock(injector.below(), ModBlocks.TETRIUM_CRUCIBLE.get());
        h.setBlock(injector, ModBlocks.PLASMA_INJECTOR.get().defaultBlockState().setValue(com.mrgregles.bsp_core.plasma.PlasmaInjectorBlock.FACING, net.minecraft.core.Direction.DOWN));
        h.runAfterDelay(100, () -> {
            var inj = entity(h, injector, com.mrgregles.bsp_core.plasma.PlasmaInjectorBlockEntity.class);
            h.assertTrue(inj.rate() > 0, "plasma arrives at the injector, rate " + inj.rate());
            double plain = com.mrgregles.bsp_core.plasma.PlasmaBoost.factor(inj.rate());
            h.assertTrue(Math.abs(inj.factor() - com.mrgregles.bsp_core.plasma.PlasmaBoost.apply(plain, 2.0)) < 1e-9, "Overclock IV doubles the speed-up: " + inj.factor() + " from " + plain);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public void overclock_scales_the_plasma_speed_up(GameTestHelper h) {
        h.assertTrue(com.mrgregles.bsp_core.plasma.PlasmaBoost.apply(1.0, 2.0) == 1.0, "no plasma: Overclock gives nothing");
        h.assertTrue(com.mrgregles.bsp_core.plasma.PlasmaBoost.apply(2.0, 1.0) == 2.0, "no Overclock: the injector's factor as it is");
        h.assertTrue(Math.abs(com.mrgregles.bsp_core.plasma.PlasmaBoost.apply(2.0, 2.0) - 3.0) < 1e-9, "a 2x injector at Overclock IV gives 3x");
        h.assertTrue(Math.abs(com.mrgregles.bsp_core.plasma.PlasmaBoost.apply(3.0, 1.25) - 3.5) < 1e-9, "a 3x injector at Overclock I gives 3.5x");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void cheap_carried_buffs_cost_half_the_xp(GameTestHelper h) {
        // Damage and Mining Speed both start at tier I, so their unhalved XP is the same figure
        var damage = TotemUpgrades.price(TotemUpgrades.Buff.DAMAGE, 0);
        var mining = TotemUpgrades.price(TotemUpgrades.Buff.MINING_SPEED, 0);
        h.assertTrue(damage != null && mining != null, "both priced");
        h.assertTrue(mining.xp() == Math.max(1, (damage.xp() + 1) / 2), "Mining Speed costs half the XP of Damage: " + mining.xp() + " vs " + damage.xp());
        h.assertTrue(mining.coins() == damage.coins(), "coins unchanged");
        h.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 1200)
    public void harvest_regrows_a_mined_ore(GameTestHelper h) {
        BlockPos totem = new BlockPos(8, 1, 8), ore = totem.offset(3, 0, 2);
        h.setBlock(totem, ModBlocks.SHATTER_TOTEM.get());
        ShatterTotemBlockEntity be = entity(h, totem, ShatterTotemBlockEntity.class);
        be.setOwner(new TotemOwner(UUID.nameUUIDFromBytes("gametest".getBytes()), "Tester"));
        be.setUpgradeLevel(TotemUpgrades.Buff.HARVEST, 4); // 30 s between regrowths
        Block oreBlock = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation("bsp_core", "tetrium_ore"));
        h.setBlock(ore, oreBlock);
        be.harvestRecord(h.absolutePos(ore), oreBlock.defaultBlockState()); // what the break handler does when a player mines it
        h.setBlock(ore, Blocks.AIR);
        h.succeedWhen(() -> h.assertBlock(ore, b -> b == oreBlock, "the ore grew back"));
    }

    // ------------------------------------------------------------------ cloaking

    @GameTest(template = EMPTY, timeoutTicks = 1600)
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

    // ------------------------------------------------------------------ machine times (balancing for 1.0, 2026-10-09)

    /**
     * One Tetrium Ore takes {@code tetrium_crucible.ticksPerOre} ticks on coal: nothing has come out 50 ticks before that, two nuggets and
     * one slag have 30 ticks after. The shipped default must be 300. The timing itself is read from the loaded config, because the game
     * test server keeps a world copy in {@code run/world/serverconfig/bsp_core-server.toml} that does not follow a changed default.
     */
    @GameTest(template = EMPTY, timeoutTicks = 1200)
    public void tetrium_crucible_takes_300_ticks(GameTestHelper h) {
        h.assertTrue(com.mrgregles.bsp_core.BSPConfig.TCRUC_TICKS.getDefault() == 300, "the shipped default is 300 ticks per ore, got " + com.mrgregles.bsp_core.BSPConfig.TCRUC_TICKS.getDefault());
        int ticks = com.mrgregles.bsp_core.BSPConfig.TCRUC_TICKS.get();
        BlockPos p = new BlockPos(2, 1, 2);
        h.setBlock(p, ModBlocks.TETRIUM_CRUCIBLE.get());
        com.mrgregles.bsp_core.machine.TetriumCrucibleBlockEntity be = entity(h, p, com.mrgregles.bsp_core.machine.TetriumCrucibleBlockEntity.class);
        net.minecraft.world.item.Item raw = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(BSPCore.MODID, "raw_tetrium"));
        h.assertTrue(be.getItems().insertItem(0, new net.minecraft.world.item.ItemStack(raw), false).isEmpty(), "the ore slot takes Raw Tetrium");
        h.assertTrue(be.getItems().insertItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL), false).isEmpty(), "the fuel slot takes coal");
        h.runAfterDelay(ticks - 50, () -> h.assertTrue(be.getItems().getStackInSlot(2).isEmpty(), "no nuggets before " + ticks + " ticks"));
        h.runAfterDelay(ticks + 30, () -> {
            net.minecraft.world.item.ItemStack nuggets = be.getItems().getStackInSlot(2), slag = be.getItems().getStackInSlot(3);
            h.assertTrue(nuggets.is(com.mrgregles.bsp_core.registry.ModItems.TETRIUM_NUGGET.get()) && nuggets.getCount() == 2, "two Tetrium Nuggets after " + ticks + " ticks, got " + nuggets);
            h.assertTrue(slag.is(com.mrgregles.bsp_core.registry.ModItems.TETRIUM_SLAG.get()) && slag.getCount() == 1, "one Tetrium Slag after " + ticks + " ticks, got " + slag);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ Plasma Injector (2026-10-10)

    /** Extractor and totem at {@code start} (Output {@code outputLevel}), an interface east of it, then Illyrium cables east up to {@code end} (exclusive). The injectors face down, so the line runs one block above them and the last cable sits over the port. */
    private static void plasmaLineTo(GameTestHelper h, BlockPos start, int outputLevel, BlockPos end) {
        totemOnExtractor(h, start, outputLevel);
        h.setBlock(start.east(), ModBlocks.PLASMA_INTERFACE.get());
        for (BlockPos p = start.east(2); p.getX() < end.getX(); p = p.east()) {
            h.setBlock(p, cable(TotemCableBlock.Kind.ILLYRIUM)); // 1,000 mB/t, so a 500 mB/t totem arrives in full
        }
    }

    private static void injectorDown(GameTestHelper h, BlockPos p) {
        h.setBlock(p, ModBlocks.PLASMA_INJECTOR.get().defaultBlockState().setValue(com.mrgregles.bsp_core.plasma.PlasmaInjectorBlock.FACING, net.minecraft.core.Direction.DOWN));
    }

    /** A vanilla furnace under a fed injector smelts in a third of its 200 ticks (Output 3 = 500 mB/t = x3); a plain one beside it has not finished. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public void injector_speeds_a_furnace(GameTestHelper h) {
        BlockPos start = new BlockPos(2, 3, 2), inj = new BlockPos(7, 2, 2), furnace = inj.below(), control = new BlockPos(7, 1, 6);
        plasmaLineTo(h, start, 3, inj.above().east());
        injectorDown(h, inj);
        h.setBlock(furnace, Blocks.FURNACE);
        h.setBlock(control, Blocks.FURNACE);
        h.setBlock(inj.east(), cable(TotemCableBlock.Kind.ILLYRIUM)); // a cable on a side: the injector only takes cables through its port
        h.runAfterDelay(100, () -> {
            var be = entity(h, inj, com.mrgregles.bsp_core.plasma.PlasmaInjectorBlockEntity.class);
            h.assertTrue(be.rate() >= 450, "plasma arriving at the injector, got " + be.rate() + " mB/t");
            h.assertBlockProperty(inj.above(), TotemCableBlock.SIDES[net.minecraft.core.Direction.DOWN.ordinal()], true); // the cable over the port reaches into it
            h.assertBlockProperty(inj.east(), TotemCableBlock.SIDES[net.minecraft.core.Direction.WEST.ordinal()], false); // the side cable does not
            for (BlockPos f : List.of(furnace, control)) {
                var fe = entity(h, f, net.minecraft.world.level.block.entity.FurnaceBlockEntity.class);
                fe.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RAW_IRON));
                fe.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL));
            }
            h.runAfterDelay(120, () -> {
                h.assertTrue(!entity(h, furnace, net.minecraft.world.level.block.entity.FurnaceBlockEntity.class).getItem(2).isEmpty(), "the injected furnace smelted within 120 ticks");
                h.assertTrue(entity(h, control, net.minecraft.world.level.block.entity.FurnaceBlockEntity.class).getItem(2).isEmpty(), "the plain furnace needs 200 ticks");
                h.succeed();
            });
        });
    }

    /** A Tetrium Crucible under a fed injector finishes an ore in a third of its ticks (300 -> 100); checked at half the plain time. */
    @GameTest(template = EMPTY, timeoutTicks = 600)
    public void injector_speeds_the_tetrium_crucible(GameTestHelper h) {
        BlockPos start = new BlockPos(2, 3, 2), inj = new BlockPos(7, 2, 2), crucible = inj.below();
        plasmaLineTo(h, start, 3, inj.above().east());
        injectorDown(h, inj);
        h.setBlock(crucible, ModBlocks.TETRIUM_CRUCIBLE.get());
        int ticks = com.mrgregles.bsp_core.BSPConfig.TCRUC_TICKS.get();
        h.runAfterDelay(100, () -> {
            var be = entity(h, crucible, com.mrgregles.bsp_core.machine.TetriumCrucibleBlockEntity.class);
            net.minecraft.world.item.Item raw = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(BSPCore.MODID, "raw_tetrium"));
            be.getItems().insertItem(0, new net.minecraft.world.item.ItemStack(raw), false);
            be.getItems().insertItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL), false);
            h.runAfterDelay(ticks / 2, () -> {
                h.assertTrue(be.plasmaFactor() >= 2.9, "the crucible reads x3 from its injector, got x" + be.plasmaFactor());
                h.assertTrue(!be.getItems().getStackInSlot(2).isEmpty(), "nuggets out in half the plain time");
                h.succeed();
            });
        });
    }

    /** An injector in a factory slice's Motivator cell gives that slice the curve's factor. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public void injector_boosts_a_factory_slice(GameTestHelper h) {
        BlockPos c = new BlockPos(10, 1, 10); // controller facing north: the slice runs south (back) from it
        h.setBlock(c, ModBlocks.COIN_FACTORY.get().defaultBlockState().setValue(com.mrgregles.bsp_core.coin.CoinFactoryBlock.FACING, net.minecraft.core.Direction.NORTH));
        h.setBlock(c.south(), ModBlocks.FACTORY_FRAME.get());
        h.setBlock(c.south(2), ModBlocks.FACTORY_BLANK_HATCH.get());
        h.setBlock(c.above(), ModBlocks.FACTORY_FRAME.get());
        h.setBlock(c.above().south(), ModBlocks.FACTORY_PRESS.get());
        h.setBlock(c.above().south(2), ModBlocks.FACTORY_POWER_PORT.get());
        BlockPos inj = c.above(2).south(2); // the back Motivator cell
        plasmaLineTo(h, new BlockPos(2, 4, 12), 3, inj.above().east());
        injectorDown(h, inj);
        h.runAfterDelay(120, () -> {
            var slice = entity(h, c, com.mrgregles.bsp_core.coin.CoinFactoryBlockEntity.class);
            h.assertTrue(slice.isFormed(), "the slice is formed");
            h.assertTrue(slice.plasmaRate() >= 450, "the slice reads its injector, got " + slice.plasmaRate() + " mB/t");
            h.assertTrue(slice.plasmaFactor() >= 2.9, "x3 press speed, got x" + slice.plasmaFactor());
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ the bank

    private static net.minecraft.server.MinecraftServer server(GameTestHelper h) {
        return h.getLevel().getServer();
    }

    @GameTest(template = EMPTY)
    public void bank_gives_starting_pieces_once(GameTestHelper h) {
        UUID id = UUID.randomUUID();
        long starting = com.mrgregles.bsp_core.BSPConfig.BANK_STARTING_PIECES.get();
        boolean[] first = new boolean[2];
        com.mrgregles.bsp_core.bank.Bank.ensure(server(h), id, "Tester", g -> first[0] = g);
        com.mrgregles.bsp_core.bank.Bank.ensure(server(h), id, "Tester", g -> first[1] = g);
        h.assertTrue(first[0] && !first[1], "the starting pieces are given on the first join only");
        h.assertTrue(com.mrgregles.bsp_core.bank.Bank.pieces(server(h), id) == starting, "balance is the starting amount once: " + com.mrgregles.bsp_core.bank.Bank.pieces(server(h), id));
        h.assertTrue(com.mrgregles.bsp_core.bank.Bank.account(server(h), id).name.equals("Tester"), "the name is kept");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void bank_pay_moves_pieces_and_refuses_a_shortfall(GameTestHelper h) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var server = server(h);
        com.mrgregles.bsp_core.bank.Bank.ensure(server, a, "Alice", g -> {});
        com.mrgregles.bsp_core.bank.Bank.ensure(server, b, "Bob", g -> {});
        long start = com.mrgregles.bsp_core.bank.Bank.pieces(server, a);
        com.mrgregles.bsp_core.bank.BankResult[] r = new com.mrgregles.bsp_core.bank.BankResult[3];
        com.mrgregles.bsp_core.bank.Bank.transfer(server, a, b, 300, x -> r[0] = x);
        h.assertTrue(r[0].ok() && r[0].pieces() == start - 300, "paid 300: " + r[0]);
        h.assertTrue(com.mrgregles.bsp_core.bank.Bank.pieces(server, b) == start + 300, "Bob received 300");
        com.mrgregles.bsp_core.bank.Bank.transfer(server, a, b, start, x -> r[1] = x);
        h.assertTrue(!r[1].ok() && r[1].key().equals("short"), "cannot pay more than the balance: " + r[1]);
        h.assertTrue(com.mrgregles.bsp_core.bank.Bank.pieces(server, a) == start - 300, "a refused payment takes nothing");
        com.mrgregles.bsp_core.bank.Bank.transfer(server, a, a, 1, x -> r[2] = x);
        h.assertTrue(!r[2].ok() && r[2].key().equals("self"), "no paying yourself");
        var log = com.mrgregles.bsp_core.bank.Bank.account(server, a);
        com.mrgregles.bsp_core.bank.Bank.log(server, a, list -> {
            h.assertTrue(list.get(0).type() == com.mrgregles.bsp_core.bank.BankTransaction.Type.PAID && list.get(0).pieces() == -300 && list.get(0).with().equals("Bob"), "Alice's newest log line is the payment: " + list.get(0));
        });
        com.mrgregles.bsp_core.bank.Bank.log(server, b, list -> {
            h.assertTrue(list.get(0).type() == com.mrgregles.bsp_core.bank.BankTransaction.Type.RECEIVED && list.get(0).pieces() == 300 && list.get(0).with().equals("Alice"), "Bob's newest log line is the receipt: " + list.get(0));
        });
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void bank_refuses_payments_when_toggled_off(GameTestHelper h) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var server = server(h);
        com.mrgregles.bsp_core.bank.Bank.ensure(server, a, "Alice", g -> {});
        com.mrgregles.bsp_core.bank.Bank.ensure(server, b, "Bob", g -> {});
        com.mrgregles.bsp_core.bank.Bank.setAcceptPay(server, b, false);
        com.mrgregles.bsp_core.bank.BankResult[] r = new com.mrgregles.bsp_core.bank.BankResult[1];
        com.mrgregles.bsp_core.bank.Bank.transfer(server, a, b, 10, x -> r[0] = x);
        h.assertTrue(!r[0].ok() && r[0].key().equals("refused"), "Bob refuses: " + r[0]);
        com.mrgregles.bsp_core.bank.Bank.setAcceptPay(server, b, true);
        com.mrgregles.bsp_core.bank.Bank.transfer(server, a, b, 10, x -> r[0] = x);
        h.assertTrue(r[0].ok(), "Bob accepts again");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void bank_converts_coins_one_way_and_keeps_banked_coins_by_tier(GameTestHelper h) {
        var server = server(h);
        var player = h.makeMockPlayer();
        UUID id = player.getUUID();
        com.mrgregles.bsp_core.bank.Bank.ensure(server, id, "Carol", g -> {});
        long start = com.mrgregles.bsp_core.bank.Bank.pieces(server, id);
        var copper = com.mrgregles.bsp_core.coin.CoinTier.COPPER;
        var diamond = com.mrgregles.bsp_core.coin.CoinTier.DIAMOND;
        player.getInventory().add(new net.minecraft.world.item.ItemStack(copper.coin(), 5));
        player.getInventory().add(new net.minecraft.world.item.ItemStack(diamond.coin(), 2));
        com.mrgregles.bsp_core.bank.BankResult[] r = new com.mrgregles.bsp_core.bank.BankResult[1];
        // convert two Copper coins: pieces rise by the rate, the coins are gone from the inventory and are not in the bank
        com.mrgregles.bsp_core.bank.Bank.depositCoins(server, player, copper, 2, true, x -> r[0] = x);
        long perCopper = com.mrgregles.bsp_core.bank.Bank.piecesPerCoin(copper);
        h.assertTrue(r[0].ok() && r[0].pieces() == start + 2 * perCopper, "two Copper coins became " + (2 * perCopper) + " pieces: " + r[0]);
        h.assertTrue(player.getInventory().countItem(copper.coin()) == 3, "three Copper coins left in hand");
        h.assertTrue(com.mrgregles.bsp_core.bank.Bank.account(server, id).coins(copper) == 0, "converted coins are not banked");
        // keep two Diamond coins in the bank: pieces unchanged, the ledger holds them, the rate scales with the tier
        com.mrgregles.bsp_core.bank.Bank.depositCoins(server, player, diamond, 2, false, x -> r[0] = x);
        h.assertTrue(r[0].ok() && r[0].pieces() == start + 2 * perCopper, "banking coins changes no pieces");
        h.assertTrue(com.mrgregles.bsp_core.bank.Bank.account(server, id).coins(diamond) == 2 && player.getInventory().countItem(diamond.coin()) == 0, "the two Diamond coins are in the bank");
        h.assertTrue(com.mrgregles.bsp_core.bank.Bank.piecesPerCoin(diamond) == perCopper * diamond.value(), "a Diamond coin is worth its coin value in pieces");
        // more coins than held: refused, nothing taken
        com.mrgregles.bsp_core.bank.Bank.depositCoins(server, player, copper, 4, true, x -> r[0] = x);
        h.assertTrue(!r[0].ok() && r[0].key().equals("coins_short") && player.getInventory().countItem(copper.coin()) == 3, "cannot deposit coins you do not hold");
        // withdraw one Diamond coin back as an item; a second withdrawal of two is refused
        com.mrgregles.bsp_core.bank.Bank.withdrawCoins(server, player, diamond, 1, x -> r[0] = x);
        h.assertTrue(r[0].ok() && player.getInventory().countItem(diamond.coin()) == 1 && com.mrgregles.bsp_core.bank.Bank.account(server, id).coins(diamond) == 1, "one Diamond coin came back");
        com.mrgregles.bsp_core.bank.Bank.withdrawCoins(server, player, diamond, 2, x -> r[0] = x);
        h.assertTrue(!r[0].ok() && r[0].key().equals("short"), "cannot withdraw more than the bank holds");
        // the shop price of a coin is the conversion value plus the markup
        long markup = com.mrgregles.bsp_core.BSPConfig.BANK_COIN_MARKUP_PERCENT.get();
        h.assertTrue(com.mrgregles.bsp_core.bank.Bank.coinSalePrice(copper) == perCopper * (100 + markup) / 100, "coin sale price carries the markup");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void bank_api_charges_and_credits(GameTestHelper h) {
        var server = server(h);
        UUID id = UUID.randomUUID();
        com.mrgregles.bsp_core.bank.Bank.ensure(server, id, "Dave", g -> {});
        long start = com.mrgregles.bsp_core.api.BspBank.balance(server, id);
        boolean[] ok = new boolean[2];
        com.mrgregles.bsp_core.api.BspBank.charge(server, id, start + 1, "Region Guard", "a plot", x -> ok[0] = x);
        h.assertTrue(!ok[0] && com.mrgregles.bsp_core.api.BspBank.balance(server, id) == start, "a charge above the balance fails and takes nothing");
        com.mrgregles.bsp_core.api.BspBank.charge(server, id, 200, "Region Guard", "a plot", x -> ok[0] = x);
        com.mrgregles.bsp_core.api.BspBank.credit(server, id, 50, "Region Guard", "refund", x -> ok[1] = x);
        h.assertTrue(ok[0] && ok[1] && com.mrgregles.bsp_core.api.BspBank.balance(server, id) == start - 150, "charged 200 and credited 50");
        com.mrgregles.bsp_core.bank.Bank.log(server, id, list -> h.assertTrue(list.size() == 3 && list.get(0).type() == com.mrgregles.bsp_core.bank.BankTransaction.Type.SOLD
                && list.get(1).type() == com.mrgregles.bsp_core.bank.BankTransaction.Type.BOUGHT && list.get(2).type() == com.mrgregles.bsp_core.bank.BankTransaction.Type.GRANT, "the log holds grant, purchase, refund newest first"));
        h.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public void bank_atm_is_two_halves_that_stand_and_fall_together(GameTestHelper h) {
        BlockPos lower = new BlockPos(5, 1, 5), upper = lower.above();
        var block = com.mrgregles.bsp_core.registry.ModBlocks.BANK_ATM.get();
        var state = block.defaultBlockState().setValue(com.mrgregles.bsp_core.bank.BankAtmBlock.FACING, net.minecraft.core.Direction.EAST);
        h.setBlock(lower, state);
        h.setBlock(upper, state.setValue(com.mrgregles.bsp_core.bank.BankAtmBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        h.assertTrue(h.getBlockEntity(lower) instanceof com.mrgregles.bsp_core.bank.BankAtmBlockEntity, "the lower half has the block entity");
        h.assertTrue(h.getBlockEntity(upper) == null, "the upper half has none");
        h.assertTrue(state.getLightEmission(h.getLevel(), h.absolutePos(lower)) == 7, "it gives off light like a machine");
        h.setBlock(upper, Blocks.AIR);
        h.runAfterDelay(2, () -> {
            h.assertBlock(lower, b -> b == Blocks.AIR, () -> "taking the upper half away removes the lower half too");
            h.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public void bank_shopping_network_is_bought_with_coins_and_reset_by_admins(GameTestHelper h) {
        var server = server(h);
        var player = h.makeMockPlayer();
        UUID id = player.getUUID();
        com.mrgregles.bsp_core.bank.Bank.ensure(server, id, "Erin", g -> {});
        com.mrgregles.bsp_core.bank.BankResult[] r = new com.mrgregles.bsp_core.bank.BankResult[1];
        com.mrgregles.bsp_core.bank.Bank.unlockRemote(server, player, x -> r[0] = x);
        h.assertTrue(!r[0].ok() && r[0].key().equals("coins_unlock"), "no coins, no unlock: " + r[0]);
        int cost = com.mrgregles.bsp_core.BSPConfig.BANK_REMOTE_UNLOCK_VALUE.get();
        var copper = com.mrgregles.bsp_core.coin.CoinTier.COPPER;
        player.getInventory().add(new net.minecraft.world.item.ItemStack(copper.coin(), cost + 2));
        com.mrgregles.bsp_core.bank.Bank.unlockRemote(server, player, x -> r[0] = x);
        h.assertTrue(r[0].ok() && com.mrgregles.bsp_core.bank.Bank.account(server, id).remoteShops, "unlocked with coins");
        h.assertTrue(player.getInventory().countItem(copper.coin()) == 2, "the coins were taken: " + player.getInventory().countItem(copper.coin()));
        com.mrgregles.bsp_core.bank.Bank.unlockRemote(server, player, x -> r[0] = x);
        h.assertTrue(!r[0].ok() && r[0].key().equals("already"), "cannot buy it twice");
        int n = com.mrgregles.bsp_core.bank.Bank.resetRemoteAll(server);
        h.assertTrue(n >= 1 && !com.mrgregles.bsp_core.bank.Bank.account(server, id).remoteShops, "the admin reset clears it");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void deposit_box_rent_slots_expiry_and_holding_fee(GameTestHelper h) {
        var server = server(h);
        var player = h.makeMockPlayer();
        UUID id = player.getUUID();
        var B = com.mrgregles.bsp_core.bank.DepositBox.class;
        com.mrgregles.bsp_core.bank.Bank.ensure(server, id, "Fay", g -> {});
        com.mrgregles.bsp_core.bank.Bank.give(server, id, 100_000, com.mrgregles.bsp_core.bank.BankTransaction.Type.ADMIN, "test", "", r -> {});
        var a = com.mrgregles.bsp_core.bank.Bank.account(server, id);
        long now = System.currentTimeMillis(), rent = com.mrgregles.bsp_core.bank.DepositBox.rentPerDay(), fee = com.mrgregles.bsp_core.bank.DepositBox.feePerDay(), step = com.mrgregles.bsp_core.bank.DepositBox.slotStepPieces();
        h.assertTrue(com.mrgregles.bsp_core.bank.DepositBox.state(a, now) == com.mrgregles.bsp_core.bank.DepositBox.State.NONE, "no box yet");
        com.mrgregles.bsp_core.bank.BankResult[] r = new com.mrgregles.bsp_core.bank.BankResult[1];
        com.mrgregles.bsp_core.bank.DepositBox.addSlots(server, player, -1, x -> r[0] = x);
        h.assertTrue(!r[0].ok() && r[0].key().equals("closed"), "no slots before renting");
        long before = a.pieces;
        com.mrgregles.bsp_core.bank.DepositBox.rent(server, player, 7, -1, x -> r[0] = x);
        h.assertTrue(r[0].ok() && com.mrgregles.bsp_core.bank.DepositBox.state(a, now) == com.mrgregles.bsp_core.bank.DepositBox.State.OPEN && a.boxSlots == 6, "rented: open with 6 slots");
        h.assertTrue(a.pieces == before - 7 * rent, "7 days cost 7 x rent: " + (before - a.pieces));
        h.assertTrue(com.mrgregles.bsp_core.bank.DepositBox.daysLeft(a, System.currentTimeMillis()) == 7, "7 days left: " + com.mrgregles.bsp_core.bank.DepositBox.daysLeft(a, System.currentTimeMillis()));
        a.box.setStackInSlot(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 5));
        for (int i = 0; i < 4; i++) {
            com.mrgregles.bsp_core.bank.DepositBox.addSlots(server, player, -1, x -> r[0] = x);
        }
        h.assertTrue(a.boxSlots == 18, "three purchases reach 18: " + a.boxSlots);
        com.mrgregles.bsp_core.bank.DepositBox.addSlots(server, player, -1, x -> r[0] = x);
        h.assertTrue(!r[0].ok() && r[0].key().equals("full"), "no more than 18");
        h.assertTrue(a.pieces == before - 7 * rent - 4 * step, "four slot steps paid: " + (before - a.pieces));
        // the rent runs out five days ago
        a.boxUntilMs = now - 5 * com.mrgregles.bsp_core.bank.DepositBox.DAY_MS + 1000;
        h.assertTrue(com.mrgregles.bsp_core.bank.DepositBox.state(a, now) == com.mrgregles.bsp_core.bank.DepositBox.State.EXPIRED, "expired");
        h.assertTrue(com.mrgregles.bsp_core.bank.DepositBox.lapsedDays(a, now) == 5, "five lapsed days: " + com.mrgregles.bsp_core.bank.DepositBox.lapsedDays(a, now));
        h.assertTrue(com.mrgregles.bsp_core.bank.DepositBox.rentCost(a, now, 7) == 7 * rent + 5 * fee, "owed is rent plus the holding fee");
        h.assertTrue(a.box.getStackInSlot(0).getCount() == 5, "the items are kept while expired");
        before = a.pieces;
        com.mrgregles.bsp_core.bank.DepositBox.rent(server, player, 7, -1, x -> r[0] = x);
        h.assertTrue(r[0].ok() && com.mrgregles.bsp_core.bank.DepositBox.state(a, System.currentTimeMillis()) == com.mrgregles.bsp_core.bank.DepositBox.State.OPEN && a.boxSlots == 18, "open again with the slots kept");
        h.assertTrue(a.pieces == before - 7 * rent - 5 * fee, "paid rent and fee: " + (before - a.pieces));
        // paying with coins: one Copper covers 7 days and the change comes back as pieces
        before = a.pieces;
        player.getInventory().add(new net.minecraft.world.item.ItemStack(com.mrgregles.bsp_core.coin.CoinTier.COPPER.coin(), 1));
        com.mrgregles.bsp_core.bank.DepositBox.rent(server, player, 7, com.mrgregles.bsp_core.coin.CoinTier.COPPER.ordinal(), x -> r[0] = x);
        long perCopper = com.mrgregles.bsp_core.bank.Bank.piecesPerCoin(com.mrgregles.bsp_core.coin.CoinTier.COPPER);
        h.assertTrue(r[0].ok() && player.getInventory().countItem(com.mrgregles.bsp_core.coin.CoinTier.COPPER.coin()) == 0, "the coin was taken");
        h.assertTrue(a.pieces == before + perCopper - 7 * rent, "change credited as pieces: " + (a.pieces - before));
        h.assertTrue(com.mrgregles.bsp_core.bank.DepositBox.daysLeft(a, System.currentTimeMillis()) == 14, "14 days left: " + com.mrgregles.bsp_core.bank.DepositBox.daysLeft(a, System.currentTimeMillis()));
        com.mrgregles.bsp_core.bank.DepositBox.rent(server, player, 400, -1, x -> r[0] = x);
        h.assertTrue(!r[0].ok() && r[0].key().equals("days"), "cannot rent past the limit");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void deposit_box_refuses_coins_and_totems(GameTestHelper h) {
        h.assertTrue(!com.mrgregles.bsp_core.bank.DepositBox.allowed(new net.minecraft.world.item.ItemStack(com.mrgregles.bsp_core.coin.CoinTier.GOLD.coin())), "no coins");
        h.assertTrue(!com.mrgregles.bsp_core.bank.DepositBox.allowed(new net.minecraft.world.item.ItemStack(com.mrgregles.bsp_core.registry.ModItems.SHATTER_TOTEM.get())), "no totems");
        h.assertTrue(com.mrgregles.bsp_core.bank.DepositBox.allowed(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND)), "anything else is fine");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void redis_link_speaks_resp_and_ignores_its_own_messages(GameTestHelper h) throws java.io.IOException {
        byte[] cmd = com.mrgregles.bsp_core.storage.RedisLink.encode("PUBLISH", "bsp_core", "bank:hub:abc");
        h.assertTrue(new String(cmd, java.nio.charset.StandardCharsets.UTF_8).equals("*3\r\n$7\r\nPUBLISH\r\n$8\r\nbsp_core\r\n$12\r\nbank:hub:abc\r\n"), "a command is a RESP array of bulk strings");
        String reply = "*3\r\n$7\r\nmessage\r\n$8\r\nbsp_core\r\n$14\r\nbox:survival:x\r\n+OK\r\n:5\r\n$-1\r\n";
        java.io.InputStream in = new java.io.ByteArrayInputStream(reply.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Object v = com.mrgregles.bsp_core.storage.RedisLink.decode(in);
        h.assertTrue(v instanceof java.util.List<?> l && l.size() == 3 && "message".equals(l.get(0)) && "box:survival:x".equals(l.get(2)), "a published message decodes as a three-part array: " + v);
        h.assertTrue("OK".equals(com.mrgregles.bsp_core.storage.RedisLink.decode(in)), "simple strings decode");
        h.assertTrue("5".equals(com.mrgregles.bsp_core.storage.RedisLink.decode(in)), "integers decode");
        h.assertTrue(com.mrgregles.bsp_core.storage.RedisLink.decode(in) == null, "a null bulk decodes as null");
        boolean threw = false;
        try {
            com.mrgregles.bsp_core.storage.RedisLink.decode(new java.io.ByteArrayInputStream("-ERR no\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.io.IOException e) {
            threw = true;
        }
        h.assertTrue(threw, "an error reply throws");
        h.assertTrue(com.mrgregles.bsp_core.storage.RedisLink.parse("bank:hub:abc", "hub") == null, "a server ignores its own messages");
        String[] p = com.mrgregles.bsp_core.storage.RedisLink.parse("bank:hub:a:b", "survival");
        h.assertTrue(p != null && p[0].equals("bank") && p[1].equals("hub") && p[2].equals("a:b"), "kind, origin and payload, the payload keeping its colons");
        h.assertTrue(com.mrgregles.bsp_core.storage.RedisLink.parse("junk", "hub") == null, "anything else is dropped");
        h.succeed();
    }

    // ------------------------------------------------------------------ shops

    @GameTest(template = EMPTY)
    public void shop_buy_pays_gives_counts_stock_and_restocks(GameTestHelper h) {
        var server = server(h);
        var player = h.makeMockPlayer();
        UUID id = player.getUUID();
        com.mrgregles.bsp_core.bank.Bank.ensure(server, id, "Gus", g -> {});
        com.mrgregles.bsp_core.bank.Bank.give(server, id, 10_000, com.mrgregles.bsp_core.bank.BankTransaction.Type.ADMIN, "test", "", r -> {});
        var shop = com.mrgregles.bsp_core.shop.Shops.create(server, "smith_" + Long.toHexString(System.nanoTime()), "Spawn Smith", com.mrgregles.bsp_core.shop.Shop.Kind.SHOP);
        h.assertTrue(shop != null && com.mrgregles.bsp_core.shop.Shops.create(server, shop.id, "again", com.mrgregles.bsp_core.shop.Shop.Kind.SHOP) == null, "ids are unique");
        var item = new com.mrgregles.bsp_core.shop.ShopItem();
        item.stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 4);
        item.buy = 120;
        item.sell = 30;
        item.stockGlobal = 3;
        item.restockMs = 60_000;
        int index = shop.add(item);
        com.mrgregles.bsp_core.shop.Shops.save(server, shop);
        long start = com.mrgregles.bsp_core.bank.Bank.pieces(server, id);
        com.mrgregles.bsp_core.shop.Shops.Outcome[] o = new com.mrgregles.bsp_core.shop.Shops.Outcome[1];
        com.mrgregles.bsp_core.shop.Shops.buy(server, player, shop, index, 2, true, x -> o[0] = x);
        h.assertTrue(o[0].ok() && o[0].key().equals("test_bought") && player.getInventory().countItem(net.minecraft.world.item.Items.IRON_INGOT) == 0 && com.mrgregles.bsp_core.bank.Bank.pieces(server, id) == start, "test mode moves nothing: " + o[0]);
        com.mrgregles.bsp_core.shop.Shops.buy(server, player, shop, index, 2, false, x -> o[0] = x);
        h.assertTrue(o[0].ok() && o[0].lots() == 2 && o[0].amount() == 240, "bought two lots for 240: " + o[0]);
        h.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.IRON_INGOT) == 8 && com.mrgregles.bsp_core.bank.Bank.pieces(server, id) == start - 240, "eight ingots given, 240 paid");
        h.assertTrue(com.mrgregles.bsp_core.shop.Shops.stockLeft(server, shop, index, id, System.currentTimeMillis()) == 1, "one lot of stock left");
        com.mrgregles.bsp_core.shop.Shops.buy(server, player, shop, index, 5, false, x -> o[0] = x);
        h.assertTrue(o[0].ok() && o[0].lots() == 1, "a buy is cut to the stock left: " + o[0]);
        com.mrgregles.bsp_core.shop.Shops.buy(server, player, shop, index, 1, false, x -> o[0] = x);
        h.assertTrue(!o[0].ok() && o[0].key().equals("sold_out"), "sold out: " + o[0]);
        com.mrgregles.bsp_core.shop.Shops.ledger(server).stock(shop.id, index).restockAt = System.currentTimeMillis() - 1;
        h.assertTrue(com.mrgregles.bsp_core.shop.Shops.stockLeft(server, shop, index, id, System.currentTimeMillis()) == 3, "restocked after the countdown");
        com.mrgregles.bsp_core.bank.Bank.set(server, id, 100, "test", r -> {});
        com.mrgregles.bsp_core.shop.Shops.buy(server, player, shop, index, 1, false, x -> o[0] = x);
        h.assertTrue(!o[0].ok() && o[0].key().equals("short") && player.getInventory().countItem(net.minecraft.world.item.Items.IRON_INGOT) == 12, "too poor: nothing given");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void shop_sell_pays_buy_only_refuses_and_offers_discount(GameTestHelper h) {
        var server = server(h);
        var player = h.makeMockPlayer();
        UUID id = player.getUUID();
        com.mrgregles.bsp_core.bank.Bank.ensure(server, id, "Hal", g -> {});
        com.mrgregles.bsp_core.bank.Bank.give(server, id, 10_000, com.mrgregles.bsp_core.bank.BankTransaction.Type.ADMIN, "test", "", r -> {});
        var shop = com.mrgregles.bsp_core.shop.Shops.create(server, "quarry_" + Long.toHexString(System.nanoTime()), "Quarry", com.mrgregles.bsp_core.shop.Shop.Kind.OFFER);
        shop.offerPercent = 25;
        var item = new com.mrgregles.bsp_core.shop.ShopItem();
        item.stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 16);
        item.buy = 100;
        item.sell = 20;
        int index = shop.add(item);
        var perPlayer = new com.mrgregles.bsp_core.shop.ShopItem();
        perPlayer.stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 1);
        perPlayer.buy = 10;
        perPlayer.stockPlayer = 2;
        int pi = shop.add(perPlayer);
        com.mrgregles.bsp_core.shop.Shops.save(server, shop);
        long now = System.currentTimeMillis();
        h.assertTrue(com.mrgregles.bsp_core.shop.Shops.price(shop, item, true, now) == 75 && com.mrgregles.bsp_core.shop.Shops.price(shop, item, false, now) == 20, "the offer cuts the buy price only");
        com.mrgregles.bsp_core.shop.Shops.Outcome[] o = new com.mrgregles.bsp_core.shop.Shops.Outcome[1];
        player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 40));
        long start = com.mrgregles.bsp_core.bank.Bank.pieces(server, id);
        com.mrgregles.bsp_core.shop.Shops.sell(server, player, shop, index, 10, false, x -> o[0] = x);
        h.assertTrue(o[0].ok() && o[0].lots() == 2 && o[0].amount() == 40 && player.getInventory().countItem(net.minecraft.world.item.Items.COBBLESTONE) == 8, "sold two full lots of 16, 8 left: " + o[0]);
        h.assertTrue(com.mrgregles.bsp_core.bank.Bank.pieces(server, id) == start + 40, "paid 40");
        com.mrgregles.bsp_core.shop.Shops.sellAll(server, player, shop, false, x -> o[0] = x);
        h.assertTrue(!o[0].ok() && o[0].key().equals("none_held"), "sell all with no full lot left: " + o[0]);
        shop.buyOnly = true;
        player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 16));
        com.mrgregles.bsp_core.shop.Shops.sell(server, player, shop, index, 1, false, x -> o[0] = x);
        h.assertTrue(!o[0].ok() && o[0].key().equals("not_bought"), "a buy-only shop buys nothing");
        com.mrgregles.bsp_core.shop.Shops.buy(server, player, shop, pi, 5, false, x -> o[0] = x);
        h.assertTrue(o[0].ok() && o[0].lots() == 2, "per-player stock caps the buy at 2: " + o[0]);
        com.mrgregles.bsp_core.shop.Shops.buy(server, player, shop, pi, 1, false, x -> o[0] = x);
        h.assertTrue(!o[0].ok() && o[0].key().equals("sold_out"), "and then it is sold out for this player");
        h.assertTrue(com.mrgregles.bsp_core.shop.Shops.stockLeft(server, shop, pi, UUID.randomUUID(), now) == 2, "another player still has two");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void shop_rotation_draws_and_open_hours_follow_both_clocks(GameTestHelper h) {
        var server = server(h);
        var shop = com.mrgregles.bsp_core.shop.Shops.create(server, "night_" + Long.toHexString(System.nanoTime()), "Night Market", com.mrgregles.bsp_core.shop.Shop.Kind.ROTATING);
        shop.displaySlots = 3;
        for (int i = 0; i < 10; i++) {
            var it = new com.mrgregles.bsp_core.shop.ShopItem();
            it.stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER, i + 1);
            it.buy = 10;
            shop.add(it);
        }
        com.mrgregles.bsp_core.shop.Shops.save(server, shop);
        long now = System.currentTimeMillis();
        var first = com.mrgregles.bsp_core.shop.Shops.visible(server, shop, now);
        h.assertTrue(first.size() == 3 && new java.util.HashSet<>(first).size() == 3, "three distinct items drawn: " + first);
        h.assertTrue(com.mrgregles.bsp_core.shop.Shops.visible(server, shop, now + 1000).equals(first), "the draw holds until the next rotation");
        var later = com.mrgregles.bsp_core.shop.Shops.visible(server, shop, now + shop.intervalMs + 1);
        h.assertTrue(later.size() == 3, "a new draw after the interval: " + later);
        var real = new com.mrgregles.bsp_core.shop.OpenHours(com.mrgregles.bsp_core.shop.OpenHours.Mode.REAL, 9 * 60, 21 * 60);
        h.assertTrue(real.isOpenAt(10 * 60) && !real.isOpenAt(22 * 60) && real.untilOpen(22 * 60) == 11 * 60, "real clock 09:00 to 21:00");
        var night = new com.mrgregles.bsp_core.shop.OpenHours(com.mrgregles.bsp_core.shop.OpenHours.Mode.GAME, 13000, 1000);
        h.assertTrue(night.isOpenAt(18000) && night.isOpenAt(500) && !night.isOpenAt(6000) && night.untilOpen(6000) == 7000, "a game-time window wrapping past midnight");
        h.assertTrue(com.mrgregles.bsp_core.shop.OpenHours.ALWAYS.isOpenAt(0), "always open");
        var ledger = com.mrgregles.bsp_core.shop.ShopLedger.load(com.mrgregles.bsp_core.shop.ShopLedger.get(server).save(new net.minecraft.nbt.CompoundTag()));
        h.assertTrue(ledger.shop(shop.id) != null && ledger.shop(shop.id).all().size() == 10 && ledger.shop(shop.id).kind == com.mrgregles.bsp_core.shop.Shop.Kind.ROTATING, "shops survive a save and load");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void sell_here_quotes_full_lots_the_shop_buys(GameTestHelper h) {
        var server = server(h);
        var player = h.makeMockPlayer();
        var shop = com.mrgregles.bsp_core.shop.Shops.create(server, "pantry_" + Long.toHexString(System.nanoTime()), "Pantry", com.mrgregles.bsp_core.shop.Shop.Kind.SHOP);
        var bread = new com.mrgregles.bsp_core.shop.ShopItem();
        bread.stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 4);
        bread.buy = 12;
        bread.sell = 3;
        shop.add(bread);
        var torch = new com.mrgregles.bsp_core.shop.ShopItem();
        torch.stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TORCH, 1);
        torch.buy = 2;
        shop.add(torch);
        com.mrgregles.bsp_core.shop.Shops.save(server, shop);
        var menu = new com.mrgregles.bsp_core.shop.SellHereMenu(1, player.getInventory(), shop.id, null, false, null);
        menu.grid.setStackInSlot(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 10));
        menu.grid.setStackInSlot(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TORCH, 5));
        menu.grid.setStackInSlot(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 1));
        long[] q = menu.quote(shop, System.currentTimeMillis());
        h.assertTrue(q[0] == 2 && q[1] == 6, "ten bread is two full lots for 6; torches (no sell price) and diamonds count nothing: " + q[0] + ", " + q[1]);
        shop.buyOnly = true;
        q = menu.quote(shop, System.currentTimeMillis());
        h.assertTrue(q[0] == 0, "a buy-only shop quotes nothing");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void shop_configurator_edits_save_through_the_shop_record(GameTestHelper h) {
        var server = server(h);
        String id = "cfg_" + Long.toHexString(System.nanoTime());
        var A = com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action.class;
        h.assertTrue(com.mrgregles.bsp_core.network.ShopConfigActionPacket.apply(server, null, new com.mrgregles.bsp_core.network.ShopConfigActionPacket(com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action.CREATE, id, "ROTATING", 0, 0), "").equals("created"), "created");
        var shop = com.mrgregles.bsp_core.shop.Shops.ledger(server).shop(id);
        h.assertTrue(shop != null && shop.kind == com.mrgregles.bsp_core.shop.Shop.Kind.ROTATING, "a rotating shop");
        h.assertTrue(com.mrgregles.bsp_core.network.ShopConfigActionPacket.apply(server, null, new com.mrgregles.bsp_core.network.ShopConfigActionPacket(com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action.ADD_ITEM, "minecraft:bread", "", 999, 4), id).equals("saved"), "added bread by id");
        h.assertTrue(com.mrgregles.bsp_core.network.ShopConfigActionPacket.apply(server, null, new com.mrgregles.bsp_core.network.ShopConfigActionPacket(com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action.ADD_ITEM, "minecraft:torch", "", 0, 1), id).equals("saved"), "added torch at the front");
        h.assertTrue(shop.all().size() == 2 && shop.item(0).stack.is(net.minecraft.world.item.Items.TORCH) && shop.item(1).stack.getCount() == 4, "torch first, a lot of four bread second");
        h.assertTrue(com.mrgregles.bsp_core.network.ShopConfigActionPacket.apply(server, null, new com.mrgregles.bsp_core.network.ShopConfigActionPacket(com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action.SET_ITEM, "buy", "12", 1, 0), id).equals("saved") && shop.item(1).buy == 12, "buy price set");
        h.assertTrue(com.mrgregles.bsp_core.network.ShopConfigActionPacket.apply(server, null, new com.mrgregles.bsp_core.network.ShopConfigActionPacket(com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action.SET_ITEM, "buy", "x", 1, 0), id).equals("bad_value"), "a bad value is refused");
        h.assertTrue(com.mrgregles.bsp_core.network.ShopConfigActionPacket.apply(server, null, new com.mrgregles.bsp_core.network.ShopConfigActionPacket(com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action.SET, "remote", "true", 0, 0), id).equals("saved") && shop.remote, "remote on");
        h.assertTrue(com.mrgregles.bsp_core.network.ShopConfigActionPacket.apply(server, null, new com.mrgregles.bsp_core.network.ShopConfigActionPacket(com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action.SET, "offer", "150", 0, 0), id).equals("saved") && shop.offerPercent == 99, "offer capped at 99");
        h.assertTrue(com.mrgregles.bsp_core.network.ShopConfigActionPacket.apply(server, null, new com.mrgregles.bsp_core.network.ShopConfigActionPacket(com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action.MOVE_ITEM, "", "", 0, 1), id).equals("saved") && shop.item(1).stack.is(net.minecraft.world.item.Items.TORCH), "moved the torch to the end");
        h.assertTrue(com.mrgregles.bsp_core.network.ShopConfigActionPacket.apply(server, null, new com.mrgregles.bsp_core.network.ShopConfigActionPacket(com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action.REMOVE_ITEM, "", "", 0, 0), id).equals("saved") && shop.all().size() == 1, "removed one");
        h.assertTrue(com.mrgregles.bsp_core.network.ShopConfigActionPacket.apply(server, null, new com.mrgregles.bsp_core.network.ShopConfigActionPacket(com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action.DELETE, "", "", 0, 0), id).equals("deleted") && com.mrgregles.bsp_core.shop.Shops.ledger(server).shop(id) == null, "deleted");
        h.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public void vendor_post_powers_the_block_beneath_while_open(GameTestHelper h) {
        var server = server(h);
        var shop = com.mrgregles.bsp_core.shop.Shops.create(server, "post_" + Long.toHexString(System.nanoTime()), "Post Shop", com.mrgregles.bsp_core.shop.Shop.Kind.SHOP);
        BlockPos lamp = new BlockPos(3, 1, 3), post = lamp.above();
        h.setBlock(lamp, Blocks.REDSTONE_LAMP);
        h.setBlock(post, com.mrgregles.bsp_core.registry.ModBlocks.VENDOR_POST.get().defaultBlockState());
        var be = entity(h, post, com.mrgregles.bsp_core.shop.VendorPostBlockEntity.class);
        h.assertTrue(!be.assigned() && !h.getBlockState(post).getValue(com.mrgregles.bsp_core.shop.VendorPostBlock.OPEN), "unassigned: closed");
        be.set(shop.id, com.mrgregles.bsp_core.shop.OpenHours.ALWAYS, "minecraft:villager", "");
        h.runAfterDelay(25, () -> {
            h.assertBlockProperty(post, com.mrgregles.bsp_core.shop.VendorPostBlock.OPEN, true);
            h.assertBlockProperty(lamp, net.minecraft.world.level.block.RedstoneLampBlock.LIT, true);
            // a game-time window that is closed now (the test world sits at dawn, time 0..): open from tick 20000 to 23000
            be.set(shop.id, new com.mrgregles.bsp_core.shop.OpenHours(com.mrgregles.bsp_core.shop.OpenHours.Mode.GAME, 20000, 23000), "minecraft:villager", "");
            h.runAfterDelay(25, () -> {
                h.assertBlockProperty(post, com.mrgregles.bsp_core.shop.VendorPostBlock.OPEN, false);
                h.assertBlockProperty(lamp, net.minecraft.world.level.block.RedstoneLampBlock.LIT, false);
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY)
    public void shopping_network_lists_remote_shops_for_unlocked_accounts_only(GameTestHelper h) {
        var server = server(h);
        UUID id = UUID.randomUUID();
        com.mrgregles.bsp_core.bank.Bank.ensure(server, id, "Ivy", g -> {});
        var local = com.mrgregles.bsp_core.shop.Shops.create(server, "loc_" + Long.toHexString(System.nanoTime()), "Local", com.mrgregles.bsp_core.shop.Shop.Kind.SHOP);
        var remote = com.mrgregles.bsp_core.shop.Shops.create(server, "rem_" + Long.toHexString(System.nanoTime()), "Remote", com.mrgregles.bsp_core.shop.Shop.Kind.SHOP);
        remote.remote = true;
        com.mrgregles.bsp_core.shop.Shops.save(server, remote);
        var list = com.mrgregles.bsp_core.shop.ShopListMenu.remote(server, h.getLevel());
        h.assertTrue(list.stream().anyMatch(e -> e.id().equals(remote.id)) && list.stream().noneMatch(e -> e.id().equals(local.id)), "only remote shops are listed");
        h.assertTrue(!com.mrgregles.bsp_core.shop.ShopListMenu.mayOpen(server, id, remote), "locked account cannot open it");
        com.mrgregles.bsp_core.bank.Bank.setRemoteShops(server, id, true);
        h.assertTrue(com.mrgregles.bsp_core.shop.ShopListMenu.mayOpen(server, id, remote) && !com.mrgregles.bsp_core.shop.ShopListMenu.mayOpen(server, id, local), "unlocked: remote yes, local no");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void shop_hours_close_it_on_the_network_and_yield_to_a_post(GameTestHelper h) {
        var server = server(h);
        var shop = com.mrgregles.bsp_core.shop.Shops.create(server, "hrs_" + Long.toHexString(System.nanoTime()), "Hours", com.mrgregles.bsp_core.shop.Shop.Kind.SHOP);
        shop.remote = true;
        h.assertTrue(com.mrgregles.bsp_core.network.ShopConfigActionPacket.apply(server, null, new com.mrgregles.bsp_core.network.ShopConfigActionPacket(com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action.SET, "hoursmode", "GAME", 0, 0), shop.id).equals("saved"), "mode set");
        com.mrgregles.bsp_core.network.ShopConfigActionPacket.apply(server, null, new com.mrgregles.bsp_core.network.ShopConfigActionPacket(com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action.SET, "hoursopen", "20000", 0, 0), shop.id);
        com.mrgregles.bsp_core.network.ShopConfigActionPacket.apply(server, null, new com.mrgregles.bsp_core.network.ShopConfigActionPacket(com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action.SET, "hoursclose", "23000", 0, 0), shop.id);
        h.assertTrue(shop.hours.mode() == com.mrgregles.bsp_core.shop.OpenHours.Mode.GAME && shop.hours.openMin() == 20000 && shop.hours.closeMin() == 23000, "hours saved: " + shop.hours);
        var entry = com.mrgregles.bsp_core.shop.ShopListMenu.remote(server, h.getLevel()).stream().filter(e -> e.id().equals(shop.id)).findFirst().orElse(null);
        h.assertTrue(entry != null && entry.open() == shop.hours.isOpen(h.getLevel()), "the network list carries the open state");
        var post = new com.mrgregles.bsp_core.shop.OpenHours(com.mrgregles.bsp_core.shop.OpenHours.Mode.REAL, 60, 120);
        h.assertTrue(shop.effectiveHours(post) == post && shop.effectiveHours(com.mrgregles.bsp_core.shop.OpenHours.ALWAYS) == shop.hours && shop.effectiveHours(null) == shop.hours, "a post's own hours win; otherwise the shop's");
        h.assertTrue(com.mrgregles.bsp_core.network.ShopConfigActionPacket.apply(server, null, new com.mrgregles.bsp_core.network.ShopConfigActionPacket(com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action.SET, "hoursmode", "REAL", 0, 0), shop.id).equals("saved"), "clock mode set");
        com.mrgregles.bsp_core.network.ShopConfigActionPacket.apply(server, null, new com.mrgregles.bsp_core.network.ShopConfigActionPacket(com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action.SET, "hoursopen", "09:30", 0, 0), shop.id);
        h.assertTrue(shop.hours.openMin() == 570, "hh:mm parsed on the clock: " + shop.hours.openMin());
        h.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public void stall_display_floats_the_linked_shops_wares_while_open(GameTestHelper h) {
        var server = server(h);
        var shop = com.mrgregles.bsp_core.shop.Shops.create(server, "disp_" + Long.toHexString(System.nanoTime()), "Display Shop", com.mrgregles.bsp_core.shop.Shop.Kind.SHOP);
        var it = new com.mrgregles.bsp_core.shop.ShopItem();
        it.stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE, 1);
        it.buy = 5;
        shop.add(it);
        com.mrgregles.bsp_core.shop.Shops.save(server, shop);
        BlockPos postPos = new BlockPos(6, 1, 6), displayPos = new BlockPos(8, 1, 6);
        h.setBlock(postPos, com.mrgregles.bsp_core.registry.ModBlocks.VENDOR_POST.get().defaultBlockState());
        h.setBlock(displayPos, com.mrgregles.bsp_core.registry.ModBlocks.STALL_DISPLAY.get().defaultBlockState());
        var post = entity(h, postPos, com.mrgregles.bsp_core.shop.VendorPostBlockEntity.class);
        var display = entity(h, displayPos, com.mrgregles.bsp_core.shop.StallDisplayBlockEntity.class);
        h.assertTrue(!display.link(h.absolutePos(new BlockPos(1, 1, 1))), "cannot link to air");
        h.assertTrue(display.link(h.absolutePos(postPos)) && display.linked(), "linked to the post");
        display.unlink();
        h.assertTrue(!display.linked() && display.shown.isEmpty(), "unlinked and dark");
        h.assertTrue(display.link(h.absolutePos(postPos)), "linked again");
        var bread = new com.mrgregles.bsp_core.shop.ShopItem();
        bread.stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 4);
        bread.buy = 1;
        shop.add(bread);
        com.mrgregles.bsp_core.shop.Shops.save(server, shop);
        var a = display.choose(h.getLevel(), shop);
        var b = display.choose(h.getLevel(), shop);
        h.assertTrue(a.is(net.minecraft.world.item.Items.GOLDEN_APPLE) && b.is(net.minecraft.world.item.Items.BREAD), "the items take turns: " + a + ", " + b);
        display.pinned = 1;
        h.assertTrue(display.choose(h.getLevel(), shop).is(net.minecraft.world.item.Items.BREAD) && display.choose(h.getLevel(), shop).is(net.minecraft.world.item.Items.BREAD), "a pinned item stays");
        display.pinned = 99;
        h.assertTrue(!display.choose(h.getLevel(), shop).isEmpty(), "a pin to a missing item falls back to the turn");
        display.pinned = 0;
        h.assertTrue(display.scale() == 0.88f && display.height() == 1.16f, "the player-built display's own point (18.5/16, in whole percent) and scale: " + display.scale() + " " + display.height());
        display.scalePct = 150;
        h.assertTrue(display.scale() == 1.5f, "size set");
        post.set(shop.id, com.mrgregles.bsp_core.shop.OpenHours.ALWAYS, "minecraft:villager", "");
        h.succeedWhen(() -> h.assertTrue(display.shown.is(net.minecraft.world.item.Items.GOLDEN_APPLE) && display.shopName.equals("Display Shop"), "the golden apple floats: " + display.shown));
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public void market_stall_is_broken_until_assigned_then_lights_lamps_and_powers_below(GameTestHelper h) {
        var server = server(h);
        var shop = com.mrgregles.bsp_core.shop.Shops.create(server, "stall_" + Long.toHexString(System.nanoTime()), "Stall Shop", com.mrgregles.bsp_core.shop.Shop.Kind.SHOP);
        var it = new com.mrgregles.bsp_core.shop.ShopItem();
        it.stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.APPLE, 1);
        it.buy = 5;
        it.stockGlobal = 1;
        shop.add(it);
        com.mrgregles.bsp_core.shop.Shops.save(server, shop);
        BlockPos lamp = new BlockPos(5, 1, 5), lower = new BlockPos(5, 2, 5), upper = new BlockPos(5, 3, 5);
        h.setBlock(lamp, Blocks.REDSTONE_LAMP);
        var block = com.mrgregles.bsp_core.registry.ModBlocks.MARKET_STALL.get();
        var state = block.defaultBlockState().setValue(com.mrgregles.bsp_core.shop.MarketStallBlock.FACING, net.minecraft.core.Direction.EAST);
        h.setBlock(lower, state);
        h.setBlock(upper, state.setValue(com.mrgregles.bsp_core.shop.MarketStallBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        var stall = entity(h, lower, com.mrgregles.bsp_core.shop.MarketStallBlockEntity.class);
        h.assertTrue(h.getBlockEntity(upper) == null, "the upper half has no block entity");
        h.assertTrue(h.getBlockState(lower).getValue(com.mrgregles.bsp_core.shop.MarketStallBlock.BROKEN) && stall.skin() == com.mrgregles.bsp_core.shop.StallSkin.VANILLA, "a new stall is vanilla and broken");
        h.assertTrue(state.getLightEmission(h.getLevel(), h.absolutePos(lower)) == 0, "a broken stall gives no light");
        stall.setSkin(com.mrgregles.bsp_core.shop.StallSkin.NETHER);
        h.assertTrue(h.getBlockState(lower).getValue(com.mrgregles.bsp_core.shop.MarketStallBlock.SKIN) == com.mrgregles.bsp_core.shop.StallSkin.NETHER
                && h.getBlockState(upper).getValue(com.mrgregles.bsp_core.shop.MarketStallBlock.SKIN) == com.mrgregles.bsp_core.shop.StallSkin.NETHER, "the skin is on both halves");
        h.assertTrue(h.getBlockEntity(lower) == stall, "changing the skin keeps the block entity");
        BlockPos displayPos = new BlockPos(7, 2, 5);
        h.setBlock(displayPos, com.mrgregles.bsp_core.registry.ModBlocks.STALL_DISPLAY.get().defaultBlockState());
        var display = entity(h, displayPos, com.mrgregles.bsp_core.shop.StallDisplayBlockEntity.class);
        h.assertTrue(h.getBlockState(displayPos).getValue(com.mrgregles.bsp_core.shop.StallDisplayBlock.BROKEN), "a new display looks neglected");
        h.assertTrue(display.link(h.absolutePos(lower)), "a Stall Display links to a stall");
        stall.assign(shop.id, com.mrgregles.bsp_core.shop.OpenHours.ALWAYS, "", "");
        h.runAfterDelay(30, () -> {
            display.refreshNow();
            var ds = h.getBlockState(displayPos);
            h.assertTrue(ds.getValue(com.mrgregles.bsp_core.shop.StallDisplayBlock.SKIN) == com.mrgregles.bsp_core.shop.StallSkin.NETHER && !ds.getValue(com.mrgregles.bsp_core.shop.StallDisplayBlock.BROKEN)
                    && display.shown.is(net.minecraft.world.item.Items.APPLE), "the display takes the stall's style and floats the apple");
            h.assertTrue(display.height() == 1.03f && display.scale() == 0.81f, "the nether display's own item point (16.5/16, in whole percent) and scale are the defaults: " + display.height() + " " + display.scale());
            h.assertTrue(!h.getBlockState(lower).getValue(com.mrgregles.bsp_core.shop.MarketStallBlock.BROKEN) && !h.getBlockState(upper).getValue(com.mrgregles.bsp_core.shop.MarketStallBlock.BROKEN), "assigned: whole again on both halves");
            h.assertTrue(stall.open && stall.stocked && stall.shown.is(net.minecraft.world.item.Items.APPLE), "open, in stock, the apple on the counter: " + stall.open + " " + stall.stocked + " " + stall.shown);
            h.assertTrue(h.getBlockState(lower).getLightEmission(h.getLevel(), h.absolutePos(lower)) == 7, "a whole nether stall glows");
            h.assertTrue(h.getBlockState(lamp).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT), "the lamp beneath is lit while open");
            com.mrgregles.bsp_core.shop.Shops.ledger(server).stock(shop.id, 0).left = 0;
            h.runAfterDelay(25, () -> {
                h.assertTrue(stall.open && !stall.stocked, "sold out: the stock lamp goes red, still open");
                stall.assign("", com.mrgregles.bsp_core.shop.OpenHours.ALWAYS, "", "");
                h.runAfterDelay(25, () -> {
                    h.assertTrue(h.getBlockState(lower).getValue(com.mrgregles.bsp_core.shop.MarketStallBlock.BROKEN) && h.getBlockState(upper).getValue(com.mrgregles.bsp_core.shop.MarketStallBlock.BROKEN), "unassigned: broken again");
                    h.assertTrue(!stall.open && stall.shown.isEmpty(), "dark counter");
                    display.refreshNow();
                    h.assertTrue(h.getBlockState(displayPos).getValue(com.mrgregles.bsp_core.shop.StallDisplayBlock.BROKEN), "the display looks neglected again");
                    h.assertTrue(!h.getBlockState(lamp).getValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT), "the lamp beneath is off");
                    h.setBlock(upper, Blocks.AIR);
                    h.runAfterDelay(2, () -> {
                        h.assertBlock(lower, b -> b == Blocks.AIR, () -> "taking the upper half away removes the lower half too");
                        h.succeed();
                    });
                });
            });
        });
    }
}
