package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.trade.TradePostMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server → client: what the open Trade Post screen shows (a page of the market as pictures, the Collect box, the traders, requests, a message). */
public record TradePostDataPacket(TradePostMenu.View view) {
    private static void items(FriendlyByteBuf buf, List<ItemStack> items) {
        buf.writeVarInt(items.size());
        items.forEach(buf::writeItem);
    }

    private static List<ItemStack> items(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<ItemStack> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(buf.readItem());
        }
        return out;
    }

    public static void encode(TradePostDataPacket msg, FriendlyByteBuf buf) {
        TradePostMenu.View v = msg.view;
        buf.writeLong(v.balance());
        buf.writeLong(v.now());
        buf.writeVarInt(v.taxPercent());
        buf.writeVarInt(v.maxStacks());
        buf.writeVarInt(v.maxLots());
        buf.writeVarInt(v.days());
        buf.writeVarInt(v.myLots());
        buf.writeVarInt(v.totalLots());
        buf.writeVarInt(v.lots().size());
        for (TradePostMenu.LotView l : v.lots()) {
            buf.writeUtf(l.id(), 16);
            buf.writeUUID(l.seller());
            buf.writeUtf(BSPNetwork.cut(l.sellerName(), 32), 32);
            buf.writeLong(l.price());
            buf.writeLong(l.listedMs());
            buf.writeLong(l.endsMs());
            items(buf, l.items());
        }
        buf.writeVarInt(v.page());
        buf.writeVarInt(v.pages());
        buf.writeVarInt(v.matched());
        items(buf, v.collect());
        buf.writeVarInt(v.collectCount());
        buf.writeVarInt(v.traders().size());
        for (TradePostMenu.TraderView t : v.traders()) {
            buf.writeUUID(t.id());
            buf.writeUtf(BSPNetwork.cut(t.name(), 32), 32);
            buf.writeUtf(BSPNetwork.cut(t.server(), 64), 64);
            buf.writeBoolean(t.busy());
        }
        buf.writeVarInt(v.toMe().size());
        for (TradePostMenu.RequestView r : v.toMe()) {
            buf.writeUUID(r.from());
            buf.writeUtf(BSPNetwork.cut(r.name(), 32), 32);
            buf.writeUtf(BSPNetwork.cut(r.server(), 64), 64);
            buf.writeVarInt(r.seconds());
        }
        buf.writeUtf(BSPNetwork.cut(v.requestTo(), 32), 32);
        buf.writeVarInt(v.requestSeconds());
        buf.writeUtf(BSPNetwork.cut(v.message(), 128), 128);
        buf.writeVarInt(v.args().size());
        v.args().forEach(a -> buf.writeUtf(BSPNetwork.cut(a, 128), 128));
    }

    public static TradePostDataPacket decode(FriendlyByteBuf buf) {
        long balance = buf.readLong(), now = buf.readLong();
        int tax = buf.readVarInt(), stacks = buf.readVarInt(), maxLots = buf.readVarInt(), days = buf.readVarInt(), myLots = buf.readVarInt(), total = buf.readVarInt();
        int n = buf.readVarInt();
        List<TradePostMenu.LotView> lots = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            lots.add(new TradePostMenu.LotView(buf.readUtf(16), buf.readUUID(), buf.readUtf(32), buf.readLong(), buf.readLong(), buf.readLong(), items(buf)));
        }
        int page = buf.readVarInt(), pages = buf.readVarInt(), matched = buf.readVarInt();
        List<ItemStack> collect = items(buf);
        int collectCount = buf.readVarInt();
        n = buf.readVarInt();
        List<TradePostMenu.TraderView> traders = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            traders.add(new TradePostMenu.TraderView(buf.readUUID(), buf.readUtf(32), buf.readUtf(64), buf.readBoolean()));
        }
        n = buf.readVarInt();
        List<TradePostMenu.RequestView> toMe = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            toMe.add(new TradePostMenu.RequestView(buf.readUUID(), buf.readUtf(32), buf.readUtf(64), buf.readVarInt()));
        }
        String to = buf.readUtf(32);
        int secs = buf.readVarInt();
        String message = buf.readUtf(128);
        n = buf.readVarInt();
        List<String> args = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            args.add(buf.readUtf(128));
        }
        return new TradePostDataPacket(new TradePostMenu.View(balance, now, tax, stacks, maxLots, days, myLots, total, lots, page, pages, matched, collect, collectCount, traders, toMe, to, secs, message, args));
    }

    public static void handle(TradePostDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.mrgregles.bsp_core.client.TradePostScreen.receive(msg.view)));
        ctx.get().setPacketHandled(true);
    }
}
