package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.link.AcceleratorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: refresh an accelerator's screen ({@code VIEW}), wake every sleeping far side ({@code WAKE}), or undo link {@code index} ({@code UNLINK}). The server answers with a view. */
public record LinkActionPacket(BlockPos pos, int action, int index) {
    public static final int VIEW = 0, WAKE = 1, UNLINK = 2;

    public static void encode(LinkActionPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeByte(msg.action);
        buf.writeVarInt(msg.index);
    }

    public static LinkActionPacket decode(FriendlyByteBuf buf) {
        return new LinkActionPacket(buf.readBlockPos(), buf.readByte(), buf.readVarInt());
    }

    public static void handle(LinkActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.level().isLoaded(msg.pos) || player.distanceToSqr(msg.pos.getX() + 0.5, msg.pos.getY() + 0.5, msg.pos.getZ() + 0.5) > 64 * 64) {
                return;
            }
            if (!(player.level().getBlockEntity(msg.pos) instanceof AcceleratorBlockEntity intake)) {
                return;
            }
            if (msg.action == WAKE && intake.mayLink(player)) {
                intake.wakeNow();
            } else if (msg.action == UNLINK && intake.mayLink(player)) {
                intake.unlink(msg.index);
            }
            BSPNetwork.sendTo(player, intake.view());
        });
        ctx.get().setPacketHandled(true);
    }
}
