package com.mrgregles.bsp_core.network;

import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.shop.Shop;
import com.mrgregles.bsp_core.shop.ShopMenu;
import com.mrgregles.bsp_core.shop.ShopConfigMenu;
import com.mrgregles.bsp_core.shop.ShopOpener;
import com.mrgregles.bsp_core.shop.Shops;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: something done on the shop screen. Every outcome is answered with a {@link ShopDataPacket}. */
public record ShopActionPacket(Action action, int index, int lots, int page) {
    public enum Action { BUY, SELL, SELL_ALL, SECTION, BACK, PAGE, SELL_HERE, ADMIN, REFRESH, VENDOR }

    public static void encode(ShopActionPacket m, FriendlyByteBuf buf) {
        buf.writeEnum(m.action);
        buf.writeVarInt(m.index);
        buf.writeVarInt(m.lots);
        buf.writeVarInt(m.page);
    }

    public static ShopActionPacket decode(FriendlyByteBuf buf) {
        return new ShopActionPacket(buf.readEnum(Action.class), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(ShopActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null && player.containerMenu instanceof ShopMenu menu) {
            ctx.get().enqueueWork(() -> run(player, menu, msg));
        }
        ctx.get().setPacketHandled(true);
    }

    private static void run(ServerPlayer player, ShopMenu menu, ShopActionPacket msg) {
        var server = player.server;
        Shop shop = Shops.ledger(server).shop(menu.shopId);
        if (shop == null) {
            player.closeContainer();
            return;
        }
        boolean open = menu.hours == null || menu.hours.isOpen(player.level());
        switch (msg.action) {
            case BUY, SELL, SELL_ALL -> {
                if (!open) {
                    ShopOpener.sync(player, menu, msg.page, "closed", null);
                    return;
                }
                java.util.function.Consumer<Shops.Outcome> then = o -> ShopOpener.sync(player, menu, msg.page, o.key(), new String[]{String.valueOf(o.lots()), com.mrgregles.bsp_core.bank.Bank.format(o.amount()),
                        com.mrgregles.bsp_core.bank.Bank.format((o.pieces() >= 0 ? o.pieces() : com.mrgregles.bsp_core.bank.Bank.pieces(server, player.getUUID())) - (o.key().equals("test_bought") ? o.amount() : 0))});
                if (msg.action == Action.BUY) {
                    Shops.buy(server, player, shop, msg.index, msg.lots, menu.test, then);
                } else if (msg.action == Action.SELL) {
                    Shops.sell(server, player, shop, msg.index, msg.lots, menu.test, then);
                } else {
                    Shops.sellAll(server, player, shop, menu.test, then);
                }
            }
            case SECTION -> {
                var item = shop.item(msg.index);
                Shop sub = item == null || item.section.isEmpty() ? null : Shops.ledger(server).shop(item.section);
                if (sub != null) {
                    ShopOpener.open(player, sub, menu.at, menu.remote, menu.hours);
                }
            }
            case BACK -> {
                Shop parent = shop.parent.isEmpty() ? null : Shops.ledger(server).shop(shop.parent);
                if (parent != null) {
                    ShopOpener.open(player, parent, menu.at, menu.remote, menu.hours);
                } else {
                    player.closeContainer();
                }
            }
            case PAGE -> ShopOpener.sync(player, menu, msg.page, "", null);
            case SELL_HERE -> {
                if (open && !shop.buyOnly && !menu.test) {
                    SellHereOpener.open(player, shop, menu.at, menu.remote, menu.hours);
                }
            }
            case ADMIN -> {
                if (Admins.isAdmin(player) || Admins.isModerator(player)) {
                    ShopConfigOpener.open(player, shop.id);
                }
            }
            case REFRESH -> ShopOpener.sync(player, menu, msg.page, "", null);
            case VENDOR -> {
                // the venue's own setup screen: a post or stall's assignment, a display's settings
                if (menu.at != null && (Admins.isAdmin(player) || Admins.isModerator(player))) {
                    var be = player.level().getBlockEntity(menu.at);
                    if (be instanceof com.mrgregles.bsp_core.shop.VendorPostBlockEntity post) {
                        com.mrgregles.bsp_core.shop.VendorMenu.open(player, post);
                    } else if (be instanceof com.mrgregles.bsp_core.shop.MarketStallBlockEntity stall) {
                        com.mrgregles.bsp_core.shop.VendorMenu.open(player, stall);
                    } else if (be instanceof com.mrgregles.bsp_core.shop.StallDisplayBlockEntity display && Admins.isAdmin(player)) {
                        com.mrgregles.bsp_core.shop.StallDisplayMenu.open(player, display);
                    }
                }
            }
        }
    }

    /** Opens the configurator (admins edit, moderators look) on a shop. */
    public static final class ShopConfigOpener {
        private ShopConfigOpener() {}

        public static void open(ServerPlayer player, String shopId) {
            boolean editable = Admins.isAdmin(player);
            net.minecraftforge.network.NetworkHooks.openScreen(player, new net.minecraft.world.SimpleMenuProvider(
                    (id, inv, p) -> new ShopConfigMenu(id, inv, editable), Component.translatable("gui.bsp_core.config.title")), buf -> buf.writeBoolean(editable));
            if (player.containerMenu instanceof ShopConfigMenu menu) {
                ShopConfigActionPacket.sync(player, menu, shopId, "", null);
            }
        }
    }

    /** Opens the Sell here grid; lives here so the shop and sell-here menus share one entry point. */
    public static final class SellHereOpener {
        private SellHereOpener() {}

        public static void open(ServerPlayer player, Shop shop, net.minecraft.core.BlockPos at, boolean remote, com.mrgregles.bsp_core.shop.OpenHours hours) {
            net.minecraftforge.network.NetworkHooks.openScreen(player, new net.minecraft.world.SimpleMenuProvider(
                    (id, inv, p) -> new com.mrgregles.bsp_core.shop.SellHereMenu(id, inv, shop.id, at, remote, hours), Component.literal(shop.name)), buf -> {
                buf.writeUtf(shop.id, 32);
                buf.writeBoolean(at != null);
                if (at != null) {
                    buf.writeBlockPos(at);
                }
                buf.writeBoolean(remote);
                buf.writeNbt(hours == null ? new net.minecraft.nbt.CompoundTag() : hours.save());
            });
        }
    }
}
