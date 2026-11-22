package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.api.BspBoards;
import com.mrgregles.bsp_core.score.ScoreScreenBlock;
import com.mrgregles.bsp_core.score.ScoreScreenBlockEntity;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * A right-click on the VIPs board's SUPPORT key, from up to {@link #REACH} blocks away (a screen is often high on a wall), opens
 * Minecraft's own link prompt with the screen's address. The click is caught before it reaches the server, so an admin gets the
 * prompt there too; anywhere else on the screen a right-click does what it always did (the settings, for admins).
 */
@Mod.EventBusSubscriber(modid = BSPCore.MODID, value = Dist.CLIENT)
public final class VipLinkClick {
    public static final double REACH = 16;

    private VipLinkClick() {}

    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isUseItem() || mc.player == null || mc.level == null) {
            return;
        }
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.ENTITY) {
            return; // a vendor, an armour stand or a frame in front of the screen keeps its click
        }
        float partialTick = mc.getFrameTime();
        HitResult hit = mc.player.pick(REACH, partialTick, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult block)) {
            return;
        }
        String link = linkAt(mc, mc.level, block, partialTick);
        if (link == null) {
            return;
        }
        event.setCanceled(true); // also a second click in the same tick, which must not reach the server either
        event.setSwingHand(false);
        if (mc.screen != null) {
            return; // the prompt is already open
        }
        mc.setScreen(new ConfirmLinkScreen(open -> {
            if (open && ScoreScreenBlockEntity.validLink(link)) {
                Util.getPlatform().openUri(link);
            }
            mc.setScreen(null);
        }, link, false));
    }

    /** The address of the SUPPORT key under this hit, or null when the hit is not on one. */
    @Nullable
    static String linkAt(Minecraft mc, Level level, BlockHitResult hit, float partialTick) {
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ScoreScreenBlock)) {
            return null;
        }
        Direction facing = state.getValue(ScoreScreenBlock.FACING);
        if (hit.getDirection() != facing) {
            return null; // the back or a side of a panel
        }
        BlockPos origin = ScoreScreenBlock.origin(level, pos, facing);
        if (!(level.getBlockEntity(origin) instanceof ScoreScreenBlockEntity screen)) {
            return null;
        }
        float t = ScoreScreenRenderer.clock(level, partialTick);
        var board = BspBoards.board(ClientVips.BOARD);
        if (board.isEmpty() || !ScoreScreenRenderer.showsVips(screen, t) || !VipBoardLayout.hasLink(screen.vip())) {
            return null;
        }
        int[] wh = ScoreScreenBlock.size(level, origin, facing);
        ScoreScreenRenderer.Shown shown = ScoreScreenRenderer.shown(screen, t);
        boolean dots = screen.mode() == ScoreScreenBlockEntity.MODE_ROTATE && shown.count() > 1;
        VipBoardLayout layout = ScoreScreenRenderer.vipLayout(mc.font, screen, board.get().title(), wh[0] * ScoreScreenRenderer.PX, wh[1] * ScoreScreenRenderer.PX,
                ScoreScreenRenderer.unit(screen, wh[0], wh[1]), dots);
        float[] c = ScoreScreenBlock.canvasPoint(hit.getLocation(), origin, facing, wh[1]);
        return layout.onKey(c[0], c[1]) ? screen.vip().link() : null;
    }
}
