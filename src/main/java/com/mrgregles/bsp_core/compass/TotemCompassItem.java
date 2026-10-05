package com.mrgregles.bsp_core.compass;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.data.TotemLedger;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Points to the holder's nearest own placed totem. Loaded with Shatter Coins and started from its
 * screen, it instead tracks the nearest totem belonging to someone else until the loaded time runs
 * out, then goes on cooldown. All state lives in the stack's NBT; the server writes the target
 * position once a second and the client's needle reads it.
 */
public class TotemCompassItem extends Item {
    public static final String STORED = "Stored", TRACK_UNTIL = "TrackUntil", COOLDOWN_UNTIL = "CooldownUntil", LEVEL = "CooldownLevel",
            TARGET = "TargetPos", TARGET_DIM = "TargetDim";

    public TotemCompassItem() {
        super(new Properties().stacksTo(1).rarity(Rarity.EPIC));
    }

    // ------------------------------------------------------------------ state helpers

    public static int stored(ItemStack s) {
        return s.hasTag() ? s.getTag().getInt(STORED) : 0;
    }

    public static int trackingLeft(ItemStack s, long now) {
        return s.hasTag() ? (int) Math.max(0, (s.getTag().getLong(TRACK_UNTIL) - now + 19) / 20) : 0;
    }

    public static int cooldownLeft(ItemStack s, long now) {
        return s.hasTag() ? (int) Math.max(0, (s.getTag().getLong(COOLDOWN_UNTIL) - now + 19) / 20) : 0;
    }

    public static int level(ItemStack s) {
        return s.hasTag() ? s.getTag().getInt(LEVEL) : 0;
    }

    public static int maxLevel() {
        int span = BSPConfig.COMPASS_COOLDOWN.get() - BSPConfig.COMPASS_MIN_COOLDOWN.get();
        int steps = (int) Math.ceil(Math.max(0, span) / (double) BSPConfig.COMPASS_COOLDOWN_STEP.get());
        return Math.min(steps, BSPConfig.COMPASS_UPGRADE_COSTS.get().size());
    }

    public static int cooldownSeconds(ItemStack s) {
        return Math.max(BSPConfig.COMPASS_MIN_COOLDOWN.get(), BSPConfig.COMPASS_COOLDOWN.get() - level(s) * BSPConfig.COMPASS_COOLDOWN_STEP.get());
    }

    /** How many coins the next cooldown upgrade costs, or -1 if fully upgraded. The coin is {@link #upgradeCoin(int)}. */
    public static int upgradeCost(ItemStack s) {
        int lvl = level(s);
        return lvl >= maxLevel() ? -1 : BSPConfig.COMPASS_UPGRADE_COSTS.get().get(lvl);
    }

    /** The Shatter Coin that pays for the upgrade from {@code level} to the next. */
    public static CoinTier upgradeCoin(int level) {
        String key = BSPConfig.levelValue(BSPConfig.getOr(BSPConfig.COMPASS_UPGRADE_TIERS, java.util.List.<String>of()), level + 1, "copper");
        for (CoinTier tier : CoinTier.values()) {
            if (tier.key.equals(key)) {
                return tier;
            }
        }
        return CoinTier.COPPER;
    }

    // ------------------------------------------------------------------ actions (server)

    /** Takes one coin of {@code tier} from the player and adds its seconds. */
    public static void loadCoin(ServerPlayer player, ItemStack compass, CoinTier tier) {
        if (player.getInventory().countItem(tier.coin()) <= 0) {
            return;
        }
        player.getInventory().clearOrCountMatchingItems(st -> st.is(tier.coin()), 1, player.inventoryMenu.getCraftSlots());
        int add = BSPConfig.levelValue(BSPConfig.COMPASS_SECONDS_PER_COIN.get(), tier.ordinal() + 1, 1);
        compass.getOrCreateTag().putInt(STORED, stored(compass) + add);
    }

    public static void startTracking(ServerPlayer player, ItemStack compass) {
        long now = player.level().getGameTime();
        int seconds = stored(compass);
        if (seconds <= 0 || trackingLeft(compass, now) > 0 || cooldownLeft(compass, now) > 0) {
            return;
        }
        CompoundTag tag = compass.getOrCreateTag();
        tag.putInt(STORED, 0);
        tag.putLong(TRACK_UNTIL, now + seconds * 20L);
        // the cooldown runs from the moment tracking ends
        tag.putLong(COOLDOWN_UNTIL, now + seconds * 20L + cooldownSeconds(compass) * 20L);
    }

    public static void buyUpgrade(ServerPlayer player, ItemStack compass) {
        int cost = upgradeCost(compass);
        if (cost < 0) {
            return;
        }
        if (!player.isCreative()) {
            net.minecraft.world.item.Item coin = upgradeCoin(level(compass)).coin();
            if (player.getInventory().countItem(coin) < cost) {
                return;
            }
            player.getInventory().clearOrCountMatchingItems(st -> st.is(coin), cost, player.inventoryMenu.getCraftSlots());
        }
        compass.getOrCreateTag().putInt(LEVEL, level(compass) + 1);
    }

    // ------------------------------------------------------------------ targeting

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || level.getGameTime() % 20 != 0 || !(entity instanceof ServerPlayer player)) {
            return;
        }
        boolean rivals = trackingLeft(stack, level.getGameTime()) > 0;
        BlockPos best = nearest(player, rivals);
        if (rivals) {
            // a working Decoy Totem within its range takes over the needle
            BlockPos decoy = com.mrgregles.bsp_core.decoy.DecoyLedger.get(player.server).fool(level.dimension(), player.blockPosition(), player.getUUID());
            if (decoy != null) {
                best = decoy;
            }
        }
        CompoundTag tag = stack.getOrCreateTag();
        if (best == null) {
            tag.remove(TARGET);
            tag.remove(TARGET_DIM);
        } else {
            tag.put(TARGET, NbtUtils.writeBlockPos(best));
            tag.putString(TARGET_DIM, level.dimension().location().toString());
        }
    }

    /** Nearest placed totem in the player's dimension: their own, or someone else's when {@code rivals}. */
    @Nullable
    private static BlockPos nearest(ServerPlayer player, boolean rivals) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (Map.Entry<UUID, Set<GlobalPos>> e : TotemLedger.get(player.server).allPlaced().entrySet()) {
            if (e.getKey().equals(player.getUUID()) == rivals) {
                continue;
            }
            for (GlobalPos pos : e.getValue()) {
                if (pos.dimension() != player.level().dimension()) {
                    continue;
                }
                double d = pos.pos().distSqr(player.blockPosition());
                if (d < bestDist) {
                    bestDist = d;
                    best = pos.pos();
                }
            }
        }
        return best;
    }

    /** Client and server: where the needle should point, or null to spin. */
    @Nullable
    public static GlobalPos target(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TARGET)) {
            return null;
        }
        var dim = net.minecraft.resources.ResourceLocation.tryParse(tag.getString(TARGET_DIM));
        if (dim == null) {
            return null;
        }
        return GlobalPos.of(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dim), NbtUtils.readBlockPos(tag.getCompound(TARGET)));
    }

    // ------------------------------------------------------------------ use

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider((id, inv, p) -> new TotemCompassMenu(id, inv, hand), Component.translatable("item.bsp_core.totem_compass")),
                    buf -> buf.writeEnum(hand));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem(); // the target updates every second; do not bob
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.bsp_core.compass").withStyle(ChatFormatting.GRAY));
        if (stored(stack) > 0) {
            tooltip.add(Component.translatable("tooltip.bsp_core.compass.stored", stored(stack)).withStyle(ChatFormatting.GOLD));
        }
    }
}
