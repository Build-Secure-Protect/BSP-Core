package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.VendorConfigPacket;
import com.mrgregles.bsp_core.shop.OpenHours;
import com.mrgregles.bsp_core.shop.VendorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Vendor Post's assignment screen in the Cash Machine skin: the shop list to pick from, the
 * mob and its villager profession (or, for a Market Stall, its skin), the opening hours (always,
 * real clock, game time) with open and close boxes, and Done. Moderators see it read-only.
 */
public class VendorScreen extends AbstractContainerScreen<VendorMenu> implements ScreenFit.Fitted {
    private static final int W = 300, H = 216;
    private static final int BEZEL = 0xFFCFC8B8, BEZEL_DARK = 0xFF8F877A, BEZEL_LIGHT = 0xFFECE6D8, SCREEN = 0xFF061008, SCREEN_EDGE = 0xFF0D3B22, PHOS = 0x58F29A, PHOS_DIM = 0x3FB97A,
            AMBER = 0xF2B544, KEY = 0xFFEAE4D5, KEY_EDGE = 0xFF9A9183, KEY_TEXT = 0x2B2823, KEY_LOCKED = 0x8F877A;
    private static final int SX = 10, SY = 10, SW = 206, SH = 176, KX = 226, KW = 64, KH = 20, KGAP = 4, LIST_ROWS = 7;

    private record Key(String label, boolean enabled, Runnable action) {}

    private final List<Key> top = new ArrayList<>(), bottom = new ArrayList<>();
    private EditBox openBox, closeBox;
    private int listPage, mobIndex, profIndex, skinIndex;
    private OpenHours.Mode mode;
    private String shopId;

    public VendorScreen(VendorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = H;
        shopId = menu.shopId;
        mode = menu.hours.mode();
        skinIndex = Math.max(0, menu.skin);
        for (int i = 0; i < VendorMenu.MOBS.length; i++) {
            if (VendorMenu.MOBS[i].equals(menu.mob)) {
                mobIndex = i;
            }
        }
        for (int i = 0; i < VendorMenu.PROFESSIONS.length; i++) {
            if (menu.mobNbt.contains("minecraft:" + VendorMenu.PROFESSIONS[i] + "\"")) {
                profIndex = i;
            }
        }
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("gui.bsp_core.vendor." + key, args);
    }

    @Override
    protected void init() {
        super.init();
        openBox = box(SX + 70, SY + 108, fmt(menu.hours.openMin()));
        closeBox = box(SX + 154, SY + 108, fmt(menu.hours.closeMin()));
    }

    private EditBox box(int x, int y, String value) {
        EditBox b = new EditBox(font, leftPos + x, topPos + y, 50, 10, Component.empty());
        b.setBordered(false);
        b.setTextColor(PHOS);
        b.setMaxLength(6);
        b.setValue(value);
        b.setEditable(menu.editable);
        addRenderableWidget(b);
        return b;
    }

    private String fmt(int v) {
        return mode == OpenHours.Mode.GAME ? String.valueOf(v) : String.format(Locale.ROOT, "%02d:%02d", v / 60, v % 60);
    }

    private int parse(String s, int fallback) {
        try {
            if (mode == OpenHours.Mode.GAME) {
                return Math.max(0, Math.min(23999, Integer.parseInt(s.trim())));
            }
            String[] p = s.trim().split(":");
            return Math.max(0, Math.min(1439, Integer.parseInt(p[0]) * 60 + (p.length > 1 ? Integer.parseInt(p[1]) : 0)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void keys() {
        top.clear();
        bottom.clear();
        boolean ed = menu.editable;
        top.add(new Key("prev", listPage > 0, () -> listPage--));
        top.add(new Key("next", (listPage + 1) * LIST_ROWS < menu.shops.size(), () -> listPage++));
        if (menu.skin >= 0) {
            top.add(new Key("skin", ed, () -> skinIndex = (skinIndex + 1) % com.mrgregles.bsp_core.shop.StallSkin.values().length));
        } else {
            top.add(new Key("mob", ed, () -> mobIndex = (mobIndex + 1) % VendorMenu.MOBS.length));
            top.add(new Key("profession", ed && VendorMenu.MOBS[mobIndex].endsWith("villager"), () -> profIndex = (profIndex + 1) % VendorMenu.PROFESSIONS.length));
        }
        bottom.add(new Key("hours_" + mode.name().toLowerCase(Locale.ROOT), ed, () -> {
            int o = parse(openBox.getValue(), 0), c = parse(closeBox.getValue(), 0);
            mode = OpenHours.Mode.values()[(mode.ordinal() + 1) % 3];
            openBox.setValue(fmt(mode == OpenHours.Mode.GAME ? Math.min(23999, o * 1000 / 60) : o));
            closeBox.setValue(fmt(mode == OpenHours.Mode.GAME ? Math.min(23999, c * 1000 / 60) : c));
        }));
        bottom.add(new Key("unassign", ed && !shopId.isEmpty(), () -> shopId = ""));
        bottom.add(new Key("done", ed, this::done));
    }

    private void done() {
        OpenHours hours = new OpenHours(mode, parse(openBox.getValue(), 0), parse(closeBox.getValue(), 0));
        String mob = VendorMenu.MOBS[mobIndex];
        String nbt = mob.endsWith("villager") && profIndex > 0 ? "{VillagerData:{profession:\"minecraft:" + VendorMenu.PROFESSIONS[profIndex] + "\",level:2,type:\"minecraft:plains\"}}" : "";
        BSPNetwork.CHANNEL.sendToServer(new VendorConfigPacket(shopId, hours, mob, nbt, menu.skin >= 0 ? skinIndex : -1));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        keys();
        int x = leftPos, y = topPos, ky = y + SY;
        for (Key k : top) {
            if (over(mx, my, x + KX, ky, KW, KH)) {
                if (k.enabled) {
                    k.action.run();
                }
                return true;
            }
            ky += KH + KGAP;
        }
        ky = y + SY + SH - KH;
        for (int i = bottom.size() - 1; i >= 0; i--) {
            Key k = bottom.get(i);
            if (over(mx, my, x + KX, ky, KW, KH)) {
                if (k.enabled) {
                    k.action.run();
                }
                return true;
            }
            ky -= KH + KGAP;
        }
        if (menu.editable) {
            for (int i = listPage * LIST_ROWS; i < Math.min(menu.shops.size(), (listPage + 1) * LIST_ROWS); i++) {
                if (over(mx, my, x + SX + 6, y + SY + 22 + (i - listPage * LIST_ROWS) * 11, SW - 12, 10)) {
                    shopId = menu.shops.get(i)[0];
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((openBox.isFocused() || closeBox.isFocused()) && key != 256) {
            return openBox.keyPressed(key, scan, mods) || closeBox.keyPressed(key, scan, mods) || true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        keys();
        renderBackground(g);
        super.render(g, mx, my, partial);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, BEZEL_DARK);
        g.fill(x, y, x + W, y + H, BEZEL);
        g.fill(x, y, x + W, y + 2, BEZEL_LIGHT);
        g.fill(x + SX - 2, y + SY - 2, x + SX + SW + 2, y + SY + SH + 2, SCREEN_EDGE);
        g.fill(x + SX, y + SY, x + SX + SW, y + SY + SH, SCREEN);
        int ky = y + SY;
        for (Key k : top) {
            key(g, x + KX, ky, k, mx, my);
            ky += KH + KGAP;
        }
        ky = y + SY + SH - KH;
        for (int i = bottom.size() - 1; i >= 0; i--) {
            key(g, x + KX, ky, bottom.get(i), mx, my);
            ky -= KH + KGAP;
        }
        g.fill(x + SX + 66, y + SY + 118, x + SX + 122, y + SY + 119, SCREEN_EDGE);
        g.fill(x + SX + 152, y + SY + 118, x + SX + 204, y + SY + 119, SCREEN_EDGE);
    }

    private void key(GuiGraphics g, int x, int y, Key k, int mx, int my) {
        CashMachine.key(g, font, x, y, KW, KH, tr(k.label).getString(), k.enabled, k.enabled && over(mx, my, x, y, KW, KH));
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        CashMachine.fit(g, font, "> " + title.getString().toUpperCase(Locale.ROOT), SX + 6, SY + 6, SW - 12, PHOS);
        g.fill(SX + 4, SY + 16, SX + SW - 4, SY + 17, SCREEN_EDGE);
        if (menu.shops.isEmpty()) {
            CashMachine.wrap(g, font, tr("no_shops"), SX + 8, SY + 22, SW - 16, PHOS_DIM);
        }
        for (int i = listPage * LIST_ROWS; i < Math.min(menu.shops.size(), (listPage + 1) * LIST_ROWS); i++) {
            String[] s = menu.shops.get(i);
            boolean cur = s[0].equals(shopId);
            g.drawString(font, font.plainSubstrByWidth((cur ? "> " : "  ") + s[1] + "  (" + s[0] + ")", SW - 14), SX + 6, SY + 22 + (i - listPage * LIST_ROWS) * 11, cur ? PHOS : PHOS_DIM, false);
        }
        g.fill(SX + 4, SY + 102, SX + SW - 4, SY + 103, SCREEN_EDGE);
        g.drawString(font, tr("open"), SX + 8, SY + 108, PHOS_DIM, false);
        CashMachine.fit(g, font, tr("close").getString(), SX + 112, SY + 108, 38, PHOS_DIM);
        CashMachine.wrap(g, font, tr("mode_" + mode.name().toLowerCase(Locale.ROOT)), SX + 8, SY + 122, SW - 16, AMBER);
        if (menu.skin >= 0) {
            var skin = com.mrgregles.bsp_core.shop.StallSkin.values()[skinIndex];
            CashMachine.fit(g, font, tr("skin_line", Component.translatable("gui.bsp_core.stall.skin." + skin.id)).getString(), SX + 8, SY + 142, SW - 16, PHOS);
        } else {
            String mob = VendorMenu.MOBS[mobIndex].replace("minecraft:", "").replace('_', ' ');
            CashMachine.fit(g, font, tr("figure", mob + (VendorMenu.MOBS[mobIndex].endsWith("villager") && profIndex > 0 ? ", " + VendorMenu.PROFESSIONS[profIndex] : "")).getString(), SX + 8, SY + 142, SW - 16, PHOS);
        }
        CashMachine.wrap(g, font, tr(shopId.isEmpty() ? (menu.skin >= 0 ? "unassigned_stall" : "unassigned") : "assigned", shopId), SX + 8, SY + 152, SW - 16, shopId.isEmpty() ? 0xFF6B5C : PHOS_DIM);
    }

    @Override
    public int panelWidth() {
        return imageWidth;
    }

    @Override
    public int panelHeight() {
        return imageHeight;
    }

    /** Drawn bigger than its own size where the window has room: the client setting shop.screenPercent. */
    @Override
    public float grow() {
        return com.mrgregles.bsp_core.BSPClientConfig.shopScreenScale();
    }
}
