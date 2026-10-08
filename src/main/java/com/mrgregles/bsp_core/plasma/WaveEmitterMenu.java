package com.mrgregles.bsp_core.plasma;

import com.mrgregles.bsp_core.registry.ModMenus;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Wave Emitter's screen: one slot for the Power Cell, kept in the emitter's tag, and the player's inventory.
 * Button 0 switches the emitter on or off. The screen reads the cell from the slot and the switch from the held emitter.
 */
public class WaveEmitterMenu extends AbstractContainerMenu {
    public static final int WIDTH = 230, SLOT_X = 22, SLOT_Y = 46, INV_X = 34, INV_Y = 110, HEIGHT = INV_Y + 82, BTN_TOGGLE = 0;
    private final Player player;
    private final InteractionHand hand;
    private final SimpleContainer box = new SimpleContainer(1);

    public WaveEmitterMenu(int id, Inventory inv, InteractionHand hand) {
        super(ModMenus.WAVE_EMITTER.get(), id);
        this.player = inv.player;
        this.hand = hand;
        box.setItem(0, WaveEmitterItem.cell(emitter()));
        box.addListener(c -> {
            ItemStack emitter = emitter();
            if (!emitter.isEmpty()) {
                WaveEmitterItem.setCell(emitter, c.getItem(0));
                if (c.getItem(0).isEmpty()) {
                    WaveEmitterItem.setOn(emitter, false);
                }
            }
        });
        addSlot(new Slot(box, 0, SLOT_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return PlasmaItems.isCell(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18) {
                    @Override
                    public boolean mayPickup(Player p) {
                        return getItem() != emitter(); // the emitter stays where it is while its screen is open
                    }
                });
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, INV_X + col * 18, INV_Y + 58) {
                @Override
                public boolean mayPickup(Player p) {
                    return getItem() != emitter();
                }
            });
        }
    }

    public ItemStack emitter() {
        ItemStack held = player.getItemInHand(hand);
        return held.getItem() instanceof WaveEmitterItem ? held : ItemStack.EMPTY;
    }

    public InteractionHand hand() {
        return hand;
    }

    @Override
    public boolean clickMenuButton(Player p, int id) {
        ItemStack emitter = emitter();
        if (id != BTN_TOGGLE || emitter.isEmpty() || WaveEmitterItem.cell(emitter).isEmpty()) {
            return false;
        }
        WaveEmitterItem.setOn(emitter, !WaveEmitterItem.isOn(emitter));
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem(), copy = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!PlasmaItems.isCell(stack) || stack == emitter() || !moveItemStackTo(stack, 0, 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player p) {
        return !emitter().isEmpty();
    }
}
