package com.mrgregles.bsp_core.client.tracking;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mrgregles.bsp_core.BSPClientConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.TrackerActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLCapabilities;

import java.util.List;

import static com.mrgregles.bsp_core.client.tracking.Hud.*;

/**
 * The Holo Grid's popout window for a second monitor, the owner's pick "Side Rail" (P9): the page fills the window, with a narrow rail of
 * numbered page keys down the left edge and a focus lamp at its foot. The grid stretches to any window shape and re-stacks its boxes past
 * 2:1 ({@link GridView#layout}).
 *
 * <p>How it is drawn (after BSP-Server Utilities' stream window): a second GLFW window whose OpenGL context shares the game's. The page is
 * drawn in the game's context into an off-screen picture at the window's size, only when something on it changed (a reading, the page,
 * the size, the mouse while focused) and at most {@code popoutFps} times a second; each frame the picture is copied into the window in its
 * own context with one blit, without waiting for the screen's refresh. Only raw OpenGL calls run in the popout's context, so the game's
 * cached GL state is never touched.
 *
 * <p>Focus (owner decision MT39/MT46): the focus key moves the keyboard and mouse into the popout without the game's pause menu (the game
 * is told it still has focus, so it keeps showing the world); the key again, Esc, or a click on the game window gives it back. Alt-tabbing
 * into the popout keeps Minecraft's own behaviour. Page keys are clickable in every state.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID, value = Dist.CLIENT)
public final class Popout {
    static final String TITLE = "BSP Holo Grid";
    private static final int RAIL = 18;

    private static long window;
    private static GLCapabilities caps;
    private static int readFbo;
    private static TextureTarget target;
    private static boolean focused, focusedByKey;
    private static long lastFrameNs, lastDrawNs;
    private static int drawnVersion = -1, drawnPage = -1, drawnW, drawnH;
    private static double mouseX = -1, mouseY = -1, lastDragX, lastDragY;
    private static boolean dirty = true, dragging;
    private static GridModel.Box dragBox;
    /** True while the popout's page is being drawn: scissor boxes use the game window's size, so they are skipped. */
    static boolean drawing;
    private static final int[] FW = new int[1], FH = new int[1], WW = new int[1], WH = new int[1];

    private Popout() {}

    public static boolean isOpen() {
        return window != 0L;
    }

    /** Opens the window at 640 x 400; nothing when GLFW cannot make it (the reason goes to the log). */
    public static void open() {
        if (window != 0L) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        long game = mc.getWindow().getWindow();
        long ctx = GLFW.glfwGetCurrentContext();
        GLCapabilities gameCaps = GL.getCapabilities();
        try {
            GLFW.glfwDefaultWindowHints();
            // the same kind of context as the game's window, so the two can share (macOS refuses to share otherwise)
            int major = GLFW.glfwGetWindowAttrib(game, GLFW.GLFW_CONTEXT_VERSION_MAJOR), minor = GLFW.glfwGetWindowAttrib(game, GLFW.GLFW_CONTEXT_VERSION_MINOR);
            int profile = GLFW.glfwGetWindowAttrib(game, GLFW.GLFW_OPENGL_PROFILE);
            GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.glfwGetWindowAttrib(game, GLFW.GLFW_CLIENT_API));
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_CREATION_API, GLFW.glfwGetWindowAttrib(game, GLFW.GLFW_CONTEXT_CREATION_API));
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, major > 0 ? major : 3);
            GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, major > 0 ? minor : 2);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, profile != 0 ? profile : GLFW.GLFW_OPENGL_CORE_PROFILE);
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.glfwGetWindowAttrib(game, GLFW.GLFW_OPENGL_FORWARD_COMPAT));
            GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE);
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUS_ON_SHOW, GLFW.GLFW_FALSE);
            long w = GLFW.glfwCreateWindow(640, 400, TITLE, 0L, game);
            GLFW.glfwDefaultWindowHints();
            if (w == 0L) {
                BSPCore.LOGGER.error("Holo Grid: the popout window could not be made (GLFW)");
                return;
            }
            GLFW.glfwMakeContextCurrent(w);
            caps = GL.createCapabilities();
            GLFW.glfwSwapInterval(0); // never wait for the screen's refresh: the game's frame rate stays the game's
            window = w;
            readFbo = 0;
            GLFW.glfwSetCursorPosCallback(w, (win, x, y) -> mouseMoved(x, y));
            GLFW.glfwSetMouseButtonCallback(w, (win, button, action, mods) -> mouseButton(button, action));
            GLFW.glfwSetScrollCallback(w, (win, dx, dy) -> scrolled(dy));
            GLFW.glfwSetKeyCallback(w, (win, key, scan, action, mods) -> key(key, scan, action));
            GLFW.glfwSetWindowFocusCallback(w, (win, f) -> {
                focused = f;
                if (!f) {
                    focusedByKey = false;
                }
                dirty = true;
            });
            dirty = true;
        } catch (Throwable e) {
            BSPCore.LOGGER.error("Holo Grid: the popout window could not be made", e);
        } finally {
            GLFW.glfwMakeContextCurrent(ctx);
            GL.setCapabilities(gameCaps);
        }
        WatchSet.update();
    }

    public static void close() {
        if (window != 0L) {
            long w = window;
            window = 0L;
            caps = null;
            readFbo = 0;
            org.lwjgl.glfw.Callbacks.glfwFreeCallbacks(w); // the callbacks' closures go with the window
            GLFW.glfwDestroyWindow(w);
        }
        focused = false; // a destroyed window sends no "focus lost"
        focusedByKey = false;
        dragging = false;
        dragBox = null;
        mouseX = mouseY = -1;
        if (target != null) {
            target.destroyBuffers(); // the off-screen picture's GPU memory
            target = null;
        }
        WatchSet.update();
    }

    /** A clip in the popout's off-screen picture, in its GUI pixels (the game window's scissor would use the wrong size). */
    static void scissor(int x, int y, int w, int h) {
        int s = scale();
        RenderSystem.enableScissor(x * s, FH[0] - (y + h) * s, Math.max(0, w * s), Math.max(0, h * s));
    }

    /** The focus key: into the popout (opening it first), or back to the game. */
    public static void focus() {
        if (window == 0L) {
            open();
        }
        if (window == 0L) {
            return;
        }
        if (focused) {
            giveBack();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.KeyMapping.releaseAll(); // keys held now would never see their release in the game window
        mc.mouseHandler.releaseMouse();
        focusedByKey = true;
        GLFW.glfwFocusWindow(window);
    }

    /** Focus back to the game window. */
    public static void giveBack() {
        focusedByKey = false;
        GLFW.glfwFocusWindow(Minecraft.getInstance().getWindow().getWindow());
    }

    // ------------------------------------------------------------------ the frame

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (window == 0L) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.START) {
            if (focusedByKey && focused) {
                mc.setWindowActive(true); // the game keeps showing the world: no pause menu for leaving it through the focus key
            }
            return;
        }
        if (GLFW.glfwWindowShouldClose(window)) {
            close();
            return;
        }
        long now = System.nanoTime();
        if (now - lastFrameNs < 1_000_000_000L / BSPClientConfig.popoutFps()) {
            return;
        }
        lastFrameNs = now;
        GLFW.glfwGetFramebufferSize(window, FW, FH);
        if (FW[0] <= 0 || FH[0] <= 0 || mc.level == null) {
            return;
        }
        if (target == null) {
            target = new TextureTarget(FW[0], FH[0], true, Minecraft.ON_OSX);
            dirty = true;
        } else if (target.width != FW[0] || target.height != FH[0]) {
            target.resize(FW[0], FH[0], Minecraft.ON_OSX);
            dirty = true;
        }
        boolean readings = drawnVersion != ClientTracking.version() && now - lastDrawNs > 100_000_000L; // new readings: at most 10 redraws a second
        if (dirty || readings || drawnPage != GridModel.current || drawnW != FW[0] || drawnH != FH[0] || now - lastDrawNs > 1_000_000_000L) {
            drawPicture(mc); // also once a second, for the map and the times shown
            lastDrawNs = now;
            dirty = false;
            drawnVersion = ClientTracking.version();
            drawnPage = GridModel.current;
            drawnW = FW[0];
            drawnH = FH[0];
        }
        present();
    }

    /** The GUI scale of the popout: 2 on a big window, else 1. */
    private static int scale() {
        return FW[0] >= 900 ? 2 : 1;
    }

    private static void drawPicture(Minecraft mc) {
        int s = scale(), gw = FW[0] / s, gh = FH[0] / s;
        target.setClearColor(0.03f, 0.035f, 0.05f, 1f);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, gw, gh, 0, 1000, 21000), VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        mv.translate(0, 0, -11000);
        RenderSystem.applyModelViewMatrix();
        Lighting.setupFor3DItems();
        drawing = true;
        try {
            GuiGraphics g = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            int mx = (int) (mouseX / s), my = (int) (mouseY / s);
            drawRail(g, gh, mx, my);
            GridModel.Page page = GridModel.page();
            List<GridView.Placed> placed = GridView.layout(page, RAIL + 2, 2, gw - RAIL - 4, gh - 4, true);
            for (GridView.Placed p : placed) {
                GridView.drawBox(g, p, focused ? mx : -1, focused ? my : -1, focused);
            }
            if (focused) {
                outline(g, 0, 0, gw, gh, TQ);
            }
            g.flush();
        } finally {
            drawing = false;
            mv.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            mc.getMainRenderTarget().bindWrite(true);
        }
    }

    private static void drawRail(GuiGraphics g, int gh, int mx, int my) {
        rect(g, 0, 0, RAIL, gh, 0xFF0B0F14);
        rect(g, RAIL, 0, 1, gh, DIM);
        for (int i = 0; i < GridModel.PAGES.size(); i++) {
            int y = 4 + i * 20;
            boolean on = i == GridModel.current;
            rect(g, 2, y, 14, 18, on ? 0xFF0E3A33 : 0xFF121821);
            outline(g, 2, y, 14, 18, on ? TQ : DIM);
            String n = String.valueOf(i + 1);
            text(g, n, 9 - font().width(n) / 2, y + 5, on ? TQ : MUTED);
        }
        lamp(g, 9, gh - 8, 3, focused ? TQ : OFF, true);
    }

    private static void present() {
        GL11.glFlush(); // the picture drawn in the game's context is finished before the other context reads it
        long ctx = GLFW.glfwGetCurrentContext();
        GLCapabilities gameCaps = GL.getCapabilities();
        GLFW.glfwMakeContextCurrent(window);
        GL.setCapabilities(caps);
        try {
            if (readFbo == 0) {
                readFbo = GL30.glGenFramebuffers();
            }
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, target.getColorTextureId(), 0);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);
            GL11.glViewport(0, 0, FW[0], FH[0]);
            GL30.glBlitFramebuffer(0, 0, target.width, target.height, 0, 0, FW[0], FH[0], GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
            GLFW.glfwSwapBuffers(window);
        } finally {
            GLFW.glfwMakeContextCurrent(ctx);
            GL.setCapabilities(gameCaps);
        }
    }

    // ------------------------------------------------------------------ input in the popout

    /** Window coordinates to framebuffer pixels (a Retina window has twice as many). */
    private static double[] toPixels(double x, double y) {
        GLFW.glfwGetWindowSize(window, WW, WH);
        GLFW.glfwGetFramebufferSize(window, FW, FH);
        double kx = WW[0] > 0 ? FW[0] / (double) WW[0] : 1, ky = WH[0] > 0 ? FH[0] / (double) WH[0] : 1;
        return new double[] {x * kx, y * ky};
    }

    private static void mouseMoved(double x, double y) {
        double[] p = toPixels(x, y);
        mouseX = p[0];
        mouseY = p[1];
        int s = scale();
        if (dragging && dragBox != null) {
            MapView.drag(GridView.map(dragBox), (mouseX - lastDragX) / s, (mouseY - lastDragY) / s);
            lastDragX = mouseX;
            lastDragY = mouseY;
        }
        if (focused) {
            dirty = true;
        }
    }

    private static void mouseButton(int button, int action) {
        if (action == GLFW.GLFW_RELEASE) {
            dragging = false;
            dragBox = null;
            return;
        }
        int s = scale(), gw = FW[0] / s, gh = FH[0] / s;
        double mx = mouseX / s, my = mouseY / s;
        dirty = true;
        // the rail's page keys work whether or not the popout has focus
        for (int i = 0; i < GridModel.PAGES.size(); i++) {
            if (mx >= 2 && mx < 16 && my >= 4 + i * 20 && my < 22 + i * 20) {
                GridModel.show(i);
                return;
            }
        }
        if (Math.hypot(mx - 9, my - (gh - 8)) <= 5) {
            if (focused) {
                giveBack();
            }
            return;
        }
        for (GridView.Placed p : GridView.layout(GridModel.page(), RAIL + 2, 2, gw - RAIL - 4, gh - 4, true)) {
            if (!p.contains(mx, my)) {
                continue;
            }
            if (p.box().src.equals("map")) {
                if (!MapView.click(p.x(), p.y(), p.w(), p.h(), GridView.map(p.box()), mx, my)) {
                    dragging = true;
                    dragBox = p.box();
                    lastDragX = mouseX;
                    lastDragY = mouseY;
                }
                return;
            }
            String id = p.box().tagId();
            if (id != null && ClientTracking.reading(id).getBoolean("op")) {
                BSPNetwork.CHANNEL.sendToServer(new TrackerActionPacket(TrackerActionPacket.OPERATE, id, "", new CompoundTag()));
                giveBack(); // the machine's screen opens in the game window
            }
            return;
        }
    }

    private static void scrolled(double dy) {
        int s = scale(), gw = FW[0] / s, gh = FH[0] / s;
        for (GridView.Placed p : GridView.layout(GridModel.page(), RAIL + 2, 2, gw - RAIL - 4, gh - 4, true)) {
            if (p.contains(mouseX / s, mouseY / s) && p.box().src.equals("map")) {
                MapView.scroll(GridView.map(p.box()), dy);
                dirty = true;
                return;
            }
        }
    }

    private static void key(int key, int scan, int action) {
        if (action != GLFW.GLFW_PRESS) {
            return;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || TrackerKeys.FOCUS.matches(key, scan)) {
            giveBack();
            return;
        }
        if (TrackerKeys.pageKey(key, scan)) {
            dirty = true;
        }
    }

    /** Closes the popout when the player leaves the world. */
    @Mod.EventBusSubscriber(modid = BSPCore.MODID, value = Dist.CLIENT)
    public static final class Leave {
        private Leave() {}

        @SubscribeEvent
        public static void onPopoutLeave(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
            close();
            MapView.clear();
            WatchSet.reset();
        }
    }
}
