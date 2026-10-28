package com.mrgregles.bsp_core.trade;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.market.Market;
import com.mrgregles.bsp_core.market.MarketLot;
import com.mrgregles.bsp_core.market.MarketResult;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.TradePostDataPacket;
import com.mrgregles.bsp_core.registry.ModMenus;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The Trade Post screen's menu (look C, the touch screen): the MARKET, SELL and COLLECT pages and the traders band. Its
 * only slots are the 9 places of a lot being listed and, on the SELL page, the player's inventory; everything else comes to
 * the screen in a {@link View} after every action and once a second. Opening it puts the player in the trader list;
 * closing it takes them off (unless a trade they just accepted is opening its own screen) and gives back what is still in
 * the lot slots. Opened from a Portable Banker ({@link #openPortable}) it has no post: the market pages only, behind the
 * account's market unlock, and the player is not in the trader list.
 */
public class TradePostMenu extends AbstractContainerMenu {
    public static final int W = 300, H = 216, H_SELL = 308, SELL_X = 15, SELL_Y = 57, INV_X = 70, INV_Y = 225, PAGE = 36, COLLECT_SENT = 45;
    public static final int SELL = 0, INV = 9, END = INV + 36;

    /** One lot as the screen shows it; {@code items} are pictures ({@link com.mrgregles.bsp_core.market.ItemPicture}), the full stacks come with the lot page. */
    public record LotView(String id, UUID seller, String sellerName, long price, long listedMs, long endsMs, List<ItemStack> items) {}

    public record TraderView(UUID id, String name, String server, boolean busy) {}

    public record RequestView(UUID from, String name, String server, int seconds) {}

    /**
     * Everything the screen shows; {@code message} is a lang key ("" none) with its arguments. {@code lots} is one page of
     * {@link #PAGE} of the {@code matched} lots ({@code page} of {@code pages}); {@code collect} the first {@link #COLLECT_SENT}
     * pictures of the {@code collectCount} stacks in the Collect box.
     */
    public record View(long balance, long now, int taxPercent, int maxStacks, int maxLots, int days, int myLots, int totalLots,
                       List<LotView> lots, int page, int pages, int matched, List<ItemStack> collect, int collectCount, List<TraderView> traders, List<RequestView> toMe,
                       String requestTo, int requestSeconds, String message, List<String> args) {
        public static final View EMPTY = new View(0, 0, 5, 9, 10, 7, 0, 0, List.of(), 0, 1, 0, List.of(), 0, List.of(), List.of(), "", 0, "", List.of());

        /** What decides whether a player's screen needs this view again: everything but the clock and the message. */
        public String key() {
            StringBuilder b = new StringBuilder().append(balance).append('|').append(taxPercent).append('|').append(maxStacks).append('|').append(maxLots)
                    .append('|').append(days).append('|').append(myLots).append('|').append(totalLots).append('|').append(page).append('/').append(pages)
                    .append('/').append(matched).append('|');
            lots.forEach(l -> b.append(l.id()).append(','));
            b.append('|').append(collectCount).append(':');
            collect.forEach(s -> b.append(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem())).append('x').append(s.getCount()).append(','));
            b.append('|');
            traders.forEach(t -> b.append(t.id()).append(t.busy() ? '+' : '-').append(t.server()).append(','));
            b.append('|');
            toMe.forEach(r -> b.append(r.from()).append('@').append(r.seconds()).append(','));
            return b.append('|').append(requestTo).append('@').append(requestSeconds).toString();
        }
    }

    /** The Trade Post it was opened at; null on a Portable Banker. */
    @Nullable
    public final GlobalPos post;
    public final TradePostBlock.Tab openedOn;
    private final SimpleContainer sell = new SimpleContainer(9);
    /** Server: what the MARKET page asks for, and the key of the last view sent ({@link View#key}). */
    String search = "";
    int sort, lotPage;
    private String sentKey = "";
    /** Server: the actions this screen may send (eight at once, then about eight a second). */
    private final RateLimit limit = new RateLimit(8, 8);
    /** Client: the page shown (the lot and inventory slots only show on SELL), and the last view received. */
    public TradePostBlock.Tab page;
    public View view = View.EMPTY;
    public int viewSerial;

    public TradePostMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, buf.readBoolean() ? buf.readGlobalPos() : null, buf.readEnum(TradePostBlock.Tab.class));
    }

    public TradePostMenu(int id, Inventory inv, @Nullable GlobalPos post, TradePostBlock.Tab tab) {
        super(ModMenus.TRADE_POST.get(), id);
        this.post = post;
        this.openedOn = tab;
        this.page = tab;
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(sell, i, SELL_X + (i % 3) * 18, SELL_Y + (i / 3) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return !Market.refused(stack);
                }

                @Override
                public boolean isActive() {
                    return page == TradePostBlock.Tab.SELL;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18) {
                    @Override
                    public boolean isActive() {
                        return page == TradePostBlock.Tab.SELL;
                    }
                });
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, INV_X + col * 18, INV_Y + 58) {
                @Override
                public boolean isActive() {
                    return page == TradePostBlock.Tab.SELL;
                }
            });
        }
    }

    public SimpleContainer lotSlots() {
        return sell;
    }

    // ------------------------------------------------------------------ opening and closing

    /** Opens the screen at a Trade Post on {@code tab}; the player joins the trader list. */
    public static void open(ServerPlayer player, TradePostBlock.Opening o) {
        NetworkHooks.openScreen(player, new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("block.bsp_core.trade_post");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new TradePostMenu(id, inv, o.post(), o.tab());
            }
        }, buf -> {
            buf.writeBoolean(true);
            buf.writeGlobalPos(o.post());
            buf.writeEnum(o.tab());
        });
        Trades.join(player, o.post());
        Bank.refresh(player.server, player.getUUID());
        sync(player, "", List.of());
    }

    /**
     * Opens the market from a Portable Banker: no post, so no traders band and no trades, and the player does not join the
     * trader list. Refused (false, nothing opens) while the account has not bought the market unlock.
     */
    public static boolean openPortable(ServerPlayer player) {
        if (!Bank.account(player.server, player.getUUID()).remoteMarket) {
            return false;
        }
        NetworkHooks.openScreen(player, new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("gui.bsp_core.trade_post.portable");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new TradePostMenu(id, inv, null, TradePostBlock.Tab.MARKET);
            }
        }, buf -> {
            buf.writeBoolean(false);
            buf.writeEnum(TradePostBlock.Tab.MARKET);
        });
        Bank.refresh(player.server, player.getUUID());
        sync(player, "", List.of());
        return true;
    }

    public boolean portable() {
        return post == null;
    }

    /** A Portable Banker's menu whose account no longer has the market (an admin reset): every action is refused. */
    public boolean locked(MinecraftServer server, UUID player) {
        return portable() && !Bank.account(server, player).remoteMarket;
    }

    @Override
    public boolean stillValid(Player player) {
        return post == null || post.dimension() == player.level().dimension()
                && player.distanceToSqr(post.pos().getX() + 0.5, post.pos().getY() + 0.5, post.pos().getZ() + 0.5) <= Trades.REACH * Trades.REACH;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player.level().isClientSide) {
            return;
        }
        giveBack(player, takeLot());
        // off the trader list unless a trade of theirs is opening its own screen (a request accepted and still starting is not one)
        if (post != null && !Trades.inTrade(player.getUUID())) {
            Trades.leave(player, "closed");
        }
    }

    /**
     * What was in the lot places, back to the player: into the inventory while they are here and alive, else (logged out,
     * dead, an old player object after a respawn) into their Collect box, where it can never be lost with an unsaved inventory.
     */
    public static void giveBack(Player player, List<ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }
        boolean here = player.isAlive() && !player.isRemoved() && !(player instanceof ServerPlayer sp && sp.hasDisconnected());
        MinecraftServer server = player.getServer();
        List<ItemStack> left = here ? Market.intoInventory(player, items) : items;
        if (!left.isEmpty()) {
            if (server != null) {
                Market.store(server).addCollect(server, player.getUUID(), left);
            } else {
                left.forEach(x -> player.drop(x, false));
            }
        }
    }

    /** Logout (before the player is saved) or death (before the drops): the lot places go back now, not when the screen is torn down later. */
    public static void emptyLotPlaces(Player player) {
        if (player.containerMenu instanceof TradePostMenu m) {
            List<ItemStack> lot = m.takeLot();
            if (!lot.isEmpty()) {
                List<ItemStack> left = Market.intoInventory(player, lot);
                if (!left.isEmpty() && player.getServer() != null) {
                    Market.store(player.getServer()).addCollect(player.getServer(), player.getUUID(), left);
                }
            }
        }
    }

    /** Empties the lot slots and returns what was in them. */
    List<ItemStack> takeLot() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < sell.getContainerSize(); i++) {
            if (!sell.getItem(i).isEmpty()) {
                out.add(sell.getItem(i).copy());
                sell.setItem(i, ItemStack.EMPTY);
            }
        }
        return out;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < INV) {
            if (!moveItemStackTo(stack, INV, END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (Market.refused(stack) || !moveItemStackTo(stack, SELL, INV, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    // ------------------------------------------------------------------ the server's side of the keys

    /** Lists what is in the lot slots for {@code price}; on a refusal the stacks go back into the slots. {@code then} gets the result. */
    public void list(MinecraftServer server, Player player, long price, Consumer<MarketResult> then) {
        List<ItemStack> stacks = takeLot();
        Market.list(server, player.getUUID(), player.getGameProfile().getName(), stacks, price, r -> {
            if (!r.ok()) {
                // a refusal can come after the screen closed (the network answered late): then not into these places, which are gone
                boolean open = player.containerMenu == this && player.isAlive() && !(player instanceof ServerPlayer sp && sp.hasDisconnected());
                List<ItemStack> back = new ArrayList<>();
                for (ItemStack s : r.items()) {
                    ItemStack left = open ? sell.addItem(s) : s;
                    if (!left.isEmpty()) {
                        back.add(left);
                    }
                }
                giveBack(player, back);
            }
            then.accept(r);
        });
    }

    /** The view for a player at a Trade Post, on the first page: their balance, the market as they search and sort it, their box, the traders, requests. */
    public static View view(MinecraftServer server, UUID player, String search, int sort, String message, List<String> args) {
        return view(server, player, search, sort, 0, message, args, true);
    }

    /** As above; on a Portable Banker ({@code atPost} false) the traders and requests are left out. */
    public static View view(MinecraftServer server, UUID player, String search, int sort, String message, List<String> args, boolean atPost) {
        return view(server, player, search, sort, 0, message, args, atPost);
    }

    /**
     * The view on page {@code page} (from 0; past the last page, the last). Lots past their end are left out (the sweep
     * closes them within 30 s); the stacks are pictures, so the view stays small whatever the lots hold.
     */
    public static View view(MinecraftServer server, UUID player, String search, int sort, int page, String message, List<String> args, boolean atPost) {
        long now = Market.now();
        List<MarketLot> all = Market.open(server);
        String q = search.trim().toLowerCase(Locale.ROOT);
        List<MarketLot> found = new ArrayList<>();
        int mine = 0, running = 0;
        for (MarketLot l : all) {
            if (l.seller.equals(player)) {
                mine++;
            }
            if (l.endedBy(now)) {
                continue;
            }
            running++;
            if (q.isEmpty() || l.matches(q)) {
                found.add(l);
            }
        }
        if (sort == 1) {
            found.sort(Comparator.comparingLong(l -> l.price));
        } else if (sort == 2) {
            found.sort(Comparator.comparingLong((MarketLot l) -> l.price).reversed());
        }
        int pages = Math.max(1, (found.size() + PAGE - 1) / PAGE), p = Math.max(0, Math.min(page, pages - 1));
        List<LotView> lots = new ArrayList<>();
        for (MarketLot l : found.subList(p * PAGE, Math.min(found.size(), (p + 1) * PAGE))) {
            lots.add(new LotView(l.id, l.seller, l.sellerName, l.price, l.listedMs, l.endsMs, l.pictures()));
        }
        List<ItemStack> box = Market.collectBox(server, player);
        List<ItemStack> collect = new ArrayList<>();
        for (ItemStack s : box.subList(0, Math.min(COLLECT_SENT, box.size()))) {
            collect.add(com.mrgregles.bsp_core.market.ItemPicture.of(s));
        }
        List<TraderView> traders = new ArrayList<>();
        List<RequestView> toMe = new ArrayList<>();
        long tnow = Trades.now();
        TradeHub.Request mineOut = null;
        if (atPost) {
            Trades.traders(player).forEach(t -> traders.add(new TraderView(t.id(), t.name(), t.server(), t.busy())));
            Trades.requestsTo(player).forEach(r -> toMe.add(new RequestView(r.from(), r.fromName(), r.fromServer(), (int) Math.max(0, (r.expires() - tnow) / 1000))));
            mineOut = Trades.requestFrom(player).orElse(null);
        }
        TradeHub.Request out = mineOut;
        String to = out == null ? "" : Trades.traders(player).stream().filter(t -> t.id().equals(out.to())).map(TradeHub.Trader::name).findFirst().orElse("?");
        return new View(Bank.pieces(server, player), now, BSPConfig.getOr(BSPConfig.MARKET_TAX_PERCENT, 5), Market.maxStacks(), Market.maxLots(), Market.listingDays(),
                mine, running, lots, p, pages, found.size(), collect, box.size(), traders, toMe,
                to, out == null ? 0 : (int) Math.max(0, (out.expires() - tnow) / 1000), message, args);
    }

    /** Sends a player with this screen open their view, with a message; only when something on it changed (or there is a message). True when sent. */
    public static boolean sync(ServerPlayer player, String message, List<String> args) {
        if (!(player.containerMenu instanceof TradePostMenu m)) {
            return false;
        }
        View v = view(player.server, player.getUUID(), m.search, m.sort, m.lotPage, message, args, !m.portable());
        String key = v.key();
        if (message.isEmpty() && key.equals(m.sentKey)) {
            return false;
        }
        TradePostDataPacket packet = new TradePostDataPacket(v);
        if (!BSPNetwork.sendChecked(player, packet, TradePostDataPacket::encode, "a Trade Post view")) {
            return false;
        }
        m.sentKey = key;
        m.lotPage = v.page();
        return true;
    }

    /** The screen asks for another search, sort or page; the next {@link #sync} sends it. */
    public void setView(String search, int sort, int page) {
        this.search = search.length() > 40 ? search.substring(0, 40) : search;
        this.sort = Math.floorMod(sort, 3);
        this.lotPage = Math.max(0, page);
    }

    public void setView(String search, int sort) {
        setView(search, sort, 0);
    }

    /** Whether the player may send another action now: a few at once, then about eight a second; the rest are dropped. */
    public boolean allow(long nowMs) {
        return limit.allow(nowMs);
    }
}
