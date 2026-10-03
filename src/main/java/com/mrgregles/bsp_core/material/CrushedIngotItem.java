package com.mrgregles.bsp_core.material;

import com.mrgregles.bsp_core.BSPConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

/**
 * What crushing an ingot with a pickaxe on a crafting table produces. It is never kept: the moment
 * it is in a player's inventory each one resolves, by chance, into dust or into a few nuggets.
 * Rolling here rather than in the crafting grid means the result cannot be previewed and re-rolled.
 */
public class CrushedIngotItem extends Item {
    private final boolean tetrium;
    private final Supplier<Item> dust;
    private final Supplier<Item> nugget;

    public CrushedIngotItem(boolean tetrium, Supplier<Item> dust, Supplier<Item> nugget) {
        super(new Properties());
        this.tetrium = tetrium;
        this.dust = dust;
        this.nugget = nugget;
    }

    private double chance() {
        return tetrium ? BSPConfig.CRUSH_TETRIUM_CHANCE.get() : BSPConfig.CRUSH_DIRTY_CHANCE.get();
    }

    private int nuggetsOnFail() {
        return tetrium ? BSPConfig.CRUSH_TETRIUM_NUGGETS.get() : BSPConfig.CRUSH_DIRTY_NUGGETS.get();
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player) || stack.isEmpty()) {
            return;
        }
        int dusts = 0, nuggets = 0;
        for (int i = 0; i < stack.getCount(); i++) {
            if (level.random.nextDouble() < chance()) {
                dusts++;
            } else {
                nuggets += nuggetsOnFail();
            }
        }
        stack.setCount(0);
        give(player, new ItemStack(dust.get(), dusts));
        give(player, new ItemStack(nugget.get(), nuggets));
        level.playSound(null, player.blockPosition(), dusts > 0 ? SoundEvents.GRINDSTONE_USE : SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 0.6F, 1.2F);
        player.displayClientMessage(Component.translatable(dusts > 0 ? "message.bsp_core.crush.dust" : "message.bsp_core.crush.nuggets", dusts, nuggets)
                .withStyle(dusts > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY), true);
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        long pct = Math.round(BSPConfig.getOr(tetrium ? BSPConfig.CRUSH_TETRIUM_CHANCE : BSPConfig.CRUSH_DIRTY_CHANCE, 0.0) * 100);
        tooltip.add(Component.translatable("tooltip.bsp_core.crushed", pct).withStyle(ChatFormatting.GRAY));
    }
}
