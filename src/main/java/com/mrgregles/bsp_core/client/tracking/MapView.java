package com.mrgregles.bsp_core.client.tracking;

import com.mojang.blaze3d.platform.NativeImage;
import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.fml.ModList;

import java.util.HashMap;
import java.util.Map;

import static com.mrgregles.bsp_core.client.tracking.Hud.*;

/**
 * The map box: a top-down map of the land this client has loaded around the player, with the player's arrow and the tagged machines of
 * this world as green pins, which the player can drag and zoom whenever the Holo Grid is open. Its J key opens JourneyMap's own full map
 * (the screen its J key opens) when JourneyMap is installed. Drawn into one texture per box size, rebuilt at most twice a second; each chunk's
 * colours are kept and read again only every few seconds, so the map costs little to show.
 *
 * <p>Drawing JourneyMap's own map inside the box (owner decision MT38) is task 5 of the plan and needs a test in a client first; this map
 * is its fallback.
 */
public final class MapView {
    /** Pan and zoom of one map box. */
    public static final class State {
        public double panX, panZ;
        /** Blocks per pixel: 1, 2 or 4. */
        public int zoom = 2;
    }

    private static final Map<Long, int[]> CHUNKS = new HashMap<>();
    private static final Map<Long, Long> READ = new HashMap<>();
    /** One texture per map box (its state), made again only when the box's size changes; the old one is released. */
    private static final Map<Object, Cached> TEXTURES = new java.util.WeakHashMap<>();
    private static final Object CENTRED = new Object();

    private static Cached texture(Object owner, int w, int h) {
        Cached c = TEXTURES.get(owner);
        if (c != null && c.w == w && c.h == h) {
            return c;
        }
        if (c != null) {
            Minecraft.getInstance().getTextureManager().release(c.id);
        }
        c = new Cached(w, h);
        TEXTURES.put(owner, c);
        return c;
    }
    private static int made;

    private static final class Cached {
        final DynamicTexture tex;
        final ResourceLocation id;
        final int w, h;
        long builtMs;
        String key = "";

        Cached(int w, int h) {
            this.w = w;
            this.h = h;
            tex = new DynamicTexture(w, h, false);
            id = new ResourceLocation(BSPCore.MODID, "holo_map_" + (made++));
            Minecraft.getInstance().getTextureManager().register(id, tex);
        }
    }

    private MapView() {}

    /** Draws the map box. {@code tools}: the zoom, centre and J keys (when the box can be used). */
    public static void draw(GuiGraphics g, int x, int y, int w, int h, State s, boolean tools, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || w < 8 || h < 8) {
            return;
        }
        Cached c = texture(s, w, h);
        double cx = mc.player.getX() + s.panX, cz = mc.player.getZ() + s.panZ;
        String key = Math.round(cx / s.zoom) + "," + Math.round(cz / s.zoom) + "," + s.zoom + "," + mc.level.dimension().location();
        long now = System.currentTimeMillis();
        if ((!key.equals(c.key) && now - c.builtMs > 200) || now - c.builtMs > 1000) {
            build(mc.level, c, cx, cz, s.zoom); // at most five times a second while moving, once a second still
            c.key = key;
            c.builtMs = now;
        }
        g.blit(c.id, x, y, 0, 0, w, h, w, h);
        // tagged machines of this world
        CompoundTag view = ClientTracking.view();
        ListTag tags = view.getList("tags", Tag.TAG_COMPOUND);
        String dim = mc.level.dimension().location().toString();
        for (int i = 0; i < tags.size(); i++) {
            CompoundTag t = tags.getCompound(i);
            if (t.contains("pos") && dim.equals(t.getString("dim"))) {
                BlockPos p = NbtUtils.readBlockPos(t.getCompound("pos"));
                int px = x + w / 2 + (int) Math.round((p.getX() + 0.5 - cx) / s.zoom), py = y + h / 2 + (int) Math.round((p.getZ() + 0.5 - cz) / s.zoom);
                if (px >= x + 2 && px < x + w - 2 && py >= y + 2 && py < y + h - 2) {
                    rect(g, px - 2, py - 2, 5, 5, BLACK);
                    rect(g, px - 1, py - 1, 3, 3, GREEN);
                }
            }
        }
        // the player's arrow, turned the way they face
        int ax = x + w / 2 + (int) Math.round(-s.panX / s.zoom), ay = y + h / 2 + (int) Math.round(-s.panZ / s.zoom);
        if (ax >= x && ax < x + w && ay >= y && ay < y + h) {
            g.pose().pushPose();
            g.pose().translate(ax, ay, 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(mc.player.getYRot() + 180));
            for (int r = 0; r < 6; r++) {
                rect(g, -r / 2, -3 + r, r + 1, 1, 0xFFFFFFFF);
            }
            rect(g, 0, -2, 1, 4, 0xFFE03A2A);
            g.pose().popPose();
        }
        if (tools) {
            String[] keys = {"+", "-", "o", ModList.get().isLoaded("journeymap") ? "J" : ""};
            for (int i = 0; i < keys.length; i++) {
                if (keys[i].isEmpty()) {
                    continue;
                }
                int kx = x + w - 13, ky = y + 3 + i * 13;
                rect(g, kx, ky, 11, 11, 0xD8141414);
                outline(g, kx, ky, 11, 11, mx >= kx && mx < kx + 11 && my >= ky && my < ky + 11 ? 0xFFE8E8E8 : 0xFF777777);
                small(g, keys[i], kx + 5.5f - smallWidth(keys[i]) / 2, ky + 3, 0xFFFFFFFF);
            }
        }
    }

    /** A map centred on a block (the transmitter's screen), {@code zoom} blocks per pixel, with nothing drawn over it. */
    public static void drawCentred(GuiGraphics g, int x, int y, int w, int h, double cx, double cz, int zoom) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || w < 8 || h < 8) {
            return;
        }
        Cached c = texture(CENTRED, w, h);
        String key = Math.round(cx) + "," + Math.round(cz) + "," + zoom + "," + mc.level.dimension().location();
        long now = System.currentTimeMillis();
        if (!key.equals(c.key) || now - c.builtMs > 2000) {
            build(mc.level, c, cx, cz, zoom);
            c.key = key;
            c.builtMs = now;
        }
        g.blit(c.id, x, y, 0, 0, w, h, w, h);
    }

    /** A click on the map's own keys: true when it was one. {@code index} 0 zoom in, 1 zoom out, 2 centre, 3 JourneyMap. */
    public static boolean click(int x, int y, int w, int h, State s, double mx, double my) {
        for (int i = 0; i < 4; i++) {
            int kx = x + w - 13, ky = y + 3 + i * 13;
            if (mx >= kx && mx < kx + 11 && my >= ky && my < ky + 11) {
                switch (i) {
                    case 0 -> s.zoom = Math.max(1, s.zoom / 2);
                    case 1 -> s.zoom = Math.min(8, s.zoom * 2);
                    case 2 -> {
                        s.panX = 0;
                        s.panZ = 0;
                    }
                    default -> openJourneyMap();
                }
                return true;
            }
        }
        return false;
    }

    /** Opens JourneyMap's full map, as its J key does; nothing when JourneyMap is absent or changed. */
    public static void openJourneyMap() {
        if (!ModList.get().isLoaded("journeymap")) {
            return;
        }
        try {
            Class<?> ui = Class.forName("journeymap.client.ui.UIManager");
            Object instance = ui.getField("INSTANCE").get(null);
            ui.getMethod("getOrOpenFullscreenMap").invoke(instance);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            BSPCore.LOGGER.warn("BSP-Core could not open JourneyMap's map ({})", e.toString());
        }
    }

    public static void drag(State s, double dx, double dy) {
        s.panX -= dx * s.zoom;
        s.panZ -= dy * s.zoom;
    }

    public static void scroll(State s, double delta) {
        s.zoom = delta > 0 ? Math.max(1, s.zoom / 2) : Math.min(8, s.zoom * 2);
    }

    private static void build(ClientLevel level, Cached c, double cx, double cz, int zoom) {
        NativeImage img = c.tex.getPixels();
        if (img == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (int py = 0; py < c.h; py++) {
            for (int px = 0; px < c.w; px++) {
                int bx = (int) Math.floor(cx + (px - c.w / 2.0) * zoom), bz = (int) Math.floor(cz + (py - c.h / 2.0) * zoom);
                img.setPixelRGBA(px, py, abgr(colour(level, bx, bz, now)));
            }
        }
        c.tex.upload();
    }

    /** The colour of one column of blocks, from its chunk's cached colours (read again after five seconds). */
    private static int colour(ClientLevel level, int bx, int bz, long now) {
        int chx = bx >> 4, chz = bz >> 4;
        long key = ((long) chx << 32) ^ (chz & 0xFFFFFFFFL);
        int[] cols = CHUNKS.get(key);
        Long read = READ.get(key);
        if (cols == null || read == null || now - read > 5000) {
            if (!level.hasChunk(chx, chz)) {
                return cols == null ? 0xFF101418 : cols[(bz & 15) * 16 + (bx & 15)];
            }
            cols = readChunk(level, chx, chz);
            CHUNKS.put(key, cols);
            READ.put(key, now);
            if (CHUNKS.size() > 16384) {
                CHUNKS.clear();
                READ.clear();
            }
        }
        return cols[(bz & 15) * 16 + (bx & 15)];
    }

    private static int[] readChunk(ClientLevel level, int chx, int chz) {
        int[] out = new int[256];
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int z = 0; z < 16; z++) {
            int prevY = Integer.MIN_VALUE;
            for (int x = 0; x < 16; x++) {
                int wx = (chx << 4) + x, wz = (chz << 4) + z;
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
                m.set(wx, y, wz);
                BlockState st = level.getBlockState(m);
                MapColor mc = st.getMapColor(level, m);
                int rgb = mc == MapColor.NONE ? 0x101418 : mc.col;
                int north = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz - 1) - 1;
                float shade = y > north ? 1.12f : y < north ? 0.82f : 1f;
                if (st.getFluidState().isSource() || !st.getFluidState().isEmpty()) {
                    shade = 1f;
                }
                out[z * 16 + x] = 0xFF000000 | scale(rgb, shade);
                prevY = y;
            }
        }
        return out;
    }

    private static int scale(int rgb, float k) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * k)), g = Math.min(255, (int) (((rgb >> 8) & 255) * k)), b = Math.min(255, (int) ((rgb & 255) * k));
        return (r << 16) | (g << 8) | b;
    }

    /** ARGB to the ABGR NativeImage stores. */
    private static int abgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
    }

    /** Forgets the cached colours (on leaving a world). */
    public static void clear() {
        CHUNKS.clear();
        READ.clear();
    }
}
