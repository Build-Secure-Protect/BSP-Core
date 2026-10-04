package com.mrgregles.bsp_core.client;

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

/**
 * Settings of a Score Screen, for admins: what it shows (scoreboard or scoring rules), the
 * scoreboard layout, font, text size, scroll speed and title. Every change is sent at once, so the
 * screen in the world updates while this is open.
 */
public class ScoreScreenConfigScreen extends Screen {
    private static final int BG = 0xF010151C, VIO = 0xFFB58CFF, SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, MUTED = 0x9AA3B5;
    private static final int W = 260, H = 178, LABEL_X = 10, OPT_X = 76, OPT_W = 58, ROW = 22, FIRST = 26;

    private final BlockPos origin;
    private int mode, layout, fontIndex, size, speed;
    private EditBox title;

    public ScoreScreenConfigScreen(BlockPos origin, ScoreScreenBlockEntity screen) {
        super(Component.translatable("gui.bsp_core.screen.title"));
        this.origin = origin;
        this.mode = screen.mode();
        this.layout = screen.layout();
        this.fontIndex = screen.font();
        this.size = screen.size();
        this.speed = screen.speed();
        this.initialTitle = screen.title();
    }

    private final String initialTitle;

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    @Override
    protected void init() {
        String keep = title == null ? initialTitle : title.getValue();
        title = new EditBox(font, left() + OPT_X, top() + FIRST + ROW * 5 - 2, W - OPT_X - 10, 16, Component.translatable("gui.bsp_core.screen.heading"));
        title.setMaxLength(ScoreScreenBlockEntity.TITLE_MAX);
        title.setValue(keep);
        title.setResponder(s -> send());
        addRenderableWidget(title);
    }

    private void send() {
        BSPNetwork.CHANNEL.sendToServer(new ScoreScreenConfigPacket(origin, mode, layout, fontIndex, size, speed, title.getValue()));
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = left(), y = top();
        for (int row = 0; row < 3; row++) {
            int options = 3;
            for (int i = 0; i < options; i++) {
                if (over(mx, my, x + OPT_X + i * (OPT_W + 2), y + FIRST + row * ROW - 3, OPT_W, 16)) {
                    if (row == 0) {
                        mode = i;
                    } else if (row == 1) {
                        layout = i;
                    } else {
                        fontIndex = i;
                    }
                    send();
                    return true;
                }
            }
        }
        for (int row = 3; row < 5; row++) {
            for (int side = 0; side < 2; side++) {
                if (over(mx, my, x + OPT_X + side * 100, y + FIRST + row * ROW - 3, 18, 16)) {
                    int step = side == 0 ? -10 : 10;
                    if (row == 3) {
                        size = Mth.clamp(size + step, 60, 160);
                    } else {
                        speed = Mth.clamp(speed + step, 0, 100);
                    }
                    send();
                    return true;
                }
            }
        }
        if (over(mx, my, x + W - 70, y + H - 24, 60, 16)) {
            onClose();
            return true;
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

    private void option(GuiGraphics g, int x, int y, int w, Component label, boolean on, int mouseX, int mouseY) {
        boolean hover = over(mouseX, mouseY, x, y, w, 16);
        g.fill(x, y, x + w, y + 16, on ? VIO : hover ? 0xFF6B7385 : DIM);
        g.fill(x + 1, y + 1, x + w - 1, y + 15, on ? 0xFF2A1F45 : SLOT_BG);
        g.pose().pushPose();
        g.pose().translate(x + w / 2f, y + 5, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, label, -font.width(label) / 2, 0, on ? VIO & 0xFFFFFF : 0xE8EAF0, false);
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = left(), y = top();
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, VIO);
        g.fill(x, y, x + W, y + H, BG);
        g.drawString(font, this.getTitle(), x + 10, y + 8, VIO & 0xFFFFFF, false);
        String[] labels = {"show", "layout", "font", "size", "speed", "heading"};
        for (int row = 0; row < labels.length; row++) {
            g.drawString(font, Component.translatable("gui.bsp_core.screen." + labels[row]), x + LABEL_X, y + FIRST + row * ROW + 1, MUTED, false);
        }
        for (int i = 0; i < 3; i++) {
            option(g, x + OPT_X + i * (OPT_W + 2), y + FIRST - 3, OPT_W, Component.translatable("gui.bsp_core.screen.mode." + i), mode == i, mouseX, mouseY);
            option(g, x + OPT_X + i * (OPT_W + 2), y + FIRST + ROW - 3, OPT_W, Component.translatable("gui.bsp_core.screen.layout." + i),
                    layout == i && mode == ScoreScreenBlockEntity.MODE_BOARD, mouseX, mouseY);
            option(g, x + OPT_X + i * (OPT_W + 2), y + FIRST + ROW * 2 - 3, OPT_W, Component.translatable("gui.bsp_core.screen.font." + i), fontIndex == i, mouseX, mouseY);
        }
        int[] values = {size, speed};
        for (int row = 3; row < 5; row++) {
            int ry = y + FIRST + row * ROW - 3;
            option(g, x + OPT_X, ry, 18, Component.literal("-"), false, mouseX, mouseY);
            option(g, x + OPT_X + 100, ry, 18, Component.literal("+"), false, mouseX, mouseY);
            String v = values[row - 3] + "%";
            g.drawString(font, v, x + OPT_X + 59 - font.width(v) / 2, ry + 4, 0xE8EAF0, false);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockState(origin).getBlock() instanceof ScoreScreenBlock) {
            int[] wh = ScoreScreenBlock.size(mc.level, origin, mc.level.getBlockState(origin).getValue(ScoreScreenBlock.FACING));
            g.pose().pushPose();
            g.pose().translate(x + 10, y + H - 19, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, Component.translatable("gui.bsp_core.screen.size_info", wh[0], wh[1]), 0, 0, MUTED, false);
            g.pose().popPose();
        }
        option(g, x + W - 70, y + H - 24, 60, Component.translatable("gui.bsp_core.screen.done"), false, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Opens the settings for the screen whose bottom-left panel is at {@code origin}. Client only. */
    public static void open(BlockPos origin) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(origin) instanceof ScoreScreenBlockEntity screen) {
            mc.setScreen(new ScoreScreenConfigScreen(origin, screen));
        }
    }
}
