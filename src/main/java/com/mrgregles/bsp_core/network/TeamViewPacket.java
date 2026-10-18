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
                             boolean chatOn, int unread, String pinText, String pinBy, List<Member> members, List<Candidate> candidates, List<Line> lines) {
    /** A member: {@code state} 0 online here, 1 online elsewhere on the network, 2 offline (then {@code seenMs} says since when). */
    public record Member(UUID id, String name, boolean leader, int state, long seenMs, int totems, String where) {}

    /** Someone who may be invited (online here, in no team), or who was and has {@code waitSeconds} left to answer. */
    public record Candidate(UUID id, String name, int waitSeconds) {}

    /** A line on the board; {@code fresh} while the viewer has not read it. */
    public record Line(long ms, String name, String text, boolean fresh) {}

    public static final int HERE = 0, ELSEWHERE = 1, OFFLINE = 2;

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
        }
        buf.writeVarInt(m.lines.size());
        for (Line l : m.lines) {
            buf.writeLong(l.ms);
            buf.writeUtf(l.name);
            buf.writeUtf(l.text);
            buf.writeBoolean(l.fresh);
        }
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
            candidates.add(new Candidate(buf.readUUID(), buf.readUtf(), buf.readVarInt()));
        }
        n = buf.readVarInt();
        List<Line> lines = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            lines.add(new Line(buf.readLong(), buf.readUtf(), buf.readUtf(), buf.readBoolean()));
        }
        return new TeamViewPacket(open, enabled, hasTeam, name, colour, leader, max, membersInvite, chatOn, unread, pinText, pinBy, members, candidates, lines);
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
            return new TeamViewPacket(open, Teams.enabled(), false, "", ChatFormatting.AQUA.getId(), false, max, membersInvite, false, 0, "", "", List.of(), List.of(), List.of());
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
            candidates.add(new Candidate(i.invitee(), Teams.nameOf(server, i.invitee()), (int) Math.max(1, (i.expiresMs() - now + 999) / 1000)));
        }
        for (ServerPlayer o : server.getPlayerList().getPlayers()) {
            if (!o.getUUID().equals(p.getUUID()) && !waiting.contains(o.getUUID()) && Teams.teamOf(server, o.getUUID()) == null && Teams.pending(o.getUUID()).isEmpty()) {
                candidates.add(new Candidate(o.getUUID(), o.getGameProfile().getName(), 0));
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
                team.unread(p.getUUID()), pin == null ? "" : pin.text(), pin == null ? "" : pin.byName(), members, candidates, lines);
    }
}
