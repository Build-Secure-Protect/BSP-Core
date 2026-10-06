package com.mrgregles.bsp_core.plasma;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Fitted to a Projector by right-clicking it: the projector then receives one more power at once. */
public class ChannelExpanderItem extends Item {
    public ChannelExpanderItem() {
        super(new Item.Properties().stacksTo(16));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bsp_core.channel_expander").withStyle(ChatFormatting.GRAY));
    }
}
