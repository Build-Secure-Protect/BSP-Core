package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.chunk.ChunkLedger;
import com.mrgregles.bsp_core.chunk.ChunkLoading;
import com.mrgregles.bsp_core.data.TotemLedger;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.totem.ShatterTotemBlock;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import com.mrgregles.bsp_core.totem.TotemMint;
import com.mrgregles.bsp_core.totem.TotemOwner;
import com.mrgregles.bsp_core.totem.TotemUpgrades;
import com.mrgregles.bsp_core.totem.TotemUpgrades.Buff;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GameTests for a player's main totem: the one whose Anchor may go past level 1 and which may buy Survey. Mock players are new
 * each run; the totems are unowned, removed and forgotten by the ledger in a {@code finally}.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class MainTotemGameTests {
    private static final String EMPTY = "empty", BATCH = "main_totem";

    /** Places a totem minted for {@code owner} at {@code pos}, as if its owner got it at {@code ownedSince} (ms). */
    private static ShatterTotemBlockEntity place(GameTestHelper h, BlockPos pos, ServerPlayer owner, long ownedSince, List<ShatterTotemBlockEntity> made) {
        ItemStack item = TotemMint.fresh(owner);
        item.getOrCreateTag().putLong(TotemOwner.TAG_OWNED_SINCE, ownedSince);
        return place(h, pos, item, made);
    }

    private static ShatterTotemBlockEntity place(GameTestHelper h, BlockPos pos, ItemStack item, List<ShatterTotemBlockEntity> made) {
        h.setBlock(pos, ModBlocks.SHATTER_TOTEM.get());
        ShatterTotemBlockEntity be = (ShatterTotemBlockEntity) h.getBlockEntity(pos);
        be.loadFromStack(item);
        made.add(be);
        return be;
    }

    private static boolean main(GameTestHelper h, ShatterTotemBlockEntity be) {
        return ChunkLoading.isMain(h.getLevel(), be.getBlockPos());
    }

    private static void steal(ShatterTotemBlockEntity be, ServerPlayer thief) {
        be.setOwner(new TotemOwner(thief.getUUID(), thief.getGameProfile().getName())); // what a finished steal does
    }

    /** Unowns and removes the test's totems and lets the ledger forget them: the test world keeps its data between runs. */
    private static void cleanUp(GameTestHelper h, List<ShatterTotemBlockEntity> made) {
        TotemLedger ledger = TotemLedger.get(h.getLevel().getServer());
        for (ShatterTotemBlockEntity be : made) {
            UUID id = be.totemId();
            if (!be.isRemoved()) {
                be.setOwner(null);
                h.getLevel().setBlock(be.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
            }
            if (id != null) {
                ledger.forget(id);
            }
        }
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_totem_no_longer_standing_never_stays_the_main_one(GameTestHelper h) {
        var server = h.getLevel().getServer();
        ServerPlayer owner = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        ChunkLedger chunks = ChunkLedger.get(server);
        List<ShatterTotemBlockEntity> made = new ArrayList<>();
        // a totem whose block went without its own removal (a world edit, a region reset): the totem ledger forgot it, the chunk ledger kept it
        GlobalPos ghost = GlobalPos.of(h.getLevel().dimension(), h.absolutePos(new BlockPos(1, 1, 1)).offset(40_000, 0, 40_000));
        ChunkLedger.Totem gone = new ChunkLedger.Totem();
        gone.owner = owner.getUUID();
        gone.since = 1; // held longer than any other
        gone.anchor = 3;
        gone.id = UUID.randomUUID();
        chunks.totems.put(ghost, gone);
        chunks.mains.put(owner.getUUID(), gone.id);
        try {
            ShatterTotemBlockEntity real = place(h, new BlockPos(1, 1, 1), owner, System.currentTimeMillis(), made);
            real.setTier(TotemUpgrades.MAX_TIER);
            real.setUpgradeLevel(Buff.ANCHOR, 1);
            h.assertTrue(main(h, real), "the totem that stands is the main one, not the forgotten entry");
            ChunkLoading.prune(server); // as the server does at start and every five seconds
            h.assertTrue(!chunks.totems.containsKey(ghost), "the forgotten totem's entry is dropped from the chunk ledger");
            real.setUpgradeLevel(Buff.ANCHOR, 3);
            h.assertTrue(main(h, real), "it stays main at Anchor 3 (buying is checked in only_the_main_totem_can_buy_anchor_past_one_and_survey)");
            h.assertTrue(real.totemId().equals(chunks.mains.get(owner.getUUID())), "it is remembered as the main totem");
        } finally {
            chunks.totems.remove(ghost);
            chunks.mains.remove(owner.getUUID());
            cleanUp(h, made);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void the_first_totem_placed_stays_main_whatever_the_owned_since_clock_says(GameTestHelper h) {
        ServerPlayer owner = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        List<ShatterTotemBlockEntity> made = new ArrayList<>();
        long now = System.currentTimeMillis();
        try {
            ShatterTotemBlockEntity first = place(h, new BlockPos(1, 1, 1), owner, now, made);
            first.setTier(1);
            // three more, one carrying an older time than the first (a totem from before chunk loading carries 0) and two tied with it;
            // the older one is also the higher tier, so the pick alone (Anchor, tier, held longest) would choose it: only the memory keeps the first
            ShatterTotemBlockEntity older = place(h, new BlockPos(3, 1, 1), owner, 0, made);
            older.setTier(TotemUpgrades.MAX_TIER);
            ShatterTotemBlockEntity tiedA = place(h, new BlockPos(1, 1, 3), owner, now, made);
            ShatterTotemBlockEntity tiedB = place(h, new BlockPos(3, 1, 3), owner, now, made);
            h.assertTrue(main(h, first), "the first totem placed is the main one");
            h.assertTrue(!main(h, older) && !main(h, tiedA) && !main(h, tiedB), "the others are not, whatever their times");
            first.setUpgradeLevel(Buff.ANCHOR, 3);
            first.setUpgradeLevel(Buff.SURVEY, 2);
            h.assertTrue(main(h, first), "it stays main as its Anchor and Survey rise");

            // picked up and placed again elsewhere: still the main one
            ItemStack carried = ShatterTotemBlock.toItemStack(h.getLevel(), first.getBlockPos());
            first.setOwner(null);
            h.getLevel().setBlock(first.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
            h.assertTrue(main(h, older), "while it is carried, another totem stands in as main");
            older.setUpgradeLevel(Buff.ANCHOR, 3); // bought while it stood in: equal Anchor, higher tier
            ShatterTotemBlockEntity moved = place(h, new BlockPos(5, 1, 5), carried, made);
            h.assertTrue(main(h, moved) && !main(h, older), "placed again, it is the main one again, though the other now has as much Anchor and a higher tier");
        } finally {
            cleanUp(h, made);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void only_the_main_totem_can_buy_anchor_past_one_and_survey(GameTestHelper h) {
        ServerPlayer owner = MockPlayers.serverPlayer(h, GameType.CREATIVE); // creative: the upgrades cost nothing, the rules still apply
        List<ShatterTotemBlockEntity> made = new ArrayList<>();
        long now = System.currentTimeMillis();
        try {
            ShatterTotemBlockEntity main = place(h, new BlockPos(1, 1, 1), owner, now - 1_000, made), second = place(h, new BlockPos(3, 1, 1), owner, now, made);
            for (ShatterTotemBlockEntity be : List.of(main, second)) {
                be.setTier(TotemUpgrades.MAX_TIER);
                be.setUpgradeLevel(Buff.OUTPUT, 2); // Anchor follows Output 2 on the tree
                be.setUpgradeLevel(Buff.ANCHOR, 1);
            }
            second.tryBuy(owner, Buff.ANCHOR.ordinal());
            h.assertTrue(second.getUpgradeLevel(Buff.ANCHOR) == 1, "the second totem is refused Anchor 2: " + second.getUpgradeLevel(Buff.ANCHOR));
            main.tryBuy(owner, Buff.ANCHOR.ordinal());
            h.assertTrue(main.getUpgradeLevel(Buff.ANCHOR) == 2, "the main totem buys Anchor 2: " + main.getUpgradeLevel(Buff.ANCHOR) + ", main " + main(h, main)
                    + ", why not " + TotemUpgrades.whyNot(Buff.ANCHOR, main::getUpgradeLevel, main.getTier(), owner) + ", price " + TotemUpgrades.price(Buff.ANCHOR, 1));
            main.tryBuy(owner, Buff.SURVEY.ordinal());
            h.assertTrue(main.getUpgradeLevel(Buff.SURVEY) == 1, "and Survey: " + main.getUpgradeLevel(Buff.SURVEY));
            second.setUpgradeLevel(Buff.ANCHOR, 2); // as if bought while it stood in
            second.tryBuy(owner, Buff.SURVEY.ordinal());
            h.assertTrue(second.getUpgradeLevel(Buff.SURVEY) == 0, "the second totem is refused Survey even with Anchor 2");
        } finally {
            cleanUp(h, made);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public void a_stolen_main_totem_hands_main_to_the_owners_next_totem(GameTestHelper h) {
        ServerPlayer owner = MockPlayers.serverPlayer(h, GameType.SURVIVAL), thief = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        List<ShatterTotemBlockEntity> made = new ArrayList<>();
        long now = System.currentTimeMillis();
        try {
            ShatterTotemBlockEntity starter = place(h, new BlockPos(1, 1, 1), owner, now - 3_000, made);
            starter.setTier(TotemUpgrades.MAX_TIER);
            starter.setUpgradeLevel(Buff.ANCHOR, 3);
            ShatterTotemBlockEntity second = place(h, new BlockPos(3, 1, 1), owner, now - 2_000, made);
            second.setTier(2);
            ShatterTotemBlockEntity third = place(h, new BlockPos(5, 1, 1), owner, now - 1_000, made);
            ShatterTotemBlockEntity thiefs = place(h, new BlockPos(1, 1, 5), thief, now, made);
            h.assertTrue(main(h, starter) && !main(h, second) && main(h, thiefs), "each player's first totem is their main one");

            steal(starter, thief);
            h.assertTrue(main(h, second), "with the main totem stolen, the owner's highest-tier totem left becomes main");
            h.assertTrue(!main(h, third), "and only that one");
            second.setUpgradeLevel(Buff.ANCHOR, 3);
            h.assertTrue(main(h, second), "it stays main at Anchor 3");
            h.assertTrue(main(h, thiefs) && !main(h, starter), "the thief keeps their own main totem; the stolen one loads its own chunk only");

            steal(starter, owner); // stolen back
            h.assertTrue(main(h, second) && !main(h, starter), "stolen back, it does not take main from the totem the owner has built up since");
        } finally {
            cleanUp(h, made);
        }
        h.succeed();
    }
}
