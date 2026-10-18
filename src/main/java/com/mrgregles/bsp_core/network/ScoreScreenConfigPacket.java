package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.score.ScoreScreenBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: new settings for a Score Screen. Ignored unless the sender is an admin standing near it. */
public record ScoreScreenConfigPacket(BlockPos origin, int mode, int layout, int font, int size, int speed, String title, String boardId, int look, int rotateSeconds, java.util.List<String> pages) {
    public static void encode(ScoreScreenConfigPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.origin);
        buf.writeVarInt(msg.mode);
        buf.writeVarInt(msg.layout);
        buf.writeVarInt(msg.font);
        buf.writeVarInt(msg.size);
        buf.writeVarInt(msg.speed);
        buf.writeUtf(msg.title, ScoreScreenBlockEntity.TITLE_MAX);
        buf.writeUtf(msg.boardId, ScoreScreenBlockEntity.BOARD_ID_MAX);
        buf.writeVarInt(msg.look);
        buf.writeVarInt(msg.rotateSeconds);
        buf.writeVarInt(msg.pages.size());
        for (String p : msg.pages) {
            buf.writeUtf(p, ScoreScreenBlockEntity.BOARD_ID_MAX);
        }
    }

    public static ScoreScreenConfigPacket decode(FriendlyByteBuf buf) {
        BlockPos origin = buf.readBlockPos();
        int mode = buf.readVarInt(), layout = buf.readVarInt(), font = buf.readVarInt(), size = buf.readVarInt(), speed = buf.readVarInt();
        String title = buf.readUtf(ScoreScreenBlockEntity.TITLE_MAX), boardId = buf.readUtf(ScoreScreenBlockEntity.BOARD_ID_MAX);
        int look = buf.readVarInt(), rotate = buf.readVarInt(), n = Math.min(16, buf.readVarInt());
        java.util.List<String> pages = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            pages.add(buf.readUtf(ScoreScreenBlockEntity.BOARD_ID_MAX));
        }
        return new ScoreScreenConfigPacket(origin, mode, layout, font, size, speed, title, boardId, look, rotate, pages);
    }

    public static void handle(ScoreScreenConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && Admins.isAdmin(player) && player.distanceToSqr(msg.origin.getCenter()) <= 32 * 32
                && player.level().getBlockEntity(msg.origin) instanceof ScoreScreenBlockEntity screen) {
            screen.configure(msg.mode, msg.layout, msg.font, msg.size, msg.speed, msg.title, msg.boardId, msg.look, msg.rotateSeconds, msg.pages);
        }
        ctx.get().setPacketHandled(true);
    }
}
