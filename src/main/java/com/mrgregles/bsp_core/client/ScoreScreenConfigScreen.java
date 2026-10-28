package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.api.BspBoards;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.ScoreScreenConfigPacket;
import com.mrgregles.bsp_core.score.ScoreScreenBlock;
import com.mrgregles.bsp_core.score.ScoreScreenBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings of a Score Screen, for admins: what it shows (the scoreboard, the scoring rules, the
 * prizes, one of the boards other mods registered, or a rotation through pages), which board and how
 * it is drawn, the rotation's pages and seconds, the scoreboard layout, font, text size, scroll speed
 * and title. Every change is sent at once, so the screen in the world updates while this is open.
 */
public class ScoreScreenConfigScreen extends Screen implements ScreenFit.Fitted {
    private static final int BG = 0xF010151C, VIO = 0xFFB58CFF, SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, MUTED = 0x9AA3B5;
    private static final int W = 330, H = 276, LABEL_X = 10, OPT_X = 86, ROW = 22, FIRST = 26, ROWS = 10;
    private static final String[] LABELS = {"show", "board", "look", "rotation", "every", "layout", "font", "size", "speed", "heading"};

    private record Hit(int x, int y, int w, int h, Runnable run) {}

    private final BlockPos origin;
    private final String initialTitle;
    private int mode, layout, fontIndex, size, speed, look, rotate;
    private String boardId;
    private List<String> pages;
    private EditBox title;
    private final List<Hit> hits = new ArrayList<>();

    public ScoreScreenConfigScreen(BlockPos origin, ScoreScreenBlockEntity screen) {
        super(Component.translatable("gui.bsp_core.screen.title"));
        this.origin = origin;
        this.mode = screen.mode();
        this.layout = screen.layout();
        this.fontIndex = screen.font();
        this.size = screen.size();
        this.speed = screen.speed();
        this.look = screen.look();
        this.rotate = screen.rotateSeconds();
        this.boardId = screen.boardId();
        this.pages = new ArrayList<>(screen.pages());
        this.initialTitle = screen.title();
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private int rowY(int row) {
        return top() + FIRST + row * ROW - 3;
    }

    @Override
    protected void init() {
        String keep = title == null ? initialTitle : title.getValue();
        title = new EditBox(font, left() + OPT_X, rowY(9) + 1, W - OPT_X - 10, 16, Component.translatable("gui.bsp_core.screen.heading"));
        title.setMaxLength(ScoreScreenBlockEntity.TITLE_MAX);
        title.setValue(keep);
        title.setResponder(s -> send());
        addRenderableWidget(title);
    }

    private void send() {
        BSPNetwork.CHANNEL.sendToServer(new ScoreScreenConfigPacket(origin, mode, layout, fontIndex, size, speed, title.getValue(), boardId, look, rotate, pages));
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** The rotation's pages as they stand: the saved list, or the default (the scoreboard, then every registered board). */
    private List<String> rotation() {
        if (!pages.isEmpty()) {
            return pages;
        }
        List<String> out = new ArrayList<>();
        out.add(ScoreScreenBlockEntity.PAGE_SCOREBOARD);
        for (BspBoards.Board b : BspBoards.boards()) {
            out.add(b.id().toString());
        }
        return out;
    }

    private void togglePage(String id) {
        List<String> now = new ArrayList<>(rotation());
        if (!now.remove(id)) {
            now.add(id);
        }
        pages = now;
        send();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (Hit h : hits) {
                if (over(mx, my, h.x(), h.y(), h.w(), h.h())) {
                    h.run().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (title.isFocused() && key != 256) { // let the title box take every key except Escape
            title.keyPressed(key, scan, mods);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void option(GuiGraphics g, int x, int y, int w, Component label, boolean on, int mouseX, int mouseY, Runnable run) {
        boolean hover = over(mouseX, mouseY, x, y, w, 16);
        g.fill(x, y, x + w, y + 16, on ? VIO : hover ? 0xFF6B7385 : DIM);
        g.fill(x + 1, y + 1, x + w - 1, y + 15, on ? 0xFF2A1F45 : SLOT_BG);
        g.pose().pushPose();
        g.pose().translate(x + w / 2f, y + 5, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        int lw = font.width(label);
        float k = lw * 0.75f > w - 6 ? (w - 6) / (lw * 0.75f) : 1f;
        g.pose().scale(k, k, 1f);
        g.drawString(font, label, -lw / 2, 0, on ? VIO & 0xFFFFFF : 0xE8EAF0, false);
        g.pose().popPose();
        hits.add(new Hit(x, y, w, 16, run));
    }

    /** A row of chips, {@code count} of them sharing the row's width. */
    private int chipWidth(int count) {
        int avail = W - OPT_X - 10;
        return Math.max(26, Math.min(58, (avail - (count - 1) * 2) / Math.max(1, count)));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        hits.clear();
        int x = left(), y = top();
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, VIO);
        g.fill(x, y, x + W, y + H, BG);
        g.drawString(font, this.getTitle(), x + 10, y + 8, VIO & 0xFFFFFF, false);
        for (int row = 0; row < ROWS; row++) {
            g.drawString(font, Component.translatable("gui.bsp_core.screen." + LABELS[row]), x + LABEL_X, rowY(row) + 4, MUTED, false);
        }
        // 0 show
        int cw = chipWidth(ScoreScreenBlockEntity.MODES);
        for (int i = 0; i < ScoreScreenBlockEntity.MODES; i++) {
            int m = i;
            option(g, x + OPT_X + i * (cw + 2), rowY(0), cw, Component.translatable("gui.bsp_core.screen.mode." + i), mode == i, mouseX, mouseY, () -> { mode = m; send(); });
        }
        // 1 board: the registered boards
        List<BspBoards.Board> boards = BspBoards.boards();
        if (boards.isEmpty()) {
            small(g, Component.translatable("gui.bsp_core.screen.board.none"), x + OPT_X, rowY(1) + 5, MUTED);
        } else {
            int bw = chipWidth(Math.min(4, boards.size()));
            for (int i = 0; i < Math.min(4, boards.size()); i++) {
                String id = boards.get(i).id().toString();
                option(g, x + OPT_X + i * (bw + 2), rowY(1), bw, boards.get(i).title(), id.equals(boardId) && (mode == ScoreScreenBlockEntity.MODE_PAGE), mouseX, mouseY, () -> {
                    boardId = id;
                    if (id.equals(ClientMarket.BOARD.toString())) {
                        look = ScoreScreenBlockEntity.LOOK_TILES; // the Market Board's own look (owner's pick); the admin can change it after
                    }
                    send();
                });
            }
        }
        // 2 look
        int lw = chipWidth(ScoreScreenBlockEntity.LOOKS);
        for (int i = 0; i < ScoreScreenBlockEntity.LOOKS; i++) {
            int k = i;
            option(g, x + OPT_X + i * (lw + 2), rowY(2), lw, Component.translatable("gui.bsp_core.screen.look." + i), look == i, mouseX, mouseY, () -> { look = k; send(); });
        }
        // 3 rotation: the pages
        List<String> rot = rotation();
        List<String> ids = new ArrayList<>(List.of(ScoreScreenBlockEntity.PAGE_SCOREBOARD, ScoreScreenBlockEntity.PAGE_RULES, ScoreScreenBlockEntity.PAGE_PRIZES));
        List<Component> names = new ArrayList<>(List.of(Component.translatable("gui.bsp_core.screen.mode.0"), Component.translatable("gui.bsp_core.screen.mode.1"), Component.translatable("gui.bsp_core.screen.mode.2")));
        for (BspBoards.Board b : boards) {
            if (ids.size() < 6) {
                ids.add(b.id().toString());
                names.add(b.title());
            }
        }
        int rw = chipWidth(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            option(g, x + OPT_X + i * (rw + 2), rowY(3), rw, names.get(i), rot.contains(id) && mode == ScoreScreenBlockEntity.MODE_ROTATE, mouseX, mouseY, () -> togglePage(id));
        }
        // 4 every: seconds
        option(g, x + OPT_X, rowY(4), 18, Component.literal("-"), false, mouseX, mouseY, () -> { rotate = Mth.clamp(rotate - 5, ScoreScreenBlockEntity.ROTATE_MIN, ScoreScreenBlockEntity.ROTATE_MAX); send(); });
        option(g, x + OPT_X + 100, rowY(4), 18, Component.literal("+"), false, mouseX, mouseY, () -> { rotate = Mth.clamp(rotate + 5, ScoreScreenBlockEntity.ROTATE_MIN, ScoreScreenBlockEntity.ROTATE_MAX); send(); });
        String sec = Component.translatable("gui.bsp_core.screen.seconds", rotate).getString();
        g.drawString(font, sec, x + OPT_X + 59 - font.width(sec) / 2, rowY(4) + 4, 0xE8EAF0, false);
        // 5 layout, 6 font
        int ow = chipWidth(3);
        for (int i = 0; i < 3; i++) {
            int k = i;
            option(g, x + OPT_X + i * (ow + 2), rowY(5), ow, Component.translatable("gui.bsp_core.screen.layout." + i), layout == i, mouseX, mouseY, () -> { layout = k; send(); });
            option(g, x + OPT_X + i * (ow + 2), rowY(6), ow, Component.translatable("gui.bsp_core.screen.font." + i), fontIndex == i, mouseX, mouseY, () -> { fontIndex = k; send(); });
        }
        // 7 size, 8 speed
        for (int row = 7; row < 9; row++) {
            int ry = rowY(row);
            boolean isSize = row == 7;
            option(g, x + OPT_X, ry, 18, Component.literal("-"), false, mouseX, mouseY, () -> { if (isSize) size = Mth.clamp(size - 10, 60, 160); else speed = Mth.clamp(speed - 10, 0, 100); send(); });
            option(g, x + OPT_X + 100, ry, 18, Component.literal("+"), false, mouseX, mouseY, () -> { if (isSize) size = Mth.clamp(size + 10, 60, 160); else speed = Mth.clamp(speed + 10, 0, 100); send(); });
            String v = (isSize ? size : speed) + "%";
            g.drawString(font, v, x + OPT_X + 59 - font.width(v) / 2, ry + 4, 0xE8EAF0, false);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockState(origin).getBlock() instanceof ScoreScreenBlock) {
            int[] wh = ScoreScreenBlock.size(mc.level, origin, mc.level.getBlockState(origin).getValue(ScoreScreenBlock.FACING));
            small(g, Component.translatable("gui.bsp_core.screen.size_info", wh[0], wh[1]), x + 10, y + H - 19, MUTED);
        }
        option(g, x + W - 70, y + H - 24, 60, Component.translatable("gui.bsp_core.screen.done"), false, mouseX, mouseY, this::onClose);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void small(GuiGraphics g, Component text, int sx, int sy, int colour) {
        g.pose().pushPose();
        g.pose().translate(sx, sy, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    /** Opens the settings for the screen whose bottom-left panel is at {@code origin}. Client only. */
    public static void open(BlockPos origin) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(origin) instanceof ScoreScreenBlockEntity screen) {
            mc.setScreen(new ScoreScreenConfigScreen(origin, screen));
        }
    }

    @Override
    public int panelWidth() {
        return W;
    }

    @Override
    public int panelHeight() {
        return H;
    }
}
