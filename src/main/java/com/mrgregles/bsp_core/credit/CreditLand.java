package com.mrgregles.bsp_core.credit;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.BankTransaction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Store credit at the Land Office (Store Credit task 5). Region Guard sells the land and takes payment by calling BSP-Core's bank
 * ({@code api/BspBank}); while the Land Office runs a quote or a purchase for a player, it marks the place here (the plot's name,
 * or open ground for open areas and enlarging), and the bank calls then let that player's tickets for the place count in the
 * balance and pay first. A purchase Region Guard pays back (it could not finish) puts the credit back on the ticket. Each claim's
 * credit share is kept, and when the claim is given up that share of Region Guard's refund is taken back out of the bank, so
 * credit never turns into pieces.
 */
public final class CreditLand {
    private CreditLand() {}

    /**
     * One Land Office action running for a player: where, whether it is a purchase (a quote only looks), until when the mark
     * lasts if Region Guard never answers, and what credit paid so far. The office keeps it and hands it back to {@link #end},
     * so a later action can never take over a purchase still in flight.
     */
    public static final class Mark {
        final CreditPlace place;
        final boolean purchase;
        final long untilMs;
        final List<CreditSpend.Receipt> receipts = new ArrayList<>();

        Mark(CreditPlace place, boolean purchase, long untilMs) {
            this.place = place;
            this.purchase = purchase;
            this.untilMs = untilMs;
        }
    }

    private static final Map<UUID, Mark> PENDING = new HashMap<>();
    /** GameTests only: finds players (test players are not in the player list); null for the server's list. */
    @Nullable
    private static java.util.function.Function<UUID, ServerPlayer> players;

    public static void usePlayers(@Nullable java.util.function.Function<UUID, ServerPlayer> lookup) {
        players = lookup;
    }

    @Nullable
    private static ServerPlayer online(MinecraftServer server, UUID id) {
        var lookup = players;
        return lookup != null ? lookup.apply(id) : server.getPlayerList().getPlayer(id);
    }
    /** A purchase waits for the bank and the database, and Region Guard holds a plot for a minute: five minutes is past any of it. */
    private static final long PURCHASE_MS = 300_000L, QUOTE_MS = 5_000L;
    /** The party Region Guard names in the bank calls (BspBank charge and credit). */
    public static final String REGION_GUARD = "Region Guard";

    /**
     * The Land Office starts a quote or purchase at {@code place}. Null while a purchase of theirs is still in flight: a purchase
     * is then refused ({@link #busy}) and a quote goes ahead without credit.
     */
    @Nullable
    public static Mark begin(ServerPlayer player, CreditPlace place, boolean purchase) {
        if (busy(player)) {
            return null;
        }
        Mark m = new Mark(place, purchase, System.currentTimeMillis() + (purchase ? PURCHASE_MS : QUOTE_MS));
        PENDING.put(player.getUUID(), m);
        return m;
    }

    /** Whether a purchase of the player's is still in flight at the office. */
    public static boolean busy(ServerPlayer player) {
        Mark m = pending(player.getUUID());
        return m != null && m.purchase;
    }

    @Nullable
    private static Mark pending(UUID player) {
        Mark m = PENDING.get(player);
        if (m != null && System.currentTimeMillis() > m.untilMs) {
            PENDING.remove(player);
            return null;
        }
        return m;
    }

    /** For {@code BspBank.balance}: the credit the player's tickets can pay at the office's place right now (0 outside a Land Office action). */
    public static long extra(MinecraftServer server, UUID player) {
        Mark p = pending(player);
        ServerPlayer sp = p == null ? null : online(server, player);
        return sp == null || !CreditSpend.wants(player) ? 0 : CreditSpend.available(sp, p.place);
    }

    /** For {@code BspBank.charge}: Region Guard charging during an office action pays with credit first. False when it is not ours to pay. */
    public static boolean charge(MinecraftServer server, UUID player, long pieces, String from, String reason, Consumer<Boolean> then) {
        Mark p = REGION_GUARD.equals(from) ? pending(player) : null;
        ServerPlayer sp = p == null ? null : online(server, player);
        if (sp == null || !CreditSpend.wants(player) || CreditSpend.available(sp, p.place) <= 0) {
            return false;
        }
        CreditSpend.pay(sp, pieces, p.place, from, reason, r -> {
            if (r.ok()) {
                p.receipts.add(r.receipt());
            }
            then.accept(r.ok());
        });
        return true;
    }

    /**
     * For {@code BspBank.credit}: Region Guard paying back a purchase it could not finish ("refund, ..."). The receipt that paid it
     * is refunded as it was paid (credit to the ticket, the rest to the bank), once. False when it is not ours.
     */
    public static boolean credit(MinecraftServer server, UUID player, long pieces, String to, String reason, Consumer<Boolean> then) {
        Mark p = REGION_GUARD.equals(to) && reason.startsWith("refund") ? pending(player) : null;
        if (p == null) {
            return false;
        }
        for (CreditSpend.Receipt r : p.receipts) {
            if (r.total() == pieces && r.credit() > 0) {
                p.receipts.remove(r);
                CreditSpend.refund(server, r, reason);
                then.accept(true);
                return true;
            }
        }
        return false;
    }

    /** The key a claim's credit share is kept under: its dimension and id (ids repeat across dimensions). */
    static String claimKey(String dimension, String claimId) {
        return dimension + "|" + claimId;
    }

    /** The Land Office's action finished: for a purchase that went through, the credit it paid is kept against the claim. */
    public static void end(ServerPlayer player, @Nullable Mark mark, boolean ok, @Nullable String claimId) {
        if (mark == null) {
            return;
        }
        PENDING.remove(player.getUUID(), mark);
        if (!ok || claimId == null || claimId.isEmpty()) {
            return;
        }
        long credit = 0;
        for (CreditSpend.Receipt r : mark.receipts) {
            credit += r.credit();
        }
        if (credit > 0) {
            CreditLedger.get(player.server).addClaim(claimKey(player.level().dimension().location().toString(), claimId), credit, player.getUUID());
            BSPCore.LOGGER.info("Store credit: claim {} of {} was paid {} pieces with credit", claimId, player.getGameProfile().getName(), credit);
        }
    }

    /**
     * Region Guard paid a refund for a claim given up. The part of it that answers to the credit paid for the claim (that credit
     * times Region Guard's refund percent) is taken back out of the payer's bank, so credit never turns into pieces. On a network
     * the take runs after Region Guard's refund on the same storage thread, so the refund is in the balance it reads.
     */
    public static void abandoned(MinecraftServer server, UUID actor, String dimension, String claimId, long refund) {
        CreditLedger.ClaimCredit share = CreditLedger.get(server).removeClaim(claimKey(dimension, claimId));
        if (share == null || refund <= 0 || share.credit <= 0) {
            return;
        }
        UUID payer = share.payer != null ? share.payer : actor;
        int percent = com.mrgregles.bsp_core.compat.regionguard.RegionGuardClaims.refundPercent();
        long back = BigInteger.valueOf(share.credit).multiply(BigInteger.valueOf(percent < 0 ? 50 : Math.min(100, percent))).divide(BigInteger.valueOf(100)).longValue();
        long take = Math.min(back, refund);
        if (take <= 0) {
            return;
        }
        String why = "the store credit part of the refund for " + claimId;
        Bank.take(server, payer, take, BankTransaction.Type.FEE, "Land Office", why, r -> {
            if (r.ok()) {
                told(server, payer, take, refund, claimId);
                return;
            }
            long have = Bank.pieces(server, payer); // spent meanwhile: as much as is there
            if (have <= 0) {
                BSPCore.LOGGER.warn("Store credit: could not take back {} pieces refunded for claim {} from {}: {}", take, claimId, payer, r.key());
                return;
            }
            Bank.take(server, payer, Math.min(have, take), BankTransaction.Type.FEE, "Land Office", why, r2 -> {
                BSPCore.LOGGER.warn("Store credit: took back {} of {} pieces refunded for claim {} from {} ({})", r2.ok() ? Math.min(have, take) : 0, take, claimId, payer, r2.ok() ? "the rest was spent" : r2.key());
                if (r2.ok()) {
                    told(server, payer, Math.min(have, take), refund, claimId);
                }
            });
        });
    }

    private static void told(MinecraftServer server, UUID payer, long take, long refund, String claimId) {
        BSPCore.LOGGER.info("Store credit: took back {} of {} pieces refunded for claim {} from {}", take, refund, claimId, payer);
        ServerPlayer sp = online(server, payer);
        if (sp != null) {
            sp.displayClientMessage(Component.translatable("message.bsp_core.credit.claim_refund", Bank.format(take)).withStyle(ChatFormatting.GOLD), false);
        }
    }

    /** GameTests only: forgets every mark. */
    public static void clear() {
        PENDING.clear();
    }
}
