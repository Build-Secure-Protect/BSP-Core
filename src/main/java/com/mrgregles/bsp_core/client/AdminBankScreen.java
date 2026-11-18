package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.api.BspBank;
import com.mrgregles.bsp_core.api.BspMarket;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.network.AdminBankAskPacket;
import com.mrgregles.bsp_core.network.AdminBankDataPacket;
import com.mrgregles.bsp_core.network.BSPNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The admin panel's Bank &amp; Market page (layout C, "a wide page of its own"): one player's pieces and the coins kept in their bank
 * with a week's summary, their bank log with EDIT TRANSACTIONS under it, and their market lots and requests, each with CANCEL (a
 * second click within four seconds confirms). Opened from the Players tab's BANK &amp; MARKET button; ◀ PLAYERS goes back. Moderators
 * see it all; CANCEL (and refunds on the edit page) only work for those the server allows, and are greyed for the rest.
 */
public class AdminBankScreen extends Screen implements ScreenFit.Fitted {
    static final int W = 440, H = 250, BG = 0xF010151C, SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, VIO = 0xFFB58CFF, TQ = 0xFF19D3B0, BAD = 0xFFFF6B5C,
            GOLD = 0xFFFFD23A, GOOD = 0xFF8FE04A, MUTED = 0x9AA3B5, TEXT = 0xE6EAF2, HOVER = 0xFF2A2140;
    private static final int LX = 10, LW = 124, MX = 142, MW = 172, RX = 322, RW = 108, TOP = 28, ROW = 10, LOG_ROWS = 17, ENTRY = 18, LOTS_SHOWN = 5, ORDERS_SHOWN = 5;
    private static final long CONFIRM_MS = 4000, RESULT_MS = 6000, WEEK_MS = 7L * 24 * 60 * 60 * 1000;

    private final Screen parent;
    private final UUID player;
    private final String name;
    @Nullable
    private AdminBankDataPacket page;
    private int logScroll, lotScroll, orderScroll;
    @Nullable
    private String armed;
    private long armedUntil, resultUntil;

    private AdminBankScreen(Screen parent, UUID player, String name) {
        super(Component.translatable("gui.bsp_core.admin.bank.title"));
        this.parent = parent;
        this.player = player;
        this.name = name;
    }

    /** Opens the page for {@code player} and asks the server for it. */
    public static void open(Screen parent, UUID player, String name) {
        AdminBankClient.clear(); // an old copy of the page (and its old answer) is never shown as this one
        Minecraft.getInstance().setScreen(new AdminBankScreen(parent, player, name));
        BSPNetwork.CHANNEL.sendToServer(AdminBankAskPacket.open(player));
    }

    @Override
    protected void init() {
        AdminBankClient.listen(this::take);
        AdminBankDataPacket latest = AdminBankClient.latest();
        if (page == null && latest != null && latest.player().equals(player)) {
            take();
        }
    }

    @Override
    public void removed() {
        AdminBankClient.listen(null);
        super.removed();
    }

    /** A page arrived for this player: show it, and its answer for a few seconds. */
    void take() {
        AdminBankDataPacket p = AdminBankClient.latest();
        if (p == null || !p.player().equals(player)) {
            return;
        }
        page = p;
        resultUntil = p.result().isEmpty() ? 0 : System.currentTimeMillis() + RESULT_MS;
        clampScrolls();
    }

    /** Asks the server again (after an action, or the refresh sign). */
    void send(AdminBankAskPacket ask) {
        BSPNetwork.CHANNEL.sendToServer(ask);
    }

    UUID player() {
        return player;
    }

    @Nullable
    AdminBankDataPacket page() {
        return page;
    }

    private void clampScrolls() {
        if (page == null) {
            return;
        }
        logScroll = Mth.clamp(logScroll, 0, Math.max(0, page.rows().size() - LOG_ROWS));
        lotScroll = Mth.clamp(lotScroll, 0, Math.max(0, page.lots().size() - LOTS_SHOWN));
        orderScroll = Mth.clamp(orderScroll, 0, Math.max(0, page.orders().size() - ORDERS_SHOWN));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public int panelWidth() {
        return W;
    }

    @Override
    public int panelHeight() {
        return H;
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    static net.minecraft.network.chat.MutableComponent tr(String key, Object... args) {
        return Component.translatable("gui.bsp_core.admin.bank." + key, args);
    }

    static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean confirmed(String id) {
        long now = System.currentTimeMillis();
        if (id.equals(armed) && now < armedUntil) {
            armed = null;
            return true;
        }
        armed = id;
        armedUntil = now + CONFIRM_MS;
        return false;
    }

    private boolean arming(String id) {
        return id.equals(armed) && System.currentTimeMillis() < armedUntil;
    }

    private boolean canEdit() {
        return page != null && page.canEdit() && page.reached();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = left(), y = top();
        if (over(mx, my, x + 10, y + 6, 58, 13)) {
            onClose();
            return true;
        }
        if (over(mx, my, x + W - 24, y + 6, 14, 13)) {
            send(AdminBankAskPacket.open(player));
            return true;
        }
        if (page == null) {
            return super.mouseClicked(mx, my, button);
        }
        if (page.reached() && over(mx, my, x + MX, y + TOP + 194, MW, 16)) {
            Minecraft.getInstance().setScreen(new AdminBankEditScreen(this));
            return true;
        }
        if (canEdit()) {
            for (int i = 0; i < LOTS_SHOWN && i + lotScroll < page.lots().size(); i++) {
                BspMarket.Lot l = page.lots().get(i + lotScroll);
                if (over(mx, my, x + RX + RW - 30, lotY(y, i) + 9, 30, 9)) {
                    if (confirmed("lot:" + l.id())) {
                        send(new AdminBankAskPacket(AdminBankAskPacket.Action.CANCEL_LOT, player, 0, 0, 0, false, l.id()));
                    }
                    return true;
                }
            }
            for (int i = 0; i < ORDERS_SHOWN && i + orderScroll < page.orders().size(); i++) {
                BspMarket.Order o = page.orders().get(i + orderScroll);
                if (over(mx, my, x + RX + RW - 30, orderY(y, i) + 9, 30, 9)) {
                    if (confirmed("order:" + o.id())) {
                        send(new AdminBankAskPacket(AdminBankAskPacket.Action.CANCEL_ORDER, player, 0, 0, 0, false, o.id()));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int x = left(), y = top(), step = -(int) Math.signum(delta);
        if (over(mx, my, x + RX, y + TOP + 10, RW, LOTS_SHOWN * ENTRY)) {
            lotScroll += step;
        } else if (over(mx, my, x + RX, orderY(y, 0), RW, ORDERS_SHOWN * ENTRY)) {
            orderScroll += step;
        } else {
            logScroll += step;
        }
        clampScrolls();
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
        BSPNetwork.CHANNEL.sendToServer(new com.mrgregles.bsp_core.network.AdminActionPacket(com.mrgregles.bsp_core.network.AdminActionPacket.REFRESH)); // coins may have changed
    }

    private static int lotY(int y, int i) {
        return y + TOP + 10 + i * ENTRY;
    }

    private static int orderY(int y, int i) {
        return y + TOP + 10 + LOTS_SHOWN * ENTRY + 16 + i * ENTRY;
    }

    // ------------------------------------------------------------------ drawing

    void small(GuiGraphics g, Component text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    /** Small text cut with "..." to {@code w} pixels on screen. */
    void smallFit(GuiGraphics g, String text, int x, int y, int w, int colour) {
        small(g, Component.literal(CashMachine.cut(font, text, Math.round(w / 0.75f))), x, y, colour);
    }

    /** Small text ending at {@code right}. */
    void smallRight(GuiGraphics g, String text, int right, int y, int colour) {
        small(g, Component.literal(text), right - Math.round(font.width(text) * 0.75f), y, colour);
    }

    void button(GuiGraphics g, int x, int y, int w, int h, Component label, boolean enabled, int colour, int mouseX, int mouseY) {
        boolean hover = enabled && over(mouseX, mouseY, x, y, w, h);
        g.fill(x, y, x + w, y + h, enabled ? colour : DIM);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? HOVER : SLOT_BG);
        if (h < 14) {
            small(g, label, x + w / 2 - Math.round(font.width(label) * 0.375f), y + (h - 6) / 2, enabled ? colour & 0xFFFFFF : 0x6B7385);
        } else {
            g.drawString(font, label, x + w / 2 - font.width(label) / 2, y + (h - 8) / 2, enabled ? colour & 0xFFFFFF : 0x6B7385, false);
        }
    }

    void box(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, DIM);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, SLOT_BG);
    }

    static int typeColour(String type) {
        int c = switch (type) {
            case "BOUGHT", "SOLD", "MARKET" -> TQ;
            case "PAID", "RECEIVED", "LOTTERY" -> VIO;
            case "CONVERT", "DEPOSIT", "WITHDRAW" -> GOLD;
            case "GRANT", "REFUND" -> GOOD;
            case "ADMIN" -> BAD;
            default -> 0xFF000000 | MUTED;
        };
        return c & 0xFFFFFF;
    }

    static String day(long ms) {
        return new SimpleDateFormat("d MMM", Locale.ROOT).format(new Date(ms));
    }

    static String when(long ms) {
        return new SimpleDateFormat("d MMM HH:mm", Locale.ROOT).format(new Date(ms));
    }

    static String signed(long n) {
        return (n > 0 ? "+" : n < 0 ? "-" : "") + String.format(Locale.ROOT, "%,d", Math.abs(n));
    }

    static String tierName(int tier) {
        return tier >= 0 && tier < CoinTier.values().length ? com.mrgregles.bsp_core.bank.Bank.tierName(CoinTier.values()[tier]) : "?";
    }

    /** The row's amount, short: pieces when it moved pieces, else its coins. */
    static String amount(BspBank.Row r) {
        if (r.pieces() != 0) {
            return signed(r.pieces());
        }
        return r.coinCount() != 0 ? signed(r.coinCount()) + " " + tierName(r.coinTier()).charAt(0) : "";
    }

    /** The lines of a row's tooltip, shared with the edit page. */
    static List<Component> rowTip(BspBank.Row r) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(r.type()).withStyle(s -> s.withColor(typeColour(r.type()))));
        tip.add(tr("tip.when", when(r.ms()), r.serverId()));
        if (!r.with().isEmpty()) {
            tip.add(tr("tip.with", r.with()));
        }
        if (!r.detail().isEmpty()) {
            tip.add(Component.literal(r.detail()));
        }
        if (r.pieces() != 0) {
            tip.add(tr("tip.pieces", signed(r.pieces())));
        }
        if (r.coinCount() != 0) {
            tip.add(tr("tip.coins", signed(r.coinCount()), tierName(r.coinTier())));
        }
        if (r.refundedPieces() > 0 || r.refundedCoins() > 0) {
            tip.add(tr("tip.refunded", String.format(Locale.ROOT, "%,d", r.refundedPieces()), String.format(Locale.ROOT, "%,d", Math.abs(r.pieces())), r.refundedCoins(),
                    Math.abs(r.coinCount())).withStyle(s -> s.withColor(GOOD & 0xFFFFFF)));
        }
        return tip;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = left(), y = top();
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, VIO);
        g.fill(x, y, x + W, y + H, BG);
        button(g, x + 10, y + 6, 58, 13, tr("back"), true, DIM | 0xFF000000, mouseX, mouseY);
        g.drawString(font, CashMachine.cut(font, name, 120), x + 74, y + 9, VIO & 0xFFFFFF, false);
        int after = x + 74 + Math.min(120, font.width(name)) + 6;
        if (page != null) {
            small(g, tr(page.online() ? "online" : "offline"), after, y + 11, page.online() ? TQ & 0xFFFFFF : MUTED);
        }
        Component heading = page != null && !page.canEdit() ? tr("heading_view") : tr("heading");
        smallRight(g, heading.getString(), x + W - 30, y + 11, (page != null && !page.canEdit() ? GOLD : MUTED) & 0xFFFFFF);
        button(g, x + W - 24, y + 6, 14, 13, Component.literal("↻"), true, TQ, mouseX, mouseY);
        if (page == null) {
            small(g, tr("loading"), x + LX, y + TOP + 4, MUTED);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        if (!page.reached()) {
            small(g, tr("unreachable_page"), x + LX, y + TOP + 4, BAD & 0xFFFFFF);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        drawBalances(g, x + LX, y + TOP, mouseX, mouseY);
        List<Component> tip = drawLog(g, x + MX, y + TOP, mouseX, mouseY);
        List<Component> marketTip = drawMarket(g, x + RX, y, mouseX, mouseY);
        if (System.currentTimeMillis() < resultUntil && !page.result().isEmpty()) {
            int colour = page.result().equals("ok") ? GOOD : BAD;
            smallFit(g, tr("result." + page.result()).getString(), x + LX, y + H - 9, W - 2 * LX, colour & 0xFFFFFF); // under all three columns
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (tip != null) {
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
        } else if (marketTip != null) {
            g.renderComponentTooltip(font, marketTip, mouseX, mouseY);
        }
    }

    private void drawBalances(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        box(g, x, y, LW, 34);
        small(g, tr("pieces"), x + 4, y + 4, MUTED);
        String pieces = String.format(Locale.ROOT, "%,d", page.pieces());
        float k = Math.min(1.5f, (LW - 8) / (float) Math.max(1, font.width(pieces)));
        g.pose().pushPose();
        g.pose().translate(x + 4, y + 14, 0);
        g.pose().scale(k, k, 1f);
        g.drawString(font, pieces, 0, 0, GOLD & 0xFFFFFF, false);
        g.pose().popPose();

        int cy = y + 40;
        box(g, x, cy, LW, 104);
        small(g, tr("coins"), x + 4, cy + 4, MUTED);
        for (CoinTier t : CoinTier.values()) {
            int ry = cy + 14 + t.ordinal() * 18;
            g.renderItem(new ItemStack(t.coin()), x + 4, ry);
            int n = t.ordinal() < page.coins().length ? page.coins()[t.ordinal()] : 0;
            g.drawString(font, CashMachine.cut(font, String.format(Locale.ROOT, "%,d", n), 40), x + 24, ry + 4, n > 0 ? TEXT : MUTED, false);
            smallFit(g, tierName(t.ordinal()), x + 68, ry + 5, LW - 72, MUTED);
        }

        int wy = cy + 110;
        box(g, x, wy, LW, 44);
        small(g, tr("week"), x + 4, wy + 4, MUTED);
        long now = System.currentTimeMillis(), in = 0, out = 0;
        int converted = 0, refunds = 0;
        for (BspBank.Row r : page.rows()) {
            if (now - r.ms() > WEEK_MS) {
                break;
            }
            if (r.pieces() > 0) {
                in += r.pieces();
            } else {
                out += r.pieces();
            }
            if (r.type().equals("CONVERT")) {
                converted++;
            } else if (r.type().equals("REFUND")) {
                refunds++;
            }
        }
        smallFit(g, tr("week_in", signed(in)).getString(), x + 4, wy + 15, LW - 8, GOOD & 0xFFFFFF);
        smallFit(g, tr("week_out", signed(out)).getString(), x + 4, wy + 24, LW - 8, BAD & 0xFFFFFF);
        smallFit(g, tr("week_counts", converted, refunds).getString(), x + 4, wy + 33, LW - 8, MUTED);
    }

    /** The log column; returns the hovered row's tooltip, or null. */
    @Nullable
    private List<Component> drawLog(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        small(g, tr("log", page.rows().size()), x, y, MUTED);
        int ly = y + 10;
        box(g, x, ly, MW, LOG_ROWS * ROW + 4);
        List<Component> tip = null;
        if (page.rows().isEmpty()) {
            small(g, tr("log_empty"), x + 4, ly + 5, MUTED);
        }
        for (int i = 0; i < LOG_ROWS && i + logScroll < page.rows().size(); i++) {
            BspBank.Row r = page.rows().get(i + logScroll);
            int ry = ly + 2 + i * ROW;
            boolean hover = over(mouseX, mouseY, x + 1, ry, MW - 2, ROW);
            if (hover) {
                g.fill(x + 1, ry, x + MW - 1, ry + ROW, HOVER);
                tip = rowTip(r);
            }
            boolean done = r.piecesLeft() == 0 && r.coinsLeft() == 0 && (r.pieces() != 0 || r.coinCount() != 0);
            int fade = done ? 0x6B7385 : -1;
            small(g, Component.literal(day(r.ms())), x + 3, ry + 2, fade >= 0 ? fade : MUTED);
            small(g, Component.literal(r.type()), x + 30, ry + 2, fade >= 0 ? fade : typeColour(r.type()));
            String what = r.detail().isEmpty() ? r.with() : r.detail();
            String amt = amount(r);
            int amtW = Math.round(font.width(amt) * 0.75f);
            smallFit(g, what, x + 72, ry + 2, Math.min(MW - 72 - 46, MW - 4 - amtW - 4 - 72), fade >= 0 ? fade : TEXT); // a huge amount pushes the text back
            int colour = r.pieces() > 0 || r.pieces() == 0 && r.coinCount() > 0 ? GOOD : BAD;
            smallRight(g, amt, x + MW - 4, ry + 2, fade >= 0 ? fade : colour & 0xFFFFFF);
        }
        if (page.rows().size() > LOG_ROWS) {
            int track = LOG_ROWS * ROW, knob = Math.max(8, track * LOG_ROWS / page.rows().size()), ky = (track - knob) * logScroll / (page.rows().size() - LOG_ROWS);
            g.fill(x + MW - 3, ly + 2 + ky, x + MW - 1, ly + 2 + ky + knob, DIM);
        }
        button(g, x, y + 194, MW, 16, tr("edit"), true, GOLD, mouseX, mouseY); // moderators may read it too; refunds are greyed for them
        return tip;
    }

    /** The market column; returns the hovered entry's tooltip, or null. */
    @Nullable
    private List<Component> drawMarket(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        List<Component> tip = null;
        long now = System.currentTimeMillis();
        small(g, tr("for_sale", page.lots().size()), x, y + TOP, MUTED);
        box(g, x, y + TOP + 9, RW, LOTS_SHOWN * ENTRY + 2);
        if (page.lots().isEmpty()) {
            small(g, tr("none_lots"), x + 4, y + TOP + 14, MUTED);
        }
        for (int i = 0; i < LOTS_SHOWN && i + lotScroll < page.lots().size(); i++) {
            BspMarket.Lot l = page.lots().get(i + lotScroll);
            int ry = lotY(y, i);
            if (!l.items().isEmpty()) {
                g.renderItem(l.items().get(0), x + 2, ry + 1);
            }
            String what = l.items().isEmpty() ? "?" : l.items().get(0).getHoverName().getString() + (l.items().size() > 1 ? " +" + (l.items().size() - 1) : "");
            smallFit(g, what, x + 20, ry + 2, RW - 22, TEXT);
            smallFit(g, String.format(Locale.ROOT, "%,d", l.price()), x + 20, ry + 10, RW - 54, GOLD & 0xFFFFFF);
            String id = "lot:" + l.id();
            button(g, x + RW - 30, ry + 9, 30, 9, tr(arming(id) ? "sure" : "cancel"), canEdit(), BAD, mouseX, mouseY);
            if (over(mouseX, mouseY, x + 1, ry, RW - 32, ENTRY)) {
                tip = new ArrayList<>();
                for (ItemStack st : l.items()) {
                    tip.add(Component.literal(st.getCount() + " x ").append(st.getHoverName()));
                }
                tip.add(tr("tip.price", String.format(Locale.ROOT, "%,d", l.price())));
                tip.add(tr("tip.ends", left(l.endsMs() - now), l.serverId()));
            }
        }
        int oy = orderY(y, 0);
        small(g, tr("requests", page.orders().size()), x, oy - 7, MUTED);
        box(g, x, oy - 1, RW, ORDERS_SHOWN * ENTRY + 2);
        if (page.orders().isEmpty()) {
            small(g, tr("none_orders"), x + 4, oy + 4, MUTED);
        }
        for (int i = 0; i < ORDERS_SHOWN && i + orderScroll < page.orders().size(); i++) {
            BspMarket.Order o = page.orders().get(i + orderScroll);
            int ry = orderY(y, i);
            g.renderItem(o.item(), x + 2, ry + 1);
            smallFit(g, o.item().getHoverName().getString() + " x" + o.wanted(), x + 20, ry + 2, RW - 22, TEXT);
            smallFit(g, o.filled() + "/" + o.wanted() + "  " + String.format(Locale.ROOT, "%,d", o.each()) + tr("each").getString(), x + 20, ry + 10, RW - 54, TQ & 0xFFFFFF);
            String id = "order:" + o.id();
            button(g, x + RW - 30, ry + 9, 30, 9, tr(arming(id) ? "sure" : "cancel"), canEdit(), BAD, mouseX, mouseY);
            if (over(mouseX, mouseY, x + 1, ry, RW - 32, ENTRY)) {
                tip = new ArrayList<>();
                tip.add(o.item().getHoverName().copy());
                tip.add(tr("tip.order", o.filled(), o.wanted(), String.format(Locale.ROOT, "%,d", o.each()), String.format(Locale.ROOT, "%,d", (long) o.toGo() * o.each())));
                tip.add(tr("tip.ends", left(o.endsMs() - now), o.serverId()));
            }
        }
        return tip;
    }

    /** "2d 4h", "5h 10m", "3m": how long until a lot or request runs out. */
    static String left(long ms) {
        long m = Math.max(0, ms) / 60_000, h = m / 60, d = h / 24;
        return d > 0 ? d + "d " + h % 24 + "h" : h > 0 ? h + "h " + m % 60 + "m" : m + "m";
    }
}
