package com.mrgregles.bsp_core.plasma;

import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * One block of an interface group. The block with the lowest position in a group is its master and
 * does all the work in {@link PlasmaNetwork}; the others only remember which master they belong to.
 */
public class PlasmaInterfaceBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos master;
    private final PlasmaNetwork network = new PlasmaNetwork(this);
    private boolean lit;

    public PlasmaInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLASMA_INTERFACE.get(), pos, state);
    }

    @Nullable
    public BlockPos master() {
        return master;
    }

    void setMaster(@Nullable BlockPos master) {
        this.master = master == null ? null : master.immutable();
    }

    public PlasmaNetwork network() {
        return network;
    }

    /** Whether the group this block belongs to has plasma: its lit parts glow blue, else grey. */
    public boolean lit() {
        return lit;
    }

    void setLit(boolean lit) {
        if (this.lit == lit) {
            return;
        }
        this.lit = lit;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    public void serverTick(ServerLevel sl) {
        if (sl.getGameTime() % 20 == 0) {
            network.tick(sl);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (master != null) {
            tag.putLong("Master", master.asLong());
        }
        tag.putBoolean("Lit", lit);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        master = tag.contains("Master") ? BlockPos.of(tag.getLong("Master")) : null;
        lit = tag.getBoolean("Lit");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.putBoolean("Lit", lit);
        return tag;
    }

    @Nullable
    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
