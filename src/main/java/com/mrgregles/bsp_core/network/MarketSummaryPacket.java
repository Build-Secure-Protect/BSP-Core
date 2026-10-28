package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.market.MarketSummary;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server → client: the newest market lots for the Market Board, with the server's clock so the client can tell their ages. */
public record MarketSummaryPacket(long now, List<MarketSummary.Entry> entries) {
    public static void encode(MarketSummaryPacket msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.now);
        buf.writeVarInt(msg.entries.size());
        for (MarketSummary.Entry e : msg.entries) {
            buf.writeItem(e.first());
            buf.writeVarInt(e.stacks());
            buf.writeLong(e.price());
            buf.writeLong(e.listedMs());
            buf.writeUtf(e.seller(), 32);
        }
    }

    public static MarketSummaryPacket decode(FriendlyByteBuf buf) {
        long now = buf.readLong();
        int n = Math.min(buf.readVarInt(), MarketSummary.CAP);
        List<MarketSummary.Entry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            entries.add(new MarketSummary.Entry(buf.readItem(), buf.readVarInt(), buf.readLong(), buf.readLong(), buf.readUtf(32)));
        }
        return new MarketSummaryPacket(now, entries);
    }

    public static void handle(MarketSummaryPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.ClientMarket.accept(msg.now, msg.entries));
        ctx.get().setPacketHandled(true);
    }
}
