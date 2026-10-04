package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Assembly Guide ("Step Builder"): shows a multiblock being built one step at a time. Blocks of
 * earlier steps are solid, the current step pulses, later steps are faint see-through outlines of
 * where blocks will go. A parts list counts what has been placed so far. Drag to turn the model; Back, Play
 * and Next (or the arrow keys) move through the steps. Opened by sneak + right-click on an
 * unfinished controller, or by holding Shift over a multiblock block in JEI.
 */
public class AssemblyGuideScreen extends Screen {
    private static final int BG = 0xF010151C, TQ = 0xFF19D3B0, SLOT_BG = 0xFF0C0E12, DIM = 0xFF2A2F3A, OUT = 0xFFFF8A3A, MUTED = 0x9AA3B5;
    private static final int W = 326, H = 206, VIEW_X = 10, VIEW_Y = 22, VIEW_W = 188, VIEW_H = 124, PARTS_X = 206, BTN_Y = 186;
    private static final String[] BUTTONS = {"back", "play", "next"};
    /** How solid the blocks of later steps are drawn. */
    private static final float GHOST_ALPHA = 0.14f;

    private final AssemblyGuide guide;
    @javax.annotation.Nullable
    private final Screen parent;
    private int step, ticks, sinceStep;
    private boolean playing;
    private float yaw = -32, pitch = 28;
    private float cx, cy, cz, span = 1;

    public AssemblyGuideScreen(AssemblyGuide guide) {
        this(guide, null);
    }

    /** @param parent the screen to go back to when the guide is closed, or null */
    public AssemblyGuideScreen(AssemblyGuide guide, @javax.annotation.Nullable Screen parent) {
        super(Component.translatable("gui.bsp_core.guide.title"));
        this.guide = guide;
        this.parent = parent;
        float minX = 0, maxX = 1, minY = 0, maxY = 1, minZ = 0, maxZ = 1;
        for (AssemblyGuide.Step s : guide.steps()) {
            for (AssemblyGuide.Placed p : s.blocks()) {
                BlockPos r = p.rel();
                minX = Math.min(minX, r.getX());
                maxX = Math.max(maxX, r.getX() + 1);
                minY = Math.min(minY, r.getY());
                maxY = Math.max(maxY, r.getY() + 1);
                minZ = Math.min(minZ, r.getZ());
                maxZ = Math.max(maxZ, r.getZ() + 1);
            }
        }
        cx = (minX + maxX) / 2;
        cy = (minY + maxY) / 2;
        cz = (minZ + maxZ) / 2;
        span = Math.max(maxY - minY, Mth.sqrt(Mth.square(maxX - minX) + Mth.square(maxZ - minZ)));
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return (height - H) / 2;
    }

    private void go(int to) {
        step = Mth.clamp(to, 0, guide.steps().size() - 1);
        sinceStep = 0;
    }

    @Override
    public void tick() {
        ticks++;
        sinceStep++;
        if (playing && sinceStep >= 32) {
            if (step >= guide.steps().size() - 1) {
                playing = false;
            } else {
                go(step + 1);
            }
        }
    }

    @Override
    public void onClose() {
        if (parent != null && minecraft != null) {
            minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int buttonX(int i) {
        return left() + 10 + i * 46;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < BUTTONS.length; i++) {
            if (mx >= buttonX(i) && mx < buttonX(i) + 42 && my >= top() + BTN_Y && my < top() + BTN_Y + 14) {
                if (i == 0) {
                    playing = false;
                    go(step - 1);
                } else if (i == 2) {
                    playing = false;
                    go(step + 1);
                } else {
                    playing = !playing;
                    if (playing && step >= guide.steps().size() - 1) {
                        go(0);
                    }
                    sinceStep = 0;
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        yaw += (float) dx;
        pitch = Mth.clamp(pitch + (float) dy, -10, 80);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 262) { // right arrow
            playing = false;
            go(step + 1);
            return true;
        }
        if (key == 263) { // left arrow
            playing = false;
            go(step - 1);
            return true;
        }
        if (minecraft != null && minecraft.options.keyInventory.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void structure(GuiGraphics g, float partialTick) {
        int x = left() + VIEW_X, y = top() + VIEW_Y;
        float scale = Math.min(VIEW_W, VIEW_H) / (span * 1.25f);
        float baseYaw = switch (guide.facing()) {
            case NORTH -> 180f;
            case EAST -> -90f;
            case WEST -> 90f;
            default -> 0f;
        };
        var blocks = Minecraft.getInstance().getBlockRenderer();
        float pulse = 0.9f + 0.1f * Mth.sin((ticks + partialTick) * 0.35f);
        g.enableScissor(x, y, x + VIEW_W, y + VIEW_H);
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x + VIEW_W / 2f, y + VIEW_H / 2f, 300);
        pose.scale(scale, -scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(pitch));
        pose.mulPose(Axis.YP.rotationDegrees(baseYaw + yaw));
        pose.translate(-cx, -cy, -cz);
        // placed and current steps first, solid; then the steps still to come as faint see-through blocks
        for (int s = 0; s <= step && s < guide.steps().size(); s++) {
            float size = s < step ? 1f : pulse;
            for (AssemblyGuide.Placed p : guide.steps().get(s).blocks()) {
                pose.pushPose();
                pose.translate(p.rel().getX() + 0.5, p.rel().getY() + 0.5, p.rel().getZ() + 0.5);
                pose.scale(size, size, size);
                pose.translate(-0.5, -0.5, -0.5);
                blocks.renderSingleBlock(p.state(), pose, g.bufferSource(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }
        g.flush();
        net.minecraft.client.renderer.MultiBufferSource faint = type -> new Faint(g.bufferSource().getBuffer(type), GHOST_ALPHA);
        for (int s = step + 1; s < guide.steps().size(); s++) {
            for (AssemblyGuide.Placed p : guide.steps().get(s).blocks()) {
                pose.pushPose();
                pose.translate(p.rel().getX(), p.rel().getY(), p.rel().getZ());
                blocks.renderSingleBlock(p.state(), pose, faint, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                        net.minecraftforge.client.model.data.ModelData.EMPTY, net.minecraft.client.renderer.Sheets.translucentCullBlockSheet());
                pose.popPose();
            }
        }
        g.flush();
        pose.popPose();
        g.disableScissor();
    }

    private void small(GuiGraphics g, Component text, int x, int y, int colour) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, colour, false);
        g.pose().popPose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int x = left(), y = top();
        g.fill(x - 1, y - 1, x + W + 1, y + H + 1, TQ);
        g.fill(x, y, x + W, y + H, BG);
        g.drawString(font, title, x + 10, y + 8, TQ & 0xFFFFFF, false);
        String name = guide.title().getString().toUpperCase(Locale.ROOT);
        g.drawString(font, name, x + W - 10 - font.width(name), y + 8, TQ & 0xFFFFFF, false);
        g.fill(x + VIEW_X - 1, y + VIEW_Y - 1, x + VIEW_X + VIEW_W + 1, y + VIEW_Y + VIEW_H + 1, DIM);
        g.fill(x + VIEW_X, y + VIEW_Y, x + VIEW_X + VIEW_W, y + VIEW_Y + VIEW_H, SLOT_BG);
        structure(g, partialTick);

        // parts list: placed so far / needed in total
        AssemblyGuide.Step current = guide.steps().get(step);
        Map<Block, int[]> parts = new LinkedHashMap<>();
        for (int s = 0; s < guide.steps().size(); s++) {
            AssemblyGuide.Step st = guide.steps().get(s);
            int[] n = parts.computeIfAbsent(st.block(), k -> new int[2]);
            n[1] += st.blocks().size();
            if (s <= step) {
                n[0] += st.blocks().size();
            }
        }
        int row = 0;
        for (var e : parts.entrySet()) {
            int py = y + VIEW_Y + row++ * 18;
            int colour = e.getKey() == current.block() ? OUT & 0xFFFFFF : e.getValue()[0] >= e.getValue()[1] ? TQ & 0xFFFFFF : 0xE8EAF0;
            g.renderItem(new ItemStack(e.getKey()), x + PARTS_X, py);
            String count = e.getValue()[0] + "/" + e.getValue()[1];
            Component partName = e.getKey() == guide.steps().get(0).block() ? Component.translatable("gui.bsp_core.guide.controller") : e.getKey().getName();
            small(g, Component.literal(font.plainSubstrByWidth(partName.getString(), 126)), x + PARTS_X + 19, py + 1, colour);
            small(g, Component.literal(count), x + PARTS_X + 19, py + 9, MUTED);
        }

        // caption and controls
        g.drawString(font, current.title(), x + 10, y + 150, OUT & 0xFFFFFF, false);
        if (current.tip() != null) {
            g.drawWordWrap(font, current.tip(), x + 10, y + 162, W - 20, MUTED);
        }
        for (int i = 0; i < BUTTONS.length; i++) {
            int bx = buttonX(i), by = y + BTN_Y;
            boolean hover = mouseX >= bx && mouseX < bx + 42 && mouseY >= by && mouseY < by + 14;
            g.fill(bx, by, bx + 42, by + 14, hover || (i == 1 && playing) ? TQ : DIM);
            g.fill(bx + 1, by + 1, bx + 41, by + 13, SLOT_BG);
            Component label = Component.translatable("gui.bsp_core.guide." + (i == 1 && playing ? "pause" : BUTTONS[i]));
            g.drawString(font, label, bx + 21 - font.width(label) / 2, by + 3, hover ? TQ & 0xFFFFFF : 0xE8EAF0, false);
        }
        small(g, Component.translatable("gui.bsp_core.guide.step", step + 1, guide.steps().size()), x + 152, y + BTN_Y + 4, MUTED);
        small(g, Component.translatable("gui.bsp_core.guide.drag"), x + PARTS_X, y + BTN_Y + 4, MUTED);
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Passes vertices through with their alpha scaled down, so a block model can be drawn see-through. */
    private record Faint(com.mojang.blaze3d.vertex.VertexConsumer to, float alpha) implements com.mojang.blaze3d.vertex.VertexConsumer {
        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer vertex(double x, double y, double z) {
            to.vertex(x, y, z);
            return this;
        }

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer color(int r, int g, int b, int a) {
            to.color(r, g, b, Math.round(a * alpha));
            return this;
        }

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer uv(float u, float v) {
            to.uv(u, v);
            return this;
        }

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer overlayCoords(int u, int v) {
            to.overlayCoords(u, v);
            return this;
        }

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer uv2(int u, int v) {
            to.uv2(u, v);
            return this;
        }

        @Override
        public com.mojang.blaze3d.vertex.VertexConsumer normal(float x, float y, float z) {
            to.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            to.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            to.defaultColor(r, g, b, a);
        }

        @Override
        public void unsetDefaultColor() {
            to.unsetDefaultColor();
        }
    }

    /** Opens the guide for the controller at {@code pos}, if it is one. Client only. */
    public static void open(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        AssemblyGuide guide = mc.level == null ? null : AssemblyGuide.of(mc.level.getBlockEntity(pos));
        if (guide != null) {
            mc.setScreen(new AssemblyGuideScreen(guide));
        }
    }
}
