package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.link.AcceleratorBlockEntity;
import com.mrgregles.bsp_core.link.Links;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.LinkActionPacket;
import com.mrgregles.bsp_core.network.LinkViewPacket;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Plasma Accelerator's screen, the Worlds board: a column for every world in play, the accelerator as a gold pip in its own
 * column, each link as a pip in the column of its world with a line drawn to it from the accelerator, lit while plasma passes and
 * grey and dashed while the link sleeps. Click a link to select it; UNLINK takes its card out at the far end, WAKE loads every
 * sleeping far side for a moment. The server refreshes the board once a second.
 */
public class AcceleratorScreen extends Screen implements ScreenFit.Fitted {
    private static final int W = 360, H = 206, TOP_ROW = 46, PIP = 8;
    private static final int BG = 0xF010151C, SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, LINE = 0xFF1F2430, TQ = 0xFF19D3B0, GOLD = 0xFFFFD23A, MUTED = 0x9AA3B5, TEXT = 0xE6EAF2, BAD = 0xFF6B5C, ION = 0xFF4FB8FF, GREEN = 0xFF58F29A;
    @Nullable
    private static LinkViewPacket view;
    private final BlockPos pos;
    private int ticks, selected = -1;

    public AcceleratorScreen(BlockPos pos) {
        super(Component.translatable("block.bsp_core.accelerator_intake"));
        this.pos = pos;
    }

    public static void receive(LinkViewPacket msg) {
        view = msg;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AcceleratorScreen s && s.pos.equals(msg.pos())) {
            s.take(msg);
        } else if (!(mc.screen instanceof AcceleratorScreen)) {
            AcceleratorScreen s = new AcceleratorScreen(msg.pos());
            mc.setScreen(s);
            s.take(msg);
        }
    }

    private void take(LinkViewPacket msg) {
        if (selected >= msg.links().size()) {
            selected = -1;
        }
    }

    @Nullable
    private LinkViewPacket v() {
        return view != null && view.pos().equals(pos) ? view : null;
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (minecraft == null || minecraft.level == null || !(minecraft.level.getBlockEntity(pos) instanceof AcceleratorBlockEntity)) {
            onClose();
            return;
        }
        if (++ticks % 20 == 0) {
            BSPNetwork.CHANNEL.sendToServer(new LinkActionPacket(pos, LinkActionPacket.VIEW, 0));
        }
    }

    // ---- the board's layout

    private static final List<String> FIRST = List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end");

    /** The worlds with a column, the vanilla three first in their order, then the rest alphabetically; only worlds in play. */
    private static List<String> columns(LinkViewPacket v) {
        List<String> all = new ArrayList<>();
        all.add(v.world());
        for (LinkViewPacket.Link l : v.links()) {
            if (!all.contains(l.world())) {
                all.add(l.world());
            }
        }
        all.sort((a, b) -> {
            int ia = FIRST.indexOf(a), ib = FIRST.indexOf(b);
            if (ia >= 0 || ib >= 0) {
                return Integer.compare(ia < 0 ? 99 : ia, ib < 0 ? 99 : ib);
            }
            return a.compareTo(b);
        });
        return all;
    }

    private static Component worldName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? Component.literal(id) : Links.worldName(ResourceKey.create(Registries.DIMENSION, rl));
    }

    private int colW(int n) {
        return (W - 20) / Math.max(1, n);
    }

    /** Centre x of column {@code i}. */
    private int colX(int n, int i) {
        return left() + 10 + i * colW(n) + colW(n) / 2;
    }

    /** Where each link's pip sits: stacked down its world's column, under the accelerator's pip when it shares the column. */
    private int[][] pips(LinkViewPacket v, List<String> cols) {
        int n = cols.size();
        int[] count = new int[n];
        int[][] out = new int[v.links().size()][2];
        int home = cols.indexOf(v.world());
        count[home] = 1;
        int step = step(v, cols);
        for (int i = 0; i < v.links().size(); i++) {
            int c = Math.max(0, cols.indexOf(v.links().get(i).world()));
            out[i][0] = colX(n, c);
            out[i][1] = top() + TOP_ROW + 22 + count[c] * step;
            count[c]++;
        }
        return out;
    }

    /** Space between pips in a column: 34, closer when a crowded column would run into the text at the foot. */
    private int step(LinkViewPacket v, List<String> cols) {
        int[] count = new int[Math.max(1, cols.size())];
        int home = Math.max(0, cols.indexOf(v.world()));
        count[home] = 1;
        int most = 1;
        for (LinkViewPacket.Link l : v.links()) {
            int c = Math.max(0, cols.indexOf(l.world()));
            most = Math.max(most, ++count[c]);
        }
        int room = H - 52 - 21 - (TOP_ROW + 22); // from the first pip to where the last one's labels must end
        return most <= 1 ? 34 : Math.min(34, room / (most - 1));
    }

    private int[] homePip(LinkViewPacket v, List<String> cols) {
        return new int[]{colX(cols.size(), Math.max(0, cols.indexOf(v.world()))), top() + TOP_ROW + 22};
    }

    private static Links.State state(LinkViewPacket.Link l) {
        Links.State[] all = Links.State.values();
        return all[Math.max(0, Math.min(all.length - 1, l.state()))];
    }

    private static int stateColour(Links.State s) {
        return switch (s) {
            case DELIVERING -> GREEN;
            case IDLE -> MUTED | 0xFF000000;
            default -> BAD;
        };
    }

    // ---- drawing

    private void small(GuiGraphics g, Component text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    /** A small line centred on {@code cx}, shrunk if it would not fit {@code maxW}. */
    private void smallCentred(GuiGraphics g, Component text, int cx, int y, int maxW, int colour) {
        float scale = 0.75f;
        int w = font.width(text);
        if (w * scale > maxW) {
            scale = Math.max(0.5f, maxW / (float) w);
        }
        g.pose().pushPose();
        g.pose().translate(cx - w * scale / 2f, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private void dot(GuiGraphics g, double x, double y, int size, int colour) {
        g.fill((int) Math.round(x) - size / 2, (int) Math.round(y) - size / 2, (int) Math.round(x) + (size + 1) / 2, (int) Math.round(y) + (size + 1) / 2, colour);
    }

    private void button(GuiGraphics g, int x, int y, int w, String text, int colour, boolean on) {
        g.fill(x, y, x + w, y + 12, SLOT_BG);
        int edge = on ? colour : DIM;
        g.fill(x, y, x + w, y + 1, edge);
        g.fill(x, y + 11, x + w, y + 12, edge);
        g.fill(x, y, x + 1, y + 12, edge);
        g.fill(x + w - 1, y, x + w, y + 12, edge);
        g.pose().pushPose();
        g.pose().translate(x + w / 2f - font.width(text) * 0.375f, y + 3, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, on ? colour & 0xFFFFFF : MUTED, false);
        g.pose().popPose();
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("gui.bsp_core.accelerator." + key, args);
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** The line from the accelerator to a link: dots along a curve that bows upward, every other dot while the link sleeps. */
    private void curve(GuiGraphics g, int x0, int y0, int x1, int y1, int colour, boolean dashed) {
        double cx = (x0 + x1) / 2.0, cy = Math.min(y0, y1) - 18;
        int steps = 28;
        for (int i = 1; i < steps; i++) {
            if (dashed && i % 2 == 0) {
                continue;
            }
            double t = i / (double) steps, u = 1 - t;
            double x = u * u * x0 + 2 * u * t * cx + t * t * x1, y = u * u * y0 + 2 * u * t * cy + t * t * y1;
            dot(g, x, y, 2, colour);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = left(), y = top();
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, TQ);
        g.fill(x, y, x + W, y + H, BG);
        g.drawString(font, title.getString().toUpperCase(Locale.ROOT), x + 10, y + 8, TQ & 0xFFFFFF, false);
        LinkViewPacket v = v();
        if (v == null) {
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        // the status line: in, out, RF; then the ring's state on the right
        CashMachine.fit(g, font, tr("status", v.in(), v.out(), String.format("%,d", v.energy()), String.format("%,d", v.energyCap())).getString(), x + 10, y + 22, W - 20, MUTED);
        Component ring = v.formed() ? tr("links", v.links().size(), v.maxLinks()) : tr("unformed");
        CashMachine.fitRight(g, font, ring.getString(), x + W - 10, y + 8, 150, v.formed() ? ION & 0xFFFFFF : BAD & 0xFFFFFF);
        // the columns
        List<String> cols = columns(v);
        int n = cols.size(), cw = colW(n);
        for (int i = 0; i < n; i++) {
            int cx = colX(n, i);
            smallCentred(g, worldName(cols.get(i)), cx, y + TOP_ROW - 10, cw - 6, MUTED);
            if (i > 0) {
                g.fill(x + 10 + i * cw, y + TOP_ROW, x + 10 + i * cw + 1, y + H - 52, LINE);
            }
        }
        g.fill(x + 10, y + TOP_ROW - 1, x + W - 10, y + TOP_ROW, LINE);
        int[] home = homePip(v, cols);
        int[][] at = pips(v, cols);
        int step = step(v, cols);
        // lines first, pips over them
        for (int i = 0; i < at.length; i++) {
            Links.State s = state(v.links().get(i));
            boolean on = s == Links.State.DELIVERING;
            curve(g, home[0], home[1], at[i][0], at[i][1], on ? ION : DIM | 0xFF5A6272, !s.live());
        }
        dot(g, home[0], home[1], PIP + 2, GOLD);
        dot(g, home[0], home[1], PIP - 2, GOLD);
        smallCentred(g, tr("here"), home[0], home[1] + 7, cw - 6, GOLD & 0xFFFFFF);
        List<Component> hover = null;
        for (int i = 0; i < at.length; i++) {
            LinkViewPacket.Link l = v.links().get(i);
            Links.State s = state(l);
            boolean sel = i == selected, isOver = over(mouseX, mouseY, at[i][0] - 7, at[i][1] - 7, 14, 14);
            int edge = s == Links.State.DELIVERING ? ION : s.live() ? MUTED | 0xFF000000 : DIM | 0xFF5A6272;
            dot(g, at[i][0], at[i][1], PIP + (sel || isOver ? 4 : 0), sel ? GOLD : edge);
            dot(g, at[i][0], at[i][1], PIP - 4 + (sel || isOver ? 4 : 0), sel ? GOLD : BG);
            boolean roomy = step >= 24; // crowded columns keep only the state under each pip; the hover still names the place
            if (roomy) {
                smallCentred(g, Component.literal(l.pos().getX() + " " + l.pos().getY() + " " + l.pos().getZ()), at[i][0], at[i][1] + 7, cw - 6, TEXT);
            }
            Component stateText = s == Links.State.DELIVERING ? tr("rate", l.rate()) : Component.translatable(s.key());
            smallCentred(g, stateText, at[i][0], at[i][1] + (roomy ? 15 : 7), cw - 6, stateColour(s) & 0xFFFFFF);
            if (isOver) {
                hover = List.of(Component.literal(worldName(l.world()).getString() + " " + l.pos().getX() + ", " + l.pos().getY() + ", " + l.pos().getZ()),
                        Component.translatable(s.key()).append(s == Links.State.DELIVERING ? ", " + tr("rate", l.rate()).getString() : ""));
            }
        }
        // what the plasma carries, then the keys: the two link keys together, DONE apart
        MutableComponent carries;
        if (v.powers().isEmpty()) {
            carries = tr("carries_none");
        } else {
            MutableComponent list = Component.empty();
            TotemUpgrades.Buff[] buffs = TotemUpgrades.Buff.values();
            boolean first = true;
            for (LinkViewPacket.Power p : v.powers()) {
                if (p.ordinal() < 0 || p.ordinal() >= buffs.length) {
                    continue;
                }
                if (!first) {
                    list.append(", ");
                }
                first = false;
                list.append(Component.translatable(buffs[p.ordinal()].translationKey())).append(" " + TotemUpgrades.roman(p.level()));
            }
            carries = tr("carries", list);
        }
        CashMachine.fit(g, font, carries.getString(), x + 10, y + H - 46, W - 20, v.powers().isEmpty() ? MUTED : GOLD & 0xFFFFFF);
        CashMachine.fit(g, font, tr("hint").getString(), x + 10, y + H - 35, W - 20, 0x5A6272);
        int ky = y + H - 20;
        button(g, x + 10, ky, 62, tr("unlink").getString(), BAD, selected >= 0);
        button(g, x + 76, ky, 62, tr("wake").getString(), ION, true);
        button(g, x + W - 72, ky, 62, tr("done").getString(), TQ, true);
        if (hover != null) {
            g.renderComponentTooltip(font, hover, mouseX, mouseY);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = left(), y = top();
        LinkViewPacket v = v();
        if (v == null) {
            return super.mouseClicked(mx, my, button);
        }
        List<String> cols = columns(v);
        int[][] at = pips(v, cols);
        for (int i = 0; i < at.length; i++) {
            if (over(mx, my, at[i][0] - 7, at[i][1] - 7, 14, 24)) {
                selected = selected == i ? -1 : i;
                return true;
            }
        }
        int ky = y + H - 20;
        if (over(mx, my, x + 10, ky, 62, 12) && selected >= 0) {
            BSPNetwork.CHANNEL.sendToServer(new LinkActionPacket(pos, LinkActionPacket.UNLINK, selected));
            selected = -1;
            return true;
        }
        if (over(mx, my, x + 76, ky, 62, 12)) {
            BSPNetwork.CHANNEL.sendToServer(new LinkActionPacket(pos, LinkActionPacket.WAKE, 0));
            return true;
        }
        if (over(mx, my, x + W - 72, ky, 62, 12)) {
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
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
