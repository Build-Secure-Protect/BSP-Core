package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bounty.Bounties;
import com.mrgregles.bsp_core.bounty.Bounty;
import com.mrgregles.bsp_core.bounty.BountyScreens;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.BountyPlacePacket;
import com.mrgregles.bsp_core.network.BountyPlayersPacket;
import com.mrgregles.bsp_core.network.BountyViewPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The PLACE A BOUNTY page of the Bounty Board screen (look A): four numbered blocks on one page. 1 WHO: a search box over every
 * player the bank knows, online first, each marked when a bounty may not go on them. 2 REWARD: pieces or coins (a coin tier and a
 * count). 3 NOTES: only hunters who accept read them. 4 TIME AND BONUS: the end (quick keys or typed days and hours, at most the
 * server's limit) and an optional bonus paid only if the kill comes within its own window. Under it what the player pays now:
 * reward, bonus and the fee, and PLACE BOUNTY, or why it cannot be placed yet. The server checks everything again.
 */
public class BountyPlacePage {
    private static final int LX = 12, LW = 152, RX = 172, RW = 136, ROWS = 5, ROW_H = 12, LIST_Y = 51;
    private static final long H = Bounties.HOUR_MS, D = Bounties.DAY_MS;
    private static final long[] END_KEYS = {H, D, 3 * D, 7 * D, 30 * D}, WITHIN_KEYS = {6 * H, D, 3 * D};
    private static final String[] END_NAMES = {"1 H", "1 D", "3 D", "7 D", "30 D"}, WITHIN_NAMES = {"6 H", "1 D", "3 D"};

    private final BountyBoardScreen screen;
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private EditBox who, amount, days, hours, bonus, withinDays, withinHours;
    private MultiLineEditBox notes;
    private List<BountyScreens.Pick> picks = List.of();
    @Nullable
    private BountyScreens.Pick target;
    private boolean coins;
    private int tier = CoinTier.DIAMOND.ordinal(), listScroll;
    private String asked = "\u0000";
    private long askAt;
    private boolean visible;

    BountyPlacePage(BountyBoardScreen screen) {
        this.screen = screen;
    }

    /** The player list arrived (for the latest search only). */
    public static void takePlayers(BountyPlayersPacket msg) {
        if (Minecraft.getInstance().screen instanceof BountyBoardScreen s && s.placePage() != null) {
            s.placePage().players(msg);
        }
    }

    void players(BountyPlayersPacket msg) {
        if (who != null && msg.search().equalsIgnoreCase(who.getValue().trim())) {
            picks = msg.players();
            listScroll = 0;
        }
    }

    private EditBox box(int x, int y, int w, String text, String filter) {
        EditBox b = new EditBox(Minecraft.getInstance().font, screen.left() + x + 2, screen.top() + y + 2, w - 4, 9, Component.empty());
        b.setBordered(false);
        b.setTextColor(BountyBoardScreen.PAPER & 0xFFFFFF);
        if (filter != null) {
            b.setFilter(s -> s.matches(filter));
        }
        b.setValue(text);
        widgets.add(b);
        return b;
    }

    /** Builds the boxes (again, after a resize), keeping what was typed. */
    void init(Consumer<AbstractWidget> add) {
        String w = who == null ? "" : who.getValue(), a = amount == null ? "" : amount.getValue(), d = days == null ? "7" : days.getValue(),
                hh = hours == null ? "0" : hours.getValue(), b = bonus == null ? "" : bonus.getValue(), wd = withinDays == null ? "1" : withinDays.getValue(),
                wh = withinHours == null ? "0" : withinHours.getValue(), n = notes == null ? "" : notes.getValue();
        widgets.clear();
        who = box(LX, 37, LW, w, "[A-Za-z0-9_]{0,16}");
        who.setMaxLength(16);
        who.setHint(Component.translatable("gui.bsp_core.bounty.search").withStyle(s -> s.withColor(BountyBoardScreen.DIM & 0xFFFFFF)));
        amount = box(98, 125, 66, a, "[0-9,]{0,16}");
        amount.setMaxLength(16);
        days = box(226, 52, 26, d, "[0-9]{0,2}");
        hours = box(262, 52, 26, hh, "[0-9]{0,2}");
        bonus = box(226, 67, 82, b, "[0-9,]{0,16}");
        bonus.setMaxLength(16);
        withinDays = box(226, 95, 26, wd, "[0-9]{0,2}");
        withinHours = box(262, 95, 26, wh, "[0-9]{0,2}");
        int notesMax = Math.max(0, screen.view().rules().notesMax());
        notes = new MultiLineEditBox(Minecraft.getInstance().font, screen.left() + LX, screen.top() + 164, LW, 40,
                Component.translatable("gui.bsp_core.bounty.notes_hint"), Component.translatable("gui.bsp_core.bounty.notes"));
        notes.setCharacterLimit(Math.max(1, notesMax));
        notes.setValue(n);
        widgets.add(notes);
        widgets.forEach(add);
        setVisible(visible);
    }

    void accept(BountyViewPacket v) {}

    /** After a bounty went up: a clean form for the next one. */
    void reset() {
        target = null;
        for (EditBox b : new EditBox[]{who, amount, bonus}) {
            if (b != null) {
                b.setValue("");
            }
        }
        if (notes != null) {
            notes.setValue("");
        }
    }

    void setVisible(boolean on) {
        visible = on;
        for (AbstractWidget w : widgets) {
            w.visible = on;
            if (!on) {
                w.setFocused(false);
            }
        }
    }

    boolean typing() {
        return visible && widgets.stream().anyMatch(AbstractWidget::isFocused);
    }

    void tick() {
        if (!visible || who == null) {
            return;
        }
        for (AbstractWidget w : widgets) {
            if (w instanceof EditBox e) {
                e.tick();
            }
        }
        notes.tick();
        String q = who.getValue().trim();
        if (!q.equals(asked) && System.currentTimeMillis() >= askAt) {
            asked = q;
            BSPNetwork.CHANNEL.sendToServer(BountyPlacePacket.search(q));
        }
    }

    // ------------------------------------------------------------------ the numbers

    private static long number(EditBox b) {
        String s = b == null ? "" : b.getValue().replace(",", "").trim();
        if (s.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    long duration() {
        return number(days) * D + number(hours) * H;
    }

    long within() {
        return number(withinDays) * D + number(withinHours) * H;
    }

    private int tierValue(int t) {
        int[] tv = screen.view().rules().tierValues();
        return t >= 0 && t < tv.length ? tv[t] : 1 << Math.max(0, t);
    }

    /** The market tax on a value, rounded up, at least 1 (0 with no tax). */
    private long fee(long value) {
        int pct = screen.view().rules().feePercent();
        return pct <= 0 || value <= 0 ? 0 : Math.max(1, (value * pct + 99) / 100);
    }

    private long haveCopper() {
        BountyScreens.Rules r = screen.view().rules();
        long v = 0;
        for (int i = 0; i < r.coins().length; i++) {
            v += (long) r.coins()[i] * tierValue(i);
        }
        return v;
    }

    /** Why PLACE BOUNTY cannot go yet (a result key), or null. */
    @Nullable
    String problem() {
        BountyScreens.Rules r = screen.view().rules();
        long a = number(amount), b = number(bonus), dur = duration();
        if (target == null) {
            return "pick";
        }
        if (!target.refusal().isEmpty()) {
            return target.refusal();
        }
        if (a <= 0 || !coins && a < r.minPieces() || coins && a > Bounties.MAX_COINS || !coins && a > Bounties.MAX_PIECES) {
            return "price";
        }
        if (b < 0 || b > (coins ? Bounties.MAX_COINS : Bounties.MAX_PIECES)) {
            return "bonus";
        }
        if (dur < H || dur > r.maxDays() * D) {
            return "time";
        }
        if (b > 0 && (within() <= 0 || within() >= dur)) {
            return "bonus_time";
        }
        if (r.open() >= r.maxOpen()) {
            return "too_many";
        }
        if (coins ? (a + b) * tierValue(tier) + fee((a + b) * tierValue(tier)) > haveCopper() : a + b + fee(a + b) > r.pieces()) {
            return "short";
        }
        return null;
    }

    private void place() {
        if (problem() != null || target == null) {
            return;
        }
        long b = number(bonus);
        BSPNetwork.CHANNEL.sendToServer(new BountyPlacePacket(false, target.id(), target.name(), coins ? tier : Bounty.PIECES, number(amount), b, b > 0 ? within() : 0,
                duration(), notes.getValue()));
    }

    // ------------------------------------------------------------------ drawing

    private void head(GuiGraphics g, int n, String key, int x, int y) {
        g.fill(x, y, x + 9, y + 9, BountyBoardScreen.AMBER);
        screen.centred(g, String.valueOf(n), x + 4.5f, y + 1, 0.85f, BountyBoardScreen.SCR, 8);
        screen.small(g, Component.translatable(key).getString().toUpperCase(Locale.ROOT), x + 13, y + 2, BountyBoardScreen.AMBER, 140, false);
    }

    private static void frameBox(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BountyBoardScreen.DIM);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BountyBoardScreen.BOX);
    }

    void render(GuiGraphics g, int x, int y, int mx, int my) {
        BountyScreens.Rules r = screen.view().rules();
        // 1 WHO
        head(g, 1, "gui.bsp_core.bounty.who", x + LX, y + 26);
        screen.small(g, Component.translatable("gui.bsp_core.bounty.online_first").getString(), x + LX + LW - 50, y + 28, BountyBoardScreen.DIM, 50, false);
        frameBox(g, x + LX, y + 37, LW, 12);
        for (int i = 0; i < ROWS; i++) {
            int at = listScroll + i;
            if (at >= picks.size()) {
                break;
            }
            BountyScreens.Pick p = picks.get(at);
            int ry = y + LIST_Y + i * ROW_H;
            boolean sel = target != null && target.id().equals(p.id()), hover = BountyBoardScreen.over(mx, my, x + LX, ry, LW, ROW_H - 1);
            if (sel || hover) {
                g.fill(x + LX, ry, x + LX + LW, ry + ROW_H - 1, sel ? BountyBoardScreen.AMBER_D : BountyBoardScreen.SEL);
                g.fill(x + LX + 1, ry + 1, x + LX + LW - 1, ry + ROW_H - 2, BountyBoardScreen.SEL);
            }
            PlayerFaceRenderer.draw(g, Minecraft.getInstance().getSkinManager().getInsecureSkinLocation(BountyBoardRenderer.profile(p.id(), p.name())), x + LX + 2, ry + 1, 9);
            int nameColour = !p.refusal().isEmpty() ? BountyBoardScreen.DIM : sel ? BountyBoardScreen.AMBER : BountyBoardScreen.PAPER;
            CashMachine.fit(g, Minecraft.getInstance().font, p.name(), x + LX + 14, ry + 2, 90, nameColour & 0xFFFFFF);
            if (!p.refusal().isEmpty()) {
                String why = Component.translatable("gui.bsp_core.bounty.not_target." + p.refusal()).getString();
                screen.small(g, why, x + LX + LW - 12 - Math.min(Minecraft.getInstance().font.width(why) * 0.75f, 44), ry + 3, BountyBoardScreen.DIM, 44, false);
            }
            g.fill(x + LX + LW - 8, ry + 4, x + LX + LW - 5, ry + 7, p.online() ? BountyBoardScreen.GREEN : BountyBoardScreen.DIM);
        }
        if (picks.isEmpty()) {
            screen.small(g, Component.translatable("gui.bsp_core.bounty.no_players").getString(), x + LX + 4, y + LIST_Y + 3, BountyBoardScreen.DIM, LW - 8, false);
        } else if (picks.size() > ROWS) {
            int track = ROWS * ROW_H - 2, bar = Math.max(6, track * ROWS / picks.size());
            int by = y + LIST_Y + (track - bar) * listScroll / Math.max(1, picks.size() - ROWS);
            g.fill(x + LX + LW + 1, by, x + LX + LW + 3, by + bar, BountyBoardScreen.DIM);
        }
        // 2 REWARD
        head(g, 2, "gui.bsp_core.bounty.reward", x + LX, y + 114);
        screen.key(g, x + LX, y + 125, 40, 12, Component.translatable("gui.bsp_core.bounty.kind.pieces"), coins ? 0 : 1, BountyBoardScreen.over(mx, my, x + LX, y + 125, 40, 12));
        screen.key(g, x + LX + 42, y + 125, 40, 12, Component.translatable("gui.bsp_core.bounty.kind.coins"), coins ? 1 : 0, BountyBoardScreen.over(mx, my, x + LX + 42, y + 125, 40, 12));
        frameBox(g, x + 98, y + 125, 66, 12);
        if (coins) {
            for (int i = 0; i < CoinTier.values().length; i++) {
                int cx = x + LX + i * 30;
                screen.key(g, cx, y + 139, 29, 11, Component.literal(""), i == tier ? 1 : 0, BountyBoardScreen.over(mx, my, cx, y + 139, 29, 11));
                g.fill(cx + 3, y + 142, cx + 8, y + 147, BountyBoardScreen.TIER_COLOURS[i]);
                String name = Bank.tierName(CoinTier.values()[i]);
                screen.small(g, name.substring(0, Math.min(4, name.length())), cx + 10, y + 141.5f, i == tier ? BountyBoardScreen.AMBER : BountyBoardScreen.MUTE, 18, false);
            }
        } else {
            screen.small(g, Component.translatable("gui.bsp_core.bounty.you_have", Bank.format(r.pieces())).getString(), x + LX, y + 141, BountyBoardScreen.MUTE, LW, false);
        }
        // 3 NOTES
        head(g, 3, "gui.bsp_core.bounty.notes_head", x + LX, y + 153);
        // 4 TIME AND BONUS
        head(g, 4, "gui.bsp_core.bounty.time_head", x + RX, y + 26);
        for (int i = 0; i < END_KEYS.length; i++) {
            int kx = x + RX + i * 27;
            screen.key(g, kx, y + 38, 25, 11, Component.literal(END_NAMES[i]), duration() == END_KEYS[i] ? 1 : END_KEYS[i] > r.maxDays() * D ? 3 : 0,
                    BountyBoardScreen.over(mx, my, kx, y + 38, 25, 11));
        }
        screen.small(g, Component.translatable("gui.bsp_core.bounty.ends_in").getString(), x + RX, y + 54, BountyBoardScreen.MUTE, 50, false);
        frameBox(g, x + 226, y + 52, 26, 12);
        screen.small(g, "d", x + 254, y + 54, BountyBoardScreen.MUTE, 6, false);
        frameBox(g, x + 262, y + 52, 26, 12);
        screen.small(g, "h", x + 290, y + 54, BountyBoardScreen.MUTE, 6, false);
        screen.small(g, Component.translatable(coins ? "gui.bsp_core.bounty.bonus_coins" : "gui.bsp_core.bounty.bonus_pieces").getString(), x + RX, y + 69,
                BountyBoardScreen.MUTE, 50, false);
        frameBox(g, x + 226, y + 67, 82, 12);
        screen.small(g, Component.translatable("gui.bsp_core.bounty.within").getString(), x + RX, y + 84, BountyBoardScreen.MUTE, 50, false);
        for (int i = 0; i < WITHIN_KEYS.length; i++) {
            int kx = x + 226 + i * 27;
            screen.key(g, kx, y + 82, 25, 11, Component.literal(WITHIN_NAMES[i]), within() == WITHIN_KEYS[i] ? 1 : 0, BountyBoardScreen.over(mx, my, kx, y + 82, 25, 11));
        }
        frameBox(g, x + 226, y + 95, 26, 12);
        screen.small(g, "d", x + 254, y + 97, BountyBoardScreen.MUTE, 6, false);
        frameBox(g, x + 262, y + 95, 26, 12);
        screen.small(g, "h", x + 290, y + 97, BountyBoardScreen.MUTE, 6, false);
        summary(g, x + RX, y + 111);
        String why = problem();
        Component line = why == null || why.equals("pick") && target == null && picks.isEmpty() ? Component.translatable("gui.bsp_core.bounty.comes_back")
                : why.equals("pick") ? Component.translatable("gui.bsp_core.bounty.pick") : BountyBoardScreen.resultText(why);
        CashMachine.smallWrap(g, Minecraft.getInstance().font, line, x + RX, y + 161, RW, (why == null || why.equals("pick") ? BountyBoardScreen.DIM : BountyBoardScreen.RED) & 0xFFFFFF);
        screen.key(g, x + RX, y + 186, RW, 16, Component.translatable("gui.bsp_core.bounty.place"), why == null ? 1 : 3, why == null && BountyBoardScreen.over(mx, my, x + RX, y + 186, RW, 16));
    }

    /** What the player pays now: reward, bonus (held until the end) and the fee, and the total. */
    private void summary(GuiGraphics g, int x, int y) {
        frameBox(g, x, y, RW, 46);
        long a = Math.max(0, number(amount)), b = Math.max(0, number(bonus));
        String reward, held, feeText, total;
        if (coins) {
            long value = (a + b) * tierValue(tier), f = fee(value);
            reward = BountyBoardScreen.describe(tier, a);
            held = BountyBoardScreen.describe(tier, b);
            feeText = Component.translatable("gui.bsp_core.bounty.fee_coins", f).getString();
            total = Component.translatable("gui.bsp_core.bounty.pay_coins", BountyBoardScreen.describe(tier, a + b), f).getString();
        } else {
            long f = fee(a + b);
            reward = Bank.format(a);
            held = Bank.format(b);
            feeText = Bank.format(f);
            total = Bank.format(a + b + f);
        }
        int pct = screen.view().rules().feePercent();
        int ly = y + 4;
        ly = row(g, x, ly, Component.translatable("gui.bsp_core.bounty.sum.reward").getString(), reward, BountyBoardScreen.PAPER);
        ly = row(g, x, ly, Component.translatable("gui.bsp_core.bounty.sum.bonus").getString(), held, BountyBoardScreen.PAPER);
        ly = row(g, x, ly, Component.translatable("gui.bsp_core.bounty.sum.fee", pct).getString(), feeText, BountyBoardScreen.PAPER);
        g.fill(x + 4, ly, x + RW - 4, ly + 1, BountyBoardScreen.DIM);
        row(g, x, ly + 2, Component.translatable("gui.bsp_core.bounty.sum.total").getString(), total, BountyBoardScreen.AMBER);
    }

    private int row(GuiGraphics g, int x, int y, String label, String value, int colour) {
        var font = Minecraft.getInstance().font;
        float vw = Math.min(font.width(value) * 0.75f, RW - 70);
        screen.small(g, label, x + 4, y, BountyBoardScreen.MUTE, RW - 12 - vw - 4, false);
        screen.small(g, value, x + RW - 4 - vw, y, colour, RW - 70, false);
        return y + 9;
    }

    // ------------------------------------------------------------------ input

    boolean mouseClicked(int mx, int my, int button) {
        int x = screen.left(), y = screen.top();
        for (int i = 0; i < ROWS; i++) {
            int at = listScroll + i;
            if (at < picks.size() && BountyBoardScreen.over(mx, my, x + LX, y + LIST_Y + i * ROW_H, LW, ROW_H - 1)) {
                target = picks.get(at);
                return true;
            }
        }
        if (BountyBoardScreen.over(mx, my, x + LX, y + 125, 40, 12)) {
            coins = false;
            return true;
        }
        if (BountyBoardScreen.over(mx, my, x + LX + 42, y + 125, 40, 12)) {
            coins = true;
            return true;
        }
        if (coins) {
            for (int i = 0; i < CoinTier.values().length; i++) {
                if (BountyBoardScreen.over(mx, my, x + LX + i * 30, y + 139, 29, 11)) {
                    tier = i;
                    return true;
                }
            }
        }
        for (int i = 0; i < END_KEYS.length; i++) {
            if (BountyBoardScreen.over(mx, my, x + RX + i * 27, y + 38, 25, 11) && END_KEYS[i] <= screen.view().rules().maxDays() * D) {
                days.setValue(String.valueOf(END_KEYS[i] / D));
                hours.setValue(String.valueOf(END_KEYS[i] % D / H));
                return true;
            }
        }
        for (int i = 0; i < WITHIN_KEYS.length; i++) {
            if (BountyBoardScreen.over(mx, my, x + 226 + i * 27, y + 82, 25, 11)) {
                withinDays.setValue(String.valueOf(WITHIN_KEYS[i] / D));
                withinHours.setValue(String.valueOf(WITHIN_KEYS[i] % D / H));
                return true;
            }
        }
        if (BountyBoardScreen.over(mx, my, x + RX, y + 186, RW, 16)) {
            place();
            return true;
        }
        return false;
    }

    boolean mouseScrolled(double mx, double my, double delta) {
        int x = screen.left(), y = screen.top();
        if (BountyBoardScreen.over((int) mx, (int) my, x + LX, y + LIST_Y, LW, ROWS * ROW_H) && picks.size() > ROWS) {
            listScroll = Mth.clamp(listScroll + (delta < 0 ? 1 : -1), 0, picks.size() - ROWS);
            return true;
        }
        return false;
    }

    boolean keyPressed(int key, int scan, int mods) {
        if (who != null && who.isFocused()) {
            askAt = System.currentTimeMillis() + 250; // search a quarter second after typing stops
        }
        return false;
    }
}
