package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.network.VaultPrizeAdminPacket;
import com.mrgregles.bsp_core.network.VaultPrizeEditPacket;
import com.mrgregles.bsp_core.vault.VaultPrizeAdmin;
import com.mrgregles.bsp_core.vault.VaultPrizes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The admin panel's VAULT tab ("Prize list", picked 2026-11-07): the vault prize table on the left with each prize's chance, the
 * chosen prize on the right with its count and weight, FROM MY HAND (the held stack, enchantments and name included) and PICK FROM
 * LIST (an item browser with a search line). Changes stay in a draft until SAVE sends the whole table; moderators see it all with
 * every key greyed out. Drawn by {@link AdminScreen}, which hands it the panel's corner, its clicks, wheel and keys.
 */
public final class VaultPrizeAdminTab {
    /** What the tab needs from the screen that hosts it. */
    interface Host {
        Font font();

        void addWidget(EditBox box);

        void send(VaultPrizeEditPacket packet);

        boolean canEdit();
    }

    private static final int W = 344, H = 210, LIST_X = 10, LIST_Y = 34, LIST_W = 108, ROW_H = 13, ROWS = 10, DX = 132,
            GRID_Y = 44, COLS = 11, GRID_ROWS = 7;
    private static final int DIM = 0xFF2A2F3A, SLOT = 0xFF0C0E12, VIO = 0xFFB58CFF, TQ = 0xFF19D3B0, BAD = 0xFFD63B2F, GOLD = 0xFFFFD23A,
            OK = 0xFF58F29A, MUTED = 0x9AA3B5, TEXT = 0xE6EAF2, OFF = 0x6B7385, HOVER = 0xFF2A2140;

    private final Host host;
    private final List<ItemStack> stacks = new ArrayList<>();
    private final List<Integer> weights = new ArrayList<>();
    private int unknown, sel, scroll, browseScroll;
    private boolean dirty, browsing, browsingNew;
    private String message = "";
    private boolean ok = true;
    private EditBox search;
    private String query;
    private List<Item> found;

    private record Hit(int x, int y, int w, int h, Runnable run) {}

    private final List<Hit> hits = new ArrayList<>();

    VaultPrizeAdminTab(Host host) {
        this.host = host;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.bsp_core.admin.vault." + key, args);
    }

    void init(int left, int top) {
        String q = search == null ? "" : search.getValue();
        search = new EditBox(host.font(), left + DX + 3, top + 31, 150, 10, tr("search_hint"));
        search.setMaxLength(64);
        search.setBordered(false);
        search.setHint(tr("search_hint"));
        search.setValue(q);
        search.setResponder(s -> browseScroll = 0);
        host.addWidget(search);
        setVisible(false);
    }

    /** Shows the search line while the browser is open on this tab. */
    void setVisible(boolean on) {
        if (search != null) {
            search.visible = on && browsing;
            if (!search.visible) {
                search.setFocused(false);
            }
        }
    }

    /** Asks the server for the table again; any unsaved change is dropped when it arrives. */
    void refresh() {
        host.send(new VaultPrizeEditPacket(VaultPrizeEditPacket.Action.REQUEST, List.of(), List.of()));
    }

    void take(VaultPrizeAdminPacket msg) {
        stacks.clear();
        weights.clear();
        for (int i = 0; i < msg.stacks().size(); i++) {
            stacks.add(msg.stacks().get(i).copy());
            weights.add(msg.weights().get(i));
        }
        unknown = msg.unknown();
        message = msg.message();
        ok = msg.ok();
        dirty = false;
        sel = Mth.clamp(sel, 0, Math.max(0, stacks.size() - 1));
    }

    private int total() {
        int t = 0;
        for (int w : weights) {
            t += w;
        }
        return t;
    }

    private String chance(int weight) {
        int t = total();
        return t <= 0 ? "0%" : String.format(Locale.ROOT, "%.1f%%", weight * 100.0 / t);
    }

    private void changed() {
        dirty = true;
        message = "";
    }

    // ------------------------------------------------------------------ input

    boolean mouseClicked(double mx, double my, int button) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (over(mx, my, h.x, h.y, h.w, h.h)) {
                h.run.run();
                return true;
            }
        }
        return false;
    }

    boolean keyPressed(int key, int scan, int mods) {
        if (browsing && key == 256) { // Escape closes the browser, not the panel
            closeBrowser();
            return true;
        }
        return false;
    }

    boolean mouseScrolled(double mx, double my, double delta) {
        if (browsing) {
            browseScroll -= (int) Math.signum(delta);
        } else {
            scroll -= (int) Math.signum(delta);
        }
        return true;
    }

    private void openBrowser(boolean forNew) {
        browsing = true;
        browsingNew = forNew;
        browseScroll = 0;
        setVisible(true);
        search.setFocused(true);
    }

    private void closeBrowser() {
        browsing = false;
        setVisible(false);
    }

    private void picked(Item item) {
        if (browsingNew || stacks.isEmpty()) {
            stacks.add(new ItemStack(item));
            weights.add(1);
            sel = stacks.size() - 1;
        } else {
            int count = Math.min(stacks.get(sel).getCount(), new ItemStack(item).getMaxStackSize());
            stacks.set(sel, new ItemStack(item, Math.max(1, count)));
        }
        changed();
        closeBrowser();
    }

    private void fromHand() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.getMainHandItem().isEmpty()) {
            return;
        }
        ItemStack held = mc.player.getMainHandItem().copy();
        if (stacks.isEmpty()) {
            stacks.add(held);
            weights.add(1);
            sel = 0;
        } else {
            stacks.set(sel, held);
        }
        changed();
    }

    private void step(boolean weight, int by) {
        if (stacks.isEmpty()) {
            return;
        }
        int n = Screen.hasShiftDown() ? by * 10 : by;
        if (weight) {
            weights.set(sel, Mth.clamp(weights.get(sel) + n, 0, VaultPrizeAdmin.MAX_WEIGHT));
        } else {
            ItemStack s = stacks.get(sel);
            s.setCount(Mth.clamp(s.getCount() + n, 1, s.getMaxStackSize()));
        }
        changed();
    }

    private void delete() {
        if (stacks.isEmpty()) {
            return;
        }
        stacks.remove(sel);
        weights.remove(sel);
        sel = Mth.clamp(sel, 0, Math.max(0, stacks.size() - 1));
        changed();
    }

    /** A client's packet to the server may carry at most 32,767 bytes; this leaves room for the channel's own bytes. */
    private static final int MOST_BYTES = 32_000;

    private void save() {
        VaultPrizeEditPacket packet = new VaultPrizeEditPacket(VaultPrizeEditPacket.Action.SAVE, List.copyOf(stacks), List.copyOf(weights));
        // too big a packet would disconnect the admin and lose the edit: measure it first
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        int size;
        try {
            VaultPrizeEditPacket.encode(packet, buf);
            size = buf.writerIndex();
        } finally {
            buf.release();
        }
        if (size > MOST_BYTES) {
            message = tr("too_big", size, MOST_BYTES).getString();
            ok = false;
            return;
        }
        host.send(packet);
    }

    // ------------------------------------------------------------------ drawing

    void render(GuiGraphics g, int x, int y, int mx, int my) {
        hits.clear();
        boolean edit = host.canEdit();
        small(g, tr("head", stacks.size(), total()), x + LIST_X, y + 24, MUTED);
        scroll = Mth.clamp(scroll, 0, Math.max(0, stacks.size() - ROWS));
        for (int i = 0; i < ROWS && i + scroll < stacks.size(); i++) {
            int index = i + scroll, ry = y + LIST_Y + i * ROW_H;
            ItemStack s = stacks.get(index);
            boolean on = index == sel, hover = over(mx, my, x + LIST_X, ry, LIST_W, ROW_H - 1);
            g.fill(x + LIST_X, ry, x + LIST_X + LIST_W, ry + ROW_H - 1, on ? VIO : hover ? DIM : SLOT);
            g.fill(x + LIST_X + 1, ry + 1, x + LIST_X + LIST_W - 1, ry + ROW_H - 2, on ? HOVER : SLOT);
            item(g, s, x + LIST_X + 1, ry + 1, 0.6875f);
            Component c = Component.literal(chance(weights.get(index)));
            int room = LIST_W - 15 - smallWidth(c) - 6;
            fit(g, (s.getCount() > 1 ? s.getCount() + " " : "") + s.getHoverName().getString(), x + LIST_X + 14, ry + 3, on ? VIO & 0xFFFFFF : TEXT, room);
            small(g, c, x + LIST_X + LIST_W - 3 - smallWidth(c), ry + 3, GOLD & 0xFFFFFF);
            hits.add(new Hit(x + LIST_X, ry, LIST_W, ROW_H - 1, () -> {
                sel = index;
                closeBrowser();
            }));
        }
        if (stacks.size() > ROWS) {
            int track = ROWS * ROW_H, knob = Math.max(8, track * ROWS / stacks.size()), ky = (track - knob) * scroll / (stacks.size() - ROWS);
            g.fill(x + LIST_X + LIST_W + 2, y + LIST_Y + ky, x + LIST_X + LIST_W + 5, y + LIST_Y + ky + knob, DIM);
        }
        key(g, x + LIST_X, y + 168, 60, 13, tr("new"), edit && stacks.size() < VaultPrizes.MAX_PRIZES - unknown, TQ, mx, my, () -> openBrowser(true));
        key(g, x + LIST_X, y + H - 24, 54, 13, tr("delete"), edit && !stacks.isEmpty(), BAD, mx, my, this::delete);
        if (unknown > 0) {
            fit(g, tr("unknown", unknown).getString(), x + LIST_X + 58, y + H - 21, MUTED, DX - LIST_X - 62);
        }
        if (browsing) {
            browser(g, x, y, mx, my);
        } else if (stacks.isEmpty()) {
            small(g, tr("empty"), x + DX, y + 34, MUTED);
        } else {
            detail(g, x, y, edit, mx, my);
        }
        // SAVE and the answer to the last action, at the foot of the right side
        Component status = !message.isEmpty() ? Component.literal(message) : dirty ? tr("unsaved") : Component.empty();
        int statusColour = !message.isEmpty() ? (ok ? OK : BAD) & 0xFFFFFF : GOLD & 0xFFFFFF;
        fit(g, status.getString(), x + DX, y + H - 21, statusColour, W - 10 - 54 - 6 - DX);
        key(g, x + W - 10 - 54, y + H - 24, 54, 13, tr("save"), edit && dirty, OK, mx, my, this::save);
    }

    private void detail(GuiGraphics g, int x, int y, boolean edit, int mx, int my) {
        Font font = host.font();
        sel = Mth.clamp(sel, 0, stacks.size() - 1);
        ItemStack s = stacks.get(sel);
        int weight = weights.get(sel);
        inset(g, x + DX, y + 30, 36, 36);
        item(g, s, x + DX + 2, y + 32, 2f);
        g.drawString(font, font.plainSubstrByWidth(s.getHoverName().getString(), W - 10 - DX - 44), x + DX + 44, y + 34, TEXT, false);
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        fit(g, id == null ? "" : id.toString(), x + DX + 44, y + 45, MUTED, W - 10 - DX - 44);
        boolean enchanted = s.isEnchanted() || s.is(Items.ENCHANTED_BOOK), named = s.hasCustomHoverName();
        small(g, tr(enchanted && named ? "enchanted_named" : enchanted ? "enchanted" : named ? "named" : "plain"), x + DX + 44, y + 54, enchanted || named ? VIO & 0xFFFFFF : OFF);

        small(g, tr("count"), x + DX, y + 78, MUTED);
        field(g, x + DX + 46, y + 75, 30, Integer.toString(s.getCount()));
        key(g, x + DX + 80, y + 75, 14, 11, Component.literal("−"), edit && s.getCount() > 1, VIO, mx, my, () -> step(false, -1));
        key(g, x + DX + 97, y + 75, 14, 11, Component.literal("+"), edit && s.getCount() < s.getMaxStackSize(), VIO, mx, my, () -> step(false, 1));
        small(g, tr("weight"), x + DX, y + 94, MUTED);
        field(g, x + DX + 46, y + 91, 30, Integer.toString(weight));
        key(g, x + DX + 80, y + 91, 14, 11, Component.literal("−"), edit && weight > 0, VIO, mx, my, () -> step(true, -1));
        key(g, x + DX + 97, y + 91, 14, 11, Component.literal("+"), edit && weight < VaultPrizeAdmin.MAX_WEIGHT, VIO, mx, my, () -> step(true, 1));
        int t = total();
        small(g, weight <= 0 || t <= 0 ? tr("never") : tr("chance", chance(weight), Math.round(t / (double) weight)), x + DX, y + 110, weight <= 0 ? OFF : TEXT);
        small(g, tr("shift"), x + DX, y + 119, MUTED);

        var mc = Minecraft.getInstance();
        boolean holding = mc.player != null && !mc.player.getMainHandItem().isEmpty();
        key(g, x + DX, y + 140, 80, 13, tr("from_hand"), edit && holding, TQ, mx, my, this::fromHand);
        key(g, x + DX + 84, y + 140, 84, 13, tr("pick"), edit, TQ, mx, my, () -> openBrowser(false));
    }

    private List<Item> results() {
        String q = search.getValue().trim().toLowerCase(Locale.ROOT);
        if (q.equals(query) && found != null) {
            return found;
        }
        query = q;
        List<Item> out = new ArrayList<>();
        for (Item it : ForgeRegistries.ITEMS) {
            if (it == Items.AIR) {
                continue;
            }
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(it);
            if (id != null && (q.isEmpty() || id.toString().contains(q) || new ItemStack(it).getHoverName().getString().toLowerCase(Locale.ROOT).contains(q))) {
                out.add(it);
                if (out.size() >= 2000) {
                    break;
                }
            }
        }
        found = out;
        return out;
    }

    private void browser(GuiGraphics g, int x, int y, int mx, int my) {
        small(g, tr(browsingNew ? "pick_new" : "pick_head"), x + DX, y + 22, MUTED);
        inset(g, x + DX, y + 29, 156, 12);
        key(g, x + W - 10 - 40, y + 29, 40, 12, tr("close"), true, TQ, mx, my, this::closeBrowser);
        List<Item> items = results();
        int rows = (items.size() + COLS - 1) / COLS;
        browseScroll = Mth.clamp(browseScroll, 0, Math.max(0, rows - GRID_ROWS));
        Item hovered = null;
        for (int i = 0; i < COLS * GRID_ROWS; i++) {
            int index = i + browseScroll * COLS, sx = x + DX + (i % COLS) * 18, sy = y + GRID_Y + (i / COLS) * 18;
            boolean has = index < items.size(), hover = has && over(mx, my, sx, sy, 18, 18);
            g.fill(sx, sy, sx + 18, sy + 18, DIM);
            g.fill(sx + 1, sy + 1, sx + 17, sy + 17, hover ? HOVER : SLOT);
            if (!has) {
                continue;
            }
            Item item = items.get(index);
            g.renderItem(new ItemStack(item), sx + 1, sy + 1);
            if (hover) {
                hovered = item;
            }
            hits.add(new Hit(sx, sy, 18, 18, () -> picked(item)));
        }
        if (rows > GRID_ROWS) {
            int track = GRID_ROWS * 18, knob = Math.max(8, track * GRID_ROWS / rows), ky = (track - knob) * browseScroll / (rows - GRID_ROWS);
            g.fill(x + DX + COLS * 18 + 1, y + GRID_Y + ky, x + DX + COLS * 18 + 3, y + GRID_Y + ky + knob, MUTED | 0xFF000000);
        }
        if (items.isEmpty()) {
            small(g, tr("no_match"), x + DX + 4, y + GRID_Y + 6, MUTED);
        }
        int lineY = y + GRID_Y + GRID_ROWS * 18 + 3;
        if (hovered != null) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(hovered);
            fit(g, new ItemStack(hovered).getHoverName().getString() + " · " + id, x + DX, lineY, TEXT, W - 10 - DX);
        } else {
            small(g, tr("browse_hint"), x + DX, lineY, MUTED);
        }
    }

    // ------------------------------------------------------------------ pieces

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void item(GuiGraphics g, ItemStack s, int x, int y, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.renderItem(s, 0, 0);
        g.pose().popPose();
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

    /** Small text shrunk further to fit {@code max} pixels, as the cash machine screens do. */
    private void fit(GuiGraphics g, String text, int x, int y, int colour, int max) {
        int w = host.font().width(text);
        float k = w <= 0 ? 0.75f : Math.min(0.75f, max / (float) w);
        if (k < 0.5f) { // too long even shrunk: cut it
            text = host.font().plainSubstrByWidth(text, Math.round(max / 0.5f) - host.font().width("…")) + "…";
            k = 0.5f;
        }
        g.pose().pushPose();
        g.pose().translate(x, y + Math.round((0.75f - k) * 4), 0);
        g.pose().scale(k, k, 1f);
        g.drawString(host.font(), text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private void inset(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, DIM);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, SLOT);
    }

    private void field(GuiGraphics g, int x, int y, int w, String value) {
        inset(g, x, y, w, 11);
        small(g, Component.literal(value), x + 3, y + 3, TEXT);
    }

    private void key(GuiGraphics g, int x, int y, int w, int h, Component label, boolean enabled, int colour, int mx, int my, Runnable run) {
        boolean hover = enabled && over(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, enabled ? colour : DIM);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? HOVER : SLOT);
        int c = enabled ? colour & 0xFFFFFF : OFF;
        if (h < 14) {
            small(g, label, x + w / 2 - smallWidth(label) / 2, y + (h - 6) / 2, c);
        } else {
            g.drawString(host.font(), label, x + w / 2 - host.font().width(label) / 2, y + (h - 8) / 2, c, false);
        }
        if (enabled) {
            hits.add(new Hit(x, y, w, h, run));
        }
    }
}
