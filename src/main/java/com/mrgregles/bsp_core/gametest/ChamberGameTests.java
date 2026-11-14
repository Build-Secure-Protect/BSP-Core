package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.chamber.MobPowers;
import com.mrgregles.bsp_core.plasma.PlasmaStore;
import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Branch;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** The Plasma Tank Mob Chamber's powers as data: where they sit among the powers, that no totem can hold one, and the drop table. */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class ChamberGameTests {
    private static final String EMPTY = "empty", BATCH = "chamber";
    private static final List<Buff> MOB = List.of(Buff.GROWTH, Buff.WITHER, Buff.WARP_WARD, Buff.UNDEAD_WARD, Buff.HASTE, Buff.SNARE, Buff.WIREFRAME,
            Buff.KEEP, Buff.SCORCH, Buff.FIRE_WARD, Buff.MENDING, Buff.LANTERN, Buff.TIDE);

    @GameTest(template = EMPTY, batch = BATCH)
    public void mob_powers_come_after_every_totem_power(GameTestHelper h) {
        // ordinals are saved in tanks and sent over the network: the totem's powers keep theirs
        h.assertTrue(Buff.DAMAGE.ordinal() == 0 && Buff.OUTPUT.ordinal() == 18 && Buff.TRIPWIRE.ordinal() == 31, "the totem's powers keep their places");
        h.assertTrue(TotemUpgrades.TOTEM_BUFFS.size() == 32 && TotemUpgrades.TOTEM_BUFFS.get(31) == Buff.TRIPWIRE, "32 totem powers, Tripwire last");
        h.assertTrue(Buff.values().length == 32 + MOB.size(), "thirteen mob powers after them");
        for (int i = 0; i < MOB.size(); i++) {
            Buff b = MOB.get(i);
            h.assertTrue(b.ordinal() == 32 + i && b.branch() == Branch.MOB && !b.onTotem(), b + " is a mob power at " + (32 + i));
            h.assertTrue(b.maxLevel() == 3 && !b.placedOnly && b.parent() == null, b + " has levels I to III and no place on a path");
            h.assertTrue(net.minecraft.locale.Language.getInstance().has(b.translationKey()), b + " has a name");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void no_totem_can_hold_a_mob_power(GameTestHelper h) {
        var player = h.makeMockPlayer();
        ItemStack totem = new ItemStack(ModItems.SHATTER_TOTEM.get());
        TotemUpgrades.setTier(totem, TotemUpgrades.MAX_TIER);
        for (Buff b : MOB) {
            h.assertTrue(TotemUpgrades.price(b, 0) == null, b + " has no price");
            h.assertFalse(TotemUpgrades.unlocked(b, x -> 9, TotemUpgrades.MAX_TIER), b + " never opens on the tree");
            h.assertTrue(TotemUpgrades.whyNot(b, x -> 0, TotemUpgrades.MAX_TIER, player) != null, b + " cannot be bought");
            h.assertTrue(com.mrgregles.bsp_core.client.TreeLayout.at(b) == null, b + " has no node on the tree");
            TotemUpgrades.setLevel(totem, b, 2);
            h.assertTrue(TotemUpgrades.getLevel(totem, b) == 0, b + " cannot be set on a held totem");
        }
        h.assertFalse(TotemUpgrades.TOTEM_BUFFS.stream().anyMatch(MOB::contains), "the totem's list has no mob power");
        h.assertTrue(java.util.Arrays.stream(com.mrgregles.bsp_core.projector.TotemProjectorBlockEntity.SENDABLE).noneMatch(MOB::contains),
                "no mob power takes a projector channel");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_mob_power_rides_plasma_and_an_old_save_still_loads(GameTestHelper h) {
        PlasmaStore store = new PlasmaStore(() -> 1_000_000L);
        int[] offered = new int[Buff.values().length];
        offered[Buff.GROWTH.ordinal()] = 2;
        long[][] mob = com.mrgregles.bsp_core.plasma.MobPlasma.empty();
        mob[Buff.GROWTH.ordinal()][2] = 10_000;
        store.add(10_000, offered, mob);
        h.assertTrue(store.mob(Buff.GROWTH, 2) == 10_000 && store.offered()[Buff.GROWTH.ordinal()] == 0 && store.buffedMb(Buff.GROWTH.ordinal()) == 0,
                "plasma carries Growth II by amount, never in the list of powers");
        // a tank saved before the mob powers has 32 power slots
        CompoundTag old = new CompoundTag();
        old.putLong("T", 5_000);
        long[] mb = new long[32];
        int[] lv = new int[32];
        mb[Buff.WARD.ordinal()] = 5_000;
        lv[Buff.WARD.ordinal()] = 3;
        old.putLongArray("TBuffMb", mb);
        old.putIntArray("TBuffLevel", lv);
        PlasmaStore loaded = new PlasmaStore(() -> 1_000_000L);
        loaded.load(old, "T");
        h.assertTrue(loaded.stored() == 5_000 && loaded.buffedMb(Buff.WARD.ordinal()) == 5_000 && loaded.offered()[Buff.WARD.ordinal()] == 3, "Ward III kept from the old save");
        h.assertTrue(MOB.stream().allMatch(b -> loaded.buffedMb(b.ordinal()) == 0), "no mob power in an old save");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void the_drop_table_names_each_drop_its_power(GameTestHelper h) {
        var bone = MobPowers.of(Items.BONE);
        h.assertTrue(bone != null && bone.power() == Buff.GROWTH && bone.worth() == 1, "a bone gives Growth, worth 1");
        var wither = MobPowers.of(ModItems.WITHER_BONE.get());
        h.assertTrue(wither != null && wither.power() == Buff.WITHER, "a Wither Bone gives Wither");
        var pearl = MobPowers.of(new ItemStack(Items.ENDER_PEARL));
        h.assertTrue(pearl != null && pearl.power() == Buff.WARP_WARD && pearl.worth() == 5, "a pearl is rare: Warp Ward, worth 5");
        h.assertTrue(MobPowers.of(Items.GLOWSTONE_DUST).power() == Buff.LANTERN && MobPowers.of(Items.PRISMARINE_SHARD).power() == Buff.TIDE
                && MobPowers.of(Items.COAL).power() == Buff.SCORCH && MobPowers.of(Items.BLAZE_ROD).power() == Buff.SCORCH, "glowstone Lantern, prismarine Tide, coal and blaze rod Scorch");
        h.assertTrue(MobPowers.of(Items.FEATHER) == null && MobPowers.of(ItemStack.EMPTY) == null, "a feather is loot only");
        java.util.Set<Buff> covered = java.util.EnumSet.noneOf(Buff.class);
        MobPowers.table().values().forEach(e -> covered.add(e.power()));
        h.assertTrue(covered.containsAll(MOB), "every mob power has a drop: missing " + MOB.stream().filter(b -> !covered.contains(b)).toList());
        // plasma one item tags: a projector-minute at I, half at II, a quarter at III; twenty spider eyes do what one bone does
        h.assertTrue(MobPowers.mbTagged(bone, 1) == 24_000 && MobPowers.mbTagged(bone, 2) == 12_000 && MobPowers.mbTagged(bone, 3) == 6_000, "bone: 24,000 / 12,000 / 6,000 mB");
        h.assertTrue(MobPowers.mbTagged(pearl, 1) == 120_000, "a pearl tags five times a bone");
        h.assertTrue(MobPowers.mbTagged(MobPowers.of(Items.SPIDER_EYE), 1) == 1_200, "a spider eye tags a twentieth");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_drop_line_must_name_a_mob_power_and_a_worth(GameTestHelper h) {
        h.assertTrue(MobPowers.parse("minecraft:bone=growth,1") != null, "a good line reads");
        h.assertTrue(MobPowers.parse(" minecraft:bone = Growth , 0.5 ").worth() == 0.5, "spaces and capitals are fine");
        h.assertTrue(MobPowers.parse("minecraft:bone=damage,1") == null, "a totem power is not a mob power");
        h.assertTrue(MobPowers.parse("minecraft:bone=growth,0") == null && MobPowers.parse("minecraft:bone=growth,-1") == null
                && MobPowers.parse("minecraft:bone=growth,x") == null, "the worth is a number above 0");
        h.assertTrue(MobPowers.parse("minecraft:bone") == null && MobPowers.parse("=growth,1") == null && MobPowers.parse("minecraft:bone=growth,") == null, "the shape is item=power,worth");
        h.assertTrue(MobPowers.parse("nomod:thing=keep,1") != null && MobPowers.table().keySet().stream().noneMatch(i -> "nomod".equals(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(i).getNamespace())),
                "a line for a missing mod reads, and is left out of the table");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void the_recipe_viewers_get_a_page_per_mob(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var roster = com.mrgregles.bsp_core.chamber.ChamberRoster.get(server);
        var zombie = roster.stream().filter(e -> e.entity().equals("minecraft:zombie")).findFirst().orElse(null);
        h.assertTrue(zombie != null && zombie.hostile() && zombie.places() > 10, "a zombie, hostile, spawns in many places: " + (zombie == null ? 0 : zombie.places()));
        h.assertTrue(zombie.biomes().size() == Math.min(zombie.places() - zombie.structures().size(), com.mrgregles.bsp_core.chamber.ChamberRoster.LISTED)
                && zombie.biomes().stream().sorted().toList().equals(zombie.biomes()), "the first " + com.mrgregles.bsp_core.chamber.ChamberRoster.LISTED + " biomes are listed, in order, the rest counted");
        h.assertTrue(zombie.side() == 3 && zombie.height() == 4, "it needs a tank 3 wide and 4 tall: " + zombie.side() + " x " + zombie.height());
        h.assertTrue(zombie.drop().equals("minecraft:rotten_flesh") && zombie.power() == Buff.UNDEAD_WARD.ordinal(), "its drop and power: " + zombie.drop());
        var blaze = roster.stream().filter(e -> e.entity().equals("minecraft:blaze")).findFirst().orElse(null);
        h.assertTrue(blaze != null && blaze.structures().contains("minecraft:fortress"), "a blaze comes from the nether fortress's own list");
        var cow = roster.stream().filter(e -> e.entity().equals("minecraft:cow")).findFirst().orElse(null);
        h.assertTrue(cow != null && !cow.hostile() && cow.power() < 0, "a cow, passive, loot only");
        var ender = roster.stream().filter(e -> e.entity().equals("minecraft:enderman")).findFirst().orElse(null);
        h.assertTrue(ender != null && ender.height() == 5, "an enderman needs a tank 5 tall");
        h.assertTrue(roster.stream().noneMatch(e -> e.entity().equals("minecraft:armor_stand")), "nothing outside the spawn lists");
        // over the wire whole
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        com.mrgregles.bsp_core.network.ChamberRosterPacket.encode(new com.mrgregles.bsp_core.network.ChamberRosterPacket(roster), buf);
        var back = com.mrgregles.bsp_core.network.ChamberRosterPacket.decode(buf).roster();
        h.assertTrue(back.equals(roster), "the roster is read back the same");
        // the pages the viewers build from it
        var before = com.mrgregles.bsp_core.chamber.ChamberRoster.client();
        try {
            com.mrgregles.bsp_core.chamber.ChamberRoster.receive(back);
            var pages = com.mrgregles.bsp_core.compat.BspProcesses.mobChamber();
            h.assertTrue(pages.size() == back.size(), "a page per mob: " + pages.size() + " of " + back.size());
            var page = pages.stream().filter(p -> p.inputs().get(0).get(0).is(net.minecraft.world.item.Items.ZOMBIE_SPAWN_EGG)).findFirst().orElse(null);
            h.assertTrue(page != null && page.outputs().get(0).is(net.minecraft.world.item.Items.ROTTEN_FLESH) && page.catalysts().get(0).get(0).is(ModBlocksAccess.grate()),
                    "the zombie's page: its egg, the Spawn Grate, rotten flesh");
            h.assertTrue(page.lines().size() == 3 && page.inputTips().get(0).size() > 2 && page.outputTips().size() == 1, "three lines, where it spawns on the egg, the power on the drop");
            h.assertTrue(com.mrgregles.bsp_core.compat.BspProcesses.all().containsKey("mob_chamber"), "the viewers list it as its own category");
        } finally {
            com.mrgregles.bsp_core.chamber.ChamberRoster.receive(before);
        }
        h.succeed();
    }

    private static final class ModBlocksAccess {
        static net.minecraft.world.item.Item grate() {
            return com.mrgregles.bsp_core.registry.ModBlocks.SPAWN_GRATE.get().asItem();
        }
    }
}
