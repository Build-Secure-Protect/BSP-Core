package com.mrgregles.bsp_core.bank;

import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The lower half of a Bank ATM. It only tracks how many players have its screen open, so the
 * renderer can show the screen in use (text and blinking cursor) rather than the idle sign.
 */
public class BankAtmBlockEntity extends BlockEntity {
    private int users;

    public BankAtmBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BANK_ATM.get(), pos, state);
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
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0, 1.2, 0).inflate(0.2);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Users", users);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        users = tag.getInt("Users");
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt.getTag() != null) {
            handleUpdateTag(pkt.getTag());
        }
    }
}
