package com.mrgregles.bsp_core.market;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * One lot on the marketplace: up to {@code market.maxStacks} stacks a player listed for one price in pieces, bought whole.
 * The items are copies held by the market from the moment of listing until the lot is bought (they go to the buyer),
 * cancelled or runs out (they wait in the seller's Collect box).
 */
public final class MarketLot {
    public final String id;
    public final UUID seller;
    public final String sellerName;
    private final List<ItemStack> items;
    public final long price;
    public final long listedMs, endsMs;
    /** The server the lot was listed on (its {@code storage.serverId}, or "local"). */
    public final String server;

    public MarketLot(String id, UUID seller, String sellerName, List<ItemStack> items, long price, long listedMs, long endsMs, String server) {
        this.id = id;
        this.seller = seller;
        this.sellerName = sellerName;
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack s : items) {
            if (!s.isEmpty()) {
                copy.add(s.copy());
            }
        }
        this.items = Collections.unmodifiableList(copy);
        this.price = price;
        this.listedMs = listedMs;
        this.endsMs = endsMs;
        this.server = server;
    }

    /** The lot's stacks as pictures ({@link ItemPicture}), made once (a lot never changes); shared, not to be changed. */
    private List<ItemStack> pictures;
    /** Its item names and seller in lower case, for the search, made once. */
    private String searchText;

    /** The lot's stacks as pictures: what a card, a board or a list needs to draw, small whatever the items hold. Do not change them. */
    public List<ItemStack> pictures() {
        if (pictures == null) {
            List<ItemStack> out = new ArrayList<>(items.size());
            items.forEach(s -> out.add(ItemPicture.of(s)));
            pictures = Collections.unmodifiableList(out);
        }
        return pictures;
    }

    /** True when {@code query} (lower case) is in the seller's name or any stack's name. */
    public boolean matches(String query) {
        if (searchText == null) {
            StringBuilder b = new StringBuilder(sellerName.toLowerCase(java.util.Locale.ROOT));
            items.forEach(s -> b.append('\n').append(s.getHoverName().getString().toLowerCase(java.util.Locale.ROOT)));
            searchText = b.toString();
        }
        return searchText.contains(query);
    }

    /** The lot's stacks, as fresh copies the caller may keep. */
    public List<ItemStack> items() {
        List<ItemStack> out = new ArrayList<>(items.size());
        items.forEach(s -> out.add(s.copy()));
        return out;
    }

    public int stacks() {
        return items.size();
    }

    public boolean endedBy(long now) {
        return now >= endsMs;
    }

    /** "64 Diamond", or "64 Diamond +2 more" for a lot of several stacks. */
    public String describe() {
        if (items.isEmpty()) {
            return "an empty lot";
        }
        ItemStack first = items.get(0);
        String s = (first.getCount() > 1 ? first.getCount() + " " : "") + first.getHoverName().getString();
        return items.size() > 1 ? s + " +" + (items.size() - 1) + " more" : s;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putUUID("Seller", seller);
        tag.putString("SellerName", sellerName);
        ListTag list = new ListTag();
        items.forEach(s -> list.add(s.save(new CompoundTag())));
        tag.put("Items", list);
        tag.putLong("Price", price);
        tag.putLong("Listed", listedMs);
        tag.putLong("Ends", endsMs);
        tag.putString("Server", server);
        return tag;
    }

    public static MarketLot load(CompoundTag tag) {
        List<ItemStack> items = new ArrayList<>();
        ListTag list = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            items.add(ItemStack.of(list.getCompound(i)));
        }
        return new MarketLot(tag.getString("Id"), tag.getUUID("Seller"), tag.getString("SellerName"), items, tag.getLong("Price"),
                tag.getLong("Listed"), tag.getLong("Ends"), tag.getString("Server"));
    }
}
