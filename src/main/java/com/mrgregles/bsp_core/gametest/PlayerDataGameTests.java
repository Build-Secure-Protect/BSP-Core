package com.mrgregles.bsp_core.gametest;

import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.data.PlayerPersistent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BSP-Core shares the player's {@code ForgeData.PlayerPersisted} compound with every other mod (Waystones keeps each player's
 * known waystones there under {@code BalmData}). These tests hold BSP-Core to editing its own {@code bsp_core:*} keys only: the
 * compound is never replaced, other mods' data survives every BSP-Core write, a login's settle, a respawn and the way home from
 * the End, and a player without the compound gets one holding only BSP-Core's keys.
 */
@GameTestHolder(BSPCore.MODID)
@PrefixGameTestTemplate(false)
public class PlayerDataGameTests {
    private static final String BATCH = "player_data";

    /** Other mods' data as a real player file holds it: Waystones (through Balm) and Integrated Dynamics. */
    private static CompoundTag otherMods() {
        CompoundTag balm = new CompoundTag();
        balm.put("ReturnPortalList", new ListTag());
        CompoundTag waystones = new CompoundTag();
        ListTag known = new ListTag();
        for (int i = 0; i < 3; i++) {
            known.add(StringTag.valueOf(UUID.randomUUID().toString()));
        }
        waystones.put("Waystones", known);
        waystones.putLong("WarpStoneCooldownUntilUnix", 1_795_000_000L);
        balm.put("WaystonesData", waystones);
        CompoundTag out = new CompoundTag();
        out.put("BalmData", balm);
        out.putByte("integrateddynamics:infoBookSpawned", (byte) 1);
        return out;
    }

    /** A player whose PlayerPersisted holds {@link #otherMods()} (and a key of BSP-Core's), as after a pre-update login. */
    private static ServerPlayer withOtherMods(GameTestHelper h, CompoundTag others) {
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        CompoundTag persisted = others.copy();
        persisted.putByte("bsp_core:TotemGranted", (byte) 1);
        p.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        p.getPersistentData().putString("othermod:rootKey", "kept"); // a key of another mod in ForgeData itself
        return p;
    }

    /** Every non-BSP key of {@code others} is in the player's PlayerPersisted, equal to what it was. */
    private static void assertOthersKept(GameTestHelper h, ServerPlayer p, CompoundTag others, String after) {
        CompoundTag persisted = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        for (String key : others.getAllKeys()) {
            h.assertTrue(others.get(key).equals(persisted.get(key)), key + " is unchanged after " + after);
        }
        h.assertTrue(persisted.getCompound("BalmData").getCompound("WaystonesData").getList("Waystones", 8).size() == 3,
                "the three known waystones are still listed after " + after);
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void bsp_writes_keep_other_mods_player_data(GameTestHelper h) {
        CompoundTag others = otherMods();
        ServerPlayer p = withOtherMods(h, others);
        CompoundTag live = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag forgeData = p.getPersistentData();

        PlayerPersistent.setTotemGranted(p, true);
        PlayerPersistent.setSeasonSeen(p, 4);
        PlayerPersistent.setCoinEpoch(p, 2);
        PlayerPersistent.setLastMainPosition(p, Level.OVERWORLD, new BlockPos(10, 64, -3));
        UUID id = UUID.randomUUID();
        PlayerPersistent.addLogoutPlaced(p, new PlayerPersistent.LogoutPlaced(Level.OVERWORLD, new BlockPos(1, 2, 3), id, UUID.randomUUID(), 5));
        PlayerPersistent.addLogoutPlaced(p, new PlayerPersistent.LogoutPlaced(Level.OVERWORLD, new BlockPos(4, 5, 6), UUID.randomUUID(), UUID.randomUUID(), 6));
        h.assertTrue(PlayerPersistent.removeLogoutPlaced(p, id) == 1, "one logout placement dropped");
        h.assertTrue(PlayerPersistent.takeLogoutPlaced(p).size() == 1, "the other taken");
        ListTag held = new ListTag();
        held.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE).save(new CompoundTag()));
        held.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT).save(new CompoundTag()));
        PlayerPersistent.get(p).put("bsp_core:ZoneHeldTotems", held); // what holdTotems leaves (it also needs the ledger)
        h.assertTrue(com.mrgregles.bsp_core.zone.ZoneHandler.dropHeld(p, id) == 0, "no held totem has that id");
        h.assertTrue(com.mrgregles.bsp_core.zone.ZoneHandler.takeHeld(p).size() == 2 && !PlayerPersistent.get(p).contains("bsp_core:ZoneHeldTotems"),
                "the held list is taken and its key removed");

        h.assertTrue(p.getPersistentData() == forgeData, "ForgeData is the same compound");
        h.assertTrue(p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG) == live, "PlayerPersisted is the same compound, edited in place");
        h.assertTrue(PlayerPersistent.get(p) == live, "the helper hands back the live compound");
        h.assertTrue("kept".equals(forgeData.getString("othermod:rootKey")), "another mod's key in ForgeData itself is kept");
        assertOthersKept(h, p, others, "every BSP-Core write");
        h.assertTrue(PlayerPersistent.seasonSeen(p) == 4 && PlayerPersistent.coinEpoch(p) == 2 && PlayerPersistent.isTotemGranted(p)
                && PlayerPersistent.getLastMainPosition(p).isPresent(), "and BSP-Core's own keys hold what was written");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void login_settle_keeps_other_mods_player_data(GameTestHelper h) {
        CompoundTag others = otherMods();
        ServerPlayer p = withOtherMods(h, others);
        CompoundTag live = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        try {
            // what a login runs here: the restriction check, the logout totems, the settle (coins, season, first totem, owed items)
            com.mrgregles.bsp_core.event.TotemRestrictionHandler.onLogin(new PlayerEvent.PlayerLoggedInEvent(p));
            com.mrgregles.bsp_core.event.TotemLogoutHandler.onLogin(new PlayerEvent.PlayerLoggedInEvent(p));
            com.mrgregles.bsp_core.admin.AdminService.settle(p);
            com.mrgregles.bsp_core.event.FirstJoinHandler.check(p);
            com.mrgregles.bsp_core.kit.KitGrants.deliver(p);
            h.assertTrue(p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG) == live, "PlayerPersisted is the same compound after the login");
            assertOthersKept(h, p, others, "the login");
            h.assertTrue("kept".equals(p.getPersistentData().getString("othermod:rootKey")), "and the ForgeData key too");
        } finally {
            p.getInventory().clearContent();
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void respawn_and_end_exit_keep_other_mods_player_data(GameTestHelper h) {
        CompoundTag others = otherMods();
        for (boolean fromEnd : new boolean[] {false, true}) {
            ServerPlayer old = withOtherMods(h, others);
            PlayerPersistent.setSeasonSeen(old, 7);
            PlayerPersistent.setLastMainPosition(old, Level.OVERWORLD, new BlockPos(1, 70, 1));
            // what the server does: a new entity for the same profile, then Forge's restoreFrom, which fires PlayerEvent.Clone
            ServerPlayer now = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
            now.restoreFrom(old, fromEnd);
            String step = fromEnd ? "the way home from the End" : "a respawn";
            assertOthersKept(h, now, others, step);
            h.assertTrue(PlayerPersistent.seasonSeen(now) == 7 && PlayerPersistent.isTotemGranted(now)
                    && PlayerPersistent.getLastMainPosition(now).isPresent(), "BSP-Core's keys passed over at " + step);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void clone_copies_only_missing_bsp_keys(GameTestHelper h) {
        ServerPlayer old = withOtherMods(h, otherMods());
        PlayerPersistent.setSeasonSeen(old, 3);
        PlayerPersistent.setCoinEpoch(old, 9);
        // a new entity whose PlayerPersisted another mod rebuilt with its own data and one BSP key of a different value
        ServerPlayer now = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        CompoundTag theirs = new CompoundTag();
        theirs.putString("othermod:fresh", "theirs");
        theirs.putInt("bsp_core:SeasonSeen", 5);
        now.getPersistentData().put(Player.PERSISTED_NBT_TAG, theirs);
        List<String> copied = PlayerPersistent.copyOwnKeys(old, now);
        CompoundTag after = now.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        h.assertTrue(after == theirs, "the new player's compound is edited, not replaced");
        h.assertTrue(copied.contains("bsp_core:CoinEpoch") && copied.contains("bsp_core:TotemGranted") && !copied.contains("bsp_core:SeasonSeen"),
                "only missing BSP keys are copied: " + copied);
        h.assertTrue(after.getInt("bsp_core:SeasonSeen") == 5 && after.getInt("bsp_core:CoinEpoch") == 9, "a key already there is not overwritten");
        h.assertTrue("theirs".equals(after.getString("othermod:fresh")) && !after.contains("BalmData") && !after.contains("integrateddynamics:infoBookSpawned"),
                "no other mod's key is read or written");
        // the Clone event itself (as Forge posts it after restoreFrom): the handler fills in the missing keys the same way
        ServerPlayer posted = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        CompoundTag rebuilt = new CompoundTag();
        rebuilt.putString("othermod:fresh", "theirs");
        rebuilt.putInt("bsp_core:SeasonSeen", 5);
        posted.getPersistentData().put(Player.PERSISTED_NBT_TAG, rebuilt);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(posted, old, true));
        CompoundTag afterEvent = posted.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        h.assertTrue(afterEvent == rebuilt && afterEvent.getInt("bsp_core:CoinEpoch") == 9 && afterEvent.getInt("bsp_core:SeasonSeen") == 5
                && !afterEvent.contains("BalmData") && "theirs".equals(afterEvent.getString("othermod:fresh")),
                "the Clone handler fills in only the missing BSP keys, in the same compound");
        // the usual case: Forge passed the same compound over, and there is nothing to copy
        ServerPlayer shared = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        shared.getPersistentData().put(Player.PERSISTED_NBT_TAG, old.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG));
        h.assertTrue(PlayerPersistent.copyOwnKeys(old, shared).isEmpty(), "the same compound: nothing to copy");
        // an old player without BSP keys makes no compound on the new one
        ServerPlayer bare = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        ServerPlayer next = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        h.assertTrue(PlayerPersistent.copyOwnKeys(bare, next).isEmpty() && !next.getPersistentData().contains(Player.PERSISTED_NBT_TAG),
                "nothing to copy makes nothing");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void player_without_persisted_data_gets_only_bsp_keys(GameTestHelper h) {
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        h.assertFalse(p.getPersistentData().contains(Player.PERSISTED_NBT_TAG), "a fresh player has no PlayerPersisted");
        PlayerPersistent.setTotemGranted(p, true);
        PlayerPersistent.setSeasonSeen(p, 1);
        PlayerPersistent.setLastMainPosition(p, Level.OVERWORLD, new BlockPos(0, 64, 0));
        CompoundTag persisted = p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        h.assertTrue(!persisted.isEmpty(), "it was made");
        for (String key : persisted.getAllKeys()) {
            h.assertTrue(key.startsWith(PlayerPersistent.KEY_PREFIX), "only BSP-Core's keys in it, not " + key);
        }
        h.assertTrue(PlayerPersistent.isTotemGranted(p) && PlayerPersistent.seasonSeen(p) == 1 && PlayerPersistent.getLastMainPosition(p).isPresent(),
                "and BSP-Core reads them back");
        // a later write from another mod into it stays
        persisted.putString("othermod:later", "x");
        PlayerPersistent.setCoinEpoch(p, 3);
        h.assertTrue("x".equals(PlayerPersistent.get(p).getString("othermod:later")), "the compound made once is the one kept");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void siege_times_from_another_clock_and_old_names(GameTestHelper h) {
        ServerPlayer p = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        long now = h.getLevel().getServer().overworld().getGameTime();
        CompoundTag data = p.getPersistentData();
        data.putLong("bsp_core:SiegeUntil", now + 100);
        h.assertTrue(com.mrgregles.bsp_core.totem.Siege.active(p), "a Siege running on this clock is active");
        data.putLong("bsp_core:SiegeUntil", now + 50_000_000L); // carried from a server whose clock is far ahead
        h.assertFalse(com.mrgregles.bsp_core.totem.Siege.active(p), "a time from another clock counts as none");
        data.remove("bsp_core:SiegeUntil");
        data.putLong("SiegeUntil", now + 100);
        h.assertTrue(com.mrgregles.bsp_core.totem.Siege.active(p), "a Siege saved under the old name still runs");
        data.putLong("SiegeUntil", now - 5);
        data.putLong("SiegeReadyAt", now - 5);
        com.mrgregles.bsp_core.totem.Siege.onLogin(new PlayerEvent.PlayerLoggedInEvent(p));
        h.assertTrue(!data.contains("SiegeUntil") && !data.contains("SiegeReadyAt"), "old-name times that are over go at login");
        h.assertFalse(com.mrgregles.bsp_core.totem.Siege.active(p), "and nothing runs");
        h.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void siege_recharge_carries_on_through_a_death_and_the_end(GameTestHelper h) {
        long now = h.getLevel().getServer().overworld().getGameTime();
        for (boolean death : new boolean[] {true, false}) {
            ServerPlayer old = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
            CompoundTag persisted = PlayerPersistent.get(old);
            persisted.putLong("bsp_core:SiegeUntil", now + 100);
            persisted.putLong("bsp_core:SiegeReadyAt", now + 3000);
            h.assertTrue(com.mrgregles.bsp_core.totem.Siege.active(old), "Siege runs");
            ServerPlayer next = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
            next.restoreFrom(old, !death); // Forge's copy, then PlayerEvent.Clone
            CompoundTag after = next.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            h.assertTrue(after.getLong("bsp_core:SiegeReadyAt") == now + 3000, "the recharge carries on after " + (death ? "a death" : "the End"));
            if (death) {
                h.assertFalse(com.mrgregles.bsp_core.totem.Siege.active(next), "a death ends the running Siege");
            } else {
                h.assertTrue(com.mrgregles.bsp_core.totem.Siege.active(next), "the way home from the End does not end it");
            }
        }
        // a Siege saved at the root (as before) is moved into the persisted data at the next write, and passes over a death
        ServerPlayer was = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        was.getPersistentData().putLong("SiegeUntil", now + 100);
        was.getPersistentData().putLong("SiegeReadyAt", now + 3000);
        ServerPlayer then = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        then.restoreFrom(was, false);
        h.assertTrue(then.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getLong("bsp_core:SiegeReadyAt") == now + 3000,
                "an old-place recharge running at the death carries on too");
        h.assertFalse(com.mrgregles.bsp_core.totem.Siege.active(then), "the death ended the old-place Siege");
        // the way home from the End with times still at the root (the prefixed names of unreleased builds): moved over, still running
        ServerPlayer inEnd = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        inEnd.getPersistentData().putLong("bsp_core:SiegeUntil", now + 100);
        inEnd.getPersistentData().putLong("bsp_core:SiegeReadyAt", now + 3000);
        ServerPlayer home = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        home.restoreFrom(inEnd, true);
        CompoundTag homeData = home.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        h.assertTrue(homeData.getLong("bsp_core:SiegeUntil") == now + 100 && homeData.getLong("bsp_core:SiegeReadyAt") == now + 3000
                && com.mrgregles.bsp_core.totem.Siege.active(home), "root times are moved over on the way home from the End and keep running");
        // a time from another clock in the new place is dropped, so it can never start counting later
        ServerPlayer carried = MockPlayers.serverPlayer(h, GameType.SURVIVAL);
        PlayerPersistent.get(carried).putLong("bsp_core:SiegeUntil", now + 50_000_000L);
        h.assertFalse(com.mrgregles.bsp_core.totem.Siege.active(carried), "a time from another clock counts as none");
        h.assertFalse(PlayerPersistent.get(carried).contains("bsp_core:SiegeUntil"), "and is removed");
        h.succeed();
    }

    /**
     * A guard for the future: {@code "PlayerPersisted"} ({@link Player#PERSISTED_NBT_TAG}, inlined by the compiler) and
     * {@code "ForgeData"} may appear in BSP-Core's classes only in {@link PlayerPersistent} and the GameTests, so no other class
     * names the shared compound (any string holding either name counts, log text included). It does not see writes to the
     * {@code ForgeData} root through {@code getPersistentData()} itself (Siege, Cloak Blind), which use prefixed keys.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public void only_the_helper_names_the_shared_compound(GameTestHelper h) throws Exception {
        var file = ModList.get().getModFileById(BSPCore.MODID);
        h.assertTrue(file != null, "BSP-Core's mod file is listed");
        Path root = file.getFile().getSecureJar().getRootPath();
        byte[][] needles = {"PlayerPersisted".getBytes(StandardCharsets.UTF_8), "ForgeData".getBytes(StandardCharsets.UTF_8)};
        List<String> found = new ArrayList<>();
        int classes = 0;
        try (var walk = Files.walk(root)) {
            for (Path c : (Iterable<Path>) walk.filter(f -> f.toString().endsWith(".class"))::iterator) {
                String name = root.relativize(c).toString().replace('\\', '/');
                classes++;
                if (name.startsWith("com/mrgregles/bsp_core/gametest/") || name.equals("com/mrgregles/bsp_core/data/PlayerPersistent.class")) {
                    continue;
                }
                byte[] bytes = Files.readAllBytes(c);
                for (byte[] needle : needles) {
                    if (indexOf(bytes, needle) >= 0) {
                        found.add(name + " names " + new String(needle, StandardCharsets.UTF_8));
                    }
                }
            }
        }
        h.assertTrue(classes > 500, "the scan read BSP-Core's classes (" + classes + ")");
        h.assertTrue(found.isEmpty(), "go through PlayerPersistent instead: " + found);
        h.succeed();
    }

    private static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= hay.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
