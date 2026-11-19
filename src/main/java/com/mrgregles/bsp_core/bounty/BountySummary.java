package com.mrgregles.bsp_core.bounty;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.BountySummaryPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * What every client knows of the bounties, for the Bounty board on Score Screens and the Bounty Board's poster screens: every
 * running bounty (at most {@link #CAP}), biggest first by worth, with no notes (those only go to hunters). Checked once a second
 * from {@link BountyEvents} and sent to everyone when it changed, and to each player at login.
 */
public final class BountySummary {
    public static final int CAP = 200;

    /**
     * One running bounty as a client sees it. {@code coinTier} {@link Bounty#PIECES} or a coin tier's ordinal; {@code bonusEndsMs}
     * 0 with no bonus; {@code hunters} how many accepted it.
     */
    public record Entry(String id, UUID target, String targetName, String placerName, int coinTier, long amount, long bonus, long bonusEndsMs,
                        long placedMs, long endsMs, int hunters) {
        /** Worth in pieces, to sort and add up by (a coin counts at its bank conversion value). */
        public long worth(long piecesPerCopper, int[] tierValues) {
            long each = coinTier == Bounty.PIECES ? 1 : piecesPerCopper * tierValues[Math.max(0, Math.min(tierValues.length - 1, coinTier))];
            return amount * each;
        }
    }

    /** What was last sent; null until the first send. */
    @Nullable
    private static String sent;

    private BountySummary() {}

    static int[] tierValues() {
        com.mrgregles.bsp_core.coin.CoinTier[] t = com.mrgregles.bsp_core.coin.CoinTier.values();
        int[] v = new int[t.length];
        for (int i = 0; i < t.length; i++) {
            v[i] = t[i].value();
        }
        return v;
    }

    /** The bounties still running, worth first (then soonest to end), at most {@link #CAP}. */
    public static List<Entry> build(MinecraftServer server) {
        long now = Bounties.now();
        long ppc = Bounties.piecesPerCopper();
        int[] tv = tierValues();
        List<Entry> out = new ArrayList<>();
        for (Bounty b : Bounties.open(server)) {
            if (!b.endedBy(now)) {
                out.add(new Entry(b.id, b.target, b.targetName, b.placerName, b.coinTier, b.amount, b.bonus, b.bonusEndsMs, b.placedMs, b.endsMs, b.hunters().size()));
            }
        }
        out.sort(Comparator.comparingLong((Entry e) -> -e.worth(ppc, tv)).thenComparingLong(Entry::endsMs).thenComparing(Entry::id));
        return out.size() > CAP ? List.copyOf(out.subList(0, CAP)) : out;
    }

    private static String key(List<Entry> list) {
        StringBuilder b = new StringBuilder();
        for (Entry e : list) {
            b.append(e.id()).append(':').append(e.hunters()).append(':').append(e.endsMs()).append(',');
        }
        return b.toString();
    }

    /** Sends the summary to every player when it changed since the last send; true if it sent. */
    public static boolean tick(MinecraftServer server) {
        List<Entry> list = build(server);
        String now = key(list);
        if (now.equals(sent)) {
            return false;
        }
        sent = now;
        BountySummaryPacket packet = new BountySummaryPacket(Bounties.now(), list);
        if (BSPNetwork.sizeOf(packet, BountySummaryPacket::encode) >= BSPNetwork.MAX_PAYLOAD) {
            BSPCore.LOGGER.error("BSP-Core bounty: the bounty summary is too big to send; not sent");
            return true;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            BSPNetwork.sendTo(p, packet);
        }
        return true;
    }

    public static void sendTo(ServerPlayer player) {
        BSPNetwork.sendChecked(player, new BountySummaryPacket(Bounties.now(), build(player.server)), BountySummaryPacket::encode, "the bounty summary");
    }

    /** The server stopped: the next one starts by sending. */
    public static void forget() {
        sent = null;
    }
}
