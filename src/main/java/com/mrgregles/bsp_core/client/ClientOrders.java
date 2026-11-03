package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.api.BspBoards;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.market.OrderSummary;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * The buy orders as last sent by the server ({@link OrderSummary}), and the Wanted board that shows them on Score Screens:
 * one line an order, best pay first, with its item as the icon and name, the price each, and how many it still wants of
 * how many. It sits beside the Market board in a screen's Board row, so one screen can show what is for sale and another
 * what people want.
 */
public final class ClientOrders {
    public static final ResourceLocation BOARD = new ResourceLocation(BSPCore.MODID, "wanted");

    private static volatile List<OrderSummary.Entry> entries = List.of();

    private ClientOrders() {}

    public static void register() {
        BspBoards.register(BOARD, Component.translatable("screen.bsp_core.wanted.title"), ClientOrders::lines);
    }

    public static void accept(List<OrderSummary.Entry> list) {
        entries = List.copyOf(list);
        BspBoards.invalidate(BOARD);
    }

    public static List<BspBoards.BoardLine> lines() {
        List<BspBoards.BoardLine> out = new ArrayList<>();
        for (OrderSummary.Entry e : entries) {
            out.add(BspBoards.BoardLine.of(e.item().getHoverName(), Component.translatable("screen.bsp_core.wanted.each", Bank.format(e.each())), e.item())
                    .withNote(Component.translatable("screen.bsp_core.wanted.to_go", e.toGo(), e.wanted())));
        }
        return out;
    }
}
