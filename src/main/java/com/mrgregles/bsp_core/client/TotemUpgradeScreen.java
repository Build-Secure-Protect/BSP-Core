package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.coin.CoinWallet;
import com.mrgregles.bsp_core.menu.TotemUpgradeMenu;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.UpgradeRequestPacket;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Upgrade tree of the totem in your hand. Carried and Raid upgrades can be bought here; Base
 * upgrades are shown with their levels but can only be bought once the totem is placed.
 */
public class TotemUpgradeScreen extends AbstractContainerScreen<TotemUpgradeMenu> {
    private static final int BG = 0xF010151C, TQ = 0xFF19D3B0, MUTED = 0x9AA3B5;
    private static final int TREE_X = 10, TREE_Y = 22, DETAIL_X = 208, DETAIL_Y = 26;
    private final TotemTree tree = new TotemTree();

    public TotemUpgradeScreen(TotemUpgradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 326;
        this.imageHeight = TREE_Y + TotemTree.H + 22;
    }

    private int level(Buff b) {
        return TotemUpgrades.getLevel(menu.getTotem(), b);
    }

    /** Why the selected upgrade cannot be bought right now, or null if it can. */
    @Nullable
    private Component blocked(Buff b) {
        int cost = b.costToUpgrade(level(b));
        if (cost < 0) {
            return null;
        }
        if (b.placedOnly) {
            return Component.translatable("gui.bsp_core.tree.place_first");
        }
        if (minecraft.player.isCreative()) {
            return null;
        }
        if (b.currency == TotemUpgrades.Currency.XP) {
            return minecraft.player.experienceLevel >= cost ? null : Component.translatable("gui.bsp_core.tree.need_xp", cost);
        }
        return CoinWallet.totalValue(minecraft.player) >= cost ? null : Component.translatable("gui.bsp_core.tree.need_coins", cost);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Buff node = tree.nodeAt(mx, my, leftPos + TREE_X, topPos + TREE_Y);
        if (node != null) {
            tree.selected = node;
            return true;
        }
        Buff b = tree.selected;
        if (TotemTree.over(mx, my, leftPos + DETAIL_X, topPos + DETAIL_Y) && b.costToUpgrade(level(b)) >= 0 && blocked(b) == null) {
            BSPNetwork.CHANNEL.sendToServer(new UpgradeRequestPacket(menu.getHand(), b.ordinal()));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        Buff hover = tree.nodeAt(mouseX, mouseY, leftPos + TREE_X, topPos + TREE_Y);
        if (hover != null) {
            g.renderTooltip(font, Component.translatable("gui.bsp_core.tree.node", Component.translatable(hover.translationKey()), level(hover), hover.maxLevel()), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, TQ);
        g.fill(x, y, x + imageWidth, y + imageHeight, BG);
        tree.render(g, font, x + TREE_X, y + TREE_Y, this::level, mouseX, mouseY);
        Buff b = tree.selected;
        Component blocked = blocked(b);
        tree.detail(g, font, x + DETAIL_X, y + DETAIL_Y, level(b), blocked, TotemTree.buyLabel(b, level(b)), blocked == null && b.costToUpgrade(level(b)) >= 0, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title.getString().toUpperCase(Locale.ROOT), 10, 8, TQ & 0xFFFFFF, false);
        Component wallet = Component.translatable("gui.bsp_core.tree.wallet", minecraft.player.experienceLevel, CoinWallet.totalValue(minecraft.player));
        g.drawString(font, wallet, imageWidth - 10 - font.width(wallet), 8, 0xFFD23A, false);
        ItemStack totem = menu.getTotem();
        g.drawString(font, Component.translatable(totem.isEmpty() ? "gui.bsp_core.tree.no_totem" : "gui.bsp_core.tree.carried_hint"), 10, TREE_Y + TotemTree.H + 6, MUTED, false);
    }
}
