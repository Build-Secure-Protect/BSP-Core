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
public class TankScreen extends Screen {
    private static final int BG = 0xF010151C, TQ = 0xFF19D3B0, SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, MUTED = 0x9AA3B5, TEXT = 0xE6EAF2, GOLD = 0xFFFFD23A, BAD = 0xFF6B5C, ION = 0xFF4FB8FF;
    private static final int W = 380, H = 214, VIEW_X = 10, VIEW_Y = 22, VIEW_W = 200, VIEW_H = 164, LIST_X = 218, COL_W = W - LIST_X - 10, RESET_W = 64;
    @Nullable
    private static TankViewPacket view;
    private final BlockPos pos;
    private float yaw = -32, pitch = 28, zoom = 1f, panX, panY;
    private int ticks;
    private final List<float[]> labels = new ArrayList<>();

    public TankScreen(BlockPos pos) {
        super(Component.translatable("gui.bsp_core.tank.title"));
        this.pos = pos;
    }

    public static void receive(TankViewPacket msg) {
        view = msg;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof TankScreen)) {
            mc.setScreen(new TankScreen(msg.pos()));
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
        g.enableScissor(x, y, x + VIEW_W, y + VIEW_H);
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
            float frac = Mth.clamp(v.stored() / (float) v.capacity(), 0f, 1f);
            pose.pushPose();
            pose.scale(1 / 16f, 1 / 16f, 1 / 16f);
            PlasmaRender.tank(PlasmaRender.buffer(g.bufferSource()), pose, PlasmaRender.sprite(), 16.2f, 16.2f, 16.2f, (v.w() - 1) * 16 - 0.2f, (v.h() - 1) * 16 - 0.2f, (v.d() - 1) * 16 - 0.2f, frac);
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
        g.fill(x + VIEW_X - 1, y + VIEW_Y - 1, x + VIEW_X + VIEW_W + 1, y + VIEW_Y + VIEW_H + 1, DIM);
        g.fill(x + VIEW_X, y + VIEW_Y, x + VIEW_X + VIEW_W, y + VIEW_Y + VIEW_H, SLOT_BG);
        TankViewPacket v = view;
        int cx = x + LIST_X, row = y + VIEW_Y;
        if (v == null || v.w() < 3) {
            small(g, tr("unformed"), cx, row, BAD);
            for (var line : font.split(tr("unformed.help"), Math.round(COL_W / 0.75f))) {
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
        g.fill(cx, row + 22, cx + Math.round(COL_W * frac), row + 30, ION);
        row += 38;
        small(g, tr("size", v.w(), v.h(), v.d(), v.w() * v.h() * v.d() - (v.w() - 2) * (v.h() - 2) * (v.d() - 2)), cx, row, TEXT);
        small(g, tr("ports", v.ports().size()), cx, row + 9, TEXT);
        small(g, tr("in", in), cx, row + 18, in > 0 ? ION & 0xFFFFFF : MUTED);
        small(g, tr("out", out), cx, row + 27, out > 0 ? GOLD & 0xFFFFFF : MUTED);
        row += 42;
        for (var line : font.split(tr("help"), Math.round(COL_W / 0.75f))) {
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
}
