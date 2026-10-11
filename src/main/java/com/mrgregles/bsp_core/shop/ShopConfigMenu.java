package com.mrgregles.bsp_core.shop;

import com.mrgregles.bsp_core.registry.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The shop configurator's menu (the Workbench): no slots. The client holds the list of shops and a
 * full copy of the selected shop, both sent by {@code ShopConfigDataPacket}; every edit goes up as a
 * {@code ShopConfigActionPacket} and the server saves and answers. {@code editable} is false for
 * moderators: they see everything and change nothing.
 */
public class ShopConfigMenu extends AbstractContainerMenu {
    public record Summary(String id, String name, Shop.Kind kind, int items, boolean remote) {}

    public final boolean editable;
    public final List<Summary> list = new ArrayList<>();
    @Nullable
    public Shop shop;
    public String messageKey = "";
    public String[] messageArgs = new String[0];
    public int messageSerial;

    public ShopConfigMenu(int id, Inventory inv, boolean editable) {
        super(ModMenus.SHOP_CONFIG.get(), id);
        this.editable = editable;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
