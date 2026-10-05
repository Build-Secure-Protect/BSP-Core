package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.ZoneConfigPacket;
import com.mrgregles.bsp_core.zone.AntiTotemBlockEntity;
import com.mrgregles.bsp_core.zone.ZoneLedger;
import com.mrgregles.bsp_core.zone.ZoneRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Settings of an Anti Totem block, "Rule List" layout. The Rules tab lists the two totem rules
 * and every BSP block by name, each allowed or blocked on its own, with a switch per group. The
 * Zone tab has the six distances (0 to 256), a map of the zone from above and the outline colour.
 * Every change is sent to the server at once.
 */
public class ZoneScreen extends Screen {
    private static final int W = 300, H = 222, BG = 0xF010151C, SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, BAD = 0xFFFF4B3C, VIO = 0xFFB58CFF, TQ = 0xFF19D3B0,
            MUTED = 0x9AA3B5, TEXT = 0xE6EAF2, HOVER = 0xFF2A1A1A;
    private static final int LIST_Y = 26, ROW_H = 13, ROWS = 14, BTN_W = 64, MAP = 110, STEP_Y = 34, STEP_H = 16;
    private static final String[] DIRS = {"north", "south", "east", "west", "up", "down"};
    private static final int[] STEPS = {-10, -1, 1, 10};

    /** A line of the rule list: a group header (key null) or one rule. */
    private record Line(String group, String key, Component name) {
    }

    private final BlockPos pos;
    private final int[] reach = new int[6];
    private final Set<String> blocked = new HashSet<>();
    private final List<Line> lines = new ArrayList<>();
    private int colour, tab, scroll;

    private ZoneScreen(BlockPos pos, AntiTotemBlockEntity zone) {
        super(Component.translatable("block.bsp_core.anti_totem"));
        this.pos = pos;
        System.arraycopy(zone.reach(), 0, reach, 0, 6);
        this.colour = zone.colourIndex();
        this.blocked.addAll(zone.blocked());
        group("totems", ZoneRules.totems());
        group("machines", ZoneRules.machines());
        group("others", ZoneRules.others());
    }

    private void group(String name, List<String> keys) {
        lines.add(new Line(name, null, Component.translatable("gui.bsp_core.zone.group." + name)));
        for (String key : keys) {
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(BSPCore.MODID, key));
            boolean rule = key.equals(ZoneLedger.TOTEM_PLACE) || key.equals(ZoneLedger.TOTEM_DROP);
            lines.add(new Line(name, key, rule || block == null ? Component.translatable("gui.bsp_core.zone.rule." + key) : block.getName()));
        }
    }

    public static void open(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof AntiTotemBlockEntity zone) {
            mc.setScreen(new ZoneScreen(pos, zone));
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
        return Component.translatable("gui.bsp_core.zone." + key, args);
    }

    private void send() {
        BSPNetwork.CHANNEL.sendToServer(new ZoneConfigPacket(pos, reach.clone(), colour, new ArrayList<>(blocked)));
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private boolean allBlocked(String group) {
        for (Line l : lines) {
            if (l.key != null && l.group.equals(group) && !blocked.contains(l.key)) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = left(), y = top();
        for (int i = 0; i < 2; i++) {
            if (over(mx, my, x + W - 10 - (2 - i) * 53, y + 6, 50, 13)) {
                tab = i;
                return true;
            }
        }
        if (tab == 0) {
            for (int i = 0; i < ROWS && i + scroll < lines.size(); i++) {
                Line l = lines.get(i + scroll);
                if (!over(mx, my, x + W - 16 - BTN_W, y + LIST_Y + i * ROW_H, BTN_W, ROW_H - 1)) {
                    continue;
                }
                if (l.key == null) {
                    boolean to = !allBlocked(l.group);
                    for (Line m : lines) {
                        if (m.key != null && m.group.equals(l.group)) {
                            if (to) {
                                blocked.add(m.key);
                            } else {
                                blocked.remove(m.key);
                            }
                        }
                    }
                } else if (!blocked.remove(l.key)) {
                    blocked.add(l.key);
                }
                send();
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }
        for (int d = 0; d < 6; d++) {
            for (int s = 0; s < STEPS.length; s++) {
                if (over(mx, my, x + stepX(s), y + STEP_Y + d * (STEP_H + 3), 22, STEP_H)) {
                    reach[d] = Mth.clamp(reach[d] + STEPS[s], 0, ZoneLedger.MAX);
                    send();
                    return true;
                }
            }
        }
        for (int c = 0; c < AntiTotemBlockEntity.COLOURS.length; c++) {
            if (over(mx, my, x + 10 + c * 16, y + 186, 14, 14)) {
                colour = c;
                send();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    /** Left edge of step button {@code s}: two on each side of the number. */
    private static int stepX(int s) {
        return 164 + (s < 2 ? s * 24 : 24 * 2 + 30 + (s - 2) * 24);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, lines.size() - ROWS));
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

    private void button(GuiGraphics g, int x, int y, int w, int h, Component label, int colour, int mouseX, int mouseY) {
        g.fill(x, y, x + w, y + h, colour);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, over(mouseX, mouseY, x, y, w, h) ? HOVER : SLOT_BG);
        small(g, label, x + w / 2 - Math.round(font.width(label) * 0.375f), y + (h - 6) / 2, colour & 0xFFFFFF);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = left(), y = top(), zc = 0xFF000000 | AntiTotemBlockEntity.COLOURS[colour];
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, BAD);
        g.fill(x, y, x + W, y + H, BG);
        g.drawString(font, title.getString().toUpperCase(java.util.Locale.ROOT), x + 10, y + 9, BAD & 0xFFFFFF, false);
        for (int i = 0; i < 2; i++) {
            int tx = x + W - 10 - (2 - i) * 53;
            g.fill(tx, y + 6, tx + 50, y + 19, tab == i ? BAD : DIM);
            g.fill(tx + 1, y + 7, tx + 49, y + 18, tab == i ? BG : SLOT_BG);
            Component label = tr(i == 0 ? "tab.rules" : "tab.zone");
            small(g, label, tx + 25 - Math.round(font.width(label) * 0.375f), y + 10, tab == i ? BAD & 0xFFFFFF : MUTED);
        }
        Component summary = tr("summary", blocked.size(), reach[ZoneLedger.E] + reach[ZoneLedger.W] + 1, reach[ZoneLedger.U] + reach[ZoneLedger.D] + 1,
                reach[ZoneLedger.N] + reach[ZoneLedger.S] + 1);
        small(g, summary, x + 10, y + H - 12, MUTED);
        if (tab == 0) {
            scroll = Mth.clamp(scroll, 0, Math.max(0, lines.size() - ROWS));
            for (int i = 0; i < ROWS && i + scroll < lines.size(); i++) {
                Line l = lines.get(i + scroll);
                int ry = y + LIST_Y + i * ROW_H, bx = x + W - 16 - BTN_W;
                if (l.key == null) {
                    g.drawString(font, l.name.getString().toUpperCase(java.util.Locale.ROOT), x + 10, ry + 2, BAD & 0xFFFFFF, false);
                    button(g, bx, ry, BTN_W, ROW_H - 1, tr(allBlocked(l.group) ? "allow_all" : "block_all"), 0xFF8A909C, mouseX, mouseY);
                } else {
                    boolean on = blocked.contains(l.key);
                    g.drawString(font, font.plainSubstrByWidth(l.name.getString(), W - BTN_W - 46), x + 18, ry + 2, on ? TEXT : MUTED, false);
                    button(g, bx, ry, BTN_W, ROW_H - 1, tr(on ? "blocked" : "allowed"), on ? BAD : 0xFF3A6B4A, mouseX, mouseY);
                }
            }
            if (lines.size() > ROWS) {
                int track = ROWS * ROW_H, knob = Math.max(8, track * ROWS / lines.size()), ky = (track - knob) * scroll / (lines.size() - ROWS);
                g.fill(x + W - 10, y + LIST_Y + ky, x + W - 8, y + LIST_Y + ky + knob, DIM);
            }
        } else {
            // the zone from above: north is up, this block in violet
            int mx0 = x + 10, my0 = y + STEP_Y, half = MAP / 2;
            g.fill(mx0 - 1, my0 - 1, mx0 + MAP + 1, my0 + MAP + 1, DIM);
            g.fill(mx0, my0, mx0 + MAP, my0 + MAP, SLOT_BG);
            float k = (half - 4) / (float) Math.max(8, Math.max(Math.max(reach[0], reach[1]), Math.max(reach[2], reach[3])));
            int zx0 = mx0 + half - Math.round(reach[ZoneLedger.W] * k), zx1 = mx0 + half + Math.round(reach[ZoneLedger.E] * k) + 2,
                    zy0 = my0 + half - Math.round(reach[ZoneLedger.N] * k), zy1 = my0 + half + Math.round(reach[ZoneLedger.S] * k) + 2;
            g.fill(zx0, zy0, zx1, zy1, zc);
            g.fill(zx0 + 1, zy0 + 1, zx1 - 1, zy1 - 1, (zc & 0xFFFFFF) | 0x40000000);
            g.fill(mx0 + half - 1, my0 + half - 1, mx0 + half + 3, my0 + half + 3, VIO);
            small(g, tr("map"), x + 10, y + STEP_Y + MAP + 4, MUTED);
            small(g, tr("reach"), x + 130, y + 24, MUTED);
            for (int d = 0; d < 6; d++) {
                int ry = y + STEP_Y + d * (STEP_H + 3);
                g.drawString(font, tr("dir." + DIRS[d]), x + 130, ry + 4, TEXT, false);
                for (int s = 0; s < STEPS.length; s++) {
                    button(g, x + stepX(s), ry, 22, STEP_H, Component.literal((STEPS[s] > 0 ? "+" : "") + STEPS[s]), TQ, mouseX, mouseY);
                }
                String v = Integer.toString(reach[d]);
                g.drawString(font, v, x + 164 + 48 + 15 - font.width(v) / 2, ry + 4, TEXT, false);
            }
            small(g, tr("colour"), x + 10, y + 176, MUTED);
            for (int c = 0; c < AntiTotemBlockEntity.COLOURS.length; c++) {
                int cx = x + 10 + c * 16;
                g.fill(cx, y + 186, cx + 14, y + 200, c == colour ? 0xFFFFFFFF : DIM);
                g.fill(cx + 1, y + 187, cx + 13, y + 199, 0xFF000000 | AntiTotemBlockEntity.COLOURS[c]);
            }
            small(g, tr("outline_hint"), x + 160, y + 190, MUTED);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }
}
