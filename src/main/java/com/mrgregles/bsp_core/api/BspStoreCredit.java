package com.mrgregles.bsp_core.api;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.credit.CreditKind;
import com.mrgregles.bsp_core.credit.CreditPlace;
import com.mrgregles.bsp_core.credit.CreditSpend;
import com.mrgregles.bsp_core.credit.CreditTicket;
import com.mrgregles.bsp_core.credit.StoreCredit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Store credit for other mods (BSP-Teleport request SC-1). An admin can mark a store credit ticket kind for teleports; such a
 * ticket in the player's inventory pays BSP-Teleport's prices in Shatter Pieces before their bank. Amounts are pieces. Call
 * everything on the server thread. BSP-Core shows its own messages when a ticket's clock starts, when it is used up and when it
 * runs out; say what was paid in your own words from the {@link Receipt}.
 */
public final class BspStoreCredit {
    private BspStoreCredit() {}

    /** What the credit pays for. */
    public enum Use { TELEPORT }

    /**
     * What {@link #charge} took: {@code credit} from tickets and {@code bank} from the bank. {@code ticketName} is the kind's name of
     * the last ticket drawn from, {@code creditLeft} what is left on it, {@code ticketSpent} whether it was used up. Keep it to
     * give the payment back with {@link #refund}.
     */
    public static final class Receipt {
        private final CreditSpend.Receipt inner;
        /** Given back already: a second refund does nothing. */
        private final java.util.concurrent.atomic.AtomicBoolean refunded = new java.util.concurrent.atomic.AtomicBoolean();

        Receipt(CreditSpend.Receipt inner) {
            this.inner = inner;
        }

        public UUID player() {
            return inner.player();
        }

        public long credit() {
            return inner.credit();
        }

        public long bank() {
            return inner.bank();
        }

        public String ticketName() {
            return inner.ticketName();
        }

        public long creditLeft() {
            return inner.creditLeft();
        }

        public boolean ticketSpent() {
            return inner.ticketSpent();
        }

        @Override
        public String toString() {
            return "Receipt[credit=" + credit() + ", bank=" + bank() + ", ticket=" + ticketName() + ", left=" + creditLeft() + "]";
        }
    }

    private static CreditPlace place(Use use) {
        return switch (use) {
            case TELEPORT -> CreditPlace.teleport();
        };
    }

    /** The credit the player's tickets can pay for this use right now (0 when none; on a network, as this server last read it). */
    public static long credit(ServerPlayer player, Use use) {
        return CreditSpend.available(player, place(use));
    }

    /** Bank balance plus {@link #credit}: what a "can they afford it" check should use. */
    public static long spendable(ServerPlayer player, Use use) {
        return Bank.pieces(player.server, player.getUUID()) + credit(player, use);
    }

    /**
     * Takes {@code pieces}: credit first (the ticket that runs out soonest, then the one with least left), the bank for the rest,
     * all or nothing. {@code then} runs on the server thread with a {@link Receipt}, or null when nothing was taken (short, the
     * database unreachable, the credit changed meanwhile). Nothing to pay gives an empty receipt. {@code from} and {@code reason}
     * name the other side and the purchase in the player's bank log.
     */
    public static void charge(ServerPlayer player, long pieces, Use use, String from, String reason, Consumer<Receipt> then) {
        CreditSpend.pay(player, pieces, place(use), from, reason, r -> then.accept(r.ok() ? new Receipt(r.receipt()) : null));
    }

    /**
     * Gives back what a receipt took, because the purchase did not happen: the credit to the ticket it came from (lost if that
     * ticket ran out or was voided meanwhile, its item handed back if it had been used up), the bank's part to the bank. Works
     * while the player is offline.
     */
    public static void refund(MinecraftServer server, @Nullable Receipt receipt, String reason) {
        if (receipt != null && receipt.refunded.compareAndSet(false, true)) {
            CreditSpend.refund(server, receipt.inner, reason);
        }
    }

    /**
     * GameTests only: puts a minted ticket holding {@code pieces} (for this use only) in the player's inventory and returns it.
     * Its kind, {@code bsp_test_<use>}, is made if missing.
     */
    public static ItemStack testTicket(ServerPlayer player, long pieces, Use use) {
        String id = "bsp_test_" + use.name().toLowerCase(java.util.Locale.ROOT);
        if (StoreCredit.kind(player.server, id) == null) {
            CreditKind k = new CreditKind(id);
            k.name = "Test " + use.name().toLowerCase(java.util.Locale.ROOT);
            k.value = CreditKind.VALUE_MAX;
            k.teleports = use == Use.TELEPORT;
            String problem = StoreCredit.saveKind(player.server, k);
            if (problem != null) {
                throw new IllegalStateException(problem);
            }
        }
        ItemStack ticket = StoreCredit.fresh(id);
        CreditTicket t = StoreCredit.mint(player, ticket);
        if (t == null) {
            throw new IllegalStateException("store credit storage is down");
        }
        t.left = Math.max(0, pieces);
        StoreCredit.refresh(ticket, t, StoreCredit.kind(player.server, id));
        if (!player.getInventory().add(ticket)) {
            throw new IllegalStateException("no room for the test ticket");
        }
        return ticket;
    }
}
