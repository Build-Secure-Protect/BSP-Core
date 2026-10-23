package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.kit.Kit;
import com.mrgregles.bsp_core.kit.Kits;
import com.mrgregles.bsp_core.network.KitAdminPacket;
import com.mrgregles.bsp_core.network.KitEditPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * The admin panel's KITS tab: the kit list beside the chosen kit's summary, and the editor that
 * EDIT or NEW KIT turns the panel into (the kit's slots, the item browser with a search line and mod
 * chips, the settings rows, SAVE / TEST ON ME / DELETE / CLOSE). Drawn by {@link AdminScreen}, which
 * hands it the panel's corner, its clicks, wheel and keys. Everything the tab shows comes from the
 * server's {@link KitAdminPacket}; every change goes back as a {@link KitEditPacket}.
 */
public final class KitAdminTab {
    /** What the tab needs from the screen that hosts it. */
    interface Host {
        Font font();

        void addWidget(EditBox box);

        void send(KitEditPacket packet);

        boolean canEdit();
    }

    static final int W = 344, H_EDIT = 310;
    private static final int BG = 0xF010151C, SLOT = 0xFF0C0E12, DIM = 0xFF2A2F3A, VIO = 0xFFB58CFF, TQ = 0xFF19D3B0, BAD = 0xFFD63B2F, GOLD = 0xFFFFD23A,
            MUTED = 0x9AA3B5, TEXT = 0xE6EAF2, HOVER = 0xFF2A2140, OK = 0x58F29A;
    private static final int LIST_X = 10, LIST_Y = 26, LIST_W = 112, ROW_H = 16, ROWS = 9, DX = 130, KEY_Y = 182, KEY_H = 18;
    private static final int SLOT_X = 8, SLOT_Y = 30, BROWSE_X = 180, BROWSE_Y = 56, BROWSE_COLS = 8, BROWSE_ROWS = 4, SET_Y = 146, EDIT_KEY_Y = 284;
    private static final long CONFIRM_MS = 3000, NOTE_MS = 4000;

    private record Hit(int x, int y, int w, int h, Runnable run) {}

    private enum Adding { NONE, SERVER, GRANT_SERVER, RANK }

    private final Host host;
    private final List<Hit> hits = new ArrayList<>();

    // what the server sent
    private final List<Kit> kits = new ArrayList<>();
    private List<String> servers = List.of(), ranks = List.of();
    private boolean ladder;
    private List<KitAdminPacket.Online> online = List.of();
    private String note = "";
    private long noteUntil;

    // the list
    @Nullable
    private String selected;
    private int listScroll;
    private boolean giving;
    private String confirm = "";
    private long confirmUntil;

    // the editor
    private boolean editing, isNew, pickingIcon;
    @Nullable
    private Kit draft;
    private int cdUnit = 1;
    private Adding adding = Adding.NONE;
    private String query = "\u0000", namespace = "";
    private List<Item> found = List.of();
    private List<String> namespaces = List.of();
    private int browseScroll, nsPage;
    @Nullable
    private Item hovered;
    private EditBox search, nameBox, descBox, priceBox, cdBox, addBox;
    private int left, top;

    KitAdminTab(Host host) {
        this.host = host;
    }

    boolean editing() {
        return editing;
    }

    // ------------------------------------------------------------------ data

    void take(KitAdminPacket msg) {
        kits.clear();
        for (CompoundTag t : msg.kits()) {
            Kit k = Kit.load(t);
            k.missing = t.getInt("Missing");
            kits.add(k);
        }
        servers = msg.servers();
        ranks = msg.ranks();
        ladder = msg.ladder();
        online = msg.online();
        if (!msg.note().isEmpty()) {
            note = msg.note();
            noteUntil = System.currentTimeMillis() + NOTE_MS;
            if (note.equals("saved") || note.equals("deleted")) {
                if (note.equals("deleted")) {
                    selected = null;
                }
                if (editing && note.equals("saved") && draft != null) {
                    isNew = false;
                    selected = draft.id;
                }
                if (note.equals("deleted")) {
                    closeEditor();
                }
            }
        }
        if (selected == null && !kits.isEmpty()) {
            selected = kits.get(0).id;
        }
    }

    void refresh() {
        host.send(KitEditPacket.refresh());
    }

    @Nullable
    private Kit selectedKit() {
        for (Kit k : kits) {
            if (k.id.equals(selected)) {
                return k;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ widgets

    void init(int left, int top) {
        this.left = left;
        this.top = top;
        Font font = host.font();
        String q = search == null ? "" : search.getValue(), n = nameBox == null ? "" : nameBox.getValue(), d = descBox == null ? "" : descBox.getValue(),
                p = priceBox == null ? "" : priceBox.getValue(), c = cdBox == null ? "" : cdBox.getValue(), a = addBox == null ? "" : addBox.getValue();
        search = box(font, 0, 0, 152, 64, tr("search_hint"), q);
        search.setResponder(s -> browseScroll = 0);
        nameBox = box(font, 0, 0, 88, Kit.NAME_MAX, tr("name"), n);
        descBox = box(font, 0, 0, 272, Kit.DESCRIPTION_MAX, tr("describe"), d);
        priceBox = box(font, 0, 0, 42, 9, Component.literal("0"), p);
        priceBox.setFilter(s -> s.matches("\\d{0,9}"));
        cdBox = box(font, 0, 0, 22, 3, Component.literal("1"), c);
        cdBox.setFilter(s -> s.matches("\\d{0,3}"));
        addBox = box(font, left, top, 60, 32, tr("v.add_hint"), a);
        place(left, top);
        setVisible(false);
    }

    /** Puts the editor's boxes on their fields of a panel whose corner is {@code left, top}: the editor's panel is taller than the list's, so its corner moves. */
    private void place(int left, int top) {
        search.setPosition(left + BROWSE_X + 2, top + 31);
        nameBox.setPosition(left + 58, top + 150);
        descBox.setPosition(left + 58, top + 165);
        priceBox.setPosition(left + 58, top + 225);
        cdBox.setPosition(left + 236, top + 210);
    }

    private EditBox box(Font font, int x, int y, int w, int max, Component hint, String value) {
        EditBox b = new EditBox(font, x, y, w, 10, hint);
        b.setMaxLength(max);
        b.setBordered(false);
        b.setHint(hint.copy().withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        b.setValue(value);
        host.addWidget(b);
        return b;
    }

    /** Shows the editor's boxes while the editor is open on this tab, the search box included. */
    void setVisible(boolean on) {
        boolean e = on && editing;
        for (EditBox b : new EditBox[] {search, nameBox, descBox, priceBox, cdBox}) {
            if (b != null) {
                b.visible = e;
                b.active = e;
                if (!e) {
                    b.setFocused(false);
                }
            }
        }
        if (addBox != null) {
            addBox.visible = e && adding != Adding.NONE;
            addBox.active = addBox.visible;
            if (!addBox.visible) {
                addBox.setFocused(false);
            }
        }
    }

    // ------------------------------------------------------------------ the editor's draft

    private void openEditor(@Nullable Kit kit) {
        isNew = kit == null;
        draft = kit == null ? new Kit("", tr("new_name").getString(), System.currentTimeMillis()) : kit.copy();
        if (isNew) {
            draft.name = "";
        }
        editing = true;
        pickingIcon = false;
        adding = Adding.NONE;
        confirm = "";
        nameBox.setValue(draft.name);
        descBox.setValue(draft.description);
        priceBox.setValue(draft.price > 0 ? Long.toString(draft.price) : "");
        long cd = draft.cooldownSeconds;
        cdUnit = cd > 0 && cd % 86400 == 0 ? 2 : cd > 0 && cd % 3600 == 0 ? 1 : 0;
        long n = cd <= 0 ? 1 : cd / (cdUnit == 2 ? 86400 : cdUnit == 1 ? 3600 : 60);
        cdBox.setValue(Long.toString(Math.max(1, n)));
        setVisible(true);
        if (isNew) {
            nameBox.setFocused(true);
        }
    }

    private void closeEditor() {
        editing = false;
        draft = null;
        adding = Adding.NONE;
        pickingIcon = false;
        setVisible(false);
    }

    /** The draft with the boxes read back; the id of a new kit comes from its name. */
    private Kit readDraft() {
        Kit d = draft;
        d.name = nameBox.getValue().trim();
        d.description = descBox.getValue().trim();
        d.price = priceBox.getValue().isEmpty() ? 0 : Long.parseLong(priceBox.getValue());
        long n = cdBox.getValue().isEmpty() ? 0 : Long.parseLong(cdBox.getValue());
        d.cooldownSeconds = d.once ? 0 : n * (cdUnit == 2 ? 86400 : cdUnit == 1 ? 3600 : 60);
        if (isNew) {
            Kit withId = new Kit(slug(d.name), d.name, d.createdMs);
            withId.description = d.description;
            withId.icon = d.icon;
            withId.items.addAll(d.items);
            withId.inBrowser = d.inBrowser;
            withId.once = d.once;
            withId.cooldownSeconds = d.cooldownSeconds;
            withId.price = d.price;
            withId.ranks.addAll(d.ranks);
            withId.servers.addAll(d.servers);
            withId.grant = d.grant;
            withId.grantServers.addAll(d.grantServers);
            withId.enabled = d.enabled;
            draft = withId;
            return withId;
        }
        return d;
    }

    static String slug(String name) {
        String s = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (s.length() > 24) {
            s = s.substring(0, 24);
        }
        return s.length() < 2 ? "kit_" + s : s;
    }

    private void save() {
        Kit d = readDraft();
        host.send(KitEditPacket.save(d.save(), isNew));
    }

    private void addToDraft(ItemStack stack) {
        if (draft == null || stack.isEmpty()) {
            return;
        }
        if (pickingIcon) {
            draft.icon = stack.copyWithCount(1);
            pickingIcon = false;
            return;
        }
        ItemStack s = stack.copy();
        for (ItemStack have : draft.items) {
            if (ItemStack.isSameItemSameTags(have, s) && have.getCount() < have.getMaxStackSize()) {
                int move = Math.min(s.getCount(), have.getMaxStackSize() - have.getCount());
                have.grow(move);
                s.shrink(move);
                if (s.isEmpty()) {
                    return;
                }
            }
        }
        while (!s.isEmpty() && draft.items.size() < Kits.maxItems()) {
            ItemStack placed = s.copyWithCount(Math.min(s.getCount(), s.getMaxStackSize()));
            draft.items.add(placed);
            s.shrink(placed.getCount());
        }
    }

    // ------------------------------------------------------------------ the browser's items

    private List<Item> results() {
        String q = search.getValue().trim().toLowerCase(Locale.ROOT);
        if (q.equals(query) && found != null && !namespaceChanged) {
            return found;
        }
        query = q;
        namespaceChanged = false;
        List<Item> out = new ArrayList<>();
        for (Item it : ForgeRegistries.ITEMS) {
            if (it == Items.AIR) {
                continue;
            }
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(it);
            if (id == null || (!namespace.isEmpty() && !id.getNamespace().equals(namespace))) {
                continue;
            }
            if (q.isEmpty() || id.toString().contains(q) || new ItemStack(it).getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(it);
                if (out.size() >= 2000) {
                    break;
                }
            }
        }
        found = out;
        return out;
    }

    private boolean namespaceChanged;

    private List<String> namespaces() {
        if (namespaces.isEmpty()) {
            TreeSet<String> set = new TreeSet<>();
            for (ResourceLocation id : ForgeRegistries.ITEMS.getKeys()) {
                set.add(id.getNamespace());
            }
            List<String> out = new ArrayList<>();
            if (set.remove("minecraft")) {
                out.add("minecraft");
            }
            if (set.remove("bsp_core")) {
                out.add("bsp_core");
            }
            out.addAll(set);
            namespaces = out;
        }
        return namespaces;
    }

    // ------------------------------------------------------------------ input

    boolean mouseClicked(double mx, double my, int button) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (mx >= h.x && mx < h.x + h.w && my >= h.y && my < h.y + h.h) {
                h.run.run();
                return true;
            }
        }
        return false;
    }

    boolean mouseScrolled(double mx, double my, double delta) {
        int step = (int) Math.signum(delta);
        if (!editing) {
            listScroll = Mth.clamp(listScroll - step, 0, Math.max(0, kits.size() - ROWS));
            return true;
        }
        if (draft != null && over(mx, my, left + SLOT_X, top + SLOT_Y, 9 * 18, 4 * 18)) {
            int col = (int) ((mx - left - SLOT_X) / 18), row = (int) ((my - top - SLOT_Y) / 18), index = row * 9 + col;
            if (index < draft.items.size()) {
                ItemStack s = draft.items.get(index);
                int by = Screen.hasShiftDown() ? 8 : 1;
                s.setCount(Mth.clamp(s.getCount() + step * by, 1, s.getMaxStackSize()));
            }
            return true;
        }
        if (over(mx, my, left + BROWSE_X, top + BROWSE_Y, BROWSE_COLS * 18 + 6, BROWSE_ROWS * 18)) {
            int total = (results().size() + BROWSE_COLS - 1) / BROWSE_COLS;
            browseScroll = Mth.clamp(browseScroll - step, 0, Math.max(0, total - BROWSE_ROWS));
            return true;
        }
        return false;
    }

    boolean keyPressed(int key, int scan, int mods) {
        if (!editing) {
            return false;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (addBox.isFocused() && adding != Adding.NONE) {
                String v = addBox.getValue().trim().toLowerCase(Locale.ROOT);
                if (!v.isEmpty() && draft != null) {
                    List<String> into = adding == Adding.RANK ? draft.ranks : adding == Adding.SERVER ? draft.servers : draft.grantServers;
                    if (!into.contains(v)) {
                        into.add(v);
                    }
                }
                addBox.setValue("");
                adding = Adding.NONE;
                setVisible(true);
                return true;
            }
            for (EditBox b : new EditBox[] {nameBox, descBox, priceBox, cdBox, search}) {
                if (b.isFocused()) {
                    b.setFocused(false);
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean confirmed(String id) {
        long now = System.currentTimeMillis();
        if (confirm.equals(id) && now < confirmUntil) {
            confirm = "";
            return true;
        }
        confirm = id;
        confirmUntil = now + CONFIRM_MS;
        return false;
    }

    private boolean arming(String id) {
        return confirm.equals(id) && System.currentTimeMillis() < confirmUntil;
    }

    // ------------------------------------------------------------------ drawing helpers

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.bsp_core.kit." + key, args);
    }

    private void small(GuiGraphics g, Component text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(host.font(), text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private int smallWidth(Component text) {
        return Math.round(host.font().width(text) * 0.75f);
    }

    private void key(GuiGraphics g, int x, int y, int w, int h, Component label, boolean enabled, int colour, int mx, int my, Runnable run) {
        boolean hover = enabled && over(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, enabled ? colour : DIM);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? HOVER : SLOT);
        int c = enabled ? colour & 0xFFFFFF : 0x6B7385;
        if (h < 14) {
            small(g, label, x + w / 2 - smallWidth(label) / 2, y + (h - 6) / 2, c);
        } else {
            g.drawString(host.font(), label, x + w / 2 - host.font().width(label) / 2, y + (h - 8) / 2, c, false);
        }
        if (enabled) {
            hits.add(new Hit(x, y, w, h, run));
        }
    }

    /** A small chip; returns its width. */
    private int chip(GuiGraphics g, int x, int y, Component label, boolean on, boolean enabled, int mx, int my, Runnable run) {
        int w = smallWidth(label) + 8, h = 11;
        boolean hover = enabled && over(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, on ? VIO : hover ? MUTED | 0xFF000000 : DIM);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, on ? 0xFF2A2140 : SLOT);
        small(g, label, x + 4, y + 3, on ? VIO & 0xFFFFFF : enabled ? TEXT : 0x6B7385);
        if (enabled) {
            hits.add(new Hit(x, y, w, h, run));
        }
        return w + 3;
    }

    private void inset(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, DIM);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, SLOT);
    }

    private void slot(GuiGraphics g, int x, int y, boolean hover) {
        g.fill(x, y, x + 18, y + 18, DIM);
        g.fill(x + 1, y + 1, x + 17, y + 17, hover ? HOVER : SLOT);
    }

    private String ruleLine(Kit k) {
        String given = switch (k.grant) {
            case NONE -> tr("v.never").getString();
            case NETWORK -> tr("v.network").getString();
            case SERVERS -> "first join · " + (k.grantServers.isEmpty() ? tr("v.every_server").getString() : String.join(" ", k.grantServers));
        };
        String take = k.once ? tr("v.once").getString() : k.cooldownSeconds > 0 ? tr("v.every").getString() + " " + Kits.span(k.cooldownSeconds) : "any time";
        return tr("given").getString() + " " + given + "   " + tr("take").getString() + " " + take + "   " + tr("browser").getString() + " " + tr(k.inBrowser ? "v.shown" : "v.hidden").getString();
    }

    private String ruleLine2(Kit k) {
        return tr("ranks").getString() + " " + (k.ranks.isEmpty() ? tr("v.anyone").getString() : String.join(", ", k.ranks)) + "   " + tr("servers").getString() + " "
                + (k.servers.isEmpty() ? tr("v.everywhere").getString() : String.join(", ", k.servers)) + "   " + tr("price").getString() + " "
                + (k.price > 0 ? com.mrgregles.bsp_core.api.BspBank.format(k.price) : tr("v.free").getString());
    }

    // ------------------------------------------------------------------ drawing

    /** The tab's title piece, drawn by the host beside the tabs. */
    Component title() {
        return editing && draft != null ? tr("title", isNew ? nameBox.getValue().isEmpty() ? tr("new_name").getString() : nameBox.getValue() : draft.name) : Component.empty();
    }

    void render(GuiGraphics g, int x, int y, int mx, int my) {
        hits.clear();
        if (x != left || y != top) {
            place(x, y);
        }
        left = x;
        top = y;
        if (editing && draft != null) {
            editor(g, x, y, mx, my);
        } else {
            list(g, x, y, mx, my);
        }
        if (!note.isEmpty() && System.currentTimeMillis() < noteUntil) {
            Component n = tr("note." + note);
            small(g, n, x + W - 10 - smallWidth(n), y + (editing ? EDIT_KEY_Y : KEY_Y) - 10, note.equals("refused") || note.equals("id_taken") || note.equals("read_only") ? 0xFF8F86 : OK);
        }
    }

    private void list(GuiGraphics g, int x, int y, int mx, int my) {
        boolean edit = host.canEdit();
        Font font = host.font();
        listScroll = Mth.clamp(listScroll, 0, Math.max(0, kits.size() - ROWS));
        Kit sel = selectedKit();
        inset(g, x + LIST_X, y + LIST_Y, LIST_W, ROWS * ROW_H + 2);
        for (int i = 0; i < ROWS && i + listScroll < kits.size(); i++) {
            Kit k = kits.get(i + listScroll);
            int ry = y + LIST_Y + 1 + i * ROW_H;
            boolean on = k == sel, hover = over(mx, my, x + LIST_X, ry, LIST_W, ROW_H);
            if (on || hover) {
                g.fill(x + LIST_X + 1, ry, x + LIST_X + LIST_W - 1, ry + ROW_H, on ? 0xFF1B2230 : DIM);
            }
            if (on) {
                g.fill(x + LIST_X + 1, ry, x + LIST_X + 3, ry + ROW_H, VIO);
            }
            ItemStack icon = k.iconOrFirst();
            if (!icon.isEmpty()) {
                g.renderItem(icon, x + LIST_X + 5, ry);
            }
            g.drawString(font, font.plainSubstrByWidth(k.name, LIST_W - 30), x + LIST_X + 24, ry + 4, k.enabled ? TEXT : MUTED, false);
            if (!k.enabled) {
                small(g, tr("v.off"), x + LIST_X + LIST_W - 6 - smallWidth(tr("v.off")), ry + 5, 0xFF8F86);
            }
            final Kit pick = k;
            hits.add(new Hit(x + LIST_X, ry, LIST_W, ROW_H, () -> {
                selected = pick.id;
                giving = false;
                confirm = "";
            }));
        }
        if (kits.size() > ROWS) {
            int track = ROWS * ROW_H, knob = Math.max(8, track * ROWS / kits.size()), ky = (track - knob) * listScroll / (kits.size() - ROWS);
            g.fill(x + LIST_X + LIST_W + 2, y + LIST_Y + ky, x + LIST_X + LIST_W + 4, y + LIST_Y + ky + knob, DIM);
        }
        int taken = 0;
        for (Kit k : kits) {
            taken += k.taken;
        }
        small(g, kits.isEmpty() ? tr("none") : tr("count", kits.size(), taken), x + LIST_X, y + LIST_Y + ROWS * ROW_H + 5, MUTED);

        inset(g, x + DX, y + LIST_Y, W - 10 - DX, ROWS * ROW_H + 2);
        if (sel == null) {
            small(g, tr("pick"), x + DX + 6, y + LIST_Y + 6, MUTED);
        } else if (giving) {
            g.drawString(font, tr("give_who", sel.name), x + DX + 6, y + LIST_Y + 6, VIO & 0xFFFFFF, false);
            int cx = x + DX + 6, cy = y + LIST_Y + 22;
            if (online.isEmpty()) {
                small(g, tr("nobody_online"), cx, cy, MUTED);
            }
            for (KitAdminPacket.Online o : online) {
                Component name = Component.literal(o.name());
                int w = smallWidth(name) + 8;
                if (cx + w > x + W - 14) {
                    cx = x + DX + 6;
                    cy += 14;
                }
                final KitAdminPacket.Online who = o;
                cx += chip(g, cx, cy, name, false, edit, mx, my, () -> {
                    host.send(KitEditPacket.give(sel.id, who.id()));
                    giving = false;
                });
            }
        } else {
            g.drawString(font, font.plainSubstrByWidth(sel.name, W - 20 - DX), x + DX + 6, y + LIST_Y + 5, VIO & 0xFFFFFF, false);
            small(g, Component.literal(font.plainSubstrByWidth(sel.description, (int) ((W - 20 - DX) / 0.75f))), x + DX + 6, y + LIST_Y + 16, MUTED);
            int gx = x + DX + 6, gy = y + LIST_Y + 26;
            for (int i = 0; i < Math.min(18, sel.items.size()); i++) {
                int sx = gx + (i % 9) * 18, sy = gy + (i / 9) * 18;
                slot(g, sx, sy, false);
                g.renderItem(sel.items.get(i), sx + 1, sy + 1);
                g.renderItemDecorations(font, sel.items.get(i), sx + 1, sy + 1);
            }
            if (sel.items.size() > 18) {
                small(g, Component.literal("+" + (sel.items.size() - 18)), gx + 9 * 18 + 3, gy + 24, MUTED);
            }
            int ruleW = (int) ((W - 22 - DX) / 0.75f);
            small(g, Component.literal(CashMachine.cut(font, ruleLine(sel), ruleW)), x + DX + 6, gy + 40, TEXT);
            small(g, Component.literal(CashMachine.cut(font, ruleLine2(sel), ruleW)), x + DX + 6, gy + 50, TEXT);
            small(g, sel.lastTakenBy.isEmpty() ? tr("taken", sel.taken) : tr("taken_last", sel.taken, sel.lastTakenBy), x + DX + 6, gy + 64, MUTED);
            int cy = y + LIST_Y + ROWS * ROW_H - 12;
            chip(g, x + DX + 6, cy, tr(sel.enabled ? "enabled" : "v.off"), sel.enabled, false, mx, my, () -> {});
            if (sel.missing > 0) {
                small(g, tr("missing", sel.missing), x + DX + 6 + smallWidth(tr(sel.enabled ? "enabled" : "v.off")) + 14, cy + 3, 0xFF8F86);
            }
        }
        int kw = 62;
        key(g, x + LIST_X, y + KEY_Y, kw, KEY_H, tr("k.new"), edit, VIO, mx, my, () -> openEditor(null));
        key(g, x + LIST_X + kw + 4, y + KEY_Y, kw, KEY_H, tr("k.edit"), edit && sel != null, TQ, mx, my, () -> openEditor(sel));
        key(g, x + LIST_X + 2 * (kw + 4), y + KEY_Y, kw, KEY_H, tr("k.give"), edit && sel != null, TQ, mx, my, () -> giving = !giving);
        key(g, x + LIST_X + 3 * (kw + 4) + 4, y + KEY_Y, kw, KEY_H, tr(arming("delete") ? "k.sure" : "k.delete"), edit && sel != null, BAD, mx, my, () -> {
            if (sel != null && confirmed("delete")) {
                host.send(KitEditPacket.of(KitEditPacket.Action.DELETE, sel.id));
            }
        });
    }

    private void editor(GuiGraphics g, int x, int y, int mx, int my) {
        Kit d = draft;
        Font font = host.font();
        boolean edit = host.canEdit();
        // the kit's slots
        small(g, tr("items_of", d.items.size(), Kits.maxItems()), x + SLOT_X, y + 22, MUTED);
        for (int i = 0; i < 36; i++) {
            int sx = x + SLOT_X + (i % 9) * 18, sy = y + SLOT_Y + (i / 9) * 18;
            boolean hover = over(mx, my, sx, sy, 18, 18);
            slot(g, sx, sy, hover && i < d.items.size());
            if (i < d.items.size()) {
                ItemStack s = d.items.get(i);
                g.renderItem(s, sx + 1, sy + 1);
                g.renderItemDecorations(font, s, sx + 1, sy + 1);
                final int index = i;
                hits.add(new Hit(sx, sy, 18, 18, () -> {
                    if (pickingIcon) {
                        d.icon = d.items.get(index).copyWithCount(1);
                        pickingIcon = false;
                    } else {
                        d.items.remove(index);
                    }
                }));
            }
        }
        small(g, pickingIcon ? tr("pick_icon") : tr("slot_hint"), x + SLOT_X, y + SLOT_Y + 4 * 18 + 3, pickingIcon ? GOLD & 0xFFFFFF : MUTED);
        key(g, x + SLOT_X, y + 113, 79, 13, tr("k.from_hand"), edit, TQ, mx, my, () -> {
            var mc = Minecraft.getInstance();
            if (mc.player != null) {
                addToDraft(mc.player.getMainHandItem());
            }
        });
        key(g, x + SLOT_X + 83, y + 113, 79, 13, tr("k.from_inventory"), edit, TQ, mx, my, () -> {
            var mc = Minecraft.getInstance();
            if (mc.player != null) {
                d.items.clear();
                for (ItemStack s : mc.player.getInventory().items) {
                    if (!s.isEmpty()) {
                        addToDraft(s);
                    }
                }
            }
        });

        // the browser
        small(g, tr("add_items"), x + BROWSE_X, y + 22, MUTED);
        inset(g, x + BROWSE_X, y + 29, 156, 12);
        int cx = x + BROWSE_X, cy = y + 43;
        List<String> ns = namespaces();
        cx += chip(g, cx, cy, Component.literal("all"), namespace.isEmpty(), true, mx, my, () -> setNamespace(""));
        int perPage = 0, start = nsPage * 4;
        for (int i = start; i < ns.size() && perPage < 4; i++) {
            String n = ns.get(i);
            Component label = Component.literal(n.length() > 10 ? n.substring(0, 9) + "…" : n);
            int w = smallWidth(label) + 11;
            if (cx + w > x + BROWSE_X + 140) {
                break;
            }
            cx += chip(g, cx, cy, label, namespace.equals(n), true, mx, my, () -> setNamespace(n));
            perPage++;
        }
        if (ns.size() > 4) {
            chip(g, x + BROWSE_X + 144, cy, Component.literal("›"), false, true, mx, my, () -> nsPage = (start + 4 >= ns.size()) ? 0 : nsPage + 1);
        }
        List<Item> items = results();
        int total = (items.size() + BROWSE_COLS - 1) / BROWSE_COLS;
        browseScroll = Mth.clamp(browseScroll, 0, Math.max(0, total - BROWSE_ROWS));
        hovered = null;
        for (int i = 0; i < BROWSE_ROWS * BROWSE_COLS; i++) {
            int index = i + browseScroll * BROWSE_COLS;
            int sx = x + BROWSE_X + (i % BROWSE_COLS) * 18, sy = y + BROWSE_Y + (i / BROWSE_COLS) * 18;
            boolean has = index < items.size(), hover = has && over(mx, my, sx, sy, 18, 18);
            slot(g, sx, sy, hover);
            if (!has) {
                continue;
            }
            Item item = items.get(index);
            ItemStack stack = new ItemStack(item);
            g.renderItem(stack, sx + 1, sy + 1);
            if (hover) {
                hovered = item;
            }
            hits.add(new Hit(sx, sy, 18, 18, () -> addToDraft(new ItemStack(item, pickingIcon ? 1 : Screen.hasShiftDown() ? stack.getMaxStackSize() : 1))));
        }
        if (total > BROWSE_ROWS) {
            int track = BROWSE_ROWS * 18, knob = Math.max(8, track * BROWSE_ROWS / total), ky = (track - knob) * browseScroll / (total - BROWSE_ROWS);
            g.fill(x + BROWSE_X + BROWSE_COLS * 18 + 2, y + BROWSE_Y + ky, x + BROWSE_X + BROWSE_COLS * 18 + 4, y + BROWSE_Y + ky + knob, MUTED | 0xFF000000);
        }
        if (items.isEmpty()) {
            small(g, tr("no_match"), x + BROWSE_X + 4, y + BROWSE_Y + 6, MUTED);
        }
        if (hovered != null) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(hovered);
            String line = new ItemStack(hovered).getHoverName().getString() + " · " + id;
            small(g, Component.literal(font.plainSubstrByWidth(line, 204)), x + BROWSE_X, y + BROWSE_Y + BROWSE_ROWS * 18 + 3, TEXT);
        } else {
            small(g, tr("browse_hint"), x + BROWSE_X, y + BROWSE_Y + BROWSE_ROWS * 18 + 3, MUTED);
        }

        // the settings
        inset(g, x + SLOT_X, y + SET_Y, W - 16, 130);
        int lx = x + 14;
        small(g, tr("name"), lx, y + 151, MUTED);
        inset(g, x + 56, y + 148, 92, 12);
        if (isNew) {
            small(g, Component.literal(CashMachine.cut(font, slug(nameBox.getValue()), (int) (44 / 0.75f))), x + 152, y + 151, 0x6B7385); // clear of "Icon"
        }
        small(g, tr("icon"), x + 200, y + 151, MUTED);
        cx = x + 226;
        cx += chip(g, cx, y + 148, tr("v.first_slot"), d.icon.isEmpty() && !pickingIcon, edit, mx, my, () -> {
            d.icon = ItemStack.EMPTY;
            pickingIcon = false;
        });
        cx += chip(g, cx, y + 148, tr("v.pick"), pickingIcon, edit, mx, my, () -> pickingIcon = !pickingIcon);
        if (!d.icon.isEmpty()) {
            g.renderItem(d.icon, cx + 2, y + 146);
        }
        small(g, tr("describe"), lx, y + 166, MUTED);
        inset(g, x + 56, y + 163, 276, 12);

        small(g, tr("given"), lx, y + 181, MUTED);
        cx = x + 56;
        cx += chip(g, cx, y + 178, tr("v.never"), d.grant == Kit.Grant.NONE, edit, mx, my, () -> d.grant = Kit.Grant.NONE);
        cx += chip(g, cx, y + 178, tr("v.network"), d.grant == Kit.Grant.NETWORK, edit, mx, my, () -> d.grant = Kit.Grant.NETWORK);
        chip(g, cx, y + 178, tr("v.servers"), d.grant == Kit.Grant.SERVERS, edit, mx, my, () -> d.grant = Kit.Grant.SERVERS);

        small(g, tr("grant_on"), lx, y + 196, d.grant == Kit.Grant.SERVERS ? MUTED : 0x4A5160);
        if (d.grant == Kit.Grant.SERVERS) {
            chips(g, x + 56, y + 193, tr("v.every_server"), d.grantServers, servers, Adding.GRANT_SERVER, edit, mx, my);
        }

        small(g, tr("browser"), lx, y + 211, MUTED);
        cx = x + 56;
        cx += chip(g, cx, y + 208, tr("v.shown"), d.inBrowser, edit, mx, my, () -> d.inBrowser = true);
        chip(g, cx, y + 208, tr("v.hidden"), !d.inBrowser, edit, mx, my, () -> d.inBrowser = false);
        small(g, tr("take"), x + 150, y + 211, MUTED);
        cx = x + 172;
        cx += chip(g, cx, y + 208, tr("v.once"), d.once, edit, mx, my, () -> d.once = true);
        chip(g, cx, y + 208, tr("v.every"), !d.once, edit, mx, my, () -> d.once = false);
        inset(g, x + 234, y + 208, 26, 12); // clear of the "every" chip
        cdBox.visible = cdBox.active = !d.once;
        cx = x + 264;
        String[] units = {"v.min", "v.h", "v.d"};
        for (int u = 0; u < 3; u++) {
            final int unit = u;
            cx += chip(g, cx, y + 208, tr(units[u]), !d.once && cdUnit == u, edit && !d.once, mx, my, () -> cdUnit = unit);
        }

        small(g, tr("price"), lx, y + 226, MUTED);
        inset(g, x + 56, y + 223, 46, 12);
        small(g, tr("v.pieces"), x + 106, y + 226, MUTED);
        small(g, tr("servers"), x + 150, y + 226, MUTED);
        chips(g, x + 190, y + 223, tr("v.everywhere"), d.servers, servers, Adding.SERVER, edit, mx, my);

        small(g, tr("ranks"), lx, y + 246, MUTED);
        if (ladder && !ranks.isEmpty()) {
            ladder(g, x + 56, y + 238, d, edit, mx, my);
        } else {
            chips(g, x + 56, y + 243, tr("v.anyone"), d.ranks, ranks, Adding.RANK, edit, mx, my);
        }

        small(g, tr("enabled"), lx, y + 269, MUTED);
        cx = x + 56;
        cx += chip(g, cx, y + 266, tr("v.on"), d.enabled, edit, mx, my, () -> d.enabled = true);
        chip(g, cx, y + 266, tr("v.off"), !d.enabled, edit, mx, my, () -> d.enabled = false);
        if (!isNew) {
            small(g, d.lastTakenBy.isEmpty() ? tr("taken", d.taken) : tr("taken_last", d.taken, d.lastTakenBy), x + 150, y + 269, MUTED);
        }

        // the keys
        key(g, x + SLOT_X, y + EDIT_KEY_Y, 62, KEY_H, tr("k.save"), edit, VIO, mx, my, this::save);
        key(g, x + SLOT_X + 66, y + EDIT_KEY_Y, 70, KEY_H, tr("k.test"), edit && !isNew, TQ, mx, my, () -> {
            save();
            host.send(KitEditPacket.of(KitEditPacket.Action.TEST, d.id));
        });
        key(g, x + SLOT_X + 140, y + EDIT_KEY_Y, 62, KEY_H, tr(arming("delete") ? "k.sure" : "k.delete"), edit && !isNew, BAD, mx, my, () -> {
            if (confirmed("delete")) {
                host.send(KitEditPacket.of(KitEditPacket.Action.DELETE, d.id));
            }
        });
        key(g, x + W - 8 - 62, y + EDIT_KEY_Y, 62, KEY_H, tr("k.close"), true, MUTED | 0xFF000000, mx, my, this::closeEditor);
    }

    private void setNamespace(String n) {
        namespace = n;
        namespaceChanged = true;
        browseScroll = 0;
    }

    /** A row of toggling chips: the "none" chip, every known value, the draft's own extra values, and + for typing one. */
    private void chips(GuiGraphics g, int x, int y, Component noneLabel, List<String> chosen, List<String> known, Adding kind, boolean edit, int mx, int my) {
        int cx = x;
        cx += chip(g, cx, y, noneLabel, chosen.isEmpty(), edit, mx, my, chosen::clear);
        List<String> all = new ArrayList<>(known);
        for (String c : chosen) {
            if (!all.contains(c)) {
                all.add(c);
            }
        }
        int limit = left + W - 12 - 66; // room left for the + chip or the 62-wide box it opens
        for (String v : all) {
            Component label = Component.literal(v.length() > 12 ? v.substring(0, 11) + "…" : v);
            int w = smallWidth(label) + 11;
            if (cx + w > limit) {
                break;
            }
            cx += chip(g, cx, y, label, chosen.contains(v), edit, mx, my, () -> {
                if (!chosen.remove(v)) {
                    chosen.add(v);
                }
            });
        }
        if (adding == kind) {
            inset(g, cx, y, 62, 12);
            addBox.setX(cx + 2);
            addBox.setY(y + 2);
            addBox.visible = addBox.active = true;
        } else {
            chip(g, cx, y, tr("v.add"), false, edit, mx, my, () -> {
                adding = kind;
                addBox.setValue("");
                addBox.visible = addBox.active = true;
                addBox.setFocused(true);
            });
        }
    }

    /** RankUp's ladder as rising steps; the chosen step and every step above it are lit. */
    private void ladder(GuiGraphics g, int x, int y, Kit d, boolean edit, int mx, int my) {
        int n = ranks.size();
        int chosen = -1;
        for (int i = 0; i < n; i++) {
            if (d.ranks.contains(ranks.get(i))) {
                chosen = i;
                break;
            }
        }
        int cx = x;
        cx += chip(g, cx, y + 8, tr("v.anyone"), d.ranks.isEmpty(), edit, mx, my, d.ranks::clear);
        cx += 4;
        int stepW = Math.max(10, Math.min(22, (left + W - 120 - cx) / Math.max(1, n)));
        String hoverName = null;
        for (int i = 0; i < n; i++) {
            int sh = 6 + (14 * (i + 1)) / Math.max(1, n), sx = cx + i * (stepW + 2), sy = y + 20 - sh;
            boolean lit = chosen >= 0 && i >= chosen, hover = over(mx, my, sx, sy, stepW, sh);
            g.fill(sx, sy, sx + stepW, y + 20, lit ? (i == chosen ? GOLD : 0xFFB89A3A) : hover ? MUTED | 0xFF000000 : DIM);
            if (hover) {
                hoverName = ranks.get(i);
            }
            final int step = i;
            if (edit) {
                hits.add(new Hit(sx, sy, stepW, sh, () -> {
                    d.ranks.clear();
                    if (step != chosenIndex(d)) {
                        d.ranks.addAll(ranks.subList(step, ranks.size()));
                    }
                }));
            }
        }
        int tx = cx + n * (stepW + 2) + 6;
        if (hoverName != null) {
            small(g, Component.literal(hoverName), tx, y + 4, TEXT);
        } else if (chosen >= 0) {
            small(g, tr("v.and_up", ranks.get(chosen)), tx, y + 4, GOLD & 0xFFFFFF);
        }
        small(g, tr("ladder_hint"), tx, y + 13, MUTED);
    }

    private int chosenIndex(Kit d) {
        for (int i = 0; i < ranks.size(); i++) {
            if (d.ranks.contains(ranks.get(i))) {
                return i;
            }
        }
        return -1;
    }

}
