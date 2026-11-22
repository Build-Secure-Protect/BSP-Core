package com.mrgregles.bsp_core.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mrgregles.bsp_core.demo.DemoTotemBlock;
import com.mrgregles.bsp_core.demo.DemoTotemBlockEntity;
import com.mrgregles.bsp_core.looks.TotemLook;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.totem.ShatterTotemBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/**
 * Draws a Demo Totem's look. The chunk bakes the Shatter Totem's own model (the gold body when plain, the bare plinth, studs and orb
 * while dressed), so this adds only what a dressed real totem's renderer adds: the body and head in the look, the gold band and the
 * hat. No orbs, no lit studs, no auras and no leader glow: a demo does nothing a totem does.
 */
public class DemoTotemRenderer implements BlockEntityRenderer<DemoTotemBlockEntity> {
    public DemoTotemRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(DemoTotemBlockEntity demo, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        var level = demo.getLevel();
        BlockState state = demo.getBlockState();
        if (level == null || !state.hasProperty(DemoTotemBlock.LOOKS)) {
            return;
        }
        if (!state.getValue(DemoTotemBlock.LOOKS)) {
            // plain: the chunk bakes the gold body; only a hat on it is drawn here (a look that arrived before its state waits for it)
            String hat = demo.getPlainHat();
            if (!hat.isEmpty()) {
                int light = Math.max(packedLight, LevelRenderer.getLightColor(level, demo.getBlockPos().above()));
                TotemDress.draw(pose, buffers, null, hat, state.getValue(DemoTotemBlock.FACING), light, AnimTime.ticks(partialTick), null);
            }
            return;
        }
        TotemLook look = demo.getLook();
        if (look == null) {
            // the block state says "look" before the look itself has arrived (or after it went): draw the baked body meanwhile
            BlockState plain = ModBlocks.SHATTER_TOTEM.get().defaultBlockState()
                    .setValue(ShatterTotemBlock.FACING, state.getValue(DemoTotemBlock.FACING)).setValue(ShatterTotemBlock.GLOW, ShatterTotemBlock.Glow.OWNED);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(plain, pose, buffers, packedLight, packedOverlay, ModelData.EMPTY, null);
            return;
        }
        int light = Math.max(packedLight, LevelRenderer.getLightColor(level, demo.getBlockPos().above()));
        TotemDress.draw(pose, buffers, look, look.hat(), state.getValue(DemoTotemBlock.FACING), light, AnimTime.ticks(partialTick),
                demo.skinOwner()); // null without the dresser's name: the totem's own face
    }

    /** As far as a real totem's look is drawn. */
    @Override
    public int getViewDistance() {
        return 64;
    }
}
