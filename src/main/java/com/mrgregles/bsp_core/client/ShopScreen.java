package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.ShopActionPacket;
import com.mrgregles.bsp_core.network.ShopDataPacket;
import com.mrgregles.bsp_core.shop.Shop;
import com.mrgregles.bsp_core.shop.Refill;
import com.mrgregles.bsp_core.shop.ShopMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;

import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The player's shop screen in the Cash Machine skin: the page of cards on the phosphor screen
 * (icon, "buy N", "sell N", stock badge; folder cards for sub-sections), a quantity view for a buy
 * or a sell, the closed view (name, CLOSED, when it opens, the countdown), keys in the right-hand
 * column (Back and pages at the top; Sell here, Sell all and Admin at the bottom) and the status strip.
 */
public class ShopScreen extends AbstractContainerScreen<ShopMenu> implements ScreenFit.Fitted {
    private static final int W = 300, H = 216;
    private static final int BEZEL = 0xFFCFC8B8, BEZEL_DARK = 0xFF8F877A, BEZEL_LIGHT = 0xFFECE6D8, SCREEN = 0xFF061008, SCREEN_EDGE = 0xFF0D3B22, PHOS = 0x58F29A,
            PHOS_DIM = 0x3FB97A, AMBER = 0xF2B544, RED = 0xFF6B5C, KEY = 0xFFEAE4D5, KEY_EDGE = 0xFF9A9183, KEY_TEXT = 0x2B2823, KEY_LOCKED = 0x8F877A, CARD = 0xFF0A1A10, CARD_SEL = 0xFF0F2D1C, CARD_OUT = 0xFF070D09;
    private static final int SX = 10, SY = 10, SW = 206, SH = 160, KX = 226, KW = 64, KH = 20, KGAP = 4, STRIP_Y = 180, STRIP_H = 22, CX = SX + 6, CY = SY + 22, CGAP = 1;

    private enum View { PAGE, QTY }

    private record Key(String label, boolean enabled, Runnable action) {}

    private final List<Key> top = new ArrayList<>(), bottom = new ArrayList<>();
    private View view = View.PAGE;
    private int lastSerial = -1, sel = -1, lots = 1;
    private boolean selling;
    private Component message = Component.empty();
    private int messageColour = AMBER;

    public ShopScreen(ShopMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    /** Client: a page packet for the open shop screen. */
    public static void receive(ShopDataPacket m) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof ShopMenu menu) {
            menu.name = m.name();
            menu.path = m.path();
            menu.kind = m.kind();
            menu.buyOnly = m.buyOnly();
            menu.open = m.open();
            menu.admin = m.admin();
            menu.offerPercent = m.offerPercent();
            menu.offerEnd = m.offerEnd();
            menu.rotatesIn = m.rotatesIn();
            menu.page = m.page();
            menu.pages = m.pages();
            menu.columns = Math.max(1, m.columns());
            menu.rows = Math.max(1, m.rows());
            menu.balance = m.balance();
            menu.opensAt = m.opensAt();
            menu.untilOpen = m.untilOpen();
            menu.cards.clear();
            menu.cards.addAll(m.cards());
            if (!m.messageKey().isEmpty()) {
                menu.messageKey = m.messageKey();
                menu.messageArgs = m.messageArgs();
                menu.messageSerial++;
            }
        }
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("gui.bsp_core.shop." + key, args);
    }

    private void send(ShopActionPacket.Action a, int index, int lots, int page) {
        BSPNetwork.CHANNEL.sendToServer(new ShopActionPacket(a, index, lots, page));
    }

    private ShopMenu.Card card(int index) {
        for (ShopMenu.Card c : menu.cards) {
            if (c.index() == index) {
                return c;
            }
        }
        return null;
    }

    private static String unit(int currency) {
        return currency < 0 ? "" : " " + Bank.tierName(CoinTier.values()[currency]).charAt(0);
    }

    private void keys() {
        top.clear();
        bottom.clear();
        if (view == View.QTY) {
            ShopMenu.Card c = card(sel);
            top.add(new Key("minus", lots > 1, () -> lots = Math.max(1, lots - 1)));
            top.add(new Key("plus", true, () -> lots++));
            top.add(new Key("plus16", true, () -> lots += 16));
            top.add(new Key("max", c != null, () -> lots = maxLots(c)));
            bottom.add(new Key("confirm", c != null, () -> { send(selling ? ShopActionPacket.Action.SELL : ShopActionPacket.Action.BUY, sel, lots, menu.page); view = View.PAGE; }));
            bottom.add(new Key("cancel", true, () -> view = View.PAGE));
            return;
        }
        top.add(new Key("back", true, () -> send(ShopActionPacket.Action.BACK, 0, 0, 0)));
        if (menu.open) {
            top.add(new Key("prev_page", menu.page > 0, () -> send(ShopActionPacket.Action.PAGE, 0, 0, menu.page - 1)));
            top.add(new Key("next_page", menu.page < menu.pages - 1, () -> send(ShopActionPacket.Action.PAGE, 0, 0, menu.page + 1)));
            if (!menu.buyOnly) {
                bottom.add(new Key("sell_here", true, () -> send(ShopActionPacket.Action.SELL_HERE, 0, 0, 0)));
                bottom.add(new Key("sell_all", true, () -> send(ShopActionPacket.Action.SELL_ALL, 0, 0, menu.page)));
            }
        }
        if (menu.admin) {
            bottom.add(new Key("admin", true, () -> send(ShopActionPacket.Action.ADMIN, 0, 0, 0)));
            if (menu.at != null) {
                bottom.add(new Key("vendor", true, () -> send(ShopActionPacket.Action.VENDOR, 0, 0, 0)));
            }
        }
    }

    private int maxLots(ShopMenu.Card c) {
        if (selling) {
            int held = 0;
            for (var s : minecraft.player.getInventory().items) {
                if (net.minecraft.world.item.ItemStack.isSameItemSameTags(s, c.stack())) {
                    held += s.getCount();
                }
            }
            int n = held / c.stack().getCount();
            if (c.selling().limited()) {
                n = Math.min(n, c.selling().left());
            }
            return Math.max(1, n);
        }
        long afford = c.buy() <= 0 ? 999 : c.currency() < 0 ? menu.balance / c.buy() : 999;
        int fits = com.mrgregles.bsp_core.shop.Shops.fits(minecraft.player, c.stack(), 999);
        long n = Math.min(afford, fits);
        if (c.stock().limited()) {
            n = Math.min(n, c.stock().left());
        }
        return (int) Math.max(1, n);
    }

    private void pollMessage() {
        if (menu.messageSerial == lastSerial) {
            return;
        }
        lastSerial = menu.messageSerial;
        String key = menu.messageKey;
        boolean good = key.equals("bought") || key.equals("sold") || key.equals("sold_all") || key.equals("sold_capped") || key.equals("sold_all_capped");
        messageColour = good ? AMBER : RED;
        message = key.isEmpty() ? Component.empty() : Component.translatable("message.bsp_core.shop." + key, (Object[]) menu.messageArgs);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        keys();
        int x = leftPos, y = topPos, ky = y + SY;
        for (Key k : top) {
            if (over(mx, my, x + KX, ky, KW, KH)) {
                if (k.enabled) {
                    click();
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
                    click();
                    k.action.run();
                }
                return true;
            }
            ky -= KH + KGAP;
        }
        if (view == View.PAGE && menu.open) {
            for (int k = 0; k < menu.cards.size(); k++) {
                int cx = x + CX + (k % menu.columns) * (cw() + CGAP), cy = y + CY + (k / menu.columns) * (ch() + CGAP);
                if (over(mx, my, cx, cy, cw(), ch())) {
                    ShopMenu.Card c = menu.cards.get(k);
                    click();
                    if (!c.section().isEmpty()) {
                        send(ShopActionPacket.Action.SECTION, c.index(), 0, 0);
                    } else if (button == 1 && hasShiftDown() && c.sell() >= 0) {
                        send(ShopActionPacket.Action.SELL, c.index(), Integer.MAX_VALUE, menu.page);
                    } else if (button == 1 && c.sell() >= 0) {
                        sel = c.index();
                        selling = true;
                        lots = 1;
                        view = View.QTY;
                    } else if (button == 0 && c.buy() >= 0 && c.stock().left() != 0) {
                        if (c.instant()) {
                            send(ShopActionPacket.Action.BUY, c.index(), 1, menu.page);
                        } else {
                            sel = c.index();
                            selling = false;
                            lots = 1;
                            view = View.QTY;
                        }
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void click() {
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BIT.get(), 1.7f, 0.4f));
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        pollMessage();
        keys();
        renderBackground(g);
        super.render(g, mx, my, partial);
        if (view == View.PAGE && menu.open) {
            for (int k = 0; k < menu.cards.size(); k++) {
                int cx = leftPos + CX + (k % menu.columns) * (cw() + CGAP), cy = topPos + CY + (k / menu.columns) * (ch() + CGAP);
                if (over(mx, my, cx, cy, cw(), ch())) {
                    ShopMenu.Card c = menu.cards.get(k);
                    List<Component> tip = new ArrayList<>(getTooltipFromItem(minecraft, c.stack()));
                    if (!c.section().isEmpty()) {
                        tip.add(tr("tip_section"));
                    } else {
                        if (c.buy() >= 0) {
                            tip.add(tr(c.instant() ? "tip_buy_instant" : "tip_buy", Bank.format(c.buy()), unitName(c.currency())));
                        }
                        if (c.sell() >= 0) {
                            tip.add(tr("tip_sell", Bank.format(c.sell()), unitName(c.currency())));
                        }
                        limitLines(tip, c.stock(), false);
                        limitLines(tip, c.selling(), true);
                    }
                    g.renderComponentTooltip(font, tip, mx, my);
                }
            }
        }
    }

    /** A limit's tooltip lines: what this player has left, what is left for everyone, when it refills. */
    private static void limitLines(List<Component> tip, ShopMenu.Limit l, boolean selling) {
        if (!l.limited()) {
            return;
        }
        tip.add(l.left() == 0 ? tr(selling ? "tip_sell_none" : "tip_buy_none").withStyle(ChatFormatting.RED)
                : tr(selling ? "tip_sell_left" : "tip_buy_left", l.left(), l.of()).withStyle(selling ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        if (l.everyone() >= 0) {
            tip.add(tr(selling ? "tip_sell_everyone" : "tip_buy_everyone", l.everyone()).withStyle(ChatFormatting.GRAY));
        }
        Refill r = Refill.parse(l.rule());
        String in = l.refillIn() >= 0 ? span(l.refillIn()) : "";
        if (r == null) {
            return;
        }
        switch (r.kind()) {
            case AFTER -> tip.add((l.refillIn() >= 0 ? tr("tip_refill_in", in) : tr("tip_refill_after", r.text())).withStyle(ChatFormatting.GRAY));
            case DAILY -> tip.add(tr("tip_refill_day", r.clock(), in).withStyle(ChatFormatting.GRAY));
            case WEEKLY -> tip.add(tr("tip_refill_week", r.day().getDisplayName(TextStyle.FULL, Locale.ENGLISH), r.clock(), in).withStyle(ChatFormatting.GRAY));
            case ROTATION -> {
                if (l.refillIn() >= 0) {
                    tip.add(tr("tip_refill_rotation", in).withStyle(ChatFormatting.GRAY));
                }
            }
            default -> { }
        }
    }

    /** A wait as "2d 4h", "9h 12m" or "42m". */
    private static String span(long ms) {
        long m = Math.max(1, (ms + 59_999) / 60_000), h = m / 60, d = h / 24;
        return d > 0 ? d + "d " + h % 24 + "h" : h > 0 ? h + "h " + m % 60 + "m" : m + "m";
    }

    private static String unitName(int currency) {
        return currency < 0 ? "pieces" : Bank.tierName(CoinTier.values()[currency]) + " coins";
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, BEZEL_DARK);
        g.fill(x, y, x + W, y + H, BEZEL);
        g.fill(x, y, x + W, y + 2, BEZEL_LIGHT);
        g.fill(x + SX - 2, y + SY - 2, x + SX + SW + 2, y + SY + SH + 2, SCREEN_EDGE);
        g.fill(x + SX, y + SY, x + SX + SW, y + SY + SH, SCREEN);
        for (int ly = y + SY; ly < y + SY + SH; ly += 3) {
            g.fill(x + SX, ly, x + SX + SW, ly + 1, 0x14000000);
        }
        if (view == View.PAGE && menu.open) {
            for (int k = 0; k < menu.cards.size(); k++) {
                ShopMenu.Card c = menu.cards.get(k);
                int cx = x + CX + (k % menu.columns) * (cw() + CGAP), cy = y + CY + (k / menu.columns) * (ch() + CGAP);
                // dark when nothing is left to do with it: no stock to buy and no room to sell
                boolean out = c.section().isEmpty() && (c.buy() >= 0 || c.sell() >= 0) && !(c.buy() >= 0 && c.stock().left() != 0) && !(c.sell() >= 0 && c.selling().left() != 0);
                g.fill(cx, cy, cx + cw(), cy + ch(), out ? CARD_OUT : over(mx, my, cx, cy, cw(), ch()) ? CARD_SEL : CARD);
                g.fill(cx, cy, cx + cw(), cy + 1, out ? 0xFF1F6B43 : PHOS_DIM | 0xFF000000);
                g.fill(cx, cy + ch() - 1, cx + cw(), cy + ch(), out ? 0xFF1F6B43 : PHOS_DIM | 0xFF000000);
                g.renderItem(c.stack(), cx + cw() / 2 - 8, cy + iconY());
                g.renderItemDecorations(font, c.stack(), cx + cw() / 2 - 8, cy + iconY());
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
        g.fill(x + SX - 2, y + STRIP_Y - 2, x + W - SX + 2, y + STRIP_Y + STRIP_H + 2, SCREEN_EDGE);
        g.fill(x + SX, y + STRIP_Y, x + W - SX, y + STRIP_Y + STRIP_H, SCREEN);
        String left = (menu.open ? tr("open_now").getString() : tr("closed_now").getString()) + " / " + (menu.remote ? tr("network").getString() : Bank.serverId());
        String bal = tr("balance", Bank.format(menu.balance)).getString();
        CashMachine.fit(g, font, left, x + SX + 6, y + STRIP_Y + 7, W - 2 * SX - 16 - font.width(bal), PHOS_DIM);
        g.drawString(font, bal, x + W - SX - 6 - font.width(bal), y + STRIP_Y + 7, PHOS_DIM, false);
    }

    private void key(GuiGraphics g, int x, int y, Key k, int mx, int my) {
        CashMachine.key(g, font, x, y, KW, KH, tr(k.label).getString(), k.enabled, k.enabled && over(mx, my, x, y, KW, KH));
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        String right = !menu.open ? tr("closed").getString() : menu.kind == Shop.Kind.ROTATING ? tr("rotates_in", clock(menu.rotatesIn)).getString() : tr("page", menu.page + 1, menu.pages).getString();
        // a special offer sits in the header beside the page, clear of the cards
        String offer = menu.open && menu.offerPercent > 0 && view != View.QTY ? tr("offer", menu.offerPercent).getString() : "";
        int offerW = offer.isEmpty() ? 0 : font.width(offer) + 10;
        String head = "> " + font.plainSubstrByWidth(menu.path.toUpperCase(Locale.ROOT), SW - 20 - font.width(right) - offerW);
        g.drawString(font, head, SX + 6, SY + 6, PHOS, false);
        g.drawString(font, right, SX + SW - 6 - font.width(right), SY + 6, menu.open ? PHOS_DIM : RED, false);
        if (!offer.isEmpty()) {
            int ox = SX + SW - 6 - font.width(right) - offerW;
            g.fill(ox, SY + 4, ox + offerW - 4, SY + 15, AMBER | 0xFF000000);
            g.drawString(font, offer, ox + 3, SY + 6, SCREEN & 0xFFFFFF, false);
        }
        g.fill(SX + 4, SY + 16, SX + SW - 4, SY + 17, SCREEN_EDGE);
        int msgH = CashMachine.height(font, message, SW - 12);
        int bottom = SY + SH - (msgH > 0 ? msgH + 4 : 4);
        CashMachine.clip(g, leftPos, topPos, SX, SY + 18, SW, SH - 18);
        if (!menu.open) {
            g.pose().pushPose();
            g.pose().translate(SX + SW / 2f - font.width(tr("closed").getString()), SY + 50, 0);
            g.pose().scale(2f, 2f, 1f);
            g.drawString(font, tr("closed").getString(), 0, 0, RED, false);
            g.pose().popPose();
            g.drawString(font, tr("opens_at", menu.opensAt), SX + SW / 2 - font.width(tr("opens_at", menu.opensAt)) / 2, SY + 80, PHOS_DIM, false);
            String until = tr("opens_in", menu.untilOpen / 60, menu.untilOpen % 60).getString();
            g.drawString(font, until, SX + SW / 2 - font.width(until) / 2, SY + 92, PHOS_DIM, false);
        } else if (view == View.QTY) {
            ShopMenu.Card c = card(sel);
            if (c != null) {
                g.renderItem(c.stack(), SX + 20, SY + 40);
                CashMachine.fit(g, font, c.stack().getCount() + " " + c.stack().getHoverName().getString(), SX + 42, SY + 44, SW - 50, PHOS);
                long unit = selling ? c.sell() : c.buy();
                String n = String.valueOf(lots);
                g.pose().pushPose();
                g.pose().translate(SX + SW / 2f - font.width(n), SY + 64, 0);
                g.pose().scale(2f, 2f, 1f);
                g.drawString(font, n, 0, 0, PHOS, false);
                g.pose().popPose();
                g.drawString(font, tr("lots").getString(), SX + SW / 2 + font.width(n) + 4, SY + 72, PHOS_DIM, false);
                String total = tr(selling ? "you_get" : "total", Bank.format(unit * lots), unitName(c.currency())).getString();
                CashMachine.fit(g, font, total, SX + SW / 2 - Math.min(font.width(total), SW - 16) / 2, SY + 92, SW - 16, AMBER);
                CashMachine.wrap(g, font, tr(selling ? "max_sell_hint" : "max_buy_hint"), SX + 8, SY + 108, SW - 16, PHOS_DIM);
            }
        } else {
            for (int k = 0; k < menu.cards.size(); k++) {
                ShopMenu.Card c = menu.cards.get(k);
                int cx = CX + (k % menu.columns) * (cw() + CGAP), cy = CY + (k / menu.columns) * (ch() + CGAP);
                small(g, c.section().isEmpty() ? (c.buy() >= 0 ? Shop.priceLabel("buy", c.buy(), c.currency()) : "--") : "> open", cx + 2, cy + labelY(), c.buy() >= 0 ? AMBER : PHOS_DIM);
                if (c.section().isEmpty()) {
                    small(g, c.sell() >= 0 ? Shop.priceLabel("sell", c.sell(), c.currency()) : "", cx + 2, cy + labelY() + 7, PHOS_DIM);
                }
                // the limits left, as "3/4": what you can still buy (green, top right) and what the shop still takes (amber; top left beside a buy count)
                boolean both = c.stock().limited() && c.selling().limited();
                if (c.stock().limited()) {
                    corner(g, c.stock().left() == 0 ? "OUT" : c.stock().left() + "/" + c.stock().of(), cx, cy, true, both, c.stock().left() == 0 ? RED : PHOS);
                }
                if (c.selling().limited()) {
                    corner(g, c.selling().left() == 0 ? "FULL" : c.selling().left() + "/" + c.selling().of(), cx + (both && c.instant() ? 5 : 0), cy, !both, both, c.selling().left() == 0 ? RED : AMBER);
                }
                if (c.instant()) {
                    small(g, "*", cx + 2, cy + 2, AMBER);
                }
            }
            if (menu.cards.isEmpty()) {
                g.drawString(font, tr("empty"), SX + 8, SY + 40, PHOS_DIM, false);
            }
            if (msgH == 0) { // a message takes the hint's place, so neither runs into the last row of cards
                CashMachine.wrapUp(g, font, tr("hint"), SX + 6, bottom, SW - 12, PHOS_DIM);
            }
        }
        g.disableScissor();
        if (msgH > 0) {
            CashMachine.wrap(g, font, message, SX + 6, SY + SH - 4 - msgH, SW - 12, messageColour);
        }
    }

    /** Card size from the shop's layout: cards per row and rows per page. */
    private int cw() {
        return (SW - 12 - (menu.columns - 1) * CGAP) / menu.columns;
    }

    private int ch() {
        return menu.rows >= 3 ? 36 : 56;
    }

    private int iconY() {
        return menu.rows >= 3 ? 3 : 10;
    }

    private int labelY() {
        return menu.rows >= 3 ? 21 : 32;
    }

    /** A count in a top corner of the card: small text, shrunk to half the card when both corners hold one. */
    private void corner(GuiGraphics g, String s, int cx, int cy, boolean right, boolean half, int colour) {
        float scale = Math.min(0.75f, ((half ? cw() / 2 : cw()) - 4) / (float) Math.max(1, font.width(s)));
        int x = right ? cx + cw() - 3 - (int) (font.width(s) * scale) : cx + 2;
        g.pose().pushPose();
        g.pose().translate(x, cy + 2, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, s, 0, 0, colour, false);
        g.pose().popPose();
    }

    /** Small (three-quarter) text, shrunk further if it would leave the card. */
    private void small(GuiGraphics g, String s, int x, int y, int colour) {
        float scale = Math.min(0.75f, (cw() - 4) / (float) Math.max(1, font.width(s)));
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, s, 0, 0, colour, false);
        g.pose().popPose();
    }

    private static String clock(long ms) {
        long m = ms / 60_000;
        return m >= 60 ? (m / 60) + " h " + (m % 60) + " m" : m + " m";
    }

    @Override
    public int panelWidth() {
        return imageWidth;
    }

    @Override
    public int panelHeight() {
        return imageHeight;
    }
}
