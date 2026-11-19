package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.admin.AdminBounty;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: the admin panel's BOUNTIES tab: read again, remove (with or without a refund), extend, blank the notes, or add a staff bounty. */
public record BountyAdminActionPacket(int action, String id, long addMs, String targetName, int coinTier, long amount, long bonus, long bonusWithinMs, long durationMs,
                                      String notes) {
    public static final int REFRESH = 0, REMOVE = 1, REMOVE_NO_REFUND = 2, EXTEND = 3, BLANK = 4, ADD = 5;

    public static BountyAdminActionPacket of(int action, String id, long addMs) {
        return new BountyAdminActionPacket(action, id, addMs, "", -1, 0, 0, 0, 0, "");
    }

    public static void encode(BountyAdminActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.action);
        buf.writeUtf(BSPNetwork.cut(msg.id, 16), 16);
        buf.writeVarLong(Math.max(0, msg.addMs));
        buf.writeUtf(BSPNetwork.cut(msg.targetName, 16), 16);
        buf.writeVarInt(msg.coinTier + 1);
        buf.writeVarLong(Math.max(0, msg.amount));
        buf.writeVarLong(Math.max(0, msg.bonus));
        buf.writeVarLong(Math.max(0, msg.bonusWithinMs));
        buf.writeVarLong(Math.max(0, msg.durationMs));
        buf.writeUtf(BSPNetwork.cut(msg.notes, 1000), 1000);
    }

    public static BountyAdminActionPacket decode(FriendlyByteBuf buf) {
        return new BountyAdminActionPacket(buf.readVarInt(), buf.readUtf(16), buf.readVarLong(), buf.readUtf(16), buf.readVarInt() - 1, buf.readVarLong(), buf.readVarLong(),
                buf.readVarLong(), buf.readVarLong(), buf.readUtf(1000));
    }

    public static void handle(BountyAdminActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            AdminBounty.handle(player, msg);
        }
        ctx.get().setPacketHandled(true);
    }
}
