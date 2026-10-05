package com.mrgregles.bsp_core.zone;

import com.mrgregles.bsp_core.admin.Admins;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Only admins can place an Anti Totem block. Holding it in the main hand shows an admin every zone's outline. */
public class AntiTotemBlockItem extends BlockItem {
    public AntiTotemBlockItem(Block block) {
        super(block, new Properties().rarity(Rarity.EPIC));
    }

    @Override
    protected boolean canPlace(BlockPlaceContext ctx, BlockState state) {
        if (ctx.getPlayer() instanceof ServerPlayer player && !Admins.isAdmin(player)) {
            player.displayClientMessage(Component.translatable("message.bsp_core.admin.denied").withStyle(ChatFormatting.RED), true);
            player.inventoryMenu.sendAllDataToRemote();
            return false;
        }
        return super.canPlace(ctx, state);
    }
}
