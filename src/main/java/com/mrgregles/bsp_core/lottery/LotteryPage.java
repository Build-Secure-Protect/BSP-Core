package com.mrgregles.bsp_core.lottery;

import com.mrgregles.bsp_core.market.Market;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.LotteryPagePacket;
import com.mrgregles.bsp_core.trade.RateLimit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
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
 * The server's side of the Trade Post's LOTTERY page: the page asks about once a second while it shows, and each player gets
 * their {@link LotteryPageView}. Who is in and the last winners come from a copy read at most every five seconds (and at once
 * after a draw), shared by every player, so many players watching cost no more database reads than one; the pot and the draw's
 * state are this server's own and always current.
 */
@Mod.EventBusSubscriber(modid = com.mrgregles.bsp_core.BSPCore.MODID)
public final class LotteryPage {
    private LotteryPage() {}

    private static final long FRESH_MS = 5000;

    /** Who is in and the last winners, as last read (shared by the page and the Score Screen board's summary). */
    record Copy(long at, LotterySettings settings, List<Lottery.Entry> entries, List<LotteryWin> wins) {}

    @Nullable
    private static Copy copy;
    private static boolean reading;
    private static final Map<UUID, RateLimit> limits = new HashMap<>();

    /** The next view reads afresh (after a draw, so the winners and the drum are current at once). */
    public static void invalidate() {
        copy = null;
    }

    /** A page asking for its view; answered unless the player asks far too often. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        limits.remove(event.getEntity().getUUID());
    }

    public static void asked(ServerPlayer player) {
        if (!limits.computeIfAbsent(player.getUUID(), k -> new RateLimit(4, 2)).allow(System.currentTimeMillis())) {
            return;
        }
        view(player.server, player.getUUID(), v -> {
            if (!player.hasDisconnected()) {
                BSPNetwork.sendChecked(player, new LotteryPagePacket(v), LotteryPagePacket::encode, "the lottery page");
            }
        });
    }

    /** The view for {@code player}: at once from the copy when it is fresh, else after reading it again. */
    public static void view(MinecraftServer server, UUID player, java.util.function.Consumer<LotteryPageView> then) {
        withCopy(server, c -> then.accept(build(server, player, c, System.currentTimeMillis())));
    }

    /** The copy: at once when it is fresh (or a read is already under way), else after reading it again. */
    static void withCopy(MinecraftServer server, java.util.function.Consumer<Copy> then) {
        long now = System.currentTimeMillis();
        Copy c = copy;
        if (c != null && (now - c.at < FRESH_MS || reading)) {
            then.accept(c); // a read under way: the old copy until it lands
            return;
        }
        reading = true;
        Lottery.standings(server, now, now, (settings, all) -> Lottery.wins(server, wins -> {
            reading = false; // first, so nothing below can leave the copy stuck as being read
            Copy fresh = new Copy(now, settings == null ? LotterySettings.DEFAULT : settings, all == null ? (c == null ? List.of() : c.entries) : all,
                    wins == null ? (c == null ? List.of() : c.wins) : wins);
            copy = fresh;
            then.accept(fresh);
        }));
    }

    private static LotteryPageView build(MinecraftServer server, UUID player, Copy c, long now) {
        List<String> names = new ArrayList<>();
        int standing = -1;
        String rank = "";
        for (Lottery.Entry e : c.entries) {
            if (e.in()) {
                names.add(e.player().name());
            }
            if (e.player().id().equals(player)) {
                standing = e.standing().ordinal();
                rank = e.rank();
            }
        }
        int in = names.size();
        names.sort(String.CASE_INSENSITIVE_ORDER);
        LotteryDraw.PageState d0 = LotteryDraw.pageState(now);
        names = withWinner(names, d0, LotteryPageView.MAX_NAMES);
        List<LotteryPageView.Win> wins = new ArrayList<>();
        for (LotteryWin w : c.wins) {
            if (wins.size() < LotteryPageView.WINS) {
                wins.add(new LotteryPageView.Win(w.name(), w.pieces(), w.ms()));
            }
        }
        LotteryDraw.PageState d = d0;
        return new LotteryPageView(Market.pot(server), names, in,
                standing, rank, c.settings.days(), wins, d.phase().ordinal(), d.sinceMs() == 0 ? 0 : now - d.sinceMs(), LotteryDraw.countdownMs(), d.winner(), d.pieces(), now);
    }

    /**
     * The drum's names cut to {@code max}, with the winner kept among them while their win shows, so the drum can stop on
     * them however many are in.
     */
    static List<String> withWinner(List<String> sorted, LotteryDraw.PageState d, int max) {
        List<String> out = new ArrayList<>(sorted.size() > max ? sorted.subList(0, max) : sorted);
        if (d.phase() == LotteryDraw.Phase.WON && !d.winner().isEmpty() && !out.contains(d.winner())) {
            if (out.size() >= max) {
                out.remove(out.size() - 1);
            }
            out.add(d.winner());
            out.sort(String.CASE_INSENSITIVE_ORDER);
        }
        return out;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        copy = null;
        reading = false;
        limits.clear();
    }
}
