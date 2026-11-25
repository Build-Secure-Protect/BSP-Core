package com.mrgregles.bsp_core.client.tracking;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
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
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.tracking.InventoryLink;
import com.mrgregles.bsp_core.tracking.ScreenMirror;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A machine's real screen in its Holo Grid box (owner decisions MS2 to MS4): the mod's own screen class, opened on a stand-in of the
 * machine built from the server's reading ({@code real}: menu type, block entity type, block state, saved data, data slot values) and drawn
 * read-only into the box, so it looks exactly as the mod draws it. It takes no input, and nothing it would send reaches the server
 * ({@code StandInQuietMixin} drops a packet sent while it is built or drawn, as XNet's controller asks for its lists); other mods' screen
 * events do not fire for it ({@code StandInScreenEventsMixin}). The stand-in sits at a place far below the world ({@link #SENTINEL}).
 *
 * <p>How the menu is built: with the mod's own menu type, given a buffer holding only the sentinel place; a menu that looks its machine up
 * there gets the stand-in ({@code StandInBlockEntityMixin}); Mekanism's menu types take the stand-in directly. Each new reading loads the
 * saved data into the stand-in again and sets the data slots (and, for vanilla-style menus with their own slots, the items). The screen is
 * laid out at its own size, drawn into an off-screen picture at a whole GUI scale and fitted into the box. Anything that fails marks that
 * kind of machine and its box keeps BSP-Core's own panel ({@link ModPanels}).
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = BSPCore.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class RealScreens {
    public static final BlockPos SENTINEL = new BlockPos(0, -2000, 0); // below every world, and within what a packed position can carry (-2048 to 2047)
    private static BlockEntity building;
    /** While a stand-in screen is laid out or drawn: other mods' screen events and anything it would send stay quiet. */
    private static boolean painting;
    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    /** Machine types whose real screen failed this run: they keep BSP-Core's panel. */
    private static final Set<String> BROKEN = new HashSet<>();
    private static int nextId = 101;
    private static Field fbW, fbH, scrW, scrH, guiW, guiH, guiScale;
    private static boolean fieldsTried;

    private static final class Entry {
        String menu = "", be = "";
        BlockEntity standIn;
        AbstractContainerMenu container;
        AbstractContainerScreen<?> screen;
        TextureTarget target;
        int scale, laidW, laidH;
        /** The part of the laid-out area to show: the panel and every tab beside it (x, y, w, h in GUI pixels). */
        int[] bounds;
        long applied = -1, drawnMs, paintedMs;
        /** The world and the player's inventory it was built for. */
        Object level, inventory;
        /** A new reading since the picture was last painted. */
        boolean dirty = true;
    }

    /** Repaints of an unchanged screen, for what it animates itself (fluids, blinking lights): 20 a second. */
    private static final long REPAINT_MS = 50;

    private RealScreens() {}

    /** The stand-in while a menu is being built (for {@code StandInBlockEntityMixin}), else null. */
    public static BlockEntity building() {
        return building;
    }

    /** Whether a stand-in screen is being built or drawn now, on the render thread (for the client mixins that keep it quiet). */
    public static boolean quiet() {
        return (painting || building != null) && RenderSystem.isOnRenderThread();
    }

    /** Draws tag {@code id}'s real screen into the box; false when it has none or it cannot be shown (the caller draws its panel). */
    public static boolean draw(GuiGraphics g, String id, CompoundTag reading, int x, int y, int w, int h) {
        CompoundTag real = reading.getCompound("real");
        Minecraft mc = Minecraft.getInstance();
        if (real.isEmpty() || reading.getByte("st") != 0 || mc.level == null || mc.player == null || w < 16 || h < 16
                || BROKEN.contains(real.getString("be")) || !fields()) {
            return false;
        }
        // one picture per box and view: the Holo Grid, the popout and a pin each show the screen at their own size
        String key = JourneyMapView.host().name() + "/" + id;
        Entry e = ENTRIES.computeIfAbsent(key, k -> new Entry());
        try {
            if (e.screen == null || !e.menu.equals(real.getString("menu")) || !e.be.equals(real.getString("be")) || e.level != mc.level
                    || e.inventory != mc.player.getInventory()) {
                build(mc, e, real); // also after a dimension change, a respawn or a server switch: never the old world's or player's
                e.applied = reading.getLong("arrived");
            }
            if (e.applied != reading.getLong("arrived")) {
                apply(e, real);
                e.applied = reading.getLong("arrived");
            }
            render(g, mc, e, x, y, w, h);
            e.drawnMs = System.currentTimeMillis();
            return true;
        } catch (Throwable t) {
            tidyAfterFailure(mc);
            BROKEN.add(real.getString("be"));
            BSPCore.LOGGER.warn("Holo Grid: {}'s own screen cannot be shown in a box ({}); BSP-Core's panel is shown instead", real.getString("be"), t.toString());
            BSPCore.LOGGER.debug("Holo Grid: real screen failure", t);
            dispose(e);
            ENTRIES.remove(key);
            return false;
        }
    }

    /** A screen that failed part-way may leave shapes half-built: they are dropped, so the next drawing does not fail too. */
    private static void tidyAfterFailure(Minecraft mc) {
        try {
            BufferBuilder bb = Tesselator.getInstance().getBuilder();
            if (bb.building()) {
                bb.end().release();
            }
            mc.renderBuffers().bufferSource().endBatch();
        } catch (RuntimeException ignored) {
            // only tidying
        }
    }

    private static boolean fields() {
        if (!fieldsTried) {
            fieldsTried = true;
            try {
                fbW = ObfuscationReflectionHelper.findField(Window.class, "f_85361_");
                fbH = ObfuscationReflectionHelper.findField(Window.class, "f_85362_");
                scrW = ObfuscationReflectionHelper.findField(Window.class, "f_85359_");
                scrH = ObfuscationReflectionHelper.findField(Window.class, "f_85360_");
                guiW = ObfuscationReflectionHelper.findField(Window.class, "f_85363_");
                guiH = ObfuscationReflectionHelper.findField(Window.class, "f_85364_");
                guiScale = ObfuscationReflectionHelper.findField(Window.class, "f_85365_");
            } catch (RuntimeException ex) {
                fbW = null;
            }
        }
        return fbW != null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void build(Minecraft mc, Entry e, CompoundTag real) throws Exception {
        dispose(e);
        e.menu = real.getString("menu");
        e.be = real.getString("be");
        BlockEntityType<?> type = ForgeRegistries.BLOCK_ENTITY_TYPES.getValue(new ResourceLocation(e.be));
        MenuType<?> menuType = BuiltInRegistries.MENU.get(new ResourceLocation(e.menu));
        if (type == null || menuType == null) {
            throw new IllegalStateException("unknown block entity or menu type");
        }
        BlockState state = NbtUtils.readBlockState(mc.level.holderLookup(Registries.BLOCK), real.getCompound("state"));
        BlockEntity standIn = type.create(SENTINEL, state);
        if (standIn == null) {
            throw new IllegalStateException("no stand-in");
        }
        if (!standIn.getClass().getName().startsWith("cofh.")) {
            standIn.setLevel(mc.level); // Mekanism's machines ask their level whether they are on the client; CoFH's must not have one
        }
        standIn.load(real.getCompound("nbt")); // before the menu: some menus size themselves from it (augment slots)
        int id = nextId;
        nextId = nextId >= 127 ? 101 : nextId + 1; // never a server menu's id (1 to 100)
        Inventory inv = mc.player.getInventory(); // the player's own items in the screen's lower half, shown as the real screen shows them
        AbstractContainerMenu menu = null;
        AbstractContainerMenu playerMenu = mc.player.containerMenu;
        building = standIn;
        try {
            for (Method m : menuType.getClass().getMethods()) {
                // Mekanism: its menu types take the machine itself
                if (m.getName().equals("create") && m.getParameterCount() == 3 && m.getParameterTypes()[0] == int.class
                        && m.getParameterTypes()[1] == Inventory.class && m.getParameterTypes()[2] == Object.class) {
                    menu = (AbstractContainerMenu) m.invoke(menuType, id, inv, standIn);
                    break;
                }
            }
            if (menu == null) {
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                if (ScreenMirror.family(standIn.getClass(), "appeng.")) {
                    // AE2 menus read a locator first: the block entity locator, the place, "not from a sub-menu"
                    buf.writeUtf("appeng.menu.locator.BlockEntityLocator");
                    buf.writeBlockPos(SENTINEL);
                    buf.writeBoolean(false);
                } else if (e.menu.equals("refinedstorage:grid")) {
                    // Refined Storage grids read which grid kind, then "has a place" + place, "has an item", the hand slot
                    boolean portable = standIn.getClass().getSimpleName().startsWith("PortableGrid");
                    buf.writeResourceLocation(new ResourceLocation("refinedstorage", portable ? "portable_grid_block" : "grid"));
                    buf.writeBoolean(true);
                    buf.writeBlockPos(SENTINEL);
                    buf.writeBoolean(false);
                    buf.writeInt(-1);
                    buf.writeBoolean(false);
                } else {
                    buf.writeBlockPos(SENTINEL);
                }
                if (ScreenMirror.family(standIn.getClass(), "mcjty.")) {
                    // McJtyLib's "remote" menus read the dimension and the machine's data after the place
                    buf.writeResourceLocation(mc.level.dimension().location());
                    buf.writeNbt(real.getCompound("nbt"));
                }
                buf.writeBytes(new byte[512]); // menus that read more than the place get empty values (false, 0, "", no item)
                menu = menuType.create(id, inv, buf);
            }
        } finally {
            building = null;
            mc.player.containerMenu = playerMenu;
        }
        if (menu == null) {
            throw new IllegalStateException("no menu");
        }
        Component parsed = Component.Serializer.fromJson(real.getString("title"));
        Component title = parsed == null ? Component.empty() : parsed;
        java.util.Optional<?> factory = MenuScreens.getScreenFactory((MenuType) menuType, mc, id, title);
        if (factory.isEmpty()) {
            throw new IllegalStateException("no screen");
        }
        Screen screen;
        building = standIn; // screens that look their machine up when made (McJtyLib's) find the stand-in too
        try {
            screen = (Screen) ((MenuScreens.ScreenConstructor) factory.get()).create(menu, inv, title);
        } finally {
            building = null;
            mc.player.containerMenu = playerMenu;
        }
        if (!(screen instanceof AbstractContainerScreen<?> acs)) {
            throw new IllegalStateException("not a container screen");
        }
        e.standIn = standIn;
        e.level = mc.level;
        e.inventory = inv;
        e.container = menu;
        e.screen = acs;
        e.laidW = e.laidH = -1;
        apply(e, real);
    }

    /** A new reading: the stand-in's saved data, the data slots, and the items of menus with their own slots (vanilla style). */
    private static void apply(Entry e, CompoundTag real) {
        e.standIn.load(real.getCompound("nbt"));
        int[] data = real.getIntArray("data");
        for (int i = 0; i < data.length; i++) {
            try {
                e.container.setData(i, data[i]);
            } catch (RuntimeException ignored) {
                break; // fewer data slots on this side: the rest are not shown
            }
        }
        CompoundTag nbt = real.getCompound("nbt");
        if (nbt.contains("Items", Tag.TAG_LIST)) {
            // vanilla-style menus: the machine's own slots (never the player's) get its saved items, by their place in the machine
            ListTag items = nbt.getList("Items", Tag.TAG_COMPOUND);
            Map<Integer, ItemStack> put = new HashMap<>();
            for (int i = 0; i < items.size(); i++) {
                CompoundTag it = items.getCompound(i);
                if (it.contains("Slot")) { // a list without places (McJtyLib's) is not this kind
                    put.put(it.getByte("Slot") & 255, ItemStack.of(it));
                }
            }
            if (put.isEmpty() && !items.isEmpty()) {
                e.dirty = true; // items without places: not a vanilla-style machine, its own screen reads them from the stand-in
                return;
            }
            for (Slot slot : e.container.slots) {
                if (!InventoryLink.playerSlot(slot)) {
                    slot.set(put.getOrDefault(slot.getContainerSlot(), ItemStack.EMPTY));
                }
            }
        }
        e.dirty = true;
    }

    private static void render(GuiGraphics g, Minecraft mc, Entry e, int x, int y, int w, int h) throws Exception {
        double hostScale, ratio;
        RenderTarget host;
        if (Popout.drawing) {
            hostScale = Popout.scale();
            ratio = Popout.pixelRatio();
            host = Popout.target();
        } else {
            Window win = mc.getWindow();
            hostScale = win.getGuiScale();
            ratio = win.getScreenWidth() > 0 ? win.getWidth() / (double) win.getScreenWidth() : 1;
            host = mc.getMainRenderTarget();
        }
        int xs = e.screen.getXSize(), ys = e.screen.getYSize();
        if (xs <= 0 || ys <= 0) {
            xs = 176;
            ys = 166;
        }
        // laid out with room round the panel for the tabs mods put beside it (Mekanism's side tabs, Thermal's panels)
        int iw = xs + 2 * MARGIN_X, ih = ys + 2 * MARGIN_Y;
        if (e.screen instanceof net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener) {
            iw = Math.max(iw, 380); // wide enough that an open recipe book sits beside the panel (and outside what is shown), not over it
        }
        int[] b = e.bounds == null ? new int[] {MARGIN_X, MARGIN_Y, xs, ys} : e.bounds;
        // a whole GUI scale that fits the shown part into the box's pixels (at least 1; the picture is then fitted down)
        int s = (int) Math.max(1, Math.min(Math.floor(w * hostScale / b[2]), Math.floor(h * hostScale / b[3])));
        s = Math.min(s, 4);
        int pw = iw * s, ph = ih * s;
        long now = System.currentTimeMillis();
        boolean repaint = e.dirty || now - e.paintedMs >= REPAINT_MS || e.laidW != iw || e.laidH != ih || e.scale != s;
        if (e.target == null) {
            e.target = new TextureTarget(pw, ph, true, Minecraft.ON_OSX);
            repaint = true;
        } else if (e.target.width != pw || e.target.height != ph) {
            e.target.resize(pw, ph, Minecraft.ON_OSX);
            repaint = true;
        }
        if (repaint) {
            paint(g, mc, e, iw, ih, s, pw, ph, ratio, host);
            e.dirty = false;
            e.paintedMs = now;
            b = e.bounds;
        }
        blit(g, e, b, iw, ih, x, y, w, h);
    }

    /** Lays the screen out when needed and draws it into its off-screen picture. */
    private static void paint(GuiGraphics g, Minecraft mc, Entry e, int iw, int ih, int s, int pw, int ph, double ratio, RenderTarget host) throws Exception {
        g.flush();
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        PoseStack mv = RenderSystem.getModelViewStack();
        PoseStack.Pose outer = mv.last();
        mv.pushPose();
        Window win = mc.getWindow();
        int sFbW = fbW.getInt(win), sFbH = fbH.getInt(win), sScrW = scrW.getInt(win), sScrH = scrH.getInt(win), sGuiW = guiW.getInt(win), sGuiH = guiH.getInt(win);
        double sScale = guiScale.getDouble(win);
        AbstractContainerMenu playerMenu = mc.player.containerMenu;
        building = e.standIn; // screens that look their machine up while laid out or drawn (McJtyLib's) find the stand-in too
        painting = true;
        try {
            e.target.setClearColor(0f, 0f, 0f, 0f);
            e.target.clear(Minecraft.ON_OSX);
            e.target.bindWrite(true);
            fbW.setInt(win, pw);
            fbH.setInt(win, ph);
            scrW.setInt(win, (int) Math.max(1, Math.round(pw / ratio)));
            scrH.setInt(win, (int) Math.max(1, Math.round(ph / ratio)));
            guiW.setInt(win, iw);
            guiH.setInt(win, ih);
            guiScale.setDouble(win, s);
            if (e.laidW != iw || e.laidH != ih || e.scale != s) {
                e.screen.init(mc, iw, ih); // the panel centred in the area, with room for its tabs
                e.laidW = iw;
                e.laidH = ih;
                e.scale = s;
                e.bounds = shown(e.screen, iw, ih);
            }
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, iw, ih, 0, 1000, ForgeHooksClient.getGuiFarPlane()), VertexSorting.ORTHOGRAPHIC_Z);
            mv.setIdentity();
            mv.translate(0, 0, 1000 - ForgeHooksClient.getGuiFarPlane());
            RenderSystem.applyModelViewMatrix();
            Lighting.setupFor3DItems();
            GuiGraphics sg = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            try {
                e.screen.render(sg, -10000, -10000, mc.getFrameTime()); // the mouse far away: read-only, no hover
                sg.flush();
            } catch (Throwable t) {
                tidyAfterFailure(mc); // half-drawn shapes go into this picture, not the host's
                throw t;
            }
        } finally {
            building = null;
            painting = false;
            mc.player.containerMenu = playerMenu; // some screens (the recipe book) take it over
            fbW.setInt(win, sFbW);
            fbH.setInt(win, sFbH);
            scrW.setInt(win, sScrW);
            scrH.setInt(win, sScrH);
            guiW.setInt(win, sGuiW);
            guiH.setInt(win, sGuiH);
            guiScale.setDouble(win, sScale);
            RenderSystem.disableScissor();
            while (mv.last() != outer && !mv.clear()) {
                mv.popPose(); // ours, and any a failed screen left behind
            }
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            host.bindWrite(true);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
        }
    }

    /** The panel and its tabs, fitted into the box keeping their shape. */
    private static void blit(GuiGraphics g, RealScreens.Entry e, int[] b, int iw, int ih, int x, int y, int w, int h) {
        float k = Math.min(w / (float) b[2], h / (float) b[3]);
        float dw = b[2] * k, dh = b[3] * k, dx = x + (w - dw) / 2, dy = y + (h - dh) / 2;
        float u0 = b[0] / (float) iw, u1 = (b[0] + b[2]) / (float) iw, v0 = 1 - b[1] / (float) ih, v1 = 1 - (b[1] + b[3]) / (float) ih;
        RenderSystem.setShaderTexture(0, e.target.getColorTextureId());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bb.vertex(m, dx, dy, 0).uv(u0, v0).endVertex();
        bb.vertex(m, dx, dy + dh, 0).uv(u0, v1).endVertex();
        bb.vertex(m, dx + dw, dy + dh, 0).uv(u1, v1).endVertex();
        bb.vertex(m, dx + dw, dy, 0).uv(u1, v0).endVertex();
        BufferUploader.drawWithShader(bb.end());
    }

    private static final int MARGIN_X = 64, MARGIN_Y = 16;

    /** The panel and every visible widget beside it (Mekanism's tabs); CoFH draws its panels without widgets, so they get room of their own. */
    private static int[] shown(AbstractContainerScreen<?> screen, int iw, int ih) {
        int x0 = screen.getGuiLeft(), y0 = screen.getGuiTop(), x1 = x0 + screen.getXSize(), y1 = y0 + screen.getYSize();
        for (var l : screen.children()) {
            if (l instanceof net.minecraft.client.gui.components.AbstractWidget a && a.visible && a.getWidth() > 0 && a.getHeight() > 0) {
                x0 = Math.min(x0, a.getX());
                y0 = Math.min(y0, a.getY());
                x1 = Math.max(x1, a.getX() + a.getWidth());
                y1 = Math.max(y1, a.getY() + a.getHeight());
            }
        }
        if (screen.getClass().getName().startsWith("cofh.")) {
            x0 = Math.min(x0, screen.getGuiLeft() - 24);
            x1 = Math.max(x1, screen.getGuiLeft() + screen.getXSize() + 24);
        }
        x0 = Math.max(0, x0);
        y0 = Math.max(0, y0);
        x1 = Math.min(iw, x1);
        y1 = Math.min(ih, y1);
        return new int[] {x0, y0, Math.max(1, x1 - x0), Math.max(1, y1 - y0)};
    }

    private static void dispose(Entry e) {
        if (e.target != null) {
            e.target.destroyBuffers();
            e.target = null;
        }
        e.screen = null; // never closed or removed: that would close the player's real menu
        e.container = null;
        e.standIn = null;
    }

    private static long sweptMs;

    /** Each client tick: frees the pictures of boxes not drawn for ten seconds (the Holo Grid closed, or the box went). */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END && !ENTRIES.isEmpty()) {
            sweep();
        }
    }

    /** A client world going away (a dimension change, a server switch): its pictures and stand-ins go with it. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onLevelUnload(net.minecraftforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ENTRIES.values().forEach(RealScreens::dispose);
            ENTRIES.clear();
        }
    }

    private static void sweep() {
        long now = System.currentTimeMillis();
        if (now - sweptMs < 2000) {
            return;
        }
        sweptMs = now;
        ENTRIES.entrySet().removeIf(en -> {
            if (now - en.getValue().drawnMs > 10_000L) {
                dispose(en.getValue());
                return true;
            }
            return false;
        });
    }

    /** Leaving the world: everything freed, failed kinds tried again next time. */
    public static void clear() {
        ENTRIES.values().forEach(RealScreens::dispose);
        ENTRIES.clear();
        BROKEN.clear();
    }
}
