package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.api.BspBoards;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.market.MarketSummary;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The market as last sent by the server ({@link MarketSummary}), and the Market Board that shows it on Score Screens: one
 * line a lot, newest first, with the first item as the icon, its count and name ("+2 more" for a lot of three stacks), the
 * price, and how long ago it was listed. Ages are worked out by the server's clock.
 */
public final class ClientMarket {
    public static final ResourceLocation BOARD = new ResourceLocation(BSPCore.MODID, "market");

    private static volatile List<MarketSummary.Entry> entries = List.of();
    /** The server's clock minus this client's, when the summary came. */
    private static volatile long offset;

    private ClientMarket() {}

    public static void register() {
        BspBoards.register(BOARD, Component.translatable("screen.bsp_core.market.title"), ClientMarket::lines);
    }

    public static void accept(long serverNow, List<MarketSummary.Entry> list) {
        offset = serverNow - System.currentTimeMillis();
        entries = List.copyOf(list);
        BspBoards.invalidate(BOARD);
    }

    public static List<BspBoards.BoardLine> lines() {
        long now = System.currentTimeMillis() + offset;
        List<BspBoards.BoardLine> out = new ArrayList<>();
        for (MarketSummary.Entry e : entries) {
            ItemStack first = e.first();
            Component text = Component.literal((first.getCount() > 1 ? first.getCount() + " " : "") + first.getHoverName().getString());
            if (e.stacks() > 1) {
                text = Component.translatable("screen.bsp_core.market.more", text, e.stacks() - 1);
            }
            out.add(BspBoards.BoardLine.of(text, Component.literal(Bank.format(e.price())), first).withNote(ago(now - e.listedMs())));
        }
        return out;
    }

    static Component ago(long ms) {
        long m = Math.max(0, ms) / 60000;
        if (m < 1) {
            return Component.translatable("screen.bsp_core.market.just_now");
        }
        String n = m < 60 ? m + " m" : m < 60 * 24 ? m / 60 + " h" : m / (60 * 24) + " d";
        return Component.translatable("screen.bsp_core.market.ago", n);
    }
}
