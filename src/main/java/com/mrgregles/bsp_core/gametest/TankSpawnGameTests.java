package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.chamber.ChamberSpawns;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.tank.TankBlock;
import com.mrgregles.bsp_core.tank.TankPartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import javax.annotation.Nullable;

/**
 * No mob spawns naturally inside a formed Plasma Tank (the inside is air on a full-block floor, which the game would otherwise take); a
 * Spawn Grate in the shell makes the tank a Mob Chamber.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class TankSpawnGameTests {
    private static final String EMPTY = "empty", BATCH = "tank_spawn";
    private static final BlockPos TANK = new BlockPos(9, 1, 9);
    private static final int SIDE = 4;

    /** A SIDE-wide hollow tank at {@code o}: casing on the edges, glass on the faces, and a Spawn Grate at {@code grate} if given. */
    private static void buildTank(GameTestHelper h, BlockPos o, @Nullable BlockPos grate) {
        for (int x = 0; x < SIDE; x++) {
            for (int y = 0; y < SIDE; y++) {
                for (int z = 0; z < SIDE; z++) {
                    int edges = (x == 0 || x == SIDE - 1 ? 1 : 0) + (y == 0 || y == SIDE - 1 ? 1 : 0) + (z == 0 || z == SIDE - 1 ? 1 : 0);
                    BlockPos p = o.offset(x, y, z);
                    if (edges > 0) {
                        Block b = p.equals(grate) ? ModBlocks.SPAWN_GRATE.get() : edges >= 2 ? ModBlocks.TANK_CASING.get() : ModBlocks.TANK_GLASS.get();
                        h.setBlock(p, b);
                    }
                }
            }
        }
    }

    private static boolean formed(GameTestHelper h) {
        return h.getLevel().getBlockState(h.absolutePos(TANK)).getValue(TankBlock.FORMED);
    }

    private static TankPartBlockEntity master(GameTestHelper h) {
        h.assertTrue(formed(h), "the tank formed");
        TankPartBlockEntity m = h.getBlockEntity(TANK) instanceof TankPartBlockEntity t ? t.masterEntity() : null;
        h.assertTrue(m != null && m.isMaster(), "the tank has a master");
        return m;
    }

    /** What Forge's spawn placement check says for a natural zombie at {@code rel}. */
    private static Event.Result placement(GameTestHelper h, BlockPos rel) {
        ServerLevel sl = h.getLevel();
        var event = new MobSpawnEvent.SpawnPlacementCheck(EntityType.ZOMBIE, sl, MobSpawnType.NATURAL, h.absolutePos(rel), sl.getRandom(), true);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getResult();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void no_mob_spawns_naturally_inside_a_formed_tank(GameTestHelper h) {
        buildTank(h, TANK, null);
        BlockPos floor = TANK.offset(1, 0, 1), inside = floor.above(), roof = TANK.offset(1, SIDE, 1);
        h.succeedWhen(() -> {
            master(h);
            ServerLevel sl = h.getLevel();
            BlockPos at = h.absolutePos(inside);
            // the game alone would allow it: air on a full-block floor
            h.assertTrue(NaturalSpawner.isSpawnPositionOk(SpawnPlacements.Type.ON_GROUND, sl, at, EntityType.ZOMBIE), "the inside is a place a zombie could stand");
            h.assertTrue(ChamberSpawns.insideFormedTank(sl, at), "it is inside a formed tank");
            h.assertTrue(placement(h, inside) == Event.Result.DENY, "so a natural spawn there is refused");
            h.assertTrue(placement(h, inside.above()) == Event.Result.DENY, "higher up inside too");
            h.assertFalse(ChamberSpawns.insideFormedTank(sl, h.absolutePos(roof)) || placement(h, roof) == Event.Result.DENY, "on the roof is outside: left to the game");
            var spawner = new MobSpawnEvent.SpawnPlacementCheck(EntityType.ZOMBIE, sl, MobSpawnType.SPAWN_EGG, at, sl.getRandom(), true);
            MinecraftForge.EVENT_BUS.post(spawner);
            h.assertTrue(spawner.getResult() != Event.Result.DENY, "a spawn egg is not a natural spawn");
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_block_put_inside_still_counts_as_inside(GameTestHelper h) {
        buildTank(h, TANK, null);
        BlockPos stand = TANK.offset(1, 1, 1);
        h.succeedWhen(() -> {
            master(h);
            h.setBlock(stand, Blocks.STONE);
            h.assertTrue(placement(h, stand.above()) == Event.Result.DENY, "standing on a stone inside is still inside");
            h.setBlock(stand, Blocks.AIR);
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void an_unformed_box_is_left_to_the_game(GameTestHelper h) {
        buildTank(h, TANK, null);
        BlockPos glass = TANK.offset(1, 1, 0), inside = TANK.offset(1, 1, 1);
        h.succeedWhen(() -> {
            master(h);
            h.setBlock(glass, Blocks.AIR);
            h.assertFalse(formed(h), "one block out: no tank");
            h.assertTrue(placement(h, inside) != Event.Result.DENY, "a box that is not a tank does not stop spawns");
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_spawn_grate_makes_the_tank_a_chamber(GameTestHelper h) {
        BlockPos grate = TANK.offset(1, 2, 0);
        buildTank(h, TANK, grate);
        h.succeedWhen(() -> {
            TankPartBlockEntity m = master(h);
            h.assertTrue(m.chamber(), "a tank with a Spawn Grate is a Mob Chamber");
            h.assertTrue(m.saveWithoutMetadata().getBoolean("Chamber"), "and saves as one");
            h.assertTrue(placement(h, TANK.offset(1, 1, 1)) == Event.Result.DENY, "a chamber gets no natural spawns either");
            h.setBlock(grate, ModBlocks.TANK_GLASS.get());
            TankPartBlockEntity again = master(h);
            h.assertFalse(again.chamber(), "glass in its place: a plain tank again");
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_spawn_grate_goes_on_a_face_not_an_edge(GameTestHelper h) {
        BlockPos edge = TANK.offset(1, 0, 0);
        buildTank(h, TANK, edge);
        h.runAfterDelay(5, () -> {
            h.assertFalse(formed(h), "a Spawn Grate on an edge leaves the box unformed");
            h.setBlock(edge, ModBlocks.TANK_CASING.get());
            h.assertTrue(formed(h) && !master(h).chamber(), "casing on the edge: a plain tank");
            h.succeed();
        });
    }
}
