package com.mrgregles.bsp_core.coin;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import com.mrgregles.bsp_core.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A factory slice's Blank Hatch or Power Port. It stores nothing itself. The hatch passes coin
 * blanks to its slice and never gives anything out, so coins cannot be piped away. The power port
 * feeds the machine's shared energy pool; each port has its own per-tick limit, so every extra
 * cable on another port charges the machine faster.
 */
public class FactoryPortBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos controller;
    private long intakeTick;
    private int intakeThisTick;

    private final LazyOptional<IItemHandler> itemCap = LazyOptional.of(BlankInput::new);
    private final LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(PowerInput::new);

    public FactoryPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FACTORY_PORT.get(), pos, state);
    }

    public void link(BlockPos controllerPos) {
        if (!controllerPos.equals(controller)) {
            controller = controllerPos;
            setChanged();
        }
    }

    @Nullable
    private CoinFactoryBlockEntity factory() {
        return controller != null && level != null && level.getBlockEntity(controller) instanceof CoinFactoryBlockEntity f && f.isFormed() ? f : null;
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER && getBlockState().is(ModBlocks.FACTORY_BLANK_HATCH.get())) {
            return itemCap.cast();
        }
        if (cap == ForgeCapabilities.ENERGY && getBlockState().is(ModBlocks.FACTORY_POWER_PORT.get())) {
            return energyCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemCap.invalidate();
        energyCap.invalidate();
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

    /** One slot that only takes coin blanks in. */
    private class BlankInput implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            CoinFactoryBlockEntity f = factory();
            return f == null ? ItemStack.EMPTY : f.getItems().getStackInSlot(CoinFactoryBlockEntity.SLOT_INPUT);
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            CoinFactoryBlockEntity f = factory();
            return f == null ? stack : f.getItems().insertItem(CoinFactoryBlockEntity.SLOT_INPUT, stack, simulate);
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return stack.getItem() instanceof CoinBlankItem;
        }
    }

    /** Receive-only view of the machine's shared pool, limited per port per tick. */
    private class PowerInput implements IEnergyStorage {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            CoinFactoryBlockEntity f = factory();
            if (f == null || level == null) {
                return 0;
            }
            long now = level.getGameTime();
            if (now != intakeTick) {
                intakeTick = now;
                intakeThisTick = 0;
            }
            int allowed = Math.max(0, BSPConfig.FACTORY_MAX_RECEIVE.get() - intakeThisTick);
            int accepted = f.poolReceive(Math.min(allowed, maxReceive), simulate);
            if (!simulate) {
                intakeThisTick += accepted;
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            CoinFactoryBlockEntity f = factory();
            return f == null ? 0 : f.poolStored();
        }

        @Override
        public int getMaxEnergyStored() {
            CoinFactoryBlockEntity f = factory();
            return f == null ? 0 : f.poolCapacity();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }
}
