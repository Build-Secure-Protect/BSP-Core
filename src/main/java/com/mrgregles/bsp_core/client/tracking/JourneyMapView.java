package com.mrgregles.bsp_core.client.tracking;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mrgregles.bsp_core.BSPClientConfig;
import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * JourneyMap's own full map (the screen its J key opens) inside a Holo Grid map box: the very same screen, drawn by JourneyMap, with its
 * map types, day and night, shading, mobs and players, waypoints, chunk grid, zoom, buttons and popups (owner decisions MT9 and MT38).
 * BSP-Core draws none of it.
 *
 * <p>How: JourneyMap 6.0.7 has no call to draw its map into a rectangle; its full map sizes itself from the game window and reads the mouse
 * from the game's mouse handler. So each map box gets its own instance of JourneyMap's {@code Fullscreen} screen, and for the moment it is
 * drawn or given input, the game's window is made to report the box's size, the mouse handler the mouse's place in the box, and
 * {@code Minecraft.screen} that screen (a {@link Swap}); it is drawn into an off-screen picture the size of the box, which is then drawn
 * into the box. All of it is put back straight after, before anything else runs. Only Minecraft's own {@code Screen} methods are called on
 * it, plus one private flag (its buttons shown or hidden) and three optional methods, so no JourneyMap classes are compiled against.
 *
 * <p>Screens JourneyMap changes to during a call:
 * <ul>
 * <li>its popups (the right-click menu, the waypoint editor; its {@code LayeredScreen}s) draw the map behind themselves and close back
 * onto it: they stay inside the box and take its input until closed;</li>
 * <li>its own screens (options, waypoint manager, its splash) open in the game window; when JourneyMap later
 * returns to the embedded map, the player is taken back to where they were (the Holo Grid, or the game) instead. A container screen open in
 * the game window that such a screen replaces (from the popout) is closed as Esc would;</li>
 * <li>closing its map (its close button, its J key) closes the Holo Grid as it closes the J screen; from the popout or the pin, and any
 * change while drawing, it is undone.</li>
 * </ul>
 *
 * <p>JourneyMap's map state is shared by all its full maps (its zoom, map type, follow and pan), so every map box and the J screen show
 * the same view, as in JourneyMap itself.
 *
 * <p>If JourneyMap is missing, or a JourneyMap update breaks this (a missing class or field), the box shows {@link MapView}'s map, which
 * draws JourneyMap's own saved map pictures and waypoints. When JourneyMap fails while in use (an error, or JourneyMap closing its map while
 * drawing it, which it does on its own errors), {@link MapView} stands in and JourneyMap is tried again after five seconds; three failures
 * in five minutes give up until the next login (owner decision JR1).
 */
public final class JourneyMapView {
    /** Where a map box is drawn: the Holo Grid in the game window, the popout window, or the pinned box on the game screen. */
    public enum Host { SCREEN, POPOUT, PIN }

    private static final String FULLSCREEN = "journeymap.client.ui.fullscreen.Fullscreen";
    private static Boolean present;
    /** Given up until the next login (owner decision JR1: three failures within five minutes). */
    private static boolean failed;
    /** After a failure the boxes show BSP-Core's map until this time, then JourneyMap is tried again with fresh screens. */
    private static long retryAtMs;
    private static final java.util.ArrayDeque<Long> FAILURES = new java.util.ArrayDeque<>();
    private static final long RETRY_MS = 5_000L, WINDOW_MS = 5 * 60_000L;
    private static final int MAX_FAILURES = 3;
    /** Screens dropped after a failure, still recognised when JourneyMap returns to one of them. */
    private static final java.util.Set<Screen> RETIRED = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static Class<?> fullscreenClass;
    private static Field buttonsVisible;
    private static Field fbW, fbH, scrW, scrH, guiW, guiH, guiScale, mouseX, mouseY, renderables;
    /** Optional: JourneyMap's return stack, its chat and search state, its state refresh (null when a JourneyMap version lacks them). */
    private static Field returnStack;
    private static Method chatOpen, searchFocused, refreshState;
    /** JourneyMap's popup base class (its right-click menu, waypoint editor): those draw the map behind themselves. */
    private static Class<?> layeredClass;

    /**
     * A JourneyMap screen, one per map box shown, kept in a pool per host and handed to another box once its box is no longer drawn:
     * making one clears JourneyMap's shared map tiles, so they are not made afresh when a box is (a layout from the server), and two map
     * boxes never share one (laying it out again at another size every frame clears the tiles too, and its mouse would be the other box's).
     * {@code layer}: a JourneyMap popup open over it in the box. {@code w} by {@code h}: the size it was last laid out at, -1 to lay out.
     */
    private static final class HostScreen {
        Screen screen, layer;
        GridModel.Box owner;
        int w = -1, h = -1;
        long drawnMs;
    }

    @SuppressWarnings("unchecked")
    private static final List<HostScreen>[] POOLS = new List[] {new ArrayList<>(), new ArrayList<>(), new ArrayList<>()};

    /** A box's off-screen picture and sizes, per box and host. */
    private static final class Embed {
        HostScreen hs;
        TextureTarget target;
        int pxW, pxH, jmScale, guiW, guiH, scrW, scrH;
        /** Where the box was last drawn, in its host's GUI pixels, and that host's GUI scale and pixels per window point. */
        double x, y, hostScale = 1, ratio = 1;
        long drawnMs;
        boolean mouseDown;
        /** The last mouse place sent to it, in host GUI pixels. */
        double lastMx, lastMy;
    }

    @SuppressWarnings("unchecked")
    private static final Map<GridModel.Box, Embed>[] EMBEDS = new Map[] {new IdentityHashMap<>(), new IdentityHashMap<>(), new IdentityHashMap<>()};
    /** Where to go when JourneyMap returns to one of the embedded maps (absent or null: the game). */
    private static final Map<Screen, Screen> RETURN_TO = new IdentityHashMap<>();
    /** The box whose size JourneyMap's shared map renderer last worked at (input elsewhere first refreshes it to its own size). */
    private static Embed laidOutFor;
    private static int swapDepth;
    private static int ticks;

    private JourneyMapView() {}

    /** True when the map boxes show JourneyMap's own map. */
    public static boolean active() {
        if (present == null) {
            present = ModList.get().isLoaded("journeymap") && setUp();
        }
        return present && !failed && System.currentTimeMillis() >= retryAtMs;
    }

    private static boolean setUp() {
        try {
            fullscreenClass = Class.forName(FULLSCREEN);
            if (!Screen.class.isAssignableFrom(fullscreenClass)) {
                throw new ClassCastException(FULLSCREEN + " is not a screen");
            }
            fullscreenClass.getConstructor();
            buttonsVisible = fullscreenClass.getDeclaredField("buttonsVisible");
            buttonsVisible.setAccessible(true);
            fbW = ObfuscationReflectionHelper.findField(Window.class, "f_85361_");
            fbH = ObfuscationReflectionHelper.findField(Window.class, "f_85362_");
            scrW = ObfuscationReflectionHelper.findField(Window.class, "f_85359_");
            scrH = ObfuscationReflectionHelper.findField(Window.class, "f_85360_");
            guiW = ObfuscationReflectionHelper.findField(Window.class, "f_85363_");
            guiH = ObfuscationReflectionHelper.findField(Window.class, "f_85364_");
            guiScale = ObfuscationReflectionHelper.findField(Window.class, "f_85365_");
            mouseX = ObfuscationReflectionHelper.findField(MouseHandler.class, "f_91507_");
            mouseY = ObfuscationReflectionHelper.findField(MouseHandler.class, "f_91508_");
            renderables = ObfuscationReflectionHelper.findField(Screen.class, "f_169369_");
            try {
                returnStack = Class.forName("journeymap.client.ui.component.screens.JmUI").getField("returnDisplayStack");
            } catch (ReflectiveOperationException | LinkageError ignored) {
                returnStack = null;
            }
            try {
                chatOpen = fullscreenClass.getMethod("isChatOpen");
                searchFocused = fullscreenClass.getMethod("isSearchFocused");
            } catch (ReflectiveOperationException | LinkageError ignored) {
                chatOpen = searchFocused = null;
            }
            try {
                layeredClass = Class.forName("journeymap.api.v2.client.ui.component.LayeredScreen");
            } catch (ReflectiveOperationException | LinkageError ignored) {
                layeredClass = null; // its popups then open as its other screens do, in the game window
            }
            try {
                refreshState = fullscreenClass.getDeclaredMethod("refreshState");
                refreshState.setAccessible(true);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                refreshState = null;
            }
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            BSPCore.LOGGER.warn("Holo Grid: JourneyMap's map cannot be shown in a box ({}); the box shows BSP-Core's map instead", e.toString());
            return false;
        }
    }

    /**
     * JourneyMap failed in a box (owner decision JR1): the boxes show BSP-Core's map, and after five seconds JourneyMap is tried again with
     * fresh screens; the third failure within five minutes gives up until the next login.
     */
    private static void fail(Throwable e) {
        long now = System.currentTimeMillis();
        if (failed || now < retryAtMs) {
            return; // already showing BSP-Core's map for this failure
        }
        FAILURES.addLast(now);
        while (!FAILURES.isEmpty() && now - FAILURES.peekFirst() > WINDOW_MS) {
            FAILURES.removeFirst();
        }
        for (Map<GridModel.Box, Embed> map : EMBEDS) {
            map.values().forEach(JourneyMapView::dispose); // the pictures' GPU memory
            map.clear();
        }
        for (List<HostScreen> pool : POOLS) {
            for (HostScreen hs : pool) {
                RETIRED.add(hs.screen); // still sent back to the Holo Grid or the game if JourneyMap returns to it
            }
            pool.clear(); // the retry starts from fresh copies of JourneyMap's map screen
        }
        laidOutFor = null;
        if (FAILURES.size() >= MAX_FAILURES) {
            failed = true;
            BSPCore.LOGGER.error("Holo Grid: JourneyMap's map failed in a box {} times in five minutes; the boxes show BSP-Core's map until the next login",
                    FAILURES.size(), e);
        } else {
            retryAtMs = now + RETRY_MS;
            if (FAILURES.size() == 1) {
                BSPCore.LOGGER.warn("Holo Grid: JourneyMap's map failed in a box; BSP-Core's map is shown and JourneyMap is tried again in 5 s", e);
            } else {
                BSPCore.LOGGER.warn("Holo Grid: JourneyMap's map failed in a box again ({}); trying again in 5 s", e.toString());
            }
        }
    }

    /** True when JourneyMap has failed too often and BSP-Core's map is shown until the next login. */
    public static boolean gaveUp() {
        return failed;
    }

    /** True while the boxes show BSP-Core's map because JourneyMap failed (for a moment, or until the next login). */
    public static boolean recovering() {
        return present != null && present && (failed || System.currentTimeMillis() < retryAtMs);
    }

    /** The host now drawing: the popout while it draws its picture, the pin when no screen is open, else the Holo Grid. */
    static Host host() {
        if (Popout.drawing) {
            return Host.POPOUT;
        }
        return Minecraft.getInstance().screen == null ? Host.PIN : Host.SCREEN;
    }

    // ------------------------------------------------------------------ drawing

    /**
     * Draws JourneyMap's map into the box at ({@code x}, {@code y}) of {@code w} by {@code h} host GUI pixels. {@code mx}, {@code my}: the
     * mouse in host GUI pixels (off the box for no hover). Returns false when it could not be drawn (the caller falls back).
     */
    public static boolean draw(GuiGraphics g, GridModel.Box box, int x, int y, int w, int h, int mx, int my) {
        if (!active()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        Host host = host();
        if (w < 8 || h < 8 || mc.player == null || mc.level == null) {
            forget(host, box); // BSP-Core's map is drawn instead: input must not go to a map no one sees
            return false;
        }
        double hostScale, ratio;
        RenderTarget hostTarget;
        if (host == Host.POPOUT) {
            hostScale = Popout.scale();
            ratio = Popout.pixelRatio();
            hostTarget = Popout.target();
        } else {
            Window win = mc.getWindow();
            hostScale = win.getGuiScale();
            ratio = win.getScreenWidth() > 0 ? win.getWidth() / (double) win.getScreenWidth() : 1;
            hostTarget = mc.getMainRenderTarget();
        }
        if (hostTarget == null) {
            return false;
        }
        g.flush(); // the host's drawing so far, before any target is made, resized or bound
        Embed e = EMBEDS[host.ordinal()].computeIfAbsent(box, k -> new Embed());
        try {
            HostScreen hs = screenFor(host, box);
            e.hs = hs;
            int pxW = Math.max(1, (int) Math.round(w * hostScale)), pxH = Math.max(1, (int) Math.round(h * hostScale));
            e.x = x;
            e.y = y;
            e.hostScale = hostScale;
            e.ratio = ratio;
            if (pxW != e.pxW || pxH != e.pxH || e.target == null) {
                size(e, pxW, pxH, hostScale);
            }
            boolean layout = hs.w != e.guiW || hs.h != e.guiH; // a new size, a screen handed over from another box, or never laid out
            hs.w = e.guiW;
            hs.h = e.guiH;
            render(mc, e, host, layout, mx, my, hostTarget);
            blit(g, e, x, y, w, h);
            e.drawnMs = hs.drawnMs = System.currentTimeMillis();
        } catch (Throwable t) {
            fail(t);
            hostTarget.bindWrite(true);
            return false;
        }
        sweep();
        return true;
    }

    /** The box's JourneyMap screen: the one it had, else one whose box is no longer drawn, else a new one. */
    private static HostScreen screenFor(Host host, GridModel.Box box) throws ReflectiveOperationException {
        List<HostScreen> pool = POOLS[host.ordinal()];
        long now = System.currentTimeMillis();
        for (HostScreen hs : pool) {
            if (hs.owner == box) {
                if (now - hs.drawnMs > 1000 || !BSPClientConfig.holoMapControls()) {
                    hs.screen.setFocused(null); // a search box left focused when the box was last shown (or now hidden) takes no keys
                }
                return hs;
            }
        }
        for (HostScreen hs : pool) {
            if (hs.owner == null || now - hs.drawnMs > 500) {
                hs.owner = box;
                hs.layer = null; // a popup belongs to the box it was opened in
                hs.screen.setFocused(null);
                return hs;
            }
        }
        HostScreen hs = new HostScreen();
        hs.screen = (Screen) fullscreenClass.getConstructor().newInstance();
        hs.owner = box;
        hs.drawnMs = now;
        pool.add(hs);
        popReturn(null); // JourneyMap pushes "return to nothing" for each new full map; left on top it would send a later return to the game
        return hs;
    }

    /** Takes {@code s} off the top of JourneyMap's return stack when it is there (only that, never anything else). */
    private static void popReturn(Screen s) {
        try {
            if (returnStack != null && returnStack.get(null) instanceof Stack<?> stack && !stack.isEmpty() && stack.peek() == s) {
                stack.pop();
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // only tidying JourneyMap's stack
        }
    }

    private static boolean pooled(Screen s) {
        if (RETIRED.contains(s)) {
            return true;
        }
        for (List<HostScreen> pool : POOLS) {
            for (HostScreen hs : pool) {
                if (hs.screen == s) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void forget(Host host, GridModel.Box box) {
        Embed e = EMBEDS[host.ordinal()].remove(box);
        if (e != null) {
            dispose(e);
        }
    }

    /** Sizes the box's picture and the GUI JourneyMap lays out in: the host's GUI scale, smaller when the box is narrow, so its buttons fit. */
    private static void size(Embed e, int pxW, int pxH, double hostScale) {
        e.pxW = pxW;
        e.pxH = pxH;
        int js = Math.max(1, (int) Math.round(hostScale));
        while (js > 1 && pxW / js < 560) {
            js--;
        }
        e.jmScale = js;
        e.guiW = (int) Math.ceil(pxW / (double) js);
        e.guiH = (int) Math.ceil(pxH / (double) js);
        e.scrW = Math.max(1, (int) Math.round(pxW / e.ratio));
        e.scrH = Math.max(1, (int) Math.round(pxH / e.ratio));
        if (e.target == null) {
            e.target = new TextureTarget(pxW, pxH, true, Minecraft.ON_OSX);
        } else {
            e.target.resize(pxW, pxH, Minecraft.ON_OSX);
        }
    }

    private static void render(Minecraft mc, Embed e, Host host, boolean layout, int mx, int my, RenderTarget hostTarget) throws Exception {
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        e.target.setClearColor(0f, 0f, 0f, 1f);
        e.target.clear(Minecraft.ON_OSX);
        e.target.bindWrite(true);
        // JourneyMap reads the mouse while drawing (hover, and the pan while dragging): off the box it is far away, except during a drag
        boolean hostHasMouse = e.mouseDown || mx >= e.x && my >= e.y && mx < e.x + e.pxW / e.hostScale && my < e.y + e.pxH / e.hostScale;
        double jx = hostHasMouse ? local(e, mx, e.x) : -10000, jy = hostHasMouse ? local(e, my, e.y) : -10000;
        try (Swap s = new Swap(mc, e, host, jx, jy)) {
          try {
            if (layout) {
                e.hs.screen.init(mc, e.guiW, e.guiH);
                if (s.changed()) {
                    // JourneyMap's splash (once per JourneyMap version): over the Holo Grid it shows as on the J screen; it must not open
                    // over whatever the game window shows from the popout, nor over play from the pin
                    if (host != Host.SCREEN) {
                        s.undo();
                    }
                    return;
                }
                if (e.hs.layer != null) {
                    e.hs.layer.init(mc, e.guiW, e.guiH);
                }
            }
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, (float) (e.pxW / (double) e.jmScale), (float) (e.pxH / (double) e.jmScale), 0, 1000,
                    ForgeHooksClient.getGuiFarPlane()), VertexSorting.ORTHOGRAPHIC_Z);
            mv.setIdentity();
            mv.translate(0, 0, 1000 - ForgeHooksClient.getGuiFarPlane());
            RenderSystem.applyModelViewMatrix();
            Lighting.setupFor3DItems();
            GuiGraphics jg = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            s.active().render(jg, (int) jx, (int) jy, mc.getFrameTime()); // a popup draws the map behind itself
            jg.flush();
            laidOutFor = e;
            if (s.changed()) {
                // JourneyMap's drawing catches its own errors and closes its map (and with it whatever was open): put that back and
                // fall back to BSP-Core's map
                s.undo();
                throw new IllegalStateException("JourneyMap closed its map while drawing it (its error is in the log above)");
            }
          } catch (Throwable t) {
            s.undo(); // whatever JourneyMap did to the open screen before failing is put back
            discardDrawing(mc);
            throw t;
          }
        } finally {
            RenderSystem.disableScissor();
            mv.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            hostTarget.bindWrite(true);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
        }
    }

    /** JourneyMap failed mid-drawing: what it had queued is dropped into the box's own picture, not drawn into the host next. */
    private static void discardDrawing(Minecraft mc) {
        try {
            BufferBuilder bb = Tesselator.getInstance().getBuilder();
            if (bb.building()) {
                bb.end().release();
            }
            mc.renderBuffers().bufferSource().endBatch();
        } catch (Throwable ignored) {
            // nothing more can be done for it
        }
    }

    /** The picture into the box, top side up (an off-screen picture is stored bottom row first). */
    private static void blit(GuiGraphics g, Embed e, int x, int y, int w, int h) {
        RenderSystem.setShaderTexture(0, e.target.getColorTextureId());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bb.vertex(m, x, y, 0).uv(0, 1).endVertex();
        bb.vertex(m, x, y + h, 0).uv(0, 0).endVertex();
        bb.vertex(m, x + w, y + h, 0).uv(1, 0).endVertex();
        bb.vertex(m, x + w, y, 0).uv(1, 1).endVertex();
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableBlend();
    }

    /** Host GUI pixels to the box's JourneyMap GUI pixels. */
    private static double local(Embed e, double m, double origin) {
        return (m - origin) * e.hostScale / e.jmScale;
    }

    /**
     * Frees the pictures of boxes not drawn for ten seconds and hands their screens back to the pool (closing any popup left open in
     * them). JourneyMap's own close is not called: it clears state its other maps share.
     */
    private static void sweep() {
        long now = System.currentTimeMillis();
        for (List<HostScreen> pool : POOLS) {
            for (HostScreen hs : pool) {
                if (hs.owner != null && now - hs.drawnMs > 10_000L) {
                    hs.owner = null;
                    hs.layer = null;
                    hs.w = hs.h = -1; // laid out again when next shown
                }
            }
        }
        for (Map<GridModel.Box, Embed> map : EMBEDS) {
            for (Iterator<Map.Entry<GridModel.Box, Embed>> it = map.entrySet().iterator(); it.hasNext(); ) {
                Embed e = it.next().getValue();
                if (now - e.drawnMs > 10_000L) {
                    dispose(e);
                    it.remove();
                }
            }
        }
    }

    private static void dispose(Embed e) {
        if (e.target != null) {
            e.target.destroyBuffers();
            e.target = null;
        }
        if (laidOutFor == e) {
            laidOutFor = null;
        }
    }

    /** Frees everything (leaving the world). */
    public static void clear() {
        for (Map<GridModel.Box, Embed> map : EMBEDS) {
            map.values().forEach(JourneyMapView::dispose);
            map.clear();
        }
        for (List<HostScreen> pool : POOLS) {
            for (HostScreen hs : pool) {
                if (hs.w >= 0) {
                    try {
                        hs.screen.removed();
                    } catch (Throwable ignored) {
                        // JourneyMap tidies its own map state on leaving the world as well
                    }
                }
            }
            pool.clear();
        }
        RETURN_TO.clear();
        RETIRED.clear();
        laidOutFor = null;
        failed = false; // a new login tries JourneyMap afresh
        retryAtMs = 0;
        FAILURES.clear();
    }

    /** Frees pictures nobody draws any more even when no map is drawn anywhere (the popout closed, the Holo Grid shut). */
    @Mod.EventBusSubscriber(modid = BSPCore.MODID, value = Dist.CLIENT)
    public static final class Sweeper {
        private Sweeper() {}

        @SubscribeEvent
        public static void onJourneyMapSweep(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END && ++ticks % 20 == 0 && present != null && present) {
                sweep();
            }
        }
    }

    // ------------------------------------------------------------------ input

    private static Embed at(Host host, GridModel.Box box) {
        if (!active()) {
            return null;
        }
        Embed e = EMBEDS[host.ordinal()].get(box);
        return e == null || e.hs == null || e.target == null || e.hs.owner != box ? null : e;
    }

    /** True when the box shows JourneyMap's map in this host (its input goes to JourneyMap). */
    public static boolean shown(Host host, GridModel.Box box) {
        return at(host, box) != null;
    }

    /**
     * The map box in this host that takes every key now, or null: one with a JourneyMap popup open in it, or its chat or search box being
     * typed in.
     */
    public static GridModel.Box typing(Host host) {
        if (!active()) {
            return null;
        }
        long now = System.currentTimeMillis();
        for (HostScreen hs : POOLS[host.ordinal()]) {
            if (hs.owner != null && now - hs.drawnMs < 250 && at(host, hs.owner) != null // only a box that can be seen now
                    && (hs.layer != null || call(chatOpen, hs.screen) || searchTyping(hs.screen))) {
                return hs.owner;
            }
        }
        return null;
    }

    /** True when JourneyMap's search box has the keyboard (JourneyMap's own test also counts the mouse merely hovering it). */
    private static boolean searchTyping(Screen s) {
        return s.getFocused() instanceof net.minecraft.client.gui.components.EditBox
                || s.getFocused() != null && s.getFocused().getClass().getSimpleName().contains("TextBox") && call(searchFocused, s);
    }

    /** True when Esc is for the box (it closes its popup or its chat), not for the Holo Grid or the popout. */
    public static boolean takesEsc(Host host, GridModel.Box box) {
        Embed e = at(host, box);
        return e != null && (e.hs.layer != null || call(chatOpen, e.hs.screen));
    }

    private static boolean call(Method m, Screen s) {
        if (m == null || s == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(m.invoke(s));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            return false;
        }
    }

    public static boolean mouseClicked(Host host, GridModel.Box box, double mx, double my, int button) {
        Embed e = at(host, box);
        if (e == null) {
            return false;
        }
        e.mouseDown = true;
        return input(e, host, mx, my, true, s -> s.mouseClicked(local(e, mx, e.x), local(e, my, e.y), button));
    }

    public static boolean mouseDragged(Host host, GridModel.Box box, double mx, double my, int button, double dx, double dy) {
        Embed e = at(host, box);
        if (e == null || !e.mouseDown) {
            return false;
        }
        double k = e.hostScale / e.jmScale;
        return input(e, host, mx, my, false, s -> s.mouseDragged(local(e, mx, e.x), local(e, my, e.y), button, dx * k, dy * k));
    }

    public static boolean mouseReleased(Host host, GridModel.Box box, double mx, double my, int button) {
        Embed e = at(host, box);
        if (e == null || !e.mouseDown) {
            return false;
        }
        e.mouseDown = false;
        return input(e, host, mx, my, false, s -> s.mouseReleased(local(e, mx, e.x), local(e, my, e.y), button));
    }

    public static boolean mouseScrolled(Host host, GridModel.Box box, double mx, double my, double delta) {
        Embed e = at(host, box);
        return e != null && input(e, host, mx, my, true, s -> s.mouseScrolled(local(e, mx, e.x), local(e, my, e.y), delta));
    }

    public static void mouseMoved(Host host, GridModel.Box box, double mx, double my) {
        Embed e = at(host, box);
        if (e != null) {
            input(e, host, mx, my, false, s -> {
                s.mouseMoved(local(e, mx, e.x), local(e, my, e.y));
                return true;
            });
        }
    }

    public static boolean keyPressed(Host host, GridModel.Box box, double mx, double my, int key, int scan, int modifiers) {
        Embed e = at(host, box);
        return e != null && input(e, host, mx, my, true, s -> s.keyPressed(key, scan, modifiers));
    }

    public static boolean charTyped(Host host, GridModel.Box box, double mx, double my, char c, int modifiers) {
        Embed e = at(host, box);
        return e != null && input(e, host, mx, my, false, s -> s.charTyped(c, modifiers));
    }

    /**
     * Ends a drag on this host's maps that will never see its release (the Holo Grid closed, the popout closed or lost focus mid-drag):
     * JourneyMap would otherwise keep panning with the mouse the next time the map is shown.
     */
    public static void endDrags(Host host) {
        if (!active()) {
            return;
        }
        for (Embed e : new ArrayList<>(EMBEDS[host.ordinal()].values())) {
            if (e.mouseDown && e.hs != null && e.target != null) {
                e.mouseDown = false;
                double mx = e.lastMx, my = e.lastMy;
                input(e, host, mx, my, false, s -> s.mouseReleased(local(e, mx, e.x), local(e, my, e.y), 0));
            }
        }
    }

    private interface Call {
        boolean run(Screen s);
    }

    /**
     * One input call into the box's JourneyMap screen (or its popup). {@code atSize}: the call finds blocks under the mouse (a click, a
     * scroll, a key), so JourneyMap's shared map renderer is first brought to this box's size when another box or host drew last.
     */
    private static boolean input(Embed e, Host host, double mx, double my, boolean atSize, Call call) {
        e.lastMx = mx;
        e.lastMy = my;
        try (Swap s = new Swap(Minecraft.getInstance(), e, host, local(e, mx, e.x), local(e, my, e.y))) {
            try {
                if (atSize && laidOutFor != e && refreshState != null) {
                    refreshState.invoke(e.hs.screen);
                    laidOutFor = e;
                }
                return call.run(s.active());
            } catch (Throwable t) {
                s.undo(); // whatever JourneyMap did to the open screen before failing is put back
                throw t;
            }
        } catch (Throwable t) {
            fail(t);
            return false;
        }
    }

    // ------------------------------------------------------------------ the swap

    /**
     * For one call into JourneyMap's screen: the game window reports the box's size, the mouse handler the mouse in the box, the open
     * screen is the box's JourneyMap screen (or its popup), and its buttons are shown or hidden as the Holo Grid's setting says. All put
     * back on close, and screens JourneyMap changed to are sorted out as the class comment says.
     */
    private static final class Swap implements AutoCloseable {
        private final Minecraft mc;
        private final HostScreen hs;
        private final Screen before, active;
        private final Host host;
        private final int sFbW, sFbH, sScrW, sScrH, sGuiW, sGuiH;
        private final double sScale, sMx, sMy;
        private final boolean sButtons, wasGrabbed, show;
        private final List<AbstractWidget> hidden = new ArrayList<>();
        private boolean undo;

        /** {@code jx}, {@code jy}: the mouse in the box's JourneyMap GUI pixels. */
        Swap(Minecraft mc, Embed e, Host host, double jx, double jy) throws IllegalAccessException {
            this.mc = mc;
            this.hs = e.hs;
            this.host = host;
            active = hs.layer != null ? hs.layer : hs.screen;
            // everything that can fail first, so a failure leaves nothing changed
            show = host != Host.PIN && BSPClientConfig.holoMapControls();
            if (!show) {
                List<Object> all = new ArrayList<>(hs.screen.children());
                if (renderables.get(hs.screen) instanceof List<?> r) {
                    all.addAll(r);
                }
                for (Object o : all) {
                    if (o instanceof AbstractWidget a && a.visible && !hidden.contains(a)) {
                        hidden.add(a);
                    }
                }
            }
            Window w = mc.getWindow();
            MouseHandler mh = mc.mouseHandler;
            wasGrabbed = mh.isMouseGrabbed();
            sFbW = fbW.getInt(w);
            sFbH = fbH.getInt(w);
            sScrW = scrW.getInt(w);
            sScrH = scrH.getInt(w);
            sGuiW = guiW.getInt(w);
            sGuiH = guiH.getInt(w);
            sScale = guiScale.getDouble(w);
            sMx = mouseX.getDouble(mh);
            sMy = mouseY.getDouble(mh);
            sButtons = buttonsVisible.getBoolean(null);
            before = mc.screen;
            // then the changes, which cannot fail
            fbW.setInt(w, e.pxW);
            fbH.setInt(w, e.pxH);
            scrW.setInt(w, e.scrW);
            scrH.setInt(w, e.scrH);
            guiW.setInt(w, e.guiW);
            guiH.setInt(w, e.guiH);
            guiScale.setDouble(w, e.jmScale);
            // the mouse in window points of the pretend window: JourneyMap turns it back into its own GUI pixels
            mouseX.setDouble(mh, jx * e.scrW / (double) e.guiW);
            mouseY.setDouble(mh, jy * e.scrH / (double) e.guiH);
            buttonsVisible.setBoolean(null, show);
            for (AbstractWidget a : hidden) {
                a.visible = false; // hidden buttons take no clicks either
            }
            swapDepth++;
            mc.screen = active;
        }

        Screen active() {
            return active;
        }

        /** True when the open screen is no longer the one swapped in (JourneyMap changed it during the call). */
        boolean changed() {
            return mc.screen != active;
        }

        /** On close, puts back the screen that was open, whatever JourneyMap did to it during the call. */
        void undo() {
            undo = true;
        }

        @Override
        public void close() {
            swapDepth--;
            for (AbstractWidget a : hidden) {
                a.visible = true;
            }
            try {
                // JourneyMap's own "hide buttons" key pressed over the box: it switches the Holo Grid's setting
                boolean nowButtons = buttonsVisible.getBoolean(null);
                if (host != Host.PIN && nowButtons != show) {
                    BSPClientConfig.set(BSPClientConfig.HOLO_MAP_CONTROLS, nowButtons);
                }
                Window w = mc.getWindow();
                fbW.setInt(w, sFbW);
                fbH.setInt(w, sFbH);
                scrW.setInt(w, sScrW);
                scrH.setInt(w, sScrH);
                guiW.setInt(w, sGuiW);
                guiH.setInt(w, sGuiH);
                guiScale.setDouble(w, sScale);
                mouseX.setDouble(mc.mouseHandler, sMx);
                mouseY.setDouble(mc.mouseHandler, sMy);
                buttonsVisible.setBoolean(null, sButtons);
            } catch (IllegalAccessException ex) {
                throw new IllegalStateException(ex);
            }
            Screen now = mc.screen;
            if (now == active) {
                mc.screen = before;
                return;
            }
            if (!undo) {
                if (now == hs.screen && hs.layer != null) {
                    hs.layer = null; // its popup closed back onto the map
                    mc.screen = before;
                    return;
                }
                if (now != null && now != before && layeredClass != null && layeredClass.isInstance(now)) {
                    // a JourneyMap popup (it draws the map behind itself and closes back onto it): it lives in the box until closed
                    hs.layer = now;
                    mc.screen = before;
                    return;
                }
            }
            if (undo || host == Host.PIN || (host == Host.POPOUT && now == null)) {
                // not JourneyMap's to change here: the pin is not used, and closing the map in the popout must not close the game
                // window's screen. Put back the screen and the mouse as they were (a screen JourneyMap opened is dropped unseen)
                mc.screen = before;
                popReturn(hs.screen);
                grab();
                return;
            }
            // JourneyMap opened its own screen (options, waypoint manager, its splash), or closed its map in the Holo Grid: that stays
            hs.layer = null;
            boolean closedContainer = false;
            if (before != now && before instanceof AbstractContainerScreen<?> && mc.player != null) {
                // a container screen in the game window replaced from the popout: closed as the player closing it would (Esc)
                mc.player.connection.send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
                mc.player.containerMenu = mc.player.inventoryMenu;
                closedContainer = true;
            }
            // coming back to the map goes back to the screen that was open instead (the game, for a container now closed)
            RETURN_TO.put(hs.screen, closedContainer ? null : before);
            if (before != null && now != before) {
                before.removed();
            }
            if (now != null && mc.mouseHandler.isMouseGrabbed()) {
                mc.mouseHandler.releaseMouse(); // JourneyMap sets some screens directly, which leaves the mouse grabbed by the game
            } else if (wasGrabbed && !mc.mouseHandler.isMouseGrabbed()) {
                // freed for the new screen while the window was pretending to be the box: the cursor goes to the real centre
                double cx = sScrW / 2.0, cy = sScrH / 2.0;
                InputConstants.grabOrReleaseMouse(mc.getWindow().getWindow(), GLFW.GLFW_CURSOR_NORMAL, cx, cy);
                try {
                    mouseX.setDouble(mc.mouseHandler, cx);
                    mouseY.setDouble(mc.mouseHandler, cy);
                } catch (IllegalAccessException ignored) {
                    // set above with the same fields
                }
            }
            if (now != null) {
                now.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()); // it was laid out in the box's size
            }
        }

        /** The mouse grabbed or free again as it was before the call. */
        private void grab() {
            boolean grabbed = mc.mouseHandler.isMouseGrabbed();
            if (wasGrabbed && !grabbed) {
                mc.mouseHandler.grabMouse();
            } else if (!wasGrabbed && grabbed) {
                mc.mouseHandler.releaseMouse();
            }
        }
    }

    /** JourneyMap returning to an embedded map: back to the Holo Grid (or the game) instead. */
    @Mod.EventBusSubscriber(modid = BSPCore.MODID, value = Dist.CLIENT)
    public static final class Returns {
        private Returns() {}

        @SubscribeEvent
        public static void onJourneyMapReturn(ScreenEvent.Opening event) {
            Screen to = event.getNewScreen();
            if (to != null && swapDepth == 0 && pooled(to)) {
                event.setNewScreen(RETURN_TO.remove(to)); // never the embedded map itself as the game's screen; null: the game
            }
        }
    }
}
