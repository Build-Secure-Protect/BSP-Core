package com.mrgregles.bsp_core.coin;

import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

import javax.annotation.Nullable;

public class CoinFactoryMenu extends AbstractContainerMenu {
    public static final int INPUT_X = 44, OUTPUT_X = 116, IO_Y = 36, UPGRADE_X = 53, UPGRADE_Y = 62, INV_Y = 118;

    @Nullable
    private final CoinFactoryBlockEntity factory;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    /** Client side: the block entity comes from the client world; values arrive through the data slots. */
    public CoinFactoryMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, inv.player.level().getBlockEntity(pos) instanceof CoinFactoryBlockEntity f ? f : null,
                new SimpleContainerData(CoinFactoryBlockEntity.DATA_COUNT), pos);
    }

    public CoinFactoryMenu(int id, Inventory inv, CoinFactoryBlockEntity factory, ContainerData data) {
        this(id, inv, factory, data, factory.getBlockPos());
    }

    private CoinFactoryMenu(int id, Inventory inv, @Nullable CoinFactoryBlockEntity factory, ContainerData data, BlockPos pos) {
        super(ModMenus.COIN_FACTORY.get(), id);
        this.factory = factory;
        this.data = data;
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);
        if (factory != null) {
            addSlot(new SlotItemHandler(factory.getItems(), CoinFactoryBlockEntity.SLOT_INPUT, INPUT_X, IO_Y));
            addSlot(new SlotItemHandler(factory.getItems(), CoinFactoryBlockEntity.SLOT_OUTPUT, OUTPUT_X, IO_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
            for (int i = 0; i < CoinFactoryBlockEntity.UPGRADE_SLOTS; i++) {
                addSlot(new SlotItemHandler(factory.getItems(), CoinFactoryBlockEntity.SLOT_UPGRADE_START + i, UPGRADE_X + i * 18, UPGRADE_Y));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 8 + col * 18, INV_Y + 58));
        }
        addDataSlots(data);
    }

    // --- synced values ---

    public int energy() {
        return (data.get(0) & 0xFFFF) | ((data.get(1) & 0xFFFF) << 16);
    }

    public int energyCapacity() {
        return Math.max(1, (data.get(2) & 0xFFFF) | ((data.get(3) & 0xFFFF) << 16));
    }

    public float progress() {
        return data.get(4) / 1000f;
    }

    public long remainingSeconds() {
        return (data.get(5) & 0xFFFFL) | ((data.get(6) & 0xFFFFL) << 16);
    }

    @Nullable
    public CoinTier jobTier() {
        return CoinTier.byOrdinal(data.get(7) - 1);
    }

    public CoinFactoryBlockEntity.SideMode side(Direction d) {
        return CoinFactoryBlockEntity.SideMode.values()[Math.floorMod(data.get(8 + d.get3DDataValue()), 4)];
    }

    public float reduction() {
        return data.get(14) / 1000f;
    }

    public int ownedCount() {
        return data.get(15);
    }

    @Nullable
    public CoinFactoryBlockEntity getFactory() {
        return factory;
    }

    /** Buttons 0-5 cycle the mode of the face with that 3D data value. Runs on the server. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (factory != null && id >= 0 && id < 6) {
            factory.cycleSide(Direction.from3DDataValue(id));
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
        int machineSlots = factory == null ? 0 : CoinFactoryBlockEntity.SLOTS;
        int end = slots.size();
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, end, true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof CoinBlankItem) {
            if (!moveItemStackTo(stack, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof SpeedGearItem) {
            if (!moveItemStackTo(stack, 2, machineSlots, false)) {
                return ItemStack.EMPTY;
            }
        } else {
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
        return stillValid(access, player, ModBlocks.COIN_FACTORY.get());
    }
}
