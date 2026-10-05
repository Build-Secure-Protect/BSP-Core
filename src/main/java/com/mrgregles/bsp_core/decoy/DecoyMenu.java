package com.mrgregles.bsp_core.decoy;

import com.mrgregles.bsp_core.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

import javax.annotation.Nullable;

/** The owner's view of a Decoy Totem: its eight sockets laid out as a tree, its state, and the Repair button. */
public class DecoyMenu extends AbstractContainerMenu {
    public static final int WIDTH = 300, TREE_H = 138, INV_X = 69, INV_Y = 152, HEIGHT = INV_Y + 82, BTN_REPAIR = 0;
    /** Top-left of each socket, in tree order: range up the middle, traps up the left, casings up the right. */
    public static final int[][] SOCKET = {{82, 76}, {82, 52}, {82, 28}, {32, 112}, {32, 88}, {32, 64}, {132, 112}, {132, 88}};
    public static final int CORE_X = 82, CORE_Y = 100;
    private static final int D_RF = 0, D_STATE = 1, D_HITS = 2, D_RANGE = 3, D_PLACED = 4, D_MAX = 5, DATA_COUNT = 6;

    @Nullable
    private final DecoyTotemBlockEntity decoy;
    private final ContainerData data;

    public DecoyMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, inv.player.level().getBlockEntity(pos) instanceof DecoyTotemBlockEntity d ? d : new DecoyTotemBlockEntity(pos, com.mrgregles.bsp_core.registry.ModBlocks.DECOY_TOTEM.get().defaultBlockState()),
                new SimpleContainerData(DATA_COUNT));
    }

    public DecoyMenu(int id, Inventory inv, DecoyTotemBlockEntity decoy) {
        this(id, inv, decoy, serverData(decoy, inv.player));
    }

    private DecoyMenu(int id, Inventory inv, DecoyTotemBlockEntity decoy, ContainerData data) {
        super(ModMenus.DECOY.get(), id);
        this.decoy = decoy;
        this.data = data;
        for (int s = 0; s < DecoyTotemBlockEntity.SOCKETS; s++) {
            int slot = s;
            addSlot(new SlotItemHandler(decoy.getItems(), s, SOCKET[s][0], SOCKET[s][1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return decoy.fits(slot, stack);
                }

                @Override
                public boolean mayPickup(Player player) {
                    return decoy.removable(slot) && super.mayPickup(player);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
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

    private static ContainerData serverData(DecoyTotemBlockEntity decoy, Player player) {
        return new ContainerData() {
            @Override
            public int get(int i) {
                if (!(decoy.getLevel() instanceof ServerLevel sl)) {
                    return 0;
                }
                return switch (i) {
                    case D_RF -> sl.getBlockEntity(decoy.getBlockPos().below()) instanceof DecoyPowerBaseBlockEntity base ? 1000 * base.stored() / DecoyPowerBaseBlockEntity.CAPACITY : -1;
                    case D_STATE -> decoy.isBroken() ? 2 : decoy.isPowered() ? 1 : 0;
                    case D_HITS -> decoy.hits();
                    case D_RANGE -> decoy.range();
                    case D_PLACED -> decoy.getOwner() == null ? 0 : DecoyLedger.get(sl.getServer()).count(decoy.getOwner());
                    default -> com.mrgregles.bsp_core.BSPConfig.DECOY_MAX.get();
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

    @Nullable
    public DecoyTotemBlockEntity decoy() {
        return decoy;
    }

    /** RF in the power base in thousandths, or -1 if there is no power base underneath. */
    public int rf() {
        return data.get(D_RF);
    }

    /** 0 no power, 1 active, 2 broken. */
    public int state() {
        return data.get(D_STATE);
    }

    public int hits() {
        return data.get(D_HITS);
    }

    public int range() {
        return data.get(D_RANGE);
    }

    public int placed() {
        return data.get(D_PLACED);
    }

    public int max() {
        return data.get(D_MAX);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BTN_REPAIR && decoy != null && player instanceof ServerPlayer sp) {
            decoy.repair(sp);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem(), original = stack.copy();
        int n = DecoyTotemBlockEntity.SOCKETS;
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
        return decoy != null && !decoy.isRemoved() && (player.level().isClientSide || decoy.isOwner(player)) && player.distanceToSqr(decoy.getBlockPos().getCenter()) <= 64.0;
    }
}
