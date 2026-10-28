package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.lottery.LotteryPage;
import com.mrgregles.bsp_core.trade.TradePostMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: the LOTTERY page, showing on a Trade Post screen, asks for its view (about once a second). */
public record LotteryPageAskPacket() {
    public static void encode(LotteryPageAskPacket msg, FriendlyByteBuf buf) {
    }

    public static LotteryPageAskPacket decode(FriendlyByteBuf buf) {
        return new LotteryPageAskPacket();
    }

    public static void handle(LotteryPageAskPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && player.containerMenu instanceof TradePostMenu) {
            ctx.get().enqueueWork(() -> LotteryPage.asked(player));
        }
        ctx.get().setPacketHandled(true);
    }
}
