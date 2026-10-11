package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.shop.Shop;
import com.mrgregles.bsp_core.shop.ShopConfigMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server → client: the shop list and the selected shop in full (as its NBT record) for the configurator. */
public record ShopConfigDataPacket(List<ShopConfigMenu.Summary> list, @Nullable CompoundTag shop, String messageKey, String[] messageArgs) {
    public static void encode(ShopConfigDataPacket m, FriendlyByteBuf buf) {
        buf.writeVarInt(m.list.size());
        for (ShopConfigMenu.Summary s : m.list) {
            buf.writeUtf(s.id(), 32);
            buf.writeUtf(s.name(), 64);
            buf.writeEnum(s.kind());
            buf.writeVarInt(s.items());
            buf.writeBoolean(s.remote());
        }
        buf.writeNbt(m.shop == null ? new CompoundTag() : m.shop);
        buf.writeUtf(m.messageKey, 64);
        buf.writeVarInt(m.messageArgs.length);
        for (String s : m.messageArgs) {
            buf.writeUtf(s, 64);
        }
    }

    public static ShopConfigDataPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<ShopConfigMenu.Summary> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new ShopConfigMenu.Summary(buf.readUtf(32), buf.readUtf(64), buf.readEnum(Shop.Kind.class), buf.readVarInt(), buf.readBoolean()));
        }
        CompoundTag shop = buf.readNbt();
        String key = buf.readUtf(64);
        String[] args = new String[buf.readVarInt()];
        for (int i = 0; i < args.length; i++) {
            args[i] = buf.readUtf(64);
        }
        return new ShopConfigDataPacket(list, shop == null || shop.isEmpty() ? null : shop, key, args);
    }

    public static void handle(ShopConfigDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.mrgregles.bsp_core.client.ShopConfigScreen.receive(msg)));
        ctx.get().setPacketHandled(true);
    }
}
