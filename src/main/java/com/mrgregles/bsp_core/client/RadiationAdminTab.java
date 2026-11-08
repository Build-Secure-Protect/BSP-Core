package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.network.RadiationAdminPacket;
import com.mrgregles.bsp_core.network.RadiationEditPacket;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The admin panel's RADIATION tab (layout A with the chart, picked 2026-11-08). On the left the new-player protection: RANK,
 * PLAYTIME or OFF, then RankUp's ladder (click the rank that ends the protection) or the hours. On the right this server's totem
 * radiation settings with − and + (shift for bigger steps), and a chart of how far each level is seen with them. Changes stay in
 * a draft until SAVE; moderators see everything with the keys greyed out. Drawn by {@link AdminScreen}.
 */
public final class RadiationAdminTab {
    interface Host {
        Font font();

        void send(RadiationEditPacket packet);

        boolean canEdit();
    }

    private static final int W = 344, LEFT_W = 158, LIST_Y = 76, ROW_H = 13, ROWS = 6, RIGHT_X = 184, NUM_Y = 42, NUM_GAP = 14, STEP_X = 280,
            BOX_W = 28, CHART_X = 184, CHART_Y = 140, CHART_W = 150, CHART_H = 36, SAVE_X = 270, SAVE_Y = 186, SAVE_W = 64, SAVE_H = 16;
    private static final int DIM = 0xFF2A2F3A, SLOT = 0xFF0C0E12, VIO = 0xFFB58CFF, TQ = 0xFF19D3B0, BAD = 0xFFD63B2F, GOLD = 0xFFFFD23A,
            OK = 0xFF58F29A, MUTED = 0x9AA3B5, TEXT = 0xE6EAF2, OFF = 0x6B7385, HOVER = 0xFF2A2140, VIO_FILL = 0xFF2A2140;
    private static final String[] MODES = {"rank", "playtime", "off"};
    /** The numbers on the right: lang key, smallest, largest, step, step with shift. */
    private static final int[][] LIMITS = {{1, 1000, 1, 10}, {100, 100_000, 100, 1000}, {0, 100, 5, 5}, {0, 90, 5, 5}, {0, 16, 1, 1}, {4, 64, 1, 8}, {5, 3600, 5, 60}};
    private static final String[] NUM_KEYS = {"full_level", "max_seen", "blur", "cut", "layers", "radius", "seconds"};

    private final Host host;
    private RadiationAdminPacket got;
    /** The draft: mode, rank, hours, radiation on, and the seven numbers. */
    private int mode, hours;
    private String rank = "";
    private boolean enabled = true;
    private final int[] nums = new int[7];
    private int scroll;
    private boolean visible;

    private record Hit(int x, int y, int w, int h, Runnable run) {}

    private final List<Hit> hits = new ArrayList<>();

    RadiationAdminTab(Host host) {
        this.host = host;
    }

    void setVisible(boolean on) {
        visible = on;
    }

    void refresh() {
        host.send(RadiationEditPacket.request());
    }

    void take(RadiationAdminPacket msg) {
        boolean keep = got != null && dirty() && !msg.ok(); // a refused save keeps the admin's draft
        got = msg;
        if (keep) {
            return;
        }
        mode = msg.mode();
        rank = msg.rank();
        hours = msg.hours();
        enabled = msg.enabled();
        int[] from = {msg.fullLevel(), msg.maxSeen(), msg.blurPct(), msg.cutPct(), msg.maxLayers(), msg.radius(), msg.seconds()};
        System.arraycopy(from, 0, nums, 0, 7);
        int i = msg.ladder().indexOf(rank);
        scroll = Math.max(0, Math.min(Math.max(0, msg.ladder().size() - ROWS), i - ROWS / 2));
    }

    private boolean dirty() {
        if (got == null) {
            return false;
        }
        int[] from = {got.fullLevel(), got.maxSeen(), got.blurPct(), got.cutPct(), got.maxLayers(), got.radius(), got.seconds()};
        for (int i = 0; i < 7; i++) {
            if (from[i] != nums[i]) {
                return true;
            }
        }
        return mode != got.mode() || !rank.equals(got.rank()) || hours != got.hours() || enabled != got.enabled();
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.bsp_core.admin.rad." + key, args);
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void small(GuiGraphics g, Component text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(host.font(), text, 0, 0, colour, false);
        g.pose().popPose();
    }

    /** Small text shrunk further when wider than {@code maxW}. */
    private void smallFit(GuiGraphics g, Component text, int x, int y, int maxW, int colour) {
        float k = Math.min(0.75f, maxW / (float) Math.max(1, host.font().width(text)));
        g.pose().pushPose();
        g.pose().translate(x, y + (0.75f - k) * 4, 0);
        g.pose().scale(k, k, 1f);
        g.drawString(host.font(), text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private int sw(Component text) {
        return Math.round(host.font().width(text) * 0.75f);
    }

    private void key(GuiGraphics g, int x, int y, int w, int h, Component label, boolean on, int col, int mx, int my, Runnable run) {
        boolean hover = on && over(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, on ? col : DIM);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? HOVER : SLOT);
        int lw = sw(label);
        small(g, label, x + w / 2 - lw / 2, y + (h - 6) / 2, on ? col & 0xFFFFFF : OFF);
        if (on) {
            hits.add(new Hit(x, y, w, h, run));
        }
    }

    private void stepper(GuiGraphics g, int x, int y, Component value, boolean on, int mx, int my, Runnable down, Runnable up) {
        key(g, x, y, 12, 11, Component.literal("−"), on, TQ, mx, my, down);
        g.fill(x + 13, y, x + 13 + BOX_W, y + 11, DIM);
        g.fill(x + 14, y + 1, x + 12 + BOX_W, y + 10, SLOT);
        int w = Math.min(BOX_W - 2, sw(value));
        smallFit(g, value, x + 13 + BOX_W / 2 - w / 2, y + 3, BOX_W - 2, GOLD & 0xFFFFFF);
        key(g, x + 14 + BOX_W, y, 12, 11, Component.literal("+"), on, TQ, mx, my, up);
    }

    private static String number(int i, int v) {
        return i == 2 || i == 3 ? v + "%" : String.format(Locale.ROOT, "%,d", v);
    }

    private void step(int i, int sign) {
        int s = Screen.hasShiftDown() ? LIMITS[i][3] : LIMITS[i][2];
        nums[i] = Math.max(LIMITS[i][0], Math.min(LIMITS[i][1], nums[i] + sign * s));
    }

    void render(GuiGraphics g, int x, int y, int mx, int my) {
        hits.clear();
        if (got == null) {
            small(g, tr("loading"), x + 10, y + 30, MUTED);
            return;
        }
        boolean edit = host.canEdit();
        Font font = host.font();
        // ---------------------------------------------------------- the protection, left
        g.drawString(font, tr("protection"), x + 10, y + 28, VIO & 0xFFFFFF, false);
        int hy = y + 40;
        for (var line : font.split(tr("protection_help"), Math.round(LEFT_W / 0.75f))) {
            g.pose().pushPose();
            g.pose().translate(x + 10, hy, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, line, 0, 0, MUTED, false);
            g.pose().popPose();
            hy += 8;
        }
        for (int i = 0; i < 3; i++) {
            int kx = x + 10 + i * 54, chosen = i;
            if (mode == i) {
                g.fill(kx, y + 58, kx + 50, y + 70, VIO);
                g.fill(kx + 1, y + 59, kx + 49, y + 69, VIO_FILL);
                Component l = tr("mode." + MODES[i]);
                small(g, l, kx + 25 - sw(l) / 2, y + 61, VIO & 0xFFFFFF);
            } else {
                key(g, kx, y + 58, 50, 12, tr("mode." + MODES[i]), edit, TQ, mx, my, () -> mode = chosen);
            }
        }
        List<String> ladder = got.ladder();
        if (mode == 0) {
            if (ladder.isEmpty()) {
                int ly = y + 80;
                for (var line : font.split(tr("no_ladder"), Math.round(LEFT_W / 0.75f))) {
                    g.pose().pushPose();
                    g.pose().translate(x + 10, ly, 0);
                    g.pose().scale(0.75f, 0.75f, 1f);
                    g.drawString(font, line, 0, 0, BAD & 0xFFFFFF, false);
                    g.pose().popPose();
                    ly += 8;
                }
            } else {
                int at = ladder.indexOf(rank);
                scroll = Math.max(0, Math.min(scroll, Math.max(0, ladder.size() - ROWS)));
                for (int r = 0; r < ROWS && r + scroll < ladder.size(); r++) {
                    int i = r + scroll, ry = y + LIST_Y + r * ROW_H;
                    boolean on = i == at, hover = edit && over(mx, my, x + 10, ry, LEFT_W, ROW_H - 1);
                    g.fill(x + 10, ry, x + 10 + LEFT_W, ry + ROW_H - 1, on ? VIO : hover ? TQ : DIM);
                    g.fill(x + 11, ry + 1, x + 9 + LEFT_W, ry + ROW_H - 2, on ? VIO_FILL : SLOT);
                    String name = ladder.get(i);
                    Component shown = Component.literal(name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1));
                    Component tag = on ? tr("ends_here") : at >= 0 && i < at ? tr("protected") : Component.empty();
                    smallFit(g, shown, x + 15, ry + 3, LEFT_W - 12 - sw(tag), on ? VIO & 0xFFFFFF : at >= 0 && i < at ? MUTED : TEXT);
                    small(g, tag, x + 6 + LEFT_W - sw(tag), ry + 3, on ? GOLD & 0xFFFFFF : MUTED);
                    if (edit) {
                        hits.add(new Hit(x + 10, ry, LEFT_W, ROW_H - 1, () -> rank = name));
                    }
                }
                if (ladder.size() > ROWS) {
                    int track = ROWS * ROW_H - 1, knob = Math.max(6, track * ROWS / ladder.size()), ky = (track - knob) * scroll / (ladder.size() - ROWS);
                    g.fill(x + 11 + LEFT_W, y + LIST_Y + ky, x + 13 + LEFT_W, y + LIST_Y + ky + knob, DIM);
                }
            }
        } else if (mode == 1) {
            g.drawString(font, tr("hours"), x + 10, y + 81, TEXT, false);
            stepper(g, x + 110, y + 79, Component.literal(String.format(Locale.ROOT, "%,d", hours)), edit, mx, my,
                    () -> hours = Math.max(1, hours - (Screen.hasShiftDown() ? 10 : 1)), () -> hours = Math.min(100_000, hours + (Screen.hasShiftDown() ? 10 : 1)));
            small(g, tr("hours_help"), x + 10, y + 96, MUTED);
        } else {
            small(g, tr("off_help"), x + 10, y + 80, MUTED);
        }
        if (mode == 0 && !ladder.isEmpty() && !ladder.contains(rank)) {
            small(g, tr("not_on_ladder", rank.isEmpty() ? "?" : Character.toUpperCase(rank.charAt(0)) + rank.substring(1)), x + 10, y + 158, BAD & 0xFFFFFF);
        } else {
            small(g, tr("protected_now", got.protectedNow()), x + 10, y + 158, TQ & 0xFFFFFF);
        }
        g.fill(x + 176, y + 26, x + 177, y + 178, DIM);
        // ---------------------------------------------------------- the radiation numbers, right
        g.drawString(font, tr("radiation"), x + RIGHT_X, y + 28, VIO & 0xFFFFFF, false);
        key(g, x + 290, y + 26, 44, 12, tr(enabled ? "on" : "off"), edit, enabled ? TQ : GOLD, mx, my, () -> enabled = !enabled);
        for (int i = 0; i < 7; i++) {
            int ry = y + NUM_Y + i * NUM_GAP, k = i;
            smallFit(g, tr(NUM_KEYS[i]), x + RIGHT_X, ry + 3, STEP_X - RIGHT_X - 4, TEXT);
            stepper(g, x + STEP_X, ry, Component.literal(number(i, nums[i])), edit, mx, my, () -> step(k, -1), () -> step(k, 1));
        }
        chart(g, x + CHART_X, y + CHART_Y);
        // ---------------------------------------------------------- the foot
        Component foot = got.message().isEmpty() ? tr("note") : Component.literal(got.message());
        int footCol = got.message().isEmpty() ? MUTED : got.ok() ? TQ & 0xFFFFFF : BAD & 0xFFFFFF;
        var footLines = font.split(foot, Math.round((SAVE_X - 16) / 0.75f));
        for (int i = 0; i < Math.min(2, footLines.size()); i++) { // a long answer takes two lines rather than shrinking out of sight
            g.pose().pushPose();
            g.pose().translate(x + 10, y + (footLines.size() > 1 ? 187 : 192) + i * 7, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, footLines.get(i), 0, 0, footCol, false);
            g.pose().popPose();
        }
        // the protection is sent only when it changed here, so saving this server's numbers never overwrites another admin's protection
        boolean setProtection = mode != got.mode() || !rank.equals(got.rank()) || hours != got.hours();
        boolean canSave = edit && dirty() && (!setProtection || mode != 0 || ladder.contains(rank));
        key(g, x + SAVE_X, y + SAVE_Y, SAVE_W, SAVE_H, tr("save"), canSave, OK, mx, my,
                () -> host.send(new RadiationEditPacket(true, setProtection, mode, rank, hours, enabled, nums[0], nums[1], nums[2], nums[3], nums[4], nums[5], nums[6])));
    }

    /** How far each level (0 to 183) is seen with the draft numbers; the dotted gold line is the full-radiation level. */
    private void chart(GuiGraphics g, int cx, int cy) {
        g.fill(cx, cy, cx + CHART_W, cy + CHART_H, DIM);
        g.fill(cx + 1, cy + 1, cx + CHART_W - 1, cy + CHART_H - 1, SLOT);
        int full = Math.max(1, nums[0]), max = Math.max(1, nums[1]);
        double exp = got.exponent();
        int innerW = CHART_W - 4, innerH = CHART_H - 4;
        for (int px = 0; px <= innerW; px++) {
            double level = 183.0 * px / innerW;
            double d = enabled ? max * Math.pow(Math.min(1.0, level / full), exp) : max;
            int py = cy + CHART_H - 2 - (int) Math.round(innerH * d / max);
            g.fill(cx + 2 + px, py, cx + 3 + px, py + 1, TQ);
        }
        small(g, Component.literal(String.format(Locale.ROOT, "%,d", max)), cx + 3, cy + 3, MUTED);
        small(g, Component.literal("0"), cx + 3, cy + CHART_H + 1, MUTED);
        if (full <= 183) {
            int fx = cx + 2 + Math.round(innerW * full / 183f);
            for (int yy = cy + 2; yy < cy + CHART_H - 2; yy += 3) {
                g.fill(fx, yy, fx + 1, yy + 1, GOLD);
            }
            // under the chart, centred on the dotted line, kept clear of the "0" and inside the chart's width
            Component fl = tr("chart_full", full);
            int lx = Math.max(cx + 10, Math.min(cx + CHART_W - sw(fl), fx - sw(fl) / 2));
            small(g, fl, lx, cy + CHART_H + 1, GOLD & 0xFFFFFF);
        }
    }

    boolean mouseClicked(double mx, double my, int button) {
        for (Hit h : hits) {
            if (over(mx, my, h.x, h.y, h.w, h.h)) {
                h.run.run();
                return true;
            }
        }
        return false;
    }

    boolean mouseScrolled(double mx, double my, double delta, int x, int y) {
        if (got != null && mode == 0 && over(mx, my, x + 10, y + LIST_Y, LEFT_W, ROWS * ROW_H)) {
            scroll = Math.max(0, Math.min(Math.max(0, got.ladder().size() - ROWS), scroll - (int) Math.signum(delta)));
            return true;
        }
        return false;
    }
}
