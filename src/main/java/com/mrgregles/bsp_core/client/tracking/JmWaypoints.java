package com.mrgregles.bsp_core.client.tracking;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * JourneyMap's waypoints and death points for BSP-Core's own map (owner decision JR3): read from JourneyMap, and every waypoint made,
 * changed or deleted on BSP-Core's map is saved into JourneyMap's own waypoint store, so the J map, its waypoint manager and BSP-Core's map
 * always hold the same waypoints. Waypoints made here belong to JourneyMap ("journeymap", its default group), exactly as one made with its B
 * key.
 *
 * <p>JourneyMap's public API (v2, inside its jar) is reached by reflection, as the rest of the Holo Grid's JourneyMap support is, so
 * BSP-Core compiles and runs without JourneyMap. A change to an existing waypoint is saved as an update through JourneyMap's waypoint store
 * (its API's add would be taken for a duplicate of itself and not written), with the raw position (JourneyMap shows a waypoint of another
 * dimension scaled by 8 between the Overworld and the Nether) and its icon colour (what JourneyMap draws). When the API cannot be reached, or
 * JourneyMap's server settings keep the client from changing waypoints, the waypoints are read from JourneyMap's waypoint file
 * ({@code waypoints/WaypointData.dat} in its world folder, read again only when it changes) and cannot be changed; the API is tried again
 * after 30 seconds.
 */
public final class JmWaypoints {
    /**
     * One waypoint as BSP-Core's map shows it: {@code x}, {@code z} where it is drawn in the dimension the player is in; {@code rawX},
     * {@code rawZ} its stored position (what the editor shows and saves). {@code handle}: JourneyMap's own waypoint object (null when read
     * from the file).
     */
    public record Wp(String guid, String name, int x, int y, int z, int rawX, int rawZ, int color, boolean enabled, boolean death, Set<String> dims,
                     Object handle) {}

    private static final String JM = "journeymap";
    private static final long RETRY_MS = 30_000L;
    private static Boolean ready;
    private static Object api, store, selfScope;
    private static Method getAll, add, remove, create, storeSave, suppress;
    private static Method wGuid, wName, wX, wY, wZ, wColor, wIconColor, wEnabled, wGroup, wDims, wSetName, wSetPos, wSetColor, wSetIconColor,
            wSetEnabled, wModId;
    private static List<Wp> cache = List.of();
    private static String cacheDim = "";
    private static long cacheMs, brokenAtMs, fileMtime = -1;
    private static boolean apiBroken;
    private static CompoundTag fileRoot;

    private JmWaypoints() {}

    private static boolean setUp() {
        if (ready != null) {
            return ready;
        }
        ready = false;
        if (!ModList.get().isLoaded(JM)) {
            return false;
        }
        try {
            Class<?> impl = Class.forName("journeymap.api.client.impl.ClientAPI");
            api = impl.getField("INSTANCE").get(null);
            Class<?> iface = Class.forName("journeymap.api.v2.client.IClientAPI");
            Class<?> wp = Class.forName("journeymap.api.v2.common.waypoint.Waypoint");
            getAll = iface.getMethod("getAllWaypoints", ResourceKey.class);
            add = iface.getMethod("addWaypoint", String.class, wp);
            remove = iface.getMethod("removeWaypoint", String.class, wp);
            create = Class.forName("journeymap.api.v2.common.waypoint.WaypointFactory")
                    .getMethod("createWaypoint", String.class, BlockPos.class, String.class, String.class, boolean.class);
            wGuid = wp.getMethod("getGuid");
            wName = wp.getMethod("getName");
            wX = wp.getMethod("getX");
            wY = wp.getMethod("getY");
            wZ = wp.getMethod("getZ");
            wColor = wp.getMethod("getColor");
            wIconColor = wp.getMethod("getIconColor");
            wEnabled = wp.getMethod("isEnabled");
            wGroup = wp.getMethod("getGroupId");
            wModId = wp.getMethod("getModId");
            wDims = wp.getMethod("getDimensions");
            wSetName = wp.getMethod("setName", String.class);
            wSetPos = wp.getMethod("setPos", int.class, int.class, int.class);
            wSetColor = wp.getMethod("setColor", int.class);
            wSetIconColor = wp.getMethod("setIconColor", Integer.class);
            wSetEnabled = wp.getMethod("setEnabled", boolean.class);
            ready = true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            BSPCore.LOGGER.warn("Holo Grid: JourneyMap's waypoint API cannot be reached ({}); its waypoints are shown read-only", e.toString());
            return false;
        }
        try {
            // JourneyMap's own store, to save a changed waypoint as an update
            Class<?> ws = Class.forName("journeymap.common.waypoint.WaypointStore");
            Class<?> scope = Class.forName("journeymap.common.waypoint.WaypointScope");
            store = ws.getMethod("getInstance").invoke(null);
            selfScope = scope.getMethod("self").invoke(null);
            storeSave = ws.getMethod("save", Class.forName("journeymap.client.waypoint.ClientWaypointImpl"), boolean.class, scope);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            storeSave = null; // a change is then saved by deleting and adding the waypoint again
        }
        try {
            suppress = Class.forName("journeymap.api.common.ClientApiGuard").getMethod("shouldSuppress");
        } catch (ReflectiveOperationException | LinkageError e) {
            suppress = null;
        }
        return true;
    }

    /** True when waypoints can be made, changed and deleted (JourneyMap's API answers and its server settings allow it). */
    public static boolean writable() {
        if (!setUp()) {
            return false;
        }
        if (apiBroken && System.currentTimeMillis() - brokenAtMs > RETRY_MS) {
            apiBroken = false; // try JourneyMap's API again
        }
        if (apiBroken) {
            return false;
        }
        try {
            return suppress == null || !Boolean.TRUE.equals(suppress.invoke(null));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return true;
        }
    }

    private static void broken(String what, Throwable e) {
        apiBroken = true;
        brokenAtMs = System.currentTimeMillis();
        BSPCore.LOGGER.warn("Holo Grid: JourneyMap's waypoint API failed {} ({}); waypoints are read-only for 30 s", what, e.toString());
    }

    /** This dimension's waypoints and death points (read again at most once a second). */
    public static List<Wp> list(ResourceKey<Level> dim) {
        long now = System.currentTimeMillis();
        String key = dim.location().toString();
        if (key.equals(cacheDim) && now - cacheMs < 1000) {
            return cache;
        }
        cacheDim = key;
        cacheMs = now;
        boolean apiUp = setUp() && !apiBroken;
        cache = apiUp ? fromApi(dim) : fromFile(dim);
        return cache;
    }

    /** Forgets the read waypoints (after a change, or leaving the world). */
    public static void refresh() {
        cacheMs = 0;
    }

    private static int raw(Object w, String method, int fallback) {
        try {
            return (int) w.getClass().getMethod(method).invoke(w);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return fallback;
        }
    }

    private static List<Wp> fromApi(ResourceKey<Level> dim) {
        List<Wp> out = new ArrayList<>();
        try {
            for (Object w : new ArrayList<>((Collection<?>) getAll.invoke(api, dim))) {
                String group = String.valueOf(wGroup.invoke(w));
                Set<String> dims = new LinkedHashSet<>();
                if (wDims.invoke(w) instanceof Collection<?> c) {
                    c.forEach(d -> dims.add(String.valueOf(d)));
                }
                int x = (int) wX.invoke(w), z = (int) wZ.invoke(w);
                Object icon = wIconColor.invoke(w);
                int color = icon instanceof Integer i && i != -1 ? i : (int) wColor.invoke(w);
                out.add(new Wp(String.valueOf(wGuid.invoke(w)), String.valueOf(wName.invoke(w)), x, (int) wY.invoke(w), z, raw(w, "getRawX", x),
                        raw(w, "getRawZ", z), color & 0xFFFFFF, (boolean) wEnabled.invoke(w), group.contains("death"), dims, w));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken("reading", e);
            return fromFile(dim);
        }
        return out;
    }

    /**
     * Read-only: JourneyMap's waypoint file ({@code waypoints} by guid: name, pos x/y/z, dimensions (the first is its own), colour, group,
     * settings.enable), read again only when the file changes.
     */
    private static List<Wp> fromFile(ResourceKey<Level> dim) {
        List<Wp> out = new ArrayList<>();
        File dir = JmTiles.worldDir();
        File file = dir == null ? null : new File(new File(dir, "waypoints"), "WaypointData.dat");
        if (file == null || !file.isFile()) {
            return out;
        }
        try {
            if (file.lastModified() != fileMtime || fileRoot == null) {
                fileMtime = file.lastModified();
                fileRoot = NbtIo.read(file);
            }
            if (fileRoot == null) {
                return out;
            }
            CompoundTag all = fileRoot.getCompound("waypoints");
            String want = dim.location().toString();
            for (String guid : all.getAllKeys()) {
                CompoundTag w = all.getCompound(guid);
                Set<String> dims = new LinkedHashSet<>();
                ListTag list = w.getList("dimensions", Tag.TAG_STRING);
                for (int i = 0; i < list.size(); i++) {
                    dims.add(list.getString(i));
                }
                if (!dims.contains(want)) {
                    continue;
                }
                CompoundTag pos = w.getCompound("pos");
                int rx = pos.getInt("x"), rz = pos.getInt("z");
                // shown as JourneyMap shows it: a waypoint of the Overworld seen from the Nether at an eighth, and the other way round
                String own = dims.isEmpty() ? want : dims.iterator().next();
                int x = rx, z = rz;
                if (own.equals("minecraft:overworld") && want.equals("minecraft:the_nether")) {
                    x = rx >> 3;
                    z = rz >> 3;
                } else if (own.equals("minecraft:the_nether") && want.equals("minecraft:overworld")) {
                    x = rx << 3;
                    z = rz << 3;
                }
                CompoundTag c = w.getCompound("color");
                int color = w.contains("color", Tag.TAG_INT) ? w.getInt("color") : (c.getInt("r") << 16) | (c.getInt("g") << 8) | c.getInt("b");
                CompoundTag settings = w.getCompound("settings");
                boolean enabled = !settings.contains("enable") || settings.getBoolean("enable");
                out.add(new Wp(guid, w.getString("name"), x, pos.getInt("y"), z, rx, rz, color & 0xFFFFFF, enabled, w.getString("groupId").contains("death"),
                        dims, null));
            }
        } catch (Exception e) {
            BSPCore.LOGGER.debug("Holo Grid: JourneyMap's waypoint file could not be read", e);
        }
        return out;
    }

    /** A new waypoint in JourneyMap's store, as its B key makes; false when it cannot be saved. */
    public static boolean create(String name, BlockPos pos, ResourceKey<Level> dim, int color, boolean enabled) {
        if (!writable()) {
            return false;
        }
        try {
            Object w = create.invoke(null, JM, pos, name, dim.location().toString(), true);
            wSetColor.invoke(w, color & 0xFFFFFF);
            wSetIconColor.invoke(w, color & 0xFFFFFF);
            wSetEnabled.invoke(w, enabled);
            add.invoke(api, JM, w);
            refresh();
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken("saving a waypoint", e);
            return false;
        }
    }

    /** Changes a waypoint in JourneyMap's store ({@code x}, {@code z}: its stored position); false when it cannot be saved. */
    public static boolean update(Wp wp, String name, int x, int y, int z, int color, boolean enabled) {
        if (!writable() || wp.handle() == null) {
            return false;
        }
        Object w = wp.handle();
        try {
            if (storeSave != null) {
                wSetName.invoke(w, name);
                wSetPos.invoke(w, x, y, z);
                wSetColor.invoke(w, color & 0xFFFFFF);
                wSetIconColor.invoke(w, color & 0xFFFFFF);
                wSetEnabled.invoke(w, enabled);
                w.getClass().getMethod("setDirty", boolean.class).invoke(w, true);
                storeSave.invoke(store, w, false, selfScope); // an update: written to JourneyMap's file (or its server) at once
            } else {
                // without JourneyMap's store: taken out and put back, so it is not taken for a duplicate of itself and left unsaved
                String mod = String.valueOf(wModId.invoke(w));
                remove.invoke(api, mod, w);
                wSetName.invoke(w, name);
                wSetPos.invoke(w, x, y, z);
                wSetColor.invoke(w, color & 0xFFFFFF);
                wSetIconColor.invoke(w, color & 0xFFFFFF);
                wSetEnabled.invoke(w, enabled);
                add.invoke(api, mod, w);
            }
            refresh();
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken("changing a waypoint", e);
            refresh();
            return false;
        }
    }

    /** Deletes a waypoint (or death point) from JourneyMap's store; false when it cannot. */
    public static boolean delete(Wp wp) {
        if (!writable() || wp.handle() == null) {
            return false;
        }
        try {
            remove.invoke(api, String.valueOf(wModId.invoke(wp.handle())), wp.handle());
            refresh();
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken("deleting a waypoint", e);
            return false;
        }
    }

    /** Leaving the world: read afresh next time, and try the API again. */
    public static void clear() {
        cache = List.of();
        cacheDim = "";
        cacheMs = 0;
        apiBroken = false;
        fileRoot = null;
        fileMtime = -1;
    }
}
