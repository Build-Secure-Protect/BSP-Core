package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.ValveSetPacket;
import com.mrgregles.bsp_core.network.ValveViewPacket;
import com.mrgregles.bsp_core.plasma.PlasmaValveBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * The Plasma Valve's screen: a dial you drag to set the limit, a box to type it, nudge buttons, a redstone switch,
 * and what is passing. The server refreshes it once a second and applies every change at once.
 */
public class PlasmaValveScreen extends Screen {
    private static final int W = 220, H = 128, DIAL_X = 56, DIAL_Y = 70, DIAL_R = 38, RIGHT = 112;
    private static final int BG = 0xF010151C, SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, TQ = 0xFF19D3B0, GOLD = 0xFFFFD23A, MUTED = 0x9AA3B5, TEXT = 0xE6EAF2, BAD = 0xFF6B5C, ION = 0xFF4FB8FF;
    private static final double SWEEP = Math.PI * 1.5; // the dial runs three quarters of a turn, from lower left to lower right
    @Nullable
    private static ValveViewPacket view;
    private final BlockPos pos;
    private int limit, max = 2000, ticks;
    private boolean redstone = true, dragging;
    private EditBox box;

    public PlasmaValveScreen(BlockPos pos) {
        super(Component.translatable("block.bsp_core.plasma_valve"));
        this.pos = pos;
    }

    public static void receive(ValveViewPacket msg) {
        view = msg;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof PlasmaValveScreen s && s.pos.equals(msg.pos())) {
            s.take(msg);
        } else if (!(mc.screen instanceof PlasmaValveScreen)) {
            PlasmaValveScreen s = new PlasmaValveScreen(msg.pos());
            mc.setScreen(s);
            s.take(msg);
        }
    }

    private void take(ValveViewPacket msg) {
        max = Math.max(10, msg.max());
        if (!dragging && (box == null || !box.isFocused())) {
            limit = msg.limit();
            redstone = msg.redstone();
            if (box != null) {
                box.setValue(Integer.toString(limit));
            }
        }
    }

    @Override
    protected void init() {
        int x = left(), y = top();
        box = new EditBox(font, x + RIGHT + 2, y + 34, 56, 14, Component.empty());
        box.setMaxLength(6);
        box.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        box.setValue(Integer.toString(limit));
        box.setResponder(s -> {
            if (!s.isEmpty() && box.isFocused()) {
                limit = clamp(Integer.parseInt(s));
            }
        });
        addRenderableWidget(box);
        if (view != null && view.pos().equals(pos)) {
            take(view);
            box.setValue(Integer.toString(limit));
        }
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private int clamp(int v) {
        return Math.max(0, Math.min(max, v));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (minecraft == null || minecraft.level == null || !(minecraft.level.getBlockEntity(pos) instanceof PlasmaValveBlockEntity)) {
            onClose();
            return;
        }
        if (++ticks % 20 == 0) {
            BSPNetwork.CHANNEL.sendToServer(new ValveSetPacket(pos, ValveSetPacket.VIEW, 0, false));
        }
    }

    private void send() {
        limit = clamp(limit);
        box.setValue(Integer.toString(limit));
        BSPNetwork.CHANNEL.sendToServer(new ValveSetPacket(pos, ValveSetPacket.SET, limit, redstone));
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** The limit the dial would read with the mouse at {@code mx, my}. */
    private int dialValue(double mx, double my) {
        double dx = mx - (left() + DIAL_X), dy = my - (top() + DIAL_Y);
        double a = Math.atan2(dx, -dy); // 0 straight up, positive clockwise
        a = Math.max(-SWEEP / 2, Math.min(SWEEP / 2, a));
        return (int) Math.round((a + SWEEP / 2) / SWEEP * max / 10.0) * 10;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = left(), y = top();
        if (box.isFocused() && !over(mx, my, box.getX(), box.getY(), box.getWidth(), box.getHeight())) {
            box.setFocused(false);
            send();
        }
        double dx = mx - (x + DIAL_X), dy = my - (y + DIAL_Y);
        if (dx * dx + dy * dy <= (DIAL_R + 6) * (DIAL_R + 6)) {
            dragging = true;
            limit = dialValue(mx, my);
            box.setValue(Integer.toString(limit));
            return true;
        }
        int by = y + 54;
        int[] steps = {-100, -10, 10, 100};
        for (int i = 0; i < steps.length; i++) {
            if (over(mx, my, x + RIGHT + i * 25, by, 23, 12)) {
                limit = clamp(limit + steps[i]);
                send();
                return true;
            }
        }
        if (over(mx, my, x + RIGHT, by + 15, 48, 12)) {
            limit = max;
            send();
            return true;
        }
        if (over(mx, my, x + RIGHT + 50, by + 15, 48, 12)) {
            limit = 0;
            send();
            return true;
        }
        if (over(mx, my, x + RIGHT, y + 88, 98, 12)) {
            redstone = !redstone;
            send();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double ddx, double ddy) {
        if (dragging) {
            limit = dialValue(mx, my);
            box.setValue(Integer.toString(limit));
            return true;
        }
        return super.mouseDragged(mx, my, button, ddx, ddy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging) {
            dragging = false;
            send();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (box.isFocused() && (key == 257 || key == 335)) { // enter
            box.setFocused(false);
            send();
            return true;
        }
        if (key == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void small(GuiGraphics g, Component text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.bsp_core.valve." + key, args);
    }

    private void dot(GuiGraphics g, double x, double y, int size, int colour) {
        g.fill((int) Math.round(x) - size / 2, (int) Math.round(y) - size / 2, (int) Math.round(x) + (size + 1) / 2, (int) Math.round(y) + (size + 1) / 2, colour);
    }

    private void button(GuiGraphics g, int x, int y, int w, String text, int colour, boolean on) {
        g.fill(x, y, x + w, y + 12, SLOT_BG);
        g.fill(x, y, x + w, y + 1, on ? colour : DIM);
        g.fill(x, y + 11, x + w, y + 12, on ? colour : DIM);
        g.fill(x, y, x + 1, y + 12, on ? colour : DIM);
        g.fill(x + w - 1, y, x + w, y + 12, on ? colour : DIM);
        g.pose().pushPose();
        g.pose().translate(x + w / 2f - font.width(text) * 0.375f, y + 3, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, on ? colour & 0xFFFFFF : TEXT, false);
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = left(), y = top();
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, TQ);
        g.fill(x, y, x + W, y + H, BG);
        g.drawString(font, title.getString().toUpperCase(Locale.ROOT), x + 10, y + 8, TQ & 0xFFFFFF, false);
        ValveViewPacket v = view != null && view.pos().equals(pos) ? view : null;
        boolean shutByRedstone = v != null && redstone && v.powered(), open = limit > 0 && !shutByRedstone;
        // the state chip, top right
        String state = tr(shutByRedstone ? "shut_redstone" : limit <= 0 ? "shut_limit" : "open").getString();
        int sw = Math.round(font.width(state) * 0.75f) + 8, sx = x + W - 10 - sw;
        g.fill(sx, y + 6, sx + sw, y + 18, SLOT_BG);
        int sc = open ? ION : BAD;
        g.fill(sx, y + 6, sx + sw, y + 7, sc);
        g.fill(sx, y + 17, sx + sw, y + 18, sc);
        g.fill(sx, y + 6, sx + 1, y + 18, sc);
        g.fill(sx + sw - 1, y + 6, sx + sw, y + 18, sc);
        small(g, Component.literal(state), sx + 4, y + 9, sc & 0xFFFFFF);
        // the dial: a ring of ticks, the swept part lit, the needle at the limit
        int cx = x + DIAL_X, cy = y + DIAL_Y;
        for (int i = 0; i <= 60; i++) {
            double a = -SWEEP / 2 + SWEEP * i / 60.0;
            boolean major = i % 6 == 0, lit = i / 60.0 <= limit / (double) max + 1e-6;
            int r0 = major ? DIAL_R - 6 : DIAL_R - 3;
            for (int r = r0; r <= DIAL_R; r += 2) {
                dot(g, cx + Math.sin(a) * r, cy - Math.cos(a) * r, 2, lit ? (open ? ION : BAD) : DIM);
            }
        }
        double na = -SWEEP / 2 + SWEEP * Mth.clamp(limit / (double) max, 0, 1);
        for (int r = 0; r <= DIAL_R - 10; r += 2) {
            dot(g, cx + Math.sin(na) * r, cy - Math.cos(na) * r, 3, open ? ION : BAD);
        }
        dot(g, cx, cy, 6, TQ);
        small(g, Component.literal("0"), cx - DIAL_R - 4, cy + DIAL_R - 10, MUTED);
        small(g, Component.literal(Integer.toString(max)), cx + DIAL_R - 14, cy + DIAL_R - 10, MUTED);
        // the right column: the limit, the typed box, the nudges, the redstone switch, what passes
        small(g, tr("limit"), x + RIGHT, y + 24, MUTED);
        g.drawString(font, limit + " mB/t", x + RIGHT + 62, y + 36, open ? ION & 0xFFFFFF : TEXT, false);
        int by = y + 54;
        String[] labels = {"-100", "-10", "+10", "+100"};
        for (int i = 0; i < labels.length; i++) {
            button(g, x + RIGHT + i * 25, by, 23, labels[i], TQ, false);
        }
        button(g, x + RIGHT, by + 15, 48, tr("max").getString(), TQ, limit >= max);
        button(g, x + RIGHT + 50, by + 15, 48, tr("shut").getString(), BAD, limit <= 0);
        button(g, x + RIGHT, y + 88, 98, tr(redstone ? "redstone_on" : "redstone_off").getString(), redstone ? GOLD : TQ, redstone);
        if (v != null) {
            small(g, tr("passing", v.in(), v.out()), x + RIGHT, y + 106, v.out() > 0 ? TEXT : MUTED);
        }
        small(g, tr(redstone ? "hint_redstone" : "hint_free"), x + 10, y + H - 12, MUTED);
        super.render(g, mouseX, mouseY, partialTick);
    }
}
