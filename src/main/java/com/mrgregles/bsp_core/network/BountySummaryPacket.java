package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.bounty.BountySummary;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server → client: every running bounty (no notes), with the server's clock so the client can count down. */
public record BountySummaryPacket(long now, List<BountySummary.Entry> entries) {
    public static void encode(BountySummaryPacket msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.now);
        buf.writeVarInt(msg.entries.size());
        for (BountySummary.Entry e : msg.entries) {
            buf.writeUtf(BSPNetwork.cut(e.id(), 16), 16);
            buf.writeUUID(e.target());
            buf.writeUtf(BSPNetwork.cut(e.targetName(), 16), 16);
            buf.writeUtf(BSPNetwork.cut(e.placerName(), 16), 16);
            buf.writeVarInt(e.coinTier() + 1);
            buf.writeVarLong(e.amount());
            buf.writeVarLong(e.bonus());
            buf.writeLong(e.bonusEndsMs());
            buf.writeLong(e.placedMs());
            buf.writeLong(e.endsMs());
            buf.writeVarInt(e.hunters());
        }
    }

    public static BountySummaryPacket decode(FriendlyByteBuf buf) {
        long now = buf.readLong();
        int n = Math.min(buf.readVarInt(), BountySummary.CAP);
        List<BountySummary.Entry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            entries.add(new BountySummary.Entry(buf.readUtf(16), buf.readUUID(), buf.readUtf(16), buf.readUtf(16), buf.readVarInt() - 1, buf.readVarLong(),
                    buf.readVarLong(), buf.readLong(), buf.readLong(), buf.readLong(), buf.readVarInt()));
        }
        return new BountySummaryPacket(now, entries);
    }

    public static void handle(BountySummaryPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.ClientBounties.accept(msg.now, msg.entries));
        ctx.get().setPacketHandled(true);
    }
}
