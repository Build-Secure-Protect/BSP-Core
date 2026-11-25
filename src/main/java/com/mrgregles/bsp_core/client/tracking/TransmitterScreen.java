package com.mrgregles.bsp_core.client.tracking;

import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.tracking.TransmitterMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import static com.mrgregles.bsp_core.client.tracking.Hud.*;

/**
 * The Remote Control Transmitter's screen, the owner's pick (P6): the "Map Table", with the "Signal Tower"'s team heads. On the left, a map
 * of the land round the transmitter with its range drawn as a square (the transmitter in gold, tagged machines inside it turquoise, those
 * outside grey; a ripple runs out while someone operates through it). On the right: the range in blocks, the three range card slots,
 * the power bar with its draw, and the placer and their team as heads in sockets, a crown for the placer and an online lamp for the rest.
 * The player's inventory below, and beside it the Inventory Link panel (the owner's pick, look A): an off or lit lamp, what the upgrade
 * does, and for the placer a gold BUY key with its Illyrium Shatter Coin price, INSTALLED once bought.
 */
public class TransmitterScreen extends AbstractContainerScreen<TransmitterMenu> {
    private static final int MX = 8, MY = 21, MW = 150, MH = 118;
    /** The Inventory Link panel beside the inventory, and its BUY key inside it. */
    private static final int LX = 176, LY = 159, LW = 136, LH = 76, KX = LX + 7, KY = LY + 44, KW = LW - 14, KH = 22;

    public TransmitterScreen(TransmitterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 320;
        imageHeight = 244;
        inventoryLabelY = 10000;
        titleLabelY = 10000;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        rect(g, x, y, imageWidth, imageHeight, BG);
        outline(g, x, y, imageWidth, imageHeight, 0xFF05070A);
        outline(g, x + 1, y + 1, imageWidth - 2, imageHeight - 2, DIM);
        rect(g, x, y, 6, 1, TQ);
        rect(g, x, y, 1, 6, TQ);
        rect(g, x + imageWidth - 6, y, 6, 1, TQ);
        rect(g, x + imageWidth - 1, y, 1, 6, TQ);
        text(g, tr("gui.bsp_core.transmitter.title").getString(), x + 8, y + 6, TQ);
        String owner = menu.people.isEmpty() ? "" : menu.people.get(0).name();
        small(g, fitSmall(owner, 140), x + imageWidth - 8 - Math.min(smallWidth(owner), 140), y + 7, MUTED);
        rect(g, x + 4, y + 17, imageWidth - 8, 1, TQ);
        map(g, x + MX, y + MY);
        right(g, x + 164, y);
        // card slots and the inventory
        for (int s = 0; s < 3; s++) {
            slotFrame(g, x + TransmitterMenu.CARD_X - 1 + s * 22, y + TransmitterMenu.CARD_Y - 1);
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                slotFrame(g, x + TransmitterMenu.INV_X - 1 + c * 18, y + TransmitterMenu.INV_Y - 1 + r * 18);
            }
        }
        for (int c = 0; c < 9; c++) {
            slotFrame(g, x + TransmitterMenu.INV_X - 1 + c * 18, y + TransmitterMenu.INV_Y + 57);
        }
        link(g, x + LX, y + LY, mouseX, mouseY);
    }

    private boolean linked() {
        return menu.data(TransmitterMenu.LINK) == 1;
    }

    /** Whether the viewer placed this transmitter (only they can buy the upgrade). */
    private boolean viewerOwns() {
        var p = Minecraft.getInstance().player;
        return p != null && !menu.people.isEmpty() && menu.people.get(0).owner() && menu.people.get(0).id().equals(p.getUUID());
    }

    private void link(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        rect(g, x, y, LW, LH, 0xFF151B24);
        outline(g, x, y, LW, LH, BLACK);
        outline(g, x + 1, y + 1, LW - 2, LH - 2, DIM);
        corners(g, x, y, LW, LH, TQ, 5);
        text(g, tr("gui.bsp_core.transmitter.link").getString(), x + 7, y + 6, TQ);
        lamp(g, x + LW - 10, y + 10, 3, TQ, linked());
        small(g, fitSmall(tr("gui.bsp_core.transmitter.link_what1").getString(), LW - 14), x + 7, y + 20, FG);
        small(g, fitSmall(tr("gui.bsp_core.transmitter.link_what2").getString(), LW - 14), x + 7, y + 29, FG);
        int kx = x + KX - LX, ky = y + KY - LY;
        if (linked()) {
            rect(g, kx, ky, KW, KH, 0xFF0B2B28);
            outline(g, kx, ky, KW, KH, TQ);
            String t = tr("gui.bsp_core.transmitter.link_installed").getString();
            text(g, t, kx + KW / 2 - font().width(t) / 2, ky + 4, TQ);
            String k = fitSmall(tr("gui.bsp_core.transmitter.link_kept").getString(), KW - 6);
            small(g, k, kx + KW / 2f - smallWidth(k) / 2f, ky + 14, MUTED);
        } else if (viewerOwns()) {
            boolean hover = mouseX >= kx && mouseX < kx + KW && mouseY >= ky && mouseY < ky + KH;
            key(g, kx, ky, KW, KH, "", false, hover, GOLD);
            String t = tr("gui.bsp_core.transmitter.link_buy", menu.data(TransmitterMenu.LINK_PRICE)).getString();
            int tw = font().width(t), sx = kx + KW / 2 - (tw + 18) / 2;
            text(g, t, sx, ky + 7, GOLD);
            g.renderItem(new ItemStack(CoinTier.ILLYRIUM.coin()), sx + tw + 2, ky + 3);
        } else {
            key(g, kx, ky, KW, KH, "", false, false, OFF);
            String t = tr("gui.bsp_core.transmitter.link_owner_only").getString();
            text(g, t, kx + KW / 2 - font().width(t) / 2, ky + 7, OFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int kx = leftPos + KX, ky = topPos + KY;
        if (button == 0 && !linked() && viewerOwns() && mouseX >= kx && mouseX < kx + KW && mouseY >= ky && mouseY < ky + KH
                && Minecraft.getInstance().gameMode != null) {
            Minecraft.getInstance().gameMode.handleInventoryButtonClick(menu.containerId, TransmitterMenu.BUY_LINK);
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static void slotFrame(GuiGraphics g, int x, int y) {
        rect(g, x, y, 18, 18, 0xFF3A3F4B);
        rect(g, x + 1, y + 1, 17, 17, 0xFF2B303B);
        rect(g, x + 1, y + 1, 16, 16, GLASS);
    }

    private void map(GuiGraphics g, int x, int y) {
        BlockPos at = menu.transmitter.getBlockPos();
        int range = menu.data(TransmitterMenu.RANGE);
        int zoom = Math.max(1, (int) Math.ceil(range * 2.3 / Math.min(MW, MH)));
        MapView.drawCentred(g, x, y, MW, MH, at.getX() + 0.5, at.getZ() + 0.5, zoom);
        rect(g, x, y, MW, MH, 0x2E080C10);
        int rr = range / zoom, cx = x + MW / 2, cy = y + MH / 2;
        g.enableScissor(x, y, x + MW, y + MH);
        rect(g, cx - rr, cy - rr, rr * 2 + 1, rr * 2 + 1, 0x2419D3B0);
        outline(g, cx - rr, cy - rr, rr * 2 + 1, rr * 2 + 1, TQ);
        if (menu.data(TransmitterMenu.IN_USE) == 1) {
            int p = (int) ((System.currentTimeMillis() / 30) % Math.max(1, rr));
            outline(g, cx - p, cy - p, p * 2 + 1, p * 2 + 1, 0x6619D3B0);
        }
        ListTag tags = ClientTracking.view().getList("tags", Tag.TAG_COMPOUND);
        String dim = Minecraft.getInstance().level == null ? "" : Minecraft.getInstance().level.dimension().location().toString();
        for (int i = 0; i < tags.size(); i++) {
            CompoundTag t = tags.getCompound(i);
            if (!t.contains("pos") || !dim.equals(t.getString("dim"))) {
                continue;
            }
            BlockPos p = NbtUtils.readBlockPos(t.getCompound("pos"));
            int px = cx + (p.getX() - at.getX()) / zoom, py = cy + (p.getZ() - at.getZ()) / zoom;
            boolean in = Math.abs(p.getX() - at.getX()) <= range && Math.abs(p.getY() - at.getY()) <= range && Math.abs(p.getZ() - at.getZ()) <= range;
            rect(g, px - 2, py - 2, 5, 5, BLACK);
            rect(g, px - 1, py - 1, 3, 3, in ? TQ : OFF);
        }
        g.disableScissor();
        rect(g, cx - 2, cy - 2, 5, 5, BLACK);
        rect(g, cx - 1, cy - 1, 3, 3, GOLD);
        outline(g, x - 1, y - 1, MW + 2, MH + 2, DIM);
        small(g, fitSmall(tr("gui.bsp_core.transmitter.legend").getString(), MW), x, y + MH + 3, OFF);
    }

    private void right(GuiGraphics g, int x, int y) {
        int range = menu.data(TransmitterMenu.RANGE);
        small(g, tr("gui.bsp_core.transmitter.range").getString(), x, y + 22, MUTED);
        text(g, tr("gui.bsp_core.transmitter.blocks", range).getString(), x, y + 31, TQ);
        small(g, tr("gui.bsp_core.transmitter.cube", range * 2 + 1).getString(), x, y + 41, OFF);
        small(g, tr("gui.bsp_core.transmitter.cards").getString(), x, y + 52, MUTED);
        small(g, tr("gui.bsp_core.transmitter.cards_count", menu.data(TransmitterMenu.CARDS)).getString(), x + 70, y + 66, FG);
        small(g, tr("gui.bsp_core.transmitter.power").getString(), x, y + 84, MUTED);
        int fe = menu.fe(), max = Math.max(1, menu.feMax());
        bar(g, x, y + 93, 148, 7, fe / (float) max, GOLD);
        small(g, compact(fe) + " / " + compact(max) + " FE", x, y + 102, GOLD);
        String draw = menu.data(TransmitterMenu.IN_USE) == 1 ? tr("gui.bsp_core.transmitter.drawing", menu.data(TransmitterMenu.FE_TICK)).getString()
                : tr("gui.bsp_core.transmitter.idle").getString();
        small(g, draw, x + 148 - smallWidth(draw), y + 102, MUTED);
        small(g, tr("gui.bsp_core.transmitter.team").getString(), x, y + 113, MUTED);
        for (int i = 0; i < Math.min(4, menu.people.size()); i++) {
            TransmitterMenu.Person p = menu.people.get(i);
            int sx = x + i * 37, sy = y + 120;
            rect(g, sx, sy, 34, 34, BLACK);
            rect(g, sx + 1, sy + 1, 32, 32, STEEL);
            well(g, sx + 8, sy + 3, 18, 18, 0xFF0A0E13);
            PlayerFaceRenderer.draw(g, skin(p), sx + 9, sy + 4, 16);
            if (p.owner()) {
                rect(g, sx + 26, sy + 5, 6, 3, GOLD);
                rect(g, sx + 26, sy + 3, 1, 2, GOLD);
                rect(g, sx + 28, sy + 3, 2, 2, GOLD);
                rect(g, sx + 31, sy + 3, 1, 2, GOLD);
            } else {
                lamp(g, sx + 29, sy + 5, 2, TQ, p.online());
            }
            small(g, fitSmall(p.name(), 31), sx + 17 - Math.min(smallWidth(p.name()), 31) / 2, sy + 24, p.online() ? FG : MUTED);
        }
        if (menu.people.size() > 4) {
            small(g, "+" + (menu.people.size() - 4), x + 148 - smallWidth("+" + (menu.people.size() - 4)), y + 113, MUTED);
        }
    }

    private static ResourceLocation skin(TransmitterMenu.Person p) {
        var conn = Minecraft.getInstance().getConnection();
        PlayerInfo info = conn == null ? null : conn.getPlayerInfo(p.id());
        return info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(p.id());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
    }
}
