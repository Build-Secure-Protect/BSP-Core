package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.shop.Shop;
import com.mrgregles.bsp_core.shop.ShopListMenu;
import com.mrgregles.bsp_core.shop.ShopOpener;
import com.mrgregles.bsp_core.shop.Shops;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: open a shop from the Shopping Network list. The server checks the account's unlock and the shop's remote flag. */
public record OpenRemoteShopPacket(String shopId) {
    public static void encode(OpenRemoteShopPacket m, FriendlyByteBuf buf) {
        buf.writeUtf(m.shopId, 32);
    }

    public static OpenRemoteShopPacket decode(FriendlyByteBuf buf) {
        return new OpenRemoteShopPacket(buf.readUtf(32));
    }

    public static void handle(OpenRemoteShopPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && player.containerMenu instanceof ShopListMenu) {
            ctx.get().enqueueWork(() -> {
                Shop shop = Shops.ledger(player.server).shop(msg.shopId);
                if (shop != null && ShopListMenu.mayOpen(player.server, player.getUUID(), shop)) {
                    ShopOpener.open(player, shop, null, true, null);
                }
            });
        }
        ctx.get().setPacketHandled(true);
    }
}
