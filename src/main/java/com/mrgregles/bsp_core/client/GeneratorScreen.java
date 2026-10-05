package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.projector.GeneratorMenu;
import com.mrgregles.bsp_core.projector.GeneratorUpgradeItem;
import com.mrgregles.bsp_core.projector.TotemGeneratorBlockEntity;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Totem Generator screen, "Power List" layout. Left: the totem's base powers, each with its level
 * on the totem, the level that arrives at the projector, and a switch. Below them the sockets for
 * three Reach Amplifiers and the Channel Expander. Right: the state of the link, the RF buffer,
 * and the cable run against the generator's reach and the cable's.
 */
public class GeneratorScreen extends AbstractContainerScreen<GeneratorMenu> {
    private static final int BG = 0xF010151C, SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, TQ = 0xFF19D3B0, GOLD = 0xFFFFD23A, BAD = 0xFFD63B2F, MUTED = 0x9AA3B5, TEXT = 0xE6EAF2,
            GHOST = 0xB010151C;
    private static final int LIST_Y = 34, ROW = 14, BTN_X = 122, BTN_W = 52, STAT_X = 186, BAR_W = 104;
    private static final String[] STATE = {"no_totem", "no_projector", "too_far", "no_power", "nothing", "sending"};

    public GeneratorScreen(GeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = GeneratorMenu.WIDTH;
        this.imageHeight = GeneratorMenu.HEIGHT;
        this.inventoryLabelX = GeneratorMenu.INV_X;
        this.inventoryLabelY = GeneratorMenu.INV_Y - 11;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.bsp_core.generator." + key, args);
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean canSwitch(int i) {
        return menu.chosen(i) || (menu.totemLevel(i) > 0 && menu.chosenCount() < menu.channels());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < TotemGeneratorBlockEntity.SENDABLE.length; i++) {
            if (canSwitch(i) && over(mx, my, leftPos + BTN_X, topPos + LIST_Y + i * ROW - 2, BTN_W, ROW - 2)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
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
        g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, TQ);
        g.fill(x, y, x + imageWidth, y + imageHeight, BG);
        for (int i = 0; i < TotemGeneratorBlockEntity.SENDABLE.length; i++) {
            int by = y + LIST_Y + i * ROW - 2;
            boolean on = menu.chosen(i), can = canSwitch(i);
            g.fill(x + BTN_X, by, x + BTN_X + BTN_W, by + ROW - 2, on ? TQ : can ? 0xFF4A5568 : DIM);
            g.fill(x + BTN_X + 1, by + 1, x + BTN_X + BTN_W - 1, by + ROW - 3, can && over(mouseX, mouseY, x + BTN_X, by, BTN_W, ROW - 2) ? 0xFF14362F : SLOT_BG);
        }
        GeneratorUpgradeItem.Kind[] kinds = GeneratorUpgradeItem.Kind.values();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            boolean socket = i < TotemGeneratorBlockEntity.SOCKETS;
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, !socket ? 0xFF3A3F4B : slot.hasItem() ? TQ : 0xFF2F6B60);
            g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, SLOT_BG);
            if (socket && !slot.hasItem()) {
                g.renderFakeItem(new ItemStack(ModItems.GENERATOR_UPGRADES.get(kinds[i]).get()), x + slot.x, y + slot.y);
                g.fill(RenderType.guiGhostRecipeOverlay(), x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, GHOST);
            }
        }
        g.fill(x + STAT_X, y + 46, x + STAT_X + BAR_W, y + 52, DIM);
        g.fill(x + STAT_X, y + 46, x + STAT_X + Math.round(BAR_W * Mth.clamp(menu.rf() / 1000f, 0, 1)), y + 52, GOLD);
    }

    private void small(GuiGraphics g, Component text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title.getString().toUpperCase(Locale.ROOT), 10, 8, TQ & 0xFFFFFF, false);
        small(g, tr("col.power"), 10, 23, MUTED);
        small(g, tr("col.totem"), 82, 23, MUTED);
        small(g, tr("col.arrives"), 100, 23, MUTED);
        for (int i = 0; i < TotemGeneratorBlockEntity.SENDABLE.length; i++) {
            int ry = LIST_Y + i * ROW, lvl = menu.totemLevel(i);
            boolean on = menu.chosen(i);
            g.drawString(font, Component.translatable("buff.bsp_core." + TotemGeneratorBlockEntity.SENDABLE[i].key), 10, ry, lvl > 0 ? TEXT : 0x6B7385, false);
            g.drawString(font, lvl > 0 ? Integer.toString(lvl) : "-", 86, ry, lvl > 0 ? GOLD & 0xFFFFFF : 0x6B7385, false);
            g.drawString(font, on && menu.arriving(i) > 0 ? Integer.toString(menu.arriving(i)) : "-", 106, ry, TQ & 0xFFFFFF, false);
            Component label = tr(lvl <= 0 ? "locked" : on ? "sending" : "off");
            small(g, label, BTN_X + BTN_W / 2 - Math.round(font.width(label) * 0.375f), ry + 1, on ? TQ & 0xFFFFFF : lvl > 0 ? MUTED : 0x6B7385);
        }
        small(g, tr("sockets.reach"), 12, GeneratorMenu.SOCKET_Y - 9, MUTED);
        small(g, tr("sockets.channel"), 84, GeneratorMenu.SOCKET_Y - 9, MUTED);

        int state = Mth.clamp(menu.state(), 0, STATE.length - 1), limit = Math.min(menu.reach(), menu.cableReach());
        g.drawString(font, tr("state." + STATE[state]), STAT_X, 22, state == TotemGeneratorBlockEntity.SENDING ? TQ & 0xFFFFFF : state == TotemGeneratorBlockEntity.NOTHING_CHOSEN ? GOLD & 0xFFFFFF : 0xFF6B5C, false);
        small(g, tr("rf", BSPConfig.getOr(BSPConfig.GENERATOR_RF, 200)), STAT_X, 36, MUTED);
        small(g, tr("channels", menu.chosenCount(), menu.channels()), STAT_X, 60, TEXT);
        small(g, tr("reach", menu.reach()), STAT_X, 70, TEXT);
        if (menu.cableReach() > 0) {
            small(g, tr("cable", menu.cableReach()), STAT_X, 80, TEXT);
            small(g, tr("run", menu.run(), limit), STAT_X, 90, menu.run() > limit ? 0xFF6B5C : TEXT);
            int pct = menu.run() > limit ? 0 : Math.round(100 * (1f - 0.5f * menu.run() / Math.max(1, limit)));
            small(g, tr("strength", pct), STAT_X, 100, TEXT);
        } else {
            small(g, tr("no_cable"), STAT_X, 80, MUTED);
        }
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
    }
}
