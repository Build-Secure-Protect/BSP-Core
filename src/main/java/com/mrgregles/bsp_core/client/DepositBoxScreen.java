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
public class DepositBoxScreen extends AbstractContainerScreen<DepositBoxMenu> {
    private static final int BEZEL = 0xFFCFC8B8, BEZEL_DARK = 0xFF8F877A, BEZEL_LIGHT = 0xFFECE6D8, SCREEN = 0xFF061008, SCREEN_EDGE = 0xFF0D3B22, PHOS = 0x58F29A,
            PHOS_DIM = 0x3FB97A, AMBER = 0xF2B544, RED = 0xFF6B5C, KEY = 0xFFEAE4D5, KEY_EDGE = 0xFF9A9183, KEY_TEXT = 0x2B2823, KEY_LOCKED = 0x8F877A, SLOT_BG = 0xFF0C1A10, SLOT_LOCKED = 0xFF0A120C;
    private static final int SX = 10, SY = 10, SW = 206, KX = 226, KW = 64, KH = 20, KGAP = 4;
    private static final SimpleDateFormat DATE = new SimpleDateFormat("d MMM", Locale.ROOT);

    private record Key(String label, boolean enabled, Runnable action) {}

    private final List<Key> top = new ArrayList<>(), bottom = new ArrayList<>();
    private int days = 7, lastSerial = -1;
    private Component message = Component.empty();
    private int messageColour = AMBER;

    public DepositBoxScreen(DepositBoxMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = DepositBoxMenu.WIDTH;
        this.imageHeight = menu.open ? DepositBoxMenu.HEIGHT : DepositBoxMenu.LOCKED_HEIGHT;
        this.inventoryLabelX = DepositBoxMenu.INV_X;
        this.inventoryLabelY = DepositBoxMenu.INV_Y - 11;
    }

    private int screenHeight() {
        return menu.open ? 108 : 118;
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

    private void keys() {
        top.clear();
        bottom.clear();
        int max = BSPConfig.getOr(BSPConfig.BOX_MAX_DAYS, 365);
        DepositBox.State st = state();
        top.add(new Key("minus_days", days > 1, () -> days = Math.max(1, days - 7 < 1 ? 1 : days - 7)));
        top.add(new Key("plus_days", DepositBox.daysLeft(menu.account, System.currentTimeMillis()) + days + 7 <= max, () -> days += 7));
        String rent = st == DepositBox.State.EXPIRED ? "pay_open" : "rent";
        bottom.add(new Key(rent + "_pieces", menu.account.pieces >= cost(), () -> send(false, -1)));
        int tier = coinTier();
        bottom.add(new Key(rent + "_coins", tier >= 0, () -> send(false, coinTier())));
        if (st == DepositBox.State.OPEN) {
            bottom.add(new Key("add_slots", menu.account.boxSlots < DepositBox.SLOTS_MAX && menu.account.pieces >= DepositBox.slotStepPieces(), () -> send(true, -1)));
        }
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
        ky = y + SY + screenHeight() - KH;
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
            // the player's inventory, inset in the bezel
            for (int i = DepositBox.SLOTS_MAX; i < menu.slots.size(); i++) {
                Slot slot = menu.slots.get(i);
                g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, KEY_EDGE);
                g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xFFDAD3C3);
            }
        }
        int ky = y + SY;
        for (Key k : top) {
            key(g, x + KX, ky, k, mx, my);
            ky += KH + KGAP;
        }
        ky = y + SY + sh - KH;
        for (int i = bottom.size() - 1; i >= 0; i--) {
            key(g, x + KX, ky, bottom.get(i), mx, my);
            ky -= KH + KGAP;
        }
    }

    private void key(GuiGraphics g, int x, int y, Key k, int mx, int my) {
        CashMachine.key(g, font, x, y, KW, KH, tr(k.label, days).getString(), k.enabled, k.enabled && over(mx, my, x, y, KW, KH));
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        long now = System.currentTimeMillis();
        DepositBox.State st = state();
        g.drawString(font, "> " + tr("title").getString().toUpperCase(Locale.ROOT), SX + 6, SY + 6, PHOS, false);
        String right = switch (st) {
            case OPEN -> tr("until", DATE.format(new Date(menu.account.boxUntilMs)), DepositBox.daysLeft(menu.account, now)).getString();
            case EXPIRED -> tr("lapsed", DepositBox.lapsedDays(menu.account, now)).getString();
            default -> tr("not_rented").getString();
        };
        CashMachine.fitRight(g, font, right, SX + SW - 6, SY + 6, SW - 12 - font.width("> " + tr("title").getString()) - 4, st == DepositBox.State.EXPIRED ? RED : PHOS_DIM);
        g.fill(SX + 4, SY + 16, SX + SW - 4, SY + 17, SCREEN_EDGE);
        int ty = SY + 22;
        if (menu.open) {
            String slots = tr("slots", menu.account.boxSlots, DepositBox.SLOTS_MAX).getString();
            g.drawString(font, slots, SX + 6, ty, PHOS_DIM, false);
            ty = DepositBoxMenu.SLOT_Y + 38;
        } else {
            long cost = cost();
            String owed = st == DepositBox.State.EXPIRED ? tr("owed", Bank.format(cost), days, DepositBox.lapsedDays(menu.account, now)).getString() : tr("first", Bank.format(cost), days).getString();
            for (var line : font.split(Component.literal(owed), SW - 12)) {
                g.drawString(font, line, SX + 6, ty, st == DepositBox.State.EXPIRED ? AMBER : PHOS, false);
                ty += 9;
            }
            ty += 4;
            if (st == DepositBox.State.EXPIRED) {
                g.drawString(font, tr("kept", menu.account.boxSlots), SX + 6, ty, PHOS_DIM, false);
                ty += 12;
            }
        }
        String terms = tr("terms", days, Bank.format(cost()), Bank.format(DepositBox.rentPerDay())).getString();
        for (var line : font.split(Component.literal(terms), SW - 12)) {
            g.drawString(font, line, SX + 6, ty, PHOS_DIM, false);
            ty += 9;
        }
        CashMachine.wrap(g, font, tr("forbidden"), SX + 6, ty + 2, SW - 12, PHOS_DIM);
        if (!message.getString().isEmpty()) {
            List<net.minecraft.util.FormattedCharSequence> lines = font.split(message, SW - 12);
            int ly = SY + screenHeight() - 4 - lines.size() * 9;
            for (var line : lines) {
                g.drawString(font, line, SX + 6, ly, messageColour, false);
                ly += 9;
            }
        }
        if (menu.open) {
            g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, KEY_TEXT, false);
        }
    }
}
