package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.network.ChunkViewPacket;
import com.mrgregles.bsp_core.projector.TotemGeneratorBlockEntity;
import com.mrgregles.bsp_core.projector.TotemProjectorBlockEntity;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Locale;

/**
 * The Totem Projector's screen ("Status Strip"): what it is doing and its RF, the powers reaching it
 * from the generator as chips, and, when chunk loading is one of them, a chunk picker centred on the
 * projector. The server refreshes it once a second.
 */
public class ProjectorScreen extends Screen {
    private static final int W = 190, H = 232, TILE = 18, MAP_X = (W - TILE * 7) / 2, MAP_Y = 82, CHIP_W = 24, CHIP_Y = 48;
    private static final int BG = 0xF010151C, SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, TQ = 0xFF19D3B0, GOLD = 0xFFFFD23A, MUTED = 0x9AA3B5, TEXT = 0xE6EAF2, BAD = 0xFF6B5C;
    private final BlockPos pos;
    private int ticks;

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
        for (int i = 0; i < TotemGeneratorBlockEntity.SENDABLE.length; i++) {
            if (TotemGeneratorBlockEntity.SENDABLE[i] == Buff.ANCHOR) {
                return i;
            }
        }
        return -1;
    }

    private static boolean chunks(ChunkViewPacket v) {
        int i = anchorIndex();
        return i >= 0 && i < v.levels().length && v.levels()[i] > 0 && v.has(ChunkViewPacket.ENABLED);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        ChunkViewPacket v = ChunkMap.viewFor(pos);
        if (v != null && chunks(v) && ChunkMap.click(mx, my, (width - W) / 2 + MAP_X, (height - H) / 2 + MAP_Y, TILE, v)) {
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
        g.drawString(font, title.getString().toUpperCase(Locale.ROOT), x + 10, y + 8, TQ & 0xFFFFFF, false);
        ChunkViewPacket v = ChunkMap.viewFor(pos);
        if (v == null) {
            return;
        }
        boolean signal = v.has(ChunkViewPacket.SIGNAL), active = v.has(ChunkViewPacket.ACTIVE);
        g.drawString(font, tr(active ? "active" : signal ? "no_power" : "no_signal"), x + 10, y + 22, active ? TQ & 0xFFFFFF : signal ? BAD : GOLD & 0xFFFFFF, false);
        g.fill(x + 10, y + 34, x + W - 10, y + 40, DIM);
        g.fill(x + 10, y + 34, x + 10 + Math.round((W - 20) * Mth.clamp(v.rf() / (float) TotemProjectorBlockEntity.CAPACITY, 0, 1)), y + 40, GOLD);
        small(g, tr("rf", v.rf(), TotemProjectorBlockEntity.CAPACITY, BSPConfig.getOr(BSPConfig.PROJECTOR_RF, 100)), x + 10, y + 42, MUTED);

        Component tip = null;
        for (int i = 0; i < v.levels().length && i < TotemGeneratorBlockEntity.SENDABLE.length; i++) {
            Buff buff = TotemGeneratorBlockEntity.SENDABLE[i];
            int cx = x + 10 + i * (CHIP_W + 1), cy = y + CHIP_Y + 6, lvl = v.levels()[i];
            int colour = lvl > 0 ? 0xFF000000 | UpgradeOrbColors.auraColor(buff) : DIM;
            g.fill(cx, cy, cx + CHIP_W, cy + 13, colour);
            g.fill(cx + 1, cy + 1, cx + CHIP_W - 1, cy + 12, SLOT_BG);
            small(g, Component.literal(buff.key.substring(0, 2).toUpperCase(Locale.ROOT) + " " + (lvl > 0 ? Integer.toString(lvl) : "-")), cx + 3, cy + 4, lvl > 0 ? colour & 0xFFFFFF : 0x566070);
            if (mouseX >= cx && mouseX < cx + CHIP_W && mouseY >= cy && mouseY < cy + 13) {
                tip = lvl > 0 ? tr("power", Component.translatable(buff.translationKey()), lvl) : tr("power_off", Component.translatable(buff.translationKey()));
            }
        }

        if (!chunks(v)) {
            small(g, tr("chunks_off"), x + 10, y + MAP_Y, MUTED);
            small(g, tr("chunks_off2"), x + 10, y + MAP_Y + 9, MUTED);
        } else {
            g.drawString(font, tr("chunks", v.used(), v.slots()), x + 10, y + MAP_Y - 12, TEXT, false);
            ChunkMap.pips(g, x + W - 10 - Math.min(v.slots(), 12) * 9, y + MAP_Y - 11, v);
            ChunkMap.draw(g, x + MAP_X, y + MAP_Y, TILE, v, mouseX, mouseY);
            String hint = !v.has(ChunkViewPacket.EDIT) ? "not_owner" : !v.has(ChunkViewPacket.LOADING) ? "needs_power" : "hint";
            small(g, tr("chunks." + hint), x + 10, y + MAP_Y + TILE * 7 + 5, v.has(ChunkViewPacket.LOADING) ? MUTED : BAD);
            small(g, tr("chunks.shared", v.radius() * 2 + 1), x + 10, y + MAP_Y + TILE * 7 + 14, MUTED);
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (tip != null) {
            g.renderTooltip(font, tip, mouseX, mouseY);
        }
    }
}
