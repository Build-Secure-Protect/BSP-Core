package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bounty.Bounties;
import com.mrgregles.bsp_core.bounty.Bounty;
import com.mrgregles.bsp_core.bounty.BountyScreens;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.BountyActionPacket;
import com.mrgregles.bsp_core.network.BountyViewPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The Bounty Board screen, look A "Poster Wall" (owner's pick, 2026-11-18): a wood-framed sepia screen. The WANTED, MY HUNTS and MY
 * BOUNTIES tabs show posters six at a time, three across and two down, with a search box and a sort key; PLACE A BOUNTY stands
 * apart at the top right ({@link BountyPlacePage}). Clicking a poster opens its own page: a big poster, the end, the bonus, the
 * hunters, the notes (only once accepted, or for its placer) and ACCEPT, ABANDON or CANCEL. When something waits in the player's
 * Collect box a card comes up first asking them to claim it. Everything shown comes from the server ({@link BountyViewPacket}).
 */
public class BountyBoardScreen extends Screen implements ScreenFit.Fitted {
    static final int W = 320, H = 220;
    static final int WOOD = 0xFF6B4426, WOOD_D = 0xFF4A2E18, WOOD_L = 0xFF8A5A32, BRASS = 0xFFC9A24A, SCR = 0xFF140D07, SCR2 = 0xFF21160B, CARD = 0xFF2A1B0D,
            PAPER = 0xFFECD9AB, INK = 0xFF3B2412, AMBER = 0xFFF2B544, AMBER_D = 0xFF8F6420, RED = 0xFFD0452E, GREEN = 0xFF7BD88F, MUTE = 0xFFA88D64,
            DIM = 0xFF5E4A30, SEL = 0xFF3A2812, BOX = 0xFF0C0804, TRACK = 0xFF3A2614;
    static final int[] TIER_COLOURS = {0xFFD27D46, 0xFFF5C542, 0xFF5FE3E3, 0xFF9C8A92, 0xFFC08CFF};
    private static final int GRID_X = 12, GRID_Y = 42, PW = 96, PH = 76, GAP = 4, PER_PAGE = 6;
    private static final long RESULT_MS = 6000, REFRESH_MS = 10_000;
    private static final String[] SORTS = {"reward", "ending", "newest"};

    private BountyViewPacket view;
    /** The server's clock minus this client's. */
    private long offset;
    private int page, gridPage, sort;
    @Nullable
    private String detail;
    private boolean popupDismissed;
    private String result = "";
    private long resultUntil, refreshAt;
    @Nullable
    private EditBox search;
    @Nullable
    private BountyPlacePage place;

    private BountyBoardScreen(BountyViewPacket view) {
        super(Component.translatable("block.bsp_core.bounty_board"));
        this.page = view.page();
        accept(view);
    }

    /** A view came from the server: open the screen, or give it to the one open. */
    public static void take(BountyViewPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BountyBoardScreen s) {
            if (msg.open()) {
                s.page = msg.page();
                s.detail = null;
                s.popupDismissed = false;
            }
            s.accept(msg);
        } else if (msg.open()) {
            mc.setScreen(new BountyBoardScreen(msg));
        }
    }

    private void accept(BountyViewPacket v) {
        this.view = v;
        this.offset = v.now() - System.currentTimeMillis();
        if (!v.result().isEmpty()) {
            result = v.result();
            resultUntil = System.currentTimeMillis() + RESULT_MS;
            if (v.result().equals("ok") && detail != null && row(detail) == null) {
                detail = null;   // cancelled: its page has nothing to show any more
            }
        }
        refreshAt = System.currentTimeMillis() + REFRESH_MS;
        if (place != null) {
            place.accept(v);
            if (!v.open() && "ok".equals(v.result()) && page == BountyScreens.PAGE_PLACE && v.page() == BountyScreens.PAGE_MINE) {
                page = BountyScreens.PAGE_MINE;   // the bounty went up: show it among the player's own
                gridPage = 0;
                place.reset();
            }
        }
    }

    @Nullable
    BountyPlacePage placePage() {
        return place;
    }

    long now() {
        return System.currentTimeMillis() + offset;
    }

    BountyViewPacket view() {
        return view;
    }

    int left() {
        return (width - W) / 2;
    }

    int top() {
        return (height - H) / 2;
    }

    @Override
    public int panelWidth() {
        return W;
    }

    @Override
    public int panelHeight() {
        return H;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        String text = search == null ? "" : search.getValue();
        search = new EditBox(font, left() + 13, top() + 27, 148, 10, Component.translatable("gui.bsp_core.bounty.search"));
        search.setBordered(false);
        search.setMaxLength(16);
        search.setTextColor(PAPER & 0xFFFFFF);
        search.setValue(text);
        search.setHint(Component.translatable("gui.bsp_core.bounty.search").withStyle(s -> s.withColor(DIM & 0xFFFFFF)));
        search.setResponder(s -> gridPage = 0);
        addRenderableWidget(search);
        if (place == null) {
            place = new BountyPlacePage(this);
        }
        place.init(this::addRenderableWidget);
        showWidgets();
    }

    private void showWidgets() {
        boolean browse = detail == null && page != BountyScreens.PAGE_PLACE && !popup();
        if (search != null) {
            search.visible = browse && page == BountyScreens.PAGE_BOARD;
            if (!search.visible) {
                search.setFocused(false);
            }
        }
        if (place != null) {
            place.setVisible(page == BountyScreens.PAGE_PLACE && !popup());
        }
    }

    @Override
    public void tick() {
        if (search != null) {
            search.tick();
        }
        if (place != null) {
            place.tick();
        }
        if (System.currentTimeMillis() > refreshAt) {
            refreshAt = System.currentTimeMillis() + REFRESH_MS;
            send(BountyScreens.Action.REFRESH, "");
        }
    }

    void send(BountyScreens.Action action, String id) {
        BSPNetwork.CHANNEL.sendToServer(new BountyActionPacket(action.ordinal(), id, page));
    }

    boolean popup() {
        return !popupDismissed && !view.collect().isEmpty();
    }

    @Nullable
    BountyScreens.Row row(String id) {
        for (BountyScreens.Row r : view.rows()) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        return null;
    }

    /** The posters the current tab shows, filtered and sorted. */
    private List<BountyScreens.Row> shown() {
        List<BountyScreens.Row> out = new ArrayList<>();
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        for (BountyScreens.Row r : view.rows()) {
            boolean in = switch (page) {
                case BountyScreens.PAGE_HUNTS -> r.accepted();
                case BountyScreens.PAGE_MINE -> r.mine();
                default -> q.isEmpty() || r.targetName().toLowerCase(Locale.ROOT).contains(q);
            };
            if (in) {
                out.add(r);
            }
        }
        if (sort == 1) {
            out.sort(Comparator.comparingLong(BountyScreens.Row::endsMs));
        } else if (sort == 2) {
            out.sort(Comparator.comparingLong(BountyScreens.Row::placedMs).reversed());
        }
        return out;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        showWidgets();
        int x = left(), y = top();
        frame(g, x, y);
        boolean modal = popup();
        int hx = modal ? -1 : mx, hy = modal ? -1 : my;
        if (page == BountyScreens.PAGE_PLACE) {
            tabs(g, x, y, hx, hy);
            place.render(g, x, y, hx, hy);
        } else if (detail != null && row(detail) != null) {
            detailPage(g, x, y, row(detail), hx, hy);
        } else {
            detail = null;
            tabs(g, x, y, hx, hy);
            browse(g, x, y, hx, hy);
        }
        if (System.currentTimeMillis() < resultUntil && !result.isEmpty()) {
            Component c = resultText(result);
            int tw = Math.min(font.width(c), W - 40);
            g.fill(x + W / 2 - tw / 2 - 4, y + H - 18, x + W / 2 + tw / 2 + 4, y + H - 7, 0xE0140D07);
            CashMachine.fit(g, font, c.getString(), x + W / 2 - tw / 2, y + H - 16, W - 40, (result.equals("ok") || result.equals("collected") ? GREEN : AMBER) & 0xFFFFFF);
        }
        super.render(g, mx, my, partial);
        if (modal) {
            collectPopup(g, x, y, mx, my);
        }
    }

    static Component resultText(String key) {
        return Component.translatable("message.bsp_core.bounty.result." + key);
    }

    /** The wood bezel with brass rivets round the dark screen. */
    private void frame(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + W, y + H, WOOD_D);
        g.fill(x + 1, y + 1, x + W - 1, y + H - 1, WOOD);
        for (int i = 0; i < 14; i++) {
            int yy = y + 1 + (i * 17 + 5) % (H - 2);
            g.fill(x + 1, yy, x + W - 1, yy + 1, i % 2 == 0 ? WOOD_L : WOOD_D);
        }
        g.fill(x + 5, y + 5, x + W - 5, y + H - 5, WOOD_D);
        g.fill(x + 6, y + 6, x + W - 6, y + H - 6, SCR);
        for (int[] r : new int[][]{{2, 2}, {W - 5, 2}, {2, H - 5}, {W - 5, H - 5}}) {
            g.fill(x + r[0], y + r[1], x + r[0] + 3, y + r[1] + 3, BRASS);
        }
    }

    /** A key in the screen's amber style. {@code state}: 0 plain, 1 selected, 2 hot (red), 3 off. */
    void key(GuiGraphics g, int x, int y, int w, int h, Component label, int state, boolean hover) {
        int edge = state == 1 ? AMBER : state == 2 ? RED : hover && state != 3 ? AMBER_D : DIM;
        int fill = state == 1 ? 0xFF3A2610 : state == 2 ? 0xFF4A140C : hover && state != 3 ? SEL : SCR2;
        g.fill(x, y, x + w, y + h, edge);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        int colour = state == 3 ? DIM : state == 1 ? AMBER : state == 2 ? 0xFFFF8A70 : PAPER;
        String s = label.getString().toUpperCase(Locale.ROOT);
        float k = Math.min(0.75f, (w - 4) / (float) Math.max(1, font.width(s)));
        g.pose().pushPose();
        g.pose().translate(x + w / 2f - font.width(s) * k / 2f, y + h / 2f - 4 * k + 0.5f, 0);
        g.pose().scale(k, k, 1);
        g.drawString(font, s, 0, 0, colour & 0xFFFFFF, false);
        g.pose().popPose();
    }

    static boolean over(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Small text: three-quarter size, {@code maxW} screen pixels at most (shrunk further when wider). */
    void small(GuiGraphics g, String s, float x, float y, int colour, float maxW, boolean centred) {
        float k = 0.75f;
        float w = font.width(s) * k;
        if (w > maxW) {
            k *= maxW / w;
            w = maxW;
        }
        g.pose().pushPose();
        g.pose().translate(centred ? x - w / 2 : x, y, 0);
        g.pose().scale(k, k, 1);
        g.drawString(font, s, 0, 0, colour & 0xFFFFFF, false);
        g.pose().popPose();
    }

    /** Text at {@code k} times the font's size, centred on {@code cx}, shrunk to {@code maxW}. */
    void centred(GuiGraphics g, String s, float cx, float y, float k, int colour, float maxW) {
        float w = font.width(s) * k;
        if (w > maxW) {
            k *= maxW / w;
            w = maxW;
        }
        g.pose().pushPose();
        g.pose().translate(cx - w / 2, y, 0);
        g.pose().scale(k, k, 1);
        g.drawString(font, s, 0, 0, colour & 0xFFFFFF, false);
        g.pose().popPose();
    }

    private static final int TAB_Y = 11, TAB_H = 11, PLACE_W = 84;

    /** The three tabs from the left, PLACE A BOUNTY apart at the right. */
    private void tabs(GuiGraphics g, int x, int y, int mx, int my) {
        int tx = x + 12;
        for (int i = 0; i < 3; i++) {
            Component label = tabName(i);
            int w = Math.round(font.width(label.getString().toUpperCase(Locale.ROOT)) * 0.75f) + 10;
            key(g, tx, y + TAB_Y, w, TAB_H, label, page == i && detail == null ? 1 : 0, over(mx, my, tx, y + TAB_Y, w, TAB_H));
            tx += w + 2;
        }
        int px = x + W - 12 - PLACE_W;
        key(g, px, y + TAB_Y, PLACE_W, TAB_H, Component.translatable("gui.bsp_core.bounty.tab.place"), page == BountyScreens.PAGE_PLACE ? 1 : 2,
                over(mx, my, px, y + TAB_Y, PLACE_W, TAB_H));
    }

    private static Component tabName(int i) {
        return Component.translatable(i == 0 ? "gui.bsp_core.bounty.tab.board" : i == 1 ? "gui.bsp_core.bounty.tab.hunts" : "gui.bsp_core.bounty.tab.mine");
    }

    private void browse(GuiGraphics g, int x, int y, int mx, int my) {
        List<BountyScreens.Row> list = shown();
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        gridPage = Mth.clamp(gridPage, 0, pages - 1);
        if (page == BountyScreens.PAGE_BOARD) {
            g.fill(x + 12, y + 25, x + 164, y + 38, DIM);
            g.fill(x + 13, y + 26, x + 163, y + 37, BOX);
            key(g, x + 168, y + 26, 62, 11, Component.translatable("gui.bsp_core.bounty.sort." + SORTS[sort]), 0, over(mx, my, x + 168, y + 26, 62, 11));
        } else {
            small(g, Component.translatable(page == BountyScreens.PAGE_HUNTS ? "gui.bsp_core.bounty.hunts_count" : "gui.bsp_core.bounty.mine_count",
                    page == BountyScreens.PAGE_HUNTS ? view.rules().accepted() : view.rules().open(),
                    page == BountyScreens.PAGE_HUNTS ? view.rules().maxAccepted() : view.rules().maxOpen()).getString(), x + 12, y + 28, MUTE, 220, false);
        }
        small(g, Component.translatable("gui.bsp_core.bounty.count", list.size()).getString(), x + W - 12 - font.width(Component.translatable("gui.bsp_core.bounty.count", list.size()).getString()) * 0.75f,
                y + 28, MUTE, 80, false);
        if (list.isEmpty()) {
            String key = page == BountyScreens.PAGE_HUNTS ? "gui.bsp_core.bounty.empty_hunts" : page == BountyScreens.PAGE_MINE ? "gui.bsp_core.bounty.empty_mine"
                    : search != null && !search.getValue().isBlank() ? "gui.bsp_core.bounty.empty_search" : "gui.bsp_core.bounty.empty_board";
            CashMachine.wrap(g, font, Component.translatable(key), x + 40, y + 100, W - 80, MUTE & 0xFFFFFF);
            return;
        }
        for (int i = 0; i < PER_PAGE; i++) {
            int at = gridPage * PER_PAGE + i;
            if (at >= list.size()) {
                break;
            }
            int px = x + GRID_X + (i % 3) * (PW + GAP), py = y + GRID_Y + (i / 3) * (PH + GAP);
            poster(g, list.get(at), px, py, PW, PH, false, over(mx, my, px, py, PW, PH));
        }
        if (pages > 1) {
            key(g, x + W / 2 - 52, y + 202, 18, 10, Component.literal("◀"), gridPage > 0 ? 0 : 3, over(mx, my, x + W / 2 - 52, y + 202, 18, 10));
            key(g, x + W / 2 + 34, y + 202, 18, 10, Component.literal("▶"), gridPage < pages - 1 ? 0 : 3, over(mx, my, x + W / 2 + 34, y + 202, 18, 10));
        }
        centred(g, Component.translatable("gui.bsp_core.bounty.page", gridPage + 1, pages).getString(), x + W / 2f, y + 204, 0.75f, DIM, 60);
    }

    /** A poster: WANTED, the face, the name, the reward, the time bar and what is left, by whom; BONUS and the viewer's marks. */
    void poster(GuiGraphics g, BountyScreens.Row r, int x, int y, int w, int h, boolean big, boolean hover) {
        long now = now();
        g.fill(x, y, x + w, y + h, hover ? AMBER : AMBER_D);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, CARD);
        float k = big ? 1f : 0.75f;
        centred(g, Component.translatable("gui.bsp_core.bounty.wanted").getString(), x + w / 2f, y + 3, k, AMBER, w - 34);
        int fs = big ? 48 : 24, fx = x + w / 2 - fs / 2, fy = y + (big ? 15 : 11);
        g.fill(fx - 1, fy - 1, fx + fs + 1, fy + fs + 1, INK);
        PlayerFaceRenderer.draw(g, Minecraft.getInstance().getSkinManager().getInsecureSkinLocation(BountyBoardRenderer.profile(r.target(), r.targetName())), fx, fy, fs);
        int yy = fy + fs + 3;
        centred(g, r.targetName(), x + w / 2f, yy, big ? 1f : 0.75f, PAPER, w - 6);
        yy += big ? 12 : 8;
        centred(g, reward(r.coinTier(), r.amount()), x + w / 2f, yy, big ? 1.2f : 1f, rewardColour(r.coinTier()), w - 6);
        yy += big ? 14 : 10;
        long leftMs = r.endsMs() - now, total = Math.max(1, r.endsMs() - r.placedMs());
        g.fill(x + 5, yy, x + w - 5, yy + 2, TRACK);
        g.fill(x + 5, yy, x + 5 + Math.round((w - 10) * Mth.clamp(leftMs / (float) total, 0, 1)), yy + 2, leftMs < Bounties.DAY_MS ? RED : AMBER);
        yy += 4;
        centred(g, ClientBounties.left(leftMs).getString(), x + w / 2f, yy, 0.75f, MUTE, w - 6);
        yy += 7;
        centred(g, Component.translatable("gui.bsp_core.bounty.by", r.placerName()).getString(), x + w / 2f, yy, big ? 0.75f : 0.6f, DIM, w - 6);
        if (r.bonus() > 0 && now < r.bonusEndsMs()) {
            int bw = big ? 30 : 22;
            g.fill(x + w - bw - 2, y + 2, x + w - 2, y + (big ? 11 : 9), RED);
            centred(g, Component.translatable("gui.bsp_core.bounty.bonus").getString(), x + w - 2 - bw / 2f, y + 3.5f, big ? 0.6f : 0.5f, 0xFFFFE0C0, bw - 2);
        }
        if (r.accepted() || r.mine()) {
            Component mark = Component.translatable(r.accepted() ? "gui.bsp_core.bounty.hunting" : "gui.bsp_core.bounty.yours");
            int c = r.accepted() ? GREEN : BRASS;
            int mw = Math.round(font.width(mark) * 0.5f) + 4;
            g.fill(x + 2, y + 2, x + 2 + mw, y + 9, c);
            g.fill(x + 3, y + 3, x + 1 + mw, y + 8, CARD);
            centred(g, mark.getString(), x + 2 + mw / 2f, y + 3.5f, 0.5f, c, mw - 2);
        }
    }

    static String reward(int coinTier, long amount) {
        if (coinTier == Bounty.PIECES) {
            return Bank.format(amount);
        }
        CoinTier t = CoinTier.byOrdinal(coinTier);
        return amount + " " + (t == null ? "?" : Bank.tierName(t));
    }

    static int rewardColour(int coinTier) {
        return coinTier == Bounty.PIECES ? AMBER : TIER_COLOURS[Mth.clamp(coinTier, 0, TIER_COLOURS.length - 1)];
    }

    // ------------------------------------------------------------------ a bounty's page

    private static final int BACK_W = 40, ACT_X = 138, ACT_Y = 186, ACT_W = 170, ACT_H = 16;

    private void detailPage(GuiGraphics g, int x, int y, BountyScreens.Row r, int mx, int my) {
        long now = now();
        key(g, x + 12, y + TAB_Y, BACK_W, TAB_H, Component.translatable("gui.bsp_core.bounty.back"), 0, over(mx, my, x + 12, y + TAB_Y, BACK_W, TAB_H));
        CashMachine.fit(g, font, Component.translatable("gui.bsp_core.bounty.on", r.targetName()).getString().toUpperCase(Locale.ROOT), x + 60, y + 13, W - 72, PAPER & 0xFFFFFF);
        poster(g, r, x + 12, y + 28, 118, 176, true, false);
        int lx = x + ACT_X, rx = x + W - 12, ly = y + 30;
        ly = line(g, lx, rx, ly, "gui.bsp_core.bounty.reward", describe(r.coinTier(), r.amount()), rewardColour(r.coinTier()));
        if (r.bonus() > 0) {
            boolean live = now < r.bonusEndsMs();
            ly = line(g, lx, rx, ly, "gui.bsp_core.bounty.bonus_line", live ? Component.translatable("gui.bsp_core.bounty.bonus_in", "+" + describe(r.coinTier(), r.bonus()),
                    Bounties.span(r.bonusEndsMs() - now)).getString() : Component.translatable("gui.bsp_core.bounty.bonus_over").getString(), live ? RED : DIM);
        }
        ly = line(g, lx, rx, ly, "gui.bsp_core.bounty.ends_in", Bounties.span(r.endsMs() - now), PAPER);
        ly = line(g, lx, rx, ly, "gui.bsp_core.bounty.hunters", Component.translatable("gui.bsp_core.bounty.hunters_n", r.hunters()).getString(), PAPER);
        ly = line(g, lx, rx, ly, "gui.bsp_core.bounty.placed", Component.translatable("gui.bsp_core.bounty.ago", Bounties.span(now - r.placedMs())).getString(), PAPER);
        g.fill(lx, ly + 1, rx, ly + 2, DIM);
        notes(g, r, lx, ly + 5, rx - lx, y + ACT_Y - ly - 9);
        // the one action this bounty offers this player
        boolean hover = over(mx, my, x + ACT_X, y + ACT_Y, ACT_W, ACT_H);
        if (r.mine()) {
            key(g, x + ACT_X, y + ACT_Y, ACT_W, ACT_H, Component.translatable(r.hunters() == 0 ? "gui.bsp_core.bounty.cancel" : "gui.bsp_core.bounty.cannot_cancel"),
                    r.hunters() == 0 ? 2 : 3, hover);
        } else if (r.accepted()) {
            key(g, x + ACT_X, y + ACT_Y, ACT_W, ACT_H, Component.translatable("gui.bsp_core.bounty.abandon"), 0, hover);
        } else if (!r.refusal().isEmpty()) {
            key(g, x + ACT_X, y + ACT_Y, ACT_W, ACT_H, resultText(r.refusal()), 3, false);
        } else {
            key(g, x + ACT_X, y + ACT_Y, ACT_W, ACT_H, Component.translatable("gui.bsp_core.bounty.accept"), 1, hover);
        }
    }

    private int line(GuiGraphics g, int lx, int rx, int ly, String labelKey, String value, int colour) {
        small(g, Component.translatable(labelKey).getString(), lx, ly, MUTE, 60, false);
        float w = Math.min(font.width(value) * 0.75f, rx - lx - 64);
        small(g, value, rx - w, ly, colour, rx - lx - 64, false);
        return ly + 10;
    }

    static String describe(int coinTier, long amount) {
        if (coinTier == Bounty.PIECES) {
            return Component.translatable("gui.bsp_core.bounty.pieces", Bank.format(amount)).getString();
        }
        CoinTier t = CoinTier.byOrdinal(coinTier);
        return Component.translatable(amount == 1 ? "gui.bsp_core.bounty.coin" : "gui.bsp_core.bounty.coins", amount, t == null ? "?" : Bank.tierName(t)).getString();
    }

    /** The notes box: the notes for a hunter who accepted (and the placer), a closed box for everyone else. */
    private void notes(GuiGraphics g, BountyScreens.Row r, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, DIM);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BOX);
        small(g, Component.translatable("gui.bsp_core.bounty.notes").getString(), x + 4, y + 3, MUTE, w - 8, false);
        if (r.accepted() || r.mine()) {
            Component text = r.notes().isEmpty() ? Component.translatable("gui.bsp_core.bounty.no_notes") : Component.literal(r.notes());
            ScreenFit.scissor(g, x + 1, y + 11, x + w - 1, y + h - 1);
            CashMachine.smallWrap(g, font, text, x + 4, y + 12, w - 8, (r.notes().isEmpty() ? DIM : PAPER) & 0xFFFFFF);
            g.disableScissor();
            return;
        }
        for (int i = 0; i < (h - 16) / 9; i++) {
            g.fill(x + 4, y + 14 + i * 9, x + w - 8 - (i * 23) % 30, y + 18 + i * 9, 0xFF2A1C10);
        }
        int bw = w - 16, by = y + h / 2 - 9;
        g.fill(x + 8, by, x + 8 + bw, by + 20, AMBER_D);
        g.fill(x + 9, by + 1, x + 7 + bw, by + 19, BOX);
        CashMachine.smallWrap(g, font, Component.translatable("gui.bsp_core.bounty.notes_locked"), x + 12, by + 3, bw - 8, AMBER & 0xFFFFFF);
    }

    // ------------------------------------------------------------------ the Collect box card

    private static final int CW = 232, CH = 118;

    private void collectPopup(GuiGraphics g, int x, int y, int mx, int my) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(x + 6, y + 6, x + W - 6, y + H - 6, 0xC0000000);
        int cx = x + (W - CW) / 2, cy = y + (H - CH) / 2;
        g.fill(cx, cy, cx + CW, cy + CH, AMBER);
        g.fill(cx + 1, cy + 1, cx + CW - 1, cy + CH - 1, CARD);
        centred(g, Component.translatable("gui.bsp_core.bounty.collect_title").getString(), cx + CW / 2f, cy + 6, 1f, AMBER, CW - 12);
        CashMachine.smallWrap(g, font, Component.translatable("gui.bsp_core.bounty.collect_text"), cx + 8, cy + 18, CW - 16, PAPER & 0xFFFFFF);
        List<ItemStack> items = view.collect();
        int shown = Math.min(items.size(), 22);
        for (int i = 0; i < shown; i++) {
            int ix = cx + 8 + (i % 11) * 20, iy = cy + 36 + (i / 11) * 20;
            g.fill(ix, iy, ix + 18, iy + 18, BOX);
            g.renderItem(items.get(i), ix + 1, iy + 1);
            g.renderItemDecorations(font, items.get(i), ix + 1, iy + 1);
        }
        if (items.size() > shown) {
            small(g, "+" + (items.size() - shown), cx + CW - 22, cy + 78, MUTE, 20, false);
        }
        int ky = cy + CH - 22;
        key(g, cx + 8, ky, 120, 15, Component.translatable("gui.bsp_core.bounty.claim_all"), 1, over(mx, my, cx + 8, ky, 120, 15));
        key(g, cx + CW - 8 - 70, ky, 70, 15, Component.translatable("gui.bsp_core.bounty.later"), 0, over(mx, my, cx + CW - 78, ky, 70, 15));
        for (int i = 0; i < shown; i++) {
            int ix = cx + 8 + (i % 11) * 20, iy = cy + 36 + (i / 11) * 20;
            if (over(mx, my, ix, iy, 18, 18)) {
                g.renderTooltip(font, items.get(i), mx, my);
            }
        }
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double dmx, double dmy, int button) {
        int mx = (int) dmx, my = (int) dmy, x = left(), y = top();
        if (popup()) {
            int cx = x + (W - CW) / 2, cy = y + (H - CH) / 2, ky = cy + CH - 22;
            if (over(mx, my, cx + 8, ky, 120, 15)) {
                send(BountyScreens.Action.COLLECT, "");
                popupDismissed = true;
            } else if (over(mx, my, cx + CW - 78, ky, 70, 15)) {
                popupDismissed = true;
            }
            return true;
        }
        if (detail != null && page != BountyScreens.PAGE_PLACE) {
            BountyScreens.Row r = row(detail);
            if (over(mx, my, x + 12, y + TAB_Y, BACK_W, TAB_H) || r == null) {
                detail = null;
                return true;
            }
            if (over(mx, my, x + ACT_X, y + ACT_Y, ACT_W, ACT_H)) {
                if (r.mine()) {
                    if (r.hunters() == 0) {
                        send(BountyScreens.Action.CANCEL, r.id());
                    }
                } else if (r.accepted()) {
                    send(BountyScreens.Action.ABANDON, r.id());
                } else if (r.refusal().isEmpty()) {
                    send(BountyScreens.Action.ACCEPT, r.id());
                }
                return true;
            }
            return super.mouseClicked(dmx, dmy, button);
        }
        // tabs
        int tx = x + 12;
        for (int i = 0; i < 3; i++) {
            int w = Math.round(font.width(tabName(i).getString().toUpperCase(Locale.ROOT)) * 0.75f) + 10;
            if (over(mx, my, tx, y + TAB_Y, w, TAB_H)) {
                page = i;
                gridPage = 0;
                return true;
            }
            tx += w + 2;
        }
        if (over(mx, my, x + W - 12 - PLACE_W, y + TAB_Y, PLACE_W, TAB_H)) {
            page = BountyScreens.PAGE_PLACE;
            return true;
        }
        if (page == BountyScreens.PAGE_PLACE) {
            return place.mouseClicked(mx, my, button) || super.mouseClicked(dmx, dmy, button);
        }
        if (page == BountyScreens.PAGE_BOARD && over(mx, my, x + 168, y + 26, 62, 11)) {
            sort = (sort + 1) % SORTS.length;
            gridPage = 0;
            return true;
        }
        List<BountyScreens.Row> list = shown();
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        if (over(mx, my, x + W / 2 - 52, y + 202, 18, 10) && gridPage > 0) {
            gridPage--;
            return true;
        }
        if (over(mx, my, x + W / 2 + 34, y + 202, 18, 10) && gridPage < pages - 1) {
            gridPage++;
            return true;
        }
        for (int i = 0; i < PER_PAGE; i++) {
            int at = gridPage * PER_PAGE + i;
            int px = x + GRID_X + (i % 3) * (PW + GAP), py = y + GRID_Y + (i / 3) * (PH + GAP);
            if (at < list.size() && over(mx, my, px, py, PW, PH)) {
                detail = list.get(at).id();
                return true;
            }
        }
        return super.mouseClicked(dmx, dmy, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (popup()) {
            return true;
        }
        if (page == BountyScreens.PAGE_PLACE) {
            return place.mouseScrolled(mx, my, delta) || super.mouseScrolled(mx, my, delta);
        }
        if (detail == null) {
            int pages = Math.max(1, (shown().size() + PER_PAGE - 1) / PER_PAGE);
            gridPage = Mth.clamp(gridPage + (delta < 0 ? 1 : -1), 0, pages - 1);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256 && popup()) {
            popupDismissed = true;
            return true;
        }
        if (key == 256 && detail != null) {
            detail = null;
            return true;
        }
        if (page == BountyScreens.PAGE_PLACE && place.keyPressed(key, scan, mods)) {
            return true;
        }
        if ((search != null && search.isFocused() || place != null && place.typing()) && key != 256) {
            return super.keyPressed(key, scan, mods) || true; // typing never closes the screen with the inventory key
        }
        return super.keyPressed(key, scan, mods);
    }

    /** The player who would be shown as "you". */
    @Nullable
    static UUID me() {
        return Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
    }

    void showPage(int p) {
        page = p;
        detail = null;
    }
}
