package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.bank.Bank;
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
    /** VIEW: {@code text} the search, {@code amount} sort + 3 x page. DETAIL: {@code text} a lot id, answered with its full stacks. */
    public enum Action { VIEW, BUY, CANCEL, LIST, COLLECT, ASK, WITHDRAW, ANSWER, DETAIL }

    public static void encode(TradePostActionPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeUtf(msg.text, 64);
        buf.writeLong(msg.amount);
        buf.writeBoolean(msg.flag);
    }

    public static TradePostActionPacket decode(FriendlyByteBuf buf) {
        return new TradePostActionPacket(buf.readEnum(Action.class), buf.readUtf(64), buf.readLong(), buf.readBoolean());
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
                menu.setView(msg.text, (int) Math.floorMod(msg.amount, 3L), (int) Math.max(0, msg.amount / 3));
            }
            return;
        }
        if (menu.portable() && (msg.action == Action.ASK || msg.action == Action.WITHDRAW || msg.action == Action.ANSWER)) {
            TradePostMenu.sync(player, "message.bsp_core.trade.needs_post", List.of());
            return;
        }
        switch (msg.action) {
            case VIEW -> {
                menu.setView(msg.text, (int) Math.floorMod(msg.amount, 3L), (int) Math.max(0, msg.amount / 3));
                TradePostMenu.sync(player, "", List.of());
            }
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

    /** A Portable Banker's market whose unlock was taken away: the screen closes (the lot slots go back) with a line saying why. */
    public static void closeLocked(ServerPlayer player) {
        player.closeContainer();
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.bsp_core.market.portable_locked"), false);
    }
}
