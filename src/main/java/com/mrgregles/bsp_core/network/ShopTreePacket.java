package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.shop.ShopSearch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server → client: the cards of the open shop's tree, for the shop screen's search to filter. */
public record ShopTreePacket(List<ShopSearch.Entry> entries) {
    public static void encode(ShopTreePacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.entries.size());
        for (ShopSearch.Entry e : m.entries) {
            buf.writeUtf(e.shop(), 32);
            buf.writeUtf(e.path(), 160);
            buf.writeUtf(e.section(), 64);
            buf.writeVarInt(e.index());
            buf.writeItem(e.stack());
            buf.writeLong(e.buy());
            buf.writeLong(e.sell());
            buf.writeVarInt(e.currency() + 1);
            buf.writeBoolean(e.locked());
        }
    }

    public static ShopTreePacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<ShopSearch.Entry> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new ShopSearch.Entry(buf.readUtf(32), buf.readUtf(160), buf.readUtf(64), buf.readVarInt(), buf.readItem(), buf.readLong(), buf.readLong(), buf.readVarInt() - 1, buf.readBoolean()));
        }
        return new ShopTreePacket(out);
    }

    public static void handle(ShopTreePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.ShopScreen.receiveTree(msg.entries())));
        ctx.get().setPacketHandled(true);
    }
}
