package com.mrgregles.bsp_core.market;

import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

/**
 * What a market call came back with. {@code key} names the outcome under {@code message.bsp_core.market.} ("ok", "short",
 * "gone", "refused", ...). {@code lot} is the lot listed, bought or closed. {@code items} are, on a failed listing, the
 * stacks to hand back to the player; on a purchase, the stacks bought. {@code count} is what a collect handed over.
 */
public record MarketResult(boolean ok, String key, @Nullable MarketLot lot, List<ItemStack> items, int count) {
    public static MarketResult ok(@Nullable MarketLot lot, List<ItemStack> items) {
        return new MarketResult(true, "ok", lot, items, 0);
    }

    public static MarketResult fail(String key, List<ItemStack> giveBack) {
        return new MarketResult(false, key, null, giveBack, 0);
    }

    public static MarketResult fail(String key) {
        return fail(key, List.of());
    }
}
