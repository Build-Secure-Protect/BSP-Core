package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: the owner changes the access list of the totem at {@code pos}: add a named online player, remove a row, or flip one of a row's switches. */
public record TotemAccessPacket(BlockPos pos, int action, String name, int index, int flag) {
    public static final int ADD = 0, REMOVE = 1, TOGGLE = 2;

    public static void encode(TotemAccessPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarInt(msg.action);
        buf.writeUtf(msg.name, 16);
        buf.writeVarInt(msg.index);
        buf.writeVarInt(msg.flag);
    }

    public static TotemAccessPacket decode(FriendlyByteBuf buf) {
        return new TotemAccessPacket(buf.readBlockPos(), buf.readVarInt(), buf.readUtf(16), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(TotemAccessPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && player.level().getBlockEntity(msg.pos) instanceof ShatterTotemBlockEntity totem && (totem.isOwner(player.getUUID()) || Admins.isAdmin(player))) {
            String why = null;
            if (msg.action == ADD) {
                ServerPlayer target = player.server.getPlayerList().getPlayerByName(msg.name.trim());
                why = target == null ? "not_online" : totem.isOwner(target.getUUID()) ? "is_owner" : totem.addAccess(target.getUUID(), target.getGameProfile().getName()) ? null : "full";
            } else if (msg.action == REMOVE) {
                totem.removeAccess(msg.index);
            } else if (msg.action == TOGGLE) {
                totem.toggleAccess(msg.index, msg.flag);
            }
            if (why != null) {
                player.displayClientMessage(Component.translatable("message.bsp_core.access." + why).withStyle(ChatFormatting.RED), true);
            }
        }
        ctx.get().setPacketHandled(true);
    }
}
