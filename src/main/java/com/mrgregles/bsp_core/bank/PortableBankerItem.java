package com.mrgregles.bsp_core.bank;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Portable Banker: the bank's screens anywhere (balance, paying players, the log). Coins are
 * converted and withdrawn at a Shatter Bank only. Its Shopping Network key reaches the shops marked
 * remote once the player has unlocked it with coins; the unlock lives on the account, not the item.
 */
public class PortableBankerItem extends Item {
    public PortableBankerItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) {
            BankAtmBlock.open(sp, null);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        for (int i = 0; i < 3; i++) {
            tooltip.add(Component.translatable("item.bsp_core.portable_banker.tip." + i).withStyle(ChatFormatting.GRAY));
        }
    }
}
