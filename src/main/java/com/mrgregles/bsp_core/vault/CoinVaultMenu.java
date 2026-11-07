package com.mrgregles.bsp_core.vault;

import com.mrgregles.bsp_core.coin.ShatterCoinItem;
import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.registry.ModMenus;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Menu of a whole joined Coin Vault. It shows one vault block's 27 slots at a time (a "page"), the
 * owner's interest, and the access and security settings. For a player who may not open the vault
 * it is {@link #locked}: it has no slots at all, so the contents are never sent to them, and only
 * offers the lockpick button.
 */
public class CoinVaultMenu extends AbstractContainerMenu {
    public static final int WIDTH = 196, SLOT_X = 17, SLOT_Y = 48, INV_Y = 144, HEIGHT = INV_Y + 82, LOCKED_HEIGHT = 122;
    public static final int BTN_PREV = 0, BTN_NEXT = 1, BTN_REDEEM = 2, BTN_LOCK = 3, BTN_ALARM = 4, BTN_PICK = 5, BTN_REMOVE = 10;
    public static final int TAB_STORAGE = 0, TAB_ACCESS = 1, TAB_SECURITY = 2, TAB_INTEREST = 3;
    private static final int D_PAGE = 0, D_XP = 1, D_TETRIUM = 2, D_ILLYRIUM = 3, D_FILL = 4, D_PICK_LEFT = 5, D_PICK_TOTAL = 6, D_PICK_STATE = 7,
            D_VALUE_LO = 8, D_VALUE_HI = 9, D_BLOCKS = 10, D_PRIZES = 11, D_MULT = 12, D_RATE_XP = 13, D_RATE_TETRIUM = 14, D_RATE_ILLYRIUM = 15,
            D_NEXT_ROLL = 16, D_PRIZE_STATE = 17, D_NET_LO = 18, D_NET_HI = 19, DATA_COUNT = 20;

    @Nullable
    private final CoinVaultBlockEntity vault;
    @Nullable
    private final List<CoinVaultBlockEntity> group;
    private final ItemStackHandler clientItems = new ItemStackHandler(CoinVaultBlockEntity.SLOTS);
    private final ContainerData data;
    private final boolean locked;
    private final int pages;
    private int page;
    /** Client only: which tab the screen shows. The vault slots are only active on the Storage tab. */
    public int tab = TAB_STORAGE;

    /** Client side. */
    public CoinVaultMenu(int id, Inventory inv, BlockPos pos, boolean locked, int pages, int page) {
        this(id, inv, inv.player.level().getBlockEntity(pos) instanceof CoinVaultBlockEntity v ? v : null, null, locked, Math.max(1, pages), page,
                new SimpleContainerData(DATA_COUNT));
    }

    public CoinVaultMenu(int id, Inventory inv, CoinVaultBlockEntity vault, List<CoinVaultBlockEntity> group, boolean locked) {
        this(id, inv, vault, group, locked, group.size(), Math.max(0, group.indexOf(vault)), null);
    }

    private CoinVaultMenu(int id, Inventory inv, @Nullable CoinVaultBlockEntity vault, @Nullable List<CoinVaultBlockEntity> group, boolean locked, int pages,
                          int page, @Nullable ContainerData clientData) {
        super(ModMenus.COIN_VAULT.get(), id);
        this.vault = vault;
        this.group = group;
        this.locked = locked;
        this.pages = pages;
        this.page = page;
        this.data = clientData != null ? clientData : serverData(inv.player);
        if (!locked) {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    addSlot(new VaultSlot(col + row * 9, SLOT_X + 1 + col * 18, SLOT_Y + 1 + row * 18));
                }
            }
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    addSlot(new Slot(inv, col + row * 9 + 9, SLOT_X + 1 + col * 18, INV_Y + row * 18));
                }
            }
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col, SLOT_X + 1 + col * 18, INV_Y + 58));
            }
        }
        addDataSlots(data);
    }

    /** A slot of whichever vault block is the current page. */
    private class VaultSlot extends SlotItemHandler {
        private final int index;

        VaultSlot(int index, int x, int y) {
            super(clientItems, index, x, y);
            this.index = index;
        }

        @Override
        public IItemHandler getItemHandler() {
            return group == null ? clientItems : group.get(Math.min(page, group.size() - 1)).getItems();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof ShatterCoinItem;
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return stack.getMaxStackSize();
        }

        @Override
        public boolean isActive() {
            return tab == TAB_STORAGE;
        }
    }

    private ContainerData serverData(Player player) {
        return new ContainerData() {
            private final int[] cache = new int[DATA_COUNT];
            private long stamp = -100;

            private void refresh() {
                if (vault == null || !(vault.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) || sl.getGameTime() - stamp < 10) {
                    return;
                }
                stamp = sl.getGameTime();
                if (!locked && vault.getOwner() != null) {
                    VaultLedger ledger = VaultLedger.get(sl.getServer());
                    int[] due = ledger.redeemable(vault.getOwner());
                    VaultLedger.Account account = ledger.account(vault.getOwner());
                    int value = account.value();
                    // synced values travel as shorts: a count past 32767 (a config without ceilings) shows as 32767, never negative
                    cache[D_XP] = Math.min(Short.MAX_VALUE, due[0]);
                    cache[D_TETRIUM] = Math.min(Short.MAX_VALUE, due[1]);
                    cache[D_ILLYRIUM] = Math.min(Short.MAX_VALUE, due[2]);
                    cache[D_FILL] = Math.round(ledger.fill(vault.getOwner()) * 1000);
                    cache[D_VALUE_LO] = value & 0xFFFF;
                    cache[D_VALUE_HI] = (value >>> 16) & 0xFFFF;
                    cache[D_BLOCKS] = account.vaultCount();
                    cache[D_PRIZES] = Math.min(Short.MAX_VALUE, ledger.prizeRolls(vault.getOwner()));
                    // synced values travel as shorts: the rates go as tenths (Illyrium as hundredths), clamped
                    double[] rates = ledger.rates(vault.getOwner());
                    cache[D_MULT] = scaled(VaultLedger.multiplier(value), 10);
                    cache[D_RATE_XP] = scaled(rates[0], 10);
                    cache[D_RATE_TETRIUM] = scaled(rates[1], 10);
                    cache[D_RATE_ILLYRIUM] = scaled(rates[2], 100);
                    cache[D_NEXT_ROLL] = Math.min(Short.MAX_VALUE, ledger.minutesToNextRoll(vault.getOwner()));
                    cache[D_PRIZE_STATE] = ledger.prizeState(vault.getOwner());
                    int network = ledger.networkValue(vault.getOwner());
                    cache[D_NET_LO] = network & 0xFFFF;
                    cache[D_NET_HI] = (network >>> 16) & 0xFFFF;
                }
            }

            @Override
            public int get(int i) {
                if (vault == null) {
                    return 0;
                }
                refresh();
                return switch (i) {
                    case D_PAGE -> page;
                    case D_PICK_LEFT -> (picking() == null ? 0 : picking().pickLeft() + 19) / 20;
                    case D_PICK_TOTAL -> picking() == null ? vault.pickSeconds() : picking().pickTotal() / 20;
                    case D_PICK_STATE -> picking() == null ? 0 : picking().isPicking(player) ? 1 : 2;
                    default -> cache[i];
                };
            }

            @Override
            public void set(int i, int value) {}

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    /** The block of this vault that is being picked, if any. */
    @Nullable
    private CoinVaultBlockEntity picking() {
        if (group != null) {
            for (CoinVaultBlockEntity v : group) {
                if (v.pickLeft() > 0) {
                    return v;
                }
            }
        }
        return null;
    }

    // --- synced values ---

    @Nullable
    public CoinVaultBlockEntity vault() {
        return vault;
    }

    public boolean locked() {
        return locked;
    }

    public int pages() {
        return pages;
    }

    public int page() {
        return data.get(D_PAGE);
    }

    public int dueXp() {
        return data.get(D_XP);
    }

    public int dueTetrium() {
        return data.get(D_TETRIUM);
    }

    public int dueIllyrium() {
        return data.get(D_ILLYRIUM);
    }

    /** {@code amount} times {@code scale}, rounded and held to what a synced value can carry. */
    public static int scaled(double amount, int scale) {
        return (int) Math.max(0, Math.min(Short.MAX_VALUE, Math.round(amount * scale)));
    }

    /** The owner's interest multiplier (x1.5 comes as 1.5). */
    public double multiplier() {
        return data.get(D_MULT) / 10.0;
    }

    /** XP levels, Tetrium Ingots and Illyrium Ingots the owner's vaults on this server earn a day. */
    public double[] dailyRates() {
        return new double[]{data.get(D_RATE_XP) / 10.0, data.get(D_RATE_TETRIUM) / 10.0, data.get(D_RATE_ILLYRIUM) / 100.0};
    }

    /** Minutes to the next prize roll, or -1 when none is building up here. */
    public int nextRollMinutes() {
        return (short) data.get(D_NEXT_ROLL);
    }

    /** One of {@link VaultLedger#PRIZE_EARNING} and the other prize states. */
    public int prizeState() {
        return data.get(D_PRIZE_STATE);
    }

    /** Coin value the owner keeps in vaults on every server of the network (this server alone without one). */
    public int networkValue() {
        return (data.get(D_NET_LO) & 0xFFFF) | ((data.get(D_NET_HI) & 0xFFFF) << 16);
    }

    /** Whole prize rolls waiting for the owner. */
    public int duePrizes() {
        return data.get(D_PRIZES);
    }

    public float fill() {
        return data.get(D_FILL) / 1000f;
    }

    public int pickLeft() {
        return data.get(D_PICK_LEFT);
    }

    public int pickTotal() {
        return Math.max(1, data.get(D_PICK_TOTAL));
    }

    /** 0 nobody is picking, 1 this player is, 2 someone else is. */
    public int pickState() {
        return data.get(D_PICK_STATE);
    }

    public int totalValue() {
        return (data.get(D_VALUE_LO) & 0xFFFF) | ((data.get(D_VALUE_HI) & 0xFFFF) << 16);
    }

    public int ownedBlocks() {
        return data.get(D_BLOCKS);
    }

    /** Runs on the server. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (vault == null || group == null || !(player instanceof ServerPlayer sp)) {
            return false;
        }
        if (locked) {
            if (id == BTN_PICK) {
                vault.startPick(sp);
                return true;
            }
            return false;
        }
        if (id == BTN_PREV || id == BTN_NEXT) {
            page = Math.floorMod(page + (id == BTN_NEXT ? 1 : -1), group.size());
            return true;
        }
        if (id == BTN_REDEEM && vault.isOwner(sp)) {
            int[] due = VaultLedger.get(sp.server).redeem(sp.getUUID());
            if (due[0] > 0) {
                sp.giveExperienceLevels(due[0]);
            }
            give(sp, new ItemStack(ModItems.TETRIUM_INGOT.get()), due[1]);
            give(sp, new ItemStack(ModItems.ILLYRIUM_INGOT.get()), due[2]);
            if (due[0] + due[1] + due[2] > 0) {
                sp.displayClientMessage(Component.translatable("message.bsp_core.vault.redeemed", due[0], due[1], due[2]).withStyle(ChatFormatting.GOLD), false);
            }
            VaultLedger ledger = VaultLedger.get(sp.server);
            int rolls = ledger.prizeRolls(sp.getUUID());
            if (rolls > 0) {
                // rolled now, one at a time; an empty table pays none and the rolls keep waiting
                int paid = VaultPrizes.get(sp.server).pay(sp, rolls, sp.getRandom());
                ledger.takePrizeRolls(sp.getUUID(), paid);
                if (paid == 0) {
                    sp.displayClientMessage(Component.translatable("message.bsp_core.vault.prize_empty", rolls).withStyle(ChatFormatting.GRAY), false);
                }
            }
            return true;
        }
        if (id == BTN_LOCK) {
            vault.buyLock(sp);
            return true;
        }
        if (id == BTN_ALARM) {
            vault.buyAlarm(sp);
            return true;
        }
        if (id >= BTN_REMOVE && id < BTN_REMOVE + CoinVaultBlockEntity.MAX_ACCESS && vault.mayManage(sp)) {
            vault.removeAccess(id - BTN_REMOVE);
            return true;
        }
        return false;
    }

    private static void give(ServerPlayer player, ItemStack item, int count) {
        while (count > 0) {
            ItemStack out = item.copyWithCount(Math.min(count, item.getMaxStackSize()));
            count -= out.getCount();
            if (!player.getInventory().add(out)) {
                player.drop(out, false);
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (locked) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int n = CoinVaultBlockEntity.SLOTS;
        if (index < n) {
            if (!moveItemStackTo(stack, n, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!(stack.getItem() instanceof ShatterCoinItem) || !moveItemStackTo(stack, 0, n, false)) {
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
        if (vault == null || vault.isRemoved()) {
            return false;
        }
        if (!player.level().isClientSide) {
            // a block was added or removed, or this player's access changed: reopen for the new state
            if (vault.group().size() != pages || vault.mayOpen(player) == locked) {
                return false;
            }
        }
        return player.distanceToSqr(vault.getBlockPos().getCenter()) <= 64.0;
    }
}
