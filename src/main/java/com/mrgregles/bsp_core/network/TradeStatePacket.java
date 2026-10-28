package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.trade.TradeMenu;
import com.mrgregles.bsp_core.trade.TradeSession;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server → client: what the open trade screen shows (pieces, READY lights, the countdown, the chat) and a message (a lang key, "" none). */
public record TradeStatePacket(TradeMenu.State state, String message, List<String> args) {
    public static void encode(TradeStatePacket msg, FriendlyByteBuf buf) {
        TradeMenu.State s = msg.state;
        buf.writeUtf(BSPNetwork.cut(s.theirName(), 32), 32);
        buf.writeUtf(BSPNetwork.cut(s.theirServer(), 64), 64);
        buf.writeLong(s.myPieces());
        buf.writeLong(s.theirPieces());
        buf.writeBoolean(s.myReady());
        buf.writeBoolean(s.theirReady());
        buf.writeUtf(BSPNetwork.cut(s.stage(), 16), 16);
        buf.writeLong(s.countdownMs());
        buf.writeBoolean(s.locked());
        buf.writeLong(s.balance());
        List<TradeSession.Line> chat = s.chat().subList(Math.max(0, s.chat().size() - 20), s.chat().size());
        buf.writeVarInt(chat.size());
        for (TradeSession.Line l : chat) {
            buf.writeUtf(BSPNetwork.cut(l.name(), 32), 32);
            buf.writeUtf(BSPNetwork.cut(l.text(), 256), 256);
        }
        buf.writeUtf(BSPNetwork.cut(msg.message, 128), 128);
        buf.writeVarInt(msg.args.size());
        msg.args.forEach(a -> buf.writeUtf(BSPNetwork.cut(a, 128), 128));
    }

    public static TradeStatePacket decode(FriendlyByteBuf buf) {
        String name = buf.readUtf(32), server = buf.readUtf(64);
        long mine = buf.readLong(), theirs = buf.readLong();
        boolean myReady = buf.readBoolean(), theirReady = buf.readBoolean();
        String stage = buf.readUtf(16);
        long left = buf.readLong();
        boolean locked = buf.readBoolean();
        long balance = buf.readLong();
        int n = buf.readVarInt();
        List<TradeSession.Line> chat = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            chat.add(new TradeSession.Line(buf.readUtf(32), buf.readUtf(256)));
        }
        String message = buf.readUtf(128);
        n = buf.readVarInt();
        List<String> args = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            args.add(buf.readUtf(128));
        }
        return new TradeStatePacket(new TradeMenu.State(name, server, mine, theirs, myReady, theirReady, stage, left, locked, balance, chat), message, args);
    }

    public static void handle(TradeStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.mrgregles.bsp_core.client.TradeScreen.receive(msg)));
        ctx.get().setPacketHandled(true);
    }
}
