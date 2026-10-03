package com.mrgregles.bsp_core.coin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** An unstruck coin. Pressed into its tier's Shatter Coin by the Shatter Coin Factory. */
public class CoinBlankItem extends Item {
    public final CoinTier tier;

    public CoinBlankItem(CoinTier tier) {
        super(new Properties().stacksTo(64));
        this.tier = tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bsp_core.blank").withStyle(ChatFormatting.GRAY));
    }
}
