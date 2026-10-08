package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Draws a living entity see-through at a chosen strength: a player inside a cloaked cube fading in or out with the blocks. The
 * entity's renderer is run again with a buffer source that sends every textured layer through the translucent entity layer for
 * the same texture, scaling the vertex alpha. The texture of a render type is read by reflection (the fields are private); when
 * that fails the entity is simply not drawn until the fade ends.
 */
public final class FadedEntities {
    private FadedEntities() {}

    private static boolean rendering;
    private static final Map<RenderType, Optional<ResourceLocation>> TEXTURES = new HashMap<>();
    @Nullable
    private static Field stateField, textureStateField, textureField;
    private static boolean reflectionFailed;

    public static boolean isRendering() {
        return rendering;
    }

    /** Runs the entity's renderer at {@code alpha} (0 invisible, 1 solid), from inside its Pre event, which must be cancelled by the caller. */
    public static void render(RenderLivingEvent.Pre<?, ?> event, float alpha) {
        if (reflectionFailed || alpha <= 0.01f) {
            return;
        }
        LivingEntity entity = event.getEntity();
        float yaw = Mth.lerp(event.getPartialTick(), entity.yRotO, entity.getYRot());
        rendering = true;
        try {
            @SuppressWarnings({"unchecked", "rawtypes"})
            net.minecraft.client.renderer.entity.LivingEntityRenderer renderer = event.getRenderer();
            renderer.render(entity, yaw, event.getPartialTick(), event.getPoseStack(), new Faded(event.getMultiBufferSource(), alpha), event.getPackedLight());
        } finally {
            rendering = false;
        }
    }

    /** The texture a composite render type samples, or empty when it has none or cannot be read. */
    private static Optional<ResourceLocation> textureOf(RenderType type) {
        return TEXTURES.computeIfAbsent(type, t -> {
            try {
                if (stateField == null) {
                    Class<?> composite = Class.forName("net.minecraft.client.renderer.RenderType$CompositeRenderType");
                    Class<?> state = Class.forName("net.minecraft.client.renderer.RenderType$CompositeState");
                    stateField = ObfuscationReflectionHelper.findField(composite, "f_110511_");
                    textureStateField = ObfuscationReflectionHelper.findField(state, "f_110576_");
                    textureField = ObfuscationReflectionHelper.findField(RenderStateShard.TextureStateShard.class, "f_110328_");
                }
                if (!stateField.getDeclaringClass().isInstance(t)) {
                    return Optional.empty();
                }
                Object state = stateField.get(t);
                Object shard = textureStateField.get(state);
                if (shard instanceof RenderStateShard.TextureStateShard) {
                    @SuppressWarnings("unchecked")
                    Optional<ResourceLocation> tex = (Optional<ResourceLocation>) textureField.get(shard);
                    return tex;
                }
                return Optional.empty();
            } catch (ReflectiveOperationException | RuntimeException e) {
                if (!reflectionFailed) {
                    reflectionFailed = true;
                    com.mrgregles.bsp_core.BSPCore.LOGGER.warn("Cannot read render type textures ({}); players under a cloak will appear without a fade", e.toString());
                }
                return Optional.empty();
            }
        });
    }

    /** A buffer source that turns every textured layer into the translucent entity layer for that texture, at a set alpha. */
    private record Faded(MultiBufferSource delegate, float alpha) implements MultiBufferSource {
        @Override
        public VertexConsumer getBuffer(RenderType type) {
            Optional<ResourceLocation> tex = textureOf(type);
            if (tex.isEmpty()) {
                return new Alpha(delegate.getBuffer(type), alpha);
            }
            return new Alpha(delegate.getBuffer(RenderType.entityTranslucent(tex.get())), alpha);
        }
    }

    /** Scales the alpha of every vertex colour written through it. */
    private record Alpha(VertexConsumer in, float alpha) implements VertexConsumer {
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            in.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            in.color(r, g, b, Math.round(a * alpha));
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
        public void defaultColor(int r, int g, int b, int a) {
            in.defaultColor(r, g, b, Math.round(a * alpha));
        }

        @Override
        public void unsetDefaultColor() {
            in.unsetDefaultColor();
        }
    }
}
