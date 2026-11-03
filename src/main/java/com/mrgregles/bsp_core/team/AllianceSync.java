package com.mrgregles.bsp_core.team;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.api.event.TeamEvent;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Alliances across the network: every change here is written to the shared tables and announced over Redis (kind
 * {@code alliance}); a change elsewhere comes back as a fresh copy of the alliance, which replaces the local one, tells the
 * players here the lines they have not seen, and resyncs the totems of everyone concerned (their perks may have changed).
 * Seating a team is the database's alone (a transaction that counts the places and checks the waits), so two servers can
 * never fill one alliance past its size. Without network storage none of this runs and the local ledger is the record.
 */
public final class AllianceSync {
    private AllianceSync() {}

    /** Alliances changed here a moment ago: a full read that started before the change must not drop or undo them. */
    private static final Map<UUID, Long> TOUCHED = new HashMap<>();
    private static final long TOUCH_MS = 30_000L;
    /**
     * Reads this server queued right after its own writes and still to come back, per alliance. While one is to come, a read made
     * for another reason (another server's notice) may predate the write and is not applied: the own read follows it and shows both.
     */
    private static final Map<UUID, Integer> OWN_READS = new HashMap<>();
    /** Teams thrown out by a break lately (here or heard from another server): their leaving is not announced twice. */
    private static final Map<UUID, Long> BROKE = new HashMap<>();

    private static void touch(UUID id) {
        long now = System.currentTimeMillis();
        TOUCHED.values().removeIf(at -> now - at >= TOUCH_MS);
        TOUCHED.put(id, now);
    }

    private static boolean touched(UUID id) {
        Long at = TOUCHED.get(id);
        return at != null && System.currentTimeMillis() - at < TOUCH_MS;
    }

    /** After a write to the alliance is queued: a read of it is queued behind it, and stale reads wait for that one. */
    private static void wrote(UUID id) {
        touch(id);
        if (NetworkStorage.enabled()) {
            expectOwnRead(id);
            NetworkStorage.allianceReload(id, true);
        }
    }

    /** Counts a read this server will make of its own write (public for tests). */
    public static void expectOwnRead(UUID id) {
        OWN_READS.merge(id, 1, Integer::sum);
    }

    private static void ownReadDone(@Nullable UUID id) {
        if (id != null) {
            OWN_READS.computeIfPresent(id, (k, n) -> n <= 1 ? null : n - 1);
        }
    }

    /** The own read could not be made: stale reads are no longer held back for it. */
    public static void ownReadFailed(@Nullable UUID id) {
        ownReadDone(id);
    }

    /** A break threw the team out: the copies read later do not announce its leaving again. */
    public static void broke(UUID teamId) {
        long now = System.currentTimeMillis();
        BROKE.values().removeIf(at -> now - at > 120_000L);
        BROKE.put(teamId, now);
    }

    private static boolean brokeLately(UUID teamId) {
        Long at = BROKE.get(teamId);
        return at != null && System.currentTimeMillis() - at <= 120_000L;
    }

    static void created(MinecraftServer server, Alliance a, UUID team) {
        Alliance.Seat seat = a.teams().get(team);
        NetworkStorage.allianceJoin(a, a.id, team, seat, Alliances.maxTeams(), seated -> refused(server, a.id, team, seated));
        wrote(a.id);
    }

    static void joined(MinecraftServer server, Alliance a, UUID team) {
        Alliance.Seat seat = a.teams().get(team);
        NetworkStorage.allianceJoin(null, a.id, team, seat, Alliances.maxTeams(), seated -> refused(server, a.id, team, seated));
        wrote(a.id);
    }

    /** The database's answer to a seat: refused, the team comes out again here and is told; unreachable, the own read decides. */
    private static void refused(MinecraftServer server, UUID allianceId, UUID teamId, @Nullable Boolean seated) {
        if (seated == null || seated) {
            return;
        }
        AllianceLedger ledger = AllianceLedger.get(server);
        Alliance now = ledger.alliance(allianceId);
        Team team = TeamLedger.get(server).team(teamId);
        Set<UUID> concerned = new HashSet<>();
        if (now == null || !now.has(teamId)) {
            return; // out already (the own read came first)
        }
        concerned.addAll(Alliances.players(server, now));
        now.remove(teamId);
        ledger.left(teamId);
        if (now.size() == 0) {
            ledger.remove(now);
            NetworkStorage.allianceDelete(allianceId); // a new alliance the database would not seat its maker in: no empty row is left
        }
        ledger.changed();
        if (team != null) {
            for (UUID p : team.members().keySet()) {
                Alliances.chatOff(server, p);
            }
            Teams.tellTeam(server, team, Component.translatable("message.bsp_core.alliance.join_refused").withStyle(ChatFormatting.RED), null);
            MinecraftForge.EVENT_BUS.post(new TeamEvent.AllianceLeft(server, team.id, team.name, allianceId, now.name, now.size() < 2));
        }
        TeamAccessSync.resyncOwners(server, concerned);
    }

    static void left(MinecraftServer server, Alliance a, UUID team, boolean ends) {
        NetworkStorage.allianceLeave(a.id, team);
        wrote(a.id);
    }

    static void removed(Alliance a) {
        NetworkStorage.allianceDelete(a.id);
        wrote(a.id);
    }

    static void seat(Alliance a, UUID team, Alliance.Seat seat) {
        NetworkStorage.allianceSeat(a.id, team, seat);
        wrote(a.id);
    }

    static void waits(List<AllianceLedger.Wait> waits) {
        NetworkStorage.allianceWaits(waits);
    }

    /** Left-alliance records made or used up here lately: a read that predates them does not undo them. */
    private static final List<AllianceLedger.Former> FORMERS_ADDED = new ArrayList<>();
    private static final Map<UUID, Long> FORMERS_SPENT = new HashMap<>();
    private static final Map<AllianceLedger.Former, Long> FORMERS_ADDED_AT = new HashMap<>();

    static void formers(List<AllianceLedger.Former> added) {
        long now = System.currentTimeMillis();
        for (AllianceLedger.Former f : added) {
            FORMERS_ADDED.add(f);
            FORMERS_ADDED_AT.put(f, now);
        }
        NetworkStorage.allianceFormers(added);
    }

    static void formersSpent(UUID team) {
        FORMERS_SPENT.put(team, System.currentTimeMillis());
        NetworkStorage.allianceFormerSpend(team);
    }

    /** The left-alliance records read from the database (the server thread), with the changes made here in the last half minute kept. */
    public static void formersRead(MinecraftServer server, List<AllianceLedger.Former> fresh) {
        long now = System.currentTimeMillis();
        FORMERS_SPENT.values().removeIf(at -> now - at >= TOUCH_MS);
        FORMERS_ADDED.removeIf(f -> now - FORMERS_ADDED_AT.getOrDefault(f, 0L) >= TOUCH_MS);
        FORMERS_ADDED_AT.keySet().retainAll(FORMERS_ADDED);
        AllianceLedger.get(server).replaceFormers(fresh, FORMERS_ADDED, FORMERS_SPENT.keySet(), now);
    }

    static void posted(Alliance a, Alliance.Message m) {
        NetworkStorage.allianceMessage(a.id, m, BSPConfig.getOr(BSPConfig.TEAM_HISTORY_SIZE, 50));
        wrote(a.id);
    }

    static void read(Alliance a, UUID player) {
        NetworkStorage.allianceRead(a.id, player, a.readSeq(player));
    }

    /** As {@link #apply(MinecraftServer, List, UUID, List, boolean, boolean)} for a read made for another reason, pruning a full read. */
    public static void apply(MinecraftServer server, List<Alliance> fresh, @Nullable UUID only, @Nullable List<AllianceLedger.Wait> waits) {
        apply(server, fresh, only, waits, false, true);
    }

    /**
     * Applies alliances read from the database (the server thread). {@code only} names the one alliance asked for; an empty list
     * for it means it ended elsewhere. {@code own} for this server's read of its own write. A read made for another reason while an
     * own read of that alliance is still to come is skipped (it may predate the write). A full read ({@code only} null) skips the
     * alliances changed here lately and, with {@code prune}, drops the ones the database no longer has. The waits are merged in.
     * An alliance with no team (a maker's seat that never went in) counts as gone.
     */
    public static void apply(MinecraftServer server, List<Alliance> fresh, @Nullable UUID only, @Nullable List<AllianceLedger.Wait> waits, boolean own, boolean prune) {
        AllianceLedger ledger = AllianceLedger.get(server);
        if (waits != null) {
            ledger.mergeWaits(waits);
        }
        if (own) {
            ownReadDone(only);
        } else if (only != null && OWN_READS.containsKey(only)) {
            return;
        }
        List<Alliance> live = new ArrayList<>();
        for (Alliance a : fresh) {
            if (a.size() > 0) {
                live.add(a);
            }
        }
        if (only == null && prune) {
            Set<UUID> keep = new HashSet<>();
            live.forEach(a -> keep.add(a.id));
            for (Alliance a : new ArrayList<>(ledger.all())) {
                if (!keep.contains(a.id) && !touched(a.id) && !OWN_READS.containsKey(a.id)) {
                    gone(server, a);
                }
            }
        }
        if (only != null && live.isEmpty()) {
            Alliance a = ledger.alliance(only);
            if (a != null) {
                gone(server, a);
            }
            return;
        }
        for (Alliance a : live) {
            if (only == null && (touched(a.id) || OWN_READS.containsKey(a.id))) {
                continue; // a routine read may be older than the change made here
            }
            Alliance old = ledger.alliance(a.id);
            Set<UUID> concerned = Alliances.players(server, a);
            // a team in this copy that the local copy still has in another alliance left that one (its notice was missed)
            for (UUID t : a.teams().keySet()) {
                Alliance elsewhere = ledger.allianceOf(t);
                if (elsewhere != null && !elsewhere.id.equals(a.id)) {
                    concerned.addAll(Alliances.players(server, elsewhere));
                }
            }
            ledger.replace(a);
            if (old != null) {
                concerned.addAll(Alliances.players(server, old));
                // new lines: those the local copy does not hold (the database may have moved a line to the next free number)
                Set<String> seen = new HashSet<>();
                for (Alliance.Message m : old.messages()) {
                    seen.add(lineKey(m));
                }
                for (Alliance.Message m : a.messages()) {
                    if (!seen.contains(lineKey(m)) && m.seq() > old.messageSeq() - old.messages().size()) {
                        Alliances.tellAlliance(server, a, Alliances.chatLine(m), null);
                    }
                }
                for (UUID t : a.teams().keySet()) {
                    Team team = TeamLedger.get(server).team(t);
                    if (!old.has(t) && team != null) {
                        Alliances.tellTeams(server, a.teams().keySet(), Component.translatable("message.bsp_core.alliance.team_joined", Teams.styled(team), Alliances.styled(a))
                                .withStyle(ChatFormatting.GREEN));
                    }
                }
                for (UUID t : old.teams().keySet()) {
                    if (!a.has(t)) {
                        for (UUID p : Alliances.players(server, List.of(t))) {
                            Alliances.chatOff(server, p);
                        }
                        if (brokeLately(t)) {
                            continue; // the break's own lines said it
                        }
                        Team team = TeamLedger.get(server).team(t);
                        Component name = team == null ? Component.literal("?") : Teams.styled(team);
                        Alliances.tellTeams(server, List.of(t), Component.translatable("message.bsp_core.alliance.we_left", Alliances.styled(a)).withStyle(ChatFormatting.YELLOW));
                        Alliances.tellTeams(server, a.teams().keySet(), Component.translatable("message.bsp_core.alliance.team_left", name, Alliances.styled(a)).withStyle(ChatFormatting.YELLOW));
                    }
                }
            }
            TeamAccessSync.resyncOwners(server, concerned);
        }
    }

    private static String lineKey(Alliance.Message m) {
        return m.author() + "|" + m.ms() + "|" + m.text();
    }

    /** The alliance ended on another server: it goes here too, its players are told and their totems resynced. */
    private static void gone(MinecraftServer server, Alliance a) {
        AllianceLedger ledger = AllianceLedger.get(server);
        Set<UUID> players = Alliances.players(server, a);
        ledger.remove(a);
        for (UUID p : players) {
            Alliances.chatOff(server, p);
            ServerPlayer online = server.getPlayerList().getPlayer(p);
            if (online != null) {
                online.displayClientMessage(Component.translatable("message.bsp_core.alliance.ended", Alliances.styled(a)).withStyle(ChatFormatting.YELLOW), false);
            }
        }
        TeamAccessSync.resyncOwners(server, players);
    }
}
