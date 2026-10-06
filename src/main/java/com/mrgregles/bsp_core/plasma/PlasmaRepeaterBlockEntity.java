package com.mrgregles.bsp_core.plasma;

import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** The repeater's block entity: only how much is passing through, so its rings pump on the client while it works. */
public class PlasmaRepeaterBlockEntity extends BlockEntity {
    private int flow;

    public PlasmaRepeaterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLASMA_REPEATER.get(), pos, state);
    }

    public int flow() {
        return flow;
    }

    public void report(int flow) {
        if (this.flow == flow) {
            return;
        }
        this.flow = flow;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Flow", flow);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        flow = tag.getInt("Flow");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putInt("Flow", flow);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
