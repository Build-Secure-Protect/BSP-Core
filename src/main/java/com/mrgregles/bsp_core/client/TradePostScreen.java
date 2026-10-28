package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.TradePostActionPacket;
import com.mrgregles.bsp_core.trade.TradePostBlock;
import com.mrgregles.bsp_core.trade.TradePostMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The Trade Post screen (look C, owner's pick 2026-10-27): one phosphor touch screen with MARKET, SELL and COLLECT tabs, the
 * lots as cards with their item pictures (search and sort above), a lot's own page with BUY, the SELL page with the 9 lot
 * places over the player's inventory, the Collect box, and at the right the players at a Trade Post anywhere on the network,
 * one of whom can be asked to trade. A request to trade comes up as a card over the screen. Everything shown comes from the
 * server ({@link TradePostMenu.View}). Opened from a Portable Banker (no post, owner's pick R2-2) the traders band is gone, the
 * pages take the whole screen and the lot cards run four across.
 */
public class TradePostScreen extends AbstractContainerScreen<TradePostMenu> implements ScreenFit.Fitted {
    private static final int BEZEL = 0xFFCFC8B8, BEZEL_DARK = 0xFF8F877A, BEZEL_LIGHT = 0xFFECE6D8, SCREEN = 0xFF061008, SCREEN_EDGE = 0xFF0D3B22,
            SEL = 0xFF0F2D1C, BAND = 0xFF030805, PHOS = 0xFF58F29A, DIM = 0xFF3FB97A, AMBER = 0xFFF2B544, LABEL = 0xFF5A5348;
    private static final int SX = 10, SY = 10, SW = 280, SH = 196, CX = 14, BX = 210, BW = 80, CARD_H = 40, GAP = 3, ROWS = 3, KEY_Y = 178;
    private static final String[] SORTS = {"newest", "cheapest", "dearest"};

    @Nullable
    private String openLot;
    @Nullable
    private UUID selected;
    private int scroll, sort;
    private String searchText = "", priceText = "";
    @Nullable
    private EditBox search, price;
    private String message = "";
    private int seenSerial = -1, seenPage;
    /** The lot page's lot as it was when opened (it stays open though new listings push it off the page), and its full stacks once they came. */
    @Nullable
    private TradePostMenu.LotView openView;
    @Nullable
    private List<ItemStack> detail;
    /** The server's clock minus this client's, from the last view; the cards' ages run on it between views (views come only on a change). */
    private long clockOffset;
    /** When the search box last changed: the search goes out a quarter second after typing stops. */
    private long searchAt;
    /** The pages' width, the lot cards across and a card's width: 192, 3, 62 beside the traders band; 272, 4, 65 on a Portable Banker. */
    private final int cw, cols, cardW;

    public TradePostScreen(TradePostMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        boolean band = !menu.portable();
        cw = band ? 192 : SW - 8;
        cols = band ? 3 : 4;
        cardW = (cw - GAP * (cols - 1)) / cols;
        this.imageWidth = TradePostMenu.W;
        this.imageHeight = menu.page == TradePostBlock.Tab.SELL ? TradePostMenu.H_SELL : TradePostMenu.H;
        if (menu.page == TradePostBlock.Tab.TRADE) {
            menu.page = TradePostBlock.Tab.MARKET;   // the TRADE key: the market with the traders band in front
        }
    }

    /** A view from the server for the open screen. */
    public static void receive(TradePostMenu.View view) {
        if (Minecraft.getInstance().screen instanceof TradePostScreen s) {
            s.menu.view = view;
            s.menu.viewSerial++;
            s.clockOffset = view.now() - System.currentTimeMillis();
        }
    }

    /** The full stacks of the lot the player opened, or word that it is gone. */
    public static void receiveLot(com.mrgregles.bsp_core.network.TradePostLotPacket lot) {
        if (Minecraft.getInstance().screen instanceof TradePostScreen s && lot.id().equals(s.openLot)) {
            if (lot.found()) {
                s.detail = lot.items();
            } else {
                s.openLot(null);
                s.message = Component.translatable("message.bsp_core.market.gone").getString();
            }
        }
    }

    private long serverNow() {
        return System.currentTimeMillis() + clockOffset;
    }

    /** Asks for the market as searched, sorted and on {@code page}. */
    private void askView(int page) {
        send(TradePostActionPacket.Action.VIEW, searchText, sort + 3L * Math.max(0, page), false);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (searchAt > 0 && System.currentTimeMillis() >= searchAt) {
            searchAt = 0;
            askView(0);
        }
    }

    private TradePostMenu.View v() {
        return menu.view;
    }

    private static void send(TradePostActionPacket.Action action, String text, long amount, boolean flag) {
        BSPNetwork.CHANNEL.sendToServer(new TradePostActionPacket(action, text, amount, flag));
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        imageHeight = menu.page == TradePostBlock.Tab.SELL ? TradePostMenu.H_SELL : TradePostMenu.H;
        super.init();
        search = null;
        price = null;
        if (menu.page == TradePostBlock.Tab.MARKET && openLot == null) {
            search = new EditBox(font, leftPos + CX + 3, topPos + 33, 120, 10, Component.translatable("gui.bsp_core.trade_post.search"));
            search.setBordered(false);
            search.setTextColor(PHOS);
            search.setMaxLength(40);
            search.setValue(searchText);
            search.setResponder(s -> {
                searchText = s;
                scroll = 0;
                searchAt = System.currentTimeMillis() + 250;
            });
            addRenderableWidget(search);
        }
        if (menu.page == TradePostBlock.Tab.SELL) {
            price = new EditBox(font, leftPos + 81, topPos + 70, 120, 10, Component.translatable("gui.bsp_core.trade_post.price"));
            price.setBordered(false);
            price.setTextColor(PHOS);
            price.setMaxLength(13);
            price.setFilter(s -> s.matches("\\d*"));
            price.setValue(priceText);
            price.setResponder(s -> priceText = s);
            addRenderableWidget(price);
        }
    }

    private void page(TradePostBlock.Tab tab) {
        menu.page = tab;
        openLot = null;
        openView = null;
        detail = null;
        scroll = 0;
        rebuildWidgets();
    }

    private void openLot(@Nullable String id) {
        openLot = id;
        openView = id == null ? null : lot(id);
        detail = null;
        if (id != null) {
            send(TradePostActionPacket.Action.DETAIL, id, 0, false);
        }
        rebuildWidgets();
    }

    @Override
    public int panelWidth() {
        return imageWidth;
    }

    @Override
    public int panelHeight() {
        return imageHeight;
    }

    // ------------------------------------------------------------------ reading the view

    @Nullable
    private TradePostMenu.LotView lot(String id) {
        return v().lots().stream().filter(l -> l.id().equals(id)).findFirst().orElse(null);
    }

    private boolean mine(TradePostMenu.LotView l) {
        return minecraft != null && minecraft.player != null && l.seller().equals(minecraft.player.getUUID());
    }

    private long tax(long p) {
        int pct = v().taxPercent();
        return pct <= 0 || p <= 0 ? 0 : Math.max(1, (p * pct + 99) / 100);
    }

    private static String ago(long ms) {
        long m = Math.max(0, ms) / 60000;
        return m < 60 ? m + " m" : m < 60 * 24 ? m / 60 + " h" : m / (60 * 24) + " d";
    }

    private static String left(long ms) {
        long m = Math.max(0, ms) / 60000;
        return m / (60 * 24) + " d " + (m / 60) % 24 + " h";
    }

    private static String title(TradePostMenu.LotView l) {
        if (l.items().isEmpty()) {
            return "?";
        }
        ItemStack first = l.items().get(0);
        String s = (first.getCount() > 1 ? first.getCount() + " " : "") + first.getHoverName().getString();
        return l.items().size() > 1 ? s + " +" + (l.items().size() - 1) : s;
    }

    private long priceValue() {
        try {
            return priceText.isEmpty() ? 0 : Long.parseLong(priceText);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean lotSlotsEmpty() {
        for (int i = 0; i < 9; i++) {
            if (!menu.lotSlots().getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ input

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double x = mx - leftPos, y = my - topPos;
        if (!menu.portable() && !v().toMe().isEmpty()) {
            TradePostMenu.RequestView r = v().toMe().get(0);
            if (over(x, y, 85, 113, 60, 16) || over(x, y, 155, 113, 60, 16)) {
                send(TradePostActionPacket.Action.ANSWER, r.from().toString(), 0, over(x, y, 85, 113, 60, 16));
                return true;
            }
            if (over(x, y, SX, SY, SW, SH)) {
                return true;
            }
        }
        if (over(x, y, 276, 12, 11, 11)) {
            onClose();
            return true;
        }
        int tx = CX;
        for (TradePostBlock.Tab tab : new TradePostBlock.Tab[]{TradePostBlock.Tab.MARKET, TradePostBlock.Tab.SELL, TradePostBlock.Tab.COLLECT}) {
            int w = tabWidth(tab);
            if (over(x, y, tx, 13, w, 13)) {
                page(tab);
                return true;
            }
            tx += w + 3;
        }
        if (!menu.portable() && clickBand(x, y)) {
            return true;
        }
        if (clickPage(x, y)) {
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    /** The traders band: pick a name, then ask (or take a request back). */
    private boolean clickBand(double x, double y) {
        List<TradePostMenu.TraderView> traders = v().traders();
        for (int i = 0; i < Math.min(7, traders.size()); i++) {
            if (over(x, y, BX + 2, 26 + i * 21, BW - 4, 20)) {
                selected = traders.get(i).id();
                return true;
            }
        }
        if (over(x, y, BX + 4, 186, BW - 8, 16)) {
            if (!v().requestTo().isEmpty()) {
                send(TradePostActionPacket.Action.WITHDRAW, "", 0, false);
            } else if (selectedTrader() != null && !selectedTrader().busy()) {
                send(TradePostActionPacket.Action.ASK, selected.toString(), 0, false);
            }
            return true;
        }
        return false;
    }

    @Nullable
    private TradePostMenu.TraderView selectedTrader() {
        return selected == null ? null : v().traders().stream().filter(t -> t.id().equals(selected)).findFirst().orElse(null);
    }

    private boolean clickPage(double x, double y) {
        switch (menu.page) {
            case SELL -> {
                if (over(x, y, CX, KEY_Y, 70, 16) && !lotSlotsEmpty() && priceValue() > 0) {
                    send(TradePostActionPacket.Action.LIST, "", priceValue(), false);
                    return true;
                }
            }
            case COLLECT -> {
                if (over(x, y, CX, KEY_Y, 80, 16) && !v().collect().isEmpty()) {
                    send(TradePostActionPacket.Action.COLLECT, "", 0, false);
                    return true;
                }
            }
            default -> {
                if (openLot != null) {
                    TradePostMenu.LotView l = openView;
                    if (over(x, y, CX + 76, KEY_Y, 44, 16)) {
                        openLot(null);
                        return true;
                    }
                    if (l != null && over(x, y, CX, KEY_Y, 72, 16)) {
                        send(mine(l) ? TradePostActionPacket.Action.CANCEL : TradePostActionPacket.Action.BUY, l.id(), 0, false);
                        return true;
                    }
                    return false;
                }
                if (over(x, y, CX + cw - 60, 31, 60, 13)) {
                    sort = (sort + 1) % 3;
                    scroll = 0;
                    askView(0);
                    return true;
                }
                if (v().pages() > 1 && over(x, y, CX + cw - 48, 174, 12, 10) && v().page() > 0) {
                    askView(v().page() - 1);
                    return true;
                }
                if (v().pages() > 1 && over(x, y, CX + cw - 12, 174, 12, 10) && v().page() < v().pages() - 1) {
                    askView(v().page() + 1);
                    return true;
                }
                List<TradePostMenu.LotView> lots = v().lots();
                for (int i = scroll * cols; i < Math.min(lots.size(), (scroll + ROWS) * cols); i++) {
                    int c = i % cols, r = i / cols - scroll;
                    if (over(x, y, CX + c * (cardW + GAP), 46 + r * (CARD_H + GAP), cardW, CARD_H)) {
                        openLot(lots.get(i).id());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (menu.page == TradePostBlock.Tab.MARKET && openLot == null) {
            int rows = (v().lots().size() + cols - 1) / cols;
            scroll = Math.max(0, Math.min(Math.max(0, rows - ROWS), scroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        EditBox focus = search != null && search.isFocused() ? search : price != null && price.isFocused() ? price : null;
        if (focus != null && key != 256) {
            return focus.keyPressed(key, scan, mods) || focus.canConsumeInput();
        }
        return super.keyPressed(key, scan, mods);
    }

    // ------------------------------------------------------------------ drawing

    private int tabWidth(TradePostBlock.Tab tab) {
        return font.width(tabLabel(tab)) + 12;
    }

    private String tabLabel(TradePostBlock.Tab tab) {
        String s = Component.translatable("gui.bsp_core.trade_post.tab." + tab.name().toLowerCase(Locale.ROOT)).getString();
        return tab == TradePostBlock.Tab.COLLECT && v().collectCount() > 0 ? s + " " + v().collectCount() : s;
    }

    /** A touch-screen key: lit (filled) or outlined, dimmed when it cannot be pressed. */
    private void key(GuiGraphics g, int x, int y, int w, int h, String label, boolean on, boolean enabled, int mx, int my) {
        boolean hover = enabled && over(mx - leftPos, my - topPos, x, y, w, h);
        if (on && enabled) {
            g.fill(x, y, x + w, y + h, hover ? 0xFF7CF7B3 : PHOS);
            CashMachine.fit(g, font, label, x + 3, y + (h - 8) / 2, w - 6, SCREEN);
        } else {
            int edge = enabled ? (hover ? PHOS : DIM) : SCREEN_EDGE;
            g.fill(x, y, x + w, y + 1, edge);
            g.fill(x, y + h - 1, x + w, y + h, edge);
            g.fill(x, y, x + 1, y + h, edge);
            g.fill(x + w - 1, y, x + w, y + h, edge);
            CashMachine.fit(g, font, label, x + 3, y + (h - 8) / 2, w - 6, enabled ? PHOS : SCREEN_EDGE);
        }
    }

    private static void slotBox(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, SCREEN_EDGE);
        g.fill(x + 1, y + 1, x + 17, y + 17, SEL);
    }

    private static String tr(String key, Object... args) {
        return Component.translatable("gui.bsp_core.trade_post." + key, args).getString();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        if (menu.viewSerial != seenSerial) {
            seenSerial = menu.viewSerial;
            if (!v().message().isEmpty()) {
                message = Component.translatable(v().message(), v().args().toArray()).getString();
                // the open lot was bought, cancelled or is gone: back to the cards
                if (openLot != null && (v().message().endsWith(".bought") || v().message().endsWith(".cancelled") || v().message().endsWith(".gone"))) {
                    openLot(null);
                }
            }
            if (v().page() != seenPage) {
                seenPage = v().page();
                scroll = 0;
            }
            // fewer lots than before (sold, ended, a narrower search): never scrolled past the end
            scroll = Math.min(scroll, Math.max(0, (v().lots().size() + cols - 1) / cols - ROWS));
        }
        renderBackground(g);
        super.render(g, mx, my, partial);
        ItemStack hovered = hoveredShown(mx - leftPos, my - topPos);
        if (!hovered.isEmpty()) {
            g.renderTooltip(font, hovered, mx, my);
        } else {
            renderTooltip(g, mx, my);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x - 2, y - 2, x + imageWidth + 2, y + imageHeight + 2, BEZEL_DARK);
        g.fill(x, y, x + imageWidth, y + imageHeight, BEZEL);
        g.fill(x, y, x + imageWidth, y + 2, BEZEL_LIGHT);
        g.fill(x + SX - 1, y + SY - 1, x + SX + SW + 1, y + SY + SH + 1, SCREEN_EDGE);
        g.fill(x + SX, y + SY, x + SX + SW, y + SY + SH, SCREEN);
        if (!menu.portable()) {
            g.fill(x + BX, y + SY, x + SX + SW, y + SY + SH, BAND);
        }
        if (menu.page == TradePostBlock.Tab.SELL) {
            for (int i = 0; i < 9; i++) {
                slotBox(g, x + TradePostMenu.SELL_X - 1 + (i % 3) * 18, y + TradePostMenu.SELL_Y - 1 + (i / 3) * 18);
            }
            for (int r = 0; r < 4; r++) {
                for (int c = 0; c < 9; c++) {
                    int sx = x + TradePostMenu.INV_X - 1 + c * 18, sy = y + TradePostMenu.INV_Y - 1 + (r < 3 ? r * 18 : 58);
                    g.fill(sx, sy, sx + 18, sy + 18, 0xFF373737);
                    g.fill(sx + 1, sy + 1, sx + 18, sy + 18, 0xFFFFFFFF);
                    g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF8B8B8B);
                }
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        // tabs and the close mark
        int tx = CX;
        for (TradePostBlock.Tab tab : new TradePostBlock.Tab[]{TradePostBlock.Tab.MARKET, TradePostBlock.Tab.SELL, TradePostBlock.Tab.COLLECT}) {
            int w = tabWidth(tab);
            key(g, tx, 13, w, 13, tabLabel(tab), menu.page == tab, true, mx, my);
            tx += w + 3;
        }
        key(g, 276, 12, 11, 11, "x", false, true, mx, my);
        g.fill(CX, 28, CX + cw, 29, SCREEN_EDGE);
        switch (menu.page) {
            case SELL -> drawSell(g, mx, my);
            case COLLECT -> drawCollect(g, mx, my);
            default -> {
                if (openLot != null && openView != null) {
                    drawLot(g, openView, mx, my);
                } else {
                    drawMarket(g, mx, my);
                }
            }
        }
        if (!message.isEmpty()) {
            CashMachine.fit(g, font, message, CX, 196, cw, AMBER);
        }
        if (!menu.portable()) {
            drawBand(g, mx, my);
        }
        if (menu.page == TradePostBlock.Tab.SELL) {
            g.drawString(font, tr("inventory"), TradePostMenu.INV_X - 1, TradePostMenu.INV_Y - 11, LABEL, false);
        }
        if (!menu.portable() && !v().toMe().isEmpty()) {
            drawRequest(g, v().toMe().get(0), mx, my);
        }
    }

    private void drawMarket(GuiGraphics g, int mx, int my) {
        g.fill(CX, 31, CX + 128, 44, SEL);
        if (searchText.isEmpty() && (search == null || !search.isFocused())) {
            g.drawString(font, tr("search"), CX + 3, 33, DIM, false);
        }
        key(g, CX + cw - 60, 31, 60, 13, tr("sort." + SORTS[sort]), false, true, mx, my);
        List<TradePostMenu.LotView> lots = v().lots();
        if (lots.isEmpty()) {
            g.drawString(font, tr(searchText.isEmpty() ? "no_lots" : "no_match"), CX + 2, 50, DIM, false);
        }
        for (int i = scroll * cols; i < Math.min(lots.size(), (scroll + ROWS) * cols); i++) {
            TradePostMenu.LotView l = lots.get(i);
            int c = i % cols, r = i / cols - scroll, x = CX + c * (cardW + GAP), y = 46 + r * (CARD_H + GAP);
            boolean hover = over(mx - leftPos, my - topPos, x, y, cardW, CARD_H);
            int edge = hover ? PHOS : SCREEN_EDGE;
            g.fill(x, y, x + cardW, y + CARD_H, hover ? SEL : SCREEN);
            g.fill(x, y, x + cardW, y + 1, edge);
            g.fill(x, y + CARD_H - 1, x + cardW, y + CARD_H, edge);
            g.fill(x, y, x + 1, y + CARD_H, edge);
            g.fill(x + cardW - 1, y, x + cardW, y + CARD_H, edge);
            if (!l.items().isEmpty()) {
                g.renderItem(l.items().get(0), x + cardW / 2 - 8, y + 3);
                g.renderItemDecorations(font, l.items().get(0), x + cardW / 2 - 8, y + 3);
            }
            if (mine(l)) {
                small(g, tr("yours"), x + 3, y + 3, AMBER, cardW / 2 - 12);
            }
            if (l.items().size() > 1) {
                String more = "+" + (l.items().size() - 1);
                g.drawString(font, more, x + cardW - 3 - font.width(more), y + 3, DIM, false);
            }
            String p = Bank.format(l.price());
            CashMachine.fit(g, font, p, x + (cardW - Math.min(font.width(p), cardW - 4)) / 2, y + 21, cardW - 4, mine(l) ? AMBER : PHOS);
            String sub = (l.items().isEmpty() ? "" : l.items().get(0).getHoverName().getString()) + " · " + ago(serverNow() - l.listedMs());
            small(g, sub, x + 3, y + 31, DIM, cardW - 6);
        }
        int rows = (lots.size() + cols - 1) / cols;
        String foot = (searchText.isEmpty() ? tr("footer", v().totalLots(), v().myLots(), v().maxLots()) : tr("footer_match", v().matched(), v().totalLots(), v().myLots(), v().maxLots()))
                + (rows > ROWS ? "  " + tr("scroll") : "");
        int footW = cw;
        if (v().pages() > 1) {
            // pages of the market: < n / m >
            footW = cw - 52;
            key(g, CX + cw - 48, 174, 12, 10, "<", false, v().page() > 0, mx, my);
            String pg = (v().page() + 1) + "/" + v().pages();
            small(g, pg, CX + cw - 24 - (int) (font.width(pg) * 0.75f / 2), 177, DIM, 24);
            key(g, CX + cw - 12, 174, 12, 10, ">", false, v().page() < v().pages() - 1, mx, my);
        }
        small(g, foot, CX, 177, DIM, footW);
    }

    /** The open lot's stacks: in full once they came from the server, their pictures until then. */
    private List<ItemStack> lotItems(TradePostMenu.LotView l) {
        return detail != null ? detail : l.items();
    }

    private void drawLot(GuiGraphics g, TradePostMenu.LotView l, int mx, int my) {
        CashMachine.fit(g, font, tr("lot_by", l.sellerName()).toUpperCase(Locale.ROOT), CX, 32, cw, PHOS);
        long now = serverNow();
        small(g, tr("lot_times", ago(now - l.listedMs()), left(l.endsMs() - now)), CX, 43, DIM, cw);
        List<ItemStack> items = lotItems(l);
        for (int i = 0; i < 9; i++) {
            int sx = CX + (i % 3) * 18, sy = 54 + (i / 3) * 18;
            slotBox(g, sx, sy);
            if (i < items.size()) {
                g.renderItem(items.get(i), sx + 1, sy + 1);
                g.renderItemDecorations(font, items.get(i), sx + 1, sy + 1);
            }
        }
        int px = CX + 62, pw = cw - 62, y = 56;
        long tax = tax(l.price());
        y = row(g, tr("price"), Bank.format(l.price()), px, y, pw, PHOS);
        y = row(g, tr("tax", v().taxPercent()), Bank.format(tax), px, y, pw, PHOS);
        g.fill(px, y, px + pw, y + 1, SCREEN_EDGE);
        y = row(g, tr("you_pay"), Bank.format(l.price() + tax), px, y + 3, pw, AMBER);
        row(g, tr("balance"), Bank.format(v().balance()), px, y + 4, pw, DIM);
        StringBuilder all = new StringBuilder();
        for (ItemStack s : l.items()) {
            all.append(all.length() == 0 ? "" : ", ").append(s.getCount()).append(" ").append(s.getHoverName().getString());
        }
        CashMachine.wrap(g, font, Component.literal(all + ". " + tr("bought_whole")), CX, 112, cw, DIM);
        if (mine(l)) {
            key(g, CX, KEY_Y, 72, 16, tr("cancel_lot"), true, true, mx, my);
        } else {
            boolean can = v().balance() >= l.price() + tax;
            key(g, CX, KEY_Y, 72, 16, tr("buy", Bank.format(l.price() + tax)), true, can, mx, my);
        }
        key(g, CX + 76, KEY_Y, 44, 16, tr("back"), false, true, mx, my);
    }

    private int row(GuiGraphics g, String left, String right, int x, int y, int w, int colour) {
        CashMachine.fit(g, font, left, x, y, w - font.width(right) - 4, DIM);
        g.drawString(font, right, x + w - font.width(right), y, colour, false);
        return y + 11;
    }

    private void drawSell(GuiGraphics g, int mx, int my) {
        CashMachine.fit(g, font, tr("list_title"), CX, 32, cw, PHOS);
        small(g, tr("list_limits", v().myLots(), v().maxLots(), v().days()), CX, 43, DIM, cw);
        int px = 78, pw = cw - 64;
        small(g, tr("price_in_pieces"), px, 58, DIM, pw);
        g.fill(px, 67, px + pw, 81, SEL);
        int edge = price != null && price.isFocused() ? PHOS : DIM;
        g.fill(px, 67, px + pw, 68, edge);
        g.fill(px, 80, px + pw, 81, edge);
        long p = priceValue(), tax = tax(p);
        CashMachine.wrap(g, font, Component.literal(p > 0 ? tr("tax_now", Bank.format(tax), Bank.format(p + tax)) : tr("set_price")), px, 85, pw, DIM);
        CashMachine.wrap(g, font, Component.literal(tr("put_stacks", v().maxStacks())), CX, 116, cw, DIM);
        key(g, CX, KEY_Y, 70, 16, tr("list"), true, !lotSlotsEmpty() && p > 0, mx, my);
    }

    private void drawCollect(GuiGraphics g, int mx, int my) {
        CashMachine.fit(g, font, tr("collect_title"), CX, 32, cw, PHOS);
        List<ItemStack> box = v().collect();
        small(g, tr("collect_count", box.size()), CX, 43, DIM, cw);
        for (int i = 0; i < Math.min(box.size(), 45); i++) {
            int sx = CX + (i % 9) * 18 + 6, sy = 54 + (i / 9) * 18;
            slotBox(g, sx, sy);
            g.renderItem(box.get(i), sx + 1, sy + 1);
            g.renderItemDecorations(font, box.get(i), sx + 1, sy + 1);
        }
        if (box.isEmpty()) {
            CashMachine.wrap(g, font, Component.literal(tr("collect_note")), CX, 60, cw, DIM);
        }
        key(g, CX, KEY_Y, 80, 16, tr("collect_all"), true, !box.isEmpty(), mx, my);
    }

    private void drawBand(GuiGraphics g, int mx, int my) {
        List<TradePostMenu.TraderView> traders = v().traders();
        small(g, tr("traders", traders.size()), BX + 4, 15, DIM, 60);
        if (traders.isEmpty()) {
            CashMachine.wrap(g, font, Component.literal(tr("nobody")), BX + 4, 30, BW - 8, DIM);
        }
        for (int i = 0; i < Math.min(7, traders.size()); i++) {
            TradePostMenu.TraderView t = traders.get(i);
            int y = 26 + i * 21;
            boolean sel = t.id().equals(selected), hover = over(mx - leftPos, my - topPos, BX + 2, y, BW - 4, 20);
            if (sel || hover) {
                g.fill(BX + 2, y, BX + BW - 2, y + 20, SEL);
            }
            g.fill(BX + 4, y + 4, BX + 7, y + 7, t.busy() ? SCREEN_EDGE : PHOS);
            CashMachine.fit(g, font, t.name(), BX + 10, y + 1, BW - 14, t.busy() ? DIM : PHOS);
            small(g, t.busy() ? tr("busy") : t.server(), BX + 10, y + 11, DIM, BW - 14);
        }
        TradePostMenu.TraderView s = selectedTrader();
        if (!v().requestTo().isEmpty()) {
            small(g, tr("asked", v().requestTo(), v().requestSeconds()), BX + 4, 175, AMBER, BW - 8);
            key(g, BX + 4, 186, BW - 8, 16, tr("take_back"), false, true, mx, my);
        } else {
            key(g, BX + 4, 186, BW - 8, 16, s == null ? tr("pick") : tr("ask", s.name()), s != null, s != null && !s.busy(), mx, my);
        }
    }

    private void drawRequest(GuiGraphics g, TradePostMenu.RequestView r, int mx, int my) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(SX, SY, SX + SW, SY + SH, 0xA0000000);
        int bx = 75, by = 75, bw = 150, bh = 66;
        g.fill(bx, by, bx + bw, by + bh, AMBER);
        g.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, SCREEN);
        String head = tr("asks", r.name()).toUpperCase(Locale.ROOT);
        CashMachine.fit(g, font, head, bx + (bw - Math.min(font.width(head), bw - 8)) / 2, by + 8, bw - 8, AMBER);
        String sub = tr("answer_in", r.server(), r.seconds());
        small(g, sub, bx + 6, by + 21, DIM, bw - 12);
        key(g, 85, 113, 60, 16, tr("accept"), true, true, mx, my);
        key(g, 155, 113, 60, 16, tr("decline"), false, true, mx, my);
        g.pose().popPose();
    }

    /** Text at three quarters of the font's size, cut to fit {@code w}. */
    private void small(GuiGraphics g, String s, int x, int y, int colour, int w) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, font.plainSubstrByWidth(s, (int) (w / 0.75f)), 0, 0, colour, false);
        g.pose().popPose();
    }

    /** The item under the mouse on a lot page, a card or the Collect box (positions relative to the panel). */
    private ItemStack hoveredShown(double x, double y) {
        if (menu.page == TradePostBlock.Tab.COLLECT) {
            List<ItemStack> box = v().collect();
            for (int i = 0; i < Math.min(box.size(), 45); i++) {
                if (over(x, y, CX + (i % 9) * 18 + 6, 54 + (i / 9) * 18, 18, 18)) {
                    return box.get(i);
                }
            }
        } else if (menu.page == TradePostBlock.Tab.MARKET && openLot != null && openView != null) {
            List<ItemStack> items = lotItems(openView);
            for (int i = 0; i < items.size(); i++) {
                if (over(x, y, CX + (i % 3) * 18, 54 + (i / 3) * 18, 18, 18)) {
                    return items.get(i);
                }
            }
        } else if (menu.page == TradePostBlock.Tab.MARKET) {
            List<TradePostMenu.LotView> lots = v().lots();
            for (int i = scroll * cols; i < Math.min(lots.size(), (scroll + ROWS) * cols); i++) {
                int c = i % cols, r = i / cols - scroll, cx = CX + c * (cardW + GAP) + cardW / 2 - 8, cy = 46 + r * (CARD_H + GAP) + 3;
                if (over(x, y, cx, cy, 16, 16) && !lots.get(i).items().isEmpty()) {
                    return lots.get(i).items().get(0);
                }
            }
        }
        return ItemStack.EMPTY;
    }
}
