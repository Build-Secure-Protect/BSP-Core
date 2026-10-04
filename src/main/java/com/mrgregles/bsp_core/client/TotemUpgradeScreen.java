package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.menu.TotemUpgradeMenu;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.UpgradeRequestPacket;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Upgrade tree of the totem in your hand. Carried and Raid upgrades and the totem's tier can be
 * bought here; Base upgrades are shown with their levels but are bought at the placed totem.
 */
public class TotemUpgradeScreen extends AbstractContainerScreen<TotemUpgradeMenu> {
    private static final int BG = 0xF010151C, TQ = 0xFF19D3B0, MUTED = 0x9AA3B5;
    private static final int WALLET_Y = 18, TREE_X = 10, TREE_Y = 38, DETAIL_X = 188, DETAIL_Y = 40;
    private final TotemTree tree = new TotemTree();

    public TotemUpgradeScreen(TotemUpgradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 326;
        this.imageHeight = TREE_Y + TotemTree.H + 22;
    }

    private int level(Buff b) {
        return TotemUpgrades.getLevel(menu.getTotem(), b);
    }

    private int tier() {
        return TotemUpgrades.getTier(menu.getTotem());
    }

    /** Why the selected upgrade cannot be bought right now, or null if it can. */
    @Nullable
    private Component blocked(Buff b) {
        if (b.placedOnly && TotemUpgrades.unlocked(b, this::level, tier()) && level(b) < b.maxLevel()) {
            return Component.translatable("gui.bsp_core.tree.place_first");
        }
        return TotemUpgrades.whyNot(b, this::level, tier(), minecraft.player);
    }

    private boolean canBuy(Buff b) {
        return level(b) < b.maxLevel() && blocked(b) == null;
    }

    private boolean canRaise() {
        return tier() < TotemUpgrades.MAX_TIER && TotemUpgrades.whyNotGate(tier(), minecraft.player) == null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Buff node = tree.nodeAt(mx, my, leftPos + TREE_X, topPos + TREE_Y);
        if (node != null) {
            tree.selected = node;
            return true;
        }
        if (TotemTree.overBuy(mx, my, leftPos + DETAIL_X, topPos + DETAIL_Y) && canBuy(tree.selected)) {
            BSPNetwork.CHANNEL.sendToServer(new UpgradeRequestPacket(menu.getHand(), tree.selected.ordinal()));
            return true;
        }
        if (TotemTree.overGate(mx, my, leftPos + DETAIL_X, topPos + DETAIL_Y) && canRaise()) {
            BSPNetwork.CHANNEL.sendToServer(new UpgradeRequestPacket(menu.getHand(), UpgradeRequestPacket.RAISE_TIER));
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
        TotemTree.wallet(g, font, x + 10, y + WALLET_Y, minecraft.player);
        tree.render(g, font, x + TREE_X, y + TREE_Y, this::level, tier());
        Buff b = tree.selected;
        tree.detail(g, font, x + DETAIL_X, y + DETAIL_Y, level(b), tier(), blocked(b),
                Component.translatable(level(b) >= b.maxLevel() ? "gui.bsp_core.upgrades.maxed" : "gui.bsp_core.tree.buy"), canBuy(b), mouseX, mouseY);
        tree.gate(g, font, x + DETAIL_X, y + DETAIL_Y, tier(), Component.translatable("gui.bsp_core.tree.raise"), canRaise(), mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title.getString().toUpperCase(Locale.ROOT), 10, 7, TQ & 0xFFFFFF, false);
        Component t = Component.translatable("gui.bsp_core.tree.tier", TotemUpgrades.roman(tier()));
        g.drawString(font, t, imageWidth - 10 - font.width(t), 7, TQ & 0xFFFFFF, false);
        // small type, so it stays clear of the tier box beside it
        g.pose().pushPose();
        g.pose().translate(10, TREE_Y + TotemTree.H + 8, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, Component.translatable(menu.getTotem().isEmpty() ? "gui.bsp_core.tree.no_totem" : "gui.bsp_core.tree.carried_hint"), 0, 0, MUTED, false);
        g.pose().popPose();
    }
}
