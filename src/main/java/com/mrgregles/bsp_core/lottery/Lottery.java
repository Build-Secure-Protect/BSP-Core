package com.mrgregles.bsp_core.lottery;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.compat.LuckPermsBridge;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The lottery. Everyone seen on the network within a set number of days of a draw is in it, one equal chance each, except
 * operators and the ranks the admin leaves out; an admin draws it, and the winner takes the whole pot ({@code Market.POT}).
 * Every login and logout, on every server, records the player's time, ranks and operator status ({@link LotteryPlayer}), in
 * the database's {@code lottery_players} on a network and in {@link LotteryLedger} on a single server. The admin's
 * {@link LotterySettings} (the days and the ranks left out) decide each player's {@link Standing}.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID)
public final class Lottery {
    private Lottery() {}

    /** Where a player stands for the next draw: in, or left out and why. */
    public enum Standing { IN, OPERATOR, RANK, AWAY }

    /** A player with their standing; {@code rank} names the rank that leaves them out (RANK only, else ""). */
    public record Entry(LotteryPlayer player, Standing standing, String rank) {
        public boolean in() {
            return standing == Standing.IN;
        }
    }

    /** A player's LuckPerms ranks while they are online; GameTests put their own in its place. Empty without LuckPerms. */
    public static Function<UUID, Set<String>> rankSource = id -> LuckPermsBridge.isPresent() ? LuckPermsBridge.groupsOf(id) : Set.of();

    /** How often every server records its online players as seen again, so a long stay never reads as away. */
    private static final int REFRESH_TICKS = 5 * 60 * 20;
    private static int refreshTicks;

    /**
     * True on a server meant to keep its records on the network whose database is not connected (yet, or any more): the
     * lottery then refuses to draw, move the pot or change settings, and records nothing, rather than act on this server's own
     * old copies.
     */
    public static boolean offline() {
        return NetworkStorage.countsWanted() && !NetworkStorage.enabled();
    }

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END && ++refreshTicks >= REFRESH_TICKS) {
            refreshTicks = 0;
            event.getServer().getPlayerList().getPlayers().forEach(Lottery::seen);
        }
    }

    /** Before the network storage closes (it closes at normal priority): everyone still online is recorded as seen now. */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGH)
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        event.getServer().getPlayerList().getPlayers().forEach(Lottery::seen);
        refreshTicks = 0;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            seen(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            seen(player);
        }
    }

    /**
     * Records the player as seen now, with their ranks and operator status. With LuckPerms there, no ranks at all means the
     * lookup failed (every LuckPerms user is at least in "default"), so the ranks stored before are kept.
     */
    public static void seen(ServerPlayer player) {
        if (offline()) {
            return;
        }
        MinecraftServer server = player.server;
        Set<String> ranks = rankSource.apply(player.getUUID());
        boolean ranksKnown = !ranks.isEmpty() || !LuckPermsBridge.isPresent();
        LotteryPlayer p = new LotteryPlayer(player.getUUID(), player.getGameProfile().getName(), System.currentTimeMillis(),
                ranks, server.getPlayerList().isOp(player.getGameProfile()), Bank.serverId());
        if (NetworkStorage.enabled()) {
            NetworkStorage.lotterySeen(p, ranksKnown);
        } else {
            LotteryLedger.get(server).seen(p, ranksKnown);
        }
    }

    /** The settings in force; null when the network could not be reached. */
    public static void settings(MinecraftServer server, Consumer<LotterySettings> then) {
        if (NetworkStorage.enabled()) {
            NetworkStorage.readStateKey(LotterySettings.STATE_KEY, v -> then.accept(v == null ? null : LotterySettings.fromText(v)));
        } else {
            then.accept(LotteryLedger.get(server).settings());
        }
    }

    /** Saves the settings; {@code then} hears whether they went in. */
    public static void saveSettings(MinecraftServer server, LotterySettings settings, Consumer<Boolean> then) {
        if (offline()) {
            then.accept(false);
            return;
        }
        if (NetworkStorage.enabled()) {
            NetworkStorage.writeStateKey(LotterySettings.STATE_KEY, settings.text(), then);
        } else {
            LotteryLedger.get(server).settings(settings);
            then.accept(true);
        }
    }

    /** Where {@code p} stands at {@code now}: an operator first, then a rank left out, then not seen within the days. */
    public static Entry standing(LotteryPlayer p, LotterySettings s, long now) {
        if (p.op()) {
            return new Entry(p, Standing.OPERATOR, "");
        }
        for (String rank : s.leftOut()) {
            if (p.ranks().contains(rank)) {
                return new Entry(p, Standing.RANK, rank);
            }
        }
        return new Entry(p, p.seenMs() >= now - s.windowMs() ? Standing.IN : Standing.AWAY, "");
    }

    /**
     * Everyone seen at or after {@code sinceMs} (and everyone within the settings' days), with where they stand at {@code now},
     * the most recently seen first, and the settings used; null when the network could not be reached.
     */
    public static void standings(MinecraftServer server, long now, long sinceMs, java.util.function.BiConsumer<LotterySettings, List<Entry>> then) {
        settings(server, s -> {
            if (s == null) {
                then.accept(null, null);
                return;
            }
            seenSince(server, Math.min(sinceMs, now - s.windowMs()), players -> {
                if (players == null) {
                    then.accept(s, null);
                    return;
                }
                List<Entry> out = new java.util.ArrayList<>();
                for (LotteryPlayer p : players) {
                    out.add(standing(p, s, now));
                }
                out.sort(java.util.Comparator.comparingLong((Entry e) -> e.player().seenMs()).reversed().thenComparing(e -> e.player().name(), String.CASE_INSENSITIVE_ORDER));
                then.accept(s, out);
            });
        });
    }

    /** The players in a draw at {@code now}; null when the network could not be reached. */
    public static void entrants(MinecraftServer server, long now, Consumer<List<LotteryPlayer>> then) {
        standings(server, now, Long.MAX_VALUE, (s, all) -> then.accept(all == null ? null : all.stream().filter(Entry::in).map(Entry::player).toList()));
    }

    /** Past wins, newest first (at most {@link LotteryWin#KEEP}); null when the network could not be reached. */
    public static void wins(MinecraftServer server, Consumer<List<LotteryWin>> then) {
        if (NetworkStorage.enabled()) {
            NetworkStorage.readStateKey(LotteryWin.STATE_KEY, v -> then.accept(v == null ? null : LotteryWin.fromText(v)));
        } else {
            then.accept(LotteryLedger.get(server).wins());
        }
    }

    /**
     * An admin adds pieces to the pot (an event prize, say); the pot's log names them and the reason. Fails with "unreachable"
     * on a network server without its database, and "drawing" while a draw runs here or anywhere on the network (the winner is
     * told the pot when the count begins).
     */
    public static void addToPot(MinecraftServer server, long pieces, String by, String reason, Consumer<com.mrgregles.bsp_core.bank.BankResult> then) {
        whenNoDraw(then, () -> {
            Bank.account(server, com.mrgregles.bsp_core.market.Market.POT).name = com.mrgregles.bsp_core.market.Market.POT_NAME;
            Bank.give(server, com.mrgregles.bsp_core.market.Market.POT, pieces, com.mrgregles.bsp_core.bank.BankTransaction.Type.ADMIN, by, reason, then);
        });
    }

    /** An admin takes pieces out of the pot; fails with "short" when it holds fewer, and as {@link #addToPot}. */
    public static void takeFromPot(MinecraftServer server, long pieces, String by, String reason, Consumer<com.mrgregles.bsp_core.bank.BankResult> then) {
        whenNoDraw(then, () -> Bank.take(server, com.mrgregles.bsp_core.market.Market.POT, pieces, com.mrgregles.bsp_core.bank.BankTransaction.Type.ADMIN, by, reason, then));
    }

    /** Runs {@code act} unless the network is wanted but away, or a draw holds the pot (here, or by its claim on a network). */
    private static void whenNoDraw(Consumer<com.mrgregles.bsp_core.bank.BankResult> refused, Runnable act) {
        if (offline()) {
            refused.accept(com.mrgregles.bsp_core.bank.BankResult.fail("unreachable"));
            return;
        }
        if (LotteryDraw.busy()) {
            refused.accept(com.mrgregles.bsp_core.bank.BankResult.fail("drawing"));
            return;
        }
        if (!NetworkStorage.enabled()) {
            act.run();
            return;
        }
        NetworkStorage.readStateKey("lottery_draw", held -> {
            if (held == null) {
                refused.accept(com.mrgregles.bsp_core.bank.BankResult.fail("unreachable"));
            } else if (LotteryDraw.claimLive(held, System.currentTimeMillis())) {
                refused.accept(com.mrgregles.bsp_core.bank.BankResult.fail("drawing"));
            } else {
                act.run();
            }
        });
    }

    /** Everyone seen at or after {@code sinceMs}, in no order; null when the network could not be reached. */
    public static void seenSince(MinecraftServer server, long sinceMs, Consumer<List<LotteryPlayer>> then) {
        if (NetworkStorage.enabled()) {
            NetworkStorage.lotteryPlayers(sinceMs, then);
        } else {
            then.accept(LotteryLedger.get(server).since(sinceMs));
        }
    }
}
