package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.bounty.Bounties;
import com.mrgregles.bsp_core.bounty.BountyScreens;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client → server, from the Bounty Board's place page: a player search ({@code target} null, {@code targetName} the search), or a
 * bounty to place (everything filled in). The server checks everything again.
 */
public record BountyPlacePacket(boolean search, UUID target, String targetName, int coinTier, long amount, long bonus, long bonusWithinMs, long durationMs, String notes) {
    public static BountyPlacePacket search(String text) {
        return new BountyPlacePacket(true, new UUID(0, 0), text, -1, 0, 0, 0, 0, "");
    }

    public static void encode(BountyPlacePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.search);
        buf.writeUUID(msg.target);
        buf.writeUtf(BSPNetwork.cut(msg.targetName, 16), 16);
        buf.writeVarInt(msg.coinTier + 1);
        buf.writeVarLong(Math.max(0, msg.amount));
        buf.writeVarLong(Math.max(0, msg.bonus));
        buf.writeVarLong(Math.max(0, msg.bonusWithinMs));
        buf.writeVarLong(Math.max(0, msg.durationMs));
        buf.writeUtf(BSPNetwork.cut(msg.notes, 1000), 1000);
    }

    public static BountyPlacePacket decode(FriendlyByteBuf buf) {
        return new BountyPlacePacket(buf.readBoolean(), buf.readUUID(), buf.readUtf(16), buf.readVarInt() - 1, buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                buf.readVarLong(), buf.readUtf(1000));
    }

    public static void handle(BountyPlacePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            if (msg.search) {
                BountyScreens.players(player, msg.targetName);
            } else {
                BountyScreens.place(player, new Bounties.Ask(msg.target, msg.targetName, msg.coinTier, msg.amount, msg.bonus, msg.bonusWithinMs, msg.durationMs, msg.notes));
            }
        }
        ctx.get().setPacketHandled(true);
    }
}
