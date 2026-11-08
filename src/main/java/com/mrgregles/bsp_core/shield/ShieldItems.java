package com.mrgregles.bsp_core.shield;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;

/** The shielding set's items: as vanilla's, with a tooltip line when the item carries the coating. */
public final class ShieldItems {
    private ShieldItems() {}

    private static void coatedLine(ItemStack stack, List<Component> tooltip) {
        if (TotemCoating.coated(stack)) {
            tooltip.add(Component.translatable("tooltip.bsp_core.coating.coated").withStyle(ChatFormatting.AQUA));
        }
    }

    public static Item block(Block block) {
        return new BlockItem(block, new Item.Properties()) {
            @Override
            public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
                coatedLine(stack, tooltip);
            }
        };
    }

    public static Item door(Block block) {
        return new DoubleHighBlockItem(block, new Item.Properties()) {
            @Override
            public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
                coatedLine(stack, tooltip);
            }
        };
    }
}
