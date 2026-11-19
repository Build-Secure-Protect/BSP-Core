package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bounty.Bounties;
import com.mrgregles.bsp_core.bounty.Bounty;
import com.mrgregles.bsp_core.bounty.BountyStore;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.network.BountyAdminActionPacket;
import com.mrgregles.bsp_core.network.BountyAdminPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The admin panel's BOUNTIES tab (layout A, owner's pick 2026-11-18): the bounty list on the left with ALL, ENDING (within a day)
 * and HISTORY filters and ADD BOUNTY; the picked bounty on the right with who placed it, its hunters and its notes (BLANK empties
 * them); along the bottom EXTEND +1 H, +1 D, +7 D, then REMOVE + REFUND, then REMOVE, NO REFUND (each removal asks for a second
 * click). ADD turns the right side into a form for a staff bounty, paid by the server. Moderators without
 * {@code bsp_core.admin.bounty} see everything with the keys greyed.
 */
class BountyAdminTab {
    interface Host {
        Font font();

        void addWidget(EditBox box);

        void send(BountyAdminActionPacket packet);
    }

    private static final int BG = 0xF010151C, SLOT = 0xFF0C0E12, DIM = 0xFF2A2F3A, VIO = 0xFFB58CFF, TQ = 0xFF19D3B0, BAD = 0xFFD63B2F, GOLD = 0xFFFFD23A,
            MUTED = 0xFF9AA3B5, TEXT = 0xFFE6EAF2, HOVER = 0xFF2A2140, GOOD = 0xFF8FE04A;
    private static final int LX = 10, LW = 152, DX = 172, DW = 162, ROW = 12, ROWS = 11, LIST_Y = 41, KEY_Y = 184, KEY_H = 14;
    private static final long H = Bounties.HOUR_MS, D = Bounties.DAY_MS, CONFIRM_MS = 4000, RESULT_MS = 6000;

    private final Host host;
    @Nullable
    private BountyAdminPacket data;
    private long offset;
    private int filter, scroll;
    @Nullable
    private String picked;
    private boolean adding, visible, coins;
    private int tier = CoinTier.DIAMOND.ordinal();
    private int armed = -1;
    private long armedUntil, resultUntil;
    private String result = "";
    private final List<EditBox> boxes = new ArrayList<>();
    private EditBox target, amount, days, bonus, within, notes;

    BountyAdminTab(Host host) {
        this.host = host;
    }

    void init(int x, int y) {
        String[] keep = boxes.isEmpty() ? new String[]{"", "", "7", "", "1", ""} : boxes.stream().map(EditBox::getValue).toArray(String[]::new);
        boxes.clear();
        target = box(x + DX + 52, y + 30, DW - 52, keep[0], "[A-Za-z0-9_]{0,16}");
        amount = box(x + DX + 52, y + 58, DW - 52, keep[1], "[0-9,]{0,16}");
        days = box(x + DX + 52, y + 72, 40, keep[2], "[0-9]{0,2}");
        bonus = box(x + DX + 52, y + 86, DW - 52, keep[3], "[0-9,]{0,16}");
        within = box(x + DX + 52, y + 100, 40, keep[4], "[0-9]{0,3}");
        notes = box(x + DX + 52, y + 114, DW - 52, keep[5], null);
        notes.setMaxLength(256);
        setVisible(visible);
    }

    private EditBox box(int x, int y, int w, String text, @Nullable String filter) {
        EditBox b = new EditBox(host.font(), x + 2, y + 2, w - 4, 9, Component.empty());
        b.setBordered(false);
        b.setTextColor(TEXT & 0xFFFFFF);
        b.setMaxLength(16);
        if (filter != null) {
            b.setFilter(s -> s.matches(filter));
        }
        b.setValue(text);
        boxes.add(b);
        host.addWidget(b);
        return b;
    }

    void setVisible(boolean on) {
        visible = on;
        for (EditBox b : boxes) {
            b.visible = on && adding;
            if (!b.visible) {
                b.setFocused(false);
            }
        }
    }

    void refresh() {
        host.send(BountyAdminActionPacket.of(BountyAdminActionPacket.REFRESH, "", 0));
    }

    void take(BountyAdminPacket p) {
        data = p;
        offset = p.now() - System.currentTimeMillis();
        if (!p.result().isEmpty()) {
            result = p.result();
            resultUntil = System.currentTimeMillis() + RESULT_MS;
            if (p.result().equals("ok") && adding) {
                adding = false;
                setVisible(visible);
            }
        }
    }

    private long now() {
        return System.currentTimeMillis() + offset;
    }

    private boolean canEdit() {
        return data != null && data.canEdit();
    }

    // ------------------------------------------------------------------ what the list shows

    private record Item(Bounty bounty, @Nullable BountyStore.Closed closed) {}

    private List<Item> items() {
        List<Item> out = new ArrayList<>();
        if (data == null) {
            return out;
        }
        if (filter == 2) {
            data.closed().forEach(c -> out.add(new Item(c.bounty(), c)));
            return out;
        }
        long now = now();
        for (Bounty b : data.open()) {
            if (filter == 1 && b.endsMs - now > D) {
                continue;
            }
            out.add(new Item(b, null));
        }
        if (filter == 1) {
            out.sort((a, b) -> Long.compare(a.bounty().endsMs, b.bounty().endsMs));
        }
        return out;
    }

    @Nullable
    private Item pickedItem() {
        for (Item i : items()) {
            if (i.bounty().id.equals(picked)) {
                return i;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ drawing

    private void key(GuiGraphics g, int x, int y, int w, int h, Component label, int colour, boolean on, boolean enabled, int mx, int my) {
        boolean hover = enabled && mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, on ? colour : enabled ? DIM : 0xFF1A1E26);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, on || hover ? HOVER : SLOT);
        String s = label.getString().toUpperCase(Locale.ROOT);
        Font font = host.font();
        float k = Math.min(0.75f, (w - 4) / (float) Math.max(1, font.width(s)));
        g.pose().pushPose();
        g.pose().translate(x + w / 2f - font.width(s) * k / 2f, y + h / 2f - 4 * k + 0.5f, 0);
        g.pose().scale(k, k, 1);
        g.drawString(font, s, 0, 0, (enabled ? colour : 0xFF4A5060) & 0xFFFFFF, false);
        g.pose().popPose();
    }

    private void small(GuiGraphics g, String s, float x, float y, int colour, float maxW) {
        Font font = host.font();
        float k = 0.75f, w = font.width(s) * k;
        if (w > maxW) {
            k *= maxW / w;
        }
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(k, k, 1);
        g.drawString(font, s, 0, 0, colour & 0xFFFFFF, false);
        g.pose().popPose();
    }

    private void smallRight(GuiGraphics g, String s, float right, float y, int colour, float maxW) {
        float w = Math.min(host.font().width(s) * 0.75f, maxW);
        small(g, s, right - w, y, colour, maxW);
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.bsp_core.admin.bounty." + key, args);
    }

    private static final String[] FILTERS = {"all", "ending", "history"};
    private static final int[][] FILTER_KEYS = {{LX, 26}, {LX + 28, 34}, {LX + 64, 42}};
    private static final int ADD_X = LX + 112, ADD_W = 40;

    void render(GuiGraphics g, int x, int y, int mx, int my) {
        if (data == null) {
            small(g, tr("loading").getString(), x + LX, y + 30, MUTED, 300);
            return;
        }
        for (int i = 0; i < FILTERS.length; i++) {
            key(g, x + FILTER_KEYS[i][0], y + 26, FILTER_KEYS[i][1], 11, tr("filter." + FILTERS[i]), VIO, filter == i && !adding, true, mx, my);
        }
        key(g, x + ADD_X, y + 26, ADD_W, 11, tr("add"), TQ, adding, canEdit(), mx, my);
        List<Item> list = items();
        scroll = Mth.clamp(scroll, 0, Math.max(0, list.size() - ROWS));
        long now = now();
        for (int i = 0; i < ROWS && scroll + i < list.size(); i++) {
            Item it = list.get(scroll + i);
            Bounty b = it.bounty();
            int ry = y + LIST_Y + i * ROW;
            boolean sel = b.id.equals(picked) && !adding, hover = mx >= x + LX && mx < x + LX + LW && my >= ry && my < ry + ROW - 1;
            g.fill(x + LX, ry, x + LX + LW, ry + ROW - 1, sel ? HOVER : hover ? 0xFF1C1828 : i % 2 == 0 ? SLOT : 0xFF141A23);
            PlayerFaceRenderer.draw(g, Minecraft.getInstance().getSkinManager().getInsecureSkinLocation(BountyBoardRenderer.profile(b.target, b.targetName)), x + LX + 2, ry + 1, 9);
            String rewardText = it.closed() == null ? BountyBoardScreen.reward(b.coinTier, b.amount) : "";
            float rw = Math.min(host.font().width(rewardText) * 0.75f, 50);
            small(g, b.targetName, x + LX + 13, ry + 2.5f, sel ? VIO : TEXT, it.closed() == null ? LW - 13 - 42 - rw - 3 : 64);
            if (it.closed() != null) {
                smallRight(g, tr("ending." + it.closed().ending().name().toLowerCase(Locale.ROOT)).getString(), x + LX + LW - 3, ry + 2.5f, endingColour(it.closed().ending()), 70);
            } else {
                smallRight(g, BountyBoardScreen.reward(b.coinTier, b.amount), x + LX + LW - 42, ry + 2.5f, GOLD, 50);
                smallRight(g, Bounties.span(b.endsMs - now), x + LX + LW - 3, ry + 2.5f, b.endsMs - now < D ? BAD : MUTED, 38);
            }
        }
        if (list.isEmpty()) {
            small(g, tr(filter == 2 ? "empty_history" : "empty").getString(), x + LX + 4, y + LIST_Y + 3, MUTED, LW - 8);
        }
        Item shownItem = adding ? null : pickedItem();
        if (shownItem == null || shownItem.closed() != null) { // the EXTEND keys' label takes this row while an open bounty is picked
            small(g, tr("footer", data.open().size(), data.closed().size()).getString(), x + LX, y + 176, MUTED, LW);
        }
        if (adding) {
            addForm(g, x, y, mx, my);
        } else {
            Item it = pickedItem();
            if (it != null) {
                detail(g, x, y, it, mx, my);
            } else {
                small(g, tr("pick").getString(), x + DX, y + 30, MUTED, DW);
            }
        }
        if (!canEdit()) {
            CashMachine.smallWrap(g, host.font(), tr("view_only"), x + DX, y + 168, DW, GOLD & 0xFFFFFF);
        } else if (System.currentTimeMillis() < resultUntil && !result.isEmpty()) {
            small(g, BountyBoardScreen.resultText(result).getString(), x + DX, y + 172, result.equals("ok") ? GOOD : GOLD, DW);
        }
    }

    private static int endingColour(BountyStore.Ending e) {
        return switch (e) {
            case CLAIMED -> TQ;
            case REMOVED_NO_REFUND -> BAD;
            case REMOVED, TARGET_STAFF -> VIO;
            default -> MUTED;
        };
    }

    private int line(GuiGraphics g, int x, int y, String label, String value, int colour) {
        small(g, tr(label).getString(), x + DX, y, MUTED, 52);
        smallRight(g, value, x + DX + DW, y, colour, DW - 56);
        return y + 9;
    }

    private void detail(GuiGraphics g, int x, int y, Item it, int mx, int my) {
        Bounty b = it.bounty();
        long now = now();
        PlayerFaceRenderer.draw(g, Minecraft.getInstance().getSkinManager().getInsecureSkinLocation(BountyBoardRenderer.profile(b.target, b.targetName)), x + DX, y + 28, 16);
        CashMachine.fit(g, host.font(), b.targetName, x + DX + 20, y + 28, DW - 20, TEXT & 0xFFFFFF);
        small(g, tr("on_server", b.server).getString(), x + DX + 20, y + 38, MUTED, DW - 20);
        int ly = y + 50;
        ly = line(g, x, ly, "reward", BountyBoardScreen.describe(b.coinTier, b.amount), GOLD);
        if (b.bonus > 0) {
            ly = line(g, x, ly, "bonus", "+" + BountyBoardScreen.describe(b.coinTier, b.bonus) + (now < b.bonusEndsMs ? " · " + Bounties.span(b.bonusEndsMs - now) : ""), GOLD);
        }
        if (it.closed() == null) {
            ly = line(g, x, ly, "ends", Bounties.span(b.endsMs - now), TEXT);
        } else {
            BountyStore.Closed c = it.closed();
            String how = tr("ending." + c.ending().name().toLowerCase(Locale.ROOT)).getString() + (c.ending() == BountyStore.Ending.CLAIMED ? " · " + c.hunterName()
                    : c.by().isEmpty() ? "" : " · " + c.by());
            ly = line(g, x, ly, "closed", how + ", " + Component.translatable("gui.bsp_core.bounty.ago", Bounties.span(now - c.closedMs())).getString(), endingColour(c.ending()));
        }
        ly = line(g, x, ly, "placed_by", b.placerName + ", " + Component.translatable("gui.bsp_core.bounty.ago", Bounties.span(now - b.placedMs)).getString(), VIO);
        String hunters = b.hunters().isEmpty() ? tr("nobody").getString() : String.join(", ", b.hunters().stream().map(Bounty.Hunter::name).toList());
        ly = line(g, x, ly, "hunters", hunters, TEXT);
        if (b.fee > 0) {
            ly = line(g, x, ly, "fee", Bank.format(b.fee), MUTED);
        }
        // the notes, with BLANK
        int ny = ly + 3, nh = y + 166 - ny;
        g.fill(x + DX, ny, x + DX + DW, ny + nh, DIM);
        g.fill(x + DX + 1, ny + 1, x + DX + DW - 1, ny + nh - 1, SLOT);
        small(g, tr("notes").getString(), x + DX + 3, ny + 3, MUTED, 60);
        boolean open = it.closed() == null;
        if (open) {
            key(g, x + DX + DW - 34, ny + 2, 31, 9, tr("blank"), BAD, false, canEdit() && !b.notes.isEmpty(), mx, my);
        }
        ScreenFit.scissor(g, x + DX + 1, ny + 12, x + DX + DW - 1, ny + nh - 1);
        CashMachine.smallWrap(g, host.font(), b.notes.isEmpty() ? Component.translatable("gui.bsp_core.bounty.no_notes") : Component.literal(b.notes), x + DX + 3, ny + 13,
                DW - 6, (b.notes.isEmpty() ? MUTED : TEXT) & 0xFFFFFF);
        g.disableScissor();
        if (open) {
            boolean e = canEdit();
            long[] adds = {H, D, 7 * D};
            String[] names = {"+1 H", "+1 D", "+7 D"};
            small(g, tr("extend").getString(), x + LX + 30, y + KEY_Y - 8, MUTED, 60);
            for (int i = 0; i < 3; i++) {
                key(g, x + LX + i * 32, y + KEY_Y, 30, KEY_H, Component.literal(names[i]), TQ, false, e && b.endsMs < now + data.maxDays() * D, mx, my);
            }
            boolean sure = System.currentTimeMillis() < armedUntil;
            key(g, x + 124, y + KEY_Y, 92, KEY_H, sure && armed == 0 ? tr("sure") : tr("remove_refund"), VIO, sure && armed == 0, e, mx, my);
            key(g, x + 232, y + KEY_Y, 102, KEY_H, sure && armed == 1 ? tr("sure") : tr("remove_no_refund"), BAD, sure && armed == 1, e, mx, my);
        }
    }

    private void addForm(GuiGraphics g, int x, int y, int mx, int my) {
        int[][] fields = {{30, 0}, {58, 1}, {72, 2}, {86, 3}, {100, 4}, {114, 5}};
        String[] labels = {"form.target", "form.amount", "form.days", "form.bonus", "form.within", "form.notes"};
        for (int i = 0; i < fields.length; i++) {
            int fy = y + fields[i][0];
            small(g, tr(labels[i]).getString(), x + DX, fy + 3, MUTED, 50);
            EditBox b = boxes.get(fields[i][1]);
            g.fill(b.getX() - 2, fy, b.getX() - 2 + b.getWidth() + 4, fy + 12, DIM);
            g.fill(b.getX() - 1, fy + 1, b.getX() + b.getWidth() + 1, fy + 11, SLOT);
        }
        small(g, tr("form.kind").getString(), x + DX, y + 47, MUTED, 50);
        key(g, x + DX + 52, y + 44, 34, 11, tr("form.pieces"), TQ, !coins, true, mx, my);
        key(g, x + DX + 88, y + 44, 34, 11, tr("form.coins"), TQ, coins, true, mx, my);
        if (coins) {
            key(g, x + DX + 124, y + 44, 38, 11, Component.literal(Bank.tierName(CoinTier.values()[tier])), GOLD, false, true, mx, my);
        }
        small(g, tr("form.hours_hint").getString(), x + DX + 96, y + 103, MUTED, 66);
        small(g, tr("form.days_hint", data == null ? 30 : data.maxDays()).getString(), x + DX + 96, y + 75, MUTED, 66);
        key(g, x + DX, y + 132, DW, 14, tr("form.add"), TQ, false, canEdit(), mx, my);
        CashMachine.smallWrap(g, host.font(), tr("form.paid_by_server"), x + DX, y + 150, DW, MUTED & 0xFFFFFF);
        key(g, x + LX, y + KEY_Y, 70, KEY_H, tr("form.cancel"), VIO, false, true, mx, my);
    }

    // ------------------------------------------------------------------ input

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static long number(EditBox b) {
        try {
            String s = b.getValue().replace(",", "").trim();
            return s.isEmpty() ? 0 : Long.parseLong(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    boolean mouseClicked(double mx, double my, int x, int y) {
        if (data == null) {
            return false;
        }
        for (int i = 0; i < FILTERS.length; i++) {
            if (over(mx, my, x + FILTER_KEYS[i][0], y + 26, FILTER_KEYS[i][1], 11)) {
                filter = i;
                scroll = 0;
                adding = false;
                setVisible(visible);
                return true;
            }
        }
        if (over(mx, my, x + ADD_X, y + 26, ADD_W, 11) && canEdit()) {
            adding = true;
            setVisible(visible);
            return true;
        }
        List<Item> list = items();
        for (int i = 0; i < ROWS && scroll + i < list.size(); i++) {
            if (over(mx, my, x + LX, y + LIST_Y + i * ROW, LW, ROW - 1)) {
                picked = list.get(scroll + i).bounty().id;
                adding = false;
                armed = -1;
                setVisible(visible);
                return true;
            }
        }
        if (adding) {
            return addClick(mx, my, x, y);
        }
        Item it = pickedItem();
        if (it == null || it.closed() != null || !canEdit()) {
            return false;
        }
        Bounty b = it.bounty();
        long[] adds = {H, D, 7 * D};
        for (int i = 0; i < 3; i++) {
            if (over(mx, my, x + LX + i * 32, y + KEY_Y, 30, KEY_H)) {
                host.send(BountyAdminActionPacket.of(BountyAdminActionPacket.EXTEND, b.id, adds[i]));
                return true;
            }
        }
        boolean sure = System.currentTimeMillis() < armedUntil;
        for (int k = 0; k < 2; k++) {
            if (over(mx, my, x + (k == 0 ? 124 : 232), y + KEY_Y, k == 0 ? 92 : 102, KEY_H)) {
                if (sure && armed == k) {
                    host.send(BountyAdminActionPacket.of(k == 0 ? BountyAdminActionPacket.REMOVE : BountyAdminActionPacket.REMOVE_NO_REFUND, b.id, 0));
                    armed = -1;
                    armedUntil = 0;
                } else {
                    armed = k;
                    armedUntil = System.currentTimeMillis() + CONFIRM_MS;
                }
                return true;
            }
        }
        int ny = noteTop(x, y, it);
        if (over(mx, my, x + DX + DW - 34, ny + 2, 31, 9) && !b.notes.isEmpty()) {
            host.send(BountyAdminActionPacket.of(BountyAdminActionPacket.BLANK, b.id, 0));
            return true;
        }
        return false;
    }

    /** Where the notes box starts for this bounty (it follows the lines above it). */
    private int noteTop(int x, int y, Item it) {
        Bounty b = it.bounty();
        int lines = 4 + (b.bonus > 0 ? 1 : 0) + (b.fee > 0 ? 1 : 0);
        return y + 50 + lines * 9 + 3;
    }

    private boolean addClick(double mx, double my, int x, int y) {
        if (over(mx, my, x + DX + 52, y + 44, 34, 11)) {
            coins = false;
            return true;
        }
        if (over(mx, my, x + DX + 88, y + 44, 34, 11)) {
            coins = true;
            return true;
        }
        if (coins && over(mx, my, x + DX + 124, y + 44, 38, 11)) {
            tier = (tier + 1) % CoinTier.values().length;
            return true;
        }
        if (over(mx, my, x + LX, y + KEY_Y, 70, KEY_H)) {
            adding = false;
            setVisible(visible);
            return true;
        }
        if (over(mx, my, x + DX, y + 132, DW, 14) && canEdit()) {
            long b = Math.max(0, number(bonus));
            host.send(new BountyAdminActionPacket(BountyAdminActionPacket.ADD, "", 0, target.getValue().trim(), coins ? tier : Bounty.PIECES, number(amount), b,
                    b > 0 ? number(within) * H : 0, number(days) * D, notes.getValue()));
            return true;
        }
        return false;
    }

    boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, scroll - (int) Math.signum(delta));
        return true;
    }

    boolean typing() {
        return boxes.stream().anyMatch(b -> b.visible && b.isFocused());
    }
}
