package com.mrgregles.bsp_core.decoy;

import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** The Decoy Power Base: a small RF buffer that takes power on its sides and bottom and is drained by the Decoy Totem standing on it. */
public class DecoyPowerBaseBlockEntity extends BlockEntity {
    public static final int CAPACITY = 20_000;
    private final Buffer energy = new Buffer();

    private final class Buffer extends EnergyStorage {
        Buffer() {
            super(CAPACITY, 2_000, 0);
        }

        void use(int rf) {
            energy -= rf;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int got = super.receiveEnergy(maxReceive, simulate);
            if (got > 0 && !simulate) {
                setChanged();
            }
            return got;
        }
    }

    private final LazyOptional<IEnergyStorage> cap = LazyOptional.of(() -> energy);

    public DecoyPowerBaseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DECOY_POWER_BASE.get(), pos, state);
    }

    public int stored() {
        return energy.getEnergyStored();
    }

    /** Uses {@code rf} if the buffer holds that much. */
    public boolean take(int rf) {
        if (energy.getEnergyStored() < rf) {
            return false;
        }
        energy.use(rf);
        setChanged();
        return true;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction side) {
        return capability == ForgeCapabilities.ENERGY && side != Direction.UP ? cap.cast() : super.getCapability(capability, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        cap.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Energy", energy.serializeNBT());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Energy")) {
            energy.deserializeNBT(tag.get("Energy"));
        }
    }
}
