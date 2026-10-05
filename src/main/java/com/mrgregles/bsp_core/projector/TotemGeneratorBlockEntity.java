package com.mrgregles.bsp_core.projector;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.projector.GeneratorUpgradeItem.Kind;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A Totem Generator: draws off some of a Shatter Totem's base powers and sends them down a Totem
 * Cable to a Totem Projector.
 *
 * <p><b>The array.</b> A generator works if it is directly under a placed totem, or touches (on a
 * side) a working generator in the three by three layer centred under that totem. Up to nine can
 * be joined. Each one is independent: its own power buffer, its own choice of powers, its own
 * cable run and its own projector.
 * <p><b>Powers.</b> Two of the totem's base powers at once (three with the Channel Expander), out
 * of Fortify, Healing Aura, Alarm, Ward, Sanctuary and Overclock.
 * <p><b>Reach.</b> The cable run is counted in cable blocks from the generator to the projector,
 * along the shortest way. It works if it is no longer than both the generator's reach (15 blocks,
 * raised by Reach Amplifiers) and the reach of the weakest cable kind in the run. Strength falls
 * from full at the generator to half at the end of that working length; levels round down but
 * never below 1.
 * <p><b>Power.</b> {@code projector.generatorRfPerTick} while sending. Joined generators share
 * their RF: one cable into any of them powers them all, as long as it can deliver enough, and
 * more cables add throughput. The projector needs its own.
 * <p><b>Who may open it.</b> The owner of the totem it is connected to, so after a steal, the
 * thief. A generator not connected to an owned totem can be opened by anyone.
 */
public class TotemGeneratorBlockEntity extends BlockEntity {
    public static final Buff[] SENDABLE = {Buff.FORTIFY, Buff.HEALING, Buff.ALARM, Buff.WARD, Buff.SANCTUARY, Buff.OVERCLOCK};
    public static final int AMP1 = 0, AMP3 = 2, EXPANDER = 3, SOCKETS = 4, CAPACITY = 50_000;
    public static final int NO_TOTEM = 0, NO_PROJECTOR = 1, TOO_FAR = 2, NO_POWER = 3, NOTHING_CHOSEN = 4, SENDING = 5;
    private static final int SEARCH_LIMIT = 600;

    private final ItemStackHandler items = new ItemStackHandler(SOCKETS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return fits(slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };
    private final Buffer energy = new Buffer();

    private final class Buffer extends EnergyStorage {
        Buffer() {
            super(CAPACITY, 2_000, 0);
        }

        void use(int rf) {
            energy = Math.max(0, energy - rf);
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int got = super.receiveEnergy(maxReceive, simulate);
            if (got > 0 && !simulate) {
                setChanged();
            }
            return got;
        }
    }

    private final LazyOptional<IEnergyStorage> cap = LazyOptional.of(() -> energy);
    /** Bit {@code i} set: {@link #SENDABLE}[i] is chosen. */
    private int chosen;
    // worked out once a second on the server; the first four are synced for the renderer and the screens
    private boolean linked;
    private int centreDx, centreDz, arrayCount = 1, state = NO_TOTEM;
    private int run, cableReach, poolPermille;
    /** The array holds enough RF to run a generator: the ring around the totem shows. Synced. */
    private boolean powered;
    private final int[] totemLevels = new int[SENDABLE.length], arriving = new int[SENDABLE.length];

    public TotemGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TOTEM_GENERATOR.get(), pos, state);
    }

    // ------------------------------------------------------------------ sockets

    public ItemStackHandler getItems() {
        return items;
    }

    /** Reach Amplifiers go in order, each needing the one before; the Channel Expander has its own socket. */
    public boolean fits(int slot, ItemStack stack) {
        Kind k = GeneratorUpgradeItem.kindOf(stack);
        if (k == null) {
            return false;
        }
        if (slot == EXPANDER) {
            return k == Kind.CHANNEL;
        }
        return k.ordinal() == slot && (slot == AMP1 || !items.getStackInSlot(slot - 1).isEmpty());
    }

    public boolean removable(int slot) {
        return slot == EXPANDER || slot == AMP3 || items.getStackInSlot(slot + 1).isEmpty();
    }

    public int amplifiers() {
        int n = 0;
        for (int s = AMP1; s <= AMP3; s++) {
            n += items.getStackInSlot(s).isEmpty() ? 0 : 1;
        }
        return n;
    }

    /** How far this generator can push, in cable blocks. */
    public int reach() {
        return BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.GENERATOR_REACH, List.<Integer>of()), amplifiers() + 1, 15);
    }

    public int channels() {
        return BSPConfig.GENERATOR_CHANNELS.get() + (items.getStackInSlot(EXPANDER).isEmpty() ? 0 : 1);
    }

    // ------------------------------------------------------------------ the array

    private boolean generatorAt(BlockPos pos) {
        return level != null && level.getBlockState(pos).getBlock() instanceof TotemGeneratorBlock;
    }

    /** The generator directly under a totem that this one belongs to, or null if it is not connected to one. */
    @Nullable
    public BlockPos findCentre() {
        if (level == null) {
            return null;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos c = worldPosition.offset(dx, 0, dz);
                if (generatorAt(c) && level.getBlockEntity(c.above()) instanceof ShatterTotemBlockEntity && members(c).contains(worldPosition)) {
                    return c;
                }
            }
        }
        return null;
    }

    /** Every generator joined, side to side, to the one at {@code centre}, within the three by three around it. */
    public Set<BlockPos> members(BlockPos centre) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(centre);
        seen.add(centre);
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(d);
                if (Math.abs(n.getX() - centre.getX()) <= 1 && Math.abs(n.getZ() - centre.getZ()) <= 1 && generatorAt(n) && seen.add(n)) {
                    queue.add(n);
                }
            }
        }
        return seen;
    }

    /** The other generators of this one's array, for sharing RF. */
    private List<TotemGeneratorBlockEntity> others(ServerLevel sl, @Nullable BlockPos centre) {
        List<TotemGeneratorBlockEntity> out = new ArrayList<>();
        if (centre != null) {
            for (BlockPos p : members(centre)) {
                if (!p.equals(worldPosition) && sl.getBlockEntity(p) instanceof TotemGeneratorBlockEntity g) {
                    out.add(g);
                }
            }
        }
        return out;
    }

    /** Takes {@code rf} from this generator's buffer first, then from the rest of the array. Call only when the array holds that much. */
    private void drain(int rf, List<TotemGeneratorBlockEntity> others) {
        int mine = Math.min(rf, energy.getEnergyStored());
        energy.use(mine);
        rf -= mine;
        for (TotemGeneratorBlockEntity g : others) {
            if (rf <= 0) {
                break;
            }
            int part = Math.min(rf, g.energy.getEnergyStored());
            g.energy.use(part);
            g.setChanged();
            rf -= part;
        }
    }

    /** Whether {@code player} may open this generator: the owner of the totem it serves (or an admin); anyone if it serves no owned totem. */
    public boolean mayOpen(net.minecraft.world.entity.player.Player player) {
        BlockPos centre = findCentre();
        if (centre == null || level == null || !(level.getBlockEntity(centre.above()) instanceof ShatterTotemBlockEntity totem) || totem.getOwner().isEmpty()) {
            return true;
        }
        return totem.isOwner(player.getUUID()) || com.mrgregles.bsp_core.admin.Admins.isAdmin(player);
    }

    // ------------------------------------------------------------------ the cable run

    private record Found(TotemProjectorBlockEntity projector, int run, int cableReach) {
    }

    /** The nearest free projector along the cables leaving this generator's sides and bottom. */
    @Nullable
    private Found findProjector(ServerLevel sl) {
        record Node(BlockPos pos, int run, int reach) {
        }
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<Node> queue = new ArrayDeque<>();
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN}) {
            BlockPos p = worldPosition.relative(d);
            if (sl.getBlockState(p).getBlock() instanceof TotemCableBlock cable && seen.add(p)) {
                queue.add(new Node(p, 1, cable.kind.reach()));
            }
        }
        while (!queue.isEmpty() && seen.size() < SEARCH_LIMIT) {
            Node n = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos p = n.pos.relative(d);
                if (sl.getBlockEntity(p) instanceof TotemProjectorBlockEntity projector && projector.accepts(worldPosition)) {
                    return new Found(projector, n.run, n.reach);
                }
                if (sl.getBlockState(p).getBlock() instanceof TotemCableBlock cable && seen.add(p)) {
                    queue.add(new Node(p, n.run + 1, Math.min(n.reach, cable.kind.reach())));
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ tick

    public void serverTick(ServerLevel sl) {
        if (sl.getGameTime() % 20 != 0) {
            return;
        }
        BlockPos centre = findCentre();
        boolean wasLinked = linked;
        int oldDx = centreDx, oldDz = centreDz, oldCount = arrayCount, oldState = state;
        boolean wasPowered = powered;
        linked = centre != null;
        centreDx = linked ? centre.getX() - worldPosition.getX() : 0;
        centreDz = linked ? centre.getZ() - worldPosition.getZ() : 0;
        arrayCount = linked ? members(centre).size() : 1;
        java.util.Arrays.fill(totemLevels, 0);
        java.util.Arrays.fill(arriving, 0);
        ShatterTotemBlockEntity totem = linked && sl.getBlockEntity(centre.above()) instanceof ShatterTotemBlockEntity t && t.getOwner().isPresent() ? t : null;
        if (totem != null) {
            for (int i = 0; i < SENDABLE.length; i++) {
                totemLevels[i] = totem.getUpgradeLevel(SENDABLE[i]);
            }
        }
        // drop choices the totem no longer has, and any beyond the number of channels
        int kept = 0;
        for (int i = 0; i < SENDABLE.length; i++) {
            if ((chosen & (1 << i)) != 0 && (totemLevels[i] <= 0 && totem != null || kept >= channels())) {
                chosen &= ~(1 << i);
            } else if ((chosen & (1 << i)) != 0) {
                kept++;
            }
        }
        Found found = totem == null ? null : findProjector(sl);
        run = found == null ? 0 : found.run;
        cableReach = found == null ? 0 : found.cableReach;
        int limit = Math.min(reach(), cableReach), need = BSPConfig.GENERATOR_RF.get() * 20;
        List<TotemGeneratorBlockEntity> others = others(sl, centre);
        long pool = energy.getEnergyStored();
        for (TotemGeneratorBlockEntity g : others) {
            pool += g.energy.getEnergyStored();
        }
        poolPermille = (int) (1000L * pool / ((long) CAPACITY * (others.size() + 1)));
        powered = linked && pool >= need;
        if (totem == null) {
            state = NO_TOTEM;
        } else if (found == null) {
            state = NO_PROJECTOR;
        } else if (run > limit) {
            state = TOO_FAR;
        } else if (chosen == 0) {
            state = NOTHING_CHOSEN;
        } else if (pool < need) {
            state = NO_POWER;
        } else {
            state = SENDING;
            drain(need, others);
            double strength = 1.0 - 0.5 * run / Math.max(1, limit);
            int[] out = new int[Buff.values().length];
            for (int i = 0; i < SENDABLE.length; i++) {
                if ((chosen & (1 << i)) != 0 && totemLevels[i] > 0) {
                    arriving[i] = Math.max(1, (int) Math.floor(totemLevels[i] * strength));
                    out[SENDABLE[i].ordinal()] = arriving[i];
                }
            }
            found.projector.feed(worldPosition, totem.getOwner().get().uuid(), out);
        }
        setChanged();
        if (wasLinked != linked || oldDx != centreDx || oldDz != centreDz || oldCount != arrayCount || oldState != state || wasPowered != powered) {
            sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Switches power {@code index} of {@link #SENDABLE} on or off, within the number of channels. */
    public void toggle(int index) {
        if (index < 0 || index >= SENDABLE.length) {
            return;
        }
        int bit = 1 << index;
        if ((chosen & bit) != 0) {
            chosen &= ~bit;
        } else if (Integer.bitCount(chosen) < channels() && totemLevels[index] > 0) {
            chosen |= bit;
        }
        setChanged();
    }

    // ------------------------------------------------------------------ read by menus and renderers

    public boolean isLinked() {
        return linked;
    }

    /** True for the generator directly under the totem. */
    public boolean isCentre() {
        return linked && centreDx == 0 && centreDz == 0;
    }

    public int arrayCount() {
        return arrayCount;
    }

    public int state() {
        return state;
    }

    public int run() {
        return run;
    }

    public int cableReach() {
        return cableReach;
    }

    public int chosen() {
        return chosen;
    }

    public int totemLevel(int i) {
        return totemLevels[i];
    }

    public int arriving(int i) {
        return arriving[i];
    }

    /** RF held by the whole array, in thousandths of what it can hold. */
    public int poolPermille() {
        return poolPermille;
    }

    public boolean isPowered() {
        return powered;
    }

    /** The ring that rises around the totem is drawn from the middle generator and is wider than the block. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(2, 0, 2).expandTowards(0, 2, 0);
    }

    public List<ItemStack> drops() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < SOCKETS; i++) {
            out.add(items.getStackInSlot(i));
        }
        return out;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        return capability == ForgeCapabilities.ENERGY && side != Direction.UP ? cap.cast() : super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        cap.invalidate();
    }

    private void writeLink(CompoundTag tag) {
        tag.putBoolean("Linked", linked);
        tag.putInt("CentreDx", centreDx);
        tag.putInt("CentreDz", centreDz);
        tag.putInt("ArrayCount", arrayCount);
        tag.putInt("State", state);
        tag.putBoolean("Powered", powered);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
        tag.put("Energy", energy.serializeNBT());
        tag.putInt("Chosen", chosen);
        writeLink(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Items")) {
            items.deserializeNBT(tag.getCompound("Items"));
        }
        if (tag.contains("Energy")) {
            energy.deserializeNBT(tag.get("Energy"));
        }
        chosen = tag.getInt("Chosen");
        linked = tag.getBoolean("Linked");
        centreDx = tag.getInt("CentreDx");
        centreDz = tag.getInt("CentreDz");
        arrayCount = Math.max(1, tag.getInt("ArrayCount"));
        state = tag.getInt("State");
        powered = tag.getBoolean("Powered");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        writeLink(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
