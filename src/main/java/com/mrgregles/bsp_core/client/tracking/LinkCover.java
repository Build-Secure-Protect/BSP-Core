package com.mrgregles.bsp_core.client.tracking;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.tracking.InventoryLink;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.ref.WeakReference;

import static com.mrgregles.bsp_core.client.tracking.Hud.*;

/**
 * The owner's pick for a locked machine screen (Inventory Link, look A "Lock plate"): in a machine screen operated from the Holo Grid
 * through a transmitter without the Inventory Link upgrade, a dark steel plate with turquoise corners, four screws and a gold padlock is
 * laid over the player's inventory, drawn last so nothing shows through, saying the link is off and where to get it. Clicks on it do
 * nothing, and neither tooltips nor shift-clicks reach under it. The server refuses those moves anyway ({@link InventoryLink}); this only shows it and spares the flicker.
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID, value = Dist.CLIENT)
public final class LinkCover {
    /** The menu the server said is locked: its screen keeps the plate, also when JEI or EMI show a recipe and come back to it. */
    private static WeakReference<AbstractContainerMenu> locked = new WeakReference<>(null);
    /** A lock for a screen not open yet (its packet came first), by container id. */
    private static int pendingId = -1;

    private LinkCover() {}

    /** From the server: the screen with this container id was just opened from the Holo Grid, locked or not. */
    public static void opened(int containerId, boolean isLocked) {
        locked = new WeakReference<>(null);
        pendingId = -1;
        var player = Minecraft.getInstance().player;
        if (!isLocked || player == null) {
            return;
        }
        if (player.containerMenu != null && player.containerMenu != player.inventoryMenu && player.containerMenu.containerId == containerId) {
            locked = new WeakReference<>(player.containerMenu);
        } else {
            pendingId = containerId;
        }
    }

    @SubscribeEvent
    public static void onOpen(ScreenEvent.Opening event) {
        if (pendingId >= 0 && event.getNewScreen() instanceof AbstractContainerScreen<?> acs && acs.getMenu().containerId == pendingId) {
            locked = new WeakReference<>(acs.getMenu());
        }
        pendingId = -1; // only the very next screen: a later one with the same id is another
    }

    /** Whether {@code s} shows the locked menu, still the player's open one. */
    private static boolean isLocked(AbstractContainerScreen<?> s) {
        AbstractContainerMenu m = locked.get();
        var player = Minecraft.getInstance().player;
        if (m == null || player == null || player.containerMenu != m) {
            locked = new WeakReference<>(null); // closed (or another opened): the lock is over
            return false;
        }
        return s.getMenu() == m;
    }

    /** The plate's place in the screen's own coordinates (x, y, w, h), or null when the screen is not locked or shows no inventory. */
    private static int[] plate(AbstractContainerScreen<?> s) {
        if (!isLocked(s)) {
            return null;
        }
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
        for (Slot slot : s.getMenu().slots) {
            if (slot.isActive() && InventoryLink.playerSlot(slot)) {
                x0 = Math.min(x0, slot.x);
                y0 = Math.min(y0, slot.y);
                x1 = Math.max(x1, slot.x + 16);
                y1 = Math.max(y1, slot.y + 16);
            }
        }
        if (x0 == Integer.MAX_VALUE) {
            return null;
        }
        // the slots and their frames, and the "Inventory" label above them
        return new int[] {x0 - 3, y0 - 14, x1 - x0 + 6, y1 - y0 + 17};
    }

    private static boolean onPlate(AbstractContainerScreen<?> s, double mx, double my) {
        int[] p = plate(s);
        return p != null && mx >= s.getGuiLeft() + p[0] && mx < s.getGuiLeft() + p[0] + p[2] && my >= s.getGuiTop() + p[1] && my < s.getGuiTop() + p[1] + p[3];
    }

    /** Drawn after the whole screen, so nothing of the inventory shows through (its tooltips are not drawn: see {@link #onTooltip}). */
    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> s)) {
            return;
        }
        int[] p = plate(s);
        if (p == null) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        g.pose().pushPose();
        g.pose().translate(s.getGuiLeft(), s.getGuiTop(), 380); // over the items and the held one, under tooltips
        draw(g, p[0], p[1], p[2], p[3]);
        g.pose().popPose();
    }

    static void draw(GuiGraphics g, int x, int y, int w, int h) {
        rect(g, x, y, w, h, 0xFF1C222C);
        outline(g, x, y, w, h, BLACK);
        outline(g, x + 1, y + 1, w - 2, h - 2, 0xFF39414F);
        corners(g, x, y, w, h, TQ, 7);
        for (int i = x + 6; i < x + w - 6; i += 6) {
            rect(g, i, y + h - 8, 3, 1, 0xFF2A313D);
        }
        screw(g, x + 6, y + 6);
        screw(g, x + w - 7, y + 6);
        screw(g, x + 6, y + h - 7);
        screw(g, x + w - 7, y + h - 7);
        int cx = x + w / 2;
        String title = tr("gui.bsp_core.tracking.link_off").getString();
        String[] lines = {tr("gui.bsp_core.tracking.link_off1").getString(), tr("gui.bsp_core.tracking.link_off2").getString(),
                tr("gui.bsp_core.tracking.link_off3").getString()};
        int block = 11 + 4 + 9 + 4 + lines.length * 8;
        int ty = y + Math.max(4, (h - block) / 2);
        padlock(g, cx - 5, ty, GOLD);
        ty += 15;
        String t = fit(title, w - 12);
        text(g, t, cx - font().width(t) / 2, ty, TQ);
        ty += 13;
        for (int i = 0; i < lines.length; i++) {
            String l = fitSmall(lines[i], w - 12);
            small(g, l, cx - smallWidth(l) / 2f, ty + i * 8, i == 0 ? FG : MUTED);
        }
    }

    @SubscribeEvent
    public static void onTooltip(RenderTooltipEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> s && onPlate(s, event.getX(), event.getY())) {
            event.setCanceled(true);
        }
    }

    /** Clicks on the plate do nothing; a shift-click anywhere would reach the inventory, so it does nothing either. */
    @SubscribeEvent
    public static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> s && isLocked(s)
                && (onPlate(s, event.getMouseX(), event.getMouseY()) || (Screen.hasShiftDown() && s.getSlotUnderMouse() != null))) {
            event.setCanceled(true);
        }
    }

    /** Letting go or dragging over the plate does nothing either (with an item held it would land in a hidden slot). */
    @SubscribeEvent
    public static void onRelease(ScreenEvent.MouseButtonReleased.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> s && onPlate(s, event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDrag(ScreenEvent.MouseDragged.Pre event) {
        if (event.getScreen() instanceof AbstractContainerScreen<?> s && onPlate(s, event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
        }
    }

    /** The hotbar keys, the offhand key and the drop key do nothing over a slot in a locked screen (they would reach the inventory). */
    @SubscribeEvent
    public static void onKey(ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> s) || !isLocked(s) || s.getSlotUnderMouse() == null) {
            return;
        }
        var o = Minecraft.getInstance().options;
        int key = event.getKeyCode(), scan = event.getScanCode();
        boolean hotbar = false;
        for (var k : o.keyHotbarSlots) {
            hotbar |= k.matches(key, scan);
        }
        if (hotbar || o.keySwapOffhand.matches(key, scan) || o.keyDrop.matches(key, scan)) {
            event.setCanceled(true);
        }
    }
}
