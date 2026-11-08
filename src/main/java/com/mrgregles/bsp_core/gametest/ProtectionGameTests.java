package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.totem.NewPlayerProtection;
import com.mrgregles.bsp_core.totem.NewPlayerProtection.Mode;
import com.mrgregles.bsp_core.totem.TotemRadiation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * GameTests for new-player protection from totem radiation: playtime and rank modes, what the protection covers, the record that
 * only goes up and the season's end. The settings are the test world's own; each test puts back what was there in a {@code finally}.
 * BSP-RankUp is not in the test run, so rank mode protects nobody here (no ladder).
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class ProtectionGameTests {
    private static final String EMPTY = "empty", BATCH = "protection";

    @GameTest(template = EMPTY, batch = BATCH)
    public void playtime_protects_a_new_players_low_tier_totems_until_the_hours_are_played(GameTestHelper h) {
        var server = h.getLevel().getServer();
        NewPlayerProtection p = NewPlayerProtection.get(server);
        Mode mode = p.mode;
        String rank = p.rank;
        int hours = p.hours;
        TotemLedger ledger = TotemLedger.get(server);
        UUID owner = UUID.randomUUID();
        GlobalPos low = GlobalPos.of(h.getLevel().dimension(), new BlockPos(-3_000_000, 64, 3_000_000)), high = GlobalPos.of(h.getLevel().dimension(), new BlockPos(-3_000_100, 64, 3_000_000));
        try {
            p.applySettings(Mode.PLAYTIME, "", 24);
            ledger.recordPlaced(owner, low, 1);
            ledger.recordLevels(low, Map.of("output", 116)); // level 117 at Tier II: seen from far
            ledger.recordPlaced(owner, high, 2);
            ledger.recordLevels(high, Map.of("output", 115)); // Tier III
            h.assertTrue(p.protects(server, owner), "a player with no playtime seen is protected");
            h.assertTrue(TotemRadiation.seenAt(server, ledger, owner, low) == 0, "their Tier II totem gives off nothing, whatever its level");
            h.assertTrue(TotemRadiation.seenAt(server, ledger, owner, high) == TotemRadiation.seenAt(ledger, high) && TotemRadiation.seenAt(ledger, high) > 0,
                    "a Tier III totem of theirs radiates as usual");
            var seeker = MockPlayers.serverPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);
            seeker.setPos(low.pos().getX() + 50.5, 64, low.pos().getZ() + 0.5);
            var compass = new net.minecraft.world.item.ItemStack(com.mrgregles.bsp_core.registry.ModItems.TOTEM_COMPASS.get());
            compass.getOrCreateTag().putInt(com.mrgregles.bsp_core.compass.TotemCompassItem.RANGE_ACROSS, 6);
            compass.getOrCreateTag().putInt(com.mrgregles.bsp_core.compass.TotemCompassItem.RANGE_DEPTH, 6);
            var found = com.mrgregles.bsp_core.compass.TotemCompassItem.nearest(seeker, true, compass);
            h.assertTrue(found != null && found.distSqr(high.pos()) < found.distSqr(low.pos()),
                    "a tracking compass 50 blocks from the protected Tier II totem passes it by and finds the Tier III one: " + found);
            p.raise(owner, -1, 23 * 60 + 59);
            h.assertTrue(TotemRadiation.seenAt(server, ledger, owner, low) == 0, "at 23 h 59 m still protected");
            p.raise(owner, -1, 24 * 60);
            h.assertTrue(!p.protects(server, owner) && TotemRadiation.seenAt(server, ledger, owner, low) == TotemRadiation.seenAt(ledger, low),
                    "at 24 h of playtime the protection ends: " + TotemRadiation.seenAt(server, ledger, owner, low));
            h.assertTrue(low.pos().equals(com.mrgregles.bsp_core.compass.TotemCompassItem.nearest(seeker, true, compass)), "and the compass finds the nearer Tier II totem");
            p.applySettings(Mode.PLAYTIME, "", 48);
            h.assertTrue(p.protects(server, owner), "raising the hours to 48 protects them again (what counts is the setting now)");
            p.applySettings(Mode.OFF, "", 48);
            h.assertTrue(!p.protects(server, owner), "with protection off nobody is protected");
        } finally {
            ledger.recordRemoved(low);
            ledger.recordRemoved(high);
            p.applySettings(mode, rank, hours);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void what_was_seen_only_goes_up_and_belongs_to_one_season(GameTestHelper h) {
        var server = h.getLevel().getServer();
        NewPlayerProtection p = NewPlayerProtection.get(server);
        Mode mode = p.mode;
        String rank = p.rank;
        int hours = p.hours;
        UUID player = UUID.randomUUID();
        int real = NewPlayerProtection.currentSeason(server);
        p.ensureSeason(real);
        try {
            p.applySettings(Mode.PLAYTIME, "", 1);
            h.assertTrue(p.raise(player, 3, 120), "a first sighting is recorded");
            h.assertTrue(!p.raise(player, 1, 30) && p.rung(player) == 3 && p.minutes(player) == 120, "lower values never lower it");
            h.assertTrue(p.raise(player, 3, 150) && p.minutes(player) == 150, "more playtime raises it");
            h.assertTrue(!p.protects(server, player), "2 h 30 m played: past the 1-hour setting");
            p.ensureSeason(real + 1);
            h.assertTrue(p.minutes(player) == 0 && p.rung(player) == -1 && p.protects(server, player), "a new season starts an empty record: protected again until seen");
            p.ensureSeason(real + 1);
            p.raise(player, -1, 10);
            h.assertTrue(p.minutes(player) == 10, "the same season again keeps what is recorded");
        } finally {
            p.ensureSeason(real);
            p.applySettings(mode, rank, hours);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "protection_admin")
    public void the_radiation_tab_shows_moderators_and_saves_only_for_admins(GameTestHelper h) {
        var server = h.getLevel().getServer();
        NewPlayerProtection p = NewPlayerProtection.get(server);
        Mode mode = p.mode;
        String rank = p.rank;
        int hours = p.hours;
        int full = com.mrgregles.bsp_core.BSPConfig.RADIATION_FULL_LEVEL.get(), max = com.mrgregles.bsp_core.BSPConfig.RADIATION_MAX_SEEN.get();
        double blur = com.mrgregles.bsp_core.BSPConfig.RADIATION_BLUR.get(), cut = com.mrgregles.bsp_core.BSPConfig.RADIATION_LAYER_CUT.get();
        int layers = com.mrgregles.bsp_core.BSPConfig.RADIATION_MAX_LAYERS.get(), radius = com.mrgregles.bsp_core.BSPConfig.RADIATION_CHECK_RADIUS.get(),
                seconds = com.mrgregles.bsp_core.BSPConfig.RADIATION_CHECK_SECONDS.get();
        boolean on = com.mrgregles.bsp_core.BSPConfig.RADIATION_ENABLED.get();
        var admin = MockPlayers.serverPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);
        try {
            var request = com.mrgregles.bsp_core.network.RadiationEditPacket.request();
            h.assertTrue("".equals(com.mrgregles.bsp_core.totem.RadiationAdmin.act(admin, request, true, false)), "a moderator gets the tab's data");
            h.assertTrue(com.mrgregles.bsp_core.totem.RadiationAdmin.act(admin, request, false, false) == null, "anyone else gets nothing");
            var save = new com.mrgregles.bsp_core.network.RadiationEditPacket(true, true, Mode.PLAYTIME.ordinal(), "", 36, true, 100, 3000, 25, 15, 5, 24, 20);
            String refused = com.mrgregles.bsp_core.totem.RadiationAdmin.act(admin, save, true, false);
            h.assertTrue(refused.startsWith("Only admins") && p.hours == hours, "a moderator cannot save: " + refused);
            String saved = com.mrgregles.bsp_core.totem.RadiationAdmin.act(admin, save, true, true);
            h.assertTrue(saved.startsWith("Saved"), "an admin saves: " + saved);
            h.assertTrue(p.mode == Mode.PLAYTIME && p.hours == 36, "the protection is set: " + p.mode + " " + p.hours);
            h.assertTrue(com.mrgregles.bsp_core.BSPConfig.RADIATION_FULL_LEVEL.get() == 100 && com.mrgregles.bsp_core.BSPConfig.RADIATION_MAX_SEEN.get() == 3000
                    && Math.abs(com.mrgregles.bsp_core.BSPConfig.RADIATION_BLUR.get() - 0.25) < 1e-9 && Math.abs(com.mrgregles.bsp_core.BSPConfig.RADIATION_LAYER_CUT.get() - 0.15) < 1e-9
                    && com.mrgregles.bsp_core.BSPConfig.RADIATION_MAX_LAYERS.get() == 5 && com.mrgregles.bsp_core.BSPConfig.RADIATION_CHECK_RADIUS.get() == 24
                    && com.mrgregles.bsp_core.BSPConfig.RADIATION_CHECK_SECONDS.get() == 20, "and the numbers are this server's config");
            var bad = new com.mrgregles.bsp_core.network.RadiationEditPacket(true, true, Mode.PLAYTIME.ordinal(), "", 36, true, 100, 3000, 25, 95, 5, 24, 20);
            h.assertTrue(com.mrgregles.bsp_core.totem.RadiationAdmin.act(admin, bad, true, true).contains("out of range"), "a 95% coating cut is refused");
            var noLadder = new com.mrgregles.bsp_core.network.RadiationEditPacket(true, true, Mode.RANK.ordinal(), "sentinel", 36, true, 100, 3000, 25, 15, 5, 24, 20);
            h.assertTrue(com.mrgregles.bsp_core.totem.RadiationAdmin.act(admin, noLadder, true, true).contains("ladder") && p.mode == Mode.PLAYTIME,
                    "setting a rank RankUp's ladder does not have is refused (no RankUp here)");
            var numbersOnly = new com.mrgregles.bsp_core.network.RadiationEditPacket(true, false, Mode.RANK.ordinal(), "nowhere", 1, true, 90, 2000, 25, 15, 5, 24, 20);
            String numbers = com.mrgregles.bsp_core.totem.RadiationAdmin.act(admin, numbersOnly, true, true);
            h.assertTrue(numbers.startsWith("Saved this server's numbers") && com.mrgregles.bsp_core.BSPConfig.RADIATION_FULL_LEVEL.get() == 90,
                    "a save of only the numbers goes through whatever the rank: " + numbers);
            h.assertTrue(p.mode == Mode.PLAYTIME && p.hours == 36, "and leaves the protection as it was (another admin's choice stands)");
        } finally {
            p.applySettings(mode, rank, hours);
            com.mrgregles.bsp_core.BSPConfig.RADIATION_ENABLED.set(on);
            com.mrgregles.bsp_core.BSPConfig.RADIATION_FULL_LEVEL.set(full);
            com.mrgregles.bsp_core.BSPConfig.RADIATION_MAX_SEEN.set(max);
            com.mrgregles.bsp_core.BSPConfig.RADIATION_BLUR.set(blur);
            com.mrgregles.bsp_core.BSPConfig.RADIATION_LAYER_CUT.set(cut);
            com.mrgregles.bsp_core.BSPConfig.RADIATION_MAX_LAYERS.set(layers);
            com.mrgregles.bsp_core.BSPConfig.RADIATION_CHECK_RADIUS.set(radius);
            com.mrgregles.bsp_core.BSPConfig.RADIATION_CHECK_SECONDS.set(seconds);
            com.mrgregles.bsp_core.BSPConfig.RADIATION_ENABLED.save();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "protection_screen", timeoutTicks = 200)
    public void a_protected_totem_tells_its_screen_what_ends_the_protection(GameTestHelper h) {
        var server = h.getLevel().getServer();
        NewPlayerProtection p = NewPlayerProtection.get(server);
        Mode mode = p.mode;
        String rank = p.rank;
        int hours = p.hours;
        var owner = MockPlayers.serverPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);
        p.applySettings(Mode.PLAYTIME, "", 24);
        BlockPos low = new BlockPos(2, 1, 2), high = new BlockPos(6, 1, 2);
        java.util.List<com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity> made = new java.util.ArrayList<>();
        Runnable cleanUp = () -> {
            p.applySettings(mode, rank, hours);
            TotemLedger ledger = TotemLedger.get(server);
            for (var be : made) {
                UUID id = be.totemId();
                be.setOwner(null);
                h.getLevel().setBlock(be.getBlockPos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                if (id != null) {
                    ledger.forget(id);
                }
            }
        };
        try {
            for (BlockPos at : java.util.List.of(low, high)) {
                h.setBlock(at, com.mrgregles.bsp_core.registry.ModBlocks.SHATTER_TOTEM.get());
                var be = (com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity) h.getBlockEntity(at);
                be.loadFromStack(com.mrgregles.bsp_core.totem.TotemMint.fresh(owner));
                made.add(be);
            }
            made.get(0).setTier(1);
            made.get(0).setUpgradeLevel(com.mrgregles.bsp_core.totem.TotemUpgrades.Buff.OUTPUT, 10); // level 11: seen from 21 blocks
            made.get(1).setTier(2);
            p.raise(owner.getUUID(), -1, 13 * 60 + 20);
        } catch (RuntimeException e) {
            cleanUp.run();
            throw e;
        }
        h.runAfterDelay(25, () -> {
            try {
                var tier1 = made.get(0);
                h.assertTrue(tier1.protectionMode() == Mode.PLAYTIME.ordinal() && tier1.protectionHours() == 24 && tier1.protectionPlayedMinutes() == 13 * 60 + 20,
                        "the Tier I totem's screen shows it protected until 24 h, 13 h 20 m played: " + tier1.protectionMode() + " " + tier1.protectionHours() + " " + tier1.protectionPlayedMinutes());
                h.assertTrue(tier1.radiationSeen() == 0 && tier1.radiationBare() == 21, "the screen gets 0 blocks seen now and 21 without the protection, from the server: "
                        + tier1.radiationSeen() + " / " + tier1.radiationBare());
                h.assertTrue(made.get(1).protectionMode() == -1, "a Tier III totem radiates, so its screen shows the bar");
                p.raise(owner.getUUID(), -1, 24 * 60);
            } catch (RuntimeException e) {
                cleanUp.run();
                throw e;
            }
            h.runAfterDelay(25, () -> {
                try {
                    h.assertTrue(made.get(0).protectionMode() == -1 && made.get(0).radiationSeen() == 21, "with 24 h played the protection ends and the screen shows 21 blocks: "
                            + made.get(0).radiationSeen());
                } finally {
                    cleanUp.run();
                }
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void rank_mode_needs_a_rankup_ladder_and_the_settings_travel_as_one_record(GameTestHelper h) {
        var server = h.getLevel().getServer();
        NewPlayerProtection p = NewPlayerProtection.get(server);
        Mode mode = p.mode;
        String rank = p.rank;
        int hours = p.hours;
        try {
            p.applySettings(Mode.RANK, "Sentinel", 24);
            h.assertTrue(p.rank.equals("sentinel"), "the rank is kept lower-cased, as RankUp's ladder has it");
            h.assertTrue(!p.protects(server, UUID.randomUUID()), "without a RankUp ladder that has the rank, nobody is protected");
            String snbt = p.settingsTag().toString();
            p.applySettings(Mode.OFF, "", 0);
            p.applySnbt(snbt);
            h.assertTrue(p.mode == Mode.RANK && p.rank.equals("sentinel") && p.hours == 24, "the network's record puts the settings back: " + snbt);
        } finally {
            p.applySettings(mode, rank, hours);
        }
        h.succeed();
    }
}
