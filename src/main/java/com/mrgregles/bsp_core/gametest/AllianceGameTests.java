package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.network.TeamViewPacket;
import com.mrgregles.bsp_core.team.Alliance;
import com.mrgregles.bsp_core.team.AllianceLedger;
import com.mrgregles.bsp_core.team.AllianceSync;
import com.mrgregles.bsp_core.team.Alliances;
import com.mrgregles.bsp_core.team.Team;
import com.mrgregles.bsp_core.team.TeamChat;
import com.mrgregles.bsp_core.team.Teams;
import com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity;
import com.mrgregles.bsp_core.totem.TotemAccess;
import com.mrgregles.bsp_core.totem.TotemOwner;
import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * GameTests for alliances: making one, inviting and answering, leaving and ending, the perks a team gives its allies on its
 * totems, the ally chat and board, the break when an ally starts a steal (the longer steal and the wait after), and a copy read
 * from the network. Every test makes teams and alliances of its own, with names no other test uses, and ends them.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class AllianceGameTests {
    private static final String EMPTY = "empty";

    private static MinecraftServer server(GameTestHelper h) {
        return h.getLevel().getServer();
    }

    private static ServerPlayer survivor(GameTestHelper h) {
        return MockPlayers.serverPlayer(h, GameType.SURVIVAL);
    }

    private static String name(String prefix) {
        String n = prefix + " " + Long.toString(System.nanoTime() % 1_000_000_000L, 36);
        return n.substring(0, Math.min(16, n.length()));
    }

    /** A new team led by {@code leader}. */
    private static Team team(GameTestHelper h, ServerPlayer leader, String prefix, ChatFormatting colour) {
        h.assertTrue(Teams.create(leader, name(prefix), colour).ok(), "a team for " + prefix);
        return Teams.teamOf(server(h), leader.getUUID());
    }

    /** An alliance made by {@code leader}'s team with the other leaders' teams in it. */
    private static Alliance ally(GameTestHelper h, ServerPlayer leader, ServerPlayer... others) {
        MinecraftServer server = server(h);
        h.assertTrue(Alliances.create(leader, name("Pact")).ok(), "the alliance is made");
        for (ServerPlayer o : others) {
            Team t = Teams.teamOf(server, o.getUUID());
            h.assertTrue(Alliances.invite(leader, t.id, o).ok(), "invited " + t.name);
            h.assertTrue(Alliances.accept(o).ok(), t.name + " accepts");
        }
        return Alliances.allianceOf(server, leader.getUUID());
    }

    private static void disband(ServerPlayer... leaders) {
        for (ServerPlayer p : leaders) {
            if (Teams.teamOf(p.server, p.getUUID()) != null) {
                Teams.disband(p);
            }
        }
    }

    @GameTest(template = EMPTY)
    public void alliance_is_made_joined_left_and_ended(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer a = survivor(h), a2 = survivor(h), b = survivor(h), c = survivor(h);
        Team ta = team(h, a, "Ash", ChatFormatting.RED);
        h.assertTrue(Teams.invite(a, a2).ok() && Teams.accept(a2).ok(), "a2 joins a's team");
        Team tb = team(h, b, "Birch", ChatFormatting.AQUA);
        Team tc = team(h, c, "Cedar", ChatFormatting.GREEN);
        h.assertFalse(Alliances.create(a2, name("Nope")).ok(), "only a team's leader makes an alliance");
        h.assertTrue(Alliances.create(a, name("Pact")).ok(), "the leader makes one");
        h.assertFalse(Alliances.create(a, name("Twice")).ok(), "one alliance at a time");
        Alliance al = Alliances.allianceOf(server, a2.getUUID());
        h.assertTrue(al != null && al.size() == 1, "the team is in it alone");
        h.assertFalse(Alliances.invite(a2, tb.id, b).ok(), "a member does not invite");
        h.assertFalse(Alliances.invite(a, tb.id, null).ok(), "an invite needs the other leader online");
        h.assertTrue(Alliances.invite(a, tb.id, b).ok(), "the leader invites Birch");
        h.assertTrue(Alliances.pending(tb.id).isPresent(), "the invite waits for Birch");
        h.assertFalse(Alliances.invite(a, tb.id, b).ok(), "one invite to a team at a time");
        h.assertTrue(Alliances.accept(b).ok(), "Birch's leader accepts");
        h.assertTrue(Alliances.allied(server, a2.getUUID(), b.getUUID()) && Alliances.allied(server, b.getUUID(), a.getUUID()), "everyone in the two teams is allied");
        h.assertFalse(Alliances.allied(server, a.getUUID(), a2.getUUID()), "teammates are not allies");
        h.assertFalse(Alliances.allied(server, a.getUUID(), c.getUUID()), "an outsider is not an ally");
        h.assertTrue(Alliances.allies(server, b.getUUID()).containsAll(List.of(a.getUUID(), a2.getUUID())), "b's allies are a's team");
        h.assertTrue(com.mrgregles.bsp_core.api.BspTeams.allianceOf(server, a2.getUUID()).map(x -> x.teams().size() == 2).orElse(false), "the api sees it");
        // the Team screen's view, and the packet round trip
        TeamViewPacket view = TeamViewPacket.of(b, false);
        h.assertTrue(view.alliance().has() && view.alliance().leader() && view.alliance().teams().size() == 2, "b's view shows the alliance");
        TeamViewPacket.AllyTeam other = view.alliance().teams().stream().filter(t -> !t.mine()).findFirst().orElseThrow();
        h.assertTrue(other.players().size() == 2 && other.players().stream().noneMatch(TeamViewPacket.Ally::hasPos), "a's two players, no coordinates");
        h.assertTrue(view.alliance().candidates().stream().anyMatch(x -> x.id().equals(tc.id)), "Cedar may be invited");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        TeamViewPacket.encode(view, buf);
        h.assertTrue(TeamViewPacket.decode(buf).equals(view), "encode and decode agree");
        // leaving with two teams ends it
        h.assertTrue(Alliances.leave(b).ok(), "Birch leaves");
        h.assertFalse(Alliances.allied(server, a.getUUID(), b.getUUID()), "no longer allies");
        h.assertTrue(AllianceLedger.get(server).alliance(al.id) == null, "one team left: the alliance ended");
        // a disbanded team leaves its alliance
        ally(h, a, b);
        Alliance again = Alliances.allianceOf(server, a.getUUID());
        h.assertTrue(Teams.disband(b).ok(), "Birch disbands");
        h.assertTrue(AllianceLedger.get(server).alliance(again.id) == null && Alliances.allianceOf(server, a.getUUID()) == null, "and the alliance ended with it");
        disband(a, b, c);
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void alliance_is_full_at_its_size(GameTestHelper h) {
        MinecraftServer server = server(h);
        int max = Alliances.maxTeams();
        ServerPlayer[] leaders = new ServerPlayer[max + 1];
        for (int i = 0; i <= max; i++) {
            leaders[i] = survivor(h);
            team(h, leaders[i], "Full" + i, ChatFormatting.GOLD);
        }
        h.assertTrue(Alliances.create(leaders[0], name("Full")).ok(), "made");
        for (int i = 1; i < max; i++) {
            Team t = Teams.teamOf(server, leaders[i].getUUID());
            h.assertTrue(Alliances.invite(leaders[0], t.id, leaders[i]).ok(), "invite " + i);
        }
        Team last = Teams.teamOf(server, leaders[max].getUUID());
        h.assertFalse(Alliances.invite(leaders[0], last.id, leaders[max]).ok(), "waiting invites hold the free places");
        for (int i = 1; i < max; i++) {
            h.assertTrue(Alliances.accept(leaders[i]).ok(), "accepts " + i);
        }
        h.assertTrue(Alliances.allianceOf(server, leaders[0].getUUID()).size() == max, "full");
        h.assertFalse(Alliances.invite(leaders[1], last.id, leaders[max]).ok(), "a full alliance takes no more");
        disband(leaders);
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void allies_get_only_the_perks_their_team_gives(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer a = survivor(h), a2 = survivor(h), b = survivor(h);
        team(h, a, "Owner", ChatFormatting.RED);
        h.assertTrue(Teams.invite(a, a2).ok() && Teams.accept(a2).ok(), "a2 joins a's team");
        team(h, b, "Guest", ChatFormatting.BLUE);
        BlockPos pos = new BlockPos(6, 1, 6);
        h.setBlock(pos, com.mrgregles.bsp_core.registry.ModBlocks.SHATTER_TOTEM.get());
        ShatterTotemBlockEntity totem = (ShatterTotemBlockEntity) h.getBlockEntity(pos);
        totem.setOwner(new TotemOwner(a.getUUID(), a.getGameProfile().getName()));
        ally(h, a, b);
        int[] flags = {TotemAccess.UPGRADES, TotemAccess.ALARM, TotemAccess.WARD, TotemAccess.MACHINES, TotemAccess.CLOAK};
        for (int f : flags) {
            h.assertFalse(totem.hasAccess(b.getUUID(), f), "an ally starts with no flag " + f);
        }
        h.assertFalse(totem.seesThroughCloak(b), "nor sees through the cloak");
        h.assertFalse(Alliances.setPerk(a2, TotemAccess.CLOAK, true).ok(), "a member does not set the perks");
        h.assertFalse(Alliances.setPerk(a, TotemAccess.UPGRADES, true).ok(), "upgrades is no perk");
        h.assertTrue(Alliances.setPerk(a, TotemAccess.CLOAK, true).ok(), "the leader lets allies see through the cloaks");
        h.assertTrue(totem.seesThroughCloak(b) && !totem.hasAccess(b.getUUID(), TotemAccess.MACHINES), "b sees through, without the machines");
        h.assertFalse(Alliances.perksFor(server, b.getUUID(), a.getUUID()) != 0, "what a's team gives says nothing of what b's team gives");
        h.assertTrue(Alliances.setPerk(a, TotemAccess.MACHINES, true).ok() && Alliances.setPerk(a, TotemAccess.WARD, true).ok(), "machines and ward too");
        var access = com.mrgregles.bsp_core.plasma.PlasmaAccess.of(totem);
        h.assertTrue(access.users.contains(b.getUUID()) && access.wardSafe.contains(b.getUUID()), "the machines and the ward let b in");
        h.assertFalse(access.editors.contains(b.getUUID()) || access.alarmSafe.contains(b.getUUID()), "but b changes no settings and the alarm still sees them");
        h.assertFalse(totem.hasAccess(b.getUUID(), TotemAccess.UPGRADES) || totem.mayUpgrade(b), "an ally never upgrades");
        var allies = totem.getUpdateTag().getList("Allies", net.minecraft.nbt.Tag.TAG_COMPOUND);
        h.assertTrue(allies.size() == 1 && allies.getCompound(0).getInt("Perks") == (TotemAccess.CLOAK | TotemAccess.MACHINES | TotemAccess.WARD), "the client is told what each ally gets");
        h.assertTrue(Alliances.setPerk(a, TotemAccess.CLOAK, false).ok() && !totem.seesThroughCloak(b), "taking a perk back works at once");
        h.assertTrue(Alliances.leave(b).ok() && totem.allyPerks(b.getUUID()) == 0 && !totem.hasAccess(b.getUUID(), TotemAccess.MACHINES), "out of the alliance, nothing is left");
        h.setBlock(pos, Blocks.AIR);
        disband(a, b);
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void an_ally_steal_breaks_the_alliance_and_takes_longer(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer a = survivor(h), b = survivor(h), c = survivor(h);
        Team ta = team(h, a, "Vault", ChatFormatting.RED);
        Team tb = team(h, b, "Thief", ChatFormatting.DARK_PURPLE);
        Team tc = team(h, c, "Third", ChatFormatting.GREEN);
        BlockPos pos = new BlockPos(6, 1, 6);
        h.setBlock(pos, com.mrgregles.bsp_core.registry.ModBlocks.SHATTER_TOTEM.get());
        ShatterTotemBlockEntity totem = (ShatterTotemBlockEntity) h.getBlockEntity(pos);
        totem.setOwner(new TotemOwner(a.getUUID(), a.getGameProfile().getName()));
        Alliance al = ally(h, a, b, c);
        h.assertTrue(Alliances.setPerk(a, TotemAccess.CLOAK, true).ok(), "Vault shows its base to its allies");
        BlockPos at = h.absolutePos(pos);
        b.setPos(at.getX() + 2.5, at.getY(), at.getZ() + 0.5);
        int base = Math.max(BSPConfig.MIN_STEAL_SECONDS.get(), Math.min(BSPConfig.MAX_STEAL_SECONDS.get(), BSPConfig.STEAL_SECONDS.get()));
        totem.tryStartSteal(b);
        h.assertTrue(totem.getSteal().isPresent(), "the steal goes ahead");
        int expected = (int) Math.round(base * Alliances.breakStealMultiplier());
        h.assertTrue(totem.getSteal().get().totalTicks() == expected * 20, "an ally's steal takes longer: " + expected + " s, not " + base);
        h.assertFalse(Alliances.allied(server, a.getUUID(), b.getUUID()) || Alliances.allied(server, c.getUUID(), b.getUUID()), "the thief's team is out");
        h.assertTrue(Alliances.allied(server, a.getUUID(), c.getUUID()) && AllianceLedger.get(server).alliance(al.id).size() == 2, "the other two stay allied");
        h.assertTrue(totem.allyPerks(b.getUUID()) == 0 && !totem.seesThroughCloak(b), "the thief's perks stopped at once");
        if (BSPConfig.ALLIANCE_BREAK_HOURS.get() > 0) {
            long now = System.currentTimeMillis();
            h.assertTrue(AllianceLedger.get(server).waitUntil(tb.id, ta.id, now) > now && AllianceLedger.get(server).waitUntil(tb.id, tc.id, now) > now, "the thief's team waits");
            h.assertFalse(Alliances.invite(a, tb.id, b).ok(), "so it cannot be invited back");
        }
        h.assertTrue(Alliances.stealSeconds(server, a.getUUID(), b.getUUID(), 300) == 300, "a steal by a former ally is an ordinary one");
        h.setBlock(pos, Blocks.AIR);
        h.assertTrue(Alliances.leave(c).ok() && AllianceLedger.get(server).alliance(al.id) == null, "two teams down to one: it ends");
        disband(a, b, c);
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void ally_chat_goes_to_the_alliance_board(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer a = survivor(h), b = survivor(h), c = survivor(h);
        team(h, a, "Loud", ChatFormatting.YELLOW);
        team(h, b, "Quiet", ChatFormatting.GRAY);
        team(h, c, "Alone", ChatFormatting.WHITE);
        Alliance al = ally(h, a, b);
        h.assertFalse(TeamChat.take(b, "to everyone"), "with no channel on, chat goes to everyone");
        h.assertTrue(Teams.toggleChat(server, b.getUUID()), "team chat on");
        h.assertTrue(Alliances.toggleChat(server, b.getUUID()) && !Teams.isChatOn(b.getUUID()), "ally chat on turns team chat off");
        h.assertTrue(TeamChat.take(b, "meet at spawn"), "the line is taken for the alliance");
        Alliance.Message m = al.messages().get(al.messages().size() - 1);
        h.assertTrue(m.text().equals("meet at spawn") && m.teamName().equals(Teams.teamOf(server, b.getUUID()).name), "on the board with the speaker's team");
        h.assertTrue(al.unread(a.getUUID()) == 1 && al.unread(b.getUUID()) == 0, "new for a, read by its author");
        Alliances.markRead(server, a.getUUID());
        h.assertTrue(al.unread(a.getUUID()) == 0, "marked read");
        h.assertFalse(Alliances.post(c, "hello").ok(), "someone in no alliance has no board");
        h.assertTrue(Teams.toggleChat(server, b.getUUID()) && !Alliances.isChatOn(b.getUUID()), "team chat on turns ally chat off");
        h.assertTrue(Alliances.toggleChat(server, b.getUUID()), "ally chat on again");
        h.assertTrue(Alliances.leave(b).ok() && !Alliances.isChatOn(b.getUUID()), "leaving the alliance switches it off");
        var copy = Alliance.load(al.save());
        h.assertTrue(copy.messages().size() == al.messages().size() && copy.readSeq(a.getUUID()) == al.readSeq(a.getUUID()), "saved and loaded whole");
        disband(a, b, c);
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void alliance_read_from_the_network_replaces_the_local_copy(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer a = survivor(h), b = survivor(h);
        team(h, a, "Here", ChatFormatting.AQUA);
        Team tb = team(h, b, "There", ChatFormatting.LIGHT_PURPLE);
        h.assertTrue(Alliances.create(a, name("Net")).ok(), "made here");
        Alliance local = Alliances.allianceOf(server, a.getUUID());
        // another server seated b's team, which gives cloak and shares coordinates
        Alliance fresh = Alliance.load(local.save());
        fresh.teams().put(tb.id, new Alliance.Seat(System.currentTimeMillis(), TotemAccess.CLOAK, true));
        AllianceSync.apply(server, List.of(fresh), fresh.id, null);
        h.assertTrue(Alliances.allied(server, a.getUUID(), b.getUUID()), "the copy read takes b's team in");
        h.assertTrue(Alliances.perksFor(server, b.getUUID(), a.getUUID()) == TotemAccess.CLOAK, "with the perks it gives");
        // a full read made before a change here does not drop the alliance just changed
        AllianceSync.apply(server, List.of(), null, List.of());
        h.assertTrue(AllianceLedger.get(server).alliance(fresh.id) != null, "a full read keeps an alliance changed here a moment ago");
        // the alliance ended elsewhere
        AllianceSync.apply(server, List.of(), fresh.id, null);
        h.assertTrue(AllianceLedger.get(server).alliance(fresh.id) == null && !Alliances.allied(server, a.getUUID(), b.getUUID()), "ended elsewhere, gone here");
        disband(a, b);
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void a_stale_read_never_undoes_a_change_made_here(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer a = survivor(h), b = survivor(h), c = survivor(h);
        Team ta = team(h, a, "Fresh", ChatFormatting.AQUA);
        Team tb = team(h, b, "Stale", ChatFormatting.RED);
        Team tc = team(h, c, "Other", ChatFormatting.GREEN);
        Alliance al = ally(h, a, b, c);
        Alliance before = Alliance.load(al.save()); // a copy another server's notice made us read, from before the change below
        h.assertTrue(Alliances.setPerk(b, TotemAccess.CLOAK, true).ok() && Alliances.leave(b).ok(), "b's team leaves here");
        AllianceSync.expectOwnRead(al.id); // as on a network: this server's read of its own write is still to come
        AllianceSync.apply(server, List.of(before), al.id, List.of(), false, false);
        h.assertFalse(Alliances.allied(server, a.getUUID(), b.getUUID()), "the stale copy does not put b's team back, perks and all");
        Alliance after = Alliance.load(AllianceLedger.get(server).alliance(al.id).save());
        AllianceSync.apply(server, List.of(after), al.id, List.of(), true, false);
        h.assertTrue(Alliances.allied(server, a.getUUID(), c.getUUID()) && !Alliances.allied(server, a.getUUID(), b.getUUID()), "the own read applies");
        // a copy that holds a team the local copy has in another alliance takes it out of that one
        h.assertTrue(Alliances.create(b, name("Second")).ok(), "b's team makes its own alliance");
        Alliance second = Alliances.allianceOf(server, b.getUUID());
        Alliance moved = Alliance.load(after.save());
        moved.teams().put(tb.id, new Alliance.Seat(System.currentTimeMillis(), 0, false));
        AllianceSync.apply(server, List.of(moved), moved.id, List.of(), true, false);
        h.assertFalse(second.has(tb.id), "b's team is out of the alliance it was in here");
        h.assertTrue(Alliances.allied(server, b.getUUID(), a.getUUID()) && Alliances.allied(server, a.getUUID(), b.getUUID()), "and allied both ways in the new one");
        // an alliance read with no team at all counts as gone
        Alliance empty = new Alliance(second.id, second.name, second.createdMs);
        AllianceSync.apply(server, List.of(empty), empty.id, List.of(), true, false);
        h.assertTrue(AllianceLedger.get(server).alliance(empty.id) == null, "a teamless alliance is dropped");
        h.assertTrue(ta != null && tc != null, "teams made");
        disband(a, b, c);
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void a_break_in_a_two_team_alliance_ends_it(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer a = survivor(h), b = survivor(h);
        team(h, a, "Pair", ChatFormatting.GOLD);
        team(h, b, "Turncoat", ChatFormatting.DARK_RED);
        Alliance al = ally(h, a, b);
        h.assertTrue(Alliances.toggleChat(server, a.getUUID()), "a talks to the alliance");
        h.assertTrue(Alliances.breakBySteal(server, b.getUUID(), b.getGameProfile().getName(), a.getUUID(), a.getGameProfile().getName()), "b's steal breaks it");
        h.assertTrue(AllianceLedger.get(server).alliance(al.id) == null, "one team left: the alliance ended");
        h.assertFalse(Alliances.isChatOn(a.getUUID()), "and the ally chat of the team left behind is off");
        h.assertFalse(Alliances.breakBySteal(server, b.getUUID(), b.getGameProfile().getName(), a.getUUID(), a.getGameProfile().getName()), "a second steal breaks nothing");
        disband(a, b);
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public void a_steal_soon_after_leaving_still_breaks_the_alliance(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer a = survivor(h), b = survivor(h), c = survivor(h);
        Team ta = team(h, a, "Stays", ChatFormatting.BLUE);
        Team tb = team(h, b, "Leaves", ChatFormatting.DARK_RED);
        Team tc = team(h, c, "Watches", ChatFormatting.GREEN);
        Alliance al = ally(h, a, b, c);
        h.assertTrue(Alliances.leave(b).ok(), "b's leader takes the team out");
        h.assertFalse(Alliances.allied(server, a.getUUID(), b.getUUID()), "no longer allies");
        if (BSPConfig.ALLIANCE_LEFT_HOURS.get() <= 0) {
            h.assertFalse(Alliances.stealBreaks(server, a.getUUID(), b.getUUID()), "with leftStealHours 0, leaving frees the team");
            disband(a, b, c);
            h.succeed();
            return;
        }
        h.assertTrue(Alliances.stealBreaks(server, a.getUUID(), b.getUUID()) && Alliances.stealBreaks(server, c.getUUID(), b.getUUID())
                && Alliances.stealBreaks(server, b.getUUID(), a.getUUID()), "a steal either way between the parted teams still counts");
        BlockPos pos = new BlockPos(6, 1, 6);
        h.setBlock(pos, com.mrgregles.bsp_core.registry.ModBlocks.SHATTER_TOTEM.get());
        ShatterTotemBlockEntity totem = (ShatterTotemBlockEntity) h.getBlockEntity(pos);
        totem.setOwner(new TotemOwner(a.getUUID(), a.getGameProfile().getName()));
        BlockPos at = h.absolutePos(pos);
        b.setPos(at.getX() + 2.5, at.getY(), at.getZ() + 0.5);
        int base = Math.max(BSPConfig.MIN_STEAL_SECONDS.get(), Math.min(BSPConfig.MAX_STEAL_SECONDS.get(), BSPConfig.STEAL_SECONDS.get()));
        totem.tryStartSteal(b);
        int expected = (int) Math.round(base * Alliances.breakStealMultiplier());
        h.assertTrue(totem.getSteal().isPresent() && totem.getSteal().get().totalTicks() == expected * 20, "the steal takes the longer time: " + expected + " s");
        long now = System.currentTimeMillis();
        if (BSPConfig.ALLIANCE_BREAK_HOURS.get() > 0) {
            h.assertTrue(AllianceLedger.get(server).waitUntil(tb.id, ta.id, now) > now && AllianceLedger.get(server).waitUntil(tb.id, tc.id, now) > now,
                    "the team waits as after a break, towards every team it left");
        }
        h.assertFalse(Alliances.stealBreaks(server, c.getUUID(), b.getUUID()), "the records are used up: the next steal is an ordinary one");
        h.assertTrue(Alliances.allied(server, a.getUUID(), c.getUUID()) && AllianceLedger.get(server).alliance(al.id).size() == 2, "the others stay allied");
        h.setBlock(pos, Blocks.AIR);
        // an admin ending an alliance leaves no record behind
        Alliance left = Alliances.allianceOf(server, a.getUUID());
        h.assertTrue(Alliances.adminDissolve(server, left).ok() && !Alliances.stealBreaks(server, a.getUUID(), c.getUUID()), "an admin's dissolve frees everyone");
        disband(a, b, c);
        h.succeed();
    }
}
