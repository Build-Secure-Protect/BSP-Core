package com.mrgregles.bsp_core.market;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * One buy order: a player wants {@code wanted} of one plain item at {@code each} pieces apiece, and anyone may send part or
 * all of it. The whole price ({@code wanted} x {@code each}) left the buyer's bank when the order was placed; what is not
 * spent comes back when the order is cancelled or runs out. {@code filled} is how many have been sent so far. An order only
 * ever changes by being filled (a new {@code BuyOrder} with more filled), so a copy can be compared by its fill.
 *
 * <p>{@code item} is the plain item as one stack of 1: the item's own default form, with no enchantments, damage, name or
 * other data beyond what the item always carries. Only stacks exactly like it fill the order.
 */
public final class BuyOrder {
    public final String id;
    public final UUID buyer;
    public final String buyerName;
    private final ItemStack item;
    public final int wanted, filled;
    public final long each, placedMs, endsMs;
    /** The server the order was placed on (its {@code storage.serverId}, or "local"). */
    public final String server;

    public BuyOrder(String id, UUID buyer, String buyerName, ItemStack item, int wanted, int filled, long each, long placedMs, long endsMs, String server) {
        this.id = id;
        this.buyer = buyer;
        this.buyerName = buyerName;
        this.item = item.copyWithCount(1);
        this.wanted = wanted;
        this.filled = filled;
        this.each = each;
        this.placedMs = placedMs;
        this.endsMs = endsMs;
        this.server = server;
    }

    /** The plain item, one of it, as a copy the caller may keep. */
    public ItemStack item() {
        return item.copy();
    }

    /** How many are still wanted. */
    public int toGo() {
        return Math.max(0, wanted - filled);
    }

    /** The pieces still held for the order: what is left to pay for the items still wanted. */
    public long held() {
        return toGo() * each;
    }

    /** This order with {@code n} more sent. */
    public BuyOrder filledBy(int n) {
        return new BuyOrder(id, buyer, buyerName, item, wanted, Math.min(wanted, filled + n), each, placedMs, endsMs, server);
    }

    public boolean endedBy(long now) {
        return now >= endsMs;
    }

    /** True for a stack that fills this order: the same item with exactly the plain item's data. */
    public boolean takes(ItemStack stack) {
        return !stack.isEmpty() && !item.isEmpty() && ItemStack.isSameItemSameTags(stack, item);
    }

    /** True when {@code query} (lower case) is in the item's name or the buyer's. */
    public boolean matches(String query) {
        return item.getHoverName().getString().toLowerCase(java.util.Locale.ROOT).contains(query) || buyerName.toLowerCase(java.util.Locale.ROOT).contains(query);
    }

    /** "64 Cactus". */
    public String describe() {
        return wanted + " " + item.getHoverName().getString();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putUUID("Buyer", buyer);
        tag.putString("BuyerName", buyerName);
        tag.put("Item", item.save(new CompoundTag()));
        tag.putInt("Wanted", wanted);
        tag.putInt("Filled", filled);
        tag.putLong("Each", each);
        tag.putLong("Placed", placedMs);
        tag.putLong("Ends", endsMs);
        tag.putString("Server", server);
        return tag;
    }

    public static BuyOrder load(CompoundTag tag) {
        return new BuyOrder(tag.getString("Id"), tag.getUUID("Buyer"), tag.getString("BuyerName"), ItemStack.of(tag.getCompound("Item")), tag.getInt("Wanted"),
                tag.getInt("Filled"), tag.getLong("Each"), tag.getLong("Placed"), tag.getLong("Ends"), tag.getString("Server"));
    }
}
