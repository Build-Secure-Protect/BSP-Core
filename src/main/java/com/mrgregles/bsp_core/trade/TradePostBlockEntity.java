package com.mrgregles.bsp_core.trade;

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
 * The Trade Post's controller. It shows, and sends to the clients, how many players have its screen open and the names in
 * the first trade running at it, which the renderer puts on the screen (and which set the scales tipping, the tray sliding
 * and the beacons turning). The trade hub keeps it up to date; nothing of it is saved.
 */
public class TradePostBlockEntity extends BlockEntity {
    private int users;
    private String first = "", second = "";

    public TradePostBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRADE_POST.get(), pos, state);
    }

    public int users() {
        return users;
    }

    public boolean trading() {
        return !first.isEmpty();
    }

    public String first() {
        return first;
    }

    public String second() {
        return second;
    }

    /** Server: what the post shows now; sent to the clients when it changed. */
    public void show(int users, String first, String second) {
        if (users == this.users && first.equals(this.first) && second.equals(this.second)) {
            return;
        }
        this.users = users;
        this.first = first;
        this.second = second;
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Users", users);
        tag.putString("First", first);
        tag.putString("Second", second);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        users = tag.getInt("Users");
        first = tag.getString("First");
        second = tag.getString("Second");
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition.offset(-2, 0, -2), worldPosition.offset(3, 3, 3));
    }
}
