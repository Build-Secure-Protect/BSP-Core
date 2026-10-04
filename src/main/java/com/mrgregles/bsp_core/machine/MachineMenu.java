package com.mrgregles.bsp_core.machine;

import com.mrgregles.bsp_core.coin.CoinFactoryBlockEntity.SideMode;
import com.mrgregles.bsp_core.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

import javax.annotation.Nullable;

/** One menu for every {@link MachineBlockEntity}; the machine's layout decides the slots. */
public class MachineMenu extends AbstractContainerMenu {
    public static final int INV_Y = 152, INV_X = 82, IN_X = 14, OUT_X = 152, PIPE_Y = 30, FUEL_X = 152, FUEL_Y = 74, UP_X = 196, UP_Y = 124, BTN_POWER = 6;

    @Nullable
    private final MachineBlockEntity machine;
    private final ContainerData data;
    private final BlockPos pos;
    private final int machineSlots;

    public MachineMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, inv.player.level().getBlockEntity(pos) instanceof MachineBlockEntity m ? m : null,
                new SimpleContainerData(MachineBlockEntity.DATA_COUNT), pos);
    }

    public MachineMenu(int id, Inventory inv, MachineBlockEntity machine, ContainerData data) {
        this(id, inv, machine, data, machine.getBlockPos());
    }

    private MachineMenu(int id, Inventory inv, @Nullable MachineBlockEntity machine, ContainerData data, BlockPos pos) {
        super(ModMenus.MACHINE.get(), id);
        this.machine = machine;
        this.data = data;
        this.pos = pos;
        int n = 0;
        if (machine != null) {
            // Slots are placed by role for the HUD layout: inputs on the left of the pipeline, outputs on the right,
            // the fuel or fluid container under the outputs, upgrades in their own row.
            int ins = 0, outs = 0, ups = 0;
            for (int i = 0; i < machine.layout().size(); i++) {
                MachineBlockEntity.Slot s = machine.layout().get(i);
                final boolean output = s.role() == MachineBlockEntity.Role.OUTPUT;
                int sx, sy;
                switch (s.role()) {
                    case INPUT -> { sx = IN_X + 20 * ins++; sy = PIPE_Y; }
                    case OUTPUT -> { sx = OUT_X + 20 * outs++; sy = PIPE_Y; }
                    case FUEL -> { sx = FUEL_X; sy = FUEL_Y; }
                    default -> { sx = UP_X + 20 * ups++; sy = UP_Y; }
                }
                addSlot(new SlotItemHandler(machine.getItems(), i, sx, sy) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return !output && super.mayPlace(stack);
                    }
                });
                n++;
            }
        }
        this.machineSlots = n;
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

    @Nullable
    public MachineBlockEntity getMachine() {
        return machine;
    }

    public float progress() {
        return Math.min(1f, data.get(0) / (float) Math.max(1, data.get(1)));
    }

    public int secondsLeft() {
        return Math.max(0, (data.get(1) - data.get(0) + 19) / 20);
    }

    public boolean working() {
        return data.get(0) > 0;
    }

    public float burn() {
        return data.get(3) <= 0 ? 0f : Math.min(1f, data.get(2) / (float) data.get(3));
    }

    public int fluid() {
        return (data.get(10) & 0xFFFF) | ((data.get(11) & 0xFFFF) << 16);
    }

    public int fluidCapacity() {
        return (data.get(12) & 0xFFFF) | ((data.get(13) & 0xFFFF) << 16);
    }

    /** 0-1000 while an RF upgrade is fitted, -1 otherwise. */
    public int energyPermille() {
        return (short) data.get(15);
    }

    public boolean enabled() {
        return data.get(16) != 0;
    }

    public boolean formed() {
        return data.get(14) != 0;
    }

    public SideMode side(Direction d) {
        return SideMode.values()[Math.floorMod(data.get(4 + d.get3DDataValue()), 4)];
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (machine != null && id >= 0 && id < 6) {
            machine.cycleSide(Direction.from3DDataValue(id));
            return true;
        }
        if (machine != null && id == BTN_POWER) {
            machine.toggleEnabled();
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
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, machineSlots, false)) { // mayPlace sends it to the first slot that accepts it
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
        return machine != null && !machine.isRemoved()
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
