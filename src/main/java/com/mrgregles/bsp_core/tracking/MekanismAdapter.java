package com.mrgregles.bsp_core.tracking;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Mekanism's own readings for a Holo Grid box (owner decision MT28/MT41), read by reflection so BSP-Core neither needs Mekanism to build
 * nor to run (checked against Mekanism and Mekanism Generators 10.4.16): every machine's active lamp, redstone mode and gas tanks; a
 * factory's progress per process; a single machine's progress; a Fission Reactor's temperature, damage, burn rate and its four tanks; an
 * Induction Matrix's stored energy and its last input and output. Anything a later Mekanism renamed is simply left out (the generic
 * reading of items, FE and fluids stands). Panel kinds: {@code mek_factory}, {@code mek_machine}, {@code mek_fission}, {@code mek_matrix},
 * {@code mek} (anything else of Mekanism's).
 */
public final class MekanismAdapter implements MachineSnapshot.Adapter {
    private static final Map<String, java.util.Optional<Method>> METHODS = new HashMap<>();
    private static final Map<String, java.util.Optional<Field>> FIELDS = new HashMap<>();
    @Nullable
    private static Capability<?> gasHandler;
    private static boolean gasLooked, warned;

    /** Registers the adapter when Mekanism is installed. */
    public static void register() {
        if (ModList.get().isLoaded("mekanism") && MachineSnapshot.ADAPTERS.stream().noneMatch(a -> a instanceof MekanismAdapter)) {
            MachineSnapshot.ADAPTERS.add(0, new MekanismAdapter());
        }
    }

    @Override
    public boolean handles(BlockEntity be) {
        return be.getClass().getName().startsWith("mekanism.");
    }

    @Override
    public void read(BlockEntity be, CompoundTag out) {
        CompoundTag x = new CompoundTag();
        String kind = "mek";
        Object active = call(be, "getActive");
        if (active instanceof Boolean b) {
            x.putBoolean("active", b);
        }
        Object control = call(be, "getControlType");
        if (control instanceof Enum<?> e) {
            x.putString("redstone", e.name());
        }
        int[] progress = field(be, "progress") instanceof int[] p ? p : null;
        Object required = call(be, "getTicksRequired");
        if (progress != null && required instanceof Integer req && req > 0) {
            ListTag procs = new ListTag();
            for (int p : progress) {
                procs.add(IntTag.valueOf(Math.min(100, p * 100 / req)));
            }
            x.put("procs", procs);
            kind = "mek_factory";
        } else if (call(be, "getScaledProgress") instanceof Double d) {
            x.putFloat("progress", (float) Math.max(0, Math.min(1, d)));
            kind = "mek_machine";
        }
        Object multiblock = call(be, "getMultiblock");
        if (multiblock != null) {
            String name = multiblock.getClass().getName();
            if (name.endsWith("FissionReactorMultiblockData")) {
                kind = "mek_fission";
                readFission(multiblock, x);
            } else if (name.endsWith("MatrixMultiblockData")) {
                kind = "mek_matrix";
                x.putDouble("energy", number(call(multiblock, "getEnergy")));
                x.putDouble("capacity", number(call(multiblock, "getStorageCap")));
                x.putDouble("in", number(call(multiblock, "getLastInput")));
                x.putDouble("out", number(call(multiblock, "getLastOutput")));
            }
        }
        readGases(be, x);
        out.putString("kind", kind);
        out.put("x", x);
    }

    private static void readFission(Object r, CompoundTag x) {
        Object heat = field(r, "heatCapacitor");
        if (heat != null) {
            x.putDouble("temp", number(call(heat, "getTemperature")));
        }
        x.putLong("damage", (long) number(call(r, "getDamagePercent")));
        x.putDouble("burn", number(field(r, "lastBurnRate")));
        x.putDouble("rateLimit", number(field(r, "rateLimit")));
        if (call(r, "isActive") instanceof Boolean b) {
            x.putBoolean("active", b);
        }
        ListTag tanks = new ListTag();
        for (String t : new String[] {"fuelTank", "gasCoolantTank", "fluidCoolantTank", "heatedCoolantTank", "wasteTank"}) {
            Object tank = field(r, t);
            if (tank == null) {
                continue;
            }
            CompoundTag c = new CompoundTag();
            c.putString("role", t.replace("Tank", ""));
            if (t.equals("fluidCoolantTank")) {
                c.putLong("amount", (long) number(call(tank, "getFluidAmount")));
                c.putLong("max", (long) number(call(tank, "getCapacity")));
            } else {
                c.putLong("amount", (long) number(call(tank, "getStored")));
                c.putLong("max", (long) number(call(tank, "getCapacity")));
            }
            if (c.getLong("max") > 0) {
                tanks.add(c);
            }
        }
        x.put("tanks", tanks);
    }

    /** The block's gas tanks through Mekanism's gas capability: id, amount, capacity. */
    private static void readGases(BlockEntity be, CompoundTag x) {
        Capability<?> cap = gasCapability();
        if (cap == null) {
            return;
        }
        be.getCapability(cap, null).ifPresent(h -> {
            Object n = call(h, "getTanks");
            if (!(n instanceof Integer tanks)) {
                return;
            }
            ListTag gases = new ListTag();
            for (int i = 0; i < Math.min(tanks, MachineSnapshot.MAX_TANKS); i++) {
                Object stack = call(h, "getChemicalInTank", i);
                Object max = call(h, "getTankCapacity", i);
                CompoundTag c = new CompoundTag();
                Object id = stack == null ? null : call(stack, "getTypeRegistryName");
                c.putString("id", id == null ? "" : id.toString());
                c.putLong("amount", (long) number(stack == null ? null : call(stack, "getAmount")));
                c.putLong("max", (long) number(max));
                gases.add(c);
            }
            x.put("gas", gases);
        });
    }

    @Nullable
    private static Capability<?> gasCapability() {
        if (!gasLooked) {
            gasLooked = true;
            try {
                gasHandler = (Capability<?>) Class.forName("mekanism.common.capabilities.Capabilities").getField("GAS_HANDLER").get(null);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                warn(e);
            }
        }
        return gasHandler;
    }

    private static double number(@Nullable Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        Object d = o == null ? null : call(o, "doubleValue"); // Mekanism's FloatingLong
        return d instanceof Double v ? v : 0;
    }

    /** Calls a public no-argument (or int-argument) method by name, or answers null when there is none. */
    @Nullable
    private static Object call(Object target, String name, Object... args) {
        String key = target.getClass().getName() + "#" + name + "/" + args.length;
        try {
            Method m = METHODS.computeIfAbsent(key, k -> java.util.Optional.ofNullable(find(target.getClass(), name, args.length))).orElse(null);
            return m == null ? null : m.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            warn(e);
            return null;
        }
    }

    @Nullable
    private static Method find(Class<?> c, String name, int args) {
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args && (args == 0 || m.getParameterTypes()[0] == int.class)) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    /** Reads a public field by name (up the class tree), or null. */
    @Nullable
    private static Object field(Object target, String name) {
        String key = target.getClass().getName() + "." + name;
        try {
            Field f = FIELDS.computeIfAbsent(key, k -> {
                try {
                    Field found = target.getClass().getField(name); // public fields, inherited ones included
                    found.setAccessible(true);
                    return java.util.Optional.of(found);
                } catch (NoSuchFieldException ignored) {
                    return java.util.Optional.empty();
                }
            }).orElse(null);
            return f == null ? null : f.get(target);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
            return null;
        }
    }

    private static void warn(Throwable e) {
        if (!warned) {
            warned = true;
            BSPCore.LOGGER.warn("BSP-Core machine tracking: a Mekanism reading failed ({}); its boxes show the plain reading", e.toString());
        }
    }
}
