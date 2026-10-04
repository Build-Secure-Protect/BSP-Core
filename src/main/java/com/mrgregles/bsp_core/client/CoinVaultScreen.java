package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.VaultAccessPacket;
import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.vault.CoinVaultBlockEntity;
import com.mrgregles.bsp_core.vault.CoinVaultMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * Coin Vault screen, "Tabbed Vault" layout in the Holo HUD style with a gold accent.
 * <ul>
 *   <li><b>Storage</b>: the 27 coin slots of one vault block, arrows to page through a joined
 *       vault, and the interest strip with its Redeem button.</li>
 *   <li><b>Access</b>: the players who may open the vault; the owner adds and removes names.</li>
 *   <li><b>Security</b>: the lock level and the alarm, each bought with a specific coin.</li>
 * </ul>
 * A player without access gets the locked view instead: what picking the lock takes, and the button to start.
 */
public class CoinVaultScreen extends AbstractContainerScreen<CoinVaultMenu> {
    private static final int BG = 0xF010151C, SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, GOLD = 0xFFFFD23A, TQ = 0xFF19D3B0, BAD = 0xFFD63B2F, MUTED = 0x9AA3B5,
            TEXT = 0xE6EAF2, HOVER = 0xFF3A3210;
    private static final int X0 = CoinVaultMenu.SLOT_X, RIGHT = X0 + 162, TAB_Y = 20, TAB_W = 52, TAB_H = 13, TAB_GAP = 3, BODY_Y = 37,
            STRIP_Y = 107, BTN_X = RIGHT - 50, BTN_W = 50, BTN_H = 18, ROW_H = 13, COL_W = 81;
    private static final String[] TABS = {"storage", "access", "security"};
    @Nullable
    private EditBox nameBox;

    public CoinVaultScreen(CoinVaultMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = CoinVaultMenu.WIDTH;
        this.imageHeight = menu.locked() ? CoinVaultMenu.LOCKED_HEIGHT : CoinVaultMenu.HEIGHT;
        this.inventoryLabelX = X0;
        this.inventoryLabelY = CoinVaultMenu.INV_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        if (!menu.locked()) {
            nameBox = new EditBox(font, leftPos + X0 + 4, topPos + STRIP_Y + 5, 100, 10, tr("add_hint"));
            nameBox.setMaxLength(16);
            nameBox.setBordered(false);
            nameBox.setHint(tr("add_hint"));
            addRenderableWidget(nameBox);
            setTab(menu.tab);
        }
    }

    // ------------------------------------------------------------------ state

    @Nullable
    private CoinVaultBlockEntity vault() {
        return menu.vault();
    }

    private boolean manager() {
        return vault() != null && vault().mayManage(minecraft.player);
    }

    private boolean owner() {
        return vault() != null && vault().isOwner(minecraft.player);
    }

    private boolean canAfford(@Nullable ItemStack cost) {
        return cost != null && (minecraft.player.isCreative() || minecraft.player.getInventory().countItem(cost.getItem()) >= cost.getCount());
    }

    private boolean anyDue() {
        return menu.dueXp() + menu.dueTetrium() + menu.dueIllyrium() > 0;
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void press(int id) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private static String clock(int seconds) {
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static int pickSeconds(int lock) {
        return BSPConfig.getOr(BSPConfig.VAULT_LOCKPICK_SECONDS, 120) + lock * BSPConfig.getOr(BSPConfig.VAULT_LOCK_LEVEL_SECONDS, 60);
    }

    private void setTab(int tab) {
        menu.tab = tab;
        if (nameBox != null) {
            nameBox.setVisible(tab == CoinVaultMenu.TAB_ACCESS && manager());
            nameBox.setFocused(false);
        }
    }

    private void addName() {
        if (nameBox != null && !nameBox.getValue().isBlank()) {
            BSPNetwork.CHANNEL.sendToServer(new VaultAccessPacket(nameBox.getValue().trim()));
            nameBox.setValue("");
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = leftPos, y = topPos;
        CoinVaultBlockEntity v = vault();
        if (menu.locked()) {
            if (menu.pickState() == 0 && over(mx, my, x + 38, y + 92, 120, BTN_H)) {
                press(CoinVaultMenu.BTN_PICK);
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }
        for (int i = 0; i < TABS.length; i++) {
            if (over(mx, my, x + X0 + i * (TAB_W + TAB_GAP), y + TAB_Y, TAB_W, TAB_H)) {
                setTab(i);
                return true;
            }
        }
        if (menu.tab == CoinVaultMenu.TAB_STORAGE) {
            if (menu.pages() > 1 && over(mx, my, x + X0, y + BODY_Y, 12, 10)) {
                press(CoinVaultMenu.BTN_PREV);
                return true;
            }
            if (menu.pages() > 1 && over(mx, my, x + RIGHT - 12, y + BODY_Y, 12, 10)) {
                press(CoinVaultMenu.BTN_NEXT);
                return true;
            }
            if (owner() && anyDue() && over(mx, my, x + BTN_X, y + STRIP_Y, BTN_W, BTN_H)) {
                press(CoinVaultMenu.BTN_REDEEM);
                return true;
            }
        } else if (menu.tab == CoinVaultMenu.TAB_ACCESS && v != null && manager()) {
            for (int i = 0; i < v.accessList().size(); i++) {
                if (over(mx, my, x + X0 + (i % 2) * COL_W + COL_W - 12, y + BODY_Y + 13 + (i / 2) * ROW_H, 10, 10)) {
                    press(CoinVaultMenu.BTN_REMOVE + i);
                    return true;
                }
            }
            if (over(mx, my, x + BTN_X, y + STRIP_Y, BTN_W, BTN_H)) {
                addName();
                return true;
            }
        } else if (menu.tab == CoinVaultMenu.TAB_SECURITY && v != null && manager()) {
            if (over(mx, my, x + BTN_X, y + BODY_Y + 6, BTN_W, BTN_H) && canAfford(CoinVaultBlockEntity.lockCost(v.lockLevel()))) {
                press(CoinVaultMenu.BTN_LOCK);
                return true;
            }
            if (over(mx, my, x + BTN_X, y + BODY_Y + 34, BTN_W, BTN_H) && canAfford(CoinVaultBlockEntity.alarmCost(v.alarmLevel()))) {
                press(CoinVaultMenu.BTN_ALARM);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (nameBox != null && nameBox.isFocused() && key != 256) {
            if (key == 257 || key == 335) {
                addName();
                return true;
            }
            nameBox.keyPressed(key, scan, mods);
            return true; // typing a name must not trigger the inventory key
        }
        return super.keyPressed(key, scan, mods);
    }

    // ------------------------------------------------------------------ drawing

    private void small(GuiGraphics g, Component text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.bsp_core.vault." + key, args);
    }

    private void button(GuiGraphics g, int x, int y, int w, Component label, boolean enabled, int mouseX, int mouseY) {
        boolean hover = enabled && over(mouseX, mouseY, x, y, w, BTN_H);
        g.fill(x, y, x + w, y + BTN_H, enabled ? GOLD : DIM);
        g.fill(x + 1, y + 1, x + w - 1, y + BTN_H - 1, hover ? HOVER : SLOT_BG);
        g.drawString(font, label, x + w / 2 - font.width(label) / 2, y + 5, enabled ? GOLD & 0xFFFFFF : 0x6B7385, false);
    }

    private void pips(GuiGraphics g, int x, int y, int lit, int max) {
        for (int i = 0; i < max; i++) {
            g.fill(x + i * 9, y, x + i * 9 + 7, y + 7, i < lit ? GOLD : DIM);
        }
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
        g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, GOLD);
        g.fill(x, y, x + imageWidth, y + imageHeight, BG);
        CoinVaultBlockEntity v = vault();
        if (menu.locked()) {
            if (menu.pickState() == 1) {
                float done = 1f - menu.pickLeft() / (float) menu.pickTotal();
                g.fill(x + X0, y + 96, x + RIGHT, y + 104, DIM);
                g.fill(x + X0, y + 96, x + X0 + Math.round(162 * Mth.clamp(done, 0, 1)), y + 104, GOLD);
            } else {
                button(g, x + 38, y + 92, 120, tr(menu.pickState() == 2 ? "busy" : "pick"), menu.pickState() == 0, mouseX, mouseY);
            }
            pips(g, x + X0 + 40, y + 41, v == null ? 0 : v.lockLevel(), CoinVaultBlockEntity.MAX_LOCK);
            return;
        }
        // tabs
        for (int i = 0; i < TABS.length; i++) {
            int tx = x + X0 + i * (TAB_W + TAB_GAP);
            boolean on = menu.tab == i;
            g.fill(tx, y + TAB_Y, tx + TAB_W, y + TAB_Y + TAB_H, on ? GOLD : DIM);
            g.fill(tx + 1, y + TAB_Y + 1, tx + TAB_W - 1, y + TAB_Y + TAB_H - (on ? 0 : 1), on ? BG : SLOT_BG);
        }
        // slots: vault slots gold-edged on the Storage tab, player inventory always
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (!slot.isActive()) {
                continue;
            }
            g.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, i < CoinVaultBlockEntity.SLOTS ? 0xFF6B5A1E : 0xFF3A3F4B);
            g.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, SLOT_BG);
        }
        if (menu.tab == CoinVaultMenu.TAB_STORAGE) {
            // interest: how full it is against its cap
            g.fill(x + X0, y + STRIP_Y + 20, x + BTN_X - 6, y + STRIP_Y + 23, DIM);
            g.fill(x + X0, y + STRIP_Y + 20, x + X0 + Math.round((BTN_X - 6 - X0) * Mth.clamp(menu.fill(), 0, 1)), y + STRIP_Y + 23, menu.fill() >= 1f ? BAD : GOLD);
            button(g, x + BTN_X, y + STRIP_Y, BTN_W, tr("redeem"), owner() && anyDue(), mouseX, mouseY);
        } else if (menu.tab == CoinVaultMenu.TAB_ACCESS && v != null) {
            if (manager()) {
                g.fill(x + X0, y + STRIP_Y, x + X0 + 108, y + STRIP_Y + BTN_H, DIM);
                g.fill(x + X0 + 1, y + STRIP_Y + 1, x + X0 + 107, y + STRIP_Y + BTN_H - 1, SLOT_BG);
                button(g, x + BTN_X, y + STRIP_Y, BTN_W, tr("add"), nameBox != null && !nameBox.getValue().isBlank(), mouseX, mouseY);
            }
        } else if (menu.tab == CoinVaultMenu.TAB_SECURITY && v != null) {
            pips(g, x + X0 + 34, y + BODY_Y + 4, v.lockLevel(), CoinVaultBlockEntity.MAX_LOCK);
            pips(g, x + X0 + 34, y + BODY_Y + 36, v.alarmLevel(), CoinVaultBlockEntity.MAX_ALARM);
            ItemStack lock = CoinVaultBlockEntity.lockCost(v.lockLevel()), alarm = CoinVaultBlockEntity.alarmCost(v.alarmLevel());
            button(g, x + BTN_X, y + BODY_Y + 6, BTN_W, tr(lock == null ? "max" : "upgrade"), manager() && canAfford(lock), mouseX, mouseY);
            button(g, x + BTN_X, y + BODY_Y + 34, BTN_W, tr(alarm == null ? "fitted" : "fit"), manager() && canAfford(alarm), mouseX, mouseY);
        }
    }

    /** Draws "icon x n" for a price, right-aligned to end at {@code right}. */
    private void price(GuiGraphics g, @Nullable ItemStack cost, int right, int y) {
        if (cost == null) {
            return;
        }
        boolean free = minecraft.player.isCreative();
        String text = free ? tr("free").getString() : "x" + cost.getCount();
        int w = font.width(text);
        g.drawString(font, text, right - w, y + 4, free ? TQ & 0xFFFFFF : canAfford(cost) ? TEXT : BAD & 0xFFFFFF, false);
        if (!free) {
            g.renderItem(cost, right - w - 18, y);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        CoinVaultBlockEntity v = vault();
        g.drawString(font, title.getString().toUpperCase(Locale.ROOT), 10, 8, GOLD & 0xFFFFFF, false);
        if (v != null) {
            Component own = tr("owner", v.getOwnerName());
            small(g, own, imageWidth - 10 - Math.round(font.width(own) * 0.75f), 10, MUTED);
        }
        if (menu.locked()) {
            g.drawString(font, tr("locked"), X0, 24, BAD & 0xFFFFFF, false);
            g.drawString(font, tr("lock"), X0, 41, TEXT, false);
            int lock = v == null ? 0 : v.lockLevel();
            g.drawString(font, tr("pick_time", clock(pickSeconds(lock))), X0, 54, TEXT, false);
            small(g, tr("share", Math.round(BSPConfig.getOr(BSPConfig.VAULT_LOCKPICK_SHARE, 0.25) * 100)), X0, 68, MUTED);
            small(g, tr("stay", BSPConfig.getOr(BSPConfig.VAULT_LOCKPICK_RADIUS, 4)), X0, 77, MUTED);
            if (menu.pickState() == 1) {
                Component left = tr("picking", clock(menu.pickLeft()));
                g.drawString(font, left, imageWidth / 2 - font.width(left) / 2, 108, GOLD & 0xFFFFFF, false);
            }
            return;
        }
        for (int i = 0; i < TABS.length; i++) {
            Component label = tr("tab." + TABS[i]);
            small(g, label, X0 + i * (TAB_W + TAB_GAP) + TAB_W / 2 - Math.round(font.width(label) * 0.375f), TAB_Y + 4, menu.tab == i ? GOLD & 0xFFFFFF : MUTED);
        }
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
        if (menu.tab == CoinVaultMenu.TAB_STORAGE) {
            Component page = tr("page", menu.page() + 1, menu.pages());
            small(g, page, (X0 + RIGHT) / 2 - Math.round(font.width(page) * 0.375f), BODY_Y + 2, MUTED);
            if (menu.pages() > 1) {
                g.drawString(font, "<", X0 + 3, BODY_Y, GOLD & 0xFFFFFF, false);
                g.drawString(font, ">", RIGHT - 9, BODY_Y, GOLD & 0xFFFFFF, false);
            }
            small(g, tr("interest", menu.dueXp()), X0, STRIP_Y, anyDue() ? GOLD & 0xFFFFFF : MUTED);
            small(g, tr("due", menu.dueTetrium(), menu.dueIllyrium()), X0, STRIP_Y + 10, anyDue() ? GOLD & 0xFFFFFF : MUTED);
        } else if (menu.tab == CoinVaultMenu.TAB_ACCESS && v != null) {
            small(g, tr("access_head", v.accessList().size(), CoinVaultBlockEntity.MAX_ACCESS), X0, BODY_Y + 2, MUTED);
            List<CoinVaultBlockEntity.Access> list = v.accessList();
            for (int i = 0; i < list.size(); i++) {
                int ax = X0 + (i % 2) * COL_W, ay = BODY_Y + 14 + (i / 2) * ROW_H;
                g.drawString(font, font.plainSubstrByWidth(list.get(i).name(), COL_W - 16), ax, ay, TEXT, false);
                if (manager()) {
                    g.drawString(font, "x", ax + COL_W - 10, ay, BAD & 0xFFFFFF, false);
                }
            }
            if (list.isEmpty()) {
                small(g, tr("access_none"), X0, BODY_Y + 16, MUTED);
            }
            if (!manager()) {
                small(g, tr("access_owner_only"), X0, STRIP_Y + 6, MUTED);
            }
        } else if (menu.tab == CoinVaultMenu.TAB_SECURITY && v != null) {
            ItemStack lock = CoinVaultBlockEntity.lockCost(v.lockLevel()), alarm = CoinVaultBlockEntity.alarmCost(v.alarmLevel());
            g.drawString(font, tr("lock"), X0, BODY_Y + 4, TEXT, false);
            small(g, tr("pick_time", clock(pickSeconds(v.lockLevel()))), X0, BODY_Y + 16, MUTED);
            if (lock != null) {
                small(g, tr("next", clock(pickSeconds(v.lockLevel() + 1))), X0, BODY_Y + 24, MUTED);
            }
            price(g, lock, BTN_X - 4, BODY_Y + 7);
            g.drawString(font, tr("alarm"), X0, BODY_Y + 36, TEXT, false);
            small(g, tr(v.alarmLevel() > 0 ? "alarm_on" : "alarm_off"), X0, BODY_Y + 48, MUTED);
            price(g, alarm, BTN_X - 4, BODY_Y + 35);
            small(g, tr("share", Math.round(BSPConfig.getOr(BSPConfig.VAULT_LOCKPICK_SHARE, 0.25) * 100)), X0, BODY_Y + 62, MUTED);
            small(g, tr("illyrium_rule", BSPConfig.getOr(BSPConfig.VAULT_ILLYRIUM_SAFE, 3)), X0, BODY_Y + 71, MUTED);
            small(g, tr(manager() ? "break_rule" : "access_owner_only"), X0, BODY_Y + 80, MUTED);
        }
    }
}
