package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Draws a set of blocks that are not in this client's world as see-through shells at a chosen strength: the real blocks of a
 * cloaked base fading in for a player who just walked in, and (through {@link #begin}, {@link #drawOne} and {@link #end}, with
 * meshes of its own) X-ray's shells. Built once into a vertex buffer, drawn each frame after the world's
 * translucent blocks, depth-tested but not depth-writing, with the shader colour's alpha as the strength.
 * <p>
 * The shells go through the translucent entity layer on the block atlas, not the translucent terrain layer. Shader packs read a
 * terrain vertex's alpha as ambient occlusion (Oculus's separate AO), so on terrain the strength came out as darkness on solid
 * blocks; on the entity layer the packs read it as opacity, as for the faded players. The mesh is filled through
 * {@link Opaque}, which keeps the shading in the colour and the alpha at full, so nothing but the strength ever reaches the alpha.
 * The entity shader shades faces by their normal itself, so the mesh carries no side shade of its own.
 */
public final class BlockShells {
    private BlockShells() {}

    /** The world with some positions answered by other blocks, so faces between them are culled as if they stood there. */
    public record View(ClientLevel level, Map<BlockPos, BlockState> blocks) implements BlockAndTintGetter {
        @Override
        public BlockState getBlockState(BlockPos pos) {
            BlockState st = blocks.get(pos);
            return st != null ? st : level.getBlockState(pos);
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
            return 1f; // the entity shader shades by normal when it draws: none baked in, or the sides would be shaded twice
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return level.getLightEngine();
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver resolver) {
            return level.getBlockTint(pos, resolver);
        }
    }

    /**
     * The faintest strength the shell layer still draws: vanilla's entity shader and Oculus's entity programs both throw away
     * anything at or under a tenth of full.
     */
    public static final float MIN_ALPHA = 0.11f;

    @Nullable
    private static java.lang.reflect.Method irisInUse;
    @Nullable
    private static Object irisApi;
    private static boolean irisLooked;

    /** Whether a shader pack is drawing the world, from Oculus's public api (read by reflection, so Oculus stays optional). */
    public static boolean shaderPackInUse() {
        if (!irisLooked) {
            irisLooked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = api.getMethod("getInstance").invoke(null);
                irisInUse = api.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                irisInUse = null; // no Oculus: no shader pack
            }
        }
        if (irisInUse == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(irisInUse.invoke(irisApi));
        } catch (ReflectiveOperationException | RuntimeException e) {
            irisInUse = null;
            return false;
        }
    }

    @Nullable
    private static BufferBuilder builder;

    /** The shells of {@code blocks}, relative to {@code origin}; null when there is nothing to draw. */
    @Nullable
    public static VertexBuffer build(ClientLevel level, Map<BlockPos, BlockState> blocks, BlockPos origin) {
        View view = new View(level, blocks);
        RandomSource random = RandomSource.create();
        PoseStack pose = new PoseStack();
        if (builder == null) {
            builder = new BufferBuilder(1 << 16); // one, kept: a BufferBuilder's native memory is never given back
        }
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        Opaque out = new Opaque(builder);
        var dispatcher = Minecraft.getInstance().getBlockRenderer();
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
            BlockState st = e.getValue();
            if (st.getRenderShape() != net.minecraft.world.level.block.RenderShape.MODEL) {
                continue;
            }
            BlockPos p = e.getKey();
            pose.pushPose();
            pose.translate(p.getX() - origin.getX(), p.getY() - origin.getY(), p.getZ() - origin.getZ());
            for (RenderType type : ItemBlockRenderTypes.getRenderLayers(st)) {
                dispatcher.renderBatched(st, p, view, pose, out, true, random, ModelData.EMPTY, type);
            }
            pose.popPose();
        }
        BufferBuilder.RenderedBuffer rendered = builder.end();
        if (rendered.drawState().vertexCount() == 0) {
            rendered.release();
            return null;
        }
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vb.bind();
        vb.upload(rendered);
        VertexBuffer.unbind();
        BUILT_FOR_SHADERS.put(vb, shaderPackInUse());
        return vb;
    }

    /** Draws a built set at {@code alpha} (0 invisible, 1 solid). Call from AFTER_TRANSLUCENT_BLOCKS. */
    public static void draw(VertexBuffer vb, BlockPos origin, RenderLevelStageEvent event, float alpha) {
        begin();
        drawOne(vb, modelAt(origin, event), event.getProjectionMatrix(), alpha);
        end(event);
    }

    /** The model matrix that puts a mesh built relative to {@code origin} in its place in the world, for this frame's camera. */
    public static Matrix4f modelAt(BlockPos origin, RenderLevelStageEvent event) {
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(origin.getX() - cam.x, origin.getY() - cam.y, origin.getZ() - cam.z);
        Matrix4f model = new Matrix4f(pose.last().pose());
        pose.popPose();
        return model;
    }

    /**
     * Sets up the shell layer for one or more {@link #drawOne} calls, which must be followed by {@link #end}: the translucent
     * entity layer on the block atlas, depth-tested but not depth-writing. A mesh's normals are in world space (it is built
     * without the camera's turn), so the two entity lights are too while shells draw.
     */
    public static void begin() {
        Sheets.translucentCullBlockSheet().setupRenderState();
        RenderSystem.depthMask(false);
        setLevelLights(new Matrix4f());
        // the entity shader measures fog through the camera's inverse turn, for vertices already turned by the camera; these are
        // not (the turn is in the model matrix, as for terrain), so no inverse turn while shells draw, or the fog would be wrong
        savedViewRot = new Matrix3f(RenderSystem.getInverseViewRotationMatrix());
        RenderSystem.setInverseViewRotationMatrix(new Matrix3f());
    }

    @Nullable
    private static Matrix3f savedViewRot;

    /** A strength for the shell layer: nothing stays nothing, anything else is at least the faintest the layer still draws. */
    public static float floored(float alpha) {
        return alpha <= 0f ? 0f : Math.max(MIN_ALPHA, alpha);
    }

    /** Whether {@code vb} was built for the other state of shader packs (Oculus lays the vertices out differently), so must be built again. */
    public static boolean stale(VertexBuffer vb) {
        Boolean forShaders = BUILT_FOR_SHADERS.get(vb);
        return forShaders != null && forShaders != shaderPackInUse();
    }

    /** For each mesh made by {@link #build}, whether a shader pack was on then. */
    private static final Map<VertexBuffer, Boolean> BUILT_FOR_SHADERS = new java.util.WeakHashMap<>();

    /** Draws one mesh made by {@link #build} (or through {@link Opaque} in {@link DefaultVertexFormat#NEW_ENTITY}) at {@code alpha}, between {@link #begin} and {@link #end}. */
    public static void drawOne(VertexBuffer vb, Matrix4f model, Matrix4f projection, float alpha) {
        RenderSystem.setShaderColor(1f, 1f, 1f, Math.max(0f, Math.min(1f, alpha)));
        vb.bind();
        vb.drawWithShader(model, projection, RenderSystem.getShader());
        VertexBuffer.unbind();
    }

    /** Ends what {@link #begin} set up: the world's own lights, turned with the camera as the level renderer set them, go back. */
    public static void end(RenderLevelStageEvent event) {
        setLevelLights(new Matrix4f(event.getPoseStack().last().pose()));
        if (savedViewRot != null) {
            RenderSystem.setInverseViewRotationMatrix(savedViewRot);
            savedViewRot = null;
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.depthMask(true);
        Sheets.translucentCullBlockSheet().clearRenderState();
    }

    /** The world's two entity lights turned by {@code m}, as the level renderer sets them (the Nether's pair where the sky is constant). */
    private static void setLevelLights(Matrix4f m) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.effects().constantAmbientLight()) {
            Lighting.setupNetherLevel(m);
        } else {
            Lighting.setupLevel(m);
        }
    }

    /**
     * Passes vertices on with the alpha held at full. A block model's shading is folded into the colour by the consumer's own
     * {@code putBulkData}, which Oculus with separate AO swaps on a {@link BufferBuilder} to put the shading in the alpha instead;
     * going through this wrapper keeps it in the colour, so the alpha is left for the fade's strength alone.
     */
    public record Opaque(VertexConsumer in) implements VertexConsumer {
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            in.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            in.color(r, g, b, 255);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            in.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            in.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            in.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            in.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            in.endVertex();
        }

        @Override
        public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v, int overlay, int light,
                           float nx, float ny, float nz) {
            in.vertex(x, y, z, r, g, b, 1f, u, v, overlay, light, nx, ny, nz);
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            in.defaultColor(r, g, b, 255);
        }

        @Override
        public void unsetDefaultColor() {
            in.unsetDefaultColor();
        }
    }
}
