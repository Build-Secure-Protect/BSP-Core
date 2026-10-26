package com.mrgregles.bsp_core.shop;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The shop screen's search (spawn shops request SS-9): the cards of a shop and every sub-section
 * under it, as this player may see them, sent to the player's game once. The game matches names
 * itself (a server cannot translate mod items' names); a click opens the result's section on the
 * page holding the card.
 */
public final class ShopSearch {
    private ShopSearch() {}

    /** One card found in the tree: its shop and that shop's path, its place, the item, its prices, and whether it is locked for the player. */
    public record Entry(String shop, String path, String section, int index, ItemStack stack, long buy, long sell, int currency, boolean locked) {}

    /** True when a shop's tree holds more cards than one page: the search box shows. */
    public static boolean searchable(MinecraftServer server, Shop shop) {
        int n = 0;
        for (Shop s : ShopExport.shops(Shops.ledger(server), shop.id)) {
            n += (int) s.all().stream().filter(it -> it.section.isEmpty()).count();
        }
        return n > Shop.PER_PAGE;
    }

    /**
     * Every card of the shop and its sub-sections the player may see now: a rotating shop's cards on
     * show only, cards hidden from them by a requirement left out, section cards left out.
     */
    public static List<Entry> tree(MinecraftServer server, Shop top, Player player) {
        List<Entry> out = new ArrayList<>();
        long now = Shops.now();
        for (Shop s : ShopExport.shops(Shops.ledger(server), top.id)) {
            Requirement.Lock shopLock = s.requires.check(player);
            if (shopLock != null && shopLock.hide()) {
                continue;
            }
            String path = path(server, s, top);
            for (int i : Shops.visible(server, s, now)) {
                ShopItem it = s.item(i);
                if (it == null || !it.section.isEmpty() || it.stack.isEmpty()) {
                    continue;
                }
                Requirement.Lock lock = Shops.lock(s, i, player);
                if (lock != null && lock.hide()) {
                    continue;
                }
                out.add(new Entry(s.id, path, s.name, i, it.stack.copy(), it.buyable() ? Shops.price(s, it, true, now) : -1, it.sellable() && !s.buyOnly ? it.sell : -1, it.currency, lock != null));
            }
        }
        return out;
    }

    /** "Decor > Woods": the names from the searched shop down to this one. */
    private static String path(MinecraftServer server, Shop s, Shop top) {
        StringBuilder b = new StringBuilder(s.name);
        for (Shop p = s; p != top && !p.parent.isEmpty(); ) {
            p = Shops.ledger(server).shop(p.parent);
            if (p == null) {
                break;
            }
            b.insert(0, p.name + " > ");
        }
        return b.toString();
    }

    /** The page of the player's view of {@code shop} that holds card {@code index} (0 when it is not on show). */
    public static int pageOf(MinecraftServer server, Shop shop, Player player, int index) {
        List<Integer> shown = new ArrayList<>(Shops.visible(server, shop, Shops.now()));
        shown.removeIf(i -> {
            Requirement.Lock l = Shops.lock(shop, i, player);
            return l != null && l.hide();
        });
        List<ShopItem> items = new ArrayList<>();
        shown.forEach(i -> items.add(shop.item(i)));
        int perPage = shop.effectiveColumns(items) * shop.rows();
        int at = shown.indexOf(index);
        return at < 0 ? 0 : at / perPage;
    }
}
