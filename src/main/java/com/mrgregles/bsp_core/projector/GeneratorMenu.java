package com.mrgregles.bsp_core.projector;

import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

/** One Totem Generator's screen: the list of powers with a switch each, its upgrade sockets, and the state of its cable run. */
public class GeneratorMenu extends AbstractContainerMenu {
    public static final int WIDTH = 300, SOCKET_Y = 140, INV_X = 69, INV_Y = 172, HEIGHT = INV_Y + 82;
    public static final int[] SOCKET_X = {12, 32, 52, 84};
    private static final int N = TotemGeneratorBlockEntity.SENDABLE.length, D_RF = 0, D_STATE = 1, D_RUN = 2, D_CABLE = 3, D_REACH = 4, D_CHANNELS = 5, D_CHOSEN = 6, D_LEVELS = 7,
            D_ARRIVING = D_LEVELS + N, DATA_COUNT = D_ARRIVING + N;

    private final TotemGeneratorBlockEntity generator;
    private final ContainerData data;

    public GeneratorMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, inv.player.level().getBlockEntity(pos) instanceof TotemGeneratorBlockEntity g ? g
                : new TotemGeneratorBlockEntity(pos, ModBlocks.TOTEM_GENERATOR.get().defaultBlockState()), new SimpleContainerData(DATA_COUNT));
    }

    public GeneratorMenu(int id, Inventory inv, TotemGeneratorBlockEntity generator) {
        this(id, inv, generator, new ContainerData() {
            @Override
            public int get(int i) {
                if (i >= D_ARRIVING) {
                    return generator.arriving(i - D_ARRIVING);
                }
                if (i >= D_LEVELS) {
                    return generator.totemLevel(i - D_LEVELS);
                }
                return switch (i) {
                    case D_RF -> generator.poolPermille();
                    case D_STATE -> generator.state();
                    case D_RUN -> generator.run();
                    case D_CABLE -> generator.cableReach();
                    case D_REACH -> generator.reach();
                    case D_CHANNELS -> generator.channels();
                    default -> generator.chosen();
                };
            }

            @Override
            public void set(int i, int value) {}

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        });
    }

    private GeneratorMenu(int id, Inventory inv, TotemGeneratorBlockEntity generator, ContainerData data) {
        super(ModMenus.GENERATOR.get(), id);
        this.generator = generator;
        this.data = data;
        for (int s = 0; s < TotemGeneratorBlockEntity.SOCKETS; s++) {
            int slot = s;
            addSlot(new SlotItemHandler(generator.getItems(), s, SOCKET_X[s], SOCKET_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return generator.fits(slot, stack);
                }

                @Override
                public boolean mayPickup(Player player) {
                    return generator.removable(slot) && super.mayPickup(player);
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

    public int rf() {
        return data.get(D_RF);
    }

    public int state() {
        return data.get(D_STATE);
    }

    public int run() {
        return data.get(D_RUN);
    }

    public int cableReach() {
        return data.get(D_CABLE);
    }

    public int reach() {
        return data.get(D_REACH);
    }

    public int channels() {
        return data.get(D_CHANNELS);
    }

    public boolean chosen(int i) {
        return (data.get(D_CHOSEN) & (1 << i)) != 0;
    }

    public int chosenCount() {
        return Integer.bitCount(data.get(D_CHOSEN));
    }

    public int totemLevel(int i) {
        return data.get(D_LEVELS + i);
    }

    public int arriving(int i) {
        return data.get(D_ARRIVING + i);
    }

    /** Button {@code id} switches power {@code id} on or off. Runs on the server. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        generator.toggle(id);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem(), original = stack.copy();
        int n = TotemGeneratorBlockEntity.SOCKETS;
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
        return !generator.isRemoved() && player.distanceToSqr(generator.getBlockPos().getCenter()) <= 100.0
                && (player.level().isClientSide || generator.mayOpen(player)); // closes if the totem changes hands
    }
}
