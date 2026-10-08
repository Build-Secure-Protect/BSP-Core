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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Fed by an interface like a charger: it keeps the plasma in its tank and remembers what the interface
 * offers. The Projector standing on it drains the tank once a second and takes the rest of the feed
 * from here. Nothing is projected by the base itself.
 */
public class ProjectorBaseBlockEntity extends BlockEntity implements PlasmaReceiver {
    private static final int FEED_TICKS = 50;
    private int tank, delivered, repeaters;
    private final int[] offered = new int[Buff.values().length];
    private PlasmaAccess access = new PlasmaAccess();
    @Nullable
    private BlockPos source, anchorTotem;
    private long fedAt = -1000;
    private boolean signal;
    private int syncedTank = -1;
    /** Client only: the level the renderer is showing, gliding toward the synced tank. */
    public float shown;

    public ProjectorBaseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PROJECTOR_BASE.get(), pos, state);
    }

    public int capacity() {
        return BSPConfig.getOr(BSPConfig.PROJECTOR_TANK, 2000);
    }

    public int tank() {
        return tank;
    }

    public boolean hasSignal() {
        return signal;
    }

    public int repeaters() {
        return repeaters;
    }

    public int[] offered() {
        return offered;
    }

    public PlasmaAccess access() {
        return access;
    }

    @Nullable
    public BlockPos source() {
        return source;
    }

    @Nullable
    public BlockPos anchorTotem() {
        return anchorTotem;
    }

    /** Takes up to {@code mB} from the tank and returns what was taken. */
    public int drain(int mB) {
        int took = Math.min(mB, tank);
        tank -= took;
        if (took > 0) {
            setChanged();
        }
        return took;
    }

    @Override
    public int wanted() {
        return Math.max(0, capacity() - tank);
    }

    @Override
    public void feed(BlockPos master, int[] offeredByOrdinal, int repeaters, int deliveredPerTick, int movedMB, PlasmaAccess access, @Nullable BlockPos anchorTotem) {
        source = master.immutable();
        System.arraycopy(offeredByOrdinal, 0, offered, 0, offered.length);
        this.repeaters = repeaters;
        this.delivered = deliveredPerTick;
        this.access = access;
        this.anchorTotem = anchorTotem;
        tank = Math.min(capacity(), tank + movedMB);
        fedAt = level == null ? 0 : level.getGameTime();
        setChanged();
    }

    public void serverTick(ServerLevel sl) {
        if (sl.getGameTime() % 20 != 0) {
            return;
        }
        boolean fed = source != null && sl.getGameTime() - fedAt <= FEED_TICKS;
        if (sl.getBlockEntity(worldPosition.above()) instanceof com.mrgregles.bsp_core.projector.TotemProjectorBlockEntity projector) {
            // the projector burns its second's worth from the tank; what the interface told us travels with it, so a cut run keeps it going until the tank is dry
            int use = BSPConfig.getOr(BSPConfig.PROJECTOR_USE, 20) * 20, took = drain(use);
            if (source != null && (fed || took > 0)) {
                projector.feed(source, offered, repeaters, fed ? delivered : 0, access, anchorTotem);
                projector.power(took >= use);
            }
        } else if (!fed && tank > 0) {
            tank = Math.max(0, tank - capacity() / 10); // nothing drawing on it and nothing feeding it: it empties on its own
        }
        if (fed != signal || tank != syncedTank) {
            signal = fed;
            syncedTank = tank;
            sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void write(CompoundTag tag) {
        tag.putInt("Tank", tank);
        tag.putBoolean("Signal", signal);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        write(tag);
        tag.putLong("FedAt", fedAt);
        tag.putInt("Delivered", delivered);
        tag.putInt("Repeaters", repeaters);
        tag.putIntArray("Offered", offered);
        if (source != null) {
            tag.putLong("Source", source.asLong());
        }
        if (anchorTotem != null) {
            tag.putLong("Totem", anchorTotem.asLong());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        tank = tag.getInt("Tank");
        signal = tag.getBoolean("Signal");
        fedAt = tag.contains("FedAt") ? tag.getLong("FedAt") : -1000;
        delivered = tag.getInt("Delivered");
        repeaters = tag.getInt("Repeaters");
        int[] o = tag.getIntArray("Offered");
        if (o.length == offered.length) {
            System.arraycopy(o, 0, offered, 0, o.length);
        }
        source = tag.contains("Source") ? BlockPos.of(tag.getLong("Source")) : null;
        anchorTotem = tag.contains("Totem") ? BlockPos.of(tag.getLong("Totem")) : null;
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
