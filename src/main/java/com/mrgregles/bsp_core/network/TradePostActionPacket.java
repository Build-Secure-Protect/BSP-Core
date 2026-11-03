package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.market.BuyOrders;
import com.mrgregles.bsp_core.market.Market;
import com.mrgregles.bsp_core.trade.TradePostMenu;
import com.mrgregles.bsp_core.trade.Trades;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client → server: a key pressed on the Trade Post screen. Every one is answered with a {@link TradePostDataPacket}
 * carrying the fresh view and, for most, a message. From a Portable Banker (no post) the trade keys are refused, and
 * every key closes the screen when the account has lost its market unlock.
 */
public record TradePostActionPacket(Action action, String text, long amount, boolean flag) {
    /**
     * VIEW: {@code text} the search, {@code amount} sort + 3 x page, {@code flag} the buy orders (WANTED) rather than the lots.
     * DETAIL: {@code text} a lot id, answered with its full stacks. ORDER: {@code text} the item id and how many, as
     * "minecraft:cactus 64", {@code amount} the price each. FILL: {@code text} an order id, {@code amount} how many to send.
     * UNORDER: {@code text} an order id, cancelled.
     */
    public enum Action { VIEW, BUY, CANCEL, LIST, COLLECT, ASK, WITHDRAW, ANSWER, DETAIL, ORDER, FILL, UNORDER }

    public static void encode(TradePostActionPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeUtf(BSPNetwork.cut(msg.text, 160), 160); // an ORDER's item id and count; a modded id can be long
        buf.writeLong(msg.amount);
        buf.writeBoolean(msg.flag);
    }

    public static TradePostActionPacket decode(FriendlyByteBuf buf) {
        return new TradePostActionPacket(buf.readEnum(Action.class), buf.readUtf(160), buf.readLong(), buf.readBoolean());
    }

    public static void handle(TradePostActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && player.containerMenu instanceof TradePostMenu menu) {
            ctx.get().enqueueWork(() -> run(player, menu, msg));
        }
        ctx.get().setPacketHandled(true);
    }

    private static UUID uuid(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return new UUID(0, 0);
        }
    }

    /** Runs one key on the server thread; public for the GameTests. */
    public static void run(ServerPlayer player, TradePostMenu menu, TradePostActionPacket msg) {
        var server = player.server;
        String name = player.getGameProfile().getName();
        if (menu.locked(server, player.getUUID())) {
            closeLocked(player);
            return;
        }
        if (!menu.allow(System.currentTimeMillis())) {
            // too many at once: a search, sort or page still counts (the once-a-second refresh sends it), anything else is dropped
            if (msg.action == Action.VIEW) {
                menu.setView(msg.text, (int) Math.floorMod(msg.amount, 3L), (int) Math.max(0, msg.amount / 3), msg.flag);
            }
            return;
        }
        if (menu.portable() && (msg.action == Action.ASK || msg.action == Action.WITHDRAW || msg.action == Action.ANSWER)) {
            TradePostMenu.sync(player, "message.bsp_core.trade.needs_post", List.of());
            return;
        }
        switch (msg.action) {
            case VIEW -> {
                menu.setView(msg.text, (int) Math.floorMod(msg.amount, 3L), (int) Math.max(0, msg.amount / 3), msg.flag);
                TradePostMenu.sync(player, "", List.of());
            }
            case ORDER -> order(player, msg);
            case FILL -> BuyOrders.fill(server, player, msg.text, (int) Math.max(0, Math.min(Integer.MAX_VALUE, msg.amount)), a -> TradePostMenu.sync(player,
                    "message.bsp_core.order." + (a.ok() ? "sent" : a.key()), a.ok() ? List.of(a.sent() + " " + a.order().item().getHoverName().getString(), a.order().buyerName,
                            Bank.format(a.paid()), Bank.format(a.tax())) : List.of()));
            case UNORDER -> BuyOrders.cancel(server, player.getUUID(), msg.text, false, a -> TradePostMenu.sync(player,
                    "message.bsp_core.order." + (a.ok() ? "cancelled" : a.key()), a.ok() ? List.of(a.order().describe(), Bank.format(a.refund())) : List.of()));
            case DETAIL -> BSPNetwork.sendChecked(player, TradePostLotPacket.of(msg.text, Market.find(server, msg.text).orElse(null)),
                    TradePostLotPacket::encode, "a lot's stacks");
            case BUY -> Market.buy(server, player.getUUID(), name, msg.text, r -> TradePostMenu.sync(player,
                    "message.bsp_core.market." + (r.ok() ? "bought" : r.key()), r.ok() ? List.of(r.lot().describe(), Bank.format(r.lot().price + Market.tax(r.lot().price))) : List.of()));
            case CANCEL -> Market.cancel(server, player.getUUID(), msg.text, false, r -> TradePostMenu.sync(player,
                    "message.bsp_core.market." + (r.ok() ? "cancelled" : r.key()), r.ok() ? List.of(r.lot().describe()) : List.of()));
            case LIST -> menu.list(server, player, msg.amount, r -> TradePostMenu.sync(player,
                    "message.bsp_core.market." + (r.ok() ? "listed" : r.key()), r.ok() ? List.of(r.lot().describe(), Bank.format(Market.tax(r.lot().price))) : List.of(String.valueOf(Market.maxStacks()))));
            case COLLECT -> Market.collect(server, player, r -> TradePostMenu.sync(player,
                    "message.bsp_core.market." + (r.key().equals("ok") ? "collected" : r.key()), List.of(String.valueOf(r.count()))));
            case ASK -> {
                UUID to = uuid(msg.text);
                String key = Trades.ask(player, to);
                String who = Trades.traders(player.getUUID()).stream().filter(t -> t.id().equals(to)).map(t -> t.name()).findFirst().orElse("?");
                TradePostMenu.sync(player, "message.bsp_core.trade." + (key.equals("ok") ? "asking" : key), List.of(who));
            }
            case WITHDRAW -> {
                Trades.withdraw(player.getUUID());
                TradePostMenu.sync(player, "message.bsp_core.trade.withdrawn", List.of());
            }
            case ANSWER -> {
                UUID from = uuid(msg.text);
                String key = Trades.answer(player, from, msg.flag);
                TradePostMenu.sync(player, key.equals("ok") || key.equals("declined") ? "" : "message.bsp_core.trade." + key, List.of("?"));
            }
        }
    }

    /** ORDER: "item count" in the text, the price each in the amount. */
    private static void order(ServerPlayer player, TradePostActionPacket msg) {
        String[] parts = msg.text.trim().split(" ");
        net.minecraft.resources.ResourceLocation key = parts.length == 2 ? net.minecraft.resources.ResourceLocation.tryParse(parts[0]) : null;
        net.minecraft.world.item.Item item = key == null ? null : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(key);
        int count;
        try {
            count = parts.length == 2 ? Integer.parseInt(parts[1]) : 0;
        } catch (NumberFormatException e) {
            count = 0;
        }
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            TradePostMenu.sync(player, "message.bsp_core.order.refused", List.of());
            return;
        }
        int n = count;
        BuyOrders.place(player.server, player.getUUID(), player.getGameProfile().getName(), item, n, msg.amount, a -> TradePostMenu.sync(player,
                "message.bsp_core.order." + (a.ok() ? "placed" : a.key()), a.ok() ? List.of(a.order().describe(), Bank.format(a.order().each), Bank.format(a.order().held()))
                        : List.of(String.valueOf(BuyOrders.maxOrders()), String.valueOf(BuyOrders.maxCount(BuyOrders.plain(item))))));
    }

    /** A Portable Banker's market whose unlock was taken away: the screen closes (the lot slots go back) with a line saying why. */
    public static void closeLocked(ServerPlayer player) {
        player.closeContainer();
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.bsp_core.market.portable_locked"), false);
    }
}
