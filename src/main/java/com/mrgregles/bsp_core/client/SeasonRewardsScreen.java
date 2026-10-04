package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.admin.SeasonData;
import com.mrgregles.bsp_core.admin.SeasonRewardsMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.Locale;

/** The season prize rows (first, second, third place; ten slots each) above the admin's own inventory, in the violet Holo style. */
public class SeasonRewardsScreen extends AbstractContainerScreen<SeasonRewardsMenu> {
    private static final int BG = 0xF010151C, SLOT_BG = 0xFF0C0E12, VIO = 0xFFB58CFF, MUTED = 0x9AA3B5;
    private static final int[] PLACE = {0xFFFFD23A, 0xFFC9CED8, 0xFFC87A3C, 0xFF19D3B0};

    public SeasonRewardsScreen(SeasonRewardsMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = SeasonRewardsMenu.WIDTH;
        this.imageHeight = SeasonRewardsMenu.HEIGHT;
        this.inventoryLabelX = SeasonRewardsMenu.INV_X;
        this.inventoryLabelY = SeasonRewardsMenu.INV_Y - 11;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, VIO);
        g.fill(x, y, x + imageWidth, y + imageHeight, BG);
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            int edge = i < SeasonData.REWARD_SLOTS ? PLACE[i / SeasonData.PER_PLACE] & 0x99FFFFFF | 0x99000000 : 0xFF3A3F4B;
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, edge);
            g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, SLOT_BG);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title.getString().toUpperCase(Locale.ROOT), 10, 8, VIO & 0xFFFFFF, false);
        if (!menu.editable()) {
            Component tag = Component.translatable("gui.bsp_core.admin.read_only");
            g.drawString(font, tag, imageWidth - 10 - font.width(tag), 8, 0xFFD23A, false);
        }
        for (int place = 0; place < SeasonData.ROWS; place++) {
            g.drawString(font, Component.translatable("gui.bsp_core.rewards.place." + place), 10, SeasonRewardsMenu.ROW_Y + 4 + place * SeasonRewardsMenu.ROW_GAP, PLACE[place] & 0xFFFFFF, false);
        }
        g.pose().pushPose();
        g.pose().translate(10, SeasonRewardsMenu.ROW_Y + SeasonData.ROWS * SeasonRewardsMenu.ROW_GAP, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, Component.translatable("gui.bsp_core.rewards.note"), 0, 0, MUTED, false);
        g.pose().popPose();
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
    }
}
