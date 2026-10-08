package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.menu.ShatterTotemMenu;
import com.mrgregles.bsp_core.network.AdminTotemActionPacket;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.ChunkViewPacket;
import com.mrgregles.bsp_core.network.PlacedUpgradeRequestPacket;
import com.mrgregles.bsp_core.network.StealRequestPacket;
import com.mrgregles.bsp_core.network.UpgradeRequestPacket;
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
    private static final int WALLET_Y = 18, TREE_X = 10, TREE_Y = 38, DETAIL_X = 188, DETAIL_Y = 40, STATUS_Y = TREE_Y + TotemTree.H + 5, TAB_X = 214, TAB_W = 40, AURAS_W = 56, SWITCH_W = 26, SWITCH_H = 11;

    private final TotemTree tree = new TotemTree();
    private Button stealButton;
    private boolean admin, operatorTab;
    private EditBox ownerBox;
    private final List<Button> adminButtons = new ArrayList<>();

    public ShatterTotemScreen(ShatterTotemMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 326;
        this.imageHeight = STATUS_Y + 48;
    }

    @Override
    protected void init() {
        super.init();
        admin = minecraft.player != null && minecraft.player.hasPermissions(2);
        stealButton = Button.builder(Component.translatable("gui.bsp_core.shatter_totem.steal"), b -> {
            BSPNetwork.CHANNEL.sendToServer(new StealRequestPacket(menu.getPos()));
            onClose();
        }).bounds(leftPos + 214, topPos + STATUS_Y + 16, 102, 20).build();
        addRenderableWidget(stealButton);
        nameBox = new EditBox(font, leftPos + 10, topPos + TREE_Y + 1, 150, 16, Component.translatable("gui.bsp_core.access.name"));
        nameBox.setMaxLength(16);
        nameBox.setHint(Component.translatable("gui.bsp_core.access.name").withStyle(ChatFormatting.DARK_GRAY));
        addRenderableWidget(nameBox);
        addButton = Button.builder(Component.translatable("gui.bsp_core.access.add"), b -> addAccess()).bounds(leftPos + 164, topPos + TREE_Y, 50, 18).build();
        addRenderableWidget(addButton);
        adminButtons.clear();
        if (admin) {
            int x = leftPos + 10, y = topPos + STATUS_Y + 6;
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
        if (chunksTab && ++chunkTicks % 40 == 0) {
            ChunkMap.request(menu.getPos());
        }
        refresh();
    }

    private boolean isOwner() {
        ShatterTotemBlockEntity totem = menu.getTotem();
        return totem != null && totem.isOwner(minecraft.player.getUUID());
    }

    private static final int T_TOTEM = 0, T_CHUNKS = 1, T_OPERATOR = 2, T_ACCESS = 3, MAP_TILE = 24, ACC_ROW = 18, ACC_Y = TREE_Y + 22, ACC_COL = 118, ACC_W = 40, ACC_GAP = 46;
    private static final int[] ACC_FLAGS = {com.mrgregles.bsp_core.totem.TotemAccess.UPGRADES, com.mrgregles.bsp_core.totem.TotemAccess.ALARM, com.mrgregles.bsp_core.totem.TotemAccess.WARD, com.mrgregles.bsp_core.totem.TotemAccess.MACHINES};
    private boolean chunksTab, accessTab;
    private EditBox nameBox;
    private Button addButton;
    private int chunkTicks;

    /** The CHUNKS tab is there for the owner (and admins) once the totem has the Anchor upgrade. */
    private boolean chunksAvailable() {
        ShatterTotemBlockEntity totem = menu.getTotem();
        return totem != null && totem.getUpgradeLevel(Buff.ANCHOR) > 0 && (isOwner() || admin);
    }

    /** The tabs in the header, left to right. With only the totem's own page there are none. */
    private int[] tabs() {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        boolean mine = isOwner() || admin;
        if (mine) {
            out.add(T_TOTEM);
            out.add(T_ACCESS);
        }
        if (chunksAvailable()) {
            if (!mine) {
                out.add(T_TOTEM);
            }
            out.add(T_CHUNKS);
        }
        if (admin) {
            out.add(T_OPERATOR);
        }
        return out.size() == 1 ? new int[0] : out.stream().mapToInt(Integer::intValue).toArray();
    }

    private int tabX(int index, int count) {
        return imageWidth - 10 - (count - index) * (TAB_W + 2) + 2;
    }

    /** The AURAS switch sits left of the tabs in the header. */
    private int aurasX() {
        int[] tabs = tabs();
        return (tabs.length == 0 ? imageWidth - 10 + 2 : tabX(0, tabs.length)) - AURAS_W - 2;
    }

    private int currentTab() {
        return operatorTab ? T_OPERATOR : chunksTab ? T_CHUNKS : accessTab ? T_ACCESS : T_TOTEM;
    }

    private void setTab(int tab) {
        operatorTab = tab == T_OPERATOR;
        chunksTab = tab == T_CHUNKS;
        accessTab = tab == T_ACCESS;
        if (chunksTab) {
            ChunkMap.request(menu.getPos());
        }
        refresh();
    }

    private void refresh() {
        ShatterTotemBlockEntity totem = menu.getTotem();
        if (chunksTab && !chunksAvailable()) {
            chunksTab = false;
        }
        if (accessTab && !(isOwner() || admin)) {
            accessTab = false;
        }
        if (nameBox != null) {
            nameBox.setVisible(accessTab);
            addButton.visible = accessTab;
            addButton.active = accessTab;
            if (!accessTab) {
                nameBox.setFocused(false);
            }
        }
        boolean canSteal = !operatorTab && !chunksTab && !accessTab && totem != null && !isOwner() && totem.getSteal().isEmpty();
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

    private int tier() {
        ShatterTotemBlockEntity totem = menu.getTotem();
        return totem == null ? 0 : totem.getTier();
    }

    /** Why the selected upgrade cannot be bought right now, or null if it can. */
    @Nullable
    private Component blocked(Buff b) {
        if (!isOwner() && !admin) {
            return level(b) >= b.maxLevel() ? null : Component.translatable("gui.bsp_core.tree.not_yours");
        }
        return TotemUpgrades.whyNot(b, this::level, tier(), minecraft.player);
    }

    private boolean canBuy(Buff b) {
        return level(b) < b.maxLevel() && blocked(b) == null;
    }

    private boolean canRaise() {
        return (isOwner() || admin) && tier() < TotemUpgrades.MAX_TIER && TotemUpgrades.whyNotGate(tier(), minecraft.player) == null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = leftPos, y = topPos;
        int[] tabs = tabs();
        if (my >= y + 3 && my < y + 16) { // tabs sit in the header
            if (mx >= x + aurasX() && mx < x + aurasX() + AURAS_W) {
                com.mrgregles.bsp_core.BSPClientConfig.toggleAuras();
                return true;
            }
            for (int i = 0; i < tabs.length; i++) {
                if (mx >= x + tabX(i, tabs.length) && mx < x + tabX(i, tabs.length) + TAB_W) {
                    setTab(tabs[i]);
                    return true;
                }
            }
        }
        if (accessTab) {
            ShatterTotemBlockEntity totem = menu.getTotem();
            if (totem != null) {
                for (int i = 0; i < totem.access().size(); i++) {
                    int ry = y + ACC_Y + i * ACC_ROW;
                    for (int k = 0; k < ACC_FLAGS.length; k++) {
                        if (mx >= x + ACC_COL + k * ACC_GAP && mx < x + ACC_COL + k * ACC_GAP + ACC_W && my >= ry && my < ry + 14) {
                            BSPNetwork.CHANNEL.sendToServer(new com.mrgregles.bsp_core.network.TotemAccessPacket(menu.getPos(), com.mrgregles.bsp_core.network.TotemAccessPacket.TOGGLE, "", i, ACC_FLAGS[k]));
                            return true;
                        }
                    }
                    if (mx >= x + ACC_COL + 4 * ACC_GAP && mx < x + ACC_COL + 4 * ACC_GAP + 14 && my >= ry && my < ry + 14) {
                        BSPNetwork.CHANNEL.sendToServer(new com.mrgregles.bsp_core.network.TotemAccessPacket(menu.getPos(), com.mrgregles.bsp_core.network.TotemAccessPacket.REMOVE, "", i, 0));
                        return true;
                    }
                }
            }
            return super.mouseClicked(mx, my, button);
        }
        if (chunksTab) {
            ChunkViewPacket view = ChunkMap.viewFor(menu.getPos());
            if (view != null && ChunkMap.click(mx, my, x + TREE_X + 1, y + TREE_Y + 1, MAP_TILE, view)) {
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }
        Buff node = tree.nodeAt(mx, my, x + TREE_X, y + TREE_Y);
        if (node != null) {
            tree.selected = node;
            return true;
        }
        Buff b = tree.selected;
        boolean buy = TotemTree.overBuy(mx, my, x + DETAIL_X, y + DETAIL_Y), gate = TotemTree.overGate(mx, my, x + DETAIL_X, y + DETAIL_Y);
        if (operatorTab) {
            // both bars become two halves: lower on the left, raise on the right
            if (buy) {
                send(AdminTotemActionPacket.Action.SET_BUFF, mx < x + DETAIL_X + TotemTree.DETAIL_W / 2.0 ? "-" : "+", b.ordinal());
                return true;
            }
            if (gate) {
                send(AdminTotemActionPacket.Action.SET_BUFF, mx < x + DETAIL_X + TotemTree.GATE_BTN_X + TotemTree.GATE_BTN_W / 2.0 ? "-" : "+", UpgradeRequestPacket.RAISE_TIER);
                return true;
            }
        } else if (buy && canBuy(b)) {
            BSPNetwork.CHANNEL.sendToServer(new PlacedUpgradeRequestPacket(menu.getPos(), b.ordinal()));
            return true;
        } else if (gate && canRaise()) {
            BSPNetwork.CHANNEL.sendToServer(new PlacedUpgradeRequestPacket(menu.getPos(), UpgradeRequestPacket.RAISE_TIER));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (nameBox != null && nameBox.isFocused()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                nameBox.setFocused(false);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                addAccess();
                return true;
            }
            nameBox.keyPressed(key, scan, mods);
            return true;
        }
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
        Buff hover = chunksTab || accessTab ? null : tree.nodeAt(mouseX, mouseY, leftPos + TREE_X, topPos + TREE_Y);
        if (hover != null) {
            g.renderTooltip(font, Component.translatable("gui.bsp_core.tree.node", Component.translatable(hover.translationKey()), level(hover), hover.maxLevel()), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, operatorTab ? VIO : TQ);
        g.fill(x, y, x + imageWidth, y + imageHeight, BG);
        int[] tabs = tabs();
        for (int i = 0; i < tabs.length; i++) {
            int tx = x + tabX(i, tabs.length);
            g.fill(tx, y + 3, tx + TAB_W, y + 16, tabs[i] != currentTab() ? DIM : tabs[i] == T_OPERATOR ? VIO : TQ);
            g.fill(tx + 1, y + 4, tx + TAB_W - 1, y + 15, SLOT_BG);
        }
        // the AURAS switch, drawn like the machines' power switch: housing, rail, a knob that slides right when on
        boolean shown = com.mrgregles.bsp_core.BSPClientConfig.showAuras();
        int sx = x + aurasX() + AURAS_W - SWITCH_W, sy = y + 4, sc = shown ? TQ : 0xFFFF6B5C;
        g.fill(sx - 1, sy - 1, sx + SWITCH_W + 1, sy + SWITCH_H + 1, sc);
        g.fill(sx, sy, sx + SWITCH_W, sy + SWITCH_H, SLOT_BG);
        g.fill(sx + 3, sy + 4, sx + SWITCH_W - 3, sy + SWITCH_H - 4, DIM);
        if (shown) {
            g.fill(sx + 3, sy + 4, sx + SWITCH_W - 3, sy + SWITCH_H - 4, sc);
        }
        int kx = shown ? sx + SWITCH_W - 10 : sx + 2;
        g.fill(kx, sy + 1, kx + 8, sy + SWITCH_H - 1, 0xFFC9CED8);
        g.fill(kx + 1, sy + 2, kx + 7, sy + SWITCH_H - 2, 0xFF8A909C);
        if (accessTab) {
            accessRows(g, x, y, mouseX, mouseY);
            return;
        }
        if (chunksTab) {
            chunks(g, x, y, mouseX, mouseY);
            return;
        }
        TotemTree.wallet(g, font, x + 10, y + WALLET_Y, minecraft.player);
        tree.render(g, font, x + TREE_X, y + TREE_Y, this::level, tier());
        Buff b = tree.selected;
        if (operatorTab) {
            Component set = Component.translatable("gui.bsp_core.tree.set_level");
            tree.detail(g, font, x + DETAIL_X, y + DETAIL_Y, level(b), tier(), null, set, true, mouseX, mouseY);
            tree.gate(g, font, x + DETAIL_X, y + DETAIL_Y, Math.min(tier(), TotemUpgrades.MAX_TIER - 1), Component.literal("-  +"), true, mouseX, mouseY);
        } else {
            tree.detail(g, font, x + DETAIL_X, y + DETAIL_Y, level(b), tier(), blocked(b),
                    Component.translatable(level(b) >= b.maxLevel() ? "gui.bsp_core.upgrades.maxed" : "gui.bsp_core.tree.buy"), canBuy(b), mouseX, mouseY);
            tree.gate(g, font, x + DETAIL_X, y + DETAIL_Y, tier(), Component.translatable("gui.bsp_core.tree.raise"), canRaise(), mouseX, mouseY);
        }
    }

    private void addAccess() {
        if (nameBox != null && !nameBox.getValue().isBlank()) {
            BSPNetwork.CHANNEL.sendToServer(new com.mrgregles.bsp_core.network.TotemAccessPacket(menu.getPos(), com.mrgregles.bsp_core.network.TotemAccessPacket.ADD, nameBox.getValue().trim(), 0, 0));
            nameBox.setValue("");
        }
    }

    /** The ACCESS tab: one row per friend with four switches and a remove button; the name box and Add are widgets. */
    private void accessRows(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        ShatterTotemBlockEntity totem = menu.getTotem();
        if (totem == null) {
            return;
        }
        String[] cols = {"upgrades", "alarm", "ward", "machines"};
        for (int k = 0; k < cols.length; k++) {
            Component c = Component.translatable("gui.bsp_core.access.col." + cols[k]);
            small(g, c, x + ACC_COL + k * ACC_GAP + ACC_W / 2 - font.width(c) * 3 / 8, y + ACC_Y - 10, MUTED);
        }
        java.util.List<com.mrgregles.bsp_core.totem.TotemAccess> list = totem.access();
        for (int i = 0; i < list.size(); i++) {
            int ry = y + ACC_Y + i * ACC_ROW;
            g.drawString(font, list.get(i).name(), x + 10, ry + 3, 0xE6EAF2, false);
            for (int k = 0; k < ACC_FLAGS.length; k++) {
                int bx = x + ACC_COL + k * ACC_GAP;
                boolean on = list.get(i).has(ACC_FLAGS[k]);
                g.fill(bx, ry, bx + ACC_W, ry + 14, on ? TQ : DIM);
                g.fill(bx + 1, ry + 1, bx + ACC_W - 1, ry + 13, mouseX >= bx && mouseX < bx + ACC_W && mouseY >= ry && mouseY < ry + 14 ? 0xFF14362F : SLOT_BG);
                Component lab = Component.translatable(on ? "gui.bsp_core.access.yes" : "gui.bsp_core.access.no");
                small(g, lab, bx + ACC_W / 2 - font.width(lab) * 3 / 8, ry + 4, on ? TQ & 0xFFFFFF : MUTED);
            }
            int rx = x + ACC_COL + 4 * ACC_GAP;
            g.fill(rx, ry, rx + 14, ry + 14, DIM);
            g.fill(rx + 1, ry + 1, rx + 13, ry + 13, SLOT_BG);
            g.drawString(font, "x", rx + 4, ry + 3, 0xFF6B5C, false);
        }
        small(g, Component.translatable("gui.bsp_core.access.count", list.size(), com.mrgregles.bsp_core.totem.TotemAccess.MAX), x + 10, y + ACC_Y + list.size() * ACC_ROW + 4, MUTED);
        for (int i = 0; i < 4; i++) {
            small(g, Component.translatable("gui.bsp_core.access.help." + i), x + 10, y + STATUS_Y - 40 + i * 9, MUTED);
        }
    }

    /** The CHUNKS tab: the map where the tree is, and the allowance beside it. */
    private void chunks(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        g.fill(x + TREE_X, y + TREE_Y, x + TREE_X + TotemTree.W, y + TREE_Y + TotemTree.H, SLOT_BG);
        ChunkViewPacket v = ChunkMap.viewFor(menu.getPos());
        if (v == null) {
            return;
        }
        ChunkMap.draw(g, x + TREE_X + 1, y + TREE_Y + 1, MAP_TILE, v, mouseX, mouseY);
        int dx = x + DETAIL_X, dy = y + DETAIL_Y, side = v.radius() * 2 + 1;
        g.drawString(font, Component.translatable("gui.bsp_core.chunks.loaded", v.used(), v.slots()), dx, dy, 0xFFD23A, false);
        ChunkMap.pips(g, dx, dy + 12, v);
        g.drawString(font, Component.translatable("gui.bsp_core.chunks.range", side, side), dx, dy + 26, 0xE8EAF0, false);
        int ly = dy + 42;
        for (String key : new String[]{"help.0", "help.1", "help.2"}) {
            small(g, Component.translatable("gui.bsp_core.chunks." + key), dx, ly, MUTED);
            ly += 9;
        }
        ly += 5;
        if (v.others().length > 0) {
            small(g, Component.translatable("gui.bsp_core.chunks.projectors", v.others().length), dx, ly, VIO & 0xFFFFFF);
            ly += 9;
        }
        if (!v.has(ChunkViewPacket.MAIN)) {
            small(g, Component.translatable("gui.bsp_core.chunks.second.0"), dx, ly, 0xFF6B5C);
            small(g, Component.translatable("gui.bsp_core.chunks.second.1"), dx, ly + 9, 0xFF6B5C);
            ly += 18;
        }
        if (v.has(ChunkViewPacket.ONLINE_ONLY)) {
            small(g, Component.translatable("gui.bsp_core.chunks.online_only"), dx, ly, 0xFFD23A);
            ly += 9;
        }
        if (!v.has(ChunkViewPacket.ENABLED)) {
            small(g, Component.translatable("gui.bsp_core.chunks.disabled"), dx, ly, 0xFF6B5C);
        }
        int legend = y + TREE_Y + TotemTree.H - 30;
        String[] names = {"totem", "picked", "projector"};
        int[] colours = {0xFFFFD23A, TQ, VIO};
        for (int i = 0; i < names.length; i++) {
            g.fill(dx, legend + i * 10, dx + 6, legend + i * 10 + 6, colours[i]);
            small(g, Component.translatable("gui.bsp_core.chunks.legend." + names[i]), dx + 10, legend + i * 10, MUTED);
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
        // the tier joins the title only while the header has room for it
        String head = title.getString().toUpperCase(Locale.ROOT);
        if (tabs().length < 2) {
            head += "  " + Component.translatable("gui.bsp_core.tree.tier", TotemUpgrades.roman(tier())).getString();
        }
        g.drawString(font, head, 10, 7, (operatorTab ? VIO : TQ) & 0xFFFFFF, false);
        boolean shown = com.mrgregles.bsp_core.BSPClientConfig.showAuras();
        Component auras = Component.translatable("gui.bsp_core.tree.auras.label");
        small(g, auras, aurasX(), 7, shown ? TQ & 0xFFFFFF : MUTED);
        int[] tabs = tabs();
        String[] tabKeys = {"totem", "chunks", "operator", "access"};
        for (int i = 0; i < tabs.length; i++) {
            Component label = Component.translatable("gui.bsp_core.tree.tab." + tabKeys[tabs[i]]);
            small(g, label, tabX(i, tabs.length) + TAB_W / 2 - font.width(label) * 3 / 8, 7, tabs[i] != currentTab() ? MUTED : (tabs[i] == T_OPERATOR ? VIO : TQ) & 0xFFFFFF);
        }
        ShatterTotemBlockEntity totem = menu.getTotem();
        if (totem == null || operatorTab || accessTab) {
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
