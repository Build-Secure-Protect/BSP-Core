package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.bank.BankTransaction;
import com.mrgregles.bsp_core.coin.CoinStamp;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.menu.TotemUpgradeMenu;
import com.mrgregles.bsp_core.network.UpgradeRequestPacket;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.team.Team;
import com.mrgregles.bsp_core.team.Teams;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import com.mrgregles.bsp_core.totem.TeamSurcharge;
import com.mrgregles.bsp_core.totem.TotemMint;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * GameTests for the team surcharge on totem upgrades: the percent by the owner's team size, the Pieces it costs, paying it from
 * the buyer's bank when a teammate raises a totem, refusing when the bank is short, nothing for a solo owner, and the numbers sent
 * to the placed and carried totem screens. Each test makes its own players and team; totems are removed and forgotten.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class TeamSurchargeGameTests {
    private static final String EMPTY = "empty", BATCH = "surcharge";

    private static ServerPlayer survivor(GameTestHelper h) {
        return MockPlayers.serverPlayer(h, GameType.SURVIVAL);
    }

    private static Team team(GameTestHelper h, ServerPlayer leader, ServerPlayer... mates) {
        String n = "Fee " + Long.toString(System.nanoTime() % 1_000_000_000L, 36);
        h.assertTrue(Teams.create(leader, n.substring(0, Math.min(16, n.length())), ChatFormatting.RED).ok(), "a team is made");
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

    private static ShatterTotemBlockEntity totem(GameTestHelper h, ServerPlayer owner) {
        BlockPos pos = new BlockPos(3, 1, 3);
        h.setBlock(pos, ModBlocks.SHATTER_TOTEM.get());
        ShatterTotemBlockEntity be = (ShatterTotemBlockEntity) h.getBlockEntity(pos);
        be.loadFromStack(TotemMint.fresh(owner));
        return be;
    }

    private static void remove(GameTestHelper h, ShatterTotemBlockEntity be) {
        if (be == null) {
            return;
        }
        UUID id = be.totemId();
        if (!be.isRemoved()) {
            be.setOwner(null);
            h.getLevel().setBlock(be.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
        }
        if (id != null) {
            TotemLedger.get(h.getLevel().getServer()).forget(id);
        }
    }

    /** Gives the player {@code pieces} in the bank (the test world is local: the bank answers at once). */
    private static void fund(MinecraftServer server, ServerPlayer p, long pieces) {
        boolean[] ok = new boolean[1];
        Bank.give(server, p.getUUID(), pieces, BankTransaction.Type.ADMIN, "test", "test funds", r -> ok[0] = r.ok());
        if (!ok[0]) {
            throw new IllegalStateException("the bank did not take the test funds");
        }
    }

    private static int coins(ServerPlayer p, CoinTier tier) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).is(tier.coin())) {
                n += p.getInventory().getItem(i).getCount();
            }
        }
        return n;
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void surcharge_is_six_percent_a_member_after_the_first_in_pieces(GameTestHelper h) {
        int[] want = {0, 6, 12, 18, 24};
        for (int size = 1; size <= want.length; size++) {
            h.assertTrue(TeamSurcharge.percentFor(size) == want[size - 1], "a team of " + size + " pays +" + want[size - 1] + "%, got " + TeamSurcharge.percentFor(size));
        }
        TotemUpgrades.Price gate = TotemUpgrades.gatePrice(0);
        long expect = Math.round(gate.coins() * gate.coin().value() * TeamSurcharge.perCopper() * 0.18);
        h.assertTrue(TeamSurcharge.pieces(gate, 18, TeamSurcharge.perCopper()) == expect && expect > 0,
                "the gate's coins' copper value x Pieces per copper x 18%: " + expect + ", got " + TeamSurcharge.pieces(gate, 18, TeamSurcharge.perCopper()));
        h.succeed();
    }

    /** A teammate raising the owner's totem in a team of 4 pays +18% in Pieces from their own bank, besides the coins and XP; the screens are told 18%. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void a_teammate_raising_a_totem_pays_the_surcharge_from_the_bank(GameTestHelper h) {
        var server = h.getLevel().getServer();
        ServerPlayer a = survivor(h), b = survivor(h), c = survivor(h), d = survivor(h);
        ShatterTotemBlockEntity be = null;
        try {
            Team t = team(h, a, b, c, d);
            be = totem(h, a);
            h.assertTrue(be.getUpdateTag().getInt("Surcharge") == 18, "the totem's screen is told +18%, got " + be.getUpdateTag().getInt("Surcharge"));
            TotemUpgrades.Price price = TotemUpgrades.gatePrice(0);
            long fee = TeamSurcharge.pieces(price, 18, TeamSurcharge.perCopper());
            b.giveExperienceLevels(price.xp() * 2);
            b.getInventory().add(CoinStamp.stamp(new ItemStack(price.coin().coin(), price.coins()), t));
            fund(server, b, fee - 1);
            long before = Bank.pieces(server, b.getUUID());
            be.tryBuy(b, UpgradeRequestPacket.RAISE_TIER);
            h.assertTrue(be.getTier() == 0 && Bank.pieces(server, b.getUUID()) == before && coins(b, price.coin()) == price.coins(),
                    "one Piece short: nothing is bought and nothing is taken");
            fund(server, b, 1);
            int xp = b.experienceLevel;
            be.tryBuy(b, UpgradeRequestPacket.RAISE_TIER);
            h.assertTrue(be.getTier() == 1, "raised with the surcharge paid, tier " + be.getTier());
            h.assertTrue(Bank.pieces(server, b.getUUID()) == before + 1 - fee && coins(b, price.coin()) == 0 && b.experienceLevel == xp - price.xp(),
                    "the bank paid " + fee + " Pieces, besides the coins and XP; bank now " + Bank.pieces(server, b.getUUID()));
        } finally {
            remove(h, be);
            disband(a);
        }
        h.succeed();
    }

    /** A solo owner pays no surcharge; a carried totem's screen gets the owner's team's percent from the server. */
    @GameTest(template = EMPTY, batch = BATCH)
    public void a_solo_owner_pays_nothing_and_a_carried_totem_gets_its_percent(GameTestHelper h) {
        var server = h.getLevel().getServer();
        ServerPlayer a = survivor(h), b = survivor(h), c = survivor(h);
        ShatterTotemBlockEntity be = null;
        try {
            be = totem(h, a);
            TotemUpgrades.Price price = TotemUpgrades.gatePrice(0);
            a.giveExperienceLevels(price.xp() * 2);
            a.getInventory().add(new ItemStack(price.coin().coin(), price.coins()));
            long before = Bank.pieces(server, a.getUUID());
            be.tryBuy(a, UpgradeRequestPacket.RAISE_TIER);
            h.assertTrue(be.getTier() == 1 && Bank.pieces(server, a.getUUID()) == before, "alone: raised, and the bank is untouched");

            team(h, a, b, c);
            b.setItemInHand(InteractionHand.MAIN_HAND, TotemMint.fresh(a));
            TotemUpgradeMenu menu = new TotemUpgradeMenu(0, b.getInventory(), InteractionHand.MAIN_HAND);
            h.assertTrue(menu.surchargePercent() == 12 && menu.surchargeRate() == TeamSurcharge.perCopper(),
                    "carrying the owner's totem in a team of 3: +12% at " + TeamSurcharge.perCopper() + ", got " + menu.surchargePercent() + " at " + menu.surchargeRate());
        } finally {
            b.getInventory().clearContent();
            remove(h, be);
            disband(a);
        }
        h.succeed();
    }
}
