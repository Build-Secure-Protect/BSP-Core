package com.mrgregles.bsp_core.tracking;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * A read-only reading of one machine for a Holo Grid box: its items, energy and fluids through Forge's capabilities (any mod's machine
 * that offers them), plus whatever a machine-specific {@link Adapter} adds (a factory's per-process progress, a reactor's temperature).
 * Small on purpose: at most {@link #MAX_SLOTS} item slots and {@link #MAX_TANKS} tanks, items by id and count only. Never loads a chunk:
 * callers read only loaded machines.
 */
public final class MachineSnapshot {
    public static final int MAX_SLOTS = 54, MAX_TANKS = 8;

    /** A status for a box with no reading: the reading is from a machine that is ... */
    public static final byte OK = 0, ASLEEP = 1, GONE = 2, NO_ACCESS = 3, OFFLINE = 4, WAITING = 5;

    /** Adds a machine's own readings (progress, temperature, mode) to its snapshot. Adapters run after the generic reading. */
    public interface Adapter {
        /** Whether this adapter reads this block entity. */
        boolean handles(BlockEntity be);

        /** Adds readings under {@code out}'s "x" compound and sets {@code out}'s "kind" to the adapter's panel kind. */
        void read(BlockEntity be, CompoundTag out);
    }

    /** Machine-specific readers, asked in order; the first that handles a block entity adds its readings. */
    public static final List<Adapter> ADAPTERS = new ArrayList<>();

    private MachineSnapshot() {}

    /** Reads the loaded machine {@code be}. */
    public static CompoundTag read(ServerLevel level, BlockEntity be) {
        CompoundTag out = new CompoundTag();
        ResourceLocation block = ForgeRegistries.BLOCKS.getKey(be.getBlockState().getBlock());
        out.putString("block", block == null ? "" : block.toString());
        out.putString("kind", "generic");
        be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(h -> {
            int slots = h.getSlots();
            out.putInt("slots", slots);
            ListTag items = new ListTag();
            for (int i = 0; i < Math.min(slots, MAX_SLOTS); i++) {
                ItemStack st = h.getStackInSlot(i);
                if (!st.isEmpty()) {
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(st.getItem());
                    CompoundTag it = new CompoundTag();
                    it.putShort("s", (short) i);
                    it.putString("id", id == null ? "minecraft:air" : id.toString());
                    it.putInt("n", st.getCount());
                    items.add(it);
                }
            }
            out.put("items", items);
        });
        be.getCapability(ForgeCapabilities.ENERGY, null).ifPresent(e -> {
            out.putLong("fe", e.getEnergyStored());
            out.putLong("feMax", e.getMaxEnergyStored());
        });
        be.getCapability(ForgeCapabilities.FLUID_HANDLER, null).ifPresent(f -> {
            ListTag tanks = new ListTag();
            for (int i = 0; i < Math.min(f.getTanks(), MAX_TANKS); i++) {
                FluidStack fs = f.getFluidInTank(i);
                CompoundTag tk = new CompoundTag();
                ResourceLocation id = fs.isEmpty() ? null : ForgeRegistries.FLUIDS.getKey(fs.getFluid());
                tk.putString("id", id == null ? "" : id.toString());
                tk.putInt("mb", fs.getAmount());
                tk.putInt("max", f.getTankCapacity(i));
                tanks.add(tk);
            }
            out.put("tanks", tanks);
        });
        for (Adapter a : ADAPTERS) {
            try {
                if (a.handles(be)) {
                    a.read(be, out);
                    break;
                }
            } catch (RuntimeException | LinkageError e) {
                // a mod changed under us: the generic reading stands
                out.putString("kind", "generic");
            }
        }
        try {
            ScreenMirror.add(level, be, out); // the real screen's data (owner decision MS2)
        } catch (RuntimeException | LinkageError e) {
            // the box keeps BSP-Core's own panel
        }
        return out;
    }
}
