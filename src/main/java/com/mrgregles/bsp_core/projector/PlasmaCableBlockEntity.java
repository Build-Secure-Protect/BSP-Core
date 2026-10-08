package com.mrgregles.bsp_core.projector;

import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * What a cable is carrying, as the interface feeding its run last reported: mB per tick, the side the
 * plasma comes in and the side it goes out. Never ticks; synced to clients when it changes so the
 * renderer can show the level inside the pipe and which way it runs.
 */
public class PlasmaCableBlockEntity extends BlockEntity {
    /** What one end of a cable does, set with the wrench. */
    public enum End {
        NORMAL, OUTPUT, INPUT, OFF, LINK;

        public String key() {
            return "gui.bsp_core.wrench.end." + name().toLowerCase(java.util.Locale.ROOT);
        }

        /** Plasma may leave the cable through this end. */
        public boolean out() {
            return this == NORMAL || this == OUTPUT || this == LINK;
        }

        /** Plasma may enter the cable through this end. */
        public boolean in() {
            return this == NORMAL || this == INPUT || this == LINK;
        }
    }

    public static final String TAG_ENDS = "Ends";
    private final byte[] ends = new byte[6];
    private int flow;
    @Nullable
    private Direction in, out;
    /** Game time of the last report (synced about every two seconds while flowing): a cable nobody reports to any more drains on the client. */
    private long at = -1000, syncedAt = -1000;

    public PlasmaCableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLASMA_CABLE.get(), pos, state);
    }

    public int flow() {
        return flow;
    }

    public End end(Direction d) {
        int i = ends[d.get3DDataValue()];
        return i < 0 || i >= End.values().length ? End.NORMAL : End.values()[i];
    }

    /** Whether any end is set to something other than Normal. */
    public boolean anyEndSet() {
        for (byte b : ends) {
            if (b != 0) {
                return true;
            }
        }
        return false;
    }

    public byte[] ends() {
        return ends.clone();
    }

    public void setEnds(byte[] values) {
        for (int i = 0; i < 6 && i < values.length; i++) {
            ends[i] = (byte) Math.max(0, Math.min(End.values().length - 1, values[i]));
        }
        sync();
    }

    public void setEnd(Direction d, End end) {
        ends[d.get3DDataValue()] = (byte) end.ordinal();
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    @Nullable
    public Direction in() {
        return in;
    }

    @Nullable
    public Direction out() {
        return out;
    }

    /** Server: called by the interface once a second; syncs only on a change. */
    public void report(int flow, @Nullable Direction in, @Nullable Direction out) {
        long now = level == null ? 0 : level.getGameTime();
        boolean changed = this.flow != flow || this.in != in || this.out != out;
        this.flow = flow;
        this.in = in;
        this.out = out;
        at = now;
        if (changed || (flow > 0 && now - syncedAt >= 40)) {
            syncedAt = now;
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
            }
        }
    }

    /** Ticks since the interface last spoke for this cable. */
    public long age() {
        return level == null ? Long.MAX_VALUE : level.getGameTime() - at;
    }

    private void write(CompoundTag tag) {
        tag.putInt("Flow", flow);
        tag.putByte("In", (byte) (in == null ? -1 : in.get3DDataValue()));
        tag.putByte("Out", (byte) (out == null ? -1 : out.get3DDataValue()));
        tag.putLong("At", at);
        tag.putByteArray(TAG_ENDS, ends.clone());
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        write(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        flow = tag.getInt("Flow");
        in = tag.getByte("In") < 0 ? null : Direction.from3DDataValue(tag.getByte("In"));
        out = tag.getByte("Out") < 0 ? null : Direction.from3DDataValue(tag.getByte("Out"));
        at = tag.getLong("At");
        byte[] e = tag.getByteArray(TAG_ENDS);
        java.util.Arrays.fill(ends, (byte) 0);
        for (int i = 0; i < 6 && i < e.length; i++) {
            ends[i] = (byte) Math.max(0, Math.min(End.values().length - 1, e[i]));
        }
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
