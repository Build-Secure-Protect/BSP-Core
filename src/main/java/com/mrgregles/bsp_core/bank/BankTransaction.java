package com.mrgregles.bsp_core.bank;

import net.minecraft.nbt.CompoundTag;

/**
 * One line of a player's transaction log. {@code pieces} is signed from the player's point of view
 * (money in is positive); a coin movement carries the tier and a signed count the same way.
 *
 * @param with  the other side: a player's name, a shop's name, the bank, or an admin
 * @param detail what it was: an item name and count, "converted 2 Copper coins", a reason
 * @param id    the row's own number, never reused: the database's on a network, the ledger's on a single server (0 until recorded)
 * @param refundedPieces how many of the row's pieces staff have put back or taken back so far (never more than {@code |pieces|})
 * @param refundedCoins  the same for its coins (never more than {@code |coinCount|})
 * @param refundOf on a REFUND row, the id of the row it refunds; 0 otherwise
 */
public record BankTransaction(long ms, Type type, String with, String detail, long pieces, int coinTier, int coinCount, String server, String shopId, String itemId,
                              long id, long refundedPieces, int refundedCoins, long refundOf) {
    /** New types go at the end (clients read them by ordinal). */
    public enum Type { BOUGHT, SOLD, PAID, RECEIVED, DEPOSIT, CONVERT, WITHDRAW, GRANT, ADMIN, RENT, FEE, MARKET, TAX, LOTTERY, REFUND, BOUNTY }

    /** A new row, before it is recorded. */
    public BankTransaction(long ms, Type type, String with, String detail, long pieces, int coinTier, int coinCount, String server, String shopId, String itemId) {
        this(ms, type, with, detail, pieces, coinTier, coinCount, server, shopId, itemId, 0, 0, 0, 0);
    }

    /** A row that is not a shop trade (or one logged before rows named the shop and item). */
    public BankTransaction(long ms, Type type, String with, String detail, long pieces, int coinTier, int coinCount, String server) {
        this(ms, type, with, detail, pieces, coinTier, coinCount, server, "", "");
    }

    /** The same row under its id. */
    public BankTransaction withId(long newId) {
        return new BankTransaction(ms, type, with, detail, pieces, coinTier, coinCount, server, shopId, itemId, newId, refundedPieces, refundedCoins, refundOf);
    }

    /** The same row with {@code p} more pieces and {@code c} more coins refunded. */
    public BankTransaction refunded(long p, int c) {
        return new BankTransaction(ms, type, with, detail, pieces, coinTier, coinCount, server, shopId, itemId, id, refundedPieces + p, refundedCoins + c, refundOf);
    }

    /** A REFUND row pointing at the row it refunds. */
    public BankTransaction refundOf(long rowId) {
        return new BankTransaction(ms, type, with, detail, pieces, coinTier, coinCount, server, shopId, itemId, id, refundedPieces, refundedCoins, rowId);
    }

    /** Pieces of this row not refunded yet. */
    public long piecesLeft() {
        return Math.max(0, Math.abs(pieces) - refundedPieces);
    }

    /** Coins of this row not refunded yet. */
    public int coinsLeft() {
        return Math.max(0, Math.abs(coinCount) - refundedCoins);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Ms", ms);
        tag.putString("Type", type.name());
        tag.putString("With", with);
        tag.putString("Detail", detail);
        tag.putLong("Pieces", pieces);
        tag.putInt("Tier", coinTier);
        tag.putInt("Count", coinCount);
        tag.putString("Server", server);
        if (!shopId.isEmpty()) {
            tag.putString("ShopId", shopId);
            tag.putString("ItemId", itemId);
        }
        if (id != 0) {
            tag.putLong("Id", id);
        }
        if (refundedPieces != 0 || refundedCoins != 0) {
            tag.putLong("RefP", refundedPieces);
            tag.putInt("RefC", refundedCoins);
        }
        if (refundOf != 0) {
            tag.putLong("RefOf", refundOf);
        }
        return tag;
    }

    public static BankTransaction load(CompoundTag tag) {
        Type type;
        try {
            type = Type.valueOf(tag.getString("Type"));
        } catch (IllegalArgumentException e) {
            type = Type.ADMIN;
        }
        return new BankTransaction(tag.getLong("Ms"), type, tag.getString("With"), tag.getString("Detail"), tag.getLong("Pieces"), tag.getInt("Tier"), tag.getInt("Count"), tag.getString("Server"),
                tag.getString("ShopId"), tag.getString("ItemId"), tag.getLong("Id"), tag.getLong("RefP"), tag.getInt("RefC"), tag.getLong("RefOf"));
    }
}
