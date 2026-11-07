package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.compass.CompassCooldowns;
import com.mrgregles.bsp_core.compass.TotemCompassItem;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * GameTests for the Totem Compass cooldown held by the player: a second compass cannot start tracking while the first one's
 * tracking or cooldown runs. Mock players are new each run; their waits are cleared in a {@code finally}.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class CompassCooldownGameTests {
    private static final String EMPTY = "empty", BATCH = "compass_cooldown";

    private static ItemStack loaded(int seconds) {
        ItemStack s = new ItemStack(ModItems.TOTEM_COMPASS.get());
        s.getOrCreateTag().putInt(TotemCompassItem.STORED, seconds);
        return s;
    }

    /** Within a second either way: a test's clock reading and the code's are a few milliseconds apart. */
    private static boolean near(int got, int want) {
        return Math.abs(got - want) <= 1;
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void compass_cooldown_follows_the_player_to_a_new_compass(GameTestHelper h) {
        var server = h.getLevel().getServer();
        if (com.mrgregles.bsp_core.storage.NetworkStorage.countsWanted()) {
            h.succeed(); // a network server claims the start through the database and answers later: not this test's ground
            return;
        }
        ServerPlayer a = MockPlayers.serverPlayer(h, GameType.SURVIVAL), b = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        CompassCooldowns waits = CompassCooldowns.get(server);
        try {
            long now = System.currentTimeMillis();
            ItemStack first = loaded(30);
            TotemCompassItem.startTracking(a, first);
            h.assertTrue(near(TotemCompassItem.trackingLeft(first, now), 30), "the first compass tracks for its 30 seconds: " + TotemCompassItem.trackingLeft(first, now));
            int wait = 30 + TotemCompassItem.cooldownSeconds(first);
            h.assertTrue(near(TotemCompassItem.playerWaitLeft(a), wait), "the player waits the tracking and the cooldown, " + wait + " s: " + TotemCompassItem.playerWaitLeft(a));
            h.assertTrue(first.getTag().getLong(TotemCompassItem.COOLDOWN_UNTIL) == waits.until(a.getUUID()), "the compass and the player hold the same real-clock end");

            ItemStack second = loaded(30);
            TotemCompassItem.startTracking(a, second);
            h.assertTrue(TotemCompassItem.trackingLeft(second, now) == 0 && TotemCompassItem.stored(second) == 30,
                    "a new compass cannot start while the player waits, and keeps its loaded seconds");
            h.assertTrue(near(TotemCompassItem.cooldownLeft(a, second), wait), "its screen shows the player's wait: " + TotemCompassItem.cooldownLeft(a, second));

            ItemStack other = loaded(30);
            TotemCompassItem.startTracking(b, other);
            h.assertTrue(near(TotemCompassItem.trackingLeft(other, now), 30), "another player is not held by it");

            var copy = CompassCooldowns.load(waits.save(new net.minecraft.nbt.CompoundTag()));
            h.assertTrue(copy.until(a.getUUID()) == waits.until(a.getUUID()) && copy.until(a.getUUID()) > now, "the world keeps the wait through a save");
            copy.prune(copy.until(a.getUUID()));
            h.assertTrue(copy.until(a.getUUID()) == 0, "an ended wait is forgotten");

            waits.clear(a.getUUID());
            TotemCompassItem.startTracking(a, second);
            h.assertTrue(near(TotemCompassItem.trackingLeft(second, now), 30), "once the wait is over a new compass tracks");
        } finally {
            waits.clear(a.getUUID());
            waits.clear(b.getUUID());
        }
        h.succeed();
    }

    /** A compass saved with game-time ticks gets real-clock times from this server's clock; one from another server's clock is dropped. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void compass_old_game_time_becomes_real_clock(GameTestHelper h) {
        long game = 1_000_000L, now = System.currentTimeMillis();
        ItemStack s = new ItemStack(ModItems.TOTEM_COMPASS.get());
        s.getOrCreateTag().putLong("TrackUntil", game + 20 * 20);
        s.getTag().putLong("CooldownUntil", game + 20 * 20 + 600 * 20);
        TotemCompassItem.upgradeTimes(s, game, now);
        h.assertTrue(!s.getTag().contains("TrackUntil") && !s.getTag().contains("CooldownUntil"), "the old keys are gone");
        h.assertTrue(TotemCompassItem.trackingLeft(s, now) == 20 && TotemCompassItem.cooldownLeft(s, now) == 620,
                "20 s of tracking and the 600 s cooldown after it carry over: " + TotemCompassItem.trackingLeft(s, now) + ", " + TotemCompassItem.cooldownLeft(s, now));

        ItemStack far = new ItemStack(ModItems.TOTEM_COMPASS.get());
        far.getOrCreateTag().putLong("CooldownUntil", game + 30L * 86_400 * 20); // thirty days ahead: another server's clock
        far.getTag().putLong("TrackUntil", game - 100); // already over
        TotemCompassItem.upgradeTimes(far, game, now);
        h.assertTrue(TotemCompassItem.cooldownLeft(far, now) == 0 && TotemCompassItem.trackingLeft(far, now) == 0 && !far.getTag().contains("CooldownUntil"),
                "a time from another server's clock, or one already past, is dropped");
        h.succeed();
    }
}
