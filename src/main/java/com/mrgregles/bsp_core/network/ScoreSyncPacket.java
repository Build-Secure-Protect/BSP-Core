package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.client.ClientScores;
import com.mrgregles.bsp_core.score.ScoreService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server → client: the current leaderboard, best first, for Score Screens and menus. */
public record ScoreSyncPacket(List<ScoreService.Entry> entries) {
    public static void encode(ScoreSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entries.size());
        for (ScoreService.Entry e : msg.entries) {
            buf.writeUUID(e.id());
            buf.writeUtf(e.name(), 32);
            buf.writeVarInt(e.totems());
            buf.writeVarInt(e.points());
            buf.writeVarInt(e.bestTier() + 1);
        }
    }

    public static ScoreSyncPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<ScoreService.Entry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            entries.add(new ScoreService.Entry(buf.readUUID(), buf.readUtf(32), buf.readVarInt(), buf.readVarInt(), buf.readVarInt() - 1));
        }
        return new ScoreSyncPacket(entries);
    }

    public static void handle(ScoreSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientScores.accept(msg.entries));
        ctx.get().setPacketHandled(true);
    }
}
