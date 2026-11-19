package com.mrgregles.bsp_core.totem;

import com.mrgregles.bsp_core.BSPConfig;
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
 * then rests for the level's recharge. Server-side timers in the player's persistent data, mirrored to the client for the ring
 * by the crosshair.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.mrgregles.bsp_core.BSPCore.MODID)
public final class Siege {
    /** In {@code ForgeData} itself (a death ends Siege), under BSP-Core's prefix like every key it keeps on a player. */
    private static final String TAG_UNTIL = com.mrgregles.bsp_core.BSPCore.MODID + ":SiegeUntil", TAG_READY = com.mrgregles.bsp_core.BSPCore.MODID + ":SiegeReadyAt";
    /** The keys as first written, without the prefix: still read, and dropped at the next use. */
    private static final String OLD_UNTIL = "SiegeUntil", OLD_READY = "SiegeReadyAt";

    /**
     * A saved time, in this server's game ticks. BSP-InventorySync carries the player's data between servers whose clocks
     * differ, so a time further ahead than the longest possible Siege and recharge came from another clock and counts as none
     * (else Siege could stay blocked for as long as that server's clock is ahead).
     */
    private static long read(ServerPlayer player, String key, String old) {
        var data = player.getPersistentData();
        long now = now(player), window = longestWindowTicks();
        if (data.contains(old) && (data.getLong(old) <= now || data.getLong(old) - now > window)) {
            data.remove(old); // an old-name time that is over, or from another clock: nothing to keep it for
        }
        long at = Math.max(data.getLong(key), data.getLong(old));
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

    /** At login, an old-name time is dropped once it is over (or from another server's clock). */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            read(player, TAG_UNTIL, OLD_UNTIL);
            read(player, TAG_READY, OLD_READY);
        }
    }

    private static void write(ServerPlayer player, long until, long ready) {
        var data = player.getPersistentData();
        data.putLong(TAG_UNTIL, until);
        data.putLong(TAG_READY, ready);
        data.remove(OLD_UNTIL);
        data.remove(OLD_READY);
        if (com.mrgregles.bsp_core.BSPCore.LOGGER.isDebugEnabled()) {
            com.mrgregles.bsp_core.BSPCore.LOGGER.debug("BSP-Core persistent data of {}: wrote {}, {} (player data root)", player.getGameProfile().getName(), TAG_UNTIL, TAG_READY);
        }
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
