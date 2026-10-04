package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.coin.CoinWallet;
import com.mrgregles.bsp_core.menu.ShatterTotemMenu;
import com.mrgregles.bsp_core.network.AdminTotemActionPacket;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.PlacedUpgradeRequestPacket;
import com.mrgregles.bsp_core.network.StealRequestPacket;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import com.mrgregles.bsp_core.totem.StealState;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The placed totem's screen. The upgrade tree with its detail panel on top; under it the owner,
 * the steal progress and the Steal button for non-owners. The owner can buy every upgrade here.
 * Operators get a second tab, OPERATOR, where the same tree sets levels directly and buttons
 * unclaim, assign, cancel or finish a steal, so one player can test the whole steal flow.
 */
public class ShatterTotemScreen extends AbstractContainerScreen<ShatterTotemMenu> {
    private static final int BG = 0xF010151C, TQ = 0xFF19D3B0, VIO = 0xFFB58CFF, SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, MUTED = 0x9AA3B5;
    private static final int TREE_X = 10, TREE_Y = 22, DETAIL_X = 208, DETAIL_Y = 26, STATUS_Y = TREE_Y + TotemTree.H + 5, TAB_X = 214, TAB_W = 50;

    private final TotemTree tree = new TotemTree();
    private Button stealButton;
    private boolean admin, operatorTab;
    private EditBox ownerBox;
    private final List<Button> adminButtons = new ArrayList<>();

    public ShatterTotemScreen(ShatterTotemMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 326;
        this.imageHeight = STATUS_Y + 46;
    }

    @Override
    protected void init() {
        super.init();
        admin = minecraft.player != null && minecraft.player.hasPermissions(2);
        stealButton = Button.builder(Component.translatable("gui.bsp_core.shatter_totem.steal"), b -> {
            BSPNetwork.CHANNEL.sendToServer(new StealRequestPacket(menu.getPos()));
            onClose();
        }).bounds(leftPos + DETAIL_X, topPos + STATUS_Y + 18, TotemTree.DETAIL_W, 20).build();
        addRenderableWidget(stealButton);
        adminButtons.clear();
        if (admin) {
            int x = leftPos + 10, y = topPos + STATUS_Y + 2;
            ownerBox = new EditBox(font, x, y + 1, 110, 16, Component.translatable("gui.bsp_core.admin.owner_name"));
            ownerBox.setMaxLength(16);
            ownerBox.setHint(Component.translatable("gui.bsp_core.admin.owner_name").withStyle(ChatFormatting.DARK_GRAY));
            addRenderableWidget(ownerBox);
            adminButton(x + 114, y, 60, "gui.bsp_core.admin.assign", () -> send(AdminTotemActionPacket.Action.ASSIGN_OWNER, ownerBox.getValue(), 0));
            adminButton(x + 178, y, 128, "gui.bsp_core.admin.unclaim", () -> send(AdminTotemActionPacket.Action.UNCLAIM, "", 0));
            adminButton(x, y + 21, 100, "gui.bsp_core.admin.cancel_steal", () -> send(AdminTotemActionPacket.Action.CANCEL_STEAL, "", 0));
            adminButton(x + 103, y + 21, 100, "gui.bsp_core.admin.finish_steal", () -> send(AdminTotemActionPacket.Action.FINISH_STEAL, "", 0));
            adminButton(x + 206, y + 21, 100, "gui.bsp_core.admin.reset_buffs", () -> send(AdminTotemActionPacket.Action.RESET_BUFFS, "", 0));
        }
        refresh();
    }

    private void adminButton(int x, int y, int w, String labelKey, Runnable action) {
        Button b = Button.builder(Component.translatable(labelKey), btn -> action.run()).bounds(x, y, w, 18).build();
        addRenderableWidget(b);
        adminButtons.add(b);
    }

    private void send(AdminTotemActionPacket.Action action, String text, int value) {
        BSPNetwork.CHANNEL.sendToServer(new AdminTotemActionPacket(menu.getPos(), action, text, value));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    private boolean isOwner() {
        ShatterTotemBlockEntity totem = menu.getTotem();
        return totem != null && totem.isOwner(minecraft.player.getUUID());
    }

    private void refresh() {
        ShatterTotemBlockEntity totem = menu.getTotem();
        boolean canSteal = !operatorTab && totem != null && !isOwner() && totem.getSteal().isEmpty();
        stealButton.visible = canSteal;
        stealButton.active = canSteal;
        for (Button b : adminButtons) {
            b.visible = operatorTab;
            b.active = operatorTab;
        }
        if (ownerBox != null) {
            ownerBox.setVisible(operatorTab);
            if (!operatorTab) {
                ownerBox.setFocused(false);
            }
        }
    }

    private int level(Buff b) {
        ShatterTotemBlockEntity totem = menu.getTotem();
        return totem == null ? 0 : totem.getUpgradeLevel(b);
    }

    /** Why the selected upgrade cannot be bought right now, or null if it can. */
    @Nullable
    private Component blocked(Buff b) {
        int cost = b.costToUpgrade(level(b));
        if (cost < 0) {
            return null;
        }
        if (!isOwner() && !admin) {
            return Component.translatable("gui.bsp_core.tree.not_yours");
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
        int x = leftPos, y = topPos;
        if (admin && my >= y + 5 && my < y + 18) {
            if (mx >= x + TAB_X && mx < x + TAB_X + TAB_W) {
                operatorTab = false;
                refresh();
                return true;
            }
            if (mx >= x + TAB_X + TAB_W + 2 && mx < x + TAB_X + TAB_W * 2 + 2) {
                operatorTab = true;
                refresh();
                return true;
            }
        }
        Buff node = tree.nodeAt(mx, my, x + TREE_X, y + TREE_Y);
        if (node != null) {
            tree.selected = node;
            return true;
        }
        Buff b = tree.selected;
        if (operatorTab) {
            // the action bar becomes two halves: lower the level, raise the level
            if (TotemTree.over(mx, my, x + DETAIL_X, y + DETAIL_Y)) {
                send(AdminTotemActionPacket.Action.SET_BUFF, mx < x + DETAIL_X + TotemTree.DETAIL_W / 2.0 ? "-" : "+", b.ordinal());
                return true;
            }
        } else if (TotemTree.over(mx, my, x + DETAIL_X, y + DETAIL_Y) && b.costToUpgrade(level(b)) >= 0 && blocked(b) == null) {
            BSPNetwork.CHANNEL.sendToServer(new PlacedUpgradeRequestPacket(menu.getPos(), b.ordinal()));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ownerBox != null && ownerBox.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                ownerBox.setFocused(false);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                send(AdminTotemActionPacket.Action.ASSIGN_OWNER, ownerBox.getValue(), 0);
                return true;
            }
            ownerBox.keyPressed(key, scan, mods);
            return true; // swallow the inventory key while typing
        }
        return super.keyPressed(key, scan, mods);
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
        g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, operatorTab ? VIO : TQ);
        g.fill(x, y, x + imageWidth, y + imageHeight, BG);
        if (admin) {
            for (int i = 0; i < 2; i++) {
                int tx = x + TAB_X + i * (TAB_W + 2);
                boolean on = (i == 1) == operatorTab;
                g.fill(tx, y + 5, tx + TAB_W, y + 18, on ? (i == 1 ? VIO : TQ) : DIM);
                g.fill(tx + 1, y + 6, tx + TAB_W - 1, y + 17, SLOT_BG);
            }
        }
        tree.render(g, font, x + TREE_X, y + TREE_Y, this::level, mouseX, mouseY);
        Buff b = tree.selected;
        if (operatorTab) {
            tree.detail(g, font, x + DETAIL_X, y + DETAIL_Y, level(b), null, Component.translatable("gui.bsp_core.tree.set_level"), true, mouseX, mouseY);
        } else {
            Component blocked = blocked(b);
            tree.detail(g, font, x + DETAIL_X, y + DETAIL_Y, level(b), blocked, TotemTree.buyLabel(b, level(b)), blocked == null && b.costToUpgrade(level(b)) >= 0, mouseX, mouseY);
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
        g.drawString(font, title.getString().toUpperCase(Locale.ROOT), 10, 8, (operatorTab ? VIO : TQ) & 0xFFFFFF, false);
        if (!operatorTab) {
            // beside the status lines, clear of the operator tabs in the header
            g.drawString(font, Component.translatable("gui.bsp_core.tree.wallet", minecraft.player.experienceLevel, CoinWallet.totalValue(minecraft.player)),
                    DETAIL_X, STATUS_Y, 0xFFD23A, false);
        }
        if (admin) {
            Component a = Component.translatable("gui.bsp_core.tree.tab.totem"), o = Component.translatable("gui.bsp_core.tree.tab.operator");
            small(g, a, TAB_X + TAB_W / 2 - font.width(a) * 3 / 8, 9, operatorTab ? MUTED : TQ & 0xFFFFFF);
            small(g, o, TAB_X + TAB_W + 2 + TAB_W / 2 - font.width(o) * 3 / 8, 9, operatorTab ? VIO & 0xFFFFFF : MUTED);
        }
        ShatterTotemBlockEntity totem = menu.getTotem();
        if (totem == null || operatorTab) {
            return;
        }
        Component ownerLine = totem.getOwner()
                .map(o -> (Component) Component.translatable("gui.bsp_core.shatter_totem.owner", o.name()))
                .orElse(Component.translatable("gui.bsp_core.shatter_totem.unclaimed").withStyle(ChatFormatting.YELLOW));
        g.drawString(font, ownerLine, 10, STATUS_Y, 0xE8EAF0, false);
        StealState steal = totem.getSteal().orElse(null);
        if (steal != null) {
            Component line = Component.translatable("gui.bsp_core.shatter_totem.stealing", steal.thiefName(), StealHudOverlay.clock(steal.ticksLeft()))
                    .withStyle(ChatFormatting.RED);
            g.drawString(font, line, 10, STATUS_Y + 11, 0xFFFFFF, false);
            StealHudOverlay.drawBar(g, 10, STATUS_Y + 23, steal, !isOwner(), 0);
            if (steal.outside()) {
                g.drawString(font, Component.translatable("gui.bsp_core.shatter_totem.grace", (steal.graceLeft() + 19) / 20).withStyle(ChatFormatting.YELLOW), 10, STATUS_Y + 31, 0xFFFFFF, false);
            }
        } else {
            g.drawString(font, Component.translatable(isOwner() ? "gui.bsp_core.shatter_totem.yours" : "gui.bsp_core.shatter_totem.hint")
                    .withStyle(isOwner() ? ChatFormatting.GREEN : ChatFormatting.GRAY), 10, STATUS_Y + 11, 0xFFFFFF, false);
        }
    }
}
