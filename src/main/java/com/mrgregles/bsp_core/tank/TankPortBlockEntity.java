package com.mrgregles.bsp_core.tank;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.plasma.PlasmaAccess;
import com.mrgregles.bsp_core.plasma.PlasmaNetwork;
import com.mrgregles.bsp_core.plasma.PlasmaReceiver;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * A Tank Port: a receiver at the end of a run that comes from an interface, and a source for runs that only lead to bases, chargers
 * and other tanks. In-and-out ports do both, following the run; the wrench sets Input or Output only. Each port gives at most an
 * interface face's worth ({@code plasma.interfaceSideMax}) a tick.
 */
public class TankPortBlockEntity extends TankPartBlockEntity implements PlasmaReceiver, PlasmaNetwork.Reservoir {
    public enum Mode {
        BOTH, INPUT, OUTPUT;

        public String key() {
            return "gui.bsp_core.tank.mode." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static final int FED_TICKS = 45;
    private Mode mode = Mode.BOTH;
    private int in, out;
    private long fedAt = -1000;
    private final PlasmaNetwork network = new PlasmaNetwork(this, this);

    public TankPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TANK_PORT.get(), pos, state);
        network.receiverFilter(p -> !(level != null && level.getBlockEntity(p) instanceof TankPortBlockEntity other && Objects.equals(other.master, master)));
    }

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        sync();
    }

    public int in() {
        return in;
    }

    public int out() {
        return out;
    }

    public PlasmaNetwork network() {
        return network;
    }

    // ---- as a receiver, fed by an interface's run

    @Override
    public int wanted() {
        TankPartBlockEntity m = masterEntity();
        return m == null ? 0 : (int) Math.min(Integer.MAX_VALUE, m.capacity() - m.stored());
    }

    @Override
    public boolean accepts(BlockPos from, BlockState state, Direction d) {
        return mode != Mode.OUTPUT && master != null;
    }

    @Override
    public void feed(BlockPos source, int[] offeredByOrdinal, int repeaters, int pressurePerTick, int movedMB, PlasmaAccess access, @Nullable net.minecraft.core.GlobalPos anchorTotem) {
        feed(source, offeredByOrdinal, repeaters, pressurePerTick, movedMB, access, anchorTotem, null);
    }

    /** Plasma in from a cable: the Mob Chamber powers' share to each power's pool at its level, the rest by its list (plain or totem). */
    @Override
    public void feed(BlockPos source, int[] offeredByOrdinal, int repeaters, int pressurePerTick, int movedMB, PlasmaAccess access,
                     @Nullable net.minecraft.core.GlobalPos anchorTotem, @Nullable long[][] mob) {
        TankPartBlockEntity m = masterEntity();
        if (m == null) {
            return;
        }
        long mobIn = 0;
        if (mob != null) {
            var buffs = Buff.values();
            for (int i = 0; i < mob.length; i++) {
                for (int l = 1; l < mob[i].length; l++) {
                    if (mob[i][l] > 0 && com.mrgregles.bsp_core.plasma.MobPlasma.isMob(i)) {
                        mobIn += m.addMob(buffs[i], l, Math.min(mob[i][l], movedMB - mobIn));
                    }
                }
            }
        }
        m.add(Math.max(0, movedMB - mobIn), com.mrgregles.bsp_core.plasma.MobPlasma.withoutMob(offeredByOrdinal));
        if (movedMB > mobIn) {
            m.addFedBy(access); // who fed it travels on with the plasma
        }
        in = pressurePerTick;
        fedAt = level == null ? 0 : level.getGameTime();
        m.portReport(worldPosition, mode.ordinal(), in, out);
    }

    // ---- as a source, feeding runs of its own

    private boolean fedLately() {
        return level != null && level.getGameTime() - fedAt <= FED_TICKS;
    }

    @Override
    public int flow() {
        TankPartBlockEntity m = masterEntity();
        if (m == null || mode == Mode.INPUT || fedLately()) {
            return 0;
        }
        return (int) Math.min(BSPConfig.getOr(BSPConfig.INTERFACE_SIDE_MAX, 1000), Math.min(Integer.MAX_VALUE, m.open() / 20));
    }

    @Override
    public int tank() {
        TankPartBlockEntity m = masterEntity();
        return m == null ? 0 : (int) Math.min(Integer.MAX_VALUE, m.open()); // mob pools stay in for a Tank Extractor
    }

    @Override
    public int drain(int mB) {
        TankPartBlockEntity m = masterEntity();
        return m == null ? 0 : (int) m.drain(mB);
    }

    @Override
    public boolean claim(BlockPos by) {
        return true;
    }

    /** Who fed the tank: a projector fed from it knows its team. */
    @Override
    public PlasmaAccess access() {
        TankPartBlockEntity m = masterEntity();
        return m == null ? new PlasmaAccess() : m.fedBy();
    }

    @Override
    public int[] offered() {
        TankPartBlockEntity m = masterEntity();
        return m == null ? new int[Buff.values().length] : m.offered(); // the powers the stored plasma still carries, from the totems that fed it
    }

    @Nullable
    @Override
    public BlockPos totemPos() {
        return null;
    }

    @Override
    public int cloak() {
        return 0;
    }

    @Override
    public void serverTick(ServerLevel sl) {
        super.serverTick(sl);
        if (sl.getGameTime() % 20 != 0) {
            return;
        }
        TankPartBlockEntity m = masterEntity();
        if (m == null) {
            return;
        }
        if (!fedLately()) {
            in = 0;
        }
        if (mode != Mode.INPUT && !fedLately() && m.open() > 0) {
            network.tick(sl);
            out = network.lastDeliveredTotal();
        } else {
            out = 0;
        }
        m.portReport(worldPosition, mode.ordinal(), in, out);
    }

    @Override
    protected void write(CompoundTag tag) {
        super.write(tag);
        tag.putByte("Mode", (byte) mode.ordinal());
        tag.putInt("In", in);
        tag.putInt("Out", out);
    }

    @Override
    protected void read(CompoundTag tag) {
        super.read(tag);
        int i = tag.getByte("Mode");
        mode = Mode.values()[Math.max(0, Math.min(Mode.values().length - 1, i))];
        in = tag.getInt("In");
        out = tag.getInt("Out");
    }

    // ---- items: the chamber's loot store, out only

    /** The master's loot store as hoppers and pipes see it from this port: they may take, never put in. */
    private final net.minecraftforge.items.IItemHandler lootOut = new net.minecraftforge.items.IItemHandler() {
        @Nullable
        private net.minecraftforge.items.ItemStackHandler store() {
            TankPartBlockEntity m = masterEntity();
            return m == null ? null : m.loot();
        }

        @Override
        public int getSlots() {
            return TankPartBlockEntity.LOOT_SLOTS;
        }

        @Override
        public net.minecraft.world.item.ItemStack getStackInSlot(int slot) {
            var s = store();
            return s == null || slot < 0 || slot >= s.getSlots() ? net.minecraft.world.item.ItemStack.EMPTY : s.getStackInSlot(slot);
        }

        @Override
        public net.minecraft.world.item.ItemStack insertItem(int slot, net.minecraft.world.item.ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public net.minecraft.world.item.ItemStack extractItem(int slot, int amount, boolean simulate) {
            var s = store();
            return s == null || slot < 0 || slot >= s.getSlots() ? net.minecraft.world.item.ItemStack.EMPTY : s.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, net.minecraft.world.item.ItemStack stack) {
            return false;
        }
    };
    private net.minecraftforge.common.util.LazyOptional<net.minecraftforge.items.IItemHandler> lootCap = net.minecraftforge.common.util.LazyOptional.of(() -> lootOut);

    @Override
    public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> cap, @Nullable Direction side) {
        if (cap == net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER) {
            return lootCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        lootCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        lootCap = net.minecraftforge.common.util.LazyOptional.of(() -> lootOut);
    }
}
