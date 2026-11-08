package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.compass.TotemCompassItem;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.totem.TotemRadiation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GameTests for totem radiation: the seen-at distance from a totem's level (tier steps plus upgrade levels) and its coated
 * layers, what a tracking compass sees, and the blur spot that stops two bearings crossing on the totem. Rival totems are
 * ledger entries far from the test area (the needle reads only the ledger), removed in a {@code finally}.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class TotemRadiationGameTests {
    private static final String EMPTY = "empty", BATCH = "totem_radiation";

    private static BlockPos farAway(ServerPlayer p) {
        int k = Math.floorMod(p.getUUID().hashCode(), 1000);
        return new BlockPos(-2_000_000 - k * 6_000, 64, 2_000_000);
    }

    /** Records a placed rival totem with the given tier (0 to 4) and upgrade levels in all. */
    private static GlobalPos totem(GameTestHelper h, TotemLedger ledger, UUID owner, BlockPos pos, int tier, int levels, List<GlobalPos> made) {
        GlobalPos at = GlobalPos.of(h.getLevel().dimension(), pos);
        ledger.recordPlaced(owner, at, tier);
        if (levels > 0) {
            ledger.recordLevels(at, Map.of("output", levels));
        }
        made.add(at);
        return at;
    }

    /** A compass with both range ladders at the top: 2,500 each way, 150 up or down. */
    private static ItemStack topCompass() {
        ItemStack c = new ItemStack(ModItems.TOTEM_COMPASS.get());
        c.getOrCreateTag().putInt(TotemCompassItem.RANGE_ACROSS, 6);
        c.getOrCreateTag().putInt(TotemCompassItem.RANGE_DEPTH, 6);
        return c;
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void radiation_seen_at_distance_follows_the_level_squared_and_the_coating(GameTestHelper h) {
        h.assertTrue(TotemRadiation.level(0, 0) == 0 && TotemRadiation.seenAt(0, 0) == 0, "a Tier I totem with nothing bought gives off nothing");
        h.assertTrue(TotemRadiation.level(1, 6) == 7 && TotemRadiation.seenAt(7, 0) == 9, "Tier II with 6 levels: level 7, seen from 9: " + TotemRadiation.seenAt(7, 0));
        h.assertTrue(TotemRadiation.seenAt(27, 0) == 127, "level 27 is seen from 127: " + TotemRadiation.seenAt(27, 0));
        h.assertTrue(TotemRadiation.seenAt(63, 0) == 689, "level 63 is seen from 689: " + TotemRadiation.seenAt(63, 0));
        h.assertTrue(TotemRadiation.seenAt(120, 0) == 2500 && TotemRadiation.seenAt(183, 0) == 2500, "level 120 and above are seen from 2,500");
        h.assertTrue(TotemRadiation.seenAt(63, 2) == 441, "two coated layers take a fifth twice: 689 to 441: " + TotemRadiation.seenAt(63, 2));
        h.assertTrue(TotemRadiation.seenAt(183, 1) == 2000, "coating helps a totem above level 120 too: " + TotemRadiation.seenAt(183, 1));
        h.assertTrue(TotemRadiation.seenAt(124, 4) == 1024 && TotemRadiation.seenAt(124, 9) == 1024, "at most 4 layers count: " + TotemRadiation.seenAt(124, 9));
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_tracking_compass_sees_a_rival_totem_only_within_its_radiation(GameTestHelper h) {
        var server = h.getLevel().getServer();
        TotemLedger ledger = TotemLedger.get(server);
        ServerPlayer seeker = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        UUID rival = UUID.randomUUID();
        BlockPos here = farAway(seeker);
        List<GlobalPos> made = new ArrayList<>();
        ItemStack compass = topCompass();
        try {
            GlobalPos settled = totem(h, ledger, rival, here.offset(200, 0, 0), 2, 25, made); // level 27: seen from 127
            seeker.setPos(here.getX() + 0.5, here.getY(), here.getZ() + 0.5);
            h.assertTrue(TotemCompassItem.nearest(seeker, true, compass) == null, "200 blocks off, a level-27 totem is not seen, though the compass reaches 2,500");

            seeker.setPos(here.getX() + 140.5, here.getY(), here.getZ() + 0.5); // 60 blocks off: inside half of 127
            h.assertTrue(settled.pos().equals(TotemCompassItem.nearest(seeker, true, compass)), "60 blocks off it is seen, and the needle points at it exactly");

            seeker.setPos(here.getX() + 100.5, here.getY(), here.getZ() + 0.5); // 100 blocks off: the outer half
            BlockPos spot = TotemCompassItem.nearest(seeker, true, compass);
            h.assertTrue(spot != null, "100 blocks off it is still seen");
            double off = Math.sqrt(spot.distSqr(settled.pos()));
            h.assertTrue(off >= 1 && off <= 0.2 * 100 + 1, "but the needle points beside it, at most a fifth of the distance off: " + off);

            GlobalPos fresh = totem(h, ledger, rival, here.offset(103, 0, 0), 0, 0, made);
            h.assertTrue(TotemCompassItem.nearest(seeker, true, compass) != null && !fresh.pos().equals(TotemCompassItem.nearest(seeker, true, compass)),
                    "a Tier I totem with nothing bought, 3 blocks away, is never seen");

            GlobalPos deep = totem(h, ledger, rival, here.offset(100, -100, 1000), 2, 25, made); // level 27, straight below a spot 1,000 blocks on
            seeker.setPos(here.getX() + 100.5, here.getY(), here.getZ() + 1000.5);
            BlockPos below = TotemCompassItem.nearest(seeker, true, compass);
            h.assertTrue(below != null && Math.sqrt(below.distSqr(deep.pos())) <= 0.2 * 100 + 1,
                    "100 blocks straight down, inside its 127, it is seen (the needle near it): " + below);
            ledger.recordCoatedLayers(deep, 2); // 127 to 81
            h.assertTrue(TotemCompassItem.nearest(seeker, true, compass) == null, "two coated layers bring it to 81, so 100 down it is hidden");

            GlobalPos strong = totem(h, ledger, rival, here.offset(600, 0, 3000), 3, 60, made); // level 63: 689
            seeker.setPos(here.getX() + 100.5, here.getY(), here.getZ() + 3000.5);
            h.assertTrue(TotemCompassItem.nearest(seeker, true, compass) != null, "a level-63 totem 500 away is seen");
            ledger.recordCoatedLayers(strong, 2); // 689 to 441
            h.assertTrue(TotemCompassItem.nearest(seeker, true, compass) == null, "coated twice it is seen from 441 only, so 500 away it is hidden");
        } finally {
            made.forEach(ledger::recordRemoved);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void the_blur_spot_is_the_same_for_everyone_all_day_and_moves_the_next(GameTestHelper h) {
        GlobalPos totem = GlobalPos.of(h.getLevel().dimension(), new BlockPos(1234, 40, -5678));
        long day = 20_000L * 86_400_000L;
        BlockPos east = new BlockPos(1234 + 1800, 40, -5678), north = new BlockPos(1234, 40, -5678 - 1800);
        BlockPos a = TotemRadiation.pointAt(totem, east, 2000, day + 1000), b = TotemRadiation.pointAt(totem, north, 2000, day + 80_000_000L);
        h.assertTrue(!a.equals(totem.pos()), "near the edge the needle points beside the totem");
        h.assertTrue(a.equals(b), "two people the same distance off, in different directions and at different hours of one day, are pointed at the same spot: "
                + a + " and " + b);
        BlockPos tomorrow = TotemRadiation.pointAt(totem, east, 2000, day + 86_400_000L + 1000);
        h.assertTrue(!tomorrow.equals(a), "the next day the spot is elsewhere");
        h.assertTrue(TotemRadiation.pointAt(totem, new BlockPos(1234 + 900, 40, -5678), 2000, day).equals(totem.pos()), "within half the seen-at distance the needle is exact");
        h.succeed();
    }
}
