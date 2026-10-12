package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.ShopConfigActionPacket;
import com.mrgregles.bsp_core.network.ShopConfigActionPacket.Action;
import com.mrgregles.bsp_core.network.ShopConfigDataPacket;
import com.mrgregles.bsp_core.shop.Shop;
import com.mrgregles.bsp_core.shop.ShopConfigMenu;
import com.mrgregles.bsp_core.shop.ShopItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The shop configurator, "Workbench" in the Cash Machine skin. The live page of the selected shop
 * sits on the screen: click a card to edit it in the side panel (prices, currency, stock, restock,
 * lot size, instant, hidden, section), click an empty slot to add the item in hand or open the
 * browser (every item in the game, searched by name). Views: the shop list, the page, the item
 * browser, the shop's settings, the style preset. The TEST key opens the real shop in test mode.
 * Moderators see it all and may change nothing.
 */
public class ShopConfigScreen extends AbstractContainerScreen<ShopConfigMenu> {
    private static final int W = 360, H = 232;
    private static final int BEZEL = 0xFFCFC8B8, BEZEL_DARK = 0xFF8F877A, BEZEL_LIGHT = 0xFFECE6D8, SCREEN = 0xFF061008, SCREEN_EDGE = 0xFF0D3B22, PHOS = 0x58F29A, PHOS_DIM = 0x3FB97A,
            AMBER = 0xF2B544, RED = 0xFF6B5C, KEY = 0xFFEAE4D5, KEY_EDGE = 0xFF9A9183, KEY_TEXT = 0x2B2823, KEY_LOCKED = 0x8F877A, CARD = 0xFF0A1A10, CARD_SEL = 0xFF0F2D1C, CARD_EMPTY = 0xFF07100A;
    private static final int SX = 10, SY = 10, SW = 206, SH = 176, PX = 222, PW = 66, KX = 292, KW = 58, KH = 18, KGAP = 3, CX = SX + 6, CY = SY + 22, CGAP = 1;
    private static final String[] STYLES = {"cash_machine", "passbook", "market_board"};
    private static final int LIST_ROWS = 7, LIST_Y = SY + 34, SIDE_Y = 38, SIDE_STEP = 11, SET_STEP = 10;
    /** Shop list columns: name, kind, cards, network. */
    private static final int[] COL = {SX + 6, SX + 104, SX + 146, SX + 174};

    private enum View { LIST, PAGE, BROWSER, SETTINGS, STYLE }

    private record Key(String label, boolean enabled, Runnable action) {}

    private final List<Key> top = new ArrayList<>(), bottom = new ArrayList<>();
    private View view = View.LIST;
    private int sel = -1, lastSerial = -1, browserPage, listPage;
    private EditBox field, search, newId;
    private String fieldName = "";
    private List<Item> found = new ArrayList<>();
    private Component message = Component.empty();
    private int messageColour = AMBER;

    public ShopConfigScreen(ShopConfigMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    public static void receive(ShopConfigDataPacket m) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof ShopConfigMenu menu) {
            menu.list.clear();
            menu.list.addAll(m.list());
            menu.shop = m.shop() == null ? null : Shop.load(m.shop());
            if (!m.messageKey().isEmpty()) {
                menu.messageKey = m.messageKey();
                menu.messageArgs = m.messageArgs();
                menu.messageSerial++;
            }
        }
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("gui.bsp_core.config." + key, args);
    }

    private void send(Action a, String s1, String s2, int i, int j) {
        BSPNetwork.CHANNEL.sendToServer(new ShopConfigActionPacket(a, s1, s2, i, j));
    }

    @Override
    protected void init() {
        super.init();
        field = new EditBox(font, leftPos + PX + 2, topPos + SY + 154, PW - 4, 10, Component.empty());
        field.setBordered(false);
        field.setTextColor(PHOS);
        field.setMaxLength(32);
        search = new EditBox(font, leftPos + SX + 8, topPos + SY + 20, 120, 10, tr("search"));
        search.setBordered(false);
        search.setTextColor(PHOS);
        search.setHint(tr("search").withStyle(s -> s.withColor(PHOS_DIM)));
        search.setResponder(s -> { browserPage = 0; findItems(s); });
        newId = new EditBox(font, leftPos + SX + 28, topPos + SY + SH - 30, 110, 10, tr("new_id"));
        newId.setBordered(false);
        newId.setTextColor(PHOS);
        newId.setHint(tr("new_id").withStyle(s -> s.withColor(PHOS_DIM)));
        // ids are lower case with underscores; typing capitals or spaces is fine, they are turned into that
        newId.setFilter(s -> s.matches("[A-Za-z0-9_ ]{0,32}"));
        newId.setResponder(s -> {
            String n = s.toLowerCase(Locale.ROOT).replace(' ', '_');
            if (!n.equals(s)) {
                newId.setValue(n);
            }
        });
        addRenderableWidget(field);
        addRenderableWidget(search);
        addRenderableWidget(newId);
        setView(menu.shop == null ? View.LIST : View.PAGE);
        findItems("");
    }

    private void findItems(String q) {
        found = new ArrayList<>();
        String s = q.trim().toLowerCase(Locale.ROOT);
        for (Item it : ForgeRegistries.ITEMS) {
            if (it == net.minecraft.world.item.Items.AIR) {
                continue;
            }
            String name = new ItemStack(it).getHoverName().getString().toLowerCase(Locale.ROOT), id = String.valueOf(ForgeRegistries.ITEMS.getKey(it));
            if (s.isEmpty() || name.contains(s) || id.contains(s)) {
                found.add(it);
                if (found.size() >= 2000) {
                    break;
                }
            }
        }
    }

    private void setView(View v) {
        view = v;
        fieldName = "";
        field.setVisible(false);
        search.setVisible(v == View.BROWSER);
        newId.setVisible(v == View.LIST && menu.editable);
        setFocused(null);
    }

    private ShopItem item() {
        return menu.shop == null ? null : menu.shop.item(sel);
    }

    private void edit(String name, String current) {
        if (!menu.editable) {
            return;
        }
        fieldName = name;
        field.setVisible(true);
        field.setValue(current);
        setFocused(field);
    }

    private void commit() {
        if (fieldName.isEmpty()) {
            return;
        }
        if (fieldName.startsWith("item.")) {
            send(Action.SET_ITEM, fieldName.substring(5), field.getValue(), sel, 0);
        } else {
            send(Action.SET, fieldName, field.getValue(), 0, 0);
        }
        fieldName = "";
        field.setVisible(false);
    }

    private void keys() {
        top.clear();
        bottom.clear();
        boolean ed = menu.editable, has = menu.shop != null;
        top.add(new Key("shops", true, () -> setView(View.LIST)));
        top.add(new Key("page", has, () -> setView(View.PAGE)));
        top.add(new Key("settings", has, () -> setView(View.SETTINGS)));
        top.add(new Key("style", has, () -> setView(View.STYLE)));
        switch (view) {
            case LIST -> {
                bottom.add(new Key("new_shop", ed && !newId.getValue().isBlank(), () -> send(Action.CREATE, newId.getValue(), "SHOP", 0, 0)));
                bottom.add(new Key("prev", listPage > 0, () -> listPage--));
                bottom.add(new Key("next", (listPage + 1) * LIST_ROWS < menu.list.size(), () -> listPage++));
            }
            case PAGE -> {
                bottom.add(new Key("hand_to_slot", ed && has && !minecraft.player.getMainHandItem().isEmpty(), () -> send(Action.ADD_HAND, "", "", sel < 0 ? 999 : sel, 0)));
                bottom.add(new Key("browse", ed && has, () -> setView(View.BROWSER)));
                bottom.add(new Key("remove", ed && item() != null, () -> { send(Action.REMOVE_ITEM, "", "", sel, 0); sel = -1; }));
                bottom.add(new Key("test", has, () -> send(Action.TEST, menu.shop.id, "", 0, 0)));
            }
            case BROWSER -> {
                bottom.add(new Key("prev", browserPage > 0, () -> browserPage--));
                bottom.add(new Key("next", (browserPage + 1) * 28 < found.size(), () -> browserPage++));
                bottom.add(new Key("back", true, () -> setView(View.PAGE)));
            }
            case SETTINGS -> bottom.add(new Key("delete", ed && has, () -> { send(Action.DELETE, "", "", 0, 0); setView(View.LIST); }));
            case STYLE -> { }
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        keys();
        int x = leftPos, y = topPos, ky = y + SY;
        for (Key k : top) {
            if (over(mx, my, x + KX, ky, KW, KH)) {
                if (k.enabled) {
                    commit();
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
                    commit();
                    k.action.run();
                }
                return true;
            }
            ky -= KH + KGAP;
        }
        if (view == View.LIST) {
            for (int i = listPage * LIST_ROWS; i < Math.min(menu.list.size(), (listPage + 1) * LIST_ROWS); i++) {
                if (over(mx, my, x + SX + 6, y + LIST_Y + (i - listPage * LIST_ROWS) * 11, SW - 12, 10)) {
                    send(Action.SELECT, menu.list.get(i).id(), "", 0, 0);
                    sel = -1;
                    setView(View.PAGE);
                    return true;
                }
            }
        } else if (view == View.PAGE && menu.shop != null) {
            for (int k = 0; k < perPage(); k++) {
                int cx = x + CX + (k % cols()) * (cw() + CGAP), cy = y + CY + (k / cols()) * (ch() + CGAP);
                if (over(mx, my, cx, cy, cw(), ch())) {
                    commit();
                    sel = k;
                    return true;
                }
            }
            if (item() != null && over(mx, my, x + PX, y + SY, PW, SH)) {
                sideClick(my - y - SY);
                return true;
            }
        } else if (view == View.BROWSER && menu.editable) {
            for (int k = browserPage * 28; k < Math.min(found.size(), (browserPage + 1) * 28); k++) {
                int n = k - browserPage * 28, cx = x + SX + 8 + (n % 7) * 28, cy = y + SY + 36 + (n / 7) * 28;
                if (over(mx, my, cx, cy, 26, 26)) {
                    send(Action.ADD_ITEM, String.valueOf(ForgeRegistries.ITEMS.getKey(found.get(k))), "", sel < 0 ? 999 : sel, 1);
                    setView(View.PAGE);
                    return true;
                }
            }
        } else if (view == View.SETTINGS && menu.shop != null && over(mx, my, x + SX, y + SY + 20, SW, SH - 24)) {
            settingsClick(my - y - SY - 20);
            return true;
        } else if (view == View.STYLE && menu.shop != null && menu.editable) {
            for (int i = 0; i < STYLES.length; i++) {
                if (over(mx, my, x + SX + 8, y + SY + 30 + i * 14, SW - 16, 12)) {
                    send(Action.SET, "style", STYLES[i], 0, 0);
                    return true;
                }
            }
            if (over(mx, my, x + SX + 8, y + SY + 84, SW - 16, 12)) {
                int c = menu.shop.columns;
                send(Action.SET, "columns", String.valueOf(c == 0 ? Shop.MIN_COLUMNS : c >= Shop.MAX_COLUMNS ? 0 : c + 1), 0, 0);
                return true;
            }
            if (over(mx, my, x + SX + 8, y + SY + 98, SW - 16, 12)) {
                send(Action.SET, "rows", String.valueOf(menu.shop.rows() == 3 ? 2 : 3), 0, 0);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void sideClick(double ry) {
        ShopItem it = item();
        if (ry < SIDE_Y) {
            return;
        }
        int row = (int) ((ry - SIDE_Y) / SIDE_STEP);
        switch (row) {
            case 0 -> edit("item.count", String.valueOf(it.stack.getCount()));
            case 1 -> edit("item.buy", String.valueOf(it.buy));
            case 2 -> edit("item.sell", String.valueOf(it.sell));
            case 3 -> send(Action.SET_ITEM, "currency", String.valueOf(it.currency >= 4 ? -1 : it.currency + 1), sel, 0);
            case 4 -> edit("item.stockglobal", String.valueOf(it.stockGlobal));
            case 5 -> edit("item.stockplayer", String.valueOf(it.stockPlayer));
            case 6 -> edit("item.restock", String.valueOf(it.restockMs / 60_000));
            case 7 -> send(Action.SET_ITEM, "instant", String.valueOf(!it.instant), sel, 0);
            case 8 -> send(Action.SET_ITEM, "hidden", String.valueOf(!it.hidden), sel, 0);
            case 9 -> edit("item.section", it.section);
            default -> { }
        }
    }

    private void settingsClick(double ry) {
        Shop s = menu.shop;
        int row = (int) (ry / SET_STEP);
        switch (row) {
            case 0 -> edit("name", s.name);
            case 1 -> send(Action.SET, "kind", Shop.Kind.values()[(s.kind.ordinal() + 1) % Shop.Kind.values().length].name(), 0, 0);
            case 2 -> edit("parent", s.parent);
            case 3 -> send(Action.SET, "remote", String.valueOf(!s.remote), 0, 0);
            case 4 -> send(Action.SET, "buyonly", String.valueOf(!s.buyOnly), 0, 0);
            case 5 -> edit("slots", String.valueOf(s.displaySlots));
            case 6 -> edit("interval", String.valueOf(s.intervalMs / 3_600_000L));
            case 7 -> edit("offer", String.valueOf(s.offerPercent));
            case 8 -> edit("offerhours", s.offerEndMs == 0 ? "0" : String.valueOf(Math.max(0, (s.offerEndMs - System.currentTimeMillis()) / 3_600_000L)));
            case 9 -> send(Action.SET, "hoursmode", com.mrgregles.bsp_core.shop.OpenHours.Mode.values()[(s.hours.mode().ordinal() + 1) % 3].name(), 0, 0);
            case 10 -> edit("hoursopen", hm(s.hours.openMin(), s.hours.mode()));
            case 11 -> edit("hoursclose", hm(s.hours.closeMin(), s.hours.mode()));
            default -> { }
        }
    }

    private static String hm(int v, com.mrgregles.bsp_core.shop.OpenHours.Mode mode) {
        return mode == com.mrgregles.bsp_core.shop.OpenHours.Mode.GAME ? String.valueOf(v) : String.format(Locale.ROOT, "%02d:%02d", v / 60, v % 60);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (field.isFocused() && key == 257) {
            commit();
            return true;
        }
        if ((field.isFocused() || search.isFocused() || newId.isFocused()) && key != 256) {
            return field.keyPressed(key, scan, mods) || search.keyPressed(key, scan, mods) || newId.keyPressed(key, scan, mods) || true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        if (menu.messageSerial != lastSerial) {
            lastSerial = menu.messageSerial;
            String key = menu.messageKey;
            messageColour = key.equals("saved") || key.equals("created") || key.equals("deleted") ? AMBER : RED;
            message = key.isEmpty() ? Component.empty() : Component.translatable("message.bsp_core.config." + key, (Object[]) menu.messageArgs);
        }
        keys();
        renderBackground(g);
        super.render(g, mx, my, partial);
        if (view == View.BROWSER) {
            for (int k = browserPage * 28; k < Math.min(found.size(), (browserPage + 1) * 28); k++) {
                int n = k - browserPage * 28, cx = leftPos + SX + 8 + (n % 7) * 28, cy = topPos + SY + 36 + (n / 7) * 28;
                if (over(mx, my, cx, cy, 26, 26)) {
                    g.renderTooltip(font, new ItemStack(found.get(k)), mx, my);
                }
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, BEZEL_DARK);
        g.fill(x, y, x + W, y + H, BEZEL);
        g.fill(x, y, x + W, y + 2, BEZEL_LIGHT);
        g.fill(x + SX - 2, y + SY - 2, x + SX + SW + 2, y + SY + SH + 2, SCREEN_EDGE);
        g.fill(x + SX, y + SY, x + SX + SW, y + SY + SH, SCREEN);
        g.fill(x + PX - 2, y + SY - 2, x + PX + PW + 2, y + SY + SH + 2, SCREEN_EDGE);
        g.fill(x + PX, y + SY, x + PX + PW, y + SY + SH, SCREEN);
        for (int ly = y + SY; ly < y + SY + SH; ly += 3) {
            g.fill(x + SX, ly, x + PX + PW, ly + 1, 0x14000000);
        }
        if (view == View.PAGE && menu.shop != null) {
            List<ShopItem> all = menu.shop.all();
            for (int k = 0; k < perPage(); k++) {
                int cx = x + CX + (k % cols()) * (cw() + CGAP), cy = y + CY + (k / cols()) * (ch() + CGAP);
                boolean has = k < all.size();
                g.fill(cx, cy, cx + cw(), cy + ch(), k == sel ? CARD_SEL : has ? CARD : CARD_EMPTY);
                g.fill(cx, cy, cx + cw(), cy + 1, has ? PHOS_DIM | 0xFF000000 : 0xFF1F6B43);
                g.fill(cx, cy + ch() - 1, cx + cw(), cy + ch(), has ? PHOS_DIM | 0xFF000000 : 0xFF1F6B43);
                if (has) {
                    g.renderItem(all.get(k).stack, cx + cw() / 2 - 8, cy + iconY());
                    g.renderItemDecorations(font, all.get(k).stack, cx + cw() / 2 - 8, cy + iconY());
                }
            }
        } else if (view == View.BROWSER) {
            for (int k = browserPage * 28; k < Math.min(found.size(), (browserPage + 1) * 28); k++) {
                int n = k - browserPage * 28, cx = x + SX + 8 + (n % 7) * 28, cy = y + SY + 36 + (n / 7) * 28;
                g.fill(cx, cy, cx + 26, cy + 26, over(mx, my, cx, cy, 26, 26) ? CARD_SEL : CARD);
                g.renderItem(new ItemStack(found.get(k)), cx + 5, cy + 5);
            }
        }
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
        if (view == View.LIST && menu.editable) {
            // the new-id field drawn as a field, so it reads as somewhere to type
            g.fill(x + SX + 24, y + SY + SH - 34, x + SX + 142, y + SY + SH - 17, PHOS_DIM | 0xFF000000);
            g.fill(x + SX + 25, y + SY + SH - 33, x + SX + 141, y + SY + SH - 18, CARD_SEL);
        }
        g.fill(x + SX - 2, y + SY + SH + 6, x + W - SX + 2, y + H - 6, SCREEN_EDGE);
        g.fill(x + SX, y + SY + SH + 8, x + W - SX, y + H - 8, SCREEN);
        String strip = tr(menu.editable ? "strip_admin" : "strip_mod").getString() + " / " + Bank.serverId();
        CashMachine.fit(g, font, strip, x + SX + 6, y + SY + SH + 12, W - 2 * SX - 12, PHOS_DIM);
    }

    private void key(GuiGraphics g, int x, int y, Key k, int mx, int my) {
        CashMachine.key(g, font, x, y, KW, KH, tr(k.label).getString(), k.enabled, k.enabled && over(mx, my, x, y, KW, KH));
    }

    private void line(GuiGraphics g, int x, int y, String label, String value, int w) {
        g.drawString(font, label, x, y, PHOS_DIM, false);
        g.drawString(font, font.plainSubstrByWidth(value, w - font.width(label) - 6), x + w - font.width(font.plainSubstrByWidth(value, w - font.width(label) - 6)), y, PHOS, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        Shop s = menu.shop;
        String head = "> " + tr("title").getString().toUpperCase(Locale.ROOT) + (s == null ? "" : ": " + font.plainSubstrByWidth(s.name.toUpperCase(Locale.ROOT), 100));
        CashMachine.fit(g, font, head, SX + 6, SY + 6, SW - 12, PHOS);
        g.fill(SX + 4, SY + 16, SX + SW - 4, SY + 17, SCREEN_EDGE);
        // the message sits at the foot of the main screen; hints stack above it and the view is clipped to the screen
        int msgH = CashMachine.height(font, message, SW - 12);
        int bottom = SY + SH - (msgH > 0 ? msgH + 4 : 4);
        CashMachine.clip(g, leftPos, topPos, SX, SY + 18, SW, SH - 18);
        switch (view) {
            case LIST -> {
                if (menu.list.isEmpty()) {
                    g.drawString(font, tr("no_shops"), SX + 8, SY + 24, PHOS_DIM, false);
                }
                String[] heads = {"col_name", "col_kind", "col_cards", "col_net"};
                for (int c = 0; c < heads.length; c++) {
                    CashMachine.fit(g, font, tr(heads[c]).getString(), COL[c], SY + 22, (c + 1 < COL.length ? COL[c + 1] : SX + SW - 6) - COL[c] - 4, AMBER);
                }
                g.fill(SX + 4, SY + 31, SX + SW - 4, SY + 32, SCREEN_EDGE);
                for (int i = listPage * LIST_ROWS; i < Math.min(menu.list.size(), (listPage + 1) * LIST_ROWS); i++) {
                    var e = menu.list.get(i);
                    boolean cur = s != null && s.id.equals(e.id());
                    int ry = LIST_Y + (i - listPage * LIST_ROWS) * 11, colour = cur ? PHOS : PHOS_DIM;
                    g.drawString(font, font.plainSubstrByWidth((cur ? "> " : "  ") + e.name(), COL[1] - COL[0] - 4), COL[0], ry, colour, false);
                    g.drawString(font, e.kind().name().toLowerCase(Locale.ROOT), COL[1], ry, colour, false);
                    g.drawString(font, String.valueOf(e.items()), COL[2], ry, colour, false);
                    g.drawString(font, e.remote() ? tr("col_net_on").getString() : "-", COL[3], ry, colour, false);
                }
                if (menu.editable) {
                    CashMachine.wrap(g, font, tr("new_hint"), SX + 8, SY + SH - 56, SW - 16, PHOS_DIM);
                    g.drawString(font, tr("id_label"), SX + 8, SY + SH - 30, AMBER, false);
                }
            }
            case PAGE -> {
                if (s != null) {
                    List<ShopItem> all = s.all();
                    for (int k = 0; k < Math.min(perPage(), all.size()); k++) {
                        ShopItem it = all.get(k);
                        int cx = CX + (k % cols()) * (cw() + CGAP), cy = CY + (k / cols()) * (ch() + CGAP);
                        small(g, it.section.isEmpty() ? (it.buy >= 0 ? Shop.priceLabel("buy", it.buy, it.currency) : "--") : "> " + it.section, cx + 2, cy + labelY(), AMBER, cw() - 4);
                        small(g, it.sell >= 0 && it.section.isEmpty() ? Shop.priceLabel("sell", it.sell, it.currency) : "", cx + 2, cy + labelY() + 7, PHOS_DIM, cw() - 4);
                        if (it.hidden) {
                            small(g, "hid", cx + cw() - 12, cy + 2, RED, 12);
                        }
                    }
                    CashMachine.wrapUp(g, font, tr(all.size() > perPage() ? "more_pages" : "page_hint", all.size()), SX + 6, bottom, SW - 12, PHOS_DIM);
                }
            }
            case BROWSER -> CashMachine.wrapUp(g, font, tr("browser", found.size()), SX + 6, bottom, SW - 12, PHOS_DIM);
            case SETTINGS -> {
                if (s != null) {
                    String[][] rows = {{"name", s.name}, {"kind", s.kind.name().toLowerCase(Locale.ROOT)}, {"parent", s.parent.isEmpty() ? "-" : s.parent}, {"remote", s.remote ? "on" : "off"},
                            {"buyonly", s.buyOnly ? "on" : "off"}, {"slots", String.valueOf(s.displaySlots)}, {"interval", s.intervalMs / 3_600_000L + " h"}, {"offer", s.offerPercent + "%"},
                            {"offerhours", s.offerEndMs == 0 ? tr("no_end").getString() : Math.max(0, (s.offerEndMs - System.currentTimeMillis()) / 3_600_000L) + " h"},
                            {"hoursmode", s.hours.mode().name().toLowerCase(Locale.ROOT)}, {"hoursopen", hm(s.hours.openMin(), s.hours.mode())}, {"hoursclose", hm(s.hours.closeMin(), s.hours.mode())}, {"id", s.id}};
                    for (int i = 0; i < rows.length; i++) {
                        line(g, SX + 8, SY + 22 + i * SET_STEP, tr("f_" + rows[i][0]).getString(), rows[i][1], SW - 16);
                    }
                    CashMachine.wrapUp(g, font, tr("settings_hint"), SX + 6, bottom, SW - 12, PHOS_DIM);
                }
            }
            case STYLE -> {
                if (s != null) {
                    for (int i = 0; i < STYLES.length; i++) {
                        boolean cur = STYLES[i].equals(s.style);
                        CashMachine.fit(g, font, (cur ? "> " : "  ") + tr("style_" + STYLES[i]).getString(), SX + 8, SY + 32 + i * 14, SW - 16, cur ? PHOS : PHOS_DIM);
                    }
                    g.fill(SX + 4, SY + 78, SX + SW - 4, SY + 79, SCREEN_EDGE);
                    String colsValue = s.columns == 0 ? tr("auto", cols()).getString() : String.valueOf(s.columns);
                    line(g, SX + 8, SY + 86, tr("f_columns").getString(), colsValue, SW - 16);
                    line(g, SX + 8, SY + 100, tr("f_rows").getString(), String.valueOf(s.rows()), SW - 16);
                    CashMachine.wrapUp(g, font, tr("style_hint"), SX + 6, bottom, SW - 12, PHOS_DIM);
                }
            }
        }
        g.disableScissor();
        if (msgH > 0) {
            CashMachine.wrap(g, font, message, SX + 6, SY + SH - 4 - msgH, SW - 12, messageColour);
        }
        // the side panel: the selected card
        ShopItem it = item();
        g.drawString(font, tr("side").getString().toUpperCase(Locale.ROOT), PX + 3, SY + 6, PHOS, false);
        g.fill(PX + 2, SY + 16, PX + PW - 2, SY + 17, SCREEN_EDGE);
        if (view == View.PAGE && it != null) {
            String[][] rows = {{"count", String.valueOf(it.stack.getCount())}, {"buy", it.buy < 0 ? "off" : String.valueOf(it.buy)}, {"sell", it.sell < 0 ? "off" : String.valueOf(it.sell)},
                    {"currency", it.currency < 0 ? "pieces" : Bank.tierName(CoinTier.values()[it.currency])}, {"stockglobal", it.stockGlobal < 0 ? "inf" : String.valueOf(it.stockGlobal)},
                    {"stockplayer", it.stockPlayer < 0 ? "inf" : String.valueOf(it.stockPlayer)}, {"restock", it.restockMs / 60_000 + " m"}, {"instant", it.instant ? "on" : "off"},
                    {"hidden", it.hidden ? "on" : "off"}, {"section", it.section.isEmpty() ? "-" : it.section}};
            g.renderItem(it.stack, PX + PW / 2 - 8, SY + 20);
            for (int i = 0; i < rows.length; i++) {
                boolean editing = fieldName.equals("item." + rows[i][0]);
                small(g, tr("f_" + rows[i][0]).getString(), PX + 3, SY + SIDE_Y + i * SIDE_STEP, editing ? AMBER : PHOS_DIM);
                small(g, rows[i][1], PX + 3, SY + SIDE_Y + 6 + i * SIDE_STEP, PHOS);
            }
        } else if (view == View.PAGE) {
            CashMachine.wrap(g, font, tr("side_hint"), PX + 3, SY + 22, PW - 6, PHOS_DIM);
        }
        if (!fieldName.isEmpty()) {
            // the field sits under the rows; the row being edited is amber
            g.fill(PX + 2, SY + 165, PX + PW - 2, SY + 166, PHOS_DIM | 0xFF000000);
            int py = SY + 167;
            for (var ln : font.split(tr("editing"), (int) ((PW - 4) / 0.75f))) {
                g.pose().pushPose();
                g.pose().translate(PX + 3, py, 0);
                g.pose().scale(0.75f, 0.75f, 1f);
                g.drawString(font, ln, 0, 0, AMBER, false);
                g.pose().popPose();
                py += 7;
            }
        }
    }

    private void small(GuiGraphics g, String s, int x, int y, int colour) {
        small(g, s, x, y, colour, Integer.MAX_VALUE);
    }

    /** Small (three-quarter) text, shrunk further if it would be wider than {@code maxW}. */
    private void small(GuiGraphics g, String s, int x, int y, int colour, int maxW) {
        float scale = Math.min(0.75f, maxW / (float) Math.max(1, font.width(s)));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, s, 0, 0, colour, false);
        g.pose().popPose();
    }

    /** The preview grid follows the shop's layout, as players see it. */
    private int cols() {
        return menu.shop == null ? Shop.MAX_COLUMNS : menu.shop.effectiveColumns(menu.shop.all());
    }

    private int rows() {
        return menu.shop == null ? 3 : menu.shop.rows();
    }

    private int perPage() {
        return cols() * rows();
    }

    private int cw() {
        return (SW - 12 - (cols() - 1) * CGAP) / cols();
    }

    private int ch() {
        return rows() >= 3 ? 36 : 56;
    }

    private int iconY() {
        return rows() >= 3 ? 3 : 10;
    }

    private int labelY() {
        return rows() >= 3 ? 21 : 32;
    }
}
