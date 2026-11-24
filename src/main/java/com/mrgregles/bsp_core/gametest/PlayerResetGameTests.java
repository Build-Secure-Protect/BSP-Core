package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.admin.PlayerReset;
import com.mrgregles.bsp_core.admin.PlayerResetLedger;
import com.mrgregles.bsp_core.admin.PlayerResetRow;
import com.mrgregles.bsp_core.api.event.PlayerResetEvent;
import com.mrgregles.bsp_core.bank.Bank;
import com.mrgregles.bsp_core.data.PlayerPersistent;
import com.mrgregles.bsp_core.lottery.LotteryLedger;
import com.mrgregles.bsp_core.lottery.LotteryPlayer;
import com.mrgregles.bsp_core.totem.NewPlayerProtection;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * {@code /bsp player reset} (vault note "Player Reset"): the player comes back a brand-new player. On one server (no database in a
 * GameTest): what they carry and BSP-Core's records of them go, other mods' keys stay, the event reaches the sister mods, an offline
 * player is wiped at their next settle and only once, a reset older than this server's record never touches them, a repeat does
 * nothing, the team lead passes on, and the confirm wants a fresh ask. In the {@code player_data} batch.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class PlayerResetGameTests {
    private static final String BATCH = "player_data";

    private static MinecraftServer server(GameTestHelper h) {
        return h.getLevel().getServer();
    }

    /** Listens for the reset events while {@code body} runs. */
    private static List<PlayerResetEvent> heard(Runnable body) {
        List<PlayerResetEvent> out = new ArrayList<>();
        Consumer<PlayerResetEvent> l = out::add;
        MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL, false, PlayerResetEvent.class, l);
        try {
            body.run();
        } finally {
            MinecraftForge.EVENT_BUS.unregister(l);
        }
        return out;
    }

    /** A player who has played a while: items, an ender chest, XP, an effect, a statistic, BSP-Core's and another mod's keys, records here. */
    private static ServerPlayer veteran(GameTestHelper h) {
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        MinecraftServer server = server(h);
        UUID id = p.getUUID();
        p.getInventory().add(new ItemStack(Items.DIAMOND, 12));
        p.getInventory().armor.set(3, new ItemStack(Items.IRON_HELMET));
        p.getEnderChestInventory().setItem(0, new ItemStack(Items.EMERALD, 5));
        p.setExperienceLevels(30);
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 6000));
        p.awardStat(Stats.JUMP, 40);
        CompoundTag persisted = PlayerPersistent.get(p);
        persisted.putByte("bsp_core:TotemGranted", (byte) 1);
        persisted.putLong("bsp_core:RecallReadyAt", System.currentTimeMillis() + 3_600_000L);
        persisted.putString("othermod:kept", "yes");
        NewPlayerProtection.get(server).raise(id, 5, 6000);
        com.mrgregles.bsp_core.compass.CompassCooldowns.get(server).start(id, System.currentTimeMillis() + 3_600_000L);
        LotteryLedger.get(server).seen(new LotteryPlayer(id, p.getGameProfile().getName(), System.currentTimeMillis(), Set.of(), false, ""));
        com.mrgregles.bsp_core.bank.DailyReward.Days.get(server).paid(id, 20_000);
        Bank.set(server, id, 123_456, "test", r -> {});
        return p;
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 40)
    public void player_reset_makes_an_online_player_new_and_keeps_other_mods_data(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer p = veteran(h);
        UUID id = p.getUUID();
        String name = p.getGameProfile().getName();
        PlayerReset.Report[] report = new PlayerReset.Report[1];
        List<PlayerResetEvent> events = heard(() -> PlayerReset.reset(server, id, name, "test admin", p, r -> report[0] = r));
        h.assertTrue(report[0] != null && report[0].ok(), "the reset is done at once on one server: " + (report[0] == null ? "no answer" : report[0].failure()));
        h.assertTrue(report[0].here().carriedCleared(), "the online, settled player is wiped at once");
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            h.assertTrue(!s.is(Items.DIAMOND) && !s.is(Items.IRON_HELMET), "the old items are gone (slot " + i + ": " + s + ")");
        }
        h.assertTrue(p.getEnderChestInventory().isEmpty(), "the ender chest is empty");
        h.assertTrue(p.experienceLevel == 0 && p.getActiveEffects().isEmpty(), "no XP and no effects");
        h.assertTrue(p.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP)) == 0, "the statistics are back to 0");
        CompoundTag persisted = PlayerPersistent.get(p);
        h.assertTrue("yes".equals(persisted.getString("othermod:kept")), "another mod's key is kept");
        h.assertTrue(!persisted.contains("bsp_core:RecallReadyAt"), "BSP-Core's old keys are gone (the Recall wait)");
        h.assertTrue(persisted.getLong(PlayerReset.TAG_RESET_AT) > 0 && !PlayerReset.due(p), "the reset is stamped as done on the player");
        h.assertTrue(NewPlayerProtection.get(server).rung(id) == -1 && NewPlayerProtection.get(server).minutes(id) == 0, "the protection memory is forgotten");
        h.assertTrue(com.mrgregles.bsp_core.compass.CompassCooldowns.get(server).until(id) == 0, "the compass wait is gone");
        h.assertTrue(LotteryLedger.get(server).since(0).stream().noneMatch(e -> e.id().equals(id)), "the lottery entry is gone");
        h.assertTrue(com.mrgregles.bsp_core.bank.DailyReward.Days.get(server).last(id) != 20_000, "the old daily reward day is gone (today's is paid as at a first login)");
        long starting = BSPConfig.getOr(BSPConfig.BANK_STARTING_PIECES, 500), pieces = Bank.pieces(server, id);
        h.assertTrue(pieces != 123_456 && pieces >= starting, "the bank starts over with the starting pieces (and today's reward): " + pieces);
        h.assertTrue(events.stream().anyMatch(e -> e instanceof PlayerResetEvent.Forgotten f && f.isOrigin() && f.getPlayer().equals(id) && "test admin".equals(f.getBy())),
                "the sister mods hear Forgotten, as the origin");
        h.assertTrue(events.stream().anyMatch(e -> e instanceof PlayerResetEvent.Cleared c && c.getEntity() == p), "and Cleared for the wiped player");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 40)
    public void player_reset_of_an_offline_player_wipes_them_at_their_next_settle_once(GameTestHelper h) throws Exception {
        MinecraftServer server = server(h);
        ServerPlayer p = veteran(h);
        UUID id = p.getUUID();
        // this server's own statistics file of the player, as left at their logout
        Path stats = server.getWorldPath(LevelResource.PLAYER_STATS_DIR).resolve(id + ".json");
        Files.createDirectories(stats.getParent());
        Files.writeString(stats, "{\"stats\":{},\"DataVersion\":3465}");
        PlayerResetRow row = new PlayerResetRow(id, p.getGameProfile().getName(), System.currentTimeMillis(), "test admin");
        var applied = PlayerReset.apply(server, row, false, null); // reached from another server while they are offline here
        h.assertTrue(applied.filesMoved() == 1 && !Files.exists(stats), "their statistics file here is moved aside");
        Path aside = server.getWorldPath(LevelResource.ROOT).resolve(PlayerReset.BACKUP_DIR).resolve(id + "-" + row.resetMs()).resolve("stats.json");
        h.assertTrue(Files.isRegularFile(aside), "into " + PlayerReset.BACKUP_DIR + ", not deleted");
        var again = PlayerReset.apply(server, row, false, null);
        h.assertTrue(again.filesMoved() == 0 && again.totems() == 0 && !again.carriedCleared() && !again.rankUp(), "the same reset again does nothing");
        // their next login: an inventory sync mod restored the old inventory, then the settle runs
        h.assertTrue(PlayerReset.due(p), "the wipe is due at their next settle");
        List<PlayerResetEvent> events = heard(() -> h.assertTrue(PlayerReset.onSettle(p), "the settle wipes them"));
        h.assertTrue(p.getEnderChestInventory().isEmpty() && p.experienceLevel == 0, "what they carry is gone");
        h.assertTrue(events.stream().anyMatch(e -> e instanceof PlayerResetEvent.Cleared), "the sister mods hear Cleared");
        p.getInventory().add(new ItemStack(Items.BREAD, 3)); // what the login hands them, and what they gather after
        h.assertTrue(!PlayerReset.onSettle(p) && p.getInventory().countItem(Items.BREAD) == 3, "a later settle wipes nothing");
        Files.deleteIfExists(aside);
        Files.deleteIfExists(aside.getParent());
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void player_reset_older_than_this_servers_record_never_touches_the_player(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer p = veteran(h);
        long now = System.currentTimeMillis();
        PlayerResetRow old = new PlayerResetRow(p.getUUID(), p.getGameProfile().getName(), now - 86_400_000L, "test admin");
        PlayerResetLedger ledger = PlayerResetLedger.get(server);
        long since = ledger.since();
        ledger.since(now - 60_000L); // this server joined the network a minute ago
        try {
            List<PlayerResetEvent> events = heard(() -> PlayerReset.fromNetwork(server, old));
            h.assertTrue(events.isEmpty(), "a reset made before this server joined is not applied");
            h.assertTrue(!PlayerReset.due(p) && !PlayerReset.onSettle(p), "and never wipes them at a login");
            h.assertTrue(p.getInventory().countItem(Items.DIAMOND) == 12 && NewPlayerProtection.get(server).minutes(p.getUUID()) == 6000, "their items and records stay");
            // a reset made just before this server's record began (it started in the seconds after) still counts
            PlayerResetRow fresh = new PlayerResetRow(p.getUUID(), p.getGameProfile().getName(), now - 90_000L, "test admin");
            h.assertTrue(!heard(() -> PlayerReset.fromNetwork(server, fresh)).isEmpty() && PlayerReset.due(p), "a reset 30 s older than the record is applied");
        } finally {
            ledger.since(since);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void player_reset_reaching_a_server_late_spares_a_player_already_wiped_elsewhere(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer p = veteran(h);
        long resetMs = System.currentTimeMillis() - 3 * 3_600_000L;
        // wiped on another server three hours ago; the stamp and what they gathered since came here with the inventory sync
        PlayerPersistent.get(p).putLong(PlayerReset.TAG_RESET_AT, resetMs);
        PlayerResetLedger ledger = PlayerResetLedger.get(server);
        long since = ledger.since();
        ledger.since(resetMs - PlayerReset.LATE_MS - 1); // this server was on the network then (it was down at the time)
        try {
            List<PlayerResetEvent> events = heard(() -> {
                var applied = PlayerReset.apply(server, new PlayerResetRow(p.getUUID(), p.getGameProfile().getName(), resetMs, "test admin"), false, p);
                h.assertTrue(!applied.carriedCleared() && applied.filesMoved() == 0, "nothing they carry is wiped");
            });
            h.assertTrue(ledger.latest(p.getUUID()) != null && events.stream().anyMatch(e -> e instanceof PlayerResetEvent.Forgotten), "the late reset is applied here");
            h.assertTrue(p.getInventory().countItem(Items.DIAMOND) == 12 && !p.getEnderChestInventory().isEmpty() && p.experienceLevel == 30, "their new things stay");
            h.assertTrue(!PlayerReset.due(p) && !PlayerReset.onSettle(p), "and no login wipes them");
        } finally {
            ledger.since(since);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void player_reset_heard_again_leaves_the_files_of_a_player_who_played_here_since(GameTestHelper h) throws Exception {
        MinecraftServer server = server(h);
        UUID id = UUID.randomUUID();
        long resetMs = System.currentTimeMillis();
        // their file here, written at their logout after the reset: it carries the stamp
        CompoundTag persisted = new CompoundTag();
        persisted.putLong(PlayerReset.TAG_RESET_AT, resetMs);
        CompoundTag forge = new CompoundTag();
        forge.put(Player.PERSISTED_NBT_TAG, persisted);
        CompoundTag root = new CompoundTag();
        root.put("ForgeData", forge);
        Path dat = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(id + ".dat");
        Files.createDirectories(dat.getParent());
        net.minecraft.nbt.NbtIo.writeCompressed(root, dat.toFile());
        try {
            var applied = PlayerReset.apply(server, new PlayerResetRow(id, "bsp_test_again", resetMs, "test admin"), false, null);
            h.assertTrue(applied.filesMoved() == 0 && Files.isRegularFile(dat), "their newer file stays where it is");
        } finally {
            Files.deleteIfExists(dat);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void player_reset_of_a_dead_player_wipes_them_at_the_respawn(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer p = veteran(h);
        p.setHealth(0); // on the death screen
        var applied = PlayerReset.apply(server, new PlayerResetRow(p.getUUID(), p.getGameProfile().getName(), System.currentTimeMillis(), "test admin"), false, p);
        h.assertTrue(!applied.carriedCleared() && applied.filesMoved() == 0, "a dead player is not wiped on the dead entity");
        ServerPlayer back = MockPlayers.respawned(h, p);
        back.getEnderChestInventory().setItem(0, new ItemStack(Items.EMERALD, 5)); // the ender chest passes to the respawned player
        h.assertTrue(PlayerReset.due(back), "the wipe is due on the respawned entity");
        com.mrgregles.bsp_core.admin.LoginSettle.onRespawn(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent(back, false)); // the respawn's settle
        h.assertTrue(!PlayerReset.due(back) && back.getEnderChestInventory().isEmpty(), "the respawn's settle wipes them");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 40)
    public void player_reset_passes_a_team_lead_on_before_the_leader_leaves(GameTestHelper h) {
        MinecraftServer server = server(h);
        ServerPlayer leader = MockPlayers.serverPlayer(h, GameType.SURVIVAL), member = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.create(leader, "Reset" + (System.nanoTime() % 100000), net.minecraft.ChatFormatting.AQUA).ok(), "a team");
        h.assertTrue(com.mrgregles.bsp_core.team.Teams.invite(leader, member).ok() && com.mrgregles.bsp_core.team.Teams.accept(member).ok(), "with a member");
        PlayerReset.Report[] report = new PlayerReset.Report[1];
        PlayerReset.reset(server, leader.getUUID(), leader.getGameProfile().getName(), "test admin", leader, r -> report[0] = r);
        h.assertTrue(report[0] != null && report[0].ok() && report[0].team().startsWith("left team"), "the leader leaves: " + (report[0] == null ? "no answer" : report[0].team() + report[0].failure()));
        var team = com.mrgregles.bsp_core.team.Teams.teamOf(server, member.getUUID());
        h.assertTrue(team != null && team.isLeader(member.getUUID()) && com.mrgregles.bsp_core.team.Teams.teamOf(server, leader.getUUID()) == null,
                "the member leads the team now, and the reset player is in none");
        com.mrgregles.bsp_core.team.Teams.disband(member);
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void player_reset_confirm_wants_a_fresh_ask_for_that_player(GameTestHelper h) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        PlayerReset.ask("test admin", a, "Alpha");
        h.assertTrue(!PlayerReset.confirmed("test admin", b), "a confirm for another player does not count");
        h.assertTrue(!PlayerReset.confirmed("test admin", a), "and it used the ask up");
        PlayerReset.ask("test admin", a, "Alpha");
        h.assertTrue(!PlayerReset.confirmed("someone else", a), "another admin cannot confirm it");
        h.assertTrue(PlayerReset.confirmed("test admin", a), "the asker confirms it");
        h.assertTrue(!PlayerReset.confirmed("test admin", a), "once");
        h.succeed();
    }
}
