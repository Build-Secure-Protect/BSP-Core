package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.ShopActionPacket;
import com.mrgregles.bsp_core.network.ShopDataPacket;
import com.mrgregles.bsp_core.shop.Shop;
import com.mrgregles.bsp_core.shop.ShopMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The player's shop screen in the Cash Machine skin: the page of cards on the phosphor screen
 * (icon, "buy N", "sell N", stock badge; folder cards for sub-sections), a quantity view for a buy
 * or a sell, the closed view (name, CLOSED, when it opens, the countdown), keys in the right-hand
 * column (Back and pages at the top; Sell here, Sell all and Admin at the bottom) and the status strip.
 */
public class ShopScreen extends AbstractContainerScreen<ShopMenu> {
    private static final int W = 300, H = 216;
    private static final int BEZEL = 0xFFCFC8B8, BEZEL_DARK = 0xFF8F877A, BEZEL_LIGHT = 0xFFECE6D8, SCREEN = 0xFF061008, SCREEN_EDGE = 0xFF0D3B22, PHOS = 0x58F29A,
            PHOS_DIM = 0x3FB97A, AMBER = 0xF2B544, RED = 0xFF6B5C, KEY = 0xFFEAE4D5, KEY_EDGE = 0xFF9A9183, KEY_TEXT = 0x2B2823, KEY_LOCKED = 0x8F877A, CARD = 0xFF0A1A10, CARD_SEL = 0xFF0F2D1C, CARD_OUT = 0xFF070D09;
    private static final int SX = 10, SY = 10, SW = 206, SH = 160, KX = 226, KW = 64, KH = 20, KGAP = 4, STRIP_Y = 180, STRIP_H = 22, CX = SX + 6, CY = SY + 22, CW = 28, CH = 42, CGAP = 1;

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
            return Math.max(1, held / c.stack().getCount());
        }
        long afford = c.buy() <= 0 ? 999 : c.currency() < 0 ? menu.balance / c.buy() : 999;
        int fits = com.mrgregles.bsp_core.shop.Shops.fits(minecraft.player, c.stack(), 999);
        long n = Math.min(afford, fits);
        if (c.stock() >= 0) {
            n = Math.min(n, c.stock());
        }
        return (int) Math.max(1, n);
    }

    private void pollMessage() {
        if (menu.messageSerial == lastSerial) {
            return;
        }
        lastSerial = menu.messageSerial;
        String key = menu.messageKey;
        boolean good = key.equals("bought") || key.equals("sold") || key.equals("sold_all");
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
                int cx = x + CX + (k % Shop.COLUMNS) * (CW + CGAP), cy = y + CY + (k / Shop.COLUMNS) * (CH + CGAP);
                if (over(mx, my, cx, cy, CW, CH)) {
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
                    } else if (button == 0 && c.buy() >= 0 && c.stock() != 0) {
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
                int cx = leftPos + CX + (k % Shop.COLUMNS) * (CW + CGAP), cy = topPos + CY + (k / Shop.COLUMNS) * (CH + CGAP);
                if (over(mx, my, cx, cy, CW, CH)) {
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
                        if (c.stock() >= 0) {
                            tip.add(tr(c.stock() == 0 ? "tip_out" : "tip_stock", c.stock()));
                        }
                    }
                    g.renderComponentTooltip(font, tip, mx, my);
                }
            }
        }
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
                int cx = x + CX + (k % Shop.COLUMNS) * (CW + CGAP), cy = y + CY + (k / Shop.COLUMNS) * (CH + CGAP);
                boolean out = c.stock() == 0;
                g.fill(cx, cy, cx + CW, cy + CH, out ? CARD_OUT : over(mx, my, cx, cy, CW, CH) ? CARD_SEL : CARD);
                g.fill(cx, cy, cx + CW, cy + 1, out ? 0xFF1F6B43 : PHOS_DIM | 0xFF000000);
                g.fill(cx, cy + CH - 1, cx + CW, cy + CH, out ? 0xFF1F6B43 : PHOS_DIM | 0xFF000000);
                g.renderItem(c.stack(), cx + 6, cy + 3);
                g.renderItemDecorations(font, c.stack(), cx + 6, cy + 3);
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
        String head = "> " + font.plainSubstrByWidth(menu.path.toUpperCase(Locale.ROOT), SW - 20 - font.width(right));
        g.drawString(font, head, SX + 6, SY + 6, PHOS, false);
        g.drawString(font, right, SX + SW - 6 - font.width(right), SY + 6, menu.open ? PHOS_DIM : RED, false);
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
            if (menu.offerPercent > 0) {
                String offer = tr("offer", menu.offerPercent).getString();
                g.fill(SX + 6, SY + 18, SX + 10 + font.width(offer), SY + 28, AMBER | 0xFF000000);
                g.drawString(font, offer, SX + 8, SY + 19, SCREEN & 0xFFFFFF, false);
            }
            for (int k = 0; k < menu.cards.size(); k++) {
                ShopMenu.Card c = menu.cards.get(k);
                int cx = CX + (k % Shop.COLUMNS) * (CW + CGAP), cy = CY + (k / Shop.COLUMNS) * (CH + CGAP);
                small(g, c.section().isEmpty() ? (c.buy() >= 0 ? "buy " + Bank.format(c.buy()) + unit(c.currency()) : "--") : "> open", cx + 2, cy + 22, c.buy() >= 0 ? AMBER : PHOS_DIM);
                if (c.section().isEmpty()) {
                    small(g, c.sell() >= 0 ? "sell " + Bank.format(c.sell()) + unit(c.currency()) : "", cx + 2, cy + 30, PHOS_DIM);
                }
                if (c.stock() >= 0) {
                    small(g, c.stock() == 0 ? "OUT" : String.valueOf(c.stock()), cx + CW - 3 - (int) (font.width(c.stock() == 0 ? "OUT" : String.valueOf(c.stock())) * 0.75f), cy + 2, c.stock() == 0 ? RED : PHOS);
                }
                if (c.instant()) {
                    small(g, "*", cx + 2, cy + 2, AMBER);
                }
            }
            if (menu.cards.isEmpty()) {
                g.drawString(font, tr("empty"), SX + 8, SY + 40, PHOS_DIM, false);
            }
            CashMachine.wrapUp(g, font, tr("hint"), SX + 6, bottom, SW - 12, PHOS_DIM);
        }
        g.disableScissor();
        if (msgH > 0) {
            CashMachine.wrap(g, font, message, SX + 6, SY + SH - 4 - msgH, SW - 12, messageColour);
        }
    }

    private void small(GuiGraphics g, String s, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, s, 0, 0, colour, false);
        g.pose().popPose();
    }

    private static String clock(long ms) {
        long m = ms / 60_000;
        return m >= 60 ? (m / 60) + " h " + (m % 60) + " m" : m + " m";
    }
}
