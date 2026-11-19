package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.bounty.BountyScreens;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: an action on the Bounty Board screen (accept, abandon, cancel, claim the Collect box, read again), and the page shown. */
public record BountyActionPacket(int action, String id, int page) {
    public static void encode(BountyActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.action);
        buf.writeUtf(BSPNetwork.cut(msg.id, 16), 16);
        buf.writeVarInt(msg.page);
    }

    public static BountyActionPacket decode(FriendlyByteBuf buf) {
        return new BountyActionPacket(buf.readVarInt(), buf.readUtf(16), buf.readVarInt());
    }

    public static void handle(BountyActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        BountyScreens.Action[] all = BountyScreens.Action.values();
        if (player != null && msg.action >= 0 && msg.action < all.length) {
            BountyScreens.act(player, all[msg.action], msg.id, Math.max(0, Math.min(3, msg.page)));
        }
        ctx.get().setPacketHandled(true);
    }
}
