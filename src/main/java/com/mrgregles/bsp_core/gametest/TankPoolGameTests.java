package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.tank.TankBlock;
import com.mrgregles.bsp_core.tank.TankLedger;
import com.mrgregles.bsp_core.tank.TankPartBlockEntity;
import com.mrgregles.bsp_core.tank.TankPools;
import com.mrgregles.bsp_core.tank.TankPortBlockEntity;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * A Plasma Tank keeps its plasma in pools that never mix: plain, the totem pool, and one pool per Mob Chamber power. Ports give out plain
 * and totem plasma as tanks always have; mob pools stay in. Old tanks load with their powers in the totem pool.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class TankPoolGameTests {
    private static final String EMPTY = "empty", BATCH = "chamber";
    private static final BlockPos TANK = new BlockPos(4, 1, 4);
    private static final int SIDE = 3;
    /** A port in the middle of the tank's top face. */
    private static final BlockPos PORT = TANK.offset(1, SIDE - 1, 1), GLASS = TANK.offset(1, 1, 0);

    private static void buildTank(GameTestHelper h) {
        for (int x = 0; x < SIDE; x++) {
            for (int y = 0; y < SIDE; y++) {
                for (int z = 0; z < SIDE; z++) {
                    BlockPos p = TANK.offset(x, y, z);
                    int edges = (x == 0 || x == SIDE - 1 ? 1 : 0) + (y == 0 || y == SIDE - 1 ? 1 : 0) + (z == 0 || z == SIDE - 1 ? 1 : 0);
                    if (edges > 0) {
                        Block b = p.equals(PORT) ? ModBlocks.TANK_PORT.get() : edges >= 2 ? ModBlocks.TANK_CASING.get() : ModBlocks.TANK_GLASS.get();
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

    private static int[] offered(Object... buffLevel) {
        int[] o = new int[Buff.values().length];
        for (int i = 0; i < buffLevel.length; i += 2) {
            o[((Buff) buffLevel[i]).ordinal()] = (Integer) buffLevel[i + 1];
        }
        return o;
    }

    /** Plain 1,000; totem 2,000 with Ward II; Growth II 3,000; Keep I 500. */
    private static void fill(TankPartBlockEntity m) {
        m.drain(m.stored());
        for (Buff b : Buff.values()) {
            m.drainMob(b, Long.MAX_VALUE);
        }
        m.add(1_000);
        m.add(2_000, offered(Buff.WARD, 2));
        m.addMob(Buff.GROWTH, 2, 3_000);
        m.addMob(Buff.KEEP, 1, 500);
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void plasma_sorts_into_pools_as_it_arrives(GameTestHelper h) {
        TankPools p = new TankPools();
        p.add(1_000, null, 0);
        p.add(900, offered(Buff.WARD, 2, Buff.GROWTH, 1, Buff.KEEP, 3), 0);
        h.assertTrue(p.plain() == 1_000, "plasma with no power is plain");
        h.assertTrue(p.totem() == 300 && p.mb(Buff.WARD) == 300 && p.level(Buff.WARD) == 2, "a mixed stream is shared: a third to the totem pool");
        h.assertTrue(p.mb(Buff.GROWTH) == 300 && p.level(Buff.GROWTH) == 1 && p.mb(Buff.KEEP) == 300 && p.level(Buff.KEEP) == 3, "and a third to each mob pool");
        h.assertTrue(p.total() == 1_900, "nothing counted twice");
        p.addMob(Buff.GROWTH, 3, 100, 0);
        h.assertTrue(p.mb(Buff.GROWTH) == 400 && p.mbAt(Buff.GROWTH, 1) == 300 && p.mbAt(Buff.GROWTH, 3) == 100, "Growth III beside Growth I: the levels stay apart");
        h.assertTrue(p.level(Buff.GROWTH) == 3, "the pool reads as its highest level");
        h.assertTrue(p.drainMob(Buff.GROWTH, 150) == 150 && p.mbAt(Buff.GROWTH, 3) == 0 && p.mbAt(Buff.GROWTH, 1) == 250 && p.level(Buff.GROWTH) == 1,
                "it gives out its highest level first");
        p.addMob(Buff.WARD, 2, 100, 0);
        h.assertTrue(p.mb(Buff.WARD) == 300 && p.totem() == 300, "a totem power is not a mob pool");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void draining_one_pool_leaves_the_others(GameTestHelper h) {
        buildTank(h);
        h.succeedWhen(() -> {
            TankPartBlockEntity m = master(h);
            fill(m);
            h.assertTrue(m.stored() == 6_500 && m.open() == 3_000, "6,500 mB in all; 3,000 plain and totem");
            h.assertTrue(m.drainMob(Buff.GROWTH, 1_000) == 1_000 && m.buffedMb(Buff.GROWTH.ordinal()) == 2_000, "a thousand out of Growth");
            h.assertTrue(m.pools().plain() == 1_000 && m.pools().totem() == 2_000 && m.buffedMb(Buff.KEEP.ordinal()) == 500, "the other pools are untouched");
            TankPortBlockEntity port = h.getBlockEntity(PORT) instanceof TankPortBlockEntity tp ? tp : null;
            h.assertTrue(port != null && port.tank() == 3_000, "a port sees only plain and totem plasma: " + (port == null ? "no port" : port.tank()));
            h.assertTrue(port.offered()[Buff.WARD.ordinal()] == 2 && port.offered()[Buff.GROWTH.ordinal()] == 0, "a port passes on Ward, not Growth");
            h.assertTrue(port.drain(3_000) == 3_000 && port.drain(1) == 0, "a port gives out the plain and totem plasma, no more");
            h.assertTrue(m.pools().plain() == 0 && m.pools().totem() == 0 && m.buffedMb(Buff.WARD.ordinal()) == 0, "plain and totem gone, Ward with it");
            h.assertTrue(m.buffedMb(Buff.GROWTH.ordinal()) == 2_000 && m.buffedLevel(Buff.GROWTH.ordinal()) == 2 && m.buffedMb(Buff.KEEP.ordinal()) == 500, "the mob pools stay in");
            h.assertTrue(m.add(m.capacity()) == m.capacity() - 2_500, "mob pools take room like any plasma");
            m.drain(m.stored());
            m.drainMob(Buff.GROWTH, Long.MAX_VALUE);
            m.drainMob(Buff.KEEP, Long.MAX_VALUE);
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_port_drains_plain_and_totem_in_proportion(GameTestHelper h) {
        TankPools p = new TankPools();
        p.addPlain(1_000);
        p.add(3_000, offered(Buff.WARD, 2, Buff.OUTPUT, 1), 0);
        h.assertTrue(p.drainOpen(2_000) == 2_000 && p.plain() == 500 && p.totem() == 1_500, "half of each");
        h.assertTrue(p.mb(Buff.WARD) == 1_500 && p.mb(Buff.OUTPUT) == 1_500 && p.level(Buff.WARD) == 2, "each power's share goes down with the totem pool");
        h.assertTrue(p.drainOpen(5_000) == 2_000 && p.open() == 0 && p.level(Buff.WARD) == 0, "all of it, and the powers with it");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void an_old_tank_loads_with_its_powers_in_the_totem_pool(GameTestHelper h) {
        buildTank(h);
        h.succeedWhen(() -> {
            TankPartBlockEntity m = master(h);
            CompoundTag tag = m.saveWithoutMetadata();
            // as a tank saved before the pools: Stored, and 32 shares of the totem powers
            tag.remove("Plain");
            tag.remove("Totem");
            long[] mb = new long[32];
            int[] lv = new int[32];
            mb[Buff.WARD.ordinal()] = 6_000;
            lv[Buff.WARD.ordinal()] = 3;
            mb[Buff.OUTPUT.ordinal()] = 4_000;
            lv[Buff.OUTPUT.ordinal()] = 2;
            tag.putLong("Stored", 10_000);
            tag.putLongArray("BuffMb", mb);
            tag.putIntArray("BuffLevel", lv);
            m.load(tag);
            h.assertTrue(m.stored() == 10_000, "the plasma is all there");
            h.assertTrue(m.pools().totem() == 6_000 && m.pools().plain() == 4_000, "what carried a power is the totem pool, the rest plain: "
                    + m.pools().totem() + " / " + m.pools().plain());
            h.assertTrue(m.buffedLevel(Buff.WARD.ordinal()) == 3 && m.buffedMb(Buff.OUTPUT.ordinal()) == 4_000 && m.offered()[Buff.OUTPUT.ordinal()] == 2, "Ward III and Output II kept");
            CompoundTag again = m.saveWithoutMetadata();
            TankPools back = new TankPools();
            back.load(again);
            h.assertTrue(back.plain() == 4_000 && back.totem() == 6_000 && again.getLongArray("BuffMb").length == Buff.values().length, "saved again in the new form");
            m.drain(m.stored());
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_broken_tank_remembers_every_pool(GameTestHelper h) {
        buildTank(h);
        h.succeedWhen(() -> {
            fill(master(h));
            h.setBlock(GLASS, Blocks.AIR);
            h.assertFalse(h.getLevel().getBlockState(h.absolutePos(TANK)).getValue(TankBlock.FORMED), "one block out: the tank comes apart");
            h.setBlock(GLASS, ModBlocks.TANK_GLASS.get());
            TankPartBlockEntity m = master(h);
            h.assertTrue(m.pools().plain() == 1_000 && m.pools().totem() == 2_000 && m.buffedLevel(Buff.WARD.ordinal()) == 2, "plain and totem plasma back, Ward II with it");
            h.assertTrue(m.buffedMb(Buff.GROWTH.ordinal()) == 3_000 && m.buffedLevel(Buff.GROWTH.ordinal()) == 2 && m.buffedMb(Buff.KEEP.ordinal()) == 500, "the mob pools back");
            m.drain(m.stored());
            m.drainMob(Buff.GROWTH, Long.MAX_VALUE);
            m.drainMob(Buff.KEEP, Long.MAX_VALUE);
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void mob_levels_are_saved_and_merged_apart(GameTestHelper h) {
        TankPools p = new TankPools();
        p.addMob(Buff.KEEP, 1, 40, 0);
        p.addMob(Buff.KEEP, 3, 600, 0);
        CompoundTag tag = new CompoundTag();
        p.save(tag);
        TankPools back = new TankPools();
        back.load(tag);
        h.assertTrue(back.mbAt(Buff.KEEP, 1) == 40 && back.mbAt(Buff.KEEP, 3) == 600 && back.level(Buff.KEEP) == 3, "both levels saved");
        back.merge(p);
        h.assertTrue(back.mbAt(Buff.KEEP, 1) == 80 && back.mbAt(Buff.KEEP, 3) == 1_200, "merged level by level");
        back.trimTo(1_000);
        h.assertTrue(back.mbAt(Buff.KEEP, 1) == 0 && back.mbAt(Buff.KEEP, 3) == 1_000, "a smaller tank loses the lowest level first");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_smaller_tank_loses_plain_plasma_first(GameTestHelper h) {
        TankPools p = new TankPools();
        p.addPlain(1_000);
        p.add(2_000, offered(Buff.WARD, 2), 0);
        p.addMob(Buff.GROWTH, 2, 3_000, 0);
        p.trimTo(4_000);
        h.assertTrue(p.plain() == 0 && p.totem() == 1_000 && p.mb(Buff.GROWTH) == 3_000, "plain goes first, then the totem pool");
        p.trimTo(1_000);
        h.assertTrue(p.totem() == 0 && p.mb(Buff.GROWTH) == 1_000 && p.total() == 1_000, "then the mob pools");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void an_old_dormant_tank_comes_back_as_plain_plasma(GameTestHelper h) {
        CompoundTag entry = new CompoundTag();
        entry.putLong("Min", BlockPos.ZERO.asLong());
        entry.putInt("W", 3);
        entry.putInt("H", 3);
        entry.putInt("D", 3);
        entry.putLong("Stored", 7_000);
        entry.putInt("Remaining", 25);
        ListTag list = new ListTag();
        list.add(entry);
        CompoundTag tag = new CompoundTag();
        tag.put("Tanks", list);
        TankPools back = TankLedger.load(tag).take(BlockPos.ZERO, 3, 3, 3);
        h.assertTrue(back.plain() == 7_000 && back.total() == 7_000, "a ledger entry from before the pools is plain plasma");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_tank_remembers_who_fed_it_while_that_plasma_lasts(GameTestHelper h) {
        buildTank(h);
        java.util.UUID owner = java.util.UUID.randomUUID(), ally = java.util.UUID.randomUUID();
        h.succeedWhen(() -> {
            TankPartBlockEntity m = master(h);
            m.drain(m.stored());
            var access = new com.mrgregles.bsp_core.plasma.PlasmaAccess();
            access.editors.add(owner);
            access.wardSafe.add(owner);
            access.wardSafe.add(ally);
            var port = (TankPortBlockEntity) h.getBlockEntity(PORT);
            port.feed(h.absolutePos(TANK), offered(Buff.WARD, 1), 0, 50, 1_000, access, null, null);
            h.assertTrue(m.fedBy().editors.contains(owner) && m.fedBy().wardSafe.contains(ally), "the tank knows who fed it");
            h.assertTrue(port.access().wardSafe.contains(owner) && port.access().wardSafe.contains(ally), "its ports pass it on");
            CompoundTag tag = m.saveWithoutMetadata();
            m.load(tag);
            h.assertTrue(m.fedBy().wardSafe.contains(ally), "saved with the tank");
            h.setBlock(GLASS, Blocks.AIR);
            h.setBlock(GLASS, ModBlocks.TANK_GLASS.get());
            TankPartBlockEntity again = master(h);
            h.assertTrue(again.fedBy().editors.contains(owner) && again.stored() == 1_000, "kept through a break and rebuild, with the plasma");
            again.drain(again.stored());
            h.assertTrue(again.fedBy().isEmpty(), "the plasma they fed is gone: so is the record");
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_tank_takes_its_team_from_its_first_feeder_and_those_they_trust(GameTestHelper h) {
        buildTank(h);
        java.util.UUID owner = java.util.UUID.randomUUID(), friend = java.util.UUID.randomUUID(), raider = java.util.UUID.randomUUID();
        h.succeedWhen(() -> {
            TankPartBlockEntity m = master(h);
            m.drain(m.stored());
            var port = (TankPortBlockEntity) h.getBlockEntity(PORT);
            var mine = new com.mrgregles.bsp_core.plasma.PlasmaAccess();
            mine.editors.add(owner);
            mine.editors.add(friend); // an Upgrades friend
            mine.wardSafe.add(owner);
            mine.wardSafe.add(friend);
            mine.wardSafe.add(raider); // the raider has Ward on the owner's totem, nothing more
            var theirs = new com.mrgregles.bsp_core.plasma.PlasmaAccess();
            theirs.editors.add(raider);
            theirs.wardSafe.add(raider);
            port.feed(h.absolutePos(TANK), offered(Buff.WARD, 1), 0, 50, 1_000, mine, null, null);
            port.feed(h.absolutePos(TANK), new int[Buff.values().length], 0, 50, 1, theirs, null, null);
            h.assertTrue(m.fedBy().editors.contains(owner) && !m.fedBy().editors.contains(raider),
                    "a Ward-only friend's plasma goes in without making them the team: " + m.fedBy().editors);
            var friends = new com.mrgregles.bsp_core.plasma.PlasmaAccess();
            friends.editors.add(friend);
            friends.wardSafe.add(friend);
            port.feed(h.absolutePos(TANK), new int[Buff.values().length], 0, 50, 10, friends, null, null);
            h.assertTrue(m.fedBy().editors.contains(friend) && m.fedBy().editors.contains(owner), "an Upgrades friend may feed it and be known, beside the owner: " + m.fedBy().editors);
            m.pools().drainOpen(Long.MAX_VALUE); // used up some other way (an extractor, a kill)
            m.serverTick(h.getLevel());
            for (int i = 0; i < 20 && !m.fedBy().isEmpty(); i++) {
                m.serverTick(h.getLevel());
            }
            h.assertTrue(m.fedBy().isEmpty(), "the plasma is gone however it left: so is the record");
        });
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 300)
    public void a_projector_base_fed_from_a_tank_knows_the_team(GameTestHelper h) {
        buildTank(h);
        BlockPos base = PORT.above();
        h.setBlock(base, ModBlocks.PROJECTOR_BASE.get());
        java.util.UUID owner = java.util.UUID.randomUUID();
        boolean[] fed = {false};
        h.onEachTick(() -> {
            if (!fed[0]) {
                if (!h.getLevel().getBlockState(h.absolutePos(TANK)).getValue(TankBlock.FORMED)) {
                    return;
                }
                TankPartBlockEntity m = master(h);
                m.drain(m.stored());
                var access = new com.mrgregles.bsp_core.plasma.PlasmaAccess();
                access.editors.add(owner);
                access.wardSafe.add(owner);
                ((TankPortBlockEntity) h.getBlockEntity(PORT)).feed(h.absolutePos(TANK), offered(Buff.WARD, 2), 0, 50, 50_000, access, null, null);
                fed[0] = true;
                return;
            }
            var pb = (com.mrgregles.bsp_core.plasma.ProjectorBaseBlockEntity) h.getBlockEntity(base);
            if (pb.tank() > 0 && pb.access().wardSafe.contains(owner)) {
                h.assertTrue(pb.offered()[Buff.WARD.ordinal()] == 2 && pb.access().editors.contains(owner), "Ward II from the tank, with the owner as its team");
                master(h).drain(Long.MAX_VALUE);
                h.succeed();
            }
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_tank_keeps_the_anchor_totem_while_its_plasma_carries_anchor(GameTestHelper h) {
        buildTank(h);
        java.util.UUID owner = java.util.UUID.randomUUID(), raider = java.util.UUID.randomUUID();
        var totem = net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), h.absolutePos(new BlockPos(0, 1, 0)));
        var theirTotem = net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), h.absolutePos(new BlockPos(1, 1, 0)));
        var mineA = new com.mrgregles.bsp_core.plasma.Anchor(totem, owner, java.util.Set.of(owner));
        var theirA = new com.mrgregles.bsp_core.plasma.Anchor(theirTotem, raider, java.util.Set.of(raider));
        h.succeedWhen(() -> {
            TankPartBlockEntity m = master(h);
            m.drain(m.stored());
            var port = (TankPortBlockEntity) h.getBlockEntity(PORT);
            var mine = new com.mrgregles.bsp_core.plasma.PlasmaAccess();
            mine.editors.add(owner);
            var theirs = new com.mrgregles.bsp_core.plasma.PlasmaAccess();
            theirs.editors.add(raider);
            port.feed(h.absolutePos(TANK), offered(Buff.WARD, 1), 0, 50, 1_000, mine, mineA, null);
            h.assertTrue(port.anchor() == null, "plasma without Anchor names no Anchor totem");
            port.feed(h.absolutePos(TANK), offered(Buff.ANCHOR, 2), 0, 50, 1_000, mine, mineA, null);
            h.assertTrue(mineA.equals(port.anchor()) && totem.equals(m.anchorTotem()), "plasma carrying Anchor brings its totem, and the port passes it on: " + port.anchor());
            port.feed(h.absolutePos(TANK), offered(Buff.ANCHOR, 3), 0, 50, 10, theirs, theirA, null);
            h.assertTrue(mineA.equals(port.anchor()), "a stranger's Anchor does not take the tank over");
            m.drain(m.stored());
            port.feed(h.absolutePos(TANK), offered(Buff.WARD, 1), 0, 50, 1_000, mine, null, null);
            port.feed(h.absolutePos(TANK), offered(Buff.ANCHOR, 3), 0, 50, 1_000, theirs, theirA, null);
            h.assertTrue(m.offered()[Buff.ANCHOR.ordinal()] == 3 && port.anchor() == null, "nor names its totem in a tank someone else fed, even one with no Anchor yet");
            m.drain(m.stored());
            var ledger = com.mrgregles.bsp_core.chunk.ChunkLedger.get(h.getLevel().getServer());
            var known = new com.mrgregles.bsp_core.chunk.ChunkLedger.Totem();
            known.owner = owner;
            ledger.totems.put(totem, known);
            port.feed(h.absolutePos(TANK), offered(Buff.ANCHOR, 2), 0, 50, 1_000, mine, mineA, null);
            h.assertTrue(mineA.equals(port.anchor()), "the owner's own Anchor names its totem");
            known.owner = raider;
            boolean stolenDrops = port.anchor() == null;
            known.owner = owner;
            ledger.totems.remove(totem);
            h.assertTrue(stolenDrops, "the totem changes hands: its old plasma no longer names it");
            m.drain(m.stored());
            port.feed(h.absolutePos(TANK), offered(Buff.ANCHOR, 2), 0, 50, 1_000, mine, mineA, null);
            CompoundTag tag = m.saveWithoutMetadata();
            m.load(tag);
            h.assertTrue(totem.equals(m.anchorTotem()), "saved with the tank");
            h.assertTrue(!m.getUpdateTag().contains("AnchorDim"), "and kept on the server");
            h.setBlock(GLASS, Blocks.AIR);
            h.setBlock(GLASS, ModBlocks.TANK_GLASS.get());
            TankPartBlockEntity again = master(h);
            h.assertTrue(totem.equals(again.anchorTotem()), "kept through a break and rebuild, with the plasma: " + again.anchorTotem());
            again.drain(again.stored());
            h.assertTrue(again.anchorTotem() == null && port.anchor() == null, "the plasma that carried Anchor is gone: so is its totem");
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_ward_only_friends_feed_leaves_the_tanks_feeders_and_anchor_alone(GameTestHelper h) {
        buildTank(h);
        java.util.UUID owner = java.util.UUID.randomUUID(), wardFriend = java.util.UUID.randomUUID();
        var totem = net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), h.absolutePos(new BlockPos(0, 1, 0)));
        var theirTotem = net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), h.absolutePos(new BlockPos(1, 1, 0)));
        var anchor = new com.mrgregles.bsp_core.plasma.Anchor(totem, owner, java.util.Set.of(owner));
        var theirAnchor = new com.mrgregles.bsp_core.plasma.Anchor(theirTotem, wardFriend, java.util.Set.of(wardFriend));
        h.succeedWhen(() -> {
            TankPartBlockEntity m = master(h);
            m.drain(m.stored());
            var port = (TankPortBlockEntity) h.getBlockEntity(PORT);
            var mine = new com.mrgregles.bsp_core.plasma.PlasmaAccess();
            mine.editors.add(owner);
            mine.wardSafe.add(owner);
            mine.wardSafe.add(wardFriend); // a friend with only Ward on the owner's totem
            var friends = new com.mrgregles.bsp_core.plasma.PlasmaAccess();
            friends.editors.add(wardFriend);
            friends.wardSafe.add(wardFriend);
            port.feed(h.absolutePos(TANK), offered(Buff.ANCHOR, 2), 0, 50, 1_000, mine, anchor, null);
            h.assertTrue(anchor.equals(port.anchor()) && anchor.holdsFor(h.getLevel().getServer(), port.access().editors), "the owner's feed: its projectors may hold chunks");
            port.feed(h.absolutePos(TANK), offered(Buff.ANCHOR, 3), 0, 50, 1_000, friends, theirAnchor, null);
            h.assertTrue(port.access().editors.equals(java.util.Set.of(owner)), "the Ward friend's feed does not change who fed the tank: " + port.access().editors);
            h.assertTrue(anchor.equals(port.anchor()), "nor its Anchor");
            h.assertTrue(anchor.holdsFor(h.getLevel().getServer(), port.access().editors), "so the owner's projectors on it keep holding chunks");
            m.drain(m.stored());
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_totem_naming_the_owner_as_its_friend_does_not_take_over_the_tank(GameTestHelper h) {
        buildTank(h);
        java.util.UUID owner = java.util.UUID.randomUUID(), other = java.util.UUID.randomUUID();
        var totem = net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), h.absolutePos(new BlockPos(0, 1, 0)));
        var otherTotem = net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), h.absolutePos(new BlockPos(1, 1, 0)));
        var anchor = new com.mrgregles.bsp_core.plasma.Anchor(totem, owner, java.util.Set.of(owner));
        var otherAnchor = new com.mrgregles.bsp_core.plasma.Anchor(otherTotem, other, java.util.Set.of(other, owner));
        h.succeedWhen(() -> {
            TankPartBlockEntity m = master(h);
            m.drain(m.stored());
            var port = (TankPortBlockEntity) h.getBlockEntity(PORT);
            var mine = new com.mrgregles.bsp_core.plasma.PlasmaAccess();
            mine.owners.add(owner);
            mine.editors.add(owner);
            var theirs = new com.mrgregles.bsp_core.plasma.PlasmaAccess(); // another player's totem, with the owner on its Upgrades list
            theirs.owners.add(other);
            theirs.editors.add(other);
            theirs.editors.add(owner);
            port.feed(h.absolutePos(TANK), offered(Buff.ANCHOR, 2), 0, 50, 1_000, mine, anchor, null);
            port.feed(h.absolutePos(TANK), offered(Buff.ANCHOR, 3), 0, 50, 1_000, theirs, otherAnchor, null);
            h.assertTrue(port.access().editors.equals(java.util.Set.of(owner)), "their feed does not join the tank's team: " + port.access().editors);
            h.assertTrue(anchor.equals(port.anchor()), "nor replace its Anchor: " + port.anchor());
            var friend = new com.mrgregles.bsp_core.plasma.PlasmaAccess(); // the owner's own Upgrades friend's totem
            java.util.UUID upgrades = java.util.UUID.randomUUID();
            mine.editors.add(upgrades);
            port.feed(h.absolutePos(TANK), offered(Buff.WARD, 1), 0, 50, 1_000, mine, anchor, null);
            friend.owners.add(upgrades);
            friend.editors.add(upgrades);
            port.feed(h.absolutePos(TANK), offered(Buff.WARD, 1), 0, 50, 1_000, friend, null, null);
            h.assertTrue(port.access().editors.contains(upgrades) && port.access().editors.contains(owner), "an Upgrades friend's own totem is trusted: " + port.access().editors);
            m.drain(m.stored());
        });
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 900)
    public void a_projector_fed_from_a_tank_holds_chunks_under_the_anchor_totem(GameTestHelper h) {
        buildTank(h);
        BlockPos base = PORT.above(), proj = base.above();
        h.setBlock(base, ModBlocks.PROJECTOR_BASE.get());
        h.setBlock(proj, ModBlocks.TOTEM_PROJECTOR.get());
        java.util.UUID owner = java.util.UUID.randomUUID();
        var access = new com.mrgregles.bsp_core.plasma.PlasmaAccess();
        access.editors.add(owner);
        access.wardSafe.add(owner);
        var totem = net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), h.absolutePos(new BlockPos(0, 1, 0)));
        var projAt = net.minecraft.core.GlobalPos.of(h.getLevel().dimension(), h.absolutePos(proj));
        int anchorIndex = java.util.Arrays.asList(com.mrgregles.bsp_core.projector.TotemProjectorBlockEntity.SENDABLE).indexOf(Buff.ANCHOR);
        int[] step = {0};
        h.onEachTick(() -> {
            var level = h.getLevel();
            var projector = (com.mrgregles.bsp_core.projector.TotemProjectorBlockEntity) h.getBlockEntity(proj);
            var entry = com.mrgregles.bsp_core.chunk.ChunkLedger.get(level.getServer()).projectors.get(projAt);
            switch (step[0]) {
                case 0 -> { // a tank filled before its Anchor totem was kept: Anchor arrives without it
                    if (!level.getBlockState(h.absolutePos(TANK)).getValue(TankBlock.FORMED)) {
                        return;
                    }
                    TankPartBlockEntity m = master(h);
                    m.drain(m.stored());
                    ((TankPortBlockEntity) h.getBlockEntity(PORT)).feed(h.absolutePos(TANK), offered(Buff.ANCHOR, 2), 0, 50, 50_000, access, null, null);
                    step[0] = 1;
                }
                case 1 -> {
                    if (projector.offered(anchorIndex) > 0) {
                        projector.toggle(anchorIndex); // the owner receives Anchor
                        step[0] = 2;
                    }
                }
                case 2 -> {
                    if (projector.isActive() && projector.anchorWithoutTotem()) {
                        h.assertTrue(entry == null || !entry.on, "Anchor without its totem holds no chunks");
                        ((TankPortBlockEntity) h.getBlockEntity(PORT)).feed(h.absolutePos(TANK), offered(Buff.ANCHOR, 2), 0, 50, 1_000, access, new com.mrgregles.bsp_core.plasma.Anchor(totem, owner, java.util.Set.of(owner)), null);
                        step[0] = 3;
                    }
                }
                case 3 -> {
                    if (entry != null && entry.on) {
                        h.assertTrue(totem.equals(entry.totem), "the ledger lists the projector under the tank's Anchor totem: " + entry.totem);
                        h.assertTrue(!projector.anchorWithoutTotem(), "and the screen no longer says the totem is missing");
                        com.mrgregles.bsp_core.chunk.ChunkLoading.projectorRemoved(level, h.absolutePos(proj));
                        master(h).drain(Long.MAX_VALUE);
                        h.succeed();
                    }
                }
                default -> { }
            }
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_battery_never_carries_anchor(GameTestHelper h) {
        var battery = new net.minecraft.world.item.ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                new net.minecraft.resources.ResourceLocation(BSPCore.MODID, "plasma_battery_4")));
        h.assertTrue(com.mrgregles.bsp_core.plasma.PlasmaItems.isBattery(battery), "a tier IV Plasma Battery");
        var allowed = java.util.Arrays.asList(com.mrgregles.bsp_core.plasma.PlasmaItems.allowed(battery));
        h.assertTrue(!allowed.contains(Buff.ANCHOR) && allowed.contains(Buff.WARD), "a battery may hold Ward but not Anchor: " + allowed);
        com.mrgregles.bsp_core.plasma.PlasmaItems.setPower(battery, Buff.ANCHOR, 3); // stamped before Anchor was taken off batteries
        com.mrgregles.bsp_core.plasma.PlasmaItems.setPower(battery, Buff.WARD, 2);
        int[] p = com.mrgregles.bsp_core.plasma.PlasmaItems.powers(battery);
        h.assertTrue(p[Buff.ANCHOR.ordinal()] == 0 && p[Buff.WARD.ordinal()] == 2, "an old Anchor stamp is ignored; Ward stays");
        h.assertTrue(com.mrgregles.bsp_core.plasma.PlasmaItems.powerCount(battery) == 1, "and takes no power slot");
        h.succeed();
    }
}
