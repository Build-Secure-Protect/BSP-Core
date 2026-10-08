package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.plasma.WrenchItem;
import com.mrgregles.bsp_core.projector.PlasmaCableBlockEntity;
import com.mrgregles.bsp_core.projector.TotemCableBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** While a wrench is held and the crosshair is on a cable: which end you are pointing at and what it is set to, above the crosshair. */
public final class WrenchHud implements IGuiOverlay {
    public static final WrenchHud INSTANCE = new WrenchHud();

    private WrenchHud() {}

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        if (!(mc.player.getMainHandItem().getItem() instanceof WrenchItem) && !(mc.player.getOffhandItem().getItem() instanceof WrenchItem)) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        Component line;
        if (mc.level.getBlockEntity(pos) instanceof com.mrgregles.bsp_core.tank.TankPortBlockEntity port) {
            line = Component.translatable("message.bsp_core.tank.port", Component.translatable(port.mode().key()));
            draw(mc, g, width, height, line);
            return;
        }
        if (!(mc.level.getBlockState(pos).getBlock() instanceof TotemCableBlock) || !(mc.level.getBlockEntity(pos) instanceof PlasmaCableBlockEntity cable)) {
            return;
        }
        if (WrenchItem.coreHit(pos, hit.getLocation())) {
            line = Component.translatable("gui.bsp_core.wrench.core");
        } else {
            Direction side = WrenchItem.endHit(pos, hit.getLocation());
            line = Component.translatable("message.bsp_core.wrench.end", Component.translatable("gui.bsp_core.wrench.side." + side.getSerializedName()), Component.translatable(cable.end(side).key()));
        }
        draw(mc, g, width, height, line);
    }

    /** One small line, well above the crosshair, so it does not sit on what you are pointing at. */
    private static void draw(Minecraft mc, GuiGraphics g, int width, int height, Component line) {
        g.pose().pushPose();
        g.pose().scale(0.75f, 0.75f, 1f);
        float sx = width / 0.75f, sy = height / 0.75f;
        int w = mc.font.width(line) + 8, x = Math.round(sx / 2 - w / 2f), y = Math.round(sy / 2) - 92;
        g.fill(x, y, x + w, y + 12, 0x90101018);
        g.drawString(mc.font, line, x + 4, y + 2, 0x4FB8FF, false);
        g.pose().popPose();
    }
}
