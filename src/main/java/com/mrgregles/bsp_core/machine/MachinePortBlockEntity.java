package com.mrgregles.bsp_core.machine;

import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A pylon, pump or hatch in a multiblock. It has no storage of its own: pipes and hoppers attached
 * to it reach the controller's items, fluid and energy while the structure is complete.
 */
public class MachinePortBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos controller;

    public MachinePortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE_PORT.get(), pos, state);
    }

    /** The controller this port last joined, if any. */
    @Nullable
    public BlockPos controller() {
        return controller;
    }

    public void link(BlockPos controllerPos) {
        if (!controllerPos.equals(controller)) {
            controller = controllerPos;
            setChanged();
            invalidateCaps();
            reviveCaps();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); // the client's clicks need to know it too
            }
        }
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (controller != null && level != null && level.getBlockEntity(controller) instanceof MultiblockControllerBlockEntity c && c.isFormed()) {
            return c.portCapability(cap, worldPosition, getBlockState().getBlock());
        }
        return super.getCapability(cap, side);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (controller != null) {
            tag.put("Controller", NbtUtils.writeBlockPos(controller));
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        controller = tag.contains("Controller") ? NbtUtils.readBlockPos(tag.getCompound("Controller")) : null;
    }

    /** Clients learn the controller too, so a click on a port (the Totem Forge's intake and hearths) is decided the same on both sides. */
    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
