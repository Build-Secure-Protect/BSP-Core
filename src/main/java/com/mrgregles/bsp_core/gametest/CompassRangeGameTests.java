package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.compass.TotemCompassItem;
import com.mrgregles.bsp_core.compass.TotemCompassItem.Range;
import com.mrgregles.bsp_core.compass.TotemCompassMenu;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.totem.TotemRadiation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GameTests for the Totem Compass's range ladders: a rival-tracking compass finds totems only within its reach across and up or
 * down, its own owner's totems at any distance; upgrades cost their coins and stay on the compass. The totems are ledger entries
 * far from the test area (the needle reads only the ledger), removed in a {@code finally}.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class CompassRangeGameTests {
    private static final String EMPTY = "empty", BATCH = "compass_range";

    /** A spot no other test uses, so only this test's totems are near: far out in the overworld, a fresh one per player. */
    private static BlockPos farAway(ServerPlayer p) {
        int k = Math.floorMod(p.getUUID().hashCode(), 1000);
        return new BlockPos(2_000_000 + k * 6_000, 64, -2_000_000);
    }

    private static GlobalPos at(GameTestHelper h, BlockPos pos) {
        return GlobalPos.of(h.getLevel().dimension(), pos);
    }

    /** A placed rival totem at full radiation (Tier V and 116 upgrade levels: level 120), so only the compass's reach decides here. */
    private static void bright(TotemLedger ledger, UUID owner, GlobalPos p) {
        ledger.recordPlaced(owner, p, 4);
        ledger.recordLevels(p, java.util.Map.of("output", 116)); // the ledger only adds the levels up: 4 + 116 = 120
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void compass_finds_rival_totems_only_within_its_reach(GameTestHelper h) {
        var server = h.getLevel().getServer();
        TotemLedger ledger = TotemLedger.get(server);
        ServerPlayer seeker = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        UUID rival = UUID.randomUUID();
        BlockPos here = farAway(seeker);
        seeker.setPos(here.getX() + 0.5, here.getY(), here.getZ() + 0.5);
        List<GlobalPos> placed = new ArrayList<>();
        try {
            ItemStack compass = new ItemStack(ModItems.TOTEM_COMPASS.get());
            h.assertTrue(TotemCompassItem.rangeBlocks(compass, Range.ACROSS) == 100 && TotemCompassItem.rangeBlocks(compass, Range.DEPTH) == 10,
                    "a new compass reaches 100 across and 10 up or down");
            GlobalPos far = at(h, here.offset(150, 0, 0)), diagonal = at(h, here.offset(80, 0, 80)), deep = at(h, here.offset(20, -30, 0));
            for (GlobalPos p : List.of(far, diagonal, deep)) {
                bright(ledger, rival, p);
                placed.add(p);
            }
            h.assertTrue(TotemCompassItem.nearest(seeker, true, compass) == null,
                    "nothing within 100 across and 10 up or down: 150 east is too far, 80/80 is 113 away on the ground (a circle), 30 down is too deep");

            compass.getOrCreateTag().putInt(TotemCompassItem.RANGE_ACROSS, 1); // 250 across
            h.assertTrue(diagonal.pos().equals(TotemCompassItem.nearest(seeker, true, compass)),
                    "at 250 across the nearest one on the same level is found (the deep one stays out of reach): " + TotemCompassItem.nearest(seeker, true, compass));
            compass.getOrCreateTag().putInt(TotemCompassItem.RANGE_DEPTH, 2); // 35 up or down
            h.assertTrue(deep.pos().equals(TotemCompassItem.nearest(seeker, true, compass)), "at 35 up or down the deep one, nearest of all, is found");

            BlockPos above = here.offset(0, 36, 0);
            bright(ledger, rival, at(h, above));
            placed.add(at(h, above));
            h.assertTrue(deep.pos().equals(TotemCompassItem.nearest(seeker, true, compass)), "36 up is beyond 35 up or down");

            UUID mine = seeker.getUUID();
            GlobalPos home = at(h, here.offset(3_000, 120, 0));
            ledger.recordPlaced(mine, home);
            placed.add(home);
            ItemStack fresh = new ItemStack(ModItems.TOTEM_COMPASS.get());
            h.assertTrue(home.pos().equals(TotemCompassItem.nearest(seeker, false, fresh)), "a new compass finds its owner's own totem 3,000 away and 120 up");
        } finally {
            placed.forEach(ledger::recordRemoved);
        }
        h.succeed();
    }

    /** A spot for the radiation tests, apart from every other test's totems (the far south-east), a fresh one per player. */
    private static BlockPos radiationSpot(ServerPlayer p) {
        int k = Math.floorMod(p.getUUID().hashCode(), 1000);
        return new BlockPos(2_000_000 + k * 6_000, 64, 2_000_000);
    }

    /** A rival's Tier IV totem with 60 upgrade levels: radiation 63, seen from 689 blocks. */
    private static GlobalPos midTotem(GameTestHelper h, TotemLedger ledger, UUID rival, BlockPos at) {
        GlobalPos totem = at(h, at);
        ledger.recordPlaced(rival, totem, 3);
        ledger.recordLevels(totem, java.util.Map.of("output", 60));
        return totem;
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void compass_feels_radiation_from_a_rival_totem_outside_its_reach(GameTestHelper h) {
        var server = h.getLevel().getServer();
        TotemLedger ledger = TotemLedger.get(server);
        ServerPlayer seeker = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        UUID rival = UUID.randomUUID();
        BlockPos here = radiationSpot(seeker);
        GlobalPos totem = midTotem(h, ledger, rival, here);
        ItemStack compass = new ItemStack(ModItems.TOTEM_COMPASS.get());
        seeker.setItemInHand(InteractionHand.MAIN_HAND, compass);
        long now = System.currentTimeMillis(); // one clock for the whole test, so the day cannot change under it
        try {
            int seen = TotemRadiation.seenAt(server, ledger, rival, totem);
            h.assertTrue(seen == 689, "radiation 63 is seen from 689 blocks: " + seen);
            BlockPos spot = TotemRadiation.feltFrom(totem, seen, now);
            double dx = spot.getX() - here.getX(), dy = spot.getY() - here.getY(), dz = spot.getZ() - here.getZ(), len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double ux = dx / len, uy = dy / len, uz = dz / len;
            h.assertTrue(Math.abs(len - 0.2 * seen) <= 2, "the day's point is a fifth of 689 from the totem: " + len);
            h.assertTrue(Math.abs(dy) <= 0.2 * seen * Math.sin(Math.PI / 3) + 1, "and at most 60 degrees up or down: " + dy);
            java.util.function.Consumer<Integer> standAt = d -> seeker.setPos(here.getX() + ux * d + 0.5, here.getY() + uy * d, here.getZ() + uz * d + 0.5);

            standAt.accept(400);
            h.assertTrue(TotemCompassItem.nearest(seeker, true, compass) == null, "400 out is far beyond a new compass's 100 across");
            h.assertTrue(TotemCompassItem.radiationNearby(seeker, compass, now), "but the totem's radiation is detected 400 out");
            standAt.accept(-400);
            h.assertTrue(TotemCompassItem.radiationNearby(seeker, compass, now), "and 400 out on the other side (538 from the point)");
            compass.getOrCreateTag().putLong(TotemCompassItem.TRACK_UNTIL, now + 60_000);
            TotemCompassItem.aim(seeker, compass, now);
            h.assertTrue(TotemCompassItem.target(compass) == null && TotemCompassItem.radiationDetected(compass),
                    "tracking: no needle target, the radiation-detected state is set");
            TotemCompassMenu menu = new TotemCompassMenu(1, seeker.getInventory(), InteractionHand.MAIN_HAND);
            h.assertTrue(menu.radiationDetected() && !menu.noneInReach(), "the screen reads the radiation-detected state");

            standAt.accept(780);
            h.assertTrue(TotemCompassItem.radiationNearby(seeker, compass, now), "780 out on the point's side is detected (642 from the point)");
            standAt.accept(-600);
            h.assertTrue(!TotemCompassItem.radiationNearby(seeker, compass, now), "600 out on the other side is not (738 from the point)");
            TotemCompassItem.aim(seeker, compass, now);
            h.assertTrue(!TotemCompassItem.radiationDetected(compass) && new TotemCompassMenu(2, seeker.getInventory(), InteractionHand.MAIN_HAND).noneInReach(),
                    "nothing detected: the state clears and the screen says none in range");

            seeker.setPos(here.getX() + 300.5, here.getY(), here.getZ() + 0.5);
            compass.getOrCreateTag().putInt(TotemCompassItem.RANGE_ACROSS, 3); // 900 across
            TotemCompassItem.aim(seeker, compass, now);
            h.assertTrue(TotemCompassItem.target(compass) != null && !TotemCompassItem.radiationDetected(compass),
                    "with the totem in reach and seen the needle points again, and the state is gone");

            compass.getOrCreateTag().putInt(TotemCompassItem.RANGE_ACROSS, 0);
            standAt.accept(400);
            TotemCompassItem.aim(seeker, compass, now);
            h.assertTrue(TotemCompassItem.radiationDetected(compass), "back out of reach: detected again");
            compass.getOrCreateTag().putLong(TotemCompassItem.TRACK_UNTIL, now - 1_000);
            h.assertTrue(!TotemCompassItem.radiationDetected(compass), "the state ends with the tracking time, before the next update");
            TotemCompassItem.aim(seeker, compass, now);
            h.assertTrue(!compass.getOrCreateTag().contains(TotemCompassItem.RADIATION), "not tracking: the update clears it");
        } finally {
            ledger.recordRemoved(totem);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void compass_detects_radiation_with_the_totem_in_reach_but_unseen(GameTestHelper h) {
        var server = h.getLevel().getServer();
        TotemLedger ledger = TotemLedger.get(server);
        ServerPlayer seeker = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        UUID rival = UUID.randomUUID();
        BlockPos here = radiationSpot(seeker).offset(0, 0, 3_000);
        GlobalPos totem = midTotem(h, ledger, rival, here);
        ItemStack compass = new ItemStack(ModItems.TOTEM_COMPASS.get());
        compass.getOrCreateTag().putInt(TotemCompassItem.RANGE_ACROSS, 3); // 900 across
        compass.getOrCreateTag().putInt(TotemCompassItem.RANGE_DEPTH, 6); // 150 up or down
        long now = System.currentTimeMillis();
        try {
            BlockPos spot = TotemRadiation.feltFrom(totem, 689, now);
            double dx = spot.getX() - here.getX(), dz = spot.getZ() - here.getZ(), flat = Math.hypot(dx, dz);
            // on the point's side, level with it and 669 beyond it: in reach (at most 807 across, 120 up or down), more than 689
            // from the totem itself, detected from the point
            double d = flat + 669;
            seeker.setPos(here.getX() + dx / flat * d + 0.5, spot.getY(), here.getZ() + dz / flat * d + 0.5);
            BlockPos at = seeker.blockPosition();
            h.assertTrue(at.distSqr(here) > 689.0 * 689.0 && TotemCompassItem.inReach(at, here, 900, 150), "the totem is in reach but too far to be seen");
            h.assertTrue(TotemCompassItem.nearest(seeker, true, compass) == null, "so the needle has nothing to point at");
            h.assertTrue(TotemCompassItem.radiationNearby(seeker, compass, now), "and its radiation is still detected, in reach or not");
        } finally {
            ledger.recordRemoved(totem);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "compass_radiation_off")
    public void compass_detects_no_radiation_with_radiation_turned_off(GameTestHelper h) {
        var server = h.getLevel().getServer();
        TotemLedger ledger = TotemLedger.get(server);
        ServerPlayer seeker = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        BlockPos here = radiationSpot(seeker).offset(0, 0, -3_000);
        GlobalPos totem = midTotem(h, ledger, UUID.randomUUID(), here);
        ItemStack compass = new ItemStack(ModItems.TOTEM_COMPASS.get());
        boolean on = com.mrgregles.bsp_core.BSPConfig.RADIATION_ENABLED.get();
        try {
            seeker.setPos(here.getX() + 300.5, here.getY(), here.getZ() + 0.5);
            h.assertTrue(TotemCompassItem.radiationNearby(seeker, compass), "radiation on: detected 300 out");
            com.mrgregles.bsp_core.BSPConfig.RADIATION_ENABLED.set(false);
            h.assertTrue(!TotemCompassItem.radiationNearby(seeker, compass), "radiation off: never detected");
        } finally {
            com.mrgregles.bsp_core.BSPConfig.RADIATION_ENABLED.set(on);
            ledger.recordRemoved(totem);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void the_compass_sells_no_upgrades_and_reads_the_levels_tuned_into_it(GameTestHelper h) {
        ServerPlayer player = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        ItemStack compass = new ItemStack(ModItems.TOTEM_COMPASS.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, compass);
        h.assertTrue(TotemCompassItem.maxRangeLevel() == 6, "six levels on each ladder: " + TotemCompassItem.maxRangeLevel());
        h.assertTrue(TotemCompassItem.rangeBlocks(Range.ACROSS, 6) == 2500 && TotemCompassItem.rangeBlocks(Range.DEPTH, 6) == 150, "the top levels reach 2,500 and 150");
        h.assertTrue(TotemCompassItem.rangeCost(5) == 40 && TotemCompassItem.rangeCoin(5) == CoinTier.ILLYRIUM, "the last level costs 40 Illyrium");

        // an untuned compass tracks at the base reach and cooldown
        h.assertTrue(TotemCompassItem.rangeBlocks(compass, Range.ACROSS) == 100 && TotemCompassItem.rangeBlocks(compass, Range.DEPTH) == 10
                && TotemCompassItem.cooldownSeconds(compass) == 600 && !TotemCompassItem.isTuned(compass), "untuned: 100 across, 10 up or down, 10 minutes");
        h.assertTrue(TotemCompassMenu.tuneState(compass) == TotemCompassMenu.UNTUNED, "the screen says untuned");

        // the old buy keys (6, 7, 8) do nothing, and no coins are taken
        player.getInventory().add(new ItemStack(CoinTier.COPPER.coin(), 20));
        TotemCompassMenu menu = new TotemCompassMenu(1, player.getInventory(), InteractionHand.MAIN_HAND);
        for (int id : new int[] {6, 7, 8}) {
            h.assertTrue(!menu.clickMenuButton(player, id), "button " + id + " is not a key of the compass any more");
        }
        h.assertTrue(player.getInventory().countItem(CoinTier.COPPER.coin()) == 20 && TotemCompassItem.rangeLevel(compass, Range.ACROSS) == 0,
                "no coins taken, no level bought");

        // a tuned compass reads its levels
        TotemCompassItem.applyTune(compass, 2, 1, 3);
        h.assertTrue(TotemCompassItem.rangeBlocks(compass, Range.ACROSS) == 500 && TotemCompassItem.rangeBlocks(compass, Range.DEPTH) == 20
                && TotemCompassItem.cooldownSeconds(compass) == 420 && TotemCompassItem.isTuned(compass), "tuned 2, 1, 3: 500 across, 20 up or down, 7 minutes");
        h.assertTrue(menu.rangeLevel(Range.ACROSS) == 2 && menu.rangeLevel(Range.DEPTH) == 1 && menu.tuneState() == TotemCompassMenu.TUNED,
                "the screen reads the tuned levels");
        TotemCompassItem.applyTune(compass, 1, 0, 0);
        h.assertTrue(TotemCompassItem.rangeLevel(compass, Range.ACROSS) == 2 && TotemCompassItem.level(compass) == 3, "a tune never lowers a level");

        // a compass upgraded before the tuner: levels and no tuned mark
        ItemStack old = new ItemStack(ModItems.TOTEM_COMPASS.get());
        old.getOrCreateTag().putInt(TotemCompassItem.RANGE_ACROSS, 4);
        h.assertTrue(TotemCompassMenu.tuneState(old) == TotemCompassMenu.UPGRADED_BEFORE_TUNER && TotemCompassItem.rangeBlocks(old, Range.ACROSS) == 1400,
                "an old upgraded compass keeps working at its levels");
        h.succeed();
    }
}
