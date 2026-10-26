package com.mrgregles.bsp_core.shop;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * One entry of a shop page: the stack sold or bought (its count is the lot size), a buy price and a
 * sell price (each -1 when that side is off) in pieces or a coin tier, stock limits on buying and
 * sell limits on selling (each for everyone and per player) with the rules that refill them, and
 * the small switches.
 */
public final class ShopItem {
    public ItemStack stack = ItemStack.EMPTY;
    public long buy = -1, sell = -1;
    /** -1 for pieces, otherwise a {@link com.mrgregles.bsp_core.coin.CoinTier} ordinal. */
    public int currency = -1;
    /** Lots that can be bought, by everyone (-1 unlimited) and by each player (-1 unlimited). */
    public int stockGlobal = -1, stockPlayer = -1;
    /** Lots the shop will buy back, from everyone (-1 unlimited) and from each player (-1 unlimited). */
    public int sellGlobal = -1, sellPlayer = -1;
    /** When each count fills again; a per-player rule of SAME follows the shop-wide one. */
    public Refill stockRefill = Refill.NEVER, stockPlayerRefill = Refill.SAME, sellRefill = Refill.NEVER, sellPlayerRefill = Refill.SAME;
    public boolean instant, hidden;
    /** A sub-section this entry opens instead of selling anything, or empty. */
    public String section = "";

    public boolean buyable() {
        return buy >= 0 && !stack.isEmpty() && section.isEmpty();
    }

    public boolean sellable() {
        return sell >= 0 && !stack.isEmpty() && section.isEmpty();
    }

    public Refill stockPlayerRule() {
        return stockPlayerRefill.kind() == Refill.Kind.SAME ? stockRefill : stockPlayerRefill;
    }

    public Refill sellPlayerRule() {
        return sellPlayerRefill.kind() == Refill.Kind.SAME ? sellRefill : sellPlayerRefill;
    }

    /** The stock countdown in minutes when the shop-wide stock refills by one (0 otherwise): the older single restock setting. */
    public long restockMinutes() {
        return stockRefill.kind() == Refill.Kind.AFTER ? stockRefill.minutes() : 0;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.put("Stack", stack.save(new CompoundTag()));
        t.putLong("Buy", buy);
        t.putLong("Sell", sell);
        t.putInt("Currency", currency);
        t.putInt("StockGlobal", stockGlobal);
        t.putInt("StockPlayer", stockPlayer);
        t.putLong("Restock", restockMinutes() * 60_000L);
        t.putString("StockRefill", stockRefill.text());
        t.putString("StockPlayerRefill", stockPlayerRefill.text());
        t.putInt("SellGlobal", sellGlobal);
        t.putInt("SellPlayer", sellPlayer);
        t.putString("SellRefill", sellRefill.text());
        t.putString("SellPlayerRefill", sellPlayerRefill.text());
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
        // a card saved before the refill rules: its restock countdown is the rule for both stock counts
        i.stockRefill = t.contains("StockRefill") ? rule(t.getString("StockRefill"), Refill.NEVER) : Refill.after(t.getLong("Restock") / 60_000L);
        i.stockPlayerRefill = rule(t.getString("StockPlayerRefill"), Refill.SAME);
        i.sellGlobal = t.contains("SellGlobal") ? t.getInt("SellGlobal") : -1;
        i.sellPlayer = t.contains("SellPlayer") ? t.getInt("SellPlayer") : -1;
        i.sellRefill = rule(t.getString("SellRefill"), Refill.NEVER);
        i.sellPlayerRefill = rule(t.getString("SellPlayerRefill"), Refill.SAME);
        i.instant = t.getBoolean("Instant");
        i.hidden = t.getBoolean("Hidden");
        i.section = t.getString("Section");
        return i;
    }

    private static Refill rule(String text, Refill otherwise) {
        Refill r = text.isEmpty() ? null : Refill.parse(text);
        return r == null ? otherwise : r;
    }
}
