package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: buy the next level of a placed-only upgrade on the totem at {@code pos}. */
public record PlacedUpgradeRequestPacket(BlockPos pos, int buffOrdinal) {
    public static void encode(PlacedUpgradeRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarInt(msg.buffOrdinal);
    }

    public static PlacedUpgradeRequestPacket decode(FriendlyByteBuf buf) {
        return new PlacedUpgradeRequestPacket(buf.readBlockPos(), buf.readVarInt());
    }

    public static void handle(PlacedUpgradeRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null
                && player.distanceToSqr(msg.pos.getX() + 0.5, msg.pos.getY() + 0.5, msg.pos.getZ() + 0.5) <= 64
                && player.level().getBlockEntity(msg.pos) instanceof ShatterTotemBlockEntity totem) {
            totem.tryBuy(player, msg.buffOrdinal);
        }
        ctx.get().setPacketHandled(true);
    }
}
