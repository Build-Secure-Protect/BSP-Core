package com.mrgregles.bsp_core.shop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * One entry of a shop page: the stack sold or bought (its count is the lot size), a buy price and a
 * sell price (each -1 when that side is off) in pieces or a coin tier, stock limits and how they
 * restock, and the small switches.
 */
public final class ShopItem {
    public ItemStack stack = ItemStack.EMPTY;
    public long buy = -1, sell = -1;
    /** -1 for pieces, otherwise a {@link com.mrgregles.bsp_core.coin.CoinTier} ordinal. */
    public int currency = -1;
    /** Stock for the whole server (-1 unlimited) and per player (-1 unlimited), and the countdown after it runs out (0 never). */
    public int stockGlobal = -1, stockPlayer = -1;
    public long restockMs;
    public boolean instant, hidden;
    /** A sub-section this entry opens instead of selling anything, or empty. */
    public String section = "";

    public boolean buyable() {
        return buy >= 0 && !stack.isEmpty() && section.isEmpty();
    }

    public boolean sellable() {
        return sell >= 0 && !stack.isEmpty() && section.isEmpty();
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.put("Stack", stack.save(new CompoundTag()));
        t.putLong("Buy", buy);
        t.putLong("Sell", sell);
        t.putInt("Currency", currency);
        t.putInt("StockGlobal", stockGlobal);
        t.putInt("StockPlayer", stockPlayer);
        t.putLong("Restock", restockMs);
        t.putBoolean("Instant", instant);
        t.putBoolean("Hidden", hidden);
        t.putString("Section", section);
        return t;
    }

    public static ShopItem load(CompoundTag t) {
        ShopItem i = new ShopItem();
        i.stack = ItemStack.of(t.getCompound("Stack"));
        i.buy = t.getLong("Buy");
        i.sell = t.getLong("Sell");
        i.currency = t.getInt("Currency");
        i.stockGlobal = t.getInt("StockGlobal");
        i.stockPlayer = t.getInt("StockPlayer");
        i.restockMs = t.getLong("Restock");
        i.instant = t.getBoolean("Instant");
        i.hidden = t.getBoolean("Hidden");
        i.section = t.getString("Section");
        return i;
    }
}
