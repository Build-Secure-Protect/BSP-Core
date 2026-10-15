package com.mrgregles.bsp_core.land;

import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The Land Office's block entity: only who is at it, so the deed cards flutter and the lamp pulses while someone has the screen open. */
public class LandOfficeBlockEntity extends BlockEntity {
    private int users;

    public LandOfficeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LAND_OFFICE.get(), pos, state);
    }

    public boolean inUse() {
        return users > 0;
    }

    public void userOpened() {
        users++;
        sync();
    }

    public void userClosed() {
        users = Math.max(0, users - 1);
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Users", users);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        users = tag.getInt("Users");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
