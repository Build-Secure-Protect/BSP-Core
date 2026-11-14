package com.mrgregles.bsp_core.client;

import com.mojang.math.Axis;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.ChunkActionPacket;
import com.mrgregles.bsp_core.network.TankViewPacket;
import com.mrgregles.bsp_core.tank.TankPartBlockEntity;
import com.mrgregles.bsp_core.tank.TankPortBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Plasma Tank's screen: the tank in 3D with its plasma at the level it holds, each port labelled with its setting and what is
 * going in or out, and the first few cables leaving each port, but not the network beyond them. Drag to turn, right-drag to pan,
 * scroll to zoom. The server refreshes it once a second.
 */
public class TankScreen extends Screen implements ScreenFit.Fitted {
    private static final int BG = 0xF010151C, TQ = 0xFF19D3B0, SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, MUTED = 0x9AA3B5, TEXT = 0xE6EAF2, GOLD = 0xFFFFD23A, BAD = 0xFF6B5C, ION = 0xFF4FB8FF;
    private static final int W = 380, H = 214, VIEW_X = 10, VIEW_Y = 22, VIEW_W = 200, VIEW_H = 164, LIST_X = 218, COL_W = W - LIST_X - 10, RESET_W = 64;
    @Nullable
    private static TankViewPacket view;
    private final BlockPos pos;
    private float yaw = -32, pitch = 28, zoom = 1f, panX, panY;
    private int ticks;
    private final List<float[]> labels = new ArrayList<>();
    /** The CHAMBER tab is showing (a Mob Chamber only); the level picked for feeding; the clickable spots of the last frame. */
    private boolean chamberTab;
    private int pickLevel = 1;
    private record Hit(int x, int y, int w, int h, Runnable action) {
    }
    private final List<Hit> hits = new ArrayList<>();

    public TankScreen(BlockPos pos) {
        super(Component.translatable("gui.bsp_core.tank.title"));
        this.pos = pos;
    }

    public static void receive(TankViewPacket msg) {
        view = msg;
        if (Minecraft.getInstance().screen instanceof TankScreen open && msg.chamber() != null && !msg.chamber().feedItem().isEmpty()) {
            open.pickLevel = msg.chamber().feedLevel();
        }
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof TankScreen)) {
            TankScreen screen = new TankScreen(msg.pos());
            if (msg.chamber() != null && !msg.chamber().feedItem().isEmpty()) {
                screen.pickLevel = msg.chamber().feedLevel(); // the level being fed shows from the first frame
            }
            mc.setScreen(screen);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (minecraft == null || minecraft.level == null || !(minecraft.level.getBlockEntity(pos) instanceof TankPartBlockEntity)) {
            onClose();
        } else if (++ticks % 20 == 0) {
            BSPNetwork.CHANNEL.sendToServer(new ChunkActionPacket(pos, ChunkActionPacket.VIEW, 0, 0));
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (chamberTab) {
            return false;
        }
        if (button == 1 || hasShiftDown()) {
            panX += (float) dx;
            panY += (float) dy;
        } else {
            yaw += (float) dx * 0.8f;
            pitch = Mth.clamp(pitch + (float) dy * 0.5f, -10f, 80f);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (chamberTab) {
            return false;
        }
        zoom = Mth.clamp(zoom * (float) Math.pow(1.15, delta), 0.25f, 8f);
        return true;
    }

    private boolean overReset(double mx, double my) {
        int x = left() + VIEW_X, y = top() + VIEW_Y + VIEW_H + 4;
        return mx >= x && mx < x + RESET_W && my >= y && my < y + 12;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Hit hit : List.copyOf(hits)) {
            if (mx >= hit.x && mx < hit.x + hit.w && my >= hit.y && my < hit.y + hit.h) {
                hit.action.run();
                return true;
            }
        }
        if (!chamberTab && overReset(mx, my)) {
            zoom = 1f;
            panX = 0;
            panY = 0;
            yaw = -32;
            pitch = 28;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private void small(GuiGraphics g, Component text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    private static Component tr(String key, Object... args) {
        return Component.translatable("gui.bsp_core.tank." + key, args);
    }

    private void structure(GuiGraphics g, TankViewPacket v, float partialTick) {
        labels.clear();
        int x = left() + VIEW_X, y = top() + VIEW_Y;
        float minX = 0, maxX = v.w(), minY = 0, maxY = v.h(), minZ = 0, maxZ = v.d();
        for (TankViewPacket.Cable c : v.cables()) {
            BlockPos r = c.pos().subtract(v.pos());
            minX = Math.min(minX, r.getX());
            maxX = Math.max(maxX, r.getX() + 1);
            minY = Math.min(minY, r.getY());
            maxY = Math.max(maxY, r.getY() + 1);
            minZ = Math.min(minZ, r.getZ());
            maxZ = Math.max(maxZ, r.getZ() + 1);
        }
        float cx = (minX + maxX) / 2, cy = (minY + maxY) / 2, cz = (minZ + maxZ) / 2;
        float span = Math.max(1f, Math.max(maxY - minY, Mth.sqrt(Mth.square(maxX - minX) + Mth.square(maxZ - minZ))));
        float scale = Math.min(VIEW_W, VIEW_H) / (span * 1.2f) * zoom;
        var blocks = Minecraft.getInstance().getBlockRenderer();
        ScreenFit.scissor(g, x, y, x + VIEW_W, y + VIEW_H);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x + VIEW_W / 2f + panX, y + VIEW_H / 2f + panY, 300);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-cx, -cy, -cz);
        // the shell, from the real blocks, then the plasma, then the cables
        for (int bx = 0; bx < v.w(); bx++) {
            for (int by = 0; by < v.h(); by++) {
                for (int bz = 0; bz < v.d(); bz++) {
                    if (bx != 0 && by != 0 && bz != 0 && bx != v.w() - 1 && by != v.h() - 1 && bz != v.d() - 1) {
                        continue;
                    }
                    BlockState state = minecraft.level == null ? null : minecraft.level.getBlockState(v.pos().offset(bx, by, bz));
                    if (state == null || state.isAir()) {
                        continue;
                    }
                    pose.pushPose();
                    pose.translate(bx, by, bz);
                    blocks.renderSingleBlock(state, pose, g.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                    pose.popPose();
                }
            }
        }
        if (v.capacity() > 0 && v.stored() > 0) {
            float frac = Math.max(Mth.clamp(v.stored() / (float) v.capacity(), 0f, 1f), TankRenderer.minFrac(v.h()));
            pose.pushPose();
            pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
            PlasmaRender.tank(PlasmaRender.buffer(g.bufferSource()), pose, PlasmaRender.sprite(), 8.2f, 8.2f, 8.2f, (v.w() - 1) * 16 + 7.8f, (v.h() - 1) * 16 + 7.8f, (v.d() - 1) * 16 + 7.8f, frac);
            pose.popPose();
        }
        for (TankViewPacket.Cable c : v.cables()) {
            BlockPos r = c.pos().subtract(v.pos());
            BlockState state = minecraft.level == null ? null : minecraft.level.getBlockState(c.pos());
            if (state == null || state.isAir()) {
                continue;
            }
            pose.pushPose();
            pose.translate(r.getX(), r.getY(), r.getZ());
            blocks.renderSingleBlock(state, pose, g.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
            Vector4f p = new Vector4f(r.getX() + 0.5f, r.getY() + 1.1f, r.getZ() + 0.5f, 1f);
            p.mul(pose.last().pose());
            labels.add(new float[]{p.x(), p.y(), 1, c.flow(), 0, 0});
        }
        for (TankViewPacket.Port port : v.ports()) {
            BlockPos r = port.pos().subtract(v.pos());
            Vector4f p = new Vector4f(r.getX() + 0.5f, r.getY() + 1.1f, r.getZ() + 0.5f, 1f);
            p.mul(pose.last().pose());
            labels.add(new float[]{p.x(), p.y(), 0, port.in(), port.out(), port.mode()});
        }
        g.flush();
        pose.popPose();
        for (float[] l : labels) {
            String text;
            int colour;
            if ((int) l[2] == 1) {
                int flow = (int) l[3];
                text = flow > 0 ? flow + " mB/t" : "-";
                colour = flow > 0 ? ION & 0xFFFFFF : MUTED;
            } else {
                int in = (int) l[3], out = (int) l[4], mode = (int) l[5];
                String m = Component.translatable(TankPortBlockEntity.Mode.values()[Math.min(2, Math.max(0, mode))].key()).getString();
                text = m + (in > 0 ? "  in " + in : "") + (out > 0 ? "  out " + out : "");
                colour = in > 0 || out > 0 ? GOLD & 0xFFFFFF : MUTED;
            }
            float w = font.width(text) * 0.6f;
            g.pose().pushPose();
            g.pose().translate(l[0] - w / 2, l[1] - 4, 400);
            g.pose().scale(0.6f, 0.6f, 1f);
            g.fill(-1, -1, font.width(text) + 1, 9, 0xA0101018);
            g.drawString(font, text, 0, 0, colour, false);
            g.pose().popPose();
        }
        g.disableScissor();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = left(), y = top();
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, TQ);
        g.fill(x, y, x + W, y + H, BG);
        g.drawString(font, title.getString().toUpperCase(Locale.ROOT), x + 10, y + 8, TQ & 0xFFFFFF, false);
        TankViewPacket v = view;
        hits.clear();
        if (v != null && v.chamber() != null) {
            tabs(g, x, y);
            if (chamberTab) {
                chamber(g, v, v.chamber(), x, y, mouseX, mouseY);
                super.render(g, mouseX, mouseY, partialTick);
                return;
            }
        } else {
            chamberTab = false;
        }
        g.fill(x + VIEW_X - 1, y + VIEW_Y - 1, x + VIEW_X + VIEW_W + 1, y + VIEW_Y + VIEW_H + 1, DIM);
        g.fill(x + VIEW_X, y + VIEW_Y, x + VIEW_X + VIEW_W, y + VIEW_Y + VIEW_H, SLOT_BG);
        int cx = x + LIST_X, row = y + VIEW_Y;
        if (v == null || v.w() < 3) {
            small(g, tr("unformed"), cx, row, BAD);
            for (var line : CashMachine.lines(font, tr("unformed.help"), Math.round(COL_W / 0.75f))) {
                row += 9;
                g.pose().pushPose();
                g.pose().translate(cx, row, 0);
                g.pose().scale(0.75f, 0.75f, 1f);
                g.drawString(font, line, 0, 0, MUTED, false);
                g.pose().popPose();
            }
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        structure(g, v, partialTick);
        int in = 0, out = 0;
        for (TankViewPacket.Port p : v.ports()) {
            in += p.in();
            out += p.out();
        }
        float frac = v.capacity() <= 0 ? 0 : Mth.clamp(v.stored() / (float) v.capacity(), 0f, 1f);
        g.drawString(font, String.format("%,d mB", v.stored()), cx, row, ION & 0xFFFFFF, false);
        small(g, tr("of", String.format("%,d", v.capacity()), Math.round(frac * 100)), cx, row + 11, MUTED);
        g.fill(cx, row + 22, cx + COL_W, row + 30, SLOT_BG);
        poolBands(g, v, cx, row + 22, Math.round(COL_W * frac));
        row += 38;
        small(g, tr("size", v.w(), v.h(), v.d(), v.w() * v.h() * v.d() - (v.w() - 2) * (v.h() - 2) * (v.d() - 2)), cx, row, TEXT);
        small(g, tr("ports", v.ports().size()), cx, row + 9, TEXT);
        small(g, tr("in", in), cx, row + 18, in > 0 ? ION & 0xFFFFFF : MUTED);
        small(g, tr("out", out), cx, row + 27, out > 0 ? GOLD & 0xFFFFFF : MUTED);
        row += 40;
        // the totem powers the stored plasma carries: while a totem feeds them, just the names; once it is gone, how much plasma still carries each
        if (v.powers().isEmpty()) {
            small(g, tr("power_none"), cx, row, MUTED);
            row += 9;
        } else {
            small(g, tr("powers"), cx, row, TEXT);
            row += 9;
            int shown = 0;
            for (TankViewPacket.Power pw : v.powers()) {
                if (shown++ >= 4) {
                    break;
                }
                var buffs = com.mrgregles.bsp_core.totem.TotemUpgrades.Buff.values();
                var buff = buffs[Math.floorMod(pw.ordinal(), buffs.length)];
                Component name = Component.translatable(buff.translationKey());
                String level = com.mrgregles.bsp_core.totem.TotemUpgrades.levelRoman(pw.level());
                small(g, pw.live() ? tr("power_live", name, level) : tr("power_left", name, level, String.format("%,d", pw.mB())), cx, row, pw.live() ? ION & 0xFFFFFF : GOLD & 0xFFFFFF);
                row += 9;
            }
        }
        row += 4;
        for (var line : CashMachine.lines(font, tr("help"), Math.round(COL_W / 0.75f))) {
            g.pose().pushPose();
            g.pose().translate(cx, row, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, line, 0, 0, MUTED, false);
            g.pose().popPose();
            row += 8;
        }
        int bx = x + VIEW_X, by = y + VIEW_Y + VIEW_H + 4;
        boolean hover = overReset(mouseX, mouseY);
        g.fill(bx, by, bx + RESET_W, by + 12, SLOT_BG);
        g.fill(bx, by, bx + RESET_W, by + 1, hover ? TQ : DIM);
        g.fill(bx, by + 11, bx + RESET_W, by + 12, hover ? TQ : DIM);
        g.fill(bx, by, bx + 1, by + 12, hover ? TQ : DIM);
        g.fill(bx + RESET_W - 1, by, bx + RESET_W, by + 12, hover ? TQ : DIM);
        String reset = Component.translatable("gui.bsp_core.interface.reset").getString();
        small(g, Component.literal(reset), bx + RESET_W / 2 - Math.round(font.width(reset) * 0.375f), by + 3, hover ? TQ & 0xFFFFFF : TEXT);
        small(g, Component.translatable("gui.bsp_core.interface.zoom", Math.round(zoom * 100)), bx + RESET_W + 6, by + 3, MUTED);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public int panelWidth() {
        return W;
    }

    @Override
    public int panelHeight() {
        return H;
    }

    // ------------------------------------------------------------------ the CHAMBER tab (layout A of tools/preview/mob_chamber_concepts.html)

    private static final int PANEL = 0xFF171A21;

    private void box(GuiGraphics g, int x, int y, int w, int h, int fill, int edge) {
        g.fill(x, y, x + w, y + h, edge);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    /** Small text cut to {@code maxW} panel pixels; right-aligned at {@code x} when {@code right}. */
    private void text(GuiGraphics g, String str, int x, int y, int colour, int maxW, boolean right) {
        String cut = maxW > 0 ? font.plainSubstrByWidth(str, Math.round(maxW / 0.75f)) : str;
        int w = Math.round(font.width(cut) * 0.75f);
        small(g, Component.literal(cut), right ? x - w : x, y, colour);
    }

    private void button(GuiGraphics g, int x, int y, int w, String label, boolean on, Runnable action) {
        box(g, x, y, w, 12, on ? 0xFF14202A : PANEL, on ? TQ : DIM);
        int tw = Math.round(font.width(label) * 0.75f);
        small(g, Component.literal(label), x + (w - tw) / 2, y + 3, on ? TQ & 0xFFFFFF : MUTED);
        hits.add(new Hit(x, y, w, 12, action));
    }

    /** TANK and CHAMBER, at the top right. */
    private void tabs(GuiGraphics g, int x, int y) {
        String[] names = {tr("tab.tank").getString(), tr("tab.chamber").getString()};
        int at = x + W - 10;
        for (int i = names.length - 1; i >= 0; i--) {
            int w = Math.round(font.width(names[i]) * 0.75f) + 12;
            at -= w;
            boolean on = (i == 1) == chamberTab;
            final boolean chamber = i == 1;
            box(g, at, y + 4, w, 13, on ? 0xFF14202A : PANEL, on ? TQ : DIM);
            small(g, Component.literal(names[i]), at + 6, y + 7, on ? TQ & 0xFFFFFF : MUTED);
            hits.add(new Hit(at, y + 4, w, 13, () -> chamberTab = chamber));
            at -= 4;
        }
    }

    private void send(int action, int value, String item) {
        BSPNetwork.CHANNEL.sendToServer(new com.mrgregles.bsp_core.network.ChamberActionPacket(pos, action, value, item));
    }

    private void chamber(GuiGraphics g, TankViewPacket v, TankViewPacket.Chamber c, int x, int y, int mouseX, int mouseY) {
        var buffs = com.mrgregles.bsp_core.totem.TotemUpgrades.Buff.values();
        float frac = v.capacity() <= 0 ? 0 : Mth.clamp(v.stored() / (float) v.capacity(), 0f, 1f), mark = c.markPermille() / 1000f;
        // the fill bar with the "mobs spawn" mark
        box(g, x + 10, y + 24, 360, 10, SLOT_BG, DIM);
        poolBands(g, v, x + 11, y + 25, Math.round(358 * frac));
        int mx = x + 11 + Math.round(358 * mark);
        g.fill(mx, y + 21, mx + 1, y + 37, GOLD);
        g.fill(mx - 2, y + 21, mx + 3, y + 23, GOLD);
        // the label beside the notch: on its right, or on its left when the mark stands far to the right (it would run into the figures)
        text(g, tr("chamber.mark").getString(), mark > 0.6f ? mx - 4 : mx + 4, y + 37, GOLD & 0xFFFFFF, 0, mark > 0.6f);
        text(g, Math.round(frac * 100) + "% \u00b7 " + String.format("%,d", v.stored()) + " mB", x + 370, y + 37, ION & 0xFFFFFF, 0, true);
        // left: the switch, the status, what can spawn here
        box(g, x + 10, y + 50, 176, 156, PANEL, DIM);
        String biome = c.biome().isEmpty() ? "" : Component.translatable("biome." + c.biome().replace(':', '.')).getString().toUpperCase(Locale.ROOT);
        text(g, tr("chamber.spawns", biome).getString(), x + 16, y + 55, TQ & 0xFFFFFF, 164, false);
        String[] modes = {"all", "hostile", "passive"};
        for (int i = 0; i < 3; i++) {
            final int mode = i;
            button(g, x + 16 + i * 55, y + 67, 52, tr("chamber.mode." + modes[i]).getString(), c.mode() == i, () -> send(com.mrgregles.bsp_core.network.ChamberActionPacket.MODE, mode, ""));
        }
        Component status;
        int statusColour;
        if (frac < mark) {
            status = tr("chamber.status.low", Math.round(mark * 100));
            statusColour = GOLD & 0xFFFFFF;
        } else if (c.spawns().isEmpty()) {
            status = tr("chamber.status.nothing");
            statusColour = BAD;
        } else if (c.serverFull()) {
            status = tr("chamber.status.server_full");
            statusColour = BAD;
        } else {
            status = c.alive() >= c.maxAlive() ? tr("chamber.status.full", c.alive(), c.maxAlive()) : tr("chamber.status.next", c.nextSeconds(), c.alive(), c.maxAlive());
            statusColour = ION & 0xFFFFFF;
        }
        text(g, status.getString(), x + 16, y + 84, statusColour, 164, false);
        int total = Math.max(c.spawnTotal(), c.spawns().size());
        int shown = Math.min(total > 8 ? 7 : 8, c.spawns().size()); // with more to come, the last row says so
        for (int i = 0; i < shown; i++) {
            TankViewPacket.Spawn sp = c.spawns().get(i);
            int ry = y + 97 + i * 13;
            var type = net.minecraft.world.entity.EntityType.byString(sp.entity()).orElse(null);
            if (type != null) {
                var egg = net.minecraftforge.common.ForgeSpawnEggItem.fromEntityType(type);
                if (egg != null) {
                    g.pose().pushPose();
                    g.pose().translate(x + 16, ry - 1, 0);
                    g.pose().scale(0.5f, 0.5f, 1f);
                    g.renderItem(new net.minecraft.world.item.ItemStack(egg), 0, 0);
                    g.pose().popPose();
                }
            }
            String name = type == null ? sp.entity() : type.getDescription().getString();
            text(g, name, x + 28, ry + 1, TEXT, 68, false);
            float pc = sp.permille() / 10f;
            text(g, (pc < 10 ? String.format(Locale.ROOT, "%.1f", pc) : String.valueOf(Math.round(pc))) + "%", x + 120, ry + 1, MUTED, 0, true);
            boolean power = sp.power() >= 0 && sp.power() < buffs.length;
            text(g, power ? Component.translatable(buffs[sp.power()].translationKey()).getString() : tr("chamber.loot_only").getString(), x + 124, ry + 1,
                    power ? PoolColors.mob(buffs[sp.power()]) & 0xFFFFFF : MUTED, 58, false);
        }
        if (total > shown) {
            text(g, tr("chamber.more", total - shown).getString(), x + 16, y + 97 + shown * 13, MUTED, 164, false);
        }
        // right: the loot store, and the power picker under it
        box(g, x + 194, y + 50, 176, 156, PANEL, DIM);
        text(g, tr("chamber.loot").getString(), x + 200, y + 55, TQ & 0xFFFFFF, 0, false);
        text(g, tr("chamber.loot_hint").getString(), x + 364, y + 55, MUTED, 110, true);
        var feedId = net.minecraft.resources.ResourceLocation.tryParse(c.feedItem());
        var feedItem = feedId == null || c.feedItem().isEmpty() ? null : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(feedId);
        net.minecraft.world.item.ItemStack hover = net.minecraft.world.item.ItemStack.EMPTY;
        int have = 0;
        for (int i = 0; i < 27 && i < c.loot().size(); i++) {
            int sx = x + 201 + (i % 9) * 18, sy = y + 66 + (i / 9) * 18;
            var stack = c.loot().get(i);
            boolean picked = feedItem != null && !stack.isEmpty() && stack.is(feedItem);
            box(g, sx, sy, 18, 18, SLOT_BG, picked ? TQ : DIM);
            if (stack.isEmpty()) {
                continue;
            }
            have += picked ? stack.getCount() : 0;
            g.renderItem(stack, sx + 1, sy + 1);
            g.renderItemDecorations(font, stack, sx + 1, sy + 1);
            if (mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18) {
                hover = stack;
            }
            if (com.mrgregles.bsp_core.chamber.MobPowers.of(stack) != null) {
                String id = String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()));
                hits.add(new Hit(sx, sy, 18, 18, () -> {
                    if (picked) {
                        send(com.mrgregles.bsp_core.network.ChamberActionPacket.FEED_OFF, 0, "");
                    } else {
                        send(com.mrgregles.bsp_core.network.ChamberActionPacket.FEED, pickLevel, id);
                    }
                }));
            }
        }
        g.fill(x + 200, y + 124, x + 364, y + 125, DIM);
        text(g, tr("chamber.feed").getString(), x + 200, y + 132, MUTED, 0, false);
        var entry = feedItem == null ? null : com.mrgregles.bsp_core.chamber.MobPowers.of(feedItem);
        for (int l = 1; l <= 3; l++) {
            final int lvl = l;
            button(g, x + 303 + (l - 1) * 21, y + 129, 18, com.mrgregles.bsp_core.totem.TotemUpgrades.levelRoman(l), pickLevel == l, () -> {
                pickLevel = lvl;
                if (!c.feedItem().isEmpty()) {
                    send(com.mrgregles.bsp_core.network.ChamberActionPacket.FEED, lvl, c.feedItem());
                }
            });
        }
        if (feedItem == null || entry == null) {
            int ly = y + 146;
            for (var line : CashMachine.lines(font, tr("chamber.feed_pick"), Math.round(164 / 0.75f))) {
                g.pose().pushPose();
                g.pose().translate(x + 200, ly, 0);
                g.pose().scale(0.75f, 0.75f, 1f);
                g.drawString(font, line, 0, 0, MUTED, false);
                g.pose().popPose();
                ly += 9;
            }
        } else {
            Component power = Component.translatable(entry.power().translationKey());
            int colour = PoolColors.mob(entry.power()) & 0xFFFFFF;
            text(g, power.getString(), x + 230, y + 132, colour, 70, false);
            text(g, tr("chamber.feed_have", have, feedItem.getDescription().getString()).getString(), x + 200, y + 146, TEXT, 164, false);
            long mb = c.feedMb().length >= c.feedLevel() ? c.feedMb()[Math.max(0, c.feedLevel() - 1)] : 0;
            text(g, tr("chamber.feed_mb", String.format("%,d", mb), power, com.mrgregles.bsp_core.totem.TotemUpgrades.levelRoman(c.feedLevel())).getString(), x + 200, y + 157, colour, 164, false);
            double minutes = c.minuteMb() <= 0 ? 0 : mb / (double) c.minuteMb();
            text(g, tr("chamber.feed_minutes", minutes < 1 ? String.format(Locale.ROOT, "%.2f", minutes) : String.format(Locale.ROOT, "%.1f", minutes)).getString(), x + 200, y + 168, MUTED, 164, false);
        }
        if (!c.feedItem().isEmpty()) { // feeding can be stopped even when that drop has run out of the store
            button(g, x + 322, y + 178, 42, tr("chamber.stop").getString(), false, () -> send(com.mrgregles.bsp_core.network.ChamberActionPacket.FEED_OFF, 0, ""));
        }
        text(g, tr("chamber.fits", c.maxHeight()).getString(), x + 200, y + 192, MUTED, 164, false);
        if (!hover.isEmpty()) {
            g.renderTooltip(font, hover, mouseX, mouseY);
        }
    }

    /**
     * The filled part of the fill bar, {@code width} pixels from {@code x}, split into bands by pool: plain, the totem pool, then each mob
     * pool, in the colours of {@link PoolColors}. A pool too small for a pixel still gets one while there is room.
     */
    private static void poolBands(GuiGraphics g, TankViewPacket v, int x, int y, int width) {
        if (width <= 0 || v.stored() <= 0) {
            return;
        }
        java.util.List<long[]> bands = new java.util.ArrayList<>(); // {mB, colour}
        bands.add(new long[]{v.plain(), PoolColors.PLAIN});
        bands.add(new long[]{v.totem(), PoolColors.TOTEM});
        var buffs = com.mrgregles.bsp_core.totem.TotemUpgrades.Buff.values();
        for (TankViewPacket.Power pw : v.powers()) {
            if (pw.ordinal() >= 0 && pw.ordinal() < buffs.length && !buffs[pw.ordinal()].onTotem()) {
                bands.add(new long[]{pw.mB(), PoolColors.mob(buffs[pw.ordinal()])});
            }
        }
        int at = x, end = x + width;
        long seen = 0;
        for (int i = 0; i < bands.size() && at < end; i++) {
            long[] b = bands.get(i);
            if (b[0] <= 0) {
                continue;
            }
            seen += b[0];
            int to = i == bands.size() - 1 ? end : x + (int) Math.round(width * Math.min(1.0, seen / (double) v.stored()));
            to = Math.min(end, Math.max(to, at + 1));
            g.fill(at, y, to, y + 8, (int) b[1]);
            at = to;
        }
        if (at < end) {
            g.fill(at, y, end, y + 8, PoolColors.PLAIN); // rounding: the last pixels go to plain
        }
    }
}
