package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.coin.CoinFactoryBlockEntity;
import com.mrgregles.bsp_core.coin.CoinFactoryMenu;
import com.mrgregles.bsp_core.coin.CoinTier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Shatter Coin Factory screen: blank in, coin out, four gear slots, energy, time left, per-face modes. */
public class CoinFactoryScreen extends AbstractContainerScreen<CoinFactoryMenu> {
    private static final int BG = 0xF0171A21, BORDER = 0xFF8A909C, SLOT_BG = 0xFF0C0E12, SLOT_EDGE = 0xFF3A3F4B;
    private static final int ENERGY_X = 10, ENERGY_Y = 30, ENERGY_W = 10, ENERGY_H = 48;
    private static final int BAR_X = 66, BAR_Y = 41, BAR_W = 46, BAR_H = 6;
    private static final int SIDES_Y = 84;

    private final Button[] sideButtons = new Button[6];

    public CoinFactoryScreen(CoinFactoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = CoinFactoryMenu.INV_Y + 82;
        this.inventoryLabelY = CoinFactoryMenu.INV_Y - 10;
    }

    @Override
    protected void init() {
        super.init();
        for (Direction d : Direction.values()) {
            int id = d.get3DDataValue();
            int col = id % 3, row = id / 3;
            sideButtons[id] = Button.builder(Component.empty(), b -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id))
                    .bounds(leftPos + 7 + col * 54, topPos + SIDES_Y + row * 15, 54, 14).build();
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
            CoinFactoryBlockEntity.SideMode mode = menu.side(d);
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
        int ex = leftPos + ENERGY_X, ey = topPos + ENERGY_Y;
        if (mouseX >= ex && mouseX < ex + ENERGY_W && mouseY >= ey && mouseY < ey + ENERGY_H) {
            g.renderTooltip(font, Component.translatable("gui.bsp_core.factory.energy",
                    String.format("%,d", menu.energy()), String.format("%,d", menu.energyCapacity())), mouseX, mouseY);
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
        // energy
        g.fill(x + ENERGY_X - 1, y + ENERGY_Y - 1, x + ENERGY_X + ENERGY_W + 1, y + ENERGY_Y + ENERGY_H + 1, SLOT_EDGE);
        g.fill(x + ENERGY_X, y + ENERGY_Y, x + ENERGY_X + ENERGY_W, y + ENERGY_Y + ENERGY_H, SLOT_BG);
        int eh = Math.round(ENERGY_H * Math.min(1f, menu.energy() / (float) menu.energyCapacity()));
        g.fill(x + ENERGY_X, y + ENERGY_Y + ENERGY_H - eh, x + ENERGY_X + ENERGY_W, y + ENERGY_Y + ENERGY_H, 0xFFD63B2F);
        // progress
        g.fill(x + BAR_X - 1, y + BAR_Y - 1, x + BAR_X + BAR_W + 1, y + BAR_Y + BAR_H + 1, SLOT_EDGE);
        g.fill(x + BAR_X, y + BAR_Y, x + BAR_X + BAR_W, y + BAR_Y + BAR_H, SLOT_BG);
        g.fill(x + BAR_X, y + BAR_Y, x + BAR_X + Math.round(BAR_W * menu.progress()), y + BAR_Y + BAR_H, 0xFF19D3B0);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, 8, 6, 0xE3B341, false);
        g.drawString(font, playerInventoryTitle, 8, inventoryLabelY, 0x9AA3B5, false);

        CoinFactoryBlockEntity factory = menu.getFactory();
        if (factory != null && !factory.getOwnerName().isEmpty()) {
            Component owner = Component.translatable("gui.bsp_core.factory.owner", factory.getOwnerName(), menu.ownedCount(), BSPConfig.FACTORY_MAX_PER_PLAYER.get());
            g.drawString(font, owner, imageWidth - 8 - font.width(owner), 6, 0x9AA3B5, false);
        }

        CoinTier tier = menu.jobTier();
        Component status;
        if (tier == null) {
            status = Component.translatable("gui.bsp_core.factory.idle").withStyle(ChatFormatting.GRAY);
        } else if (menu.progress() >= 1f) {
            status = Component.translatable("gui.bsp_core.factory.blocked").withStyle(ChatFormatting.RED);
        } else {
            status = Component.translatable("gui.bsp_core.factory.pressing", Component.translatable("tier.bsp_core." + tier.key), duration(menu.remainingSeconds()))
                    .withStyle(ChatFormatting.AQUA);
        }
        g.drawString(font, status, 26, 20, 0xFFFFFF, false);

        int pct = Math.round(menu.reduction() * 100);
        g.drawString(font, Component.translatable("gui.bsp_core.factory.speed", pct), 26, CoinFactoryMenu.UPGRADE_Y - 9 + 13, 0x9AA3B5, false);
    }

    static String duration(long seconds) {
        long d = seconds / 86400, h = (seconds % 86400) / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (d > 0) return d + "d " + h + "h " + m + "m";
        if (h > 0) return h + "h " + m + "m " + s + "s";
        return m + "m " + s + "s";
    }
}
