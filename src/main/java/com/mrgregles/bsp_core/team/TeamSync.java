package com.mrgregles.bsp_core.team;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Teams across the network: every change here is written to the shared tables and announced over
 * Redis (kind {@code team}); a change elsewhere comes back as a fresh copy of the team, which
 * replaces the local one, tells the members online here the lines they have not seen, and resyncs
 * their totems. Without network storage none of this runs and the local ledger is the record.
 */
public final class TeamSync {
    private TeamSync() {}

    static void written(Team team) {
        NetworkStorage.teamWrite(team);
    }

    static void member(Team team, UUID player) {
        Team.Member m = team.members().get(player);
        if (m != null) {
            NetworkStorage.teamMember(team.id, player, m);
        }
    }

    static void left(Team team, UUID player) {
        NetworkStorage.teamLeft(team.id, player);
    }

    static void removed(Team team) {
        NetworkStorage.teamDelete(team.id);
    }

    static void posted(Team team, Team.Message message) {
        NetworkStorage.teamMessage(team.id, message, BSPConfig.getOr(BSPConfig.TEAM_HISTORY_SIZE, 50));
    }

    /** Marks the player online here or gone, for the other servers' member lists. */
    public static void online(ServerPlayer p, boolean online) {
        if (Teams.teamOf(p.server, p.getUUID()) != null) {
            NetworkStorage.teamOnline(p.getUUID(), online);
        }
    }

    /** Where a member is online, as the database last said; "" when nowhere or unknown. */
    public static String onlineServer(Team team, UUID player) {
        Team.Member m = team.members().get(player);
        return m == null ? "" : m.onlineServer;
    }

    /**
     * Applies teams read from the database (the server thread). {@code only} names the one team
     * asked for; an empty list for it means it was disbanded elsewhere.
     */
    public static void apply(MinecraftServer server, List<Team> fresh, @Nullable UUID only) {
        TeamLedger ledger = TeamLedger.get(server);
        if (only != null && fresh.isEmpty()) {
            Team gone = ledger.team(only);
            if (gone != null) {
                Set<UUID> members = new HashSet<>(gone.members().keySet());
                ledger.remove(gone);
                tellHere(server, members, Component.translatable("message.bsp_core.team.disbanded", Teams.styled(gone)).withStyle(ChatFormatting.YELLOW));
                TeamAccessSync.resyncOwners(server, members);
            }
            return;
        }
        for (Team team : fresh) {
            Team old = ledger.team(team.id);
            // keep this server's own view of who is online here
            for (var e : team.members().entrySet()) {
                if (server.getPlayerList().getPlayer(e.getKey()) != null) {
                    e.getValue().onlineServer = BSPConfig.getOr(BSPConfig.STORAGE_SERVER_ID, "server");
                }
            }
            ledger.replace(team);
            Set<UUID> concerned = new HashSet<>(team.members().keySet());
            if (old != null) {
                concerned.addAll(old.members().keySet());
                int seen = old.messageSeq();
                List<Team.Message> fresh2 = new ArrayList<>();
                for (Team.Message m : team.messages()) {
                    if (m.seq() > seen) {
                        fresh2.add(m);
                    }
                }
                for (Team.Message m : fresh2) {
                    tellHere(server, team.members().keySet(), Teams.chatLine(team, m));
                }
                for (UUID p : old.members().keySet()) {
                    if (!team.has(p)) {
                        ServerPlayer online = server.getPlayerList().getPlayer(p);
                        if (online != null) {
                            TeamChat.sendState(online);
                        }
                    }
                }
            }
            TeamAccessSync.resyncOwners(server, concerned);
        }
    }

    private static void tellHere(MinecraftServer server, Set<UUID> players, Component line) {
        for (UUID p : players) {
            ServerPlayer online = server.getPlayerList().getPlayer(p);
            if (online != null) {
                online.displayClientMessage(line, false);
            }
        }
    }
}
