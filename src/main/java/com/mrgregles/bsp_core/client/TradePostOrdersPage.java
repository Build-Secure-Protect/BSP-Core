package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.market.BuyOrders;
import com.mrgregles.bsp_core.network.TradePostActionPacket;
import com.mrgregles.bsp_core.trade.TradePostMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The MARKET page's WANTED side on the Trade Post screen (option A, owner's pick 2026-11-03): the buy orders as cards like the
 * lots (the first card places a new one), an order's own page to fill it from the inventory (or, for the buyer, to cancel it),
 * and the NEW ORDER page, which finds the item by name among every item that can be ordered and takes how many and the price
 * each. Everything shown comes from the server ({@link TradePostMenu.OrdersView}); the screen hands its drawing and clicks here
 * while WANTED is on.
 */
final class TradePostOrdersPage {
    private static final int SCREEN = 0xFF061008, SCREEN_EDGE = 0xFF0D3B22, SEL = 0xFF0F2D1C, PHOS = 0xFF58F29A, DIM = 0xFF3FB97A, AMBER = 0xFFF2B544;
    private static final int CX = 14, CARD_H = 40, GAP = 3, ROWS = 3, KEY_Y = 178;

    /** Every item that can be ordered, with its name in lower case for the search; made once. */
    private record Choice(Item item, ItemStack plain, String name) {}

    private static List<Choice> choices;

    private static TradePostMenu.OrdersView view = TradePostMenu.OrdersView.EMPTY;
    private static int serial;

    private final TradePostScreen screen;
    private final Font font;
    private final int cw, cols, cardW;
    private int scroll, seenSerial = -1;
    /** The order whose page is open (as it was when opened, kept though it leaves the page of cards), else null. */
    @Nullable
    private TradePostMenu.OrderView open;
    private int send = 1;
    /** The NEW ORDER page: open, the search and the item picked, how many and the price each as typed. */
    private boolean placing;
    private String findText = "", countText = "", eachText = "";
    @Nullable
    private Choice picked;
    private int resultScroll;
    @Nullable
    private EditBox find, count, each;

    TradePostOrdersPage(TradePostScreen screen, Font font, int cw, int cols, int cardW) {
        this.screen = screen;
        this.font = font;
        this.cw = cw;
        this.cols = cols;
        this.cardW = cardW;
    }

    static void receive(TradePostMenu.OrdersView v) {
        view = v;
        serial++;
    }

    /** The screen closed: the next one starts empty until the server sends. */
    static void reset() {
        view = TradePostMenu.OrdersView.EMPTY;
    }

    TradePostMenu.OrdersView view() {
        return view;
    }

    /** Whether the page of cards shows (no order page, no NEW ORDER page): the screen's search box and sort key are for it then. */
    boolean cards() {
        return open == null && !placing;
    }

    /** Back to the cards, without rebuilding the screen's widgets (the screen does that itself). */
    void closePages() {
        open = null;
        placing = false;
        picked = null;
    }

    /** Back to the cards. */
    void back() {
        open = null;
        placing = false;
        picked = null;
        screen.rebuild();
    }

    private static void send(TradePostActionPacket.Action action, String text, long amount) {
        TradePostScreen.send(action, text, amount, true);
    }

    // ------------------------------------------------------------------ widgets

    /** The NEW ORDER page's text boxes, placed for the screen at {@code left}, {@code top}; none on the other pages. */
    List<EditBox> widgets(int left, int top) {
        find = count = each = null;
        if (!placing) {
            return List.of();
        }
        find = box(left + CX + 3, top + 44, cw - 6, 40, findText, s -> {
            findText = s;
            resultScroll = 0;
        });
        count = box(left + CX + 67, top + 94, 34, 6, countText, s -> countText = s);
        count.setFilter(s -> s.matches("\\d*"));
        each = box(left + CX + 67, top + 108, 70, 13, eachText, s -> eachText = s);
        each.setFilter(s -> s.matches("\\d*"));
        return List.of(find, count, each);
    }

    private EditBox box(int x, int y, int w, int max, String value, java.util.function.Consumer<String> responder) {
        EditBox b = new EditBox(font, x, y, w, 10, Component.empty());
        b.setBordered(false);
        b.setTextColor(PHOS);
        b.setMaxLength(max);
        b.setValue(value);
        b.setResponder(responder);
        return b;
    }

    /** The text box that has the keyboard, if one of this page's does. */
    @Nullable
    EditBox focused() {
        for (EditBox b : new EditBox[]{find, count, each}) {
            if (b != null && b.isFocused()) {
                return b;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ reading

    private boolean mine(TradePostMenu.OrderView o) {
        return Minecraft.getInstance().player != null && o.buyer().equals(Minecraft.getInstance().player.getUUID());
    }

    @Nullable
    private TradePostMenu.OrderView byId(String id) {
        return view.orders().stream().filter(o -> o.id().equals(id)).findFirst().orElse(null);
    }

    /** The order on its page as it now stands: the latest view of it, else as it was when opened. */
    @Nullable
    private TradePostMenu.OrderView current() {
        if (open == null) {
            return null;
        }
        TradePostMenu.OrderView now = byId(open.id());
        return now != null ? now : open;
    }

    private static List<Choice> choices() {
        if (choices == null) {
            List<Choice> out = new ArrayList<>();
            for (Item item : ForgeRegistries.ITEMS) {
                if (BuyOrders.orderable(item)) {
                    ItemStack plain = BuyOrders.plain(item);
                    out.add(new Choice(item, plain, plain.getHoverName().getString().toLowerCase(Locale.ROOT)));
                }
            }
            out.sort(java.util.Comparator.comparing(Choice::name));
            choices = out;
        }
        return choices;
    }

    /** The items whose name holds the search; none until something is typed. */
    private List<Choice> results() {
        String q = findText.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return List.of();
        }
        List<Choice> out = new ArrayList<>();
        for (Choice c : choices()) {
            if (c.name().contains(q)) {
                out.add(c);
            }
        }
        // names starting with the search first
        out.sort(java.util.Comparator.comparingInt((Choice c) -> c.name().startsWith(q) ? 0 : 1));
        return out;
    }

    private int perRow() {
        return Math.max(1, (cw + 1) / 19);
    }

    private static long num(String s) {
        try {
            return s.isEmpty() ? 0 : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int maxCount() {
        // the server's market.maxStacks (from its view), not this client's config
        return picked == null ? 0 : screen.maxStacks() * Math.max(1, picked.plain().getMaxStackSize());
    }

    private long total() {
        long n = num(countText), e = num(eachText);
        return n <= 0 || e <= 0 || e > Long.MAX_VALUE / Math.max(1, n) ? 0 : n * e;
    }

    private boolean canPlace() {
        long n = num(countText);
        return picked != null && n >= 1 && n <= maxCount() && num(eachText) >= 1 && total() > 0 && total() <= screen.balance()
                && view.mine() < view.maxOrders();
    }

    // ------------------------------------------------------------------ input

    /** A click on the page (positions relative to the panel); true when it was taken. */
    boolean click(double x, double y) {
        if (placing) {
            return clickPlacing(x, y);
        }
        TradePostMenu.OrderView o = current();
        if (o != null) {
            return clickOrder(o, x, y);
        }
        if (view.pages() > 1 && TradePostScreen.over(x, y, CX + cw - 48, 174, 12, 10) && view.page() > 0) {
            screen.askView(view.page() - 1);
            return true;
        }
        if (view.pages() > 1 && TradePostScreen.over(x, y, CX + cw - 12, 174, 12, 10) && view.page() < view.pages() - 1) {
            screen.askView(view.page() + 1);
            return true;
        }
        int n = view.orders().size() + 1;
        for (int i = scroll * cols; i < Math.min(n, (scroll + ROWS) * cols); i++) {
            int c = i % cols, r = i / cols - scroll;
            if (TradePostScreen.over(x, y, CX + c * (cardW + GAP), 46 + r * (CARD_H + GAP), cardW, CARD_H)) {
                if (i == 0) {
                    placing = true;
                    picked = null;
                    findText = "";
                    countText = "";
                    eachText = "";
                    resultScroll = 0;
                } else {
                    open = view.orders().get(i - 1);
                    send = Math.max(1, Math.min(open.toGo(), open.carried()));
                }
                screen.rebuild();
                return true;
            }
        }
        return false;
    }

    private boolean clickOrder(TradePostMenu.OrderView o, double x, double y) {
        if (TradePostScreen.over(x, y, CX + 76, KEY_Y, 44, 16)) {
            back();
            return true;
        }
        if (mine(o)) {
            if (TradePostScreen.over(x, y, CX, KEY_Y, 72, 16)) {
                send(TradePostActionPacket.Action.UNORDER, o.id(), 0);
                return true;
            }
            return false;
        }
        int most = Math.max(1, Math.min(o.toGo(), o.carried()));
        if (TradePostScreen.over(x, y, CX + 30, 91, 12, 11)) {
            send = Math.max(1, send - (Screen.hasShiftDown() ? Math.max(1, o.item().getMaxStackSize()) : 1));
            return true;
        }
        if (TradePostScreen.over(x, y, CX + 76, 91, 12, 11)) {
            send = Math.min(most, send + (Screen.hasShiftDown() ? Math.max(1, o.item().getMaxStackSize()) : 1));
            return true;
        }
        if (TradePostScreen.over(x, y, CX + 92, 91, 26, 11)) {
            send = most;
            return true;
        }
        if (TradePostScreen.over(x, y, CX, KEY_Y, 72, 16) && o.carried() > 0) {
            send(TradePostActionPacket.Action.FILL, o.id(), Math.min(send, most));
            return true;
        }
        return false;
    }

    private boolean clickPlacing(double x, double y) {
        if (TradePostScreen.over(x, y, CX + 76, KEY_Y, 44, 16)) {
            back();
            return true;
        }
        List<Choice> found = results();
        int per = perRow();
        for (int i = 0; i < per && resultScroll * per + i < found.size(); i++) {
            if (TradePostScreen.over(x, y, CX + i * 19, 58, 18, 18)) {
                picked = found.get(resultScroll * per + i);
                if (countText.isEmpty()) {
                    countText = String.valueOf(Math.min(maxCount(), Math.max(1, picked.plain().getMaxStackSize())));
                    if (count != null) {
                        count.setValue(countText);
                    }
                }
                return true;
            }
        }
        if (TradePostScreen.over(x, y, CX + 52, 93, 12, 11)) {
            step(count, -1);
            return true;
        }
        if (TradePostScreen.over(x, y, CX + 104, 93, 12, 11)) {
            step(count, 1);
            return true;
        }
        if (TradePostScreen.over(x, y, CX + 52, 107, 12, 11)) {
            step(each, -1);
            return true;
        }
        if (TradePostScreen.over(x, y, CX + 140, 107, 12, 11)) {
            step(each, 1);
            return true;
        }
        if (TradePostScreen.over(x, y, CX, KEY_Y, 72, 16) && canPlace()) {
            send(TradePostActionPacket.Action.ORDER, ForgeRegistries.ITEMS.getKey(picked.item()) + " " + num(countText), num(eachText));
            return true;
        }
        return false;
    }

    /** The - and + keys beside a box: one at a time, a stack at a time with shift held. */
    private void step(@Nullable EditBox box, int sign) {
        if (box == null) {
            return;
        }
        boolean isCount = box == count;
        long step = Screen.hasShiftDown() ? (isCount && picked != null ? Math.max(1, picked.plain().getMaxStackSize()) : 10) : 1;
        long v = Math.max(1, num(box.getValue()) + sign * step);
        if (isCount && picked != null) {
            v = Math.min(v, maxCount());
        }
        box.setValue(String.valueOf(v));
    }

    boolean scrolled(double x, double y, double delta) {
        if (placing) {
            int rows = (results().size() + perRow() - 1) / perRow();
            resultScroll = Math.max(0, Math.min(Math.max(0, rows - 1), resultScroll - (int) Math.signum(delta)));
            return true;
        }
        if (open != null) {
            return true;
        }
        int rows = (view.orders().size() + 1 + cols - 1) / cols;
        scroll = Math.max(0, Math.min(Math.max(0, rows - ROWS), scroll - (int) Math.signum(delta)));
        return true;
    }

    /** A message came with the view: an order placed or closed goes back to the cards. */
    void message(String key) {
        if (placing && key.endsWith(".order.placed") || open != null && (key.endsWith(".order.cancelled") || key.endsWith(".order.gone"))) {
            back();
        }
    }

    // ------------------------------------------------------------------ drawing

    void draw(GuiGraphics g, int mx, int my) {
        if (serial != seenSerial) {
            seenSerial = serial;
            scroll = Math.min(scroll, Math.max(0, (view.orders().size() + 1 + cols - 1) / cols - ROWS));
            TradePostMenu.OrderView o = current();
            if (o != null) {
                send = Math.max(1, Math.min(send, Math.max(1, Math.min(o.toGo(), o.carried()))));
            }
        }
        if (placing) {
            drawPlacing(g, mx, my);
        } else if (current() != null) {
            drawOrder(g, current(), mx, my);
        } else {
            drawCards(g, mx, my);
        }
    }

    private void drawCards(GuiGraphics g, int mx, int my) {
        int n = view.orders().size() + 1;
        for (int i = scroll * cols; i < Math.min(n, (scroll + ROWS) * cols); i++) {
            int c = i % cols, r = i / cols - scroll, x = CX + c * (cardW + GAP), y = 46 + r * (CARD_H + GAP);
            boolean hover = screen.hover(mx, my, x, y, cardW, CARD_H);
            g.fill(x, y, x + cardW, y + CARD_H, hover ? SEL : SCREEN);
            if (i == 0) {
                outline(g, x, y, cardW, CARD_H, AMBER);
                String plus = "+";
                g.drawString(font, plus, x + (cardW - font.width(plus)) / 2, y + 9, AMBER, false);
                String label = TradePostScreen.tr("new_order");
                CashMachine.fit(g, font, label, x + (cardW - Math.min(font.width(label), cardW - 4)) / 2, y + 23, cardW - 4, AMBER);
                continue;
            }
            TradePostMenu.OrderView o = view.orders().get(i - 1);
            boolean mine = mine(o), can = !mine && o.carried() > 0;
            outline(g, x, y, cardW, CARD_H, hover || can ? PHOS : SCREEN_EDGE);
            // the item at the left, so its right holds whose order it is and how many the viewer could send
            g.renderItem(o.item(), x + 3, y + 3);
            int tx = x + 22, tw = cardW - 25;
            if (mine) {
                screen.tag(g, TradePostScreen.tr("yours"), tx, y + 4, AMBER, tw);
            } else {
                screen.small(g, o.buyerName(), tx, y + 4, DIM, tw);
                if (can) {
                    screen.tag(g, TradePostScreen.tr("you_have", o.carried()), tx, y + 12, AMBER, tw);
                }
            }
            String p = TradePostScreen.tr("each", Bank.format(o.each()));
            CashMachine.fit(g, font, p, x + (cardW - Math.min(font.width(p), cardW - 4)) / 2, y + 21, cardW - 4, mine ? AMBER : PHOS);
            // how many first: a long item name is what gets cut
            screen.small(g, TradePostScreen.tr("to_go", o.toGo(), o.item().getHoverName().getString()), x + 3, y + 31, DIM, cardW - 6);
            bar(g, x + 3, y + CARD_H - 3, cardW - 6, 1, o.filled(), o.wanted());
        }
        int rows = (n + cols - 1) / cols;
        String foot = TradePostScreen.tr("orders_footer", view.total(), view.mine(), view.maxOrders()) + (rows > ROWS ? "  " + TradePostScreen.tr("scroll") : "");
        int footW = cw;
        if (view.pages() > 1) {
            footW = cw - 52;
            screen.key(g, CX + cw - 48, 174, 12, 10, "<", false, view.page() > 0, mx, my);
            String pg = (view.page() + 1) + "/" + view.pages();
            screen.small(g, pg, CX + cw - 24 - (int) (font.width(pg) * 0.75f / 2), 177, DIM, 24);
            screen.key(g, CX + cw - 12, 174, 12, 10, ">", false, view.page() < view.pages() - 1, mx, my);
        }
        screen.small(g, foot, CX, 177, DIM, footW);
    }

    private void drawOrder(GuiGraphics g, TradePostMenu.OrderView o, int mx, int my) {
        boolean mine = mine(o);
        CashMachine.fit(g, font, (mine ? TradePostScreen.tr("your_order") : TradePostScreen.tr("wanted_by", o.buyerName())).toUpperCase(Locale.ROOT), CX, 32, cw, PHOS);
        long now = screen.serverNow();
        screen.small(g, TradePostScreen.tr("order_times", TradePostScreen.ago(now - o.placedMs()), TradePostScreen.left(o.endsMs() - now)), CX, 43, DIM, cw);
        TradePostScreen.slotBox(g, CX, 54);
        g.renderItem(o.item(), CX + 1, 55);
        String name = o.item().getHoverName().getString();
        CashMachine.fit(g, font, TradePostScreen.tr("order_line", o.wanted(), name), CX + 24, 55, cw - 24, PHOS);
        bar(g, CX + 24, 66, cw - 24, 3, o.filled(), o.wanted());
        screen.small(g, TradePostScreen.tr("sent_to_go", o.filled(), o.toGo(), Bank.format(o.each())), CX + 24, 72, DIM, cw - 24);
        if (mine) {
            CashMachine.wrap(g, font, Component.literal(TradePostScreen.tr("order_held", Bank.format(o.toGo() * o.each()))), CX, 92, cw, DIM);
            screen.key(g, CX, KEY_Y, 72, 16, TradePostScreen.tr("cancel_order"), true, true, mx, my);
            screen.key(g, CX + 76, KEY_Y, 44, 16, TradePostScreen.tr("back"), false, true, mx, my);
            return;
        }
        screen.small(g, TradePostScreen.tr("you_carry", o.carried()).toUpperCase(Locale.ROOT), CX + 24, 81, o.carried() > 0 ? AMBER : DIM, cw - 24);
        int most = Math.max(1, Math.min(o.toGo(), o.carried()));
        int n = Math.min(send, most);
        screen.small(g, TradePostScreen.tr("send"), CX, 94, DIM, 28);
        screen.key(g, CX + 30, 91, 12, 11, "-", false, o.carried() > 0 && n > 1, mx, my);
        String amount = String.valueOf(o.carried() > 0 ? n : 0);
        g.drawString(font, amount, CX + 59 - font.width(amount) / 2, 93, PHOS, false);
        screen.key(g, CX + 76, 91, 12, 11, "+", false, o.carried() > 0 && n < most, mx, my);
        screen.key(g, CX + 92, 91, 26, 11, TradePostScreen.tr("all"), false, o.carried() > 0, mx, my);
        long pay = o.carried() > 0 ? n * o.each() : 0, tax = Math.max(0, Math.min(screen.tax(pay), pay - 1)); // as BuyOrders.tax
        int y = 107;
        y = screen.row(g, TradePostScreen.tr("times", o.carried() > 0 ? n : 0, Bank.format(o.each())), Bank.format(pay), CX, y, cw, PHOS);
        y = screen.row(g, TradePostScreen.tr("tax", screen.taxPercent()), "-" + Bank.format(tax), CX, y, cw, PHOS);
        g.fill(CX, y, CX + cw, y + 1, SCREEN_EDGE);
        screen.row(g, TradePostScreen.tr("you_get"), Bank.format(pay - tax), CX, y + 3, cw, AMBER);
        CashMachine.wrap(g, font, Component.literal(TradePostScreen.tr("plain_only")), CX, 146, cw, DIM);
        screen.key(g, CX, KEY_Y, 72, 16, TradePostScreen.tr("fill", o.carried() > 0 ? n : 0), true, o.carried() > 0, mx, my);
        screen.key(g, CX + 76, KEY_Y, 44, 16, TradePostScreen.tr("back"), false, true, mx, my);
    }

    private void drawPlacing(GuiGraphics g, int mx, int my) {
        CashMachine.fit(g, font, TradePostScreen.tr("new_order").toUpperCase(Locale.ROOT), CX, 32, cw, PHOS);
        g.fill(CX, 42, CX + cw, 55, SEL);
        if (findText.isEmpty() && (find == null || !find.isFocused())) {
            g.drawString(font, TradePostScreen.tr("find_item"), CX + 3, 44, DIM, false);
        }
        List<Choice> found = results();
        int per = perRow();
        for (int i = 0; i < per && resultScroll * per + i < found.size(); i++) {
            Choice c = found.get(resultScroll * per + i);
            int sx = CX + i * 19;
            TradePostScreen.slotBox(g, sx, 58);
            if (c == picked) {
                outline(g, sx, 58, 18, 18, PHOS);
            }
            g.renderItem(c.plain(), sx + 1, 59);
        }
        if (!findText.isBlank() && found.isEmpty()) {
            screen.small(g, TradePostScreen.tr("find_none"), CX, 63, DIM, cw);
        } else if (found.size() > per) {
            int rows = (found.size() + per - 1) / per;
            screen.small(g, TradePostScreen.tr("find_more", found.size(), resultScroll + 1, rows), CX, 78, DIM, cw);
        }
        CashMachine.fit(g, font, picked == null ? TradePostScreen.tr("pick_item") : picked.plain().getHoverName().getString().toUpperCase(Locale.ROOT), CX, 84, cw, picked == null ? DIM : PHOS);
        screen.small(g, TradePostScreen.tr("how_many"), CX, 96, DIM, 50);
        screen.key(g, CX + 52, 93, 12, 11, "-", false, picked != null, mx, my);
        field(g, CX + 65, 93, 38, count);
        screen.key(g, CX + 104, 93, 12, 11, "+", false, picked != null, mx, my);
        if (picked != null) {
            screen.small(g, TradePostScreen.tr("up_to", maxCount()), CX + 120, 96, DIM, cw - 120);
        }
        screen.small(g, TradePostScreen.tr("pay_each"), CX, 110, DIM, 50);
        screen.key(g, CX + 52, 107, 12, 11, "-", false, picked != null, mx, my);
        field(g, CX + 65, 107, 74, each);
        screen.key(g, CX + 140, 107, 12, 11, "+", false, picked != null, mx, my);
        long total = total();
        int y = 124;
        y = screen.row(g, TradePostScreen.tr("held_now"), Bank.format(total), CX, y, cw, total > screen.balance() ? 0xFFE0573E : AMBER);
        screen.row(g, TradePostScreen.tr("balance_after"), Bank.format(Math.max(0, screen.balance() - total)), CX, y, cw, PHOS);
        String note = view.mine() >= view.maxOrders() ? TradePostScreen.tr("orders_full", view.maxOrders()) : TradePostScreen.tr("order_note", screen.days());
        CashMachine.wrap(g, font, Component.literal(note), CX, 148, cw, view.mine() >= view.maxOrders() ? AMBER : DIM);
        screen.key(g, CX, KEY_Y, 72, 16, TradePostScreen.tr("place_order"), true, canPlace(), mx, my);
        screen.key(g, CX + 76, KEY_Y, 44, 16, TradePostScreen.tr("back"), false, true, mx, my);
    }

    /** A text box's ground and underline, lit while it has the keyboard. */
    private static void field(GuiGraphics g, int x, int y, int w, @Nullable EditBox box) {
        g.fill(x, y - 1, x + w, y + 11, SEL);
        g.fill(x, y + 10, x + w, y + 11, box != null && box.isFocused() ? PHOS : DIM);
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int colour) {
        g.fill(x, y, x + w, y + 1, colour);
        g.fill(x, y + h - 1, x + w, y + h, colour);
        g.fill(x, y, x + 1, y + h, colour);
        g.fill(x + w - 1, y, x + w, y + h, colour);
    }

    /** How much of an order is sent, as an amber bar over a dark one. */
    private static void bar(GuiGraphics g, int x, int y, int w, int h, int done, int of) {
        g.fill(x, y, x + w, y + h, SCREEN_EDGE);
        int fill = of <= 0 ? 0 : (int) ((long) w * Math.min(done, of) / of);
        if (fill > 0) {
            g.fill(x, y, x + fill, y + h, AMBER);
        }
    }

    // ------------------------------------------------------------------ the item under the mouse

    ItemStack hovered(double x, double y) {
        if (placing) {
            List<Choice> found = results();
            int per = perRow();
            for (int i = 0; i < per && resultScroll * per + i < found.size(); i++) {
                if (TradePostScreen.over(x, y, CX + i * 19, 58, 18, 18)) {
                    return found.get(resultScroll * per + i).plain();
                }
            }
            return ItemStack.EMPTY;
        }
        TradePostMenu.OrderView o = current();
        if (o != null) {
            return TradePostScreen.over(x, y, CX, 54, 18, 18) ? o.item() : ItemStack.EMPTY;
        }
        int n = view.orders().size() + 1;
        for (int i = Math.max(1, scroll * cols); i < Math.min(n, (scroll + ROWS) * cols); i++) {
            int c = i % cols, r = i / cols - scroll, cx = CX + c * (cardW + GAP) + 3, cy = 46 + r * (CARD_H + GAP) + 3;
            if (TradePostScreen.over(x, y, cx, cy, 16, 16)) {
                return view.orders().get(i - 1).item();
            }
        }
        return ItemStack.EMPTY;
    }
}
