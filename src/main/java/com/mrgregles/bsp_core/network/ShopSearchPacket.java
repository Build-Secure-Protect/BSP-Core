package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.shop.Shop;
import com.mrgregles.bsp_core.shop.ShopMenu;
import com.mrgregles.bsp_core.shop.ShopOpener;
import com.mrgregles.bsp_core.shop.ShopSearch;
import com.mrgregles.bsp_core.shop.Shops;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server, from the shop screen's search: with no shop, "send me this shop's tree" (answered
 * with a {@link ShopTreePacket}); with a shop and a card, "open that section on the card's page".
 */
public record ShopSearchPacket(String shop, int index) {
    public static void encode(ShopSearchPacket m, FriendlyByteBuf buf) {
        buf.writeUtf(m.shop, 32);
        buf.writeVarInt(m.index);
    }

    public static ShopSearchPacket decode(FriendlyByteBuf buf) {
        return new ShopSearchPacket(buf.readUtf(32), buf.readVarInt());
    }

    public static void handle(ShopSearchPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && player.containerMenu instanceof ShopMenu menu) {
            ctx.get().enqueueWork(() -> {
                Shop top = Shops.ledger(player.server).shop(menu.shopId);
                if (top == null) {
                    return;
                }
                if (msg.shop.isEmpty()) {
                    BSPNetwork.sendTo(player, new ShopTreePacket(ShopSearch.tree(player.server, top, player)));
                    return;
                }
                Shop target = Shops.ledger(player.server).shop(msg.shop);
                // only a card the search could have found, so a search cannot open a shop the player has no way to
                if (target == null || !ShopSearch.findable(player.server, top, target, msg.index, player)) {
                    ShopOpener.sync(player, menu, menu.page, "search_gone", null); // the result went stale (a new draw, a change): say so
                    return;
                }
                int page = ShopSearch.pageOf(player.server, target, player, msg.index);
                if (target == top) {
                    ShopOpener.sync(player, menu, page, "", null);
                } else {
                    ShopOpener.open(player, target, menu.at, menu.remote, menu.hours, menu.test);
                    if (player.containerMenu instanceof ShopMenu opened) {
                        ShopOpener.sync(player, opened, page, "", null);
                    }
                }
            });
        }
        ctx.get().setPacketHandled(true);
    }
}
