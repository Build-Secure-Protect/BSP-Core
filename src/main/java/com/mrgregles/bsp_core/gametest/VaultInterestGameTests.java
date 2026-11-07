package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.vault.VaultLedger;
import com.mrgregles.bsp_core.vault.VaultPrizes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.UUID;

/**
 * GameTests for the stepped Coin Vault interest: the multiplier steps (x1 below 32 coin value, x1.5 from 32, x2 from 128, x3 from
 * 512), the daily rates agreed with the owner for each sample hoard and their ceilings, one day's accrual on a ledger, the 3-day
 * cap, redeeming whole amounts, the Illyrium allowance for a small hoard, the one-time move of the old default rates, and the
 * prize rolls: one every 12 hours from 32 coin value up to 6 waiting, picked by weight from the prize table, paid as items.
 * They read the config defaults; a server with other vault.interest values fails the first check with a message saying so.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class VaultInterestGameTests {
    private static final String EMPTY = "empty", BATCH = "vault_interest";
    private static final long DAY = 86_400_000L;
    /** Copper, Gold, Diamond, Netherite, Illyrium. */
    private static final int[] MID_HOARD = {0, 0, 24, 4, 0};

    private static boolean defaults() {
        return BSPConfig.VAULT_INTEREST_UNIT.get() == 10 && BSPConfig.VAULT_XP_RATE.get() == 1.0 && BSPConfig.VAULT_TETRIUM_RATE.get() == 3.0
                && BSPConfig.VAULT_STEP_VALUES.get().equals(java.util.List.of(32, 128, 512))
                && BSPConfig.VAULT_STEP_MULTIPLIERS.get().stream().map(Number::doubleValue).toList().equals(java.util.List.of(1.5, 2.0, 3.0))
                && BSPConfig.VAULT_MAX_XP_PER_DAY.get() == 100.0 && BSPConfig.VAULT_MAX_TETRIUM_PER_DAY.get() == 384.0 && BSPConfig.VAULT_MAX_ILLYRIUM_PER_DAY.get() == 4.0
                && BSPConfig.VAULT_ILLYRIUM_VALUE_PER_INGOT.get() == 32 && BSPConfig.VAULT_ILLYRIUM_PER_PERIOD.get() == 2
                && BSPConfig.VAULT_ILLYRIUM_PERIOD_DAYS.get() == 3.0 && BSPConfig.VAULT_CAP_DAYS.get() == 3.0;
    }

    private static boolean near(double[] got, double... want) {
        for (int i = 0; i < want.length; i++) {
            if (Math.abs(got[i] - want[i]) > 0.01) {
                return false;
            }
        }
        return true;
    }

    private static void rates(GameTestHelper h, String hoard, int value, int high, double xp, double tetrium, double illyrium) {
        double[] got = VaultLedger.dailyRates(value, high);
        h.assertTrue(near(got, xp, tetrium, illyrium), hoard + " earns " + xp + " XP, " + tetrium + " Tetrium, " + illyrium + " Illyrium a day, got " + Arrays.toString(got));
    }

    /** A ledger holding one vault block with these coins, its clock started just before {@code t0}. */
    private static VaultLedger ledger(UUID owner, int[] coins, long t0) {
        VaultLedger ledger = new VaultLedger();
        ledger.record(owner, GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), coins);
        ledger.accrueAt(owner, t0);
        return ledger;
    }

    /** The multiplier is that of the highest step reached: x1 below 32, x1.5 from 32, x2 from 128, x3 from 512 and beyond. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void multiplier_steps_up_with_the_hoard(GameTestHelper h) {
        h.assertTrue(defaults(), "this test reads the default vault.interest values");
        int[] values = {0, 31, 32, 127, 128, 511, 512, 100_000};
        double[] want = {1, 1, 1.5, 1.5, 2, 2, 3, 3};
        for (int i = 0; i < values.length; i++) {
            h.assertTrue(VaultLedger.multiplier(values[i]) == want[i], values[i] + " coin value has x" + want[i] + ", got x" + VaultLedger.multiplier(values[i]));
        }
        h.succeed();
    }

    /** The rates agreed with the owner: 20 coins of each tier, the mid hoard of 24 Diamond and 4 Netherite, and a full block held to the ceilings. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void daily_rates_match_the_agreed_table(GameTestHelper h) {
        h.assertTrue(defaults(), "this test reads the default vault.interest values");
        rates(h, "20 Copper", 20, 0, 2, 6, 0);
        rates(h, "20 Gold", 40, 0, 6, 18, 0);
        rates(h, "20 Diamond", 80, 0, 12, 36, 0);
        rates(h, "20 Netherite", 160, 160, 32, 96, 4);
        rates(h, "20 Illyrium", 320, 320, 64, 192, 4);
        rates(h, "24 Diamond and 4 Netherite", 128, 32, 25.6, 76.8, 2);
        rates(h, "64 Netherite", 512, 512, 100, 384, 4);
        rates(h, "a full block of Netherite", 2592, 2592, 100, 384, 4);
        rates(h, "one Netherite coin (the allowance)", 8, 8, 0.8, 2.4, 2 / 3.0);
        h.succeed();
    }

    /** One day pays one day's rates; ten days stop at three days' worth; redeeming takes the whole amounts and leaves the fractions. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void a_day_accrues_the_cap_holds_and_redeem_keeps_fractions(GameTestHelper h) {
        h.assertTrue(defaults(), "this test reads the default vault.interest values");
        UUID owner = UUID.randomUUID();
        long t0 = System.currentTimeMillis() + 1000;
        VaultLedger ledger = ledger(owner, MID_HOARD, t0);
        h.assertTrue(ledger.account(owner).value() == 128, "24 Diamond and 4 Netherite coins are 128 coin value");
        h.assertTrue(near(ledger.rates(owner), 25.6, 76.8, 2), "the hoard's rates are x2: " + Arrays.toString(ledger.rates(owner)));
        ledger.accrueAt(owner, t0 + DAY);
        h.assertTrue(near(ledger.owed(owner), 25.6, 76.8, 2), "one day owes one day's rates, got " + Arrays.toString(ledger.owed(owner)));
        h.assertTrue(Math.abs(ledger.fill(owner) - 1 / 3f) < 0.01, "one day of three fills a third of the bar, got " + ledger.fill(owner));
        ledger.accrueAt(owner, t0 + 10 * DAY);
        h.assertTrue(near(ledger.owed(owner), 76.8, 230.4, 6), "ten days stop at three days' worth, got " + Arrays.toString(ledger.owed(owner)));
        h.assertTrue(ledger.fill(owner) == 1f, "the bar is full at the cap");
        int[] out = ledger.redeem(owner);
        h.assertTrue(Arrays.equals(out, new int[]{76, 230, 6}), "redeem pays the whole amounts, got " + Arrays.toString(out));
        h.assertTrue(near(ledger.owed(owner), 0.8, 0.4, 0), "the fractions stay to keep building, got " + Arrays.toString(ledger.owed(owner)));
        h.succeed();
    }

    /** A hoard under the first step earns at x1, and one Netherite coin earns the old allowance of 2 Illyrium Ingots every 3 days. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void a_small_hoard_earns_at_x1_and_the_illyrium_allowance(GameTestHelper h) {
        h.assertTrue(defaults(), "this test reads the default vault.interest values");
        UUID owner = UUID.randomUUID();
        long t0 = System.currentTimeMillis() + 1000;
        VaultLedger ledger = ledger(owner, new int[]{0, 0, 0, 1, 0}, t0);
        ledger.accrueAt(owner, t0 + 3 * DAY);
        h.assertTrue(near(ledger.owed(owner), 2.4, 7.2, 2), "three days of one Netherite coin owe 2.4 XP, 7.2 Tetrium, 2 Illyrium, got " + Arrays.toString(ledger.owed(owner)));
        UUID poor = UUID.randomUUID();
        VaultLedger copper = ledger(poor, new int[]{20, 0, 0, 0, 0}, t0);
        copper.accrueAt(poor, t0 + DAY);
        h.assertTrue(near(copper.owed(poor), 2, 6, 0), "20 Copper coins owe 2 XP, 6 Tetrium and no Illyrium a day, got " + Arrays.toString(copper.owed(poor)));
        h.succeed();
    }

    /** The old defaults (50 coin value a unit, 0.5 Tetrium) move to 10 and 3 once; values an admin set are left alone. */
    @GameTest(template = EMPTY, batch = "vault_config") // its own batch: it saves the config file, which other tests read
    public void old_default_rates_move_once(GameTestHelper h) {
        int unit = BSPConfig.VAULT_INTEREST_UNIT.get();
        double tetrium = BSPConfig.VAULT_TETRIUM_RATE.get();
        boolean moved = BSPConfig.VAULT_RATES_MOVED.get();
        try {
            h.assertTrue(moved, "the step already ran when the server loaded its config");
            BSPConfig.VAULT_INTEREST_UNIT.set(50);
            BSPConfig.VAULT_TETRIUM_RATE.set(0.5);
            BSPConfig.VAULT_RATES_MOVED.set(false);
            h.assertTrue(BSPConfig.moveOldVaultRates() && BSPConfig.VAULT_INTEREST_UNIT.get() == 10 && BSPConfig.VAULT_TETRIUM_RATE.get() == 3.0
                    && BSPConfig.VAULT_RATES_MOVED.get(), "50 and 0.5 move to 10 and 3 and are marked");
            BSPConfig.VAULT_INTEREST_UNIT.set(50);
            h.assertTrue(!BSPConfig.moveOldVaultRates() && BSPConfig.VAULT_INTEREST_UNIT.get() == 50, "an admin's 50 after the step is kept");
            BSPConfig.VAULT_INTEREST_UNIT.set(25);
            BSPConfig.VAULT_TETRIUM_RATE.set(0.5);
            BSPConfig.VAULT_RATES_MOVED.set(false);
            h.assertTrue(BSPConfig.moveOldVaultRates() && BSPConfig.VAULT_INTEREST_UNIT.get() == 25 && BSPConfig.VAULT_TETRIUM_RATE.get() == 3.0,
                    "a 25 is left alone while the old Tetrium rate still moves");
        } finally {
            BSPConfig.VAULT_INTEREST_UNIT.set(unit);
            BSPConfig.VAULT_TETRIUM_RATE.set(tetrium);
            BSPConfig.VAULT_RATES_MOVED.set(moved);
            BSPConfig.SPEC.save();
        }
        h.succeed();
    }

    private static boolean prizeDefaults() {
        return BSPConfig.VAULT_PRIZE_HOURS.get() == 12.0 && BSPConfig.VAULT_PRIZE_MIN_VALUE.get() == 32 && BSPConfig.VAULT_PRIZE_MAX_WAITING.get() == 6
                && BSPConfig.VAULT_PRIZE_RARE_WEIGHT.get() == 5;
    }

    private static int countOf(net.minecraft.server.level.ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack s = p.getInventory().getItem(i);
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** 128 coin value earns 2 prize rolls a day and stops at 6; 20 coin value (under the 32 minimum) earns none. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void prize_rolls_build_every_12_hours_from_32_coin_value(GameTestHelper h) {
        h.assertTrue(prizeDefaults(), "this test reads the default vault.prizes values");
        long t0 = System.currentTimeMillis() + 1000;
        UUID owner = UUID.randomUUID();
        VaultLedger ledger = ledger(owner, MID_HOARD, t0);
        ledger.accrueAt(owner, t0 + DAY);
        h.assertTrue(Math.abs(ledger.owed(owner)[3] - 2) < 0.01, "a day at 128 coin value earns 2 rolls, got " + ledger.owed(owner)[3]);
        ledger.accrueAt(owner, t0 + 10 * DAY);
        h.assertTrue(ledger.owed(owner)[3] == 6, "rolls stop at 6 waiting, got " + ledger.owed(owner)[3]);
        ledger.takePrizeRolls(owner, 4);
        h.assertTrue(ledger.owed(owner)[3] == 2, "taking 4 leaves 2, got " + ledger.owed(owner)[3]);
        UUID poor = UUID.randomUUID();
        VaultLedger copper = ledger(poor, new int[]{20, 0, 0, 0, 0}, t0);
        copper.accrueAt(poor, t0 + 3 * DAY);
        h.assertTrue(copper.owed(poor)[3] == 0, "20 coin value is under the minimum and earns no rolls, got " + copper.owed(poor)[3]);
        h.succeed();
    }

    /** A roll picks by weight (3 to 1 comes out near 75 %), an empty table or one with every weight 0 gives nothing. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void prize_table_rolls_by_weight(GameTestHelper h) {
        VaultPrizes table = new VaultPrizes();
        h.assertTrue(table.roll(net.minecraft.util.RandomSource.create(1)) == null, "an empty table rolls nothing");
        table.set(java.util.List.of(new VaultPrizes.Prize(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 8), 3),
                new VaultPrizes.Prize(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD), 1)));
        net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(42);
        int diamonds = 0;
        for (int i = 0; i < 4000; i++) {
            if (table.roll(random).stack().is(net.minecraft.world.item.Items.DIAMOND)) {
                diamonds++;
            }
        }
        h.assertTrue(diamonds > 2800 && diamonds < 3200, "weight 3 against 1 wins about 3,000 of 4,000 rolls, got " + diamonds);
        table.set(java.util.List.of(new VaultPrizes.Prize(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND), 0)));
        h.assertTrue(table.roll(random) == null, "a table whose weights are all 0 rolls nothing");
        h.succeed();
    }

    /** The default table holds every default prize whose item is installed, with its count and weight, and none whose mod is missing. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void default_prize_table_holds_the_installed_items(GameTestHelper h) {
        VaultPrizes table = VaultPrizes.withDefaults();
        int installed = 0;
        for (Object[] d : VaultPrizes.DEFAULTS) {
            net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation((String) d[0]));
            boolean present = item != null && item != net.minecraft.world.item.Items.AIR;
            boolean listed = table.prizes().stream().anyMatch(p -> p.stack().is(item) && p.stack().getCount() == (Integer) d[1] && p.weight() == (Integer) d[2]);
            h.assertTrue(present == listed, d[0] + (present ? " is installed and must be listed with its count and weight" : " is not installed and must be left out"));
            installed += present ? 1 : 0;
        }
        h.assertTrue(table.prizes().size() == installed, "only the default prizes are listed, got " + table.prizes().size());
        h.assertTrue(table.totalWeight() <= 100, "the weights add up to 100 at most, got " + table.totalWeight());
        h.succeed();
    }

    /** Paying gives each prize to the player, and an empty table pays nothing so the rolls stay waiting. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void paying_prizes_gives_items_and_an_empty_table_pays_none(GameTestHelper h) {
        var player = MockPlayers.serverPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);
        VaultPrizes table = new VaultPrizes();
        h.assertTrue(table.pay(player, 3, net.minecraft.util.RandomSource.create(7)) == 0, "an empty table pays none");
        table.set(java.util.List.of(new VaultPrizes.Prize(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 8), 21)));
        h.assertTrue(table.pay(player, 2, net.minecraft.util.RandomSource.create(7)) == 2, "two rolls pay two prizes");
        h.assertTrue(countOf(player, net.minecraft.world.item.Items.DIAMOND) == 16, "the player has 2 x 8 diamonds, got " + countOf(player, net.minecraft.world.item.Items.DIAMOND));
        h.succeed();
    }

    /** A prize keeps its count, enchantments and name through a save and load, and its weight. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void prize_table_survives_a_save_with_enchantments(GameTestHelper h) {
        net.minecraft.world.item.ItemStack book = net.minecraft.world.item.EnchantedBookItem.createForEnchantment(
                new net.minecraft.world.item.enchantment.EnchantmentInstance(net.minecraft.world.item.enchantment.Enchantments.MENDING, 1));
        book.setHoverName(net.minecraft.network.chat.Component.literal("Vault Mending"));
        VaultPrizes table = new VaultPrizes();
        table.set(java.util.List.of(new VaultPrizes.Prize(book, 4), new VaultPrizes.Prize(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHER_STAR), 5)));
        VaultPrizes copy = VaultPrizes.load(table.save(new net.minecraft.nbt.CompoundTag()));
        h.assertTrue(copy.prizes().size() == 2, "both prizes load, got " + copy.prizes().size());
        h.assertTrue(net.minecraft.world.item.ItemStack.matches(copy.prizes().get(0).stack(), book) && copy.prizes().get(0).weight() == 4, "the book keeps its enchantment, name and weight");
        h.assertTrue(copy.totalWeight() == 9, "the weights load, got " + copy.totalWeight());
        h.succeed();
    }

    /** The network form: a table goes to SNBT and back unchanged; a prize whose mod is missing here is kept and written back; unreadable text changes nothing. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void shared_prize_table_round_trips_and_keeps_unknown_items(GameTestHelper h) {
        VaultPrizes table = new VaultPrizes();
        table.set(java.util.List.of(new VaultPrizes.Prize(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 8), 21)));
        String other = "{Prizes:[{Item:{id:\"minecraft:diamond\",Count:8b},Weight:21},{Item:{id:\"nomod:mystery_ingot\",Count:2b},Weight:6}]}";
        VaultPrizes here = new VaultPrizes();
        h.assertTrue(here.applySnbt(other), "a table from the network is read");
        h.assertTrue(here.prizes().size() == 1 && here.unknownCount() == 1, "the diamond prize is usable and the missing mod's prize is kept aside");
        h.assertTrue(here.totalWeight() == 21, "a prize this server cannot give is never rolled, total " + here.totalWeight());
        h.assertTrue(here.snbt().contains("nomod:mystery_ingot"), "the missing mod's prize is written back: " + here.snbt());
        VaultPrizes copy = new VaultPrizes();
        h.assertTrue(copy.applySnbt(table.snbt()) && copy.snbt().equals(table.snbt()), "a table round-trips through SNBT unchanged");
        h.assertTrue(!copy.applySnbt("{Prizes:[{oops") && copy.prizes().size() == 1, "unreadable text changes nothing");
        h.succeed();
    }

    private static net.minecraft.world.item.ItemStack namedBook() {
        net.minecraft.world.item.ItemStack book = net.minecraft.world.item.EnchantedBookItem.createForEnchantment(
                new net.minecraft.world.item.enchantment.EnchantmentInstance(net.minecraft.world.item.enchantment.Enchantments.MENDING, 1));
        book.setHoverName(net.minecraft.network.chat.Component.literal("Vault Mending"));
        return book;
    }

    /** The VAULT tab: a moderator may look but not save, an admin's save replaces the table and keeps prizes of mods missing here. */
    @GameTest(template = EMPTY, batch = "vault_admin") // its own batch: it changes the world's prize table, then puts it back
    public void vault_tab_saves_for_admins_and_keeps_unknown_prizes(GameTestHelper h) {
        var player = MockPlayers.serverPlayer(h, net.minecraft.world.level.GameType.SURVIVAL);
        VaultPrizes table = VaultPrizes.get(h.getLevel().getServer());
        String before = table.snbt();
        try {
            h.assertTrue(table.applySnbt("{Prizes:[{Item:{id:\"minecraft:diamond\",Count:8b},Weight:21},{Item:{id:\"nomod:mystery_ingot\",Count:2b},Weight:6}]}"), "the test table loads");
            var save = new com.mrgregles.bsp_core.network.VaultPrizeEditPacket(com.mrgregles.bsp_core.network.VaultPrizeEditPacket.Action.SAVE,
                    java.util.List.of(namedBook(), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHER_STAR)), java.util.List.of(4, 5));
            h.assertTrue(com.mrgregles.bsp_core.vault.VaultPrizeAdmin.act(player, save, false, false) == null, "someone who may not look gets no answer");
            String refused = com.mrgregles.bsp_core.vault.VaultPrizeAdmin.act(player, save, true, false);
            h.assertTrue(refused != null && refused.startsWith("Only admins") && table.prizes().size() == 1, "a moderator's save is refused and changes nothing: " + refused);
            String saved = com.mrgregles.bsp_core.vault.VaultPrizeAdmin.act(player, save, true, true);
            h.assertTrue(saved != null && saved.startsWith("Saved 2 prizes"), "an admin's save is answered: " + saved);
            h.assertTrue(table.prizes().size() == 2 && net.minecraft.world.item.ItemStack.matches(table.prizes().get(0).stack(), namedBook())
                    && table.prizes().get(0).weight() == 4 && table.prizes().get(1).weight() == 5, "the table is the admin's, enchantment and name kept");
            h.assertTrue(table.unknownCount() == 1 && table.snbt().contains("nomod:mystery_ingot"), "the prize from a mod missing here is kept for the other servers");
            String asked = com.mrgregles.bsp_core.vault.VaultPrizeAdmin.act(player, new com.mrgregles.bsp_core.network.VaultPrizeEditPacket(
                    com.mrgregles.bsp_core.network.VaultPrizeEditPacket.Action.REQUEST, java.util.List.of(), java.util.List.of()), true, false);
            h.assertTrue("".equals(asked), "a moderator may ask for the table");
        } finally {
            table.applySnbt(before);
        }
        h.succeed();
    }

    /** A table that cannot be saved: too many prizes, an empty item, a weight out of range, or lists of different lengths. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void vault_tab_refuses_a_bad_table(GameTestHelper h) {
        net.minecraft.world.item.ItemStack d = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND);
        java.util.List<net.minecraft.world.item.ItemStack> many = java.util.Collections.nCopies(VaultPrizes.MAX_PRIZES + 1, d);
        java.util.List<Integer> ones = java.util.Collections.nCopies(VaultPrizes.MAX_PRIZES + 1, 1);
        h.assertTrue(com.mrgregles.bsp_core.vault.VaultPrizeAdmin.check(many, ones, VaultPrizes.MAX_PRIZES) != null, "25 prizes are too many");
        h.assertTrue(com.mrgregles.bsp_core.vault.VaultPrizeAdmin.check(java.util.List.of(net.minecraft.world.item.ItemStack.EMPTY), java.util.List.of(1), VaultPrizes.MAX_PRIZES) != null, "an empty item is refused");
        h.assertTrue(com.mrgregles.bsp_core.vault.VaultPrizeAdmin.check(java.util.List.of(d), java.util.List.of(com.mrgregles.bsp_core.vault.VaultPrizeAdmin.MAX_WEIGHT + 1), VaultPrizes.MAX_PRIZES) != null, "a weight over the most is refused");
        h.assertTrue(com.mrgregles.bsp_core.vault.VaultPrizeAdmin.check(java.util.List.of(d), java.util.List.of(-1), VaultPrizes.MAX_PRIZES) != null, "a weight under 0 is refused");
        h.assertTrue(com.mrgregles.bsp_core.vault.VaultPrizeAdmin.check(java.util.List.of(d, d), java.util.List.of(1), VaultPrizes.MAX_PRIZES) != null, "lists of different lengths are refused");
        h.assertTrue(com.mrgregles.bsp_core.vault.VaultPrizeAdmin.check(java.util.List.of(d), java.util.List.of(0), VaultPrizes.MAX_PRIZES) == null, "a weight of 0 is allowed (never rolled)");
        h.assertTrue(com.mrgregles.bsp_core.vault.VaultPrizeAdmin.check(java.util.List.of(), java.util.List.of(), VaultPrizes.MAX_PRIZES) == null, "an empty table is allowed");
        h.succeed();
    }

    /** The VAULT tab's packets carry each prize's stack (count, enchantment, name) and weight both ways. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void vault_tab_packets_round_trip(GameTestHelper h) {
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        var edit = new com.mrgregles.bsp_core.network.VaultPrizeEditPacket(com.mrgregles.bsp_core.network.VaultPrizeEditPacket.Action.SAVE,
                java.util.List.of(namedBook(), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 8)), java.util.List.of(4, 21));
        com.mrgregles.bsp_core.network.VaultPrizeEditPacket.encode(edit, buf);
        var back = com.mrgregles.bsp_core.network.VaultPrizeEditPacket.decode(buf);
        h.assertTrue(back.action() == com.mrgregles.bsp_core.network.VaultPrizeEditPacket.Action.SAVE && back.weights().equals(java.util.List.of(4, 21))
                && net.minecraft.world.item.ItemStack.matches(back.stacks().get(0), namedBook()) && back.stacks().get(1).getCount() == 8, "the edit packet arrives whole");
        var buf2 = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        var data = new com.mrgregles.bsp_core.network.VaultPrizeAdminPacket(back.stacks(), back.weights(), 2, "Saved 2 prizes.", true);
        com.mrgregles.bsp_core.network.VaultPrizeAdminPacket.encode(data, buf2);
        var got = com.mrgregles.bsp_core.network.VaultPrizeAdminPacket.decode(buf2);
        h.assertTrue(got.stacks().size() == 2 && got.unknown() == 2 && got.message().equals("Saved 2 prizes.") && got.ok()
                && net.minecraft.world.item.ItemStack.matches(got.stacks().get(0), namedBook()), "the data packet arrives whole");
        h.succeed();
    }

    /** The INTEREST tab's prize line: earning with the minutes to the next roll, full at 6, short under 32 coin value. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void interest_tab_prize_state_and_next_roll(GameTestHelper h) {
        h.assertTrue(prizeDefaults(), "this test reads the default vault.prizes values");
        long t0 = System.currentTimeMillis() + 1000;
        UUID owner = UUID.randomUUID();
        VaultLedger ledger = ledger(owner, MID_HOARD, t0);
        ledger.accrueAt(owner, t0 + DAY / 4);
        h.assertTrue(ledger.prizeState(owner) == VaultLedger.PRIZE_EARNING, "128 coin value is earning rolls, got state " + ledger.prizeState(owner));
        int minutes = ledger.minutesToNextRoll(owner);
        h.assertTrue(minutes >= 359 && minutes <= 360, "6 hours in, the next roll is 6 hours away, got " + minutes + " minutes");
        ledger.accrueAt(owner, t0 + 10 * DAY);
        h.assertTrue(ledger.prizeState(owner) == VaultLedger.PRIZE_FULL && ledger.minutesToNextRoll(owner) == -1, "6 waiting is full, with no countdown");
        UUID poor = UUID.randomUUID();
        VaultLedger copper = ledger(poor, new int[]{20, 0, 0, 0, 0}, t0);
        h.assertTrue(copper.prizeState(poor) == VaultLedger.PRIZE_SHORT && copper.minutesToNextRoll(poor) == -1, "20 coin value is short of the minimum");
        h.assertTrue(copper.networkValue(poor) == 20, "the network value is this server's alone without a network, got " + copper.networkValue(poor));
        h.succeed();
    }

    /** Synced values travel as shorts: rates go as tenths or hundredths, held to 0..32767. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void interest_tab_synced_values_are_scaled_and_clamped(GameTestHelper h) {
        h.assertTrue(com.mrgregles.bsp_core.vault.CoinVaultMenu.scaled(25.6, 10) == 256, "25.6 goes as 256");
        h.assertTrue(com.mrgregles.bsp_core.vault.CoinVaultMenu.scaled(2 / 3.0, 100) == 67, "two thirds go as 67 hundredths");
        h.assertTrue(com.mrgregles.bsp_core.vault.CoinVaultMenu.scaled(1_000_000, 10) == Short.MAX_VALUE, "a huge rate is held to 32767");
        h.assertTrue(com.mrgregles.bsp_core.vault.CoinVaultMenu.scaled(-3, 10) == 0, "nothing goes below 0");
        h.succeed();
    }

    /** Audit fix: a clock that goes back and forward again pays the span once, not twice. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void a_clock_going_back_pays_nothing_twice(GameTestHelper h) {
        h.assertTrue(defaults() && prizeDefaults(), "this test reads the default vault.interest and vault.prizes values");
        long t0 = System.currentTimeMillis() + 1000;
        UUID owner = UUID.randomUUID();
        VaultLedger ledger = ledger(owner, MID_HOARD, t0);
        ledger.accrueAt(owner, t0 + DAY);
        ledger.accrueAt(owner, t0 + DAY / 2); // the clock went back half a day
        ledger.accrueAt(owner, t0 + 2 * DAY);
        h.assertTrue(near(ledger.owed(owner), 51.2, 153.6, 4, 4), "two days pay two days' worth, got " + Arrays.toString(ledger.owed(owner)));
        h.succeed();
    }

    /** Audit fix: while the other servers are not known, XP and Tetrium build but Illyrium and prize time waits, then is settled once. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void network_time_waits_until_the_other_servers_are_known(GameTestHelper h) {
        h.assertTrue(defaults() && prizeDefaults(), "this test reads the default vault.interest and vault.prizes values");
        long t0 = System.currentTimeMillis() + 1000;
        UUID owner = UUID.randomUUID();
        VaultLedger ledger = ledger(owner, MID_HOARD, t0);
        ledger.accrueAt(owner, t0 + DAY, false);
        h.assertTrue(near(ledger.owed(owner), 25.6, 76.8, 0, 0), "unsure: XP and Tetrium build, Illyrium and prizes wait, got " + Arrays.toString(ledger.owed(owner)));
        ledger.accrueAt(owner, t0 + DAY, true);
        h.assertTrue(near(ledger.owed(owner), 25.6, 76.8, 2, 2), "once sure, the waiting day is settled, got " + Arrays.toString(ledger.owed(owner)));
        ledger.accrueAt(owner, t0 + DAY, true);
        h.assertTrue(near(ledger.owed(owner), 25.6, 76.8, 2, 2), "and only once, got " + Arrays.toString(ledger.owed(owner)));
        h.succeed();
    }

    private static String snbtOf(int known, int unknown) {
        StringBuilder b = new StringBuilder("{Prizes:[");
        for (int i = 0; i < known + unknown; i++) {
            b.append(i > 0 ? "," : "").append(i < known ? "{Item:{id:\"minecraft:diamond\",Count:1b},Weight:1}" : "{Item:{id:\"nomod:thing_" + i + "\",Count:1b},Weight:1}");
        }
        return b.append("]}").toString();
    }

    /** Audit fix: prizes kept for other servers count toward the 24, and a table of more than 24 loads only 24. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void unknown_prizes_count_toward_the_limit(GameTestHelper h) {
        VaultPrizes table = new VaultPrizes();
        h.assertTrue(table.applySnbt(snbtOf(1, 3)) && table.room() == VaultPrizes.MAX_PRIZES - 3, "3 kept for other servers leave room for 21, got " + table.room());
        java.util.List<VaultPrizes.Prize> full = new java.util.ArrayList<>();
        for (int i = 0; i < VaultPrizes.MAX_PRIZES; i++) {
            full.add(new VaultPrizes.Prize(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND), 1));
        }
        table.set(full);
        h.assertTrue(table.prizes().size() == 21 && table.unknownCount() == 3, "a 24-prize save keeps 21 here and the 3 others, got " + table.prizes().size());
        h.assertTrue(com.mrgregles.bsp_core.vault.VaultPrizeAdmin.check(full.stream().map(VaultPrizes.Prize::stack).toList(), java.util.Collections.nCopies(24, 1), table.room()) != null,
                "the admin tab's save of 24 is refused while 3 are kept for other servers");
        VaultPrizes big = new VaultPrizes();
        h.assertTrue(big.applySnbt(snbtOf(20, 10)) && big.prizes().size() + big.unknownCount() == VaultPrizes.MAX_PRIZES, "a table of 30 loads only 24");
        h.succeed();
    }
}
