package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.shield.LeadLinedChestBlock;
import com.mrgregles.bsp_core.shield.LeadLinedChestBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * Draws what moves on a Lead-Lined Chest; the body is the block model. The Rail Frame lid (its own baked model, one for a single chest
 * and one for each half of a double) swings open on its back edge as a vanilla chest's lid does; the light line inside its rail glows
 * green while the chest is shut and amber while anyone has it open; the gauge's needle trembles on its dial. Light is taken from the
 * air above and in front, since the chest's own block reads dark, channel by channel.
 *
 * <p>Everything goes into one buffer, {@link Sheets#cutoutBlockSheet()}, which is one of the level renderer's fixed buffers: the light
 * line and the needle are a white sprite tinted, so a room of chests is not a draw call per chest. They stand a quarter pixel clear of
 * the surfaces under them, so they do not flicker against them from afar.
 */
public class LeadLinedChestRenderer implements BlockEntityRenderer<LeadLinedChestBlockEntity> {
    public static final ResourceLocation LID = new ResourceLocation(BSPCore.MODID, "block/lead_lined_chest_lid"),
            LID_LEFT = new ResourceLocation(BSPCore.MODID, "block/lead_lined_chest_lid_left"),
            LID_RIGHT = new ResourceLocation(BSPCore.MODID, "block/lead_lined_chest_lid_right");
    private static final int GREEN = 0xFF58F29A, AMBER = 0xFFF2B544, NEEDLE = 0xFFC0392B;
    private static final ResourceLocation WHITE = new ResourceLocation(BSPCore.MODID, "block/lead_chest_white");
    /** The gauge's centre in model pixels (tools/gen_lead_assets.py GAUGE), for a single chest and the LEFT half of a double. */
    private static final float GAUGE_SINGLE_X = 4.0f, GAUGE_LEFT_X = 2 + 14 / 6f, GAUGE_Y = 7.6f;

    public LeadLinedChestRenderer(BlockEntityRendererProvider.Context ctx) {}

    private static float yRot(Direction facing) {
        return switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    /** One tinted quad of the white sprite, corners in blocks, both windings, in the entity format of the cutout sheet. */
    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float[][] q, TextureAtlasSprite white, int argb, int light, float nx, float ny, float nz) {
        int a = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        float u = white.getU(8), v = white.getV(8);
        for (int pass = 0; pass < 2; pass++) {
            float k = pass == 0 ? 1 : -1;
            for (int i = 0; i < 4; i++) {
                float[] p = q[pass == 0 ? i : 3 - i];
                vc.vertex(pose.pose(), p[0], p[1], p[2]).color(r, g, b, a).uv(u, v).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                        .uv2(light).normal(pose.normal(), nx * k, ny * k, nz * k).endVertex();
            }
        }
    }

    /** A flat quad lying at height {@code y} (model pixels). */
    private static void flat(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite white, float x0, float z0, float x1, float z1, float y, int argb, int light) {
        quad(vc, pose, new float[][] {{x0 / 16, y / 16, z0 / 16}, {x0 / 16, y / 16, z1 / 16}, {x1 / 16, y / 16, z1 / 16}, {x1 / 16, y / 16, z0 / 16}}, white, argb, light, 0, 1, 0);
    }

    /** The brighter of two packed lights, block light and sky light each on its own. */
    private static int brighter(int a, int b) {
        return LightTexture.pack(Math.max(LightTexture.block(a), LightTexture.block(b)), Math.max(LightTexture.sky(a), LightTexture.sky(b)));
    }

    @Override
    public void render(LeadLinedChestBlockEntity chest, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = chest.getBlockState();
        if (chest.getLevel() == null || !state.hasProperty(LeadLinedChestBlock.TYPE)) {
            return;
        }
        Direction facing = state.getValue(LeadLinedChestBlock.FACING);
        ChestType type = state.getValue(LeadLinedChestBlock.TYPE);
        BlockPos p = chest.getBlockPos();
        int lit = brighter(LevelRenderer.getLightColor(chest.getLevel(), p.above()), LevelRenderer.getLightColor(chest.getLevel(), p.relative(facing)));
        VertexConsumer vc = buffers.getBuffer(Sheets.cutoutBlockSheet());
        TextureAtlasSprite white = Minecraft.getInstance().getModelManager().getAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).getSprite(WHITE);
        boolean westOut = type != ChestType.RIGHT, eastOut = type != ChestType.LEFT;

        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-yRot(facing)));
        pose.translate(-0.5, 0, -0.5);

        // the gauge's needle, in front of the dial, trembling a little about a steady reading
        if (type != ChestType.RIGHT) {
            float gx = type == ChestType.SINGLE ? GAUGE_SINGLE_X : GAUGE_LEFT_X;
            float t = (chest.getLevel().getGameTime() + partial) / 20f + p.asLong() % 7;
            float angle = -0.6f + (float) Math.sin(t * 0.7) * 0.15f + (float) Math.sin(t * 5.3) * 0.02f;
            pose.pushPose();
            pose.translate(gx / 16, GAUGE_Y / 16, -0.05 / 16); // a quarter pixel in front of the dial's face (z 0.2)
            pose.mulPose(Axis.ZP.rotation(angle));
            quad(vc, pose.last(), new float[][] {{-0.12f / 16, 0, 0}, {-0.12f / 16, 1.15f / 16, 0}, {0.12f / 16, 1.15f / 16, 0}, {0.12f / 16, 0, 0}},
                    white, NEEDLE, lit, 0, 0, -1);
            pose.popPose();
        }

        // the lid, hinged on its back edge (y 10, z 15 in model pixels)
        float open = chest.openness(partial);
        open = 1 - open;
        open = 1 - open * open * open;
        pose.translate(0, 10 / 16f, 15 / 16f);
        pose.mulPose(Axis.XP.rotation(open * (float) Math.PI / 2));
        pose.translate(0, -10 / 16f, -15 / 16f);
        BakedModel lid = Minecraft.getInstance().getModelManager().getModel(type == ChestType.SINGLE ? LID : type == ChestType.LEFT ? LID_LEFT : LID_RIGHT);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(), vc, state, lid,
                1f, 1f, 1f, lit, overlay, ModelData.EMPTY, null);

        // the light line inside the rail: round the pair on a double chest, so a half leaves out its joining side
        int colour = chest.openCount() > 0 || open > 0.01f ? AMBER : GREEN;
        float y = 14.25f, lx0 = westOut ? 4.4f : 0, lx1 = eastOut ? 11.6f : 16; // a quarter pixel above the lid's top (14)
        PoseStack.Pose at = pose.last();
        flat(vc, at, white, lx0, 2.7f, lx1, 3.0f, y, colour, LightTexture.FULL_BRIGHT);
        flat(vc, at, white, lx0, 13.0f, lx1, 13.3f, y, colour, LightTexture.FULL_BRIGHT);
        if (westOut) {
            flat(vc, at, white, 2.7f, 4.4f, 3.0f, 11.6f, y, colour, LightTexture.FULL_BRIGHT);
        }
        if (eastOut) {
            flat(vc, at, white, 13.0f, 4.4f, 13.3f, 11.6f, y, colour, LightTexture.FULL_BRIGHT);
        }
        pose.popPose();
    }
}
