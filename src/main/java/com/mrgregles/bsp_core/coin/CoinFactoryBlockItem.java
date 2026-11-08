package com.mrgregles.bsp_core.coin;

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

/** Placing a factory is refused once the player already owns as many slices as their team's size allows ({@link FactoryCap}). */
public class CoinFactoryBlockItem extends BlockItem {
    public CoinFactoryBlockItem(Block block) {
        super(block, new Properties());
    }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        if (ctx.getPlayer() instanceof ServerPlayer player) {
            int limit = FactoryCap.cap(player.server, player.getUUID());
            if (FactoryLedger.get(player.server).count(player.getUUID()) >= limit) {
                player.displayClientMessage(limitMessage(player, limit), true);
                player.inventoryMenu.sendAllDataToRemote(); // undo the client's optimistic placement
                return InteractionResult.FAIL;
            }
        }
        InteractionResult result = super.place(ctx);
        if (result.consumesAction() && ctx.getPlayer() instanceof ServerPlayer player && com.mrgregles.bsp_core.storage.NetworkStorage.enabled()) {
            // On a network the quick check above used the count from the player's login. Ask the database again now,
            // and take the slice back if slices placed on other servers since then put the player over the limit.
            net.minecraft.core.BlockPos pos = ctx.getClickedPos();
            net.minecraft.server.level.ServerLevel level = player.serverLevel();
            int limit = FactoryCap.cap(player.server, player.getUUID());
            com.mrgregles.bsp_core.storage.NetworkStorage.countSlicesElsewhere(player.getUUID(), elsewhere -> {
                if (FactoryLedger.get(player.server).localCount(player.getUUID()) + elsewhere <= limit
                        || !(level.getBlockEntity(pos) instanceof CoinFactoryBlockEntity factory) || !player.getUUID().equals(factory.getOwner())) {
                    return;
                }
                level.removeBlock(pos, false);
                if (!player.isCreative()) {
                    ItemStack back = new ItemStack(this);
                    if (!player.getInventory().add(back)) {
                        player.drop(back, false);
                    }
                }
                player.displayClientMessage(limitMessage(player, limit), false);
            });
        }
        return result;
    }

    /** "You already own the maximum of 10 ..." alone, or "Your team of 4 may own 6 ... each" in a team. */
    static Component limitMessage(ServerPlayer player, int limit) {
        int size = FactoryCap.teamSize(player.server, player.getUUID());
        return (size > 1 ? Component.translatable("message.bsp_core.factory.team_limit", size, limit)
                : Component.translatable("message.bsp_core.factory.limit", limit)).withStyle(ChatFormatting.RED);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bsp_core.factory").withStyle(ChatFormatting.GRAY));
    }
}
