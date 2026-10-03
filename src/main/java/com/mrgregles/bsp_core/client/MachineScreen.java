package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.coin.CoinFactoryBlockEntity.SideMode;
import com.mrgregles.bsp_core.machine.MachineBlockEntity;
import com.mrgregles.bsp_core.machine.MachineMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Shared screen for the single-block machines: slots from the machine's layout, progress, fuel, side modes. */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final int BG = 0xF0171A21, BORDER = 0xFF8A909C, SLOT_BG = 0xFF0C0E12, SLOT_EDGE = 0xFF3A3F4B;
    private static final int BAR_X = 68, BAR_Y = 36, BAR_W = 34, BAR_H = 6, SIDES_Y = 84;
    private final Button[] sideButtons = new Button[6];

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = MachineMenu.INV_Y + 82;
        this.inventoryLabelY = MachineMenu.INV_Y - 10;
    }

    @Override
    protected void init() {
        super.init();
        for (Direction d : Direction.values()) {
            int id = d.get3DDataValue();
            sideButtons[id] = Button.builder(Component.empty(), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id))
                    .bounds(leftPos + 7 + (id % 3) * 54, topPos + SIDES_Y + (id / 3) * 15, 54, 14).build();
            addRenderableWidget(sideButtons[id]);
        }
        refreshSides();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshSides();
    }

    private void refreshSides() {
        for (Direction d : Direction.values()) {
            SideMode mode = menu.side(d);
            String name = d.getName().substring(0, 1).toUpperCase() + d.getName().substring(1);
            sideButtons[d.get3DDataValue()].setMessage(Component.translatable("gui.bsp_core.factory.side", name,
                    Component.translatable("gui.bsp_core.factory.mode." + mode.name().toLowerCase())));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (menu.fluidCapacity() > 0 && mouseX >= leftPos + 10 && mouseX < leftPos + 20 && mouseY >= topPos + 20 && mouseY < topPos + 76) {
            g.renderTooltip(font, Component.translatable("gui.bsp_core.machine.tank", String.format("%,d", menu.fluid()), String.format("%,d", menu.fluidCapacity())), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, BORDER);
        g.fill(x, y, x + imageWidth, y + imageHeight, BG);
        for (Slot slot : menu.slots) {
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, SLOT_EDGE);
            g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, SLOT_BG);
        }
        g.fill(x + BAR_X - 1, y + BAR_Y - 1, x + BAR_X + BAR_W + 1, y + BAR_Y + BAR_H + 1, SLOT_EDGE);
        g.fill(x + BAR_X, y + BAR_Y, x + BAR_X + BAR_W, y + BAR_Y + BAR_H, SLOT_BG);
        g.fill(x + BAR_X, y + BAR_Y, x + BAR_X + Math.round(BAR_W * menu.progress()), y + BAR_Y + BAR_H, 0xFFB58CFF);

        MachineBlockEntity machine = menu.getMachine();
        if (menu.fluidCapacity() > 0) {
            int tx = x + 10, ty = y + 20, th = 56;
            g.fill(tx - 1, ty - 1, tx + 11, ty + th + 1, SLOT_EDGE);
            g.fill(tx, ty, tx + 10, ty + th, SLOT_BG);
            int lvl = Math.round(th * Math.min(1f, menu.fluid() / (float) menu.fluidCapacity()));
            g.fill(tx, ty + th - lvl, tx + 10, ty + th, machine == null ? 0xFF3A8BFF : machine.fluidColor());
        }
        if (menu.energyPermille() >= 0) {
            int ex = x + 158, ey = y + 20, eh = 36;
            g.fill(ex - 1, ey - 1, ex + 5, ey + eh + 1, SLOT_EDGE);
            g.fill(ex, ey, ex + 4, ey + eh, SLOT_BG);
            int lit = Math.round(eh * menu.energyPermille() / 1000f);
            g.fill(ex, ey + eh - lit, ex + 4, ey + eh, 0xFFFFD23A);
        }
        if (machine != null && machine.hasFuelSlot()) {
            // flame gauge between the ore and fuel slots
            int fx = x + 49, fy = y + 42, fh = 13;
            g.fill(fx - 1, fy - 1, fx + 7, fy + fh + 1, SLOT_EDGE);
            g.fill(fx, fy, fx + 6, fy + fh, SLOT_BG);
            int lit = Math.round(fh * menu.burn());
            g.fill(fx, fy + fh - lit, fx + 6, fy + fh, 0xFFFF7A1A);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, 0xE3B341, false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0x9AA3B5, false);
        Component status = !menu.formed()
                ? Component.translatable("gui.bsp_core.machine.unformed").withStyle(ChatFormatting.RED)
                : menu.working()
                ? Component.translatable("gui.bsp_core.machine.working", menu.secondsLeft()).withStyle(ChatFormatting.AQUA)
                : Component.translatable("gui.bsp_core.machine.idle").withStyle(ChatFormatting.GRAY);
        g.drawString(font, status, imageWidth - 8 - font.width(status), 6, 0xFFFFFF, false);
    }
}
