package com.mrgregles.bsp_core.vault;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.coin.CoinTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What every player keeps in their Coin Vaults on this server, and the interest that has built up.
 *
 * <p>Interest is worked out on <b>all of a player's vaults together</b>. It builds up in real time,
 * online or offline, from the coin value stored, and stops at a cap of {@code vault.interestCapDays}
 * days' worth, so it has to be redeemed to keep earning. It pays XP levels and Tetrium Ingots by
 * value, and Illyrium Ingots at a fixed slow rate while the vaults hold any Netherite or Illyrium coin.
 */
public class VaultLedger extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_vaults";
    private static final double DAY_MS = 86_400_000d;

    /** One player's account: coins per vault, and interest earned but not yet redeemed. */
    public static final class Account {
        final Map<GlobalPos, int[]> vaults = new HashMap<>();
        double xp, tetrium, illyrium;
        long lastMs;
        /** Coins from vaults somebody else broke, waiting for the owner to collect. */
        int[] recovered = new int[CoinTier.values().length];

        /** Coins of each tier across all of this player's vaults. */
        public int[] coins() {
            int[] sum = new int[CoinTier.values().length];
            for (int[] v : vaults.values()) {
                for (int i = 0; i < sum.length; i++) {
                    sum[i] += v[i];
                }
            }
            return sum;
        }

        public int value() {
            int[] c = coins();
            int total = 0;
            for (CoinTier tier : CoinTier.values()) {
                total += c[tier.ordinal()] * tier.value();
            }
            return total;
        }

        public java.util.List<GlobalPos> positions() {
            return new java.util.ArrayList<>(vaults.keySet());
        }

        public int vaultCount() {
            return vaults.size();
        }
    }

    private final Map<UUID, Account> accounts = new HashMap<>();

    public static VaultLedger get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(VaultLedger::load, VaultLedger::new, DATA_NAME);
    }

    public Account account(UUID owner) {
        return accounts.computeIfAbsent(owner, k -> new Account());
    }

    public Map<UUID, Account> all() {
        return accounts;
    }

    /** Brings a player's interest up to date. Call before their stored coins change and before reading it. */
    public void accrue(UUID owner) {
        Account a = account(owner);
        long now = System.currentTimeMillis();
        if (a.lastMs <= 0 || now < a.lastMs) {
            a.lastMs = now;
            return;
        }
        double days = (now - a.lastMs) / DAY_MS, units = a.value() / (double) BSPConfig.VAULT_INTEREST_UNIT.get(), cap = BSPConfig.VAULT_CAP_DAYS.get();
        a.lastMs = now;
        if (days <= 0) {
            return;
        }
        double xpRate = units * BSPConfig.VAULT_XP_RATE.get(), tetRate = units * BSPConfig.VAULT_TETRIUM_RATE.get();
        a.xp = Math.min(Math.max(a.xp, xpRate * cap), a.xp + xpRate * days);
        a.tetrium = Math.min(Math.max(a.tetrium, tetRate * cap), a.tetrium + tetRate * days);
        int[] c = a.coins();
        // on a network only one of the servers holding such a coin pays this, so the allowance is not multiplied
        if (c[CoinTier.NETHERITE.ordinal()] + c[CoinTier.ILLYRIUM.ordinal()] > 0 && !com.mrgregles.bsp_core.storage.NetworkStorage.illyriumPaidElsewhere(owner)) {
            double period = Math.max(0.01, BSPConfig.VAULT_ILLYRIUM_PERIOD_DAYS.get());
            int per = BSPConfig.VAULT_ILLYRIUM_PER_PERIOD.get();
            a.illyrium = Math.min(Math.max(a.illyrium, per), a.illyrium + per * days / period);
        }
        setDirty();
    }

    /** Records what one vault block holds now. */
    public void record(UUID owner, GlobalPos pos, int[] coins) {
        boolean known = account(owner).vaults.containsKey(pos);
        if (!known) {
            forget(pos);
        }
        accrue(owner);
        account(owner).vaults.put(pos, coins.clone());
        setDirty();
        if (!known) {
            publish(owner); // a new block: the network hears at once, coin changes go with the regular sync
        }
    }

    /** One player's totals on this server as the network stores them: five coin counts, then the block count. */
    public int[] row(UUID owner) {
        Account a = account(owner);
        int[] coins = a.coins(), row = new int[coins.length + 1];
        System.arraycopy(coins, 0, row, 0, coins.length);
        row[coins.length] = a.vaultCount();
        return row;
    }

    private void publish(UUID owner) {
        if (com.mrgregles.bsp_core.storage.NetworkStorage.enabled()) {
            com.mrgregles.bsp_core.storage.NetworkStorage.putVaultRow(owner, row(owner), null);
        }
    }

    /** Removes a vault block from whichever account has it. */
    public void forget(GlobalPos pos) {
        for (var e : accounts.entrySet()) {
            if (e.getValue().vaults.containsKey(pos)) {
                accrue(e.getKey());
                e.getValue().vaults.remove(pos);
                setDirty();
                publish(e.getKey());
            }
        }
    }

    /** Keeps the coins of a vault that someone other than its owner broke, for the owner to collect. */
    public void addRecovered(UUID owner, int[] coins) {
        Account a = account(owner);
        for (int i = 0; i < a.recovered.length; i++) {
            a.recovered[i] += coins[i];
        }
        setDirty();
    }

    /** Hands back, and clears, the coins waiting for the owner. */
    public int[] takeRecovered(UUID owner) {
        Account a = account(owner);
        int[] out = a.recovered;
        a.recovered = new int[out.length];
        setDirty();
        return out;
    }

    /** Admin full reset: nobody is owed any interest or recovered coins any more. */
    public void clearEarnings() {
        long now = System.currentTimeMillis();
        for (Account a : accounts.values()) {
            a.xp = a.tetrium = a.illyrium = 0;
            a.recovered = new int[CoinTier.values().length];
            a.lastMs = now;
        }
        setDirty();
    }

    /** Whole XP levels, Tetrium Ingots and Illyrium Ingots the player could redeem now. */
    public int[] redeemable(UUID owner) {
        accrue(owner);
        Account a = account(owner);
        return new int[]{(int) a.xp, (int) a.tetrium, (int) a.illyrium};
    }

    /** Takes the whole amounts out of the account and returns them; the fractions keep building. */
    public int[] redeem(UUID owner) {
        int[] out = redeemable(owner);
        Account a = account(owner);
        a.xp -= out[0];
        a.tetrium -= out[1];
        a.illyrium -= out[2];
        setDirty();
        return out;
    }

    /** How full the interest is against its cap, 0 to 1. */
    public float fill(UUID owner) {
        Account a = account(owner);
        double cap = a.value() / (double) BSPConfig.VAULT_INTEREST_UNIT.get() * BSPConfig.VAULT_XP_RATE.get() * BSPConfig.VAULT_CAP_DAYS.get();
        return cap <= 0 ? 0f : (float) Math.min(1.0, a.xp / cap);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        accounts.forEach((owner, a) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("Owner", owner);
            e.putDouble("Xp", a.xp);
            e.putDouble("Tetrium", a.tetrium);
            e.putDouble("Illyrium", a.illyrium);
            e.putLong("LastMs", a.lastMs);
            e.putIntArray("Recovered", a.recovered);
            ListTag vaults = new ListTag();
            a.vaults.forEach((pos, coins) -> {
                CompoundTag v = new CompoundTag();
                v.putString("Dim", pos.dimension().location().toString());
                v.putLong("Pos", pos.pos().asLong());
                v.putIntArray("Coins", coins);
                vaults.add(v);
            });
            e.put("Vaults", vaults);
            list.add(e);
        });
        tag.put("Accounts", list);
        return tag;
    }

    private static VaultLedger load(CompoundTag tag) {
        VaultLedger ledger = new VaultLedger();
        for (Tag raw : tag.getList("Accounts", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) raw;
            if (!e.hasUUID("Owner")) {
                continue;
            }
            Account a = ledger.account(e.getUUID("Owner"));
            a.xp = e.getDouble("Xp");
            a.tetrium = e.getDouble("Tetrium");
            a.illyrium = e.getDouble("Illyrium");
            a.lastMs = e.getLong("LastMs");
            if (e.getIntArray("Recovered").length == a.recovered.length) {
                a.recovered = e.getIntArray("Recovered");
            }
            for (Tag rv : e.getList("Vaults", Tag.TAG_COMPOUND)) {
                CompoundTag v = (CompoundTag) rv;
                ResourceLocation dim = ResourceLocation.tryParse(v.getString("Dim"));
                int[] coins = v.getIntArray("Coins");
                if (dim != null && coins.length == CoinTier.values().length) {
                    a.vaults.put(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(v.getLong("Pos"))), coins);
                }
            }
        }
        return ledger;
    }
}
