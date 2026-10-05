package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.zone.AntiTotemBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Client → server: new settings for an Anti Totem block. Ignored unless the sender is an admin near it. */
public record ZoneConfigPacket(BlockPos pos, int[] reach, int colour, List<String> blocked) {
    public static void encode(ZoneConfigPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarIntArray(msg.reach);
        buf.writeVarInt(msg.colour);
        buf.writeVarInt(msg.blocked.size());
        msg.blocked.forEach(k -> buf.writeUtf(k, 64));
    }

    public static ZoneConfigPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int[] reach = buf.readVarIntArray(6);
        int colour = buf.readVarInt(), n = Math.min(512, buf.readVarInt());
        List<String> blocked = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            blocked.add(buf.readUtf(64));
        }
        return new ZoneConfigPacket(pos, reach, colour, blocked);
    }

    public static void handle(ZoneConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && Admins.isAdmin(player) && msg.reach.length == 6 && player.distanceToSqr(msg.pos.getCenter()) <= 32 * 32
                && player.level().getBlockEntity(msg.pos) instanceof AntiTotemBlockEntity zone) {
            zone.configure(msg.reach, msg.colour, msg.blocked);
        }
        ctx.get().setPacketHandled(true);
    }
}
