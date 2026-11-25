package com.mrgregles.bsp_core.client.tracking;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mrgregles.bsp_core.client.tracking.Hud.*;

/**
 * Draws a Holo Grid page into any rectangle: the Holo Grid's VIEW mode, the popout and the pinned box. The grid stretches to fill the
 * rectangle; in the popout (owner decision MT25), when the rectangle is more than twice as wide or tall as the page's own shape, the boxes
 * re-stack in reading order instead, as a row or a column.
 */
public final class GridView {
    /** A box and where it is drawn. */
    public record Placed(GridModel.Box box, int x, int y, int w, int h) {
        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /** BSP-Core's map's view (when JourneyMap's map is not shown), kept per box and host: the same box can be in two hosts at two sizes. */
    @SuppressWarnings("unchecked")
    private static final Map<GridModel.Box, MapView.State>[] MAPS = new Map[] {new java.util.WeakHashMap<>(), new java.util.WeakHashMap<>(), new java.util.WeakHashMap<>()};

    private GridView() {}

    public static MapView.State map(JourneyMapView.Host host, GridModel.Box b) {
        return MAPS[host.ordinal()].computeIfAbsent(b, k -> new MapView.State());
    }

    /** The map box of this page whose BSP-Core map takes every key now (its waypoint editor or manager is open), or null. */
    public static GridModel.Box mapTyping(JourneyMapView.Host host, GridModel.Page page) {
        for (GridModel.Box b : page.boxes) {
            if (b.src.equals("map") && !JourneyMapView.shown(host, b) && MapView.takesKeys(map(host, b))) {
                return b;
            }
        }
        return null;
    }

    /** Where each box of the page goes in the rectangle; {@code flex} allows re-stacking (the popout). */
    public static List<Placed> layout(GridModel.Page page, int x, int y, int w, int h, boolean flex) {
        List<Placed> out = new ArrayList<>();
        double k = (w / (double) h) / (page.cols / (double) page.rows);
        if (!flex || (k <= 2 && k >= 0.5) || page.boxes.isEmpty()) {
            double cw = w / (double) page.cols, ch = h / (double) page.rows;
            for (GridModel.Box b : page.boxes) {
                int bx = x + (int) Math.round(b.x * cw), by = y + (int) Math.round(b.y * ch);
                int bw = x + (int) Math.round((b.x + b.w) * cw) - bx, bh = y + (int) Math.round((b.y + b.h) * ch) - by;
                out.add(new Placed(b, bx + 1, by + 1, bw - 2, bh - 2));
            }
            return out;
        }
        boolean tall = k < 0.5;
        List<GridModel.Box> order = new ArrayList<>(page.boxes);
        order.sort((a, b) -> a.y != b.y ? Integer.compare(a.y, b.y) : Integer.compare(a.x, b.x));
        double total = order.stream().mapToDouble(b -> Math.max(2, tall ? b.h : b.w)).sum(), pos = 0;
        for (GridModel.Box b : order) {
            double share = Math.max(2, tall ? b.h : b.w) / total;
            if (tall) {
                int by = y + (int) Math.round(pos * h), bh = (int) Math.round(share * h);
                out.add(new Placed(b, x + 1, by + 1, w - 2, bh - 2));
            } else {
                int bx = x + (int) Math.round(pos * w), bw = (int) Math.round(share * w);
                out.add(new Placed(b, bx + 1, y + 1, bw - 2, h - 2));
            }
            pos += share;
        }
        return out;
    }

    /** Draws one box's content. {@code tools}: the map's keys are shown (it can be used here). */
    public static void drawBox(GuiGraphics g, Placed p, int mx, int my, boolean tools) {
        GridModel.Box b = p.box();
        boolean hover = p.contains(mx, my);
        if (b.src.equals("map")) {
            rect(g, p.x(), p.y(), p.w(), p.h(), BLACK);
            // JourneyMap's own full map, drawn by JourneyMap; BSP-Core's map only without JourneyMap
            if (!JourneyMapView.draw(g, b, p.x(), p.y(), p.w(), p.h(), tools ? mx : -1, tools ? my : -1)) {
                MapView.draw(g, p.x(), p.y(), p.w(), p.h(), map(JourneyMapView.host(), b), tools, mx, my);
            }
            outline(g, p.x() - 1, p.y() - 1, p.w() + 2, p.h() + 2, hover && tools ? TQ : DIM);
            return;
        }
        if (b.src.equals("all")) {
            allTags(g, p.x(), p.y(), p.w(), p.h());
            outline(g, p.x() - 1, p.y() - 1, p.w() + 2, p.h() + 2, DIM);
            return;
        }
        String id = b.tagId();
        CompoundTag tag = id == null ? null : tag(id);
        if (tag == null) {
            rect(g, p.x(), p.y(), p.w(), p.h(), 0xC010151C);
            outline(g, p.x(), p.y(), p.w(), p.h(), DIM);
            String s = tr(id == null ? "gui.bsp_core.tracking.box_empty" : "gui.bsp_core.tracking.tag_again").getString();
            small(g, fitSmall(s, p.w() - 6), p.x() + p.w() / 2f - Math.min(smallWidth(s), p.w() - 6) / 2, p.y() + p.h() / 2f - 3, MUTED);
            return;
        }
        CompoundTag reading = ClientTracking.smoothed(id);
        rect(g, p.x(), p.y(), p.w(), p.h(), BLACK);
        if (RealScreens.draw(g, id, reading, p.x(), p.y(), p.w(), p.h())) {
            // the machine's own screen, read-only (owner decision MS2); an operable box is edged turquoise, brighter under the mouse
            if (reading.getBoolean("op")) {
                outline(g, p.x() - 1, p.y() - 1, p.w() + 2, p.h() + 2, hover && tools ? FG : TQ);
            }
            return;
        }
        ModPanels.draw(g, p.x(), p.y(), p.w(), p.h(), tag, reading, reading.getBoolean("op"), hover && tools);
    }

    /** One line per tag: its lamp, its name, and a bar when its reading has a progress. */
    private static void allTags(GuiGraphics g, int x, int y, int w, int h) {
        rect(g, x, y, w, h, BG);
        rect(g, x, y, w, 10, 0xFF0D1218);
        small(g, tr("gui.bsp_core.tracking.all_tags").getString(), x + 4, y + 2, TQ);
        ListTag tags = ClientTracking.view().getList("tags", Tag.TAG_COMPOUND);
        for (int i = 0; i < tags.size(); i++) {
            int ry = y + 13 + i * 10;
            if (ry + 9 > y + h) {
                break;
            }
            CompoundTag t = tags.getCompound(i);
            CompoundTag r = ClientTracking.smoothed(t.getString("id"));
            lamp(g, x + 6, ry + 4, 2, r.getByte("st") == 0 && r.contains("block") ? TQ : OFF, true);
            small(g, fitSmall(tagName(t), w * 0.5f), x + 11, ry + 1, FG);
            if (w > 110) {
                CompoundTag xr = r.getCompound("x");
                float f = xr.contains("progress") ? xr.getFloat("progress") : xr.getList("procs", Tag.TAG_INT).isEmpty() ? -1
                        : xr.getList("procs", Tag.TAG_INT).getInt(0) / 100f;
                if (r.contains("feMax") && f < 0 && r.getLong("feMax") > 0) {
                    bar(g, x + (int) (w * 0.55), ry + 2, (int) (w * 0.45) - 6, 5, r.getLong("fe") / (float) r.getLong("feMax"), GOLD);
                } else if (f >= 0) {
                    bar(g, x + (int) (w * 0.55), ry + 2, (int) (w * 0.45) - 6, 5, f, TQ);
                }
            }
        }
    }

    /** The view's tag with this id, or null. */
    public static CompoundTag tag(String id) {
        ListTag tags = ClientTracking.view().getList("tags", Tag.TAG_COMPOUND);
        for (int i = 0; i < tags.size(); i++) {
            if (tags.getCompound(i).getString("id").equals(id)) {
                return tags.getCompound(i);
            }
        }
        return null;
    }
}
