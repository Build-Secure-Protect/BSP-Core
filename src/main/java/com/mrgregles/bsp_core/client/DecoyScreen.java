package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.decoy.DecoyMenu;
import com.mrgregles.bsp_core.decoy.DecoyTotemBlockEntity;
import com.mrgregles.bsp_core.decoy.DecoyUpgradeItem;
import com.mrgregles.bsp_core.decoy.DecoyUpgradeItem.Kind;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Decoy Totem screen, "Socket Tree" layout: the Magnet Core in the middle and three branches of
 * sockets growing out of it. Range coils go up the middle, trap parts up the left, casings up the
 * right. A socket is dim until the one before it is filled, and shows a faint picture of the part
 * it takes. The column on the right gives the decoy's state, power, range, traps and toughness,
 * and the Repair button when it is broken.
 */
public class DecoyScreen extends AbstractContainerScreen<DecoyMenu> {
    private static final int BG = 0xF010151C, SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, BLUE = 0xFF7FB3FF, TQ = 0xFF19D3B0, GOLD = 0xFFFFD23A, BAD = 0xFFD63B2F, MUTED = 0x9AA3B5,
            TEXT = 0xE6EAF2, GHOST = 0xB010151C;
    private static final int STAT_X = 186, BTN_Y = 112, BTN_W = 104, BTN_H = 18;
    private static final Kind[] EXPECT = {Kind.RANGE1, Kind.RANGE2, Kind.RANGE3, Kind.BLAST, Kind.AMPLIFIER, Kind.BLAST, Kind.CASING, Kind.CASING};
    private static final int[] BEFORE = {-1, 0, 1, -1, 3, 4, -1, 6};

    public DecoyScreen(DecoyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = DecoyMenu.WIDTH;
        this.imageHeight = DecoyMenu.HEIGHT;
        this.inventoryLabelX = DecoyMenu.INV_X;
        this.inventoryLabelY = DecoyMenu.INV_Y - 11;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.bsp_core.decoy." + key, args);
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean open(int socket) {
        return BEFORE[socket] < 0 || menu.slots.get(BEFORE[socket]).hasItem();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (menu.state() == 2 && over(mx, my, leftPos + STAT_X, topPos + BTN_Y, BTN_W, BTN_H)) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, DecoyMenu.BTN_REPAIR);
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    /** A line between two socket centres, drawn as an L of two straight runs. */
    private void link(GuiGraphics g, int ax, int ay, int bx, int by, boolean lit) {
        int c = lit ? BLUE : DIM, x = leftPos, y = topPos;
        g.fill(x + Math.min(ax, bx) + 7, y + ay + 7, x + Math.max(ax, bx) + 9, y + ay + 9, c);
        g.fill(x + bx + 7, y + Math.min(ay, by) + 7, x + bx + 9, y + Math.max(ay, by) + 9, c);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, BLUE);
        g.fill(x, y, x + imageWidth, y + imageHeight, BG);
        int[][] s = DecoyMenu.SOCKET;
        // branches, lit as far as they are filled
        for (int i = 0; i < s.length; i++) {
            int from = BEFORE[i];
            int fx = from < 0 ? DecoyMenu.CORE_X : s[from][0], fy = from < 0 ? DecoyMenu.CORE_Y : s[from][1];
            link(g, fx, fy, s[i][0], s[i][1], menu.slots.get(i).hasItem());
        }
        g.fill(x + DecoyMenu.CORE_X - 3, y + DecoyMenu.CORE_Y - 3, x + DecoyMenu.CORE_X + 19, y + DecoyMenu.CORE_Y + 19, BLUE);
        g.fill(x + DecoyMenu.CORE_X - 2, y + DecoyMenu.CORE_Y - 2, x + DecoyMenu.CORE_X + 18, y + DecoyMenu.CORE_Y + 18, SLOT_BG);
        g.renderItem(new ItemStack(ModItems.MAGNET_CORE.get()), x + DecoyMenu.CORE_X, y + DecoyMenu.CORE_Y);
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            boolean socket = i < DecoyTotemBlockEntity.SOCKETS;
            int edge = !socket ? 0xFF3A3F4B : slot.hasItem() ? BLUE : open(i) ? 0xFF4A6FA5 : DIM;
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, edge);
            g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, SLOT_BG);
            if (socket && !slot.hasItem()) {
                g.renderFakeItem(new ItemStack(ModItems.DECOY_UPGRADES.get(EXPECT[i]).get()), x + slot.x, y + slot.y);
                g.fill(RenderType.guiGhostRecipeOverlay(), x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, open(i) ? GHOST : 0xE010151C);
            }
        }
        // power bar
        int rf = menu.rf();
        g.fill(x + STAT_X, y + 42, x + STAT_X + BTN_W, y + 48, DIM);
        if (rf > 0) {
            g.fill(x + STAT_X, y + 42, x + STAT_X + Math.round(BTN_W * Mth.clamp(rf / 1000f, 0, 1)), y + 48, GOLD);
        }
        if (menu.state() == 2) {
            boolean hover = over(mouseX, mouseY, x + STAT_X, y + BTN_Y, BTN_W, BTN_H);
            g.fill(x + STAT_X, y + BTN_Y, x + STAT_X + BTN_W, y + BTN_Y + BTN_H, TQ);
            g.fill(x + STAT_X + 1, y + BTN_Y + 1, x + STAT_X + BTN_W - 1, y + BTN_Y + BTN_H - 1, hover ? 0xFF14362F : SLOT_BG);
        }
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
        g.drawString(font, title.getString().toUpperCase(Locale.ROOT), 10, 8, BLUE & 0xFFFFFF, false);
        small(g, tr("branch.traps"), 22, 132, MUTED);
        small(g, tr("branch.range"), 76, 18, MUTED);
        small(g, tr("branch.casing"), 122, 132, MUTED);
        int state = menu.state();
        g.drawString(font, tr(state == 2 ? "state.broken" : state == 1 ? "state.active" : "state.off"), STAT_X, 22, state == 1 ? TQ & 0xFFFFFF : state == 2 ? 0xFF6B5C : GOLD & 0xFFFFFF, false);
        small(g, menu.rf() < 0 ? tr("no_base") : tr("rf", BSPConfig.getOr(BSPConfig.DECOY_RF, 100)), STAT_X, 34, menu.rf() < 0 ? 0xFF6B5C : MUTED);
        small(g, tr("range", menu.range()), STAT_X, 56, TEXT);
        List<String> traps = new ArrayList<>();
        for (int socket : new int[]{DecoyTotemBlockEntity.C1, DecoyTotemBlockEntity.C2}) {
            Kind k = DecoyUpgradeItem.kindOf(menu.slots.get(socket).getItem());
            if (k != null) {
                traps.add(tr("trap." + k.id).getString());
            }
        }
        small(g, tr("trap", traps.isEmpty() ? tr("trap.none").getString() : String.join(" + ", traps)), STAT_X, 66, TEXT);
        int casings = (menu.slots.get(DecoyTotemBlockEntity.K1).hasItem() ? 1 : 0) + (menu.slots.get(DecoyTotemBlockEntity.K2).hasItem() ? 1 : 0);
        small(g, tr("survives", Math.max(0, casings + 1 - menu.hits()), casings + 1), STAT_X, 76, TEXT);
        small(g, tr("placed", menu.placed(), menu.max()), STAT_X, 86, MUTED);
        small(g, tr("disguise." + (state == 1 ? "on" : "off")), STAT_X, 96, MUTED);
        if (state == 2) {
            Component repair = tr("repair", BSPConfig.getOr(BSPConfig.DECOY_REPAIR_INGOTS, 2));
            small(g, repair, STAT_X + BTN_W / 2 - Math.round(font.width(repair) * 0.375f), BTN_Y + 6, TQ & 0xFFFFFF);
        }
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
    }
}
