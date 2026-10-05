package com.mrgregles.bsp_core.machine;

import com.mrgregles.bsp_core.coin.CoinFactoryBlockEntity.SideMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.material.RfUpgradeItem;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Shared base for the single-block BSP processing machines.
 *
 * <p>A subclass describes its slots with {@link Slot} entries (position in the screen plus a role),
 * says when it can work, how long a job takes and what finishing a job does. This class supplies the
 * inventory, optional furnace-style fuel, progress, per-face input/output modes for other mods'
 * pipes and hoppers, saving and client sync.
 */
public abstract class MachineBlockEntity extends BlockEntity {
    public enum Role { INPUT, FUEL, OUTPUT, UPGRADE, RF }

    /** One inventory slot: where it is drawn and what it is for. */
    public record Slot(Role role, int x, int y) {}

    public static final int DATA_COUNT = 17;

    protected final ItemStackHandler items;
    private final List<Slot> layout;
    private final SideMode[] sides = new SideMode[6];
    protected int progress;
    protected int burnTime;
    protected int burnTotal;
    private boolean wasWorking;
    /** The on/off switch in the screen. A machine that is switched off does not start or continue jobs. */
    private boolean enabled = true;
    private double speedCarry;
    private final MachineEnergy energy = new MachineEnergy();
    private final LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energy);

    @SuppressWarnings("unchecked")
    private final LazyOptional<IItemHandler>[] sideCaps = new LazyOptional[6];
    private final LazyOptional<IItemHandler> unsidedCap = LazyOptional.of(() -> new SidedView(null));

    protected MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, List<Slot> layout) {
        super(type, pos, state);
        this.layout = layout;
        this.items = new ItemStackHandler(layout.size()) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }

            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                if (layout.get(slot).role() == Role.RF) {
                    return stack.getItem() instanceof RfUpgradeItem;
                }
                return MachineBlockEntity.this.isItemValid(slot, stack);
            }

            @Override
            public int getSlotLimit(int slot) {
                Role role = layout.get(slot).role();
                return role == Role.UPGRADE || role == Role.RF ? 1 : 64;
            }
        };
        for (Direction d : Direction.values()) {
            sides[d.get3DDataValue()] = d == Direction.DOWN ? SideMode.OUTPUT : d == Direction.UP ? SideMode.INPUT : SideMode.BOTH;
            sideCaps[d.get3DDataValue()] = LazyOptional.of(() -> new SidedView(d));
        }
    }

    // ------------------------------------------------------------------ what a machine defines

    /** Whether {@code stack} belongs in {@code slot}. Output slots should return false. */
    protected abstract boolean isItemValid(int slot, ItemStack stack);

    /** True when there is a job to do and room for its results. */
    protected abstract boolean canWork();

    /** Ticks one job takes. */
    public abstract int workTime();

    /** Consume the inputs and produce the outputs of one job. */
    protected abstract void finishJob();

    /** Whether this machine burns furnace fuel from its FUEL slot. */
    protected boolean usesFuel() {
        return false;
    }

    public abstract String titleKey();

    /** Something the machine still needs before it can run: an icon and a sentence for the screen's status column. */
    public record Need(ItemStack icon, net.minecraft.network.chat.Component text) {}

    protected static Need need(net.minecraft.world.level.ItemLike icon, String key, Object... args) {
        return new Need(new ItemStack(icon), net.minecraft.network.chat.Component.translatable(key, args));
    }

    /**
     * What is stopping the machine right now, worked out on the client from the open screen:
     * {@code fluidMb} and {@code burning} come from the synced menu data, items from the slots.
     */
    public abstract List<Need> missing(int fluidMb, boolean burning);

    /** Example item shown faintly in an empty slot, and the tooltip explaining what belongs there. */
    public abstract ItemStack slotIcon(int slot);

    public abstract net.minecraft.network.chat.Component slotHint(int slot);

    /** Icon and hint for the RF upgrade slot, shared by every machine. */
    protected boolean isRfSlot(int slot) {
        return slot >= 0 && slot < layout.size() && layout.get(slot).role() == Role.RF;
    }

    // ------------------------------------------------------------------ RF upgrade

    /** Speed gain of the fitted RF upgrade (0 if none). */
    public double rfBonus() {
        int slot = slotOf(Role.RF, 0);
        return slot >= 0 && items.getStackInSlot(slot).getItem() instanceof RfUpgradeItem up ? up.bonus() : 0;
    }

    public boolean hasRfUpgrade() {
        int slot = slotOf(Role.RF, 0);
        return slot >= 0 && items.getStackInSlot(slot).getItem() instanceof RfUpgradeItem;
    }

    /** True for a machine that cannot run at all without RF (no upgrade item needed to accept it). */
    protected boolean needsRf() {
        return false;
    }

    /** RF such a machine uses on every working tick. */
    protected int rfPerWorkTick() {
        return 0;
    }

    public int energyStored() {
        return energy.getEnergyStored();
    }

    /** One extra line for the machine's screen (for example how many layers a stack has), or null. */
    @javax.annotation.Nullable
    public net.minecraft.network.chat.Component statusLine() {
        return null;
    }

    /** True when an RF upgrade is fitted and there is enough energy for this tick. */
    public boolean rfActive() {
        return hasRfUpgrade() && energy.getEnergyStored() >= BSPConfig.RF_PER_TICK.get();
    }

    /** Progress added per working tick. RF adds its bonus; machines may scale further. */
    protected double speed() {
        return rfActive() ? 1.0 + rfBonus() : 1.0;
    }

    /** Share of the normal lava a job uses: reduced by the RF bonus while powered. */
    protected double lavaFactor() {
        return rfActive() ? 1.0 - rfBonus() : 1.0;
    }

    /** Millibuckets in the machine's tank, for machines that have one. */
    public int fluidAmount() {
        return 0;
    }

    public int fluidCapacity() {
        return 0;
    }

    /** Colour of the tank gauge in the screen (ARGB). */
    public int fluidColor() {
        return 0xFF3A8BFF;
    }

    /** False for a multiblock whose structure is incomplete. Single blocks are always formed. */
    public boolean isFormed() {
        return true;
    }

    // ------------------------------------------------------------------ accessors

    public ItemStackHandler getItems() {
        return items;
    }

    public List<Slot> layout() {
        return layout;
    }

    public boolean hasFuelSlot() {
        return usesFuel();
    }

    public boolean isWorking() {
        return demo || progress > 0;
    }

    /** Demo mode: an operator has set the machine to play its working animation, whatever is in it. */
    public boolean isDemo() {
        return demo;
    }

    public void toggleDemo() {
        demo = !demo;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** True while fuel is burning. Synced to the client with the rest of the machine's state. */
    public boolean isBurning() {
        return demo || burnTime > 0;
    }

    public float progressFraction() {
        if (demo && level != null) {
            return (level.getGameTime() % 160) / 160f; // an eight-second job, over and over
        }
        return Math.min(1f, progress / (float) Math.max(1, workTime()));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void toggleEnabled() {
        enabled = !enabled;
        setChanged();
    }

    public SideMode getSide(Direction d) {
        return sides[d.get3DDataValue()];
    }

    /** Lets a machine set its own default for a face or, in a multiblock, for a numbered port. */
    protected void setSideMode(int index, SideMode mode) {
        sides[index] = mode;
    }

    public void cycleSide(Direction d) {
        sides[d.get3DDataValue()] = sides[d.get3DDataValue()].next();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    protected int slotOf(Role role, int nth) {
        int seen = 0;
        for (int i = 0; i < layout.size(); i++) {
            if (layout.get(i).role() == role && seen++ == nth) {
                return i;
            }
        }
        return -1;
    }

    /** True if {@code stack} can be added to output slot {@code slot} in full. */
    protected boolean fits(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        ItemStack cur = items.getStackInSlot(slot);
        return cur.isEmpty() || (ItemStack.isSameItemSameTags(cur, stack) && cur.getCount() + stack.getCount() <= cur.getMaxStackSize());
    }

    protected void addOutput(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack cur = items.getStackInSlot(slot);
        items.setStackInSlot(slot, cur.isEmpty() ? stack.copy() : cur.copyWithCount(cur.getCount() + stack.getCount()));
    }

    // ------------------------------------------------------------------ ticking

    /** True while a redstone signal is holding the machine paused. Server side. */
    private boolean powered;
    private boolean demo;
    /** Speed multiplier from a nearby totem's Overclock aura, refreshed every few seconds. Server side. */
    private double overclock = 1.0;

    public void serverTick(ServerLevel level) {
        powered = level.hasNeighborSignal(worldPosition); // a redstone signal pauses the machine
        if (level.getGameTime() % 100 == 0) {
            overclock = com.mrgregles.bsp_core.totem.TotemAuras.overclock(level, worldPosition);
        }
        boolean work = enabled && !powered && canWork() && (!needsRf() || energy.getEnergyStored() >= rfPerWorkTick());
        if (work && usesFuel() && burnTime <= 0) {
            work = tryIgnite();
        }
        if (burnTime > 0) {
            burnTime--;
        }
        if (work) {
            speedCarry += speed() * overclock;
            if (rfActive()) {
                energy.use(BSPConfig.RF_PER_TICK.get());
            }
            if (needsRf()) {
                energy.use(rfPerWorkTick());
            }
            int step = (int) speedCarry;
            speedCarry -= step;
            progress += step;
            if (progress >= workTime()) {
                progress = 0;
                finishJob();
            }
            setChanged();
        } else if (progress > 0) {
            progress = Math.max(0, progress - 2); // cools down, as a furnace does
        }
        boolean working = work || burnTime > 0;
        if (working && level.getGameTime() % 20 == 0) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        if (working != wasWorking) {
            wasWorking = working;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private boolean tryIgnite() {
        int slot = slotOf(Role.FUEL, 0);
        if (slot < 0) {
            return false;
        }
        ItemStack fuel = items.getStackInSlot(slot);
        int burn = ForgeHooks.getBurnTime(fuel, null);
        if (burn <= 0) {
            return false;
        }
        burnTime = burnTotal = burn;
        ItemStack remainder = fuel.getCraftingRemainingItem(); // e.g. the empty bucket from a lava bucket
        if (fuel.getCount() == 1 && !remainder.isEmpty()) {
            items.setStackInSlot(slot, remainder);
        } else {
            items.setStackInSlot(slot, fuel.copyWithCount(fuel.getCount() - 1));
        }
        return true;
    }

    // ------------------------------------------------------------------ menu data

    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> progress;
                case 1 -> workTime();
                case 2 -> Math.min(burnTime, Short.MAX_VALUE);
                case 3 -> Math.min(burnTotal, Short.MAX_VALUE);
                case 4, 5, 6, 7, 8, 9 -> sides[i - 4].ordinal();
                case 10 -> fluidAmount() & 0xFFFF;
                case 11 -> (fluidAmount() >>> 16) & 0xFFFF;
                case 12 -> fluidCapacity() & 0xFFFF;
                case 13 -> (fluidCapacity() >>> 16) & 0xFFFF;
                case 14 -> isFormed() ? 1 : 0;
                case 16 -> !enabled ? 0 : powered ? 2 : 1; // 2 = switched on but paused by redstone
                case 15 -> hasRfUpgrade() || needsRf() ? (int) (1000L * energy.getEnergyStored() / Math.max(1, energy.getMaxEnergyStored())) : -1;
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

    // ------------------------------------------------------------------ capabilities

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return (side == null ? unsidedCap : sideCaps[side.get3DDataValue()]).cast();
        }
        if (cap == ForgeCapabilities.ENERGY) {
            return energyCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
        unsidedCap.invalidate();
        for (LazyOptional<IItemHandler> c : sideCaps) {
            c.invalidate();
        }
    }

    /** What pipes and hoppers see: inputs and fuel can be inserted, outputs extracted, gated by the face's mode. */
    private class SidedView implements IItemHandler {
        @Nullable
        private final Direction side;

        SidedView(@Nullable Direction side) {
            this.side = side;
        }

        private SideMode mode() {
            return side == null ? SideMode.BOTH : sides[side.get3DDataValue()];
        }

        @Override
        public int getSlots() {
            return items.getSlots();
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            Role role = layout.get(slot).role();
            if (!mode().allowsInput() || (role != Role.INPUT && role != Role.FUEL)) {
                return stack;
            }
            return items.insertItem(slot, stack, simulate);
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!mode().allowsOutput()) {
                return ItemStack.EMPTY;
            }
            Role role = layout.get(slot).role();
            // outputs, plus empty buckets left in the fuel slot
            boolean spentFuel = role == Role.FUEL && ForgeHooks.getBurnTime(items.getStackInSlot(slot), null) <= 0;
            if (role != Role.OUTPUT && !spentFuel) {
                return ItemStack.EMPTY;
            }
            return items.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return items.isItemValid(slot, stack);
        }
    }

    /** RF buffer. Only accepts energy while an RF upgrade is fitted. */
    private class MachineEnergy extends EnergyStorage {
        MachineEnergy() {
            super(100_000, 2_000, 0);
        }

        @Override
        public int getMaxEnergyStored() {
            return BSPConfig.RF_CAPACITY.get();
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            if (!hasRfUpgrade() && !needsRf()) {
                return 0;
            }
            int accepted = Math.max(0, Math.min(getMaxEnergyStored() - energy, Math.min(2_000, maxReceive)));
            if (!simulate && accepted > 0) {
                energy += accepted;
                setChanged();
            }
            return accepted;
        }

        @Override
        public boolean canReceive() {
            return hasRfUpgrade() || needsRf();
        }

        void use(int amount) {
            energy = Math.max(0, energy - amount);
        }

        void set(int amount) {
            energy = amount;
        }
    }

    // ------------------------------------------------------------------ persistence + sync

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
        tag.putInt("Progress", progress);
        tag.putInt("BurnTime", burnTime);
        tag.putInt("BurnTotal", burnTotal);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putBoolean("Enabled", enabled);
        tag.putBoolean("Demo", demo);
        int[] modes = new int[6];
        for (int i = 0; i < 6; i++) modes[i] = sides[i].ordinal();
        tag.putIntArray("Sides", modes);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Items")) {
            items.deserializeNBT(tag.getCompound("Items"));
        }
        progress = tag.getInt("Progress");
        burnTime = tag.getInt("BurnTime");
        burnTotal = tag.getInt("BurnTotal");
        energy.set(tag.getInt("Energy"));
        enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
        demo = tag.getBoolean("Demo");
        int[] modes = tag.getIntArray("Sides");
        for (int i = 0; i < 6 && i < modes.length; i++) {
            sides[i] = SideMode.values()[Math.floorMod(modes[i], SideMode.values().length)];
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
