package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.TradeActionPacket;
import com.mrgregles.bsp_core.network.TradeStatePacket;
import com.mrgregles.bsp_core.trade.TradeMenu;
import com.mrgregles.bsp_core.trade.TradeSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Locale;

/**
 * The trade screen (look C, low and wide, owner's pick 2026-10-27): your 12 offer places at the left with your pieces box and
 * READY under them, theirs at the right, a status screen between with five countdown pips, and your inventory and the trade
 * chat side by side underneath. Escape (or walking away) cancels the trade and gives the items back. It shrinks to fit a
 * small window. Everything but the slots comes from the server ({@link TradeMenu.State}).
 */
public class TradeScreen extends AbstractContainerScreen<TradeMenu> implements ScreenFit.Fitted {
    private static final int BEZEL = 0xFFCFC8B8, BEZEL_DARK = 0xFF8F877A, BEZEL_LIGHT = 0xFFECE6D8, SCREEN = 0xFF061008, SCREEN_EDGE = 0xFF0D3B22,
            PHOS = 0xFF58F29A, DIM = 0xFF3FB97A, AMBER = 0xFFF2B544, LABEL = 0xFF5A5348;
    private EditBox pieces, chatBox;
    private String piecesText = "", lastSent = "";
    private int seen = -1;
    private String message = "";
    private long messageAt;

    public TradeScreen(TradeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = TradeMenu.W;
        this.imageHeight = TradeMenu.H;
    }

    public static void receive(TradeStatePacket msg) {
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.containerMenu instanceof TradeMenu m) {
            m.shown = msg.state();
            if (!msg.message().isEmpty()) {
                m.message = msg.message();
                m.messageArgs = msg.args();
            }
            m.serial++;
        }
    }

    private static void send(TradeActionPacket.Action a, long amount, String text) {
        BSPNetwork.CHANNEL.sendToServer(new TradeActionPacket(a, amount, text));
    }

    private TradeMenu.State st() {
        return menu.shown;
    }

    @Override
    protected void init() {
        super.init();
        pieces = new EditBox(font, leftPos + 48, topPos + 78, 32, 9, Component.translatable("gui.bsp_core.trade.pieces"));
        pieces.setBordered(false);
        pieces.setTextColor(PHOS);
        pieces.setMaxLength(13);
        pieces.setFilter(s -> s.matches("\\d*"));
        pieces.setValue(piecesText);
        pieces.setResponder(s -> piecesText = s);
        addRenderableWidget(pieces);
        chatBox = new EditBox(font, leftPos + 184, topPos + 188, 182, 9, Component.translatable("gui.bsp_core.trade.say"));
        chatBox.setBordered(false);
        chatBox.setTextColor(PHOS);
        chatBox.setMaxLength(TradeSession.CHAT_MAX);
        addRenderableWidget(chatBox);
    }

    @Override
    public int panelWidth() {
        return imageWidth;
    }

    @Override
    public int panelHeight() {
        return imageHeight;
    }

    // ------------------------------------------------------------------ input

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void sendPieces() {
        if (!piecesText.equals(lastSent)) {
            lastSent = piecesText;
            long p = 0;
            try {
                p = piecesText.isEmpty() ? 0 : Long.parseLong(piecesText);
            } catch (NumberFormatException ignored) {
                // over 13 digits cannot be typed
            }
            send(TradeActionPacket.Action.PIECES, p, "");
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double x = mx - leftPos, y = my - topPos;
        if (pieces.isFocused() && !over(x, y, 10, 76, 72, 12)) {
            sendPieces();
        }
        if (over(x, y, 10, 93, 72, 16)) {
            sendPieces();
            send(st().myReady() ? TradeActionPacket.Action.UNREADY : TradeActionPacket.Action.READY, 0, "");
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (chatBox.isFocused()) {
            if (key == 257 || key == 335) {   // enter
                if (!chatBox.getValue().isBlank()) {
                    send(TradeActionPacket.Action.SAY, 0, chatBox.getValue());
                    chatBox.setValue("");
                }
                return true;
            }
            if (key != 256) {
                return chatBox.keyPressed(key, scan, mods) || chatBox.canConsumeInput();
            }
        }
        if (pieces.isFocused()) {
            if (key == 257 || key == 335) {
                sendPieces();
                pieces.setFocused(false);
                return true;
            }
            if (key != 256) {
                return pieces.keyPressed(key, scan, mods) || pieces.canConsumeInput();
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        if (menu.serial != seen) {
            seen = menu.serial;
            if (!menu.message.isEmpty()) {
                message = Component.translatable(menu.message, menu.messageArgs.toArray()).getString();
                messageAt = System.currentTimeMillis();
                menu.message = "";
            }
            if (!pieces.isFocused()) {
                piecesText = st().myPieces() == 0 ? "" : String.valueOf(st().myPieces());
                lastSent = piecesText;
                pieces.setValue(piecesText);
            }
        }
        if (!message.isEmpty() && System.currentTimeMillis() - messageAt > 5000) {
            message = "";
        }
        pieces.setEditable(!st().locked());
        renderBackground(g);
        super.render(g, mx, my, partial);
        renderTooltip(g, mx, my);
    }

    private static void screen(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, SCREEN_EDGE);
        g.fill(x, y, x + w, y + h, SCREEN);
    }

    private static void slots(GuiGraphics g, int x, int y, int cols, int rows) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int sx = x + c * 18, sy = y + r * 18;
                g.fill(sx, sy, sx + 18, sy + 18, 0xFF373737);
                g.fill(sx + 1, sy + 1, sx + 18, sy + 18, 0xFFFFFFFF);
                g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF8B8B8B);
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x - 2, y - 2, x + imageWidth + 2, y + imageHeight + 2, BEZEL_DARK);
        g.fill(x, y, x + imageWidth, y + imageHeight, BEZEL);
        g.fill(x, y, x + imageWidth, y + 2, BEZEL_LIGHT);
        slots(g, x + TradeMenu.MINE_X, y + TradeMenu.MINE_Y, 4, 3);
        slots(g, x + TradeMenu.THEIRS_X, y + TradeMenu.MINE_Y, 4, 3);
        slots(g, x + TradeMenu.INV_X, y + TradeMenu.INV_Y, 9, 3);
        slots(g, x + TradeMenu.INV_X, y + TradeMenu.INV_Y + 58, 9, 1);
        screen(g, x + 90, y + 18, 200, 91);
        screen(g, x + 10, y + 76, 72, 12);
        screen(g, x + 298, y + 76, 72, 12);
        screen(g, x + 180, y + 122, 190, 76);
        if (st().locked()) {   // the offers are locked: a dark veil over both
            g.fill(x + TradeMenu.MINE_X, y + TradeMenu.MINE_Y, x + TradeMenu.MINE_X + 72, y + TradeMenu.MINE_Y + 54, 0x40000000);
        }
    }

    /** A Cash Machine key: beige, or phosphor green when on. */
    private void key(GuiGraphics g, int x, int y, int w, int h, String label, boolean on, boolean enabled, int mx, int my) {
        boolean hover = enabled && over(mx - leftPos, my - topPos, x, y, w, h);
        CashMachine.key(g, font, x, y, w, h, label, enabled, hover, on);
    }

    private static String tr(String key, Object... args) {
        return Component.translatable("gui.bsp_core.trade." + key, args).getString();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        TradeMenu.State s = st();
        CashMachine.fit(g, font, tr("title", s.theirName(), s.theirServer()).toUpperCase(Locale.ROOT), 10, 6, 280, LABEL);
        CashMachine.fit(g, font, tr("esc"), 300, 6, 70, LABEL);
        // pieces boxes and READY keys
        g.drawString(font, tr("pieces"), 13, 78, DIM, false);
        String theirs = Bank.format(s.theirPieces());
        g.drawString(font, tr("pieces"), 301, 78, DIM, false);
        g.drawString(font, theirs, 367 - font.width(theirs), 78, PHOS, false);
        boolean settling = s.stage().equals("SETTLING");
        key(g, 10, 93, 72, 16, s.myReady() ? tr("ready_on") : tr("ready"), s.myReady(), !settling, mx, my);
        key(g, 298, 93, 72, 16, s.theirReady() ? tr("ready_on") : tr("not_ready"), s.theirReady(), false, mx, my);
        // the status screen
        g.drawString(font, tr("you"), 96, 22, DIM, false);
        String them = s.theirName().toUpperCase(Locale.ROOT);
        CashMachine.fit(g, font, them, 284 - Math.min(font.width(them), 90), 22, 90, DIM);
        int lit = s.stage().equals("COUNTDOWN") ? (int) Math.ceil(s.countdownMs() / 1000.0) : 0;
        for (int i = 0; i < 5; i++) {
            g.fill(140 + i * 20, 40, 152 + i * 20, 52, i < lit ? PHOS : SCREEN_EDGE);
        }
        String status = switch (s.stage()) {
            case "COUNTDOWN" -> tr("counting", lit);
            case "SETTLING" -> tr("settling");
            default -> s.theirReady() ? tr("they_ready", s.theirName()) : s.myReady() ? tr("waiting", s.theirName()) : tr("fill");
        };
        int ny = CashMachine.wrap(g, font, Component.literal(status), 100, 60, 180, PHOS);
        TradeSession.Line note = null;
        for (int i = s.chat().size() - 1; i >= 0; i--) {
            if (s.chat().get(i).name().isEmpty()) {
                note = s.chat().get(i);
                break;
            }
        }
        if (!message.isEmpty()) {
            CashMachine.wrap(g, font, Component.literal(message), 100, Math.max(ny + 2, 82), 180, AMBER);
        } else if (note != null && !s.stage().equals("COUNTDOWN")) {
            CashMachine.wrap(g, font, Component.literal(note.text()), 100, Math.max(ny + 2, 82), 180, AMBER);
        }
        // the chat, newest at the bottom, the input line under it
        List<TradeSession.Line> lines = s.chat().stream().filter(l -> !l.name().isEmpty()).toList();
        int y = 182;
        for (int i = lines.size() - 1; i >= 0 && y > 124; i--) {
            TradeSession.Line l = lines.get(i);
            String who = l.name() + ": ";
            g.drawString(font, who, 184, y - 9, AMBER, false);
            CashMachine.fit(g, font, l.text(), 184 + font.width(who), y - 9, 182 - font.width(who), PHOS);
            y -= 10;
        }
        g.fill(182, 185, 368, 186, SCREEN_EDGE);
        if (chatBox.getValue().isEmpty() && !chatBox.isFocused()) {
            g.drawString(font, tr("say_hint"), 184, 188, SCREEN_EDGE + 0x202020, false);
        }
    }
}
