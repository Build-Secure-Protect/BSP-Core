package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.trade.Trade;
import com.mrgregles.bsp_core.trade.TradeMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** Client → server: a key on the trade screen (the pieces offered, READY, un-ready, a chat line). Answered with a {@link TradeStatePacket}. */
public record TradeActionPacket(Action action, long amount, String text) {
    public enum Action { PIECES, READY, UNREADY, SAY }

    public static void encode(TradeActionPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeLong(msg.amount);
        buf.writeUtf(msg.text, 256);
    }

    public static TradeActionPacket decode(FriendlyByteBuf buf) {
        return new TradeActionPacket(buf.readEnum(Action.class), buf.readLong(), buf.readUtf(256));
    }

    public static void handle(TradeActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && player.containerMenu instanceof TradeMenu menu && menu.trade() != null) {
            ctx.get().enqueueWork(() -> {
                long now = System.currentTimeMillis();
                if (!menu.actionLimit.allow(now)) {
                    return; // more than a screen sends: dropped
                }
                if (msg.action == Action.SAY && !menu.chatLimit.allow(now)) {
                    TradeMenu.sync(player, "message.bsp_core.trade.too_fast", List.of());
                    return;
                }
                run(player, menu.trade(), msg);
            });
        }
        ctx.get().setPacketHandled(true);
    }

    static void run(ServerPlayer player, Trade trade, TradeActionPacket msg) {
        var id = player.getUUID();
        String key = switch (msg.action) {
            case PIECES -> trade.setPieces(id, msg.amount);
            case READY -> trade.ready(id);
            case UNREADY -> trade.unready(id);
            case SAY -> {
                trade.say(id, msg.text);
                yield "ok";
            }
        };
        TradeMenu.sync(player, key.equals("ok") ? "" : "message.bsp_core.trade." + key, List.of());
    }
}
