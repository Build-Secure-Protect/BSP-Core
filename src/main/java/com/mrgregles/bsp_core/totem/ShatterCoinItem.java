package com.mrgregles.bsp_core.totem;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Currency for placed-totem upgrades. How players earn it is defined later. */
public class ShatterCoinItem extends Item {
    public ShatterCoinItem() {
        super(new Properties().stacksTo(64).rarity(Rarity.RARE));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bsp_core.shatter_coin").withStyle(ChatFormatting.GRAY));
    }
}
