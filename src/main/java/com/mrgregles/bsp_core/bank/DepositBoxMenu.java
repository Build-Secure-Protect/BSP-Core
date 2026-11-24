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
import java.util.Map;

/**
 * The deposit box menu. While the rent is paid it shows the 18 box slots (the locked ones take
 * nothing) over the player's inventory; when not, it has no box slots at all, so the contents never
 * reach a player who cannot open it, and only offers the rent keys.
 *
 * <p>While season prizes wait ({@link PrizeBox}), a gold drawer under the screen holds them, up to three rows of nine, rented box
 * or not, with the inventory under it (owner pick P1 A). The drawer is take-out only; each take is written at once.
 */
public class DepositBoxMenu extends AbstractContainerMenu implements BankClientMenu {
    public static final int WIDTH = 300, SLOT_X = 24, SLOT_Y = 48, INV_X = 24, INV_Y = 132, HEIGHT = INV_Y + 82, LOCKED_HEIGHT = 150;
    /** The green screen's height open and locked (the screen draws it), the drawer's most rows and slots, its title band. */
    public static final int SCREEN_OPEN = 116, SCREEN_LOCKED = 118, PRIZE_COLS = 9, PRIZE_ROWS = 3, PRIZES_MAX = PRIZE_COLS * PRIZE_ROWS, DRAWER_TITLE = 18;

    /** What the drawer shows: the stacks with each one's row id, place and season, and how many more wait than fit. */
    public record Prizes(List<ItemStack> stacks, long[] ids, int[] places, int[] seasons, int more) {
        public static final Prizes NONE = new Prizes(List.of(), new long[0], new int[0], new int[0], 0);

        public static Prizes of(List<PrizeBox.Entry> entries) {
            if (entries == null || entries.isEmpty()) {
                return NONE;
            }
            int n = Math.min(PRIZES_MAX, entries.size());
            List<ItemStack> stacks = new ArrayList<>();
            long[] ids = new long[n];
            int[] places = new int[n], seasons = new int[n];
            for (int i = 0; i < n; i++) {
                PrizeBox.Entry e = entries.get(i);
                stacks.add(e.stack().copy());
                ids[i] = e.id();
                places[i] = e.place();
                seasons[i] = e.season();
            }
            return new Prizes(stacks, ids, places, seasons, entries.size() - n);
        }

        public int count() {
            return places.length;
        }

        public void write(net.minecraft.network.FriendlyByteBuf buf) {
            buf.writeVarInt(count());
            for (int i = 0; i < count(); i++) {
                buf.writeVarInt(places[i]);
                buf.writeVarInt(seasons[i]);
            }
            buf.writeVarInt(more);
        }

        /** Client: the places and seasons; the stacks come with the menu's own slot sync. */
        public static Prizes read(net.minecraft.network.FriendlyByteBuf buf) {
            int n = Math.min(PRIZES_MAX, buf.readVarInt());
            int[] places = new int[n], seasons = new int[n];
            List<ItemStack> stacks = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                places[i] = buf.readVarInt();
                seasons[i] = buf.readVarInt();
                stacks.add(ItemStack.EMPTY);
            }
            return new Prizes(stacks, new long[n], places, seasons, buf.readVarInt());
        }
    }

    public final BlockPos pos;
    public final boolean open;
    public final Prizes prizes;
    /** Where the drawer and the inventory sit, and how tall the whole screen is (both sides build the same). */
    public final int drawerY, drawerRows, invY, height;
    public final int prizeStart;
    private final net.minecraft.world.SimpleContainer prizeItems;
    private final ItemStack[] written;
    @Nullable
    private final ServerPlayer owner;
    private final IItemHandler items;
    private final int slotsAtOpen;
    public final BankAccount account = new BankAccount();
    public final List<BankTransaction> log = new ArrayList<>();
    public String messageKey = "";
    public String[] messageArgs = new String[0];
    public int messageSerial;

    /** Client side. */
    public DepositBoxMenu(int id, Inventory inv, BlockPos pos, boolean open, int slots, Prizes prizes) {
        this(id, inv, pos, open, slots, new ItemStackHandler(DepositBox.SLOTS_MAX), prizes);
    }

    public DepositBoxMenu(int id, Inventory inv, BlockPos pos, boolean open, int slots, IItemHandler items, Prizes prizes) {
        super(ModMenus.DEPOSIT_BOX.get(), id);
        this.pos = pos;
        this.open = open;
        this.items = items;
        this.slotsAtOpen = slots;
        this.account.boxSlots = slots;
        this.prizes = prizes;
        this.owner = inv.player instanceof ServerPlayer sp ? sp : null;
        int n = prizes.count();
        this.drawerRows = n == 0 ? 0 : (n + PRIZE_COLS - 1) / PRIZE_COLS;
        this.drawerY = 10 + (open ? SCREEN_OPEN : SCREEN_LOCKED) + 8;
        int drawerH = drawerHeight(drawerRows);
        this.invY = n == 0 ? INV_Y : drawerY + drawerH + 20;
        this.height = n == 0 ? (open ? HEIGHT : LOCKED_HEIGHT) : invY + 82;
        this.prizeItems = new net.minecraft.world.SimpleContainer(Math.max(1, n));
        this.written = new ItemStack[n];
        for (int i = 0; i < n; i++) {
            prizeItems.setItem(i, prizes.stacks().get(i).copy());
            written[i] = prizes.stacks().get(i).copy();
        }
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
        }
        if (open || n > 0) {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    addSlot(new Slot(inv, col + row * 9 + 9, INV_X + 1 + col * 18, invY + row * 18));
                }
            }
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col, INV_X + 1 + col * 18, invY + 58));
            }
        }
        this.prizeStart = this.slots.size();
        for (int i = 0; i < n; i++) {
            int cell = i; // not "index": inside a Slot that name is the slot's own menu index
            addSlot(new Slot(prizeItems, i, SLOT_X + 1 + (i % PRIZE_COLS) * 18, drawerY + DRAWER_TITLE + (i / PRIZE_COLS) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false; // take out only
                }

                @Override
                public void setChanged() {
                    super.setChanged();
                    prizeChanged(cell);
                }
            });
        }
    }

    /** The drawer's height for a number of prize rows. */
    public static int drawerHeight(int rows) {
        return rows == 0 ? 0 : DRAWER_TITLE + rows * 18 + 8;
    }

    /** Whether a menu slot index is one of the drawer's. */
    public boolean isPrize(int index) {
        return index >= prizeStart && index < prizeStart + prizes.count();
    }

    /** Server: a drawer slot changed; what is left of its row is written at once (a crash before the close cannot hand it out twice). */
    private void prizeChanged(int index) {
        if (owner == null) {
            return;
        }
        ItemStack now = prizeItems.getItem(index);
        if (ItemStack.matches(now, written[index])) {
            return;
        }
        written[index] = now.copy();
        PrizeBox.taken(owner.server, owner.getUUID(), Map.of(prizes.ids()[index], now.copy()));
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
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem(), original = stack.copy();
        int n = open ? DepositBox.SLOTS_MAX : 0, invEnd = prizeStart;
        boolean moved;
        if (isPrize(index) || index < n) {
            moved = moveItemStackTo(stack, n, invEnd, true); // a prize or a box stack to the inventory
        } else {
            moved = open && DepositBox.allowed(stack) && moveItemStackTo(stack, 0, account.boxSlots, false); // never into the drawer
        }
        if (!moved) {
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
            for (int i = 0; i < prizes.count(); i++) {
                prizeChanged(i);
            }
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
