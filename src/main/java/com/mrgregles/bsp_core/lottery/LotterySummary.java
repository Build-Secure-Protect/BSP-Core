package com.mrgregles.bsp_core.lottery;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.market.Market;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.LotterySummaryPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What every client knows of the lottery, for the Lottery board on Score Screens and the card on the Portable Banker: the
 * pot, the names of everyone in (for the board's drum, at most {@link #NAMES}), how many are in, the last {@link #WINS}
 * winners, the draw as it is on this server, and the player's own standing ({@code standing} a {@link Lottery.Standing}
 * ordinal, -1 not known; {@code rank} the rank leaving them out). Checked once a second and sent to each player whose summary
 * changed, and at login; who is in and the winners come from the LOTTERY page's shared copy ({@link LotteryPage}), read at
 * most every five seconds.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class LotterySummary {
    private LotterySummary() {}

    public static final int NAMES = 60, WINS = 3;

    public record Summary(long pot, List<String> names, int in, List<LotteryPageView.Win> wins, int phase, long phaseMs, String winner, long winPieces,
                          int standing, String rank) {
        public static final Summary EMPTY = new Summary(0, List.of(), 0, List.of(), 0, 0, "", 0, -1, "");

        /** What decides whether the client needs it again (not the phase's age, which changes every second). */
        String key() {
            return pot + "|" + in + "|" + String.join(",", names) + "|" + wins + "|" + phase + "|" + winner + "|" + winPieces + "|" + standing + "|" + rank;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeLong(pot);
            buf.writeVarInt(Math.min(NAMES, names.size()));
            names.stream().limit(NAMES).forEach(n -> buf.writeUtf(BSPNetwork.cut(n, 16), 16));
            buf.writeVarInt(in);
            buf.writeVarInt(Math.min(WINS, wins.size()));
            wins.stream().limit(WINS).forEach(w -> {
                buf.writeUtf(BSPNetwork.cut(w.name(), 16), 16);
                buf.writeLong(w.pieces());
                buf.writeLong(w.ms());
            });
            buf.writeVarInt(phase);
            buf.writeLong(phaseMs);
            buf.writeUtf(BSPNetwork.cut(winner, 16), 16);
            buf.writeLong(winPieces);
            buf.writeVarInt(standing + 1);
            buf.writeUtf(BSPNetwork.cut(rank, 32), 32);
        }

        public static Summary decode(FriendlyByteBuf buf) {
            long pot = buf.readLong();
            int n = Math.min(NAMES, buf.readVarInt());
            List<String> names = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                names.add(buf.readUtf(16));
            }
            int in = buf.readVarInt();
            n = Math.min(WINS, buf.readVarInt());
            List<LotteryPageView.Win> wins = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                wins.add(new LotteryPageView.Win(buf.readUtf(16), buf.readLong(), buf.readLong()));
            }
            return new Summary(pot, names, in, wins, buf.readVarInt(), buf.readLong(), buf.readUtf(16), buf.readLong(), buf.readVarInt() - 1, buf.readUtf(32));
        }
    }

    /** The key of the summary last sent to each player online. */
    private static final Map<UUID, String> sent = new HashMap<>();
    private static int ticks;

    /** {@code player}'s summary from the shared copy (read again when it is old); null for nobody's standing. */
    public static void build(MinecraftServer server, @Nullable UUID player, java.util.function.Consumer<Summary> then) {
        LotteryPage.withCopy(server, c -> then.accept(base(server, c).forPlayer(player)));
    }

    /** Everything but the player's own standing, worked out once for everyone. */
    private record Base(long pot, List<String> names, int in, List<LotteryPageView.Win> wins, int phase, long phaseMs, String winner, long winPieces,
                        Map<UUID, Lottery.Entry> byId) {
        Summary forPlayer(@Nullable UUID player) {
            Lottery.Entry e = player == null ? null : byId.get(player);
            return new Summary(pot, names, in, wins, phase, phaseMs, winner, winPieces, e == null ? -1 : e.standing().ordinal(), e == null ? "" : e.rank());
        }
    }

    private static Base base(MinecraftServer server, LotteryPage.Copy c) {
        List<String> names = new ArrayList<>();
        Map<UUID, Lottery.Entry> byId = new HashMap<>();
        for (Lottery.Entry e : c.entries()) {
            byId.put(e.player().id(), e);
            if (e.in()) {
                names.add(e.player().name());
            }
        }
        int in = names.size();
        names.sort(String.CASE_INSENSITIVE_ORDER);
        List<LotteryPageView.Win> wins = new ArrayList<>();
        for (LotteryWin w : c.wins()) {
            if (wins.size() < WINS) {
                wins.add(new LotteryPageView.Win(w.name(), w.pieces(), w.ms()));
            }
        }
        long now = System.currentTimeMillis();
        LotteryDraw.PageState d = LotteryDraw.pageState(now);
        return new Base(Market.pot(server), List.copyOf(LotteryPage.withWinner(names, d, NAMES)), in, wins,
                d.phase().ordinal(), d.sinceMs() == 0 ? 0 : now - d.sinceMs(), d.winner(), d.pieces(), byId);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks < 20) {
            return;
        }
        ticks = 0;
        MinecraftServer server = event.getServer();
        if (server.getPlayerList().getPlayerCount() == 0) {
            return;
        }
        LotteryPage.withCopy(server, c -> {
            Base base = base(server, c);
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                Summary s = base.forPlayer(p.getUUID());
                String key = s.key();
                if (!key.equals(sent.get(p.getUUID()))) {
                    sent.put(p.getUUID(), key);
                    BSPNetwork.sendTo(p, new LotterySummaryPacket(s));
                }
            }
        });
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            build(player.server, player.getUUID(), s -> {
                if (!player.hasDisconnected()) {
                    sent.put(player.getUUID(), s.key());
                    BSPNetwork.sendChecked(player, new LotterySummaryPacket(s), LotterySummaryPacket::encode, "the lottery summary");
                }
            });
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        sent.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        sent.clear();
        ticks = 0;
    }
}
