package com.mrgregles.bsp_core.tracking;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * The Holo Grid layout a player keeps (owner decisions MT11 to MT16, MT26 and MT40): up to {@link #MAX_PAGES} pages, each with its own
 * grid of {@link #MIN_COLS} to {@link #MAX_COLS} columns and {@link #MIN_ROWS} to {@link #MAX_ROWS} rows and boxes that never overlap;
 * up to {@link #MAX_PRESETS} presets, each a full set of pages; and the box pinned to the game screen. The client sends the whole layout
 * on every change; {@link #clean} keeps only what fits these rules, so nothing a client sends can grow a record without bound.
 *
 * <p>Stored as NBT: {@code pages} (list of {@code name, cols, rows, boxes}), {@code presets} (list of {@code name, saved, pages}),
 * {@code pin} ({@code page, box, corner, size, on}). A box is {@code x, y, w, h, src}, where {@code src} is {@code tag:<id>}, {@code map},
 * {@code all} or empty.
 */
public final class GridLayout {
    public static final int MAX_PAGES = 9, MAX_PRESETS = 10, MAX_BOXES = 64;
    /** Every box of the layout together, pages and presets: about 21 KB of NBT, so a layout always fits a client's packet (32 KB). */
    public static final int MAX_TOTAL_BOXES = 400;
    public static final int MIN_COLS = 4, MAX_COLS = 32, MIN_ROWS = 3, MAX_ROWS = 24, START_COLS = 12, START_ROWS = 8;

    private GridLayout() {}

    /** The layout the client sent, cut to the rules. */
    public static CompoundTag clean(CompoundTag in) {
        int[] budget = {MAX_TOTAL_BOXES};
        BUDGET.set(budget);
        try {
            return cleanAll(in);
        } finally {
            BUDGET.remove();
        }
    }

    private static final ThreadLocal<int[]> BUDGET = new ThreadLocal<>();

    private static CompoundTag cleanAll(CompoundTag in) {
        CompoundTag out = new CompoundTag();
        out.put("pages", cleanPages(in.getList("pages", Tag.TAG_COMPOUND)));
        ListTag presets = new ListTag();
        ListTag inPresets = in.getList("presets", Tag.TAG_COMPOUND);
        for (int i = 0; i < inPresets.size() && presets.size() < MAX_PRESETS; i++) {
            CompoundTag p = inPresets.getCompound(i);
            CompoundTag q = new CompoundTag();
            q.putString("name", TrackedTag.cleanName(p.getString("name"), "Preset " + (presets.size() + 1)));
            q.putLong("saved", Math.max(0, p.getLong("saved")));
            q.put("pages", cleanPages(p.getList("pages", Tag.TAG_COMPOUND)));
            presets.add(q);
        }
        out.put("presets", presets);
        CompoundTag pin = in.getCompound("pin");
        CompoundTag p = new CompoundTag();
        p.putInt("page", clamp(pin.getInt("page"), 0, MAX_PAGES - 1));
        p.putInt("box", clamp(pin.getInt("box"), 0, MAX_BOXES - 1));
        p.putInt("corner", clamp(pin.getInt("corner"), 0, 3));
        p.putInt("size", clamp(pin.getInt("size"), 0, 1));
        p.putBoolean("on", pin.getBoolean("on"));
        out.put("pin", p);
        return out;
    }

    private static ListTag cleanPages(ListTag in) {
        ListTag pages = new ListTag();
        for (int i = 0; i < in.size() && pages.size() < MAX_PAGES; i++) {
            CompoundTag page = in.getCompound(i);
            int cols = clamp(page.contains("cols") ? page.getInt("cols") : START_COLS, MIN_COLS, MAX_COLS);
            int rows = clamp(page.contains("rows") ? page.getInt("rows") : START_ROWS, MIN_ROWS, MAX_ROWS);
            CompoundTag q = new CompoundTag();
            q.putString("name", TrackedTag.cleanName(page.getString("name"), "Page " + (pages.size() + 1)));
            q.putInt("cols", cols);
            q.putInt("rows", rows);
            List<int[]> kept = new ArrayList<>();
            ListTag boxes = new ListTag();
            ListTag inBoxes = page.getList("boxes", Tag.TAG_COMPOUND);
            int[] budget = BUDGET.get();
            for (int b = 0; b < inBoxes.size() && boxes.size() < MAX_BOXES && (budget == null || budget[0] > 0); b++) {
                CompoundTag box = inBoxes.getCompound(b);
                int x = clamp(box.getInt("x"), 0, cols - 1), y = clamp(box.getInt("y"), 0, rows - 1);
                int w = clamp(box.getInt("w"), 1, cols - x), h = clamp(box.getInt("h"), 1, rows - y);
                int[] r = {x, y, w, h};
                if (kept.stream().anyMatch(o -> x < o[0] + o[2] && o[0] < x + w && y < o[1] + o[3] && o[1] < y + h)) {
                    continue; // boxes never overlap: the later one goes
                }
                kept.add(r);
                if (budget != null) {
                    budget[0]--;
                }
                CompoundTag c = new CompoundTag();
                c.putInt("x", x);
                c.putInt("y", y);
                c.putInt("w", w);
                c.putInt("h", h);
                c.putString("src", cleanSource(box.getString("src")));
                boxes.add(c);
            }
            q.put("boxes", boxes);
            pages.add(q);
        }
        return pages;
    }

    /** A box's source as the server keeps it: {@code map}, {@code all}, {@code tag:} and an 8-character id, or empty. */
    static String cleanSource(String src) {
        if (src.equals("map") || src.equals("all")) {
            return src;
        }
        if (src.startsWith("tag:") && src.length() == 12 && src.substring(4).chars().allMatch(ch -> Character.digit(ch, 16) >= 0)) {
            return src;
        }
        return "";
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
