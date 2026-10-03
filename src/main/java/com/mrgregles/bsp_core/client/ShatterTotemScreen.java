package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.menu.ShatterTotemMenu;
import com.mrgregles.bsp_core.network.AdminTotemActionPacket;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.PlacedUpgradeRequestPacket;
import com.mrgregles.bsp_core.coin.CoinWallet;
import com.mrgregles.bsp_core.network.StealRequestPacket;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import com.mrgregles.bsp_core.totem.StealState;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The placed totem's panel: owner, steal progress, and the Steal button for non-owners.
 * Operators also get an admin column on the left to unclaim, assign, cancel or finish steals and
 * view or change buffs, so a single player can test the whole steal flow.
 */
public class ShatterTotemScreen extends AbstractContainerScreen<ShatterTotemMenu> {
    private static final int PANEL_BG = 0xE0101018;
    private static final int PANEL_BORDER = 0xFFE3B341;
    private static final int ADMIN_BORDER = 0xFFB080FF;
    private static final int ADMIN_W = 150;
    private static final int ADMIN_GAP = 8;

    private Button stealButton;
    private boolean admin;
    private EditBox ownerBox;
    private final List<Button> adminButtons = new ArrayList<>();
    private int adminLeft, adminTop, adminHeight;
    private static final TotemUpgrades.Buff[] PLACED_BUFFS = java.util.Arrays.stream(TotemUpgrades.Buff.values())
            .filter(b -> b.placedOnly).toArray(TotemUpgrades.Buff[]::new);
    private static final int PLACED_ROW_Y = 74, PLACED_ROW_H = 24;
    private final Map<TotemUpgrades.Buff, Button> placedButtons = new EnumMap<>(TotemUpgrades.Buff.class);

    public ShatterTotemScreen(ShatterTotemMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 236;
        this.imageHeight = PLACED_ROW_Y + PLACED_ROW_H * PLACED_BUFFS.length + 48;
    }

    @Override
    protected void init() {
        super.init();
        admin = minecraft.player != null && minecraft.player.hasPermissions(2);
        if (admin) {
            // Shift the main panel right so the admin column fits beside it.
            leftPos = Math.max(ADMIN_W + ADMIN_GAP + 4, (width - imageWidth) / 2 + (ADMIN_W + ADMIN_GAP) / 2);
        }

        stealButton = Button.builder(Component.translatable("gui.bsp_core.shatter_totem.steal"), b -> {
            BSPNetwork.CHANNEL.sendToServer(new StealRequestPacket(menu.getPos()));
            onClose();
        }).bounds(leftPos + imageWidth / 2 - 50, topPos + imageHeight - 26, 100, 20).build();
        addRenderableWidget(stealButton);

        placedButtons.clear();
        int row = 0;
        for (TotemUpgrades.Buff buff : PLACED_BUFFS) {
            int y = topPos + PLACED_ROW_Y + row * PLACED_ROW_H;
            Button b = Button.builder(Component.empty(),
                            btn -> BSPNetwork.CHANNEL.sendToServer(new PlacedUpgradeRequestPacket(menu.getPos(), buff.ordinal())))
                    .bounds(leftPos + imageWidth - 104, y - 2, 96, 20).build();
            addRenderableWidget(b);
            placedButtons.put(buff, b);
            row++;
        }

        if (admin) {
            initAdminColumn();
        }
        refresh();
    }

    private void initAdminColumn() {
        adminButtons.clear();
        adminLeft = leftPos - ADMIN_GAP - ADMIN_W;
        adminTop = topPos;
        int x = adminLeft + 6;
        int y = adminTop + 16;
        int w = ADMIN_W - 12;

        ownerBox = new EditBox(font, x, y, w - 52, 16, Component.translatable("gui.bsp_core.admin.owner_name"));
        ownerBox.setMaxLength(16);
        ownerBox.setHint(Component.translatable("gui.bsp_core.admin.owner_name").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(ownerBox);
        adminButton(x + w - 50, y - 2, 50, "gui.bsp_core.admin.assign",
                () -> send(AdminTotemActionPacket.Action.ASSIGN_OWNER, ownerBox.getValue(), 0));
        y += 22;
        adminButton(x, y, w, "gui.bsp_core.admin.unclaim", () -> send(AdminTotemActionPacket.Action.UNCLAIM, "", 0));
        y += 22;
        adminButton(x, y, w / 2 - 1, "gui.bsp_core.admin.cancel_steal", () -> send(AdminTotemActionPacket.Action.CANCEL_STEAL, "", 0));
        adminButton(x + w / 2 + 1, y, w / 2 - 1, "gui.bsp_core.admin.finish_steal", () -> send(AdminTotemActionPacket.Action.FINISH_STEAL, "", 0));
        y += 26;
        for (TotemUpgrades.Buff buff : TotemUpgrades.Buff.values()) {
            final int ordinal = buff.ordinal();
            adminButton(x + w - 42, y - 3, 20, "-", () -> send(AdminTotemActionPacket.Action.SET_BUFF, "-", ordinal));
            adminButton(x + w - 20, y - 3, 20, "+", () -> send(AdminTotemActionPacket.Action.SET_BUFF, "+", ordinal));
            y += 16;
        }
        y += 4;
        adminButton(x, y, w, "gui.bsp_core.admin.reset_buffs", () -> send(AdminTotemActionPacket.Action.RESET_BUFFS, "", 0));
        y += 24;
        adminHeight = y - adminTop;
    }

    private void adminButton(int x, int y, int w, String labelKey, Runnable action) {
        Component label = labelKey.startsWith("gui.") ? Component.translatable(labelKey) : Component.literal(labelKey);
        Button b = Button.builder(label, btn -> action.run()).bounds(x, y, w, 18).build();
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

    private void refresh() {
        ShatterTotemBlockEntity totem = menu.getTotem();
        boolean canSteal = totem != null
                && !totem.isOwner(minecraft.player.getUUID())
                && totem.getSteal().isEmpty();
        stealButton.visible = canSteal;
        stealButton.active = canSteal;

        boolean owner = totem != null && totem.isOwner(minecraft.player.getUUID());
        boolean creative = minecraft.player.isCreative();
        int coins = CoinWallet.totalValue(minecraft.player);
        for (var e : placedButtons.entrySet()) {
            Button b = e.getValue();
            b.visible = owner || admin;
            if (totem == null) {
                b.active = false;
                continue;
            }
            int lvl = totem.getUpgradeLevel(e.getKey());
            int cost = e.getKey().costToUpgrade(lvl);
            if (cost < 0) {
                b.setMessage(Component.translatable("gui.bsp_core.upgrades.maxed"));
                b.active = false;
            } else {
                b.setMessage(Component.translatable("gui.bsp_core.upgrades.buy_coins", cost));
                b.active = creative || coins >= cost;
            }
        }
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
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, PANEL_BORDER);
        g.fill(x, y, x + imageWidth, y + imageHeight, PANEL_BG);
        if (admin) {
            g.fill(adminLeft - 1, adminTop - 1, adminLeft + ADMIN_W + 1, adminTop + adminHeight + 1, ADMIN_BORDER);
            g.fill(adminLeft, adminTop, adminLeft + ADMIN_W, adminTop + adminHeight, PANEL_BG);
            g.drawCenteredString(font, Component.translatable("gui.bsp_core.admin.title").withStyle(ChatFormatting.LIGHT_PURPLE),
                    adminLeft + ADMIN_W / 2, adminTop + 4, 0xFFFFFF);
            ShatterTotemBlockEntity totem = menu.getTotem();
            int ly = adminTop + 16 + 22 + 22 + 26;
            for (TotemUpgrades.Buff buff : TotemUpgrades.Buff.values()) {
                int lvl = totem == null ? 0 : totem.getUpgradeLevel(buff);
                g.drawString(font, Component.translatable("gui.bsp_core.admin.buff_level",
                        Component.translatable(buff.translationKey()), lvl, buff.maxLevel()), adminLeft + 6, ly, 0xFFFFFF);
                ly += 16;
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawCenteredString(font, title, imageWidth / 2, 8, 0xE3B341);
        ShatterTotemBlockEntity totem = menu.getTotem();
        if (totem == null) {
            return;
        }
        Component ownerLine = totem.getOwner()
                .map(o -> Component.translatable("gui.bsp_core.shatter_totem.owner", o.name()))
                .orElse(Component.translatable("gui.bsp_core.shatter_totem.unclaimed").withStyle(ChatFormatting.YELLOW));
        g.drawCenteredString(font, ownerLine, imageWidth / 2, 28, 0xFFFFFF);

        StealState steal = totem.getSteal().orElse(null);
        if (steal != null) {
            Component line = Component.translatable("gui.bsp_core.shatter_totem.stealing", steal.thiefName(), StealHudOverlay.clock(steal.ticksLeft()))
                    .withStyle(ChatFormatting.RED);
            g.drawCenteredString(font, line, imageWidth / 2, 44, 0xFFFFFF);
            StealHudOverlay.drawBar(g, imageWidth / 2 - 91, 56, steal, !totem.isOwner(minecraft.player.getUUID()), 0);
            if (steal.outside()) {
                g.drawCenteredString(font, Component.translatable("gui.bsp_core.shatter_totem.grace", (steal.graceLeft() + 19) / 20).withStyle(ChatFormatting.YELLOW),
                        imageWidth / 2, 64, 0xFFFFFF);
            }
        } else if (totem.isOwner(minecraft.player.getUUID())) {
            g.drawCenteredString(font, Component.translatable("gui.bsp_core.shatter_totem.yours").withStyle(ChatFormatting.GREEN), imageWidth / 2, 46, 0xFFFFFF);
        } else {
            g.drawCenteredString(font, Component.translatable("gui.bsp_core.shatter_totem.hint").withStyle(ChatFormatting.GRAY), imageWidth / 2, 46, 0xFFFFFF);
        }

        // placed-only upgrades (visible to everyone, buyable by the owner)
        int row = 0;
        for (TotemUpgrades.Buff buff : PLACED_BUFFS) {
            int y = PLACED_ROW_Y + row * PLACED_ROW_H;
            int lvl = totem.getUpgradeLevel(buff);
            g.drawString(font, Component.translatable(buff.translationKey()).withStyle(ChatFormatting.WHITE), 10, y, 0xFFFFFF);
            Component sub = lvl > 0
                    ? Component.translatable("gui.bsp_core.shatter_totem.aura_level", lvl, buff.maxLevel(), buff.radius(lvl))
                    : Component.translatable("gui.bsp_core.shatter_totem.aura_inactive", buff.maxLevel());
            g.drawString(font, sub.copy().withStyle(ChatFormatting.GRAY), 10, y + 10, 0xFFFFFF);
            row++;
        }
        if (totem.isOwner(minecraft.player.getUUID()) || admin) {
            int coins = CoinWallet.totalValue(minecraft.player);
            g.drawString(font, Component.translatable("gui.bsp_core.shatter_totem.your_coins", coins).withStyle(ChatFormatting.GOLD),
                    10, PLACED_ROW_Y + PLACED_ROW_H * PLACED_BUFFS.length + 2, 0xFFFFFF);
        }
    }
}
