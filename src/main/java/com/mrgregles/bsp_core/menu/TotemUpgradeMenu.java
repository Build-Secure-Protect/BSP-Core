package com.mrgregles.bsp_core.menu;

import com.mrgregles.bsp_core.registry.ModMenus;
import com.mrgregles.bsp_core.totem.TotemInventories;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** The upgrade tree opened by right-clicking with a totem in the main hand. Reads the held stack live. */
public class TotemUpgradeMenu extends AbstractContainerMenu {
    private final Player player;
    private final InteractionHand hand;

    public TotemUpgradeMenu(int id, Inventory inventory, InteractionHand hand) {
        super(ModMenus.TOTEM_UPGRADES.get(), id);
        this.player = inventory.player;
        this.hand = hand;
    }

    public InteractionHand getHand() {
        return hand;
    }

    /** The totem being upgraded, or EMPTY if the player is no longer holding one in that hand. */
    public ItemStack getTotem() {
        ItemStack held = player.getItemInHand(hand);
        return TotemInventories.isTotem(held) ? held : ItemStack.EMPTY;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return !getTotem().isEmpty();
    }
}
