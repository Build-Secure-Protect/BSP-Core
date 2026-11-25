package com.mrgregles.bsp_core.tracking;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The Remote Control Transmitter (owner decisions MT31 to MT36, MT42, MT43): three slots of range cards, a stack each; a range of
 * {@code transmitterBaseRange} blocks plus {@code transmitterRangePerCard} per card, up to {@code transmitterMaxRange}, as a cube around
 * the block; an FE buffer filled from any side, drawn while someone operates a machine through it ({@code transmitterFeBase} plus
 * {@code transmitterFePerCard} per card, each tick, however many people operate through it at once). Its owner and their team may use it.
 * The cards stay in it when it is broken (the loot table copies them to the dropped item), and so does the Inventory Link upgrade
 * ({@link #inventoryLink()}): bought once by the owner, it lets operators use their own inventory in the machine screens it carries.
 */
public class RemoteTransmitterBlockEntity extends BlockEntity {
    public static final int SLOTS = 3;

    @Nullable
    private UUID owner;
    private String ownerName = "";
    /** The game time a machine was last operated through it; it draws FE for that tick. */
    private long operatedTick = -1;
    private boolean operatedLastTick;
    /** The Inventory Link upgrade: operators may move items between the machine and their own inventory. */
    private boolean inventoryLink;

    private final ItemStackHandler cards = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return stack.is(ModItems.RANGE_CARD.get());
        }

        @Override
        protected void onContentsChanged(int slot) {
            changed();
        }
    };
    /** What pipes and hoppers see: they may put range cards in, never take them out. */
    private final net.minecraftforge.items.IItemHandler piped = new net.minecraftforge.items.IItemHandler() {
        @Override
        public int getSlots() {
            return SLOTS;
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return cards.getStackInSlot(slot);
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            return cards.insertItem(slot, stack, simulate);
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return cards.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return cards.isItemValid(slot, stack);
        }
    };
    private final LazyOptional<net.minecraftforge.items.IItemHandler> cardCap = LazyOptional.of(() -> piped);

    private final class Buffer extends EnergyStorage {
        Buffer() {
            super(BSPConfig.getOr(BSPConfig.TRANSMITTER_FE_BUFFER, 100_000), Integer.MAX_VALUE, 0);
        }

        /** The capacity follows the config, so an admin's change applies to transmitters already placed. */
        void sync() {
            capacity = BSPConfig.getOr(BSPConfig.TRANSMITTER_FE_BUFFER, 100_000);
            maxReceive = capacity;
            energy = Math.min(energy, capacity);
        }

        boolean draw(int fe) {
            if (energy < fe) {
                return false;
            }
            energy -= fe;
            return true;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            sync();
            int got = super.receiveEnergy(maxReceive, simulate);
            if (got > 0 && !simulate) {
                setChanged();
            }
            return got;
        }
    }

    private final Buffer energy = new Buffer();
    private final LazyOptional<net.minecraftforge.energy.IEnergyStorage> energyCap = LazyOptional.of(() -> energy);

    public RemoteTransmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REMOTE_TRANSMITTER.get(), pos, state);
    }

    // ------------------------------------------------------------------ owner, range, power

    public void setOwner(UUID id, String name) {
        owner = id;
        ownerName = name;
        changed();
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public boolean inventoryLink() {
        return inventoryLink;
    }

    public void setInventoryLink(boolean on) {
        inventoryLink = on;
        changed();
    }

    public ItemStackHandler getCards() {
        return cards;
    }

    public int cardCount() {
        int n = 0;
        for (int i = 0; i < SLOTS; i++) {
            ItemStack st = cards.getStackInSlot(i);
            if (st.is(ModItems.RANGE_CARD.get())) {
                n += st.getCount();
            }
        }
        return n;
    }

    /** Blocks each way from this block that the range reaches. */
    public int range() {
        return range(cardCount());
    }

    public static int range(int cards) {
        long r = (long) BSPConfig.getOr(BSPConfig.TRANSMITTER_BASE_RANGE, 16) + (long) cards * BSPConfig.getOr(BSPConfig.TRANSMITTER_RANGE_PER_CARD, 16);
        return (int) Math.min(BSPConfig.getOr(BSPConfig.TRANSMITTER_MAX_RANGE, 3072), r);
    }

    /** Whether {@code pos} (in this block's world) is inside the range: a cube around the block. */
    public boolean covers(BlockPos pos) {
        int r = range();
        return Math.abs(pos.getX() - worldPosition.getX()) <= r && Math.abs(pos.getY() - worldPosition.getY()) <= r && Math.abs(pos.getZ() - worldPosition.getZ()) <= r;
    }

    /** FE a tick while someone operates through it. */
    public int fePerTick() {
        return BSPConfig.getOr(BSPConfig.TRANSMITTER_FE_BASE, 20) + cardCount() * BSPConfig.getOr(BSPConfig.TRANSMITTER_FE_PER_CARD, 1);
    }

    public int energyStored() {
        return energy.getEnergyStored();
    }

    public int energyCapacity() {
        energy.sync();
        return energy.getMaxEnergyStored();
    }

    /**
     * A machine is being operated through this transmitter this tick: draws the tick's FE once (a second operator in the same tick costs
     * nothing more). False when the buffer cannot pay, which closes the operator's screen.
     */
    public boolean operate() {
        if (level == null) {
            return false;
        }
        long now = level.getGameTime();
        if (operatedTick == now) {
            return true;
        }
        if (!energy.draw(fePerTick())) {
            return false;
        }
        operatedTick = now;
        setChanged();
        return true;
    }

    /** Whether the buffer holds a tick's FE now. */
    public boolean powered() {
        return energy.getEnergyStored() >= fePerTick();
    }

    /** Whether a machine was operated through it in the last tick (the renderer's "in use" look). */
    public boolean inUse() {
        return operatedLastTick;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RemoteTransmitterBlockEntity be) {
        boolean now = level.getGameTime() - be.operatedTick <= 1;
        if (now != be.operatedLastTick) {
            be.operatedLastTick = now;
            be.changed();
        }
    }

    /** For tests: puts FE in the buffer. */
    public void fillForTest(int fe) {
        energy.receiveEnergy(fe, false);
    }

    // ------------------------------------------------------------------ capabilities

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ENERGY) {
            return energyCap.cast();
        }
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            return cardCap.cast();
        }
        return super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
        cardCap.invalidate();
    }

    // ------------------------------------------------------------------ save and sync

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) {
            tag.putUUID("Owner", owner);
            tag.putString("OwnerName", ownerName);
        }
        tag.put("Cards", cards.serializeNBT());
        tag.put("Energy", energy.serializeNBT());
        if (inventoryLink) {
            tag.putBoolean("InventoryLink", true);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        if (tag.contains("Cards")) {
            CompoundTag c = tag.getCompound("Cards").copy();
            c.putInt("Size", SLOTS); // never more slots than the block has, whatever the item said
            cards.deserializeNBT(c);
        }
        if (tag.contains("Energy")) {
            energy.deserializeNBT(tag.get("Energy"));
        }
        operatedLastTick = tag.getBoolean("InUse");
        inventoryLink = tag.getBoolean("InventoryLink");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putInt("CardCount", cardCount());
        tag.putBoolean("InUse", operatedLastTick);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        operatedLastTick = tag.getBoolean("InUse");
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
