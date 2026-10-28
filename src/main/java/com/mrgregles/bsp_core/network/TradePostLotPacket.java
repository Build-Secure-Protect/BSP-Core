package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.market.ItemPicture;
import com.mrgregles.bsp_core.market.MarketLot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server → client: the full stacks of the one lot a player opened on the Trade Post screen (tooltips with everything in
 * them), or {@code found} false when it is gone. Within {@link #BUDGET} bytes: a stack that would go past it is sent as its
 * picture instead, so even a lot of shulker boxes full of books fits in one packet.
 */
public record TradePostLotPacket(String id, boolean found, List<ItemStack> items) {
    public static final int BUDGET = 800_000;

    public static TradePostLotPacket of(String id, @Nullable MarketLot lot) {
        if (lot == null) {
            return new TradePostLotPacket(id, false, List.of());
        }
        List<ItemStack> out = new ArrayList<>();
        int total = 0;
        for (ItemStack s : lot.items()) {
            int size = BSPNetwork.sizeOf(s, (x, buf) -> buf.writeItem(x));
            if (total + size > BUDGET) {
                s = ItemPicture.of(s);
                size = BSPNetwork.sizeOf(s, (x, buf) -> buf.writeItem(x));
            }
            total += size;
            out.add(s);
        }
        return new TradePostLotPacket(id, true, out);
    }

    public static void encode(TradePostLotPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.id, 16);
        buf.writeBoolean(msg.found);
        buf.writeVarInt(msg.items.size());
        msg.items.forEach(buf::writeItem);
    }

    public static TradePostLotPacket decode(FriendlyByteBuf buf) {
        String id = buf.readUtf(16);
        boolean found = buf.readBoolean();
        int n = Math.min(buf.readVarInt(), 64);
        List<ItemStack> items = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            items.add(buf.readItem());
        }
        return new TradePostLotPacket(id, found, items);
    }

    public static void handle(TradePostLotPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.mrgregles.bsp_core.client.TradePostScreen.receiveLot(msg)));
        ctx.get().setPacketHandled(true);
    }
}
