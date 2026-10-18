package com.mrgregles.bsp_core.link;

import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Link Card: blank until right-clicked on an Accelerator Intake, which writes that accelerator's world and position into it.
 * Right-clicked on a Decelerator Core the written card goes into the core and links it to that accelerator; it comes back out
 * when the link is undone or the core is broken.
 */
public class LinkCardItem extends Item {
    public static final String TAG = "Link";

    public LinkCardItem() {
        super(new Item.Properties().stacksTo(16));
    }

    public static boolean isWritten(ItemStack stack) {
        return stack.getItem() instanceof LinkCardItem && stack.hasTag() && stack.getTag().contains(TAG);
    }

    @Nullable
    public static GlobalPos target(ItemStack stack) {
        return isWritten(stack) ? Links.load(stack.getTag().getCompound(TAG), "At") : null;
    }

    public static void write(ItemStack stack, GlobalPos at) {
        CompoundTag t = new CompoundTag();
        Links.save(t, "At", at);
        stack.getOrCreateTag().put(TAG, t);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isWritten(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        GlobalPos at = target(stack);
        if (at == null) {
            tooltip.add(Component.translatable("tooltip.bsp_core.link_card.blank").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.bsp_core.link_card.written", Links.describe(at)).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.bsp_core.link_card.use").withStyle(ChatFormatting.GRAY));
        }
    }
}
