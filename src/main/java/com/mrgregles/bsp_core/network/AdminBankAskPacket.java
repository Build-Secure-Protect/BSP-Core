package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.admin.AdminBank;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client → server: the admin panel's Bank &amp; Market page asks for a player's page ({@link Action#OPEN}), or refunds a log row, or
 * takes one of the player's lots or requests off the market. The server checks who sent it ({@link AdminBank#act}) and answers
 * with an {@link AdminBankDataPacket}.
 *
 * @param rowId  the log row (REFUND)
 * @param pieces how many of the row's pieces to refund (REFUND)
 * @param coins  how many of its coins (REFUND)
 * @param other  also reverse a payment on the other player (REFUND)
 * @param entry  the lot's or request's id (CANCEL_LOT, CANCEL_ORDER)
 */
public record AdminBankAskPacket(Action action, UUID player, long rowId, long pieces, int coins, boolean other, String entry) {
    public enum Action { OPEN, REFUND, CANCEL_LOT, CANCEL_ORDER }

    public static AdminBankAskPacket open(UUID player) {
        return new AdminBankAskPacket(Action.OPEN, player, 0, 0, 0, false, "");
    }

    public static void encode(AdminBankAskPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeUUID(msg.player);
        buf.writeLong(msg.rowId);
        buf.writeLong(msg.pieces);
        buf.writeVarInt(msg.coins);
        buf.writeBoolean(msg.other);
        buf.writeUtf(BSPNetwork.cut(msg.entry, 32), 32);
    }

    public static AdminBankAskPacket decode(FriendlyByteBuf buf) {
        return new AdminBankAskPacket(buf.readEnum(Action.class), buf.readUUID(), buf.readLong(), buf.readLong(), buf.readVarInt(), buf.readBoolean(), buf.readUtf(32));
    }

    public static void handle(AdminBankAskPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            ctx.get().enqueueWork(() -> AdminBank.act(player, msg));
        }
        ctx.get().setPacketHandled(true);
    }
}
