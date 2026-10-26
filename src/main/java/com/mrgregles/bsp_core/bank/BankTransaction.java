package com.mrgregles.bsp_core.bank;

import net.minecraft.nbt.CompoundTag;

/**
 * One line of a player's transaction log. {@code pieces} is signed from the player's point of view
 * (money in is positive); a coin movement carries the tier and a signed count the same way.
 *
 * @param with  the other side: a player's name, a shop's name, the bank, or an admin
 * @param detail what it was: an item name and count, "converted 2 Copper coins", a reason
 */
public record BankTransaction(long ms, Type type, String with, String detail, long pieces, int coinTier, int coinCount, String server, String shopId, String itemId) {
    public enum Type { BOUGHT, SOLD, PAID, RECEIVED, DEPOSIT, CONVERT, WITHDRAW, GRANT, ADMIN, RENT, FEE }

    /** A row that is not a shop trade (or one logged before rows named the shop and item). */
    public BankTransaction(long ms, Type type, String with, String detail, long pieces, int coinTier, int coinCount, String server) {
        this(ms, type, with, detail, pieces, coinTier, coinCount, server, "", "");
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
                tag.getString("ShopId"), tag.getString("ItemId"));
    }
}
