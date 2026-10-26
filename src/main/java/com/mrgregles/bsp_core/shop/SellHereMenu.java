package com.mrgregles.bsp_core.shop;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Sell here": a 14-slot grid the player drops items into. Closing it, or the Sell now key, sells
 * every full lot the shop buys at its sell prices, up to each card's sell limit (a rotating shop
 * buys only what is on show), and hands everything else back.
 */
public class SellHereMenu extends AbstractContainerMenu {
    public static final int GRID = 14, WIDTH = 300, SLOT_X = 24, SLOT_Y = 40, INV_X = 24, INV_Y = 132, HEIGHT = INV_Y + 82;
    public final String shopId;
    @Nullable
    public final BlockPos at;
    public final boolean remote;
    @Nullable
    public final OpenHours hours;
    public final ItemStackHandler grid = new ItemStackHandler(GRID);
    /** Client: the total the grid would fetch, kept up to date by the server. */
    public long total;
    public int lots;
    /** After a sale: true when a sell limit kept some full lots back. */
    public boolean capped;
    public String name = "";
    private final java.util.UUID player;

    public SellHereMenu(int id, Inventory inv, String shopId, @Nullable BlockPos at, boolean remote, @Nullable OpenHours hours) {
        super(ModMenus.SELL_HERE.get(), id);
        this.shopId = shopId;
        this.at = at;
        this.remote = remote;
        this.hours = hours;
        this.player = inv.player.getUUID();
        for (int i = 0; i < GRID; i++) {
            addSlot(new SlotItemHandler(grid, i, SLOT_X + 1 + (i % 7) * 18, SLOT_Y + 1 + (i / 7) * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, INV_X + 1 + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, INV_X + 1 + col * 18, INV_Y + 58));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < GRID ? !moveItemStackTo(stack, GRID, slots.size(), true) : !moveItemStackTo(stack, 0, GRID, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return at == null || player.distanceToSqr(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5) < 64;
    }

    /** What the grid would fetch now: {lots, pieces}; coin-priced items are not sold here, and each card counts only up to its sell limit. */
    public long[] quote(MinecraftServer server, Shop shop, long now) {
        long lots = 0, pieces = 0;
        Map<Integer, Integer> room = new HashMap<>();
        List<Integer> shown = shown(server, shop, now);
        for (int i = 0; i < GRID; i++) {
            ItemStack s = grid.getStackInSlot(i);
            int index = s.isEmpty() ? -1 : match(shop, s, shown);
            if (index >= 0) {
                int n = take(server, shop, index, s.getCount() / shop.item(index).stack.getCount(), room, now);
                lots += n;
                pieces += n * Shops.price(shop, shop.item(index), false, now);
            }
        }
        return new long[]{lots, pieces};
    }

    /** The cards this shop buys from now: a rotating shop's draw, or null for every card. */
    @Nullable
    private static List<Integer> shown(MinecraftServer server, Shop shop, long now) {
        return shop.kind == Shop.Kind.ROTATING ? Shops.visible(server, shop, now) : null;
    }

    /** How many of {@code want} lots card {@code index} still takes, counting what earlier slots of the grid used. */
    private int take(MinecraftServer server, Shop shop, int index, int want, Map<Integer, Integer> room, long now) {
        int left = room.computeIfAbsent(index, k -> Shops.sellLeft(server, shop, k, player, now));
        int n = left < 0 ? want : Math.min(want, left);
        if (left >= 0) {
            room.put(index, left - n);
        }
        return n;
    }

    /** The index of the card this stack sells to, or -1. */
    private static int match(Shop shop, ItemStack s, @Nullable List<Integer> shown) {
        if (shop.buyOnly || com.mrgregles.bsp_core.compat.InventorySyncPlaceholders.is(s)) { // a placeholder is handed back
            return -1;
        }
        List<ShopItem> all = shop.all();
        for (int i = 0; i < all.size(); i++) {
            ShopItem it = all.get(i);
            if (it.sellable() && it.currency < 0 && (shown == null || shown.contains(i)) && ItemStack.isSameItemSameTags(s, it.stack)) {
                return i;
            }
        }
        return -1;
    }

    /** Sells the grid's full lots up to the sell limits and hands the rest back (see the two-argument form). */
    public void sellAll(ServerPlayer player) {
        sellAll(player, () -> {});
    }

    /**
     * Sells the grid's full lots up to the sell limits and hands the rest back, then runs {@code done}.
     * On a server alone the pieces are paid in one bank credit, at once. On a network (counts held in
     * the database) everything goes back to the inventory first and each card's lots are then sold
     * from it as a Sell would, so nothing waits in the grid for the database's answer.
     */
    public void sellAll(ServerPlayer player, Runnable done) {
        Shop shop = Shops.ledger(player.server).shop(shopId);
        long now = Shops.now();
        if (shop != null && Shops.sharedCounts()) {
            sellThroughInventory(player, shop, now, done);
            return;
        }
        long paid = 0;
        int lots = 0;
        boolean held = false;
        StringBuilder what = new StringBuilder();
        if (shop != null && (hours == null || hours.isOpen(player.level()))) {
            Map<Integer, Integer> room = new HashMap<>();
            List<Integer> shown = shown(player.server, shop, now);
            for (int i = 0; i < GRID; i++) {
                ItemStack s = grid.getStackInSlot(i);
                int index = s.isEmpty() ? -1 : match(shop, s, shown);
                if (index < 0) {
                    continue;
                }
                ShopItem it = shop.item(index);
                int want = s.getCount() / it.stack.getCount();
                int n = take(player.server, shop, index, want, room, now);
                held |= n < want;
                if (n <= 0) {
                    continue;
                }
                s.shrink(n * it.stack.getCount());
                grid.setStackInSlot(i, s);
                Shops.hold(player.server, shop, index, player.getUUID(), true, n, now, got -> {}); // this server's counts: taken at once
                paid += n * Shops.price(shop, it, false, now);
                lots += n;
                what.append(what.length() > 0 ? ", " : "").append(n * it.stack.getCount()).append(' ').append(it.stack.getHoverName().getString());
            }
        }
        for (int i = 0; i < GRID; i++) {
            ItemStack s = grid.getStackInSlot(i);
            if (!s.isEmpty()) {
                if (!player.getInventory().add(s)) {
                    player.drop(s, false);
                }
                grid.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        if (paid > 0 && shop != null) {
            Bank.give(player.server, player.getUUID(), paid, com.mrgregles.bsp_core.bank.BankTransaction.Type.SOLD, shop.name, what.toString(), r -> {});
        }
        this.lots = lots;
        this.total = paid;
        this.capped = held;
        done.run();
    }

    private void sellThroughInventory(ServerPlayer player, Shop shop, long now, Runnable done) {
        Map<Integer, Integer> wanted = new java.util.LinkedHashMap<>();
        boolean open = hours == null || hours.isOpen(player.level());
        List<Integer> shown = shown(player.server, shop, now);
        for (int i = 0; i < GRID; i++) {
            ItemStack s = grid.getStackInSlot(i);
            int index = s.isEmpty() || !open ? -1 : match(shop, s, shown);
            if (index >= 0) {
                wanted.merge(index, s.getCount() / shop.item(index).stack.getCount(), Integer::sum);
            }
            if (!s.isEmpty()) {
                if (!player.getInventory().add(s)) {
                    player.drop(s, false);
                }
                grid.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        this.lots = 0;
        this.total = 0;
        this.capped = false;
        sellNext(player, shop, List.copyOf(wanted.entrySet()), 0, done);
    }

    private void sellNext(ServerPlayer player, Shop shop, List<Map.Entry<Integer, Integer>> cards, int at, Runnable done) {
        if (at >= cards.size()) {
            done.run();
            return;
        }
        var card = cards.get(at);
        if (card.getValue() <= 0) {
            sellNext(player, shop, cards, at + 1, done);
            return;
        }
        Shops.sell(player.server, player, shop, card.getKey(), card.getValue(), false, o -> {
            if (o.ok()) {
                lots += o.lots();
                total += o.amount();
            }
            capped |= o.key().equals("sold_capped") || o.key().equals("sell_full") || o.key().equals("counts_unreachable");
            sellNext(player, shop, cards, at + 1, done);
        });
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer sp) {
            sellAll(sp, () -> {
                if (lots > 0) {
                    sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.bsp_core.shop.sold_all", lots, Bank.format(total), Bank.format(Bank.pieces(sp.server, sp.getUUID()))).withStyle(net.minecraft.ChatFormatting.GOLD), false);
                }
                if (capped) {
                    sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.bsp_core.shop.sell_here_capped").withStyle(net.minecraft.ChatFormatting.YELLOW), false);
                }
            });
        }
    }
}
