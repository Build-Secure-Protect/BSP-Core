package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.machine.TotemForgeBlockEntity;
import com.mrgregles.bsp_core.machine.TotemForgeBlockEntity.Metal;
import com.mrgregles.bsp_core.machine.TotemForgeBlockEntity.PourCheck;
import com.mrgregles.bsp_core.machine.TotemForgeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * The Totem Forge's screen, as drafted in tools/preview/totem_forge_concepts.html, section 5. Top left the
 * mould's eight places, filled as the pieces go in; top right a tube for each line of the metal bill; under
 * them the forge's slots (ingots, lava bucket, RF upgrade) and the lava gauge; then what the forge is doing
 * or waiting for, the main key (POUR, the coin price, SETTING, TAKE TOTEM or STEAL) and TAKE PIECE OUT.
 */
public class TotemForgeScreen extends AbstractContainerScreen<TotemForgeMenu> {
    private static final int BG = 0xF010151C, EDGE = 0xFFFFD23A, SLOT_BG = 0xFF0C0E12, SLOT_EDGE = 0xFF3A3F4B, DIM = 0xFF2A2F3A, GOLD = 0xFFFFD23A,
            TQ = 0xFF19D3B0, BAD = 0xFFFF6B5C, LAVA = 0xFFFF7A1A, FG = 0xE8EAF0, MUTED = 0x9AA3B5, HOT = 0xFFFFB347;
    private static final int[] TUBE = {0xFFB58CFF, 0xFF19D3B0, 0xFF7FB3FF, 0xFF6191DD, 0xFFF0C23A, 0xFF34C38F, 0xFFC04CFF, 0xFF8A7F86};
    private static final int MOULD_X = 10, MOULD_Y = 26, BOX = 26, TUBES_X = 136, TUBES_Y = 26, TUBE_H = 52, TUBE_STEP = 14,
            MSG_Y = 134, KEY_Y = 146, KEY_H = 16, MAIN_X = 10, MAIN_W = 118, TAKE_X = 134, TAKE_W = 112;

    public TotemForgeScreen(TotemForgeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = TotemForgeMenu.W;
        imageHeight = TotemForgeMenu.H;
        inventoryLabelY = -1000; // the HUD has its own labels
        titleLabelY = -1000;
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltips(g, mouseX, mouseY);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, EDGE);
        g.fill(x, y, x + imageWidth, y + imageHeight, BG);
        TotemForgeBlockEntity f = menu.forge();
        // mould: two rows of four places
        for (int i = 0; i < TotemForgeBlockEntity.PLACES; i++) {
            int bx = x + MOULD_X + (i % 4) * (BOX + 4), by = y + MOULD_Y + (i / 4) * (BOX + 4);
            ItemStack in = f == null ? ItemStack.EMPTY : f.mouldAt(i);
            boolean next = f != null && in.isEmpty() && ItemStack.isSameItem(f.nextMouldPiece(), TotemForgeBlockEntity.pieceForPlace(i));
            g.fill(bx, by, bx + BOX, by + BOX, in.isEmpty() ? (next ? GOLD : SLOT_EDGE) : GOLD);
            g.fill(bx + 1, by + 1, bx + BOX - 1, by + BOX - 1, SLOT_BG);
            ItemStack show = in.isEmpty() ? TotemForgeBlockEntity.pieceForPlace(i) : in;
            g.renderItem(show, bx + 5, by + 5);
            if (in.isEmpty()) {
                g.fill(bx + 1, by + 1, bx + BOX - 1, by + BOX - 1, next ? 0x7010151C : 0xC010151C); // a ghost of the piece the place takes
            }
        }
        // metal tubes, one per bill line
        List<Metal> bill = TotemForgeBlockEntity.bill();
        for (int i = 0; i < bill.size() && i < 8; i++) {
            Metal m = bill.get(i);
            int tx = x + TUBES_X + i * TUBE_STEP, ty = y + TUBES_Y;
            float full = f == null || m.capacity() == 0 ? 0 : Math.min(1f, f.stored(m) / (float) m.capacity());
            g.fill(tx, ty, tx + 10, ty + TUBE_H, full >= 1 ? GOLD : DIM);
            g.fill(tx + 1, ty + 1, tx + 9, ty + TUBE_H - 1, SLOT_BG);
            int top = ty + TUBE_H - 1 - Math.round((TUBE_H - 2) * full);
            g.fill(tx + 2, top, tx + 8, ty + TUBE_H - 1, TUBE[i % TUBE.length]);
            g.pose().pushPose();
            g.pose().translate(tx - 1, ty + TUBE_H + 3, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.renderItem(icon(m), 0, 0);
            g.pose().popPose();
        }
        // the forge's slots and the lava gauge
        for (int i = 0; i < TotemForgeMenu.SLOT_X.length; i++) {
            int sx = TotemForgeMenu.SLOT_X[i];
            g.fill(x + sx - 1, y + TotemForgeMenu.SLOT_Y - 1, x + sx + 17, y + TotemForgeMenu.SLOT_Y + 17, SLOT_EDGE);
            g.fill(x + sx, y + TotemForgeMenu.SLOT_Y, x + sx + 16, y + TotemForgeMenu.SLOT_Y + 16, SLOT_BG);
            if (f != null && emptySlot(i)) { // a faint example of what goes in, as on the other machines
                g.renderFakeItem(f.slotIcon(i), x + sx, y + TotemForgeMenu.SLOT_Y);
                g.fill(x + sx, y + TotemForgeMenu.SLOT_Y, x + sx + 16, y + TotemForgeMenu.SLOT_Y + 16, 0xB00C0E12);
            }
        }
        int gx = x + 136, gy = y + TotemForgeMenu.SLOT_Y + 4, gw = 110;
        float lava = menu.lavaCapacity() <= 0 ? 0 : Math.min(1f, menu.lava() / (float) menu.lavaCapacity());
        g.fill(gx, gy, gx + gw, gy + 8, DIM);
        g.fill(gx + 1, gy + 1, gx + 1 + Math.round((gw - 2) * lava), gy + 7, LAVA);
        if (menu.energyPermille() >= 0) { // with an RF upgrade fitted: its buffer, a thin gold bar under the lava
            g.fill(gx, gy + 10, gx + gw, gy + 13, DIM);
            g.fill(gx + 1, gy + 11, gx + 1 + Math.round((gw - 2) * Math.min(1f, menu.energyPermille() / 1000f)), gy + 12, GOLD);
        }
        // keys
        Key main = mainKey(f);
        drawKey(g, x + MAIN_X, y + KEY_Y, MAIN_W, main.label, main.enabled, over(mouseX, mouseY, x + MAIN_X, y + KEY_Y, MAIN_W, KEY_H));
        if (main.coin) {
            g.pose().pushPose();
            g.pose().translate(x + MAIN_X + MAIN_W - 14, y + KEY_Y + 2, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.renderItem(new ItemStack(TotemForgeBlockEntity.pourCoin().coin()), 0, 0);
            g.pose().popPose();
        }
        boolean canTake = f != null && f.pourFraction() == 0 && f.mouldCount() > 0;
        drawKey(g, x + TAKE_X, y + KEY_Y, TAKE_W, Component.translatable("gui.bsp_core.totem_forge.take_piece"), canTake, canTake && over(mouseX, mouseY, x + TAKE_X, y + KEY_Y, TAKE_W, KEY_H));
        if (minecraft != null && minecraft.player != null && minecraft.player.hasPermissions(2)) {
            OperatorDemo.draw(g, font, x + 10, y + TotemForgeMenu.INV_Y + 2, f != null && f.isDemo(), mouseX, mouseY);
        }
    }

    private boolean emptySlot(int i) {
        return i < menu.slots.size() && !menu.slots.get(i).hasItem();
    }

    private void drawKey(GuiGraphics g, int kx, int ky, int w, Component label, boolean enabled, boolean hot) {
        g.fill(kx, ky, kx + w, ky + KEY_H, enabled ? (hot ? GOLD : 0xFFB38F1E) : DIM);
        g.fill(kx + 1, ky + 1, kx + w - 1, ky + KEY_H - 1, SLOT_BG);
        g.drawString(font, label, kx + 6, ky + 4, enabled ? GOLD : 0xFF565C6B, false);
    }

    /** The ingot a bill line shows: its item, or the first item of its tag. */
    private static ItemStack icon(Metal m) {
        if (m.item() != null) {
            return new ItemStack(m.item());
        }
        return m.tag() == null ? new ItemStack(Items.BARRIER)
                : BuiltInRegistries.ITEM.getTag(m.tag()).flatMap(set -> set.stream().findFirst()).map(h -> new ItemStack(h.value())).orElse(new ItemStack(Items.BARRIER));
    }

    private record Key(Component label, boolean enabled, boolean coin) {}

    /** POUR (free), the coin price, POURING, SETTING, then TAKE TOTEM for the pourer and STEAL for anyone else. */
    private Key mainKey(TotemForgeBlockEntity f) {
        if (f == null) {
            return new Key(Component.translatable("gui.bsp_core.totem_forge.pour"), false, false);
        }
        if (f.isSet()) {
            boolean mine = minecraft != null && minecraft.player != null && minecraft.player.getUUID().equals(f.pourer());
            return new Key(Component.translatable(mine ? "gui.bsp_core.totem_forge.take_totem" : "gui.bsp_core.totem_forge.steal"), true, false);
        }
        if (f.pourFraction() > 0) {
            return new Key(Component.translatable(f.pourFraction() < 1 ? "gui.bsp_core.totem_forge.pouring" : "gui.bsp_core.totem_forge.setting"), false, false);
        }
        if (!f.isFormed()) {
            return new Key(Component.translatable("gui.bsp_core.totem_forge.pour"), false, false);
        }
        PourCheck c = menu.check();
        if (c == PourCheck.PAID || c == PourCheck.COINS) {
            return new Key(Component.translatable("gui.bsp_core.totem_forge.pay", menu.coins()), c == PourCheck.PAID, true);
        }
        return new Key(Component.translatable("gui.bsp_core.totem_forge.pour"), c == PourCheck.FREE, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        TotemForgeBlockEntity f = menu.forge();
        g.drawString(font, Component.literal("> ").append(title), 10, 9, GOLD, false);
        Component tag = Component.translatable("gui.bsp_core.totem_forge.state." + state(f));
        g.drawString(font, tag, imageWidth - 10 - font.width(tag), 9, MUTED, false);
        g.drawString(font, Component.translatable("gui.bsp_core.totem_forge.mould", f == null ? 0 : f.mouldCount()), MOULD_X, 17, MUTED, false);
        g.drawString(font, Component.translatable("gui.bsp_core.totem_forge.metal"), TUBES_X, 17, MUTED, false);
        String[] slotKeys = {"ingots", "lava", "rf"};
        for (int i = 0; i < 3; i++) {
            g.drawString(font, Component.translatable("gui.bsp_core.totem_forge.slot." + slotKeys[i]), TotemForgeMenu.SLOT_X[i], TotemForgeMenu.SLOT_Y - 9, MUTED, false);
        }
        g.drawString(font, Component.translatable("gui.bsp_core.totem_forge.lava", String.format("%,d", menu.lava())), 136, TotemForgeMenu.SLOT_Y - 8, MUTED, false);
        int ly = MSG_Y - 10;
        for (FormattedCharSequence line : font.split(message(f), imageWidth - 20)) {
            if (ly > MSG_Y) {
                break; // two lines at most
            }
            g.drawString(font, line, 10, ly, menu.check() == PourCheck.COINS || menu.check() == PourCheck.ALREADY_POURING ? BAD & 0xFFFFFF : FG, false);
            ly += 10;
        }
    }

    private String state(TotemForgeBlockEntity f) {
        return f == null ? "unformed" : f.screenState();
    }

    /** What the forge is doing or waiting for, for the viewer. */
    private Component message(TotemForgeBlockEntity f) {
        if (f == null) {
            return Component.translatable("need.bsp_core.structure");
        }
        if (f.isSet()) {
            boolean mine = minecraft != null && minecraft.player != null && minecraft.player.getUUID().equals(f.pourer());
            return mine ? Component.translatable("gui.bsp_core.totem_forge.msg.set_mine")
                    : Component.translatable("gui.bsp_core.totem_forge.msg.set_other", f.pourerName(), com.mrgregles.bsp_core.BSPConfig.TFORGE_STEAL_SECONDS.get());
        }
        if (f.pourFraction() > 0 && f.pourFraction() < 1) {
            return Component.translatable("gui.bsp_core.totem_forge.msg.pouring", Math.round(f.pourFraction() * 100), f.pourerName());
        }
        if (f.pourFraction() >= 1) {
            long left = menu.minutesLeft();
            long days = Math.round(f.setMillis() / 86_400_000.0);
            return Component.translatable("gui.bsp_core.totem_forge.msg.setting", left / 1440, (left / 60) % 24, left % 60, days, f.pourerName());
        }
        if (!f.isFormed()) {
            return Component.translatable("need.bsp_core.structure");
        }
        ItemStack next = f.nextMouldPiece();
        if (!next.isEmpty()) {
            return Component.translatable("message.bsp_core.totem_forge.pour.mould", next.getHoverName());
        }
        if (!f.metalComplete()) {
            List<Component> short_ = new ArrayList<>();
            for (Metal m : TotemForgeBlockEntity.bill()) {
                if (f.stored(m) < m.capacity() && short_.size() < 3) {
                    short_.add(Component.translatable("gui.bsp_core.totem_forge.short", icon(m).getHoverName(), f.stored(m) / TotemForgeBlockEntity.MB_PER_INGOT, m.ingots()));
                }
            }
            Component list = short_.isEmpty() ? Component.empty() : short_.get(0);
            for (int i = 1; i < short_.size(); i++) {
                list = Component.empty().append(list).append(", ").append(short_.get(i));
            }
            return Component.translatable("gui.bsp_core.totem_forge.msg.waiting", list);
        }
        long free = Math.round(com.mrgregles.bsp_core.BSPConfig.TFORGE_FREE_SET_HOURS.get() / 24.0), paid = Math.round(com.mrgregles.bsp_core.BSPConfig.TFORGE_PAID_SET_HOURS.get() / 24.0);
        String coin = new ItemStack(TotemForgeBlockEntity.pourCoin().coin()).getHoverName().getString();
        return switch (menu.check()) {
            case FREE -> Component.translatable("gui.bsp_core.totem_forge.msg.free", free);
            case PAID -> Component.translatable("gui.bsp_core.totem_forge.msg.paid", menu.owned(), menu.coins(), coin, paid);
            case COINS -> Component.translatable("message.bsp_core.totem_forge.pour.coins", menu.owned(), menu.coins(), coin);
            case ALREADY_POURING -> Component.translatable("message.bsp_core.totem_forge.pour.already");
            case UNKNOWN -> Component.empty();
            default -> Component.translatable("gui.bsp_core.totem_forge.msg.ready");
        };
    }

    private void renderTooltips(GuiGraphics g, int mouseX, int mouseY) {
        TotemForgeBlockEntity f = menu.forge();
        int x = leftPos, y = topPos;
        if (f == null) {
            return;
        }
        for (int i = 0; i < TotemForgeBlockEntity.PLACES; i++) {
            int bx = x + MOULD_X + (i % 4) * (BOX + 4), by = y + MOULD_Y + (i / 4) * (BOX + 4);
            if (over(mouseX, mouseY, bx, by, BOX, BOX)) {
                ItemStack in = f.mouldAt(i);
                if (!in.isEmpty()) {
                    g.renderTooltip(font, in, mouseX, mouseY);
                } else {
                    g.renderTooltip(font, Component.translatable("gui.bsp_core.totem_forge.empty_place", TotemForgeBlockEntity.pieceForPlace(i).getHoverName()), mouseX, mouseY);
                }
                return;
            }
        }
        for (int i = 0; i < TotemForgeMenu.SLOT_X.length; i++) {
            if (emptySlot(i) && over(mouseX, mouseY, x + TotemForgeMenu.SLOT_X[i], y + TotemForgeMenu.SLOT_Y, 16, 16)) {
                g.renderTooltip(font, f.slotHint(i), mouseX, mouseY);
                return;
            }
        }
        if (menu.energyPermille() >= 0 && over(mouseX, mouseY, x + 136, y + TotemForgeMenu.SLOT_Y + 14, 110, 3)) {
            g.renderTooltip(font, Component.translatable("gui.bsp_core.totem_forge.rf", menu.energyPermille() / 10), mouseX, mouseY);
            return;
        }
        List<Metal> bill = TotemForgeBlockEntity.bill();
        for (int i = 0; i < bill.size() && i < 8; i++) {
            int tx = x + TUBES_X + i * TUBE_STEP;
            if (over(mouseX, mouseY, tx - 1, y + TUBES_Y, TUBE_STEP, TUBE_H + 16)) {
                Metal m = bill.get(i);
                g.renderTooltip(font, Component.translatable("gui.bsp_core.totem_forge.tube", icon(m).getHoverName(), f.stored(m) / TotemForgeBlockEntity.MB_PER_INGOT, m.ingots(),
                        String.format("%,d", f.stored(m)), String.format("%,d", m.capacity())), mouseX, mouseY);
                return;
            }
        }
        if (menu.check() == PourCheck.PAID || menu.check() == PourCheck.COINS) {
            if (over(mouseX, mouseY, x + MAIN_X, y + KEY_Y, MAIN_W, KEY_H) && f.pourFraction() == 0) {
                g.renderTooltip(font, Component.translatable("gui.bsp_core.totem_forge.pay_tip", menu.coins(), new ItemStack(TotemForgeBlockEntity.pourCoin().coin()).getHoverName(), menu.owned()), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = leftPos, y = topPos;
        TotemForgeBlockEntity f = menu.forge();
        if (minecraft != null && minecraft.gameMode != null) {
            if (over(mx, my, x + MAIN_X, y + KEY_Y, MAIN_W, KEY_H) && mainKey(f).enabled) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, TotemForgeMenu.BTN_MAIN);
                return true;
            }
            if (over(mx, my, x + TAKE_X, y + KEY_Y, TAKE_W, KEY_H) && f != null && f.pourFraction() == 0 && f.mouldCount() > 0) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, TotemForgeMenu.BTN_TAKE_PIECE);
                return true;
            }
            if (minecraft.player != null && minecraft.player.hasPermissions(2) && OperatorDemo.over(mx, my, x + 10, y + TotemForgeMenu.INV_Y + 2)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, TotemForgeMenu.BTN_DEMO);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }
}
