package com.mrgregles.bsp_core.vault;

import com.mrgregles.bsp_core.BSPConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
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

/** The Coin Vault item: enforces {@code vault.maxBlocksPerPlayer} and shows the upgrades a moved vault carries. */
public class CoinVaultBlockItem extends BlockItem {
    public CoinVaultBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean canPlace(BlockPlaceContext ctx, BlockState state) {
        if (ctx.getPlayer() instanceof ServerPlayer player) {
            int max = BSPConfig.VAULT_MAX_BLOCKS.get();
            // on a network the limit covers every server; the other servers' count is as last read from the database
            if (VaultLedger.get(player.server).account(player.getUUID()).vaultCount()
                    + com.mrgregles.bsp_core.storage.NetworkStorage.vaultBlocksElsewhere(player.getUUID()) >= max) {
                player.displayClientMessage(Component.translatable("message.bsp_core.vault.limit", max).withStyle(ChatFormatting.RED), true);
                player.inventoryMenu.sendAllDataToRemote(); // the client already guessed the placement
                return false;
            }
        }
        return super.canPlace(ctx, state);
    }

    /**
     * On a network the quick check in {@link #canPlace} used the other servers' count as last read.
     * Once the block is down, the database is told and asked again, and the block is taken back if
     * vault blocks placed on other servers in the meantime put the player over the limit.
     */
    @Override
    public net.minecraft.world.InteractionResult place(BlockPlaceContext ctx) {
        ItemStack one = ctx.getItemInHand().copyWithCount(1);
        net.minecraft.world.InteractionResult result = super.place(ctx);
        if (result.consumesAction() && ctx.getPlayer() instanceof ServerPlayer player && com.mrgregles.bsp_core.storage.NetworkStorage.enabled()) {
            net.minecraft.core.BlockPos pos = ctx.getClickedPos();
            net.minecraft.server.level.ServerLevel level = player.serverLevel();
            int max = BSPConfig.VAULT_MAX_BLOCKS.get();
            VaultLedger ledger = VaultLedger.get(player.server);
            com.mrgregles.bsp_core.storage.NetworkStorage.putVaultRow(player.getUUID(), ledger.row(player.getUUID()), elsewhere -> {
                if (ledger.account(player.getUUID()).vaultCount() + elsewhere <= max
                        || !(level.getBlockEntity(pos) instanceof CoinVaultBlockEntity vault) || !vault.isOwner(player)) {
                    return;
                }
                vault.brokenBy(player); // taken back as the owner's own block: nothing is "recovered" and nobody is warned
                level.removeBlock(pos, false);
                if (!player.isCreative() && !player.getInventory().add(one)) {
                    player.drop(one, false);
                }
                player.displayClientMessage(Component.translatable("message.bsp_core.vault.limit", max).withStyle(ChatFormatting.RED), false);
            });
        }
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTagElement("BlockEntityTag");
        if (tag != null && (tag.getInt("Lock") > 0 || tag.getInt("Alarm") > 0)) {
            tooltip.add(Component.translatable("tooltip.bsp_core.vault.upgrades", tag.getInt("Lock"),
                    Component.translatable(tag.getInt("Alarm") > 0 ? "tooltip.bsp_core.vault.alarm_yes" : "tooltip.bsp_core.vault.alarm_no")).withStyle(ChatFormatting.GRAY));
        }
    }
}
