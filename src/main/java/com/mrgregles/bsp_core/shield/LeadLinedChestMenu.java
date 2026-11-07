package com.mrgregles.bsp_core.shield;

import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * The Lead-Lined Chest's screen: its rows of {@link SlotItemHandler}s over the chest's handler, then the player's inventory and hotbar,
 * laid out as a vanilla chest's (slots from (8, 18), the inventory 13 pixels below the chest rows, the hotbar 4 below that).
 * The client builds it from the chest's position and row count, with an empty handler the server fills.
 *
 * <p>On the server it keeps the chest halves it was opened on and the level they are in: every change through a slot marks those
 * halves changed (a {@link SlotItemHandler} on its own does not, so a shift-click that tops up a stack would be lost or doubled at the
 * next load), the screen closes as soon as either half is no longer the same block entity (broken, or broken and replaced), and closing
 * it lowers the lid of those halves, whichever world the player is in by then.
 */
public class LeadLinedChestMenu extends AbstractContainerMenu {
    public static final int SLOT_X = 8, SLOT_Y = 18;

    private final int rows;
    private final BlockPos pos;
    private final ContainerLevelAccess access;
    /** The other half of a double chest, or null: the screen closes once the two are no longer joined. */
    @javax.annotation.Nullable
    private final BlockPos other;
    private final Player viewer;
    /** Server: the level and the halves this screen was opened on, the clicked half first. Empty on the client. */
    @javax.annotation.Nullable
    private final net.minecraft.world.level.Level level;
    private final java.util.List<LeadLinedChestBlockEntity> chests = new java.util.ArrayList<>(2);

    /** Client side, from the packet {@link LeadLinedChestBlock} writes: the position, then the rows. */
    public LeadLinedChestMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), buf.readVarInt());
    }

    private LeadLinedChestMenu(int id, Inventory inv, BlockPos pos, int rows) {
        this(id, inv, new ItemStackHandler(rows * 9), rows, pos, null);
    }

    /** {@code other} is the other half of a double chest ({@code rows} 6), or null for a single chest. */
    public LeadLinedChestMenu(int id, Inventory inv, IItemHandler chest, int rows, BlockPos pos, @javax.annotation.Nullable BlockPos other) {
        super(ModMenus.LEAD_LINED_CHEST.get(), id);
        this.rows = rows;
        this.pos = pos;
        this.other = other;
        this.access = inv.player.level().isClientSide ? ContainerLevelAccess.NULL : ContainerLevelAccess.create(inv.player.level(), pos);
        this.viewer = inv.player;
        this.level = inv.player.level().isClientSide ? null : inv.player.level();
        if (level != null) {
            if (level.getBlockEntity(pos) instanceof LeadLinedChestBlockEntity be) {
                chests.add(be);
            }
            if (other != null && level.getBlockEntity(other) instanceof LeadLinedChestBlockEntity be) {
                chests.add(be);
            }
            for (int i = 0; i < chests.size(); i++) {
                chests.get(i).startOpen(inv.player, i == 0); // the lid lifts; the clicked half plays the sound
            }
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new SlotItemHandler(chest, c + r * 9, SLOT_X + c * 18, SLOT_Y + r * 18) {
                    @Override
                    public void setChanged() {
                        super.setChanged();
                        chests.forEach(LeadLinedChestBlockEntity::setChanged); // saved, and comparators told, however the stack changed
                    }
                });
            }
        }
        int y = inventoryY(rows);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                addSlot(new Slot(inv, c + r * 9 + 9, SLOT_X + c * 18, y + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            addSlot(new Slot(inv, c, SLOT_X + c * 18, y + 58));
        }
    }

    /** Top of the player's inventory rows for a chest of {@code rows} rows (85 for 3, 139 for 6), as a vanilla chest. */
    public static int inventoryY(int rows) {
        return SLOT_Y + rows * 18 + 13;
    }

    /** The screen's height for {@code rows} chest rows (168 for 3, 222 for 6), as a vanilla chest's. */
    public static int imageHeight(int rows) {
        return 114 + rows * 18;
    }

    /** Where the screen's Inventory label sits, and with it the SHIELDED tag (2 above it, 11 high), between the chest rows and the inventory. */
    public static int inventoryLabelY(int rows) {
        return imageHeight(rows) - 94;
    }

    public int rows() {
        return rows;
    }

    public BlockPos pos() {
        return pos;
    }

    /** Whether this screen shows the chest at {@code at} (either half). */
    public boolean shows(BlockPos at) {
        return at.equals(pos) || at.equals(other);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (level != null && player == viewer) {
            for (int i = 0; i < chests.size(); i++) {
                chests.get(i).stopOpen(player, i == 0); // the lid shuts once the last viewer goes, in the world it was opened in
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem(), original = stack.copy();
        int chest = rows * 9;
        if (index < chest ? !moveItemStackTo(stack, chest, slots.size(), true) : !moveItemStackTo(stack, 0, chest, false)) {
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
        if (!stillValid(access, player, ModBlocks.LEAD_LINED_CHEST.get())) {
            return false;
        }
        if (level != null) {
            // the very block entities it was opened on: a half broken and replaced in one tick holds a new, empty handler
            for (LeadLinedChestBlockEntity be : chests) {
                if (be.isRemoved() || level.getBlockEntity(be.getBlockPos()) != be) {
                    return false;
                }
            }
            if (chests.size() != (other == null ? 1 : 2)) {
                return false;
            }
        }
        // a double chest's screen holds both halves' slots: it closes when either half is broken or the pair splits
        return other == null || access.evaluate((level, at) -> other.equals(partnerPos(level, at)), true);
    }

    @javax.annotation.Nullable
    private static BlockPos partnerPos(net.minecraft.world.level.Level level, BlockPos at) {
        LeadLinedChestBlockEntity partner = LeadLinedChestBlock.partner(level, at, level.getBlockState(at));
        return partner == null ? null : partner.getBlockPos();
    }
}
