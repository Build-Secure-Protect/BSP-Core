package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.shop.VendorPostBlock;
import com.mrgregles.bsp_core.shop.VendorPostBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;

/**
 * Draws the stock mob chosen for a Vendor Post on top of it, facing the post's front and turning
 * its head toward the nearest player, and the lamp: dark while closed, blinking red while no shop
 * is assigned. The mob is a client-side entity that never joins the world.
 */
public class VendorPostRenderer implements BlockEntityRenderer<VendorPostBlockEntity> {
    private static final int RED = 0xFFE24B3B, DARK = 0xFF1A1D24;
    private Entity figure;
    private String figureKey = "";

    public VendorPostRenderer(BlockEntityRendererProvider.Context ctx) {}

    private static float yRot(Direction facing) {
        return switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    private Entity figure(VendorPostBlockEntity post) {
        String key = post.mob + "|" + post.mobNbt;
        if (figure == null || !figureKey.equals(key)) {
            figure = null;
            figureKey = key;
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(ResourceLocation.tryParse(post.mob));
            if (type != null && post.getLevel() != null) {
                Entity e = type.create(post.getLevel());
                if (e != null && !post.mobNbt.isEmpty()) {
                    try {
                        var tag = e.saveWithoutId(new net.minecraft.nbt.CompoundTag());
                        tag.merge(TagParser.parseTag(post.mobNbt));
                        e.load(tag);
                    } catch (Exception ignored) {
                        // bad NBT: plain mob
                    }
                }
                figure = e;
            }
        }
        return figure;
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int argb) {
        int a = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        float[][] q = {{x0, y0}, {x0, y1}, {x1, y1}, {x1, y0}};
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 4; i++) {
                float[] p = q[pass == 0 ? i : 3 - i];
                vc.vertex(m, p[0], p[1], z).color(r, g, b, a).uv2(LightTexture.FULL_BRIGHT).endVertex();
            }
        }
    }

    @Override
    public void render(VendorPostBlockEntity post, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (post.getLevel() == null || !post.getBlockState().hasProperty(VendorPostBlock.FACING)) {
            return;
        }
        Direction facing = post.getBlockState().getValue(VendorPostBlock.FACING);
        boolean open = post.getBlockState().getValue(VendorPostBlock.OPEN);
        float t = post.getLevel().getGameTime() + partial;
        // the lamp overlay on the front
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-yRot(facing)));
        pose.translate(-0.5, 0, -0.5);
        if (!post.assigned()) {
            if (((int) (t / 10)) % 2 == 0) {
                quad(buffers.getBuffer(RenderType.textBackground()), pose.last().pose(), 6f / 16, 6f / 16, 10f / 16, 8f / 16, -0.45f / 16, RED);
            }
        } else if (!open) {
            quad(buffers.getBuffer(RenderType.textBackground()), pose.last().pose(), 6f / 16, 6f / 16, 10f / 16, 8f / 16, -0.45f / 16, DARK);
        }
        pose.popPose();
        if (!post.assigned()) {
            return;
        }
        Entity e = figure(post);
        if (e == null) {
            return;
        }
        // face the front, head toward the nearest player within six blocks
        float body = yRot(facing) + 180;
        Player near = Minecraft.getInstance().player;
        float head = body;
        if (near != null) {
            double dx = near.getX() - (post.getBlockPos().getX() + 0.5), dz = near.getZ() - (post.getBlockPos().getZ() + 0.5);
            if (dx * dx + dz * dz < 36) {
                head = (float) (Mth.atan2(dz, dx) * (180 / Math.PI)) - 90;
                head = body + Mth.clamp(Mth.wrapDegrees(head - body), -70, 70);
            }
        }
        e.setYRot(body);
        e.yRotO = body;
        if (e instanceof LivingEntity le) {
            le.yBodyRot = body;
            le.yBodyRotO = body;
            le.yHeadRot = head;
            le.yHeadRotO = head;
            le.tickCount = (int) t;
        }
        pose.pushPose();
        pose.translate(0.5, 1.0, 0.5);
        Minecraft.getInstance().getEntityRenderDispatcher().render(e, 0, 0, 0, 0, partial, pose, buffers, light);
        pose.popPose();
    }
}
