package com.mrgregles.bsp_core.team;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.api.event.TeamEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The rules of teams: making one, inviting, accepting and declining, leaving, removing, handing the
 * lead over, renaming, recolouring, disbanding, the pinned note and the board. Every change goes
 * through here, tells the players concerned and posts a {@link TeamEvent}. Invites live in memory
 * and run out after {@code team.inviteSeconds}; an invite holds a seat while it waits.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class Teams {
    private Teams() {}

    /** What a rule said: ok, or a message for the player who asked. */
    public record Outcome(boolean ok, @Nullable Component message) {
        static Outcome done() {
            return new Outcome(true, null);
        }

        static Outcome done(String key, Object... args) {
            return new Outcome(true, Component.translatable("message.bsp_core.team." + key, args).withStyle(ChatFormatting.GREEN));
        }

        static Outcome fail(String key, Object... args) {
            return new Outcome(false, Component.translatable("message.bsp_core.team." + key, args).withStyle(ChatFormatting.RED));
        }
    }

    /** An invite waiting for an answer. */
    public record Invite(int id, UUID teamId, UUID inviter, String inviterName, UUID invitee, long expiresMs) {}

    private static int inviteIds;

    private static final Map<UUID, Invite> INVITES = new HashMap<>();
    /** Players whose chat goes to their team until they switch back (the TEAM CHAT key). */
    private static final java.util.Set<UUID> CHAT_ON = new java.util.HashSet<>();
    private static int ticks;

    // ------------------------------------------------------------------ lookups

    public static boolean enabled() {
        return BSPConfig.getOr(BSPConfig.TEAM_ENABLED, true);
    }

    public static int maxMembers() {
        return Math.max(1, BSPConfig.getOr(BSPConfig.TEAM_MAX_MEMBERS, 5));
    }

    @Nullable
    public static Team teamOf(MinecraftServer server, UUID player) {
        return TeamLedger.get(server).teamOf(player);
    }

    /** The invite waiting for the player, if one is and it has not run out. */
    public static Optional<Invite> pending(UUID player) {
        Invite i = INVITES.get(player);
        return i == null || i.expiresMs() <= now() ? Optional.empty() : Optional.of(i);
    }

    /** The invites a team has waiting, for its invite view. */
    public static List<Invite> invitesOf(UUID teamId) {
        long now = now();
        List<Invite> out = new ArrayList<>();
        for (Invite i : INVITES.values()) {
            if (i.teamId().equals(teamId) && i.expiresMs() > now) {
                out.add(i);
            }
        }
        return out;
    }

    /** Invites waiting for seats in a team. */
    static int pendingFor(UUID teamId) {
        long now = now();
        int n = 0;
        for (Invite i : INVITES.values()) {
            if (i.teamId().equals(teamId) && i.expiresMs() > now) {
                n++;
            }
        }
        return n;
    }

    /** Null when the name may be used, otherwise why not. {@code self} is the team being renamed, if any. */
    @Nullable
    public static Component checkName(MinecraftServer server, String name, @Nullable Team self) {
        int min = BSPConfig.getOr(BSPConfig.TEAM_NAME_MIN, 3), max = BSPConfig.getOr(BSPConfig.TEAM_NAME_MAX, 16);
        String n = clean(name);
        if (n.length() < min || n.length() > max) {
            return Outcome.fail("name_length", min, max).message();
        }
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == ' ' || c == '_') || c > 127) {
                return Outcome.fail("name_chars").message();
            }
        }
        Team other = TeamLedger.get(server).byName(n);
        if (other != null && other != self) {
            return Outcome.fail("name_taken", other.name).message();
        }
        return null;
    }

    /** Trims and collapses the spaces in a name. */
    public static String clean(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }

    /** The team's name in its colour. */
    public static Component styled(Team team) {
        return Component.literal(team.name).withStyle(team.colour);
    }

    // ------------------------------------------------------------------ making and joining

    public static Outcome create(ServerPlayer player, String name, ChatFormatting colour) {
        if (!enabled()) {
            return Outcome.fail("disabled");
        }
        MinecraftServer server = player.server;
        TeamLedger ledger = TeamLedger.get(server);
        if (ledger.teamOf(player.getUUID()) != null) {
            return Outcome.fail("already_in_team");
        }
        Component bad = checkName(server, name, null);
        if (bad != null) {
            return new Outcome(false, bad);
        }
        if (colour == null || !colour.isColor()) {
            colour = ChatFormatting.AQUA;
        }
        long now = now();
        Team team = new Team(UUID.randomUUID(), clean(name), colour, player.getUUID(), now);
        team.join(player.getUUID(), player.getGameProfile().getName(), now);
        ledger.add(team);
        INVITES.remove(player.getUUID());
        TeamSync.written(team);
        MinecraftForge.EVENT_BUS.post(new TeamEvent.Created(server, team.id, team.name, player.getUUID()));
        return Outcome.done("created", styled(team));
    }

    public static Outcome invite(ServerPlayer inviter, ServerPlayer invitee) {
        if (!enabled()) {
            return Outcome.fail("disabled");
        }
        MinecraftServer server = inviter.server;
        TeamLedger ledger = TeamLedger.get(server);
        Team team = ledger.teamOf(inviter.getUUID());
        if (team == null) {
            return Outcome.fail("not_in_team");
        }
        if (inviter.getUUID().equals(invitee.getUUID())) {
            return Outcome.fail("invite_self");
        }
        if (!team.isLeader(inviter.getUUID()) && !BSPConfig.getOr(BSPConfig.TEAM_MEMBERS_INVITE, true)) {
            return Outcome.fail("members_cannot_invite");
        }
        String name = invitee.getGameProfile().getName();
        if (ledger.teamOf(invitee.getUUID()) != null) {
            return Outcome.fail("player_in_team", name);
        }
        if (pending(invitee.getUUID()).isPresent()) {
            return Outcome.fail("invite_pending", name);
        }
        if (team.size() + pendingFor(team.id) >= maxMembers()) {
            return Outcome.fail("full", styled(team), maxMembers());
        }
        long seconds = Math.max(5, BSPConfig.getOr(BSPConfig.TEAM_INVITE_SECONDS, 60));
        Invite invite = new Invite(++inviteIds, team.id, inviter.getUUID(), inviter.getGameProfile().getName(), invitee.getUUID(), now() + seconds * 1000L);
        INVITES.put(invitee.getUUID(), invite);
        TeamPrompts.offer(invitee, invite, team, (int) seconds);
        return Outcome.done("invited", name, styled(team));
    }

    public static Outcome accept(ServerPlayer player) {
        MinecraftServer server = player.server;
        Optional<Invite> pending = pending(player.getUUID());
        if (pending.isEmpty()) {
            INVITES.remove(player.getUUID());
            return Outcome.fail("no_invite");
        }
        Invite invite = pending.get();
        INVITES.remove(player.getUUID());
        TeamPrompts.clear(server, invite);
        TeamLedger ledger = TeamLedger.get(server);
        Team team = ledger.team(invite.teamId());
        if (team == null) {
            return Outcome.fail("invite_gone");
        }
        if (ledger.teamOf(player.getUUID()) != null) {
            return Outcome.fail("already_in_team");
        }
        if (team.size() >= maxMembers()) {
            return Outcome.fail("full", styled(team), maxMembers());
        }
        String name = player.getGameProfile().getName();
        team.join(player.getUUID(), name, now());
        ledger.joined(team, player.getUUID());
        TeamSync.written(team);
        tellTeam(server, team, Component.translatable("message.bsp_core.team.member_joined", name).withStyle(ChatFormatting.GREEN), player.getUUID());
        MinecraftForge.EVENT_BUS.post(new TeamEvent.Joined(server, team.id, team.name, player.getUUID()));
        return Outcome.done("joined", styled(team));
    }

    public static Outcome decline(ServerPlayer player) {
        Optional<Invite> pending = pending(player.getUUID());
        INVITES.remove(player.getUUID());
        if (pending.isEmpty()) {
            return Outcome.fail("no_invite");
        }
        Invite invite = pending.get();
        TeamPrompts.clear(player.server, invite);
        Team team = TeamLedger.get(player.server).team(invite.teamId());
        tell(player.server, invite.inviter(), Component.translatable("message.bsp_core.team.invite_declined", player.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW));
        return Outcome.done("declined", team == null ? Component.literal("?") : styled(team));
    }

    // ------------------------------------------------------------------ leaving

    public static Outcome leave(ServerPlayer player) {
        MinecraftServer server = player.server;
        TeamLedger ledger = TeamLedger.get(server);
        Team team = ledger.teamOf(player.getUUID());
        if (team == null) {
            return Outcome.fail("not_in_team");
        }
        if (team.isLeader(player.getUUID()) && team.size() > 1) {
            return Outcome.fail("leader_must_hand_over");
        }
        if (team.size() == 1) {
            return disbandNow(server, team, player.getUUID());
        }
        team.remove(player.getUUID());
        ledger.left(player.getUUID());
        ledger.changed();
        CHAT_ON.remove(player.getUUID());
        TeamChat.sendState(player);
        TeamSync.left(team, player.getUUID());
        tellTeam(server, team, Component.translatable("message.bsp_core.team.member_left", player.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW), null);
        MinecraftForge.EVENT_BUS.post(new TeamEvent.Left(server, team.id, team.name, player.getUUID(), TeamEvent.Left.Reason.LEFT));
        return Outcome.done("left", styled(team));
    }

    public static Outcome kick(ServerPlayer leader, UUID member) {
        Team team = teamOf(leader.server, leader.getUUID());
        if (team == null) {
            return Outcome.fail("not_in_team");
        }
        if (!team.isLeader(leader.getUUID())) {
            return Outcome.fail("not_leader");
        }
        return kickNow(leader.server, team, member);
    }

    private static Outcome kickNow(MinecraftServer server, Team team, UUID member) {
        if (team.isLeader(member)) {
            return Outcome.fail("cannot_remove_leader");
        }
        Team.Member m = team.remove(member);
        if (m == null) {
            return Outcome.fail("not_member", nameOf(server, member));
        }
        TeamLedger ledger = TeamLedger.get(server);
        ledger.left(member);
        ledger.changed();
        CHAT_ON.remove(member);
        ServerPlayer kicked = server.getPlayerList().getPlayer(member);
        if (kicked != null) {
            TeamChat.sendState(kicked);
        }
        TeamSync.left(team, member);
        tell(server, member, Component.translatable("message.bsp_core.team.kicked", styled(team)).withStyle(ChatFormatting.RED));
        tellTeam(server, team, Component.translatable("message.bsp_core.team.member_kicked", m.name).withStyle(ChatFormatting.YELLOW), null);
        MinecraftForge.EVENT_BUS.post(new TeamEvent.Left(server, team.id, team.name, member, TeamEvent.Left.Reason.KICKED));
        return Outcome.done("removed", m.name);
    }

    public static Outcome transfer(ServerPlayer leader, UUID member) {
        Team team = teamOf(leader.server, leader.getUUID());
        if (team == null) {
            return Outcome.fail("not_in_team");
        }
        if (!team.isLeader(leader.getUUID())) {
            return Outcome.fail("not_leader");
        }
        return transferNow(leader.server, team, member);
    }

    private static Outcome transferNow(MinecraftServer server, Team team, UUID member) {
        Team.Member m = team.members().get(member);
        if (m == null) {
            return Outcome.fail("not_member", nameOf(server, member));
        }
        if (team.isLeader(member)) {
            return Outcome.fail("already_leader", m.name);
        }
        UUID previous = team.leader;
        team.leader = member;
        TeamLedger.get(server).changed();
        TeamSync.written(team);
        tell(server, member, Component.translatable("message.bsp_core.team.lead_received", nameOf(server, previous), styled(team)).withStyle(ChatFormatting.GREEN));
        tellTeam(server, team, Component.translatable("message.bsp_core.team.lead_changed", m.name).withStyle(ChatFormatting.YELLOW), member);
        MinecraftForge.EVENT_BUS.post(new TeamEvent.LeaderChanged(server, team.id, team.name, previous, member));
        return Outcome.done("lead_given", m.name);
    }

    public static Outcome disband(ServerPlayer leader) {
        Team team = teamOf(leader.server, leader.getUUID());
        if (team == null) {
            return Outcome.fail("not_in_team");
        }
        if (!team.isLeader(leader.getUUID())) {
            return Outcome.fail("not_leader");
        }
        return disbandNow(leader.server, team, leader.getUUID());
    }

    private static Outcome disbandNow(MinecraftServer server, Team team, @Nullable UUID by) {
        TeamLedger ledger = TeamLedger.get(server);
        List<UUID> members = new ArrayList<>(team.members().keySet());
        ledger.remove(team);
        INVITES.values().removeIf(i -> {
            if (i.teamId().equals(team.id)) {
                TeamPrompts.clear(server, i);
                return true;
            }
            return false;
        });
        CHAT_ON.removeAll(members);
        TeamSync.removed(team);
        Component line = Component.translatable("message.bsp_core.team.disbanded", styled(team)).withStyle(ChatFormatting.YELLOW);
        for (UUID m : members) {
            if (!m.equals(by)) {
                tell(server, m, line);
            }
            ServerPlayer online = server.getPlayerList().getPlayer(m);
            if (online != null) {
                TeamChat.sendState(online);
            }
            MinecraftForge.EVENT_BUS.post(new TeamEvent.Left(server, team.id, team.name, m, TeamEvent.Left.Reason.DISBANDED));
        }
        MinecraftForge.EVENT_BUS.post(new TeamEvent.Disbanded(server, team.id, team.name, by));
        return Outcome.done("disbanded", styled(team));
    }

    // ------------------------------------------------------------------ settings

    public static Outcome rename(ServerPlayer leader, String name) {
        Team team = teamOf(leader.server, leader.getUUID());
        if (team == null) {
            return Outcome.fail("not_in_team");
        }
        if (!team.isLeader(leader.getUUID())) {
            return Outcome.fail("not_leader");
        }
        return renameNow(leader.server, team, name);
    }

    private static Outcome renameNow(MinecraftServer server, Team team, String name) {
        Component bad = checkName(server, name, team);
        if (bad != null) {
            return new Outcome(false, bad);
        }
        team.name = clean(name);
        TeamLedger.get(server).changed();
        TeamSync.written(team);
        tellTeam(server, team, Component.translatable("message.bsp_core.team.renamed", styled(team)).withStyle(ChatFormatting.YELLOW), null);
        return Outcome.done();
    }

    public static Outcome recolour(ServerPlayer leader, ChatFormatting colour) {
        Team team = teamOf(leader.server, leader.getUUID());
        if (team == null) {
            return Outcome.fail("not_in_team");
        }
        if (!team.isLeader(leader.getUUID())) {
            return Outcome.fail("not_leader");
        }
        if (colour == null || !colour.isColor()) {
            return Outcome.fail("bad_colour");
        }
        team.colour = colour;
        TeamLedger.get(leader.server).changed();
        TeamSync.written(team);
        tellTeam(leader.server, team, Component.translatable("message.bsp_core.team.recoloured", styled(team)).withStyle(ChatFormatting.YELLOW), null);
        return Outcome.done();
    }

    /** Pins a note above the board; an empty text takes the note down. */
    public static Outcome pin(ServerPlayer leader, String text) {
        Team team = teamOf(leader.server, leader.getUUID());
        if (team == null) {
            return Outcome.fail("not_in_team");
        }
        if (!team.isLeader(leader.getUUID())) {
            return Outcome.fail("not_leader");
        }
        String t = text.trim();
        if (t.length() > 200) {
            return Outcome.fail("text_long", 200);
        }
        team.pin(t.isEmpty() ? null : new Team.Note(t, leader.getUUID(), leader.getGameProfile().getName(), now()));
        TeamLedger.get(leader.server).changed();
        TeamSync.written(team);
        tellTeam(leader.server, team, Component.translatable(t.isEmpty() ? "message.bsp_core.team.unpinned" : "message.bsp_core.team.pinned", leader.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW), leader.getUUID());
        return Outcome.done();
    }

    // ------------------------------------------------------------------ the board

    /** Leaves a line on the team's board and shows it to the members online here. */
    public static Outcome post(ServerPlayer player, String text) {
        MinecraftServer server = player.server;
        Team team = teamOf(server, player.getUUID());
        if (team == null) {
            return Outcome.fail("not_in_team");
        }
        String t = text.trim();
        if (t.isEmpty()) {
            return Outcome.fail("text_empty");
        }
        if (t.length() > 256) {
            return Outcome.fail("text_long", 256);
        }
        Team.Message m = team.post(player.getUUID(), player.getGameProfile().getName(), t, now(), BSPConfig.getOr(BSPConfig.TEAM_HISTORY_SIZE, 50));
        TeamLedger.get(server).changed();
        TeamSync.posted(team, m);
        TeamSync.member(team, player.getUUID());
        tellTeam(server, team, chatLine(team, m), null);
        return Outcome.done();
    }

    /** A board line as it reads in chat: {@code [Team] name: text} in the team's colour. */
    public static Component chatLine(Team team, Team.Message m) {
        return Component.translatable("message.bsp_core.team.chat", m.authorName(), m.text()).withStyle(team.colour);
    }

    /** Marks every line on the board read for the player (they opened the MESSAGES view). */
    public static void markRead(MinecraftServer server, UUID player) {
        Team team = teamOf(server, player);
        if (team != null) {
            team.markRead(player);
            TeamLedger.get(server).changed();
            TeamSync.member(team, player);
        }
    }

    // ------------------------------------------------------------------ admin

    public static Outcome adminDisband(MinecraftServer server, Team team) {
        return disbandNow(server, team, null);
    }

    public static Outcome adminKick(MinecraftServer server, UUID player) {
        Team team = teamOf(server, player);
        if (team == null) {
            return Outcome.fail("not_member", nameOf(server, player));
        }
        if (team.isLeader(player)) {
            return team.size() == 1 ? disbandNow(server, team, null) : Outcome.fail("cannot_remove_leader");
        }
        return kickNow(server, team, player);
    }

    public static Outcome adminRename(MinecraftServer server, Team team, String name) {
        return renameNow(server, team, name);
    }

    public static Outcome adminLead(MinecraftServer server, Team team, UUID member) {
        return transferNow(server, team, member);
    }

    // ------------------------------------------------------------------ team chat channel

    public static boolean isChatOn(UUID player) {
        return CHAT_ON.contains(player);
    }

    /** Switches the player's chat between the team and everyone; returns the new state. */
    public static boolean toggleChat(MinecraftServer server, UUID player) {
        if (CHAT_ON.remove(player)) {
            return false;
        }
        if (teamOf(server, player) == null) {
            return false;
        }
        CHAT_ON.add(player);
        return true;
    }

    @SubscribeEvent
    public static void onLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            seen(p);
            TeamSync.online(p, true);
        }
    }

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            seen(p);
            CHAT_ON.remove(p.getUUID());
            TeamSync.online(p, false);
        }
    }

    /** Notes the time and the current name on the player's member record. */
    public static void seen(ServerPlayer p) {
        Team team = teamOf(p.server, p.getUUID());
        if (team != null) {
            Team.Member m = team.members().get(p.getUUID());
            if (m != null) {
                m.seenMs = now();
                m.name = p.getGameProfile().getName();
                TeamLedger.get(p.server).changed();
                TeamSync.member(team, p.getUUID());
            }
        }
    }

    // ------------------------------------------------------------------ invites running out

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || INVITES.isEmpty() || ++ticks % 20 != 0) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            expireInvites(server, now());
        }
    }

    /** Drops every invite past its time, telling both sides. Public so tests can move the clock. */
    public static void expireInvites(MinecraftServer server, long nowMs) {
        List<Invite> gone = new ArrayList<>();
        INVITES.values().removeIf(i -> {
            if (i.expiresMs() <= nowMs) {
                gone.add(i);
                return true;
            }
            return false;
        });
        for (Invite i : gone) {
            TeamPrompts.clear(server, i);
            Team team = TeamLedger.get(server).team(i.teamId());
            Component name = team == null ? Component.literal("?") : styled(team);
            tell(server, i.invitee(), Component.translatable("message.bsp_core.team.invite_expired", name).withStyle(ChatFormatting.GRAY));
            tell(server, i.inviter(), Component.translatable("message.bsp_core.team.invite_expired_inviter", nameOf(server, i.invitee())).withStyle(ChatFormatting.GRAY));
        }
    }

    // ------------------------------------------------------------------ telling people

    static long now() {
        return System.currentTimeMillis();
    }

    /** A member's name: online, then as the team last saw them, then the bank's lookup. */
    public static String nameOf(MinecraftServer server, UUID player) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        Team team = teamOf(server, player);
        if (team != null) {
            Team.Member m = team.members().get(player);
            if (m != null && !m.name.isEmpty()) {
                return m.name;
            }
        }
        return com.mrgregles.bsp_core.bank.Bank.nameOf(server, player);
    }

    private static void tell(MinecraftServer server, UUID player, Component message) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            online.displayClientMessage(message, false);
        }
    }

    /** Tells every member online here, except {@code except}. */
    public static void tellTeam(MinecraftServer server, Team team, Component message, @Nullable UUID except) {
        for (UUID m : team.members().keySet()) {
            if (!m.equals(except)) {
                tell(server, m, message);
            }
        }
    }
}
