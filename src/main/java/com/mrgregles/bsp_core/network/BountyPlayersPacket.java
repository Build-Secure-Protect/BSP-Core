package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.bounty.BountyScreens;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server → client: the place page's player list for a search: online first, each with why a bounty may not go on them. */
public record BountyPlayersPacket(String search, List<BountyScreens.Pick> players) {
    public static final int MAX = 200;

    public static void encode(BountyPlayersPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(BSPNetwork.cut(msg.search, 16), 16);
        int n = Math.min(msg.players.size(), MAX);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            BountyScreens.Pick p = msg.players.get(i);
            buf.writeUUID(p.id());
            buf.writeUtf(BSPNetwork.cut(p.name(), 16), 16);
            buf.writeBoolean(p.online());
            buf.writeUtf(BSPNetwork.cut(p.refusal(), 16), 16);
        }
    }

    public static BountyPlayersPacket decode(FriendlyByteBuf buf) {
        String search = buf.readUtf(16);
        int n = Math.min(buf.readVarInt(), MAX);
        List<BountyScreens.Pick> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new BountyScreens.Pick(buf.readUUID(), buf.readUtf(16), buf.readBoolean(), buf.readUtf(16)));
        }
        return new BountyPlayersPacket(search, out);
    }

    public static void handle(BountyPlayersPacket msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.BountyPlacePage.takePlayers(msg));
        ctx.get().setPacketHandled(true);
    }
}
