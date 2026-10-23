package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.compat.regionguard.RegionGuardClaims.Plot;
import com.mrgregles.bsp_core.land.LandOfficeMenu;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.LandOfficeActionPacket;
import com.mrgregles.bsp_core.network.LandOfficeActionPacket.Action;
import com.mrgregles.bsp_core.network.LandOfficeDataPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Land Office in the Cash Machine skin: a map of the land around the player on the screen,
 * with plots for sale in amber, the player's own claims in green, open areas dashed and the ground
 * they outlined with the wand in white. Click a plot to choose it; the side panel shows what it is
 * and what it costs; BUY, ENLARGE and GIVE UP ask once more before they act. Region Guard keeps the
 * plots and the money moves through it.
 */
public class LandOfficeScreen extends AbstractContainerScreen<LandOfficeMenu> {
    private static final int W = 360, H = 232;
    private static final int BEZEL = 0xFFCFC8B8, BEZEL_DARK = 0xFF8F877A, BEZEL_LIGHT = 0xFFECE6D8, SCREEN = 0xFF061008, SCREEN_EDGE = 0xFF0D3B22, PHOS = 0x58F29A, PHOS_DIM = 0x3FB97A,
            AMBER = 0xF2B544, RED = 0xFF6B5C, WHITE = 0xF4F1E8;
    private static final int SX = 10, SY = 10, SW = 206, SH = 176, PX = 222, PW = 66, KX = 292, KW = 58, KH = 18, KGAP = 3;
    private static final int MAP_X = SX + 3, MAP_Y = SY + 19, MAP = 154;
    private static final int[] ZOOMS = {48, 96, 160};

    private record Key(String label, boolean enabled, Runnable action) {}

    private final List<Key> top = new ArrayList<>(), bottom = new ArrayList<>();
    private int zoom = 1, lastSerial = -1;
    private int centreX, centreZ;
    @Nullable
    private String selectedId;
    @Nullable
    private Action confirming;

    public LandOfficeScreen(LandOfficeMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = W;
        this.imageHeight = H;
        var player = Minecraft.getInstance().player;
        centreX = player == null ? 0 : player.getBlockX();
        centreZ = player == null ? 0 : player.getBlockZ();
    }

    public static void receive(LandOfficeDataPacket m) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof LandOfficeMenu menu) {
            menu.plots.clear();
            menu.plots.addAll(m.plots());
            menu.balance = m.balance();
            menu.present = m.present();
            menu.allowed = m.allowed();
            menu.selection = m.selection();
            menu.message = m.message();
            menu.messageOk = m.ok();
            menu.coins = m.coins();
            menu.serial++;
        }
    }

    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("gui.bsp_core.land." + key, args);
    }

    private void send(Action a, String id) {
        BSPNetwork.CHANNEL.sendToServer(new LandOfficeActionPacket(a, id));
    }

    @Nullable
    private Plot selected() {
        for (Plot p : menu.plots) {
            if (p.id().equals(selectedId)) {
                return p;
            }
        }
        return null;
    }

    /** The open area the outlined ground lies in, if any. */
    @Nullable
    private Plot areaOfSelection() {
        if (menu.selection == null) {
            return null;
        }
        for (Plot p : menu.plots) {
            if (p.kind() == Plot.OPEN && p.contains(menu.selection)) {
                return p;
            }
        }
        return null;
    }

    private void keys() {
        top.clear();
        bottom.clear();
        Plot sel = selected();
        top.add(new Key("zoom_in", zoom > 0, () -> zoom--));
        top.add(new Key("zoom_out", zoom < ZOOMS.length - 1, () -> zoom++));
        top.add(new Key("centre", true, () -> {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                centreX = player.getBlockX();
                centreZ = player.getBlockZ();
            }
        }));
        boolean canBuyPlot = sel != null && sel.kind() == Plot.FOR_SALE;
        boolean canBuyOpen = sel != null && sel.kind() == Plot.OPEN && menu.selection != null && sel.contains(menu.selection);
        boolean canEnlarge = sel != null && sel.kind() == Plot.MINE && menu.selection != null && contains(menu.selection, sel);
        if (sel != null && sel.kind() == Plot.MINE) {
            bottom.add(new Key(confirming == Action.ENLARGE ? "confirm" : "enlarge", canEnlarge, () -> confirmThen(Action.ENLARGE, sel.id())));
        } else {
            bottom.add(new Key(confirming == Action.BUY_PLOT || confirming == Action.BUY_OPEN ? "confirm" : "buy", menu.present && menu.allowed && (canBuyPlot || canBuyOpen),
                    () -> confirmThen(canBuyPlot ? Action.BUY_PLOT : Action.BUY_OPEN, sel == null ? "" : sel.id())));
        }
        bottom.add(new Key(confirming == Action.ABANDON ? "confirm" : "give_up", sel != null && sel.kind() == Plot.MINE, () -> confirmThen(Action.ABANDON, sel == null ? "" : sel.id())));
        bottom.add(new Key("done", true, this::onClose));
    }

    /** True when the outlined ground contains the whole plot (for enlarging it). */
    private static boolean contains(int[] ground, Plot plot) {
        return ground[0] <= plot.minX() && ground[1] <= plot.minZ() && ground[2] >= plot.maxX() && ground[3] >= plot.maxZ();
    }

    /** The first press asks; the second press of the same key sends. */
    private void confirmThen(Action action, String id) {
        if (confirming == action) {
            confirming = null;
            send(action, id);
        } else {
            confirming = action;
        }
    }

    private int across() {
        return ZOOMS[zoom];
    }

    private int toScreenX(int wx) {
        return leftPos + MAP_X + Math.round((wx - (centreX - across() / 2f)) * MAP / (float) across());
    }

    private int toScreenZ(int wz) {
        return topPos + MAP_Y + Math.round((wz - (centreZ - across() / 2f)) * MAP / (float) across());
    }

    private int toWorldX(double sx) {
        return (int) Math.floor((sx - leftPos - MAP_X) * across() / (double) MAP + centreX - across() / 2f);
    }

    private int toWorldZ(double sy) {
        return (int) Math.floor((sy - topPos - MAP_Y) * across() / (double) MAP + centreZ - across() / 2f);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        keys();
        int x = leftPos, y = topPos, ky = y + SY;
        for (Key k : top) {
            if (over(mx, my, x + KX, ky, KW, KH)) {
                if (k.enabled) {
                    k.action.run();
                }
                return true;
            }
            ky += KH + KGAP;
        }
        ky = y + SY + SH - KH;
        for (int i = bottom.size() - 1; i >= 0; i--) {
            Key k = bottom.get(i);
            if (over(mx, my, x + KX, ky, KW, KH)) {
                if (k.enabled) {
                    k.action.run();
                }
                return true;
            }
            ky -= KH + KGAP;
        }
        confirming = null;
        if (over(mx, my, x + MAP_X, y + MAP_Y, MAP, MAP)) {
            int wx = toWorldX(mx), wz = toWorldZ(my);
            Plot hit = null;
            // the smallest plot under the click wins, so a plot inside an open area is chosen over the area
            for (Plot p : menu.plots) {
                if (p.containsXZ(wx, wz) && (hit == null || (long) p.width() * p.depth() < (long) hit.width() * hit.depth())) {
                    hit = p;
                }
            }
            selectedId = hit == null ? null : hit.id();
            if (hit != null) {
                if (hit.kind() == Plot.FOR_SALE) {
                    send(Action.QUOTE_PLOT, hit.id());
                } else if (hit.kind() == Plot.OPEN && menu.selection != null && hit.contains(menu.selection)) {
                    send(Action.QUOTE_OPEN, hit.id());
                }
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && over(mx, my, leftPos + MAP_X, topPos + MAP_Y, MAP, MAP) && (Math.abs(dx) + Math.abs(dy)) > 0) {
            centreX -= (int) Math.round(dx * across() / (double) MAP);
            centreZ -= (int) Math.round(dy * across() / (double) MAP);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        zoom = Math.max(0, Math.min(ZOOMS.length - 1, zoom - (int) Math.signum(delta)));
        return true;
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        if (menu.serial != lastSerial) {
            lastSerial = menu.serial;
            if (selectedId == null && menu.selection != null) {
                Plot area = areaOfSelection();
                selectedId = area == null ? null : area.id();
                centreX = (menu.selection[0] + menu.selection[2]) / 2;
                centreZ = (menu.selection[1] + menu.selection[3]) / 2;
            }
        }
        keys();
        renderBackground(g);
        super.render(g, mx, my, partial);
        if (over(mx, my, leftPos + MAP_X, topPos + MAP_Y, MAP, MAP)) {
            int wx = toWorldX(mx), wz = toWorldZ(my);
            for (Plot p : menu.plots) {
                if (p.containsXZ(wx, wz)) {
                    g.renderTooltip(font, Component.literal(p.name()), mx, my);
                    break;
                }
            }
        }
    }

    private void box(GuiGraphics g, int x0, int y0, int x1, int y1, int colour, boolean fillIt) {
        int mapL = leftPos + MAP_X, mapT = topPos + MAP_Y, mapR = mapL + MAP, mapB = mapT + MAP;
        x0 = Math.max(mapL, x0);
        y0 = Math.max(mapT, y0);
        x1 = Math.min(mapR, x1);
        y1 = Math.min(mapB, y1);
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        if (fillIt) {
            g.fill(x0, y0, x1, y1, (colour & 0xFFFFFF) | 0x40000000);
        }
        g.fill(x0, y0, x1, y0 + 1, colour);
        g.fill(x0, y1 - 1, x1, y1, colour);
        g.fill(x0, y0, x0 + 1, y1, colour);
        g.fill(x1 - 1, y0, x1, y1, colour);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x - 2, y - 2, x + W + 2, y + H + 2, BEZEL_DARK);
        g.fill(x, y, x + W, y + H, BEZEL);
        g.fill(x, y, x + W, y + 2, BEZEL_LIGHT);
        g.fill(x + SX - 2, y + SY - 2, x + SX + SW + 2, y + SY + SH + 2, SCREEN_EDGE);
        g.fill(x + SX, y + SY, x + SX + SW, y + SY + SH, SCREEN);
        g.fill(x + PX - 2, y + SY - 2, x + PX + PW + 2, y + SY + SH + 2, SCREEN_EDGE);
        g.fill(x + PX, y + SY, x + PX + PW, y + SY + SH, SCREEN);
        // the map, with the plots on top
        var level = Minecraft.getInstance().level;
        if (level != null) {
            LandMap.draw(g, level, centreX, centreZ, across(), x + MAP_X, y + MAP_Y, MAP);
        }
        for (int ly = y + MAP_Y; ly < y + MAP_Y + MAP; ly += 3) {
            g.fill(x + MAP_X, ly, x + MAP_X + MAP, ly + 1, 0x18000000);
        }
        Plot sel = selected();
        for (Plot p : menu.plots) {
            int colour = p.kind() == Plot.FOR_SALE ? 0xFFF2B544 : p.kind() == Plot.MINE ? 0xFF58F29A : p.kind() == Plot.OTHER ? 0xFFB58CFF : 0xFF3FB97A;
            boolean chosen = sel != null && sel.id().equals(p.id());
            box(g, toScreenX(p.minX()), toScreenZ(p.minZ()), toScreenX(p.maxX() + 1), toScreenZ(p.maxZ() + 1), chosen ? 0xFFFFFFFF : colour, chosen || (p.kind() != Plot.OPEN && p.kind() != Plot.OTHER));
        }
        if (menu.selection != null) {
            int[] s = menu.selection;
            box(g, toScreenX(s[0]), toScreenZ(s[1]), toScreenX(s[2] + 1), toScreenZ(s[3] + 1), 0xFFF4F1E8, true);
        }
        var player = Minecraft.getInstance().player;
        if (player != null) {
            int px = toScreenX(player.getBlockX()), pz = toScreenZ(player.getBlockZ());
            if (over(px, pz, x + MAP_X, y + MAP_Y, MAP, MAP)) {
                g.fill(px - 2, pz - 2, px + 2, pz + 2, 0xFF000000);
                g.fill(px - 1, pz - 1, px + 1, pz + 1, 0xFFFFFFFF);
            }
        }
        int ky = y + SY;
        for (Key k : top) {
            key(g, x + KX, ky, k, mx, my);
            ky += KH + KGAP;
        }
        ky = y + SY + SH - KH;
        for (int i = bottom.size() - 1; i >= 0; i--) {
            key(g, x + KX, ky, bottom.get(i), mx, my);
            ky -= KH + KGAP;
        }
        g.fill(x + SX - 2, y + SY + SH + 6, x + W - SX + 2, y + H - 6, SCREEN_EDGE);
        g.fill(x + SX, y + SY + SH + 8, x + W - SX, y + H - 8, SCREEN);
        String strip = tr(menu.present ? (menu.allowed ? "strip" : "strip_no_claims") : "strip_absent").getString() + " / " + Bank.serverId();
        String bal = menu.coins != null
                ? tr("coins", menu.coins.carried().isEmpty() ? tr("coins_none").getString() : menu.coins.carried()).getString() // coin prices: the coins that pay
                : tr("balance", Bank.format(menu.balance)).getString();
        CashMachine.fit(g, font, strip, x + SX + 6, y + SY + SH + 12, W - 2 * SX - 16 - font.width(bal), PHOS_DIM);
        g.drawString(font, bal, x + W - SX - 6 - font.width(bal), y + SY + SH + 12, PHOS_DIM, false);
    }

    private void key(GuiGraphics g, int x, int y, Key k, int mx, int my) {
        CashMachine.key(g, font, x, y, KW, KH, tr(k.label).getString(), k.enabled, k.enabled && over(mx, my, x, y, KW, KH), k.label.equals("confirm"));
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, "> " + tr("title").getString().toUpperCase(Locale.ROOT), SX + 6, SY + 6, PHOS, false);
        String scale = tr("scale", across()).getString();
        g.drawString(font, scale, SX + SW - 6 - font.width(scale), SY + 6, PHOS_DIM, false);
        g.fill(SX + 4, SY + 16, SX + SW - 4, SY + 17, SCREEN_EDGE);
        // under the map: the legend, or the message
        int bottom = SY + SH - 4;
        if (!menu.message.isEmpty()) {
            bottom = CashMachine.wrapUp(g, font, Component.literal(menu.message), SX + 6, bottom, SW - 12, menu.messageOk ? AMBER : RED) - 2;
        }
        if (confirming != null) {
            bottom = CashMachine.wrapUp(g, font, tr("confirm_hint"), SX + 6, bottom, SW - 12, AMBER) - 2;
        } else {
            CashMachine.wrapUp(g, font, tr("legend"), SX + 6, Math.min(bottom, SY + SH - 4), SW - 12, PHOS_DIM);
        }
        // the side panel: the chosen plot
        g.drawString(font, tr("side").getString().toUpperCase(Locale.ROOT), PX + 3, SY + 6, PHOS, false);
        g.fill(PX + 2, SY + 16, PX + PW - 2, SY + 17, SCREEN_EDGE);
        Plot sel = selected();
        int ty = SY + 22;
        if (sel == null) {
            CashMachine.wrap(g, font, tr(menu.plots.isEmpty() ? (menu.present ? "none_here" : "absent") : "side_hint"), PX + 3, ty, PW - 6, PHOS_DIM);
            return;
        }
        ty += CashMachine.wrap(g, font, Component.literal(sel.name()), PX + 3, ty, PW - 6, sel.kind() == Plot.MINE ? PHOS : AMBER) + 3;
        ty += CashMachine.wrap(g, font, sel.kind() == Plot.OTHER ? tr("kind_other", sel.owner()) : tr(sel.kind() == Plot.MINE ? "kind_mine" : sel.kind() == Plot.OPEN ? "kind_open" : "kind_sale"), PX + 3, ty, PW - 6, PHOS_DIM) + 3;
        ty += CashMachine.wrap(g, font, tr("size", sel.width(), sel.depth()), PX + 3, ty, PW - 6, PHOS) + 3;
        int at = menu.plots.indexOf(sel);
        var coins = menu.coins != null && at >= 0 && at < menu.coins.price().size() ? menu.coins : null; // Region Guard prices in Shatter Coins
        if (sel.kind() == Plot.FOR_SALE) {
            if (coins != null) {
                ty += CashMachine.wrap(g, font, tr("price_coins", coins.price().get(at)), PX + 3, ty, PW - 6, AMBER) + 3;
            } else {
                ty += CashMachine.wrap(g, font, tr("price", Bank.format(sel.price())), PX + 3, ty, PW - 6, AMBER) + 3;
                ty += CashMachine.wrap(g, font, tr("after", Bank.format(Math.max(0, menu.balance - sel.price()))), PX + 3, ty, PW - 6, PHOS_DIM) + 3;
            }
        } else if (sel.kind() == Plot.OPEN) {
            ty += CashMachine.wrap(g, font, tr("per_block", coins != null ? coins.perBlock().get(at) : Bank.format(sel.costPerBlock()), sel.minSize(), sel.maxSize()), PX + 3, ty, PW - 6, AMBER) + 3;
            if (menu.selection != null && sel.contains(menu.selection)) {
                int w = menu.selection[2] - menu.selection[0] + 1, d = menu.selection[3] - menu.selection[1] + 1;
                ty += CashMachine.wrap(g, font, coins != null ? tr("outline_coins", w, d, coins.outline().get(at))
                        : tr("outline", w, d, Bank.format((long) w * d * sel.costPerBlock())), PX + 3, ty, PW - 6, PHOS) + 3;
                // how close the outline lies to the nearest other plot or claim
                Plot near = null;
                int best = Integer.MAX_VALUE;
                for (Plot p : menu.plots) {
                    if (p.kind() == Plot.OPEN || p.id().equals(sel.id())) {
                        continue;
                    }
                    int gx = Math.max(0, Math.max(p.minX() - menu.selection[2], menu.selection[0] - p.maxX()));
                    int gz = Math.max(0, Math.max(p.minZ() - menu.selection[3], menu.selection[1] - p.maxZ()));
                    int gap = Math.max(gx, gz);
                    if (gap < best) {
                        best = gap;
                        near = p;
                    }
                }
                if (near != null) {
                    ty += CashMachine.wrap(g, font, best == 0 ? tr("touches", near.name()) : tr("nearest", best, near.name()), PX + 3, ty, PW - 6, best == 0 ? RED : PHOS_DIM) + 3;
                }
            } else {
                ty += CashMachine.wrap(g, font, tr("outline_none"), PX + 3, ty, PW - 6, PHOS_DIM) + 3;
            }
        } else if (menu.selection != null) {
            ty += CashMachine.wrap(g, font, tr(contains(menu.selection, sel) ? "enlarge_ok" : "enlarge_no"), PX + 3, ty, PW - 6, PHOS_DIM) + 3;
        }
        var player = Minecraft.getInstance().player;
        if (player != null) {
            int dx = Math.max(0, Math.max(sel.minX() - player.getBlockX(), player.getBlockX() - sel.maxX()));
            int dz = Math.max(0, Math.max(sel.minZ() - player.getBlockZ(), player.getBlockZ() - sel.maxZ()));
            CashMachine.wrap(g, font, tr("distance", Math.round(Math.sqrt((double) dx * dx + dz * dz))), PX + 3, ty, PW - 6, PHOS_DIM);
        }
    }
}
