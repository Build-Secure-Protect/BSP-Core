package com.mrgregles.bsp_core.credit;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.BankResult;
import com.mrgregles.bsp_core.bank.BankTransaction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Paying with store credit (Store Credit task 2, one server; the network comes in task 3).
 * <p>A price is paid from the tickets in the player's inventory that may pay at the place (the kind lists it, the ticket is not
 * voided, withdrawn, used up or out of time, and it is unbound or bound to this player), the one that runs out soonest first and
 * then the one with least left, and the bank pays the rest. It is all or nothing: when the bank cannot pay its part, no credit is
 * taken. A ticket's first purchase starts its clock and binds it to the player; a ticket that reaches nothing disappears.
 */
public final class CreditSpend {
    private CreditSpend() {}

    /** What one ticket paid. */
    public record Part(String serial, long pieces) {}

    /**
     * What a payment took: {@code credit} from tickets ({@code parts}), {@code bank} from the bank. {@code ticketName} and
     * {@code creditLeft} describe the last ticket drawn from (what is left on it after), {@code ticketSpent} whether it was used up.
     */
    public record Receipt(UUID player, long credit, long bank, List<Part> parts, String ticketName, long creditLeft, boolean ticketSpent) {
        public long total() {
            return credit + bank;
        }
    }

    /** A payment's answer: the receipt, or why nothing was taken ("short", "unreachable", "busy", "changed", ...). */
    public record Result(@Nullable Receipt receipt, String failKey) {
        public boolean ok() {
            return receipt != null;
        }

        static Result fail(String key) {
            return new Result(null, key);
        }
    }

    /** A ticket that may pay at a place, with the time it would run out by if it paid now. */
    public record Usable(CreditTicket ticket, CreditKind kind, long runsOut) {}

    /** Players who switched PAY WITH CREDIT off (it is on for everyone else); forgotten when they log out. */
    private static final Set<UUID> NO_CREDIT = new HashSet<>();

    /** Whether the player pays with credit where a ticket of theirs works (the switch on the shop and office screens; on by default). */
    public static boolean wants(UUID player) {
        return !NO_CREDIT.contains(player);
    }

    public static void setWants(UUID player, boolean on) {
        if (on) {
            NO_CREDIT.remove(player);
        } else {
            NO_CREDIT.add(player);
        }
    }

    /** Players with a payment waiting for the bank or the database, with when it started: a second one waits its turn ("busy"). */
    private static final java.util.Map<UUID, Long> PAYING = new java.util.HashMap<>();
    /** A payment never answered (a lost callback) frees the player after this long. */
    private static final long PAYING_MS = 60_000L;

    /** Forgets a player's waiting payment (they logged out). */
    public static void forget(UUID player) {
        PAYING.remove(player);
    }

    /**
     * The tickets in the player's inventory that may pay at {@code place}, once each however many copies they hold, in the order
     * they pay: the one that runs out soonest (a ticket not started yet runs out its time limit from now; one with no time
     * limit never), then the one with least left.
     */
    public static List<Usable> usable(ServerPlayer player, CreditPlace place) {
        long now = StoreCredit.now();
        CreditLedger l = CreditLedger.get(player.server);
        Map<String, Usable> found = new LinkedHashMap<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            String serial = StoreCredit.isTicket(s) ? StoreCredit.serialOf(s) : "";
            if (serial.isEmpty() || found.containsKey(serial)) {
                continue;
            }
            CreditTicket t = l.ticket(serial);
            CreditKind k = t == null ? null : l.kind(t.kind);
            if (t == null || k == null || t.voided || t.left <= 0 || !t.usableBy(player.getUUID()) || t.expired(k, now) || !place.coveredBy(k)) {
                continue;
            }
            long end = t.endsMs(k);
            long runsOut = end > 0 ? end : k.durationMs > 0 ? now + k.durationMs : Long.MAX_VALUE;
            found.put(serial, new Usable(t, k, runsOut));
        }
        List<Usable> out = new ArrayList<>(found.values());
        out.sort(Comparator.comparingLong(Usable::runsOut).thenComparingLong(u -> u.ticket().left).thenComparing(u -> u.ticket().serial));
        return out;
    }

    /** The credit the player's tickets can pay at {@code place} right now. */
    public static long available(ServerPlayer player, CreditPlace place) {
        long sum = 0;
        for (Usable u : usable(player, place)) {
            sum += u.ticket().left;
        }
        return sum;
    }

    /**
     * Pays {@code price} pieces: credit first, the bank for the rest, all or nothing. {@code from} and {@code what} name the
     * other side and the purchase in the bank log. {@code then} gets the result on the server thread: at once on a single
     * server, once the database answered on a network. A network server whose database is not connected refuses credit
     * ("unreachable"); a player who left while the bank answered gets the bank's part back ("gone").
     */
    public static void pay(ServerPlayer player, long price, CreditPlace place, String from, String what, Consumer<Result> then) {
        pay(player, price, place, from, what, "", "", then);
    }

    /** As {@link #pay}, for a shop trade: the bank log rows name the shop and the item, for the shop report. */
    public static void pay(ServerPlayer player, long price, CreditPlace place, String from, String what, String shopId, String itemId, Consumer<Result> then) {
        if (price <= 0) {
            then.accept(new Result(new Receipt(player.getUUID(), 0, 0, List.of(), "", 0, false), ""));
            return;
        }
        UUID id = player.getUUID();
        Long since = PAYING.get(id);
        if (since != null && System.currentTimeMillis() - since < PAYING_MS) {
            then.accept(Result.fail("busy"));
            return;
        }
        long credit = Math.min(price, available(player, place)), bank = price - credit;
        if (credit > 0 && CreditSync.down()) {
            then.accept(Result.fail("unreachable"));
            return;
        }
        long started = System.currentTimeMillis();
        PAYING.put(id, started);
        Consumer<Result> done = r -> {
            PAYING.remove(id, started);
            then.accept(r);
        };
        Log log = new Log(from, what, shopId, itemId);
        if (bank <= 0) {
            draw(player, credit, 0, place, log, done);
            return;
        }
        Bank.take(player.server, id, bank, BankTransaction.Type.BOUGHT, from, credit > 0 ? what + " (" + Bank.format(credit) + " of it with store credit)" : what, shopId, itemId, answer -> {
            if (!answer.ok()) {
                done.accept(Result.fail(answer.key()));
                return;
            }
            Consumer<Result> refundBank = r -> {
                if (!r.ok()) { // nothing is bought: the bank's part goes back
                    Bank.give(player.server, id, bank, BankTransaction.Type.SOLD, from, what + " (refunded: " + r.failKey() + ")", back -> {
                        if (!back.ok()) {
                            BSPCore.LOGGER.error("Store credit: could not give {} pieces back to {} after a payment that did not go through ({}): {}", bank, id, r.failKey(), back.key());
                        }
                    });
                }
                done.accept(r);
            };
            if (gone(player)) {
                refundBank.accept(Result.fail("gone"));
                return;
            }
            draw(player, credit, bank, place, log, refundBank);
        });
    }

    private static boolean gone(ServerPlayer player) {
        return com.mrgregles.bsp_core.shop.Shops.gone(player);
    }

    /** How a payment is named in the bank log: the other side, the purchase, and for a shop trade the shop and item ids. */
    private record Log(String from, String what, String shopId, String itemId) {}

    /** One ticket's share of a payment, planned from this server's copy. */
    private record Take(Usable ticket, long pieces, boolean first) {}

    /**
     * Takes {@code credit} from the tickets, checking them again first (they may have changed while the bank answered). On a
     * network the database takes it (all or nothing, and only if every ticket still has its share); alone, the ledger does.
     */
    private static void draw(ServerPlayer player, long credit, long bank, CreditPlace place, Log log, Consumer<Result> then) {
        if (credit <= 0) {
            then.accept(new Result(new Receipt(player.getUUID(), 0, bank, List.of(), "", 0, false), ""));
            return;
        }
        List<Take> plan = new ArrayList<>();
        long need = credit;
        for (Usable u : usable(player, place)) {
            if (need <= 0) {
                break;
            }
            long take = Math.min(need, u.ticket().left);
            plan.add(new Take(u, take, u.ticket().firstUseMs <= 0));
            need -= take;
        }
        if (need > 0) {
            then.accept(Result.fail("changed"));
            return;
        }
        MinecraftServer server = player.server;
        if (!CreditSync.on()) {
            long now = StoreCredit.now();
            for (Take t : plan) {
                CreditTicket ticket = t.ticket().ticket();
                if (t.first()) {
                    ticket.firstUseMs = now;
                }
                if (ticket.owner == null) { // every ticket is bound to whoever makes its first purchase
                    ticket.owner = player.getUUID();
                    ticket.ownerName = player.getGameProfile().getName();
                }
                ticket.left -= t.pieces();
            }
            CreditLedger.get(server).setDirty();
            then.accept(finish(player, plan, credit, bank, log, now));
            return;
        }
        List<CreditStore.Claim> claims = new ArrayList<>();
        List<String> serials = new ArrayList<>();
        for (Take t : plan) {
            claims.add(new CreditStore.Claim(t.ticket().ticket().serial, t.pieces(), t.ticket().kind().durationMs));
            serials.add(t.ticket().ticket().serial);
        }
        UUID id = player.getUUID();
        String name = player.getGameProfile().getName();
        record Spent(long at, List<CreditTicket> rows) {}
        CreditSync.run("spend store credit", s -> {
            long at = s.spend(claims, id, name);
            List<CreditTicket> rows;
            try {
                rows = s.readTickets(serials);
            } catch (Exception e) { // the spend is made: never report it as failed for want of the read-back
                BSPCore.LOGGER.warn("Store credit: spent, but could not read the tickets back: {}", e.toString());
                rows = null;
            }
            return new Spent(at, rows);
        }, "t:" + String.join(",", serials), spent -> {
            if (spent == null) {
                then.accept(Result.fail("unreachable"));
                return;
            }
            CreditLedger l = CreditLedger.get(server);
            if (spent.rows() == null) { // spent but not read back: this server's copy takes the spend itself, and is read again
                if (spent.at() >= 0) {
                    for (Take t : plan) {
                        CreditTicket ticket = t.ticket().ticket();
                        if (t.first()) {
                            ticket.firstUseMs = spent.at();
                        }
                        if (ticket.owner == null) {
                            ticket.owner = id;
                            ticket.ownerName = name;
                        }
                        ticket.left -= t.pieces();
                    }
                    l.setDirty();
                }
                CreditSync.reload(server, serials, null);
            } else {
                for (CreditTicket row : spent.rows()) {
                    l.adoptTicket(row); // the network's state, spent or not: a refused spend corrects this server's copy
                }
            }
            if (spent.at() < 0) {
                StoreCredit.refreshHeld(player);
                then.accept(Result.fail("changed"));
                return;
            }
            then.accept(finish(player, plan, credit, bank, log, spent.at()));
        });
    }

    /** After the credit is taken: bank log lines, the clock message, used-up tickets taken away, the receipt. */
    private static Result finish(ServerPlayer player, List<Take> plan, long credit, long bank, Log log, long now) {
        String from = log.from(), what = log.what();
        MinecraftServer server = player.server;
        CreditLedger l = CreditLedger.get(server);
        List<Part> parts = new ArrayList<>();
        CreditTicket last = null;
        CreditKind lastKind = null;
        for (Take t : plan) {
            CreditTicket ticket = l.ticket(t.ticket().ticket().serial);
            if (ticket == null) {
                ticket = t.ticket().ticket();
            }
            CreditKind kind = t.ticket().kind();
            parts.add(new Part(ticket.serial, t.pieces()));
            last = ticket;
            lastKind = kind;
            // the shop report counts a row's items from its leading number: only one row of a purchase may start with it
            String line = bank <= 0 && parts.size() == 1 ? what + " with store credit: " + Bank.format(t.pieces()) + " pieces from " + kind.name + " #" + ticket.serial
                    : "store credit for " + what + ": " + Bank.format(t.pieces()) + " pieces from " + kind.name + " #" + ticket.serial;
            Bank.record(server, player.getUUID(), new BankTransaction(now, BankTransaction.Type.BOUGHT, from, line, 0, 0, 0, Bank.serverId(), log.shopId(), log.itemId()));
            BSPCore.LOGGER.info("Store credit: {} paid {} pieces with ticket {} ({}), {} left, at {} for {}", player.getGameProfile().getName(), t.pieces(), ticket.serial, ticket.kind, ticket.left, from, what);
            if (t.first() && kind.durationMs > 0) {
                player.displayClientMessage(Component.translatable("message.bsp_core.credit.started", kind.name, Bank.format(ticket.left), StoreCredit.date(ticket.endsMs(kind)))
                        .withStyle(ChatFormatting.GOLD), false);
                StoreCredit.notice(player, new com.mrgregles.bsp_core.network.CreditNoticePacket(false, kind.name, kind.colour, kind.mark, ticket.left, ticket.endsMs(kind)));
            }
            if (ticket.left <= 0) {
                StoreCredit.takeAway(player, ticket.serial);
                player.displayClientMessage(Component.translatable("message.bsp_core.credit.used_up", kind.name).withStyle(ChatFormatting.GOLD), false);
            }
        }
        StoreCredit.refreshHeld(player);
        return new Result(new Receipt(player.getUUID(), credit, bank, List.copyOf(parts), lastKind.name, last.left, last.left <= 0), "");
    }

    /**
     * Gives back what a receipt took, because the purchase did not happen: each ticket's part goes back on that ticket (lost when
     * the ticket was voided, withdrawn or ran out of time meanwhile), with its item handed back if it had been used up; the
     * bank's part goes back to the bank (also while they are offline). On a network the tickets are put back in the database.
     */
    public static void refund(MinecraftServer server, Receipt receipt, String why) {
        CreditLedger l = CreditLedger.get(server);
        long now = StoreCredit.now();
        List<Part> back = new ArrayList<>();
        java.util.Set<String> wereEmpty = new java.util.HashSet<>();
        for (Part p : receipt.parts()) {
            CreditTicket t = l.ticket(p.serial());
            CreditKind k = t == null ? null : l.kind(t.kind);
            if (t == null || k == null || t.voided || t.expired(k, now)) {
                BSPCore.LOGGER.info("Store credit refund of {} pieces to ticket {} lost: the ticket is gone ({})", p.pieces(), p.serial(), why);
                continue;
            }
            back.add(p);
            if (t.left <= 0) {
                wereEmpty.add(p.serial());
            }
        }
        if (receipt.bank() > 0) {
            Bank.give(server, receipt.player(), receipt.bank(), BankTransaction.Type.SOLD, "Store credit", "refund: " + why, (BankResult r) -> {
                if (!r.ok()) {
                    BSPCore.LOGGER.warn("Could not give {} pieces back to {} ({})", receipt.bank(), receipt.player(), why);
                }
            });
        }
        if (back.isEmpty()) {
            return;
        }
        if (!CreditSync.on()) {
            for (Part p : back) {
                l.ticket(p.serial()).left += p.pieces();
            }
            l.setDirty();
            handBack(server, receipt.player(), back, wereEmpty);
            return;
        }
        List<String> serials = new ArrayList<>();
        back.forEach(p -> serials.add(p.serial()));
        CreditSync.run("refund store credit", s -> {
            for (Part p : back) {
                if (!s.refund(p.serial(), p.pieces())) {
                    BSPCore.LOGGER.info("Store credit refund of {} pieces to ticket {} lost: voided on the network ({})", p.pieces(), p.serial(), why);
                }
            }
            return s.readTickets(serials);
        }, "t:" + String.join(",", serials), rows -> {
            if (rows == null) {
                BSPCore.LOGGER.error("Store credit refund to {} could not be written ({}): {}", receipt.player(), why, back);
                return;
            }
            rows.forEach(l::adoptTicket);
            handBack(server, receipt.player(), back, wereEmpty);
        });
    }

    /** The items of tickets that had been used up go back to the player, and every ticket they hold shows its credit again. */
    private static void handBack(MinecraftServer server, UUID player, List<Part> back, java.util.Set<String> wereEmpty) {
        CreditLedger l = CreditLedger.get(server);
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        for (Part p : back) {
            CreditTicket t = l.ticket(p.serial());
            if (t == null || !wereEmpty.contains(p.serial()) || t.left <= 0) {
                continue;
            }
            ItemStack item = StoreCredit.itemOf(t, l.kind(t.kind));
            if (online != null) {
                com.mrgregles.bsp_core.admin.AdminService.giveItems(online, List.of(item), "message.bsp_core.credit.refunded");
            } else {
                com.mrgregles.bsp_core.admin.AdminService.deliver(server, player, List.of(item), "message.bsp_core.credit.refunded");
            }
        }
        if (online != null) {
            StoreCredit.refreshHeld(online);
        }
    }

    /** GameTests only: forgets payments left waiting by a test. */
    public static void clearWaiting() {
        PAYING.clear();
    }
}
