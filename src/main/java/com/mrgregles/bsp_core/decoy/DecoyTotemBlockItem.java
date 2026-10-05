package com.mrgregles.bsp_core.decoy;

import com.mrgregles.bsp_core.BSPConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

/** The Decoy Totem item: a player may have {@code decoy.maxPerPlayer} placed at a time. */
public class DecoyTotemBlockItem extends BlockItem {
    public DecoyTotemBlockItem(Block block) {
        super(block, new Properties());
    }

    @Override
    protected boolean canPlace(BlockPlaceContext ctx, BlockState state) {
        if (ctx.getPlayer() instanceof ServerPlayer player) {
            int max = BSPConfig.DECOY_MAX.get();
            if (DecoyLedger.get(player.server).count(player.getUUID()) >= max) {
                player.displayClientMessage(Component.translatable("message.bsp_core.decoy.limit", max).withStyle(ChatFormatting.RED), true);
                player.inventoryMenu.sendAllDataToRemote();
                return false;
            }
        }
        return super.canPlace(ctx, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bsp_core.decoy_totem").withStyle(ChatFormatting.GRAY));
    }
}
