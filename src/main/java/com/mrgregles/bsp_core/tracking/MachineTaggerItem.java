package com.mrgregles.bsp_core.tracking;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The Machine Tagger: right-click a machine to tag it for the Holo Grid, sneak-right-click a tagged machine to untag it, right-click the
 * air for the tagger's screen (its list of tags, slots and the Remote Control unlock). The tagging runs in {@link #onItemUseFirst}, before the
 * machine's own right-click, so tagging a furnace does not also open it. The tag slots belong to the player, not to the item.
 */
public class MachineTaggerItem extends Item {
    public MachineTaggerItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        Level level = ctx.getLevel();
        if (level.getBlockEntity(ctx.getClickedPos()) == null) {
            return InteractionResult.PASS; // not a machine: the right-click goes on as usual (the air screen opens from use())
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl) {
            if (sp.isSecondaryUseActive()) {
                untag(sp, sl, ctx);
            } else {
                tag(sp, sl, ctx);
            }
        }
        return InteractionResult.CONSUME;
    }

    private static void tag(ServerPlayer player, ServerLevel level, UseOnContext ctx) {
        TrackerRules.TagResult result = TrackerRules.tag(player, level, ctx.getClickedPos());
        TrackerRecord r = TrackerOwners.get(player.server).record(player.server, player.getUUID());
        int season = TrackerOwners.season(player.server);
        if (result.refusal() != null) {
            net.minecraft.network.chat.MutableComponent msg;
            if (result.refusal().equals("slots_full")) {
                int price = TrackerRules.nextPrice(r, season);
                msg = price < 0 ? Component.translatable("message.bsp_core.tracking.slots_full_max") : Component.translatable("message.bsp_core.tracking.slots_full", price);
            } else {
                msg = Component.translatable("message.bsp_core.tracking." + result.refusal());
            }
            player.displayClientMessage(msg.withStyle(ChatFormatting.RED), true);
            return;
        }
        player.displayClientMessage(Component.translatable("message.bsp_core.tracking.tagged", TrackerRules.displayName(result.tag(), level), r.tags.size(),
                TrackerRules.slots(r, season)).withStyle(ChatFormatting.GREEN), true);
    }

    private static void untag(ServerPlayer player, ServerLevel level, UseOnContext ctx) {
        TrackerRecord r = TrackerOwners.get(player.server).record(player.server, player.getUUID());
        String here = TrackerRules.serverId();
        var tag = r.tags.stream().filter(t -> t.at(here, level.dimension().location(), ctx.getClickedPos())).findFirst();
        if (tag.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.bsp_core.tracking.not_tagged").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        TrackerRules.untag(player, tag.get().id());
        player.displayClientMessage(Component.translatable("message.bsp_core.tracking.untagged", TrackerRules.displayName(tag.get(), level)).withStyle(ChatFormatting.YELLOW), true);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            TrackerScreens.openTagger(sp);
        } else if (level.isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.mrgregles.bsp_core.client.tracking.TaggerScreen.open());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tips, TooltipFlag flag) {
        tips.add(Component.translatable("tooltip.bsp_core.machine_tagger.use").withStyle(ChatFormatting.GRAY));
        tips.add(Component.translatable("tooltip.bsp_core.machine_tagger.untag").withStyle(ChatFormatting.GRAY));
        tips.add(Component.translatable("tooltip.bsp_core.machine_tagger.list").withStyle(ChatFormatting.GRAY));
        tips.add(Component.translatable("tooltip.bsp_core.machine_tagger.slots").withStyle(ChatFormatting.DARK_GRAY));
    }
}
