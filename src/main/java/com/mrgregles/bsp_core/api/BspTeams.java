package com.mrgregles.bsp_core.api;

import com.mrgregles.bsp_core.team.TeamLedger;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only lookups about teams for other mods: which team a player is in, who is in it, and
 * whether two players are teammates (teammates count as friends with every access flag on each
 * other's Shatter Totems). Nothing here changes a team; players do that from the Team screen.
 */
public final class BspTeams {
    private BspTeams() {}

    /** A team as other mods see it. {@code members} includes the leader, in joining order. */
    public record Team(UUID id, String name, ChatFormatting colour, UUID leader, List<UUID> members) {}

    private static Team view(com.mrgregles.bsp_core.team.Team t) {
        return new Team(t.id, t.name, t.colour, t.leader, List.copyOf(t.members().keySet()));
    }

    /** The team the player is in, if any. */
    public static Optional<Team> teamOf(MinecraftServer server, UUID player) {
        com.mrgregles.bsp_core.team.Team t = TeamLedger.get(server).teamOf(player);
        return t == null ? Optional.empty() : Optional.of(view(t));
    }

    /** The team with this id, if it still exists. */
    public static Optional<Team> team(MinecraftServer server, UUID teamId) {
        com.mrgregles.bsp_core.team.Team t = TeamLedger.get(server).team(teamId);
        return t == null ? Optional.empty() : Optional.of(view(t));
    }

    /** Whether two players are in the same team. A player is not their own teammate. */
    public static boolean sameTeam(MinecraftServer server, UUID a, UUID b) {
        if (a.equals(b)) {
            return false;
        }
        com.mrgregles.bsp_core.team.Team t = TeamLedger.get(server).teamOf(a);
        return t != null && t.has(b);
    }

    /** The members of the player's team other than the player; empty when they are in none. */
    public static List<UUID> teammates(MinecraftServer server, UUID player) {
        com.mrgregles.bsp_core.team.Team t = TeamLedger.get(server).teamOf(player);
        if (t == null) {
            return List.of();
        }
        List<UUID> out = new ArrayList<>(t.members().keySet());
        out.remove(player);
        return out;
    }

    /**
     * A member as other mods see them: {@code server} is the network server they are online on ("" when
     * offline or unknown; this server's id when here). {@code seenMs} is wall-clock and never 0 for a
     * member: at the latest it is the moment they joined (they were online to accept), and it moves on
     * at every login and logout seen on any server, so "offline, seen 2 h ago" is always right.
     */
    public record Member(UUID uuid, String name, boolean leader, boolean onlineHere, String server, long seenMs) {}

    /** The members of a team with where they are, in joining order; empty when the team is gone. */
    public static List<Member> members(MinecraftServer server, UUID teamId) {
        com.mrgregles.bsp_core.team.Team t = TeamLedger.get(server).team(teamId);
        if (t == null) {
            return List.of();
        }
        String me = com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.STORAGE_SERVER_ID, "server");
        List<Member> out = new ArrayList<>();
        for (var e : t.members().entrySet()) {
            boolean here = server.getPlayerList().getPlayer(e.getKey()) != null;
            out.add(new Member(e.getKey(), e.getValue().name, t.isLeader(e.getKey()), here, here ? me : e.getValue().onlineServer, e.getValue().seenMs));
        }
        return out;
    }

    private static volatile String teleporter = "";

    /**
     * A teleport mod says it handles {@code TeamEvent.TeleportRequest}. Call once at setup; from
     * then on the Team screen draws a TELEPORT key on every online teammate's row.
     */
    public static void teleportsHandledBy(String modId) {
        teleporter = modId == null ? "" : modId;
    }

    /** The mod that handles team teleports, or "" when none has said so. */
    public static String teleporter() {
        return teleporter;
    }

    /** Asks the teleport mod to move the player to a teammate: the same thing the TELEPORT key does. */
    public static boolean requestTeleport(net.minecraft.server.level.ServerPlayer player, UUID target) {
        return com.mrgregles.bsp_core.team.Teams.requestTeleport(player, target).ok();
    }

    /** Every team this server knows. */
    public static List<Team> all(MinecraftServer server) {
        List<Team> out = new ArrayList<>();
        for (com.mrgregles.bsp_core.team.Team t : TeamLedger.get(server).all()) {
            out.add(view(t));
        }
        return out;
    }
}
