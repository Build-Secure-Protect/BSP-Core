package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
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
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * Draws a set of blocks that are not in this client's world as see-through shells at a chosen strength: the real blocks of a
 * cloaked base fading in for a player who just walked in. Built once into a vertex buffer, drawn each frame after the world's
 * translucent blocks, depth-tested but not depth-writing, with the shader colour's alpha as the strength.
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
    }

    /** The shells of {@code blocks}, relative to {@code origin}; null when there is nothing to draw. */
    @Nullable
    public static VertexBuffer build(ClientLevel level, Map<BlockPos, BlockState> blocks, BlockPos origin) {
        View view = new View(level, blocks);
        RandomSource random = RandomSource.create();
        PoseStack pose = new PoseStack();
        BufferBuilder builder = new BufferBuilder(1 << 16);
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
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
                dispatcher.renderBatched(st, p, view, pose, builder, true, random, ModelData.EMPTY, type);
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
        return vb;
    }

    /** Draws a built set at {@code alpha} (0 invisible, 1 solid). Call from AFTER_TRANSLUCENT_BLOCKS. */
    public static void draw(VertexBuffer vb, BlockPos origin, RenderLevelStageEvent event, float alpha) {
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(origin.getX() - cam.x, origin.getY() - cam.y, origin.getZ() - cam.z);
        Matrix4f model = new Matrix4f(pose.last().pose());
        pose.popPose();
        RenderType.translucent().setupRenderState();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(1f, 1f, 1f, Math.max(0f, Math.min(1f, alpha)));
        vb.bind();
        vb.drawWithShader(model, event.getProjectionMatrix(), RenderSystem.getShader());
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.depthMask(true);
        RenderType.translucent().clearRenderState();
    }
}
