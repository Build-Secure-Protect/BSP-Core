package com.mrgregles.bsp_core.client;

import com.mojang.math.Axis;
import com.mrgregles.bsp_core.network.BSPNetwork;
import com.mrgregles.bsp_core.network.ChunkActionPacket;
import com.mrgregles.bsp_core.network.InterfaceViewPacket;
import com.mrgregles.bsp_core.plasma.PlasmaInterfaceBlockEntity;
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
 * The Plasma Interface's screen: the group drawn in 3D as it stands, with the extractors it draws from, the
 * cables and repeaters leaving it and the projector bases and chargers they reach, each labelled with
 * the mB/t passing it, so a player can see where the flow splits and where it stops. Drag to turn.
 */
public class InterfaceScreen extends Screen {
    private static final int BG = 0xF010151C, TQ = 0xFF19D3B0, SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, MUTED = 0x9AA3B5, TEXT = 0xE6EAF2, GOLD = 0xFFFFD23A, BAD = 0xFF6B5C, ION = 0xFF4FB8FF;
    private static final int W = 380, H = 214, VIEW_X = 10, VIEW_Y = 22, VIEW_W = 200, VIEW_H = 164, LIST_X = 218, COL_W = W - LIST_X - 10, RESET_W = 64;
    @Nullable
    private static InterfaceViewPacket view;
    private final BlockPos pos;
    private float yaw = -32, pitch = 28, zoom = 1f, panX, panY;
    private int ticks;
    private final List<float[]> labels = new ArrayList<>();

    public InterfaceScreen(BlockPos pos) {
        super(Component.translatable("block.bsp_core.plasma_interface"));
        this.pos = pos;
    }

    public static void receive(InterfaceViewPacket msg) {
        view = msg;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof InterfaceScreen)) {
            mc.setScreen(new InterfaceScreen(msg.pos()));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (minecraft == null || minecraft.level == null || !(minecraft.level.getBlockEntity(pos) instanceof PlasmaInterfaceBlockEntity)) {
            onClose();
        } else if (++ticks % 20 == 0) {
            BSPNetwork.CHANNEL.sendToServer(new ChunkActionPacket(pos, ChunkActionPacket.VIEW, 0, 0));
        }
    }

    /** Left-drag turns, right-drag (or shift-drag) pans, the wheel zooms, the button under the view puts it back. */
    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
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
        zoom = Mth.clamp(zoom * (float) Math.pow(1.15, delta), 0.25f, 8f);
        return true;
    }

    private boolean overReset(double mx, double my) {
        int x = left() + VIEW_X, y = top() + VIEW_Y + VIEW_H + 4;
        return mx >= x && mx < x + RESET_W && my >= y && my < y + 12;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (overReset(mx, my)) {
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
        return Component.translatable("gui.bsp_core.interface." + key, args);
    }

    /**
     * Cables that touch and carry the same figure form one run; each run is labelled once, on the cable nearest its middle.
     * Runs with different figures that touch still get their own labels, side by side, which is where the figure changes.
     */
    private static java.util.Set<BlockPos> runSpeakers(InterfaceViewPacket v) {
        java.util.Map<BlockPos, Integer> cables = new java.util.HashMap<>();
        for (InterfaceViewPacket.Entry e : v.blocks()) {
            if (e.kind() == InterfaceViewPacket.CABLE) {
                cables.put(e.pos(), e.flow());
            }
        }
        java.util.Set<BlockPos> seen = new java.util.HashSet<>(), speakers = new java.util.HashSet<>();
        for (BlockPos start : cables.keySet()) {
            if (!seen.add(start)) {
                continue;
            }
            int flow = cables.get(start);
            List<BlockPos> run = new ArrayList<>();
            java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>(List.of(start));
            while (!queue.isEmpty()) {
                BlockPos p = queue.poll();
                run.add(p);
                for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                    BlockPos n = p.relative(d);
                    if (cables.containsKey(n) && cables.get(n) == flow && seen.add(n)) {
                        queue.add(n);
                    }
                }
            }
            double cx = 0, cy = 0, cz = 0;
            for (BlockPos p : run) {
                cx += p.getX();
                cy += p.getY();
                cz += p.getZ();
            }
            cx /= run.size();
            cy /= run.size();
            cz /= run.size();
            BlockPos best = run.get(0);
            double bestD = Double.MAX_VALUE;
            for (BlockPos p : run) {
                double d = Mth.square(p.getX() - cx) + Mth.square(p.getY() - cy) + Mth.square(p.getZ() - cz);
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
            speakers.add(best);
        }
        return speakers;
    }

    private void structure(GuiGraphics g, InterfaceViewPacket v, float partialTick) {
        labels.clear();
        int x = left() + VIEW_X, y = top() + VIEW_Y;
        float minX = 0, maxX = 1, minY = 0, maxY = 1, minZ = 0, maxZ = 1;
        for (InterfaceViewPacket.Entry e : v.blocks()) {
            BlockPos r = e.pos().subtract(v.pos());
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
        g.enableScissor(x, y, x + VIEW_W, y + VIEW_H);
        var pose = g.pose();
        pose.pushPose();
        pose.translate(x + VIEW_W / 2f + panX, y + VIEW_H / 2f + panY, 300);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.translate(-cx, -cy, -cz);
        float pulse = 0.9f + 0.1f * Mth.sin((ticks + partialTick) * 0.35f);
        java.util.Set<BlockPos> speakers = runSpeakers(v);
        for (InterfaceViewPacket.Entry e : v.blocks()) {
            BlockPos r = e.pos().subtract(v.pos());
            BlockState state = minecraft.level == null ? null : minecraft.level.getBlockState(e.pos());
            if (state == null || state.isAir()) {
                continue;
            }
            pose.pushPose();
            pose.translate(r.getX() + 0.5, r.getY() + 0.5, r.getZ() + 0.5);
            float size = e.kind() == InterfaceViewPacket.REFUSED ? pulse : 1f;
            pose.scale(size, size, size);
            pose.translate(-0.5, -0.5, -0.5);
            blocks.renderSingleBlock(state, pose, g.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            pose.popPose();
            if (e.kind() == InterfaceViewPacket.CABLE && !speakers.contains(e.pos())) {
                continue; // one label per run of cables carrying the same figure, on the cable nearest its middle
            }
            // where the block's top centre lands on screen, for its label
            Vector4f p = new Vector4f(r.getX() + 0.5f, r.getY() + 1.1f, r.getZ() + 0.5f, 1f);
            p.mul(pose.last().pose());
            labels.add(new float[]{p.x(), p.y(), e.kind(), e.flow(), e.repeaters()});
        }
        g.flush();
        pose.popPose();
        // labels drawn flat over the view: the flow through cables, extractors and receivers
        for (float[] l : labels) {
            int kind = (int) l[2], flow = (int) l[3];
            if (kind == InterfaceViewPacket.INTERFACE) {
                continue;
            }
            boolean shut = kind == InterfaceViewPacket.VALVE && (int) l[4] == 1;
            String text = kind == InterfaceViewPacket.REFUSED ? tr("refused").getString() : shut ? tr("shut").getString() : flow > 0 ? flow + " mB/t" : "-";
            int colour = kind == InterfaceViewPacket.REFUSED || shut ? BAD : flow > 0 ? (kind == InterfaceViewPacket.EXTRACTOR ? GOLD & 0xFFFFFF : ION & 0xFFFFFF) : MUTED;
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
        g.fill(x + VIEW_X - 1, y + VIEW_Y - 1, x + VIEW_X + VIEW_W + 1, y + VIEW_Y + VIEW_H + 1, DIM);
        g.fill(x + VIEW_X, y + VIEW_Y, x + VIEW_X + VIEW_W, y + VIEW_Y + VIEW_H, SLOT_BG);
        InterfaceViewPacket v = view != null && view.pos().equals(pos) ? view : null;
        if (v == null) {
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        structure(g, v, partialTick);
        int extractors = 0, cables = 0, repeaters = 0, receivers = 0, refused = 0, delivered = 0;
        for (InterfaceViewPacket.Entry e : v.blocks()) {
            switch (e.kind()) {
                case InterfaceViewPacket.EXTRACTOR -> extractors++;
                case InterfaceViewPacket.CABLE, InterfaceViewPacket.VALVE -> cables++;
                case InterfaceViewPacket.REPEATER -> repeaters++;
                case InterfaceViewPacket.RECEIVER -> { receivers++; delivered += e.flow(); }
                case InterfaceViewPacket.REFUSED -> refused++;
                default -> { }
            }
        }
        int ly = y + VIEW_Y, cx = x + LIST_X;
        g.drawString(font, tr("supply", v.supply()), cx, ly, GOLD & 0xFFFFFF, false);
        small(g, tr("supply_from"), cx, ly + 11, MUTED);
        int row = ly + 20;
        if (v.cloak() > 0) { // the totem makes more than the extractors get: Cloaking takes its share first
            small(g, tr("cloak", v.supply() + v.cloak(), v.cloak()), cx, row, MUTED);
            row += 9;
        }
        small(g, tr("members", v.members(), v.max()), cx, row, v.members() >= v.max() ? BAD : TEXT);
        row += 9;
        if (refused > 0) {
            small(g, tr("refused_n", refused), cx, row, BAD);
            row += 9;
        }
        row += 4;
        small(g, tr("extractors", extractors), cx, row, TEXT);
        small(g, tr("cables", cables, repeaters), cx, row + 9, TEXT);
        small(g, tr("receivers", receivers), cx, row + 18, TEXT);
        small(g, tr("delivered", delivered), cx, row + 27, delivered < v.supply() && receivers > 0 ? TEXT : MUTED);
        row += 36;
        if (v.supply() > 0 && receivers > 0 && delivered < v.supply()) {
            small(g, tr("spare", v.supply() - delivered), cx, row, MUTED);
            row += 9;
        }
        row += 6;
        for (var line : font.split(tr("help"), Math.round(COL_W / 0.75f))) { // wrapped to the column, so nothing runs past the edge
            g.pose().pushPose();
            g.pose().translate(cx, row, 0);
            g.pose().scale(0.75f, 0.75f, 1f);
            g.drawString(font, line, 0, 0, MUTED, false);
            g.pose().popPose();
            row += 8;
        }
        row += 4;
        small(g, tr("legend.extractor"), cx, row, GOLD & 0xFFFFFF);
        small(g, tr("legend.cable"), cx, row + 9, ION & 0xFFFFFF);
        small(g, tr("legend.refused"), cx, row + 18, BAD);
        // the reset button under the view
        int bx = x + VIEW_X, by = y + VIEW_Y + VIEW_H + 4;
        boolean hover = overReset(mouseX, mouseY);
        g.fill(bx, by, bx + RESET_W, by + 12, SLOT_BG);
        g.fill(bx, by, bx + RESET_W, by + 1, hover ? TQ : DIM);
        g.fill(bx, by + 11, bx + RESET_W, by + 12, hover ? TQ : DIM);
        g.fill(bx, by, bx + 1, by + 12, hover ? TQ : DIM);
        g.fill(bx + RESET_W - 1, by, bx + RESET_W, by + 12, hover ? TQ : DIM);
        String reset = tr("reset").getString();
        small(g, Component.literal(reset), bx + RESET_W / 2 - Math.round(font.width(reset) * 0.375f), by + 3, hover ? TQ & 0xFFFFFF : TEXT);
        small(g, tr("zoom", Math.round(zoom * 100)), bx + RESET_W + 6, by + 3, MUTED);
        super.render(g, mouseX, mouseY, partialTick);
    }
}
