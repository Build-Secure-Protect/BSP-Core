package com.mrgregles.bsp_core.totem;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.data.PlayerPersistent;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.SiegeStatePacket;
import com.mrgregles.bsp_core.plasma.CarriedPowers;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Siege, the carried Raid power: pressed, it lets the carrier mine at full speed inside an enemy Fortify for the level's seconds,
 * then rests for the level's recharge. Server-side timers in the player's persisted data, mirrored to the client for the ring
 * by the crosshair.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.mrgregles.bsp_core.BSPCore.MODID)
public final class Siege {
    /**
     * Kept with BSP-Core's other keys in the player's data that survives a respawn ({@link PlayerPersistent}), so the recharge
     * runs on through a death or the way home from the End; a death ends a Siege that is running.
     */
    private static final String TAG_UNTIL = com.mrgregles.bsp_core.BSPCore.MODID + ":SiegeUntil", TAG_READY = com.mrgregles.bsp_core.BSPCore.MODID + ":SiegeReadyAt";
    /** Where the times were kept before, at the root of the player's data (the unprefixed names, then the prefixed): still read, dropped once over and at the next use. */
    private static final String[] OLD_UNTIL = {"SiegeUntil", TAG_UNTIL}, OLD_READY = {"SiegeReadyAt", TAG_READY};

    /**
     * A saved time, in this server's game ticks. BSP-InventorySync carries the player's data between servers whose clocks
     * differ, so a time further ahead than the longest possible Siege and recharge came from another clock and counts as none
     * (else Siege could stay blocked for as long as that server's clock is ahead).
     */
    private static long read(ServerPlayer player, String key, String[] olds) {
        long now = now(player), window = longestWindowTicks();
        var persisted = PlayerPersistent.get(player);
        long at = persisted.getLong(key);
        if (at - now > window) {
            persisted.remove(key); // from another clock: kept, it would start counting once this clock came near it
            at = 0;
        }
        var root = player.getPersistentData();
        for (String old : olds) {
            if (!root.contains(old)) {
                continue;
            }
            long was = root.getLong(old);
            if (was <= now || was - now > window) {
                root.remove(old); // over, or from another clock: nothing to keep it for
            } else {
                at = Math.max(at, was);
            }
        }
        return at - now > window ? 0 : at;
    }

    /** The longest Siege plus the longest recharge any level has, in ticks; an empty list counts as its default, as for the level. */
    private static long longestWindowTicks() {
        return (longest(BSPConfig.getOr(BSPConfig.SIEGE_SECONDS, List.<Integer>of()), 8) + longest(BSPConfig.getOr(BSPConfig.SIEGE_RECHARGE, List.<Integer>of()), 180)) * 20L;
    }

    private static long longest(List<? extends Integer> values, int fallback) {
        long most = 0;
        for (int v : values) {
            most = Math.max(most, v);
        }
        return values.isEmpty() ? fallback : most;
    }

    /** At login: old-place times that are over (or from another server's clock) are dropped, and the ring shows a recharge still running. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            showRunning(player);
        }
    }

    /**
     * A respawn or the way home from the End: the times carry on in the new entity (Forge passes the persisted data over whole;
     * times still at the old place, the root, are read from the old entity), and a death ends a running Siege.
     */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onClone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getOriginal() instanceof ServerPlayer was)) {
            return;
        }
        long now = now(player), until = read(was, TAG_UNTIL, OLD_UNTIL), ready = read(was, TAG_READY, OLD_READY);
        if (now >= until && now >= ready) {
            return; // nothing running
        }
        boolean moved = false;
        for (String[] olds : new String[][] {OLD_UNTIL, OLD_READY}) {
            for (String old : olds) {
                moved |= was.getPersistentData().contains(old);
            }
        }
        if (moved || (event.isWasDeath() && now < until)) {
            write(player, event.isWasDeath() && now < until ? now : until, ready);
        }
    }

    /** After a respawn or the way home from the End the client's ring starts empty: send it what still runs. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onRespawn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            showRunning(player);
        }
    }

    /**
     * Tells the client's ring what runs now: at login, at a respawn and after BSP-InventorySync's restore. Sent even when nothing
     * runs, so a ring left over from another server (another clock) is cleared.
     */
    public static void showRunning(ServerPlayer player) {
        send(player, read(player, TAG_UNTIL, OLD_UNTIL), read(player, TAG_READY, OLD_READY));
    }

    private static void write(ServerPlayer player, long until, long ready) {
        var persisted = PlayerPersistent.get(player);
        persisted.putLong(TAG_UNTIL, until);
        persisted.putLong(TAG_READY, ready);
        var root = player.getPersistentData();
        for (String old : OLD_UNTIL) {
            root.remove(old);
        }
        for (String old : OLD_READY) {
            root.remove(old);
        }
        PlayerPersistent.edited(player, TAG_UNTIL, TAG_READY);
    }

    private Siege() {}

    private static long now(ServerPlayer player) {
        return player.server.overworld().getGameTime();
    }

    public static boolean active(Player player) {
        if (!(player instanceof ServerPlayer sp)) {
            return false;
        }
        return now(sp) < read(sp, TAG_UNTIL, OLD_UNTIL);
    }

    /** The key was pressed: start it, stop it early, or say why not. */
    public static void toggle(ServerPlayer player) {
        int lvl = CarriedPowers.level(player, TotemUpgrades.Buff.SIEGE);
        long now = now(player), until = read(player, TAG_UNTIL, OLD_UNTIL), ready = read(player, TAG_READY, OLD_READY);
        if (lvl <= 0) {
            player.displayClientMessage(Component.translatable("message.bsp_core.siege.none").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (now < until) {
            write(player, now, ready); // stopped early; the recharge still runs from the use
            send(player, now, ready);
            return;
        }
        if (now < ready) {
            player.displayClientMessage(Component.translatable("message.bsp_core.siege.recharging", (ready - now) / 20).withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        int seconds = BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.SIEGE_SECONDS, List.<Integer>of()), lvl, 8);
        int recharge = BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.SIEGE_RECHARGE, List.<Integer>of()), lvl, 180);
        until = now + seconds * 20L;
        ready = until + recharge * 20L;
        write(player, until, ready);
        send(player, until, ready);
        player.displayClientMessage(Component.translatable("message.bsp_core.siege.on", seconds).withStyle(ChatFormatting.GOLD), true);
    }

    private static void send(ServerPlayer player, long until, long ready) {
        long now = now(player);
        BSPNetwork.sendTo(player, new SiegeStatePacket((int) Math.max(0, until - now), (int) Math.max(0, ready - now)));
    }
}
