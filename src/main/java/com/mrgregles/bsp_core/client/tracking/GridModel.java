package com.mrgregles.bsp_core.client.tracking;

import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.TrackerActionPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * The client's working copy of the player's Holo Grid layout (pages of boxes, presets, the pinned box), read from the server's view and
 * sent back whole after each finished change (the server cuts it to the rules, {@code GridLayout.clean}). Also holds which page is shown,
 * which the page keys, the Holo Grid and the popout share.
 */
public final class GridModel {
    public static final int MAX_PAGES = 9, MAX_PRESETS = 10, MAX_BOXES = 64, MIN_COLS = 4, MAX_COLS = 32, MIN_ROWS = 3, MAX_ROWS = 24;

    public static final class Box {
        public int x, y, w, h;
        public String src = "";

        public Box(int x, int y, int w, int h, String src) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.src = src;
        }

        boolean overlaps(Box o) {
            return x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h;
        }

        /** The tag id when the box shows a tagged machine, else null. */
        public String tagId() {
            return src.startsWith("tag:") ? src.substring(4) : null;
        }
    }

    public static final class Page {
        public String name;
        public int cols = 12, rows = 8;
        public final List<Box> boxes = new ArrayList<>();

        public Page(String name) {
            this.name = name;
        }

        public boolean free(Box b, Box ignore) {
            if (b.x < 0 || b.y < 0 || b.x + b.w > cols || b.y + b.h > rows) {
                return false;
            }
            for (Box o : boxes) {
                if (o != ignore && o.overlaps(b)) {
                    return false;
                }
            }
            return true;
        }

        /** After the grid shrank: boxes cut to fit, those that no longer fit at all removed. */
        public void fit() {
            boxes.removeIf(b -> b.x >= cols || b.y >= rows);
            boxes.forEach(b -> {
                b.w = Math.min(b.w, cols - b.x);
                b.h = Math.min(b.h, rows - b.y);
            });
        }
    }

    public static final class Preset {
        public String name;
        public long saved;
        public final List<Page> pages = new ArrayList<>();
    }

    public static final List<Page> PAGES = new ArrayList<>();
    public static final List<Preset> PRESETS = new ArrayList<>();
    /** The pinned box: page and box index, corner (0 top left, 1 top right, 2 bottom left, 3 bottom right), size (0 small, 1 medium). */
    public static int pinPage, pinBox, pinCorner = 1, pinSize;
    public static boolean pinOn;
    /** The page shown in the Holo Grid and the popout. */
    public static int current;
    private static CompoundTag loaded, sent;
    private static long savedMs;

    private GridModel() {}

    /** Reads the layout from the server's view when it changed there (not while the player is mid-edit of the same layout). */
    public static void refresh() {
        CompoundTag layout = ClientTracking.view().getCompound("layout");
        if (layout.equals(loaded) && !PAGES.isEmpty()) {
            return;
        }
        if (sent != null && layout.equals(sent) && !PAGES.isEmpty()) {
            loaded = layout.copy(); // the server's echo of what this client just saved: the working copy (and what is picked in it) stays
            sent = null;
            return;
        }
        if (sent != null && System.currentTimeMillis() - savedMs < 2000 && !PAGES.isEmpty()) {
            return; // an echo of an earlier save while a later one is on its way: the working copy is newer
        }
        loaded = layout.copy();
        PAGES.clear();
        PAGES.addAll(readPages(layout.getList("pages", Tag.TAG_COMPOUND)));
        if (PAGES.isEmpty()) {
            PAGES.add(new Page("Page 1"));
        }
        PRESETS.clear();
        ListTag presets = layout.getList("presets", Tag.TAG_COMPOUND);
        for (int i = 0; i < presets.size(); i++) {
            CompoundTag p = presets.getCompound(i);
            Preset q = new Preset();
            q.name = p.getString("name");
            q.saved = p.getLong("saved");
            q.pages.addAll(readPages(p.getList("pages", Tag.TAG_COMPOUND)));
            PRESETS.add(q);
        }
        CompoundTag pin = layout.getCompound("pin");
        pinPage = pin.getInt("page");
        pinBox = pin.getInt("box");
        pinCorner = pin.contains("corner") ? pin.getInt("corner") : 1;
        pinSize = pin.getInt("size");
        pinOn = pin.getBoolean("on");
        current = Math.max(0, Math.min(current, PAGES.size() - 1));
    }

    private static List<Page> readPages(ListTag list) {
        List<Page> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag p = list.getCompound(i);
            Page page = new Page(p.getString("name"));
            page.cols = Math.max(MIN_COLS, Math.min(MAX_COLS, p.contains("cols") ? p.getInt("cols") : 12));
            page.rows = Math.max(MIN_ROWS, Math.min(MAX_ROWS, p.contains("rows") ? p.getInt("rows") : 8));
            ListTag boxes = p.getList("boxes", Tag.TAG_COMPOUND);
            for (int b = 0; b < boxes.size(); b++) {
                CompoundTag c = boxes.getCompound(b);
                page.boxes.add(new Box(c.getInt("x"), c.getInt("y"), c.getInt("w"), c.getInt("h"), c.getString("src")));
            }
            out.add(page);
        }
        return out;
    }

    private static ListTag writePages(List<Page> pages) {
        ListTag list = new ListTag();
        for (Page page : pages) {
            CompoundTag p = new CompoundTag();
            p.putString("name", page.name);
            p.putInt("cols", page.cols);
            p.putInt("rows", page.rows);
            ListTag boxes = new ListTag();
            for (Box b : page.boxes) {
                CompoundTag c = new CompoundTag();
                c.putInt("x", b.x);
                c.putInt("y", b.y);
                c.putInt("w", b.w);
                c.putInt("h", b.h);
                c.putString("src", b.src);
                boxes.add(c);
            }
            p.put("boxes", boxes);
            list.add(p);
        }
        return list;
    }

    public static CompoundTag toTag() {
        CompoundTag t = new CompoundTag();
        t.put("pages", writePages(PAGES));
        ListTag presets = new ListTag();
        for (Preset q : PRESETS) {
            CompoundTag p = new CompoundTag();
            p.putString("name", q.name);
            p.putLong("saved", q.saved);
            p.put("pages", writePages(q.pages));
            presets.add(p);
        }
        t.put("presets", presets);
        CompoundTag pin = new CompoundTag();
        pin.putInt("page", pinPage);
        pin.putInt("box", pinBox);
        pin.putInt("corner", pinCorner);
        pin.putInt("size", pinSize);
        pin.putBoolean("on", pinOn);
        t.put("pin", pin);
        return t;
    }

    /** Sends the layout to the server after a finished change. */
    public static void save() {
        CompoundTag t = toTag();
        TrackerActionPacket packet = new TrackerActionPacket(TrackerActionPacket.LAYOUT, "", "", t);
        if (BSPNetwork.sizeOf(packet, TrackerActionPacket::encode) > 30_000) {
            var p = net.minecraft.client.Minecraft.getInstance().player;
            if (p != null) {
                p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.bsp_core.tracking.layout_too_big").withStyle(net.minecraft.ChatFormatting.RED), true);
            }
            return; // a client packet over 32 KB would throw: kept here, not sent
        }
        sent = t.copy();
        savedMs = System.currentTimeMillis();
        loaded = null; // the server's answer is read again; when it cut something to its rules, it replaces the working copy
        BSPNetwork.CHANNEL.sendToServer(packet);
        WatchSet.update();
    }

    public static Page page() {
        if (PAGES.isEmpty()) {
            PAGES.add(new Page("Page 1"));
        }
        current = Math.max(0, Math.min(current, PAGES.size() - 1));
        return PAGES.get(current);
    }

    /** Shows another page (the page keys and tabs). */
    public static void show(int index) {
        if (index >= 0 && index < PAGES.size() && index != current) {
            current = index;
            WatchSet.update();
        }
    }

    public static void next(int step) {
        if (!PAGES.isEmpty()) {
            show(Math.floorMod(current + step, PAGES.size()));
        }
    }

    /** A deep copy of pages, for presets. */
    public static List<Page> copy(List<Page> pages) {
        return readPages(writePages(pages));
    }

    /** A box is about to be removed from a page: the pin keeps pointing at the same box (or goes, if it is that box). */
    public static void removing(Page page, Box box) {
        int pi = PAGES.indexOf(page), bi = page.boxes.indexOf(box);
        if (pi == pinPage && bi >= 0) {
            if (bi == pinBox) {
                pinOn = false;
            } else if (bi < pinBox) {
                pinBox--;
            }
        }
    }

    /** Forgets the layout (on leaving a server). */
    public static void clear() {
        PAGES.clear();
        PRESETS.clear();
        pinOn = false;
        loaded = null;
        sent = null;
        current = 0;
    }

    /** The pinned box, or null when none is pinned or it no longer exists. */
    public static Box pinned() {
        if (!pinOn || pinPage < 0 || pinPage >= PAGES.size()) {
            return null;
        }
        Page p = PAGES.get(pinPage);
        return pinBox >= 0 && pinBox < p.boxes.size() ? p.boxes.get(pinBox) : null;
    }
}
