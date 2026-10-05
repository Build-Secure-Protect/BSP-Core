package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.network.AdminActionPacket;
import com.mrgregles.bsp_core.network.AdminDataPacket;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * The admin panel, "List And Detail" layout in the Holo HUD style with a violet accent. Players
 * are listed on the left by score; the right side shows the selected player's totems with their
 * tier and coordinates, their Coin Vault coins, and the actions. A second tab holds the season
 * controls. Moderators see everything but every action is greyed out. Destructive buttons ask
 * for a second click.
 */
public class AdminScreen extends Screen {
    private static final int W = 344, H = 210, BG = 0xF010151C, SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, VIO = 0xFFB58CFF, TQ = 0xFF19D3B0, BAD = 0xFFD63B2F,
            GOLD = 0xFFFFD23A, MUTED = 0x9AA3B5, TEXT = 0xE6EAF2, HOVER = 0xFF2A2140;
    private static final int LIST_X = 10, LIST_Y = 26, LIST_W = 112, ROW_H = 12, ROWS = 14, DX = 130, TOTEM_Y = 62, TOTEM_H = 11, TOTEM_ROWS = 6,
            BTN_Y = 182, BTN_W = 98, BTN_H = 18, TAB_W = 46, TAB_H = 13;
    private static final long CONFIRM_MS = 4000;

    private AdminDataPacket data;
    @Nullable
    private UUID selected;
    private int tab, scroll, confirm = -1;
    private long confirmUntil;
    /** Full reset stays locked until the admin types this word. */
    private static final String UNLOCK = "RESET";
    @Nullable
    private net.minecraft.client.gui.components.EditBox unlockBox;

    @Override
    protected void init() {
        unlockBox = new net.minecraft.client.gui.components.EditBox(font, left() + LIST_X + 4, top() + 165, 142, 10, tr("unlock_hint", UNLOCK));
        unlockBox.setMaxLength(8);
        unlockBox.setBordered(false);
        unlockBox.setHint(tr("unlock_hint", UNLOCK));
        unlockBox.visible = tab == 1 && data.canEdit();
        addRenderableWidget(unlockBox);
        hoursBox = new net.minecraft.client.gui.components.EditBox(font, left() + LIST_X + 164, top() + 141, 46, 10, tr("holder_hint"));
        hoursBox.setMaxLength(3);
        hoursBox.setBordered(false);
        hoursBox.setFilter(s -> s.matches("\\d{0,3}"));
        hoursBox.setHint(tr("holder_hint"));
        hoursBox.setValue(data.holderHours() > 0 ? Integer.toString(data.holderHours()) : "");
        hoursBox.visible = tab == 1 && data.canEdit();
        addRenderableWidget(hoursBox);
    }

    /** Hours between automatic holder rewards, typed by the admin; 0 or empty turns it off. */
    @Nullable
    private net.minecraft.client.gui.components.EditBox hoursBox;

    private void sendHours() {
        if (hoursBox == null) {
            return;
        }
        int hours = hoursBox.getValue().isEmpty() ? 0 : Integer.parseInt(hoursBox.getValue());
        send(new AdminActionPacket(AdminActionPacket.SET_HOLDER_HOURS, new UUID(0, 0), "", new net.minecraft.core.BlockPos(hours, 0, 0)));
        hoursBox.setFocused(false);
    }

    private boolean unlocked() {
        return unlockBox != null && UNLOCK.equals(unlockBox.getValue().trim());
    }

    private void setTab(int to) {
        tab = to;
        confirm = -1;
        if (unlockBox != null) {
            unlockBox.setValue("");
            unlockBox.setFocused(false);
            unlockBox.visible = to == 1 && data.canEdit();
        }
        if (hoursBox != null) {
            hoursBox.setFocused(false);
            hoursBox.visible = to == 1 && data.canEdit();
        }
    }

    private AdminScreen(AdminDataPacket data) {
        super(Component.translatable("gui.bsp_core.admin.title"));
        this.data = data;
    }

    /** Called when panel data arrives: opens the panel, or refreshes it if it is showing. */
    public static void receive(AdminDataPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AdminScreen screen) {
            screen.data = packet;
        } else if (packet.open()) {
            mc.setScreen(new AdminScreen(packet));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.bsp_core.admin." + key, args);
    }

    @Nullable
    private AdminDataPacket.Row row() {
        List<AdminDataPacket.Row> rows = data.rows();
        for (AdminDataPacket.Row r : rows) {
            if (r.id().equals(selected)) {
                return r;
            }
        }
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void send(AdminActionPacket packet) {
        BSPNetwork.CHANNEL.sendToServer(packet);
    }

    /** First click arms the button, a second click within a few seconds confirms. */
    private boolean confirmed(int id) {
        long now = System.currentTimeMillis();
        if (confirm == id && now < confirmUntil) {
            confirm = -1;
            return true;
        }
        confirm = id;
        confirmUntil = now + CONFIRM_MS;
        return false;
    }

    private boolean arming(int id) {
        return confirm == id && System.currentTimeMillis() < confirmUntil;
    }

    private static String where(AdminDataPacket.Totem t) {
        String dim = t.dimension().substring(t.dimension().indexOf(':') + 1);
        return t.pos().getX() + ", " + t.pos().getY() + ", " + t.pos().getZ() + "  " + dim;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = left(), y = top();
        for (int i = 0; i < 3; i++) {
            if (over(mx, my, x + W - 10 - (3 - i) * (TAB_W + 3) + 3, y + 6, TAB_W, TAB_H)) {
                setTab(i);
                return true;
            }
        }
        if (over(mx, my, x + W - 10 - 3 * (TAB_W + 3) - 50, y + 6, 46, TAB_H)) {
            send(new AdminActionPacket(AdminActionPacket.REFRESH));
            return true;
        }
        boolean edit = data.canEdit();
        if (tab == 2) {
            if (edit && over(mx, my, x + LIST_X, y + 162, 150, BTN_H)) {
                send(new AdminActionPacket(AdminActionPacket.SET_CHUNKS_ONLINE, new UUID(0, 0), "", new net.minecraft.core.BlockPos(data.chunksOnlineOnly() ? 0 : 1, 0, 0)));
                return true;
            }
            for (int i = 0; i < data.decoyRanges().length && edit; i++) {
                for (int k = 0; k < RANGE_STEPS.length; k++) {
                    if (over(mx, my, x + rangeStepX(k), y + 60 + i * 20, 24, 16)) {
                        send(new AdminActionPacket(AdminActionPacket.SET_DECOY_RANGE, new UUID(0, 0), "", new net.minecraft.core.BlockPos(i, data.decoyRanges()[i] + RANGE_STEPS[k], 0)));
                        return true;
                    }
                }
            }
            return super.mouseClicked(mx, my, button);
        }
        if (tab == 1) {
            if (edit && over(mx, my, x + LIST_X, y + 112, 150, BTN_H) && confirmed(AdminActionPacket.END_SEASON)) {
                send(new AdminActionPacket(AdminActionPacket.END_SEASON));
                return true;
            }
            if (edit && over(mx, my, x + LIST_X + 160, y + 112, 150, BTN_H) && confirmed(AdminActionPacket.REWARD_HOLDERS)) {
                send(new AdminActionPacket(AdminActionPacket.REWARD_HOLDERS));
                return true;
            }
            if (edit && over(mx, my, x + LIST_X + 218, y + 136, 36, BTN_H)) {
                sendHours();
                return true;
            }
            if (over(mx, my, x + LIST_X, y + 136, 150, BTN_H)) {
                send(new AdminActionPacket(AdminActionPacket.OPEN_REWARDS));
                return true;
            }
            if (edit && unlocked() && over(mx, my, x + LIST_X + 160, y + 160, 150, BTN_H) && confirmed(AdminActionPacket.FULL_RESET)) {
                send(new AdminActionPacket(AdminActionPacket.FULL_RESET));
                setTab(1);
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }
        List<AdminDataPacket.Row> rows = data.rows();
        for (int i = 0; i < ROWS && i + scroll < rows.size(); i++) {
            if (over(mx, my, x + LIST_X, y + LIST_Y + i * ROW_H, LIST_W, ROW_H)) {
                selected = rows.get(i + scroll).id();
                confirm = -1;
                return true;
            }
        }
        AdminDataPacket.Row r = row();
        if (r == null || !edit) {
            return super.mouseClicked(mx, my, button);
        }
        for (int i = 0; i < Math.min(TOTEM_ROWS, r.totems().size()); i++) {
            AdminDataPacket.Totem t = r.totems().get(i);
            if (over(mx, my, x + W - 34, y + TOTEM_Y + i * TOTEM_H - 1, 24, 10)) {
                send(new AdminActionPacket(AdminActionPacket.TP_POS, r.id(), t.dimension(), t.pos()));
                onClose();
                return true;
            }
        }
        if (r.online() && over(mx, my, x + DX, y + BTN_Y, BTN_W, BTN_H)) {
            send(new AdminActionPacket(AdminActionPacket.TP_PLAYER, r.id()));
            onClose();
            return true;
        }
        if (r.totemCount() > 0 && over(mx, my, x + DX + BTN_W + 8, y + BTN_Y, BTN_W, BTN_H) && confirmed(AdminActionPacket.RESET_TOTEMS)) {
            send(new AdminActionPacket(AdminActionPacket.RESET_TOTEMS, r.id()));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (hoursBox != null && hoursBox.isFocused() && (key == 257 || key == 335)) {
            sendHours();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, data.rows().size() - ROWS));
        return true;
    }

    // ------------------------------------------------------------------ drawing

    private void small(GuiGraphics g, Component text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, Component label, boolean enabled, int colour, int mouseX, int mouseY) {
        boolean hover = enabled && over(mouseX, mouseY, x, y, w, h);
        g.fill(x, y, x + w, y + h, enabled ? colour : DIM);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? HOVER : SLOT_BG);
        if (h < 14) {
            small(g, label, x + w / 2 - Math.round(font.width(label) * 0.375f), y + (h - 6) / 2, enabled ? colour & 0xFFFFFF : 0x6B7385);
        } else {
            g.drawString(font, label, x + w / 2 - font.width(label) / 2, y + (h - 8) / 2, enabled ? colour & 0xFFFFFF : 0x6B7385, false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = left(), y = top();
        boolean edit = data.canEdit();
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, VIO);
        g.fill(x, y, x + W, y + H, BG);
        g.drawString(font, title, x + 10, y + 9, VIO & 0xFFFFFF, false);
        if (!edit) {
            small(g, tr("read_only"), x + 14 + font.width(title), y + 11, GOLD & 0xFFFFFF);
        }
        button(g, x + W - 10 - 3 * (TAB_W + 3) - 50, y + 6, 46, TAB_H, tr("refresh"), true, TQ, mouseX, mouseY);
        for (int i = 0; i < 3; i++) {
            int tx = x + W - 10 - (3 - i) * (TAB_W + 3) + 3;
            g.fill(tx, y + 6, tx + TAB_W, y + 6 + TAB_H, tab == i ? VIO : DIM);
            g.fill(tx + 1, y + 7, tx + TAB_W - 1, y + 5 + TAB_H, tab == i ? BG : SLOT_BG);
            Component label = tr(i == 0 ? "tab.players" : i == 1 ? "tab.season" : "tab.settings");
            small(g, label, tx + TAB_W / 2 - Math.round(font.width(label) * 0.375f), y + 10, tab == i ? VIO & 0xFFFFFF : MUTED);
        }
        if (tab == 1) {
            season(g, x, y, edit, mouseX, mouseY);
        } else if (tab == 2) {
            settings(g, x, y, edit, mouseX, mouseY);
        } else {
            players(g, x, y, edit, mouseX, mouseY);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private static final int[] RANGE_STEPS = {-8, -1, 1, 8};

    private static int rangeStepX(int k) {
        return 150 + (k < 2 ? k * 26 : 52 + 34 + (k - 2) * 26);
    }

    /** Server settings an admin can change without editing the config file. */
    private void settings(GuiGraphics g, int x, int y, boolean edit, int mouseX, int mouseY) {
        g.drawString(font, tr("settings.decoy"), x + LIST_X, y + 30, VIO & 0xFFFFFF, false);
        small(g, tr("settings.decoy_help"), x + LIST_X, y + 44, MUTED);
        for (int i = 0; i < data.decoyRanges().length; i++) {
            int ry = y + 60 + i * 20;
            g.drawString(font, tr("settings.coils", i), x + LIST_X, ry + 4, TEXT, false);
            for (int k = 0; k < RANGE_STEPS.length; k++) {
                button(g, x + rangeStepX(k), ry, 24, 16, Component.literal((RANGE_STEPS[k] > 0 ? "+" : "") + RANGE_STEPS[k]), edit, TQ, mouseX, mouseY);
            }
            String v = Integer.toString(data.decoyRanges()[i]);
            g.drawString(font, v, x + 150 + 52 + 17 - font.width(v) / 2, ry + 4, GOLD & 0xFFFFFF, false);
        }
        small(g, tr("settings.saved"), x + LIST_X, y + 146, MUTED);
        button(g, x + LIST_X, y + 162, 150, BTN_H, tr(data.chunksOnlineOnly() ? "settings.chunks_online" : "settings.chunks_always"), edit, TQ, mouseX, mouseY);
        small(g, tr("settings.chunks_help"), x + LIST_X + 158, y + 162 + (BTN_H - 6) / 2, MUTED);
    }

    private void season(GuiGraphics g, int x, int y, boolean edit, int mouseX, int mouseY) {
        g.drawString(font, tr("season", data.season(), data.day()), x + LIST_X, y + 30, VIO & 0xFFFFFF, false);
        g.drawString(font, data.lastWinner().isEmpty() ? tr("no_winner") : tr("last_winner", data.lastWinner(), data.lastWinnerPoints()), x + LIST_X, y + 44, TEXT, false);
        for (int i = 0; i < 4; i++) {
            small(g, tr("season_help." + i), x + LIST_X, y + 62 + i * 9, MUTED);
        }
        button(g, x + LIST_X, y + 112, 150, BTN_H, tr(arming(AdminActionPacket.END_SEASON) ? "confirm" : "end_season"), edit, BAD, mouseX, mouseY);
        button(g, x + LIST_X + 160, y + 112, 150, BTN_H, tr(arming(AdminActionPacket.REWARD_HOLDERS) ? "confirm" : "reward_holders"), edit, GOLD, mouseX, mouseY);
        button(g, x + LIST_X, y + 136, 150, BTN_H, tr("prizes"), true, VIO, mouseX, mouseY);
        if (edit) {
            g.fill(x + LIST_X + 160, y + 136, x + LIST_X + 214, y + 136 + BTN_H, DIM);
            g.fill(x + LIST_X + 161, y + 137, x + LIST_X + 213, y + 135 + BTN_H, SLOT_BG);
        }
        button(g, x + LIST_X + 218, y + 136, 36, BTN_H, tr("holder_set"), edit, GOLD, mouseX, mouseY);
        small(g, data.holderHours() <= 0 ? tr("holder_off") : tr("holder_every", data.holderHours()), x + LIST_X + 258, y + 138, TEXT);
        if (data.holderHours() > 0) {
            small(g, tr("holder_next", data.holderMinutesLeft() / 60, data.holderMinutesLeft() % 60), x + LIST_X + 258, y + 147, MUTED);
        }
        if (edit) {
            g.fill(x + LIST_X, y + 160, x + LIST_X + 150, y + 160 + BTN_H, unlocked() ? BAD : DIM);
            g.fill(x + LIST_X + 1, y + 161, x + LIST_X + 149, y + 159 + BTN_H, SLOT_BG);
        }
        button(g, x + LIST_X + 160, y + 160, 150, BTN_H, tr(arming(AdminActionPacket.FULL_RESET) ? "confirm" : "full_reset"), edit && unlocked(), BAD, mouseX, mouseY);
        small(g, tr("season_note"), x + LIST_X, y + 186, MUTED);
        small(g, tr("full_note"), x + LIST_X, y + 195, MUTED);
    }

    private void players(GuiGraphics g, int x, int y, boolean edit, int mouseX, int mouseY) {
        List<AdminDataPacket.Row> rows = data.rows();
        AdminDataPacket.Row sel = row();
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - ROWS));
        for (int i = 0; i < ROWS && i + scroll < rows.size(); i++) {
            AdminDataPacket.Row r = rows.get(i + scroll);
            int ry = y + LIST_Y + i * ROW_H;
            boolean on = r == sel;
            g.fill(x + LIST_X, ry, x + LIST_X + LIST_W, ry + ROW_H - 1, on ? VIO : over(mouseX, mouseY, x + LIST_X, ry, LIST_W, ROW_H) ? DIM : SLOT_BG);
            g.fill(x + LIST_X + 1, ry + 1, x + LIST_X + LIST_W - 1, ry + ROW_H - 2, SLOT_BG);
            String pts = Integer.toString(r.points());
            g.drawString(font, font.plainSubstrByWidth((i + scroll + 1) + ". " + r.name(), LIST_W - 10 - font.width(pts)), x + LIST_X + 3, ry + 2, r.online() ? TEXT : MUTED, false);
            g.drawString(font, pts, x + LIST_X + LIST_W - 3 - font.width(pts), ry + 2, GOLD & 0xFFFFFF, false);
        }
        if (rows.size() > ROWS) {
            int track = ROWS * ROW_H, knob = Math.max(8, track * ROWS / rows.size()), ky = (track - knob) * scroll / (rows.size() - ROWS);
            g.fill(x + LIST_X + LIST_W + 2, y + LIST_Y + ky, x + LIST_X + LIST_W + 4, y + LIST_Y + ky + knob, DIM);
        }
        if (sel == null) {
            small(g, tr("nobody"), x + DX, y + 30, MUTED);
            return;
        }
        g.drawString(font, sel.name(), x + DX, y + 28, VIO & 0xFFFFFF, false);
        small(g, tr(sel.online() ? "online" : "offline"), x + DX + font.width(sel.name()) + 6, y + 30, sel.online() ? TQ & 0xFFFFFF : MUTED);
        small(g, tr("score", sel.points(), sel.totemCount()), x + DX, y + 41, TEXT);
        small(g, tr("totems"), x + DX, y + 52, MUTED);
        for (int i = 0; i < Math.min(TOTEM_ROWS, sel.totems().size()); i++) {
            AdminDataPacket.Totem t = sel.totems().get(i);
            int ty = y + TOTEM_Y + i * TOTEM_H;
            g.drawString(font, TotemUpgrades.roman(t.tier()), x + DX, ty, GOLD & 0xFFFFFF, false);
            small(g, t.carried() ? tr("carried", where(t)) : Component.literal(where(t)), x + DX + 22, ty + 1, TEXT);
            button(g, x + W - 34, ty - 1, 24, 10, tr("tp"), edit, TQ, mouseX, mouseY);
        }
        if (sel.totems().isEmpty()) {
            small(g, tr("no_totems"), x + DX, y + TOTEM_Y + 1, MUTED);
        } else if (sel.totemCount() > TOTEM_ROWS) {
            small(g, tr("more", sel.totemCount() - TOTEM_ROWS), x + DX, y + TOTEM_Y + TOTEM_ROWS * TOTEM_H, MUTED);
        }
        small(g, tr("vault", sel.vaultValue(), sel.vaultBlocks()), x + DX, y + 140, MUTED);
        for (CoinTier tier : CoinTier.values()) {
            int cx = x + DX + tier.ordinal() * 40;
            g.renderItem(new ItemStack(tier.coin()), cx, y + 150);
            int n = tier.ordinal() < sel.coins().length ? sel.coins()[tier.ordinal()] : 0;
            g.drawString(font, Integer.toString(n), cx + 18, y + 154, n > 0 ? TEXT : MUTED, false);
        }
        button(g, x + DX, y + BTN_Y, BTN_W, BTN_H, tr("tp_player"), edit && sel.online(), TQ, mouseX, mouseY);
        button(g, x + DX + BTN_W + 8, y + BTN_Y, BTN_W, BTN_H, tr(arming(AdminActionPacket.RESET_TOTEMS) ? "confirm" : "reset_totems"), edit && sel.totemCount() > 0, BAD, mouseX, mouseY);
    }
}
