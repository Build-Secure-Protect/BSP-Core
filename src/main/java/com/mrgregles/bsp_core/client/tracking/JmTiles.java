package com.mrgregles.bsp_core.client.tracking;

import com.mojang.blaze3d.platform.NativeImage;
import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * JourneyMap's own saved map pictures, for BSP-Core's map (owner decision JR2): JourneyMap keeps one 512 by 512 picture per region (512 by
 * 512 blocks, one pixel a block) for each map type it draws, in {@code journeymap/data/<sp|mp>/<world>/<dimension>/<type>/<rx>,<rz>.png},
 * where the type is {@code day}, {@code night} or a cave layer's chunk height ({@code -4} to {@code 19}). Drawing those gives JourneyMap's
 * exact colours, shading and night look; topo and biome maps are made by JourneyMap while it draws and are not saved.
 *
 * <p>Pictures are read off the game thread, at most eight at a time, kept in a cache (96 full pictures, 2048 small copies an eighth the
 * size for far zoom), and read again when JourneyMap has rewritten the file (checked every few seconds for pictures in use, spread over
 * time). JourneyMap's world folder is asked for again every five seconds: it changes when a server sends its world id, or on moving to
 * another server behind the proxy.
 */
public final class JmTiles {
    /** A region's picture: loading, loaded, or missing (no file). */
    public static final class Tile {
        DynamicTexture tex;
        ResourceLocation id;
        long mtime, checkedMs, usedMs;
        boolean loading, missing;
        final File file;
        final boolean small;

        Tile(File file, boolean small) {
            this.file = file;
            this.small = small;
        }

        /** The texture to draw, or null while loading or when there is no picture. */
        public ResourceLocation id() {
            return id;
        }
    }

    public static final int REGION = 512, SMALL = 64;
    private static final int MAX_FULL = 96, MAX_SMALL = 2048, MAX_LOADING = 8;
    /** Pictures being read now; and the cache's age, so a picture read for an emptied cache is thrown away. */
    private static int loading, generation;
    private static long worldCheckedMs;
    private static final java.util.Random JITTER = new java.util.Random();
    private static final Map<String, Tile> FULL = new LinkedHashMap<>(64, 0.75f, true), SMALLS = new LinkedHashMap<>(256, 0.75f, true);
    private static int made;
    private static File world;
    private static boolean worldLooked;
    private static Method worldDirMethod, dimPathMethod;
    private static Boolean reflect;

    private JmTiles() {}

    /** JourneyMap's folder for the world being played, or null (JourneyMap not installed, or no folder yet). */
    public static File worldDir() {
        long now = System.currentTimeMillis();
        if (worldLooked && now - worldCheckedMs < 5000) {
            return world;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !ModList.get().isLoaded("journeymap")) {
            return null;
        }
        worldCheckedMs = now;
        File before = world;
        world = look(mc);
        worldLooked = true;
        if (before != null && !before.equals(world)) {
            freeAll(); // another world folder: its pictures are another world's
        }
        return world;
    }

    private static File look(Minecraft mc) {
        File world = null;
        if (reflect()) {
            try {
                Object f = worldDirMethod.invoke(null, mc);
                if (f instanceof File file && file.isDirectory()) {
                    return file;
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                BSPCore.LOGGER.debug("Holo Grid: JourneyMap's world folder could not be asked for", e);
            }
        }
        // without JourneyMap's answer: the most recently written world folder of this kind (single player or a server)
        File base = new File(new File(mc.gameDirectory, "journeymap"), "data/" + (mc.hasSingleplayerServer() ? "sp" : "mp"));
        File[] dirs = base.listFiles(File::isDirectory);
        long best = -1;
        if (dirs != null) {
            for (File d : dirs) {
                if (d.lastModified() > best) {
                    best = d.lastModified();
                    world = d;
                }
            }
        }
        return world;
    }

    private static boolean reflect() {
        if (reflect == null) {
            try {
                Class<?> fh = Class.forName("journeymap.client.io.FileHandler");
                worldDirMethod = fh.getMethod("getJMWorldDir", Minecraft.class);
                dimPathMethod = fh.getMethod("getDimPath", File.class, ResourceKey.class);
                reflect = true;
            } catch (ReflectiveOperationException | LinkageError e) {
                reflect = false;
            }
        }
        return reflect;
    }

    /** The dimension's folder in JourneyMap's world folder, or null. */
    public static File dimDir(ResourceKey<Level> dim) {
        File w = worldDir();
        if (w == null) {
            return null;
        }
        if (reflect()) {
            try {
                if (dimPathMethod.invoke(null, w, dim) instanceof Path p) {
                    return p.toFile();
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // JourneyMap's own naming below
            }
        }
        ResourceLocation l = dim.location();
        return new File(w, "minecraft".equals(l.getNamespace()) ? l.getPath() : l.getNamespace() + "~" + l.getPath().replace('/', '~'));
    }

    /**
     * The region's picture of this map type, read in the background when needed; null until it is there. {@code small}: the eighth-size
     * copy (far zoom).
     */
    public static Tile tile(File dimDir, String type, int rx, int rz, boolean small) {
        Map<String, Tile> cache = small ? SMALLS : FULL;
        File file = new File(new File(dimDir, type), rx + "," + rz + ".png");
        String key = file.getPath();
        Tile t = cache.get(key);
        long now = System.currentTimeMillis();
        if (t == null) {
            if (loading >= MAX_LOADING) {
                return null; // asked for again next frame
            }
            t = new Tile(file, small);
            cache.put(key, t);
            load(t);
            trim(cache, small ? MAX_SMALL : MAX_FULL);
        } else if (!t.loading && now >= t.checkedMs && loading < MAX_LOADING) {
            t.checkedMs = now + 3000 + JITTER.nextInt(2000); // spread, so a screenful is not checked in one frame
            long m = file.lastModified(); // 0 when there is no file
            if (m != t.mtime) {
                load(t); // JourneyMap drew more of the region (or the file appeared, or the last read failed)
            }
        }
        t.usedMs = now;
        return t;
    }

    private static void load(Tile t) {
        t.loading = true;
        loading++;
        t.checkedMs = System.currentTimeMillis() + 3000 + JITTER.nextInt(2000);
        int gen = generation;
        CompletableFuture.supplyAsync(() -> read(t), Util.backgroundExecutor()).whenComplete((img, err) -> Minecraft.getInstance().execute(() -> {
            t.loading = false;
            loading = Math.max(0, loading - 1);
            if (gen != generation) {
                if (img != null) {
                    img.close(); // read for a cache emptied since (another world): never made into a texture
                }
                return;
            }
            if (img == null) {
                t.missing = t.tex == null;
                return;
            }
            if (t.tex == null || t.tex.getPixels() == null || t.tex.getPixels().getWidth() != img.getWidth()) {
                if (t.id != null) {
                    Minecraft.getInstance().getTextureManager().release(t.id);
                }
                t.tex = new DynamicTexture(img);
                t.id = new ResourceLocation(BSPCore.MODID, "jm_tile_" + (made++));
                Minecraft.getInstance().getTextureManager().register(t.id, t.tex);
            } else {
                t.tex.setPixels(img); // the old picture is closed by setPixels
                t.tex.upload();
            }
            t.missing = false;
        }));
    }

    /** Off the game thread: the picture, or its eighth-size copy, or null when there is no file. */
    private static NativeImage read(Tile t) {
        File f = t.file;
        if (!f.isFile()) {
            t.mtime = 0;
            return null;
        }
        long m = f.lastModified();
        try (InputStream in = new FileInputStream(f)) {
            NativeImage full = NativeImage.read(in);
            t.mtime = m; // only once read, so a failed read is tried again
            if (!t.small) {
                return full;
            }
            int k = Math.max(1, full.getWidth() / SMALL);
            NativeImage s = new NativeImage(SMALL, SMALL, true);
            for (int y = 0; y < SMALL; y++) {
                for (int x = 0; x < SMALL; x++) {
                    long r = 0, g = 0, b = 0, a = 0;
                    int n = 0;
                    for (int dy = 0; dy < k; dy++) {
                        for (int dx = 0; dx < k; dx++) {
                            int px = x * k + dx, py = y * k + dy;
                            if (px < full.getWidth() && py < full.getHeight()) {
                                int c = full.getPixelRGBA(px, py); // ABGR
                                int ca = (c >>> 24) & 255;
                                a += ca;
                                r += (long) (c & 255) * ca;
                                g += (long) ((c >> 8) & 255) * ca;
                                b += (long) ((c >> 16) & 255) * ca;
                                n++;
                            }
                        }
                    }
                    int oa = n == 0 ? 0 : (int) (a / n);
                    int or = a == 0 ? 0 : (int) (r / a), og = a == 0 ? 0 : (int) (g / a), ob = a == 0 ? 0 : (int) (b / a);
                    s.setPixelRGBA(x, y, (oa << 24) | (ob << 16) | (og << 8) | or);
                }
            }
            full.close();
            return s;
        } catch (Exception e) {
            BSPCore.LOGGER.debug("Holo Grid: JourneyMap picture {} could not be read", f, e);
            return null;
        }
    }

    private static void trim(Map<String, Tile> cache, int max) {
        Iterator<Map.Entry<String, Tile>> it = cache.entrySet().iterator();
        while (cache.size() > max && it.hasNext()) {
            Tile t = it.next().getValue();
            if (t.loading) {
                continue; // its picture is on its way; dropped next time
            }
            free(t);
            it.remove();
        }
    }

    private static void free(Tile t) {
        if (t.id != null) {
            Minecraft.getInstance().getTextureManager().release(t.id);
        }
        t.id = null;
        t.tex = null;
    }

    /** Leaving the world: every picture freed and the world folder looked for again next time. */
    public static void clear() {
        freeAll();
        world = null;
        worldLooked = false;
    }

    private static void freeAll() {
        FULL.values().forEach(JmTiles::free);
        SMALLS.values().forEach(JmTiles::free);
        FULL.clear();
        SMALLS.clear();
        generation++;
        loading = 0;
    }
}
