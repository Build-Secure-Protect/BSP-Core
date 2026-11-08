package com.mrgregles.bsp_core.shield;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

/** Totem Radiation Coating: used on a Radiation Shielding Panel, Window or Door, it coats that block ({@link TotemCoating}). */
public class TotemCoatingItem extends Item {
    public TotemCoatingItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockState state = level.getBlockState(ctx.getClickedPos());
        Player player = ctx.getPlayer();
        if (!TotemCoating.coatable(state)) {
            if (player != null && !level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.bsp_core.coating.only_shielding").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        if (TotemCoating.coated(state)) {
            if (player != null && !level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.bsp_core.coating.already").withStyle(ChatFormatting.YELLOW), true);
            }
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide && TotemCoating.coat(level, ctx.getClickedPos())) {
            if (player == null || !player.getAbilities().instabuild) {
                ctx.getItemInHand().shrink(1);
            }
            level.levelEvent(null, LevelEvent.PARTICLES_AND_SOUND_WAX_ON, ctx.getClickedPos(), 0);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bsp_core.coating").withStyle(ChatFormatting.GRAY));
    }
}
