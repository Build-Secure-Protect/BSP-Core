package com.mrgregles.bsp_core.score;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.api.event.ScoreEvent;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.ScoreSyncPacket;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import com.mrgregles.bsp_core.totem.TotemInventories;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The leaderboard. A player's score is the sum of their totems' tier points ("Weighted Points":
 * Tier I to V are worth {@code scoring.tierPoints}, 1, 2, 4, 7 and 10 by default), so both taking
 * totems and upgrading them count. Ties go to the player with more totems, then the higher tier.
 *
 * <p>A totem counts for whoever owns it while placed, and for whoever carries it while in an
 * inventory. Placed totems come from the {@link TotemLedger}, so they count while their chunk is
 * unloaded and their owner is offline; carried totems come from the players who are online (a
 * player who logs out has their carried totems placed, so nothing is lost).
 *
 * <p>On a single server the board is this server's own totems. With network storage on, each
 * server publishes its rows to the database and reads back every server's rows; the board adds up
 * the rows of the servers heard from within {@code scoring.serverTimeoutMinutes}, so a server that
 * stopped, was renamed or left the network stops counting instead of holding its totems on the
 * board for good. The board is rebuilt from the live state every {@code scoring.refreshSeconds},
 * and sooner when a totem changes, and sent to all clients for the Score Screens. A placed totem
 * in a world this server does not have counts for nobody: it cannot be reached, seen or taken.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class ScoreService {
    /** One player's line on the board. */
    public record Entry(UUID id, String name, int totems, int points, int bestTier) {
    }

    /** One server's row for one player as the network database holds it, and how many seconds ago that server last wrote it. */
    public record ServerRow(String server, Entry entry, long ageSeconds) {
    }

    /** One totem as the admin panel lists it: its owner (or carrier), tier, whether carried, and where, on which server. */
    public record Spot(UUID owner, int tier, boolean carried, String server, String dimension, net.minecraft.core.BlockPos pos) {
    }

    private static final Comparator<Entry> ORDER = Comparator.comparingInt(Entry::points).reversed()
            .thenComparing(Comparator.comparingInt(Entry::totems).reversed())
            .thenComparing(Comparator.comparingInt(Entry::bestTier).reversed())
            .thenComparing(Entry::name);
    private static final int SENT = 100;

    private static volatile List<Entry> board = List.of();
    /** The board before hand-set adjustments: what everyone's totems give. */
    private static volatile List<Entry> raw = List.of();
    private static boolean published;
    private static List<Entry> lastSent = List.of();
    private static boolean dirty = true;
    private static int ticks;
    /** Every server's rows as last read from the network (with their ages then), and when that read arrived. */
    private static volatile List<ServerRow> networkRows = List.of();
    private static volatile long networkReadMs;

    private ScoreService() {}

    /** The current board, best first. */
    public static List<Entry> board() {
        return board;
    }

    /** Points a totem of {@code tier} (0 = I) is worth. */
    public static int tierPoints(int tier) {
        return BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.SCORE_TIER_POINTS, List.<Integer>of()), tier + 1, tier + 1);
    }

    /** Asks for a rebuild on the next tick: a totem was placed, taken, raised or changed hands. */
    public static void markDirty() {
        dirty = true;
    }

    public static String name(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return server.getProfileCache() == null ? id.toString().substring(0, 8)
                : server.getProfileCache().get(id).map(com.mojang.authlib.GameProfile::getName).orElse(id.toString().substring(0, 8));
    }

    /** Every totem on this server: placed ones from the ledger (sorted by place), then those carried by players online here. */
    public static List<Spot> spots(MinecraftServer server) {
        String here = com.mrgregles.bsp_core.bank.Bank.serverId();
        List<Spot> out = new ArrayList<>();
        TotemLedger ledger = TotemLedger.get(server);
        ledger.allPlaced().forEach((owner, positions) -> {
            List<GlobalPos> sorted = new ArrayList<>();
            for (GlobalPos pos : positions) {
                if (server.getLevel(pos.dimension()) != null) { // a world this server does not have (a mod taken out): nobody holds that totem
                    sorted.add(pos);
                }
            }
            sorted.sort(java.util.Comparator.comparing((GlobalPos p) -> p.dimension().location().toString()).thenComparing(GlobalPos::pos));
            for (GlobalPos pos : sorted) {
                out.add(new Spot(owner, ledger.tierAt(pos), false, here, pos.dimension().location().toString(), pos.pos()));
            }
        });
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (TotemInventories.isTotem(stack)) {
                    out.add(new Spot(player.getUUID(), TotemUpgrades.getTier(stack), true, here, player.level().dimension().location().toString(), player.blockPosition()));
                }
            }
        }
        return out;
    }

    /** This server's own rows (placed totems from the ledger plus totems carried by online players), with the scores set by hand. */
    public static List<Entry> local(MinecraftServer server) {
        return adjusted(server, entries(server, spots(server)));
    }

    /**
     * What a player's totems give, before any score set by hand: on a network the whole network's as last read (with this
     * server's own counted live), else this server's.
     */
    public static int totemPoints(MinecraftServer server, UUID player) {
        int points = 0;
        for (Entry e : entries(server, spots(server))) {
            if (e.id().equals(player)) {
                points = e.points();
            }
        }
        if (!NetworkStorage.enabled()) {
            return points;
        }
        for (Entry e : raw) {
            if (e.id().equals(player)) {
                return e.points() - mineAtRead.getOrDefault(player, 0) + points;
            }
        }
        return points;
    }

    /** This server's own points per player in the rows the last network read was built from. */
    private static volatile Map<UUID, Integer> mineAtRead = Map.of();

    /** The board with the scores set by hand: each adjustment added (never below 0), and a player with only an adjustment listed. */
    static List<Entry> adjusted(MinecraftServer server, List<Entry> entries) {
        Map<UUID, Integer> adjust = ScoreAdjust.all(server);
        if (adjust.isEmpty()) {
            return entries;
        }
        Map<UUID, Integer> left = new HashMap<>(adjust);
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            int add = left.getOrDefault(e.id(), 0);
            left.remove(e.id());
            out.add(add == 0 ? e : new Entry(e.id(), e.name(), e.totems(), Math.max(0, e.points() + add), e.bestTier()));
        }
        left.forEach((id, add) -> {
            if (add > 0) {
                out.add(new Entry(id, name(server, id), 0, add, -1));
            }
        });
        out.sort(ORDER);
        return out;
    }

    /**
     * How long a server may go without refreshing its rows before they stop counting, in seconds: {@code scoring.serverTimeoutMinutes},
     * but never less than three refreshes ({@code scoring.refreshSeconds}), so a slow refresh or a lagging server does not make its
     * rows flicker in and out of the board.
     */
    public static long serverTimeoutSeconds() {
        long timeout = 60L * Math.max(2, BSPConfig.getOr(BSPConfig.SCORE_SERVER_TIMEOUT_MINUTES, 30));
        long refresh = Math.max(5, BSPConfig.getOr(BSPConfig.SCORE_REFRESH_SECONDS, 30));
        return Math.max(timeout, 3 * refresh);
    }

    /** Whether a row {@code server} wrote {@code ageSeconds} ago counts: this server's own always do, another's while it is heard from. */
    public static boolean counts(String server, String here, long ageSeconds, long maxAgeSeconds) {
        return server.equals(here) || ageSeconds <= maxAgeSeconds;
    }

    /**
     * The network's board from every server's rows: each player's totems and points added up over the rows that {@link #counts}
     * (the best tier is the highest of them), best first. A player whose only rows are from silent servers is not on it.
     */
    public static List<Entry> combine(List<ServerRow> rows, String here, long maxAgeSeconds) {
        Map<UUID, int[]> acc = new HashMap<>(); // totems, points, best tier
        Map<UUID, String> names = new HashMap<>();
        Map<UUID, Long> nameAge = new HashMap<>();
        for (ServerRow r : rows) {
            if (!counts(r.server(), here, r.ageSeconds(), maxAgeSeconds)) {
                continue;
            }
            Entry e = r.entry();
            int[] v = acc.computeIfAbsent(e.id(), k -> new int[]{0, 0, -1});
            v[0] += e.totems();
            v[1] += e.points();
            v[2] = Math.max(v[2], e.bestTier());
            if (!e.name().isEmpty() && r.ageSeconds() < nameAge.getOrDefault(e.id(), Long.MAX_VALUE)) {
                names.put(e.id(), e.name()); // the freshest row's name
                nameAge.put(e.id(), r.ageSeconds());
            }
        }
        List<Entry> out = new ArrayList<>();
        acc.forEach((id, v) -> out.add(new Entry(id, names.getOrDefault(id, id.toString().substring(0, 8)), v[0], v[1], v[2])));
        out.sort(ORDER);
        return out;
    }

    /**
     * Why a player has the score they have, for {@code /bsp score why}: the board's line, the totems this server counts for them
     * (placed, with where, and carried), on a network every server's row with its age and whether it counts, and the score set by
     * hand riding on top.
     */
    public static List<String> explain(MinecraftServer server, UUID player) {
        List<String> out = new ArrayList<>();
        String here = com.mrgregles.bsp_core.bank.Bank.serverId();
        Entry line = null;
        for (Entry e : board) {
            if (e.id().equals(player)) {
                line = e;
            }
        }
        out.add(line == null ? name(server, player) + " is not on the board (0 points)."
                : name(server, player) + ": " + line.points() + " points on the board, " + line.totems() + " totem" + (line.totems() == 1 ? "" : "s") + ".");
        int placed = 0, carried = 0, points = 0;
        List<String> where = new ArrayList<>();
        for (Spot s : spots(server)) {
            if (s.owner().equals(player)) {
                points += tierPoints(s.tier());
                if (s.carried()) {
                    carried++;
                } else {
                    placed++;
                    if (where.size() < 8) {
                        where.add("Tier " + TotemUpgrades.roman(s.tier()) + " at " + s.pos().getX() + " " + s.pos().getY() + " " + s.pos().getZ() + " in " + s.dimension());
                    }
                }
            }
        }
        out.add("Here (" + here + "): " + placed + " placed, " + carried + " carried, " + points + " points.");
        where.forEach(w -> out.add("  " + w));
        if (placed > where.size()) {
            out.add("  and " + (placed - where.size()) + " more");
        }
        int missingWorld = 0;
        for (GlobalPos pos : TotemLedger.get(server).placedFor(player)) {
            if (server.getLevel(pos.dimension()) == null) {
                missingWorld++;
            }
        }
        if (missingWorld > 0) {
            out.add("  " + missingWorld + " more listed in worlds this server does not have: not counted.");
        }
        if (NetworkStorage.enabled()) {
            long timeout = serverTimeoutSeconds();
            long since = networkReadMs == 0 ? -1 : (System.currentTimeMillis() - networkReadMs) / 1000;
            out.add(since < 0 ? "The network has not been read since this server started." : "Network rows as read " + since + " s ago:");
            boolean any = false;
            for (ServerRow r : networkRows) {
                if (r.entry().id().equals(player)) {
                    any = true;
                    boolean counted = counts(r.server(), here, r.ageSeconds(), timeout);
                    out.add("  " + r.server() + ": " + r.entry().totems() + " totems, " + r.entry().points() + " points, written " + r.ageSeconds() + " s before the read"
                            + (counted ? "" : " (not counted: silent for more than " + timeout / 60 + " min)"));
                }
            }
            if (!any && since >= 0) {
                out.add("  no server lists them.");
            }
        }
        int add = ScoreAdjust.of(server, player);
        out.add(add == 0 ? "No score set by hand." : "Set by hand: " + (add > 0 ? "+" : "") + add + " on top of the totems (/bsp score clear removes it).");
        return out;
    }

    private static List<Entry> entries(MinecraftServer server, List<Spot> spots) {
        Map<UUID, int[]> acc = new HashMap<>(); // totems, points, best tier
        for (Spot s : spots) {
            add(acc, s.owner(), s.tier());
        }
        List<Entry> out = new ArrayList<>();
        acc.forEach((id, v) -> out.add(new Entry(id, name(server, id), v[0], v[1], v[2])));
        out.sort(ORDER);
        return out;
    }

    private static void add(Map<UUID, int[]> acc, UUID id, int tier) {
        int[] v = acc.computeIfAbsent(id, k -> new int[]{0, 0, -1});
        v[0]++;
        v[1] += tierPoints(tier);
        v[2] = Math.max(v[2], tier);
    }

    private static void publish(MinecraftServer server, List<Entry> entries) {
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(ORDER);
        List<Entry> before = board;
        board = List.copyOf(sorted);
        if (published) {
            postChanges(server, before, board);
        }
        published = true;
        List<Entry> top = List.copyOf(sorted.subList(0, Math.min(SENT, sorted.size())));
        if (top.equals(lastSent)) {
            return; // the screens already show this board (a newcomer is sent it at login)
        }
        lastSent = top;
        ScoreSyncPacket packet = new ScoreSyncPacket(top);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            BSPNetwork.sendTo(player, packet);
        }
    }

    /** Posts a {@link ScoreEvent.Changed} for every player whose points differ between two boards. */
    private static void postChanges(MinecraftServer server, List<Entry> before, List<Entry> after) {
        Map<UUID, Entry> old = new HashMap<>();
        for (Entry e : before) {
            old.put(e.id(), e);
        }
        for (Entry e : after) {
            Entry was = old.remove(e.id());
            int previous = was == null ? 0 : was.points();
            if (previous != e.points()) {
                MinecraftForge.EVENT_BUS.post(new ScoreEvent.Changed(server, e.id(), e.name(), e.points(), e.points() - previous, e.totems(), e.bestTier() + 1));
            }
        }
        for (Entry gone : old.values()) {
            if (gone.points() != 0) {
                MinecraftForge.EVENT_BUS.post(new ScoreEvent.Changed(server, gone.id(), gone.name(), 0, -gone.points(), 0, 0));
            }
        }
    }

    /** Rebuilds the board now and sends it to everyone. */
    public static void refresh(MinecraftServer server) {
        dirty = false;
        ticks = 0;
        List<Spot> spots = spots(server);
        List<Entry> mine = entries(server, spots);
        if (NetworkStorage.enabled()) {
            Map<UUID, Integer> sent = new HashMap<>();
            for (Entry e : mine) {
                sent.put(e.id(), e.points());
            }
            // the rows and every totem behind them, for the board and for the admin panel on every server
            String here = com.mrgregles.bsp_core.bank.Bank.serverId();
            NetworkStorage.syncScores(mine, spots, (rows, adjust) -> { // answer arrives on the server thread
                if (adjust != null) {
                    ScoreAdjust.networkRead(adjust);
                }
                networkRows = List.copyOf(rows);
                networkReadMs = System.currentTimeMillis();
                List<Entry> combined = combine(rows, here, serverTimeoutSeconds());
                raw = List.copyOf(combined);
                mineAtRead = Map.copyOf(sent);
                publish(server, adjusted(server, combined));
            });
        } else {
            raw = List.copyOf(mine);
            publish(server, adjusted(server, mine));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ticks++;
        int every = Math.max(5, BSPConfig.getOr(BSPConfig.SCORE_REFRESH_SECONDS, 30)) * 20;
        // a change is picked up within a second on a single server; a network waits for its timer so the database is not hammered
        if (ticks >= every || (dirty && ticks >= 20 && !NetworkStorage.enabled())) {
            refresh(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            List<Entry> now = board;
            BSPNetwork.sendTo(player, new ScoreSyncPacket(now.subList(0, Math.min(SENT, now.size()))));
            markDirty();
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        markDirty();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        board = List.of();
        raw = List.of();
        networkRows = List.of();
        networkReadMs = 0;
        mineAtRead = Map.of();
        ScoreAdjust.reset();
        lastSent = List.of();
        published = false;
        dirty = true;
        ticks = 0;
    }
}
