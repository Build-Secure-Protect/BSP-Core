package com.mrgregles.bsp_core.coin;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.data.FactoryLedger;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;

/** Placing a factory is refused once the player already owns {@code factory.maxPerPlayer}. */
public class CoinFactoryBlockItem extends BlockItem {
    public CoinFactoryBlockItem(Block block) {
        super(block, new Properties());
    }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        if (ctx.getPlayer() instanceof ServerPlayer player) {
            int limit = BSPConfig.FACTORY_MAX_PER_PLAYER.get();
            if (FactoryLedger.get(player.server).count(player.getUUID()) >= limit) {
                player.displayClientMessage(Component.translatable("message.bsp_core.factory.limit", limit).withStyle(ChatFormatting.RED), true);
                player.inventoryMenu.sendAllDataToRemote(); // undo the client's optimistic placement
                return InteractionResult.FAIL;
            }
        }
        return super.place(ctx);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bsp_core.factory").withStyle(ChatFormatting.GRAY));
    }
}
