package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.chamber.ChamberSpawner;
import com.mrgregles.bsp_core.chamber.ChamberSpawner.Mode;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.tank.TankBlock;
import com.mrgregles.bsp_core.tank.TankPartBlockEntity;
import com.mrgregles.bsp_core.network.TankViewPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** A Mob Chamber spawns on its own timer from its biome's list, its mobs have no AI, the plasma burns them, and a broken tank wakes them. */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class ChamberSpawnGameTests {
    private static final String EMPTY = "empty", BATCH = "chamber_spawn";
    private static final BlockPos TANK = new BlockPos(4, 1, 4);

    /** A hollow tank of {@code w} x {@code h} x {@code d} at TANK, with a Spawn Grate in the middle of the north face if {@code grate}. */
    private static void build(GameTestHelper h, int w, int ht, int d, boolean grate) {
        BlockPos g = TANK.offset(w / 2, ht / 2, 0);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < ht; y++) {
                for (int z = 0; z < d; z++) {
                    int edges = (x == 0 || x == w - 1 ? 1 : 0) + (y == 0 || y == ht - 1 ? 1 : 0) + (z == 0 || z == d - 1 ? 1 : 0);
                    BlockPos p = TANK.offset(x, y, z);
                    if (edges > 0) {
                        Block b = grate && p.equals(g) ? ModBlocks.SPAWN_GRATE.get() : edges >= 2 ? ModBlocks.TANK_CASING.get() : ModBlocks.TANK_GLASS.get();
                        h.setBlock(p, b);
                    }
                }
            }
        }
    }

    private static TankPartBlockEntity master(GameTestHelper h) {
        h.assertTrue(h.getLevel().getBlockState(h.absolutePos(TANK)).getValue(TankBlock.FORMED), "the tank formed");
        TankPartBlockEntity m = h.getBlockEntity(TANK) instanceof TankPartBlockEntity t ? t.masterEntity() : null;
        h.assertTrue(m != null && m.isMaster(), "the tank has a master");
        return m;
    }

    /** Plains, whatever the test world has where the test stands: the lists are then the same every run. */
    private static net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> plains(GameTestHelper h) {
        return h.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME).getHolderOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS);
    }

    private static String names(java.util.List<net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData> list) {
        return list.stream().map(d -> net.minecraft.world.entity.EntityType.getKey(d.type) + "/" + d.type.getCategory().getName()).toList().toString();
    }

    private static void fillTo(TankPartBlockEntity m, double fraction) {
        m.drain(m.stored());
        m.add((long) Math.ceil(m.capacity() * fraction));
    }

    private static List<Mob> chamberMobs(GameTestHelper h, TankPartBlockEntity m) {
        return h.getLevel().getEntitiesOfClass(Mob.class, ChamberSpawner.inside(m), ChamberSpawner::isChamberMob);
    }

    private static void clearMobs(GameTestHelper h, TankPartBlockEntity m) {
        h.getLevel().getEntitiesOfClass(Mob.class, ChamberSpawner.inside(m).inflate(1)).forEach(Mob::discard);
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void the_timer_shrinks_and_the_cap_grows_with_size(GameTestHelper h) {
        h.assertTrue(ChamberSpawner.timerSeconds(4, 4) == 30 && ChamberSpawner.timerSeconds(5, 4) == 28 && ChamberSpawner.timerSeconds(12, 12) == 14
                && ChamberSpawner.timerSeconds(3, 3) == 30, "30 s at 4 wide, 2 s less per block, 14 s at 12 wide; a 3-wide tank waits 30 s");
        h.assertTrue(ChamberSpawner.maxAtOnce(3, 3) == 1 && ChamberSpawner.maxAtOnce(4, 4) == 1 && ChamberSpawner.maxAtOnce(6, 6) == 4 && ChamberSpawner.maxAtOnce(8, 6) == 6,
                "one mob per 2x2 of inside floor, at least one");
        h.assertTrue(ChamberSpawner.maxAtOnce(12, 12) == 16, "at most 16 in one chamber (100 floor blocks would be 25)");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public void a_chamber_spawns_from_the_start_mark_with_no_ai(GameTestHelper h) {
        build(h, 6, 6, 6, true);
        h.succeedWhen(() -> {
            ServerLevel sl = h.getLevel();
            TankPartBlockEntity m = master(h);
            h.assertTrue(m.chamber(), "a chamber");
            m.setChamberMode(Mode.ALL);
            clearMobs(h, m);
            fillTo(m, 0.10);
            h.assertTrue(ChamberSpawner.spawnOnce(sl, m, sl.getRandom()).equals(ChamberSpawner.LOW), "10% full: nothing spawns");
            fillTo(m, 0.16);
            h.assertTrue(!ChamberSpawner.pool(sl, m, plains(h)).isEmpty(), "plains has mobs to spawn (" + sl.getDifficulty() + ")");
            int spawned = 0;
            String last = "";
            for (int i = 0; i < 24 && !last.equals(ChamberSpawner.FULL); i++) {
                last = ChamberSpawner.spawnOnce(sl, m, sl.getRandom(), plains(h));
                spawned += last.equals(ChamberSpawner.SPAWNED) ? 1 : 0;
            }
            h.assertTrue(last.equals(ChamberSpawner.FULL) && spawned == 4, "16% full, 6 wide: four mobs, then full (" + spawned + ", " + last + ")");
            List<Mob> mobs = chamberMobs(h, m);
            h.assertTrue(mobs.size() >= 4, "they are the chamber's: " + mobs.size());
            for (Mob mob : mobs) {
                h.assertTrue(mob.isNoAi() && mob.isPersistenceRequired(), mob.getType() + " has no AI and stays loaded");
                h.assertTrue(ChamberSpawner.inside(m).contains(mob.position()), mob.getType() + " stands inside");
            }
            clearMobs(h, m);
            m.drain(m.stored());
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void the_switch_picks_monsters_or_the_rest(GameTestHelper h) {
        build(h, 6, 6, 6, true);
        h.succeedWhen(() -> {
            ServerLevel sl = h.getLevel();
            TankPartBlockEntity m = master(h);
            m.setChamberMode(Mode.HOSTILE);
            var hostile = ChamberSpawner.pool(sl, m, plains(h));
            h.assertTrue(!hostile.isEmpty(), "plains has monsters (" + sl.getDifficulty() + ", here " + sl.getBiome(h.absolutePos(TANK)).unwrapKey() + ")");
            h.assertTrue(hostile.stream().allMatch(d -> d.type.getCategory() == MobCategory.MONSTER), "HOSTILE: monsters only: " + names(hostile));
            m.setChamberMode(Mode.PASSIVE);
            var passive = ChamberSpawner.pool(sl, m, plains(h));
            h.assertTrue(!passive.isEmpty() && passive.stream().allMatch(d -> d.type.getCategory().isFriendly()), "PASSIVE: animals, no monsters: " + names(passive));
            m.setChamberMode(Mode.ALL);
            h.assertTrue(ChamberSpawner.pool(sl, m, plains(h)).size() == hostile.size() + passive.size(), "ALL: both");
            h.assertTrue(m.saveWithoutMetadata().getByte("ChamberMode") == Mode.ALL.ordinal(), "the setting is saved");
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void only_mobs_that_fit_spawn(GameTestHelper h) {
        build(h, 4, 4, 4, true);
        h.succeedWhen(() -> {
            TankPartBlockEntity m = master(h);
            h.assertTrue(ChamberSpawner.fits(EntityType.ZOMBIE, m) && ChamberSpawner.fits(EntityType.SPIDER, m), "a zombie and a spider fit inside 2 x 2 x 2");
            h.assertFalse(ChamberSpawner.fits(EntityType.ENDERMAN, m), "an enderman is too tall");
            h.assertFalse(ChamberSpawner.fits(EntityType.GHAST, m), "a ghast is too big");
            var pool = ChamberSpawner.pool(h.getLevel(), m, plains(h));
            h.assertTrue(!pool.isEmpty() && pool.stream().allMatch(d -> ChamberSpawner.fits(d.type, m)), "the list holds only mobs that fit: " + names(pool));
            h.assertTrue(pool.stream().noneMatch(d -> d.type == EntityType.ENDERMAN), "no enderman in a 4-wide chamber's list");
        });
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 400)
    public void the_plasma_burns_a_mob_in_about_five_seconds(GameTestHelper h) {
        build(h, 4, 4, 4, true);
        long[] since = {-1};
        Mob[] mob = {null};
        h.onEachTick(() -> {
            ServerLevel sl = h.getLevel();
            if (since[0] < 0) {
                if (!h.getLevel().getBlockState(h.absolutePos(TANK)).getValue(TankBlock.FORMED)) {
                    return;
                }
                TankPartBlockEntity m = master(h);
                fillTo(m, 0.5);
                // a creeper: undead would also burn in the daylight through the glass
                mob[0] = ChamberSpawner.spawn(sl, m, EntityType.CREEPER, sl.getRandom());
                h.assertTrue(mob[0] instanceof Creeper, "a creeper spawned in the chamber");
                since[0] = sl.getGameTime();
                return;
            }
            Mob z = mob[0];
            long t = sl.getGameTime() - since[0];
            if (z.isAlive()) {
                h.assertTrue(t < 160, "still alive after " + t + " ticks (health " + z.getHealth() + ")");
                return;
            }
            h.assertTrue(t >= 70, "it took about five seconds, not " + t + " ticks");
            var last = z.getLastDamageSource();
            h.assertTrue(last != null && last.is(ChamberSpawner.PLASMA), "the plasma killed it: " + last);
            h.assertTrue(z.getLastHurtByMob() == null && (z.getKillCredit() == null || z.getKillCredit() instanceof net.minecraftforge.common.util.FakePlayer),
                    "no real player gets the kill, only the chamber's stand-in for drops: " + z.getKillCredit());
            master(h).drain(Long.MAX_VALUE);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public void a_mob_put_inside_is_claimed_and_a_broken_tank_wakes_it(GameTestHelper h) {
        build(h, 4, 4, 4, true);
        BlockPos inside = TANK.offset(1, 1, 1), glass = TANK.offset(1, 1, 3);
        Creeper[] z = {null};
        boolean[] keptBefore = {false};
        h.onEachTick(() -> {
            if (!h.getLevel().getBlockState(h.absolutePos(TANK)).getValue(TankBlock.FORMED) && z[0] == null) {
                return;
            }
            if (z[0] == null) {
                master(h).drain(Long.MAX_VALUE); // an empty tank still burns, but a creeper outlives a few seconds of it
                z[0] = h.spawn(EntityType.CREEPER, inside); // not undead: no sunburn through the glass
                keptBefore[0] = z[0].isPersistenceRequired();
                return;
            }
            Creeper zombie = z[0];
            if (!zombie.isNoAi()) {
                return; // wait for the chamber's second
            }
            h.assertTrue(ChamberSpawner.isChamberMob(zombie) && zombie.isPersistenceRequired() && zombie.getHealth() < zombie.getMaxHealth(),
                    "claimed: no AI, kept, and burning even in an empty tank");
            h.setBlock(glass, Blocks.AIR);
            h.assertFalse(zombie.isNoAi() || ChamberSpawner.isChamberMob(zombie), "the tank broke: it wakes as an ordinary creeper");
            h.assertTrue(zombie.isPersistenceRequired() == keptBefore[0], "as it was before the chamber took it");
            zombie.discard();
            h.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_chamber_enderman_does_not_teleport_out(GameTestHelper h) {
        build(h, 6, 6, 6, true);
        h.succeedWhen(() -> {
            ServerLevel sl = h.getLevel();
            TankPartBlockEntity m = master(h);
            Mob ender = ChamberSpawner.spawn(sl, m, EntityType.ENDERMAN, sl.getRandom());
            h.assertTrue(ender != null, "an enderman fits a 6-wide chamber");
            h.assertTrue(ForgeEventFactory.onEnderTeleport(ender, ender.getX() + 20, ender.getY(), ender.getZ()).isCanceled(), "its teleport is cancelled");
            Mob outside = h.spawn(EntityType.ENDERMAN, new BlockPos(16, 1, 16));
            h.assertFalse(ForgeEventFactory.onEnderTeleport(outside, outside.getX() + 20, outside.getY(), outside.getZ()).isCanceled(), "one outside still teleports");
            ender.discard();
            outside.discard();
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_plain_tank_does_not_spawn(GameTestHelper h) {
        build(h, 4, 4, 4, false);
        h.succeedWhen(() -> {
            TankPartBlockEntity m = master(h);
            fillTo(m, 0.5);
            h.assertTrue(ChamberSpawner.spawnOnce(h.getLevel(), m, h.getLevel().getRandom()).equals(ChamberSpawner.NOT_CHAMBER), "no grate, no spawning");
            m.drain(m.stored());
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void the_chamber_tab_gets_what_it_shows(GameTestHelper h) {
        build(h, 6, 5, 6, true);
        h.succeedWhen(() -> {
            ServerLevel sl = h.getLevel();
            TankPartBlockEntity m = master(h);
            clearMobs(h, m);
            m.setChamberMode(Mode.HOSTILE);
            m.drain(m.stored());
            m.add(1_000);
            m.storeLoot(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE, 5));
            m.setFeed(net.minecraft.world.item.Items.BONE, 2);
            var c = com.mrgregles.bsp_core.chamber.ChamberView.build(sl, m);
            h.assertTrue(c.mode() == Mode.HOSTILE.ordinal() && c.markPermille() == 150 && c.maxHeight() == 3 && c.maxAlive() == 4, "the switch, the 15% mark, 3 blocks tall, four at once");
            h.assertTrue(c.loot().size() == 27 && c.loot().get(0).is(net.minecraft.world.item.Items.BONE) && c.loot().get(0).getCount() == 5, "the 27 loot slots");
            h.assertTrue(c.feedItem().equals("minecraft:bone") && c.feedLevel() == 2 && c.feedMb()[0] == 24_000 && c.feedMb()[1] == 12_000 && c.feedMb()[2] == 6_000
                    && c.minuteMb() == 24_000, "the drop being fed and what one item tags at each level, against what a projector burns in a minute (20 mB/t)");
            h.assertTrue(c.spawns().stream().allMatch(sp -> net.minecraft.world.entity.EntityType.byString(sp.entity()).map(t -> t.getCategory() == net.minecraft.world.entity.MobCategory.MONSTER).orElse(false)),
                    "HOSTILE lists monsters only: " + c.spawns());
            h.assertTrue(c.spawns().stream().mapToInt(TankViewPacket.Spawn::permille).sum() <= 1_001, "chances add up to at most 100%");
            // the drop and power each mob is shown with
            var zombie = com.mrgregles.bsp_core.chamber.ChamberView.drop(sl, EntityType.ZOMBIE).orElse(null);
            h.assertTrue(zombie != null && zombie.item() == net.minecraft.world.item.Items.ROTTEN_FLESH && zombie.power() == com.mrgregles.bsp_core.totem.TotemUpgrades.Buff.UNDEAD_WARD, "a zombie: rotten flesh, Undead Ward");
            var wither = com.mrgregles.bsp_core.chamber.ChamberView.drop(sl, EntityType.WITHER_SKELETON).orElse(null);
            h.assertTrue(wither != null && wither.power() == com.mrgregles.bsp_core.totem.TotemUpgrades.Buff.WITHER || wither != null && wither.power() == com.mrgregles.bsp_core.totem.TotemUpgrades.Buff.SCORCH,
                    "a wither skeleton: a Wither Bone (or coal), never plain bone: " + wither);
            var cow = com.mrgregles.bsp_core.chamber.ChamberView.drop(sl, EntityType.COW).orElse(null);
            h.assertTrue(cow != null && cow.power() == null, "a cow: loot only");
            // only a player who opened the screen by right-clicking, near the tank, may change it
            var player = MockPlayers.serverPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);
            BlockPos near = h.absolutePos(TANK).offset(3, 1, -2);
            player.moveTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
            h.assertFalse(com.mrgregles.bsp_core.tank.TankStructure.mayUse(player, m), "no screen opened: no changes");
            com.mrgregles.bsp_core.tank.TankStructure.open(player, h.absolutePos(TANK.offset(5, 2, 2)));
            h.assertTrue(com.mrgregles.bsp_core.tank.TankStructure.mayUse(player, m), "opened by right-clicking any block of it: changes go through");
            player.moveTo(near.getX() + 30.5, near.getY(), near.getZ() + 0.5);
            h.assertFalse(com.mrgregles.bsp_core.tank.TankStructure.mayUse(player, m), "walked away: no more");
            // the view goes over the wire whole
            var packet = new TankViewPacket(m.master(), m.w(), m.h(), m.d(), m.stored(), m.capacity(), m.pools().plain(), m.pools().totem(), java.util.List.of(), java.util.List.of(), java.util.List.of(), c);
            var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            TankViewPacket.encode(packet, buf);
            var back = TankViewPacket.decode(buf).chamber();
            h.assertTrue(back != null && back.loot().size() == 27 && back.loot().get(0).getCount() == 5 && back.feedItem().equals("minecraft:bone") && back.feedMb()[1] == 12_000
                    && back.spawns().equals(c.spawns()) && back.biome().equals(c.biome()), "encoded and read back the same");
            // a spawn lights the grates' lamps on every client
            m.setChamberMode(Mode.ALL);
            m.add(m.capacity() / 2);
            String r = ChamberSpawner.spawnOnce(sl, m, sl.getRandom(), plains(h));
            h.assertTrue(r.equals(ChamberSpawner.SPAWNED) && m.lastSpawnAt() == sl.getGameTime() && m.saveWithoutMetadata().getLong("LastSpawn") == sl.getGameTime(), "the spawn time is kept and sent: " + r);
            clearMobs(h, m);
            m.drain(m.stored());
            m.setFeed(null, 1);
            m.loot().setStackInSlot(0, net.minecraft.world.item.ItemStack.EMPTY);
        });
    }
}
