package com.mrgregles.bsp_core.shop;

import com.mrgregles.bsp_core.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The player's shop screen menu: no slots; the shop's visible page comes down in a
 * {@code ShopDataPacket} and actions go up in a {@code ShopActionPacket}. Opened at a vendor or
 * stall (the player stays within reach), from the Shopping Network (no position) or by command.
 */
public class ShopMenu extends AbstractContainerMenu {
    /** One card on the page as the client sees it. */
    public record Card(int index, ItemStack stack, long buy, long sell, int currency, int stock, boolean instant, String section) {}

    public final String shopId;
    @Nullable
    public final BlockPos at;
    public final boolean remote;
    @Nullable
    public final OpenHours hours;
    /** Test mode (the configurator's preview): buys and sells report what would happen and move nothing. */
    public final boolean test;
    // client-side copies
    public String name = "", path = "";
    public Shop.Kind kind = Shop.Kind.SHOP;
    public boolean buyOnly, open = true, admin;
    public int offerPercent, page, pages = 1, untilOpen;
    public long offerEnd, rotatesIn, balance;
    public String opensAt = "";
    public final List<Card> cards = new ArrayList<>();
    public String messageKey = "";
    public String[] messageArgs = new String[0];
    public int messageSerial;

    public ShopMenu(int id, Inventory inv, String shopId, @Nullable BlockPos at, boolean remote, @Nullable OpenHours hours, boolean test) {
        super(ModMenus.SHOP.get(), id);
        this.shopId = shopId;
        this.at = at;
        this.remote = remote;
        this.hours = hours;
        this.test = test;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return at == null || player.distanceToSqr(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5) < 64;
    }
}
