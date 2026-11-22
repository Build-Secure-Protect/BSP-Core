package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.admin.Admins;
import com.mrgregles.bsp_core.demo.DemoTotemBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * What right-clicking a Demo Totem opens: the totem screen's frame with only its LOOKS tab. An admin dresses the demo there as an
 * owner dresses their totem (every hat, no VIP rank needed); anyone else sees it read-only, turning in the well in its look.
 * Opened on the client: no menu, since a demo holds nothing; the tab asks the server for its data as it does on a totem.
 */
public class DemoTotemScreen extends Screen implements ScreenFit.Fitted {
    private static final int W = 326, H = 262, TAB_W = 40;
    private static final int BG = 0xF010151C, TQ = 0xFF19D3B0, SLOT_BG = 0xFF0C0E12;
    private final BlockPos pos;
    private LooksTab looks;

    private DemoTotemScreen(BlockPos pos) {
        super(Component.translatable("block.bsp_core.demo_totem"));
        this.pos = pos;
    }

    public static void open(BlockPos pos) {
        Minecraft.getInstance().setScreen(new DemoTotemScreen(pos));
    }

    @Nullable
    private DemoTotemBlockEntity demo() {
        return minecraft != null && minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof DemoTotemBlockEntity d ? d : null;
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    @Override
    protected void init() {
        if (looks == null) { // a resize calls init again: keep the draft
            looks = new LooksTab(font);
            looks.setDemo(true);
            looks.setReadOnly(minecraft.player == null || !Admins.isAdmin(minecraft.player));
            looks.open(pos);
        }
    }

    @Override
    public void tick() {
        // gone, or walked away from: close, as a container screen does
        if (demo() == null || minecraft.player == null || minecraft.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) {
            onClose();
        }
    }

    /** The inventory key closes it, as it closes a totem's screen. */
    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (minecraft != null && minecraft.options.keyInventory.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = left(), y = top();
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, TQ);
        g.fill(x, y, x + W, y + H, BG);
        // the header: the name, and the one tab, as on a totem's screen
        int tx = x + W - 10 - TAB_W;
        g.fill(tx, y + 3, tx + TAB_W, y + 16, TQ);
        g.fill(tx + 1, y + 4, tx + TAB_W - 1, y + 15, SLOT_BG);
        g.drawString(font, title.getString().toUpperCase(Locale.ROOT), x + 10, y + 7, TQ & 0xFFFFFF, false);
        Component tab = Component.translatable("gui.bsp_core.tree.tab.looks");
        g.pose().pushPose();
        g.pose().translate(tx + TAB_W / 2f - font.width(tab) * 3 / 8f, y + 7, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, tab, 0, 0, TQ & 0xFFFFFF, false);
        g.pose().popPose();
        looks.renderBg(g, x, y, mouseX, mouseY, partialTick, demo());
        super.render(g, mouseX, mouseY, partialTick);
        looks.renderTooltips(g, mouseX, mouseY, x, y);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return looks.mouseClicked(mx, my, button, left(), top(), demo()) || super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        return looks.mouseDragged(mx, my, dx, left(), top()) || super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        return looks.mouseReleased() || super.mouseReleased(mx, my, button);
    }

    @Override
    public int panelWidth() {
        return W;
    }

    @Override
    public int panelHeight() {
        return H;
    }
}
