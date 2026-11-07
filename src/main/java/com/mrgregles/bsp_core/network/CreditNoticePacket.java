package com.mrgregles.bsp_core.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → client: a store credit ticket's clock started (its first purchase) or it ran out of time (Store Credit task 9). The
 * client shows the full-screen stamp ({@code client.CreditNoticeHud}): the kind's name and look, the pieces (left to spend, or
 * unspent), and the end.
 */
public record CreditNoticePacket(boolean end, String name, String colour, String mark, long pieces, long endsMs) {
    public static void encode(CreditNoticePacket m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.end);
        buf.writeUtf(m.name, 64);
        buf.writeUtf(m.colour, 16);
        buf.writeUtf(m.mark, 1);
        buf.writeVarLong(Math.max(0, m.pieces));
        buf.writeLong(m.endsMs);
    }

    public static CreditNoticePacket decode(FriendlyByteBuf buf) {
        return new CreditNoticePacket(buf.readBoolean(), buf.readUtf(64), buf.readUtf(16), buf.readUtf(1), buf.readVarLong(), buf.readLong());
    }

    public static void handle(CreditNoticePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.CreditNoticeHud.show(msg)));
        ctx.get().setPacketHandled(true);
    }
}
