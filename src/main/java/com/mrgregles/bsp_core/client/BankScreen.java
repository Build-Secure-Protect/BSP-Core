package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.BankAccount;
import com.mrgregles.bsp_core.bank.BankMenu;
import com.mrgregles.bsp_core.bank.BankTransaction;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.BankActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The banking screen in the "Cash Machine" skin: a beige bezel, a black phosphor screen that fills
 * the left and middle, one column of keys on the right (navigation at the top, the view's own
 * actions at the bottom) and a status strip across the base. Views: account, pay a player,
 * transaction log, deposit coins, withdraw coins. The Portable Banker shows the same screen with a
 * Shopping Network key in place of the deposit keys.
 */
public class BankScreen extends AbstractContainerScreen<BankMenu> {
    private static final int W = 300, H = 216;
    private static final int BEZEL = 0xFFCFC8B8, BEZEL_DARK = 0xFF8F877A, BEZEL_LIGHT = 0xFFECE6D8, SCREEN = 0xFF061008, SCREEN_EDGE = 0xFF0D3B22, PHOS = 0x58F29A,
            PHOS_DIM = 0x3FB97A, AMBER = 0xF2B544, KEY = 0xFFEAE4D5, KEY_EDGE = 0xFF9A9183, KEY_TEXT = 0x2B2823, KEY_LOCKED = 0x8F877A, ROW_SEL = 0xFF0F2D1C;
    private static final int SX = 10, SY = 10, SW = 206, SH = 160, KX = 226, KW = 64, KH = 20, KGAP = 4, STRIP_Y = 180, STRIP_H = 22;
    private static final int PAGE = 7;
    private static final String[] FILTERS = {"all", "bought", "sold", "paid", "received", "deposits"};
    private static final SimpleDateFormat DATE = new SimpleDateFormat("d MMM HH:mm", Locale.ROOT);

    private enum View { ACCOUNT, PAY, LOG, DEPOSIT, WITHDRAW }

    private record Key(String label, boolean enabled, Runnable action) {}

    private View view = View.ACCOUNT;
    private final List<Key> top = new ArrayList<>(), bottom = new ArrayList<>();
    private EditBox nameBox, amountBox, searchBox;
    private int filter, page, tier, count = 1, lastSerial = -1;
    private boolean sortByAmount, convert = true, confirming;
    private String confirmName = "";
    private long confirmAmount;
    private Component message = Component.empty();
    private int messageColour = AMBER;

    public BankScreen(BankMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    /** Client: a data packet for the open banking or deposit box screen. */
    public static void receive(com.mrgregles.bsp_core.network.BankDataPacket msg) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof com.mrgregles.bsp_core.bank.BankClientMenu menu) {
            menu.receive(msg.account(), msg.log(), msg.found(), msg.messageKey(), msg.messageArgs());
        }
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void init() {
        super.init();
        nameBox = box(SX + 8, SY + 22, 120, "find");
        nameBox.setResponder(s -> send(BankActionPacket.Action.SEARCH, 0, 0, s, 0, false));
        amountBox = box(SX + 8, SY + 96, 90, "amount");
        amountBox.setFilter(s -> s.matches("[0-9]{0,12}"));
        searchBox = box(SX + 8, SY + 20, 120, "search");
        setView(View.ACCOUNT);
    }

    private EditBox box(int x, int y, int w, String hint) {
        EditBox b = new EditBox(font, leftPos + x, topPos + y, w, 10, tr(hint));
        b.setBordered(false);
        b.setTextColor(PHOS);
        b.setHint(tr(hint).withStyle(s -> s.withColor(PHOS_DIM)));
        b.setMaxLength(hint.equals("amount") ? 12 : 16);
        addRenderableWidget(b);
        return b;
    }

    private static net.minecraft.network.chat.MutableComponent tr(String key, Object... args) {
        return Component.translatable("gui.bsp_core.bank." + key, args);
    }

    private BankAccount account() {
        return menu.account;
    }

    private void send(BankActionPacket.Action action, int tier, int count, String text, long amount, boolean flag) {
        BSPNetwork.CHANNEL.sendToServer(new BankActionPacket(action, tier, count, text, amount, flag));
    }

    private void setView(View v) {
        view = v;
        confirming = false;
        nameBox.setVisible(v == View.PAY);
        amountBox.setVisible(v == View.PAY);
        searchBox.setVisible(v == View.LOG);
        setFocused(null);
        top.clear();
        bottom.clear();
        boolean portable = menu.portable();
        switch (v) {
            case ACCOUNT -> {
                top.add(new Key("pay_players", true, () -> setView(View.PAY)));
                top.add(new Key("transaction_log", true, () -> setView(View.LOG)));
                if (portable) {
                    top.add(new Key(account().remoteShops ? "shopping_network" : "unlock_network", true,
                            () -> { if (!account().remoteShops) { send(BankActionPacket.Action.UNLOCK_NETWORK, 0, 0, "", 0, false); } }));
                    bottom.add(new Key("convert_at_bank", false, () -> {}));
                } else {
                    bottom.add(new Key("deposit_coins", true, () -> setView(View.DEPOSIT)));
                    bottom.add(new Key("withdraw_coins", true, () -> setView(View.WITHDRAW)));
                }
            }
            case PAY -> {
                top.add(new Key("back", true, () -> setView(View.ACCOUNT)));
                top.add(new Key(account().acceptPay ? "accept_on" : "accept_off", true, () -> send(BankActionPacket.Action.TOGGLE_PAY, 0, 0, "", 0, false)));
                bottom.add(new Key("pay", true, this::pay));
            }
            case LOG -> {
                top.add(new Key("back", true, () -> setView(View.ACCOUNT)));
                top.add(new Key("prev_page", true, () -> page = Math.max(0, page - 1)));
                top.add(new Key("next_page", true, () -> page = Math.min(Math.max(0, (filtered().size() - 1) / PAGE), page + 1)));
                bottom.add(new Key("filter", true, () -> { filter = (filter + 1) % FILTERS.length; page = 0; }));
                bottom.add(new Key(sortByAmount ? "sort_amount" : "sort_date", true, () -> { sortByAmount = !sortByAmount; setView(View.LOG); }));
            }
            case DEPOSIT, WITHDRAW -> {
                top.add(new Key("back", true, () -> setView(View.ACCOUNT)));
                if (v == View.DEPOSIT) {
                    top.add(new Key(convert ? "convert" : "keep", true, () -> { convert = !convert; setView(View.DEPOSIT); }));
                }
                bottom.add(new Key("minus", true, () -> count = Math.max(1, count - 1)));
                bottom.add(new Key("plus", true, () -> count = Math.min(available(), count + 1)));
                bottom.add(new Key("all", true, () -> count = Math.max(1, available())));
                bottom.add(new Key("confirm", available() > 0, () -> send(v == View.DEPOSIT ? BankActionPacket.Action.DEPOSIT : BankActionPacket.Action.WITHDRAW, tier, count, "", 0, convert)));
            }
        }
    }

    private int available() {
        CoinTier t = CoinTier.values()[tier];
        return view == View.DEPOSIT ? minecraft.player.getInventory().countItem(t.coin()) : account().coins(t);
    }

    private void pay() {
        String name = nameBox.getValue().trim();
        long amount;
        try {
            amount = Long.parseLong(amountBox.getValue().trim());
        } catch (NumberFormatException e) {
            amount = 0;
        }
        if (confirming) {
            send(BankActionPacket.Action.PAY, 0, 0, confirmName, confirmAmount, true);
            confirming = false;
            setView(View.PAY);
            return;
        }
        send(BankActionPacket.Action.PAY, 0, 0, name, amount, false);
    }

    private List<BankTransaction> filtered() {
        String q = searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        List<BankTransaction> out = new ArrayList<>();
        for (BankTransaction t : menu.log) {
            boolean ok = switch (FILTERS[filter]) {
                case "bought" -> t.type() == BankTransaction.Type.BOUGHT;
                case "sold" -> t.type() == BankTransaction.Type.SOLD;
                case "paid" -> t.type() == BankTransaction.Type.PAID;
                case "received" -> t.type() == BankTransaction.Type.RECEIVED;
                case "deposits" -> t.type() == BankTransaction.Type.DEPOSIT || t.type() == BankTransaction.Type.CONVERT || t.type() == BankTransaction.Type.WITHDRAW;
                default -> true;
            };
            if (ok && (q.isEmpty() || t.with().toLowerCase(Locale.ROOT).contains(q) || t.detail().toLowerCase(Locale.ROOT).contains(q))) {
                out.add(t);
            }
        }
        if (sortByAmount) {
            out.sort((a, b) -> Long.compare(Math.abs(b.pieces()), Math.abs(a.pieces())));
        }
        return out;
    }

    /** Reacts to a fresh message from the server. */
    private void pollMessage() {
        if (menu.messageSerial == lastSerial) {
            return;
        }
        lastSerial = menu.messageSerial;
        String key = menu.messageKey;
        Object[] args = menu.messageArgs;
        if (key.equals("confirm_screen")) {
            confirming = true;
            confirmName = args.length > 1 ? (String) args[1] : nameBox.getValue().trim();
            try {
                confirmAmount = Long.parseLong(amountBox.getValue().trim());
            } catch (NumberFormatException e) {
                confirmAmount = 0;
            }
            bottom.clear();
            bottom.add(new Key("confirm", true, this::pay));
            bottom.add(new Key("cancel", true, () -> setView(View.PAY)));
        } else if (key.equals("paid")) {
            setView(View.PAY);
            amountBox.setValue("");
        } else if (key.equals("accepting") || key.equals("refusing")) {
            setView(View.PAY);
        } else if (key.equals("network_unlocked")) {
            setView(View.ACCOUNT);
        } else if (key.equals("converted") || key.equals("deposited") || key.equals("withdrawn")) {
            count = 1;
            setView(view);
        }
        boolean bad = !(key.equals("paid") || key.equals("converted") || key.equals("deposited") || key.equals("withdrawn") || key.equals("accepting") || key.equals("refusing") || key.equals("confirm_screen") || key.equals("network_unlocked"));
        messageColour = bad ? 0xFF6B5C : AMBER;
        message = Component.translatable("message.bsp_core.bank." + key, args);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = leftPos, y = topPos;
        int ky = y + SY;
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
        if (view == View.PAY) {
            for (int i = 0; i < Math.min(5, menu.found.size()); i++) {
                if (over(mx, my, x + SX + 8, y + SY + 36 + i * 11, 120, 10)) {
                    nameBox.setValue(menu.found.get(i));
                    click();
                    return true;
                }
            }
        }
        if (view == View.LOG) {
            int fx = x + SX + 6;
            for (int i = 0; i < FILTERS.length; i++) {
                int w = font.width(tr("filter_" + FILTERS[i])) + 6;
                if (over(mx, my, fx, y + SY + 34, w, 10)) {
                    filter = i;
                    page = 0;
                    click();
                    return true;
                }
                fx += w + 3;
            }
        }
        if (view == View.DEPOSIT || view == View.WITHDRAW) {
            for (int i = 0; i < CoinTier.values().length; i++) {
                if (over(mx, my, x + SX + 10 + i * 38, y + SY + 30, 34, 40)) {
                    tier = i;
                    count = 1;
                    click();
                    setView(view);
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

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((nameBox.isFocused() || amountBox.isFocused() || searchBox.isFocused()) && key != 256) {
            if (key == 257 && view == View.PAY) {
                pay();
            }
            return nameBox.keyPressed(key, scan, mods) || amountBox.keyPressed(key, scan, mods) || searchBox.keyPressed(key, scan, mods) || true;
        }
        return super.keyPressed(key, scan, mods);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        pollMessage();
        renderBackground(g);
        super.render(g, mx, my, partial);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, BEZEL_DARK);
        g.fill(x, y, x + W, y + H, BEZEL);
        g.fill(x, y, x + W, y + 2, BEZEL_LIGHT);
        g.fill(x, y + H - 3, x + W, y + H, BEZEL_DARK);
        // the screen
        g.fill(x + SX - 2, y + SY - 2, x + SX + SW + 2, y + SY + SH + 2, SCREEN_EDGE);
        g.fill(x + SX, y + SY, x + SX + SW, y + SY + SH, SCREEN);
        for (int ly = y + SY; ly < y + SY + SH; ly += 3) {
            g.fill(x + SX, ly, x + SX + SW, ly + 1, 0x14000000);
        }
        // keys
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
        // status strip
        g.fill(x + SX - 2, y + STRIP_Y - 2, x + W - SX + 2, y + STRIP_Y + STRIP_H + 2, SCREEN_EDGE);
        g.fill(x + SX, y + STRIP_Y, x + W - SX, y + STRIP_Y + STRIP_H, SCREEN);
        String where = menu.portable() ? tr("anywhere").getString() : tr("at_bank").getString();
        g.drawString(font, where + " / " + Bank.serverId(), x + SX + 6, y + STRIP_Y + 7, PHOS_DIM, false);
        String synced = tr("synced").getString();
        g.drawString(font, synced, x + W - SX - 6 - font.width(synced), y + STRIP_Y + 7, PHOS_DIM, false);
    }

    private void key(GuiGraphics g, int x, int y, Key k, int mx, int my) {
        boolean hover = k.enabled && over(mx, my, x, y, KW, KH);
        g.fill(x, y, x + KW, y + KH + 2, KEY_EDGE);
        g.fill(x, y, x + KW, y + KH, hover ? 0xFFF5F0E4 : KEY);
        String label = tr(k.label).getString().toUpperCase(Locale.ROOT);
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(label), KW - 6);
        int ty = y + (KH - lines.size() * 9) / 2 + 1;
        for (var line : lines) {
            g.drawString(font, line, x + KW / 2 - font.width(line) / 2, ty, k.enabled ? KEY_TEXT : KEY_LOCKED, false);
            ty += 9;
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        // header line of the screen
        String head = "> " + (menu.portable() ? tr("title_portable") : tr("title_bank")).getString().toUpperCase(Locale.ROOT);
        g.drawString(font, head, SX + 6, SY + 6, PHOS, false);
        String who = minecraft.player.getGameProfile().getName();
        g.drawString(font, who, SX + SW - 6 - font.width(who), SY + 6, PHOS_DIM, false);
        g.fill(SX + 4, SY + 16, SX + SW - 4, SY + 17, SCREEN_EDGE);
        switch (view) {
            case ACCOUNT -> drawAccount(g);
            case PAY -> drawPay(g);
            case LOG -> drawLog(g);
            case DEPOSIT, WITHDRAW -> drawCoins(g);
        }
        // the message line at the foot of the screen
        if (!message.getString().isEmpty()) {
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(message, SW - 12);
            int ly = SY + SH - 4 - lines.size() * 9;
            for (var line : lines) {
                g.drawString(font, line, SX + 6, ly, messageColour, false);
                ly += 9;
            }
        }
    }

    private void drawAccount(GuiGraphics g) {
        String big = Bank.format(account().pieces);
        g.pose().pushPose();
        g.pose().translate(SX + SW / 2f - font.width(big), SY + 30, 0);
        g.pose().scale(2f, 2f, 1f);
        g.drawString(font, big, 0, 0, PHOS, false);
        g.pose().popPose();
        String unit = tr("pieces").getString().toUpperCase(Locale.ROOT);
        g.drawString(font, unit, SX + SW / 2 + font.width(big) + 4, SY + 38, PHOS_DIM, false);
        coinRow(g, SY + 58, true);
        List<net.minecraft.util.FormattedCharSequence> hint = font.split(tr("coins_hint", Bank.format(Bank.piecesPerCoin(CoinTier.COPPER))), SW - 16);
        int hy = SY + 110;
        for (var line : hint) {
            g.drawString(font, line, SX + 8, hy, PHOS_DIM, false);
            hy += 9;
        }
        if (menu.portable()) {
            g.drawString(font, tr(account().remoteShops ? "network_open" : "network_locked", com.mrgregles.bsp_core.BSPConfig.getOr(com.mrgregles.bsp_core.BSPConfig.BANK_REMOTE_UNLOCK_VALUE, 16)), SX + 8, hy + 4, AMBER, false);
        }
    }

    /** The five tiers in a row: a phosphor ring around the coin, its count below; dim and broken when none. */
    private void coinRow(GuiGraphics g, int y, boolean bank) {
        for (CoinTier t : CoinTier.values()) {
            int i = t.ordinal(), cx = SX + 10 + i * 38;
            int n = bank ? account().coins(t) : minecraft.player.getInventory().countItem(t.coin());
            int ring = n > 0 ? PHOS : PHOS_DIM;
            boolean sel = (view == View.DEPOSIT || view == View.WITHDRAW) && tier == i;
            if (sel) {
                g.fill(cx - 2, y - 2, cx + 36, y + 40, ROW_SEL);
            }
            int bx = cx + 8;
            if (n > 0) {
                g.fill(bx - 2, y - 2, bx + 18, y - 1, ring);
                g.fill(bx - 2, y + 16, bx + 18, y + 17, ring);
                g.fill(bx - 2, y - 2, bx - 1, y + 17, ring);
                g.fill(bx + 17, y - 2, bx + 18, y + 17, ring);
            } else {
                for (int d = -2; d < 18; d += 4) {
                    g.fill(bx + d, y - 2, bx + d + 2, y - 1, ring);
                    g.fill(bx + d, y + 16, bx + d + 2, y + 17, ring);
                    g.fill(bx - 2, y + d, bx - 1, y + d + 2, ring);
                    g.fill(bx + 17, y + d, bx + 18, y + d + 2, ring);
                }
            }
            g.renderItem(new ItemStack(t.coin()), bx, y);
            String cnt = String.valueOf(n);
            g.drawString(font, cnt, cx + 17 - font.width(cnt) / 2, y + 20, n > 0 ? PHOS : PHOS_DIM, false);
            String name = Bank.tierName(t).toUpperCase(Locale.ROOT);
            g.pose().pushPose();
            g.pose().translate(cx + 17 - font.width(name) * 0.5f * 0.75f, y + 30, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, name, 0, 0, PHOS_DIM, false);
            g.pose().popPose();
        }
    }

    private void drawPay(GuiGraphics g) {
        g.drawString(font, tr("find"), SX + 8, SY + 22 - 10, PHOS_DIM, false);
        g.fill(SX + 6, SY + 32, SX + 132, SY + 33, SCREEN_EDGE);
        for (int i = 0; i < Math.min(5, menu.found.size()); i++) {
            String n = menu.found.get(i);
            boolean chosen = n.equalsIgnoreCase(nameBox.getValue().trim());
            g.drawString(font, (chosen ? "> " : "  ") + n, SX + 8, SY + 36 + i * 11, chosen ? PHOS : PHOS_DIM, false);
        }
        g.drawString(font, tr("amount"), SX + 8, SY + 96 - 10, PHOS_DIM, false);
        g.fill(SX + 6, SY + 106, SX + 100, SY + 107, SCREEN_EDGE);
        g.drawString(font, tr("pieces"), SX + 104, SY + 96, PHOS_DIM, false);
        long amount;
        try {
            amount = Long.parseLong(amountBox.getValue().trim());
        } catch (NumberFormatException e) {
            amount = 0;
        }
        String after = tr("balance_after", Bank.format(Math.max(0, account().pieces - amount))).getString();
        g.drawString(font, after, SX + 8, SY + 112, PHOS_DIM, false);
        g.drawString(font, tr("balance", Bank.format(account().pieces)), SX + SW - 6 - font.width(tr("balance", Bank.format(account().pieces))), SY + 22 - 10, PHOS_DIM, false);
    }

    private void drawLog(GuiGraphics g) {
        g.fill(SX + 6, SY + 30, SX + 132, SY + 31, SCREEN_EDGE);
        int fx = SX + 6;
        for (int i = 0; i < FILTERS.length; i++) {
            String f = tr("filter_" + FILTERS[i]).getString();
            int w = font.width(f) + 6;
            if (i == filter) {
                g.fill(fx, SY + 33, fx + w, SY + 44, PHOS);
            } else {
                g.fill(fx, SY + 33, fx + w, SY + 34, PHOS_DIM);
                g.fill(fx, SY + 43, fx + w, SY + 44, PHOS_DIM);
            }
            g.drawString(font, f, fx + 3, SY + 35, i == filter ? SCREEN & 0xFFFFFF : PHOS_DIM, false);
            fx += w + 3;
        }
        List<BankTransaction> list = filtered();
        if (list.isEmpty()) {
            g.drawString(font, tr("log_empty"), SX + 8, SY + 52, PHOS_DIM, false);
            return;
        }
        int pages = (list.size() + PAGE - 1) / PAGE;
        page = Math.min(page, pages - 1);
        for (int i = page * PAGE; i < Math.min(list.size(), (page + 1) * PAGE); i++) {
            BankTransaction t = list.get(i);
            int ry = SY + 50 + (i - page * PAGE) * 11;
            String when = DATE.format(new Date(t.ms()));
            String what = t.type().name().toLowerCase(Locale.ROOT) + " " + t.with() + (t.detail().isEmpty() ? "" : ", " + t.detail());
            String amount = t.pieces() != 0 ? (t.pieces() > 0 ? "+" : "-") + Bank.format(Math.abs(t.pieces()))
                    : t.coinTier() >= 0 ? (t.coinCount() > 0 ? "+" : "") + t.coinCount() + " " + Bank.tierName(CoinTier.values()[t.coinTier()]) : "";
            g.drawString(font, when, SX + 6, ry, PHOS_DIM, false);
            int aw = font.width(amount);
            int maxW = SW - 12 - 66 - aw - 4;
            g.drawString(font, font.plainSubstrByWidth(what, maxW), SX + 72, ry, PHOS, false);
            g.drawString(font, amount, SX + SW - 6 - aw, ry, t.pieces() < 0 ? AMBER : PHOS, false);
        }
        String pg = tr("page", page + 1, pages, list.size()).getString();
        g.drawString(font, pg, SX + SW - 6 - font.width(pg), SY + SH - 22, PHOS_DIM, false);
    }

    private void drawCoins(GuiGraphics g) {
        boolean dep = view == View.DEPOSIT;
        g.drawString(font, tr(dep ? "deposit_title" : "withdraw_title"), SX + 8, SY + 20, PHOS_DIM, false);
        coinRow(g, SY + 30, !dep);
        CoinTier t = CoinTier.values()[tier];
        int avail = available();
        count = Math.max(1, Math.min(count, Math.max(1, avail)));
        String line1 = tr(dep ? "in_hand" : "in_bank", avail, Bank.tierName(t)).getString();
        g.drawString(font, line1, SX + 8, SY + 84, PHOS_DIM, false);
        String preview = dep ? (convert ? tr("preview_convert", count, Bank.tierName(t), Bank.format(Bank.piecesPerCoin(t) * count)) : tr("preview_keep", count, Bank.tierName(t))).getString()
                : tr("preview_withdraw", count, Bank.tierName(t)).getString();
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.literal(preview), SW - 16);
        int py = SY + 98;
        for (var line : lines) {
            g.drawString(font, line, SX + 8, py, PHOS, false);
            py += 9;
        }
        if (dep) {
            g.drawString(font, tr(convert ? "convert_hint" : "keep_hint"), SX + 8, py + 4, PHOS_DIM, false);
        }
    }
}
