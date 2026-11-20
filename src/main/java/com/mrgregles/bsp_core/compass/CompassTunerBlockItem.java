package com.mrgregles.bsp_core.compass;

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

/** The Compass Tuner item: enforces {@code compassTuner.maxPerPlayer} and shows the upgrades a mined tuner carries. */
public class CompassTunerBlockItem extends BlockItem {
    public CompassTunerBlockItem(Block block) {
        super(block, new Properties());
    }

    @Override
    protected boolean canPlace(BlockPlaceContext ctx, BlockState state) {
        if (ctx.getPlayer() instanceof ServerPlayer player) {
            int max = BSPConfig.getOr(BSPConfig.TUNER_MAX, 1);
            if (TunerLedger.get(player.server).count(player.server, player.getUUID()) >= max) {
                player.displayClientMessage(Component.translatable("message.bsp_core.compass_tuner.limit", max).withStyle(ChatFormatting.RED), true);
                player.inventoryMenu.sendAllDataToRemote(); // the client already guessed the placement
                return false;
            }
        }
        return super.canPlace(ctx, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bsp_core.compass_tuner").withStyle(ChatFormatting.GRAY));
        CompoundTag tag = stack.getTagElement("BlockEntityTag");
        if (tag != null) {
            int a = tag.getInt(CompassTunerBlockEntity.ACROSS), d = tag.getInt(CompassTunerBlockEntity.DEPTH),
                    c = tag.getInt(CompassTunerBlockEntity.COOLDOWN), s = tag.getInt(CompassTunerBlockEntity.SPEED);
            if (a + d + c + s > 0) {
                tooltip.add(Component.translatable("tooltip.bsp_core.compass_tuner.levels", a, d, c, s).withStyle(ChatFormatting.AQUA));
            }
        }
    }
}
