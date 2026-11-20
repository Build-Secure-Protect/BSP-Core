package com.mrgregles.bsp_core.compass;

import com.mrgregles.bsp_core.compass.CompassTunerBlockEntity.Ladder;
import com.mrgregles.bsp_core.registry.ModMenus;
import net.minecraft.core.BlockPos;
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
 * The Compass Tuner's screen (concept C, the Dial): the compass in the middle of the dial, the material slots on the dial, the
 * four upgrade cards on the right with FE, plasma and TUNE under them, and the player's inventory below. Buttons: 0 to 3 buy
 * the next level of that {@link Ladder}, 4 is TUNE. Only the owner and their team can open it.
 */
public class CompassTunerMenu extends AbstractContainerMenu {
    public static final int WIDTH = 240, HEIGHT = 354, INV_X = 39, INV_Y = 272, BTN_TUNE = 4;
    /** The dial's centre and radius; the compass slot sits in the middle. */
    public static final int DIAL_X = 72, DIAL_Y = 82, DIAL_R = 56;
    /** Top-left of the compass slot and of material slots 1 to 4: at 8, 4 and 6 o'clock on the dial, the fourth at 12. */
    public static final int[][] SLOT_POS = {{DIAL_X - 9, DIAL_Y - 9}, {29, 57}, {97, 57}, {DIAL_X - 9, 113}, {DIAL_X - 9, 33}};

    public static final int D_ACROSS = 0, D_DEPTH = 1, D_COOLDOWN = 2, D_SPEED = 3, D_TUNING = 4, D_STALL = 5, D_PROGRESS = 6,
            D_LEFT_LO = 7, D_LEFT_HI = 8, D_FE = 9, D_PLASMA = 10, D_FACTOR = 11, D_CHECK = 12, D_HAND_A = 13, D_HAND_D = 14, D_HAND_C = 15,
            DATA_COUNT = 16;

    @Nullable
    private final CompassTunerBlockEntity tuner;
    private final ContainerData data;

    /** Client: from the opening packet. */
    public CompassTunerMenu(int id, Inventory inv, BlockPos pos) {
        this(id, inv, inv.player.level().getBlockEntity(pos) instanceof CompassTunerBlockEntity t ? t
                : new CompassTunerBlockEntity(pos, com.mrgregles.bsp_core.registry.ModBlocks.COMPASS_TUNER.get().defaultBlockState()), new SimpleContainerData(DATA_COUNT));
    }

    /** Server. */
    public CompassTunerMenu(int id, Inventory inv, CompassTunerBlockEntity tuner) {
        this(id, inv, tuner, serverData(tuner, inv.player));
    }

    private CompassTunerMenu(int id, Inventory inv, CompassTunerBlockEntity tuner, ContainerData data) {
        super(ModMenus.COMPASS_TUNER.get(), id);
        this.tuner = tuner;
        this.data = data;
        for (int s = 0; s < CompassTunerBlockEntity.SLOTS; s++) {
            int slot = s;
            addSlot(new SlotItemHandler(tuner.getItems(), s, SLOT_POS[s][0], SLOT_POS[s][1]) {
                /** A merge or split by shift-click changes the stack in place: the tuner must still be saved. */
                @Override
                public void setChanged() {
                    super.setChanged();
                    tuner.setChanged();
                }

                @Override
                public boolean isActive() {
                    return slot == CompassTunerBlockEntity.COMPASS_SLOT || TunerMaterials.forSlot(slot - 1) != null || hasItem();
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

    private static ContainerData serverData(CompassTunerBlockEntity tuner, Player player) {
        return new ContainerData() {
            @Override
            public int get(int i) {
                int[] hand = tuner.handover();
                return switch (i) {
                    case D_ACROSS -> tuner.across();
                    case D_DEPTH -> tuner.depth();
                    case D_COOLDOWN -> tuner.cooldown();
                    case D_SPEED -> tuner.speed();
                    case D_TUNING -> tuner.isTuning() ? 1 : 0;
                    case D_STALL -> tuner.stall().ordinal();
                    case D_PROGRESS -> (int) Math.round(tuner.progress() * 10_000);
                    case D_LEFT_LO -> leftSeconds(tuner) & 0x7FFF;
                    case D_LEFT_HI -> leftSeconds(tuner) >> 15;
                    case D_FE -> (int) (1000L * tuner.energyStored() / Math.max(1, tuner.energyCapacity()));
                    case D_PLASMA -> Math.min(1000, 1000 * tuner.plasmaRate() / Math.max(1, com.mrgregles.bsp_core.plasma.PlasmaBoost.topRate()));
                    case D_FACTOR -> (int) Math.round(tuner.plasmaFactor() * 100);
                    case D_CHECK -> tuner.checkTune(player).ordinal();
                    case D_HAND_A -> hand == null ? -1 : hand[0];
                    case D_HAND_D -> hand == null ? -1 : hand[1];
                    case D_HAND_C -> hand == null ? -1 : hand[2];
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

    /** Seconds left of the running tune at the speed it runs now (or would run with FE), or one whole tune's length when idle. */
    static int leftSeconds(CompassTunerBlockEntity tuner) {
        double perSecond = 1000.0 * tuner.plasmaFactor() / Math.max(1, tuner.tuneMs());
        double left = tuner.isTuning() ? 1 - tuner.progress() : 1;
        return (int) Math.min(Integer.MAX_VALUE >> 1, Math.ceil(left / perSecond));
    }

    @Nullable
    public CompassTunerBlockEntity tuner() {
        return tuner;
    }

    public int level(Ladder ladder) {
        return data.get(ladder.ordinal());
    }

    public boolean tuning() {
        return data.get(D_TUNING) != 0;
    }

    public CompassTunerBlockEntity.Stall stall() {
        CompassTunerBlockEntity.Stall[] all = CompassTunerBlockEntity.Stall.values();
        return all[Math.max(0, Math.min(all.length - 1, data.get(D_STALL)))];
    }

    /** 0 to 1. */
    public double progress() {
        return data.get(D_PROGRESS) / 10_000.0;
    }

    public int leftSeconds() {
        return (data.get(D_LEFT_LO) & 0x7FFF) | (data.get(D_LEFT_HI) << 15);
    }

    /** FE in the buffer, in thousandths of its capacity. */
    public int fe() {
        return data.get(D_FE);
    }

    /** Plasma arriving, in thousandths of the rate that gives the most speed. */
    public int plasma() {
        return data.get(D_PLASMA);
    }

    public double factor() {
        return data.get(D_FACTOR) / 100.0;
    }

    public CompassTunerBlockEntity.TuneCheck check() {
        CompassTunerBlockEntity.TuneCheck[] all = CompassTunerBlockEntity.TuneCheck.values();
        return all[Math.max(0, Math.min(all.length - 1, data.get(D_CHECK)))];
    }

    /** What an old compass in the slot handed over (across, up or down, cooldown), or null. */
    @Nullable
    public int[] handover() {
        return data.get(D_HAND_A) < 0 ? null : new int[] {data.get(D_HAND_A), data.get(D_HAND_D), data.get(D_HAND_C)};
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (tuner == null || !(player instanceof ServerPlayer sp) || !tuner.mayUse(sp)) {
            return false;
        }
        if (id >= 0 && id < Ladder.values().length) {
            return tuner.buy(sp, Ladder.values()[id]);
        }
        if (id == BTN_TUNE) {
            return tuner.startTune(sp) == CompassTunerBlockEntity.TuneCheck.OK;
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
        int n = CompassTunerBlockEntity.SLOTS;
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
        return tuner != null && !tuner.isRemoved() && (player.level().isClientSide || tuner.mayUse(player))
                && player.distanceToSqr(tuner.getBlockPos().getCenter()) <= 64.0;
    }
}
