package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.tracking.WatchService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Client → server: the tags whose boxes the player is looking at now (Holo Grid page, popout page, pinned box); empty to stop. */
public record TrackerWatchPacket(List<String> ids) {
    public static void encode(TrackerWatchPacket msg, FriendlyByteBuf buf) {
        int n = Math.min(WatchService.MAX_WATCH, msg.ids.size());
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeUtf(BSPNetwork.cut(msg.ids.get(i), 16), 16);
        }
    }

    public static TrackerWatchPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > WatchService.MAX_WATCH) {
            throw new IllegalArgumentException("watch list of " + n);
        }
        List<String> ids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ids.add(buf.readUtf(16));
        }
        return new TrackerWatchPacket(ids);
    }

    public static void handle(TrackerWatchPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) {
            WatchService.watch(player, msg.ids);
        }
        ctx.get().setPacketHandled(true);
    }
}
