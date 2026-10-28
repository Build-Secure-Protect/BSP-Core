package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.lottery.LotteryAdmin;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: a key on the lottery's admin page ({@link LotteryAdmin.Action}); the server checks the sender is an operator. */
public record LotteryAdminActionPacket(LotteryAdmin.Action action, String text, long amount, boolean flag) {
    public static void encode(LotteryAdminActionPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeUtf(BSPNetwork.cut(msg.text, 64), 64);
        buf.writeLong(msg.amount);
        buf.writeBoolean(msg.flag);
    }

    public static LotteryAdminActionPacket decode(FriendlyByteBuf buf) {
        return new LotteryAdminActionPacket(buf.readEnum(LotteryAdmin.Action.class), buf.readUtf(64), buf.readLong(), buf.readBoolean());
    }

    public static void handle(LotteryAdminActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            ctx.get().enqueueWork(() -> LotteryAdmin.act(player, msg.action, msg.text, msg.amount, msg.flag));
        }
        ctx.get().setPacketHandled(true);
    }
}
