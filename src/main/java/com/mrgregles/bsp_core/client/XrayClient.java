package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.plasma.CarriedPowers;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * X-ray ("Fading Depth", first version): while on, ores, containers and other block entities within
 * the level's radius are marked through the blocks in front of them, fading with depth. Switched on
 * with its key, it lasts the level's seconds and then recharges. All client-side: the carried level
 * is read from the offhand item.
 */
public final class XrayClient implements IGuiOverlay {
    public static final XrayClient INSTANCE = new XrayClient();
    public static final KeyMapping TOGGLE = new KeyMapping("key.bsp_core.xray", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_X, "key.categories.bsp_core");
    private record Mark(BlockPos pos, int colour) {
    }

    private static final List<Mark> MARKS = new ArrayList<>();
    private static long onUntil, readyAt;
    private static int radius;

    private XrayClient() {}

    private static int level() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? 0 : CarriedPowers.level(mc.player, Buff.XRAY);
    }

    public static boolean isOn() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getGameTime() < onUntil && level() > 0;
    }

    private static void toggle() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        int lvl = level();
        long now = mc.level.getGameTime();
        if (lvl <= 0) {
            mc.player.displayClientMessage(Component.translatable("message.bsp_core.xray.none").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (now < onUntil) {
            onUntil = now; // switch off early: the recharge still runs from the use
            return;
        }
        if (now < readyAt) {
            mc.player.displayClientMessage(Component.translatable("message.bsp_core.xray.recharging", (readyAt - now) / 20).withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        int seconds = BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.XRAY_SECONDS, List.<Integer>of()), lvl, 25), recharge = BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.XRAY_RECHARGE, List.<Integer>of()), lvl, 120);
        radius = BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.XRAY_RADIUS, List.<Integer>of()), lvl, 4);
        onUntil = now + seconds * 20L;
        readyAt = onUntil + recharge * 20L;
        scan();
    }

    /** Collects what to mark: ores (the forge ores tag), block entities, spawners. Runs every half second while on. */
    private static void scan() {
        Minecraft mc = Minecraft.getInstance();
        MARKS.clear();
        if (mc.level == null || mc.player == null) {
            return;
        }
        BlockPos centre = mc.player.blockPosition();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    p.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    BlockState st = mc.level.getBlockState(p);
                    if (st.isAir()) {
                        continue;
                    }
                    if (st.is(Tags.Blocks.ORES)) {
                        MARKS.add(new Mark(p.immutable(), 0x4FB8FF));
                    } else if (st.is(net.minecraft.world.level.block.Blocks.SPAWNER)) {
                        MARKS.add(new Mark(p.immutable(), 0xFF6B5C));
                    } else if (st.hasBlockEntity()) {
                        MARKS.add(new Mark(p.immutable(), 0xFFD23A));
                    }
                }
            }
        }
    }

    @Mod.EventBusSubscriber(modid = BSPCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Register {
        @SubscribeEvent
        public static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE);
        }
    }

    @Mod.EventBusSubscriber(modid = BSPCore.MODID, value = Dist.CLIENT)
    public static final class Events {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            while (TOGGLE.consumeClick()) {
                toggle();
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && isOn() && mc.level.getGameTime() % 10 == 0) {
                scan();
            }
        }

        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !isOn() || MARKS.isEmpty()) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            int lvl = level();
            float strength = BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.XRAY_ALPHA, List.<Double>of()), lvl, 0.35).floatValue();
            Vec3 cam = event.getCamera().getPosition();
            PoseStack pose = event.getPoseStack();
            pose.pushPose();
            pose.translate(-cam.x, -cam.y, -cam.z);
            Matrix4f m = pose.last().pose();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            BufferBuilder b = Tesselator.getInstance().getBuilder();
            b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (Mark mark : MARKS) {
                double d = Math.sqrt(mark.pos.distSqr(mc.player.blockPosition()));
                float a = strength * (1f - 0.6f * (float) Math.min(1, d / Math.max(1, radius))); // fades with depth
                int r = (mark.colour >> 16) & 0xFF, g = (mark.colour >> 8) & 0xFF, bl = mark.colour & 0xFF, al = Math.round(a * 255);
                float x0 = mark.pos.getX() + 0.15f, y0 = mark.pos.getY() + 0.15f, z0 = mark.pos.getZ() + 0.15f, x1 = x0 + 0.7f, y1 = y0 + 0.7f, z1 = z0 + 0.7f;
                float[][] faces = {{x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1}, {x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0}, {x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1},
                        {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, {x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0}, {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}};
                for (float[] f : faces) {
                    for (int i = 0; i < 4; i++) {
                        b.vertex(m, f[i * 3], f[i * 3 + 1], f[i * 3 + 2]).color(r, g, bl, al).endVertex();
                    }
                }
            }
            Tesselator.getInstance().end();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
            pose.popPose();
        }
    }

    /** A small ring by the crosshair: filling while on (time left), hollow while recharging. */
    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || level() <= 0) {
            return;
        }
        long now = mc.level.getGameTime();
        boolean on = now < onUntil, charging = !on && now < readyAt;
        if (!on && !charging) {
            return;
        }
        float k = on ? (onUntil - now) / (float) Math.max(1, onUntil - (readyAt - (readyAt - onUntil))) : 1f - (readyAt - now) / (float) Math.max(1, readyAt - onUntil);
        if (on) {
            int lvl = level();
            k = (onUntil - now) / (20f * BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.XRAY_SECONDS, List.<Integer>of()), lvl, 25));
        }
        int cx = width / 2 + 24, cy = height / 2;
        for (int i = 0; i < 24; i++) {
            double a = -Math.PI / 2 + i / 24.0 * Math.PI * 2;
            int px = cx + (int) Math.round(Math.cos(a) * 7), py = cy + (int) Math.round(Math.sin(a) * 7);
            g.fill(px, py, px + 1, py + 1, i / 24f < k ? (on ? 0xFF4FB8FF : 0xFF9AA3B5) : 0x802A2F3A);
        }
    }
}
