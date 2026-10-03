package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.coin.CoinFactoryBlock;
import com.mrgregles.bsp_core.coin.CoinFactoryBlockEntity;
import com.mrgregles.bsp_core.coin.CoinTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Animates the Shatter Coin Factory: the press head rises and slams onto the die while a coin is
 * being pressed, the blank on the die turns into the coin at each strike, and fitted Speed Gears
 * light their sockets on the front.
 */
public class CoinFactoryRenderer implements BlockEntityRenderer<CoinFactoryBlockEntity> {
    public static final ResourceLocation HEAD_MODEL = new ResourceLocation(BSPCore.MODID, "block/shatter_coin_factory_head");
    private static final ResourceLocation ATLAS_TEX = new ResourceLocation(BSPCore.MODID, "block/shatter_coin_factory");
    private static final int[] MARK_COLORS = {0x9AA1AE, 0xF2C12E, 0x19D3B0};
    private static final float PX = 1f / 16f;

    public CoinFactoryRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(CoinFactoryBlockEntity factory, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        long gameTime = factory.getLevel() == null ? 0 : factory.getLevel().getGameTime();
        float time = gameTime + partialTick;
        boolean working = factory.isWorking();

        // one press cycle: wait, drop, hold, lift. More gears = quicker cycle.
        float cycle = 80f / (1f + factory.fittedUpgrades() * 0.4f);
        float p = working ? (time % cycle) / cycle : 0f;
        float down = p < 0.5f ? 0f : p < 0.6f ? (p - 0.5f) / 0.1f : p < 0.7f ? 1f : p < 0.85f ? 1f - (p - 0.7f) / 0.15f : 0f;
        float headY = (1.5f - down * 2.4f) * PX;

        Direction facing = factory.getBlockState().hasProperty(CoinFactoryBlock.FACING)
                ? factory.getBlockState().getValue(CoinFactoryBlock.FACING) : Direction.NORTH;
        float yRot = switch (facing) {
            case EAST -> -90f;
            case SOUTH -> 180f;
            case WEST -> 90f;
            default -> 0f;
        };

        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(yRot));
        pose.translate(-0.5, 0, -0.5);

        // --- moving press head (a separate baked model)
        BakedModel head = mc.getModelManager().getModel(HEAD_MODEL);
        pose.pushPose();
        pose.translate(0, headY, 0);
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), null, head,
                1f, 1f, 1f, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.cutout());
        pose.popPose();

        // --- blank / coin lying on the die
        CoinTier tier = factory.getJobTier();
        if (tier != null) {
            ItemStack shown = new ItemStack(p >= 0.6f ? tier.coin() : tier.blank());
            pose.pushPose();
            pose.translate(0.5, 7.2f * PX, 0.5);
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.scale(0.34f, 0.34f, 0.34f);
            mc.getItemRenderer().renderStatic(shown, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, factory.getLevel(), 0);
            pose.popPose();
        }

        // --- lit sockets for fitted gears (front face)
        TextureAtlasSprite sprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ATLAS_TEX);
        VertexConsumer vc = buffers.getBuffer(RenderType.cutout());
        for (int i = 0; i < CoinFactoryBlockEntity.UPGRADE_SLOTS; i++) {
            int mark = factory.upgradeMark(i);
            if (mark <= 0) continue;
            float x0 = (2.4f + 3 * i) * PX, x1 = (4.6f + 3 * i) * PX;
            litBox(vc, pose, sprite, x0, 0.9f * PX, -0.5f * PX, x1, 3.1f * PX, 0.45f * PX, MARK_COLORS[Math.min(mark, 3) - 1]);
        }
        pose.popPose();
    }

    /** Full-bright tinted box using the atlas's white tile. */
    private static void litBox(VertexConsumer vc, PoseStack pose, TextureAtlasSprite sprite,
                               float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        float u = sprite.getU(4.5f + 1.5f), v = sprite.getV(4.5f + 1.5f); // inside the white tile (px 16..32 of 64 -> units 4..8)
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        float[][] quads = {
                {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, 0, 0, -1},
                {x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, 0, 0, 1},
                {x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, -1, 0, 0},
                {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1, 0, 0},
                {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0},
                {x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0, -1, 0},
        };
        for (float[] q : quads) {
            for (int k = 0; k < 4; k++) {
                vc.vertex(m, q[k * 3], q[k * 3 + 1], q[k * 3 + 2]).color(r, g, b, 255).uv(u, v)
                        .uv2(LightTexture.FULL_BRIGHT).normal(nm, q[12], q[13], q[14]).endVertex();
            }
        }
    }
}
