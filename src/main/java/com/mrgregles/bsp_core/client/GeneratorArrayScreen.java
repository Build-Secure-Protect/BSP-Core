package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.GeneratorArrayPacket;
import com.mrgregles.bsp_core.network.GeneratorOpenPacket;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Shown when a player right-clicks a Totem Generator that is joined to others: the three by three
 * under the totem seen from above (north up), with a lit square where a generator stands. Clicking
 * a generator opens that generator's own screen. The one that was right-clicked is outlined in gold.
 */
public class GeneratorArrayScreen extends Screen {
    private static final int W = 176, H = 170, CELL = 36, GRID_X = (W - CELL * 3) / 2, GRID_Y = 30, BG = 0xF010151C, SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, TQ = 0xFF19D3B0,
            GOLD = 0xFFFFD23A, MUTED = 0x9AA3B5;
    private final GeneratorArrayPacket data;

    private GeneratorArrayScreen(GeneratorArrayPacket data) {
        super(Component.translatable("gui.bsp_core.generator.array"));
        this.data = data;
    }

    public static void open(GeneratorArrayPacket data) {
        Minecraft.getInstance().setScreen(new GeneratorArrayScreen(data));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean has(int col, int row) {
        return (data.mask() & (1 << (row * 3 + col))) != 0;
    }

    private BlockPos at(int col, int row) {
        return data.centre().offset(col - 1, 0, row - 1);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = (width - W) / 2 + GRID_X, y = (height - H) / 2 + GRID_Y;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (has(col, row) && mx >= x + col * CELL && mx < x + (col + 1) * CELL && my >= y + row * CELL && my < y + (row + 1) * CELL) {
                    BSPNetwork.CHANNEL.sendToServer(new GeneratorOpenPacket(at(col, row)));
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = (width - W) / 2, y = (height - H) / 2, n = Integer.bitCount(data.mask());
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, TQ);
        g.fill(x, y, x + W, y + H, BG);
        g.drawString(font, title.getString().toUpperCase(java.util.Locale.ROOT), x + 10, y + 9, TQ & 0xFFFFFF, false);
        Component count = Component.translatable("gui.bsp_core.generator.array.count", n);
        g.drawString(font, count, x + W - 10 - font.width(count), y + 9, MUTED, false);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int cx = x + GRID_X + col * CELL, cy = y + GRID_Y + row * CELL;
                boolean here = has(col, row), hover = here && mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL;
                g.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, !here ? DIM : at(col, row).equals(data.clicked()) ? GOLD : TQ);
                g.fill(cx + 2, cy + 2, cx + CELL - 2, cy + CELL - 2, hover ? 0xFF14362F : SLOT_BG);
                if (here) {
                    g.renderItem(new ItemStack(col == 1 && row == 1 ? ModItems.SHATTER_TOTEM.get() : ModItems.PROJECTOR_ITEMS.get(0).get()), cx + 10, cy + 10);
                }
            }
        }
        Component north = Component.translatable("gui.bsp_core.generator.array.north");
        g.drawString(font, north, x + W / 2 - font.width(north) / 2, y + GRID_Y - 10, MUTED, false);
        Component hint = Component.translatable("gui.bsp_core.generator.array.hint");
        g.drawString(font, hint, x + W / 2 - font.width(hint) / 2, y + H - 16, MUTED, false);
        super.render(g, mouseX, mouseY, partialTick);
    }
}
