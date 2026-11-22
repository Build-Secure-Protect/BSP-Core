package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.api.BspBoards;
import com.mrgregles.bsp_core.score.ScoreScreenBlockEntity;
import com.mrgregles.bsp_core.score.VipList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * The server's VIPs as last sent ({@link com.mrgregles.bsp_core.network.VipListPacket}), and the VIPs board on Score Screens. The
 * Score Screen draws this board itself (look B, owner's pick 2026-11-22: a banner with the admin's message, rows of heads sliding
 * sideways, the second message and a SUPPORT key); its lines here (a head and a name each) are what the Side look's column shows.
 * The board joins a rotation only where an admin ticks it.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = BSPCore.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class ClientVips {
    public static final ResourceLocation BOARD = new ResourceLocation(ScoreScreenBlockEntity.PAGE_VIPS);

    private static volatile List<VipList.Vip> vips = List.of();

    private ClientVips() {}

    public static void register() {
        BspBoards.register(BOARD, Component.translatable("screen.bsp_core.vips.title"), ClientVips::lines);
    }

    public static void accept(List<VipList.Vip> list) {
        vips = List.copyOf(list);
        BspBoards.invalidate(BOARD);
    }

    /** Leaving a world or server: none of its VIPs show in the next. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        vips = List.of();
        BspBoards.invalidate(BOARD);
    }

    /** The VIPs, sorted by name as the server sent them. */
    public static List<VipList.Vip> vips() {
        return vips;
    }

    public static List<BspBoards.BoardLine> lines() {
        List<BspBoards.BoardLine> out = new ArrayList<>();
        for (VipList.Vip v : vips) {
            out.add(BspBoards.BoardLine.of(Component.literal(v.name()), null, ClientBounties.head(v.id(), v.name())));
        }
        return out;
    }
}
