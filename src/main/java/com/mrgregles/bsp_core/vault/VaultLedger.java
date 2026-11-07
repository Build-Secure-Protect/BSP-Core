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
 * <p>Interest is worked out on <b>all of a player's vaults on this server together</b>. It builds up in
 * real time, online or offline, from the coin value stored, and stops at a cap of {@code vault.interest.capDays}
 * days' worth, so it has to be redeemed to keep earning. It pays XP levels and Tetrium Ingots by value,
 * and Illyrium Ingots by the value of Netherite and Illyrium coins (with a small allowance as the least
 * while any is held). The whole hoard's value picks a multiplier from {@code vault.interest.stepValues},
 * so a bigger hoard earns a better rate, up to a daily ceiling for each of the three.
 *
 * <p>Prize rolls build up too, one every {@code vault.prizes.hoursPerRoll} hours while the player keeps at least
 * {@code vault.prizes.minimumCoinValue} coin value in vaults (every server of a network counted), up to
 * {@code vault.prizes.maxWaiting}. On a network only one server builds them up, so they are never paid twice.
 * They are rolled from {@link VaultPrizes} when redeemed.
 */
public class VaultLedger extends SavedData {
    private static final String DATA_NAME = BSPCore.MODID + "_vaults";
    private static final double DAY_MS = 86_400_000d;

    /** One player's account: coins per vault, and interest earned but not yet redeemed. */
    public static final class Account {
        final Map<GlobalPos, int[]> vaults = new HashMap<>();
        double xp, tetrium, illyrium, prizes;
        long lastMs;
        /**
         * Time up to which Illyrium and prize rolls are settled. They depend on the other servers of a network (which one pays,
         * the coin value there), so this clock only moves while those are known; XP and Tetrium use {@link #lastMs}.
         */
        long netMs;
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
        accrueAt(owner, System.currentTimeMillis());
    }

    /** {@link #accrue} as of {@code now} (epoch milliseconds). */
    public void accrueAt(UUID owner, long now) {
        accrueAt(owner, now, networkSure());
    }

    /**
     * True when this server may settle Illyrium and prize rolls: it is not on a network, or it is connected and has read the other
     * servers' vaults at least once. Before that (the first seconds after a start, or while the database is out of reach) every
     * server would think it is the one that pays.
     */
    public static boolean networkSure() {
        return !com.mrgregles.bsp_core.storage.NetworkStorage.countsWanted()
                || (com.mrgregles.bsp_core.storage.NetworkStorage.enabled() && com.mrgregles.bsp_core.storage.NetworkStorage.vaultsKnown());
    }

    /**
     * {@link #accrue} as of {@code now}, with {@code sure} saying whether Illyrium and prize rolls may be settled (see {@link #networkSure});
     * while not, their time waits and is settled at the next sure accrual. A time before the last one changes nothing, so a clock
     * that went back is never paid twice.
     */
    public void accrueAt(UUID owner, long now, boolean sure) {
        Account a = account(owner);
        if (a.lastMs <= 0) {
            a.lastMs = a.netMs = now;
            return;
        }
        if (a.netMs <= 0) {
            a.netMs = a.lastMs; // an account saved before the separate clock
        }
        double cap = BSPConfig.VAULT_CAP_DAYS.get();
        int high = highValue(a);
        double[] r = dailyRates(a.value(), high);
        if (now > a.lastMs) {
            double days = (now - a.lastMs) / DAY_MS;
            a.lastMs = now;
            a.xp = Math.min(Math.max(a.xp, r[0] * cap), a.xp + r[0] * days);
            a.tetrium = Math.min(Math.max(a.tetrium, r[1] * cap), a.tetrium + r[1] * days);
            setDirty();
        }
        if (sure && now > a.netMs) {
            double days = (now - a.netMs) / DAY_MS;
            a.netMs = now;
            // on a network only one of the servers holding such a coin pays this, so the allowance is not multiplied
            if (high > 0 && !com.mrgregles.bsp_core.storage.NetworkStorage.illyriumPaidElsewhere(owner)) {
                double illyriumCap = Math.max(BSPConfig.VAULT_ILLYRIUM_PER_PERIOD.get(), r[2] * cap);
                a.illyrium = Math.min(Math.max(a.illyrium, illyriumCap), a.illyrium + r[2] * days);
            }
            double hours = BSPConfig.VAULT_PRIZE_HOURS.get();
            if (hours > 0 && a.value() > 0 && networkValue(owner) >= BSPConfig.VAULT_PRIZE_MIN_VALUE.get()
                    && !com.mrgregles.bsp_core.storage.NetworkStorage.prizesPaidElsewhere(owner)) {
                int most = BSPConfig.VAULT_PRIZE_MAX_WAITING.get();
                a.prizes = Math.min(Math.max(a.prizes, most), a.prizes + days * 24 / hours);
            }
            setDirty();
        }
    }

    /**
     * Brings every account up to date. Run before each network vault sync, so who pays Illyrium and prize rolls, and the coin value
     * on the other servers, are judged every sync rather than over whatever time has passed since a vault was last touched.
     */
    public void accrueAll() {
        long now = System.currentTimeMillis();
        boolean sure = networkSure();
        for (UUID owner : new java.util.ArrayList<>(accounts.keySet())) {
            accrueAt(owner, now, sure);
        }
    }

    /** Coin value the player keeps in vaults on this server and, on a network, on every other one. */
    public int networkValue(UUID owner) {
        int[] elsewhere = com.mrgregles.bsp_core.storage.NetworkStorage.vaultCoinsElsewhere(owner);
        int value = account(owner).value();
        for (CoinTier tier : CoinTier.values()) {
            value += elsewhere[tier.ordinal()] * tier.value();
        }
        return value;
    }

    /** Prize roll states, for the vault's INTEREST tab. */
    public static final int PRIZE_EARNING = 0, PRIZE_SHORT = 1, PRIZE_ELSEWHERE = 2, PRIZE_FULL = 3, PRIZE_OFF = 4, PRIZE_NETWORK = 5;

    /**
     * Whether this player's prize rolls are building up here: {@link #PRIZE_OFF} (turned off), {@link #PRIZE_SHORT} (under the
     * minimum, every server counted), {@link #PRIZE_NETWORK} (the other servers are not known yet), {@link #PRIZE_ELSEWHERE}
     * (another server builds them up), {@link #PRIZE_FULL} (as many wait as may) or {@link #PRIZE_EARNING}. Read after {@link #accrue}.
     */
    public int prizeState(UUID owner) {
        Account a = account(owner);
        if (BSPConfig.VAULT_PRIZE_HOURS.get() <= 0) {
            return PRIZE_OFF;
        }
        if (!networkSure()) {
            return PRIZE_NETWORK;
        }
        if (networkValue(owner) < BSPConfig.VAULT_PRIZE_MIN_VALUE.get() || (a.value() <= 0 && !com.mrgregles.bsp_core.storage.NetworkStorage.enabled())) {
            return PRIZE_SHORT;
        }
        if (a.value() <= 0 || com.mrgregles.bsp_core.storage.NetworkStorage.prizesPaidElsewhere(owner)) {
            return PRIZE_ELSEWHERE;
        }
        return a.prizes >= BSPConfig.VAULT_PRIZE_MAX_WAITING.get() ? PRIZE_FULL : PRIZE_EARNING;
    }

    /** Whole minutes until the next prize roll while they build up here, or -1. Read after {@link #accrue}. */
    public int minutesToNextRoll(UUID owner) {
        if (prizeState(owner) != PRIZE_EARNING) {
            return -1;
        }
        double p = account(owner).prizes;
        return (int) Math.ceil((1 - (p - Math.floor(p))) * BSPConfig.VAULT_PRIZE_HOURS.get() * 60 - 1e-9);
    }

    /** Whole prize rolls waiting, brought up to date. */
    public int prizeRolls(UUID owner) {
        accrue(owner);
        return (int) account(owner).prizes;
    }

    /** Takes {@code n} whole prize rolls out of the account (after they were paid); the fraction keeps building. */
    public void takePrizeRolls(UUID owner, int n) {
        Account a = account(owner);
        a.prizes = Math.max(0, a.prizes - n);
        setDirty();
    }

    /** Coin value of the Netherite and Illyrium coins in a player's vaults: what Illyrium interest is paid on. */
    private static int highValue(Account a) {
        int[] c = a.coins();
        return c[CoinTier.NETHERITE.ordinal()] * CoinTier.NETHERITE.value() + c[CoinTier.ILLYRIUM.ordinal()] * CoinTier.ILLYRIUM.value();
    }

    /**
     * The multiplier for a hoard of this much coin value: that of the highest step in {@code vault.interest.stepValues} it reaches,
     * or 1 below them all.
     */
    public static double multiplier(int value) {
        java.util.List<? extends Integer> steps = BSPConfig.VAULT_STEP_VALUES.get();
        java.util.List<? extends Number> multipliers = BSPConfig.VAULT_STEP_MULTIPLIERS.get();
        double m = 1.0;
        int reached = Integer.MIN_VALUE;
        for (int i = 0; i < Math.min(steps.size(), multipliers.size()); i++) {
            int step = steps.get(i);
            if (value >= step && step > reached) {
                reached = step;
                m = multipliers.get(i).doubleValue();
            }
        }
        return m;
    }

    /**
     * XP levels, Tetrium Ingots and Illyrium Ingots earned a day by a hoard of {@code value} coin value, {@code highValue} of it in
     * Netherite and Illyrium coins: the base rates times {@link #multiplier}, each held to its daily ceiling. Illyrium is never less
     * than the allowance ({@code illyriumIngotsPerPeriod} per {@code illyriumPeriodDays}) while any such coin is held, and 0 without one.
     */
    public static double[] dailyRates(int value, int highValue) {
        double m = multiplier(value), units = value / (double) BSPConfig.VAULT_INTEREST_UNIT.get();
        double xp = ceiling(units * BSPConfig.VAULT_XP_RATE.get() * m, BSPConfig.VAULT_MAX_XP_PER_DAY.get());
        double tetrium = ceiling(units * BSPConfig.VAULT_TETRIUM_RATE.get() * m, BSPConfig.VAULT_MAX_TETRIUM_PER_DAY.get());
        double illyrium = 0;
        if (highValue > 0) {
            double allowance = BSPConfig.VAULT_ILLYRIUM_PER_PERIOD.get() / Math.max(0.01, BSPConfig.VAULT_ILLYRIUM_PERIOD_DAYS.get());
            int perIngot = BSPConfig.VAULT_ILLYRIUM_VALUE_PER_INGOT.get();
            double scaled = perIngot <= 0 ? 0 : ceiling(highValue / (double) perIngot * m, BSPConfig.VAULT_MAX_ILLYRIUM_PER_DAY.get());
            illyrium = Math.max(allowance, scaled);
        }
        return new double[]{xp, tetrium, illyrium};
    }

    private static double ceiling(double amount, double max) {
        return max > 0 ? Math.min(amount, max) : amount;
    }

    /** What this player's vaults on this server earn a day now: XP levels, Tetrium Ingots, Illyrium Ingots. */
    public double[] rates(UUID owner) {
        Account a = account(owner);
        return dailyRates(a.value(), highValue(a));
    }

    /** XP, Tetrium, Illyrium and prize rolls owed, with their fractions, as last brought up to date (no accrual). */
    public double[] owed(UUID owner) {
        Account a = account(owner);
        return new double[]{a.xp, a.tetrium, a.illyrium, a.prizes};
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
            a.xp = a.tetrium = a.illyrium = a.prizes = 0;
            a.recovered = new int[CoinTier.values().length];
            a.lastMs = a.netMs = now;
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
        double[] r = rates(owner);
        double days = BSPConfig.VAULT_CAP_DAYS.get();
        // XP shows how full it is; with XP turned off, Tetrium does
        if (r[0] > 0) {
            return (float) Math.min(1.0, a.xp / (r[0] * days));
        }
        return r[1] <= 0 ? 0f : (float) Math.min(1.0, a.tetrium / (r[1] * days));
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
            e.putDouble("Prizes", a.prizes);
            e.putLong("LastMs", a.lastMs);
            e.putLong("NetMs", a.netMs);
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
            a.prizes = e.getDouble("Prizes");
            a.lastMs = e.getLong("LastMs");
            a.netMs = e.getLong("NetMs");
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
