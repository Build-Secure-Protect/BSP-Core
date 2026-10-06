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
    private int flow;
    @Nullable
    private Direction in, out;

    public PlasmaCableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLASMA_CABLE.get(), pos, state);
    }

    public int flow() {
        return flow;
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
        if (this.flow == flow && this.in == in && this.out == out) {
            return;
        }
        this.flow = flow;
        this.in = in;
        this.out = out;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    private void write(CompoundTag tag) {
        tag.putInt("Flow", flow);
        tag.putByte("In", (byte) (in == null ? -1 : in.get3DDataValue()));
        tag.putByte("Out", (byte) (out == null ? -1 : out.get3DDataValue()));
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
