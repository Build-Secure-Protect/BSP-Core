package com.mrgregles.bsp_core.demo;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Demo Totem item: an ordinary block item, so none of the totem's carrying rules apply (it may be stored, dropped anywhere and
 * kept through a logout). Only admins may place it; anyone else holding one (dropped by an admin, say) is refused. Stacks of 64;
 * demos with different looks do not stack.
 */
public class DemoTotemBlockItem extends BlockItem {
    public DemoTotemBlockItem(Block block) {
        super(block, new Properties().stacksTo(64).fireResistant().rarity(Rarity.EPIC));
    }

    @Override
    protected boolean canPlace(BlockPlaceContext ctx, BlockState state) {
        if (!DemoTotems.isAdmin(ctx.getPlayer())) {
            if (ctx.getPlayer() instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.bsp_core.demo.place_admins").withStyle(ChatFormatting.RED), true);
                player.inventoryMenu.sendAllDataToRemote();
            }
            return false;
        }
        return super.canPlace(ctx, state);
    }

    /** Shines like a real totem's item. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bsp_core.demo_totem").withStyle(ChatFormatting.GRAY));
    }
}
