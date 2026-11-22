package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.score.ScoreScreenBlockEntity;
import com.mrgregles.bsp_core.score.VipList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * GameTests for the Score Screen's VIPs board: who is on it (members of the configured LuckPerms groups and of groups inheriting
 * them, sorted by name), what a failed or overlapping LuckPerms answer does, and when screens are told. LuckPerms is replaced by
 * {@link VipList#source}; the test world has no LuckPerms.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class VipBoardGameTests {
    private static final String EMPTY = "empty", BATCH = "scores";

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new net.minecraft.gametest.framework.GameTestAssertException(what);
        }
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void vip_groups_include_the_groups_that_inherit_them(GameTestHelper h) {
        Map<String, Set<String>> parents = Map.of(
                "vip", Set.of("default"),
                "mvp", Set.of("vip", "default"),
                "legend", Set.of("mvp"),
                "staff", Set.of("default"),
                "default", Set.of());
        check(VipList.withHeirs(Set.of("vip"), parents).equals(Set.of("vip", "mvp", "legend")), "vip, mvp and legend: " + VipList.withHeirs(Set.of("vip"), parents));
        check(VipList.withHeirs(Set.of("missing"), parents).equals(Set.of("missing")), "an unknown group stays alone");
        check(VipList.withHeirs(Set.of(), parents).isEmpty(), "no groups, no VIPs");
        check(VipList.groups().contains("vip"), "the default group is vip: " + VipList.groups());
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void the_vip_list_follows_luckperms(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        List<VipList.Vip> before = VipList.vips();
        var oldSource = VipList.source;
        Deque<CompletableFuture<Map<UUID, String>>> answers = new ArrayDeque<>();
        List<Set<String>> asked = new ArrayList<>();
        int[] told = {0};
        Consumer<MinecraftServer> listener = s -> told[0]++;
        UUID zed = UUID.randomUUID(), alex = UUID.randomUUID(), mira = UUID.randomUUID(), nameless = UUID.randomUUID();
        CompletableFuture<Map<UUID, String>> slow = new CompletableFuture<>(), slower = new CompletableFuture<>();
        try {
            VipList.source = groups -> {
                asked.add(groups);
                return answers.isEmpty() ? CompletableFuture.completedFuture(Map.of()) : answers.poll();
            };
            VipList.CHANGED.add(listener);

            // members sorted by name ignoring case; a member with no name anywhere is left out
            answers.add(CompletableFuture.completedFuture(Map.of(zed, "zed", alex, "Alex", mira, "mira", nameless, "")));
            boolean[] ok = {false};
            VipList.refresh(server, r -> ok[0] = r);
            check(ok[0], "the answer came");
            check(asked.get(0).equals(VipList.groups()), "LuckPerms is asked about the configured groups: " + asked);
            check(names().equals(List.of("Alex", "mira", "zed")), "sorted, nameless left out: " + names());
            check(told[0] == 1, "screens told once: " + told[0]);
            check(commandCount(server) == 3, "/bsp vips counts 3");

            // the same answer tells nobody
            answers.add(CompletableFuture.completedFuture(Map.of(zed, "zed", alex, "Alex", mira, "mira")));
            VipList.refresh(server);
            check(told[0] == 1, "an unchanged list tells nobody: " + told[0]);

            // a failed answer keeps the last list and reports the failure
            answers.add(CompletableFuture.failedFuture(new IllegalStateException("storage down (test)")));
            boolean[] failed = {true};
            VipList.refresh(server, r -> failed[0] = r);
            check(!failed[0], "the failure is reported");
            check(names().equals(List.of("Alex", "mira", "zed")), "the last list is kept: " + names());

            // an ask while one is in flight waits and asks again after it, and both callers hear
            answers.add(slow);
            answers.add(slower);
            int asks = asked.size();
            boolean[] first = {false}, second = {false};
            VipList.refresh(server, r -> first[0] = r);
            VipList.refresh(server, r -> second[0] = r);
            check(asked.size() == asks + 1, "only one ask in flight");
            Map<UUID, String> slowAnswer = new HashMap<>();
            slowAnswer.put(mira, "mira");
            slow.complete(slowAnswer);
            check(asked.size() == asks + 2, "asked again after the answer");
            check(first[0] && !second[0], "the first caller heard the first answer, the second waits for the next");
            check(names().equals(List.of("mira")), "the first answer applied: " + names());
            slower.complete(Map.of(alex, "Alex"));
            check(second[0], "the second caller heard the second answer");
            check(names().equals(List.of("Alex")), "the newest answer wins: " + names());
            check(told[0] == 3, "told for each change: " + told[0]);
            h.succeed();
        } finally {
            slow.complete(Map.of()); // a failed step must not leave an ask in flight for the rest of the run
            slower.complete(Map.of());
            VipList.source = oldSource;
            VipList.CHANGED.remove(listener);
            Map<UUID, String> back = new HashMap<>();
            before.forEach(v -> back.put(v.id(), v.name()));
            VipList.source = g -> CompletableFuture.completedFuture(back);
            VipList.refresh(server);
            VipList.source = oldSource;
        }
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void the_vip_list_reaches_clients_whole(GameTestHelper h) {
        List<VipList.Vip> three = List.of(new VipList.Vip(UUID.randomUUID(), "Alex"), new VipList.Vip(UUID.randomUUID(), "mira"),
                new VipList.Vip(UUID.randomUUID(), "Zed_9"));
        check(roundTrip(com.mrgregles.bsp_core.network.VipListPacket.of(three)).vips().equals(three), "three VIPs arrive as sent");
        check(roundTrip(com.mrgregles.bsp_core.network.VipListPacket.of(List.of())).vips().isEmpty(), "an empty list arrives empty");
        List<VipList.Vip> many = new ArrayList<>();
        for (int i = 0; i < com.mrgregles.bsp_core.network.VipListPacket.MAX + 200; i++) {
            many.add(new VipList.Vip(UUID.randomUUID(), "VipPlayer_" + i));
        }
        var big = com.mrgregles.bsp_core.network.VipListPacket.of(many);
        check(big.vips().size() == com.mrgregles.bsp_core.network.VipListPacket.MAX, "at most MAX are sent: " + big.vips().size());
        int size = com.mrgregles.bsp_core.network.BSPNetwork.sizeOf(big, com.mrgregles.bsp_core.network.VipListPacket::encode);
        check(size < com.mrgregles.bsp_core.network.BSPNetwork.MAX_PAYLOAD / 10, "the largest list is small: " + size + " bytes");
        check(roundTrip(big).vips().equals(big.vips()), "the largest list arrives whole");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void the_vips_board_joins_only_rotations_that_tick_it(GameTestHelper h) {
        check(!com.mrgregles.bsp_core.score.ScoreScreenBlockEntity.inDefaultRotation(com.mrgregles.bsp_core.score.ScoreScreenBlockEntity.PAGE_VIPS), "not in the default rotation");
        check(com.mrgregles.bsp_core.score.ScoreScreenBlockEntity.inDefaultRotation("bsp_core:lottery"), "other boards are");
        check(com.mrgregles.bsp_core.score.ScoreScreenBlockEntity.inDefaultRotation("bsp_teleport:homes"), "other mods' boards are");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void only_web_addresses_may_be_the_support_link(GameTestHelper h) {
        String[] good = {"", "https://shop.buildsecureprotect.com/products/vip", "http://buildsecureprotect.com", "HTTPS://X.COM/a?b=c#d"};
        String[] bad = {"javascript:alert(1)", "file:///etc/passwd", "https://", "shop.buildsecureprotect.com", "https://a b.com",
                "https://user:pw@example.com", "ftp://example.com", "https://good.com/\u202Emoc.live", "https://b\u00FCcher.de", "mailto:someone@example.com", "https://example.com/" + "x".repeat(ScoreScreenBlockEntity.LINK_MAX)};
        for (String g : good) {
            check(ScoreScreenBlockEntity.validLink(g), "taken: " + g);
        }
        for (String b : bad) {
            check(!ScoreScreenBlockEntity.validLink(b), "refused: " + b);
        }
        check(!ScoreScreenBlockEntity.validLink(null), "null refused");
        check(ScoreScreenBlockEntity.cleanText("  &6Thank\u00A7c you\n\t ", 80).equals("&6Thankc you"), "control characters and section signs go: " + ScoreScreenBlockEntity.cleanText("  &6Thank\u00A7c you\n\t ", 80));
        check(ScoreScreenBlockEntity.cleanText("x".repeat(100), ScoreScreenBlockEntity.VIP_TEXT_MAX).length() == ScoreScreenBlockEntity.VIP_TEXT_MAX, "cut to 80");
        check(ScoreScreenBlockEntity.cleanText(null, 10).isEmpty(), "null is empty");
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void a_score_screen_keeps_its_vip_texts(GameTestHelper h) {
        var pos = new net.minecraft.core.BlockPos(1, 1, 1);
        h.setBlock(pos, com.mrgregles.bsp_core.registry.ModBlocks.SCORE_SCREEN.get());
        if (!(h.getBlockEntity(pos) instanceof ScoreScreenBlockEntity be)) {
            h.fail("no Score Screen block entity");
            return;
        }
        check(be.vip().equals(ScoreScreenBlockEntity.VipTexts.NONE), "a new screen has no texts");
        String link = "https://shop.buildsecureprotect.com/products/vip";
        var texts = new ScoreScreenBlockEntity.VipTexts("&6Thank you to everyone who keeps BSP running!", "VIPs get Totem Looks.", "Become a VIP", link);
        check(be.configure(ScoreScreenBlockEntity.MODE_PAGE, 0, 0, 100, 40, "OUR VIPS", ScoreScreenBlockEntity.PAGE_VIPS, 0, 10, List.of(), texts), "the link is taken");
        check(be.vip().equals(texts), "kept as written: " + be.vip());
        check(be.boardId().equals(ScoreScreenBlockEntity.PAGE_VIPS), "the other settings still apply");

        // a bad link is ignored, the rest is taken
        var worse = new ScoreScreenBlockEntity.VipTexts("New top", "", "x".repeat(40), "javascript:alert(1)");
        check(!be.configure(ScoreScreenBlockEntity.MODE_PAGE, 0, 0, 100, 40, "OUR VIPS", ScoreScreenBlockEntity.PAGE_VIPS, 0, 10, List.of(), worse), "the link is refused");
        check(be.vip().top().equals("New top") && be.vip().bottom().isEmpty(), "the messages change: " + be.vip());
        check(be.vip().label().length() == ScoreScreenBlockEntity.LINK_LABEL_MAX, "the label is cut to 24");
        check(be.vip().link().equals(link), "the old link stays: " + be.vip().link());

        // the older settings call keeps the texts
        be.configure(ScoreScreenBlockEntity.MODE_BOARD, 0, 0, 100, 40, "TOTEMS");
        check(be.vip().top().equals("New top") && be.vip().link().equals(link), "texts survive a settings change without them");

        // saved and loaded, and sent to clients in the update tag
        var tag = be.saveWithoutMetadata();
        var copy = new ScoreScreenBlockEntity(be.getBlockPos(), be.getBlockState());
        copy.load(tag);
        check(copy.vip().equals(be.vip()), "saved and loaded: " + copy.vip());
        var sent = new ScoreScreenBlockEntity(be.getBlockPos(), be.getBlockState());
        sent.load(be.getUpdateTag());
        check(sent.vip().equals(be.vip()), "in the update tag");
        var hacked = be.saveWithoutMetadata();
        hacked.putString("VipLink", "file:///etc/passwd");
        copy.load(hacked);
        check(copy.vip().link().isEmpty(), "a bad saved link is dropped on load");

        // the settings packet carries them
        var packet = new com.mrgregles.bsp_core.network.ScoreScreenConfigPacket(pos, 3, 0, 0, 100, 40, "OUR VIPS", ScoreScreenBlockEntity.PAGE_VIPS, 0, 10, List.of("scoreboard"), texts);
        net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            com.mrgregles.bsp_core.network.ScoreScreenConfigPacket.encode(packet, buf);
            var back = com.mrgregles.bsp_core.network.ScoreScreenConfigPacket.decode(buf);
            check(back.vip().equals(texts) && back.pages().equals(List.of("scoreboard")), "the packet carries the texts: " + back.vip());
        } finally {
            buf.release();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void a_click_on_a_screen_finds_its_canvas_point(GameTestHelper h) {
        var origin = new net.minecraft.core.BlockPos(10, 64, -5);
        // the corners as a viewer sees them: a screen facing north is read from the north, so its left edge is the east side
        float[] nw = com.mrgregles.bsp_core.score.ScoreScreenBlock.canvasPoint(new net.minecraft.world.phys.Vec3(11.0, 65.0, -5 + 0.8125), origin, net.minecraft.core.Direction.NORTH, 1);
        check(Math.abs(nw[0]) < 0.01 && Math.abs(nw[1]) < 0.01, "north: top-left at the east top corner: " + nw[0] + ", " + nw[1]);
        float[] s0 = com.mrgregles.bsp_core.score.ScoreScreenBlock.canvasPoint(new net.minecraft.world.phys.Vec3(10.0, 65.0, -5 + 0.1875), origin, net.minecraft.core.Direction.SOUTH, 1);
        check(Math.abs(s0[0]) < 0.01 && Math.abs(s0[1]) < 0.01, "south: top-left at the west top corner: " + s0[0] + ", " + s0[1]);
        // every facing: a point placed as the renderer places the canvas comes back where it was
        for (net.minecraft.core.Direction facing : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            for (float[] c : new float[][]{{10, 20}, {200, 3}, {63.5f, 120}}) {
                org.joml.Vector3f v = new org.joml.Vector3f(-0.5f + c[0] / 64f, 2 - 0.5f - c[1] / 64f, -0.3085f);
                v.rotate(com.mojang.math.Axis.YP.rotationDegrees(-facing.toYRot()));
                var world = new net.minecraft.world.phys.Vec3(origin.getX() + 0.5 + v.x, origin.getY() + 0.5 + v.y, origin.getZ() + 0.5 + v.z);
                float[] back = com.mrgregles.bsp_core.score.ScoreScreenBlock.canvasPoint(world, origin, facing, 2);
                check(Math.abs(back[0] - c[0]) < 0.01 && Math.abs(back[1] - c[1]) < 0.01, facing + " " + c[0] + "," + c[1] + " came back as " + back[0] + "," + back[1]);
            }
        }
        h.succeed();
    }

    private static com.mrgregles.bsp_core.network.VipListPacket roundTrip(com.mrgregles.bsp_core.network.VipListPacket p) {
        net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            com.mrgregles.bsp_core.network.VipListPacket.encode(p, buf);
            return com.mrgregles.bsp_core.network.VipListPacket.decode(buf);
        } finally {
            buf.release();
        }
    }

    private static List<String> names() {
        return VipList.vips().stream().map(VipList.Vip::name).toList();
    }

    private static int commandCount(MinecraftServer server) {
        try {
            return server.getCommands().getDispatcher().execute("bsp vips", server.createCommandSourceStack().withSuppressedOutput());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("/bsp vips failed: " + e.getMessage());
        }
    }
}
