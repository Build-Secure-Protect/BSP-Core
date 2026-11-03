package com.mrgregles.bsp_core.team;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.api.event.TeamEvent;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import com.mrgregles.bsp_core.totem.TotemAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The rules of alliances: making one, inviting a team, accepting and declining, leaving, the perks a team gives its allies,
 * sharing coordinates, the alliance's board and chat, and the break when an ally starts a steal on an ally's totem. Only a
 * team's leader changes its place in an alliance; every member sees it. Every change goes through here, tells the players
 * concerned and posts a {@link TeamEvent}.
 * <p>
 * An invite goes to the other team's leader, who must be online (on another server it travels over Redis, as a team invite
 * does). It holds a place in the alliance while it waits and runs out after {@code team.inviteSeconds}.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class Alliances {
    private Alliances() {}

    /**
     * An invite waiting for an answer, keyed by the invited team. {@code elsewhere} is the server the other side is on: the invited
     * leader's on the inviter's server, the inviter's on the invited leader's; "" when both are here.
     */
    public record Invite(int id, UUID allianceId, String allianceName, UUID fromTeam, UUID inviter, String inviterName, UUID toTeam, UUID toLeader,
                         String toLeaderName, long expiresMs, String elsewhere, int ref, boolean sent) {
        public boolean remote() {
            return !elsewhere.isEmpty();
        }

        boolean answersElsewhere() {
            return remote() && !sent;
        }
    }

    private static int inviteIds;
    private static Teams.Link link = NetworkStorage::allianceInvite;

    /** For tests: catch the messages to other servers instead of sending them; null puts Redis back. */
    public static void useLink(@Nullable Teams.Link l) {
        link = l == null ? NetworkStorage::allianceInvite : l;
    }

    private static final Map<UUID, Invite> INVITES = new HashMap<>();
    /** Players whose chat goes to their alliance until they switch back (the ALLY CHAT key). */
    private static final Set<UUID> CHAT_ON = new HashSet<>();
    private static int ticks;

    // ------------------------------------------------------------------ lookups

    public static boolean enabled() {
        return Teams.enabled() && BSPConfig.getOr(BSPConfig.ALLIANCE_ENABLED, true);
    }

    public static int maxTeams() {
        return Math.max(2, BSPConfig.getOr(BSPConfig.ALLIANCE_MAX_TEAMS, 3));
    }

    /** The perks this server lets a team give its allies. */
    public static int allowedPerks() {
        int m = 0;
        if (BSPConfig.getOr(BSPConfig.ALLIANCE_ALLOW_CLOAK, true)) {
            m |= TotemAccess.CLOAK;
        }
        if (BSPConfig.getOr(BSPConfig.ALLIANCE_ALLOW_WARD, true)) {
            m |= TotemAccess.WARD;
        }
        if (BSPConfig.getOr(BSPConfig.ALLIANCE_ALLOW_ALARM, true)) {
            m |= TotemAccess.ALARM;
        }
        if (BSPConfig.getOr(BSPConfig.ALLIANCE_ALLOW_MACHINES, true)) {
            m |= TotemAccess.MACHINES;
        }
        return m;
    }

    @Nullable
    public static Alliance allianceOfTeam(MinecraftServer server, UUID teamId) {
        return AllianceLedger.get(server).allianceOf(teamId);
    }

    /** The alliance the player's team is in, or null. */
    @Nullable
    public static Alliance allianceOf(MinecraftServer server, UUID player) {
        Team team = Teams.teamOf(server, player);
        return team == null ? null : allianceOfTeam(server, team.id);
    }

    /** Whether two players are allies: in two different teams of the same alliance. Teammates are not allies. */
    public static boolean allied(MinecraftServer server, UUID a, UUID b) {
        if (!enabled() || a.equals(b)) {
            return false;
        }
        Team ta = Teams.teamOf(server, a), tb = Teams.teamOf(server, b);
        if (ta == null || tb == null || ta.id.equals(tb.id)) {
            return false;
        }
        Alliance al = allianceOfTeam(server, ta.id);
        return al != null && al.has(tb.id);
    }

    /** The perks {@code owner}'s team gives {@code player} as an ally ({@link TotemAccess} flags); 0 when they are not allies. */
    public static int perksFor(MinecraftServer server, UUID owner, UUID player) {
        if (!allied(server, owner, player)) {
            return 0;
        }
        Team ot = Teams.teamOf(server, owner);
        Alliance.Seat seat = allianceOfTeam(server, ot.id).teams().get(ot.id);
        return seat == null ? 0 : seat.perks & allowedPerks();
    }

    /** Every ally of the player (the members of the other teams in their alliance). */
    public static List<UUID> allies(MinecraftServer server, UUID player) {
        Team team = Teams.teamOf(server, player);
        Alliance a = team == null || !enabled() ? null : allianceOfTeam(server, team.id);
        if (a == null) {
            return List.of();
        }
        List<UUID> out = new ArrayList<>();
        TeamLedger teams = TeamLedger.get(server);
        for (UUID t : a.teams().keySet()) {
            Team other = teams.team(t);
            if (other != null && !t.equals(team.id)) {
                out.addAll(other.members().keySet());
            }
        }
        return out;
    }

    /**
     * Where a player of an alliance is. {@code state} is 0 online here, 1 online on another server, 2 offline; {@code server} names
     * that server (this one's id when here, "" when offline); {@code dim} is the dimension's id when known; {@code pos} only when
     * their team shares its coordinates.
     */
    public record Whereabouts(UUID id, String name, boolean leader, int state, String server, String dim, @Nullable net.minecraft.core.BlockPos pos, long seenMs) {}

    public static final int HERE = 0, ELSEWHERE = 1, OFFLINE = 2;

    /** Where every member of the team is, as its allies may see it. */
    public static List<Whereabouts> whereabouts(MinecraftServer server, Team team, boolean shares) {
        String me = BSPConfig.getOr(BSPConfig.STORAGE_SERVER_ID, "server");
        List<Whereabouts> out = new ArrayList<>();
        for (var e : team.members().entrySet()) {
            ServerPlayer here = server.getPlayerList().getPlayer(e.getKey());
            if (here != null) {
                out.add(new Whereabouts(e.getKey(), here.getGameProfile().getName(), team.isLeader(e.getKey()), HERE, me, here.level().dimension().location().toString(),
                        shares ? here.blockPosition() : null, e.getValue().seenMs));
                continue;
            }
            var elsewhere = NetworkStorage.onlineElsewhere(e.getKey());
            if (elsewhere.isPresent()) {
                NetworkStorage.OnlinePlayer o = elsewhere.get();
                boolean known = !o.dim().isEmpty() && o.posMs() > 0;
                out.add(new Whereabouts(e.getKey(), e.getValue().name, team.isLeader(e.getKey()), ELSEWHERE, o.server(), known ? o.dim() : "",
                        shares && known ? new net.minecraft.core.BlockPos(o.x(), o.y(), o.z()) : null, e.getValue().seenMs));
            } else {
                out.add(new Whereabouts(e.getKey(), e.getValue().name, team.isLeader(e.getKey()), OFFLINE, "", "", null, e.getValue().seenMs));
            }
        }
        return out;
    }

    /** Every player in every team of the alliance. */
    public static Set<UUID> players(MinecraftServer server, Alliance a) {
        return players(server, a.teams().keySet());
    }

    static Set<UUID> players(MinecraftServer server, java.util.Collection<UUID> teamIds) {
        Set<UUID> out = new HashSet<>();
        TeamLedger teams = TeamLedger.get(server);
        for (UUID t : teamIds) {
            Team team = teams.team(t);
            if (team != null) {
                out.addAll(team.members().keySet());
            }
        }
        return out;
    }

    /** The invite waiting for the team, if one is and it has not run out. */
    public static Optional<Invite> pending(UUID teamId) {
        Invite i = INVITES.get(teamId);
        return i == null || i.expiresMs() <= now() ? Optional.empty() : Optional.of(i);
    }

    /** The invites an alliance has waiting, for its invite view. */
    public static List<Invite> invitesOf(UUID allianceId) {
        long now = now();
        List<Invite> out = new ArrayList<>();
        for (Invite i : INVITES.values()) {
            if (i.allianceId().equals(allianceId) && i.expiresMs() > now) {
                out.add(i);
            }
        }
        return out;
    }

    /** The invite waiting for the player's team when they lead it. */
    public static Optional<Invite> pendingFor(MinecraftServer server, UUID leader) {
        Team team = Teams.teamOf(server, leader);
        return team == null || !team.isLeader(leader) ? Optional.empty() : pending(team.id);
    }

    @Nullable
    public static Component checkName(MinecraftServer server, String name, @Nullable Alliance self) {
        int min = BSPConfig.getOr(BSPConfig.TEAM_NAME_MIN, 3), max = BSPConfig.getOr(BSPConfig.TEAM_NAME_MAX, 16);
        String n = Teams.clean(name);
        if (n.length() < min || n.length() > max) {
            return fail("name_length", min, max).message();
        }
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == ' ' || c == '_') || c > 127) {
                return fail("name_chars").message();
            }
        }
        Alliance other = AllianceLedger.get(server).byName(n);
        if (other != null && other != self) {
            return fail("name_taken", other.name).message();
        }
        return null;
    }

    public static Component styled(Alliance a) {
        return Component.literal(a.name).withStyle(ChatFormatting.DARK_AQUA);
    }

    // ------------------------------------------------------------------ outcomes

    static Teams.Outcome done(String key, Object... args) {
        return new Teams.Outcome(true, Component.translatable("message.bsp_core.alliance." + key, args).withStyle(ChatFormatting.GREEN));
    }

    static Teams.Outcome fail(String key, Object... args) {
        return new Teams.Outcome(false, Component.translatable("message.bsp_core.alliance." + key, args).withStyle(ChatFormatting.RED));
    }

    /** The player's team when they lead it, or the reason they cannot act for it. */
    private record Lead(@Nullable Team team, @Nullable Teams.Outcome refusal) {}

    private static Lead lead(ServerPlayer p) {
        if (!enabled()) {
            return new Lead(null, fail("disabled"));
        }
        Team team = Teams.teamOf(p.server, p.getUUID());
        if (team == null) {
            return new Lead(null, fail("no_team"));
        }
        if (!team.isLeader(p.getUUID())) {
            return new Lead(null, fail("not_leader"));
        }
        return new Lead(team, null);
    }

    // ------------------------------------------------------------------ making one and inviting

    public static Teams.Outcome create(ServerPlayer leader, String name) {
        Lead l = lead(leader);
        if (l.team() == null) {
            return l.refusal();
        }
        MinecraftServer server = leader.server;
        AllianceLedger ledger = AllianceLedger.get(server);
        if (ledger.allianceOf(l.team().id) != null) {
            return fail("already_allied");
        }
        Component bad = checkName(server, name, null);
        if (bad != null) {
            return new Teams.Outcome(false, bad);
        }
        long now = now();
        Alliance a = new Alliance(UUID.randomUUID(), Teams.clean(name), now);
        a.join(l.team().id, now);
        ledger.add(a);
        dropInvite(server, INVITES.remove(l.team().id));
        AllianceSync.created(server, a, l.team().id);
        Teams.tellTeam(server, l.team(), Component.translatable("message.bsp_core.alliance.created_team", leader.getGameProfile().getName(), styled(a)).withStyle(ChatFormatting.GREEN), leader.getUUID());
        MinecraftForge.EVENT_BUS.post(new TeamEvent.Allied(server, l.team().id, l.team().name, a.id, a.name));
        return done("created", styled(a));
    }

    /** Invites another team: the invite goes to its leader, here or on another server. */
    public static Teams.Outcome invite(ServerPlayer inviter, UUID toTeamId) {
        Team to = TeamLedger.get(inviter.server).team(toTeamId);
        return invite(inviter, toTeamId, to == null ? null : inviter.server.getPlayerList().getPlayer(to.leader));
    }

    /** As {@link #invite(ServerPlayer, UUID)}, with the invited team's leader as found here (tests pass a mock player). */
    public static Teams.Outcome invite(ServerPlayer inviter, UUID toTeamId, @Nullable ServerPlayer leaderHere) {
        Lead l = lead(inviter);
        if (l.team() == null) {
            return l.refusal();
        }
        MinecraftServer server = inviter.server;
        AllianceLedger ledger = AllianceLedger.get(server);
        Alliance a = ledger.allianceOf(l.team().id);
        if (a == null) {
            return fail("no_alliance");
        }
        Team to = TeamLedger.get(server).team(toTeamId);
        if (to == null) {
            return fail("team_gone");
        }
        if (to.id.equals(l.team().id)) {
            return fail("invite_self");
        }
        if (ledger.allianceOf(to.id) != null) {
            return fail("team_allied", Teams.styled(to));
        }
        if (pending(to.id).isPresent()) {
            return fail("invite_pending", Teams.styled(to));
        }
        if (a.size() + invitesOf(a.id).size() >= maxTeams()) {
            return fail("full", styled(a), maxTeams());
        }
        long wait = waitWith(server, a, to.id);
        if (wait > 0) {
            return fail("waiting", Teams.styled(to), hoursLeft(wait));
        }
        String leaderName = Teams.nameOf(server, to.leader);
        long expires = now() + inviteSeconds() * 1000L;
        int id = ++inviteIds;
        ServerPlayer here = leaderHere != null && leaderHere.getUUID().equals(to.leader) ? leaderHere : null;
        if (here != null) {
            Invite invite = new Invite(id, a.id, a.name, l.team().id, inviter.getUUID(), inviter.getGameProfile().getName(), to.id, to.leader, leaderName, expires, "", id, false);
            INVITES.put(to.id, invite);
            AlliancePrompts.offer(here, invite, l.team(), a, (int) inviteSeconds());
            return done("invited", Teams.styled(to), leaderName);
        }
        var elsewhere = NetworkStorage.onlineElsewhere(to.leader);
        if (elsewhere.isEmpty()) {
            return fail("leader_offline", Teams.styled(to), leaderName);
        }
        Invite invite = new Invite(id, a.id, a.name, l.team().id, inviter.getUUID(), inviter.getGameProfile().getName(), to.id, to.leader, leaderName, expires,
                elsewhere.get().server(), id, true);
        CompoundTag m = message("offer", invite);
        m.putLong("Expires", expires);
        if (!link.send(elsewhere.get().server(), m)) {
            return fail("leader_unreachable", leaderName, elsewhere.get().server());
        }
        INVITES.put(to.id, invite);
        return done("invited", Teams.styled(to), leaderName);
    }

    /** The latest wait between the team and any team in the alliance; 0 when none. */
    static long waitWith(MinecraftServer server, Alliance a, UUID teamId) {
        AllianceLedger ledger = AllianceLedger.get(server);
        long now = now(), until = 0;
        for (UUID t : a.teams().keySet()) {
            until = Math.max(until, ledger.waitUntil(teamId, t, now));
        }
        return until;
    }

    static long hoursLeft(long untilMs) {
        return Math.max(1, (untilMs - now() + 3_599_999L) / 3_600_000L);
    }

    private static long inviteSeconds() {
        return Math.max(5, BSPConfig.getOr(BSPConfig.TEAM_INVITE_SECONDS, 60));
    }

    // ------------------------------------------------------------------ invites between servers

    private static CompoundTag message(String op, Invite i) {
        CompoundTag m = new CompoundTag();
        m.putString("Op", op);
        m.putUUID("Alliance", i.allianceId());
        m.putString("AllianceName", i.allianceName());
        m.putUUID("From", i.fromTeam());
        m.putUUID("Inviter", i.inviter());
        m.putString("InviterName", i.inviterName());
        m.putUUID("To", i.toTeam());
        m.putUUID("Leader", i.toLeader());
        m.putString("LeaderName", i.toLeaderName());
        m.putInt("Ref", i.ref());
        return m;
    }

    /** An alliance invite message from another server (the server thread); see {@code Teams.onRemote} for the shape. */
    public static void onRemote(MinecraftServer server, String from, CompoundTag m) {
        onRemote(server, from, m, m.hasUUID("Leader") ? server.getPlayerList().getPlayer(m.getUUID("Leader")) : null);
    }

    public static void onRemote(MinecraftServer server, String from, CompoundTag m, @Nullable ServerPlayer online) {
        if (!m.hasUUID("Alliance") || !m.hasUUID("To") || !m.hasUUID("Leader") || !m.hasUUID("From") || !m.hasUUID("Inviter")) {
            return;
        }
        UUID allianceId = m.getUUID("Alliance"), toTeam = m.getUUID("To");
        switch (m.getString("Op")) {
            case "offer" -> {
                Invite invite = new Invite(++inviteIds, allianceId, m.getString("AllianceName"), m.getUUID("From"), m.getUUID("Inviter"), m.getString("InviterName"),
                        toTeam, m.getUUID("Leader"), m.getString("LeaderName"), m.getLong("Expires"), from, m.getInt("Ref"), false);
                Alliance a = AllianceLedger.get(server).alliance(allianceId);
                Team fromTeam = TeamLedger.get(server).team(invite.fromTeam());
                Team mine = TeamLedger.get(server).team(toTeam);
                String refusal = online == null ? "invite_left" : a == null || fromTeam == null ? "invite_retry"
                        : mine == null || !mine.isLeader(online.getUUID()) ? "invite_left"
                        : AllianceLedger.get(server).allianceOf(toTeam) != null ? "team_allied_remote" : pending(toTeam).isPresent() ? "invite_pending_remote" : null;
                if (a == null) {
                    NetworkStorage.allianceReload(allianceId);
                }
                if (refusal != null) {
                    CompoundTag back = message("refused", invite);
                    back.putString("Reason", refusal);
                    link.send(from, back);
                    return;
                }
                INVITES.put(toTeam, invite);
                AlliancePrompts.offer(online, invite, fromTeam, a, (int) Math.max(1, (invite.expiresMs() - now() + 999) / 1000));
            }
            case "refused", "answer" -> {
                Invite mine = INVITES.get(toTeam);
                if (mine == null || !mine.sent() || !mine.allianceId().equals(allianceId) || !mine.elsewhere().equals(from) || mine.ref() != m.getInt("Ref")) {
                    return;
                }
                INVITES.remove(toTeam);
                String key = m.getString("Op").equals("refused") ? m.getString("Reason") : m.getString("Result").equals("declined") ? "invite_declined" : null;
                if (key == null) {
                    return; // joined (told as the alliance changes) or could not join (told on their server)
                }
                Team t = TeamLedger.get(server).team(toTeam);
                Component teamName = t == null ? Component.literal(mine.toLeaderName()) : Teams.styled(t);
                tell(server, mine.inviter(), Component.translatable("message.bsp_core.alliance." + key, teamName, mine.toLeaderName(), from)
                        .withStyle(key.equals("invite_declined") ? ChatFormatting.YELLOW : ChatFormatting.RED));
            }
            default -> { }
        }
    }

    /** An invite to a team that can no longer take it (it made its own alliance, or is gone): the card goes, the inviter hears no. */
    private static void dropInvite(MinecraftServer server, @Nullable Invite invite) {
        if (invite == null) {
            return;
        }
        AlliancePrompts.clear(server, invite);
        if (invite.answersElsewhere()) {
            answered(invite, "failed");
        }
    }

    private static void answered(Invite invite, String result) {
        if (invite.answersElsewhere()) {
            CompoundTag m = message("answer", invite);
            m.putString("Result", result);
            link.send(invite.elsewhere(), m);
        }
    }

    public static Teams.Outcome accept(ServerPlayer leader) {
        Lead l = lead(leader);
        if (l.team() == null) {
            return l.refusal();
        }
        MinecraftServer server = leader.server;
        Optional<Invite> pending = pending(l.team().id);
        INVITES.remove(l.team().id);
        if (pending.isEmpty()) {
            return fail("no_invite");
        }
        Invite invite = pending.get();
        AlliancePrompts.clear(server, invite);
        AllianceLedger ledger = AllianceLedger.get(server);
        Alliance a = ledger.alliance(invite.allianceId());
        if (a == null) {
            answered(invite, "failed");
            return fail("invite_gone");
        }
        if (ledger.allianceOf(l.team().id) != null) {
            answered(invite, "failed");
            return fail("already_allied");
        }
        if (a.size() >= maxTeams()) {
            answered(invite, "failed");
            return fail("full", styled(a), maxTeams());
        }
        if (waitWith(server, a, l.team().id) > 0) {
            answered(invite, "failed");
            return fail("waiting_self", hoursLeft(waitWith(server, a, l.team().id)));
        }
        answered(invite, "joined");
        Set<UUID> before = players(server, a);
        a.join(l.team().id, now());
        ledger.joined(a, l.team().id);
        AllianceSync.joined(server, a, l.team().id);
        tellAlliance(server, a, Component.translatable("message.bsp_core.alliance.team_joined", Teams.styled(l.team()), styled(a)).withStyle(ChatFormatting.GREEN), null);
        before.addAll(l.team().members().keySet());
        TeamAccessSync.resyncOwners(server, before);
        MinecraftForge.EVENT_BUS.post(new TeamEvent.Allied(server, l.team().id, l.team().name, a.id, a.name));
        return Teams.Outcome.done();
    }

    public static Teams.Outcome decline(ServerPlayer leader) {
        Lead l = lead(leader);
        if (l.team() == null) {
            return l.refusal();
        }
        Optional<Invite> pending = pending(l.team().id);
        INVITES.remove(l.team().id);
        if (pending.isEmpty()) {
            return fail("no_invite");
        }
        Invite invite = pending.get();
        AlliancePrompts.clear(leader.server, invite);
        if (invite.answersElsewhere()) {
            answered(invite, "declined");
        } else {
            tell(leader.server, invite.inviter(), Component.translatable("message.bsp_core.alliance.invite_declined", Teams.styled(l.team()), leader.getGameProfile().getName(), "")
                    .withStyle(ChatFormatting.YELLOW));
        }
        return done("declined", invite.allianceName());
    }

    // ------------------------------------------------------------------ leaving

    public static Teams.Outcome leave(ServerPlayer leader) {
        Lead l = lead(leader);
        if (l.team() == null) {
            return l.refusal();
        }
        Alliance a = allianceOfTeam(leader.server, l.team().id);
        if (a == null) {
            return fail("no_alliance");
        }
        part(leader.server, a, l.team(), false, true);
        return done("left", styled(a));
    }

    /**
     * Takes the team out of the alliance and ends the alliance when fewer than two teams are left. {@code quiet}: no line to the
     * alliance (the break has its own). {@code remember}: for {@code alliance.leftStealHours} a steal between the team and the
     * teams it leaves still counts as breaking the alliance (a leader leaving on purpose, so a member can steal at once).
     */
    private static void part(MinecraftServer server, Alliance a, Team team, boolean quiet, boolean remember) {
        AllianceLedger ledger = AllianceLedger.get(server);
        Set<UUID> concerned = players(server, a);
        List<UUID> others = new ArrayList<>(a.teams().keySet());
        others.remove(team.id);
        long hours = BSPConfig.getOr(BSPConfig.ALLIANCE_LEFT_HOURS, 24);
        if (remember && hours > 0 && !others.isEmpty()) {
            List<AllianceLedger.Former> formers = new ArrayList<>();
            long until = now() + hours * 3_600_000L;
            for (UUID o : others) {
                AllianceLedger.Former f = new AllianceLedger.Former(team.id, o, a.id, a.name, until);
                ledger.addFormer(f);
                formers.add(f);
            }
            AllianceSync.formers(formers);
        }
        a.remove(team.id);
        ledger.left(team.id);
        boolean ends = a.size() < 2;
        if (ends) {
            ledger.remove(a);
            INVITES.values().removeIf(i -> {
                if (i.allianceId().equals(a.id)) {
                    AlliancePrompts.clear(server, i);
                    return true;
                }
                return false;
            });
        }
        AllianceSync.left(server, a, team.id, ends);
        for (UUID p : team.members().keySet()) {
            chatOff(server, p);
        }
        if (!quiet) {
            Teams.tellTeam(server, team, Component.translatable("message.bsp_core.alliance.we_left", styled(a)).withStyle(ChatFormatting.YELLOW), null);
            tellTeams(server, others, Component.translatable(ends ? "message.bsp_core.alliance.team_left_ended" : "message.bsp_core.alliance.team_left",
                    Teams.styled(team), styled(a)).withStyle(ChatFormatting.YELLOW));
        }
        if (ends) {
            for (UUID p : players(server, others)) {
                chatOff(server, p);
            }
        }
        TeamAccessSync.resyncOwners(server, concerned);
        MinecraftForge.EVENT_BUS.post(new TeamEvent.AllianceLeft(server, team.id, team.name, a.id, a.name, ends));
    }

    /** A team was disbanded here: it leaves its alliance first. */
    static void teamGone(MinecraftServer server, Team team) {
        dropInvite(server, INVITES.remove(team.id));
        INVITES.values().removeIf(i -> {
            if (i.fromTeam().equals(team.id)) {
                AlliancePrompts.clear(server, i);
                return true;
            }
            return false;
        });
        Alliance a = allianceOfTeam(server, team.id);
        if (a != null) {
            part(server, a, team, false, false);
        }
    }

    /** An admin ends the alliance for every team in it. */
    public static Teams.Outcome adminDissolve(MinecraftServer server, Alliance a) {
        TeamLedger teams = TeamLedger.get(server);
        for (UUID t : new ArrayList<>(a.teams().keySet())) {
            Team team = teams.team(t);
            if (team != null && AllianceLedger.get(server).alliance(a.id) != null) {
                part(server, a, team, false, false);
            }
        }
        if (AllianceLedger.get(server).alliance(a.id) != null) { // only teams this server does not know were left
            AllianceLedger.get(server).remove(a);
            AllianceSync.removed(a);
        }
        return Teams.Outcome.done();
    }

    // ------------------------------------------------------------------ perks and coordinates

    /** Turns one perk the leader's team gives its allies on or off. */
    public static Teams.Outcome setPerk(ServerPlayer leader, int flag, boolean on) {
        Lead l = lead(leader);
        if (l.team() == null) {
            return l.refusal();
        }
        Alliance a = allianceOfTeam(leader.server, l.team().id);
        if (a == null) {
            return fail("no_alliance");
        }
        if ((flag & TotemAccess.ALLY_PERKS) == 0 || Integer.bitCount(flag) != 1) {
            return fail("bad_perk");
        }
        if (on && (allowedPerks() & flag) == 0) {
            return fail("perk_forbidden");
        }
        Alliance.Seat seat = a.teams().get(l.team().id);
        seat.perks = on ? seat.perks | flag : seat.perks & ~flag;
        AllianceLedger.get(leader.server).changed();
        AllianceSync.seat(a, l.team().id, seat);
        TeamAccessSync.resyncOwners(leader.server, new HashSet<>(l.team().members().keySet()));
        tellAlliance(leader.server, a, Component.translatable(on ? "message.bsp_core.alliance.perk_on" : "message.bsp_core.alliance.perk_off", Teams.styled(l.team()),
                Component.translatable("gui.bsp_core.alliance.perk." + perkKey(flag))).withStyle(ChatFormatting.GRAY), null);
        return Teams.Outcome.done();
    }

    public static String perkKey(int flag) {
        return switch (flag) {
            case TotemAccess.CLOAK -> "cloak";
            case TotemAccess.WARD -> "ward";
            case TotemAccess.ALARM -> "alarm";
            default -> "machines";
        };
    }

    public static Teams.Outcome setShare(ServerPlayer leader, boolean on) {
        Lead l = lead(leader);
        if (l.team() == null) {
            return l.refusal();
        }
        Alliance a = allianceOfTeam(leader.server, l.team().id);
        if (a == null) {
            return fail("no_alliance");
        }
        Alliance.Seat seat = a.teams().get(l.team().id);
        seat.shareCoords = on;
        AllianceLedger.get(leader.server).changed();
        AllianceSync.seat(a, l.team().id, seat);
        tellAlliance(leader.server, a, Component.translatable(on ? "message.bsp_core.alliance.share_on" : "message.bsp_core.alliance.share_off", Teams.styled(l.team()))
                .withStyle(ChatFormatting.GRAY), null);
        return Teams.Outcome.done();
    }

    // ------------------------------------------------------------------ the break

    /** How many times as long a steal takes when it breaks an alliance. */
    public static double breakStealMultiplier() {
        return Math.max(1.0, BSPConfig.getOr(BSPConfig.ALLIANCE_BREAK_STEAL, 1.5));
    }

    /**
     * Whether {@code thief}'s steal on {@code owner}'s totem breaks an alliance: they are allies, or their teams were allied until
     * one of them left less than {@code alliance.leftStealHours} ago.
     */
    public static boolean stealBreaks(MinecraftServer server, @Nullable UUID owner, UUID thief) {
        if (owner == null || !enabled()) {
            return false;
        }
        if (allied(server, owner, thief)) {
            return true;
        }
        Team tt = Teams.teamOf(server, thief), ot = Teams.teamOf(server, owner);
        return tt != null && ot != null && !tt.id.equals(ot.id) && AllianceLedger.get(server).former(tt.id, ot.id, now()) != null;
    }

    /** The steal's seconds when it breaks an alliance: longer by {@link #breakStealMultiplier}; unchanged otherwise. */
    public static int stealSeconds(MinecraftServer server, @Nullable UUID owner, UUID thief, int seconds) {
        return stealBreaks(server, owner, thief) ? (int) Math.round(seconds * breakStealMultiplier()) : seconds;
    }

    /**
     * An ally started a steal on {@code owner}'s totem: the thief's team is thrown out of the alliance at once (its perks stop),
     * it waits {@code alliance.breakCooldownHours} before allying again with any team that was in it, and every member of those
     * teams is told, on any server. True when an alliance broke.
     */
    public static boolean breakBySteal(MinecraftServer server, UUID thief, String thiefName, UUID owner, String ownerName) {
        if (!enabled()) {
            return false;
        }
        if (!allied(server, owner, thief)) {
            return breakAfterLeaving(server, thief, thiefName, owner, ownerName);
        }
        Team thiefTeam = Teams.teamOf(server, thief);
        Alliance a = allianceOfTeam(server, thiefTeam.id);
        List<UUID> before = new ArrayList<>(a.teams().keySet());
        List<UUID> others = new ArrayList<>(before);
        others.remove(thiefTeam.id);
        long hours = BSPConfig.getOr(BSPConfig.ALLIANCE_BREAK_HOURS, 24);
        List<AllianceLedger.Wait> waits = new ArrayList<>();
        if (hours > 0) {
            long until = now() + hours * 3_600_000L;
            for (UUID o : others) {
                waits.add(new AllianceLedger.Wait(thiefTeam.id, o, until));
            }
        }
        AllianceLedger ledger = AllianceLedger.get(server);
        waits.forEach(ledger::addWait);
        AllianceSync.waits(waits);
        part(server, a, thiefTeam, true, false);
        boolean ended = AllianceLedger.get(server).alliance(a.id) == null;
        BSPCore.LOGGER.info("BSP-Core alliances: {} of {} started stealing {}'s totem; {} is out of {}{}", thiefName, thiefTeam.name, ownerName, thiefTeam.name, a.name,
                ended ? ", which has ended" : "");
        MutableComponent line = Component.translatable("message.bsp_core.alliance.broken", thiefName, Teams.styled(thiefTeam), ownerName, styled(a)).withStyle(ChatFormatting.RED);
        Component third = ended ? Component.translatable("message.bsp_core.alliance.ended", styled(a)).withStyle(ChatFormatting.RED) : null;
        announceBreak(server, thief, thiefName, thiefTeam, ownerName, a.name, before, line, hours, third,
                Component.translatable("message.bsp_core.alliance.broken_thief", styled(a), Teams.styled(thiefTeam)).withStyle(ChatFormatting.GOLD));
        MinecraftForge.EVENT_BUS.post(new TeamEvent.AllianceBroken(server, thiefTeam.id, thiefTeam.name, a.id, a.name, thief, owner, ended));
        return true;
    }

    /**
     * A steal between two teams that were allied until one of them left less than {@code alliance.leftStealHours} ago: it counts as
     * breaking the alliance. The thief's team waits as after a break, every team it left lately is told, and the records are used
     * up (its next steal on them is an ordinary one).
     */
    private static boolean breakAfterLeaving(MinecraftServer server, UUID thief, String thiefName, UUID owner, String ownerName) {
        Team thiefTeam = Teams.teamOf(server, thief), ownerTeam = Teams.teamOf(server, owner);
        if (thiefTeam == null || ownerTeam == null || thiefTeam.id.equals(ownerTeam.id)) {
            return false;
        }
        AllianceLedger ledger = AllianceLedger.get(server);
        long now = now();
        AllianceLedger.Former f = ledger.former(thiefTeam.id, ownerTeam.id, now);
        if (f == null) {
            return false;
        }
        List<UUID> others = new ArrayList<>();
        for (AllianceLedger.Former x : ledger.formersOf(thiefTeam.id, now)) {
            UUID o = x.team().equals(thiefTeam.id) ? x.other() : x.team();
            if (!others.contains(o)) {
                others.add(o);
            }
        }
        long hours = BSPConfig.getOr(BSPConfig.ALLIANCE_BREAK_HOURS, 24);
        List<AllianceLedger.Wait> waits = new ArrayList<>();
        if (hours > 0) {
            long until = now + hours * 3_600_000L;
            for (UUID o : others) {
                waits.add(new AllianceLedger.Wait(thiefTeam.id, o, until));
            }
        }
        waits.forEach(ledger::addWait);
        AllianceSync.waits(waits);
        ledger.spendFormers(thiefTeam.id);
        AllianceSync.formersSpent(thiefTeam.id);
        BSPCore.LOGGER.info("BSP-Core alliances: {} of {} started stealing {}'s totem soon after the teams left {}; it counts as breaking it", thiefName, thiefTeam.name, ownerName,
                f.allianceName());
        List<UUID> told = new ArrayList<>(others);
        told.add(thiefTeam.id);
        Component allianceName = Component.literal(f.allianceName()).withStyle(ChatFormatting.DARK_AQUA);
        long within = BSPConfig.getOr(BSPConfig.ALLIANCE_LEFT_HOURS, 24);
        MutableComponent line = Component.translatable("message.bsp_core.alliance.broken_left", thiefName, Teams.styled(thiefTeam), ownerName, allianceName, within)
                .withStyle(ChatFormatting.RED);
        announceBreak(server, thief, thiefName, thiefTeam, ownerName, f.allianceName(), told, line, hours, null,
                Component.translatable("message.bsp_core.alliance.broken_thief_left", allianceName, within).withStyle(ChatFormatting.GOLD));
        MinecraftForge.EVENT_BUS.post(new TeamEvent.AllianceBroken(server, thiefTeam.id, thiefTeam.name, f.allianceId(), f.allianceName(), thief, owner,
                ledger.alliance(f.allianceId()) == null));
        return true;
    }

    /**
     * Tells every member of {@code teams} (later at login when offline, on any server): the break line, the wait, and {@code ended}
     * to those here; the thief gets {@code thiefLine}; and the red card goes up here and, over Redis, on every other server.
     */
    private static void announceBreak(MinecraftServer server, UUID thief, String thiefName, Team thiefTeam, String ownerName, String allianceName, List<UUID> teams,
                                      Component line, long hours, @Nullable Component ended, Component thiefLine) {
        Component second = hours <= 0 ? null : Component.translatable("message.bsp_core.alliance.broken_wait", Teams.styled(thiefTeam), hours).withStyle(ChatFormatting.RED);
        for (UUID p : players(server, teams)) {
            if (p.equals(thief)) {
                continue;
            }
            NetworkStorage.tell(server, p, line);
            if (second != null) {
                NetworkStorage.tell(server, p, second);
            }
            if (ended != null) {
                tell(server, p, ended); // the other servers say it themselves when the alliance goes there
            }
        }
        tell(server, thief, thiefLine);
        // the card, here and (over Redis) on every other server
        CompoundTag card = new CompoundTag();
        card.putString("Alliance", allianceName);
        card.putString("Thief", thiefName);
        card.putUUID("ThiefId", thief);
        card.putUUID("ThiefTeamId", thiefTeam.id);
        card.putString("ThiefTeam", thiefTeam.name);
        card.putInt("Colour", thiefTeam.colour.getId());
        card.putString("Owner", ownerName);
        net.minecraft.nbt.ListTag ids = new net.minecraft.nbt.ListTag();
        for (UUID p : players(server, teams)) {
            ids.add(net.minecraft.nbt.NbtUtils.createUUID(p));
        }
        card.put("Players", ids);
        brokenCard(server, card);
        NetworkStorage.allianceBroken(card);
    }

    /** Shows the break card, with a warning sound, to the players named in it who are online here (never the thief). */
    public static void brokenCard(MinecraftServer server, CompoundTag card) {
        UUID thief = card.hasUUID("ThiefId") ? card.getUUID("ThiefId") : null;
        if (card.hasUUID("ThiefTeamId")) {
            AllianceSync.broke(card.getUUID("ThiefTeamId")); // the copy read next does not announce the team's leaving again
        }
        for (net.minecraft.nbt.Tag raw : card.getList("Players", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
            UUID id = net.minecraft.nbt.NbtUtils.loadUUID(raw);
            ServerPlayer p = id.equals(thief) ? null : server.getPlayerList().getPlayer(id);
            if (p != null) {
                AlliancePrompts.broken(p, card);
            }
        }
    }

    // ------------------------------------------------------------------ the board and chat

    public static Teams.Outcome post(ServerPlayer player, String text) {
        MinecraftServer server = player.server;
        if (!enabled()) {
            return fail("disabled");
        }
        Team team = Teams.teamOf(server, player.getUUID());
        Alliance a = team == null ? null : allianceOfTeam(server, team.id);
        if (a == null) {
            return fail("no_alliance");
        }
        String t = text.trim();
        if (t.isEmpty()) {
            return Teams.Outcome.fail("text_empty");
        }
        if (t.length() > 256) {
            return Teams.Outcome.fail("text_long", 256);
        }
        Alliance.Message m = a.post(player.getUUID(), player.getGameProfile().getName(), team.name, team.colour.getId(), t, now(), BSPConfig.getOr(BSPConfig.TEAM_HISTORY_SIZE, 50));
        AllianceLedger.get(server).changed();
        AllianceSync.posted(a, m);
        AllianceSync.read(a, player.getUUID());
        tellAlliance(server, a, chatLine(m), null);
        return Teams.Outcome.done();
    }

    /** A board line as it reads in chat: {@code [Ally] [Team] name: text}. */
    public static Component chatLine(Alliance.Message m) {
        ChatFormatting c = ChatFormatting.getById(m.colour());
        return Component.translatable("message.bsp_core.alliance.chat", Component.literal(m.teamName()).withStyle(c == null || !c.isColor() ? ChatFormatting.AQUA : c),
                m.authorName(), m.text()).withStyle(ChatFormatting.DARK_AQUA);
    }

    public static void markRead(MinecraftServer server, UUID player) {
        Alliance a = allianceOf(server, player);
        if (a != null) {
            a.markRead(player);
            AllianceLedger.get(server).changed();
            AllianceSync.read(a, player);
        }
    }

    public static boolean isChatOn(UUID player) {
        return CHAT_ON.contains(player);
    }

    /** Switches the player's chat between the alliance and everyone (the team channel goes off); returns the new state. */
    public static boolean toggleChat(MinecraftServer server, UUID player) {
        if (CHAT_ON.remove(player)) {
            return false;
        }
        if (allianceOf(server, player) == null || !enabled()) {
            return false;
        }
        Teams.chatOff(player);
        CHAT_ON.add(player);
        return true;
    }

    /** The alliance channel goes off (the team channel took over, or the player's team left the alliance). */
    static void chatOff(MinecraftServer server, UUID player) {
        if (CHAT_ON.remove(player)) {
            ServerPlayer online = server.getPlayerList().getPlayer(player);
            if (online != null) {
                TeamChat.sendState(online);
            }
        }
    }

    static void chatOff(UUID player) {
        CHAT_ON.remove(player);
    }

    // ------------------------------------------------------------------ ally teleports (moved by a teleport mod)

    public static Teams.Outcome requestTeleport(ServerPlayer player, UUID target) {
        MinecraftServer server = player.server;
        if (!allied(server, player.getUUID(), target)) {
            return fail("not_ally", Teams.nameOf(server, target));
        }
        if (com.mrgregles.bsp_core.api.BspTeams.allyTeleporter().isEmpty()) {
            return Teams.Outcome.fail("no_teleport");
        }
        boolean here = server.getPlayerList().getPlayer(target) != null;
        String where = here ? "" : NetworkStorage.onlineElsewhere(target).map(NetworkStorage.OnlinePlayer::server).orElse("");
        if (!here && where.isEmpty()) {
            return Teams.Outcome.fail("teleport_offline", Teams.nameOf(server, target));
        }
        Team team = Teams.teamOf(server, player.getUUID());
        Alliance a = allianceOfTeam(server, team.id);
        TeamEvent.AllyTeleportRequest event = new TeamEvent.AllyTeleportRequest(server, team.id, team.name, a.id, a.name, player, target, Teams.nameOf(server, target), where);
        MinecraftForge.EVENT_BUS.post(event);
        return event.isHandled() ? Teams.Outcome.done() : Teams.Outcome.fail("no_teleport");
    }

    // ------------------------------------------------------------------ logins, invites running out

    @SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        CHAT_ON.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks % 20 != 0) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        if (!INVITES.isEmpty()) {
            expireInvites(server, now());
        }
        if (ticks % (20 * 60) == 0) {
            AllianceLedger.get(server).dropOldWaits(now());
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
            AlliancePrompts.clear(server, i);
            tell(server, i.toLeader(), Component.translatable("message.bsp_core.alliance.invite_expired", i.allianceName()).withStyle(ChatFormatting.GRAY));
            Team t = TeamLedger.get(server).team(i.toTeam());
            tell(server, i.inviter(), Component.translatable("message.bsp_core.alliance.invite_expired_inviter", t == null ? Component.literal(i.toLeaderName()) : Teams.styled(t))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** Forgets the invites and chat switches (a test's fresh start). */
    public static void reset() {
        INVITES.clear();
        CHAT_ON.clear();
    }

    // ------------------------------------------------------------------ telling people

    static long now() {
        return System.currentTimeMillis();
    }

    private static void tell(MinecraftServer server, UUID player, Component message) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            online.displayClientMessage(message, false);
        }
    }

    /** Tells every member online here of every team in the alliance, except {@code except}. */
    public static void tellAlliance(MinecraftServer server, Alliance a, Component message, @Nullable UUID except) {
        for (UUID p : players(server, a)) {
            if (!p.equals(except)) {
                tell(server, p, message);
            }
        }
    }

    static void tellTeams(MinecraftServer server, java.util.Collection<UUID> teamIds, Component message) {
        for (UUID p : players(server, teamIds)) {
            tell(server, p, message);
        }
    }
}
