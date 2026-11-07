package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.credit.CreditKind;
import com.mrgregles.bsp_core.network.CreditAdminActionPacket;
import com.mrgregles.bsp_core.network.CreditAdminActionPacket.Action;
import com.mrgregles.bsp_core.network.CreditAdminPacket;
import com.mrgregles.bsp_core.network.CreditStripPacket;
import com.mrgregles.bsp_core.network.CreditTicketsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The admin panel's CREDIT tab (Store Credit task 10, the owner's pick A): the list of ticket kinds on the left, the chosen
 * kind's form on the right (name, value, colour and mark, time limit, bind when given, where it spends), and EDIT opening the
 * places as a pop-over with SHOPS, LAND OFFICE and TELEPORTS tabs, a search line, tick boxes, plot names with how many plots
 * share each and a box for a name this server does not sell. COPY /GIVE puts the kind's /give line on the clipboard; TICKETS
 * lists the kind's tickets with VOID. Everything comes from {@link CreditAdminPacket}; every change goes back as a
 * {@link CreditAdminActionPacket}, which the server checks.
 */
public final class CreditAdminTab {
    interface Host {
        Font font();

        void addWidget(EditBox box);

        void send(CreditAdminActionPacket packet);

        /** Whether the panel is not read-only; store credit is for admins and operators. */
        boolean canEdit();
    }

    private static final int W = 344, H = 210, X = 130, R = 334;
    private static final int BG = 0xFF10151C, SLOT = 0xFF0C0E12, DIM = 0xFF2A2F3A, VIO = 0xFFB58CFF, TQ = 0xFF19D3B0, BAD = 0xFFD63B2F, GOLD = 0xFFFFD23A,
            MUTED = 0x9AA3B5, TEXT = 0xE6EAF2, OFF = 0x6B7385, HOVERV = 0xFF2A2140, OK = 0x58F29A, RED = 0xFF8F86;
    private static final int LIST_ROWS = 9, POP_ROWS = 9, TICKET_ROWS = 8;
    private static final long CONFIRM_MS = 4000;

    private record Hit(int x, int y, int w, int h, Runnable run) {}

    private final Host host;
    private final List<Hit> hits = new ArrayList<>();
    private final List<CreditKind> kinds = new ArrayList<>();
    private final List<Integer> given = new ArrayList<>();
    private List<CreditAdminPacket.Shop> shops = List.of();
    private List<CreditAdminPacket.PlotName> plots = List.of();
    private String message = "";
    private boolean messageOk = true, visible, asked;
    @Nullable
    private String selected;
    /** The kind being edited: a copy of the chosen one, or a new one (then {@link #isNew}). */
    @Nullable
    private CreditKind draft;
    private boolean isNew, pop;
    private int popTab = 1, popScroll, listScroll, ticketScroll;
    @Nullable
    private CreditTicketsPacket tickets;
    private boolean showTickets;
    private String armed = "";
    private long armedUntil;
    private EditBox idBox, nameBox, valueBox, daysBox, hoursBox, searchBox, plotBox;
    private int left, top;

    CreditAdminTab(Host host) {
        this.host = host;
    }

    // ------------------------------------------------------------------ data

    void take(CreditAdminPacket msg) {
        kinds.clear();
        given.clear();
        for (CompoundTag t : msg.kinds()) {
            kinds.add(CreditKind.load(t));
        }
        given.addAll(msg.given());
        shops = msg.shops();
        plots = msg.plots();
        note(msg.message(), msg.ok());
        if (msg.ok() && msg.message().startsWith("Saved") && draft != null) {
            selected = draft.id; // a new kind saved: it is chosen now
            isNew = false;
        }
        CreditKind k = kind(selected);
        if (k == null) {
            selected = kinds.isEmpty() ? null : kinds.get(0).id;
            k = kind(selected);
            showTickets = false;
        }
        // the server's copy replaces the draft when it is the answer to this form's save, or nothing was typed; otherwise the typing stays
        boolean answer = waiting;
        waiting = false;
        if (!isNew && (draft == null || answer && msg.ok() || !dirty(k))) {
            draft = k == null ? null : k.copy();
            fill();
        }
    }

    private long noteUntil;
    /** A SAVE or DELETE of this form waits for its answer. */
    private boolean waiting;

    /** Whether what is in the form differs from the saved kind. */
    private boolean dirty(@Nullable CreditKind saved) {
        CreditKind now = read();
        if (saved == null || now == null) {
            return false;
        }
        CompoundTag a = now.save(), b = saved.save();
        a.remove("Edited");
        b.remove("Edited");
        return !a.equals(b);
    }

    private void note(String text, boolean ok) {
        message = text;
        messageOk = ok;
        noteUntil = System.currentTimeMillis() + 6000;
    }

    void takeTickets(CreditTicketsPacket msg) {
        if (!msg.kind().equals(selected)) {
            return; // an answer for a kind no longer shown
        }
        tickets = msg;
        ticketScroll = Math.min(ticketScroll, Math.max(0, msg.rows().size() - TICKET_ROWS));
    }

    /** Asks for the tab's data (the first time it shows, and on REFRESH). */
    void refresh() {
        host.send(new CreditAdminActionPacket(Action.REQUEST, "", new CompoundTag()));
        asked = true;
    }

    @Nullable
    private CreditKind kind(@Nullable String id) {
        for (CreditKind k : kinds) {
            if (k.id.equals(id)) {
                return k;
            }
        }
        return null;
    }

    private int givenOf(String id) {
        for (int i = 0; i < kinds.size(); i++) {
            if (kinds.get(i).id.equals(id)) {
                return i < given.size() ? given.get(i) : 0;
            }
        }
        return 0;
    }

    private void choose(@Nullable CreditKind k) {
        isNew = false;
        showTickets = false;
        pop = false;
        draft = k == null ? null : k.copy();
        selected = k == null ? null : k.id;
        fill();
    }

    private void startNew() {
        CreditKind k = new CreditKind("");
        k.value = 1000;
        draft = k;
        isNew = true;
        showTickets = false;
        pop = false;
        noteUntil = 0;
        fill();
    }

    /** Puts the draft's values in the boxes. */
    private void fill() {
        if (nameBox == null) {
            return;
        }
        CreditKind d = draft;
        idBox.setValue(d == null ? "" : d.id);
        nameBox.setValue(d == null ? "" : d.name);
        valueBox.setValue(d == null ? "" : Long.toString(d.value));
        long h = d == null ? 0 : d.durationMs / 3_600_000L;
        daysBox.setValue(d == null ? "" : Long.toString(h / 24));
        hoursBox.setValue(d == null ? "" : Long.toString(h % 24));
        setVisible(visible);
    }

    /** The draft as typed: the boxes' values over the draft's switches and places. */
    @Nullable
    private CreditKind read() {
        if (draft == null) {
            return null;
        }
        String id = isNew ? (idBox.getValue().isBlank() ? KitAdminTab.slug(nameBox.getValue()) : idBox.getValue().trim().toLowerCase(Locale.ROOT)) : draft.id;
        CompoundTag t = draft.save();
        t.putString("Id", id);
        CreditKind k = CreditKind.load(t);
        k.name = nameBox.getValue();
        k.value = number(valueBox.getValue());
        k.durationMs = (number(daysBox.getValue()) * 24 + number(hoursBox.getValue())) * 3_600_000L;
        return k;
    }

    private static long number(String s) {
        try {
            return s.isBlank() ? 0 : Long.parseLong(s.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------ widgets

    void init(int left, int top) {
        if (nameBox != null && draft != null && !showTickets) { // a resize re-creates the boxes: keep what was typed
            CreditKind typed = read();
            if (typed != null) {
                draft = typed;
            }
        }
        Font font = host.font();
        idBox = box(font, 32, "id");
        nameBox = box(font, CreditKind.NAME_MAX, "name");
        valueBox = box(font, 10, "pieces");
        valueBox.setFilter(s -> s.matches("[0-9,]{0,13}"));
        daysBox = box(font, 4, "0");
        daysBox.setFilter(s -> s.matches("\\d{0,4}"));
        hoursBox = box(font, 2, "0");
        hoursBox.setFilter(s -> s.matches("\\d{0,2}"));
        searchBox = box(font, 32, Component.translatable("gui.bsp_core.credit.admin.search").getString());
        plotBox = box(font, CreditKind.PLACE_NAME_MAX, Component.translatable("gui.bsp_core.credit.admin.type_plot").getString());
        plotBox.setHint(Component.translatable("gui.bsp_core.credit.admin.type_plot").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        place(left, top);
        fill();
    }

    private EditBox box(Font font, int max, String hint) {
        EditBox b = new EditBox(font, 0, 0, 40, 10, Component.literal(hint));
        b.setMaxLength(max);
        b.setBordered(false);
        b.setHint(Component.literal(hint).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        host.addWidget(b);
        return b;
    }

    private static void at(EditBox b, int x, int y, int w) {
        b.setX(x);
        b.setY(y);
        b.setWidth(w);
    }

    private void place(int left, int top) {
        this.left = left;
        this.top = top;
        at(idBox, left + X + 34 + 3, top + 37, 100);
        at(nameBox, left + X + 34 + 3, top + 48, 106);
        at(valueBox, left + X + 34 + 3, top + 62, 60);
        at(daysBox, left + X + 34 + 3, top + 103, 20);
        at(hoursBox, left + X + 34 + 46 + 3, top + 103, 14);
        at(searchBox, left + 126 + 9, top + 22 + 31, 190);
        at(plotBox, left + 126 + 9, top + 22 + 150, 132);
    }

    void setVisible(boolean on) {
        visible = on;
        if (nameBox == null) {
            return;
        }
        boolean form = on && draft != null && !showTickets && !pop;
        for (EditBox b : new EditBox[]{nameBox, valueBox, daysBox, hoursBox}) {
            show(b, form);
        }
        show(idBox, form && isNew);
        show(searchBox, on && pop && draft != null && popTab != 2);
        show(plotBox, on && pop && draft != null && popTab == 1);
        if (on && !asked) {
            refresh();
        }
    }

    private static void show(EditBox b, boolean on) {
        b.visible = on;
        b.active = on;
        if (!on) {
            b.setFocused(false);
        }
    }

    // ------------------------------------------------------------------ input

    boolean mouseClicked(double mx, double my, int button) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.x && mx < h.x + h.w && my >= h.y && my < h.y + h.h) {
                h.run.run();
                setVisible(visible);
                return true;
            }
        }
        return false;
    }

    boolean mouseScrolled(double mx, double my, double delta) {
        int step = delta > 0 ? -1 : 1;
        if (pop && over(mx, my, left + 126, top + 22, 212, 182)) {
            popScroll = Math.max(0, Math.min(popRows().size() - POP_ROWS, popScroll + step));
            return true;
        }
        if (showTickets && tickets != null && mx >= left + X) {
            ticketScroll = Math.max(0, Math.min(tickets.rows().size() - TICKET_ROWS, ticketScroll + step));
            return true;
        }
        if (mx < left + X) {
            listScroll = Math.max(0, Math.min(kinds.size() - LIST_ROWS, listScroll + step));
            return true;
        }
        return false;
    }

    boolean keyPressed(int key, int scan, int mods) {
        if (key == 256 && pop) { // Esc closes the pop-over first
            pop = false;
            setVisible(visible);
            return true;
        }
        if (key == 257 && plotBox != null && plotBox.isFocused()) { // Enter adds the typed plot name
            addPlot();
            return true;
        }
        for (EditBox b : new EditBox[]{idBox, nameBox, valueBox, daysBox, hoursBox, searchBox, plotBox}) {
            if (b != null && b.isFocused() && b.visible) {
                b.keyPressed(key, scan, mods);
                return key != 256;
            }
        }
        return false;
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean confirmed(String what) {
        long now = System.currentTimeMillis();
        if (what.equals(armed) && now < armedUntil) {
            armed = "";
            return true;
        }
        armed = what;
        armedUntil = now + CONFIRM_MS;
        return false;
    }

    private boolean arming(String what) {
        return what.equals(armed) && System.currentTimeMillis() < armedUntil;
    }

    private void save() {
        CreditKind k = read();
        if (k == null) {
            return;
        }
        CreditKind tidy = k.copy();
        tidy.tidy();
        String problem = tidy.problem();
        if (problem != null) {
            note(problem, false);
            return;
        }
        if (isNew && kind(k.id) != null) {
            note("There is already a kind with the id \"" + k.id + "\".", false);
            return;
        }
        draft = k;
        waiting = true;
        host.send(new CreditAdminActionPacket(Action.SAVE, k.id, k.save()));
    }

    private void copyGive() {
        if (draft == null || draft.id.isEmpty()) {
            return;
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(give(draft.id));
        note(Component.translatable("gui.bsp_core.credit.admin.copied").getString(), true);
    }

    static String give(String id) {
        return "/give @p bsp_core:store_credit{Kind:\"" + id + "\"}";
    }

    private void addPlot() {
        if (draft == null || plotBox == null) {
            return;
        }
        String name = plotBox.getValue().trim();
        if (!name.isEmpty() && draft.plots.stream().noneMatch(p -> p.equalsIgnoreCase(name))) {
            draft.plots.add(name);
        }
        plotBox.setValue("");
    }

    // ------------------------------------------------------------------ drawing

    private void fill(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + h, c);
    }

    private void inset(GuiGraphics g, int x, int y, int w, int h) {
        fill(g, x, y, w, h, DIM);
        fill(g, x + 1, y + 1, w - 2, h - 2, SLOT);
    }

    private int small(GuiGraphics g, String s, int x, int y, int colour) {
        Font font = host.font();
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, s, 0, 0, colour, false);
        g.pose().popPose();
        return Math.round(font.width(s) * 0.75f);
    }

    private int sw(String s) {
        return Math.round(host.font().width(s) * 0.75f);
    }

    /** A chip: a small framed word, violet (or {@code col}) when on. Returns its width plus the gap. */
    private int chip(GuiGraphics g, int x, int y, String label, boolean on, int col, Runnable run) {
        int w = sw(label) + 8;
        fill(g, x, y, w, 11, on ? col : DIM);
        fill(g, x + 1, y + 1, w - 2, 9, on ? HOVERV : SLOT);
        small(g, label, x + 4, y + 3, on ? col & 0xFFFFFF : TEXT);
        if (run != null) {
            hits.add(new Hit(x, y, w, 11, run));
        }
        return w + 3;
    }

    private void key(GuiGraphics g, int x, int y, int w, int h, String label, int col, boolean enabled, Runnable run) {
        fill(g, x, y, w, h, enabled ? col : DIM);
        fill(g, x + 1, y + 1, w - 2, h - 2, SLOT);
        String l = label;
        while (sw(l) > w - 4 && l.length() > 1) {
            l = l.substring(0, l.length() - 1);
        }
        small(g, l, x + w / 2 - sw(l) / 2, y + (h - 6) / 2, enabled ? col & 0xFFFFFF : OFF);
        if (enabled && run != null) {
            hits.add(new Hit(x, y, w, h, run));
        }
    }

    private void tick(GuiGraphics g, int x, int y, boolean on, Runnable run) {
        fill(g, x, y, 8, 8, on ? VIO : DIM);
        fill(g, x + 1, y + 1, 6, 6, on ? HOVERV : SLOT);
        if (on) {
            small(g, "✓", x + 1, y, VIO & 0xFFFFFF);
        }
        if (run != null) {
            hits.add(new Hit(x, y, 8, 8, run));
        }
    }

    private void icon(GuiGraphics g, CreditKind k, int x, int y, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.renderItem(ClientCredit.icon(new CreditStripPacket.Summary("", 0, k.name, k.colour, k.mark, 0, 0)), 0, 0);
        g.pose().popPose();
    }

    void render(GuiGraphics g, int x, int y, int mx, int my) {
        place(x, y);
        hits.clear();
        if (!host.canEdit()) {
            small(g, Component.translatable("gui.bsp_core.credit.admin.admins_only").getString(), x + 10, y + 30, MUTED);
            return;
        }
        kindList(g, x, y);
        if (showTickets && tickets == null && !isNew && draft != null) {
            small(g, Component.translatable("gui.bsp_core.credit.admin.loading").getString(), x + X, y + 30, MUTED);
            key(g, x + X, y + 186, 40, 13, Component.translatable("gui.bsp_core.credit.admin.back").getString(), TQ, true, () -> showTickets = false);
        } else if (draft == null) {
            small(g, Component.translatable(kinds.isEmpty() ? "gui.bsp_core.credit.admin.none" : "gui.bsp_core.credit.admin.pick").getString(), x + X, y + 30, MUTED);
        } else if (showTickets && tickets != null && !isNew) {
            ticketList(g, x, y);
        } else {
            form(g, x, y);
            if (pop) {
                popover(g, x, y);
            }
        }
    }

    private void kindList(GuiGraphics g, int x, int y) {
        small(g, Component.translatable("gui.bsp_core.credit.admin.kinds", kinds.size()).getString(), x + 10, y + 26, MUTED);
        listScroll = Math.max(0, Math.min(listScroll, kinds.size() - LIST_ROWS));
        for (int i = 0; i < Math.min(LIST_ROWS, kinds.size() - listScroll); i++) {
            CreditKind k = kinds.get(i + listScroll);
            int ry = y + 34 + i * 14;
            boolean sel = !isNew && k.id.equals(selected);
            if (sel) {
                fill(g, x + 10, ry, 112, 13, VIO);
                fill(g, x + 11, ry + 1, 110, 11, HOVERV);
            }
            icon(g, k, x + 12, ry + 1, 0.6875f);
            String v = Bank.format(k.value);
            String name = k.name;
            while (sw(name) > 112 - 17 - sw(v) - 6 && name.length() > 1) {
                name = name.substring(0, name.length() - 1);
            }
            small(g, name, x + 25, ry + 4, sel ? VIO & 0xFFFFFF : TEXT);
            small(g, v, x + 119 - sw(v), ry + 4, GOLD & 0xFFFFFF);
            hits.add(new Hit(x + 10, ry, 112, 13, () -> choose(k)));
        }
        if (kinds.size() > LIST_ROWS) { // more than show: the wheel scrolls
            String more = (listScroll > 0 ? "\u25b2 " : "") + (listScroll + LIST_ROWS) + " / " + kinds.size() + (listScroll + LIST_ROWS < kinds.size() ? " \u25bc" : "");
            small(g, more, x + 122 - sw(more), y + 26, MUTED);
        }
        int ny = y + 34 + Math.min(LIST_ROWS, kinds.size()) * 14 + 4;
        key(g, x + 10, ny, 54, 13, Component.translatable("gui.bsp_core.credit.admin.new").getString(), TQ, true, this::startNew);
        if (draft != null && !isNew) {
            String id = draft.id;
            key(g, x + 10, y + H - 24, 54, 13, Component.translatable(arming("delete " + id) ? "gui.bsp_core.credit.admin.sure" : "gui.bsp_core.credit.admin.delete").getString(), BAD, true, () -> {
                if (confirmed("delete " + id)) {
                    waiting = true;
                    host.send(new CreditAdminActionPacket(Action.DELETE, id, new CompoundTag()));
                    draft = null;
                    selected = null;
                }
            });
        }
    }

    private void form(GuiGraphics g, int x, int y) {
        CreditKind d = draft;
        int fx = x + X, lx = fx + 34;
        g.drawString(host.font(), host.font().plainSubstrByWidth(Component.translatable(isNew ? "gui.bsp_core.credit.admin.new_kind" : "gui.bsp_core.credit.admin.kind", d.name).getString(), R - X - 20), fx, y + 26, TQ & 0xFFFFFF, false);
        if (!isNew) {
            small(g, Component.translatable("gui.bsp_core.credit.admin.id", d.id).getString(), fx, y + 37, OFF);
        }
        if (isNew) {
            small(g, "Id", fx, y + 38, MUTED);
            inset(g, lx, y + 35, 106, 11);
        }
        small(g, Component.translatable("gui.bsp_core.credit.admin.name").getString(), fx, y + 49, MUTED);
        inset(g, lx, y + 46, 110, 11);
        small(g, Component.translatable("gui.bsp_core.credit.admin.value").getString(), fx, y + 63, MUTED);
        inset(g, lx, y + 60, 64, 11);
        small(g, Component.translatable("gui.bsp_core.credit.admin.pieces").getString(), lx + 68, y + 63, MUTED);
        // the look: 16 dye swatches, the mark, the ticket as it will look
        small(g, Component.translatable("gui.bsp_core.credit.admin.look").getString(), fx, y + 77, MUTED);
        DyeColor[] dyes = DyeColor.values();
        for (int i = 0; i < dyes.length; i++) {
            DyeColor dye = dyes[i];
            int sx = lx + i * 9, sy = y + 75;
            if (dye.getName().equals(d.colour)) {
                fill(g, sx - 1, sy - 1, 9, 9, 0xFFFFFFFF);
            }
            float[] c = dye.getTextureDiffuseColors();
            fill(g, sx, sy, 7, 7, 0xFF000000 | Math.round(c[0] * 255) << 16 | Math.round(c[1] * 255) << 8 | Math.round(c[2] * 255));
            hits.add(new Hit(sx - 1, sy - 1, 9, 9, () -> d.colour = dye.getName()));
        }
        int cx = lx;
        for (String[] m : new String[][]{{"C", "C"}, {"D", "D"}, {"", Component.translatable("gui.bsp_core.credit.admin.no_mark").getString()}}) {
            cx += chip(g, cx, y + 86, m[1], m[0].equals(d.mark), VIO, () -> d.mark = m[0]);
        }
        small(g, Component.translatable("gui.bsp_core.credit.admin.mark_hint").getString(), cx + 2, y + 89, OFF);
        fill(g, x + R - 18, y + 60, 18, 18, DIM);
        fill(g, x + R - 17, y + 61, 16, 16, 0xFF8B8B8B);
        if (!pop) { // items draw nearer than the pop-over's panel, so none under it
            icon(g, d, x + R - 17, y + 61, 1f);
        }
        // the time limit
        small(g, Component.translatable("gui.bsp_core.credit.admin.time").getString(), fx, y + 104, MUTED);
        inset(g, lx, y + 101, 24, 11);
        small(g, Component.translatable("gui.bsp_core.credit.admin.days").getString(), lx + 27, y + 104, MUTED);
        inset(g, lx + 46, y + 101, 18, 11);
        small(g, Component.translatable("gui.bsp_core.credit.admin.hours").getString(), lx + 67, y + 104, MUTED);
        boolean none = number(daysBox.getValue()) <= 0 && number(hoursBox.getValue()) <= 0;
        chip(g, lx + 90, y + 101, Component.translatable("gui.bsp_core.credit.admin.no_limit").getString(), none, VIO, () -> {
            daysBox.setValue("0");
            hoursBox.setValue("0");
        });
        small(g, Component.translatable("gui.bsp_core.credit.admin.clock").getString(), lx, y + 115, OFF);
        tick(g, fx, y + 126, d.bind, () -> d.bind = !d.bind);
        small(g, Component.translatable("gui.bsp_core.credit.admin.bind").getString(), fx + 11, y + 127, TEXT);
        hits.add(new Hit(fx + 11, y + 125, 150, 9, () -> d.bind = !d.bind));
        // where it spends: a summary chip per place, EDIT opens the list
        small(g, Component.translatable("gui.bsp_core.credit.admin.spends").getString(), fx, y + 141, MUTED);
        key(g, lx, y + 138, 32, 11, Component.translatable("gui.bsp_core.credit.admin.edit").getString(), TQ, true, () -> {
            pop = true;
            popScroll = 0;
        });
        int px = fx;
        List<String> chips = summary(d);
        for (int i = 0; i < chips.size(); i++) {
            String c = chips.get(i);
            int w = sw(c) + 11;
            if (px + w > x + R) {
                small(g, "+" + (chips.size() - i), px, y + 154, MUTED);
                break;
            }
            px += chip(g, px, y + 151, c, true, TQ, null);
        }
        if (chips.isEmpty()) {
            small(g, ellipsis(Component.translatable("gui.bsp_core.credit.admin.nowhere").getString(), R - X), fx, y + 154, RED);
        }
        // the /give line, then the keys
        String give = isNew ? give(idBox.getValue().isBlank() ? KitAdminTab.slug(nameBox.getValue()) : idBox.getValue()) : give(d.id);
        if (!answer(g, fx, y + 165, R - X)) { // the answer to the last action, for a few seconds, in place of the /give line
            inset(g, fx, y + 166, R - X, 12);
            small(g, ellipsis(give, R - X - 6), fx + 3, y + 169, OFF);
        }
        key(g, fx, y + 186, 50, 13, Component.translatable("gui.bsp_core.credit.admin.save").getString(), TQ, true, this::save);
        key(g, fx + 54, y + 186, 58, 13, Component.translatable("gui.bsp_core.credit.admin.copy").getString(), TQ, !isNew, this::copyGive);
        if (!isNew) {
            key(g, x + R - 70, y + 186, 70, 13, Component.translatable("gui.bsp_core.credit.admin.tickets", givenOf(d.id)).getString(), GOLD, true, () -> {
                showTickets = true;
                ticketScroll = 0;
                tickets = null; // until this kind's list arrives
                host.send(new CreditAdminActionPacket(Action.TICKETS, d.id, new CompoundTag()));
            });
        }
    }

    /** Text cut to {@code max} pixels at the small size, with "..." when cut. */
    private String ellipsis(String s, int max) {
        if (sw(s) <= max) {
            return s;
        }
        String t = s;
        while (t.length() > 1 && sw(t + "...") > max) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "...";
    }

    /** Draws the last answer on up to two lines in a band of width {@code w}; false when there is none to show. */
    private boolean answer(GuiGraphics g, int x, int y, int w) {
        if (message.isEmpty() || System.currentTimeMillis() >= noteUntil) {
            return false;
        }
        fill(g, x, y, w, 16, messageOk ? 0xFF14352A : 0xFF3A1A1A);
        List<String> lines = wrap(message, w - 6);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            String line = i == 1 && lines.size() > 2 ? ellipsis(lines.get(1) + " " + lines.get(2), w - 6) : lines.get(i);
            small(g, line, x + 3, y + 2 + i * 7, messageOk ? OK : RED);
        }
        return true;
    }

    /** The places as short chips: a shop by name (with "+n" sub-shops), any plot, plot names, teleports. */
    private List<String> summary(CreditKind d) {
        List<String> out = new ArrayList<>();
        for (String id : d.shops) {
            String name = id;
            int subs = 0;
            for (CreditAdminPacket.Shop s : shops) {
                if (s.id().equals(id)) {
                    name = s.name();
                }
                if (s.parent().equals(id)) {
                    subs++;
                }
            }
            out.add(name + (subs > 0 ? " +" + subs : ""));
        }
        if (d.anyPlot) {
            out.add(Component.translatable("gui.bsp_core.credit.admin.any_plot_short").getString());
        }
        for (String p : d.plots) {
            int n = 0;
            for (CreditAdminPacket.PlotName pn : plots) {
                if (pn.name().equalsIgnoreCase(p)) {
                    n = pn.count();
                }
            }
            out.add(p + (n > 0 ? " · " + n : ""));
        }
        if (d.teleports) {
            out.add(Component.translatable("gui.bsp_core.credit.admin.teleports").getString());
        }
        return out;
    }

    /** One row of the pop-over's list: what it says, a count or note at the right, whether it is ticked, and what a click does. */
    private record Row(String name, String extra, boolean on, int indent, Runnable toggle) {}

    private List<Row> popRows() {
        CreditKind d = draft;
        List<Row> rows = new ArrayList<>();
        if (d == null) {
            return rows;
        }
        String q = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        if (popTab == 0) {
            for (CreditAdminPacket.Shop s : shops) { // top shops, each followed by its sub-shops
                if (!s.parent().isEmpty() && shops.stream().anyMatch(p -> p.id().equals(s.parent()))) {
                    continue;
                }
                addShop(rows, d, s, 0, q);
            }
        } else if (popTab == 1) {
            if (q.isEmpty()) {
                rows.add(new Row(Component.translatable("gui.bsp_core.credit.admin.any_plot").getString(), "", d.anyPlot, 0, () -> d.anyPlot = !d.anyPlot));
            }
            List<String> listed = new ArrayList<>();
            for (CreditAdminPacket.PlotName p : plots) {
                listed.add(p.name().toLowerCase(Locale.ROOT));
                if (q.isEmpty() || p.name().toLowerCase(Locale.ROOT).contains(q)) {
                    rows.add(plotRow(d, p.name(), p.count()));
                }
            }
            for (String p : d.plots) { // names typed in this session, not saved yet
                if (!listed.contains(p.toLowerCase(Locale.ROOT)) && (q.isEmpty() || p.toLowerCase(Locale.ROOT).contains(q))) {
                    rows.add(plotRow(d, p, 0));
                }
            }
        }
        return rows;
    }

    private Row plotRow(CreditKind d, String name, int count) {
        boolean on = d.plots.stream().anyMatch(p -> p.equalsIgnoreCase(name));
        String extra = count == 0 ? Component.translatable("gui.bsp_core.credit.admin.not_here").getString()
                : Component.translatable(count == 1 ? "gui.bsp_core.credit.admin.plot" : "gui.bsp_core.credit.admin.plots", count).getString();
        return new Row(name, extra, on, 0, () -> {
            if (!d.plots.removeIf(p -> p.equalsIgnoreCase(name))) {
                d.plots.add(name);
            }
        });
    }

    private void addShop(List<Row> rows, CreditKind d, CreditAdminPacket.Shop s, int depth, String q) {
        if (depth > 3 || rows.size() > 600) {
            return;
        }
        boolean viaParent = false;
        for (String p = s.parent(); !p.isEmpty(); ) {
            if (d.shops.contains(p)) {
                viaParent = true;
                break;
            }
            String parent = p;
            p = shops.stream().filter(x -> x.id().equals(parent)).map(CreditAdminPacket.Shop::parent).findFirst().orElse("");
            if (p.equals(parent)) {
                break;
            }
        }
        if (q.isEmpty() || s.name().toLowerCase(Locale.ROOT).contains(q) || s.id().contains(q)) {
            String extra = viaParent ? Component.translatable("gui.bsp_core.credit.admin.via_parent").getString()
                    : depth > 0 ? Component.translatable("gui.bsp_core.credit.admin.sub_shop").getString() : "";
            rows.add(new Row(s.name().isBlank() ? s.id() : s.name(), extra, d.shops.contains(s.id()), depth, () -> {
                if (!d.shops.remove(s.id())) {
                    d.shops.add(s.id());
                }
            }));
        }
        for (CreditAdminPacket.Shop c : shops) {
            if (c.parent().equals(s.id()) && !c.id().equals(s.id())) {
                addShop(rows, d, c, depth + 1, q);
            }
        }
    }

    private void popover(GuiGraphics g, int x, int y) {
        CreditKind d = draft;
        int px = x + 126, py = y + 22, pw = 212, ph = 182;
        // drawn at the panel's own depth (the form leaves out its item icons meanwhile), so the search and plot boxes show over it
        fill(g, px - 1, py - 1, pw + 2, ph + 2, 0x99000000);
        fill(g, px, py, pw, ph, VIO);
        fill(g, px + 1, py + 1, pw - 2, ph - 2, BG);
        hits.add(new Hit(px, py, pw, ph, () -> { })); // clicks inside the pop-over never reach the form under it
        small(g, Component.translatable("gui.bsp_core.credit.admin.where", d.name.isBlank() ? "?" : d.name).getString(), px + 6, py + 5, VIO & 0xFFFFFF);
        int tx = px + 6;
        String[] tabs = {Component.translatable("gui.bsp_core.credit.admin.tab_shops", d.shops.size()).getString(),
                Component.translatable("gui.bsp_core.credit.admin.tab_land", d.plots.size() + (d.anyPlot ? 1 : 0)).getString(),
                Component.translatable(d.teleports ? "gui.bsp_core.credit.admin.tab_tp_on" : "gui.bsp_core.credit.admin.tab_tp").getString()};
        for (int i = 0; i < 3; i++) {
            int t = i;
            tx += chip(g, tx, py + 14, tabs[i], popTab == i, VIO, () -> {
                popTab = t;
                popScroll = 0;
            });
        }
        if (popTab == 2) {
            tick(g, px + 6, py + 32, d.teleports, () -> d.teleports = !d.teleports);
            small(g, Component.translatable("gui.bsp_core.credit.admin.tp_line").getString(), px + 17, py + 33, d.teleports ? VIO & 0xFFFFFF : TEXT);
            hits.add(new Hit(px + 17, py + 31, 180, 9, () -> d.teleports = !d.teleports));
            int ly = py + 46;
            for (String line : wrap(Component.translatable("gui.bsp_core.credit.admin.tp_hint").getString(), pw - 24)) {
                small(g, line, px + 17, ly, MUTED);
                ly += 8;
            }
        } else {
            inset(g, px + 6, py + 29, pw - 12, 12);
            searchBox.setHint(Component.translatable(popTab == 0 ? "gui.bsp_core.credit.admin.search_shops" : "gui.bsp_core.credit.admin.search_plots").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            List<Row> rows = popRows();
            popScroll = Math.max(0, Math.min(popScroll, rows.size() - POP_ROWS));
            for (int i = 0; i < Math.min(POP_ROWS, rows.size() - popScroll); i++) {
                Row r = rows.get(i + popScroll);
                int ry = py + 46 + i * 11, ind = r.indent() * 8;
                tick(g, px + 6 + ind, ry, r.on(), r.toggle());
                String name = r.name();
                int room = pw - 26 - ind - (r.extra().isEmpty() ? 0 : sw(r.extra()) + 4);
                while (sw(name) > room && name.length() > 1) {
                    name = name.substring(0, name.length() - 1);
                }
                small(g, name, px + 17 + ind, ry + 1, r.on() ? VIO & 0xFFFFFF : TEXT);
                if (!r.extra().isEmpty()) {
                    small(g, r.extra(), px + pw - 10 - sw(r.extra()), ry + 1, MUTED);
                }
                hits.add(new Hit(px + 17 + ind, ry - 1, room, 10, r.toggle()));
            }
            if (rows.isEmpty()) {
                small(g, Component.translatable(popTab == 0 ? "gui.bsp_core.credit.admin.no_shops" : "gui.bsp_core.credit.admin.no_plots").getString(), px + 8, py + 48, MUTED);
            }
            if (rows.size() > POP_ROWS) { // the scroll bar
                int bh = 98, th = Math.max(6, bh * POP_ROWS / rows.size()), ty = py + 46 + (bh - th) * popScroll / Math.max(1, rows.size() - POP_ROWS);
                fill(g, px + pw - 6, py + 46, 3, bh, SLOT);
                fill(g, px + pw - 6, ty, 3, th, DIM);
            }
            if (popTab == 1) {
                inset(g, px + 6, py + 148, 140, 12);
                key(g, px + 150, py + 148, 30, 12, Component.translatable("gui.bsp_core.credit.admin.add").getString(), TQ, true, this::addPlot);
            } else {
                small(g, Component.translatable("gui.bsp_core.credit.admin.subs_come").getString(), px + 6, py + 151, OFF);
            }
        }
        key(g, px + pw - 44, py + ph - 18, 38, 13, Component.translatable("gui.bsp_core.credit.admin.done").getString(), TQ, true, () -> pop = false);
    }

    private List<String> wrap(String text, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : text.split(" ")) {
            String t = line.length() == 0 ? w : line + " " + w;
            if (sw(t) > max && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(w);
            } else {
                line = new StringBuilder(t);
            }
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }

    private void ticketList(GuiGraphics g, int x, int y) {
        CreditTicketsPacket t = tickets;
        int fx = x + X;
        g.drawString(host.font(), Component.translatable("gui.bsp_core.credit.admin.given", t.total()).getString(), fx, y + 26, TQ & 0xFFFFFF, false);
        String[] heads = {Component.translatable("gui.bsp_core.credit.admin.col_serial").getString(), Component.translatable("gui.bsp_core.credit.admin.col_holder").getString(),
                Component.translatable("gui.bsp_core.credit.admin.col_left").getString(), Component.translatable("gui.bsp_core.credit.admin.col_time").getString()};
        int[] cols = {0, 41, 103, 133};
        for (int i = 0; i < 4; i++) {
            small(g, heads[i], fx + cols[i], y + 40, MUTED);
        }
        fill(g, fx, y + 47, R - X, 1, DIM);
        long now = System.currentTimeMillis();
        for (int i = 0; i < Math.min(TICKET_ROWS, t.rows().size() - ticketScroll); i++) {
            CreditTicketsPacket.Row r = t.rows().get(i + ticketScroll);
            int ry = y + 51 + i * 15;
            String state;
            int sc = TEXT;
            boolean dead = false;
            if (r.voided()) {
                state = Component.translatable("gui.bsp_core.credit.admin.state_voided").getString();
                dead = true;
            } else if (r.left() <= 0) {
                state = Component.translatable("gui.bsp_core.credit.admin.state_spent").getString();
                dead = true;
            } else if (r.endsMs() > 0 && now >= r.endsMs()) {
                state = Component.translatable("gui.bsp_core.credit.admin.state_expired").getString();
                dead = true;
            } else if (r.firstUseMs() <= 0) {
                state = Component.translatable("gui.bsp_core.credit.admin.state_unused").getString();
            } else if (r.endsMs() <= 0) {
                state = Component.translatable("gui.bsp_core.credit.admin.state_no_limit").getString();
            } else {
                long left = r.endsMs() - now, h = left / 3_600_000L;
                state = h >= 24 ? h / 24 + " d " + h % 24 + " h" : h + " h " + left / 60_000L % 60 + " m";
                sc = left < 24 * 3_600_000L ? GOLD & 0xFFFFFF : TEXT;
            }
            small(g, r.serial(), fx, ry + 3, OFF);
            String holder = r.holder().isEmpty() ? Component.translatable("gui.bsp_core.credit.admin.unbound").getString() : r.holder();
            small(g, ellipsis(holder, 60), fx + 41, ry + 3, r.holder().isEmpty() ? MUTED : TEXT);
            small(g, Bank.format(r.left()), fx + 103, ry + 3, dead ? OFF : GOLD & 0xFFFFFF);
            small(g, state, fx + 133, ry + 3, dead ? OFF : sc);
            if (!dead) {
                String serial = r.serial();
                key(g, x + R - 26, ry, 26, 12, Component.translatable(arming("void " + serial) ? "gui.bsp_core.credit.admin.sure" : "gui.bsp_core.credit.admin.void").getString(), BAD, true, () -> {
                    if (confirmed("void " + serial)) {
                        host.send(new CreditAdminActionPacket(Action.VOID, serial, new CompoundTag()));
                    }
                });
            }
        }
        if (t.rows().isEmpty()) {
            small(g, Component.translatable("gui.bsp_core.credit.admin.no_tickets").getString(), fx, y + 54, MUTED);
        }
        key(g, fx, y + 186, 40, 13, Component.translatable("gui.bsp_core.credit.admin.back").getString(), TQ, true, () -> showTickets = false);
        if (!answer(g, fx, y + 168, R - X)) {
            small(g, Component.translatable("gui.bsp_core.credit.admin.void_hint").getString(), fx + 46, y + 190, OFF);
        }
    }
}
