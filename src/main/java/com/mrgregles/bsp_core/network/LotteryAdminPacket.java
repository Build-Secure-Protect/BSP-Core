package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.lottery.LotteryAdminView;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → client: the lottery's admin page (operators only), and whether to open it by itself. */
public record LotteryAdminPacket(LotteryAdminView view) {
    public static void encode(LotteryAdminPacket msg, FriendlyByteBuf buf) {
        msg.view.encode(buf);
    }

    public static LotteryAdminPacket decode(FriendlyByteBuf buf) {
        return new LotteryAdminPacket(LotteryAdminView.decode(buf));
    }

    public static void handle(LotteryAdminPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.mrgregles.bsp_core.client.LotteryAdminPage.receive(msg.view)));
        ctx.get().setPacketHandled(true);
    }
}
