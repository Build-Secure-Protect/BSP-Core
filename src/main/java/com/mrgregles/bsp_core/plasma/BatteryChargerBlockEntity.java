package com.mrgregles.bsp_core.plasma;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * Fed by an interface like a projector. Each second it pours its tank into the battery or cell in its
 * slot, and refreshes the levels of the powers stamped into that item to what the interface offers.
 * Stamping is chosen on its screen; a stamped power keeps its level until the item is emptied.
 */
public class BatteryChargerBlockEntity extends BlockEntity implements PlasmaReceiver {
    private static final int FEED_TICKS = 50;
    private final ItemStackHandler items = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return PlasmaItems.isChargeable(stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private int tank, delivered;
    private final int[] offered = new int[Buff.values().length];
    private PlasmaAccess access = new PlasmaAccess();
    @Nullable
    private BlockPos source;
    private long fedAt = -1000;
    private boolean signal, filling;

    public BatteryChargerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BATTERY_CHARGER.get(), pos, state);
    }

    public ItemStackHandler getItems() {
        return items;
    }

    public int capacity() {
        return BSPConfig.getOr(BSPConfig.CHARGER_TANK, 4000);
    }

    public int tank() {
        return tank;
    }

    public int delivered() {
        return signal ? delivered : 0;
    }

    public boolean hasSignal() {
        return signal;
    }

    public boolean isFilling() {
        return filling;
    }

    public int offered(Buff b) {
        return signal ? offered[b.ordinal()] : 0;
    }

    public boolean mayEdit(net.minecraft.world.entity.player.Player player) {
        return access.editors.isEmpty() || access.editors.contains(player.getUUID()) || com.mrgregles.bsp_core.admin.Admins.isAdmin(player);
    }

    /** Whether the player may open the charger: an owner or a Machines friend of a totem on its interface, or an admin; anyone when nothing feeds it. */
    public boolean mayUse(net.minecraft.world.entity.player.Player player) {
        return !signal || access.users.isEmpty() || access.users.contains(player.getUUID()) || com.mrgregles.bsp_core.admin.Admins.isAdmin(player);
    }

    @Override
    public int wanted() {
        return Math.max(0, capacity() - tank);
    }

    @Override
    public void feed(BlockPos master, int[] offeredByOrdinal, int repeaters, int deliveredPerTick, PlasmaAccess access, @Nullable BlockPos anchorTotem) {
        source = master.immutable();
        System.arraycopy(offeredByOrdinal, 0, offered, 0, offered.length);
        delivered = deliveredPerTick;
        this.access = access;
        tank = Math.min(capacity(), tank + deliveredPerTick * 20);
        fedAt = level == null ? 0 : level.getGameTime();
        setChanged();
    }

    /** Stamps or clears power {@code buff} on the item in the slot, if the item may hold it and has room. */
    public void stamp(Buff buff) {
        ItemStack stack = items.getStackInSlot(0);
        if (stack.isEmpty() || !Arrays.asList(PlasmaItems.allowed(stack)).contains(buff)) {
            return;
        }
        int[] p = PlasmaItems.powers(stack);
        if (p[buff.ordinal()] > 0) {
            PlasmaItems.setPower(stack, buff, 0);
        } else if (PlasmaItems.powerCount(stack) < PlasmaItems.maxPowers(stack) && offered[buff.ordinal()] > 0) {
            PlasmaItems.setPower(stack, buff, offered[buff.ordinal()]);
        }
        items.setStackInSlot(0, stack);
    }

    public void serverTick(ServerLevel sl) {
        if (sl.getGameTime() % 20 != 0) {
            return;
        }
        boolean fed = source != null && sl.getGameTime() - fedAt <= FEED_TICKS, wasFilling = filling;
        filling = false;
        ItemStack stack = items.getStackInSlot(0);
        if (!stack.isEmpty()) {
            int stored = PlasmaItems.stored(stack), move = Math.min(tank, PlasmaItems.capacity(stack) - stored);
            if (move > 0) {
                PlasmaItems.setStored(stack, stored + move);
                tank -= move;
                filling = true;
            }
            if (fed) { // stamped powers follow the totem's current level while the item sits in a fed charger
                int[] p = PlasmaItems.powers(stack);
                for (Buff b : PlasmaItems.allowed(stack)) {
                    if (p[b.ordinal()] > 0 && offered[b.ordinal()] > 0 && offered[b.ordinal()] != p[b.ordinal()]) {
                        PlasmaItems.setPower(stack, b, offered[b.ordinal()]);
                    }
                }
            }
            items.setStackInSlot(0, stack);
        }
        if (fed != signal || wasFilling != filling) {
            signal = fed;
            sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        setChanged();
    }

    private void write(CompoundTag tag) {
        tag.put("Items", items.serializeNBT());
        tag.putInt("Tank", tank);
        tag.putBoolean("Signal", signal);
        tag.putBoolean("Filling", filling);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        write(tag);
        tag.putLong("FedAt", fedAt);
        tag.putInt("Delivered", delivered);
        tag.putIntArray("Offered", offered);
        if (source != null) {
            tag.putLong("Source", source.asLong());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Items")) {
            items.deserializeNBT(tag.getCompound("Items"));
        }
        tank = tag.getInt("Tank");
        signal = tag.getBoolean("Signal");
        filling = tag.getBoolean("Filling");
        fedAt = tag.contains("FedAt") ? tag.getLong("FedAt") : -1000;
        delivered = tag.getInt("Delivered");
        int[] o = tag.getIntArray("Offered");
        if (o.length == offered.length) {
            System.arraycopy(o, 0, offered, 0, o.length);
        }
        source = tag.contains("Source") ? BlockPos.of(tag.getLong("Source")) : null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        write(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
