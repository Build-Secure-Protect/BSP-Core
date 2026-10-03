package com.mrgregles.bsp_core.coin;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.data.FactoryLedger;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The Shatter Coin Factory: presses coin blanks into Shatter Coins.
 *
 * <p><b>Time</b> is real time. A press takes {@code factory.pressHours} per tier and keeps counting
 * while the chunk is unloaded or the server is off; on load the factory catches up, finishing the
 * running press and any further presses its buffered energy and blanks allow.
 * <p><b>Energy</b> (Forge Energy / RF) is taken in full when a press starts.
 * <p><b>Upgrades</b>: four slots of Speed Gears whose reductions add up, capped by
 * {@code factory.maxTotalReduction}.
 * <p><b>Automation</b>: each face is NONE, INPUT (blanks in), OUTPUT (coins out) or BOTH.
 */
public class CoinFactoryBlockEntity extends BlockEntity {
    public static final int SLOT_INPUT = 0, SLOT_OUTPUT = 1, SLOT_UPGRADE_START = 2, UPGRADE_SLOTS = 4;
    public static final int SLOTS = SLOT_UPGRADE_START + UPGRADE_SLOTS;
    public static final int DATA_COUNT = 16;

    public enum SideMode {
        NONE, INPUT, OUTPUT, BOTH;

        public SideMode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public boolean allowsInput() {
            return this == INPUT || this == BOTH;
        }

        public boolean allowsOutput() {
            return this == OUTPUT || this == BOTH;
        }
    }

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            syncIfVisualChanged();
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            if (slot == SLOT_INPUT) return stack.getItem() instanceof CoinBlankItem;
            if (slot == SLOT_OUTPUT) return stack.getItem() instanceof ShatterCoinItem;
            return stack.getItem() instanceof SpeedGearItem;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot >= SLOT_UPGRADE_START ? 1 : 64;
        }
    };

    private final FactoryEnergy energy = new FactoryEnergy();
    private final SideMode[] sides = new SideMode[6];
    @Nullable
    private UUID owner;
    private String ownerName = "";

    /** Tier being pressed, or -1 when idle. */
    private int jobTier = -1;
    /** Progress of the current press in base (un-upgraded) milliseconds. */
    private double progressMs;
    /** Wall-clock time the progress was last brought up to date. */
    private long lastUpdateMs;
    private int lastVisualHash;

    private final LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energy);
    @SuppressWarnings("unchecked")
    private final LazyOptional<IItemHandler>[] sideCaps = new LazyOptional[6];
    private final LazyOptional<IItemHandler> unsidedCap = LazyOptional.of(() -> new SidedView(null));

    public CoinFactoryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COIN_FACTORY.get(), pos, state);
        for (Direction d : Direction.values()) {
            sides[d.get3DDataValue()] = d == Direction.UP ? SideMode.INPUT : d == Direction.DOWN ? SideMode.OUTPUT : SideMode.BOTH;
            sideCaps[d.get3DDataValue()] = LazyOptional.of(() -> new SidedView(d));
        }
    }

    // ------------------------------------------------------------------ accessors

    public ItemStackHandler getItems() {
        return items;
    }

    public SideMode getSide(Direction d) {
        return sides[d.get3DDataValue()];
    }

    public void cycleSide(Direction d) {
        sides[d.get3DDataValue()] = sides[d.get3DDataValue()].next();
        setChanged();
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwner(Player player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            FactoryLedger.get(serverLevel.getServer()).add(owner, GlobalPos.of(serverLevel.dimension(), worldPosition));
        }
    }

    public boolean isWorking() {
        return jobTier >= 0;
    }

    @Nullable
    public CoinTier getJobTier() {
        return CoinTier.byOrdinal(jobTier);
    }

    /** Mark (1-3) of the gear in upgrade slot {@code i}, or 0. */
    public int upgradeMark(int i) {
        ItemStack stack = items.getStackInSlot(SLOT_UPGRADE_START + i);
        return stack.getItem() instanceof SpeedGearItem gear ? gear.mark : 0;
    }

    public int fittedUpgrades() {
        int n = 0;
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            if (upgradeMark(i) > 0) n++;
        }
        return n;
    }

    /** Combined time reduction of the fitted gears, capped by config. */
    public double totalReduction() {
        double sum = 0;
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            if (items.getStackInSlot(SLOT_UPGRADE_START + i).getItem() instanceof SpeedGearItem gear) {
                sum += gear.reduction();
            }
        }
        return Math.min(sum, BSPConfig.FACTORY_MAX_REDUCTION.get());
    }

    private double speed() {
        return 1.0 / (1.0 - totalReduction());
    }

    /** Real seconds until the current press finishes, or 0 when idle. */
    public long remainingSeconds() {
        CoinTier tier = getJobTier();
        if (tier == null) {
            return 0;
        }
        return (long) Math.ceil(Math.max(0, tier.pressMillis() - progressMs) / speed() / 1000.0);
    }

    public float progressFraction() {
        CoinTier tier = getJobTier();
        return tier == null ? 0f : (float) Math.min(1.0, progressMs / tier.pressMillis());
    }

    // ------------------------------------------------------------------ pressing

    public void serverTick(ServerLevel level) {
        if (level.getGameTime() % 20 == 0) {
            advance(System.currentTimeMillis());
        }
    }

    /** Brings the factory up to date with the wall clock, finishing and starting presses as needed. */
    public void advance(long now) {
        if (lastUpdateMs <= 0 || now < lastUpdateMs) {
            lastUpdateMs = now; // first run, or the clock moved backwards
        }
        for (int guard = 0; guard < 256; guard++) {
            CoinTier tier = getJobTier();
            if (tier != null) {
                double speed = speed();
                long elapsed = now - lastUpdateMs;
                double needed = (tier.pressMillis() - progressMs) / speed;
                if (elapsed < needed) {
                    progressMs += elapsed * speed;
                    lastUpdateMs = now;
                    break;
                }
                if (!canOutput(tier)) {
                    // finished but the output is blocked: hold at 100% until there is room
                    progressMs = tier.pressMillis();
                    lastUpdateMs = now;
                    break;
                }
                ItemStack out = items.getStackInSlot(SLOT_OUTPUT);
                items.setStackInSlot(SLOT_OUTPUT, out.isEmpty() ? new ItemStack(tier.coin()) : out.copyWithCount(out.getCount() + 1));
                lastUpdateMs += (long) Math.max(0, needed);
                jobTier = -1;
                progressMs = 0;
            }
            if (!tryStartJob()) {
                lastUpdateMs = now;
                break;
            }
        }
        setChanged();
        syncIfVisualChanged();
    }

    private boolean tryStartJob() {
        ItemStack in = items.getStackInSlot(SLOT_INPUT);
        if (!(in.getItem() instanceof CoinBlankItem blank)) {
            return false;
        }
        CoinTier tier = blank.tier;
        if (!canOutput(tier) || energy.getEnergyStored() < tier.energyPerCoin()) {
            return false;
        }
        energy.consume(tier.energyPerCoin());
        items.setStackInSlot(SLOT_INPUT, in.copyWithCount(in.getCount() - 1));
        jobTier = tier.ordinal();
        progressMs = 0;
        return true;
    }

    private boolean canOutput(CoinTier tier) {
        ItemStack out = items.getStackInSlot(SLOT_OUTPUT);
        return out.isEmpty() || (out.is(tier.coin()) && out.getCount() < out.getMaxStackSize());
    }

    // ------------------------------------------------------------------ menu data

    /** Values the open screen needs; ints are sent as shorts, so large numbers are split in two. */
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            long rem = remainingSeconds();
            return switch (i) {
                case 0 -> energy.getEnergyStored() & 0xFFFF;
                case 1 -> (energy.getEnergyStored() >>> 16) & 0xFFFF;
                case 2 -> energy.getMaxEnergyStored() & 0xFFFF;
                case 3 -> (energy.getMaxEnergyStored() >>> 16) & 0xFFFF;
                case 4 -> Math.round(progressFraction() * 1000);
                case 5 -> (int) (rem & 0xFFFF);
                case 6 -> (int) ((rem >>> 16) & 0xFFFF);
                case 7 -> jobTier + 1;
                case 8, 9, 10, 11, 12, 13 -> sides[i - 8].ordinal();
                case 14 -> (int) Math.round(totalReduction() * 1000);
                case 15 -> level instanceof ServerLevel sl && owner != null ? FactoryLedger.get(sl.getServer()).count(owner) : 0;
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
        if (cap == ForgeCapabilities.ENERGY) {
            return energyCap.cast();
        }
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return (side == null ? unsidedCap : sideCaps[side.get3DDataValue()]).cast();
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

    /** What pipes, hoppers and storage buses see: slot 0 = blanks in, slot 1 = coins out, gated by the face's mode. */
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
            return 2;
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (slot != SLOT_INPUT || !mode().allowsInput()) {
                return stack;
            }
            return items.insertItem(SLOT_INPUT, stack, simulate);
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != SLOT_OUTPUT || !mode().allowsOutput()) {
                return ItemStack.EMPTY;
            }
            return items.extractItem(SLOT_OUTPUT, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return slot == SLOT_INPUT && items.isItemValid(SLOT_INPUT, stack);
        }
    }

    /** Energy buffer sized from config; accepts from any face, never gives energy out. */
    private class FactoryEnergy extends EnergyStorage {
        FactoryEnergy() {
            super(1_000_000, 10_000, 0);
        }

        @Override
        public int getMaxEnergyStored() {
            return BSPConfig.FACTORY_ENERGY_CAPACITY.get();
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int accepted = Math.max(0, Math.min(getMaxEnergyStored() - energy, Math.min(BSPConfig.FACTORY_MAX_RECEIVE.get(), maxReceive)));
            if (!simulate && accepted > 0) {
                energy += accepted;
                setChanged();
            }
            return accepted;
        }

        @Override
        public boolean canReceive() {
            return true;
        }

        void consume(int amount) {
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
        tag.putInt("Energy", energy.getEnergyStored());
        int[] modes = new int[6];
        for (int i = 0; i < 6; i++) modes[i] = sides[i].ordinal();
        tag.putIntArray("Sides", modes);
        if (owner != null) {
            tag.putUUID("Owner", owner);
            tag.putString("OwnerName", ownerName);
        }
        tag.putInt("JobTier", jobTier);
        tag.putDouble("ProgressMs", progressMs);
        tag.putLong("LastUpdateMs", lastUpdateMs);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Items")) {
            items.deserializeNBT(tag.getCompound("Items"));
        }
        energy.set(tag.getInt("Energy"));
        int[] modes = tag.getIntArray("Sides");
        for (int i = 0; i < 6 && i < modes.length; i++) {
            sides[i] = SideMode.values()[Math.floorMod(modes[i], SideMode.values().length)];
        }
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        jobTier = tag.contains("JobTier") ? tag.getInt("JobTier") : -1;
        progressMs = tag.getDouble("ProgressMs");
        lastUpdateMs = tag.getLong("LastUpdateMs");
    }

    /** What the renderer shows: working or not, tier on the die, which sockets are lit. */
    private int visualHash() {
        int h = jobTier + 1;
        ItemStack in = items.getStackInSlot(SLOT_INPUT);
        h = h * 31 + (in.getItem() instanceof CoinBlankItem b ? b.tier.ordinal() + 1 : 0);
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            h = h * 7 + upgradeMark(i);
        }
        return h;
    }

    private void syncIfVisualChanged() {
        if (level == null || level.isClientSide) {
            return;
        }
        int h = visualHash();
        if (h != lastVisualHash) {
            lastVisualHash = h;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
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
