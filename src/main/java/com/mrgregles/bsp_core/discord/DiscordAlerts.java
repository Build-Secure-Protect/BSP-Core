package com.mrgregles.bsp_core.discord;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.api.event.TotemEvent;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * When Discord hears about a steal. The game's own warning to the owner (after the thief's Shroud) is the moment: the public
 * line goes out for every steal of an owned totem, and the owner's direct message too when the totem has Herald. When the
 * steal ends, the owner who was told gets the outcome. Once per steal each; a player gets at most one warning every
 * {@link #PLAYER_GAP_MS} (several totems raided at once give one message). Nothing is sent where {@link Herald#available()}
 * is false. Server thread only.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class DiscordAlerts {
    public static final long PLAYER_GAP_MS = 30_000;
    /** Steals the owner has been warned about, by totem: true when the owner got the direct message (and so is owed the outcome). */
    private static final Map<GlobalPos, Boolean> running = new HashMap<>();
    private static final Map<UUID, Long> lastWarning = new HashMap<>();
    /** GameTest recorders: while any is listening, alerts go to them instead of Redis, and Discord counts as on. */
    private static final List<AlertSender> recorders = new CopyOnWriteArrayList<>();
    private static boolean toldNoRedis;

    private DiscordAlerts() {}

    /** Whether alerts are sent here: Herald is available, or a GameTest is listening. */
    public static boolean on() {
        return !recorders.isEmpty() || Herald.available();
    }

    /** The name the messages give this server: {@code discord.serverName}, else {@code storage.serverId}. */
    public static String serverName() {
        String name = BSPConfig.getOr(BSPConfig.DISCORD_SERVER_NAME, "").trim();
        return name.isEmpty() ? BSPConfig.getOr(BSPConfig.STORAGE_SERVER_ID, "") : name;
    }

    /** The game has just warned the owner of the totem at {@code pos} that {@code thief} is stealing it. */
    public static void warned(ServerLevel level, BlockPos pos, UUID owner, int heraldLevel, String thief, int secondsLeft) {
        warned(GlobalPos.of(level.dimension(), pos.immutable()), owner, heraldLevel, thief, secondsLeft, System.currentTimeMillis());
    }

    public static void warned(GlobalPos where, UUID owner, int heraldLevel, String thief, int secondsLeft, long now) {
        if (!on() || running.containsKey(where)) {
            return;
        }
        String server = serverName();
        Long last = lastWarning.get(owner);
        boolean direct = heraldLevel > 0 && (last == null || now - last >= PLAYER_GAP_MS);
        running.put(where, direct);
        send(DiscordAlert.channel(server, now));
        if (direct) {
            lastWarning.put(owner, now);
            lastWarning.values().removeIf(t -> now - t >= PLAYER_GAP_MS && t != now); // keep the map small
            send(DiscordAlert.warning(owner, server, where, thief, secondsLeft, now));
        }
    }

    @SubscribeEvent
    public static void onStealEnded(TotemEvent.StealEnded event) {
        ended(event.getWhere(), event.getOwner(), event.getThiefName(), event.isSuccess(), event.getReasonKey(), System.currentTimeMillis());
    }

    public static void ended(GlobalPos where, UUID owner, String thief, boolean stolen, String reasonKey, long now) {
        Boolean direct = running.remove(where);
        if (Boolean.TRUE.equals(direct) && owner != null && on()) {
            send(DiscordAlert.outcome(owner, serverName(), where, thief, stolen, reasonKey, now));
        }
    }

    private static void send(DiscordAlert alert) {
        if (!recorders.isEmpty()) {
            recorders.forEach(r -> r.send(alert));
            return;
        }
        if (!NetworkStorage.publishTo(BSPConfig.getOr(BSPConfig.DISCORD_REDIS_CHANNEL, "bsp_core:discord"), alert.toJson()) && !toldNoRedis) {
            toldNoRedis = true;
            BSPCore.LOGGER.warn("BSP-Core: discord.enabled is on, but this server has no Redis link (storage.mode \"mysql\" and storage.redis.enabled), so no Discord alert can reach the bot");
        }
    }

    /** GameTests: send alerts to {@code recorder} (and count Discord as on) until {@link #stopListening}. */
    public static void listen(AlertSender recorder) {
        recorders.add(recorder);
    }

    public static void stopListening(AlertSender recorder) {
        recorders.remove(recorder);
    }
}
