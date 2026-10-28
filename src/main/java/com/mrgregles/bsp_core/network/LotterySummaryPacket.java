package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.lottery.LotterySummary;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: the lottery for the Lottery board on Score Screens. */
public record LotterySummaryPacket(LotterySummary.Summary summary) {
    public static void encode(LotterySummaryPacket msg, FriendlyByteBuf buf) {
        msg.summary.encode(buf);
    }

    public static LotterySummaryPacket decode(FriendlyByteBuf buf) {
        return new LotterySummaryPacket(LotterySummary.Summary.decode(buf));
    }

    public static void handle(LotterySummaryPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.ClientLottery.accept(msg.summary));
        ctx.get().setPacketHandled(true);
    }
}
