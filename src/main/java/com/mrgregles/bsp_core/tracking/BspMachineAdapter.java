package com.mrgregles.bsp_core.tracking;

import com.mrgregles.bsp_core.machine.MachineBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * BSP-Core's own processing machines (Tetrium Crucible, Combination Forge and the rest built on {@link MachineBlockEntity}): their
 * progress, whether they are working and burning, and their power switch. Panel kind {@code bsp_machine}.
 */
public final class BspMachineAdapter implements MachineSnapshot.Adapter {
    public static void register() {
        if (MachineSnapshot.ADAPTERS.stream().noneMatch(a -> a instanceof BspMachineAdapter)) {
            MachineSnapshot.ADAPTERS.add(new BspMachineAdapter());
        }
    }

    @Override
    public boolean handles(BlockEntity be) {
        return be instanceof MachineBlockEntity;
    }

    @Override
    public void read(BlockEntity be, CompoundTag out) {
        MachineBlockEntity m = (MachineBlockEntity) be;
        CompoundTag x = new CompoundTag();
        x.putFloat("progress", m.isDemo() ? 0 : m.progressFraction());
        x.putBoolean("working", m.isWorking() && !m.isDemo());
        x.putBoolean("burning", m.isBurning() && !m.isDemo());
        x.putBoolean("enabled", m.isEnabled());
        out.putString("kind", "bsp_machine");
        out.put("x", x);
    }
}
