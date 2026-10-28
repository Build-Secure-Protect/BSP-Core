package com.mrgregles.bsp_core.lottery;

import com.mrgregles.bsp_core.admin.SeasonData;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.compat.LuckPermsBridge;
import com.mrgregles.bsp_core.market.Market;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.LotteryAdminPacket;
import com.mrgregles.bsp_core.trade.RateLimit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The server's side of the lottery's admin page (operators only), in the Trade Post behind its cog tab and alone by
 * {@code /bsp lottery admin}: the page asks for its view and sends the admin's keys ({@link Action}); every answer is a fresh
 * {@link LotteryAdminView}, with a message for the key pressed.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.mrgregles.bsp_core.BSPCore.MODID)
public final class LotteryAdmin {
    private LotteryAdmin() {}

    /** REFRESH; DAYS ({@code amount} added to the days); RANK ({@code text} left out when {@code flag}, else let in); ADD and TAKE ({@code amount} pieces, {@code text} the reason); DRAW. */
    public enum Action { REFRESH, DAYS, RANK, ADD, TAKE, DRAW }

    private static final Map<UUID, RateLimit> limits = new HashMap<>();

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        limits.remove(event.getEntity().getUUID());
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        limits.clear();
    }

    public static boolean allowed(ServerPlayer player) {
        return player.hasPermissions(2);
    }

    /** A key from an admin's page: refused (nothing sent) unless they are an operator; then done and answered with the view. */
    public static void act(ServerPlayer player, Action action, String text, long amount, boolean flag) {
        if (!allowed(player) || !limits.computeIfAbsent(player.getUUID(), k -> new RateLimit(8, 4)).allow(System.currentTimeMillis())) {
            return;
        }
        apply(player.server, player.getGameProfile().getName(), action, text, amount, flag, (msg, args) -> send(player, msg, args, false));
    }

    /** Opens the page by itself for an operator (the command); nothing for anyone else, however the command reached them. */
    public static void open(ServerPlayer player) {
        if (allowed(player)) {
            send(player, "", List.of(), true);
        }
    }

    /** Does one key for admin {@code by}; {@code then} gets the message key ("" none) and its arguments. */
    public static void apply(MinecraftServer server, String by, Action action, String text, long amount, boolean flag, BiConsumer<String, List<String>> then) {
        String k = "gui.bsp_core.lottery.admin.";
        switch (action) {
            case REFRESH -> then.accept("", List.of());
            case DAYS, RANK -> Lottery.settings(server, s -> {
                if (s == null) {
                    then.accept(k + "unreachable", List.of());
                    return;
                }
                LotterySettings next = action == Action.DAYS ? s.withDays(s.days() + (int) Math.max(-90, Math.min(90, amount))) : s.withRank(text, flag);
                Lottery.saveSettings(server, next, ok -> {
                    if (!ok) {
                        then.accept(k + "unreachable", List.of());
                    } else if (action == Action.DAYS) {
                        then.accept(k + "days_set", List.of(String.valueOf(next.days())));
                    } else {
                        then.accept(k + (flag ? "rank_out" : "rank_in"), List.of(text.toLowerCase(java.util.Locale.ROOT)));
                    }
                });
            });
            case ADD, TAKE -> {
                String reason = text.trim();
                if (amount <= 0 || amount > Market.MAX_PRICE) {
                    then.accept(k + "amount", List.of());
                    return;
                }
                if (reason.isEmpty()) {
                    then.accept(k + "reason_needed", List.of());
                    return;
                }
                Consumer<com.mrgregles.bsp_core.bank.BankResult> done = r -> then.accept(k + (r.ok() ? (action == Action.ADD ? "added" : "taken")
                        : r.key().equals("short") ? "short" : r.key().equals("drawing") ? "during_draw" : "unreachable"),
                        List.of(Bank.format(amount)));
                if (action == Action.ADD) {
                    Lottery.addToPot(server, amount, by, reason, done);
                } else {
                    Lottery.takeFromPot(server, amount, by, reason, done);
                }
            }
            case DRAW -> LotteryDraw.start(server, by, key -> then.accept(k + (key.equals("ok") ? "drawing" : key.equals("nobody") ? "nobody_in" : key), List.of()));
        }
    }

    /** Builds the page's view: everyone seen this season, the settings, the ranks to pick from. */
    public static void view(MinecraftServer server, String message, List<String> args, boolean open, Consumer<LotteryAdminView> then) {
        long now = System.currentTimeMillis();
        Lottery.standings(server, now, SeasonData.get(server).startMs, (settings, all) -> {
            LotterySettings s = settings == null ? LotterySettings.DEFAULT : settings;
            List<Lottery.Entry> entries = all == null ? List.of() : all;
            String msg = all == null && message.isEmpty() ? "gui.bsp_core.lottery.admin.unreachable" : message;
            TreeSet<String> ranks = new TreeSet<>(s.leftOut());
            LuckPermsBridge.groupNames().forEach(r -> ranks.add(r.toLowerCase(java.util.Locale.ROOT)));
            List<LotteryAdminView.Row> rows = new ArrayList<>();
            int in = 0;
            for (Lottery.Entry e : entries) {
                ranks.addAll(e.player().ranks());
                if (e.in()) {
                    in++;
                }
                if (rows.size() < LotteryAdminView.MAX_ROWS) {
                    rows.add(new LotteryAdminView.Row(e.player().name(), e.player().seenMs(), e.player().ranksText(), e.standing().ordinal(), e.rank()));
                }
            }
            then.accept(new LotteryAdminView(Market.pot(server), s.days(), s.leftOut(), List.copyOf(ranks), rows, in, entries.size() - in, entries.size(),
                    LotteryDraw.busy(), now, msg, args, open));
        });
    }

    private static void send(ServerPlayer player, String message, List<String> args, boolean open) {
        view(player.server, message, args, open, v -> {
            if (!player.hasDisconnected()) {
                BSPNetwork.sendChecked(player, new LotteryAdminPacket(v), LotteryAdminPacket::encode, "the lottery admin page");
            }
        });
    }
}
