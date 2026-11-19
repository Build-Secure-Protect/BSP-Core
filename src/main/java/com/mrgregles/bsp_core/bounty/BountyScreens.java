package com.mrgregles.bsp_core.bounty;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.market.Market;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.BountyViewPacket;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Bounty Board screen's server side. A click on a board opens the screen at a page and remembers the board; every action the
 * screen sends (accept, abandon, cancel, claim the Collect box, place, look up players) is refused unless the player still stands
 * within {@link #REACH} blocks of that board. After each action the player gets the whole view again, with the answer's key.
 *
 * <p>The view is the bounties as this player may see them: every running bounty (at most {@link #MAX_ROWS}, the player's own and
 * hunted ones always in), each with whether they accepted it, whether it is theirs, why they may not accept it, and the notes only
 * when they accepted it or placed it. Also what waits in their Collect box, their pieces and coins, and the rules the place page
 * shows.
 */
public final class BountyScreens {
    private BountyScreens() {}

    public static final double REACH = 8;
    public static final int MAX_ROWS = 200;
    /** Pages of the screen. */
    public static final int PAGE_BOARD = 0, PAGE_HUNTS = 1, PAGE_MINE = 2, PAGE_PLACE = 3;
    /** Row flags. */
    public static final int ACCEPTED = 1, MINE = 2;

    private static final Map<UUID, GlobalPos> OPEN_AT = new ConcurrentHashMap<>();

    /** What a click on a board opens (the board's opener). */
    public static void open(ServerPlayer player, BountyBoardBlock.Opening at) {
        OPEN_AT.put(player.getUUID(), at.board());
        int page = switch (at.key()) {
            case PLACE -> PAGE_PLACE;
            case HUNTS -> PAGE_HUNTS;
            default -> PAGE_BOARD;
        };
        send(player, page, "", true);
    }

    /** Whether the player still stands at the board they opened. */
    public static boolean near(ServerPlayer player) {
        GlobalPos at = OPEN_AT.get(player.getUUID());
        if (at == null || !player.level().dimension().equals(at.dimension())) {
            return false;
        }
        if (!(player.level().getBlockState(at.pos()).getBlock() instanceof BountyBoardBlock)) {
            return false;
        }
        return player.position().distanceToSqr(at.pos().getCenter()) <= REACH * REACH;
    }

    public static void forget(UUID player) {
        OPEN_AT.remove(player);
        NEXT_SEARCH.remove(player);
    }

    /** GameTests: as if the player had opened the board at {@code at}. */
    public static void openedAt(UUID player, GlobalPos at) {
        OPEN_AT.put(player, at);
    }

    // ------------------------------------------------------------------ actions

    public enum Action { REFRESH, ACCEPT, ABANDON, CANCEL, COLLECT }

    /** One action from the screen; the view comes back with the answer. {@code page} is the page the screen shows. */
    public static void act(ServerPlayer player, Action action, String id, int page) {
        MinecraftServer server = player.server;
        if (!near(player)) {
            send(player, page, "far", false);
            return;
        }
        UUID me = player.getUUID();
        String name = player.getGameProfile().getName();
        switch (action) {
            case REFRESH -> send(player, page, "", false);
            case ACCEPT -> Bounties.accept(server, me, name, id, r -> send(player, page, r.key(), false));
            case ABANDON -> Bounties.abandon(server, me, id, r -> send(player, page, r.key(), false));
            case CANCEL -> Bounties.cancel(server, me, id, r -> send(player, page, r.key(), false));
            case COLLECT -> Market.collect(server, player, r -> send(player, page, r.key().equals("collect_full") ? "collect_full" : r.ok() ? "collected" : "unreachable", false));
        }
    }

    /**
     * The place page's PLACE BOUNTY: the target must be a player the bank knows by that id and name (the screen picked them from
     * {@link #players}); then {@link Bounties#place} decides. On success the screen turns to MY BOUNTIES.
     */
    public static void place(ServerPlayer player, Bounties.Ask ask) {
        MinecraftServer server = player.server;
        if (!near(player)) {
            send(player, PAGE_PLACE, "far", false);
            return;
        }
        Bounties.knownPlayers(server, ask.targetName(), list -> {
            if (player.hasDisconnected() || !near(player)) {
                return; // left, or walked off, while the network answered: nothing is taken from a player who is not here
            }
            Bounties.Known known = list.stream().filter(k -> k.id().equals(ask.target()) && k.name().equalsIgnoreCase(ask.targetName())).findFirst().orElse(null);
            if (known == null) {
                send(player, PAGE_PLACE, "unknown", false);
                return;
            }
            Bounties.Ask checked = new Bounties.Ask(known.id(), known.name(), ask.coinTier(), ask.amount(), ask.bonus(), ask.bonusWithinMs(), ask.durationMs(), ask.notes());
            Bounties.place(server, player, checked, r -> send(player, r.ok() ? PAGE_MINE : PAGE_PLACE, r.key(), false));
        });
    }

    /** A player the place page lists, and why a bounty may not go on them ("" when it may). */
    public record Pick(UUID id, String name, boolean online, String refusal) {}

    /** At most one player search a player at a time; a search asked meanwhile runs after it (only the latest). */
    private static final Map<UUID, String> SEARCHING = new ConcurrentHashMap<>(), NEXT_SEARCH = new ConcurrentHashMap<>();

    /** The place page's player list for {@code search}, online first, each with why they may not be a target. */
    public static void players(ServerPlayer player, String search) {
        MinecraftServer server = player.server;
        UUID me = player.getUUID();
        if (!near(player)) {
            return;
        }
        if (SEARCHING.containsKey(me)) {
            NEXT_SEARCH.put(me, search);
            return;
        }
        SEARCHING.put(me, search);
        Bounties.knownPlayers(server, search, list -> {
            SEARCHING.remove(me);
            if (!player.hasDisconnected()) {
                List<Pick> out = new ArrayList<>();
                for (Bounties.Known k : list) {
                    String why = Bounties.targetRefusal(server, me, k.id(), k.name());
                    out.add(new Pick(k.id(), k.name(), k.online(), why == null ? "" : why));
                }
                BSPNetwork.sendChecked(player, new com.mrgregles.bsp_core.network.BountyPlayersPacket(search, out), com.mrgregles.bsp_core.network.BountyPlayersPacket::encode,
                        "the Bounty Board player list");
            }
            String next = NEXT_SEARCH.remove(me);
            if (next != null && !player.hasDisconnected()) {
                players(player, next);
            }
        });
    }

    // ------------------------------------------------------------------ the view

    /** One bounty as this player sees it. {@code notes} is "" unless they accepted it or placed it; {@code refusal} why they may not accept ("" when they may). */
    public record Row(String id, UUID target, String targetName, String placerName, int coinTier, long amount, long bonus, long bonusEndsMs,
                      long placedMs, long endsMs, int hunters, int flags, String notes, String refusal) {
        public boolean accepted() {
            return (flags & ACCEPTED) != 0;
        }

        public boolean mine() {
            return (flags & MINE) != 0;
        }
    }

    /** What the place page needs to know: the rules and what the player has to pay with. */
    public record Rules(long pieces, int[] coins, int feePercent, long minPieces, int maxDays, int notesMax, int maxOpen, int maxAccepted, int open, int accepted,
                        long piecesPerCopper, int[] tierValues) {}

    public static List<Row> rows(MinecraftServer server, UUID viewer) {
        long now = Bounties.now();
        List<Row> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        // the viewer's own and hunted bounties first, all of them, whatever the summary's limit leaves out
        for (Bounty b : Bounties.open(server)) {
            if (!b.endedBy(now) && (b.placer.equals(viewer) || b.hasHunter(viewer))) {
                out.add(row(server, b, viewer));
                seen.add(b.id);
            }
        }
        for (BountySummary.Entry e : BountySummary.build(server)) {
            if (out.size() >= MAX_ROWS) {
                break;
            }
            if (seen.contains(e.id())) {
                continue;
            }
            Bounty b = Bounties.find(server, e.id()).orElse(null);
            if (b != null && !b.endedBy(now)) {
                out.add(row(server, b, viewer));
            }
        }
        return out;
    }

    private static Row row(MinecraftServer server, Bounty b, UUID viewer) {
        int flags = (b.hasHunter(viewer) ? ACCEPTED : 0) | (b.placer.equals(viewer) ? MINE : 0);
        String refusal = Bounties.hunterRefusal(server, b, viewer);
        return new Row(b.id, b.target, b.targetName, b.placerName, b.coinTier, b.amount, b.bonus, b.bonusEndsMs, b.placedMs, b.endsMs, b.hunters().size(),
                flags, flags != 0 ? b.notes : "", refusal == null ? "" : refusal);
    }

    public static Rules rules(MinecraftServer server, ServerPlayer player) {
        int[] coins = new int[CoinTier.values().length];
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            for (CoinTier t : CoinTier.values()) {
                if (s.is(t.coin())) {
                    coins[t.ordinal()] += s.getCount();
                }
            }
        }
        UUID me = player.getUUID();
        return new Rules(Bank.pieces(server, me), coins, com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.MARKET_TAX_PERCENT, 5),
                Bounties.minPieces(), Bounties.maxDays(), Bounties.notesMax(), Bounties.maxOpen(), Bounties.maxAccepted(),
                Bounties.placedBy(server, me).size(), Bounties.huntedBy(server, me).size(), Bounties.piecesPerCopper(), BountySummary.tierValues());
    }

    /** Sends the whole view; {@code open} opens the screen on that page, otherwise an open screen takes it. */
    public static void send(ServerPlayer player, int page, String result, boolean open) {
        MinecraftServer server = player.server;
        BountyViewPacket packet = new BountyViewPacket(Bounties.now(), page, open, result, rows(server, player.getUUID()),
                Market.store(server).peekCollect(server, player.getUUID()), rules(server, player));
        BSPNetwork.sendChecked(player, packet, BountyViewPacket::encode, "the Bounty Board view");
    }

    @Nullable
    public static GlobalPos openAt(UUID player) {
        return OPEN_AT.get(player);
    }
}
