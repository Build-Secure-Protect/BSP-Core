package com.mrgregles.bsp_core.admin;

import com.mrgregles.bsp_core.registry.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * The season's prizes: ten slots each for first, second and third place. An admin puts real items
 * in (from the creative inventory or their own); they stay here until the season ends, when each
 * row is handed to the player who finished in that place and the rows are emptied for the next season.
 * A fourth row holds the holder reward, which is copied to every totem holder at each payout and stays.
 */
public class SeasonRewardsMenu extends AbstractContainerMenu {
    public static final int WIDTH = 236, ROW_X = 46, ROW_Y = 26, ROW_GAP = 22, INV_X = 37, INV_Y = 132, HEIGHT = INV_Y + 82;

    private final boolean editable;

    public SeasonRewardsMenu(int id, Inventory inv, boolean editable) {
        this(id, inv, new ItemStackHandler(SeasonData.REWARD_SLOTS), editable);
    }

    /** {@code editable} is false for moderators: they see the prizes but cannot move anything in or out. */
    public SeasonRewardsMenu(int id, Inventory inv, IItemHandler rewards, boolean editable) {
        super(ModMenus.SEASON_REWARDS.get(), id);
        this.editable = editable;
        for (int place = 0; place < SeasonData.ROWS; place++) {
            for (int col = 0; col < SeasonData.PER_PLACE; col++) {
                addSlot(new SlotItemHandler(rewards, place * SeasonData.PER_PLACE + col, ROW_X + col * 18, ROW_Y + place * ROW_GAP) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return editable && super.mayPlace(stack);
                    }

                    @Override
                    public boolean mayPickup(Player player) {
                        return editable && super.mayPickup(player);
                    }
                });
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, INV_X + col * 18, INV_Y + 58));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!editable || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem(), original = stack.copy();
        int n = SeasonData.REWARD_SLOTS;
        if (index < n ? !moveItemStackTo(stack, n, slots.size(), true) : !moveItemStackTo(stack, 0, n, false)) {
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
        return player.level().isClientSide || (editable ? Admins.isAdmin(player) : Admins.isModerator(player));
    }

    public boolean editable() {
        return editable;
    }
}
