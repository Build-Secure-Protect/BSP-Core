package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.compass.TotemCompassItem;
import com.mrgregles.bsp_core.compass.TotemCompassItem.Range;
import com.mrgregles.bsp_core.compass.TotemCompassMenu;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.registry.ModItems;
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

    @GameTest(template = EMPTY, batch = BATCH)
    public void compass_range_upgrades_cost_their_coins_and_stay_on_the_compass(GameTestHelper h) {
        ServerPlayer player = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        ItemStack compass = new ItemStack(ModItems.TOTEM_COMPASS.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, compass);
        h.assertTrue(TotemCompassItem.maxRangeLevel() == 6, "six levels on each ladder: " + TotemCompassItem.maxRangeLevel());
        h.assertTrue(TotemCompassItem.rangeBlocks(Range.ACROSS, 6) == 2500 && TotemCompassItem.rangeBlocks(Range.DEPTH, 6) == 150, "the top levels reach 2,500 and 150");
        h.assertTrue(TotemCompassItem.rangeCost(5) == 40 && TotemCompassItem.rangeCoin(5) == CoinTier.ILLYRIUM, "the last level costs 40 Illyrium");

        h.assertTrue(!TotemCompassItem.buyRange(player, compass, Range.ACROSS), "no coins, no upgrade");
        player.getInventory().add(new ItemStack(CoinTier.COPPER.coin(), 12));
        player.getInventory().add(new ItemStack(CoinTier.GOLD.coin(), 10));
        h.assertTrue(TotemCompassItem.buyRange(player, compass, Range.ACROSS), "10 Copper buys Across level 1");
        h.assertTrue(player.getInventory().countItem(CoinTier.COPPER.coin()) == 2, "and takes exactly 10 Copper");
        h.assertTrue(!TotemCompassItem.buyRange(player, compass, Range.DEPTH), "Up or down level 1 also costs 10 Copper: 2 are not enough");
        h.assertTrue(TotemCompassItem.buyRange(player, compass, Range.ACROSS) && player.getInventory().countItem(CoinTier.GOLD.coin()) == 0,
                "10 Gold buys Across level 2");
        h.assertTrue(TotemCompassItem.rangeLevel(compass, Range.ACROSS) == 2 && TotemCompassItem.rangeBlocks(compass, Range.ACROSS) == 500
                && TotemCompassItem.rangeLevel(compass, Range.DEPTH) == 0, "the compass reaches 500 across and still 10 up or down");

        // through the screen's keys
        player.getInventory().add(new ItemStack(CoinTier.COPPER.coin(), 8));
        TotemCompassMenu menu = new TotemCompassMenu(1, player.getInventory(), InteractionHand.MAIN_HAND);
        h.assertTrue(menu.clickMenuButton(player, TotemCompassMenu.BTN_DEEPER), "DEEPER is a key of the screen");
        h.assertTrue(menu.rangeLevel(Range.DEPTH) == 1 && menu.rangeLevel(Range.ACROSS) == 2, "DEEPER bought Up or down level 1; the screen reads both levels");
        h.assertTrue(TotemCompassItem.rangeBlocks(compass, Range.DEPTH) == 20, "20 up or down");

        ItemStack other = new ItemStack(ModItems.TOTEM_COMPASS.get());
        h.assertTrue(TotemCompassItem.rangeLevel(other, Range.ACROSS) == 0 && TotemCompassItem.rangeLevel(other, Range.DEPTH) == 0,
                "another compass starts from nothing: the upgrades belong to the compass");

        compass.getOrCreateTag().putInt(TotemCompassItem.RANGE_ACROSS, 6);
        player.setGameMode(GameType.CREATIVE);
        h.assertTrue(!TotemCompassItem.buyRange(player, compass, Range.ACROSS) && TotemCompassItem.rangeLevel(compass, Range.ACROSS) == 6,
                "at the top there is nothing more to buy, even in creative");
        h.succeed();
    }
}
