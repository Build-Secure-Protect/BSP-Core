package com.mrgregles.bsp_core.link;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Fitted to an Accelerator Intake by right-clicking it: the accelerator may hold one more link. Two at most. */
public class LinkExpanderItem extends Item {
    public LinkExpanderItem() {
        super(new Item.Properties().stacksTo(16));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bsp_core.link_expander", AcceleratorBlockEntity.baseLinks(), AcceleratorBlockEntity.expanderMax()).withStyle(ChatFormatting.GRAY));
    }
}
