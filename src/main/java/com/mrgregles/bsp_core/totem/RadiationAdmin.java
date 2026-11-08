package com.mrgregles.bsp_core.totem;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.compat.RankUpHook;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.RadiationAdminPacket;
import com.mrgregles.bsp_core.network.RadiationEditPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * The server side of the admin panel's RADIATION tab: shows the new-player protection and this server's totem radiation settings
 * to moderators and admins, and saves them for admins. The protection is one record for the network ({@link NewPlayerProtection});
 * the radiation numbers are this server's config ({@code totemRadiation}).
 */
public final class RadiationAdmin {
    private RadiationAdmin() {}

    public static void act(ServerPlayer player, RadiationEditPacket msg) {
        act(player, msg, Admins.isModerator(player), Admins.isAdmin(player));
    }

    /**
     * As {@link #act(ServerPlayer, RadiationEditPacket)} with the permissions decided. Returns the answer sent, or null when nothing
     * was sent yet (none at all for a sender who may not look; on a network a changed protection is answered once the database has it).
     */
    @Nullable
    public static String act(ServerPlayer player, RadiationEditPacket msg, boolean view, boolean edit) {
        if (!view) {
            return null; // a modified client: nothing is answered
        }
        if (!msg.save()) {
            return send(player, "", true);
        }
        if (!edit) {
            return send(player, "Only admins can change these settings.", false);
        }
        if (msg.setProtection() && com.mrgregles.bsp_core.storage.NetworkStorage.down()) {
            return send(player, "The network database is not connected. Try again shortly.", false);
        }
        String problem = check(player.server, msg);
        if (problem != null) {
            return send(player, problem, false);
        }
        BSPConfig.RADIATION_ENABLED.set(msg.enabled());
        BSPConfig.RADIATION_FULL_LEVEL.set(msg.fullLevel());
        BSPConfig.RADIATION_MAX_SEEN.set(msg.maxSeen());
        BSPConfig.RADIATION_BLUR.set(msg.blurPct() / 100.0);
        BSPConfig.RADIATION_LAYER_CUT.set(msg.cutPct() / 100.0);
        BSPConfig.RADIATION_MAX_LAYERS.set(msg.maxLayers());
        BSPConfig.RADIATION_CHECK_RADIUS.set(msg.radius());
        BSPConfig.RADIATION_CHECK_SECONDS.set(msg.seconds());
        BSPConfig.RADIATION_ENABLED.save(); // one file: saving one value writes them all
        BSPCore.LOGGER.info("Totem radiation numbers saved by {}: radiation {}, full at level {}, {} blocks, blur {}%, coating {}% x {} within {} every {} s",
                player.getGameProfile().getName(), msg.enabled() ? "on" : "off", msg.fullLevel(), msg.maxSeen(), msg.blurPct(), msg.cutPct(), msg.maxLayers(),
                msg.radius(), msg.seconds());
        if (!msg.setProtection()) {
            return send(player, "Saved this server's numbers.", true);
        }
        NewPlayerProtection.Mode mode = NewPlayerProtection.Mode.values()[msg.mode()];
        String[] answer = {null};
        NewPlayerProtection.get(player.server).setSettings(mode, msg.rank(), msg.hours(), stored -> {
            if (stored) {
                BSPCore.LOGGER.info("New-player protection set by {}: {} {} {} h", player.getGameProfile().getName(), mode, msg.rank(), msg.hours());
            }
            answer[0] = send(player, !stored ? "Saved this server's numbers, but the protection could not reach the network database. Try again."
                    : com.mrgregles.bsp_core.storage.NetworkStorage.enabled() ? "Saved. The protection holds on every server; the numbers on this one." : "Saved.", stored);
        });
        return answer[0];
    }

    /** What is wrong with the settings an admin sent, or null if they can be saved. */
    @Nullable
    public static String check(MinecraftServer server, RadiationEditPacket m) {
        if (m.setProtection()) {
            if (m.mode() < 0 || m.mode() >= NewPlayerProtection.Mode.values().length) {
                return "Unknown protection choice.";
            }
            if (m.mode() == NewPlayerProtection.Mode.RANK.ordinal() && RankUpHook.rungIndex(server, m.rank()) < 0) {
                return "Pick a rank from BSP-RankUp's ladder.";
            }
            if (m.hours() < 1 || m.hours() > 100_000) {
                return "Playtime must be 1 to 100,000 hours.";
            }
        }
        if (m.fullLevel() < 1 || m.fullLevel() > 1000 || m.maxSeen() < 1 || m.maxSeen() > 100_000 || m.blurPct() < 0 || m.blurPct() > 100
                || m.cutPct() < 0 || m.cutPct() > 90 || m.maxLayers() < 0 || m.maxLayers() > 16 || m.radius() < 4 || m.radius() > 64
                || m.seconds() < 5 || m.seconds() > 3600) {
            return "A number is out of range.";
        }
        return null;
    }

    private static String send(ServerPlayer player, String message, boolean ok) {
        BSPNetwork.sendTo(player, data(player.server, message, ok));
        return message;
    }

    /** How many owners of totems placed on this server are protected right now. */
    public static int protectedNow(MinecraftServer server) {
        NewPlayerProtection p = NewPlayerProtection.get(server);
        int n = 0;
        for (UUID owner : TotemLedger.get(server).allPlaced().keySet()) {
            if (p.protects(server, owner)) {
                n++;
            }
        }
        return n;
    }

    public static RadiationAdminPacket data(MinecraftServer server, String message, boolean ok) {
        NewPlayerProtection p = NewPlayerProtection.get(server);
        List<String> ladder = RankUpHook.ladder(server);
        return new RadiationAdminPacket(p.mode.ordinal(), p.rank, p.hours, ladder, protectedNow(server), BSPConfig.getOr(BSPConfig.RADIATION_ENABLED, true),
                BSPConfig.getOr(BSPConfig.RADIATION_FULL_LEVEL, 120), BSPConfig.getOr(BSPConfig.RADIATION_MAX_SEEN, 2500), BSPConfig.getOr(BSPConfig.RADIATION_EXPONENT, 2.0),
                (int) Math.round(BSPConfig.getOr(BSPConfig.RADIATION_BLUR, 0.2) * 100), (int) Math.round(BSPConfig.getOr(BSPConfig.RADIATION_LAYER_CUT, 0.2) * 100),
                BSPConfig.getOr(BSPConfig.RADIATION_MAX_LAYERS, 4), BSPConfig.getOr(BSPConfig.RADIATION_CHECK_RADIUS, 32), BSPConfig.getOr(BSPConfig.RADIATION_CHECK_SECONDS, 30),
                message, ok);
    }
}
