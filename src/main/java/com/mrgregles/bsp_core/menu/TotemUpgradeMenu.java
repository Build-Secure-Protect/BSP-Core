package com.mrgregles.bsp_core.menu;

import com.mrgregles.bsp_core.registry.ModMenus;
import com.mrgregles.bsp_core.totem.TotemInventories;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/** The upgrade tree opened by right-clicking with a totem in the main hand. Reads the held stack live. */
public class TotemUpgradeMenu extends AbstractContainerMenu {
    private final Player player;
    private final InteractionHand hand;
    /** The team surcharge percent and Pieces per copper value (two shorts), worked out on the server: its config reaches clients only at login. */
    private final ContainerData surcharge;

    public TotemUpgradeMenu(int id, Inventory inventory, InteractionHand hand) {
        super(ModMenus.TOTEM_UPGRADES.get(), id);
        this.player = inventory.player;
        this.hand = hand;
        this.surcharge = inventory.player instanceof net.minecraft.server.level.ServerPlayer sp ? new ContainerData() {
            @Override
            public int get(int i) {
                if (i == 0) {
                    java.util.UUID owner = com.mrgregles.bsp_core.totem.TotemOwner.fromStack(getTotem()).map(com.mrgregles.bsp_core.totem.TotemOwner::uuid).orElse(null);
                    return com.mrgregles.bsp_core.totem.TeamSurcharge.percent(sp.server, owner, sp.getUUID());
                }
                long rate = Math.min(Integer.MAX_VALUE, com.mrgregles.bsp_core.totem.TeamSurcharge.perCopper());
                return i == 1 ? (int) (rate & 0xFFFF) : (int) ((rate >>> 16) & 0xFFFF);
            }

            @Override
            public void set(int i, int value) {}

            @Override
            public int getCount() {
                return 3;
            }
        } : new SimpleContainerData(3);
        addDataSlots(surcharge);
    }

    /** The team surcharge percent on the held totem, from the server. */
    public int surchargePercent() {
        return surcharge.get(0);
    }

    /** Pieces per copper value for the surcharge, from the server. */
    public long surchargeRate() {
        return (surcharge.get(1) & 0xFFFFL) | ((surcharge.get(2) & 0xFFFFL) << 16);
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
