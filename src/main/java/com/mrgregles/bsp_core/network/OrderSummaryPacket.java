package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.market.OrderSummary;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server → client: the open buy orders for the Wanted board, best pay first. */
public record OrderSummaryPacket(List<OrderSummary.Entry> entries) {
    public static void encode(OrderSummaryPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entries.size());
        for (OrderSummary.Entry e : msg.entries) {
            buf.writeItem(e.item());
            buf.writeVarInt(e.toGo());
            buf.writeVarInt(e.wanted());
            buf.writeLong(e.each());
            buf.writeUtf(BSPNetwork.cut(e.buyer(), 32), 32);
        }
    }

    public static OrderSummaryPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), OrderSummary.CAP);
        List<OrderSummary.Entry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            entries.add(new OrderSummary.Entry(buf.readItem(), buf.readVarInt(), buf.readVarInt(), buf.readLong(), buf.readUtf(32)));
        }
        return new OrderSummaryPacket(entries);
    }

    public static void handle(OrderSummaryPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.ClientOrders.accept(msg.entries));
        ctx.get().setPacketHandled(true);
    }
}
