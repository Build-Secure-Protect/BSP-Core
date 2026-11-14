package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.chamber.ChamberLoot;
import com.mrgregles.bsp_core.chamber.ChamberSpawner;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.tank.TankBlock;
import com.mrgregles.bsp_core.tank.TankPartBlockEntity;
import com.mrgregles.bsp_core.tank.TankPortBlockEntity;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** A Mob Chamber's kills drop into the tank's loot store; ports give it to hoppers and pipes; each kill tags 10 mB with its drops' powers. */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class ChamberLootGameTests {
    private static final String EMPTY = "empty", BATCH = "chamber_loot";
    private static final BlockPos TANK = new BlockPos(4, 2, 4);
    private static final int W = 4, H = 5;
    /** A port in the middle of the floor, and the hopper under it. */
    private static final BlockPos PORT = TANK.offset(1, 0, 1), HOPPER = PORT.below();

    private static void build(GameTestHelper h, boolean grate) {
        BlockPos g = TANK.offset(1, 2, 0);
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                for (int z = 0; z < W; z++) {
                    int edges = (x == 0 || x == W - 1 ? 1 : 0) + (y == 0 || y == H - 1 ? 1 : 0) + (z == 0 || z == W - 1 ? 1 : 0);
                    BlockPos p = TANK.offset(x, y, z);
                    if (edges > 0) {
                        Block b = p.equals(PORT) ? ModBlocks.TANK_PORT.get() : grate && p.equals(g) ? ModBlocks.SPAWN_GRATE.get()
                                : edges >= 2 ? ModBlocks.TANK_CASING.get() : ModBlocks.TANK_GLASS.get();
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

    /** A mob of {@code type} marked as {@code m}'s, not in the world: enough for its drops event. */
    private static LivingEntity chamberMob(GameTestHelper h, TankPartBlockEntity m, EntityType<? extends Mob> type) {
        Mob mob = type.create(h.getLevel());
        mob.moveTo(ChamberSpawner.inside(m).getCenter());
        mob.getPersistentData().putLong(ChamberSpawner.TAG, m.master().asLong());
        return mob;
    }

    /** Posts the drops event for {@code mob} with {@code stacks}; returns what was left to fall. */
    private static List<ItemEntity> die(GameTestHelper h, LivingEntity mob, ItemStack... stacks) {
        List<ItemEntity> drops = new ArrayList<>();
        for (ItemStack s : stacks) {
            drops.add(new ItemEntity(h.getLevel(), mob.getX(), mob.getY(), mob.getZ(), s));
        }
        MinecraftForge.EVENT_BUS.post(new LivingDropsEvent(mob, h.getLevel().damageSources().generic(), drops, 0, false));
        return drops;
    }

    private static int count(TankPartBlockEntity m, Item item) {
        int n = 0;
        for (int i = 0; i < m.loot().getSlots(); i++) {
            ItemStack s = m.loot().getStackInSlot(i);
            n += s.is(item) ? s.getCount() : 0;
        }
        return n;
    }

    private static void reset(TankPartBlockEntity m) {
        m.drain(m.stored());
        for (Buff b : Buff.values()) {
            m.drainMob(b, Long.MAX_VALUE);
        }
        for (int i = 0; i < m.loot().getSlots(); i++) {
            m.loot().setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_kills_drops_go_into_the_store_and_tag_ten_mb(GameTestHelper h) {
        build(h, true);
        h.succeedWhen(() -> {
            TankPartBlockEntity m = master(h);
            reset(m);
            m.add(1_000);
            List<ItemEntity> left = die(h, chamberMob(h, m, EntityType.ZOMBIE), new ItemStack(Items.ROTTEN_FLESH, 2), new ItemStack(Items.IRON_INGOT));
            h.assertTrue(left.isEmpty(), "nothing falls on the floor");
            h.assertTrue(count(m, Items.ROTTEN_FLESH) == 2 && count(m, Items.IRON_INGOT) == 1, "both drops are in the store");
            h.assertTrue(m.buffedMb(Buff.UNDEAD_WARD.ordinal()) == ChamberLoot.TAG_MB && m.buffedLevel(Buff.UNDEAD_WARD.ordinal()) == 1, "rotten flesh tags 10 mB with Undead Ward I");
            h.assertTrue(m.pools().plain() == 990 && m.stored() == 1_000, "taken from the plain plasma, none made");
            reset(m);
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_wither_skeletons_bones_are_wither_bones(GameTestHelper h) {
        build(h, true);
        h.succeedWhen(() -> {
            TankPartBlockEntity m = master(h);
            reset(m);
            m.add(1_000);
            die(h, chamberMob(h, m, EntityType.WITHER_SKELETON), new ItemStack(Items.BONE, 2), new ItemStack(Items.COAL));
            h.assertTrue(count(m, ModItems.WITHER_BONE.get()) == 2 && count(m, Items.BONE) == 0 && count(m, Items.COAL) == 1, "two Wither Bones and a coal");
            h.assertTrue(m.buffedMb(Buff.WITHER.ordinal()) == 10 && m.buffedMb(Buff.SCORCH.ordinal()) == 10 && m.buffedMb(Buff.GROWTH.ordinal()) == 0,
                    "Wither and Scorch tagged, not Growth");
            die(h, chamberMob(h, m, EntityType.SKELETON), new ItemStack(Items.BONE, 2));
            h.assertTrue(count(m, Items.BONE) == 2 && m.buffedMb(Buff.GROWTH.ordinal()) == 10, "a skeleton's bones stay bones: Growth");
            reset(m);
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_kill_tag_keeps_a_pools_level_and_needs_plain_plasma(GameTestHelper h) {
        build(h, true);
        h.succeedWhen(() -> {
            TankPartBlockEntity m = master(h);
            reset(m);
            die(h, chamberMob(h, m, EntityType.SKELETON), new ItemStack(Items.BONE));
            h.assertTrue(m.buffedMb(Buff.GROWTH.ordinal()) == 0, "no plain plasma: nothing tagged");
            m.addMob(Buff.GROWTH, 3, 1_000);
            m.add(100);
            die(h, chamberMob(h, m, EntityType.SKELETON), new ItemStack(Items.BONE));
            h.assertTrue(m.buffedMb(Buff.GROWTH.ordinal()) == 1_010 && m.buffedLevel(Buff.GROWTH.ordinal()) == 3, "Growth III stays III with a kill's 10 mB");
            h.assertTrue(m.pools().mbAt(Buff.GROWTH, 3) == 1_000 && m.pools().mbAt(Buff.GROWTH, 1) == 10, "the kill's 10 mB sit at level I, apart");
            reset(m);
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void others_drops_and_a_full_store(GameTestHelper h) {
        build(h, true);
        h.succeedWhen(() -> {
            TankPartBlockEntity m = master(h);
            reset(m);
            LivingEntity stranger = EntityType.ZOMBIE.create(h.getLevel());
            h.assertTrue(die(h, stranger, new ItemStack(Items.ROTTEN_FLESH)).size() == 1 && count(m, Items.ROTTEN_FLESH) == 0, "a mob that is not the chamber's drops as usual");
            for (int i = 0; i < m.loot().getSlots(); i++) {
                m.loot().setStackInSlot(i, new ItemStack(Items.STONE, 64));
            }
            h.assertTrue(die(h, chamberMob(h, m, EntityType.ZOMBIE), new ItemStack(Items.ROTTEN_FLESH)).isEmpty() && count(m, Items.ROTTEN_FLESH) == 0,
                    "a full store: the rest is lost, nothing piles up inside");
            reset(m);
        });
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 300)
    public void a_hopper_under_a_port_empties_the_store(GameTestHelper h) {
        build(h, true);
        h.setBlock(HOPPER, Blocks.HOPPER);
        boolean[] started = {false};
        h.onEachTick(() -> {
            if (!started[0]) {
                if (!h.getLevel().getBlockState(h.absolutePos(TANK)).getValue(TankBlock.FORMED)) {
                    return;
                }
                TankPartBlockEntity m = master(h);
                reset(m);
                h.assertTrue(m.storeLoot(new ItemStack(Items.ROTTEN_FLESH, 3)).isEmpty(), "three rotten flesh in the store");
                var port = (TankPortBlockEntity) h.getBlockEntity(PORT);
                var items = port.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElse(null);
                h.assertTrue(items != null && items.getSlots() == 27, "the port shows the 27-slot store");
                h.assertTrue(items.insertItem(0, new ItemStack(Items.DIRT), false).getCount() == 1, "nothing goes in through a port");
                started[0] = true;
                return;
            }
            if (h.getBlockEntity(HOPPER) instanceof HopperBlockEntity hopper) {
                int n = 0;
                for (int i = 0; i < hopper.getContainerSize(); i++) {
                    n += hopper.getItem(i).is(Items.ROTTEN_FLESH) ? hopper.getItem(i).getCount() : 0;
                }
                if (n == 3) {
                    h.assertTrue(count(master(h), Items.ROTTEN_FLESH) == 0, "the store is empty");
                    hopper.clearContent();
                    h.succeed();
                }
            }
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_broken_tank_spills_its_loot(GameTestHelper h) {
        build(h, true);
        BlockPos glass = TANK.offset(1, 2, 3);
        h.succeedWhen(() -> {
            TankPartBlockEntity m = master(h);
            reset(m);
            m.storeLoot(new ItemStack(Items.GUNPOWDER, 5));
            h.setBlock(glass, Blocks.AIR);
            var spilled = h.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(glass)).inflate(1), e -> e.getItem().is(Items.GUNPOWDER));
            h.assertTrue(spilled.stream().mapToInt(e -> e.getItem().getCount()).sum() == 5, "the five gunpowder fall out where the block broke");
            h.assertTrue(count(m, Items.GUNPOWDER) == 0, "none left in the store");
            spilled.forEach(ItemEntity::discard);
        });
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public void a_chicken_burned_in_a_chamber_leaves_its_meat_in_the_store(GameTestHelper h) {
        build(h, true);
        Mob[] chicken = {null};
        h.onEachTick(() -> {
            ServerLevel sl = h.getLevel();
            if (chicken[0] == null) {
                if (!sl.getBlockState(h.absolutePos(TANK)).getValue(TankBlock.FORMED)) {
                    return;
                }
                TankPartBlockEntity m = master(h);
                reset(m);
                m.add(m.capacity() / 2);
                chicken[0] = ChamberSpawner.spawn(sl, m, EntityType.CHICKEN, sl.getRandom());
                h.assertTrue(chicken[0] != null, "a chicken spawned");
                chicken[0].hurt(ChamberSpawner.plasma(sl), 100f);
                return;
            }
            TankPartBlockEntity m = master(h);
            if (count(m, Items.CHICKEN) + count(m, Items.COOKED_CHICKEN) >= 1) {
                h.assertTrue(sl.getEntitiesOfClass(ItemEntity.class, ChamberSpawner.inside(m).inflate(1)).isEmpty(), "no items on the floor");
                reset(m);
                h.succeed();
            }
        });
    }
}
