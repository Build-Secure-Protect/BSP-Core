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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
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
        int cost = com.mrgregles.bsp_core.bank.Bank.remoteUnlockCoins();
        var tier = com.mrgregles.bsp_core.bank.Bank.remoteUnlockTier();
        h.assertTrue(cost == 12 && tier == com.mrgregles.bsp_core.coin.CoinTier.NETHERITE, "the default price is 12 Netherite coins");
        player.getInventory().add(new net.minecraft.world.item.ItemStack(com.mrgregles.bsp_core.coin.CoinTier.ILLYRIUM.coin(), 64));
        com.mrgregles.bsp_core.bank.Bank.unlockRemote(server, player, x -> r[0] = x);
        h.assertTrue(!r[0].ok() && r[0].key().equals("coins_unlock"), "other tiers do not pay for it, whatever their value");
        player.getInventory().add(new net.minecraft.world.item.ItemStack(tier.coin(), cost + 2));
        com.mrgregles.bsp_core.bank.Bank.unlockRemote(server, player, x -> r[0] = x);
        h.assertTrue(r[0].ok() && com.mrgregles.bsp_core.bank.Bank.account(server, id).remoteShops, "unlocked with coins");
        h.assertTrue(player.getInventory().countItem(tier.coin()) == 2, "the coins were taken: " + player.getInventory().countItem(tier.coin()));
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
            // a game-time window that is closed now: it opens a quarter day from the current day time (the test world is
            // reused between runs with the daylight cycle on, so a fixed window would sometimes be open)
            int now = (int) (h.getLevel().getDayTime() % 24000);
            be.set(shop.id, new com.mrgregles.bsp_core.shop.OpenHours(com.mrgregles.bsp_core.shop.OpenHours.Mode.GAME, (now + 6000) % 24000, (now + 9000) % 24000), "minecraft:villager", "");
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
        BlockPos displayPos = new BlockPos(8, 1, 6);
        h.setBlock(displayPos, com.mrgregles.bsp_core.registry.ModBlocks.STALL_DISPLAY.get().defaultBlockState());
        var display = entity(h, displayPos, com.mrgregles.bsp_core.shop.StallDisplayBlockEntity.class);
        h.assertTrue(!display.linkShop("no_such_shop_" + Long.toHexString(System.nanoTime())), "cannot link a shop that does not exist");
        h.assertTrue(display.linkShop(shop.id) && display.linked(), "linked to the shop");
        display.unlink();
        h.assertTrue(!display.linked() && display.shown.isEmpty(), "unlinked and dark");
        h.assertTrue(display.linkShop(shop.id), "linked again");
        var bread = new com.mrgregles.bsp_core.shop.ShopItem();
        bread.stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 4);
        bread.buy = 1;
        shop.add(bread);
        com.mrgregles.bsp_core.shop.Shops.save(server, shop);
        var a = display.choose(h.getLevel(), shop);
        var b = display.choose(h.getLevel(), shop);
        h.assertTrue((a.is(net.minecraft.world.item.Items.GOLDEN_APPLE) && b.is(net.minecraft.world.item.Items.BREAD)) || (a.is(net.minecraft.world.item.Items.BREAD) && b.is(net.minecraft.world.item.Items.GOLDEN_APPLE)), "the items take turns: " + a + ", " + b);
        display.pinned = 1;
        h.assertTrue(display.choose(h.getLevel(), shop).is(net.minecraft.world.item.Items.BREAD) && display.choose(h.getLevel(), shop).is(net.minecraft.world.item.Items.BREAD), "a pinned item stays");
        display.pinned = 99;
        h.assertTrue(!display.choose(h.getLevel(), shop).isEmpty(), "a pin to a missing item falls back to the turn");
        display.pinned = 0;
        h.assertTrue(display.scale() == 0.88f && display.height() == 1.16f, "the player-built display's own point (18.5/16, in whole percent) and scale: " + display.scale() + " " + display.height());
        display.scalePct = 150;
        h.assertTrue(display.scale() == 1.5f, "size set");
        display.refreshNow();
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
        h.assertTrue(display.linkShop(shop.id), "a Stall Display shows the stall's shop");
        display.setSkin(com.mrgregles.bsp_core.shop.StallSkin.NETHER);
        stall.assign(shop.id, com.mrgregles.bsp_core.shop.OpenHours.ALWAYS, "", "");
        h.runAfterDelay(30, () -> {
            display.refreshNow();
            var ds = h.getBlockState(displayPos);
            h.assertTrue(ds.getValue(com.mrgregles.bsp_core.shop.StallDisplayBlock.SKIN) == com.mrgregles.bsp_core.shop.StallSkin.NETHER && !ds.getValue(com.mrgregles.bsp_core.shop.StallDisplayBlock.BROKEN)
                    && display.shown.is(net.minecraft.world.item.Items.APPLE), "the display shows its chosen style and floats the apple");
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

    @GameTest(template = EMPTY)
    public void api_totem_lookups_for_other_mods(GameTestHelper h) {
        var server = server(h);
        var player = h.makeMockPlayer();
        UUID id = player.getUUID();
        var api = com.mrgregles.bsp_core.api.BspTotems.class;
        h.assertTrue(com.mrgregles.bsp_core.api.BspTotems.placed(server, UUID.randomUUID()).isEmpty(), "a stranger has no placed totems");
        h.assertTrue(com.mrgregles.bsp_core.api.BspTotems.beingStolen(server, id).isEmpty(), "nothing is being stolen");
        var buff = com.mrgregles.bsp_core.api.BspTotems.upgrade("swiftness");
        h.assertTrue(buff == com.mrgregles.bsp_core.totem.TotemUpgrades.Buff.SWIFTNESS && com.mrgregles.bsp_core.api.BspTotems.upgrade("no_such") == null, "upgrades look up by name");
        h.assertTrue(com.mrgregles.bsp_core.api.BspTotems.highestLevel(player, buff) == 0, "no totem, level 0");
        var totem = new net.minecraft.world.item.ItemStack(com.mrgregles.bsp_core.registry.ModItems.SHATTER_TOTEM.get());
        new com.mrgregles.bsp_core.totem.TotemOwner(id, "Erin").applyTo(totem);
        com.mrgregles.bsp_core.totem.TotemUpgrades.setLevel(totem, buff, 2);
        player.getInventory().add(totem);
        h.assertTrue(com.mrgregles.bsp_core.api.BspTotems.highestLevel(player, buff) == 2, "a carried totem counts: " + com.mrgregles.bsp_core.api.BspTotems.highestLevel(player, buff));
        var other = new net.minecraft.world.item.ItemStack(com.mrgregles.bsp_core.registry.ModItems.SHATTER_TOTEM.get());
        new com.mrgregles.bsp_core.totem.TotemOwner(UUID.randomUUID(), "Sam").applyTo(other);
        com.mrgregles.bsp_core.totem.TotemUpgrades.setLevel(other, buff, 5);
        player.getInventory().add(other);
        h.assertTrue(com.mrgregles.bsp_core.api.BspTotems.highestLevel(player, buff) == 2, "someone else's totem in the bag does not count");
        h.assertTrue(com.mrgregles.bsp_core.api.BspPlayers.isDimensionAllowed(net.minecraft.world.level.Level.OVERWORLD.location()), "the Overworld is allowed");
        h.assertTrue(!com.mrgregles.bsp_core.api.BspZones.isBlocked(h.getLevel(), h.absolutePos(new BlockPos(1, 1, 1)), com.mrgregles.bsp_core.api.BspZones.TOTEM_PLACE), "no zone here");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void api_totem_events_fire_for_other_mods(GameTestHelper h) {
        var server = server(h);
        UUID erin = UUID.randomUUID(), sam = UUID.randomUUID();
        List<String> seen = new ArrayList<>();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((com.mrgregles.bsp_core.api.event.TotemEvent.Placed e) -> {
            if (erin.equals(e.getOwner())) {
                seen.add("placed");
            }
        });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((com.mrgregles.bsp_core.api.event.TotemEvent.OwnerChanged e) -> {
            if (erin.equals(e.getPrevious()) && sam.equals(e.getOwner())) {
                seen.add("changed");
            }
        });
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((com.mrgregles.bsp_core.api.event.TotemEvent.Removed e) -> {
            if (sam.equals(e.getOwner())) {
                seen.add("removed");
            }
        });
        BlockPos pos = new BlockPos(4, 1, 4);
        h.setBlock(pos, com.mrgregles.bsp_core.registry.ModBlocks.SHATTER_TOTEM.get().defaultBlockState());
        var totem = entity(h, pos, com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity.class);
        totem.setOwner(new com.mrgregles.bsp_core.totem.TotemOwner(erin, "Erin"));
        h.assertTrue(seen.contains("placed"), "Placed fired for Erin: " + seen);
        h.assertTrue(com.mrgregles.bsp_core.api.BspTotems.placed(server, erin).contains(net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), h.absolutePos(pos))), "the API lists it");
        totem.setOwner(new com.mrgregles.bsp_core.totem.TotemOwner(sam, "Sam"));
        h.assertTrue(seen.contains("changed"), "OwnerChanged fired from Erin to Sam: " + seen);
        h.setBlock(pos, Blocks.AIR);
        h.runAfterDelay(2, () -> {
            h.assertTrue(seen.contains("removed"), "Removed fired for Sam's totem: " + seen);
            h.assertTrue(com.mrgregles.bsp_core.api.BspTotems.placed(server, sam).isEmpty(), "and the API no longer lists it");
            h.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public void api_transfer_hook_stores_the_totem_and_hands_it_back(GameTestHelper h) {
        var server = server(h);
        var player = h.makeMockPlayer();
        UUID id = player.getUUID();
        var totem = new net.minecraft.world.item.ItemStack(com.mrgregles.bsp_core.registry.ModItems.SHATTER_TOTEM.get());
        new com.mrgregles.bsp_core.totem.TotemOwner(id, "Erin").applyTo(totem);
        player.getInventory().setItem(0, totem);
        h.assertTrue(com.mrgregles.bsp_core.api.BspTransfer.beginTransfer(player) == 1, "one totem taken for the transfer");
        h.assertTrue(com.mrgregles.bsp_core.api.BspTransfer.isTransferring(id), "the flag is set");
        h.assertTrue(com.mrgregles.bsp_core.totem.TotemInventories.removeAll(player).isEmpty(), "the totem is out of the inventory");
        h.assertTrue(com.mrgregles.bsp_core.api.BspTransfer.endTransfer(player), "a cancelled teleport ends the transfer");
        h.assertTrue(!com.mrgregles.bsp_core.api.BspTransfer.isTransferring(id), "the flag is cleared");
        h.assertTrue(com.mrgregles.bsp_core.totem.TotemInventories.isTotem(player.getInventory().getItem(0)), "the totem is back in the hand slot");
        h.assertTrue(!com.mrgregles.bsp_core.api.BspTransfer.endTransfer(player), "nothing to end twice");
        // the window runs out without a logout: the totem comes back on its own
        player.getInventory().setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE, 64));
        com.mrgregles.bsp_core.api.BspTransfer.beginTransfer(player);
        com.mrgregles.bsp_core.api.BspTransfer.expire(server, System.currentTimeMillis() + com.mrgregles.bsp_core.api.BspTransfer.transferSeconds() * 1000L - 1000);
        h.assertTrue(com.mrgregles.bsp_core.api.BspTransfer.isTransferring(id), "still inside the window");
        com.mrgregles.bsp_core.api.BspTransfer.expire(server, System.currentTimeMillis() + com.mrgregles.bsp_core.api.BspTransfer.transferSeconds() * 1000L + 1);
        h.assertTrue(!com.mrgregles.bsp_core.api.BspTransfer.isTransferring(id), "expired after the window");
        // (a mock player is not in the server's player list, so the hand-back on expiry is covered by the endTransfer path above)
        // a logout mid-transfer: the handler gets the held totem to place, and the flag is gone
        var totem2 = new net.minecraft.world.item.ItemStack(com.mrgregles.bsp_core.registry.ModItems.SHATTER_TOTEM.get());
        new com.mrgregles.bsp_core.totem.TotemOwner(id, "Erin").applyTo(totem2);
        player.getInventory().setItem(0, totem2);
        com.mrgregles.bsp_core.api.BspTransfer.beginTransfer(player);
        var held = com.mrgregles.bsp_core.api.BspTransfer.takeOnLogout(id);
        h.assertTrue(held != null && held.size() == 1 && !com.mrgregles.bsp_core.api.BspTransfer.isTransferring(id), "a logout without a database hands the held totem to the placer");
        h.assertTrue(com.mrgregles.bsp_core.api.BspTransfer.takeOnLogout(id) == null, "an ordinary logout has nothing");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void api_teleport_upgrades_sit_on_the_home_spoke_at_carried_prices(GameTestHelper h) {
        var B = com.mrgregles.bsp_core.totem.TotemUpgrades.Buff.class;
        var homes = com.mrgregles.bsp_core.totem.TotemUpgrades.Buff.EXTRA_HOMES;
        var travel = com.mrgregles.bsp_core.totem.TotemUpgrades.Buff.SWIFT_TRAVEL;
        h.assertTrue(homes.parent() == com.mrgregles.bsp_core.totem.TotemUpgrades.Buff.HEALING && travel.parent() == homes, "Healing Aura, then Extra Homes, then Swift Travel");
        h.assertTrue(homes.path == 3 && travel.path == 3 && homes.tier == 1 && travel.tier == 2, "on the Home spoke at tiers II and III");
        h.assertTrue(homes.maxLevel() == 3 && travel.maxLevel() == 3 && !homes.placedOnly, "three levels each, bought while carried too");
        var price = com.mrgregles.bsp_core.totem.TotemUpgrades.price(homes, 0);
        var swift = com.mrgregles.bsp_core.totem.TotemUpgrades.price(com.mrgregles.bsp_core.totem.TotemUpgrades.Buff.SWIFTNESS, 0);
        h.assertTrue(price != null && swift != null && price.coin() == swift.coin() && price.coins() == swift.coins() && price.xp() == swift.xp(), "priced like Swiftness (same tier, Carried, half XP): " + price + " vs " + swift);
        h.assertTrue(com.mrgregles.bsp_core.totem.TotemUpgrades.price(homes, 3) == null, "maxed at three");
        h.assertTrue(com.mrgregles.bsp_core.api.BspTotems.upgrade("extra_homes") == homes && com.mrgregles.bsp_core.api.BspTotems.upgrade("SWIFT_TRAVEL") == travel, "the API finds them by name");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void api_zone_teleport_rules_are_stored_and_read(GameTestHelper h) {
        var server = server(h);
        var ledger = com.mrgregles.bsp_core.zone.ZoneLedger.get(server);
        BlockPos centre = h.absolutePos(new BlockPos(4, 1, 4));
        var where = net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), centre);
        ledger.put(where, new int[]{3, 3, 3, 3, 3, 3}, java.util.Set.of(com.mrgregles.bsp_core.api.BspZones.TELEPORT_IN, com.mrgregles.bsp_core.api.BspZones.SET_HOME));
        BlockPos inside = centre.offset(2, 0, 2), outside = centre.offset(6, 0, 0);
        h.assertTrue(com.mrgregles.bsp_core.api.BspZones.isBlocked(h.getLevel(), inside, com.mrgregles.bsp_core.api.BspZones.TELEPORT_IN), "teleporting in is blocked inside");
        h.assertTrue(com.mrgregles.bsp_core.api.BspZones.isBlocked(h.getLevel(), inside, com.mrgregles.bsp_core.api.BspZones.SET_HOME), "setting a home is blocked inside");
        h.assertTrue(!com.mrgregles.bsp_core.api.BspZones.isBlocked(h.getLevel(), inside, com.mrgregles.bsp_core.api.BspZones.TELEPORT_OUT), "teleporting out was not switched on");
        h.assertTrue(!com.mrgregles.bsp_core.api.BspZones.isBlocked(h.getLevel(), outside, com.mrgregles.bsp_core.api.BspZones.TELEPORT_IN), "nothing is blocked outside");
        h.assertTrue(com.mrgregles.bsp_core.zone.ZoneRules.known("set_home") && !com.mrgregles.bsp_core.zone.ZoneRules.defaults().contains("set_home"), "a known rule, off on a new block");
        h.assertTrue(com.mrgregles.bsp_core.compat.regionguard.RegionGuardZones.allows(h.getLevel(), inside, com.mrgregles.bsp_core.zone.ZoneLedger.TOTEM_PLACE, null)
                == !com.mrgregles.bsp_core.compat.regionguard.RegionGuardZones.present(), "without Region Guard its say is always yes");
        ledger.remove(where);
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void api_recall_offer_waits_for_an_owner_on_another_server(GameTestHelper h) {
        var player = h.makeMockPlayer();
        UUID away = UUID.randomUUID();
        com.mrgregles.bsp_core.totem.RecallService.offer(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 3)), away, 3);
        h.assertTrue(com.mrgregles.bsp_core.totem.RecallService.hasRemoteOffer(away), "an owner who is not here gets a waiting offer");
        com.mrgregles.bsp_core.totem.RecallService.offer(h.getLevel(), h.absolutePos(new BlockPos(3, 1, 3)), UUID.randomUUID(), 0);
        String[] target = new String[1];
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((com.mrgregles.bsp_core.api.event.RecallEvent.CrossServer e) -> {
            if (e.getOwner() == player) {
                target[0] = e.getTargetServer();
                e.setHandled();
            }
        });
        h.assertTrue(com.mrgregles.bsp_core.totem.RecallService.acceptRemote(player, "hub"), "a listener that moves the player marks the offer handled");
        h.assertTrue("hub".equals(target[0]), "the event names the server the totem stands on: " + target[0]);
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void api_transfer_departures_time_out_after_48_hours(GameTestHelper h) {
        var server = server(h);
        var ledger = com.mrgregles.bsp_core.totem.TransferLedger.get(server);
        UUID id = UUID.randomUUID();
        long now = System.currentTimeMillis();
        var where = net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), h.absolutePos(new BlockPos(2, 1, 2)));
        ledger.put(id, now, where);
        h.assertTrue(ledger.has(id) && ledger.departure(id).where().equals(where), "the departure point is kept");
        h.assertTrue(com.mrgregles.bsp_core.api.BspTransfer.timeoutMs() == 48L * 3_600_000L, "the default timeout is 48 hours");
        h.assertTrue(ledger.due(now + 47L * 3_600_000L, com.mrgregles.bsp_core.api.BspTransfer.timeoutMs()).isEmpty(), "not due at 47 hours");
        h.assertTrue(ledger.due(now + 49L * 3_600_000L, com.mrgregles.bsp_core.api.BspTransfer.timeoutMs()).contains(id), "due at 49 hours");
        var saved = com.mrgregles.bsp_core.totem.TransferLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag()));
        h.assertTrue(saved.has(id) && saved.departure(id).where().equals(where), "survives a save and load");
        com.mrgregles.bsp_core.api.BspTransfer.placeBackTimedOut(server, now + 49L * 3_600_000L);
        h.assertTrue(!ledger.has(id), "without a database a due departure is simply forgotten");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void tank_passes_on_the_powers_its_plasma_carries_until_it_is_used_up(GameTestHelper h) {
        buildTank(h, TANK, 3, 3, 3, Set.of(TANK.offset(1, 2, 1)), null);
        h.succeedWhen(() -> {
            TankPartBlockEntity master = entity(h, TANK, TankPartBlockEntity.class);
            h.assertTrue(master.isMaster(), "formed");
            int oc = TotemUpgrades.Buff.OVERCLOCK.ordinal(), out = TotemUpgrades.Buff.OUTPUT.ordinal();
            int[] offered = new int[TotemUpgrades.Buff.values().length];
            offered[oc] = 3;
            offered[out] = 2;
            master.drain(master.stored());
            h.assertTrue(master.add(1000, offered) == 1000, "a thousand mB with Overclock III and Output II went in");
            h.assertTrue(master.offered()[oc] == 3 && master.offered()[out] == 2 && master.buffedMb(oc) == 1000, "the tank now offers what fed it");
            TankPortBlockEntity port = entity(h, TANK.offset(1, 2, 1), TankPortBlockEntity.class);
            h.assertTrue(port.offered()[oc] == 3, "a port giving out offers it too, totem or no totem");
            h.assertTrue(master.drain(500) == 500 && master.buffedMb(oc) == 500, "draining takes a proportional share of the buffed plasma: " + master.buffedMb(oc));
            h.assertTrue(master.add(500, null) == 500 && master.buffedMb(oc) == 500 && master.offered()[oc] == 3, "plain plasma dilutes it but the level stays while any remains");
            master.drain(1000);
            h.assertTrue(master.buffedMb(oc) == 0 && master.offered()[oc] == 0 && master.offered()[out] == 0, "used up: nothing more is passed on");
            master.add(200, offered);
            var tag = master.saveWithoutMetadata();
            h.assertTrue(tag.getLongArray("BuffMb")[oc] == 200 && tag.getIntArray("BuffLevel")[oc] == 3, "the buffed amount and level are saved with the tank");
            master.drain(master.stored());
        });
    }

    @GameTest(template = EMPTY)
    public void api_lists_placed_totems_near_a_spot_and_the_steal_radius(GameTestHelper h) {
        var server = server(h);
        UUID erin = UUID.randomUUID();
        BlockPos pos = new BlockPos(4, 1, 4);
        h.setBlock(pos, com.mrgregles.bsp_core.registry.ModBlocks.SHATTER_TOTEM.get().defaultBlockState());
        entity(h, pos, com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity.class).setOwner(new com.mrgregles.bsp_core.totem.TotemOwner(erin, "Erin"));
        var here = net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), h.absolutePos(pos));
        h.assertTrue(com.mrgregles.bsp_core.api.BspTotems.allPlaced(server).stream().anyMatch(p -> p.owner().equals(erin) && p.where().equals(here)), "every placed totem is listed with its owner");
        h.assertTrue(com.mrgregles.bsp_core.api.BspTotems.placedNear(h.getLevel(), h.absolutePos(pos.offset(2, 0, 0)), 3).stream().anyMatch(p -> p.owner().equals(erin)), "found within three blocks");
        h.assertTrue(com.mrgregles.bsp_core.api.BspTotems.placedNear(h.getLevel(), h.absolutePos(pos.offset(2, 0, 0)), 1).stream().noneMatch(p -> p.owner().equals(erin)), "not within one");
        h.assertTrue(com.mrgregles.bsp_core.api.BspTotems.isTotem(h.getBlockState(pos)) && !com.mrgregles.bsp_core.api.BspTotems.isTotem(Blocks.STONE.defaultBlockState()), "a totem state is known as one");
        h.assertTrue(com.mrgregles.bsp_core.api.BspTotems.thiefAt(h.getLevel(), h.absolutePos(pos)).isEmpty(), "no steal is running");
        h.assertTrue(com.mrgregles.bsp_core.api.BspTotems.stealRadius() > 0, "the steal radius is published");
        h.assertTrue(com.mrgregles.bsp_core.api.BspTotems.stealsAnywhere(server).stream().noneMatch(s -> s.pos().equals(h.absolutePos(pos))), "no steal is listed for the totem");
        h.assertTrue(com.mrgregles.bsp_core.api.BspTotems.stealsAnywhere(server, erin).isEmpty(), "no steal is listed for the owner");
        h.setBlock(pos, Blocks.AIR);
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void land_office_data_travels_and_says_when_region_guard_is_missing(GameTestHelper h) {
        var plots = java.util.List.of(
                new com.mrgregles.bsp_core.compat.regionguard.RegionGuardClaims.Plot("shop1", "Harbour Row 3", 10, 10, 29, 29, 4000, 0, 0, 0, com.mrgregles.bsp_core.compat.regionguard.RegionGuardClaims.Plot.FOR_SALE, ""),
                new com.mrgregles.bsp_core.compat.regionguard.RegionGuardClaims.Plot("field", "Common Field", -40, -40, 40, 40, 0, 12, 10, 40, com.mrgregles.bsp_core.compat.regionguard.RegionGuardClaims.Plot.OPEN, ""),
                new com.mrgregles.bsp_core.compat.regionguard.RegionGuardClaims.Plot("sam1", "Sam's place", 50, 50, 69, 69, 0, 0, 0, 0, com.mrgregles.bsp_core.compat.regionguard.RegionGuardClaims.Plot.OTHER, "Sam"));
        var packet = new com.mrgregles.bsp_core.network.LandOfficeDataPacket(plots, 5250, true, true, new int[]{0, 0, 19, 19}, "You can afford it.", true);
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        com.mrgregles.bsp_core.network.LandOfficeDataPacket.encode(packet, buf);
        var back = com.mrgregles.bsp_core.network.LandOfficeDataPacket.decode(buf);
        h.assertTrue(back.plots().size() == 3 && back.plots().get(0).name().equals("Harbour Row 3") && back.plots().get(0).price() == 4000 && back.plots().get(1).costPerBlock() == 12
                && back.plots().get(2).kind() == com.mrgregles.bsp_core.compat.regionguard.RegionGuardClaims.Plot.OTHER && back.plots().get(2).owner().equals("Sam"), "listings travel whole, another player's claim with its owner");
        h.assertTrue(back.balance() == 5250 && back.selection() != null && back.selection()[2] == 19 && back.message().equals("You can afford it.") && back.ok(), "balance, outline and message travel");
        h.assertTrue(back.plots().get(1).contains(new int[]{0, 0, 19, 19}) && !back.plots().get(0).contains(new int[]{0, 0, 19, 19}), "an outline knows which plot holds it");
        h.assertTrue(back.plots().get(0).containsXZ(15, 15) && !back.plots().get(0).containsXZ(30, 15), "a click finds a plot");
        BlockPos office = new BlockPos(2, 1, 2);
        h.setBlock(office, com.mrgregles.bsp_core.registry.ModBlocks.LAND_OFFICE.get().defaultBlockState());
        var be = entity(h, office, com.mrgregles.bsp_core.land.LandOfficeBlockEntity.class);
        h.assertTrue(!be.inUse(), "nobody at a fresh office");
        be.userOpened();
        be.userOpened();
        be.userClosed();
        h.assertTrue(be.inUse(), "two at it, one left: still in use");
        be.userClosed();
        h.assertTrue(!be.inUse(), "the last one left");
        h.setBlock(office, Blocks.AIR);
        if (!com.mrgregles.bsp_core.compat.regionguard.RegionGuardClaims.present()) {
            h.assertTrue(com.mrgregles.bsp_core.compat.regionguard.RegionGuardClaims.listings(h.getLevel(), UUID.randomUUID(), BlockPos.ZERO).isEmpty(), "no Region Guard, no listings");
            h.assertTrue(com.mrgregles.bsp_core.compat.regionguard.RegionGuardClaims.selection(null) == null, "and no outline");
            var r = com.mrgregles.bsp_core.compat.regionguard.RegionGuardClaims.quotePlot(null, "shop1");
            h.assertTrue(!r.ok() && r.message().contains("not installed"), "and a plain answer why: " + r.message());
        }
        h.succeed();
    }

    // ------------------------------------------------------------------ plasma between worlds

    /** The seven frames of a ring whose intake at {@code intake} faces north: right is east. */
    private static List<BlockPos> ringFrames(BlockPos intake) {
        List<BlockPos> out = new ArrayList<>();
        for (int[] c : new int[][]{{-1, 0}, {1, 0}, {-1, 1}, {1, 1}, {-1, 2}, {0, 2}, {1, 2}}) {
            out.add(intake.east(c[0]).above(c[1]));
        }
        return out;
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public void accelerator_ring_forms_when_seven_frames_stand_round_an_empty_middle(GameTestHelper h) {
        BlockPos intake = new BlockPos(4, 1, 4), hole = intake.above();
        h.setBlock(intake, ModBlocks.ACCELERATOR_INTAKE.get().defaultBlockState().setValue(com.mrgregles.bsp_core.link.AcceleratorIntakeBlock.FACING, net.minecraft.core.Direction.NORTH));
        for (BlockPos p : ringFrames(intake)) {
            h.setBlock(p, ModBlocks.ACCELERATOR_FRAME.get());
        }
        h.setBlock(hole, Blocks.STONE); // the middle must be empty: with a block there the ring stays unformed
        h.runAfterDelay(45, () -> {
            var be = entity(h, intake, com.mrgregles.bsp_core.link.AcceleratorBlockEntity.class);
            h.assertTrue(!be.isFormed(), "not formed while the middle is blocked");
            h.assertTrue(be.hole().equals(h.absolutePos(hole)), "the controller knows where the middle is");
            h.setBlock(hole, Blocks.AIR);
            h.runAfterDelay(25, () -> {
                h.assertTrue(be.isFormed(), "formed once the middle is clear");
                h.assertBlockProperty(intake, com.mrgregles.bsp_core.machine.StructurePartBlock.FORMED, true);
                for (BlockPos p : ringFrames(intake)) {
                    h.assertBlockProperty(p, com.mrgregles.bsp_core.machine.StructurePartBlock.FORMED, true);
                    h.assertTrue(h.absolutePos(intake).equals(entity(h, p, com.mrgregles.bsp_core.link.LinkPartBlockEntity.class).controller()), "every frame knows the intake");
                }
                h.assertTrue(be.missingFrames().isEmpty(), "nothing missing");
                BlockPos corner = intake.east().above(2);
                h.setBlock(corner, Blocks.AIR);
                h.runAfterDelay(3, () -> {
                    h.assertTrue(!be.isFormed(), "a broken frame un-forms the ring at once");
                    h.assertBlockProperty(intake, com.mrgregles.bsp_core.machine.StructurePartBlock.FORMED, false);
                    h.assertBlockProperty(intake.west(), com.mrgregles.bsp_core.machine.StructurePartBlock.FORMED, false);
                    h.assertTrue(entity(h, intake.west(), com.mrgregles.bsp_core.link.LinkPartBlockEntity.class).controller() == null, "the frames are freed");
                    h.assertTrue(be.missingFrames().size() == 1 && be.missingFrames().get(0).equals(h.absolutePos(corner)), "the missing frame is named");
                    h.setBlock(corner, ModBlocks.ACCELERATOR_FRAME.get());
                    h.runAfterDelay(25, () -> {
                        h.assertTrue(be.isFormed(), "whole again");
                        h.setBlock(intake, Blocks.AIR);
                        h.runAfterDelay(3, () -> {
                            h.assertBlockProperty(intake.west(), com.mrgregles.bsp_core.machine.StructurePartBlock.FORMED, false);
                            h.assertTrue(entity(h, intake.west(), com.mrgregles.bsp_core.link.LinkPartBlockEntity.class).controller() == null, "breaking the intake frees the frames");
                            h.succeed();
                        });
                    });
                });
            });
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public void decelerator_forms_from_a_core_with_a_port_behind_it(GameTestHelper h) {
        BlockPos core = new BlockPos(8, 1, 8), behind = core.south(), inFront = core.north();
        h.setBlock(core, ModBlocks.DECELERATOR_CORE.get().defaultBlockState().setValue(com.mrgregles.bsp_core.link.DeceleratorCoreBlock.FACING, net.minecraft.core.Direction.NORTH));
        h.setBlock(inFront, ModBlocks.DECELERATOR_PORT.get()); // in front of the core, not behind it: does not count
        h.runAfterDelay(45, () -> {
            var be = entity(h, core, com.mrgregles.bsp_core.link.DeceleratorBlockEntity.class);
            h.assertTrue(!be.isFormed(), "a port in front does not form it");
            h.assertTrue(be.portPos().equals(h.absolutePos(behind)), "the port belongs behind the core");
            h.setBlock(behind, ModBlocks.DECELERATOR_PORT.get());
            h.runAfterDelay(25, () -> {
                h.assertTrue(be.isFormed(), "formed with the port behind");
                h.assertBlockProperty(core, com.mrgregles.bsp_core.machine.StructurePartBlock.FORMED, true);
                h.assertBlockProperty(behind, com.mrgregles.bsp_core.machine.StructurePartBlock.FORMED, true);
                h.assertBlockProperty(inFront, com.mrgregles.bsp_core.machine.StructurePartBlock.FORMED, false);
                h.assertTrue(h.absolutePos(core).equals(entity(h, behind, com.mrgregles.bsp_core.link.LinkPartBlockEntity.class).controller()), "the port knows its core");
                h.setBlock(behind, Blocks.AIR);
                h.runAfterDelay(3, () -> {
                    h.assertTrue(!be.isFormed(), "breaking the port un-forms it at once");
                    h.assertBlockProperty(core, com.mrgregles.bsp_core.machine.StructurePartBlock.FORMED, false);
                    h.setBlock(behind, ModBlocks.DECELERATOR_PORT.get());
                    h.runAfterDelay(25, () -> {
                        h.assertTrue(be.isFormed(), "whole again");
                        h.setBlock(core, Blocks.AIR);
                        h.runAfterDelay(3, () -> {
                            h.assertBlockProperty(behind, com.mrgregles.bsp_core.machine.StructurePartBlock.FORMED, false);
                            h.assertTrue(entity(h, behind, com.mrgregles.bsp_core.link.LinkPartBlockEntity.class).controller() == null, "breaking the core frees the port");
                            h.succeed();
                        });
                    });
                });
            });
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public void link_cards_bind_decelerators_to_an_accelerator_within_its_link_limit(GameTestHelper h) {
        BlockPos intake = new BlockPos(3, 1, 3);
        h.setBlock(intake, ModBlocks.ACCELERATOR_INTAKE.get().defaultBlockState().setValue(com.mrgregles.bsp_core.link.AcceleratorIntakeBlock.FACING, net.minecraft.core.Direction.NORTH));
        List<com.mrgregles.bsp_core.link.DeceleratorBlockEntity> cores = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            BlockPos core = new BlockPos(10, 1, 3 + i * 3);
            h.setBlock(core, ModBlocks.DECELERATOR_CORE.get().defaultBlockState().setValue(com.mrgregles.bsp_core.link.DeceleratorCoreBlock.FACING, net.minecraft.core.Direction.NORTH));
            h.setBlock(core.south(), ModBlocks.DECELERATOR_PORT.get());
            cores.add(entity(h, core, com.mrgregles.bsp_core.link.DeceleratorBlockEntity.class));
        }
        var a = entity(h, intake, com.mrgregles.bsp_core.link.AcceleratorBlockEntity.class);
        // writing a card through the block: a mock player right-clicks the intake with a blank card
        var player = h.makeMockPlayer();
        ItemStack hand = new ItemStack(com.mrgregles.bsp_core.registry.ModItems.LINK_CARD.get());
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, hand);
        BlockPos abs = h.absolutePos(intake);
        h.getBlockState(intake).use(h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(abs), net.minecraft.core.Direction.NORTH, abs, false));
        ItemStack card = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(com.mrgregles.bsp_core.link.LinkCardItem.isWritten(card), "the card in hand is written");
        h.assertTrue(a.here().equals(com.mrgregles.bsp_core.link.LinkCardItem.target(card)), "it names this intake in this world");
        h.assertTrue(com.mrgregles.bsp_core.link.LinkCardItem.target(new ItemStack(com.mrgregles.bsp_core.registry.ModItems.LINK_CARD.get())) == null, "a blank card names nothing");
        // one link without expanders
        ItemStack cards = card.copyWithCount(5);
        h.assertTrue(cores.get(0).plug(new ItemStack(com.mrgregles.bsp_core.registry.ModItems.LINK_CARD.get())) == com.mrgregles.bsp_core.link.Links.Plug.BLANK, "a blank card is refused");
        h.assertTrue(cores.get(0).plug(cards) == com.mrgregles.bsp_core.link.Links.Plug.LINKED && cards.getCount() == 4, "the first core links and takes one card");
        h.assertTrue(cores.get(0).isLinked() && a.links().size() == 1 && a.hasLink(cores.get(0).here()), "the accelerator lists the core");
        h.assertTrue(cores.get(0).plug(cards) == com.mrgregles.bsp_core.link.Links.Plug.ALREADY, "a linked core takes no second card");
        h.assertTrue(cores.get(1).plug(cards) == com.mrgregles.bsp_core.link.Links.Plug.FULL && cards.getCount() == 4 && !cores.get(1).isLinked(), "the second core finds no free link");
        // expanders raise the limit to three
        h.assertTrue(a.fitExpander() && a.maxLinks() == 2, "one expander: two links");
        h.assertTrue(cores.get(1).plug(cards) == com.mrgregles.bsp_core.link.Links.Plug.LINKED && a.links().size() == 2, "the second core links now");
        h.assertTrue(cores.get(2).plug(cards) == com.mrgregles.bsp_core.link.Links.Plug.FULL, "the third waits");
        h.assertTrue(a.fitExpander() && a.maxLinks() == 3 && !a.fitExpander(), "two expanders at most");
        h.assertTrue(cores.get(2).plug(cards) == com.mrgregles.bsp_core.link.Links.Plug.LINKED && a.links().size() == 3, "three links");
        h.assertTrue(cores.get(3).plug(cards) == com.mrgregles.bsp_core.link.Links.Plug.FULL && cards.getCount() == 2, "no fourth");
        h.assertTrue(!a.takeExpander(), "an expander whose link is in use cannot be taken out");
        // unlinking frees the slot and returns the card; breaking a core does the same
        ItemStack back = cores.get(1).unplug();
        h.assertTrue(com.mrgregles.bsp_core.link.LinkCardItem.isWritten(back) && !cores.get(1).isLinked() && a.links().size() == 2, "the card comes back and the slot is free");
        h.assertTrue(cores.get(3).plug(cards) == com.mrgregles.bsp_core.link.Links.Plug.LINKED && a.links().size() == 3, "the fourth core takes the free slot");
        h.setBlock(new BlockPos(10, 1, 12), Blocks.AIR);
        h.runAfterDelay(2, () -> {
            h.assertTrue(a.links().size() == 2 && !a.hasLink(cores.get(3).here()), "a broken core is forgotten");
            h.assertTrue(a.takeExpander() && a.maxLinks() == 2, "with a slot free an expander comes out");
            var saved = a.saveWithoutMetadata();
            h.assertTrue(saved.getList("Links", net.minecraft.nbt.Tag.TAG_COMPOUND).size() == 2 && saved.getInt("Expanders") == 1, "links and expanders are saved");
            h.assertTrue(com.mrgregles.bsp_core.link.LinkCardItem.isWritten(ItemStack.of(cores.get(0).saveWithoutMetadata().getCompound("Card"))), "a core saves its card");
            h.succeed();
        });
    }

    /** A fed, formed accelerator: Output 3 totem on an extractor at {@code start}, interface and an Illyrium cable east of it, the intake east of that facing east (the ring stands north to south). */
    private static com.mrgregles.bsp_core.link.AcceleratorBlockEntity fedAccelerator(GameTestHelper h, BlockPos start, ShatterTotemBlockEntity[] totemOut) {
        ShatterTotemBlockEntity totem = totemOnExtractor(h, start, 3);
        if (totemOut != null) {
            totemOut[0] = totem;
        }
        h.setBlock(start.east(), ModBlocks.PLASMA_INTERFACE.get());
        h.setBlock(start.east(2), cable(TotemCableBlock.Kind.ILLYRIUM));
        BlockPos intake = start.east(3);
        h.setBlock(intake, ModBlocks.ACCELERATOR_INTAKE.get().defaultBlockState().setValue(com.mrgregles.bsp_core.link.AcceleratorIntakeBlock.FACING, net.minecraft.core.Direction.EAST));
        for (int[] c : new int[][]{{-1, 0}, {1, 0}, {-1, 1}, {1, 1}, {-1, 2}, {0, 2}, {1, 2}}) {
            h.setBlock(intake.relative(net.minecraft.core.Direction.SOUTH, c[0]).above(c[1]), ModBlocks.ACCELERATOR_FRAME.get());
        }
        var a = entity(h, intake, com.mrgregles.bsp_core.link.AcceleratorBlockEntity.class);
        a.energy().fill(com.mrgregles.bsp_core.link.LinkEnergy.capacity());
        return a;
    }

    /** A decelerator (core facing north, port behind it) in {@code level} at {@code pos}, with a full RF pool and a card for {@code a}. */
    private static com.mrgregles.bsp_core.link.DeceleratorBlockEntity linkedDecelerator(GameTestHelper h, ServerLevel level, BlockPos pos, com.mrgregles.bsp_core.link.AcceleratorBlockEntity a) {
        // the test world persists between runs: a core left by a failed run would still hold its card
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos.south(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos, ModBlocks.DECELERATOR_CORE.get().defaultBlockState().setValue(com.mrgregles.bsp_core.link.DeceleratorCoreBlock.FACING, net.minecraft.core.Direction.NORTH), 3);
        level.setBlock(pos.south(), ModBlocks.DECELERATOR_PORT.get().defaultBlockState(), 3);
        var core = (com.mrgregles.bsp_core.link.DeceleratorBlockEntity) level.getBlockEntity(pos);
        h.assertTrue(core != null, "a core stands at " + pos);
        core.energy().fill(com.mrgregles.bsp_core.link.LinkEnergy.capacity());
        ItemStack card = new ItemStack(com.mrgregles.bsp_core.registry.ModItems.LINK_CARD.get());
        com.mrgregles.bsp_core.link.LinkCardItem.write(card, a.here());
        var r = core.plug(card);
        h.assertTrue(r == com.mrgregles.bsp_core.link.Links.Plug.LINKED, "linked: " + r);
        return core;
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public void accelerator_sends_plasma_to_the_nether_with_its_powers_when_both_ends_have_rf(GameTestHelper h) {
        var server = server(h);
        ShatterTotemBlockEntity[] totem = new ShatterTotemBlockEntity[1];
        BlockPos start = new BlockPos(1, 1, 3);
        var a = fedAccelerator(h, start, totem);
        totem[0].setUpgradeLevel(TotemUpgrades.Buff.OVERCLOCK, 2);
        totem[0].setUpgradeLevel(TotemUpgrades.Buff.ANCHOR, 1);
        ServerLevel nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
        h.assertTrue(nether != null, "the server has a Nether");
        BlockPos far = new BlockPos(1000, 100, 1000);
        nether.setChunkForced(far.getX() >> 4, far.getZ() >> 4, true);
        var core = linkedDecelerator(h, nether, far, a);
        int oc = TotemUpgrades.Buff.OVERCLOCK.ordinal(), cap = com.mrgregles.bsp_core.link.LinkEnergy.capacity();
        Runnable cleanUp = () -> {
            nether.setBlock(far, Blocks.AIR.defaultBlockState(), 3);
            nether.setBlock(far.south(), Blocks.AIR.defaultBlockState(), 3);
            nether.setChunkForced(far.getX() >> 4, far.getZ() >> 4, false);
        };
        h.runAfterDelay(100, () -> {
            h.assertTrue(a.isFormed(), "ring formed");
            h.assertTrue(a.inRate() > 0, "plasma arrives at the intake");
            h.assertTrue(core.isFormed(), "the Nether core is formed in its forced chunk");
            h.assertTrue(core.store().stored() > 0, "plasma arrived in the Nether: " + core.store().stored());
            h.assertTrue(core.store().offered()[oc] == 2, "Overclock II crossed with it");
            var totemAt = net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), h.absolutePos(start.above()));
            h.assertTrue(totemAt.equals(core.anchorTotem()), "the anchor totem is named with its world: " + core.anchorTotem());
            h.assertTrue(core.access().users.contains(totem[0].getOwner().get().uuid()), "the totem's access list came along");
            var report = a.report(core.here());
            h.assertTrue(report.state() == com.mrgregles.bsp_core.link.Links.State.DELIVERING && report.rate() > 0, "the link reports delivering: " + report);
            h.assertTrue(a.outRate() > 0 && a.energy().getEnergyStored() < cap && core.energy().getEnergyStored() < cap, "both ends paid RF");
            h.assertTrue(!a.mayLink(h.makeMockPlayer()), "a stranger may not write a card at a fed intake");
            long before = core.store().stored();
            core.energy().fill(0);
            h.runAfterDelay(45, () -> {
                h.assertTrue(a.report(core.here()).state() == com.mrgregles.bsp_core.link.Links.State.NO_RF_THERE, "no RF there stops the hop: " + a.report(core.here()));
                h.assertTrue(core.store().stored() == before, "nothing more arrived");
                core.energy().fill(cap);
                a.energy().fill(0);
                h.runAfterDelay(45, () -> {
                    h.assertTrue(a.report(core.here()).state() == com.mrgregles.bsp_core.link.Links.State.NO_RF_HERE, "no RF here stops it too: " + a.report(core.here()));
                    h.assertTrue(core.store().stored() == before, "still nothing more");
                    cleanUp.run();
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 400)
    public void accelerator_shares_its_flow_equally_between_live_links(GameTestHelper h) {
        var a = fedAccelerator(h, new BlockPos(1, 1, 3), null);
        h.assertTrue(a.fitExpander(), "two links");
        var core1 = linkedDecelerator(h, h.getLevel(), h.absolutePos(new BlockPos(9, 1, 1)), a);
        var core2 = linkedDecelerator(h, h.getLevel(), h.absolutePos(new BlockPos(9, 1, 6)), a);
        h.runAfterDelay(100, () -> {
            long s1 = core1.store().stored(), s2 = core2.store().stored();
            h.assertTrue(s1 > 0 && s2 > 0, "both received: " + s1 + " and " + s2);
            h.assertTrue(Math.abs(s1 - s2) <= Math.max(s1, s2) / 5, "about equal shares: " + s1 + " and " + s2);
            core2.energy().fill(0);
            h.runAfterDelay(45, () -> {
                long before = core1.store().stored();
                h.runAfterDelay(20, () -> {
                    long gained = core1.store().stored() - before;
                    h.assertTrue(a.report(core2.here()).state() == com.mrgregles.bsp_core.link.Links.State.NO_RF_THERE, "the unpowered link sleeps");
                    h.assertTrue(gained >= 9_000 && gained <= 11_000, "the whole flow (500 mB/t) goes to the other link now: " + gained);
                    h.assertTrue(a.report(core1.here()).rate() >= 450, "and its rate says so: " + a.report(core1.here()));
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 500)
    public void accelerator_wakes_a_sleeping_far_side_and_delivers(GameTestHelper h) {
        var server = server(h);
        var a = fedAccelerator(h, new BlockPos(1, 1, 3), null);
        ServerLevel nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
        BlockPos far = new BlockPos(2000, 100, 2000); // nothing keeps this chunk loaded
        var core = linkedDecelerator(h, nether, far, a);
        h.runAfterDelay(120, () -> {
            var before = a.report(core.here()).state();
            h.assertTrue(before == com.mrgregles.bsp_core.link.Links.State.ASLEEP || before == com.mrgregles.bsp_core.link.Links.State.DELIVERING || before == com.mrgregles.bsp_core.link.Links.State.UNFORMED_THERE,
                    "an unforced far side is asleep (or still loaded): " + before);
            a.wakeNow();
            h.runAfterDelay(65, () -> {
                nether.getChunk(far);
                var woken = (com.mrgregles.bsp_core.link.DeceleratorBlockEntity) nether.getBlockEntity(far);
                h.assertTrue(woken != null && woken.isFormed() && woken.store().stored() > 0, "the woken far side formed and received: " + (woken == null ? "gone" : woken.store().stored()));
                h.assertTrue(a.report(core.here()).state() == com.mrgregles.bsp_core.link.Links.State.DELIVERING, "and the link delivers while the wake ticket holds: " + a.report(core.here()));
                nether.setBlock(far, Blocks.AIR.defaultBlockState(), 3);
                nether.setBlock(far.south(), Blocks.AIR.defaultBlockState(), 3);
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 500)
    public void nether_projector_keeps_its_chunk_loaded_from_an_overworld_totem_through_a_link(GameTestHelper h) {
        var server = server(h);
        ShatterTotemBlockEntity[] totem = new ShatterTotemBlockEntity[1];
        BlockPos start = new BlockPos(1, 1, 3);
        var a = fedAccelerator(h, start, totem);
        totem[0].setOwner(new TotemOwner(UUID.randomUUID(), "Anchorite")); // its owner's only totem, so it is the main one with the full Anchor allowance
        totem[0].setUpgradeLevel(TotemUpgrades.Buff.ANCHOR, 2); // three chunks: its own and two to spare for projectors
        ServerLevel nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
        BlockPos far = new BlockPos(3000, 100, 3000), basePos = far.south(3), projPos = basePos.above();
        nether.setChunkForced(far.getX() >> 4, far.getZ() >> 4, true);
        for (int i = 0; i <= 4; i++) { // the world persists between runs
            nether.setBlock(far.south(i).above(), Blocks.AIR.defaultBlockState(), 3);
            nether.setBlock(far.south(i), Blocks.AIR.defaultBlockState(), 3);
        }
        var core = linkedDecelerator(h, nether, far, a);
        nether.setBlock(far.south(2), cable(TotemCableBlock.Kind.ILLYRIUM).defaultBlockState(), 3); // out of the port's back face
        nether.setBlock(basePos, ModBlocks.PROJECTOR_BASE.get().defaultBlockState(), 3);
        nether.setBlock(projPos, ModBlocks.TOTEM_PROJECTOR.get().defaultBlockState(), 3);
        var base = (com.mrgregles.bsp_core.plasma.ProjectorBaseBlockEntity) nether.getBlockEntity(basePos);
        var projector = (com.mrgregles.bsp_core.projector.TotemProjectorBlockEntity) nether.getBlockEntity(projPos);
        var totemAt = net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), h.absolutePos(start.above()));
        var projAt = net.minecraft.core.GlobalPos.of(nether.dimension(), projPos);
        Runnable cleanUp = () -> {
            for (int i = 0; i <= 4; i++) {
                nether.setBlock(far.south(i).above(), Blocks.AIR.defaultBlockState(), 3);
                nether.setBlock(far.south(i), Blocks.AIR.defaultBlockState(), 3);
            }
            nether.setChunkForced(far.getX() >> 4, far.getZ() >> 4, false);
        };
        h.runAfterDelay(120, () -> {
            var portBe = nether.getBlockEntity(far.south());
            h.assertTrue(core.outRate() > 0, "the port sends the plasma on into its cable: out " + core.outRate() + ", stored " + core.store().stored() + ", formed " + core.isFormed()
                    + ", in " + core.inRate() + ", port " + (portBe == null ? "none" : portBe.getClass().getSimpleName() + " controller " + ((com.mrgregles.bsp_core.link.LinkPartBlockEntity) portBe).controller())
                    + ", base tank " + base.tank() + ", link " + a.report(core.here()));
            h.assertTrue(base.tank() > 0, "the Projector Base fills");
            h.assertTrue(totemAt.equals(base.anchorTotem()), "the base names the Overworld totem with its world: " + base.anchorTotem());
            h.assertTrue(base.offered()[TotemUpgrades.Buff.ANCHOR.ordinal()] == 2, "Anchor II is offered in the Nether");
            int anchorIndex = java.util.Arrays.asList(com.mrgregles.bsp_core.projector.TotemProjectorBlockEntity.SENDABLE).indexOf(TotemUpgrades.Buff.ANCHOR);
            projector.toggle(anchorIndex); // the player picks Anchor on the projector
            h.runAfterDelay(60, () -> {
                var ledger = com.mrgregles.bsp_core.chunk.ChunkLedger.get(server);
                var entry = ledger.projectors.get(projAt);
                h.assertTrue(entry != null && entry.on && totemAt.equals(entry.totem), "the ledger lists the Nether projector under the Overworld totem: " + (entry == null ? "none" : entry.totem + " on=" + entry.on));
                h.assertTrue(!entry.chunks.isEmpty(), "and it holds its own chunk");
                cleanUp.run();
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public void accelerator_view_travels_whole_and_unlink_drops_the_card_at_the_far_end(GameTestHelper h) {
        BlockPos intake = new BlockPos(3, 1, 3), corePos = new BlockPos(10, 1, 3);
        h.setBlock(intake, ModBlocks.ACCELERATOR_INTAKE.get().defaultBlockState().setValue(com.mrgregles.bsp_core.link.AcceleratorIntakeBlock.FACING, net.minecraft.core.Direction.NORTH));
        var a = entity(h, intake, com.mrgregles.bsp_core.link.AcceleratorBlockEntity.class);
        h.assertTrue(a.fitExpander(), "an expander");
        var core = linkedDecelerator(h, h.getLevel(), h.absolutePos(corePos), a);
        core.energy().fill(0); // no RF there: the link reports it
        h.runAfterDelay(45, () -> {
            var view = a.view();
            var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            com.mrgregles.bsp_core.network.LinkViewPacket.encode(view, buf);
            var back = com.mrgregles.bsp_core.network.LinkViewPacket.decode(buf);
            h.assertTrue(back.pos().equals(h.absolutePos(intake)) && back.world().equals(h.getLevel().dimension().location().toString()), "position and world travel");
            h.assertTrue(!back.formed() && back.maxLinks() == 2 && back.expanders() == 1 && back.energyCap() == com.mrgregles.bsp_core.link.LinkEnergy.capacity(), "ring state, links and RF travel: " + back);
            h.assertTrue(back.links().size() == 1 && back.links().get(0).pos().equals(h.absolutePos(corePos)) && back.links().get(0).world().equals(back.world()), "the link travels with its world and position");
            h.assertTrue(back.links().get(0).state() == com.mrgregles.bsp_core.link.Links.State.NO_RF_THERE.ordinal(), "and its state: " + com.mrgregles.bsp_core.link.Links.State.values()[back.links().get(0).state()]);
            a.unlink(0);
            h.assertTrue(a.links().isEmpty() && !core.isLinked(), "unlink from the accelerator's side takes the card out of the core");
            h.assertItemEntityPresent(com.mrgregles.bsp_core.registry.ModItems.LINK_CARD.get(), corePos, 2.0);
            h.assertTrue(a.view().links().isEmpty(), "the view follows");
            h.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public void transfer_keeps_the_totem_in_transit_and_trusts_the_instance_that_arrives(GameTestHelper h) {
        var server = server(h);
        var ledger = com.mrgregles.bsp_core.data.TotemLedger.get(server);
        var player = h.makeMockPlayer();
        UUID who = player.getUUID();
        var totem = new ItemStack(com.mrgregles.bsp_core.registry.ModItems.SHATTER_TOTEM.get());
        new com.mrgregles.bsp_core.totem.TotemOwner(who, "Erin").applyTo(totem);
        com.mrgregles.bsp_core.totem.TotemIdentity.ensure(totem);
        UUID id = com.mrgregles.bsp_core.totem.TotemIdentity.id(totem), instance = com.mrgregles.bsp_core.totem.TotemIdentity.instance(totem);
        long now = server.overworld().getGameTime();
        ledger.seen(id, instance, who, com.mrgregles.bsp_core.data.TotemLedger.HELD, totem.getTag(), now);
        player.getInventory().setItem(0, totem);
        h.assertTrue(com.mrgregles.bsp_core.api.BspTransfer.beginTransfer(player) == 1, "the totem is taken for the transfer");
        var s = ledger.sighting(id);
        h.assertTrue(s != null && s.where() == com.mrgregles.bsp_core.data.TotemLedger.IN_TRANSIT && s.instance().equals(instance), "the ledger marks it in transit, same instance: " + s);
        h.assertTrue(ledger.lostHeld(now + 10_000, 60).isEmpty(), "a totem in transit is never lost, however long the proxy takes");
        h.assertTrue(com.mrgregles.bsp_core.api.BspTransfer.endTransfer(player), "the teleport is cancelled");
        s = ledger.sighting(id);
        h.assertTrue(s != null && s.where() == com.mrgregles.bsp_core.data.TotemLedger.HELD && s.instance().equals(instance) && who.equals(s.holder()), "back in hand: held again with the same instance");
        // the other server reissued it meanwhile: the copy that arrives through the database carries a new instance and is the real one
        UUID reissued = UUID.randomUUID();
        var arriving = totem.copy();
        com.mrgregles.bsp_core.totem.TotemIdentity.setInstance(arriving, reissued);
        player.getInventory().clearContent();
        com.mrgregles.bsp_core.api.BspTransfer.handBack(player, List.of(arriving), "message.bsp_core.transfer.arrived");
        h.assertTrue(reissued.equals(ledger.currentInstance(id)), "the arriving instance is current now");
        h.assertTrue(com.mrgregles.bsp_core.totem.TotemInventories.isTotem(player.getInventory().getItem(0)) && !com.mrgregles.bsp_core.totem.TotemIdentity.isSpent(player.getInventory().getItem(0)), "and the totem in hand is whole, not a husk");
        ledger.forget(id);
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void creative_player_destroys_a_spare_totem_instead_of_getting_it_back(GameTestHelper h) {
        var server = server(h);
        var ledger = com.mrgregles.bsp_core.data.TotemLedger.get(server);
        UUID id = UUID.randomUUID(), instance = UUID.randomUUID();
        long now = server.overworld().getGameTime();
        var creative = com.mrgregles.bsp_core.gametest.MockPlayers.serverPlayer(h, net.minecraft.world.level.GameType.CREATIVE);
        var survival = com.mrgregles.bsp_core.gametest.MockPlayers.serverPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);
        ledger.seen(id, instance, creative.getUUID(), com.mrgregles.bsp_core.data.TotemLedger.HELD, null, now);
        h.assertTrue(com.mrgregles.bsp_core.event.TotemRestrictionHandler.lost(creative, ledger, id, now + 100) == com.mrgregles.bsp_core.event.TotemRestrictionHandler.Lost.DESTROYED, "deleted in creative: destroyed");
        h.assertTrue(ledger.sighting(id) == null, "and forgotten");
        h.assertTrue(com.mrgregles.bsp_core.totem.TotemInventories.removeAll(creative).isEmpty(), "nothing was handed back");
        UUID id2 = UUID.randomUUID();
        ledger.seen(id2, UUID.randomUUID(), survival.getUUID(), com.mrgregles.bsp_core.data.TotemLedger.HELD, null, now);
        h.assertTrue(com.mrgregles.bsp_core.event.TotemRestrictionHandler.lost(survival, ledger, id2, now + 100) == com.mrgregles.bsp_core.event.TotemRestrictionHandler.Lost.REISSUED, "a survival player gets it back");
        h.assertTrue(ledger.sighting(id2) != null && com.mrgregles.bsp_core.totem.TotemInventories.removeAll(survival).size() == 1, "reissued into their hands");
        ledger.forget(id2);
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void logout_placement_puts_a_flying_players_totem_on_the_ground_below(GameTestHelper h) {
        BlockPos floor = new BlockPos(5, 1, 5), pool = new BlockPos(9, 1, 9);
        h.setBlock(floor, Blocks.STONE);
        h.setBlock(pool, Blocks.WATER);
        var level = h.getLevel();
        h.assertTrue(com.mrgregles.bsp_core.totem.TotemPlacer.grounded(level, h.absolutePos(floor.above(6))).equals(h.absolutePos(floor.above())), "six blocks up over stone: the block on the stone");
        h.assertTrue(com.mrgregles.bsp_core.totem.TotemPlacer.grounded(level, h.absolutePos(pool.above(5))).equals(h.absolutePos(pool.above())), "over water: the block on the surface, not the sea bed");
        h.assertTrue(com.mrgregles.bsp_core.totem.TotemPlacer.grounded(level, h.absolutePos(floor.above())).equals(h.absolutePos(floor.above())), "already on the ground: unchanged");
        // the logout target of a player hovering in the air is that ground spot
        var flyer = com.mrgregles.bsp_core.gametest.MockPlayers.serverPlayer(h, net.minecraft.world.level.GameType.CREATIVE);
        BlockPos hover = h.absolutePos(floor.above(6));
        flyer.setPos(hover.getX() + 0.5, hover.getY(), hover.getZ() + 0.5);
        flyer.getAbilities().flying = true;
        var target = com.mrgregles.bsp_core.totem.TotemPlacer.safeTargetFor(flyer);
        h.assertTrue(target.pos().equals(h.absolutePos(floor.above())), "a flying player's totem goes to the ground below them: " + target.pos());
        var totem = new ItemStack(com.mrgregles.bsp_core.registry.ModItems.SHATTER_TOTEM.get());
        new com.mrgregles.bsp_core.totem.TotemOwner(flyer.getUUID(), "Icarus").applyTo(totem);
        h.assertTrue(com.mrgregles.bsp_core.totem.TotemPlacer.place(target.level(), target.pos(), totem), "and is placed there");
        h.assertBlockPresent(com.mrgregles.bsp_core.registry.ModBlocks.SHATTER_TOTEM.get(), floor.above());
        h.setBlock(floor.above(), Blocks.AIR);
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void totem_placed_by_a_logout_comes_back_when_its_owner_returns(GameTestHelper h) {
        BlockPos floor = new BlockPos(4, 1, 4);
        h.setBlock(floor, Blocks.STONE);
        var player = com.mrgregles.bsp_core.gametest.MockPlayers.serverPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);
        BlockPos stand = h.absolutePos(floor.above());
        player.setPos(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        var totem = new ItemStack(com.mrgregles.bsp_core.registry.ModItems.SHATTER_TOTEM.get());
        new com.mrgregles.bsp_core.totem.TotemOwner(player.getUUID(), "Erin").applyTo(totem);
        com.mrgregles.bsp_core.totem.TotemIdentity.ensure(totem);
        UUID id = com.mrgregles.bsp_core.totem.TotemIdentity.id(totem);
        player.getInventory().setItem(0, totem);
        // a crash is a logout: the totem is placed where they stood and the placement remembered
        com.mrgregles.bsp_core.event.TotemLogoutHandler.placeOnLogout(player);
        h.assertTrue(com.mrgregles.bsp_core.totem.TotemInventories.removeAll(player).isEmpty(), "the totem left the inventory");
        h.assertBlockPresent(com.mrgregles.bsp_core.registry.ModBlocks.SHATTER_TOTEM.get(), floor.above());
        // they come back: the totem comes back
        var r = com.mrgregles.bsp_core.event.TotemLogoutHandler.returnPlaced(player);
        h.assertTrue(r.equals(List.of(com.mrgregles.bsp_core.event.TotemLogoutHandler.Returned.BACK)), "the totem came back: " + r);
        h.assertBlockNotPresent(com.mrgregles.bsp_core.registry.ModBlocks.SHATTER_TOTEM.get(), floor.above());
        var back = player.getInventory().getItem(0);
        h.assertTrue(com.mrgregles.bsp_core.totem.TotemInventories.isTotem(back) && id.equals(com.mrgregles.bsp_core.totem.TotemIdentity.id(back)), "the same totem is in hand");
        h.assertTrue(com.mrgregles.bsp_core.event.TotemLogoutHandler.returnPlaced(player).isEmpty(), "nothing is remembered twice");
        // someone took it while they were away: nothing comes back
        com.mrgregles.bsp_core.event.TotemLogoutHandler.placeOnLogout(player);
        h.setBlock(floor.above(), Blocks.AIR);
        h.assertTrue(com.mrgregles.bsp_core.event.TotemLogoutHandler.returnPlaced(player).equals(List.of(com.mrgregles.bsp_core.event.TotemLogoutHandler.Returned.GONE)), "a missing totem is reported gone");
        h.assertTrue(com.mrgregles.bsp_core.totem.TotemInventories.removeAll(player).isEmpty(), "and nothing was conjured");
        // a steal in progress: it stays in the world
        var totem2 = new ItemStack(com.mrgregles.bsp_core.registry.ModItems.SHATTER_TOTEM.get());
        new com.mrgregles.bsp_core.totem.TotemOwner(player.getUUID(), "Erin").applyTo(totem2);
        player.getInventory().setItem(0, totem2);
        com.mrgregles.bsp_core.event.TotemLogoutHandler.placeOnLogout(player);
        var thief = com.mrgregles.bsp_core.gametest.MockPlayers.serverPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);
        thief.setPos(stand.getX() + 1.5, stand.getY(), stand.getZ() + 0.5);
        entity(h, floor.above(), com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity.class).tryStartSteal(thief);
        boolean stealing = entity(h, floor.above(), com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity.class).getSteal().isPresent();
        var r3 = com.mrgregles.bsp_core.event.TotemLogoutHandler.returnPlaced(player);
        h.assertTrue(stealing ? r3.equals(List.of(com.mrgregles.bsp_core.event.TotemLogoutHandler.Returned.BEING_STOLEN)) : r3.equals(List.of(com.mrgregles.bsp_core.event.TotemLogoutHandler.Returned.BACK)),
                "mid-steal it stays, otherwise it comes back (steal started: " + stealing + "): " + r3);
        h.setBlock(floor.above(), Blocks.AIR);
        h.succeed();
    }
    // ------------------------------------------------------------------ teams

    /** A team name nobody used before: the test world persists between runs. */
    private static String teamName(String prefix) {
        String n = prefix + " " + Long.toString(System.nanoTime() % 1_000_000_000L, 36);
        return n.substring(0, Math.min(16, n.length()));
    }

    private static net.minecraft.server.level.ServerPlayer survivor(GameTestHelper h) {
        return com.mrgregles.bsp_core.gametest.MockPlayers.serverPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);
    }

    @GameTest(template = EMPTY)
    public void team_is_made_joined_by_invite_and_left(GameTestHelper h) {
        var server = server(h);
        var a = survivor(h);
        var b = survivor(h);
        var c = survivor(h);
        java.util.List<String> events = new java.util.ArrayList<>();
        java.util.function.Consumer<com.mrgregles.bsp_core.api.event.TeamEvent> listener = e -> events.add(e.getClass().getSimpleName());
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            String name = teamName("Night");
            var made = com.mrgregles.bsp_core.team.Teams.create(a, name, net.minecraft.ChatFormatting.AQUA);
            h.assertTrue(made.ok(), "a teamless player makes a team: " + made.message());
            h.assertFalse(com.mrgregles.bsp_core.team.Teams.create(a, teamName("Day"), net.minecraft.ChatFormatting.RED).ok(), "one team per player");
            var team = com.mrgregles.bsp_core.api.BspTeams.teamOf(server, a.getUUID()).orElseThrow();
            h.assertTrue(team.name().equals(name) && team.leader().equals(a.getUUID()) && team.members().size() == 1, "the maker leads it alone");
            h.assertFalse(com.mrgregles.bsp_core.team.Teams.accept(b).ok(), "nothing to accept yet");
            h.assertTrue(com.mrgregles.bsp_core.team.Teams.invite(a, b).ok(), "the leader invites");
            h.assertTrue(com.mrgregles.bsp_core.team.Teams.pending(b.getUUID()).isPresent(), "the invite waits");
            h.assertFalse(com.mrgregles.bsp_core.team.Teams.invite(a, b).ok(), "not twice while it waits");
            h.assertTrue(com.mrgregles.bsp_core.team.Teams.accept(b).ok(), "the invitee accepts");
            h.assertTrue(com.mrgregles.bsp_core.api.BspTeams.sameTeam(server, a.getUUID(), b.getUUID()), "teammates");
            h.assertFalse(com.mrgregles.bsp_core.api.BspTeams.sameTeam(server, a.getUUID(), c.getUUID()), "an outsider is not");
            h.assertFalse(com.mrgregles.bsp_core.api.BspTeams.sameTeam(server, a.getUUID(), a.getUUID()), "nobody is their own teammate");
            h.assertTrue(com.mrgregles.bsp_core.api.BspTeams.teammates(server, a.getUUID()).equals(java.util.List.of(b.getUUID())), "teammates lists the others");
            h.assertTrue(com.mrgregles.bsp_core.team.Teams.invite(b, c).ok(), "a member may invite too (membersInvite)");
            h.assertTrue(com.mrgregles.bsp_core.team.Teams.decline(c).ok() && com.mrgregles.bsp_core.team.Teams.pending(c.getUUID()).isEmpty(), "declining clears the invite");
            h.assertFalse(com.mrgregles.bsp_core.team.Teams.leave(a).ok(), "the leader hands over before leaving");
            h.assertTrue(com.mrgregles.bsp_core.team.Teams.leave(b).ok(), "a member leaves");
            h.assertFalse(com.mrgregles.bsp_core.api.BspTeams.sameTeam(server, a.getUUID(), b.getUUID()), "and is no teammate at once");
            h.assertTrue(com.mrgregles.bsp_core.team.Teams.leave(a).ok(), "the last member leaving disbands");
            h.assertTrue(com.mrgregles.bsp_core.api.BspTeams.teamOf(server, a.getUUID()).isEmpty(), "gone");
            h.assertTrue(events.equals(java.util.List.of("Created", "Joined", "Left", "Left", "Disbanded")), "events in order: " + events);
        } finally {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void team_names_seats_and_the_lead_follow_the_rules(GameTestHelper h) {
        var server = server(h);
        var a = survivor(h);
        h.assertFalse(com.mrgregles.bsp_core.team.Teams.create(a, "ab", net.minecraft.ChatFormatting.AQUA).ok(), "too short");
        h.assertFalse(com.mrgregles.bsp_core.team.Teams.create(a, "seventeen letters!", net.minecraft.ChatFormatting.AQUA).ok(), "too long");
        h.assertFalse(com.mrgregles.bsp_core.team.Teams.create(a, "bad<name>", net.minecraft.ChatFormatting.AQUA).ok(), "odd characters");
        String name = teamName("Dawn");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.create(a, "  " + name + "  ", net.minecraft.ChatFormatting.GOLD).ok(), "spaces round a name are trimmed");
        var b = survivor(h);
        h.assertFalse(com.mrgregles.bsp_core.team.Teams.create(b, name.toUpperCase(java.util.Locale.ROOT), net.minecraft.ChatFormatting.RED).ok(), "names are unique whatever the case");
        // five seats: the leader, then four invites hold the rest
        var others = new java.util.ArrayList<net.minecraft.server.level.ServerPlayer>();
        for (int i = 0; i < 4; i++) {
            var p = survivor(h);
            others.add(p);
            h.assertTrue(com.mrgregles.bsp_core.team.Teams.invite(a, p).ok(), "seat " + (i + 2));
        }
        var sixth = survivor(h);
        h.assertFalse(com.mrgregles.bsp_core.team.Teams.invite(a, sixth).ok(), "a waiting invite holds its seat: the sixth is refused");
        for (var p : others) {
            h.assertTrue(com.mrgregles.bsp_core.team.Teams.accept(p).ok(), "accepted");
        }
        h.assertFalse(com.mrgregles.bsp_core.team.Teams.invite(a, sixth).ok(), "full with five members");
        var second = others.get(0);
        h.assertFalse(com.mrgregles.bsp_core.team.Teams.kick(second, others.get(1).getUUID()).ok(), "a member cannot remove");
        h.assertFalse(com.mrgregles.bsp_core.team.Teams.kick(a, a.getUUID()).ok(), "the leader cannot remove themself");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.kick(a, others.get(1).getUUID()).ok(), "the leader removes a member");
        h.assertTrue(com.mrgregles.bsp_core.api.BspTeams.teamOf(server, others.get(1).getUUID()).isEmpty(), "who is out at once");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.transfer(a, second.getUUID()).ok(), "the lead is handed over");
        h.assertTrue(com.mrgregles.bsp_core.api.BspTeams.teamOf(server, a.getUUID()).orElseThrow().leader().equals(second.getUUID()), "the new leader");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.leave(a).ok(), "the old leader may leave now");
        h.assertFalse(com.mrgregles.bsp_core.team.Teams.rename(a, teamName("Dusk")).ok(), "an outsider changes nothing");
        String renamed = teamName("Dusk");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.rename(second, renamed).ok() && com.mrgregles.bsp_core.team.Teams.recolour(second, net.minecraft.ChatFormatting.GREEN).ok(), "the leader renames and recolours");
        var view = com.mrgregles.bsp_core.api.BspTeams.teamOf(server, second.getUUID()).orElseThrow();
        h.assertTrue(view.name().equals(renamed) && view.colour() == net.minecraft.ChatFormatting.GREEN, "as asked");
        var team = com.mrgregles.bsp_core.team.TeamLedger.get(server).team(view.id());
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.adminLead(server, team, others.get(2).getUUID()).ok() && team.leader.equals(others.get(2).getUUID()), "an admin moves the lead");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.adminKick(server, second.getUUID()).ok(), "an admin removes a member");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.disband(others.get(2)).ok(), "the leader disbands");
        for (var p : others) {
            h.assertTrue(com.mrgregles.bsp_core.api.BspTeams.teamOf(server, p.getUUID()).isEmpty(), "nobody is left in it");
        }
        h.assertTrue(com.mrgregles.bsp_core.team.TeamLedger.get(server).byName(renamed) == null, "and the name is free again");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void team_invite_runs_out(GameTestHelper h) {
        var server = server(h);
        var a = survivor(h);
        var b = survivor(h);
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.create(a, teamName("Owl"), net.minecraft.ChatFormatting.DARK_AQUA).ok(), "made");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.invite(a, b).ok(), "invited");
        com.mrgregles.bsp_core.team.Teams.expireInvites(server, System.currentTimeMillis() + 30_000L);
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.pending(b.getUUID()).isPresent(), "still waiting after thirty seconds");
        com.mrgregles.bsp_core.team.Teams.expireInvites(server, System.currentTimeMillis() + 61_000L);
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.pending(b.getUUID()).isEmpty(), "gone after a minute");
        h.assertFalse(com.mrgregles.bsp_core.team.Teams.accept(b).ok(), "too late to accept");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.invite(a, b).ok(), "the seat is free for a new invite");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.disband(a).ok(), "disbanding");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.pending(b.getUUID()).isEmpty(), "takes its invites with it");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void team_board_keeps_the_last_lines_and_a_pinned_note(GameTestHelper h) {
        var server = server(h);
        var a = survivor(h);
        var b = survivor(h);
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.create(a, teamName("Fox"), net.minecraft.ChatFormatting.LIGHT_PURPLE).ok(), "made");
        for (int i = 1; i <= 55; i++) {
            h.assertTrue(com.mrgregles.bsp_core.team.Teams.post(a, "line " + i).ok(), "posted " + i);
        }
        var team = com.mrgregles.bsp_core.team.Teams.teamOf(server, a.getUUID());
        h.assertTrue(team.messages().size() == 50 && team.messages().get(0).text().equals("line 6") && team.messages().get(49).text().equals("line 55"), "the board keeps the last fifty");
        h.assertTrue(team.unread(a.getUUID()) == 0, "your own lines are read");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.invite(a, b).ok() && com.mrgregles.bsp_core.team.Teams.accept(b).ok(), "b joins");
        h.assertTrue(team.unread(b.getUUID()) == 0, "what was there before you joined is not new");
        com.mrgregles.bsp_core.team.Teams.post(a, "after");
        com.mrgregles.bsp_core.team.Teams.post(a, "b joined");
        h.assertTrue(team.unread(b.getUUID()) == 2, "two new lines for b");
        com.mrgregles.bsp_core.team.Teams.markRead(server, b.getUUID());
        h.assertTrue(team.unread(b.getUUID()) == 0, "read once the board was opened");
        h.assertFalse(com.mrgregles.bsp_core.team.Teams.post(b, "   ").ok(), "an empty line is refused");
        h.assertFalse(com.mrgregles.bsp_core.team.Teams.pin(b, "mine").ok(), "only the leader pins");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.pin(a, "Meet at the hub").ok() && team.pinned() != null && team.pinned().text().equals("Meet at the hub"), "the leader pins a note");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.pin(a, "").ok() && team.pinned() == null, "an empty note takes it down");
        // the board survives a save and load
        var copy = com.mrgregles.bsp_core.team.Team.load(team.save());
        h.assertTrue(copy.messages().size() == 50 && copy.members().size() == 2 && copy.unread(b.getUUID()) == 0 && copy.colour == net.minecraft.ChatFormatting.LIGHT_PURPLE, "saved and loaded whole");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.disband(a).ok(), "disbanding");
        h.succeed();
    }
    @GameTest(template = EMPTY)
    public void teammates_share_access_to_each_others_totems(GameTestHelper h) {
        var server = server(h);
        var a = survivor(h);
        var b = survivor(h);
        var c = survivor(h);
        BlockPos pos = new BlockPos(6, 1, 6);
        h.setBlock(pos, com.mrgregles.bsp_core.registry.ModBlocks.SHATTER_TOTEM.get());
        var totem = entity(h, pos, com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity.class);
        totem.setOwner(new com.mrgregles.bsp_core.totem.TotemOwner(a.getUUID(), a.getGameProfile().getName()));
        BlockPos at = h.absolutePos(pos);
        b.setPos(at.getX() + 2.5, at.getY(), at.getZ() + 0.5);
        int[] flags = {com.mrgregles.bsp_core.totem.TotemAccess.UPGRADES, com.mrgregles.bsp_core.totem.TotemAccess.ALARM, com.mrgregles.bsp_core.totem.TotemAccess.WARD, com.mrgregles.bsp_core.totem.TotemAccess.MACHINES};
        for (int f : flags) {
            h.assertFalse(totem.hasAccess(b.getUUID(), f), "a stranger has no flag " + f);
        }
        h.assertFalse(totem.mayUpgrade(b), "a stranger may not upgrade");
        h.assertFalse(com.mrgregles.bsp_core.plasma.PlasmaAccess.of(totem).users.contains(b.getUUID()), "nor use the machines");
        h.assertTrue(com.mrgregles.bsp_core.compass.TotemCompassItem.isRival(server, b.getUUID(), a.getUUID()), "the compass counts them as rivals");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.create(a, teamName("Keep"), net.minecraft.ChatFormatting.BLUE).ok(), "a makes a team");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.invite(a, b).ok() && com.mrgregles.bsp_core.team.Teams.accept(b).ok(), "b joins");
        h.assertTrue(totem.isTeammate(b.getUUID()) && !totem.isTeammate(a.getUUID()) && !totem.isTeammate(c.getUUID()), "b is the owner's teammate, the owner and c are not");
        for (int f : flags) {
            h.assertTrue(totem.hasAccess(b.getUUID(), f), "a teammate has flag " + f);
        }
        h.assertTrue(totem.mayUpgrade(b), "a teammate may upgrade");
        var access = com.mrgregles.bsp_core.plasma.PlasmaAccess.of(totem);
        h.assertTrue(access.editors.contains(b.getUUID()) && access.users.contains(b.getUUID()) && access.alarmSafe.contains(b.getUUID()) && access.wardSafe.contains(b.getUUID()), "and reaches the machines, alarm and ward");
        h.assertFalse(access.users.contains(c.getUUID()), "an outsider does not");
        h.assertTrue(totem.getUpdateTag().getList("Teammates", net.minecraft.nbt.Tag.TAG_INT_ARRAY).size() == 1, "the client is told who the teammates are");
        totem.tryStartSteal(b);
        h.assertTrue(totem.getSteal().isEmpty(), "a teammate cannot start a steal");
        h.assertFalse(com.mrgregles.bsp_core.compass.TotemCompassItem.isRival(server, b.getUUID(), a.getUUID()), "the compass ignores a teammate's totem");
        h.assertTrue(com.mrgregles.bsp_core.compass.TotemCompassItem.isRival(server, c.getUUID(), a.getUUID()), "an outsider is still a rival");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.leave(b).ok(), "b leaves");
        h.assertFalse(totem.hasAccess(b.getUUID(), com.mrgregles.bsp_core.totem.TotemAccess.UPGRADES) || totem.mayUpgrade(b), "and the access is gone at once");
        h.assertTrue(totem.getUpdateTag().getList("Teammates", net.minecraft.nbt.Tag.TAG_INT_ARRAY).isEmpty(), "the client learns that too");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.disband(a).ok(), "disbanding");
        h.setBlock(pos, Blocks.AIR);
        h.succeed();
    }
    @GameTest(template = EMPTY)
    public void team_screen_view_and_actions_round_trip(GameTestHelper h) {
        var server = server(h);
        var a = survivor(h);
        var b = survivor(h);
        var A = com.mrgregles.bsp_core.network.TeamActionPacket.Action.class;
        var none = com.mrgregles.bsp_core.network.TeamViewPacket.of(a, true);
        h.assertTrue(none.open() && !none.hasTeam() && none.enabled() && none.maxMembers() == 5, "no team yet: an empty view that opens the screen");
        String name = teamName("Lynx");
        com.mrgregles.bsp_core.team.TeamActions.apply(a, com.mrgregles.bsp_core.network.TeamActionPacket.text(com.mrgregles.bsp_core.network.TeamActionPacket.Action.CREATE, name, net.minecraft.ChatFormatting.GOLD.getId()));
        var va = com.mrgregles.bsp_core.network.TeamViewPacket.of(a, false);
        h.assertTrue(va.hasTeam() && va.name().equals(name) && va.colour() == net.minecraft.ChatFormatting.GOLD.getId() && va.leader() && va.members().size() == 1 && va.members().get(0).leader(), "CREATE made the team as asked: " + va);
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.invite(a, b).ok(), "invited");
        va = com.mrgregles.bsp_core.network.TeamViewPacket.of(a, false);
        h.assertTrue(va.candidates().size() == 1 && va.candidates().get(0).id().equals(b.getUUID()) && va.candidates().get(0).waitSeconds() > 0 && va.candidates().get(0).waitSeconds() <= 60, "the invite view shows b waiting: " + va.candidates());
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.accept(b).ok(), "b joins");
        com.mrgregles.bsp_core.team.TeamActions.apply(a, com.mrgregles.bsp_core.network.TeamActionPacket.text(com.mrgregles.bsp_core.network.TeamActionPacket.Action.POST, "first", 0));
        com.mrgregles.bsp_core.team.TeamActions.apply(a, com.mrgregles.bsp_core.network.TeamActionPacket.text(com.mrgregles.bsp_core.network.TeamActionPacket.Action.POST, "second", 0));
        var vb = com.mrgregles.bsp_core.network.TeamViewPacket.of(b, false);
        h.assertTrue(vb.hasTeam() && !vb.leader() && vb.members().size() == 2 && vb.unread() == 2 && vb.lines().size() == 2 && vb.lines().get(0).fresh() && vb.lines().get(1).text().equals("second"), "b sees two new lines: " + vb);
        h.assertTrue(vb.members().stream().allMatch(m -> m.state() == com.mrgregles.bsp_core.network.TeamViewPacket.OFFLINE && m.totems() == 0), "mock players count as offline with no totems");
        com.mrgregles.bsp_core.team.TeamActions.apply(b, com.mrgregles.bsp_core.network.TeamActionPacket.of(com.mrgregles.bsp_core.network.TeamActionPacket.Action.MARK_READ));
        vb = com.mrgregles.bsp_core.network.TeamViewPacket.of(b, false);
        h.assertTrue(vb.unread() == 0 && vb.lines().stream().noneMatch(com.mrgregles.bsp_core.network.TeamViewPacket.Line::fresh), "read after MARK_READ");
        com.mrgregles.bsp_core.team.TeamActions.apply(a, com.mrgregles.bsp_core.network.TeamActionPacket.text(com.mrgregles.bsp_core.network.TeamActionPacket.Action.PIN, "Meet at the hub", 0));
        String renamed = teamName("Puma");
        com.mrgregles.bsp_core.team.TeamActions.apply(a, com.mrgregles.bsp_core.network.TeamActionPacket.text(com.mrgregles.bsp_core.network.TeamActionPacket.Action.RENAME, renamed, 0));
        com.mrgregles.bsp_core.team.TeamActions.apply(a, com.mrgregles.bsp_core.network.TeamActionPacket.text(com.mrgregles.bsp_core.network.TeamActionPacket.Action.RECOLOUR, "", net.minecraft.ChatFormatting.RED.getId()));
        com.mrgregles.bsp_core.team.TeamActions.apply(a, com.mrgregles.bsp_core.network.TeamActionPacket.of(com.mrgregles.bsp_core.network.TeamActionPacket.Action.CHAT_TOGGLE));
        va = com.mrgregles.bsp_core.network.TeamViewPacket.of(a, false);
        h.assertTrue(va.pinText().equals("Meet at the hub") && va.pinBy().equals(a.getGameProfile().getName()) && va.name().equals(renamed) && va.colour() == net.minecraft.ChatFormatting.RED.getId() && va.chatOn(), "pin, rename, recolour and the chat toggle show in the view: " + va);
        com.mrgregles.bsp_core.team.TeamActions.apply(a, com.mrgregles.bsp_core.network.TeamActionPacket.of(com.mrgregles.bsp_core.network.TeamActionPacket.Action.CHAT_TOGGLE));
        h.assertFalse(com.mrgregles.bsp_core.team.Teams.isChatOn(a.getUUID()), "and toggles back");
        // the view survives the wire
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        com.mrgregles.bsp_core.network.TeamViewPacket.encode(va, buf);
        h.assertTrue(com.mrgregles.bsp_core.network.TeamViewPacket.decode(buf).equals(va), "encode and decode give the same view");
        com.mrgregles.bsp_core.team.TeamActions.apply(a, com.mrgregles.bsp_core.network.TeamActionPacket.target(com.mrgregles.bsp_core.network.TeamActionPacket.Action.TRANSFER, b.getUUID()));
        h.assertTrue(com.mrgregles.bsp_core.network.TeamViewPacket.of(b, false).leader() && !com.mrgregles.bsp_core.network.TeamViewPacket.of(a, false).leader(), "the lead moved to b");
        com.mrgregles.bsp_core.team.TeamActions.apply(a, com.mrgregles.bsp_core.network.TeamActionPacket.target(com.mrgregles.bsp_core.network.TeamActionPacket.Action.KICK, b.getUUID()));
        h.assertTrue(com.mrgregles.bsp_core.network.TeamViewPacket.of(a, false).members().size() == 2, "a member cannot remove the leader");
        com.mrgregles.bsp_core.team.TeamActions.apply(b, com.mrgregles.bsp_core.network.TeamActionPacket.target(com.mrgregles.bsp_core.network.TeamActionPacket.Action.KICK, a.getUUID()));
        h.assertFalse(com.mrgregles.bsp_core.network.TeamViewPacket.of(a, false).hasTeam(), "the leader removed a");
        com.mrgregles.bsp_core.team.TeamActions.apply(b, com.mrgregles.bsp_core.network.TeamActionPacket.of(com.mrgregles.bsp_core.network.TeamActionPacket.Action.DISBAND));
        h.assertFalse(com.mrgregles.bsp_core.network.TeamViewPacket.of(b, false).hasTeam(), "and disbanded");
        h.succeed();
    }
    @GameTest(template = EMPTY)
    public void team_invite_arrives_as_a_card_and_a_clickable_line(GameTestHelper h) {
        var server = server(h);
        var a = survivor(h);
        var b = survivor(h);
        var c = survivor(h);
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.create(a, teamName("Hawk"), net.minecraft.ChatFormatting.GREEN).ok(), "made");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.invite(a, b).ok(), "invited (the card, the chime and the chat line went to b)");
        var invite = com.mrgregles.bsp_core.team.Teams.pending(b.getUUID()).orElseThrow();
        h.assertTrue(invite.id() > 0 && invite.inviter().equals(a.getUUID()), "the invite carries an id for the card");
        var team = com.mrgregles.bsp_core.team.Teams.teamOf(server, a.getUUID());
        var line = com.mrgregles.bsp_core.team.TeamPrompts.chatLine(invite, team);
        java.util.List<String> clicks = new java.util.ArrayList<>();
        line.visit((style, text) -> {
            if (style.getClickEvent() != null) {
                clicks.add(style.getClickEvent().getValue());
            }
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        h.assertTrue(clicks.equals(java.util.List.of(com.mrgregles.bsp_core.team.TeamPrompts.ACCEPT_COMMAND, com.mrgregles.bsp_core.team.TeamPrompts.DECLINE_COMMAND)), "Accept and Decline click the two commands: " + clicks);
        // the card packet survives the wire, and a clear is a clear
        var packet = new com.mrgregles.bsp_core.network.TeamPromptPacket(invite.id(), false, a.getUUID(), "A", team.name, team.colour.getId(), 1, 5, "A", 60);
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        com.mrgregles.bsp_core.network.TeamPromptPacket.encode(packet, buf);
        h.assertTrue(com.mrgregles.bsp_core.network.TeamPromptPacket.decode(buf).equals(packet), "encode and decode agree");
        h.assertTrue(com.mrgregles.bsp_core.network.TeamPromptPacket.clear(7).clear() && com.mrgregles.bsp_core.network.TeamPromptPacket.clear(7).id() == 7, "a clear names its card");
        // the chat line's clicks go through the player command
        server.getCommands().performPrefixedCommand(b.createCommandSourceStack(), "bspteam accept");
        h.assertTrue(com.mrgregles.bsp_core.api.BspTeams.sameTeam(server, a.getUUID(), b.getUUID()), "clicking Accept joined b");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.invite(a, c).ok(), "c invited");
        server.getCommands().performPrefixedCommand(c.createCommandSourceStack(), "bspteam decline");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.pending(c.getUUID()).isEmpty() && com.mrgregles.bsp_core.api.BspTeams.teamOf(server, c.getUUID()).isEmpty(), "clicking Decline cleared the invite");
        server.getCommands().performPrefixedCommand(c.createCommandSourceStack(), "bspteam accept");
        h.assertTrue(com.mrgregles.bsp_core.api.BspTeams.teamOf(server, c.getUUID()).isEmpty(), "a stale click joins nothing");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.disband(a).ok(), "disbanding");
        h.succeed();
    }
    @GameTest(template = EMPTY)
    public void team_chat_goes_to_the_board_when_switched_on(GameTestHelper h) {
        var server = server(h);
        var a = survivor(h);
        var b = survivor(h);
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.create(a, teamName("Wren"), net.minecraft.ChatFormatting.YELLOW).ok(), "made");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.invite(a, b).ok() && com.mrgregles.bsp_core.team.Teams.accept(b).ok(), "b joins");
        var team = com.mrgregles.bsp_core.team.Teams.teamOf(server, a.getUUID());
        // channel off: a chat line goes to everyone and the board stays empty
        var everyone = new net.minecraftforge.event.ServerChatEvent(a, "hello all", net.minecraft.network.chat.Component.literal("hello all"));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(everyone);
        h.assertFalse(everyone.isCanceled(), "with the channel off the line is not taken");
        h.assertTrue(team.messages().isEmpty(), "and nothing is on the board");
        // channel on: the line is taken and lands on the board
        h.assertTrue(com.mrgregles.bsp_core.team.TeamChat.toggle(a), "TEAM CHAT on");
        var toTeam = new net.minecraftforge.event.ServerChatEvent(a, "hello team", net.minecraft.network.chat.Component.literal("hello team"));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(toTeam);
        h.assertTrue(toTeam.isCanceled(), "with the channel on the line is taken from everyone");
        h.assertTrue(team.messages().size() == 1 && team.messages().get(0).text().equals("hello team") && team.messages().get(0).author().equals(a.getUUID()), "and is on the board");
        h.assertTrue(team.unread(b.getUUID()) == 1, "new to b");
        // /t sends one line without switching
        h.assertFalse(com.mrgregles.bsp_core.team.TeamChat.toggle(a), "TEAM CHAT off again");
        server.getCommands().performPrefixedCommand(b.createCommandSourceStack(), "t on my way");
        h.assertTrue(team.messages().size() == 2 && team.messages().get(1).text().equals("on my way") && team.messages().get(1).author().equals(b.getUUID()), "/t posted b's line");
        h.assertFalse(com.mrgregles.bsp_core.team.Teams.isChatOn(b.getUUID()), "without switching b's channel");
        // the login summary: new lines and the pinned note (posting your own line counts the board as read up to it)
        h.assertTrue(team.unread(b.getUUID()) == 0, "b's own line marked the board read for b");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.post(a, "one more").ok() && com.mrgregles.bsp_core.team.Teams.pin(a, "Bring lava").ok(), "a posts again and pins");
        var summary = com.mrgregles.bsp_core.team.TeamChat.loginSummary(team, b.getUUID());
        h.assertTrue(summary.size() == 2, "b hears two things: " + summary);
        var first = (net.minecraft.network.chat.contents.TranslatableContents) summary.get(0).getContents();
        var second = (net.minecraft.network.chat.contents.TranslatableContents) summary.get(1).getContents();
        h.assertTrue(first.getKey().equals("message.bsp_core.team.unread") && first.getArgs()[0].equals(1), "one new line: " + first.getKey() + " " + java.util.Arrays.toString(first.getArgs()));
        h.assertTrue(second.getKey().equals("message.bsp_core.team.pinned_login") && second.getArgs()[1].equals("Bring lava"), "and the note");
        com.mrgregles.bsp_core.team.Teams.markRead(server, b.getUUID());
        h.assertTrue(com.mrgregles.bsp_core.team.TeamChat.loginSummary(team, b.getUUID()).size() == 1, "read: only the note");
        // leaving switches the channel off
        h.assertTrue(com.mrgregles.bsp_core.team.TeamChat.toggle(b) && com.mrgregles.bsp_core.team.Teams.leave(b).ok() && !com.mrgregles.bsp_core.team.Teams.isChatOn(b.getUUID()), "leaving turns TEAM CHAT off");
        var afterwards = new net.minecraftforge.event.ServerChatEvent(b, "free again", net.minecraft.network.chat.Component.literal("free again"));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(afterwards);
        h.assertFalse(afterwards.isCanceled(), "and b's chat reaches everyone");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.disband(a).ok(), "disbanding");
        h.succeed();
    }
    @GameTest(template = EMPTY)
    public void team_read_from_the_network_replaces_the_local_copy(GameTestHelper h) {
        var server = server(h);
        var a = survivor(h);
        var b = survivor(h);
        var c = survivor(h);
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.create(a, teamName("Elk"), net.minecraft.ChatFormatting.DARK_GREEN).ok(), "made locally");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.invite(a, b).ok() && com.mrgregles.bsp_core.team.Teams.accept(b).ok(), "b joins locally");
        var local = com.mrgregles.bsp_core.team.Teams.teamOf(server, a.getUUID());
        com.mrgregles.bsp_core.team.Teams.post(a, "before");
        // what another server would have written: c joined there, a line was posted there, the name changed there
        var fresh = com.mrgregles.bsp_core.team.Team.load(local.save());
        fresh.members().put(c.getUUID(), new com.mrgregles.bsp_core.team.Team.Member(c.getGameProfile().getName(), System.currentTimeMillis(), fresh.messageSeq()));
        fresh.members().get(c.getUUID()).onlineServer = "survival-2";
        fresh.messages().add(new com.mrgregles.bsp_core.team.Team.Message(fresh.messageSeq() + 1, System.currentTimeMillis(), c.getUUID(), c.getGameProfile().getName(), "hello from survival-2"));
        fresh.setMessageSeq(fresh.messageSeq() + 1);
        String renamed = teamName("Moose");
        fresh.name = renamed;
        com.mrgregles.bsp_core.team.TeamSync.apply(server, java.util.List.of(fresh), fresh.id);
        var now = com.mrgregles.bsp_core.team.Teams.teamOf(server, a.getUUID());
        h.assertTrue(now == fresh, "the fresh copy is the team now");
        h.assertTrue(now.name.equals(renamed) && now.messages().size() == 2 && now.messages().get(1).text().equals("hello from survival-2"), "with its name and the new line");
        h.assertTrue(com.mrgregles.bsp_core.api.BspTeams.sameTeam(server, a.getUUID(), c.getUUID()), "c, who joined elsewhere, is a teammate here");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.teamOf(server, c.getUUID()) == now, "and is found by their id");
        h.assertTrue(now.unread(b.getUUID()) == 2, "b has the local line and the remote line to read");
        var view = com.mrgregles.bsp_core.network.TeamViewPacket.of(a, false);
        var cRow = view.members().stream().filter(m -> m.id().equals(c.getUUID())).findFirst().orElseThrow();
        h.assertTrue(cRow.state() == com.mrgregles.bsp_core.network.TeamViewPacket.ELSEWHERE && cRow.where().equals("survival-2"), "the screen shows c on survival-2: " + cRow);
        // c leaves elsewhere
        var later = com.mrgregles.bsp_core.team.Team.load(fresh.save());
        later.members().remove(c.getUUID());
        com.mrgregles.bsp_core.team.TeamSync.apply(server, java.util.List.of(later), later.id);
        h.assertFalse(com.mrgregles.bsp_core.api.BspTeams.sameTeam(server, a.getUUID(), c.getUUID()), "c is out here too");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.teamOf(server, c.getUUID()) == null, "and in no team");
        // disbanded elsewhere
        com.mrgregles.bsp_core.team.TeamSync.apply(server, java.util.List.of(), later.id);
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.teamOf(server, a.getUUID()) == null && com.mrgregles.bsp_core.team.Teams.teamOf(server, b.getUUID()) == null, "gone here too");
        h.assertTrue(com.mrgregles.bsp_core.team.TeamLedger.get(server).byName(renamed) == null, "and the name is free");
        h.succeed();
    }
    @GameTest(template = EMPTY)
    public void team_teleport_is_asked_of_the_teleport_mod(GameTestHelper h) {
        var server = server(h);
        var a = survivor(h);
        var b = survivor(h);
        var c = survivor(h);
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.create(a, teamName("Stag"), net.minecraft.ChatFormatting.GOLD).ok(), "made");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.invite(a, b).ok() && com.mrgregles.bsp_core.team.Teams.accept(b).ok(), "b joins");
        var team = com.mrgregles.bsp_core.team.Teams.teamOf(server, a.getUUID());
        var members = com.mrgregles.bsp_core.api.BspTeams.members(server, team.id);
        h.assertTrue(members.size() == 2 && members.get(0).uuid().equals(a.getUUID()) && members.get(0).leader() && !members.get(0).onlineHere() && members.get(0).server().isEmpty(), "the members api lists them in order with where they are: " + members);
        // nobody handles team teleports yet: no key, and a request is refused
        String before = com.mrgregles.bsp_core.api.BspTeams.teleporter();
        com.mrgregles.bsp_core.api.BspTeams.teleportsHandledBy("");
        h.assertFalse(com.mrgregles.bsp_core.network.TeamViewPacket.of(a, false).canTeleport(), "no TELEPORT keys without a teleport mod");
        team.members().get(b.getUUID()).onlineServer = "survival-2";
        h.assertFalse(com.mrgregles.bsp_core.team.Teams.requestTeleport(a, b.getUUID()).ok(), "refused without a teleport mod");
        // a teleport mod declares itself and handles the event
        com.mrgregles.bsp_core.api.BspTeams.teleportsHandledBy("bsp_teleport");
        h.assertTrue(com.mrgregles.bsp_core.network.TeamViewPacket.of(a, false).canTeleport(), "keys appear once a mod handles the request");
        java.util.List<com.mrgregles.bsp_core.api.event.TeamEvent.TeleportRequest> seen = new java.util.ArrayList<>();
        java.util.function.Consumer<com.mrgregles.bsp_core.api.event.TeamEvent.TeleportRequest> handler = e -> { seen.add(e); e.setHandled(); };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(handler);
        try {
            h.assertTrue(com.mrgregles.bsp_core.team.Teams.requestTeleport(a, b.getUUID()).ok(), "the request is handled");
            h.assertTrue(seen.size() == 1 && seen.get(0).getPlayer() == a && seen.get(0).getTarget().equals(b.getUUID()) && seen.get(0).getTargetServer().equals("survival-2") && seen.get(0).getTeamId().equals(team.id), "with the player, the target and the target's server: " + seen);
            h.assertFalse(com.mrgregles.bsp_core.team.Teams.requestTeleport(a, a.getUUID()).ok(), "not to yourself");
            h.assertFalse(com.mrgregles.bsp_core.team.Teams.requestTeleport(a, c.getUUID()).ok(), "not to an outsider");
            team.members().get(b.getUUID()).onlineServer = "";
            h.assertFalse(com.mrgregles.bsp_core.team.Teams.requestTeleport(a, b.getUUID()).ok(), "not to a teammate who is offline everywhere");
            h.assertTrue(seen.size() == 1, "the refused ones never reached the teleport mod");
        } finally {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(handler);
            com.mrgregles.bsp_core.api.BspTeams.teleportsHandledBy(before);
        }
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.disband(a).ok(), "disbanding");
        h.succeed();
    }
    @GameTest(template = EMPTY)
    public void proxy_relay_sends_public_chat_only_when_switched_on(GameTestHelper h) {
        var server = server(h);
        var a = survivor(h);
        boolean before = com.mrgregles.bsp_core.BSPConfig.PROXY_RELAY_CHAT.get();
        try {
            // off by default: nothing goes
            com.mrgregles.bsp_core.BSPConfig.PROXY_RELAY_CHAT.set(false);
            com.mrgregles.bsp_core.proxy.ProxyRelay.forget();
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.ServerChatEvent(a, "quiet", net.minecraft.network.chat.Component.literal("quiet")));
            h.assertTrue(com.mrgregles.bsp_core.proxy.ProxyRelay.last() == null, "with relayChat off nothing is sent");
            // on: a public line goes with every field
            com.mrgregles.bsp_core.BSPConfig.PROXY_RELAY_CHAT.set(true);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.ServerChatEvent(a, "hello all", net.minecraft.network.chat.Component.literal("hello all")));
            byte[] bytes = com.mrgregles.bsp_core.proxy.ProxyRelay.last();
            h.assertTrue(bytes != null, "a public line is relayed");
            var r = com.mrgregles.bsp_core.proxy.ProxyRelay.decode(bytes);
            h.assertTrue(r.version() == 1 && r.kind().equals("chat") && r.sender().equals(a.getUUID()) && r.name().equals(a.getGameProfile().getName()) && r.text().equals("hello all"), "version, kind, sender, name and text: " + r);
            var line = net.minecraft.network.chat.Component.Serializer.fromJson(r.json());
            h.assertTrue(line != null && line.getString().equals("<" + a.getGameProfile().getName() + "> hello all"), "the JSON is the decorated line: " + (line == null ? null : line.getString()));
            h.assertTrue(r.server().equals(com.mrgregles.bsp_core.BSPConfig.STORAGE_SERVER_ID.get()), "and names this server");
            // a listener's change is what goes (the chat tag, here)
            com.mrgregles.bsp_core.proxy.ProxyRelay.forget();
            var changed = new net.minecraftforge.event.ServerChatEvent(a, "tagged", net.minecraft.network.chat.Component.literal("tagged"));
            java.util.function.Consumer<net.minecraftforge.event.ServerChatEvent> prefixer = e -> e.setMessage(net.minecraft.network.chat.Component.literal("[X] ").append(e.getMessage()));
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGH, prefixer);
            try {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(changed);
            } finally {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(prefixer);
            }
            h.assertTrue(com.mrgregles.bsp_core.proxy.ProxyRelay.decode(com.mrgregles.bsp_core.proxy.ProxyRelay.last()).text().equals("[X] tagged"), "the final message text, after the listeners");
            // team chat cancels the event: never relayed
            h.assertTrue(com.mrgregles.bsp_core.team.Teams.create(a, teamName("Vole"), net.minecraft.ChatFormatting.AQUA).ok() && com.mrgregles.bsp_core.team.TeamChat.toggle(a), "team chat on");
            com.mrgregles.bsp_core.proxy.ProxyRelay.forget();
            var teamLine = new net.minecraftforge.event.ServerChatEvent(a, "secret", net.minecraft.network.chat.Component.literal("secret"));
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(teamLine);
            h.assertTrue(teamLine.isCanceled() && com.mrgregles.bsp_core.proxy.ProxyRelay.last() == null, "a team line is cancelled and never reaches the proxy");
            com.mrgregles.bsp_core.team.TeamChat.toggle(a);
            h.assertTrue(com.mrgregles.bsp_core.team.Teams.disband(a).ok(), "disbanding");
            // the api hands a line over the same way
            com.mrgregles.bsp_core.api.BspProxy.relayChat(a, net.minecraft.network.chat.Component.literal("from a mod"));
            h.assertTrue(com.mrgregles.bsp_core.proxy.ProxyRelay.decode(com.mrgregles.bsp_core.proxy.ProxyRelay.last()).text().equals("from a mod"), "BspProxy.relayChat sends too");
        } finally {
            com.mrgregles.bsp_core.BSPConfig.PROXY_RELAY_CHAT.set(before);
            com.mrgregles.bsp_core.proxy.ProxyRelay.forget();
        }
        h.succeed();
    }
    @GameTest(template = EMPTY)
    public void score_screen_shows_registered_boards_and_rotates(GameTestHelper h) {
        var id = new net.minecraft.resources.ResourceLocation("bsp_test", "homes");
        var id2 = new net.minecraft.resources.ResourceLocation("bsp_test", "broken");
        try {
            // the api: register, list, read lines, a failing supplier gives nothing, unregister
            com.mrgregles.bsp_core.api.BspBoards.register(id, net.minecraft.network.chat.Component.literal("Home servers"), () -> java.util.List.of(
                    com.mrgregles.bsp_core.api.BspBoards.BoardLine.of(net.minecraft.network.chat.Component.literal("BSP-Wild"), net.minecraft.network.chat.Component.literal("12 home"), new ItemStack(net.minecraft.world.item.Items.COMPASS)),
                    com.mrgregles.bsp_core.api.BspBoards.BoardLine.of(net.minecraft.network.chat.Component.literal("BSP-Frost"), null)));
            com.mrgregles.bsp_core.api.BspBoards.register(id2, net.minecraft.network.chat.Component.literal("Broken"), () -> { throw new IllegalStateException("no data yet"); });
            h.assertTrue(com.mrgregles.bsp_core.api.BspBoards.boards().stream().anyMatch(b -> b.id().equals(id)) && com.mrgregles.bsp_core.api.BspBoards.board(id).isPresent(), "registered and listed");
            var lines = com.mrgregles.bsp_core.api.BspBoards.lines(id);
            h.assertTrue(lines.size() == 2 && lines.get(0).value() != null && !lines.get(0).icon().isEmpty() && lines.get(1).value() == null && lines.get(1).icon().isEmpty(), "lines as given: " + lines);
            h.assertTrue(com.mrgregles.bsp_core.api.BspBoards.lines(id2).isEmpty(), "a failing supplier gives no lines");
            h.assertTrue(com.mrgregles.bsp_core.api.BspBoards.lines(new net.minecraft.resources.ResourceLocation("bsp_test", "nothing")).isEmpty(), "an unknown board gives no lines");
            com.mrgregles.bsp_core.api.BspBoards.register(id, net.minecraft.network.chat.Component.literal("Home servers 2"), java.util.List::of);
            h.assertTrue(com.mrgregles.bsp_core.api.BspBoards.board(id).orElseThrow().title().getString().equals("Home servers 2") && com.mrgregles.bsp_core.api.BspBoards.boards().stream().filter(b -> b.id().equals(id)).count() == 1, "registering again replaces, never doubles");
            // the screen keeps a board, a look, the rotation and its seconds, and syncs them
            BlockPos pos = new BlockPos(4, 1, 4), other = new BlockPos(8, 1, 8);
            h.setBlock(pos, com.mrgregles.bsp_core.registry.ModBlocks.SCORE_SCREEN.get());
            h.setBlock(other, com.mrgregles.bsp_core.registry.ModBlocks.SCORE_SCREEN.get());
            var screen = entity(h, pos, com.mrgregles.bsp_core.score.ScoreScreenBlockEntity.class);
            var pages = java.util.List.of(com.mrgregles.bsp_core.score.ScoreScreenBlockEntity.PAGE_SCOREBOARD, id.toString(), id.toString(), "", com.mrgregles.bsp_core.score.ScoreScreenBlockEntity.PAGE_PRIZES);
            screen.configure(com.mrgregles.bsp_core.score.ScoreScreenBlockEntity.MODE_ROTATE, 0, 0, 100, 40, "SPAWN", id.toString(), com.mrgregles.bsp_core.score.ScoreScreenBlockEntity.LOOK_TILES, 3, pages);
            h.assertTrue(screen.mode() == 4 && screen.boardId().equals(id.toString()) && screen.look() == 1 && screen.rotateSeconds() == 5, "mode, board, look kept; seconds clamped up to 5");
            h.assertTrue(screen.pages().equals(java.util.List.of("scoreboard", id.toString(), "prizes")), "pages kept once each, blanks dropped: " + screen.pages());
            var copy = entity(h, other, com.mrgregles.bsp_core.score.ScoreScreenBlockEntity.class);
            copy.load(screen.getUpdateTag());
            h.assertTrue(copy.mode() == 4 && copy.boardId().equals(id.toString()) && copy.look() == 1 && copy.rotateSeconds() == 5 && copy.pages().equals(screen.pages()), "the update tag carries all of it to the client");
            screen.configure(0, 1, 2, 120, 50, "AGAIN");
            h.assertTrue(screen.boardId().equals(id.toString()) && screen.pages().size() == 3, "the old six-field configure leaves the board settings alone");
            // the settings packet carries the new fields
            var packet = new com.mrgregles.bsp_core.network.ScoreScreenConfigPacket(h.absolutePos(pos), 4, 0, 0, 100, 40, "SPAWN", id.toString(), 2, 15, java.util.List.of("scoreboard", id.toString()));
            var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            com.mrgregles.bsp_core.network.ScoreScreenConfigPacket.encode(packet, buf);
            h.assertTrue(com.mrgregles.bsp_core.network.ScoreScreenConfigPacket.decode(buf).equals(packet), "encode and decode agree");
            h.setBlock(pos, Blocks.AIR);
            h.setBlock(other, Blocks.AIR);
        } finally {
            com.mrgregles.bsp_core.api.BspBoards.unregister(id);
            com.mrgregles.bsp_core.api.BspBoards.unregister(id2);
        }
        h.assertTrue(com.mrgregles.bsp_core.api.BspBoards.board(id).isEmpty(), "unregistered");
        h.succeed();
    }
}
