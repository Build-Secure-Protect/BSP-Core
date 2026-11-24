package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.DepositBox;
import com.mrgregles.bsp_core.bank.DepositBoxMenu;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.DepositBoxActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The deposit box screen in the Cash Machine skin: the bezel, a phosphor screen holding the 18 slots
 * (locked ones hatched) or the locked notice with what is owed, a key column (days up and down at
 * the top; rent with pieces or coins, add slots, pay to open at the bottom) and the player's inventory.
 */
public class DepositBoxScreen extends AbstractContainerScreen<DepositBoxMenu> implements ScreenFit.Fitted {
    private static final int BEZEL = 0xFFCFC8B8, BEZEL_DARK = 0xFF8F877A, BEZEL_LIGHT = 0xFFECE6D8, SCREEN = 0xFF061008, SCREEN_EDGE = 0xFF0D3B22, PHOS = 0x58F29A,
            PHOS_DIM = 0x3FB97A, AMBER = 0xF2B544, RED = 0xFF6B5C, KEY = 0xFFEAE4D5, KEY_EDGE = 0xFF9A9183, KEY_TEXT = 0x2B2823, KEY_LOCKED = 0x8F877A, SLOT_BG = 0xFF0C1A10, SLOT_LOCKED = 0xFF0A120C;
    private static final int SX = 10, SY = 10, SW = 206, KX = 226, KW = 64, KH = 20, KGAP = 4;
    private static final int DRAWER = 0xFF2A2210, DRAWER_EDGE = 0xFF8A6A1C, DRAWER_INSET = 0xFF4A3B16, DRAWER_SLOT = 0xFF1A150A;
    private static final int[] PLACE_EDGE = {0xFFFFD23A, 0xFFC9CED8, 0xFFC87A3C};
    private static final SimpleDateFormat DATE = new SimpleDateFormat("d MMM", Locale.ROOT);

    private record Key(String label, boolean enabled, Runnable action) {}

    private final List<Key> top = new ArrayList<>(), middle = new ArrayList<>(), bottom = new ArrayList<>();
    private int days = 7, lastSerial = -1;
    private Component message = Component.empty();
    private int messageColour = AMBER;

    public DepositBoxScreen(DepositBoxMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = DepositBoxMenu.WIDTH;
        this.imageHeight = menu.height;
        this.inventoryLabelX = DepositBoxMenu.INV_X;
        this.inventoryLabelY = menu.invY - 11;
    }

    private int screenHeight() {
        return menu.open ? DepositBoxMenu.SCREEN_OPEN : DepositBoxMenu.SCREEN_LOCKED;
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("gui.bsp_core.box." + key, args);
    }

    private DepositBox.State state() {
        return DepositBox.state(menu.account, System.currentTimeMillis());
    }

    private int coinTier() {
        // the smallest coin the player holds that covers the cost; -1 when none
        long cost = cost();
        for (CoinTier t : CoinTier.values()) {
            if (minecraft.player.getInventory().countItem(t.coin()) >= DepositBox.coinsFor(cost, t)) {
                return t.ordinal();
            }
        }
        return -1;
    }

    private long cost() {
        return DepositBox.rentCost(menu.account, System.currentTimeMillis(), days);
    }

    /** Three groups down the key column, each its own job: the days at the top, the two ways to pay in the middle, more slots on its own at the foot. */
    private int groupY(int group) {
        return switch (group) {
            case 0 -> topPos + SY;
            case 1 -> topPos + SY + 2 * (KH + KGAP) + 16;
            default -> topPos + imageHeight - 10 - KH;
        };
    }

    private List<Key> group(int group) {
        return group == 0 ? top : group == 1 ? middle : bottom;
    }

    private void keys() {
        top.clear();
        middle.clear();
        bottom.clear();
        int max = BSPConfig.getOr(BSPConfig.BOX_MAX_DAYS, 365);
        DepositBox.State st = state();
        top.add(new Key("minus_days", days > 1, () -> days = Math.max(1, days - 7 < 1 ? 1 : days - 7)));
        top.add(new Key("plus_days", DepositBox.daysLeft(menu.account, System.currentTimeMillis()) + days + 7 <= max, () -> days += 7));
        String rent = st == DepositBox.State.EXPIRED ? "pay_open" : "rent";
        middle.add(new Key(rent + "_pieces", menu.account.pieces >= cost(), () -> send(false, -1)));
        int tier = coinTier();
        middle.add(new Key(rent + "_coins", tier >= 0, () -> send(false, coinTier())));
        if (st == DepositBox.State.OPEN) {
            bottom.add(new Key("add_slots", menu.account.boxSlots < DepositBox.SLOTS_MAX && menu.account.pieces >= DepositBox.slotStepPieces(), () -> send(true, -1)));
        }
    }

    /** What a key's label says it costs: the pieces, or the smallest coin that covers it (or "coins" when none does). */
    private Object[] labelArgs(String label) {
        if (label.endsWith("_pieces")) {
            return new Object[]{days, Bank.format(cost())};
        }
        if (label.endsWith("_coins")) {
            int tier = coinTier();
            if (tier < 0) {
                return new Object[]{days, "", tr("coins_none").getString()};
            }
            CoinTier t = CoinTier.values()[tier];
            return new Object[]{days, DepositBox.coinsFor(cost(), t), Bank.tierName(t)};
        }
        if (label.equals("add_slots")) {
            return new Object[]{Bank.format(DepositBox.slotStepPieces())};
        }
        return new Object[]{days};
    }

    private void send(boolean slots, int tier) {
        BSPNetwork.CHANNEL.sendToServer(new DepositBoxActionPacket(slots, days, tier));
    }

    private void pollMessage() {
        if (menu.messageSerial == lastSerial) {
            return;
        }
        lastSerial = menu.messageSerial;
        String key = menu.messageKey;
        boolean good = key.equals("rented") || key.equals("slots_added");
        messageColour = good ? AMBER : RED;
        message = key.isEmpty() ? Component.empty() : Component.translatable("message.bsp_core.box." + key, (Object[]) menu.messageArgs);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        keys();
        for (int gi = 0; gi < 3; gi++) {
            int ky = groupY(gi);
            for (Key k : group(gi)) {
                if (over(mx, my, leftPos + KX, ky, KW, KH)) {
                    if (k.enabled) {
                        k.action.run();
                    }
                    return true;
                }
                ky += KH + KGAP;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        pollMessage();
        keys();
        renderBackground(g);
        super.render(g, mx, my, partial);
        renderTooltip(g, mx, my);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos, sh = screenHeight();
        g.fill(x - 2, y - 2, x + imageWidth + 2, y + imageHeight + 2, BEZEL_DARK);
        g.fill(x, y, x + imageWidth, y + imageHeight, BEZEL);
        g.fill(x, y, x + imageWidth, y + 2, BEZEL_LIGHT);
        g.fill(x + SX - 2, y + SY - 2, x + SX + SW + 2, y + SY + sh + 2, SCREEN_EDGE);
        g.fill(x + SX, y + SY, x + SX + SW, y + SY + sh, SCREEN);
        for (int ly = y + SY; ly < y + SY + sh; ly += 3) {
            g.fill(x + SX, ly, x + SX + SW, ly + 1, 0x14000000);
        }
        if (menu.open) {
            for (int i = 0; i < DepositBox.SLOTS_MAX; i++) {
                Slot slot = menu.slots.get(i);
                boolean unlocked = i < menu.account.boxSlots;
                g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, unlocked ? PHOS_DIM | 0xFF000000 : SLOT_LOCKED);
                g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, unlocked ? SLOT_BG : SLOT_LOCKED);
                if (!unlocked) {
                    for (int d = 0; d < 16; d += 4) {
                        g.fill(x + slot.x + d, y + slot.y + d, x + slot.x + d + 2, y + slot.y + d + 2, 0xFF1F6B43);
                    }
                }
            }
        }
        // the player's inventory, inset in the bezel (a rented box, or the prize drawer)
        for (int i = menu.open ? DepositBox.SLOTS_MAX : 0; i < menu.prizeStart; i++) {
            Slot slot = menu.slots.get(i);
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, KEY_EDGE);
            g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xFFDAD3C3);
        }
        drawer(g, x, y);
        for (int gi = 0; gi < 3; gi++) {
            int ky = groupY(gi);
            for (Key k : group(gi)) {
                key(g, x + KX, ky, k, mx, my);
                ky += KH + KGAP;
            }
        }
    }

    /** The gold prize drawer under the screen: each prize edged in its place's colour, the rest of the last row left empty. */
    private void drawer(GuiGraphics g, int x, int y) {
        int n = menu.prizes.count();
        if (n == 0) {
            return;
        }
        int dy = y + menu.drawerY, dh = DepositBoxMenu.drawerHeight(menu.drawerRows);
        g.fill(x + SX - 2, dy - 2, x + SX + SW + 2, dy + dh + 2, DRAWER_EDGE);
        g.fill(x + SX, dy, x + SX + SW, dy + dh, DRAWER);
        g.fill(x + SX, dy, x + SX + SW, dy + 2, DRAWER_INSET);
        for (int i = 0; i < menu.drawerRows * DepositBoxMenu.PRIZE_COLS; i++) {
            int sx = x + DepositBoxMenu.SLOT_X + 1 + (i % DepositBoxMenu.PRIZE_COLS) * 18, sy = dy + DepositBoxMenu.DRAWER_TITLE + (i / DepositBoxMenu.PRIZE_COLS) * 18;
            int edge = i < n ? PLACE_EDGE[Math.max(0, Math.min(2, menu.prizes.places()[i] - 1))] : DRAWER_INSET;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, edge);
            g.fill(sx, sy, sx + 16, sy + 16, DRAWER_SLOT);
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics g, int mx, int my) {
        if (hoveredSlot != null && hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            int index = menu.slots.indexOf(hoveredSlot);
            if (menu.isPrize(index)) {
                int i = index - menu.prizeStart;
                List<Component> lines = new ArrayList<>(getTooltipFromContainerItem(hoveredSlot.getItem()));
                lines.add(tr("prize_from", menu.prizes.places()[i], menu.prizes.seasons()[i]).withStyle(net.minecraft.ChatFormatting.GOLD));
                g.renderTooltip(font, lines, hoveredSlot.getItem().getTooltipImage(), mx, my);
                return;
            }
        }
        super.renderTooltip(g, mx, my);
    }

    private void key(GuiGraphics g, int x, int y, Key k, int mx, int my) {
        CashMachine.key(g, font, x, y, KW, KH, tr(k.label, labelArgs(k.label)).getString(), k.enabled, k.enabled && over(mx, my, x, y, KW, KH));
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        if (menu.prizes.count() > 0) {
            int ty = menu.drawerY + 5;
            String more = menu.prizes.more() > 0 ? tr("prizes_more", menu.prizes.more()).getString() : "";
            int moreW = more.isEmpty() ? 0 : font.width(more) + 6;
            CashMachine.fit(g, font, "> " + tr("prizes").getString().toUpperCase(Locale.ROOT), SX + 6, ty, SW - 12 - moreW, AMBER);
            if (!more.isEmpty()) {
                g.drawString(font, more, SX + SW - 6 - font.width(more), ty, PHOS_DIM, false);
            }
        }
        labels(g);
    }

    private void labels(GuiGraphics g) {
        long now = System.currentTimeMillis();
        DepositBox.State st = state();
        g.drawString(font, "> " + tr("title").getString().toUpperCase(Locale.ROOT), SX + 6, SY + 6, PHOS, false);
        String right = switch (st) {
            case OPEN -> tr("until", DATE.format(new Date(menu.account.boxUntilMs)), DepositBox.daysLeft(menu.account, now)).getString();
            case EXPIRED -> tr("lapsed", DepositBox.lapsedDays(menu.account, now)).getString();
            default -> tr("not_rented").getString();
        };
        g.fill(SX + 4, SY + 16, SX + SW - 4, SY + 17, SCREEN_EDGE);
        // the rent state on its own line under the title
        CashMachine.fit(g, font, right, SX + 6, SY + 20, SW - 12, st == DepositBox.State.EXPIRED ? RED : PHOS_DIM);
        int sh = screenHeight();
        // the message sits at the foot; the terms stack above it, clipped to the space left
        int bottom = SY + sh - 4;
        if (!message.getString().isEmpty()) {
            bottom = CashMachine.wrapUp(g, font, message, SX + 6, bottom, SW - 12, messageColour) - 2;
        }
        int top;
        if (menu.open) {
            // a rented box shows only how long the rent has left under the slots
            CashMachine.fit(g, font, tr("slots", menu.account.boxSlots, DepositBox.SLOTS_MAX).getString(), SX + 6, SY + 30, SW - 12, PHOS_DIM);
            long left = Math.max(0, menu.account.boxUntilMs - now), d = left / 86_400_000L, hh = left / 3_600_000L % 24, mm = left / 60_000L % 60;
            // a long message rises from the foot over these two lines: while it shows, the lines it reaches are left out
            if (bottom >= DepositBoxMenu.SLOT_Y + 49) {
                CashMachine.fit(g, font, tr("time_left", d, hh, mm).getString(), SX + 6, DepositBoxMenu.SLOT_Y + 40, SW - 12, PHOS);
            }
            // what the keys would charge for more days, in pieces and in the smallest coin that covers it
            int tier = coinTier();
            Component costLine = tier < 0 ? tr("cost_pieces", days, Bank.format(cost()))
                    : tr("cost_both", days, Bank.format(cost()), DepositBox.coinsFor(cost(), CoinTier.values()[tier]), Bank.tierName(CoinTier.values()[tier]));
            if (bottom >= DepositBoxMenu.SLOT_Y + 60) {
                CashMachine.fit(g, font, costLine.getString(), SX + 6, DepositBoxMenu.SLOT_Y + 51, SW - 12, PHOS_DIM);
            }
            return;
        } else {
            int ty = SY + 32;
            long cost = cost();
            Component owed = st == DepositBox.State.EXPIRED ? tr("owed", Bank.format(cost), days, DepositBox.lapsedDays(menu.account, now)) : tr("first", Bank.format(cost), days);
            CashMachine.clip(g, leftPos, topPos, SX, ty, SW, bottom - ty);
            ty += CashMachine.wrap(g, font, owed, SX + 6, ty, SW - 12, st == DepositBox.State.EXPIRED ? AMBER : PHOS) + 3;
            if (st == DepositBox.State.EXPIRED) {
                ty += CashMachine.wrap(g, font, tr("kept", menu.account.boxSlots), SX + 6, ty, SW - 12, PHOS_DIM) + 3;
            }
            g.disableScissor();
            top = ty;
        }
        CashMachine.clip(g, leftPos, topPos, SX, top, SW, Math.max(0, bottom - top));
        Component terms = tr(menu.open ? "terms_open" : "terms", days, Bank.format(cost()), Bank.format(DepositBox.rentPerDay()));
        Component forbidden = tr("forbidden");
        if (CashMachine.height(font, terms, SW - 12) + 2 + CashMachine.height(font, forbidden, SW - 12) <= bottom - top) {
            int y2 = CashMachine.wrapUp(g, font, forbidden, SX + 6, bottom, SW - 12, PHOS_DIM);
            CashMachine.wrapUp(g, font, terms, SX + 6, y2 - 2, SW - 12, PHOS_DIM);
        } else { // no room for both: the terms from their first line, the forbidden-items note left out
            CashMachine.wrap(g, font, terms, SX + 6, top, SW - 12, PHOS_DIM);
        }
        g.disableScissor();
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
