package com.mrgregles.bsp_core.shop;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import javax.annotation.Nullable;

/**
 * "Sell here": a 14-slot grid the player drops items into. Closing it, or the Sell now key, sells
 * every full lot the shop buys at its sell prices and hands everything else back.
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
    public String name = "";

    public SellHereMenu(int id, Inventory inv, String shopId, @Nullable BlockPos at, boolean remote, @Nullable OpenHours hours) {
        super(ModMenus.SELL_HERE.get(), id);
        this.shopId = shopId;
        this.at = at;
        this.remote = remote;
        this.hours = hours;
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

    /** What the grid would fetch now: {lots, pieces}; coin-priced items are not sold here. */
    public long[] quote(Shop shop, long now) {
        long lots = 0, pieces = 0;
        for (int i = 0; i < GRID; i++) {
            ItemStack s = grid.getStackInSlot(i);
            if (s.isEmpty()) {
                continue;
            }
            ShopItem it = match(shop, s);
            if (it != null) {
                int n = s.getCount() / it.stack.getCount();
                lots += n;
                pieces += n * Shops.price(shop, it, false, now);
            }
        }
        return new long[]{lots, pieces};
    }

    @Nullable
    private static ShopItem match(Shop shop, ItemStack s) {
        if (shop.buyOnly) {
            return null;
        }
        for (ShopItem it : shop.all()) {
            if (it.sellable() && it.currency < 0 && ItemStack.isSameItemSameTags(s, it.stack)) {
                return it;
            }
        }
        return null;
    }

    /** Sells the grid's full lots and hands the rest back; the pieces are paid in one bank credit. */
    public void sellAll(ServerPlayer player) {
        Shop shop = Shops.ledger(player.server).shop(shopId);
        long now = System.currentTimeMillis();
        long paid = 0;
        int lots = 0;
        StringBuilder what = new StringBuilder();
        if (shop != null && (hours == null || hours.isOpen(player.level()))) {
            for (int i = 0; i < GRID; i++) {
                ItemStack s = grid.getStackInSlot(i);
                ShopItem it = s.isEmpty() ? null : match(shop, s);
                if (it == null) {
                    continue;
                }
                int n = s.getCount() / it.stack.getCount();
                if (n <= 0) {
                    continue;
                }
                s.shrink(n * it.stack.getCount());
                grid.setStackInSlot(i, s);
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
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer sp) {
            sellAll(sp);
            if (lots > 0) {
                sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.bsp_core.shop.sold_all", lots, Bank.format(total), Bank.format(Bank.pieces(sp.server, sp.getUUID()))).withStyle(net.minecraft.ChatFormatting.GOLD), false);
            }
        }
    }
}
