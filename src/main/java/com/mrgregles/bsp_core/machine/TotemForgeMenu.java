package com.mrgregles.bsp_core.machine;

import com.mrgregles.bsp_core.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
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

/**
 * The Totem Forge's own screen: its three slots (ingots, lava bucket, RF upgrade), the player's inventory,
 * and what only the server knows about the viewer: whether they could pour now, how many totems they own
 * and what the pour would cost them. Everything else (the mould, the tanks, the pour and the setting) the
 * screen reads from the forge's synced block entity.
 */
public class TotemForgeMenu extends AbstractContainerMenu {
    public static final int W = 256, H = 248, INV_X = 48, INV_Y = 166, SLOT_Y = 104;
    /** Ingots, lava, RF upgrade: the forge's layout order. */
    public static final int[] SLOT_X = {12, 56, 100};
    public static final int BTN_MAIN = 0, BTN_TAKE_PIECE = 1, BTN_DEMO = 2;
    /**
     * Data: the viewer's pour check (ordinal + 1, so the 0 a client starts with reads as not known yet), totems owned, coins
     * the pour costs, lava in mB, the lava tank's size, and the setting's minutes left in two 15-bit halves (the server's
     * clock, not the viewer's), and the RF buffer in permille (-1 without an RF upgrade). Menu data travels as shorts, so every
     * value stays within -1 to 32,767.
     */
    private static final int DATA = 8;

    @Nullable
    private final TotemForgeBlockEntity forge;
    private final ContainerData data;
    private final BlockPos pos;

    /** Client side, from the open-screen packet. */
    public TotemForgeMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), new SimpleContainerData(DATA));
    }

    /** Server side. */
    public TotemForgeMenu(int id, Inventory inv, TotemForgeBlockEntity forge) {
        this(id, inv, forge.getBlockPos(), viewerData(forge, inv.player));
    }

    private TotemForgeMenu(int id, Inventory inv, BlockPos pos, ContainerData data) {
        super(ModMenus.TOTEM_FORGE.get(), id);
        this.pos = pos;
        this.data = data;
        this.forge = inv.player.level().getBlockEntity(pos) instanceof TotemForgeBlockEntity f ? f : null;
        if (forge != null) {
            for (int i = 0; i < SLOT_X.length; i++) {
                addSlot(new SlotItemHandler(forge.getItems(), i, SLOT_X[i], SLOT_Y));
            }
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

    /** The viewer's pour plan, worked out twice a second (it reads the score board and the claims). */
    private static ContainerData viewerData(TotemForgeBlockEntity forge, Player player) {
        return new ContainerData() {
            private long at = -1;
            private TotemForgeBlockEntity.PourPlan plan = new TotemForgeBlockEntity.PourPlan(TotemForgeBlockEntity.PourCheck.NOT_FORMED, 0, 0);

            @Override
            public int get(int i) {
                if (i < 3 && player instanceof ServerPlayer sp && forge.getLevel() != null && forge.getLevel().getGameTime() / 10 != at) {
                    at = forge.getLevel().getGameTime() / 10;
                    plan = forge.plan(sp);
                }
                return switch (i) {
                    case 0 -> plan.check().ordinal() + 1;
                    case 1 -> plan.owned();
                    case 2 -> plan.coins();
                    case 3 -> forge.fluidAmount();
                    case 4 -> forge.fluidCapacity();
                    case 5 -> high(minutesLeft(forge));
                    case 6 -> low(minutesLeft(forge));
                    default -> forge.hasRfUpgrade() ? (int) (1000L * forge.energyStored() / Math.max(1, com.mrgregles.bsp_core.BSPConfig.RF_CAPACITY.get())) : -1;
                };
            }

            @Override
            public void set(int i, int value) {}

            @Override
            public int getCount() {
                return DATA;
            }
        };
    }

    @Nullable
    public TotemForgeBlockEntity forge() {
        return forge;
    }

    /** The viewer's pour check; UNKNOWN until the server's first answer reaches the screen. */
    public TotemForgeBlockEntity.PourCheck check() {
        TotemForgeBlockEntity.PourCheck[] all = TotemForgeBlockEntity.PourCheck.values();
        int v = data.get(0);
        return v <= 0 || v > all.length ? TotemForgeBlockEntity.PourCheck.UNKNOWN : all[v - 1];
    }

    /** The RF buffer in permille, or -1 without an RF upgrade. */
    public int energyPermille() {
        return data.get(7);
    }

    /** Minutes the setting has left, by the server's clock, rounded up; 0 once set or before the pour has ended. */
    public int minutesLeft() {
        return join(data.get(5), data.get(6));
    }

    private static int minutesLeft(TotemForgeBlockEntity forge) {
        return (int) Math.min(Integer.MAX_VALUE >> 1, (forge.setMillisLeft() + 59_999) / 60_000);
    }

    /** The high and low 15 bits of {@code v} (0 to 2^30), each safe as a short. */
    public static int high(int v) {
        return (v >> 15) & 0x7FFF;
    }

    public static int low(int v) {
        return v & 0x7FFF;
    }

    public static int join(int high, int low) {
        return ((high & 0x7FFF) << 15) | (low & 0x7FFF);
    }

    public int owned() {
        return data.get(1);
    }

    public int coins() {
        return data.get(2);
    }

    public int lava() {
        return data.get(3);
    }

    public int lavaCapacity() {
        return data.get(4);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (forge == null || !(player instanceof ServerPlayer sp)) {
            return false;
        }
        switch (id) {
            case BTN_MAIN -> { // POUR, or once set TAKE TOTEM (the pourer) or STEAL (anyone else)
                if (forge.isSet()) {
                    forge.takeOrSteal(sp);
                } else {
                    forge.requestPour(sp);
                }
            }
            case BTN_TAKE_PIECE -> {
                ItemStack out = forge.takeMould();
                if (!out.isEmpty() && !sp.getInventory().add(out)) {
                    sp.drop(out, false);
                }
            }
            case BTN_DEMO -> {
                if (sp.hasPermissions(2)) {
                    forge.toggleDemo();
                }
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        int own = forge == null ? 0 : SLOT_X.length;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < own) {
            if (!moveItemStackTo(stack, own, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, own, false)) { // mayPlace sends it to the slot that takes it
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
        return forge != null && !forge.isRemoved() && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
