package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.score.ScoreScreenBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: new settings for a Score Screen. Ignored unless the sender is an admin standing near it. */
public record ScoreScreenConfigPacket(BlockPos origin, int mode, int layout, int font, int size, int speed, String title) {
    public static void encode(ScoreScreenConfigPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.origin);
        buf.writeVarInt(msg.mode);
        buf.writeVarInt(msg.layout);
        buf.writeVarInt(msg.font);
        buf.writeVarInt(msg.size);
        buf.writeVarInt(msg.speed);
        buf.writeUtf(msg.title, ScoreScreenBlockEntity.TITLE_MAX);
    }

    public static ScoreScreenConfigPacket decode(FriendlyByteBuf buf) {
        return new ScoreScreenConfigPacket(buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readUtf(ScoreScreenBlockEntity.TITLE_MAX));
    }

    public static void handle(ScoreScreenConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && Admins.isAdmin(player) && player.distanceToSqr(msg.origin.getCenter()) <= 32 * 32
                && player.level().getBlockEntity(msg.origin) instanceof ScoreScreenBlockEntity screen) {
            screen.configure(msg.mode, msg.layout, msg.font, msg.size, msg.speed, msg.title);
        }
        ctx.get().setPacketHandled(true);
    }
}
