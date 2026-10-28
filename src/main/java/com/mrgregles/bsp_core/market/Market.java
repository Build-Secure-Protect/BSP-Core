package com.mrgregles.bsp_core.market;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.api.event.MarketEvent;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.BankTransaction;
import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * The marketplace rules. Players list lots of up to {@code market.maxStacks} stacks for one price in pieces; anyone else
 * buys a lot whole. The seller pays {@code market.taxPercent} of the price when listing and keeps nothing back if the lot
 * is cancelled or runs out; the buyer pays the price plus the same tax; the seller gets the price. Every tax goes into the
 * lottery pot, a bank account of its own ({@link #POT}). Cancelled and expired lots wait in the seller's Collect box.
 * Shatter Totems and Totem Cores never go on the market, not even inside a shulker box or a bundle.
 *
 * <p>Every call answers through {@code then} on the server thread (at once on a single server). The money and the lot move
 * in one step of the {@link MarketStore} (on a network, one database transaction), so a lot is sold once and nothing
 * moves halfway; when the network cannot be reached nothing moves at all.
 */
public final class Market {
    private Market() {}

    /** The lottery pot's bank account. Not a player: nobody can log in as it, pay it or be paid from it. */
    public static final UUID POT = UUID.nameUUIDFromBytes((BSPCore.MODID + ":lottery_pot").getBytes(StandardCharsets.UTF_8));
    public static final String POT_NAME = "Lottery pot";
    /** The highest price a lot may ask, so the price and the tax stay far from overflow. */
    public static final long MAX_PRICE = 1_000_000_000_000L;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    // ------------------------------------------------------------------ hooks

    @Nullable
    private static MarketStore storeOverride;
    private static LongSupplier clock = System::currentTimeMillis;

    private static final LocalMarket LOCAL = new LocalMarket(null);

    /** The test's store, else the network's while network storage is on, else this world's. */
    public static MarketStore store(MinecraftServer server) {
        if (storeOverride != null) {
            return storeOverride;
        }
        NetworkMarket net = NetworkStorage.market();
        return net != null ? net : LOCAL;
    }

    /** GameTests: use this store (null for the usual one again); returns the one it replaced. Set and put back within one tick. */
    @Nullable
    public static MarketStore useStore(@Nullable MarketStore store) {
        MarketStore old = storeOverride;
        storeOverride = store;
        return old;
    }

    public static long now() {
        return clock.getAsLong();
    }

    /** GameTests: run the market at a chosen time (null for the wall clock again). Set and put back within one tick. */
    public static void useClock(@Nullable LongSupplier c) {
        clock = c == null ? System::currentTimeMillis : c;
    }

    // ------------------------------------------------------------------ rules read by the screens too

    public static int maxStacks() {
        return BSPConfig.getOr(BSPConfig.MARKET_MAX_STACKS, 9);
    }

    public static int maxLots() {
        return BSPConfig.getOr(BSPConfig.MARKET_MAX_LOTS, 10);
    }

    public static int listingDays() {
        return BSPConfig.getOr(BSPConfig.MARKET_DAYS, 7);
    }

    /** The tax on a price: {@code market.taxPercent} of it, rounded up, at least 1 piece; 0 when the tax is off. */
    public static long tax(long price) {
        int pct = BSPConfig.getOr(BSPConfig.MARKET_TAX_PERCENT, 5);
        if (pct <= 0 || price <= 0) {
            return 0;
        }
        return Math.max(1, (price * pct + 99) / 100);
    }

    /** True for anything that may not be listed or traded: totems, Totem Cores, the config list, and containers holding any of them. */
    public static boolean refused(ItemStack stack) {
        return refused(stack, 0);
    }

    private static boolean refused(ItemStack stack, int depth) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(ModItems.SHATTER_TOTEM.get()) || stack.is(ModItems.TOTEM_CORE.get()) || inRefusedList(stack)) {
            return true;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || depth >= 4) {
            return false;
        }
        // a shulker box keeps its items under BlockEntityTag, a bundle at the top
        List<ListTag> inner = new ArrayList<>();
        if (tag.contains("BlockEntityTag", Tag.TAG_COMPOUND)) {
            inner.add(tag.getCompound("BlockEntityTag").getList("Items", Tag.TAG_COMPOUND));
        }
        inner.add(tag.getList("Items", Tag.TAG_COMPOUND));
        for (ListTag list : inner) {
            for (int i = 0; i < list.size(); i++) {
                if (refused(ItemStack.of(list.getCompound(i)), depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean inRefusedList(ItemStack stack) {
        List<? extends String> list = BSPConfig.getOr(BSPConfig.MARKET_REFUSED, List.of());
        if (list.isEmpty()) {
            return false;
        }
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        for (String entry : list) {
            String e = entry.trim();
            if (e.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(e.substring(1));
                if (tag != null && stack.is(TagKey.create(Registries.ITEM, tag))) {
                    return true;
                }
            } else if (key != null && key.toString().equals(e)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ reading

    /** Every open lot, newest first. */
    public static List<MarketLot> open(MinecraftServer server) {
        return store(server).open(server);
    }

    public static Optional<MarketLot> find(MinecraftServer server, String id) {
        return store(server).open(server).stream().filter(l -> l.id.equals(id)).findFirst();
    }

    public static int lotsOf(MinecraftServer server, UUID seller) {
        return (int) store(server).open(server).stream().filter(l -> l.seller.equals(seller)).count();
    }

    /** What waits in the player's Collect box. */
    public static List<ItemStack> collectBox(MinecraftServer server, UUID player) {
        return store(server).peekCollect(server, player);
    }

    /** Pieces in the lottery pot. */
    public static long pot(MinecraftServer server) {
        return Bank.pieces(server, POT);
    }

    /** Pays {@code pieces} into the lottery pot (market tax, a shop's share of a purchase); nothing for 0 or less. A failure is logged. */
    public static void toPot(MinecraftServer server, long pieces, String from, String detail) {
        if (pieces <= 0) {
            return;
        }
        Bank.account(server, POT).name = POT_NAME;
        Bank.give(server, POT, pieces, BankTransaction.Type.TAX, from, detail, r -> {
            if (!r.ok()) {
                BSPCore.LOGGER.warn("BSP-Core: {} pieces ({}) could not reach the lottery pot ({})", pieces, detail, r.key());
            }
        });
    }

    // ------------------------------------------------------------------ listing

    /**
     * Lists {@code stacks} (already taken from the player) for {@code price} pieces. On success the market holds the items;
     * on failure the result's items are the stacks to give back. Fails with "empty", "stacks" (too many), "refused",
     * "price", "lots" (the player has {@code market.maxLots} open), "short" (the tax), "unreachable", or "unsure" (the
     * network's answer was lost: nothing comes back, the lot is listed or its stacks go to the seller's Collect box).
     */
    public static void list(MinecraftServer server, UUID seller, String name, List<ItemStack> stacks, long price, Consumer<MarketResult> then) {
        List<ItemStack> given = new ArrayList<>();
        stacks.forEach(s -> {
            if (!s.isEmpty()) {
                given.add(s.copy());
            }
        });
        String fail = given.isEmpty() ? "empty"
                : given.size() > maxStacks() ? "stacks"
                : given.stream().anyMatch(Market::refused) ? "refused"
                : price < 1 || price > MAX_PRICE ? "price"
                : lotsOf(server, seller) >= maxLots() ? "lots" : null;
        if (fail != null) {
            then.accept(MarketResult.fail(fail, given));
            return;
        }
        long now = now();
        MarketLot lot = new MarketLot(newId(server), seller, name, given, price, now, now + listingDays() * DAY_MS, Bank.serverId());
        long tax = tax(price);
        store(server).list(server, lot, tax, key -> {
            if (key.equals("unsure")) {
                then.accept(MarketResult.fail(key, List.of())); // the stacks are not handed back: listed, or soon in the Collect box
                return;
            }
            if (!key.equals("ok")) {
                then.accept(MarketResult.fail(key, given));
                return;
            }
            BSPCore.LOGGER.info("BSP-Core market: {} listed lot {} ({}) for {} pieces, tax {}", name, lot.id, lot.describe(), price, tax);
            MinecraftForge.EVENT_BUS.post(new MarketEvent.Listed(server, lot.id, seller, name, price, lot.items(), tax));
            then.accept(MarketResult.ok(lot, List.of()));
        });
    }

    // ------------------------------------------------------------------ buying

    /**
     * Buys a lot whole: the buyer pays the price plus the tax, the seller is paid the price, the tax goes into the pot, the
     * items go into the buyer's inventory when they are online here (what does not fit, and everything when they are not,
     * goes to their Collect box). Fails with "gone" (bought, cancelled or run out), "own", "short", "unreachable" or
     * "unsure_buy" (the network's answer was lost; if it went through, the items wait in the Collect box).
     */
    public static void buy(MinecraftServer server, UUID buyer, String name, String id, Consumer<MarketResult> then) {
        MarketLot seen = find(server, id).orElse(null);
        if (seen == null || seen.endedBy(now())) {
            then.accept(MarketResult.fail("gone"));
            return;
        }
        if (seen.seller.equals(buyer)) {
            then.accept(MarketResult.fail("own"));
            return;
        }
        long tax = tax(seen.price), total = seen.price + tax;
        if (Bank.pieces(server, buyer) < total) {
            then.accept(MarketResult.fail("short"));
            return;
        }
        store(server).buy(server, id, buyer, name, tax, answer -> {
            if (!answer.ok()) {
                then.accept(MarketResult.fail(answer.key()));
                return;
            }
            MarketLot lot = answer.lot();
            NetworkStorage.tell(server, lot.seller, Component.translatable("message.bsp_core.market.sold", lot.describe(), name, Bank.format(lot.price)));
            List<ItemStack> items = lot.items();
            if (answer.boxed()) {
                // on a network the items are already in the buyer's Collect box; empty it into the inventory when they are here
                ServerPlayer online = server.getPlayerList().getPlayer(buyer);
                if (online != null) {
                    collect(server, online, r -> {
                        if (!r.ok() || !r.items().isEmpty()) {
                            online.displayClientMessage(Component.translatable("message.bsp_core.market.to_collect", Math.max(1, r.items().size())), false);
                        }
                    });
                }
            } else {
                deliver(server, buyer, items);
            }
            BSPCore.LOGGER.info("BSP-Core market: {} bought lot {} ({}) from {} for {} pieces + {} tax", name, lot.id, lot.describe(), lot.sellerName, lot.price, tax);
            MinecraftForge.EVENT_BUS.post(new MarketEvent.Sold(server, lot.id, lot.seller, lot.sellerName, lot.price, items, buyer, name, tax));
            then.accept(MarketResult.ok(lot, items));
        });
    }

    // ------------------------------------------------------------------ cancelling, running out, collecting

    /** Takes an open lot off the market: by its seller, or by an admin ({@code admin}). The items go to the seller's Collect box; the listing tax stays paid. */
    public static void cancel(MinecraftServer server, UUID by, String id, boolean admin, Consumer<MarketResult> then) {
        MarketLot seen = find(server, id).orElse(null);
        if (seen == null) {
            then.accept(MarketResult.fail("gone"));
            return;
        }
        if (!admin && !seen.seller.equals(by)) {
            then.accept(MarketResult.fail("not_yours"));
            return;
        }
        store(server).close(server, id, answer -> {
            if (!answer.ok()) {
                then.accept(MarketResult.fail(answer.key()));
                return;
            }
            MarketLot lot = answer.lot();
            if (!lot.seller.equals(by)) {
                NetworkStorage.tell(server, lot.seller, Component.translatable("message.bsp_core.market.removed", lot.describe()));
            }
            BSPCore.LOGGER.info("BSP-Core market: lot {} ({}) of {} cancelled{}", lot.id, lot.describe(), lot.sellerName, admin && !lot.seller.equals(by) ? " by an admin" : "");
            then.accept(MarketResult.ok(lot, List.of()));
        });
    }

    /** How long past its end a lot listed on another server waits for that server to close it before any server does (its own may be down). */
    public static final long SWEEP_GRACE_MS = 10 * 60_000L;

    /**
     * Moves every lot past its end time to its seller's Collect box and tells them. On a network each server closes the lots
     * listed on it, and any server those more than {@link #SWEEP_GRACE_MS} past their end; the database lets one close each
     * lot. Returns how many this call closed at once (on a network the answers come later).
     */
    public static int expireDue(MinecraftServer server) {
        long now = now();
        MarketStore store = store(server);
        String here = Bank.serverId();
        List<String> due = new ArrayList<>();
        for (MarketLot lot : store.open(server)) {
            if (lot.endedBy(now) && (lot.server.equals(here) || now >= lot.endsMs + SWEEP_GRACE_MS)) {
                due.add(lot.id);
            }
        }
        if (due.isEmpty()) {
            return 0;
        }
        int[] n = {0};
        store.closeAll(server, due, answer -> {
            if (answer.ok()) {
                MarketLot l = answer.lot();
                NetworkStorage.tell(server, l.seller, Component.translatable("message.bsp_core.market.expired", l.describe()));
                BSPCore.LOGGER.info("BSP-Core market: lot {} ({}) of {} ran out", l.id, l.describe(), l.sellerName);
                n[0]++;
            }
        });
        return n[0];
    }

    /** Empties the player's Collect box into their inventory; what does not fit stays in the box. The result's count is the stacks handed over. */
    public static void collect(MinecraftServer server, Player player, Consumer<MarketResult> then) {
        MarketStore store = store(server);
        store.takeCollect(server, player.getUUID(), got -> {
            if (got.isEmpty()) {
                then.accept(MarketResult.fail("unreachable"));
                return;
            }
            List<ItemStack> items = got.get();
            List<ItemStack> left = intoInventory(player, items);
            if (!left.isEmpty()) {
                store.addCollect(server, player.getUUID(), left);
            }
            int given = items.size() - left.size();
            then.accept(new MarketResult(true, left.isEmpty() ? "ok" : "collect_full", null, left, given));
        });
    }

    // ------------------------------------------------------------------ inside

    /** Items for a player: into their inventory when they are online here, the rest into their Collect box. */
    private static void deliver(MinecraftServer server, UUID player, List<ItemStack> items) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        List<ItemStack> left = online == null ? items : intoInventory(online, items);
        if (!left.isEmpty()) {
            store(server).addCollect(server, player, left);
            if (online != null) {
                online.displayClientMessage(Component.translatable("message.bsp_core.market.to_collect", left.size()), false);
            }
        }
    }

    /** Puts the stacks into the inventory as far as they fit (stacking first); returns what did not fit. */
    public static List<ItemStack> intoInventory(Player player, List<ItemStack> items) {
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack s : items) {
            ItemStack copy = s.copy();
            player.getInventory().add(copy);
            if (!copy.isEmpty()) {
                left.add(copy);
            }
        }
        return left;
    }

    private static String newId(MinecraftServer server) {
        List<MarketLot> open = store(server).open(server);
        while (true) {
            String id = Long.toString(ThreadLocalRandom.current().nextLong(36L * 36 * 36 * 36 * 36 * 36 * 36, 36L * 36 * 36 * 36 * 36 * 36 * 36 * 36), 36);
            if (open.stream().noneMatch(l -> l.id.equals(id))) {
                return id;
            }
        }
    }
}
