package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.admin.SeasonData;
import com.mrgregles.bsp_core.admin.SeasonRewardsMenu;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.SeasonGroupPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.Locale;

/**
 * The season prize rows (first, second, third place; ten slots each) above the admin's own inventory, in the violet Holo style. Each
 * place row ends in a box for the LuckPerms group its winner is given (owner pick P2 A): Enter, clicking away or closing saves it.
 */
public class SeasonRewardsScreen extends AbstractContainerScreen<SeasonRewardsMenu> implements ScreenFit.Fitted {
    private static final int BG = 0xF010151C, SLOT_BG = 0xFF0C0E12, VIO = 0xFFB58CFF, MUTED = 0x9AA3B5;
    private static final int[] PLACE = {0xFFFFD23A, 0xFFC9CED8, 0xFFC87A3C, 0xFF19D3B0};
    private final EditBox[] groups = new EditBox[SeasonData.PLACES];
    private final String[] saved = new String[SeasonData.PLACES];

    public SeasonRewardsScreen(SeasonRewardsMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = SeasonRewardsMenu.WIDTH;
        this.imageHeight = SeasonRewardsMenu.HEIGHT;
        this.inventoryLabelX = SeasonRewardsMenu.INV_X;
        this.inventoryLabelY = SeasonRewardsMenu.INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < groups.length; i++) {
            String now = groups[i] != null ? groups[i].getValue() : menu.groups[i];
            if (saved[i] == null) {
                saved[i] = menu.groups[i];
            }
            EditBox box = new EditBox(font, leftPos + SeasonRewardsMenu.GROUP_X + 1, topPos + SeasonRewardsMenu.ROW_Y + i * SeasonRewardsMenu.ROW_GAP + 3,
                    SeasonRewardsMenu.GROUP_W - 2, 12, Component.translatable("gui.bsp_core.rewards.group_hint"));
            box.setMaxLength(SeasonData.GROUP_MAX);
            box.setFilter(t -> t.matches("[A-Za-z0-9_.-]*"));
            box.setValue(now);
            box.setHint(Component.translatable("gui.bsp_core.rewards.group_hint").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            box.setTextColor(0xE9E1FF);
            box.setEditable(menu.editable());
            box.setTooltip(Tooltip.create(Component.translatable("gui.bsp_core.rewards.group_tip")));
            groups[i] = box;
            addRenderableWidget(box);
        }
    }

    /** Sends a place's group if it changed since it was last sent. */
    private void save(int i) {
        if (!menu.editable() || groups[i] == null) {
            return;
        }
        String g = groups[i].getValue().trim();
        if (!g.equals(saved[i]) && SeasonData.validGroup(g)) {
            saved[i] = g;
            BSPNetwork.CHANNEL.sendToServer(new SeasonGroupPacket(i, g));
        }
    }

    private void saveAll() {
        for (int i = 0; i < groups.length; i++) {
            save(i);
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        for (int i = 0; i < groups.length; i++) {
            EditBox box = groups[i];
            if (box != null && box.isFocused()) {
                if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                    save(i);
                    box.setFocused(false);
                    setFocused(null);
                    return true;
                }
                if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                    return super.keyPressed(key, scan, mods);
                }
                box.keyPressed(key, scan, mods);
                return true; // typing in a box never closes the screen or drops an item
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (EditBox box : groups) {
            if (box != null && box.isFocused() && !box.isMouseOver(mx, my)) {
                box.setFocused(false);
                setFocused(null);
            }
        }
        saveAll();
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void onClose() {
        saveAll(); // before the close packet: the server takes a group only while this screen is open there
        super.onClose();
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
        // the group box frames, in each place's colour
        for (int place = 0; place < SeasonData.PLACES; place++) {
            int bx = x + SeasonRewardsMenu.GROUP_X, by = y + SeasonRewardsMenu.ROW_Y + place * SeasonRewardsMenu.ROW_GAP - 1;
            g.fill(bx - 1, by - 1, bx + SeasonRewardsMenu.GROUP_W + 1, by + 19, PLACE[place] & 0x99FFFFFF | 0x99000000);
            g.fill(bx, by, bx + SeasonRewardsMenu.GROUP_W, by + 18, 0xFF05070A);
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

    @Override
    public int panelWidth() {
        return imageWidth;
    }

    @Override
    public int panelHeight() {
        return imageHeight;
    }
}
