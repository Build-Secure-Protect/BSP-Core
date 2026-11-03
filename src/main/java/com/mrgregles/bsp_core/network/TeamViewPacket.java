package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.team.Team;
import com.mrgregles.bsp_core.team.TeamLedger;
import com.mrgregles.bsp_core.team.Teams;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Server to client: everything the Team screen shows for one player. {@code open} asks the client to
 * open the screen; otherwise an open screen takes the new data and a closed one ignores it.
 */
public record TeamViewPacket(boolean open, boolean enabled, boolean hasTeam, String name, int colour, boolean leader, int maxMembers, boolean membersInvite,
                             boolean chatOn, int unread, String pinText, String pinBy, List<Member> members, List<Candidate> candidates, List<Line> lines, boolean canTeleport,
                             AllianceView alliance) {
    /** A member: {@code state} 0 online here, 1 online elsewhere on the network, 2 offline (then {@code seenMs} says since when). */
    public record Member(UUID id, String name, boolean leader, int state, long seenMs, int totems, String where) {}

    /**
     * Someone who may be invited (online anywhere on the network, in no team), or who was and has {@code waitSeconds} left to answer.
     * {@code server} is the server they are on when it is not this one, else "".
     */
    public record Candidate(UUID id, String name, int waitSeconds, String server) {}

    /** A line on the board; {@code fresh} while the viewer has not read it. */
    public record Line(long ms, String name, String text, boolean fresh) {}

    public static final int HERE = 0, ELSEWHERE = 1, OFFLINE = 2;

    /**
     * The ALLIANCE tab. {@code has} when the team is in an alliance; {@code leader} when the viewer leads their team (only leaders
     * act for it). {@code myPerks} and {@code myShare} are what the viewer's team gives; {@code allowed} the perks this server lets a
     * team give. {@code invite} names the alliance whose invite waits for the viewer's team ("" none), {@code inviteFrom} the team
     * that sent it.
     */
    public record AllianceView(boolean enabled, boolean has, String name, int maxTeams, boolean leader, boolean chatOn, int unread, int myPerks, boolean myShare,
                               int allowed, List<AllyTeam> teams, List<AllyLine> lines, List<TeamCandidate> candidates, String invite, String inviteFrom, int inviteSeconds,
                               boolean canTeleport) {
        public static final AllianceView NONE = new AllianceView(false, false, "", 3, false, false, 0, 0, false, 0, List.of(), List.of(), List.of(), "", "", 0, false);
    }

    /** A team in the alliance with what it gives and its players (none listed for the viewer's own team). */
    public record AllyTeam(UUID id, String name, int colour, boolean mine, int perks, boolean shares, List<Ally> players) {}

    /** An ally: {@code state} as for members; {@code where} the server when elsewhere; {@code dim} the dimension's id when known; the block when shared. */
    public record Ally(UUID id, String name, boolean leader, int state, String where, String dim, boolean hasPos, int x, int y, int z, long seenMs) {}

    /** A line on the alliance's board, with the speaker's team. */
    public record AllyLine(long ms, String team, int colour, String name, String text, boolean fresh) {}

    /**
     * A team that may be invited: in no alliance. {@code waitSeconds} while an invite to it waits; {@code blockedHours} while it
     * may not ally with this alliance after a break; {@code leaderOnline} when its leader can get the invite now.
     */
    public record TeamCandidate(UUID id, String name, int colour, String leader, int members, int waitSeconds, int blockedHours, boolean leaderOnline) {}

    public static void encode(TeamViewPacket m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.open);
        buf.writeBoolean(m.enabled);
        buf.writeBoolean(m.hasTeam);
        buf.writeUtf(m.name);
        buf.writeByte(m.colour);
        buf.writeBoolean(m.leader);
        buf.writeVarInt(m.maxMembers);
        buf.writeBoolean(m.membersInvite);
        buf.writeBoolean(m.chatOn);
        buf.writeVarInt(m.unread);
        buf.writeUtf(m.pinText);
        buf.writeUtf(m.pinBy);
        buf.writeVarInt(m.members.size());
        for (Member x : m.members) {
            buf.writeUUID(x.id);
            buf.writeUtf(x.name);
            buf.writeBoolean(x.leader);
            buf.writeByte(x.state);
            buf.writeLong(x.seenMs);
            buf.writeVarInt(x.totems);
            buf.writeUtf(x.where);
        }
        buf.writeVarInt(m.candidates.size());
        for (Candidate c : m.candidates) {
            buf.writeUUID(c.id);
            buf.writeUtf(c.name);
            buf.writeVarInt(c.waitSeconds);
            buf.writeUtf(c.server);
        }
        buf.writeVarInt(m.lines.size());
        for (Line l : m.lines) {
            buf.writeLong(l.ms);
            buf.writeUtf(l.name);
            buf.writeUtf(l.text);
            buf.writeBoolean(l.fresh);
        }
        buf.writeBoolean(m.canTeleport);
        AllianceView a = m.alliance;
        buf.writeBoolean(a.enabled());
        buf.writeBoolean(a.has());
        buf.writeUtf(a.name());
        buf.writeVarInt(a.maxTeams());
        buf.writeBoolean(a.leader());
        buf.writeBoolean(a.chatOn());
        buf.writeVarInt(a.unread());
        buf.writeVarInt(a.myPerks());
        buf.writeBoolean(a.myShare());
        buf.writeVarInt(a.allowed());
        buf.writeVarInt(a.teams().size());
        for (AllyTeam t : a.teams()) {
            buf.writeUUID(t.id());
            buf.writeUtf(t.name());
            buf.writeByte(t.colour());
            buf.writeBoolean(t.mine());
            buf.writeVarInt(t.perks());
            buf.writeBoolean(t.shares());
            buf.writeVarInt(t.players().size());
            for (Ally x : t.players()) {
                buf.writeUUID(x.id());
                buf.writeUtf(x.name());
                buf.writeBoolean(x.leader());
                buf.writeByte(x.state());
                buf.writeUtf(x.where());
                buf.writeUtf(x.dim());
                buf.writeBoolean(x.hasPos());
                buf.writeVarInt(x.x());
                buf.writeVarInt(x.y());
                buf.writeVarInt(x.z());
                buf.writeLong(x.seenMs());
            }
        }
        buf.writeVarInt(a.lines().size());
        for (AllyLine l : a.lines()) {
            buf.writeLong(l.ms());
            buf.writeUtf(l.team());
            buf.writeByte(l.colour());
            buf.writeUtf(l.name());
            buf.writeUtf(l.text());
            buf.writeBoolean(l.fresh());
        }
        buf.writeVarInt(a.candidates().size());
        for (TeamCandidate c : a.candidates()) {
            buf.writeUUID(c.id());
            buf.writeUtf(c.name());
            buf.writeByte(c.colour());
            buf.writeUtf(c.leader());
            buf.writeVarInt(c.members());
            buf.writeVarInt(c.waitSeconds());
            buf.writeVarInt(c.blockedHours());
            buf.writeBoolean(c.leaderOnline());
        }
        buf.writeUtf(a.invite());
        buf.writeUtf(a.inviteFrom());
        buf.writeVarInt(a.inviteSeconds());
        buf.writeBoolean(a.canTeleport());
    }

    private static AllianceView readAlliance(FriendlyByteBuf buf) {
        boolean enabled = buf.readBoolean(), has = buf.readBoolean();
        String name = buf.readUtf();
        int max = buf.readVarInt();
        boolean leader = buf.readBoolean(), chatOn = buf.readBoolean();
        int unread = buf.readVarInt(), myPerks = buf.readVarInt();
        boolean myShare = buf.readBoolean();
        int allowed = buf.readVarInt();
        int n = buf.readVarInt();
        List<AllyTeam> teams = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            UUID id = buf.readUUID();
            String tn = buf.readUtf();
            int colour = buf.readByte();
            boolean mine = buf.readBoolean();
            int perks = buf.readVarInt();
            boolean shares = buf.readBoolean();
            int k = buf.readVarInt();
            List<Ally> players = new ArrayList<>(k);
            for (int j = 0; j < k; j++) {
                players.add(new Ally(buf.readUUID(), buf.readUtf(), buf.readBoolean(), buf.readByte(), buf.readUtf(), buf.readUtf(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readLong()));
            }
            teams.add(new AllyTeam(id, tn, colour, mine, perks, shares, players));
        }
        n = buf.readVarInt();
        List<AllyLine> lines = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            lines.add(new AllyLine(buf.readLong(), buf.readUtf(), buf.readByte(), buf.readUtf(), buf.readUtf(), buf.readBoolean()));
        }
        n = buf.readVarInt();
        List<TeamCandidate> candidates = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            candidates.add(new TeamCandidate(buf.readUUID(), buf.readUtf(), buf.readByte(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
        }
        String invite = buf.readUtf(), inviteFrom = buf.readUtf();
        int inviteSeconds = buf.readVarInt();
        boolean canTeleport = buf.readBoolean();
        return new AllianceView(enabled, has, name, max, leader, chatOn, unread, myPerks, myShare, allowed, teams, lines, candidates, invite, inviteFrom, inviteSeconds, canTeleport);
    }

    public static TeamViewPacket decode(FriendlyByteBuf buf) {
        boolean open = buf.readBoolean(), enabled = buf.readBoolean(), hasTeam = buf.readBoolean();
        String name = buf.readUtf();
        int colour = buf.readByte();
        boolean leader = buf.readBoolean();
        int max = buf.readVarInt();
        boolean membersInvite = buf.readBoolean(), chatOn = buf.readBoolean();
        int unread = buf.readVarInt();
        String pinText = buf.readUtf(), pinBy = buf.readUtf();
        int n = buf.readVarInt();
        List<Member> members = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            members.add(new Member(buf.readUUID(), buf.readUtf(), buf.readBoolean(), buf.readByte(), buf.readLong(), buf.readVarInt(), buf.readUtf()));
        }
        n = buf.readVarInt();
        List<Candidate> candidates = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            candidates.add(new Candidate(buf.readUUID(), buf.readUtf(), buf.readVarInt(), buf.readUtf()));
        }
        n = buf.readVarInt();
        List<Line> lines = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            lines.add(new Line(buf.readLong(), buf.readUtf(), buf.readUtf(), buf.readBoolean()));
        }
        boolean canTeleport = buf.readBoolean();
        AllianceView alliance = readAlliance(buf);
        return new TeamViewPacket(open, enabled, hasTeam, name, colour, leader, max, membersInvite, chatOn, unread, pinText, pinBy, members, candidates, lines, canTeleport, alliance);
    }

    public static void handle(TeamViewPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.TeamScreen.receive(msg)));
        ctx.get().setPacketHandled(true);
    }

    /** The view for one player, built on the server. */
    public static TeamViewPacket of(ServerPlayer p, boolean open) {
        MinecraftServer server = p.server;
        Team team = Teams.teamOf(server, p.getUUID());
        int max = Teams.maxMembers();
        boolean membersInvite = BSPConfig.getOr(BSPConfig.TEAM_MEMBERS_INVITE, true);
        if (team == null) {
            return new TeamViewPacket(open, Teams.enabled(), false, "", ChatFormatting.AQUA.getId(), false, max, membersInvite, false, 0, "", "", List.of(), List.of(), List.of(), false,
                    AllianceView.NONE);
        }
        TotemLedger totems = TotemLedger.get(server);
        List<Member> members = new ArrayList<>();
        String myServer = BSPConfig.getOr(BSPConfig.STORAGE_SERVER_ID, "server");
        for (var e : team.members().entrySet()) {
            boolean here = server.getPlayerList().getPlayer(e.getKey()) != null;
            String elsewhere = here ? "" : e.getValue().onlineServer;
            int state = here ? HERE : !elsewhere.isEmpty() && !elsewhere.equals(myServer) ? ELSEWHERE : OFFLINE;
            members.add(new Member(e.getKey(), e.getValue().name, team.isLeader(e.getKey()), state, e.getValue().seenMs, totems.placedFor(e.getKey()).size(), state == ELSEWHERE ? elsewhere : ""));
        }
        List<Candidate> candidates = new ArrayList<>();
        long now = System.currentTimeMillis();
        List<UUID> waiting = new ArrayList<>();
        for (Teams.Invite i : Teams.invitesOf(team.id)) {
            waiting.add(i.invitee());
            boolean here = server.getPlayerList().getPlayer(i.invitee()) != null;
            candidates.add(new Candidate(i.invitee(), i.inviteeName(), (int) Math.max(1, (i.expiresMs() - now + 999) / 1000), here ? "" : i.elsewhere()));
        }
        for (ServerPlayer o : server.getPlayerList().getPlayers()) {
            if (!o.getUUID().equals(p.getUUID()) && !waiting.contains(o.getUUID()) && Teams.teamOf(server, o.getUUID()) == null && Teams.pending(o.getUUID()).isEmpty()) {
                candidates.add(new Candidate(o.getUUID(), o.getGameProfile().getName(), 0, ""));
            }
        }
        // players on other servers only when an invite can reach them (Redis)
        for (var o : com.mrgregles.bsp_core.storage.NetworkStorage.redisOn() ? com.mrgregles.bsp_core.storage.NetworkStorage.playersElsewhere()
                : List.<com.mrgregles.bsp_core.storage.NetworkStorage.OnlinePlayer>of()) {
            if (!o.id().equals(p.getUUID()) && !waiting.contains(o.id()) && server.getPlayerList().getPlayer(o.id()) == null && Teams.teamOf(server, o.id()) == null
                    && Teams.pending(o.id()).isEmpty()) {
                candidates.add(new Candidate(o.id(), o.name(), 0, o.server()));
            }
        }
        Team.Member me = team.members().get(p.getUUID());
        int read = me == null ? Integer.MAX_VALUE : me.readSeq;
        List<Line> lines = new ArrayList<>();
        for (Team.Message m : team.messages()) {
            lines.add(new Line(m.ms(), m.authorName(), m.text(), m.seq() > read));
        }
        Team.Note pin = team.pinned();
        return new TeamViewPacket(open, Teams.enabled(), true, team.name, team.colour.getId(), team.isLeader(p.getUUID()), max, membersInvite, Teams.isChatOn(p.getUUID()),
                team.unread(p.getUUID()), pin == null ? "" : pin.text(), pin == null ? "" : pin.byName(), members, candidates, lines, !com.mrgregles.bsp_core.api.BspTeams.teleporter().isEmpty(),
                alliance(p, team));
    }

    /** The ALLIANCE tab for a player in {@code team}. */
    private static AllianceView alliance(ServerPlayer p, Team team) {
        MinecraftServer server = p.server;
        boolean enabled = com.mrgregles.bsp_core.team.Alliances.enabled();
        int max = com.mrgregles.bsp_core.team.Alliances.maxTeams(), allowed = com.mrgregles.bsp_core.team.Alliances.allowedPerks();
        boolean leader = team.isLeader(p.getUUID());
        long now = System.currentTimeMillis();
        var pending = com.mrgregles.bsp_core.team.Alliances.pending(team.id);
        String invite = pending.map(com.mrgregles.bsp_core.team.Alliances.Invite::allianceName).orElse("");
        String inviteFrom = pending.map(i -> {
            Team from = com.mrgregles.bsp_core.team.TeamLedger.get(server).team(i.fromTeam());
            return from == null ? i.inviterName() : from.name;
        }).orElse("");
        int inviteSeconds = pending.map(i -> (int) Math.max(1, (i.expiresMs() - now + 999) / 1000)).orElse(0);
        com.mrgregles.bsp_core.team.Alliance a = enabled ? com.mrgregles.bsp_core.team.Alliances.allianceOfTeam(server, team.id) : null;
        if (a == null) {
            return new AllianceView(enabled, false, "", max, leader, false, 0, 0, false, allowed, List.of(), List.of(), List.of(), invite, inviteFrom, inviteSeconds, false);
        }
        List<AllyTeam> teams = new ArrayList<>();
        var teamLedger = com.mrgregles.bsp_core.team.TeamLedger.get(server);
        for (var e : a.teams().entrySet()) {
            Team t = teamLedger.team(e.getKey());
            if (t == null) {
                continue;
            }
            boolean mine = t.id.equals(team.id);
            List<Ally> players = new ArrayList<>();
            if (!mine) {
                for (var w : com.mrgregles.bsp_core.team.Alliances.whereabouts(server, t, e.getValue().shareCoords)) {
                    players.add(new Ally(w.id(), w.name(), w.leader(), w.state(), w.state() == ELSEWHERE ? w.server() : "", w.dim(), w.pos() != null,
                            w.pos() == null ? 0 : w.pos().getX(), w.pos() == null ? 0 : w.pos().getY(), w.pos() == null ? 0 : w.pos().getZ(), w.seenMs()));
                }
            }
            teams.add(new AllyTeam(t.id, t.name, t.colour.getId(), mine, e.getValue().perks & allowed, e.getValue().shareCoords, players));
        }
        int read = a.readSeq(p.getUUID());
        List<AllyLine> lines = new ArrayList<>();
        for (var m : a.messages()) {
            lines.add(new AllyLine(m.ms(), m.teamName(), m.colour(), m.authorName(), m.text(), m.seq() > read));
        }
        List<TeamCandidate> candidates = new ArrayList<>();
        var allianceLedger = com.mrgregles.bsp_core.team.AllianceLedger.get(server);
        List<UUID> waiting = new ArrayList<>();
        for (var i : com.mrgregles.bsp_core.team.Alliances.invitesOf(a.id)) {
            Team t = teamLedger.team(i.toTeam());
            if (t != null) {
                waiting.add(t.id);
                candidates.add(new TeamCandidate(t.id, t.name, t.colour.getId(), i.toLeaderName(), t.size(), (int) Math.max(1, (i.expiresMs() - now + 999) / 1000), 0, true));
            }
        }
        for (Team t : teamLedger.all()) {
            if (t.id.equals(team.id) || waiting.contains(t.id) || allianceLedger.allianceOf(t.id) != null) {
                continue;
            }
            long blocked = 0;
            for (UUID in : a.teams().keySet()) {
                blocked = Math.max(blocked, allianceLedger.waitUntil(t.id, in, now));
            }
            boolean online = server.getPlayerList().getPlayer(t.leader) != null
                    || com.mrgregles.bsp_core.storage.NetworkStorage.redisOn() && com.mrgregles.bsp_core.storage.NetworkStorage.onlineElsewhere(t.leader).isPresent();
            candidates.add(new TeamCandidate(t.id, t.name, t.colour.getId(), Teams.nameOf(server, t.leader), t.size(), 0,
                    blocked > now ? (int) Math.max(1, (blocked - now + 3_599_999L) / 3_600_000L) : 0, online));
        }
        candidates.sort(java.util.Comparator.comparing((TeamCandidate c) -> c.waitSeconds() == 0).thenComparing(c -> !c.leaderOnline()).thenComparing(c -> c.blockedHours() > 0)
                .thenComparing(c -> c.name().toLowerCase(java.util.Locale.ROOT)));
        var seat = a.teams().get(team.id);
        return new AllianceView(true, true, a.name, max, leader, com.mrgregles.bsp_core.team.Alliances.isChatOn(p.getUUID()), a.unread(p.getUUID()), seat == null ? 0 : seat.perks & allowed,
                seat != null && seat.shareCoords, allowed, teams, lines, candidates, invite, inviteFrom, inviteSeconds, !com.mrgregles.bsp_core.api.BspTeams.allyTeleporter().isEmpty());
    }
}
