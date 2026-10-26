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
    /** A rotating shop's draw: how likely this card is to be picked against the others (0 never, 1 usual, up to 100). */
    public int weight = 1;
    /** Selling: false takes only the card's exact item and data; true takes the same item whatever it carries (see {@link #takes}). */
    public boolean matchItem;
    /** A note for players, shown at the foot of the card's tooltip (never put on the item): up to three lines of plain text. */
    public final java.util.List<String> note = new java.util.ArrayList<>();
    public static final int NOTE_LINES = 3, NOTE_CHARS = 60;
    /** What a player needs to trade this card (a permission, hours of play), and whether it hides or shows locked. */
    public Requirement requires = Requirement.NONE;

    /** Sets the note from text with "|" between lines; empty lines are dropped, a fourth line and more are dropped, and a line is cut at 60 characters with "…". Empty clears it. */
    public void setNote(String text) {
        note.clear();
        for (String line : text.split("\\|")) {
            String t = line.strip();
            if (t.isEmpty() || note.size() >= NOTE_LINES) {
                continue;
            }
            note.add(t.length() > NOTE_CHARS ? t.substring(0, NOTE_CHARS - 1).strip() + "…" : t);
        }
    }

    /** The note as it is typed: lines with "|" between them. */
    public String noteText() {
        return String.join("|", note);
    }

    /** The data that says what an item is, kept even when a card matches by item: a potion's contents, a book's enchantments. */
    private static final java.util.List<String> IDENTITY = java.util.List.of("Potion", "CustomPotionEffects", "StoredEnchantments");

    /** True when {@code offered} counts as this card's item for a sale (whether it may be sold is {@link #refusal}). */
    public boolean takes(ItemStack offered) {
        return sameKind(offered, stack, matchItem);
    }

    /**
     * The match rule, also used by the client's Max: the same item and data; or with {@code anyData}
     * the same item, any data except {@link #IDENTITY} (a used machine is a machine, a water bottle
     * is not a Potion of Strength).
     */
    public static boolean sameKind(ItemStack offered, ItemStack card, boolean anyData) {
        if (!anyData) {
            return ItemStack.isSameItemSameTags(offered, card);
        }
        if (offered.isEmpty() || !ItemStack.isSameItem(offered, card)) {
            return false;
        }
        for (String key : IDENTITY) {
            if (!java.util.Objects.equals(offered.getTag() == null ? null : offered.getTag().get(key), card.getTag() == null ? null : card.getTag().get(key))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Why a stack this card takes may not be sold to it, or null: with a match by item, a damaged
     * item ("damaged") or one holding items ("holds_items": a backpack, a shulker box, a machine with
     * items in its slots), which would be lost with it.
     */
    @javax.annotation.Nullable
    public String refusal(ItemStack offered) {
        if (!matchItem) {
            return null;
        }
        if (offered.isDamaged()) {
            return "damaged";
        }
        return holdsItems(offered) ? "holds_items" : null;
    }

    /** True when a stack carries other items: through its item handler, or an "Items" list anywhere in its data. */
    public static boolean holdsItems(ItemStack s) {
        var handler = s.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER).resolve();
        if (handler.isPresent()) {
            for (int i = 0; i < handler.get().getSlots(); i++) {
                if (!handler.get().getStackInSlot(i).isEmpty()) {
                    return true;
                }
            }
        }
        return s.getTag() != null && itemsList(s.getTag(), 0);
    }

    private static boolean itemsList(net.minecraft.nbt.Tag tag, int depth) {
        if (depth > 8) {
            return false;
        }
        if (tag instanceof CompoundTag c) {
            for (String k : c.getAllKeys()) {
                net.minecraft.nbt.Tag v = c.get(k);
                if (k.equals("Items") && v instanceof net.minecraft.nbt.ListTag l && !l.isEmpty() || itemsList(v, depth + 1)) {
                    return true;
                }
            }
        } else if (tag instanceof net.minecraft.nbt.ListTag l) {
            for (net.minecraft.nbt.Tag v : l) {
                if (itemsList(v, depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }
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
        t.putInt("Weight", weight);
        t.putString("Match", matchItem ? "item" : "exact");
        net.minecraft.nbt.ListTag lines = new net.minecraft.nbt.ListTag();
        note.forEach(l -> lines.add(net.minecraft.nbt.StringTag.valueOf(l)));
        t.put("Note", lines);
        t.put("Requires", requires.save());
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
        i.weight = t.contains("Weight") ? Math.max(0, Math.min(100, t.getInt("Weight"))) : 1;
        i.matchItem = t.getString("Match").equals("item");
        StringBuilder note = new StringBuilder();
        for (net.minecraft.nbt.Tag l : t.getList("Note", net.minecraft.nbt.Tag.TAG_STRING)) {
            note.append(note.length() > 0 ? "|" : "").append(l.getAsString().replace("|", "/"));
        }
        i.setNote(note.toString());
        i.requires = Requirement.load(t.getCompound("Requires"));
        return i;
    }

    private static Refill rule(String text, Refill otherwise) {
        Refill r = text.isEmpty() ? null : Refill.parse(text);
        return r == null ? otherwise : r;
    }
}
