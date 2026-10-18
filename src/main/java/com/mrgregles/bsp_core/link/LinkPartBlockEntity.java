package com.mrgregles.bsp_core.link;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A part of a formed Plasma Accelerator or Decelerator (a Frame block, a Decelerator Port): it only remembers the controller it
 * belongs to, the Intake or the Core, which does all the work. Null while the structure is not formed.
 */
public class LinkPartBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos controller;

    /** An Accelerator Frame. */
    public LinkPartBlockEntity(BlockPos pos, BlockState state) {
        this(com.mrgregles.bsp_core.registry.ModBlockEntities.ACCELERATOR_FRAME.get(), pos, state);
    }

    protected LinkPartBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Nullable
    public BlockPos controller() {
        return controller;
    }

    void setController(@Nullable BlockPos controller) {
        BlockPos next = controller == null ? null : controller.immutable();
        if (!java.util.Objects.equals(this.controller, next)) {
            this.controller = next;
            setChanged();
        }
    }

    // ---- RF goes in through any Accelerator Frame, into the intake's pool

    private final net.minecraftforge.common.util.LazyOptional<net.minecraftforge.energy.IEnergyStorage> energyCap = net.minecraftforge.common.util.LazyOptional.of(() -> new net.minecraftforge.energy.IEnergyStorage() {
        @Nullable
        private LinkEnergy pool() {
            return controller != null && level != null && level.getBlockEntity(controller) instanceof AcceleratorBlockEntity intake ? intake.energy() : null;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            LinkEnergy pool = pool();
            return pool == null ? 0 : pool.receiveEnergy(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            LinkEnergy pool = pool();
            return pool == null ? 0 : pool.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            LinkEnergy pool = pool();
            return pool == null ? 0 : pool.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return pool() != null;
        }
    });

    @Nonnull
    @Override
    public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(@Nonnull net.minecraftforge.common.capabilities.Capability<T> cap, @Nullable net.minecraft.core.Direction side) {
        if (cap == net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY && getType() == com.mrgregles.bsp_core.registry.ModBlockEntities.ACCELERATOR_FRAME.get()) {
            return energyCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (controller != null) {
            tag.putLong("Controller", controller.asLong());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        controller = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
    }
}
