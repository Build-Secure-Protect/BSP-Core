package com.mrgregles.bsp_core.coin;

import com.mrgregles.bsp_core.data.FactoryLedger;
import com.mrgregles.bsp_core.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Menu of a whole joined factory: one lane per slice, laid out left to right in the order the
 * slices stand when seen from the front. Each lane has a blank slot and a coin tray; energy, the
 * power switch and the owner line are shared.
 */
public class CoinFactoryMenu extends AbstractContainerMenu {
    public static final int WIDTH = 326, LANE_W = 30, BLANK_Y = 31, TRAY_Y = 75, INV_X = 82, INV_Y = 144, BTN_POWER = 0, BTN_DEMO = 1;
    /** Per slice: tier + 1, progress in thousandths, seconds left (two shorts), Motivators, state. */
    private static final int PER = 7, SHARED = CoinFactoryBlockEntity.MAX_SLICES * PER, DATA_COUNT = SHARED + 9;

    @Nullable
    private final CoinFactoryBlockEntity origin;
    private final ContainerData data;
    private final int sliceCount;

    /** Client side: slot contents and values arrive from the server. */
    public CoinFactoryMenu(int id, Inventory inv, BlockPos pos, int slices) {
        this(id, inv, inv.player.level().getBlockEntity(pos) instanceof CoinFactoryBlockEntity f ? f : null, null,
                Math.max(1, Math.min(CoinFactoryBlockEntity.MAX_SLICES, slices)), new SimpleContainerData(DATA_COUNT));
    }

    public CoinFactoryMenu(int id, Inventory inv, CoinFactoryBlockEntity origin, List<CoinFactoryBlockEntity> slices) {
        this(id, inv, origin, slices, slices.size(), serverData(origin, slices));
    }

    private CoinFactoryMenu(int id, Inventory inv, @Nullable CoinFactoryBlockEntity origin, @Nullable List<CoinFactoryBlockEntity> slices, int count, ContainerData data) {
        super(ModMenus.COIN_FACTORY.get(), id);
        this.origin = origin;
        this.data = data;
        this.sliceCount = count;
        for (int i = 0; i < count; i++) {
            // screen left = the slice on the viewer's left, which is the far end of the row order
            ItemStackHandler handler = slices == null ? new ItemStackHandler(CoinFactoryBlockEntity.SLOTS) : slices.get(count - 1 - i).getItems();
            addSlot(new SlotItemHandler(handler, CoinFactoryBlockEntity.SLOT_INPUT, laneX(i, count), BLANK_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof CoinBlankItem;
                }
            });
            addSlot(new SlotItemHandler(handler, CoinFactoryBlockEntity.SLOT_OUTPUT, laneX(i, count), TRAY_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
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

    /** Left edge of the slots in lane {@code i} of {@code n}, lanes centred on the screen. */
    public static int laneX(int i, int n) {
        return (WIDTH - n * LANE_W) / 2 + i * LANE_W + 7;
    }

    private static ContainerData serverData(CoinFactoryBlockEntity origin, List<CoinFactoryBlockEntity> slices) {
        int n = slices.size();
        return new ContainerData() {
            @Override
            public int get(int i) {
                if (i < SHARED) {
                    int lane = i / PER;
                    if (lane >= n) {
                        return 0;
                    }
                    CoinFactoryBlockEntity s = slices.get(n - 1 - lane);
                    long rem = s.remainingSeconds();
                    return switch (i % PER) {
                        case 0 -> s.getJobTier() == null ? 0 : s.getJobTier().ordinal() + 1;
                        case 1 -> Math.round(s.progressFraction() * 1000);
                        case 2 -> (int) (rem & 0xFFFF);
                        case 3 -> (int) ((rem >>> 16) & 0xFFFF);
                        case 4 -> s.motivators();
                        case 5 -> s.plasmaRate();
                        default -> s.stateCode();
                    };
                }
                int stored = origin.poolStored(), cap = origin.poolCapacity(), intake = 0, cables = 0;
                for (CoinFactoryBlockEntity s : slices) {
                    intake += s.intakeRate();
                    cables += s.intakeRate() > 0 ? 1 : 0;
                }
                return switch (i - SHARED) {
                    case 0 -> stored & 0xFFFF;
                    case 1 -> (stored >>> 16) & 0xFFFF;
                    case 2 -> cap & 0xFFFF;
                    case 3 -> (cap >>> 16) & 0xFFFF;
                    case 4 -> origin.isEnabled() ? 1 : 0;
                    case 5 -> intake & 0xFFFF;
                    case 6 -> (intake >>> 16) & 0xFFFF;
                    case 7 -> cables;
                    default -> origin.getLevel() instanceof ServerLevel sl && origin.getOwner() != null ? FactoryLedger.get(sl.getServer()).count(origin.getOwner()) : 0;
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

    // --- synced values ---

    public int sliceCount() {
        return sliceCount;
    }

    @Nullable
    public CoinTier jobTier(int lane) {
        return CoinTier.byOrdinal(data.get(lane * PER) - 1);
    }

    public float progress(int lane) {
        return data.get(lane * PER + 1) / 1000f;
    }

    public long remainingSeconds(int lane) {
        return (data.get(lane * PER + 2) & 0xFFFFL) | ((data.get(lane * PER + 3) & 0xFFFFL) << 16);
    }

    public int motivators(int lane) {
        return data.get(lane * PER + 4);
    }

    /** mB/t of Wave Plasma arriving at the lane's injectors. */
    public int plasmaRate(int lane) {
        return data.get(lane * PER + 5);
    }

    public int state(int lane) {
        return data.get(lane * PER + 6);
    }

    private int wide(int lo) {
        return (data.get(SHARED + lo) & 0xFFFF) | ((data.get(SHARED + lo + 1) & 0xFFFF) << 16);
    }

    public int energy() {
        return wide(0);
    }

    public int energyCapacity() {
        return Math.max(1, wide(2));
    }

    public boolean enabled() {
        return data.get(SHARED + 4) != 0;
    }

    /** RF per tick coming in over the last second, and how many Power Ports it came through. */
    public int intake() {
        return wide(5);
    }

    public int cables() {
        return data.get(SHARED + 7);
    }

    public int ownedCount() {
        return data.get(SHARED + 8);
    }

    @Nullable
    public CoinFactoryBlockEntity getFactory() {
        return origin;
    }

    /** Runs on the server. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (origin != null && id == BTN_POWER) {
            origin.toggleEnabled();
            return true;
        }
        if (origin != null && id == BTN_DEMO && player.hasPermissions(2)) {
            origin.toggleDemo();
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
        int machineSlots = sliceCount * 2;
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof CoinBlankItem) {
            for (int lane = 0; lane < sliceCount && !stack.isEmpty(); lane++) {
                moveItemStackTo(stack, lane * 2, lane * 2 + 1, false);
            }
            if (stack.getCount() == original.getCount()) {
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
        if (origin == null || origin.isRemoved()) {
            return false;
        }
        if (!player.level().isClientSide && origin.group().size() != sliceCount) {
            return false; // a slice was added or removed: reopen to get the new layout
        }
        return player.distanceToSqr(origin.getBlockPos().getCenter()) <= 144.0;
    }
}
