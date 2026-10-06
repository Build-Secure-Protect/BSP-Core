package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.ChunkActionPacket;
import com.mrgregles.bsp_core.network.ChunkViewPacket;
import com.mrgregles.bsp_core.projector.TotemProjectorBlockEntity;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;

/**
 * The Projector's screen ("Three Tabs"): POWERS lists what the interface offers, what would arrive
 * and a RECEIVE switch per power; CHUNKS is the chunk picker; STATUS shows the plasma arriving
 * against what is needed, the run, and the Channel Expander. The server refreshes it once a second.
 */
public class ProjectorScreen extends Screen {
    private static final int W = 300, H = 200, TILE = 20, TAB_W = 50, TAB_X = W - 10 - 3 * (TAB_W + 2) + 2, ROW = 14, LIST_Y = 36, BTN_X = 206, BTN_W = 84;
    private static final int BG = 0xF010151C, SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, TQ = 0xFF19D3B0, GOLD = 0xFFFFD23A, MUTED = 0x9AA3B5, TEXT = 0xE6EAF2, BAD = 0xFF6B5C, ION = 0xFF4FB8FF;
    private static final String[] TABS = {"powers", "chunks", "status"};
    private final BlockPos pos;
    private int tab, ticks;

    public ProjectorScreen(BlockPos pos) {
        super(Component.translatable("block.bsp_core.totem_projector"));
        this.pos = pos;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (minecraft == null || minecraft.level == null || !(minecraft.level.getBlockEntity(pos) instanceof TotemProjectorBlockEntity)) {
            onClose();
        } else if (++ticks % 20 == 0) {
            ChunkMap.request(pos);
        }
    }

    private static int anchorIndex() {
        for (int i = 0; i < TotemProjectorBlockEntity.SENDABLE.length; i++) {
            if (TotemProjectorBlockEntity.SENDABLE[i] == Buff.ANCHOR) {
                return i;
            }
        }
        return -1;
    }

    private static boolean chunks(ChunkViewPacket v) {
        int i = anchorIndex();
        return i >= 0 && i < v.levels().length && v.levels()[i] > 0 && v.has(ChunkViewPacket.ENABLED);
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = (width - W) / 2, y = (height - H) / 2;
        ChunkViewPacket v = ChunkMap.viewFor(pos);
        for (int i = 0; i < TABS.length; i++) {
            if (over(mx, my, x + TAB_X + i * (TAB_W + 2), y + 3, TAB_W, 13)) {
                tab = i;
                return true;
            }
        }
        if (v == null) {
            return super.mouseClicked(mx, my, button);
        }
        if (tab == 0 && v.has(ChunkViewPacket.EDIT)) {
            for (int i = 0; i < v.offered().length; i++) {
                boolean on = (v.chosen() & (1 << i)) != 0, can = v.offered()[i] > 0 && (on || Integer.bitCount(v.chosen()) < v.channels());
                if (can && over(mx, my, x + BTN_X, y + LIST_Y + i * ROW - 2, BTN_W, ROW - 2)) {
                    BSPNetwork.CHANNEL.sendToServer(new ChunkActionPacket(pos, ChunkActionPacket.RECEIVE, i, 0));
                    return true;
                }
            }
        }
        if (tab == 1 && chunks(v) && ChunkMap.click(mx, my, x + 10, y + 24, TILE, v)) {
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void small(GuiGraphics g, Component text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.bsp_core.projector." + key, args);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = (width - W) / 2, y = (height - H) / 2;
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, TQ);
        g.fill(x, y, x + W, y + H, BG);
        g.drawString(font, title.getString().toUpperCase(Locale.ROOT), x + 10, y + 7, TQ & 0xFFFFFF, false);
        for (int i = 0; i < TABS.length; i++) {
            int tx = x + TAB_X + i * (TAB_W + 2);
            g.fill(tx, y + 3, tx + TAB_W, y + 16, i == tab ? TQ : DIM);
            g.fill(tx + 1, y + 4, tx + TAB_W - 1, y + 15, SLOT_BG);
            Component label = tr("tab." + TABS[i]);
            small(g, label, tx + TAB_W / 2 - font.width(label) * 3 / 8, y + 7, i == tab ? TQ & 0xFFFFFF : MUTED);
        }
        ChunkViewPacket v = ChunkMap.viewFor(pos);
        if (v == null) {
            return;
        }
        Component tip = null;
        if (tab == 0) {
            tip = powers(g, x, y, v, mouseX, mouseY);
        } else if (tab == 1) {
            chunksTab(g, x, y, v, mouseX, mouseY);
        } else {
            status(g, x, y, v);
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (tip != null) {
            g.renderTooltip(font, tip, mouseX, mouseY);
        }
    }

    private Component powers(GuiGraphics g, int x, int y, ChunkViewPacket v, int mouseX, int mouseY) {
        boolean signal = v.has(ChunkViewPacket.SIGNAL), edit = v.has(ChunkViewPacket.EDIT);
        small(g, tr("col.power"), x + 10, y + 24, MUTED);
        small(g, tr("col.offered"), x + 120, y + 24, MUTED);
        small(g, tr("col.arrives"), x + 160, y + 24, MUTED);
        Component tip = null;
        for (int i = 0; i < TotemProjectorBlockEntity.SENDABLE.length; i++) {
            Buff b = TotemProjectorBlockEntity.SENDABLE[i];
            int ry = y + LIST_Y + i * ROW, off = i < v.offered().length ? v.offered()[i] : 0, arr = i < v.levels().length ? v.levels()[i] : 0;
            boolean on = (v.chosen() & (1 << i)) != 0, can = edit && off > 0 && (on || Integer.bitCount(v.chosen()) < v.channels());
            g.drawString(font, Component.translatable(b.translationKey()), x + 10, ry, off > 0 ? TEXT : 0x6B7385, false);
            g.drawString(font, off > 0 ? Integer.toString(off) : "-", x + 128, ry, off > 0 ? GOLD & 0xFFFFFF : 0x6B7385, false);
            g.drawString(font, on ? Integer.toString(arr) : "-", x + 172, ry, arr > 0 ? TQ & 0xFFFFFF : on ? BAD : 0x6B7385, false);
            int by = ry - 2;
            g.fill(x + BTN_X, by, x + BTN_X + BTN_W, by + ROW - 2, on ? TQ : can ? 0xFF4A5568 : DIM);
            g.fill(x + BTN_X + 1, by + 1, x + BTN_X + BTN_W - 1, by + ROW - 3, can && over(mouseX, mouseY, x + BTN_X, by, BTN_W, ROW - 2) ? 0xFF14362F : SLOT_BG);
            Component label = tr(off <= 0 ? "not_offered" : on ? "receiving" : "off");
            small(g, label, x + BTN_X + BTN_W / 2 - Math.round(font.width(label) * 0.375f), ry + 1, on ? TQ & 0xFFFFFF : off > 0 ? MUTED : 0x6B7385);
            if (on && arr <= 0 && over(mouseX, mouseY, x + 160, ry, 30, ROW)) {
                tip = tr("lost", v.repeaters());
            }
        }
        int fy = y + LIST_Y + TotemProjectorBlockEntity.SENDABLE.length * ROW + 4;
        small(g, tr(signal ? "channels" : "no_signal_help", Integer.bitCount(v.chosen()), v.channels()), x + 10, fy, signal ? TEXT : BAD);
        small(g, v.repeaters() == 0 ? tr("no_repeaters") : tr("repeaters", v.repeaters()), x + 10, fy + 9, MUTED);
        if (!edit) {
            small(g, tr("chunks.not_owner"), x + 10, fy + 18, MUTED);
        }
        return tip;
    }

    private void chunksTab(GuiGraphics g, int x, int y, ChunkViewPacket v, int mouseX, int mouseY) {
        if (!chunks(v)) {
            small(g, tr("chunks_off"), x + 10, y + 30, MUTED);
            small(g, tr("chunks_off2"), x + 10, y + 39, MUTED);
            return;
        }
        ChunkMap.draw(g, x + 10, y + 24, TILE, v, mouseX, mouseY);
        int dx = x + 10 + TILE * 7 + 10;
        g.drawString(font, tr("chunks", v.used(), v.slots()), dx, y + 26, TEXT, false);
        ChunkMap.pips(g, dx, y + 38, v);
        String hint = !v.has(ChunkViewPacket.EDIT) ? "not_owner" : !v.has(ChunkViewPacket.LOADING) ? "needs_power" : "hint";
        small(g, tr("chunks." + hint), dx, y + 52, v.has(ChunkViewPacket.LOADING) ? MUTED : BAD);
        small(g, tr("chunks.shared", v.radius() * 2 + 1), dx, y + 61, MUTED);
    }

    private void status(GuiGraphics g, int x, int y, ChunkViewPacket v) {
        boolean signal = v.has(ChunkViewPacket.SIGNAL), active = v.has(ChunkViewPacket.ACTIVE);
        int need = BSPConfig.getOr(BSPConfig.PROJECTOR_NEED, 100), cap = BSPConfig.getOr(BSPConfig.PROJECTOR_TANK, 2000);
        g.drawString(font, tr(active ? "active" : signal ? "low_pressure" : "no_signal"), x + 10, y + 26, active ? TQ & 0xFFFFFF : signal ? BAD : GOLD & 0xFFFFFF, false);
        small(g, tr("pressure"), x + 10, y + 42, MUTED);
        g.fill(x + 10, y + 50, x + W - 10, y + 58, DIM);
        g.fill(x + 10, y + 50, x + 10 + Math.round((W - 20) * Mth.clamp(v.delivered() / (float) need, 0, 1)), y + 58, active ? ION : BAD);
        small(g, tr("pressure_text", v.delivered(), need), x + 10, y + 60, TEXT);
        small(g, tr("tank", v.tank(), cap), x + 10, y + 72, MUTED);
        small(g, signal ? tr("channels", Integer.bitCount(v.chosen()), v.channels()) : tr("no_signal_help"), x + 10, y + 86, TEXT);
        small(g, v.repeaters() == 0 ? tr("no_repeaters") : tr("repeaters", v.repeaters()), x + 10, y + 95, MUTED);
        small(g, tr(v.has(ChunkViewPacket.EXPANDER) ? "expander_fitted" : "expander_none"), x + 10, y + 108, v.has(ChunkViewPacket.EXPANDER) ? TQ & 0xFFFFFF : MUTED);
        small(g, tr("expander_help"), x + 10, y + 117, MUTED);
        for (int i = 0; i < 4; i++) {
            small(g, tr("rule." + i), x + 10, y + 136 + i * 9, MUTED);
        }
    }
}
