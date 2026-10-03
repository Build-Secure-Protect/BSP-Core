package com.mrgregles.bsp_core.coin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** A Shatter Coin of one tier. Spent on placed-totem upgrades by value. */
public class ShatterCoinItem extends Item {
    public final CoinTier tier;

    public ShatterCoinItem(CoinTier tier) {
        super(new Properties().stacksTo(64).rarity(tier.ordinal() >= 3 ? Rarity.EPIC : tier.ordinal() >= 1 ? Rarity.RARE : Rarity.UNCOMMON));
        this.tier = tier;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return tier == CoinTier.ILLYRIUM;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bsp_core.coin.value", tier.value()).withStyle(ChatFormatting.GOLD));
    }
}
