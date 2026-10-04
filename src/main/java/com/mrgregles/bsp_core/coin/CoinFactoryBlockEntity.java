package com.mrgregles.bsp_core.coin;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.data.FactoryLedger;
import com.mrgregles.bsp_core.machine.StructurePartBlock;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import com.mrgregles.bsp_core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One slice of the Shatter Coin Factory, held by its controller block.
 *
 * <p><b>Structure</b>: 1 wide, 2 high, 3 long. Bottom row from the front: controller, Factory Frame,
 * Blank Hatch. Top row: Factory Frame, Factory Press, Power Port. Up to three Motivators sit on top.
 * <p><b>Joining</b>: complete slices side by side with the same facing and owner form one machine
 * of up to {@link #MAX_SLICES}. Each slice keeps its own blanks, press and coin tray and starts its
 * own presses; energy and the power switch are shared.
 * <p><b>Time</b> is real time. A press takes {@code factory.pressHours} per tier and keeps counting
 * while the chunk is unloaded or the server is off, as long as the machine was switched on.
 * <p><b>Energy</b> is taken in full from the shared pool when a press starts. Every slice adds
 * {@code factory.energyCapacity} to the pool.
 * <p><b>No output</b>: nothing can be piped out. Coins are taken from the tray by hand.
 */
public class CoinFactoryBlockEntity extends BlockEntity {
    public static final int SLOT_INPUT = 0, SLOT_OUTPUT = 1, SLOTS = 2, MAX_SLICES = 10, MOTIVATOR_CELLS = 3;
    /** Slice state shown on the screen. */
    public static final int STATE_NEEDS_BLANK = 0, STATE_PRESSING = 1, STATE_TRAY_FULL = 2, STATE_NEEDS_RF = 3, STATE_REDSTONE = 4;

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

    public record Part(BlockPos pos, Block block) {
    }

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            syncIfVisualChanged();
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return slot == SLOT_INPUT ? stack.getItem() instanceof CoinBlankItem : stack.getItem() instanceof ShatterCoinItem;
        }
    };

    /** This slice's share of the machine's energy pool. */
    private int energy;
    @Nullable
    private UUID owner;
    private String ownerName = "";
    private boolean enabled = true;
    private boolean formed;
    /** True while a redstone signal at the controller is holding this slice paused. */
    private boolean powered;
    private boolean demo;
    /** Speed multiplier from a nearby totem's Overclock aura. Server side. */
    private double overclock = 1.0;
    /** Bit i set = a Motivator sits on top cell i (0 = front). */
    private int motivatorMask;

    /** Tier being pressed, or -1 when idle. */
    private int jobTier = -1;
    /** Progress of the current press in base (un-motivated) milliseconds. */
    private double progressMs;
    /** Wall-clock time the progress was last brought up to date. */
    private long lastUpdateMs;
    private int lastVisualHash;
    /** Energy taken in through this slice's Power Port since the last one-second check, and the rate that gave. */
    private long intakeAcc;
    private int intakeRate;

    public CoinFactoryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COIN_FACTORY.get(), pos, state);
    }

    // ------------------------------------------------------------------ accessors

    public ItemStackHandler getItems() {
        return items;
    }

    public Direction facing() {
        return getBlockState().hasProperty(CoinFactoryBlock.FACING) ? getBlockState().getValue(CoinFactoryBlock.FACING) : Direction.NORTH;
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

    public boolean isFormed() {
        return formed;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** True while a blank is on the line and the machine is running. */
    public boolean isWorking() {
        return formed && (demo || (enabled && !powered && jobTier >= 0 && progressFraction() < 1f));
    }

    /** Demo mode: an operator has set this slice to play its working animation with nothing in it. */
    public boolean isDemo() {
        return demo;
    }

    /** Switches demo mode for the whole joined machine. */
    public void toggleDemo() {
        boolean on = !demo;
        for (CoinFactoryBlockEntity f : group()) {
            f.demo = on;
            f.setChanged();
            f.syncIfVisualChanged();
        }
    }

    /** The coin tier the model shows on its belts: the real job, or in demo mode each tier in turn. */
    @Nullable
    public CoinTier visualTier() {
        if (demo && level != null) {
            return CoinTier.values()[(int) (level.getGameTime() / 200 % CoinTier.values().length)];
        }
        return getJobTier();
    }

    @Nullable
    public CoinTier getJobTier() {
        return CoinTier.byOrdinal(jobTier);
    }

    public int motivatorMask() {
        return motivatorMask;
    }

    public int motivators() {
        return Integer.bitCount(motivatorMask);
    }

    public boolean hasCoins() {
        return !items.getStackInSlot(SLOT_OUTPUT).isEmpty();
    }

    /** Tier of the coins lying in the tray, or null. */
    @Nullable
    public CoinTier trayTier() {
        return items.getStackInSlot(SLOT_OUTPUT).getItem() instanceof ShatterCoinItem c ? c.tier : null;
    }

    /** Share of the press time removed by this slice's Motivators. */
    public double totalReduction() {
        Number r = BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.FACTORY_MOTIVATOR_REDUCTIONS, List.<Number>of()), motivators(), (Number) 0.0);
        return Math.min(r.doubleValue(), BSPConfig.getOr(BSPConfig.FACTORY_MAX_REDUCTION, 0.75));
    }

    private double speed() {
        return overclock / (1.0 - totalReduction());
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

    public int stateCode() {
        if (powered) {
            return STATE_REDSTONE;
        }
        CoinTier tier = getJobTier();
        if (tier != null) {
            return progressFraction() >= 1f ? STATE_TRAY_FULL : STATE_PRESSING;
        }
        if (!(items.getStackInSlot(SLOT_INPUT).getItem() instanceof CoinBlankItem blank)) {
            return STATE_NEEDS_BLANK;
        }
        if (!canOutput(blank.tier)) {
            return STATE_TRAY_FULL;
        }
        return poolStored() < blank.tier.energyPerCoin() ? STATE_NEEDS_RF : STATE_PRESSING;
    }

    /** RF per tick that came in through this slice's Power Port over the last second. */
    public int intakeRate() {
        return intakeRate;
    }

    // ------------------------------------------------------------------ structure

    private Direction back() {
        return facing().getOpposite();
    }

    /** The five blocks that, with the controller, make a slice. */
    public List<Part> parts() {
        Direction b = back();
        BlockPos p = worldPosition, up = p.above();
        return List.of(new Part(p.relative(b), ModBlocks.FACTORY_FRAME.get()), new Part(p.relative(b, 2), ModBlocks.FACTORY_BLANK_HATCH.get()),
                new Part(up, ModBlocks.FACTORY_FRAME.get()), new Part(up.relative(b), ModBlocks.FACTORY_PRESS.get()),
                new Part(up.relative(b, 2), ModBlocks.FACTORY_POWER_PORT.get()));
    }

    public List<Part> missingParts() {
        List<Part> missing = new ArrayList<>();
        if (level != null) {
            for (Part part : parts()) {
                if (!level.getBlockState(part.pos()).is(part.block())) {
                    missing.add(part);
                }
            }
        }
        return missing;
    }

    public BlockPos motivatorPos(int cell) {
        return worldPosition.above(2).relative(back(), cell);
    }

    /** Re-reads the structure, hides or shows its blocks and counts the Motivators on top. */
    public void checkStructure() {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean ok = missingParts().isEmpty();
        int mask = 0;
        if (ok) {
            for (int i = 0; i < MOTIVATOR_CELLS; i++) {
                if (level.getBlockState(motivatorPos(i)).is(ModBlocks.FACTORY_MOTIVATOR.get())) {
                    mask |= 1 << i;
                }
            }
        }
        showParts(!ok);
        if (ok) {
            for (Part part : parts()) {
                if (level.getBlockEntity(part.pos()) instanceof FactoryPortBlockEntity port) {
                    port.link(worldPosition);
                }
            }
        }
        if (ok != formed || mask != motivatorMask) {
            formed = ok;
            motivatorMask = mask;
            setChanged();
        }
        BlockState state = getBlockState();
        if (state.hasProperty(StructurePartBlock.FORMED) && state.getValue(StructurePartBlock.FORMED) != ok) {
            level.setBlock(worldPosition, state.setValue(StructurePartBlock.FORMED, ok), 3);
        }
        syncIfVisualChanged();
    }

    /** Shows the slice's blocks as ordinary cubes ({@code true}) or hides them behind the machine model. */
    public void showParts(boolean show) {
        if (level == null || level.isClientSide) {
            return;
        }
        List<BlockPos> all = new ArrayList<>();
        parts().forEach(p -> all.add(p.pos()));
        for (int i = 0; i < MOTIVATOR_CELLS; i++) {
            all.add(motivatorPos(i));
        }
        for (BlockPos pos : all) {
            BlockState state = level.getBlockState(pos);
            if (state.hasProperty(StructurePartBlock.FORMED) && state.getValue(StructurePartBlock.FORMED) == show
                    && (state.getBlock() instanceof FactoryPortBlock || state.is(ModBlocks.FACTORY_FRAME.get()) || state.is(ModBlocks.FACTORY_PRESS.get())
                    || state.is(ModBlocks.FACTORY_MOTIVATOR.get()))) {
                level.setBlock(pos, state.setValue(StructurePartBlock.FORMED, !show), 3);
            }
        }
    }

    // ------------------------------------------------------------------ joined machine

    /** Whether {@code other} is part of the same machine when it stands next to this slice. */
    public boolean joins(@Nullable CoinFactoryBlockEntity other) {
        return other != null && formed && other.formed && facing() == other.facing() && Objects.equals(owner, other.owner);
    }

    /** Model +x: the slice to the left of someone looking at the front. */
    public Direction rowDirection() {
        return facing().getClockWise();
    }

    @Nullable
    private CoinFactoryBlockEntity neighbour(Direction dir, int steps) {
        return level != null && level.getBlockEntity(worldPosition.relative(dir, steps)) instanceof CoinFactoryBlockEntity f ? f : null;
    }

    /** Every slice of the machine this slice belongs to, in row order. Always contains this slice. */
    public List<CoinFactoryBlockEntity> group() {
        List<CoinFactoryBlockEntity> out = new ArrayList<>();
        out.add(this);
        if (!formed) {
            return out;
        }
        Direction row = rowDirection();
        CoinFactoryBlockEntity prev = this;
        for (int i = 1; out.size() < MAX_SLICES; i++) {
            CoinFactoryBlockEntity n = neighbour(row.getOpposite(), i);
            if (!prev.joins(n)) {
                break;
            }
            out.add(0, n);
            prev = n;
        }
        prev = this;
        for (int i = 1; out.size() < MAX_SLICES; i++) {
            CoinFactoryBlockEntity n = neighbour(row, i);
            if (!prev.joins(n)) {
                break;
            }
            out.add(n);
            prev = n;
        }
        return out;
    }

    public int sliceCapacity() {
        return BSPConfig.getOr(BSPConfig.FACTORY_ENERGY_CAPACITY, 1_000_000);
    }

    public int poolStored() {
        long sum = 0;
        for (CoinFactoryBlockEntity f : group()) {
            sum += f.energy;
        }
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    public int poolCapacity() {
        return (int) Math.min(Integer.MAX_VALUE, (long) group().size() * sliceCapacity());
    }

    /** Adds energy to the pool through this slice's Power Port; returns what was accepted. */
    public int poolReceive(int amount, boolean simulate) {
        int left = Math.max(0, amount);
        for (CoinFactoryBlockEntity f : group()) {
            int room = Math.max(0, f.sliceCapacity() - f.energy);
            int put = Math.min(room, left);
            if (put > 0 && !simulate) {
                f.energy += put;
                f.setChanged();
            }
            left -= put;
            if (left <= 0) {
                break;
            }
        }
        int accepted = Math.max(0, amount) - left;
        if (!simulate) {
            intakeAcc += accepted;
        }
        return accepted;
    }

    private boolean poolConsume(int amount) {
        List<CoinFactoryBlockEntity> group = group();
        long sum = 0;
        for (CoinFactoryBlockEntity f : group) {
            sum += f.energy;
        }
        if (sum < amount) {
            return false;
        }
        int left = amount;
        for (CoinFactoryBlockEntity f : group) {
            int take = Math.min(f.energy, left);
            f.energy -= take;
            left -= take;
            f.setChanged();
        }
        return true;
    }

    /** The power switch: applies to the whole machine. */
    public void toggleEnabled() {
        boolean on = !enabled;
        long now = System.currentTimeMillis();
        for (CoinFactoryBlockEntity f : group()) {
            f.advance(now);
            f.enabled = on;
            f.setChanged();
            f.syncIfVisualChanged();
        }
    }

    // ------------------------------------------------------------------ pressing

    public void serverTick(ServerLevel level) {
        // each slice works on its own tick offset, so joined slices never act in step
        long phase = level.getGameTime() + worldPosition.asLong();
        if (Math.floorMod(phase, 5L) == 0) {
            checkStructure(); // four times a second, so a broken block shows the rest of the slice again almost at once
        }
        if (Math.floorMod(phase, 20L) != 0) {
            return;
        }
        intakeRate = (int) (intakeAcc / 20);
        intakeAcc = 0;
        double aura = com.mrgregles.bsp_core.totem.TotemAuras.overclock(level, worldPosition);
        if (aura != overclock) {
            advance(System.currentTimeMillis()); // settle progress at the old speed first
            overclock = aura;
        }
        boolean signal = level.hasNeighborSignal(worldPosition);
        if (signal != powered) {
            advance(System.currentTimeMillis()); // settle progress under the old state before pausing or resuming
            powered = signal;
            setChanged();
        }
        advance(System.currentTimeMillis());
    }

    /** Brings the slice up to date with the wall clock, finishing and starting presses as needed. */
    public void advance(long now) {
        if (lastUpdateMs <= 0 || now < lastUpdateMs || !formed || !enabled || powered) {
            lastUpdateMs = now; // first run, clock moved backwards, or the line is stopped
            syncIfVisualChanged();
            return;
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
                    // finished but the tray is full: hold at 100% until coins are taken
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
        if (!canOutput(tier) || !poolConsume(tier.energyPerCoin())) {
            return false;
        }
        items.setStackInSlot(SLOT_INPUT, in.copyWithCount(in.getCount() - 1));
        jobTier = tier.ordinal();
        progressMs = 0;
        return true;
    }

    private boolean canOutput(CoinTier tier) {
        ItemStack out = items.getStackInSlot(SLOT_OUTPUT);
        return out.isEmpty() || (out.is(tier.coin()) && out.getCount() < out.getMaxStackSize());
    }

    // ------------------------------------------------------------------ persistence + sync

    /** The model reaches past the controller's own block, so it must not be culled with it. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(4);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
        tag.putInt("Energy", energy);
        if (owner != null) {
            tag.putUUID("Owner", owner);
            tag.putString("OwnerName", ownerName);
        }
        tag.putBoolean("Enabled", enabled);
        tag.putBoolean("Formed", formed);
        tag.putBoolean("Powered", powered);
        tag.putBoolean("Demo", demo);
        tag.putInt("Motivators", motivatorMask);
        tag.putInt("JobTier", jobTier);
        tag.putDouble("ProgressMs", progressMs);
        tag.putLong("LastUpdateMs", lastUpdateMs);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Items")) {
            CompoundTag saved = tag.getCompound("Items");
            saved.putInt("Size", SLOTS); // older saves had upgrade slots
            items.deserializeNBT(saved);
        }
        energy = tag.getInt("Energy");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
        formed = tag.getBoolean("Formed");
        powered = tag.getBoolean("Powered");
        demo = tag.getBoolean("Demo");
        motivatorMask = tag.getInt("Motivators");
        jobTier = tag.contains("JobTier") ? tag.getInt("JobTier") : -1;
        progressMs = tag.getDouble("ProgressMs");
        lastUpdateMs = tag.getLong("LastUpdateMs");
    }

    /** What the renderer shows: formed, running, tier on the line, coins in the tray, Motivators. */
    private int visualHash() {
        int h = jobTier + 1;
        h = h * 31 + (items.getStackInSlot(SLOT_INPUT).getItem() instanceof CoinBlankItem b ? b.tier.ordinal() + 1 : 0);
        CoinTier tray = trayTier();
        h = h * 31 + (tray == null ? 0 : tray.ordinal() + 1);
        h = h * 31 + motivatorMask;
        h = h * 2 + (formed ? 1 : 0);
        h = h * 2 + (enabled ? 1 : 0);
        h = h * 2 + (demo ? 1 : 0);
        h = h * 2 + (isWorking() ? 1 : 0);
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
