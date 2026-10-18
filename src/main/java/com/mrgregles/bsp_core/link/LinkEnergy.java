package com.mrgregles.bsp_core.link;

import net.minecraftforge.energy.IEnergyStorage;

/** The RF pool of an Accelerator or Decelerator: receive only, {@code plasma.linkRfCapacity} deep, taking four ticks' worth a tick at most. */
public final class LinkEnergy implements IEnergyStorage {
    private final java.util.function.IntSupplier perTick;
    private final Runnable changed;
    private int energy;

    public LinkEnergy(java.util.function.IntSupplier perTick, Runnable changed) {
        this.perTick = perTick;
        this.changed = changed;
    }

    public static int capacity() {
        return com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.LINK_RF_CAPACITY, 200_000);
    }

    /** RF one second of work costs. */
    public int perSecond() {
        return perTick.getAsInt() * 20;
    }

    /** Whether a second's worth is in the pool. */
    public boolean hasSecond() {
        return perTick.getAsInt() <= 0 || energy >= perSecond();
    }

    /** Pays for one second of work; false (and nothing taken) when the pool is short. */
    public boolean useSecond() {
        if (!hasSecond()) {
            return false;
        }
        energy -= perSecond();
        changed.run();
        return true;
    }

    /** Sets the pool outright (creative tools and tests). */
    public void fill(int rf) {
        energy = Math.max(0, Math.min(capacity(), rf));
        changed.run();
    }

    /** Restores the pool from a save (no change callback). */
    public void load(int rf) {
        energy = Math.max(0, rf);
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        int accepted = Math.max(0, Math.min(capacity() - energy, Math.min(Math.max(1000, perTick.getAsInt() * 4), maxReceive)));
        if (!simulate && accepted > 0) {
            energy += accepted;
            changed.run();
        }
        return accepted;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return energy;
    }

    @Override
    public int getMaxEnergyStored() {
        return capacity();
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
