package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.chamber.ChamberFeeder;
import com.mrgregles.bsp_core.chamber.ChamberSpawner;
import com.mrgregles.bsp_core.network.ChamberActionPacket;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.tank.TankBlock;
import com.mrgregles.bsp_core.tank.TankPartBlockEntity;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Feeding a power: the drop picked on the CHAMBER tab leaves the loot store one item at a time and turns plain plasma into its power at
 * the picked level: 24,000 mB (a minute of a Projector) an item of worth 1 at level I, half at II, a quarter at III.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class ChamberFeedGameTests {
    private static final String EMPTY = "empty", BATCH = "chamber_feed";
    private static final BlockPos TANK = new BlockPos(4, 1, 4);
    private static final int SIDE = 4;

    private static void build(GameTestHelper h) {
        BlockPos g = TANK.offset(1, 2, 0);
        for (int x = 0; x < SIDE; x++) {
            for (int y = 0; y < SIDE; y++) {
                for (int z = 0; z < SIDE; z++) {
                    int edges = (x == 0 || x == SIDE - 1 ? 1 : 0) + (y == 0 || y == SIDE - 1 ? 1 : 0) + (z == 0 || z == SIDE - 1 ? 1 : 0);
                    BlockPos p = TANK.offset(x, y, z);
                    if (edges > 0) {
                        Block b = p.equals(g) ? ModBlocks.SPAWN_GRATE.get() : edges >= 2 ? ModBlocks.TANK_CASING.get() : ModBlocks.TANK_GLASS.get();
                        h.setBlock(p, b);
                    }
                }
            }
        }
    }

    private static TankPartBlockEntity master(GameTestHelper h) {
        h.assertTrue(h.getLevel().getBlockState(h.absolutePos(TANK)).getValue(TankBlock.FORMED), "the tank formed");
        TankPartBlockEntity m = h.getBlockEntity(TANK) instanceof TankPartBlockEntity t ? t.masterEntity() : null;
        h.assertTrue(m != null && m.isMaster() && m.chamber(), "the chamber has a master");
        return m;
    }

    /** Empty: no plasma, no loot, nothing picked, and no mobs to drop or spawn into it (the spawn timer pushed far off). */
    private static void reset(GameTestHelper h, TankPartBlockEntity m) {
        m.drain(m.stored());
        for (Buff b : Buff.values()) {
            m.drainMob(b, Long.MAX_VALUE);
        }
        for (int i = 0; i < m.loot().getSlots(); i++) {
            m.loot().setStackInSlot(i, ItemStack.EMPTY);
        }
        m.setFeed(null, 1);
        m.setNextSpawnAt(Long.MAX_VALUE);
        h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, ChamberSpawner.inside(m)).forEach(net.minecraft.world.entity.Mob::discard);
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void an_item_tags_a_projector_minute_at_level_one_less_at_two_and_three(GameTestHelper h) {
        build(h);
        h.succeedWhen(() -> {
            TankPartBlockEntity m = master(h);
            reset(h, m);
            m.add(80_000);
            m.storeLoot(new ItemStack(Items.BONE, 3));
            m.setFeed(Items.BONE, 1);
            h.assertTrue(ChamberFeeder.feed(m, 64) == 3, "three bones fed");
            h.assertTrue(m.pools().mbAt(Buff.GROWTH, 1) == 72_000 && m.pools().plain() == 8_000 && ChamberFeeder.stocked(m, Items.BONE) == 0,
                    "24,000 mB of Growth I a bone, from the plain plasma");
            m.add(24_000);
            m.storeLoot(new ItemStack(Items.BONE, 4));
            m.setFeed(Items.BONE, 2);
            h.assertTrue(ChamberFeeder.feed(m, 64) == 2 && m.pools().mbAt(Buff.GROWTH, 2) == 24_000, "level II: 12,000 mB a bone, two bones for 24,000");
            m.setFeed(Items.BONE, 3);
            m.add(12_000);
            h.assertTrue(ChamberFeeder.feed(m, 64) == 2 && m.pools().mbAt(Buff.GROWTH, 3) == 12_000, "level III: 6,000 mB a bone");
            h.assertTrue(m.buffedLevel(Buff.GROWTH.ordinal()) == 3 && m.buffedMb(Buff.GROWTH.ordinal()) == 108_000, "the pool reads Growth III, all levels together");
            reset(h, m);
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void rare_drops_tag_more_and_spider_eyes_far_less(GameTestHelper h) {
        build(h);
        h.succeedWhen(() -> {
            TankPartBlockEntity m = master(h);
            reset(h, m);
            m.add(140_000);
            m.storeLoot(new ItemStack(Items.ENDER_PEARL));
            m.setFeed(Items.ENDER_PEARL, 1);
            h.assertTrue(ChamberFeeder.feed(m, 64) == 1 && m.pools().mbAt(Buff.WARP_WARD, 1) == 120_000, "a pearl: five projector-minutes of Warp Ward");
            m.storeLoot(new ItemStack(Items.SPIDER_EYE, 20));
            m.setFeed(Items.SPIDER_EYE, 1);
            h.assertTrue(ChamberFeeder.feed(m, 64) == 16 && m.pools().mbAt(Buff.WIREFRAME, 1) == 19_200, "a spider eye: 1,200 mB; the last 20,000 mB plain covers 16");
            reset(h, m);
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void feeding_waits_for_plasma_and_stops_when_the_items_run_out(GameTestHelper h) {
        build(h);
        h.succeedWhen(() -> {
            TankPartBlockEntity m = master(h);
            reset(h, m);
            m.add(50_000);
            m.storeLoot(new ItemStack(Items.ROTTEN_FLESH, 5));
            m.setFeed(Items.ROTTEN_FLESH, 1);
            h.assertTrue(ChamberFeeder.feed(m, 64) == 2 && ChamberFeeder.stocked(m, Items.ROTTEN_FLESH) == 3 && m.pools().plain() == 2_000, "plasma for two");
            h.assertTrue(ChamberFeeder.feed(m, 64) == 0, "then it waits: 2,000 mB will not cover a whole item");
            m.addMob(Buff.KEEP, 1, 1_000_000);
            h.assertTrue(ChamberFeeder.feed(m, 64) == 0, "another pool is not plain plasma");
            m.add(1_000_000);
            h.assertTrue(ChamberFeeder.feed(m, 64) == 3 && ChamberFeeder.feed(m, 64) == 0, "with plasma it feeds the last three, then has none to feed");
            m.storeLoot(new ItemStack(Items.ROTTEN_FLESH, 40));
            m.add(10_000_000);
            h.assertTrue(ChamberFeeder.feed(m, 16) == 16, "no more than the cap a second");
            reset(h, m);
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void the_tab_picks_only_drops_with_a_power(GameTestHelper h) {
        build(h);
        h.succeedWhen(() -> {
            TankPartBlockEntity m = master(h);
            reset(h, m);
            h.assertFalse(ChamberActionPacket.apply(m, ChamberActionPacket.FEED, 1, "minecraft:feather"), "a feather has no power");
            h.assertFalse(ChamberActionPacket.apply(m, ChamberActionPacket.FEED, 4, "minecraft:bone"), "there is no level IV");
            h.assertFalse(ChamberActionPacket.apply(m, ChamberActionPacket.FEED, 1, "nomod:thing"), "an item that does not exist");
            h.assertTrue(m.feedItem() == null, "nothing picked");
            h.assertTrue(ChamberActionPacket.apply(m, ChamberActionPacket.FEED, 2, "minecraft:gunpowder") && m.feedItem() == Items.GUNPOWDER && m.feedLevel() == 2, "gunpowder at II");
            CompoundTag tag = m.saveWithoutMetadata();
            h.assertTrue(tag.getString("FeedItem").equals("minecraft:gunpowder") && tag.getByte("FeedLevel") == 2, "saved with the tank");
            h.assertTrue(ChamberActionPacket.apply(m, ChamberActionPacket.MODE, 1, "") && m.chamberMode() == ChamberSpawner.Mode.HOSTILE, "the switch: HOSTILE");
            h.assertFalse(ChamberActionPacket.apply(m, ChamberActionPacket.MODE, 9, ""), "no fourth setting");
            h.assertTrue(ChamberActionPacket.apply(m, ChamberActionPacket.FEED_OFF, 0, "") && m.feedItem() == null, "feeding off");
            m.setChamberMode(ChamberSpawner.Mode.ALL);
        });
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 100)
    public void a_chamber_feeds_on_its_own_each_second(GameTestHelper h) {
        build(h);
        boolean[] set = {false};
        h.onEachTick(() -> {
            if (!set[0]) {
                if (!h.getLevel().getBlockState(h.absolutePos(TANK)).getValue(TankBlock.FORMED)) {
                    return;
                }
                TankPartBlockEntity m = master(h);
                reset(h, m);
                m.add(26_000);
                m.storeLoot(new ItemStack(Items.STRING, 2));
                m.setFeed(Items.STRING, 1);
                set[0] = true;
                return;
            }
            TankPartBlockEntity m = master(h);
            if (m.pools().mbAt(Buff.SNARE, 1) == 24_000) {
                h.assertTrue(ChamberFeeder.stocked(m, Items.STRING) == 1 && m.pools().plain() == 2_000, "one string fed; the other waits for plasma");
                reset(h, m);
                h.succeed();
            }
        });
    }
}
