package com.mrgregles.bsp_core.bank;

import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.BankDataPacket;
import com.mrgregles.bsp_core.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The deposit box menu. While the rent is paid it shows the 18 box slots (the locked ones take
 * nothing) over the player's inventory; when not, it has no box slots at all, so the contents never
 * reach a player who cannot open it, and only offers the rent keys.
 */
public class DepositBoxMenu extends AbstractContainerMenu implements BankClientMenu {
    public static final int WIDTH = 300, SLOT_X = 24, SLOT_Y = 48, INV_X = 24, INV_Y = 132, HEIGHT = INV_Y + 82, LOCKED_HEIGHT = 150;

    public final BlockPos pos;
    public final boolean open;
    private final IItemHandler items;
    private final int slotsAtOpen;
    public final BankAccount account = new BankAccount();
    public final List<BankTransaction> log = new ArrayList<>();
    public String messageKey = "";
    public String[] messageArgs = new String[0];
    public int messageSerial;

    /** Client side. */
    public DepositBoxMenu(int id, Inventory inv, BlockPos pos, boolean open, int slots) {
        this(id, inv, pos, open, slots, new ItemStackHandler(DepositBox.SLOTS_MAX));
    }

    public DepositBoxMenu(int id, Inventory inv, BlockPos pos, boolean open, int slots, IItemHandler items) {
        super(ModMenus.DEPOSIT_BOX.get(), id);
        this.pos = pos;
        this.open = open;
        this.items = items;
        this.slotsAtOpen = slots;
        this.account.boxSlots = slots;
        if (open) {
            for (int i = 0; i < DepositBox.SLOTS_MAX; i++) {
                int index = i;
                addSlot(new SlotItemHandler(items, i, SLOT_X + 1 + (i % 9) * 18, SLOT_Y + 1 + (i / 9) * 18) {
                    @Override
                    public boolean mayPlace(@javax.annotation.Nonnull ItemStack stack) {
                        return index < account.boxSlots && DepositBox.allowed(stack) && super.mayPlace(stack);
                    }

                    @Override
                    public boolean mayPickup(Player player) {
                        return index < account.boxSlots && super.mayPickup(player);
                    }

                    @Override
                    public boolean isActive() {
                        return index < account.boxSlots;
                    }
                });
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
    }

    @Override
    public void receive(BankAccount a, List<BankTransaction> log, List<String> found, String key, String[] args) {
        this.account.copyFrom(a);
        this.account.boxSlots = Math.max(this.account.boxSlots, slotsAtOpen);
        this.log.clear();
        this.log.addAll(log);
        if (!key.isEmpty()) {
            messageKey = key;
            messageArgs = args;
            messageSerial++;
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!open) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem(), original = stack.copy();
        int n = DepositBox.SLOTS_MAX;
        if (index < n ? !moveItemStackTo(stack, n, slots.size(), true) : !DepositBox.allowed(stack) || !moveItemStackTo(stack, 0, account.boxSlots, false)) {
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
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < 64;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer sp) {
            DepositBox.saved(sp.server, sp.getUUID());
        }
    }

    /** Server: sends the account (with the box state) and a message to the open deposit box screen. */
    public static void sync(ServerPlayer player, String messageKey, @Nullable String[] args) {
        if (player.containerMenu instanceof DepositBoxMenu) {
            BSPNetwork.sendTo(player, new BankDataPacket(Bank.account(player.server, player.getUUID()), List.of(), messageKey, args == null ? new String[0] : args, List.of()));
        }
    }
}
