package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.api.BspBoards;
import com.mrgregles.bsp_core.bounty.Bounty;
import com.mrgregles.bsp_core.bounty.BountySummary;
import com.mrgregles.bsp_core.client.ClientBounties;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.score.ScoreScreenBlockEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** GameTests for the Bounties board on Score Screens and the Most Wanted look (task 9). Batch {@code bounty}. */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class BountyBoardLinesGameTests {
    private static final String EMPTY = "empty";
    private static final long H = 3_600_000L, D = 24 * H;

    @GameTest(template = EMPTY, batch = "bounty")
    public void the_bounties_board_gives_one_line_per_target_with_their_head_total_and_soonest_end(GameTestHelper h) {
        long now = System.currentTimeMillis();
        UUID ray = UUID.randomUUID(), jo = UUID.randomUUID();
        List<BountySummary.Entry> entries = List.of(
                new BountySummary.Entry("b1", jo, "Jo", "A", Bounty.PIECES, 80_000, 0, 0, now - H, now + 3 * D, 2),
                new BountySummary.Entry("b2", ray, "Ray", "B", Bounty.PIECES, 45_000, 10_000, now + D, now - H, now + 4 * D, 3),
                new BountySummary.Entry("b3", jo, "Jo", "C", Bounty.PIECES, 40_000, 0, 0, now - H, now + 6 * H, 3),
                new BountySummary.Entry("b4", ray, "Ray", "D", CoinTier.DIAMOND.ordinal(), 3, 0, 0, now - H, now + 2 * D, 0),
                new BountySummary.Entry("b5", UUID.randomUUID(), "Gone", "E", Bounty.PIECES, 9_999_999, 0, 0, now - 2 * D, now - 1, 0));
        try {
            ClientBounties.accept(now, entries);
            List<BspBoards.BoardLine> lines = ClientBounties.lines();
            h.assertTrue(lines.size() == 2, "one line per target, the ended one left out: " + lines.size());
            BspBoards.BoardLine first = lines.get(0), second = lines.get(1);
            h.assertTrue(first.text().getString().startsWith("Jo") && first.value().getString().equals("120,000"), "Jo first, both bounties added up: " + first.text().getString()
                    + " " + first.value().getString());
            h.assertTrue(first.icon().is(Items.PLAYER_HEAD) && first.icon().getTag() != null && first.icon().getTag().contains("SkullOwner"), "Jo's head as the icon");
            h.assertTrue(first.note() != null && first.note().getString().matches("[56] h .*") && first.note().getString().contains("5 hunting"), "the soonest end (6 h, read a moment later) and the hunters: "
                    + first.note().getString());
            h.assertTrue(second.value().getString().contains("45,000") && second.value().getString().contains("3 ") && second.note().getString().startsWith("BONUS"),
                    "Ray: pieces and coins, and BONUS while it runs: " + second.value().getString() + " / " + second.note().getString());
        } finally {
            ClientBounties.accept(now, List.of());
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = "bounty")
    public void a_score_screen_may_take_the_most_wanted_look(GameTestHelper h) {
        h.assertTrue(ScoreScreenBlockEntity.LOOK_SPOTLIGHT == 3 && ScoreScreenBlockEntity.LOOKS == 4, "a fourth look");
        var pos = new net.minecraft.core.BlockPos(1, 1, 1);
        h.setBlock(pos, com.mrgregles.bsp_core.registry.ModBlocks.SCORE_SCREEN.get());
        if (h.getBlockEntity(pos) instanceof ScoreScreenBlockEntity be) {
            be.configure(ScoreScreenBlockEntity.MODE_PAGE, 0, 0, 100, 40, "MOST WANTED", ClientBounties.BOARD.toString(), ScoreScreenBlockEntity.LOOK_SPOTLIGHT, 10, List.of());
            h.assertTrue(be.look() == ScoreScreenBlockEntity.LOOK_SPOTLIGHT, "kept");
            be.configure(ScoreScreenBlockEntity.MODE_PAGE, 0, 0, 100, 40, "X", "", 9, 10, List.of());
            h.assertTrue(be.look() == ScoreScreenBlockEntity.LOOKS - 1, "a look past the last is clamped");
        } else {
            h.fail("no Score Screen block entity");
        }
        h.succeed();
    }
}
