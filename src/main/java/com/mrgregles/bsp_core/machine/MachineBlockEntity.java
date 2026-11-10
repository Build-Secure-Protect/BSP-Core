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
    /** One handler per face (what that face's mode allows) and one for {@code null} (the machine's own access). They are made once and
     *  handed out on every call, so a pipe that keeps one keeps working; {@link #reviveCaps} makes new ones after Forge invalidated them. */
    private final SidedView[] sideViews = new SidedView[6];
    private final SidedView unsidedView = new SidedView(null);
    private LazyOptional<IEnergyStorage> energyCap;
    @SuppressWarnings("unchecked")
    private final LazyOptional<IItemHandler>[] sideCaps = new LazyOptional[6];
    private LazyOptional<IItemHandler> unsidedCap;

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

            @Override
            protected int getStackLimit(int slot, @Nonnull ItemStack stack) {
                return Math.min(super.getStackLimit(slot, stack), MachineBlockEntity.this.stackLimit(slot, stack));
            }
        };
        for (Direction d : Direction.values()) {
            sides[d.get3DDataValue()] = d == Direction.DOWN ? SideMode.OUTPUT : d == Direction.UP ? SideMode.INPUT : SideMode.BOTH;
            sideViews[d.get3DDataValue()] = new SidedView(d);
        }
        makeCaps();
    }

    private void makeCaps() {
        energyCap = LazyOptional.of(() -> energy);
        unsidedCap = LazyOptional.of(() -> unsidedView);
        for (int i = 0; i < 6; i++) {
            SidedView view = sideViews[i];
            sideCaps[i] = LazyOptional.of(() -> view);
        }
    }

    // ------------------------------------------------------------------ what a machine defines

    /** Whether {@code stack} belongs in {@code slot}. Output slots should return false. */
    protected abstract boolean isItemValid(int slot, ItemStack stack);

    /** Whether pipes may take {@code stack} out of {@code slot} even through a side set to input only (the Totem Forge's spent lava buckets). */
    protected boolean alwaysExtractable(int slot, ItemStack stack) {
        return false;
    }

    /**
     * Whether pipes may take {@code stack} back out of input slot {@code slot} (through a face that allows output) because the machine
     * cannot use it however much more arrives, for example Tetrium Slag left in the Illyrium Crucible's additive slot while it alloys.
     * Without this such a stack blocks the slot, and every other item it takes, until a player clears it.
     */
    protected boolean unusableInput(int slot, ItemStack stack) {
        return false;
    }

    /** How many of {@code stack} the slot may hold at most, for pipes, hoppers and the screen alike; a machine lowers it to take only what it can use. */
    protected int stackLimit(int slot, ItemStack stack) {
        return 64;
    }

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

    /** The blocks a Plasma Injector may stand against to serve this machine: the block itself, or every block of a formed multiblock. */
    protected Iterable<BlockPos> boostPositions() {
        return List.of(worldPosition);
    }

    /** Whether an injector pointing at {@code pos} speeds this machine (one of its blocks, or a formed multiblock's part). */
    public boolean boostedAt(BlockPos pos) {
        for (BlockPos p : boostPositions()) {
            if (p.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    /** mB/t of Wave Plasma arriving at this machine's injectors, and the speed factor it gives. */
    public int plasmaRate() {
        return plasmaRate;
    }

    /** The speed factor the injectors give: the plasma's curve, scaled by the feeding totem's Overclock. The tick and Jade both read this. */
    public double plasmaFactor() {
        return com.mrgregles.bsp_core.plasma.PlasmaBoost.apply(plasma, overclock);
    }

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
    /** The feeding totem's Overclock scale, through the injectors (1.0 with none), refreshed once a second. Server side. */
    private double overclock = 1.0;
    /** Speed multiplier from Plasma Injectors standing against the machine, and the mB/t they get. Server side. */
    private double plasma = 1.0;
    private int plasmaRate;

    public void serverTick(ServerLevel level) {
        powered = level.hasNeighborSignal(worldPosition); // a redstone signal pauses the machine
        if (level.getGameTime() % 20 == 0) {
            plasmaRate = com.mrgregles.bsp_core.plasma.PlasmaBoost.rate(level, boostPositions());
            plasma = com.mrgregles.bsp_core.plasma.PlasmaBoost.factor(plasmaRate);
            overclock = com.mrgregles.bsp_core.plasma.PlasmaBoost.scale(level, boostPositions()); // the feeding totem's Overclock, through the injectors
        }
        boolean work = enabled && !powered && canWork() && (!needsRf() || energy.getEnergyStored() >= rfPerWorkTick());
        if (work && usesFuel() && burnTime <= 0) {
            work = tryIgnite();
        }
        if (burnTime > 0) {
            burnTime--;
        }
        if (work) {
            speedCarry += speed() * plasmaFactor(); // Overclock scales the plasma speed-up, nothing more
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
            sendData(); // progress for the renderers: the data alone, since a block update makes every client re-mesh the chunk section
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

    /** Forge revives a block entity's capabilities after invalidating them; an invalidated LazyOptional stays dead, so make new ones. */
    @Override
    public void reviveCaps() {
        super.reviveCaps();
        makeCaps();
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

        /** Whether items go into this slot through this face: input and fuel slots, through a face that takes items in. */
        private boolean takesIn(int slot) {
            Role role = layout.get(slot).role();
            return mode().allowsInput() && (role == Role.INPUT || role == Role.FUEL);
        }

        /**
         * Whether what is in this slot comes out through this face: outputs, what is left in a fuel slot that it would not take now (the
         * empty bucket a lava or water bucket leaves; never magma blocks or water buckets still to be used, which burn no time) and inputs
         * the machine cannot use, through a face that gives items out (or anywhere, for {@link #alwaysExtractable}).
         */
        private boolean givesOut(int slot) {
            ItemStack there = items.getStackInSlot(slot);
            if (there.isEmpty() || (!mode().allowsOutput() && !alwaysExtractable(slot, there))) {
                return false;
            }
            Role role = layout.get(slot).role();
            return role == Role.OUTPUT || (role == Role.FUEL && !items.isItemValid(slot, there)) || (role == Role.INPUT && unusableInput(slot, there));
        }

        /**
         * What a slot holds, as this face sees it: a slot this face can neither fill nor empty (upgrades, and inputs seen from an
         * output-only face) looks empty. Pipes that walk the slots and stop at the first one they cannot empty (Industrial Foregoing's
         * transporter), and storage views that list every slot (Refined Storage's External Storage), then see only what they can move.
         * Through a face that takes items in, inputs show, so pipes can top them up and AE2's blocking mode sees them.
         */
        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return takesIn(slot) || givesOut(slot) ? items.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        /** The machine works on its own copy: a pipe may hand over a stack it still holds (Industrial Foregoing's conveyor gives an item entity's own). */
        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !takesIn(slot)) {
                return stack;
            }
            ItemStack left = items.insertItem(slot, stack.copy(), simulate);
            return left.getCount() == stack.getCount() ? stack : left;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return givesOut(slot) ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        /**
         * The most this slot holds of what is in it now, the per-item cap included (the Totem Forge takes only the ingots it still needs):
         * Mekanism plans items already on their way from this number without asking the machine again.
         */
        @Override
        public int getSlotLimit(int slot) {
            ItemStack there = items.getStackInSlot(slot);
            int limit = items.getSlotLimit(slot);
            return there.isEmpty() ? limit : Math.min(limit, Math.min(there.getMaxStackSize(), stackLimit(slot, there)));
        }

        /** The same answer {@link #insertItem} gives: only input and fuel slots, only through a face that takes items in. Pipes that check this first (Mekanism) then never plan a move the insert refuses. */
        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return takesIn(slot) && items.isItemValid(slot, stack);
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

    /** Sends this block entity's data to the players tracking its chunk, without a block update (which would make every client re-mesh the chunk section). */
    protected void sendData() {
        if (level instanceof net.minecraft.server.level.ServerLevel sl) {
            var packet = getUpdatePacket();
            if (packet != null) {
                sl.getChunkSource().chunkMap.getPlayers(new net.minecraft.world.level.ChunkPos(worldPosition), false).forEach(p -> p.connection.send(packet));
            }
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
