package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.api.BspMarket;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.market.BuyOrder;
import com.mrgregles.bsp_core.market.BuyOrders;
import com.mrgregles.bsp_core.market.LocalMarket;
import com.mrgregles.bsp_core.market.LocalOrders;
import com.mrgregles.bsp_core.market.Market;
import com.mrgregles.bsp_core.market.MarketLedger;
import com.mrgregles.bsp_core.market.MarketLot;
import com.mrgregles.bsp_core.market.MarketResult;
import com.mrgregles.bsp_core.market.MarketStore;
import com.mrgregles.bsp_core.market.OrderLedger;
import com.mrgregles.bsp_core.market.OrderStore;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * GameTests for {@link BspMarket} (BSP-Server Utilities request SU-BC1). Batch {@code market_api}. Each test gives the market
 * and the orders blank stores of their own and a fixed clock, and puts the world's back before the tick ends.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class MarketApiGameTests {
    private static final String EMPTY = "empty";
    private static final long DAY = 24L * 60 * 60 * 1000;

    private static UUID account(MinecraftServer server, String name, long pieces) {
        UUID id = UUID.randomUUID();
        Bank.ensure(server, id, name, g -> {});
        Bank.set(server, id, pieces, "gametest", r -> {});
        return id;
    }

    @GameTest(template = EMPTY, batch = "market_api")
    public void the_api_lists_open_lots_and_orders_with_their_items_intact_and_leaves_out_ended_ones(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        MarketLedger lots = new MarketLedger();
        OrderLedger orders = new OrderLedger();
        MarketStore oldMarket = Market.useStore(new LocalMarket(lots));
        OrderStore oldOrders = BuyOrders.useStore(new LocalOrders(orders));
        long now = System.currentTimeMillis();
        Market.useClock(() -> now);
        try {
            UUID seller = account(server, "Sella", 10_000), buyer = account(server, "Bo", 10_000);
            // a lot of two stacks, one enchanted and named: listed the usual way
            ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
            sword.enchant(Enchantments.SHARPNESS, 5);
            sword.setHoverName(Component.literal("Edge"));
            sword.setDamageValue(17);
            ItemStack diamonds = new ItemStack(Items.DIAMOND, 64);
            MarketResult[] r = new MarketResult[1];
            Market.list(server, seller, "Sella", List.of(sword.copy(), diamonds.copy()), 2_500, x -> r[0] = x);
            h.assertTrue(r[0] != null && r[0].ok(), "the lot is listed: " + r[0]);
            // a lot past its end, not yet closed by the sweep: the market still holds it, the api leaves it out
            lots.put(new MarketLot("ended", seller, "Sella", List.of(new ItemStack(Items.DIRT, 3)), 10, now - 8 * DAY, now - 1, Bank.serverId()));
            h.assertTrue(Market.open(server).size() == 2, "the market holds both lots");

            List<BspMarket.Lot> got = BspMarket.openLots(server);
            h.assertTrue(got.size() == 1, "one open lot, the ended one left out: " + got.size());
            BspMarket.Lot lot = got.get(0);
            h.assertTrue(lot.id().equals(r[0].lot().id) && lot.seller().equals(seller) && lot.sellerName().equals("Sella") && lot.price() == 2_500
                    && lot.listedMs() == now && lot.endsMs() == now + Market.listingDays() * DAY && lot.serverId().equals(Bank.serverId()),
                    "the lot's fields: " + lot);
            h.assertTrue(lot.items().size() == 2 && ItemStack.matches(lot.items().get(0), sword) && ItemStack.matches(lot.items().get(1), diamonds),
                    "the items come back intact: enchantment, name, damage and count: " + lot.items());
            // the items are copies: changing them changes nothing in the market
            lot.items().get(1).setCount(1);
            lot.items().get(0).setHoverName(Component.literal("Mine"));
            BspMarket.Lot again = BspMarket.openLots(server).get(0);
            h.assertTrue(ItemStack.matches(again.items().get(0), sword) && again.items().get(1).getCount() == 64, "the market's items are untouched");
            boolean refused = false;
            try {
                lot.items().add(ItemStack.EMPTY);
            } catch (UnsupportedOperationException e) {
                refused = true;
            }
            h.assertTrue(refused, "the list of items cannot be changed");

            // an order placed the usual way, one ended, one with nothing left to send
            OrderStore.Answer[] a = new OrderStore.Answer[1];
            BuyOrders.place(server, buyer, "Bo", Items.CACTUS, 100, 3, x -> a[0] = x);
            h.assertTrue(a[0] != null && a[0].ok(), "the order is placed: " + a[0]);
            orders.put(new BuyOrder("ended", buyer, "Bo", new ItemStack(Items.SAND), 5, 0, 1, now - 8 * DAY, now - 1, Bank.serverId()));
            orders.put(new BuyOrder("filled", buyer, "Bo", new ItemStack(Items.GRAVEL), 5, 5, 1, now - DAY, now + DAY, Bank.serverId()));
            List<BspMarket.Order> os = BspMarket.openOrders(server);
            h.assertTrue(os.size() == 1, "one open order, the ended and the filled ones left out: " + os.size());
            BspMarket.Order o = os.get(0);
            h.assertTrue(o.id().equals(a[0].order().id) && o.buyer().equals(buyer) && o.buyerName().equals("Bo") && o.item().is(Items.CACTUS) && o.item().getCount() == 1
                    && o.wanted() == 100 && o.filled() == 0 && o.toGo() == 100 && o.each() == 3 && o.price() == 300 && o.placedMs() == now
                    && o.endsMs() == now + Market.listingDays() * DAY && o.serverId().equals(Bank.serverId()), "the order's fields: " + o);
            o.item().setCount(40);
            h.assertTrue(BspMarket.openOrders(server).get(0).item().getCount() == 1, "the order's item is a copy");

            // on a single server the copy is always current, and a refresh answers at once
            h.assertTrue(BspMarket.lotsReadMs(server) == now && BspMarket.ordersReadMs(server) == now, "read now on a single server");
            boolean[] done = {false};
            BspMarket.refresh(server, () -> done[0] = true);
            h.assertTrue(done[0], "a refresh on a single server answers at once");
        } finally {
            Market.useClock(null);
            Market.useStore(oldMarket);
            BuyOrders.useStore(oldOrders);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "market_api")
    public void the_api_shows_a_lot_until_its_last_millisecond(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        MarketLedger lots = new MarketLedger();
        MarketStore oldMarket = Market.useStore(new LocalMarket(lots));
        OrderStore oldOrders = BuyOrders.useStore(new LocalOrders(new OrderLedger()));
        long now = System.currentTimeMillis();
        try {
            UUID seller = UUID.randomUUID();
            lots.put(new MarketLot("edge", seller, "Sella", List.of(new ItemStack(Items.DIRT)), 10, now - DAY, now + 1, "local"));
            Market.useClock(() -> now);
            h.assertTrue(BspMarket.openLots(server).size() == 1, "a lot one millisecond from its end is listed");
            Market.useClock(() -> now + 1);
            h.assertTrue(BspMarket.openLots(server).isEmpty(), "at its end it is left out, as the sweep closes it");
            h.assertTrue(BspMarket.openOrders(server).isEmpty(), "no orders, an empty list");
        } finally {
            Market.useClock(null);
            Market.useStore(oldMarket);
            BuyOrders.useStore(oldOrders);
        }
        h.succeed();
    }
}
