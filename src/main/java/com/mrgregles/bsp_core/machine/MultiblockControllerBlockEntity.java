package com.mrgregles.bsp_core.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
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
 * Controller of a 3x3x3 multiblock machine with one fluid tank.
 *
 * <p>The structure is described by three layers of three strings, bottom layer first, each string a
 * row from the back of the structure to the front as seen from the controller's facing. The
 * controller sits at the middle of the front face on the middle layer. Characters are mapped to
 * blocks by {@link #blockFor(char)}; a space means "anything". The shape is re-checked once a second
 * and the machine only runs while it is complete.
 *
 * <p>Pipes connect to the controller itself: items by face mode like the single-block machines,
 * fluid on any face.
 */
public abstract class MultiblockControllerBlockEntity extends MachineBlockEntity {
    protected final FluidTank tank;
    private final LazyOptional<IFluidHandler> fluidCap;
    private boolean formed;

    protected MultiblockControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, List<Slot> layout, Fluid fluid) {
        super(type, pos, state, layout);
        this.tank = new FluidTank(8000, f -> f.getFluid().isSame(fluid)) {
            @Override
            protected void onContentsChanged() {
                setChanged();
            }

            @Override
            public int getCapacity() {
                return tankCapacity();
            }
        };
        this.fluidCap = LazyOptional.of(() -> tank);
    }

    /** Three layers (bottom, middle, top), each three rows back to front, each row three characters left to right. 'X' marks the controller. */
    protected abstract String[][] pattern();

    /** The block a pattern character requires, or null for "anything". */
    @Nullable
    protected abstract Block blockFor(char c);

    protected abstract int tankCapacity();

    protected abstract Fluid fluid();

    @Override
    public boolean isFormed() {
        return formed;
    }

    @Override
    public int fluidAmount() {
        return tank.getFluidAmount();
    }

    @Override
    public int fluidCapacity() {
        return tankCapacity();
    }

    protected boolean drain(int mb) {
        if (tank.getFluidAmount() < mb) {
            return false;
        }
        tank.drain(mb, IFluidHandler.FluidAction.EXECUTE);
        return true;
    }

    /** Adds fluid from a container item (e.g. a bucket), leaving the empty container. Returns true if anything was added. */
    protected boolean fillFrom(int slot, ItemStack full, ItemStack empty, int mb) {
        ItemStack stack = items.getStackInSlot(slot);
        if (!stack.is(full.getItem()) || tank.getFluidAmount() + mb > tankCapacity()) {
            return false;
        }
        tank.fill(new FluidStack(fluid(), mb), IFluidHandler.FluidAction.EXECUTE);
        if (empty.isEmpty()) {
            items.setStackInSlot(slot, stack.copyWithCount(stack.getCount() - 1));
        } else if (stack.getCount() == 1) {
            items.setStackInSlot(slot, empty);
        } else {
            return true; // stacked buckets are not expected; fluid was added, container left as is
        }
        return true;
    }

    protected static ItemStack bucket() {
        return new ItemStack(Items.BUCKET);
    }

    @Override
    public void serverTick(ServerLevel level) {
        if (level.getGameTime() % 20 == 0) {
            boolean ok = checkStructure(level);
            if (ok != formed) {
                formed = ok;
                setChanged();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
            if (formed) {
                refill();
            }
        }
        if (formed) {
            super.serverTick(level);
        }
    }

    /** Called once a second while formed: move fluid from container items into the tank. */
    protected abstract void refill();

    /** A block the structure needs and where it goes. */
    public record Part(BlockPos pos, Block block) {}

    /** Every position of the structure with the block it requires, worked out from the controller's facing. */
    public java.util.List<Part> parts() {
        java.util.List<Part> out = new java.util.ArrayList<>();
        Direction facing = getBlockState().hasProperty(MachineBlock.FACING) ? getBlockState().getValue(MachineBlock.FACING) : Direction.NORTH;
        Direction back = facing.getOpposite();
        Direction right = back.getClockWise(); // left to right as seen when looking at the front
        String[][] p = pattern();
        int cl = -1, cr = -1, cc = -1;
        for (int l = 0; l < p.length; l++) for (int r = 0; r < p[l].length; r++) {
            int c = p[l][r].indexOf('X');
            if (c >= 0) { cl = l; cr = r; cc = c; }
        }
        if (cl < 0) {
            return out;
        }
        for (int l = 0; l < p.length; l++) {
            for (int r = 0; r < p[l].length; r++) {
                for (int c = 0; c < p[l][r].length(); c++) {
                    Block want = blockFor(p[l][r].charAt(c));
                    if (want != null) {
                        // rows run back to front, so a smaller row index is further behind the controller
                        out.add(new Part(worldPosition.above(l - cl).relative(back, cr - r).relative(right, c - cc), want));
                    }
                }
            }
        }
        return out;
    }

    /** Parts that are not in place yet. Works on either side; the client uses it to draw the ghost layout. */
    public java.util.List<Part> missingParts() {
        java.util.List<Part> missing = new java.util.ArrayList<>();
        if (level != null) {
            for (Part part : parts()) {
                if (!level.getBlockState(part.pos()).is(part.block())) {
                    missing.add(part);
                }
            }
        }
        return missing;
    }

    private boolean checkStructure(ServerLevel level) {
        boolean ok = true;
        for (Part part : parts()) {
            if (!level.getBlockState(part.pos()).is(part.block())) {
                ok = false;
            } else if (level.getBlockEntity(part.pos()) instanceof MachinePortBlockEntity port) {
                port.link(worldPosition);
            }
        }
        return ok;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(3);
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
        tag.put("Tank", tank.writeToNBT(new CompoundTag()));
        tag.putBoolean("Formed", formed);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        tank.readFromNBT(tag.getCompound("Tank"));
        formed = tag.getBoolean("Formed");
    }
}
