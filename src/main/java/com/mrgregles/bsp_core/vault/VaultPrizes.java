package com.mrgregles.bsp_core.vault;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The vault prize table: real item stacks (with their count, enchantments and name) and a weight each. A prize roll picks one
 * by weight: its chance is its weight over the total. Admins edit it in the admin panel; a new world starts with the default
 * table, leaving out any item whose mod is not installed. Kept in the overworld's saved data; on a network the database holds
 * one table for every server (the {@code state} key {@code vault_prizes}, as SNBT), which replaces the local copy at start, when
 * another server announces a change over Redis, and at the periodic catch-up. A prize whose item this server does not have is
 * kept as it was saved (never rolled), so saving here does not drop it for the servers that have it.
 */
public class VaultPrizes extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_vault_prizes";
    public static final int MAX_PRIZES = 24;

    /** One line of the table. */
    public record Prize(ItemStack stack, int weight) {
        public Prize {
            stack = stack.copy();
        }
    }

    /** The default table: item id, count, weight. Out of 100 when every mod is installed. */
    public static final Object[][] DEFAULTS = {
            {"minecraft:diamond", 8, 21}, {"minecraft:netherite_ingot", 1, 12}, {"minecraft:totem_of_undying", 1, 12},
            {"allthemodium:allthemodium_ingot", 2, 12}, {"minecraft:enchanted_golden_apple", 1, 10}, {"bsp_core:illyrium_ingot", 2, 8},
            {"allthemodium:vibranium_ingot", 1, 8}, {"minecraft:nether_star", 1, 5}, {"allthemodium:unobtainium_ingot", 1, 4},
            {"mekanism:pellet_polonium", 2, 4}, {"minecraft:elytra", 1, 3}, {"mekanism:pellet_antimatter", 1, 1}};

    private final List<Prize> prizes = new ArrayList<>();
    /** Saved prizes whose item is not in this game, kept as saved and written back with the table. */
    private final List<CompoundTag> unknown = new ArrayList<>();

    public static VaultPrizes get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(VaultPrizes::load, VaultPrizes::withDefaults, DATA_NAME);
    }

    /** A table holding the default prizes whose items exist in this game. */
    public static VaultPrizes withDefaults() {
        VaultPrizes table = new VaultPrizes();
        for (Object[] d : DEFAULTS) {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation((String) d[0]));
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                BSPCore.LOGGER.info("BSP-Core: vault prize {} left out of the default table: its mod is not installed", d[0]);
                continue;
            }
            table.prizes.add(new Prize(new ItemStack(item, (Integer) d[1]), (Integer) d[2]));
        }
        table.setDirty();
        return table;
    }

    public List<Prize> prizes() {
        return Collections.unmodifiableList(prizes);
    }

    /** How many prizes this server may hold: {@link #MAX_PRIZES} less those kept for items it does not have. */
    public int room() {
        return Math.max(0, MAX_PRIZES - unknown.size());
    }

    /** Replaces the whole table; empty stacks and weights below 0 are dropped, and at most {@link #room} kept. */
    public void set(List<Prize> list) {
        prizes.clear();
        int room = room();
        for (Prize p : list) {
            if (!p.stack().isEmpty() && p.weight() >= 0 && prizes.size() < room) {
                prizes.add(p);
            }
        }
        setDirty();
    }

    /** Replaces the table (as {@link #set}) and, on a network, writes it to the database for every server. */
    public void setAndShare(List<Prize> list) {
        set(list);
        if (com.mrgregles.bsp_core.storage.NetworkStorage.enabled()) {
            com.mrgregles.bsp_core.storage.NetworkStorage.vaultPrizesWrite(snbt());
        }
    }

    /** The whole table as SNBT, the form the network keeps. */
    public String snbt() {
        return save(new CompoundTag()).toString();
    }

    /** Replaces this table with one read as SNBT from the network. False, changing nothing, if it cannot be read; the same table changes nothing either. */
    public boolean applySnbt(String snbt) {
        if (snbt.equals(snbt())) {
            return true;
        }
        CompoundTag tag;
        try {
            tag = net.minecraft.nbt.TagParser.parseTag(snbt);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException | RuntimeException e) {
            BSPCore.LOGGER.error("BSP-Core: the network's vault prize table could not be read: {}", e.toString());
            return false;
        }
        VaultPrizes read = load(tag);
        prizes.clear();
        prizes.addAll(read.prizes);
        unknown.clear();
        unknown.addAll(read.unknown);
        setDirty();
        return true;
    }

    /** Prizes kept for items this server does not have. */
    public int unknownCount() {
        return unknown.size();
    }

    public int totalWeight() {
        int total = 0;
        for (Prize p : prizes) {
            total += p.weight();
        }
        return total;
    }

    /** One roll by weight, or null when the table is empty or every weight is 0. */
    @Nullable
    public Prize roll(RandomSource random) {
        int total = totalWeight();
        if (total <= 0) {
            return null;
        }
        int at = random.nextInt(total);
        for (Prize p : prizes) {
            at -= p.weight();
            if (at < 0) {
                return p;
            }
        }
        return null;
    }

    /**
     * Rolls {@code rolls} times and gives each prize to the player (dropping what does not fit), with a chat line each; a rare one
     * ({@code vault.prizes.rareWeight} or less) also plays the level-up sound and says its odds. Returns how many were paid: 0 when
     * the table has nothing to give, so the caller can keep the rolls waiting.
     */
    public int pay(ServerPlayer player, int rolls, RandomSource random) {
        int paid = 0, total = totalWeight(), rare = BSPConfig.VAULT_PRIZE_RARE_WEIGHT.get();
        for (int i = 0; i < rolls; i++) {
            Prize p = roll(random);
            if (p == null) {
                break;
            }
            ItemStack out = p.stack().copy();
            Component name = out.getHoverName().copy();
            if (!player.getInventory().add(out)) {
                player.drop(out, false);
            }
            if (p.weight() <= rare) {
                player.displayClientMessage(Component.translatable("message.bsp_core.vault.prize_rare", p.stack().getCount(), name, Math.round(total / (double) Math.max(1, p.weight())))
                        .withStyle(ChatFormatting.AQUA), false);
                player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1f);
            } else {
                player.displayClientMessage(Component.translatable("message.bsp_core.vault.prize", p.stack().getCount(), name).withStyle(ChatFormatting.AQUA), false);
            }
            paid++;
        }
        return paid;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Prize p : prizes) {
            CompoundTag e = new CompoundTag();
            e.put("Item", p.stack().save(new CompoundTag()));
            e.putInt("Weight", p.weight());
            list.add(e);
        }
        for (CompoundTag e : unknown) {
            list.add(e.copy());
        }
        tag.put("Prizes", list);
        return tag;
    }

    public static VaultPrizes load(CompoundTag tag) {
        VaultPrizes table = new VaultPrizes();
        for (Tag raw : tag.getList("Prizes", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) raw;
            if (table.prizes.size() + table.unknown.size() >= MAX_PRIZES) {
                BSPCore.LOGGER.warn("BSP-Core: the vault prize table holds more than {} prizes; the rest are left out", MAX_PRIZES);
                break;
            }
            ItemStack stack = ItemStack.of(e.getCompound("Item"));
            if (!stack.isEmpty()) {
                table.prizes.add(new Prize(stack, Math.max(0, e.getInt("Weight"))));
            } else if (!e.getCompound("Item").getString("id").isEmpty()) {
                table.unknown.add(e.copy()); // an item from a mod this server lacks
            }
        }
        return table;
    }
}
