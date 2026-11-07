package com.mrgregles.bsp_core.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * A single-block machine that burns furnace fuel or lava from its own tank (the Tetrium Crucible and the Combination Forge).
 *
 * <p>Lava comes first: while the tank holds one job's worth and no fuel is burning, the machine needs no fuel, and each job
 * that finishes with no fuel burning pays {@link #lavaCost()} from the tank. Otherwise the fuel slot burns as before. Pipes
 * fill the tank from any side, whatever the side's item mode, and cannot drain it.
 */
public abstract class LavaTankMachineBlockEntity extends MachineBlockEntity {
    /** Still lava only: a tank holding flowing lava would refuse still lava until it emptied. */
    protected final FluidTank lava = new FluidTank(8000, f -> f.getFluid() == Fluids.LAVA) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }

        @Override
        public int getCapacity() {
            return lavaTankSize();
        }

        /** Forge's fill reads the {@code capacity} field, so it follows the config only once set here. A tank over a lowered size keeps its lava and takes no more. */
        @Override
        public int fill(FluidStack resource, FluidAction action) {
            setCapacity(lavaTankSize());
            return getFluidAmount() >= capacity ? 0 : super.fill(resource, action);
        }
    };
    /** Taken at the start of each tick, before the tick burns down fuel or uses RF: whether this tick's work runs on lava, and at what price. */
    private boolean tickOnLava;
    private int tickLavaCost;
    /** True while the machine is working on lava; saved and synced, for the hot look. */
    private boolean lavaHot;

    /** What pipes and buckets see: fill only. */
    private final IFluidHandler fillOnly = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 1;
        }

        @Nonnull
        @Override
        public FluidStack getFluidInTank(int tank) {
            return lava.getFluid().copy();
        }

        @Override
        public int getTankCapacity(int tank) {
            return lava.getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
            return lava.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return lava.fill(resource, action);
        }

        @Nonnull
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Nonnull
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    };
    private final LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(() -> fillOnly);

    protected LavaTankMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, List<Slot> layout) {
        super(type, pos, state, layout);
    }

    /** Lava one job takes from the tank at full price, in mB (from the config). */
    protected abstract int lavaPerJob();

    /** Tank size in mB (from the config). */
    protected abstract int lavaTankSize();

    /** Whether the machine needs heat right now. The Combination Forge on RF does not. */
    protected boolean needsHeat() {
        return true;
    }

    /** Lava the next job takes: a powered RF upgrade saves the same share as it adds in speed. */
    public int lavaCost() {
        return (int) Math.round(lavaPerJob() * lavaFactor());
    }

    /** True when the tank holds the next job's lava (at least 1 mB, so a cost set to 0 still needs lava in the tank). */
    public boolean lavaReady() {
        return lava.getFluidAmount() >= Math.max(1, lavaCost());
    }

    /** Fuel is needed only while no lava is ready, or to finish a burn already going. */
    @Override
    protected final boolean usesFuel() {
        return needsHeat() && (burnTime > 0 || !lavaReady());
    }

    /** The fuel slot and the HEAT gauge stay whether or not lava is in the tank. */
    @Override
    public boolean hasFuelSlot() {
        return needsHeat();
    }

    /** Working on lava counts as burning, for the crucible's hot look. */
    @Override
    public boolean isBurning() {
        return super.isBurning() || lavaHot;
    }

    /**
     * Notes before the base tick whether this tick runs on lava: heat needed, no fuel burning and a job's lava in the tank (then the
     * base lights no fuel). Read again after the tick, the burn may have just run out or the RF just been used up, which would bill
     * a job heated by coal or RF to the tank.
     */
    @Override
    public void serverTick(net.minecraft.server.level.ServerLevel level) {
        tickOnLava = needsHeat() && burnTime <= 0 && lavaReady();
        tickLavaCost = lavaCost();
        int before = progress;
        super.serverTick(level);
        boolean hot = tickOnLava && isEnabled() && !level.hasNeighborSignal(worldPosition) && canWork();
        if (hot != lavaHot) {
            lavaHot = hot;
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        if (before > 0 && progress == 0 && !hot && burnTime <= 0) {
            sendData(); // a job stopped on lava has cooled down: without this the client keeps the last progress and animates on
        }
    }

    /** Call from {@code finishJob}: a job finished on a tick that ran on lava pays the price taken at the start of that tick. */
    protected void payLava() {
        if (tickOnLava && tickLavaCost > 0 && lava.getFluidAmount() >= tickLavaCost) {
            lava.drain(tickLavaCost, IFluidHandler.FluidAction.EXECUTE);
        }
    }

    /** Whether the machine could run on its tank or its fuel slot, for the screen's checklist. */
    protected boolean heatMissing(int fluidMb, boolean burning, net.minecraft.world.item.ItemStack fuel) {
        return needsHeat() && !burning && fluidMb < Math.max(1, lavaCost()) && net.minecraftforge.common.ForgeHooks.getBurnTime(fuel, null) <= 0;
    }

    @Override
    public int fluidAmount() {
        return lava.getFluidAmount();
    }

    @Override
    public int fluidCapacity() {
        return lava.getCapacity();
    }

    @Override
    public int fluidColor() {
        return 0xFFFF7A1A; // lava orange, as the Illyrium Crucible's gauge
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCap.invalidate();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Lava", lava.writeToNBT(new CompoundTag()));
        tag.putBoolean("LavaHot", lavaHot);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        lava.setFluid(FluidStack.EMPTY);
        lava.readFromNBT(tag.getCompound("Lava"));
        lavaHot = tag.getBoolean("LavaHot");
    }
}
