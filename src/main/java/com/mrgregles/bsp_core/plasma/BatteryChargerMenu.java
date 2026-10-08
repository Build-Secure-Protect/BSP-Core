package com.mrgregles.bsp_core.plasma;

import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.registry.ModMenus;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

/** The Battery Charger's screen: one slot for the battery or cell, the tank and fill gauges, and a STAMP switch per power the interface offers. */
public class BatteryChargerMenu extends AbstractContainerMenu {
    public static final int WIDTH = 300, SLOT_X = 24, SLOT_Y = 44, INV_X = 69, INV_Y = 164, HEIGHT = INV_Y + 82;
    public static final int D_TANK = 0, D_FILL = 1, D_SIGNAL = 2, D_FILLING = 3, D_KIND = 4, D_MAX = 5, D_EDIT = 6, D_DELIVERED = 7, D_OFFERED = 8;
    private static final int NB = PlasmaItems.BATTERY_POWERS.length, NC = PlasmaItems.CELL_POWERS.length, D_STAMPED = D_OFFERED + NB + NC, DATA_COUNT = D_STAMPED + NB + NC;

    private final BatteryChargerBlockEntity charger;
    private final ContainerData data;

    public BatteryChargerMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, inv.player.level().getBlockEntity(pos) instanceof BatteryChargerBlockEntity c ? c : new BatteryChargerBlockEntity(pos, ModBlocks.BATTERY_CHARGER.get().defaultBlockState()), new SimpleContainerData(DATA_COUNT));
    }

    public BatteryChargerMenu(int id, Inventory inv, BatteryChargerBlockEntity charger) {
        this(id, inv, charger, serverData(charger, inv.player));
    }

    private BatteryChargerMenu(int id, Inventory inv, BatteryChargerBlockEntity charger, ContainerData data) {
        super(ModMenus.BATTERY_CHARGER.get(), id);
        this.charger = charger;
        this.data = data;
        addSlot(new SlotItemHandler(charger.getItems(), 0, SLOT_X, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return PlasmaItems.isChargeable(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, INV_X + col * 18, INV_Y + 58));
        }
        addDataSlots(data);
    }

    /** The powers listed for the item in the slot: a battery's or a cell's. Index into {@code offered}/{@code stamped} by {@link #row(int)}. */
    public static Buff[] list(int kind) {
        return kind == 2 ? PlasmaItems.CELL_POWERS : PlasmaItems.BATTERY_POWERS;
    }

    private static int row(int kind, int i) {
        return kind == 2 ? NB + i : i;
    }

    private static ContainerData serverData(BatteryChargerBlockEntity charger, Player player) {
        return new ContainerData() {
            @Override
            public int get(int i) {
                ItemStack stack = charger.getItems().getStackInSlot(0);
                int kind = PlasmaItems.isBattery(stack) ? 1 : PlasmaItems.isCell(stack) ? 2 : 0;
                if (i >= D_STAMPED) {
                    int k = i - D_STAMPED;
                    Buff b = k < NB ? PlasmaItems.BATTERY_POWERS[k] : PlasmaItems.CELL_POWERS[k - NB];
                    return kind == 0 ? 0 : PlasmaItems.powers(stack)[b.ordinal()];
                }
                if (i >= D_OFFERED) {
                    int k = i - D_OFFERED;
                    Buff b = k < NB ? PlasmaItems.BATTERY_POWERS[k] : PlasmaItems.CELL_POWERS[k - NB];
                    return charger.offered(b);
                }
                return switch (i) {
                    case D_TANK -> (int) (1000L * charger.tank() / Math.max(1, charger.capacity()));
                    case D_FILL -> kind == 0 ? -1 : (int) (1000L * PlasmaItems.stored(stack) / Math.max(1, PlasmaItems.capacity(stack)));
                    case D_SIGNAL -> charger.hasSignal() ? 1 : 0;
                    case D_FILLING -> charger.isFilling() ? 1 : 0;
                    case D_KIND -> kind;
                    case D_MAX -> kind == 0 ? 0 : PlasmaItems.maxPowers(stack);
                    case D_EDIT -> charger.mayEdit(player) ? 1 : 0;
                    case D_DELIVERED -> charger.delivered();
                    default -> 0;
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

    public int tankPermille() {
        return data.get(D_TANK);
    }

    public int fillPermille() {
        return data.get(D_FILL);
    }

    public boolean signal() {
        return data.get(D_SIGNAL) == 1;
    }

    public boolean filling() {
        return data.get(D_FILLING) == 1;
    }

    public int kind() {
        return data.get(D_KIND);
    }

    public int maxPowers() {
        return data.get(D_MAX);
    }

    public boolean mayEdit() {
        return data.get(D_EDIT) == 1;
    }

    public int delivered() {
        return data.get(D_DELIVERED);
    }

    public int offered(int i) {
        return data.get(D_OFFERED + row(kind(), i));
    }

    public int stamped(int i) {
        return data.get(D_STAMPED + row(kind(), i));
    }

    public int stampedCount() {
        int n = 0;
        for (int i = 0; i < list(kind()).length; i++) {
            n += stamped(i) > 0 ? 1 : 0;
        }
        return n;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!charger.mayEdit(player)) {
            return false;
        }
        Buff[] list = list(kind());
        if (id >= 0 && id < list.length) {
            charger.stamp(list[id]);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem(), copy = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!PlasmaItems.isChargeable(stack) || !moveItemStackTo(stack, 0, 1, false)) {
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
    public boolean stillValid(Player player) {
        return player.distanceToSqr(charger.getBlockPos().getX() + 0.5, charger.getBlockPos().getY() + 0.5, charger.getBlockPos().getZ() + 0.5) <= 64;
    }
}
