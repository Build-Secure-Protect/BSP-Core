package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.api.BspBank;
import com.mrgregles.bsp_core.network.AdminBankAskPacket;
import com.mrgregles.bsp_core.network.AdminBankDataPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

import static com.mrgregles.bsp_core.client.AdminBankScreen.BAD;
import static com.mrgregles.bsp_core.client.AdminBankScreen.DIM;
import static com.mrgregles.bsp_core.client.AdminBankScreen.GOLD;
import static com.mrgregles.bsp_core.client.AdminBankScreen.GOOD;
import static com.mrgregles.bsp_core.client.AdminBankScreen.HOVER;
import static com.mrgregles.bsp_core.client.AdminBankScreen.MUTED;
import static com.mrgregles.bsp_core.client.AdminBankScreen.SLOT_BG;
import static com.mrgregles.bsp_core.client.AdminBankScreen.TEXT;
import static com.mrgregles.bsp_core.client.AdminBankScreen.TQ;
import static com.mrgregles.bsp_core.client.AdminBankScreen.VIO;
import static com.mrgregles.bsp_core.client.AdminBankScreen.over;
import static com.mrgregles.bsp_core.client.AdminBankScreen.tr;

/**
 * EDIT TRANSACTIONS, the Bank &amp; Market page's larger log (layout C): every row the bank keeps on the left; the picked row in full on
 * the right with what a refund puts back or takes away. FULL REFUND refunds all that is left of the row; REFUND AMOUNT the amounts in
 * the boxes. A coin conversion has two switches, "give the coins back" and "take back the pieces it made"; a payment one, "also take it
 * from" the other player. A second click within four seconds confirms. Greyed for staff who may only look.
 */
public class AdminBankEditScreen extends Screen implements ScreenFit.Fitted {
    private static final int W = 460, H = 260, LX = 10, LW = 296, DX = 314, DW = 136, TOP = 26, ROW = 10, ROWS = 21;
    private static final long CONFIRM_MS = 4000, RESULT_MS = 6000;
    private static final String FULL = "full", AMOUNT = "amount";

    private final AdminBankScreen parent;
    private int scroll;
    private long selected = -1;
    private boolean coinsOn = true, piecesOn = true, other;
    @Nullable
    private String armed;
    private long armedUntil, resultUntil;
    @Nullable
    private EditBox piecesBox, coinsBox;

    AdminBankEditScreen(AdminBankScreen parent) {
        super(tr("edit"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        AdminBankClient.listen(this::take);
        String keepPieces = piecesBox == null ? null : piecesBox.getValue(), keepCoins = coinsBox == null ? null : coinsBox.getValue();
        piecesBox = box(160);
        coinsBox = box(182);
        if (keepPieces != null) { // a resize lays the screen out again: what was typed stays
            piecesBox.setValue(keepPieces);
            coinsBox.setValue(keepCoins);
        }
        armed = null; // and nothing armed before it goes out after it
        pick(selected);
    }

    private EditBox box(int dy) {
        EditBox b = new EditBox(font, left() + DX + 70, top() + dy + 1, 60, 9, Component.empty());
        b.setMaxLength(12);
        b.setBordered(false);
        b.setFilter(s -> s.matches("\\d{0,12}"));
        b.visible = false;
        addRenderableWidget(b);
        return b;
    }

    @Override
    public void removed() {
        AdminBankClient.listen(null);
        super.removed();
    }

    /** A page arrived (after a refund): the Bank &amp; Market page takes it too; the picked row stays picked. */
    private void take() {
        AdminBankDataPacket latest = AdminBankClient.latest();
        if (latest == null || !latest.player().equals(parent.player())) {
            return; // another player's page: not ours
        }
        parent.take();
        resultUntil = !latest.result().isEmpty() ? System.currentTimeMillis() + RESULT_MS : 0;
        scroll = Mth.clamp(scroll, 0, Math.max(0, latest.rows().size() - ROWS));
        armed = null;
        pick(selected);
    }

    @Nullable
    private AdminBankDataPacket page() {
        return parent.page();
    }

    @Nullable
    private BspBank.Row row() {
        AdminBankDataPacket p = page();
        if (p == null) {
            return null;
        }
        for (BspBank.Row r : p.rows()) {
            if (r.id() == selected) {
                return r;
            }
        }
        return null;
    }

    /** Picks a row: the boxes start at what is left of it, the switches at their defaults (a payment's "also take it from" off). */
    private void pick(long id) {
        boolean changed = id != selected;
        selected = id;
        BspBank.Row r = row();
        boolean edit = canEdit();
        if (piecesBox != null) {
            piecesBox.visible = r != null && r.pieces() != 0 && r.piecesLeft() > 0 && edit;
            if (r != null && (changed || piecesBox.getValue().isEmpty() || parseOr(piecesBox, 0) > r.piecesLeft())) {
                piecesBox.setValue(Long.toString(r.piecesLeft()));
            }
        }
        if (coinsBox != null) {
            coinsBox.visible = r != null && r.coinCount() != 0 && r.coinTier() >= 0 && r.coinsLeft() > 0 && edit;
            if (r != null && (changed || coinsBox.getValue().isEmpty() || parseOr(coinsBox, 0) > r.coinsLeft())) {
                coinsBox.setValue(Integer.toString(r.coinsLeft()));
            }
        }
        if (changed) {
            coinsOn = true;
            piecesOn = true;
            other = false;
            armed = null;
        }
    }

    private static long parseOr(EditBox b, long or) {
        try {
            return b.getValue().isEmpty() ? or : Long.parseLong(b.getValue());
        } catch (NumberFormatException e) {
            return or;
        }
    }

    private boolean canEdit() {
        AdminBankDataPacket p = page();
        return p != null && p.canEdit() && p.reached();
    }

    private boolean hasCoins(BspBank.Row r) {
        return r.coinCount() != 0 && r.coinTier() >= 0;
    }

    /** What REFUND AMOUNT would send for the row: pieces and coins, each 0 when its switch is off. */
    private long[] asked(BspBank.Row r, boolean full) {
        long p = r.pieces() == 0 || !piecesOn ? 0 : full ? r.piecesLeft() : Math.min(r.piecesLeft(), piecesBox == null ? 0 : parseOr(piecesBox, 0));
        long c = !hasCoins(r) || !coinsOn ? 0 : full ? r.coinsLeft() : Math.min(r.coinsLeft(), coinsBox == null ? 0 : parseOr(coinsBox, 0));
        return new long[] {p, c};
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

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
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

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) { // the amount boxes first, so a click into one focuses it
            return true;
        }
        int x = left(), y = top();
        if (over(mx, my, x + 10, y + 6, 58, 13)) {
            onClose();
            return true;
        }
        AdminBankDataPacket p = page();
        if (p == null) {
            return false;
        }
        for (int i = 0; i < ROWS && i + scroll < p.rows().size(); i++) {
            if (over(mx, my, x + LX + 1, rowY(y, i), LW - 2, ROW)) {
                setFocused(null);
                if (piecesBox != null) {
                    piecesBox.setFocused(false);
                    coinsBox.setFocused(false);
                }
                pick(p.rows().get(i + scroll).id());
                return true;
            }
        }
        BspBank.Row r = row();
        if (r == null || !canEdit()) {
            return false;
        }
        if (hasCoins(r) && r.pieces() != 0) {
            if (over(mx, my, x + DX + 4, y + 112, DW - 8, 9)) {
                coinsOn = !coinsOn;
                return true;
            }
            if (over(mx, my, x + DX + 4, y + 122, DW - 8, 9)) {
                piecesOn = !piecesOn;
                return true;
            }
        }
        if (r.payment() && over(mx, my, x + DX + 4, y + 112, DW - 8, 18)) {
            other = !other;
            return true;
        }
        long[] full = asked(r, true), part = asked(r, false);
        if (over(mx, my, x + DX + 4, y + 202, DW - 8, 14) && (full[0] > 0 || full[1] > 0)) {
            if (confirmed(armKey(FULL, r, full))) {
                send(r, full);
            }
            return true;
        }
        if (over(mx, my, x + DX + 4, y + 220, DW - 8, 14) && (part[0] > 0 || part[1] > 0)) {
            if (confirmed(armKey(AMOUNT, r, part))) {
                send(r, part);
            }
            return true;
        }
        return false;
    }

    /** What a second click confirms: the button, the row and exactly what would be sent, so a change in between needs a fresh confirm. */
    private String armKey(String button, BspBank.Row r, long[] amounts) {
        return button + r.id() + ":" + amounts[0] + ":" + amounts[1] + ":" + (other && r.payment());
    }

    private void send(BspBank.Row r, long[] amounts) {
        parent.send(new AdminBankAskPacket(AdminBankAskPacket.Action.REFUND, parent.player(), r.id(), amounts[0], (int) amounts[1], other && r.payment() && amounts[0] > 0, ""));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        AdminBankDataPacket p = page();
        if (p != null) {
            scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, p.rows().size() - ROWS));
        }
        return true;
    }

    private static int rowY(int y, int i) {
        return y + TOP + 12 + i * ROW;
    }

    // ------------------------------------------------------------------ drawing

    private void small(GuiGraphics g, Component text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private void smallFit(GuiGraphics g, String text, int x, int y, int w, int colour) {
        small(g, Component.literal(CashMachine.cut(font, text, Math.round(w / 0.75f))), x, y, colour);
    }

    private void smallRight(GuiGraphics g, String text, int right, int y, int colour) {
        small(g, Component.literal(text), right - Math.round(font.width(text) * 0.75f), y, colour);
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, Component label, boolean enabled, int colour, int mouseX, int mouseY) {
        boolean hover = enabled && over(mouseX, mouseY, x, y, w, h);
        g.fill(x, y, x + w, y + h, enabled ? colour : DIM);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? HOVER : SLOT_BG);
        if (h < 14) {
            small(g, label, x + w / 2 - Math.round(font.width(label) * 0.375f), y + (h - 6) / 2, enabled ? colour & 0xFFFFFF : 0x6B7385);
        } else {
            g.drawString(font, label, x + w / 2 - font.width(label) / 2, y + (h - 8) / 2, enabled ? colour & 0xFFFFFF : 0x6B7385, false);
        }
    }

    private void box(GuiGraphics g, int x, int y, int w, int h, int edge) {
        g.fill(x, y, x + w, y + h, edge);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, SLOT_BG);
    }

    /** A switch: a small square, filled when on, and its label. */
    private void toggle(GuiGraphics g, int x, int y, int w, boolean on, Component label, boolean enabled) {
        g.fill(x, y + 1, x + 7, y + 8, enabled ? TQ : DIM);
        g.fill(x + 1, y + 2, x + 6, y + 7, on ? (enabled ? TQ : DIM) : SLOT_BG);
        smallFit(g, label.getString(), x + 10, y + 2, w - 10, enabled ? TEXT : 0x6B7385);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = left(), y = top();
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, VIO);
        g.fill(x, y, x + W, y + H, AdminBankScreen.BG);
        button(g, x + 10, y + 6, 58, 13, tr("edit_back"), true, DIM | 0xFF000000, mouseX, mouseY);
        AdminBankDataPacket p = page();
        if (p == null) {
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        g.drawString(font, CashMachine.cut(font, p.name(), 140), x + 74, y + 9, VIO & 0xFFFFFF, false);
        String pieces = tr("edit_pieces", String.format(Locale.ROOT, "%,d", p.pieces())).getString();
        smallRight(g, pieces, x + W - 10, y + 11, GOLD & 0xFFFFFF);
        if (!p.canEdit()) {
            small(g, tr("heading_view"), x + 74 + Math.min(140, font.width(p.name())) + 6, y + 11, GOLD & 0xFFFFFF);
        }

        // the list
        int[] col = {LX + 3, LX + 56, LX + 98, LX + 206, LX + 256};
        small(g, tr("col.when"), x + col[0], y + TOP + 2, MUTED);
        small(g, tr("col.type"), x + col[1], y + TOP + 2, MUTED);
        small(g, tr("col.what"), x + col[2], y + TOP + 2, MUTED);
        smallRight(g, tr("col.pieces").getString(), x + col[4] - 4, y + TOP + 2, MUTED);
        smallRight(g, tr("col.coins").getString(), x + LX + LW - 4, y + TOP + 2, MUTED);
        box(g, x + LX, y + TOP + 10, LW, ROWS * ROW + 4, DIM);
        List<Component> tip = null;
        for (int i = 0; i < ROWS && i + scroll < p.rows().size(); i++) {
            BspBank.Row r = p.rows().get(i + scroll);
            int ry = rowY(y, i);
            boolean on = r.id() == selected, hover = over(mouseX, mouseY, x + LX + 1, ry, LW - 2, ROW);
            if (on || hover) {
                g.fill(x + LX + 1, ry, x + LX + LW - 1, ry + ROW, on ? HOVER : 0xFF1A1F29);
            }
            if (hover) {
                tip = AdminBankScreen.rowTip(r);
            }
            boolean done = r.piecesLeft() == 0 && r.coinsLeft() == 0 && (r.pieces() != 0 || r.coinCount() != 0);
            int fade = done ? 0x6B7385 : -1;
            small(g, Component.literal(AdminBankScreen.when(r.ms())), x + col[0], ry + 2, fade >= 0 ? fade : MUTED);
            small(g, Component.literal(r.type()), x + col[1], ry + 2, fade >= 0 ? fade : AdminBankScreen.typeColour(r.type()));
            int piecesW = r.pieces() == 0 ? 0 : Math.round(font.width(AdminBankScreen.signed(r.pieces())) * 0.75f);
            int whatW = Math.min(col[3] - col[2] - 4, col[4] - 4 - piecesW - 4 - col[2]); // a huge figure pushes WHAT back, never over it
            smallFit(g, r.detail().isEmpty() ? r.with() : r.detail(), x + col[2], ry + 2, whatW, fade >= 0 ? fade : TEXT);
            if (r.pieces() != 0) {
                smallRight(g, AdminBankScreen.signed(r.pieces()), x + col[4] - 4, ry + 2, fade >= 0 ? fade : (r.pieces() > 0 ? GOOD : BAD) & 0xFFFFFF);
            }
            if (r.coinCount() != 0) {
                smallRight(g, AdminBankScreen.signed(r.coinCount()) + " " + AdminBankScreen.tierName(r.coinTier()).charAt(0), x + LX + LW - 4, ry + 2,
                        fade >= 0 ? fade : (r.coinCount() > 0 ? GOOD : BAD) & 0xFFFFFF);
            }
        }
        if (p.rows().size() > ROWS) {
            int track = ROWS * ROW, knob = Math.max(8, track * ROWS / p.rows().size()), ky = (track - knob) * scroll / (p.rows().size() - ROWS);
            g.fill(x + LX + LW - 3, y + TOP + 12 + ky, x + LX + LW - 1, y + TOP + 12 + ky + knob, DIM);
        }

        // the picked row
        box(g, x + DX, y + TOP, DW, H - TOP - 10, DIM);
        BspBank.Row r = row();
        if (r == null) {
            CashMachine.smallWrap(g, font, tr("pick_row"), x + DX + 4, y + TOP + 6, DW - 8, MUTED);
        } else {
            detail(g, x, y, r, mouseX, mouseY);
        }
        if (System.currentTimeMillis() < resultUntil && !p.result().isEmpty()) {
            smallFit(g, tr("result." + p.result()).getString(), x + LX, y + H - 9, W - 2 * LX, (p.result().equals("ok") ? GOOD : BAD) & 0xFFFFFF);
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (tip != null) {
            g.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
    }

    private void detail(GuiGraphics g, int x, int y, BspBank.Row r, int mouseX, int mouseY) {
        int dx = x + DX + 4, w = DW - 8;
        g.drawString(font, r.type(), dx, y + TOP + 5, AdminBankScreen.typeColour(r.type()), false);
        int ty = y + TOP + 17; // one line each: the list's tooltip has the whole of a long detail
        smallFit(g, r.detail().isEmpty() ? r.with() : r.detail(), dx, ty, w, TEXT);
        smallFit(g, AdminBankScreen.when(r.ms()) + ", " + r.serverId(), dx, ty + 9, w, MUTED);
        if (!r.with().isEmpty() && !r.detail().isEmpty()) {
            smallFit(g, tr("tip.with", r.with()).getString(), dx, ty + 18, w, MUTED);
        }
        small(g, tr("left"), dx, y + 84, MUTED);
        String left = (r.pieces() != 0 ? String.format(Locale.ROOT, "%,d", r.piecesLeft()) + tr("unit_pieces").getString() : "")
                + (r.pieces() != 0 && hasCoins(r) ? ", " : "") + (hasCoins(r) ? r.coinsLeft() + " " + AdminBankScreen.tierName(r.coinTier()) : "");
        smallFit(g, left.isEmpty() ? "-" : left, dx, y + 92, w, TEXT);
        if (r.refundedPieces() > 0 || r.refundedCoins() > 0) {
            smallFit(g, tr("done_so_far", String.format(Locale.ROOT, "%,d", r.refundedPieces()), r.refundedCoins()).getString(), dx, y + 100, w, GOOD & 0xFFFFFF);
        }
        boolean edit = canEdit();
        boolean nothingLeft = r.piecesLeft() == 0 && r.coinsLeft() == 0;
        if (nothingLeft) {
            CashMachine.smallWrap(g, font, tr(r.pieces() == 0 && r.coinCount() == 0 ? "nothing_moved" : "all_refunded"), dx, y + 112, w, GOOD & 0xFFFFFF);
            return;
        }
        // the switches
        if (hasCoins(r) && r.pieces() != 0) {
            toggle(g, dx, y + 112, w, coinsOn, tr(r.coinCount() < 0 ? "switch.coins_back" : "switch.coins_take"), edit);
            toggle(g, dx, y + 122, w, piecesOn, tr(r.pieces() > 0 ? "switch.pieces_take" : "switch.pieces_back"), edit);
        }
        if (r.payment()) {
            // a payment has no coins, so its one switch takes the first line, with the other player's name under it
            toggle(g, dx, y + 112, w, other, tr(r.type().equals("PAID") ? "switch.other_take" : "switch.other_give"), edit);
            smallFit(g, r.with(), dx + 10, y + 121, w - 10, edit ? VIO & 0xFFFFFF : 0x6B7385);
        }
        // the amounts
        if (r.pieces() != 0) {
            smallFit(g, tr(r.pieces() < 0 ? "amount.pieces_back" : "amount.pieces_take").getString(), dx, y + 162, 64, piecesOn ? TEXT : 0x6B7385);
            if (edit) {
                box(g, x + DX + 68, y + 159, 64, 12, piecesBox != null && piecesBox.isFocused() ? GOLD : DIM);
            }
        }
        if (hasCoins(r)) {
            smallFit(g, tr(r.coinCount() < 0 ? "amount.coins_back" : "amount.coins_take").getString(), dx, y + 184, 64, coinsOn ? TEXT : 0x6B7385);
            if (edit) {
                box(g, x + DX + 68, y + 181, 64, 12, coinsBox != null && coinsBox.isFocused() ? GOLD : DIM);
            }
        }
        long[] full = asked(r, true), part = asked(r, false);
        button(g, dx, y + 202, w, 14, tr(arming(armKey(FULL, r, full)) ? "sure" : "full_refund"), edit && (full[0] > 0 || full[1] > 0), GOLD, mouseX, mouseY);
        button(g, dx, y + 220, w, 14, tr(arming(armKey(AMOUNT, r, part)) ? "sure" : "refund_amount"), edit && (part[0] > 0 || part[1] > 0), TQ, mouseX, mouseY);
        // what the amount refund does
        long dp = -Long.signum(r.pieces()) * part[0], dc = -Integer.signum(r.coinCount()) * part[1];
        String does = (dc != 0 ? AdminBankScreen.signed(dc) + " " + AdminBankScreen.tierName(r.coinTier()) : "") + (dc != 0 && dp != 0 ? ", " : "")
                + (dp != 0 ? AdminBankScreen.signed(dp) + tr("unit_pieces").getString() : "") + (other && r.payment() && dp != 0 ? tr("and_other").getString() : "");
        if (!does.isEmpty()) {
            smallFit(g, does, dx, y + 237, w, (dp > 0 || dc > 0 ? GOOD : BAD) & 0xFFFFFF);
        }
    }
}
