package com.mrgregles.bsp_core.machine;

import com.mrgregles.bsp_core.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A pylon, pump or hatch in a multiblock. It has no storage of its own: pipes and hoppers attached
 * to it reach the controller's items, fluid and energy while the structure is complete, and find an empty inventory, tank or buffer
 * while it is not.
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
            refresh();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); // the client's clicks need to know it too
            }
        }
    }

    /**
     * What this port hands pipes: one handler per capability the port carries (items for an Item Hatch; fluid and RF for pylons and
     * pumps), made once and kept while the port belongs to a controller. Each call goes on to the controller's handler while the
     * structure stands and finds nothing while it is broken, unfinished or unloaded, so a pipe never sees the connection vanish: EnderIO
     * conduits drop a side's settings and filters when a handler disappears, and other pipes stop until asked again.
     */
    private final Map<Capability<?>, LazyOptional<?>> handed = new HashMap<>();
    /** Moves on whenever the structure forms or breaks or the port joins a controller, so each {@link Link} looks the handler up again. */
    private int epoch;

    /** The structure formed or broke: look the controller's handlers up afresh, and tell the neighbours, so a pipe that was told "nothing here" before the port joined asks again. Server side. */
    public void refresh() {
        epoch++;
        if (level != null && !level.isClientSide) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock()); // the other kind of update, which Sophisticated Storage's hopper upgrade listens for
        }
    }

    /** The controller, while it is loaded and its structure stands. Never loads a chunk. */
    @Nullable
    private MultiblockControllerBlockEntity formedController() {
        if (controller == null || level == null || !level.isLoaded(controller)) {
            return null;
        }
        return level.getBlockEntity(controller) instanceof MultiblockControllerBlockEntity c && c.isFormed() ? c : null;
    }

    /** Finds the controller's handler for one capability, kept until the structure changes or the controller's own handler goes away. */
    private final class Link<T> {
        private final Capability<T> cap;
        private LazyOptional<T> source = LazyOptional.empty();
        @Nullable
        private T handler;
        private int seen = -1;
        @Nullable
        private MultiblockControllerBlockEntity from;

        Link(Capability<T> cap) {
            this.cap = cap;
        }

        @Nullable
        T get() {
            MultiblockControllerBlockEntity c = formedController();
            if (c == null) {
                return null;
            }
            if (seen != epoch || from != c || !source.isPresent()) {
                seen = epoch;
                from = c;
                source = c.portCapability(cap, worldPosition, getBlockState().getBlock());
                handler = source.orElse(null);
            }
            return handler;
        }
    }

    @Nullable
    private Object makeHandler(Capability<?> cap) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return new PortItems(new Link<>(ForgeCapabilities.ITEM_HANDLER));
        }
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return new PortFluids(new Link<>(ForgeCapabilities.FLUID_HANDLER));
        }
        if (cap == ForgeCapabilities.ENERGY) {
            return new PortEnergy(new Link<>(ForgeCapabilities.ENERGY));
        }
        return null;
    }

    @Nonnull
    @Override
    @SuppressWarnings("unchecked")
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (controller != null && level != null) {
            LazyOptional<?> mine = handed.get(cap);
            if (mine != null) {
                return mine.cast();
            }
            // whether this port carries the capability at all is the controller's answer, formed or not (an Item Hatch never carries fluid)
            if (level.isLoaded(controller) && level.getBlockEntity(controller) instanceof MultiblockControllerBlockEntity c
                    && c.portCapability(cap, worldPosition, getBlockState().getBlock()).isPresent()) {
                Object made = makeHandler(cap);
                if (made != null) {
                    LazyOptional<T> out = LazyOptional.of(() -> (T) made);
                    handed.put(cap, out);
                    return out;
                }
                return c.portCapability(cap, worldPosition, getBlockState().getBlock()); // a capability the port does not wrap
            }
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        List<LazyOptional<?>> old = new ArrayList<>(handed.values());
        handed.clear();
        old.forEach(LazyOptional::invalidate);
    }

    /** Items through the port: the controller's handler for this port while the structure stands, an empty inventory otherwise. */
    private static final class PortItems implements IItemHandler {
        private final Link<IItemHandler> link;

        PortItems(Link<IItemHandler> link) {
            this.link = link;
        }

        @Override
        public int getSlots() {
            IItemHandler h = link.get();
            return h == null ? 0 : h.getSlots();
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            IItemHandler h = link.get();
            return h == null || slot >= h.getSlots() ? ItemStack.EMPTY : h.getStackInSlot(slot);
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            IItemHandler h = link.get();
            return h == null || slot >= h.getSlots() ? stack : h.insertItem(slot, stack, simulate);
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            IItemHandler h = link.get();
            return h == null || slot >= h.getSlots() ? ItemStack.EMPTY : h.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            IItemHandler h = link.get();
            return h == null || slot >= h.getSlots() ? 0 : h.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            IItemHandler h = link.get();
            return h != null && slot < h.getSlots() && h.isItemValid(slot, stack);
        }
    }

    /** Fluid through the port: the controller's tank while the structure stands, no tanks otherwise. */
    private static final class PortFluids implements IFluidHandler {
        private final Link<IFluidHandler> link;

        PortFluids(Link<IFluidHandler> link) {
            this.link = link;
        }

        @Override
        public int getTanks() {
            IFluidHandler h = link.get();
            return h == null ? 0 : h.getTanks();
        }

        @Nonnull
        @Override
        public FluidStack getFluidInTank(int tank) {
            IFluidHandler h = link.get();
            return h == null || tank >= h.getTanks() ? FluidStack.EMPTY : h.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            IFluidHandler h = link.get();
            return h == null || tank >= h.getTanks() ? 0 : h.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
            IFluidHandler h = link.get();
            return h != null && tank < h.getTanks() && h.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            IFluidHandler h = link.get();
            return h == null ? 0 : h.fill(resource, action);
        }

        @Nonnull
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            IFluidHandler h = link.get();
            return h == null ? FluidStack.EMPTY : h.drain(resource, action);
        }

        @Nonnull
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            IFluidHandler h = link.get();
            return h == null ? FluidStack.EMPTY : h.drain(maxDrain, action);
        }
    }

    /** RF through the port: the machine's buffer while the structure stands, nothing otherwise. */
    private static final class PortEnergy implements IEnergyStorage {
        private final Link<IEnergyStorage> link;

        PortEnergy(Link<IEnergyStorage> link) {
            this.link = link;
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            IEnergyStorage h = link.get();
            return h == null ? 0 : h.receiveEnergy(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            IEnergyStorage h = link.get();
            return h == null ? 0 : h.extractEnergy(maxExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            IEnergyStorage h = link.get();
            return h == null ? 0 : h.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            IEnergyStorage h = link.get();
            return h == null ? 0 : h.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            IEnergyStorage h = link.get();
            return h != null && h.canExtract();
        }

        @Override
        public boolean canReceive() {
            IEnergyStorage h = link.get();
            return h != null && h.canReceive();
        }
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
