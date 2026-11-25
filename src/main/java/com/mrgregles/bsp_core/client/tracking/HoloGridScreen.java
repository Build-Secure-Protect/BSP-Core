package com.mrgregles.bsp_core.client.tracking;

import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.TrackerActionPacket;
import com.mrgregles.bsp_core.tracking.TrackedTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.mrgregles.bsp_core.client.tracking.Hud.*;

/**
 * The Holo Grid, the owner's pick "Holo Table" (P1): a steel table filling the window. Along the top, nine round keys choose the page (a +
 * on the first free one adds a page) beside the page's name, which a click renames. On the left, a lever flips BUILD and VIEW and two
 * knurled knobs set the columns and rows (click a knob's left or right half, or scroll on it). On the right, the preset cartridges, SAVE,
 * POPOUT and PIN. Along the bottom, a drawer of tiles: the map, all tags, and each tagged machine.
 *
 * <p>BUILD: drag on empty cells to draw a box, click a box to pick it, drag it to move it, drag its bottom-right corner to resize it,
 * right-click (or Delete) to remove it; a tile from the drawer fills the picked box. Boxes never overlap. VIEW: the boxes show their
 * machines live; a box marked OPERATE opens the machine's own screen; the map can be dragged and zoomed; a pin key on a hovered box pins it
 * to the game screen. Every finished change is saved to the server.
 */
public class HoloGridScreen extends Screen {
    private static final int TOP = 22, LEFT = 36, RIGHT = 40, DRAWER = 48;

    private boolean build;
    private GridModel.Box picked;
    /** A drag in progress: 0 none, 1 drawing, 2 moving, 3 resizing, 4 panning a map. */
    private int drag;
    private int dragX0, dragY0, dragX1, dragY1, grabDX, grabDY;
    private GridModel.Box dragBox;
    private double lastMX, lastMY;
    private int drawerScroll, rackScroll;
    /** Overlays: 0 none, 1 presets, 2 pin, 3 confirm loading a preset. */
    private int overlay;
    private int confirmPreset = -1;
    private GridModel.Box pinChoice;
    /** Text being typed: "page" (the page's name) or "preset" (a new preset's name), else null. */
    private String typing;
    private String buffer = "";

    public HoloGridScreen() {
        super(tr("gui.bsp_core.tracking.holo"));
    }

    public static void open() {
        GridModel.refresh();
        Minecraft.getInstance().setScreen(new HoloGridScreen());
    }

    @Override
    protected void init() {
        GridModel.refresh();
        WatchSet.update();
    }

    @Override
    public void removed() {
        super.removed();
        JourneyMapView.endDrags(JourneyMapView.Host.SCREEN);
        WatchSet.updateLater(); // the grid's page is no longer watched (unless the popout shows it): once the screen has changed
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int gx() {
        return LEFT + 4;
    }

    private int gy() {
        return TOP + 3;
    }

    private int gw() {
        return width - LEFT - RIGHT - 8;
    }

    private int gh() {
        return height - TOP - DRAWER - 7;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        rect(g, 0, 0, width, height, 0xFF20252E);
        rect(g, 0, 0, width, 1, 0xFF3A414E);
        screw(g, 6, 6);
        screw(g, width - 7, 6);
        screw(g, 6, height - 7);
        screw(g, width - 7, height - 7);
        pageKeys(g, mx, my);
        lever(g, mx, my);
        rack(g, mx, my);
        drawer(g, mx, my);
        // the bezel round the grid
        rect(g, gx() - 4, gy() - 4, gw() + 8, gh() + 8, BLACK);
        rect(g, gx() - 3, gy() - 3, gw() + 6, gh() + 6, STEEL);
        rect(g, gx() - 3, gy() - 3, gw() + 6, 1, STEEL_L);
        rect(g, gx(), gy(), gw(), gh(), 0xFF0A0E13);
        if (picked != null && !GridModel.page().boxes.contains(picked)) {
            picked = null; // the page changed (a page key, the popout's rail) or the box went
        }
        if (build) {
            buildGrid(g, mx, my);
        } else {
            viewGrid(g, mx, my);
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 400); // over the boxes' item icons
        if (overlay == 1 || overlay == 3) {
            presets(g, mx, my);
        } else if (overlay == 2) {
            pinPicker(g, mx, my);
        }
        g.pose().popPose();
    }

    private void pageKeys(GuiGraphics g, int mx, int my) {
        small(g, tr("gui.bsp_core.tracking.page").getString(), 14, 9, MUTED);
        int n = GridModel.PAGES.size();
        for (int i = 0; i < 9; i++) {
            int cx = 44 + i * 16;
            boolean has = i < n;
            roundKey(g, cx, 11, 6, has ? String.valueOf(i + 1) : i == n ? "+" : "", has && i == GridModel.current, has || i == n);
        }
        int nx = 44 + 9 * 16;
        if ("page".equals(typing)) {
            well(g, nx, 4, 120, 14, 0xFF071A17);
            outline(g, nx, 4, 120, 14, TQ);
            text(g, fit(buffer + ((System.currentTimeMillis() / 400) % 2 == 0 ? "_" : ""), 114), nx + 3, 7, FG);
        } else {
            String name = GridModel.page().name.isEmpty() ? tr("gui.bsp_core.tracking.page_n", GridModel.current + 1).getString() : GridModel.page().name;
            text(g, fit(name.toUpperCase(), width - nx - 60), nx, 7, TQ);
        }
        if (Popout.isOpen()) {
            small(g, tr("gui.bsp_core.tracking.popout_open").getString(), width - 6 - smallWidth(tr("gui.bsp_core.tracking.popout_open").getString()), 9, GOLD);
        }
    }

    private void lever(GuiGraphics g, int mx, int my) {
        small(g, tr("gui.bsp_core.tracking.mode").getString(), 8, 26, MUTED);
        well(g, 8, 36, 22, 44, 0xFF0B0F14);
        int ly = build ? 40 : 64;
        rect(g, 11, ly, 16, 12, 0xFF8A909C);
        rect(g, 11, ly, 16, 2, 0xFFC9CED8);
        rect(g, 11, ly + 10, 16, 2, 0xFF4A505C);
        String b = tr("gui.bsp_core.tracking.build").getString(), v = tr("gui.bsp_core.tracking.view").getString();
        small(g, b, 19 - smallWidth(b) / 2, 83, build ? TQ : OFF);
        small(g, v, 19 - smallWidth(v) / 2, 90, build ? OFF : TQ);
        GridModel.Page p = GridModel.page();
        small(g, tr("gui.bsp_core.tracking.cols").getString(), 8, 104, MUTED);
        knob(g, 19, 122, String.valueOf(p.cols));
        small(g, tr("gui.bsp_core.tracking.rows").getString(), 8, 140, MUTED);
        knob(g, 19, 158, String.valueOf(p.rows));
    }

    private int rackSlots() {
        return Math.max(1, (height - TOP - DRAWER - 100) / 24);
    }

    private void rack(GuiGraphics g, int mx, int my) {
        int x = width - RIGHT + 4;
        small(g, tr("gui.bsp_core.tracking.presets").getString(), x, 26, MUTED);
        for (int i = 0; i < rackSlots(); i++) {
            int k = i + rackScroll, y = 36 + i * 24;
            if (k >= GridModel.PRESETS.size()) {
                break;
            }
            cartridge(g, x, y, 30, 21, GridModel.PRESETS.get(k), false);
        }
        int ky = 36 + rackSlots() * 24 + 4;
        key(g, x - 1, ky, 32, 14, tr("gui.bsp_core.tracking.save").getString(), overlay == 1, in(mx, my, x - 1, ky, 32, 14), TQ);
        key(g, x - 1, ky + 18, 32, 14, tr("gui.bsp_core.tracking.popout").getString(), Popout.isOpen(), in(mx, my, x - 1, ky + 18, 32, 14), TQ);
        key(g, x - 1, ky + 36, 32, 14, tr("gui.bsp_core.tracking.pin").getString(), overlay == 2, in(mx, my, x - 1, ky + 36, 32, 14), TQ);
    }

    private static void cartridge(GuiGraphics g, int x, int y, int w, int h, GridModel.Preset p, boolean big) {
        rect(g, x, y, w, h, BLACK);
        rect(g, x + 1, y + 1, w - 2, h - 2, 0xFF323946);
        rect(g, x + 1, y + 1, w - 2, 3, STEEL_L);
        if (!p.pages.isEmpty()) {
            mini(g, x + 3, y + 6, w - 6, h - 9, p.pages.get(0), TQ_D);
        }
    }

    private static void mini(GuiGraphics g, int x, int y, int w, int h, GridModel.Page page, int col) {
        rect(g, x, y, w, h, 0xFF070A0E);
        for (GridModel.Box b : page.boxes) {
            int bx = x + b.x * w / page.cols, by = y + b.y * h / page.rows, bw = Math.max(1, b.w * w / page.cols - 1), bh = Math.max(1, b.h * h / page.rows - 1);
            rect(g, bx, by, bw, bh, b.src.equals("map") ? 0xFF2C5A34 : Hud.darken(col, 0.6f));
        }
    }

    private List<String> tiles() {
        List<String> out = new ArrayList<>(List.of("map", "all"));
        ListTag tags = ClientTracking.view().getList("tags", Tag.TAG_COMPOUND);
        for (int i = 0; i < tags.size(); i++) {
            out.add("tag:" + tags.getCompound(i).getString("id"));
        }
        return out;
    }

    private int drawerVisible() {
        return Math.max(1, (width - LEFT - RIGHT - 30) / 48);
    }

    private void drawer(GuiGraphics g, int mx, int my) {
        int y = height - DRAWER + 2;
        rect(g, LEFT, y - 2, width - LEFT - RIGHT, DRAWER - 2, BLACK);
        rect(g, LEFT + 1, y - 1, width - LEFT - RIGHT - 2, DRAWER - 4, 0xFF151A21);
        rect(g, LEFT + 1, y - 1, width - LEFT - RIGHT - 2, 1, 0xFF3A414E);
        boolean assigning = build && picked != null;
        small(g, tr(assigning ? "gui.bsp_core.tracking.drawer_pick" : "gui.bsp_core.tracking.drawer").getString(), LEFT + 6, y + 1, assigning ? TQ : MUTED);
        List<String> tiles = tiles();
        drawerScroll = Math.max(0, Math.min(drawerScroll, Math.max(0, tiles.size() - drawerVisible())));
        for (int i = 0; i < drawerVisible() && i + drawerScroll < tiles.size(); i++) {
            String src = tiles.get(i + drawerScroll);
            int tx = LEFT + 6 + i * 48, ty = y + 10;
            boolean hover = in(mx, my, tx, ty, 46, 30);
            rect(g, tx, ty, 46, 30, BLACK);
            rect(g, tx + 1, ty + 1, 44, 28, hover ? 0xFF26323A : assigning ? 0xFF18232A : 0xFF1C222C);
            rect(g, tx + 1, ty + 1, 44, 1, 0xFF3A414E);
            String label;
            if (src.equals("map")) {
                rect(g, tx + 3, ty + 4, 14, 14, 0xFF2C5A34);
                rect(g, tx + 6, ty + 8, 6, 3, 0xFF3A6AC0);
                label = tr("gui.bsp_core.tracking.map").getString();
            } else if (src.equals("all")) {
                for (int j = 0; j < 3; j++) {
                    rect(g, tx + 3, ty + 5 + j * 5, 14, 3, TQ_D);
                }
                label = tr("gui.bsp_core.tracking.all_tags").getString();
            } else {
                CompoundTag t = GridView.tag(src.substring(4));
                label = t == null ? "?" : tagName(t);
                var block = t == null ? null : net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(net.minecraft.resources.ResourceLocation.tryParse(t.getString("block")));
                if (block != null) {
                    g.renderItem(new net.minecraft.world.item.ItemStack(block.asItem()), tx + 2, ty + 3);
                }
                CompoundTag r = t == null ? new CompoundTag() : ClientTracking.reading(t.getString("id"));
                lamp(g, tx + 40, ty + 24, 2, r.getByte("st") == 0 ? TQ : OFF, true);
            }
            small(g, fitSmall(label, 26), tx + 19, ty + 4, FG);
            if (!src.equals("map") && !src.equals("all")) {
                CompoundTag t = GridView.tag(src.substring(4));
                if (t != null && !t.getString("server").isEmpty()) {
                    small(g, fitSmall(t.getString("server"), 26), tx + 19, ty + 12, OFF);
                }
            }
        }
        if (tiles.size() > drawerVisible()) {
            key(g, width - RIGHT - 14, y + 10, 10, 30, ">", false, in(mx, my, width - RIGHT - 14, y + 10, 10, 30), TQ);
        }
    }

    private int[] cellAt(double mx, double my) {
        GridModel.Page p = GridModel.page();
        int cx = (int) Math.floor((mx - gx()) / (gw() / (double) p.cols)), cy = (int) Math.floor((my - gy()) / (gh() / (double) p.rows));
        return new int[] {Math.max(0, Math.min(p.cols - 1, cx)), Math.max(0, Math.min(p.rows - 1, cy))};
    }

    private int[] cellRect(GridModel.Box b) {
        GridModel.Page p = GridModel.page();
        double cw = gw() / (double) p.cols, ch = gh() / (double) p.rows;
        int x = gx() + (int) Math.round(b.x * cw), y = gy() + (int) Math.round(b.y * ch);
        return new int[] {x + 1, y + 1, gx() + (int) Math.round((b.x + b.w) * cw) - x - 2, gy() + (int) Math.round((b.y + b.h) * ch) - y - 2};
    }

    private void buildGrid(GuiGraphics g, int mx, int my) {
        GridModel.Page p = GridModel.page();
        double cw = gw() / (double) p.cols, ch = gh() / (double) p.rows;
        for (int i = 0; i <= p.cols; i++) {
            rect(g, gx() + (int) Math.round(i * cw), gy(), 1, gh(), 0x1219D3B0);
        }
        for (int j = 0; j <= p.rows; j++) {
            rect(g, gx(), gy() + (int) Math.round(j * ch), gw(), 1, 0x1219D3B0);
        }
        for (int i = 0; i <= p.cols; i++) {
            for (int j = 0; j <= p.rows; j++) {
                rect(g, gx() + (int) Math.round(i * cw), gy() + (int) Math.round(j * ch), 1, 1, 0x8C19D3B0);
            }
        }
        for (GridModel.Box b : p.boxes) {
            int[] r = cellRect(b);
            boolean sel = b == picked;
            rect(g, r[0], r[1], r[2], r[3], 0xEB10151C);
            outline(g, r[0], r[1], r[2], r[3], sel ? TQ : 0xFF3D5A5A);
            String label = boxLabel(b);
            small(g, fitSmall(label, r[2] - 6), r[0] + r[2] / 2f - Math.min(smallWidth(label), r[2] - 6) / 2, r[1] + r[3] / 2f - 3, b.src.isEmpty() ? MUTED : FG);
            String size = b.w + " × " + b.h;
            small(g, size, r[0] + r[2] - 3 - smallWidth(size), r[1] + r[3] - 8, sel ? TQ : OFF);
            if (sel) {
                rect(g, r[0] + r[2] - 4, r[1] + r[3] - 4, 4, 4, TQ);
            }
        }
        if (drag == 1) {
            GridModel.Box nb = new GridModel.Box(Math.min(dragX0, dragX1), Math.min(dragY0, dragY1), Math.abs(dragX1 - dragX0) + 1, Math.abs(dragY1 - dragY0) + 1, "");
            boolean ok = p.free(nb, null);
            int[] r = cellRect(nb);
            rect(g, r[0], r[1], r[2], r[3], ok ? 0x1F19D3B0 : 0x2ED63B2F);
            outline(g, r[0], r[1], r[2], r[3], ok ? TQ : BAD);
            String s = ok ? nb.w + " × " + nb.h : tr("gui.bsp_core.tracking.no_overlap").getString();
            small(g, s, r[0] + r[2] / 2f - smallWidth(s) / 2, r[1] + r[3] / 2f - 3, ok ? TQ : BAD);
        }
        String hint = tr("gui.bsp_core.tracking.build_hint").getString();
        small(g, fitSmall(hint, gw() - 8), gx() + 4, gy() + gh() - 9, 0xFF3D5A5A);
    }

    private String boxLabel(GridModel.Box b) {
        if (b.src.equals("map")) {
            return tr("gui.bsp_core.tracking.map").getString();
        }
        if (b.src.equals("all")) {
            return tr("gui.bsp_core.tracking.all_tags").getString();
        }
        CompoundTag t = b.tagId() == null ? null : GridView.tag(b.tagId());
        if (t != null) {
            return tagName(t);
        }
        return tr(b.src.isEmpty() ? "gui.bsp_core.tracking.box_empty" : "gui.bsp_core.tracking.tag_again").getString();
    }

    private void viewGrid(GuiGraphics g, int mx, int my) {
        List<GridView.Placed> placed = GridView.layout(GridModel.page(), gx(), gy(), gw(), gh(), false);
        for (GridView.Placed p : placed) {
            GridView.drawBox(g, p, mx, my, overlay == 0);
            if (overlay == 0 && p.contains(mx, my) && !p.box().src.isEmpty()) {
                int kx = p.x() + p.w() - 18, ky = p.y() + p.h() - 12;
                key(g, kx, ky, 16, 10, "", false, in(mx, my, kx, ky, 16, 10), TQ);
                rect(g, kx + 6, ky + 2, 4, 1, FG);
                rect(g, kx + 5, ky + 3, 6, 3, FG);
                rect(g, kx + 7, ky + 6, 2, 3, FG);
            }
        }
        if (GridModel.page().boxes.isEmpty()) {
            String s = tr("gui.bsp_core.tracking.empty_page").getString();
            text(g, s, gx() + gw() / 2 - font().width(s) / 2, gy() + gh() / 2 - 4, MUTED);
        }
    }

    private void presets(GuiGraphics g, int mx, int my) {
        rect(g, gx(), gy(), gw(), gh(), 0xEE06090C);
        text(g, tr("gui.bsp_core.tracking.preset_rack").getString(), gx() + 8, gy() + 5, TQ);
        String count = GridModel.PRESETS.size() + " / " + GridModel.MAX_PRESETS;
        small(g, count, gx() + gw() - 8 - smallWidth(count), gy() + 6, MUTED);
        int per = Math.max(1, (gw() - 16) / 82);
        for (int i = 0; i < GridModel.PRESETS.size(); i++) {
            int col = i % per, row = i / per, x = gx() + 8 + col * 82, y = gy() + 20 + row * 112;
            if (y + 110 > gy() + gh() - 22) {
                break;
            }
            GridModel.Preset p = GridModel.PRESETS.get(i);
            rect(g, x, y, 74, 108, BLACK);
            rect(g, x + 1, y + 1, 72, 106, 0xFF2B313C);
            rect(g, x + 1, y + 1, 72, 8, STEEL_L);
            if (!p.pages.isEmpty()) {
                mini(g, x + 6, y + 13, 62, 40, p.pages.get(0), TQ);
            }
            well(g, x + 6, y + 57, 62, 14, 0xFFECE6D6);
            small(g, fitSmall(p.name, 58), x + 37 - Math.min(smallWidth(p.name), 58) / 2, y + 61, 0xFF26201A);
            String pages = tr("gui.bsp_core.tracking.preset_pages", p.pages.size()).getString();
            small(g, pages, x + 37 - smallWidth(pages) / 2, y + 74, MUTED);
            key(g, x + 6, y + 84, 30, 10, tr("gui.bsp_core.tracking.load").getString(), confirmPreset == i, in(mx, my, x + 6, y + 84, 30, 10), TQ);
            key(g, x + 38, y + 84, 30, 10, tr("gui.bsp_core.tracking.save_over").getString(), false, in(mx, my, x + 38, y + 84, 30, 10), TQ);
            key(g, x + 6, y + 96, 62, 9, tr("gui.bsp_core.tracking.delete").getString(), false, in(mx, my, x + 6, y + 96, 62, 9), BAD);
        }
        int by = gy() + gh() - 18;
        if ("preset".equals(typing)) {
            well(g, gx() + 8, by, 140, 14, 0xFF071A17);
            outline(g, gx() + 8, by, 140, 14, TQ);
            text(g, fit(buffer + ((System.currentTimeMillis() / 400) % 2 == 0 ? "_" : ""), 134), gx() + 11, by + 3, FG);
            key(g, gx() + 152, by, 50, 14, tr("gui.bsp_core.tracking.save").getString(), true, in(mx, my, gx() + 152, by, 50, 14), TQ);
        } else if (GridModel.PRESETS.size() < GridModel.MAX_PRESETS) {
            key(g, gx() + 8, by, 110, 14, tr("gui.bsp_core.tracking.new_preset").getString(), true, in(mx, my, gx() + 8, by, 110, 14), TQ);
        }
        key(g, gx() + gw() - 58, by, 50, 14, tr("gui.bsp_core.tracking.close").getString(), false, in(mx, my, gx() + gw() - 58, by, 50, 14), TQ);
        if (overlay == 3 && confirmPreset >= 0) {
            int cx = gx() + gw() / 2 - 110, cy = gy() + gh() / 2 - 30;
            rect(g, cx, cy, 220, 60, BLACK);
            rect(g, cx + 1, cy + 1, 218, 58, BG);
            outline(g, cx, cy, 220, 60, TQ);
            String q = tr("gui.bsp_core.tracking.load_confirm").getString();
            small(g, fitSmall(q, 210), cx + 110 - Math.min(smallWidth(q), 210) / 2, cy + 12, FG);
            key(g, cx + 30, cy + 34, 70, 14, tr("gui.bsp_core.tracking.load").getString(), true, in(mx, my, cx + 30, cy + 34, 70, 14), TQ);
            key(g, cx + 120, cy + 34, 70, 14, tr("gui.bsp_core.tracking.cancel").getString(), false, in(mx, my, cx + 120, cy + 34, 70, 14), TQ);
        }
    }

    private void pinPicker(GuiGraphics g, int mx, int my) {
        rect(g, gx(), gy(), gw(), gh(), 0xEE06090C);
        text(g, tr("gui.bsp_core.tracking.pin_title").getString(), gx() + 8, gy() + 5, TQ);
        GridModel.Box b = pinChoice != null ? pinChoice : GridModel.pinned();
        String which = b == null ? tr("gui.bsp_core.tracking.pin_none").getString() : boxLabel(b);
        small(g, fitSmall(which, gw() - 16), gx() + 8, gy() + 17, MUTED);
        int sx = gx() + 12, sy = gy() + 30, sw = Math.min(200, gw() - 120), sh = sw * 9 / 16;
        rect(g, sx - 4, sy - 4, sw + 8, sh + 8, BLACK);
        rect(g, sx - 3, sy - 3, sw + 6, sh + 6, STEEL);
        well(g, sx, sy, sw, sh, 0xFF203A28);
        for (int i = 0; i < 4; i++) {
            int pw = GridModel.pinSize == 1 ? sw / 2 : sw * 3 / 8, ph = pw * 2 / 3;
            int px = i % 2 == 1 ? sx + sw - pw - 4 : sx + 4, py = i >= 2 ? sy + sh - ph - 4 : sy + 4;
            boolean on = GridModel.pinCorner == i;
            rect(g, px, py, pw, ph, on ? 0x8019D3B0 : 0x40101418);
            outline(g, px, py, pw, ph, on ? TQ : 0xFF5A6A5A);
        }
        int kx = sx + sw + 16;
        small(g, tr("gui.bsp_core.tracking.size").getString(), kx, sy, MUTED);
        key(g, kx, sy + 10, 60, 14, tr("gui.bsp_core.tracking.small").getString(), GridModel.pinSize == 0, in(mx, my, kx, sy + 10, 60, 14), TQ);
        key(g, kx, sy + 28, 60, 14, tr("gui.bsp_core.tracking.medium").getString(), GridModel.pinSize == 1, in(mx, my, kx, sy + 28, 60, 14), TQ);
        small(g, fitSmall(tr("gui.bsp_core.tracking.pin_hidden").getString(), gx() + gw() - kx - 6), kx, sy + 50, OFF);
        int by = gy() + gh() - 18;
        key(g, gx() + 8, by, 60, 14, tr("gui.bsp_core.tracking.unpin").getString(), false, in(mx, my, gx() + 8, by, 60, 14), TQ);
        key(g, gx() + 72, by, 60, 14, tr("gui.bsp_core.tracking.pin_it").getString(), b != null, in(mx, my, gx() + 72, by, 60, 14), TQ);
        key(g, gx() + gw() - 58, by, 50, 14, tr("gui.bsp_core.tracking.close").getString(), false, in(mx, my, gx() + gw() - 58, by, 50, 14), TQ);
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        lastMX = mx;
        lastMY = my;
        if (typing != null && !"preset".equals(typing)) {
            commitTyping();
        }
        if (overlay == 1 || overlay == 3) {
            return presetClick(mx, my);
        }
        if (overlay == 2) {
            return pinClick(mx, my);
        }
        // page keys and the page name
        int n = GridModel.PAGES.size();
        for (int i = 0; i < 9; i++) {
            if (Math.hypot(mx - (44 + i * 16), my - 11) <= 7) {
                if (i < n) {
                    GridModel.show(i);
                    picked = null;
                } else if (i == n) {
                    GridModel.PAGES.add(new GridModel.Page(tr("gui.bsp_core.tracking.page_n", n + 1).getString()));
                    GridModel.show(n);
                    picked = null;
                    GridModel.save();
                }
                return true;
            }
        }
        if (in(mx, my, 44 + 9 * 16, 4, 120, 14)) {
            typing = "page";
            buffer = GridModel.page().name;
            return true;
        }
        // lever and knobs
        if (in(mx, my, 8, 36, 22, 44)) {
            build = !build;
            picked = null;
            return true;
        }
        if (Math.hypot(mx - 19, my - 122) <= 10) {
            stepGrid(true, mx < 19 ? -1 : 1);
            return true;
        }
        if (Math.hypot(mx - 19, my - 158) <= 10) {
            stepGrid(false, mx < 19 ? -1 : 1);
            return true;
        }
        // rack
        int rx = width - RIGHT + 4;
        for (int i = 0; i < rackSlots(); i++) {
            if (i + rackScroll < GridModel.PRESETS.size() && in(mx, my, rx, 36 + i * 24, 30, 21)) {
                overlay = 1;
                return true;
            }
        }
        int ky = 36 + rackSlots() * 24 + 4;
        if (in(mx, my, rx - 1, ky, 32, 14)) {
            overlay = 1;
            return true;
        }
        if (in(mx, my, rx - 1, ky + 18, 32, 14)) {
            if (Popout.isOpen()) {
                Popout.close();
            } else {
                Popout.open();
            }
            return true;
        }
        if (in(mx, my, rx - 1, ky + 36, 32, 14)) {
            overlay = 2;
            pinChoice = build ? picked : null;
            return true;
        }
        // drawer
        if (drawerClick(mx, my)) {
            return true;
        }
        // the grid
        if (in(mx, my, gx(), gy(), gw(), gh())) {
            return build ? buildClick(mx, my, button) : viewClick(mx, my, button);
        }
        return super.mouseClicked(mx, my, button);
    }

    private void stepGrid(boolean cols, int step) {
        GridModel.Page p = GridModel.page();
        if (cols) {
            p.cols = Math.max(GridModel.MIN_COLS, Math.min(GridModel.MAX_COLS, p.cols + step));
        } else {
            p.rows = Math.max(GridModel.MIN_ROWS, Math.min(GridModel.MAX_ROWS, p.rows + step));
        }
        p.fit();
        GridModel.save();
    }

    private boolean drawerClick(double mx, double my) {
        int y = height - DRAWER + 2;
        List<String> tiles = tiles();
        if (tiles.size() > drawerVisible() && in(mx, my, width - RIGHT - 14, y + 10, 10, 30)) {
            drawerScroll = drawerScroll + drawerVisible() >= tiles.size() ? 0 : drawerScroll + 1;
            return true;
        }
        for (int i = 0; i < drawerVisible() && i + drawerScroll < tiles.size(); i++) {
            if (in(mx, my, LEFT + 6 + i * 48, y + 10, 46, 30)) {
                String src = tiles.get(i + drawerScroll);
                GridModel.Page p = GridModel.page();
                if ((picked == null || !build) && p.boxes.size() >= GridModel.MAX_BOXES) {
                    return true; // a page holds at most 64 boxes
                }
                if (picked == null || !build) {
                    // no box picked: a new one in the first free spot, a quarter of the grid where it fits
                    GridModel.Box nb = firstFree(p);
                    if (nb == null) {
                        return true;
                    }
                    p.boxes.add(nb);
                    picked = nb;
                    build = true;
                }
                picked.src = src;
                GridModel.save();
                return true;
            }
        }
        return false;
    }

    private static GridModel.Box firstFree(GridModel.Page p) {
        int w = Math.max(1, p.cols / 3), h = Math.max(1, p.rows / 3);
        for (int size = 0; size < 3; size++) {
            for (int y = 0; y + h <= p.rows; y++) {
                for (int x = 0; x + w <= p.cols; x++) {
                    GridModel.Box b = new GridModel.Box(x, y, w, h, "");
                    if (p.free(b, null)) {
                        return b;
                    }
                }
            }
            w = Math.max(1, w / 2);
            h = Math.max(1, h / 2);
        }
        return null;
    }

    private GridModel.Box boxAt(double mx, double my) {
        for (GridModel.Box b : GridModel.page().boxes) {
            int[] r = cellRect(b);
            if (in(mx, my, r[0], r[1], r[2], r[3])) {
                return b;
            }
        }
        return null;
    }

    private boolean buildClick(double mx, double my, int button) {
        GridModel.Box b = boxAt(mx, my);
        if (button == 1) {
            if (b != null) {
                GridModel.removing(GridModel.page(), b);
                GridModel.page().boxes.remove(b);
                if (picked == b) {
                    picked = null;
                }
                GridModel.save();
            }
            return true;
        }
        int[] c = cellAt(mx, my);
        if (b != null) {
            int[] r = cellRect(b);
            if (b == picked && mx >= r[0] + r[2] - 6 && my >= r[1] + r[3] - 6) {
                drag = 3;
            } else {
                drag = 2;
                grabDX = c[0] - b.x;
                grabDY = c[1] - b.y;
            }
            picked = b;
            dragBox = b;
            return true;
        }
        picked = null;
        drag = 1;
        dragX0 = dragX1 = c[0];
        dragY0 = dragY1 = c[1];
        return true;
    }

    private boolean viewClick(double mx, double my, int button) {
        for (GridView.Placed p : GridView.layout(GridModel.page(), gx(), gy(), gw(), gh(), false)) {
            if (!p.contains(mx, my)) {
                continue;
            }
            int kx = p.x() + p.w() - 18, ky = p.y() + p.h() - 12;
            if (!p.box().src.isEmpty() && in(mx, my, kx, ky, 16, 10)) {
                pinChoice = p.box();
                overlay = 2;
                return true;
            }
            if (p.box().src.equals("map")) {
                if (JourneyMapView.shown(JourneyMapView.Host.SCREEN, p.box())) {
                    JourneyMapView.mouseClicked(JourneyMapView.Host.SCREEN, p.box(), mx, my, button);
                    drag = 4; // JourneyMap pans on the drag and acts on the release
                    dragBox = p.box();
                } else if (!MapView.click(GridView.map(JourneyMapView.Host.SCREEN, p.box()), mx, my, button)) {
                    drag = 4;
                    dragBox = p.box();
                }
                return true;
            }
            String id = p.box().tagId();
            if (id != null && ClientTracking.reading(id).getBoolean("op")) {
                BSPNetwork.CHANNEL.sendToServer(new TrackerActionPacket(TrackerActionPacket.OPERATE, id, "", new CompoundTag()));
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        GridModel.Page p = GridModel.page();
        if (drag == 1) {
            int[] c = cellAt(mx, my);
            dragX1 = c[0];
            dragY1 = c[1];
        } else if (drag == 2 && dragBox != null) {
            int[] c = cellAt(mx, my);
            GridModel.Box nb = new GridModel.Box(Math.max(0, Math.min(p.cols - dragBox.w, c[0] - grabDX)), Math.max(0, Math.min(p.rows - dragBox.h, c[1] - grabDY)), dragBox.w, dragBox.h, dragBox.src);
            if (p.free(nb, dragBox)) {
                dragBox.x = nb.x;
                dragBox.y = nb.y;
            }
        } else if (drag == 3 && dragBox != null) {
            int[] c = cellAt(mx, my);
            GridModel.Box nb = new GridModel.Box(dragBox.x, dragBox.y, Math.max(1, c[0] - dragBox.x + 1), Math.max(1, c[1] - dragBox.y + 1), dragBox.src);
            if (p.free(nb, dragBox)) {
                dragBox.w = nb.w;
                dragBox.h = nb.h;
            }
        } else if (drag == 4 && dragBox != null) {
            if (JourneyMapView.shown(JourneyMapView.Host.SCREEN, dragBox)) {
                JourneyMapView.mouseDragged(JourneyMapView.Host.SCREEN, dragBox, mx, my, button, dx, dy);
            } else {
                MapView.drag(GridView.map(JourneyMapView.Host.SCREEN, dragBox), mx - lastMX, my - lastMY);
            }
        }
        lastMX = mx;
        lastMY = my;
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        GridModel.Page p = GridModel.page();
        if (drag == 1) {
            GridModel.Box nb = new GridModel.Box(Math.min(dragX0, dragX1), Math.min(dragY0, dragY1), Math.abs(dragX1 - dragX0) + 1, Math.abs(dragY1 - dragY0) + 1, "");
            if (p.free(nb, null) && p.boxes.size() < 64) {
                p.boxes.add(nb);
                picked = nb;
                GridModel.save();
            }
        } else if (drag == 2 || drag == 3) {
            GridModel.save();
        } else if (drag == 4 && dragBox != null && JourneyMapView.shown(JourneyMapView.Host.SCREEN, dragBox)) {
            JourneyMapView.mouseReleased(JourneyMapView.Host.SCREEN, dragBox, mx, my, button);
        }
        drag = 0;
        dragBox = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (Math.hypot(mx - 19, my - 122) <= 10) {
            stepGrid(true, delta > 0 ? 1 : -1);
            return true;
        }
        if (Math.hypot(mx - 19, my - 158) <= 10) {
            stepGrid(false, delta > 0 ? 1 : -1);
            return true;
        }
        if (my >= height - DRAWER) {
            drawerScroll = Math.max(0, drawerScroll - (int) Math.signum(delta));
            return true;
        }
        if (mx >= width - RIGHT) {
            rackScroll = Math.max(0, Math.min(Math.max(0, GridModel.PRESETS.size() - rackSlots()), rackScroll - (int) Math.signum(delta)));
            return true;
        }
        if (!build) {
            for (GridView.Placed p : GridView.layout(GridModel.page(), gx(), gy(), gw(), gh(), false)) {
                if (p.contains(mx, my) && p.box().src.equals("map")) {
                    if (!JourneyMapView.mouseScrolled(JourneyMapView.Host.SCREEN, p.box(), mx, my, delta)) {
                        MapView.scroll(GridView.map(JourneyMapView.Host.SCREEN, p.box()), delta);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private boolean presetClick(double mx, double my) {
        if (overlay == 3 && confirmPreset >= GridModel.PRESETS.size()) {
            overlay = 1; // the preset went (the server's copy came back) while the question was open
            confirmPreset = -1;
        }
        if (overlay == 3 && confirmPreset >= 0) {
            int cx = gx() + gw() / 2 - 110, cy = gy() + gh() / 2 - 30;
            if (in(mx, my, cx + 30, cy + 34, 70, 14)) {
                GridModel.Preset p = GridModel.PRESETS.get(confirmPreset);
                GridModel.pinOn = false; // the pinned box's page and place no longer mean the same box
                GridModel.PAGES.clear();
                GridModel.PAGES.addAll(GridModel.copy(p.pages));
                GridModel.current = 0;
                picked = null;
                GridModel.save();
                overlay = 1;
                confirmPreset = -1;
            } else if (in(mx, my, cx + 120, cy + 34, 70, 14)) {
                overlay = 1;
                confirmPreset = -1;
            }
            return true;
        }
        int per = Math.max(1, (gw() - 16) / 82);
        for (int i = 0; i < GridModel.PRESETS.size(); i++) {
            int col = i % per, row = i / per, x = gx() + 8 + col * 82, y = gy() + 20 + row * 112;
            if (in(mx, my, x + 6, y + 84, 30, 10)) {
                confirmPreset = i;
                overlay = 3;
                return true;
            }
            if (in(mx, my, x + 38, y + 84, 30, 10)) {
                GridModel.Preset p = GridModel.PRESETS.get(i);
                p.pages.clear();
                p.pages.addAll(GridModel.copy(GridModel.PAGES));
                p.saved = System.currentTimeMillis();
                GridModel.save();
                return true;
            }
            if (in(mx, my, x + 6, y + 96, 62, 9)) {
                GridModel.PRESETS.remove(i);
                GridModel.save();
                return true;
            }
        }
        int by = gy() + gh() - 18;
        if ("preset".equals(typing) && in(mx, my, gx() + 152, by, 50, 14)) {
            commitTyping();
            return true;
        }
        if (typing == null && GridModel.PRESETS.size() < GridModel.MAX_PRESETS && in(mx, my, gx() + 8, by, 110, 14)) {
            typing = "preset";
            buffer = "";
            return true;
        }
        if (in(mx, my, gx() + gw() - 58, by, 50, 14)) {
            overlay = 0;
            typing = null;
        }
        return true;
    }

    private boolean pinClick(double mx, double my) {
        int sx = gx() + 12, sy = gy() + 30, sw = Math.min(200, gw() - 120), sh = sw * 9 / 16;
        for (int i = 0; i < 4; i++) {
            int pw = GridModel.pinSize == 1 ? sw / 2 : sw * 3 / 8, ph = pw * 2 / 3;
            int px = i % 2 == 1 ? sx + sw - pw - 4 : sx + 4, py = i >= 2 ? sy + sh - ph - 4 : sy + 4;
            if (in(mx, my, px, py, pw, ph)) {
                GridModel.pinCorner = i;
                if (GridModel.pinOn) {
                    GridModel.save();
                }
                return true;
            }
        }
        int kx = sx + sw + 16;
        if (in(mx, my, kx, sy + 10, 60, 14) || in(mx, my, kx, sy + 28, 60, 14)) {
            GridModel.pinSize = in(mx, my, kx, sy + 10, 60, 14) ? 0 : 1;
            if (GridModel.pinOn) {
                GridModel.save();
            }
            return true;
        }
        int by = gy() + gh() - 18;
        if (in(mx, my, gx() + 8, by, 60, 14)) {
            GridModel.pinOn = false;
            GridModel.save();
            overlay = 0;
            return true;
        }
        if (in(mx, my, gx() + 72, by, 60, 14)) {
            GridModel.Box b = pinChoice != null ? pinChoice : GridModel.pinned();
            if (b != null) {
                for (int pi = 0; pi < GridModel.PAGES.size(); pi++) {
                    int bi = GridModel.PAGES.get(pi).boxes.indexOf(b);
                    if (bi >= 0) {
                        GridModel.pinPage = pi;
                        GridModel.pinBox = bi;
                        GridModel.pinOn = true;
                    }
                }
                GridModel.save();
            }
            overlay = 0;
            return true;
        }
        if (in(mx, my, gx() + gw() - 58, by, 50, 14)) {
            overlay = 0;
        }
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (typing != null && buffer.length() < TrackedTag.NAME_MAX && c >= ' ' && c != '§') {
            buffer += c;
            return true;
        }
        if (typing == null) {
            double[] m = mouse();
            GridModel.Box jm = !build && overlay == 0 ? JourneyMapView.typing(JourneyMapView.Host.SCREEN) : null;
            if (jm != null) {
                JourneyMapView.charTyped(JourneyMapView.Host.SCREEN, jm, m[0], m[1], c, modifiers); // its chat or search box
                return true;
            }
            GridModel.Box fb = !build && overlay == 0 ? GridView.mapTyping(JourneyMapView.Host.SCREEN, GridModel.page()) : null;
            if (fb != null) {
                MapView.charTyped(GridView.map(JourneyMapView.Host.SCREEN, fb), c); // BSP-Core's map's waypoint editor
                return true;
            }
            GridView.Placed map = mapAt(m[0], m[1]);
            if (map != null && JourneyMapView.charTyped(JourneyMapView.Host.SCREEN, map.box(), m[0], m[1], c, modifiers)) {
                return true;
            }
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        GridView.Placed map = mapAt(mx, my);
        if (map != null) {
            JourneyMapView.mouseMoved(JourneyMapView.Host.SCREEN, map.box(), mx, my);
        }
    }

    /** The map box drawn by JourneyMap under the mouse (VIEW, no overlay open), or null. */
    private GridView.Placed mapAt(double mx, double my) {
        if (build || overlay != 0) {
            return null;
        }
        for (GridView.Placed p : GridView.layout(GridModel.page(), gx(), gy(), gw(), gh(), false)) {
            if (p.contains(mx, my) && p.box().src.equals("map") && JourneyMapView.shown(JourneyMapView.Host.SCREEN, p.box())) {
                return p;
            }
        }
        return null;
    }

    /** The map box drawn by BSP-Core (JourneyMap's map not shown) under the mouse (VIEW, no overlay open), or null. */
    private GridView.Placed ownMapAt(double mx, double my) {
        if (build || overlay != 0) {
            return null;
        }
        for (GridView.Placed p : GridView.layout(GridModel.page(), gx(), gy(), gw(), gh(), false)) {
            if (p.contains(mx, my) && p.box().src.equals("map") && !JourneyMapView.shown(JourneyMapView.Host.SCREEN, p.box())) {
                return p;
            }
        }
        return null;
    }

    /** The mouse in this screen's GUI pixels. */
    private double[] mouse() {
        Minecraft mc = Minecraft.getInstance();
        return new double[] {mc.mouseHandler.xpos() * width / Math.max(1, mc.getWindow().getScreenWidth()),
                mc.mouseHandler.ypos() * height / Math.max(1, mc.getWindow().getScreenHeight())};
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (typing != null) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                commitTyping();
            } else if (key == GLFW.GLFW_KEY_ESCAPE) {
                typing = null;
            } else if (key == GLFW.GLFW_KEY_BACKSPACE && !buffer.isEmpty()) {
                buffer = buffer.substring(0, buffer.length() - 1);
            }
            return true;
        }
        // a JourneyMap popup in the map box, or its chat or search box being typed in: every key is for that (Esc too, but for search);
        // only in VIEW with no overlay of the Holo Grid's own open, where the map can be seen
        boolean mapsSeen = !build && overlay == 0;
        GridModel.Box jm = mapsSeen ? JourneyMapView.typing(JourneyMapView.Host.SCREEN) : null;
        if (jm != null && (key != GLFW.GLFW_KEY_ESCAPE || JourneyMapView.takesEsc(JourneyMapView.Host.SCREEN, jm))) {
            double[] m = mouse();
            JourneyMapView.keyPressed(JourneyMapView.Host.SCREEN, jm, m[0], m[1], key, scan, modifiers);
            return true;
        }
        GridModel.Box fb = mapsSeen ? GridView.mapTyping(JourneyMapView.Host.SCREEN, GridModel.page()) : null;
        if (fb != null) {
            double[] m = mouse();
            MapView.key(GridView.map(JourneyMapView.Host.SCREEN, fb), key, scan, modifiers, m[0], m[1]); // BSP-Core's map's waypoint editor or manager
            return true;
        }
        if (overlay != 0 && key == GLFW.GLFW_KEY_ESCAPE) {
            overlay = 0;
            confirmPreset = -1;
            return true;
        }
        if (build && picked != null && (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE)) {
            GridModel.removing(GridModel.page(), picked);
            GridModel.page().boxes.remove(picked);
            picked = null;
            GridModel.save();
            return true;
        }
        if (TrackerKeys.pageKey(key, scan)) {
            picked = null;
            return true;
        }
        if (key != GLFW.GLFW_KEY_ESCAPE) {
            // JourneyMap's own keys (map type, follow, a waypoint here, pan, zoom) for the map under the mouse
            double[] m = mouse();
            GridView.Placed map = mapAt(m[0], m[1]);
            if (map != null && JourneyMapView.keyPressed(JourneyMapView.Host.SCREEN, map.box(), m[0], m[1], key, scan, modifiers)) {
                return true;
            }
            GridView.Placed own = ownMapAt(m[0], m[1]);
            if (own != null && MapView.key(GridView.map(JourneyMapView.Host.SCREEN, own.box()), key, scan, modifiers, m[0], m[1])) {
                return true; // BSP-Core's map's keys (B, N, F, [, zoom, arrows)
            }
        }
        if (TrackerKeys.OPEN.matches(key, scan) || Minecraft.getInstance().options.keyInventory.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    private void commitTyping() {
        if ("page".equals(typing)) {
            GridModel.page().name = buffer.trim();
            GridModel.save();
        } else if ("preset".equals(typing) && GridModel.PRESETS.size() < GridModel.MAX_PRESETS) {
            GridModel.Preset p = new GridModel.Preset();
            p.name = buffer.trim().isEmpty() ? tr("gui.bsp_core.tracking.preset_n", GridModel.PRESETS.size() + 1).getString() : buffer.trim();
            p.saved = System.currentTimeMillis();
            p.pages.addAll(GridModel.copy(GridModel.PAGES));
            GridModel.PRESETS.add(p);
            GridModel.save();
        }
        typing = null;
    }
}
