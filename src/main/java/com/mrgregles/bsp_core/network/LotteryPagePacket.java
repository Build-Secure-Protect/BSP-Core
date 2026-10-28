package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.lottery.LotteryPageView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: the Trade Post's LOTTERY page. */
public record LotteryPagePacket(LotteryPageView view) {
    public static void encode(LotteryPagePacket msg, FriendlyByteBuf buf) {
        msg.view.encode(buf);
    }

    public static LotteryPagePacket decode(FriendlyByteBuf buf) {
        return new LotteryPagePacket(LotteryPageView.decode(buf));
    }

    public static void handle(LotteryPagePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.mrgregles.bsp_core.client.LotteryPlayerPage.receive(msg.view)));
        ctx.get().setPacketHandled(true);
    }
}
