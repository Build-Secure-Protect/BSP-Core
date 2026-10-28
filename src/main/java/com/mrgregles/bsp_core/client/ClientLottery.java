package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.api.BspBoards;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.lottery.LotteryPageView;
import com.mrgregles.bsp_core.lottery.LotterySummary;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The lottery as last sent by the server ({@link LotterySummary}), and the Lottery board on Score Screens. The Score Screen
 * draws this board itself (look B, owner's pick 2026-10-28: a drum of the names of everyone in at the left, the pot, how many
 * are in and the last three winners at the right); its lines here (the pot, how many are in, the winners) are what the Side
 * look's column shows.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = BSPCore.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class ClientLottery {
    public static final ResourceLocation BOARD = new ResourceLocation(BSPCore.MODID, "lottery");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private static volatile LotterySummary.Summary summary = LotterySummary.Summary.EMPTY;
    /** This client's clock when the summary came, for the draw's age. */
    private static volatile long receivedAt;

    private ClientLottery() {}

    public static void register() {
        BspBoards.register(BOARD, Component.translatable("screen.bsp_core.lottery.title"), ClientLottery::lines);
    }

    public static void accept(LotterySummary.Summary s) {
        summary = s;
        receivedAt = System.currentTimeMillis();
        BspBoards.invalidate(BOARD);
    }

    /** Leaving a world or server: none of its lottery shows in the next (the board, the card, the pages). */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        summary = LotterySummary.Summary.EMPTY;
        LotteryPlayerPage.reset();
        LotteryAdminPage.reset();
        BspBoards.invalidate(BOARD);
    }

    public static LotterySummary.Summary summary() {
        return summary;
    }

    /** How long ago the draw's phase began, by this client's clock since the summary came. */
    public static long phaseAge() {
        return summary.phaseMs() + (System.currentTimeMillis() - receivedAt);
    }

    public static String day(long ms) {
        return DAY.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()));
    }

    public static List<BspBoards.BoardLine> lines() {
        LotterySummary.Summary s = summary;
        List<BspBoards.BoardLine> out = new ArrayList<>();
        out.add(BspBoards.BoardLine.of(Component.translatable("screen.bsp_core.lottery.pot"), Component.literal(Bank.format(s.pot()))));
        out.add(BspBoards.BoardLine.of(Component.translatable("screen.bsp_core.lottery.in"), Component.literal(String.valueOf(s.in()))));
        for (LotteryPageView.Win w : s.wins()) {
            out.add(BspBoards.BoardLine.of(Component.literal(w.name()), Component.literal(Bank.format(w.pieces()))).withNote(Component.literal(day(w.ms()))));
        }
        return out;
    }
}
