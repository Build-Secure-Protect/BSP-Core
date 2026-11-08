package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.coin.CoinFactoryBlock;
import com.mrgregles.bsp_core.coin.CoinFactoryBlockEntity;
import com.mrgregles.bsp_core.coin.CoinStamp;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.registry.ModItems;
import com.mrgregles.bsp_core.team.Team;
import com.mrgregles.bsp_core.team.Teams;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import com.mrgregles.bsp_core.totem.TotemMint;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import com.mrgregles.bsp_core.network.UpgradeRequestPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GameTests for the coin stamp: a factory stamps the coins it presses with its owner's team, a teammate's totem takes only this
 * team's coins (and clean ones), and your own totem takes any, spending those pressed outside your team first. Every test makes its
 * own players and team; totems are unowned, removed and forgotten in a {@code finally}.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class CoinStampGameTests {
    private static final String EMPTY = "empty", BATCH = "coin_stamp";

    private static ServerPlayer survivor(GameTestHelper h) {
        return MockPlayers.serverPlayer(h, GameType.SURVIVAL);
    }

    private static Team team(GameTestHelper h, ServerPlayer leader, ServerPlayer... mates) {
        String n = "Stamp " + Long.toString(System.nanoTime() % 1_000_000_000L, 36);
        h.assertTrue(Teams.create(leader, n.substring(0, Math.min(16, n.length())), ChatFormatting.AQUA).ok(), "a team is made");
        for (ServerPlayer m : mates) {
            h.assertTrue(Teams.invite(leader, m).ok() && Teams.accept(m).ok(), "a mate joins");
        }
        return Teams.teamOf(h.getLevel().getServer(), leader.getUUID());
    }

    private static void disband(ServerPlayer leader) {
        if (Teams.teamOf(leader.server, leader.getUUID()) != null) {
            Teams.disband(leader);
        }
    }

    private static ItemStack coins(CoinTier tier, int n, Team team) {
        return CoinStamp.stamp(new ItemStack(tier.coin(), n), team);
    }

    private static ItemStack outsideCoins(CoinTier tier, int n) {
        return CoinStamp.stamp(new ItemStack(tier.coin(), n), null);
    }

    private static ShatterTotemBlockEntity totem(GameTestHelper h, BlockPos pos, ServerPlayer owner) {
        h.setBlock(pos, ModBlocks.SHATTER_TOTEM.get());
        ShatterTotemBlockEntity be = (ShatterTotemBlockEntity) h.getBlockEntity(pos);
        be.loadFromStack(TotemMint.fresh(owner));
        return be;
    }

    private static void remove(GameTestHelper h, ShatterTotemBlockEntity be) {
        UUID id = be.totemId();
        if (!be.isRemoved()) {
            be.setOwner(null);
            h.getLevel().setBlock(be.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
        }
        if (id != null) {
            TotemLedger.get(h.getLevel().getServer()).forget(id);
        }
    }

    /** The team surcharge (a team of 2 pays +6%) comes from the buyer's bank: give them enough for it. */
    private static void fundSurcharge(GameTestHelper h, ServerPlayer p, TotemUpgrades.Price price) {
        long fee = com.mrgregles.bsp_core.totem.TeamSurcharge.pieces(price, 100, com.mrgregles.bsp_core.totem.TeamSurcharge.perCopper());
        com.mrgregles.bsp_core.bank.Bank.give(h.getLevel().getServer(), p.getUUID(), Math.max(1, fee), com.mrgregles.bsp_core.bank.BankTransaction.Type.ADMIN, "test", "test funds", r -> {});
    }

    private static String key(Component c) {
        return c != null && c.getContents() instanceof TranslatableContents tc ? tc.getKey() : String.valueOf(c);
    }

    private static int count(ServerPlayer p, CoinTier tier, java.util.function.Predicate<String> stamp) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (st.is(tier.coin()) && stamp.test(CoinStamp.of(st))) {
                n += st.getCount();
            }
        }
        return n;
    }

    /** One finished press on {@code slice}, by the clock handed to it. */
    private static void press(CoinFactoryBlockEntity slice) {
        CoinTier tier = CoinTier.COPPER;
        for (int i = 0; i < 1000 && slice.poolStored() < tier.energyPerCoin(); i++) {
            slice.poolReceive(Integer.MAX_VALUE, false);
        }
        slice.getItems().setStackInSlot(CoinFactoryBlockEntity.SLOT_INPUT, new ItemStack(ModItems.BLANKS.get(tier).get()));
        long t0 = System.currentTimeMillis();
        slice.advance(t0);
        slice.advance(t0 + tier.pressMillis() + 1000);
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public void pressed_coins_carry_the_owners_team_and_change_with_it(GameTestHelper h) {
        ServerPlayer a = survivor(h), b = survivor(h);
        BlockPos c = new BlockPos(4, 1, 3);
        try {
            Team t = team(h, a, b);
            h.setBlock(c, ModBlocks.COIN_FACTORY.get().defaultBlockState().setValue(CoinFactoryBlock.FACING, Direction.NORTH));
            h.setBlock(c.south(), ModBlocks.FACTORY_FRAME.get());
            h.setBlock(c.south(2), ModBlocks.FACTORY_BLANK_HATCH.get());
            h.setBlock(c.above(), ModBlocks.FACTORY_FRAME.get());
            h.setBlock(c.above().south(), ModBlocks.FACTORY_PRESS.get());
            h.setBlock(c.above().south(2), ModBlocks.FACTORY_POWER_PORT.get());
            CoinFactoryBlockEntity slice = (CoinFactoryBlockEntity) h.getBlockEntity(c);
            slice.setOwner(a);
            slice.checkStructure();
            h.assertTrue(slice.isFormed(), "the slice is formed");
            press(slice);
            ItemStack tray = slice.getItems().getStackInSlot(CoinFactoryBlockEntity.SLOT_OUTPUT);
            h.assertTrue(tray.getCount() == 1 && t.id.toString().equals(CoinStamp.of(tray)) && t.name.equals(CoinStamp.teamName(tray)),
                    "the coin is stamped with the owner's team, got " + tray.getTag());
            List<Component> tip = new ArrayList<>();
            tray.getItem().appendHoverText(tray, null, tip, TooltipFlag.NORMAL);
            h.assertTrue(tip.stream().anyMatch(l -> key(l).equals("tooltip.bsp_core.coin.team")), "the tooltip names the team: " + tip);

            disband(a); // a is alone now: the next coin is pressed outside a team and cannot join the team's coins on the tray
            press(slice);
            h.assertTrue(slice.getItems().getStackInSlot(CoinFactoryBlockEntity.SLOT_OUTPUT).getCount() == 1
                    && slice.stateCode() == CoinFactoryBlockEntity.STATE_TRAY_OTHER_TEAM, "the press waits for the team's coin to be taken: state " + slice.stateCode());
            slice.getItems().setStackInSlot(CoinFactoryBlockEntity.SLOT_OUTPUT, ItemStack.EMPTY);
            press(slice);
            tray = slice.getItems().getStackInSlot(CoinFactoryBlockEntity.SLOT_OUTPUT);
            h.assertTrue(tray.getCount() >= 1 && CoinStamp.NONE.equals(CoinStamp.of(tray)), "pressed outside a team now, got " + tray.getTag());
            tip.clear();
            tray.getItem().appendHoverText(tray, null, tip, TooltipFlag.NORMAL);
            h.assertTrue(tip.stream().anyMatch(l -> key(l).equals("tooltip.bsp_core.coin.no_team")), "the tooltip says outside a team: " + tip);
            h.assertFalse(ItemStack.isSameItemSameTags(tray, coins(CoinTier.COPPER, 1, t)), "differently stamped coins do not stack");
        } finally {
            h.setBlock(c, Blocks.AIR);
            disband(a);
        }
        h.succeed();
    }

    /** On a teammate's totem, coins pressed outside the team do not pay (with the reason), this team's and clean ones do, and only those are taken. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void a_teammates_totem_takes_only_this_teams_or_clean_coins(GameTestHelper h) {
        ServerPlayer a = survivor(h), b = survivor(h);
        ShatterTotemBlockEntity be = null;
        try {
            Team t = team(h, a, b);
            be = totem(h, new BlockPos(3, 1, 3), a);
            TotemUpgrades.Price price = TotemUpgrades.gatePrice(0);
            h.assertTrue(price != null && price.coin() == CoinTier.COPPER, "Tier I to II costs Copper coins");
            int need = price.coins();
            b.giveExperienceLevels(price.xp() * 3);
            fundSurcharge(h, b, price);
            b.getInventory().add(outsideCoins(CoinTier.COPPER, need));
            CoinStamp.Purse purse = CoinStamp.forTotem(b.server, b.getUUID(), a.getUUID());
            Component why = TotemUpgrades.whyNotGate(0, b, purse);
            h.assertTrue(key(why).equals("gui.bsp_core.tree.why.stamp"), "the reason names coins pressed outside the team, got " + key(why));
            be.tryBuy(b, UpgradeRequestPacket.RAISE_TIER);
            h.assertTrue(be.getTier() == 0 && count(b, CoinTier.COPPER, s -> true) == need, "nothing is bought or taken");

            // a client sees the same from the synced block
            ShatterTotemBlockEntity cb = new ShatterTotemBlockEntity(be.getBlockPos(), be.getBlockState());
            cb.load(be.getUpdateTag());
            h.assertTrue(!cb.clientPurse(b.getUUID()).usable(outsideCoins(CoinTier.COPPER, 1))
                    && cb.clientPurse(b.getUUID()).usable(coins(CoinTier.COPPER, 1, t)) && cb.clientPurse(a.getUUID()).usable(outsideCoins(CoinTier.COPPER, 1)),
                    "the client's purse: the owner pays with any coin, a teammate only with the team's");

            b.getInventory().add(coins(CoinTier.COPPER, need - 1, t));
            b.getInventory().add(new ItemStack(CoinTier.COPPER.coin(), 1)); // clean: pressed before stamps
            be.tryBuy(b, UpgradeRequestPacket.RAISE_TIER);
            h.assertTrue(be.getTier() == 1, "the team's and clean coins pay, tier " + be.getTier());
            h.assertTrue(count(b, CoinTier.COPPER, CoinStamp.NONE::equals) == need && count(b, CoinTier.COPPER, s -> !CoinStamp.NONE.equals(s)) == 0,
                    "only the team's and clean coins were taken; the others are still there");
        } finally {
            if (be != null) {
                remove(h, be);
            }
            disband(a);
        }
        h.succeed();
    }

    /** On your own totem any coins pay, and those pressed outside your team (or for another team) go first, clean ones last. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void own_totem_takes_any_coins_spending_those_from_outside_the_team_first(GameTestHelper h) {
        ServerPlayer a = survivor(h), b = survivor(h), x = survivor(h), y = survivor(h);
        ShatterTotemBlockEntity be = null;
        try {
            Team t = team(h, a, b);
            Team other = team(h, x, y);
            be = totem(h, new BlockPos(3, 1, 3), a);
            TotemUpgrades.Price price = TotemUpgrades.gatePrice(0);
            int need = price.coins();
            a.giveExperienceLevels(price.xp() * 3);
            fundSurcharge(h, a, price);
            a.getInventory().setItem(0, new ItemStack(CoinTier.COPPER.coin(), need)); // clean, in the first slot
            a.getInventory().setItem(1, coins(CoinTier.COPPER, need, t));
            a.getInventory().setItem(2, coins(CoinTier.COPPER, 1, other));
            a.getInventory().setItem(3, outsideCoins(CoinTier.COPPER, need - 1));
            h.assertTrue(TotemUpgrades.whyNotGate(0, a, CoinStamp.forTotem(a.server, a.getUUID(), a.getUUID())) == null, "any coins pay on your own totem");
            be.tryBuy(a, UpgradeRequestPacket.RAISE_TIER);
            h.assertTrue(be.getTier() == 1, "raised, tier " + be.getTier());
            h.assertTrue(count(a, CoinTier.COPPER, s -> s != null && !s.equals(t.id.toString())) == 0, "the other team's and outside coins went first");
            h.assertTrue(count(a, CoinTier.COPPER, s -> t.id.toString().equals(s)) == need && count(a, CoinTier.COPPER, s -> s == null) == need,
                    "the team's and clean coins are all still there");
        } finally {
            if (be != null) {
                remove(h, be);
            }
            disband(a);
            disband(x);
        }
        h.succeed();
    }

    /** Banked coins keep their stamps: a withdrawal hands back this team's coins first, then clean ones, then the rest. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void the_bank_keeps_coin_stamps_and_hands_back_this_teams_first(GameTestHelper h) {
        var server = h.getLevel().getServer();
        ServerPlayer a = survivor(h), b = survivor(h);
        CoinTier tier = CoinTier.DIAMOND;
        try {
            Team t = team(h, a, b);
            a.getInventory().setItem(0, outsideCoins(tier, 2));
            a.getInventory().setItem(1, new ItemStack(tier.coin(), 1));
            a.getInventory().setItem(2, coins(tier, 2, t));
            boolean[] ok = new boolean[1];
            com.mrgregles.bsp_core.bank.Bank.depositCoins(server, a, tier, 5, false, r -> ok[0] = r.ok());
            var account = com.mrgregles.bsp_core.bank.Bank.account(server, a.getUUID());
            h.assertTrue(ok[0] && count(a, tier, s -> true) == 0 && account.coins(tier) == 5, "five Diamond coins banked");
            h.assertTrue(account.stamps.get(t.id.toString()).counts[tier.ordinal()] == 2 && account.stamps.get(CoinStamp.NONE).counts[tier.ordinal()] == 2,
                    "the bank knows two are the team's and two outside a team");
            var saved = com.mrgregles.bsp_core.bank.BankAccount.load(account.save());
            h.assertTrue(saved.stamps.get(t.id.toString()).counts[tier.ordinal()] == 2, "the stamps are saved with the account");
            com.mrgregles.bsp_core.bank.Bank.withdrawCoins(server, a, tier, 3, r -> ok[0] = r.ok());
            h.assertTrue(ok[0] && count(a, tier, s -> t.id.toString().equals(s)) == 2 && count(a, tier, s -> s == null) == 1 && count(a, tier, CoinStamp.NONE::equals) == 0,
                    "three out: the team's two, then the clean one");
            com.mrgregles.bsp_core.bank.Bank.withdrawCoins(server, a, tier, 2, r -> ok[0] = r.ok());
            h.assertTrue(ok[0] && count(a, tier, CoinStamp.NONE::equals) == 2 && account.coins(tier) == 0 && account.stamps.isEmpty(),
                    "the last two come back still stamped outside a team, and the bank's breakdown is empty");
        } finally {
            a.getInventory().clearContent();
            disband(a);
        }
        h.succeed();
    }

    /** Change from a coin payment keeps the stamp of the coin it was broken from; new coins given to a player take that player's team. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void change_keeps_the_broken_coins_stamp_and_new_coins_take_the_players_team(GameTestHelper h) {
        ServerPlayer a = survivor(h), b = survivor(h);
        try {
            CoinTier big = CoinTier.GOLD;
            int value = big.value();
            h.assertTrue(value > 1, "a Gold coin is worth more than a Copper one");
            a.getInventory().setItem(0, outsideCoins(big, 1));
            h.assertTrue(com.mrgregles.bsp_core.coin.CoinWallet.pay(a, 1), "paid 1 with a Gold coin");
            int change = 0;
            for (int i = 0; i < a.getInventory().getContainerSize(); i++) {
                ItemStack st = a.getInventory().getItem(i);
                if (st.getItem() instanceof com.mrgregles.bsp_core.coin.ShatterCoinItem coin) {
                    h.assertTrue(CoinStamp.NONE.equals(CoinStamp.of(st)), "change carries the broken coin's stamp, got " + st.getTag());
                    change += coin.tier.value() * st.getCount();
                }
            }
            h.assertTrue(change == value - 1, "the change is worth " + (value - 1) + ", got " + change);
            a.getInventory().clearContent();
            Team t = team(h, a, b);
            com.mrgregles.bsp_core.coin.CoinWallet.give(a, value);
            h.assertTrue(count(a, big, s -> t.id.toString().equals(s)) == 1, "a new coin given to a team member is stamped for the team");
        } finally {
            a.getInventory().clearContent();
            disband(a);
        }
        h.succeed();
    }

    /** Coins kept from a vault someone else broke go back to the owner with their stamps; the rest come back clean. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void a_broken_vaults_coins_go_back_with_their_stamps(GameTestHelper h) {
        var ledger = com.mrgregles.bsp_core.vault.VaultLedger.get(h.getLevel().getServer());
        UUID owner = UUID.randomUUID();
        int[] counts = new int[CoinTier.values().length];
        counts[CoinTier.COPPER.ordinal()] = 3;
        ledger.addRecovered(owner, counts, List.of(outsideCoins(CoinTier.COPPER, 2)));
        List<ItemStack> back = ledger.takeRecoveredStacks(owner);
        int none = 0, clean = 0;
        for (ItemStack st : back) {
            if (CoinStamp.NONE.equals(CoinStamp.of(st))) {
                none += st.getCount();
            } else if (CoinStamp.of(st) == null) {
                clean += st.getCount();
            }
        }
        h.assertTrue(none == 2 && clean == 1, "two stamped and one clean, got " + none + " and " + clean);
        h.assertTrue(ledger.takeRecoveredStacks(owner).isEmpty(), "taken once");
        h.succeed();
    }

    /** A formed slice owned by {@code owner} at {@code c}, facing north. */
    private static CoinFactoryBlockEntity slice(GameTestHelper h, BlockPos c, ServerPlayer owner) {
        h.setBlock(c, ModBlocks.COIN_FACTORY.get().defaultBlockState().setValue(CoinFactoryBlock.FACING, Direction.NORTH));
        h.setBlock(c.south(), ModBlocks.FACTORY_FRAME.get());
        h.setBlock(c.south(2), ModBlocks.FACTORY_BLANK_HATCH.get());
        h.setBlock(c.above(), ModBlocks.FACTORY_FRAME.get());
        h.setBlock(c.above().south(), ModBlocks.FACTORY_PRESS.get());
        h.setBlock(c.above().south(2), ModBlocks.FACTORY_POWER_PORT.get());
        CoinFactoryBlockEntity f = (CoinFactoryBlockEntity) h.getBlockEntity(c);
        f.setOwner(owner);
        f.checkStructure();
        h.assertTrue(f.isFormed(), "the slice is formed");
        return f;
    }

    /**
     * A coin pressed outside the team, or for another team (a stolen one), is re-pressed in half the time and for half the RF and
     * comes out for the owner's team; coins pressed for the team and coins with no stamp are refused.
     */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public void re_pressing_takes_half_the_time_and_rf_and_stamps_for_the_owners_team(GameTestHelper h) {
        ServerPlayer a = survivor(h), b = survivor(h), x = survivor(h), y = survivor(h);
        BlockPos c = new BlockPos(4, 1, 3);
        try {
            Team t = team(h, a, b);
            Team other = team(h, x, y);
            CoinFactoryBlockEntity f = slice(h, c, a);
            var items = f.getItems();
            CoinTier tier = CoinTier.GOLD;
            h.assertTrue(items.insertItem(CoinFactoryBlockEntity.SLOT_INPUT, new ItemStack(tier.coin(), 1), true).getCount() == 1, "a coin with no stamp is refused");
            h.assertTrue(items.insertItem(CoinFactoryBlockEntity.SLOT_INPUT, coins(tier, 1, t), true).getCount() == 1, "a coin pressed for the team is refused");
            h.assertTrue(items.insertItem(CoinFactoryBlockEntity.SLOT_INPUT, coins(tier, 1, other), true).isEmpty(), "another team's coin is taken");
            h.assertTrue(items.insertItem(CoinFactoryBlockEntity.SLOT_INPUT, outsideCoins(tier, 2), false).isEmpty(), "coins pressed outside a team are taken");
            for (int i = 0; i < 1000 && f.poolStored() < tier.energyPerCoin(); i++) {
                f.poolReceive(Integer.MAX_VALUE, false);
            }
            int before = f.poolStored();
            long t0 = System.currentTimeMillis();
            f.advance(t0 - 1); // a new slice's first update only starts its clock
            f.advance(t0);
            h.assertTrue(f.isRepressing() && f.getJobTier() == tier && before - f.poolStored() == tier.energyPerCoin() / 2,
                    "a re-press starts and takes half the RF: " + (before - f.poolStored()) + " of " + tier.energyPerCoin());
            f.advance(t0 + tier.pressMillis() / 4);
            h.assertTrue(Math.abs(f.progressFraction() - 0.5f) < 0.01f, "a quarter of a press's time is half a re-press, got " + f.progressFraction());
            f.advance(t0 + tier.pressMillis() / 2 + 1000);
            ItemStack tray = f.getItems().getStackInSlot(CoinFactoryBlockEntity.SLOT_OUTPUT);
            h.assertTrue(tray.getCount() >= 1 && t.id.toString().equals(CoinStamp.of(tray)), "the coin comes out for the owner's team, got " + tray.getTag());
        } finally {
            h.setBlock(c, Blocks.AIR);
            disband(a);
            disband(x);
        }
        h.succeed();
    }

    /** A coin waiting to be re-pressed stays put once the owner leaves the team (it needs no re-press then), and shift-click puts coins to re-press into a lane. */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public void a_coin_waits_when_the_owner_has_no_team_and_shift_click_fills_a_lane(GameTestHelper h) {
        ServerPlayer a = survivor(h), b = survivor(h);
        BlockPos c = new BlockPos(4, 1, 3);
        try {
            team(h, a, b);
            CoinFactoryBlockEntity f = slice(h, c, a);
            var menu = new com.mrgregles.bsp_core.coin.CoinFactoryMenu(0, a.getInventory(), f, List.of(f));
            a.getInventory().setItem(9, outsideCoins(CoinTier.COPPER, 3)); // the first main inventory slot: menu slot 2 behind one lane's two
            a.getInventory().setItem(10, new ItemStack(CoinTier.COPPER.coin(), 3));
            menu.quickMoveStack(a, 3);
            h.assertTrue(f.getItems().getStackInSlot(CoinFactoryBlockEntity.SLOT_INPUT).isEmpty() && a.getInventory().getItem(10).getCount() == 3,
                    "shift-click leaves a coin with no stamp where it is");
            menu.quickMoveStack(a, 2);
            h.assertTrue(f.getItems().getStackInSlot(CoinFactoryBlockEntity.SLOT_INPUT).getCount() == 3 && a.getInventory().getItem(9).isEmpty(),
                    "shift-click puts the coins to re-press into the lane");
            disband(a); // alone now: re-pressing would only stamp them outside a team again
            h.assertTrue(f.stateCode() == CoinFactoryBlockEntity.STATE_COIN_DONE, "the lane says the coin needs no re-press, state " + f.stateCode());
            for (int i = 0; i < 1000 && f.poolStored() < CoinTier.COPPER.energyPerCoin(); i++) {
                f.poolReceive(Integer.MAX_VALUE, false);
            }
            long now = System.currentTimeMillis();
            f.advance(now - 1); // a new slice's first update only starts its clock
            f.advance(now);
            h.assertTrue(f.getJobTier() == null && f.getItems().getStackInSlot(CoinFactoryBlockEntity.SLOT_INPUT).getCount() == 3, "nothing starts");
            h.assertTrue(f.getItems().insertItem(CoinFactoryBlockEntity.SLOT_INPUT, outsideCoins(CoinTier.COPPER, 1), true).getCount() == 1, "and no more coins go in");
        } finally {
            h.setBlock(c, Blocks.AIR);
            a.getInventory().clearContent();
            disband(a);
        }
        h.succeed();
    }

    /** A renamed team's coins still join its tray, and a tray of coins pressed before stamps takes the owner's stamp instead of stopping the press. */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 200)
    public void a_renamed_team_keeps_pressing_and_an_old_tray_takes_the_stamp(GameTestHelper h) {
        ServerPlayer a = survivor(h), b = survivor(h);
        BlockPos c = new BlockPos(4, 1, 3);
        try {
            Team t = team(h, a, b);
            CoinFactoryBlockEntity f = slice(h, c, a);
            f.getItems().setStackInSlot(CoinFactoryBlockEntity.SLOT_OUTPUT, new ItemStack(CoinTier.COPPER.coin(), 2)); // pressed before stamps
            press(f);
            ItemStack tray = f.getItems().getStackInSlot(CoinFactoryBlockEntity.SLOT_OUTPUT);
            h.assertTrue(tray.getCount() == 3 && t.id.toString().equals(CoinStamp.of(tray)), "the old tray took the coin and the team's stamp, got " + tray.getCount() + " " + tray.getTag());
            String renamed = ("Re " + Long.toString(System.nanoTime() % 1_000_000L, 36));
            h.assertTrue(Teams.rename(a, renamed).ok(), "the team is renamed");
            press(f);
            tray = f.getItems().getStackInSlot(CoinFactoryBlockEntity.SLOT_OUTPUT);
            h.assertTrue(tray.getCount() == 4 && t.id.toString().equals(CoinStamp.of(tray)) && renamed.equals(CoinStamp.teamName(tray)),
                    "after a rename the coin still joins the tray, which shows the new name: " + tray.getCount() + " " + tray.getTag());
        } finally {
            h.setBlock(c, Blocks.AIR);
            disband(a);
        }
        h.succeed();
    }
}
