package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.plasma.CarriedPowers;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import com.mrgregles.bsp_core.xray.GhostBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * X-ray, the approved "Fading Depth" look, done with ghost blocks: while on, every solid block within the level's radius is
 * swapped, in this client's copy of the world only, for a {@link GhostBlock} that draws nothing but keeps the block's shape.
 * The real blocks are drawn by this class as see-through shells that fade with depth; ores stay real and are drawn again at
 * full brightness so they read through the rock; containers and spawners stay real and get a lit mark. Switched on with its key,
 * it lasts the level's seconds and then recharges. The carried level is read from the offhand item. Blocks inside a cloaked
 * cube that hides from this player are left alone.
 */
public final class XrayClient implements IGuiOverlay {
    public static final XrayClient INSTANCE = new XrayClient();
    public static final KeyMapping TOGGLE = new KeyMapping("key.bsp_core.xray", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, InputConstants.KEY_X, "key.categories.bsp_core");
    private static final int BANDS = 4, FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** The shells use half the level's strength: the owner found the full figure too solid (2026-10-08). The marks keep the full figure. */
    private static final float SHELL = 0.5f;
    /** Container marks barely there, 8 % of the strength: any stronger and the gold box hid which machine it was (owner, 2026-10-08). */
    private static final float CONTAINER_MARK = 0.08f;

    private record Mark(BlockPos pos, int colour, float alpha) {
    }

    private static final List<Mark> MARKS = new ArrayList<>();
    /** Real blocks under the ghosts. */
    private static final Map<BlockPos, BlockState> GHOSTS = new HashMap<>();
    /** One shell buffer per depth band (near to far), one buffer of ores drawn bright, all relative to {@link #origin}. */
    private static final VertexBuffer[] SHELLS = new VertexBuffer[BANDS];
    @Nullable
    private static VertexBuffer ores;
    private static BlockPos origin = BlockPos.ZERO;
    private static long onUntil, readyAt;
    private static int radius;
    @Nullable
    private static ClientLevel lastLevel;

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
            restoreAll();
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

    /** Whether a block is one X-ray hides: a full opaque block that is not an ore, a container or a spawner, and not under a cloak. */
    private static boolean hides(ClientLevel level, BlockPos pos, BlockState st) {
        return !st.isAir() && st.getFluidState().isEmpty() && st.isSolidRender(level, pos) && !(st.getBlock() instanceof GhostBlock)
                && !st.is(Tags.Blocks.ORES) && !st.hasBlockEntity() && !st.is(Blocks.SPAWNER) && !CloakClient.hiddenFromMe(pos);
    }

    /**
     * Every half second while on: ghosts every hiding block in the cube, puts back those that left it, remembers what the server
     * sent in the meantime, lists the marks, and rebuilds the shell and ore buffers.
     */
    private static void scan() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        ClientLevel level = mc.level;
        BlockState ghost = ModBlocks.GHOST_BLOCK.get().defaultBlockState();
        BlockPos centre = mc.player.blockPosition();
        MARKS.clear();
        // ghosts outside the cube, or in chunks that went away, go back
        for (Iterator<Map.Entry<BlockPos, BlockState>> it = GHOSTS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, BlockState> e = it.next();
            BlockPos p = e.getKey();
            if (!level.isLoaded(p)) {
                it.remove();
            } else if (Math.abs(p.getX() - centre.getX()) > radius || Math.abs(p.getY() - centre.getY()) > radius || Math.abs(p.getZ() - centre.getZ()) > radius) {
                if (level.getBlockState(p).getBlock() instanceof GhostBlock) {
                    level.setBlock(p, e.getValue(), FLAGS);
                }
                it.remove();
            }
        }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        List<BlockPos> oreList = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    p.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (!level.isLoaded(p)) {
                        continue;
                    }
                    BlockState st = level.getBlockState(p);
                    if (st.getBlock() instanceof GhostBlock) {
                        if (!GHOSTS.containsKey(p)) {
                            level.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS); // a ghost we do not remember: cannot be right, clear it
                        }
                        continue;
                    }
                    if (hides(level, p, st)) {
                        GHOSTS.put(p.immutable(), st); // new, or the server sent a different block: remember and ghost it
                        level.setBlock(p, ghost, FLAGS);
                    } else if (st.is(Tags.Blocks.ORES)) {
                        oreList.add(p.immutable());
                    } else if (st.is(Blocks.SPAWNER)) {
                        MARKS.add(new Mark(p.immutable(), 0xFF6B5C, 1f));
                    } else if (st.hasBlockEntity() && !st.isAir()) {
                        MARKS.add(new Mark(p.immutable(), 0xFFD23A, CONTAINER_MARK));
                    }
                }
            }
        }
        rebuild(level, centre, oreList);
    }

    /** Puts every real block back and drops the buffers. */
    private static void restoreAll() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            GHOSTS.forEach((p, st) -> {
                if (mc.level.isLoaded(p) && mc.level.getBlockState(p).getBlock() instanceof GhostBlock) {
                    mc.level.setBlock(p, st, FLAGS);
                }
            });
        }
        GHOSTS.clear();
        MARKS.clear();
        dropBuffers();
    }

    private static void dropBuffers() {
        for (int i = 0; i < BANDS; i++) {
            if (SHELLS[i] != null) {
                SHELLS[i].close();
                SHELLS[i] = null;
            }
        }
        if (ores != null) {
            ores.close();
            ores = null;
        }
    }

    /** The world as it really is: ghost positions answer with the block under them, and everything is fully lit. */
    private record RealView(ClientLevel level) implements BlockAndTintGetter {
        @Override
        public BlockState getBlockState(BlockPos pos) {
            BlockState real = GHOSTS.get(pos);
            return real != null ? real : level.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return level.getBlockEntity(pos);
        }

        @Override
        public int getHeight() {
            return level.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return level.getMinBuildHeight();
        }

        @Override
        public float getShade(Direction dir, boolean shade) {
            return level.getShade(dir, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return level.getLightEngine();
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            return level.getBlockTint(pos, resolver);
        }

        @Override
        public int getBrightness(LightLayer layer, BlockPos pos) {
            return 15;
        }

        @Override
        public int getRawBrightness(BlockPos pos, int amount) {
            return 15;
        }
    }

    /** Builds the shells of the ghosted blocks (faces culled against the real neighbours, so only surfaces are drawn) by depth band, and the ores. */
    private static void rebuild(ClientLevel level, BlockPos centre, List<BlockPos> oreList) {
        dropBuffers();
        origin = centre.immutable();
        RealView view = new RealView(level);
        RandomSource random = RandomSource.create();
        PoseStack pose = new PoseStack();
        BufferBuilder[] builders = new BufferBuilder[BANDS];
        for (int i = 0; i < BANDS; i++) {
            builders[i] = new BufferBuilder(1 << 16);
            builders[i].begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        }
        for (Map.Entry<BlockPos, BlockState> e : GHOSTS.entrySet()) {
            BlockPos p = e.getKey();
            int d = Math.max(Math.abs(p.getX() - centre.getX()), Math.max(Math.abs(p.getY() - centre.getY()), Math.abs(p.getZ() - centre.getZ())));
            int band = Math.min(BANDS - 1, d * BANDS / Math.max(1, radius + 1));
            emit(view, p, e.getValue(), pose, builders[band], random);
        }
        for (int i = 0; i < BANDS; i++) {
            SHELLS[i] = upload(builders[i]);
        }
        BufferBuilder oreBuilder = new BufferBuilder(1 << 14);
        oreBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        for (BlockPos p : oreList) {
            emit(view, p, level.getBlockState(p), pose, oreBuilder, random);
        }
        ores = upload(oreBuilder);
    }

    private static void emit(RealView view, BlockPos p, BlockState st, PoseStack pose, BufferBuilder into, RandomSource random) {
        pose.pushPose();
        pose.translate(p.getX() - origin.getX(), p.getY() - origin.getY(), p.getZ() - origin.getZ());
        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        for (RenderType type : ItemBlockRenderTypes.getRenderLayers(st)) {
            dispatcher.renderBatched(st, p, view, pose, into, true, random, ModelData.EMPTY, type);
        }
        pose.popPose();
    }

    @Nullable
    private static VertexBuffer upload(BufferBuilder builder) {
        BufferBuilder.RenderedBuffer rendered = builder.end();
        if (rendered.drawState().vertexCount() == 0) {
            rendered.release();
            return null;
        }
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vb.bind();
        vb.upload(rendered);
        VertexBuffer.unbind();
        return vb;
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
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != lastLevel) { // another world: the old ghosts are gone with it
                lastLevel = mc.level;
                GHOSTS.clear();
                MARKS.clear();
                dropBuffers();
                onUntil = 0;
                readyAt = 0;
            }
            while (TOGGLE.consumeClick()) {
                toggle();
            }
            if (mc.level == null) {
                return;
            }
            if (isOn()) {
                if (mc.level.getGameTime() % 10 == 0) {
                    scan();
                }
            } else if (!GHOSTS.isEmpty()) {
                restoreAll(); // ran out, or the power was put away
            }
        }

        @SubscribeEvent
        public static void onRenderLevel(RenderLevelStageEvent event) {
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !isOn()) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            int lvl = level();
            float strength = BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.XRAY_ALPHA, List.<Double>of()), lvl, 0.35).floatValue();
            Vec3 cam = event.getCamera().getPosition();
            PoseStack pose = event.getPoseStack();
            Matrix4f projection = event.getProjectionMatrix();
            // the ores, bright and solid, over the dark chunk copy of themselves
            pose.pushPose();
            pose.translate(origin.getX() - cam.x, origin.getY() - cam.y, origin.getZ() - cam.z);
            Matrix4f model = new Matrix4f(pose.last().pose());
            pose.popPose();
            if (ores != null) {
                RenderType.cutoutMipped().setupRenderState();
                ores.bind();
                ores.drawWithShader(model, projection, RenderSystem.getShader());
                VertexBuffer.unbind();
                RenderType.cutoutMipped().clearRenderState();
            }
            // the shells: see-through, no depth write, fainter the further the band
            RenderType.translucent().setupRenderState();
            RenderSystem.depthMask(false);
            for (int i = 0; i < BANDS; i++) {
                if (SHELLS[i] == null) {
                    continue;
                }
                float a = strength * SHELL * (1f - 0.7f * i / (float) (BANDS - 1));
                RenderSystem.setShaderColor(1f, 1f, 1f, a);
                SHELLS[i].bind();
                SHELLS[i].drawWithShader(model, projection, RenderSystem.getShader());
                VertexBuffer.unbind();
            }
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.depthMask(true);
            RenderType.translucent().clearRenderState();
            if (MARKS.isEmpty()) {
                return;
            }
            // containers and spawners: a lit mark through everything, as before
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
                float a = strength * mark.alpha * (1f - 0.6f * (float) Math.min(1, d / Math.max(1, radius)));
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
        float k;
        if (on) {
            int lvl = level();
            k = (onUntil - now) / (20f * BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.XRAY_SECONDS, List.<Integer>of()), lvl, 25));
        } else {
            k = 1f - (readyAt - now) / (float) Math.max(1, readyAt - onUntil);
        }
        int cx = width / 2 + 24, cy = height / 2;
        for (int i = 0; i < 24; i++) {
            double a = -Math.PI / 2 + i / 24.0 * Math.PI * 2;
            int px = cx + (int) Math.round(Math.cos(a) * 7), py = cy + (int) Math.round(Math.sin(a) * 7);
            g.fill(px, py, px + 1, py + 1, i / 24f < k ? (on ? 0xFF4FB8FF : 0xFF9AA3B5) : 0x802A2F3A);
        }
    }
}
