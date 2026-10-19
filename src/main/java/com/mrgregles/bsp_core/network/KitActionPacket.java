package com.mrgregles.bsp_core.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: open or refresh the kit browser, redeem a kit, or take one that is waiting. The server answers with the view. */
public record KitActionPacket(Action action, String kitId) {
    public enum Action { OPEN, REFRESH, REDEEM, TAKE }

    public static KitActionPacket of(Action action) {
        return new KitActionPacket(action, "");
    }

    public static void encode(KitActionPacket m, FriendlyByteBuf buf) {
        buf.writeEnum(m.action);
        buf.writeUtf(m.kitId, 32);
    }

    public static KitActionPacket decode(FriendlyByteBuf buf) {
        return new KitActionPacket(buf.readEnum(Action.class), buf.readUtf(32));
    }

    public static void handle(KitActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            com.mrgregles.bsp_core.kit.KitActions.apply(player, msg);
        }
        ctx.get().setPacketHandled(true);
    }
}
