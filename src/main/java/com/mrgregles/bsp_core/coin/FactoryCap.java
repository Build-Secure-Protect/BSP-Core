package com.mrgregles.bsp_core.coin;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.data.FactoryLedger;
import com.mrgregles.bsp_core.team.Team;
import com.mrgregles.bsp_core.team.Teams;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * How many Shatter Coin Factory slices a player may own: {@code factory.maxPerTeamSize} by the size
 * of their team now (counted across the network, current members only), never above
 * {@code factory.maxPerPlayer}. Slices over it are not taken away: the newest ones pause until the
 * owner is under it again.
 */
public final class FactoryCap {
    private static final List<Integer> DEFAULT = List.of(10, 8, 7, 6, 5);

    private FactoryCap() {}

    /** The player's team size: 1 in no team (or with teams switched off). */
    public static int teamSize(MinecraftServer server, UUID player) {
        Team team = Teams.enabled() ? Teams.teamOf(server, player) : null;
        return team == null ? 1 : Math.max(1, team.members().size());
    }

    /** The slices each member of a team of {@code size} may own. */
    public static int capFor(int size) {
        int perSize = BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.FACTORY_MAX_PER_TEAM_SIZE, DEFAULT), Math.max(1, size), (Integer) 10);
        return Math.max(0, Math.min(perSize, BSPConfig.getOr(BSPConfig.FACTORY_MAX_PER_PLAYER, 10)));
    }

    /** The slices this player may own now. */
    public static int cap(MinecraftServer server, UUID player) {
        return capFor(teamSize(server, player));
    }

    /**
     * Since when the player's team has had its current size: the newest member's join time (0 in no
     * team). A slice that was unloaded when the team grew pauses from then, not from when it loads.
     */
    public static long sizeSince(MinecraftServer server, UUID player) {
        Team team = Teams.enabled() ? Teams.teamOf(server, player) : null;
        if (team == null) {
            return 0;
        }
        long newest = 0;
        for (Team.Member m : team.members().values()) {
            newest = Math.max(newest, m.joinedMs);
        }
        return newest;
    }

    /**
     * When the slice at {@code pos} went over its owner's limit, as far as the team's current members tell: the join time of the
     * member whose joining brought the team to a size whose limit the slice's place is beyond. 0 when it never was.
     */
    public static long overSince(MinecraftServer server, UUID owner, GlobalPos pos) {
        FactoryLedger ledger = FactoryLedger.get(server);
        long placed = ledger.placedAt(pos);
        Team team = Teams.enabled() ? Teams.teamOf(server, owner) : null;
        if (placed < 0) {
            return 0;
        }
        int rank = Collections.binarySearch(ledger.ranked(owner), FactoryLedger.Slice.of(placed, com.mrgregles.bsp_core.bank.Bank.serverId(), pos));
        if (team == null) {
            return 0; // alone: over only by a lowered setting, so from its last update
        }
        long[] joined = team.members().values().stream().mapToLong(m -> m.joinedMs).sorted().toArray();
        for (int size = 1; size <= joined.length; size++) {
            if (rank >= capFor(size)) {
                return joined[size - 1];
            }
        }
        return sizeSince(server, owner);
    }

    /** Whether the slice at {@code pos}, owned by {@code owner}, is one of the owner's newest slices beyond their cap. */
    public static boolean overCap(MinecraftServer server, UUID owner, GlobalPos pos) {
        FactoryLedger ledger = FactoryLedger.get(server);
        int cap = cap(server, owner);
        if (ledger.count(owner) <= cap) {
            return false;
        }
        long placed = ledger.placedAt(pos);
        if (placed < 0) {
            return false;
        }
        int rank = Collections.binarySearch(ledger.ranked(owner), FactoryLedger.Slice.of(placed, com.mrgregles.bsp_core.bank.Bank.serverId(), pos));
        return rank >= cap;
    }
}
