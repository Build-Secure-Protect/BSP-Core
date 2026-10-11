package com.mrgregles.bsp_core.shop;

import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.ShopDataPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Opens a shop for a player and keeps the open screen fed with the page it shows. */
public final class ShopOpener {
    private ShopOpener() {}

    /** From a command or an Easy NPC: no position, no hours (always open), not the remote list. */
    public static void open(ServerPlayer player, Shop shop, @Nullable BlockPos at) {
        open(player, shop, at, false, null);
    }

    public static void open(ServerPlayer player, Shop shop, @Nullable BlockPos at, boolean remote, @Nullable OpenHours hours) {
        open(player, shop, at, remote, hours, false);
    }

    public static void open(ServerPlayer player, Shop shop, @Nullable BlockPos at, boolean remote, @Nullable OpenHours hours, boolean test) {
        NetworkHooks.openScreen(player, new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal(shop.name);
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                return new ShopMenu(id, inv, shop.id, at, remote, hours, test);
            }
        }, buf -> {
            buf.writeUtf(shop.id, 32);
            buf.writeBoolean(at != null);
            if (at != null) {
                buf.writeBlockPos(at);
            }
            buf.writeBoolean(remote);
            buf.writeNbt(hours == null ? new net.minecraft.nbt.CompoundTag() : hours.save());
            buf.writeBoolean(test);
        });
        if (player.containerMenu instanceof ShopMenu menu) {
            sync(player, menu, 0, "", null);
        }
    }

    /** Sends the page as the player may see it now. */
    public static void sync(ServerPlayer player, ShopMenu menu, int page, String messageKey, @Nullable String[] args) {
        var server = player.server;
        Shop shop = Shops.ledger(server).shop(menu.shopId);
        if (shop == null) {
            player.closeContainer();
            return;
        }
        long now = System.currentTimeMillis();
        boolean open = menu.hours == null || menu.hours.isOpen(player.level());
        List<Integer> shown = open ? Shops.visible(server, shop, now) : List.of();
        int perPage = Shop.PER_PAGE, pages = Math.max(1, (shown.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));
        List<ShopMenu.Card> cards = new ArrayList<>();
        for (int k = page * perPage; k < Math.min(shown.size(), (page + 1) * perPage); k++) {
            int i = shown.get(k);
            ShopItem it = shop.item(i);
            cards.add(new ShopMenu.Card(i, it.stack.copy(), it.buyable() ? Shops.price(shop, it, true, now) : -1, it.sellable() && !shop.buyOnly ? it.sell : -1, it.currency,
                    Shops.stockLeft(server, shop, i, player.getUUID(), now), it.instant, it.section));
        }
        StringBuilder path = new StringBuilder(shop.name);
        for (Shop p = shop.parent.isEmpty() ? null : Shops.ledger(server).shop(shop.parent); p != null; p = p.parent.isEmpty() ? null : Shops.ledger(server).shop(p.parent)) {
            path.insert(0, p.name + " > ");
        }
        long rotatesIn = shop.kind == Shop.Kind.ROTATING ? Math.max(0, Shops.ledger(server).draw(shop.id).nextAt - now) : 0;
        String opensAt = "";
        int until = 0;
        if (!open && menu.hours != null) {
            int at = OpenHours.now(menu.hours.mode(), player.level());
            until = menu.hours.untilOpen(at);
            opensAt = menu.hours.mode() == OpenHours.Mode.GAME ? String.format(Locale.ROOT, "tick %d", menu.hours.openMin()) : String.format(Locale.ROOT, "%02d:%02d", menu.hours.openMin() / 60, menu.hours.openMin() % 60);
        }
        boolean admin = Admins.isAdmin(player) || Admins.isModerator(player);
        BSPNetwork.sendTo(player, new ShopDataPacket(shop.name, path.toString(), shop.kind, shop.buyOnly, open, admin, shop.offerActive(now) ? shop.offerPercent : 0, shop.offerEndMs, rotatesIn,
                page, pages, Bank.pieces(server, player.getUUID()), opensAt, until, cards, messageKey, args == null ? new String[0] : args));
    }
}
