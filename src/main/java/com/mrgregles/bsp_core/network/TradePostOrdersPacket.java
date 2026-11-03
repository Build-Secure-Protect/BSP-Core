package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.trade.TradePostMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server → client: the buy orders the open Trade Post screen shows on the MARKET page's WANTED side (one page of them). */
public record TradePostOrdersPacket(TradePostMenu.OrdersView view) {
    public static void encode(TradePostOrdersPacket msg, FriendlyByteBuf buf) {
        TradePostMenu.OrdersView v = msg.view;
        buf.writeVarInt(v.orders().size());
        for (TradePostMenu.OrderView o : v.orders()) {
            buf.writeUtf(o.id(), 16);
            buf.writeUUID(o.buyer());
            buf.writeUtf(BSPNetwork.cut(o.buyerName(), 32), 32);
            buf.writeItem(o.item());
            buf.writeVarInt(o.wanted());
            buf.writeVarInt(o.filled());
            buf.writeLong(o.each());
            buf.writeLong(o.placedMs());
            buf.writeLong(o.endsMs());
            buf.writeVarInt(o.carried());
        }
        buf.writeVarInt(v.page());
        buf.writeVarInt(v.pages());
        buf.writeVarInt(v.matched());
        buf.writeVarInt(v.mine());
        buf.writeVarInt(v.maxOrders());
        buf.writeVarInt(v.total());
    }

    public static TradePostOrdersPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), TradePostMenu.PAGE);
        List<TradePostMenu.OrderView> orders = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            orders.add(new TradePostMenu.OrderView(buf.readUtf(16), buf.readUUID(), buf.readUtf(32), buf.readItem(), buf.readVarInt(), buf.readVarInt(), buf.readLong(),
                    buf.readLong(), buf.readLong(), buf.readVarInt()));
        }
        return new TradePostOrdersPacket(new TradePostMenu.OrdersView(orders, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
    }

    public static void handle(TradePostOrdersPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.mrgregles.bsp_core.client.TradePostScreen.receiveOrders(msg.view)));
        ctx.get().setPacketHandled(true);
    }
}
