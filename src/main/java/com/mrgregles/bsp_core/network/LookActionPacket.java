package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.looks.LookActions;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/** Client → server: something the LOOKS tab asks for at the totem at {@code pos}. {@link LookActions} checks everything. */
public record LookActionPacket(BlockPos pos, LookActions.Action action, int slot, String hat, @Nullable CompoundTag look) {
    public static void encode(LookActionPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeEnum(msg.action);
        buf.writeVarInt(msg.slot);
        buf.writeUtf(BSPNetwork.cut(msg.hat, 32), 32);
        buf.writeNbt(msg.look);
    }

    public static LookActionPacket decode(FriendlyByteBuf buf) {
        return new LookActionPacket(buf.readBlockPos(), buf.readEnum(LookActions.Action.class), buf.readVarInt(), buf.readUtf(32), buf.readNbt());
    }

    public static void handle(LookActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            LookActions.handle(player, msg.pos, msg.action, msg.slot, msg.hat, msg.look);
        }
        ctx.get().setPacketHandled(true);
    }
}
