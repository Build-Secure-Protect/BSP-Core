package com.mrgregles.bsp_core.bank;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The one door to every player's money. Shops, the ATM, the Portable Banker, commands, other mods
 * (through {@code api.BspBank}) and later Region Guard all charge and credit through here, so the
 * rules live in one place: pieces are a balance, coins in the bank are a ledger by tier, converting
 * coins to pieces is one way, nothing in the bank can be stolen.
 *
 * <p>Every operation that changes money takes a {@code then} callback that runs on the server thread.
 * On a single server it runs at once; on a network the database decides and it runs when the answer
 * is back. The local {@link BankLedger} is the record on a single server and the server's copy of
 * the database on a network.
 */
public final class Bank {
    private Bank() {}

    // ------------------------------------------------------------------ reading

    public static BankAccount account(MinecraftServer server, UUID id) {
        return BankLedger.get(server).account(id);
    }

    public static long pieces(MinecraftServer server, UUID id) {
        return account(server, id).pieces;
    }

    /** Pieces one coin of this tier converts to. */
    public static long piecesPerCoin(CoinTier tier) {
        return (long) tier.value() * BSPConfig.getOr(BSPConfig.BANK_PIECES_PER_COPPER, 1000);
    }

    /** Pieces a shop asks for one coin of this tier: the conversion value plus the markup. */
    public static long coinSalePrice(CoinTier tier) {
        return piecesPerCoin(tier) * (100 + BSPConfig.getOr(BSPConfig.BANK_COIN_MARKUP_PERCENT, 25)) / 100;
    }

    public static String format(long pieces) {
        return String.format(Locale.ROOT, "%,d", pieces);
    }

    public static String serverId() {
        return NetworkStorage.enabled() ? BSPConfig.getOr(BSPConfig.STORAGE_SERVER_ID, "server") : "local";
    }

    /** A player's log, newest first; on a network the database copy is fetched first. */
    public static void log(MinecraftServer server, UUID id, Consumer<List<BankTransaction>> then) {
        if (!NetworkStorage.enabled()) {
            then.accept(BankLedger.get(server).log(id));
            return;
        }
        NetworkStorage.bankLogRead(id, list -> {
            if (list != null) {
                BankLedger.get(server).replaceLog(id, list);
            }
            then.accept(BankLedger.get(server).log(id));
        });
    }

    // ------------------------------------------------------------------ accounts

    /**
     * Opens the account if the player has none anywhere on the network and gives the starting pieces,
     * once; records their name; refreshes the cached balance on a network. {@code then} gets true when
     * the starting pieces were given just now.
     */
    public static void ensure(MinecraftServer server, UUID id, String name, Consumer<Boolean> then) {
        BankLedger ledger = BankLedger.get(server);
        BankAccount a = ledger.account(id);
        a.name = name;
        ledger.setDirty();
        long starting = BSPConfig.getOr(BSPConfig.BANK_STARTING_PIECES, 500);
        if (!NetworkStorage.enabled()) {
            if (a.granted) {
                then.accept(false);
                return;
            }
            a.granted = true;
            a.pieces += starting;
            ledger.record(id, tx(BankTransaction.Type.GRANT, "Shatter Bank", "welcome", starting));
            then.accept(true);
            return;
        }
        NetworkStorage.bankEnsure(id, name, starting, (inserted, row) -> {
            if (row != null) {
                a.copyFrom(row);
                a.name = name;
                ledger.setDirty();
            }
            if (inserted) {
                record(server, id, tx(BankTransaction.Type.GRANT, "Shatter Bank", "welcome", starting));
            }
            then.accept(inserted);
        });
    }

    // ------------------------------------------------------------------ pieces

    /** Adds pieces (a sale, a refund, an admin gift). */
    public static void give(MinecraftServer server, UUID id, long pieces, BankTransaction.Type type, String with, String detail, Consumer<BankResult> then) {
        if (pieces <= 0) {
            then.accept(BankResult.fail("amount"));
            return;
        }
        adjust(server, id, pieces, null, tx(type, with, detail, pieces), then);
    }

    /** Takes pieces (a purchase, a fee); fails with "short" when the balance is too small and takes nothing. */
    public static void take(MinecraftServer server, UUID id, long pieces, BankTransaction.Type type, String with, String detail, Consumer<BankResult> then) {
        if (pieces <= 0) {
            then.accept(BankResult.fail("amount"));
            return;
        }
        adjust(server, id, -pieces, null, tx(type, with, detail, -pieces), then);
    }

    /** Moves pieces from one player to another. Fails with "short", "refused" (they do not accept payments) or "self". */
    public static void transfer(MinecraftServer server, UUID from, UUID to, long amount, Consumer<BankResult> then) {
        if (amount <= 0) {
            then.accept(BankResult.fail("amount"));
            return;
        }
        if (from.equals(to)) {
            then.accept(BankResult.fail("self"));
            return;
        }
        BankLedger ledger = BankLedger.get(server);
        String fromName = nameOf(server, from), toName = nameOf(server, to);
        if (!NetworkStorage.enabled()) {
            BankAccount a = ledger.account(from), b = ledger.account(to);
            if (!b.acceptPay) {
                then.accept(BankResult.fail("refused"));
                return;
            }
            if (a.pieces < amount) {
                then.accept(BankResult.fail("short"));
                return;
            }
            a.pieces -= amount;
            b.pieces += amount;
            ledger.record(from, tx(BankTransaction.Type.PAID, toName, "", -amount));
            ledger.record(to, tx(BankTransaction.Type.RECEIVED, fromName, "", amount));
            tellReceived(server, to, fromName, amount);
            then.accept(BankResult.ok(a.pieces));
            return;
        }
        NetworkStorage.bankTransfer(from, to, amount, result -> {
            if (result.ok()) {
                ledger.account(from).pieces = result.pieces();
                ledger.setDirty();
                record(server, from, tx(BankTransaction.Type.PAID, toName, "", -amount));
                record(server, to, tx(BankTransaction.Type.RECEIVED, fromName, "", amount));
                refresh(server, to);
                tellReceived(server, to, fromName, amount);
            }
            then.accept(result);
        });
    }

    /** Admin: sets the balance outright. */
    public static void set(MinecraftServer server, UUID id, long pieces, String admin, Consumer<BankResult> then) {
        long now = account(server, id).pieces;
        if (!NetworkStorage.enabled()) {
            adjust(server, id, pieces - now, null, tx(BankTransaction.Type.ADMIN, admin, "set to " + format(pieces), pieces - now), then);
            return;
        }
        NetworkStorage.bankSet(id, pieces, row -> {
            if (row == null) {
                then.accept(BankResult.fail("unreachable"));
                return;
            }
            account(server, id).copyFrom(row);
            BankLedger.get(server).setDirty();
            record(server, id, tx(BankTransaction.Type.ADMIN, admin, "set to " + format(pieces), pieces - now));
            then.accept(BankResult.ok(row.pieces));
        });
    }

    // ------------------------------------------------------------------ coins

    /**
     * Takes {@code count} coins of a tier out of the player's inventory and either keeps them in the
     * bank by tier or converts them to pieces (one way). Fails with "coins" when they do not hold that many.
     */
    public static void depositCoins(MinecraftServer server, Player player, CoinTier tier, int count, boolean convert, Consumer<BankResult> then) {
        if (count <= 0) {
            then.accept(BankResult.fail("amount"));
            return;
        }
        if (player.getInventory().countItem(tier.coin()) < count) {
            then.accept(BankResult.fail("coins_short"));
            return;
        }
        player.getInventory().clearOrCountMatchingItems(st -> st.is(tier.coin()), count, player.inventoryMenu.getCraftSlots());
        player.inventoryMenu.broadcastChanges();
        UUID id = player.getUUID();
        String what = count + " " + tierName(tier) + (count == 1 ? " coin" : " coins");
        int[] dCoins = new int[CoinTier.values().length];
        long dPieces = 0;
        BankTransaction t;
        if (convert) {
            dPieces = piecesPerCoin(tier) * count;
            t = new BankTransaction(System.currentTimeMillis(), BankTransaction.Type.CONVERT, "Shatter Bank", "converted " + what, dPieces, tier.ordinal(), -count, serverId());
        } else {
            dCoins[tier.ordinal()] = count;
            t = new BankTransaction(System.currentTimeMillis(), BankTransaction.Type.DEPOSIT, "Shatter Bank", "banked " + what, 0, tier.ordinal(), count, serverId());
        }
        adjust(server, id, dPieces, dCoins, t, r -> {
            if (!r.ok()) {
                giveCoins(player, tier, count); // the database did not take them: hand the coins back
            }
            then.accept(r);
        });
    }

    /** Hands {@code count} banked coins of a tier back as items. Fails with "short" when the bank holds fewer. */
    public static void withdrawCoins(MinecraftServer server, Player player, CoinTier tier, int count, Consumer<BankResult> then) {
        if (count <= 0) {
            then.accept(BankResult.fail("amount"));
            return;
        }
        int[] dCoins = new int[CoinTier.values().length];
        dCoins[tier.ordinal()] = -count;
        String what = count + " " + tierName(tier) + (count == 1 ? " coin" : " coins");
        BankTransaction t = new BankTransaction(System.currentTimeMillis(), BankTransaction.Type.WITHDRAW, "Shatter Bank", "took out " + what, 0, tier.ordinal(), -count, serverId());
        adjust(server, player.getUUID(), 0, dCoins, t, r -> {
            if (r.ok()) {
                giveCoins(player, tier, count);
            }
            then.accept(r);
        });
    }

    private static void giveCoins(Player player, CoinTier tier, int count) {
        while (count > 0) {
            int batch = Math.min(count, 64);
            ItemStack stack = new ItemStack(tier.coin(), batch);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            count -= batch;
        }
    }

    // ------------------------------------------------------------------ switches

    public static void setAcceptPay(MinecraftServer server, UUID id, boolean accept) {
        BankLedger ledger = BankLedger.get(server);
        ledger.account(id).acceptPay = accept;
        ledger.setDirty();
        NetworkStorage.bankFlags(id, accept, null, null);
    }

    public static void setRemoteShops(MinecraftServer server, UUID id, boolean on) {
        BankLedger ledger = BankLedger.get(server);
        ledger.account(id).remoteShops = on;
        ledger.setDirty();
        NetworkStorage.bankFlags(id, null, on, null);
        pushState(server, id);
    }

    /** Sends the player's own box and network state to their client, for the deposit box renderer. */
    public static void pushState(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            BankAccount a = account(server, id);
            com.mrgregles.bsp_core.network.BSPNetwork.sendTo(online, new com.mrgregles.bsp_core.network.BankStatePacket(a.boxSlots, a.boxUntilMs, a.remoteShops));
        }
    }

    /** Buys the Shopping Network for the player's Portable Banker with coins from their inventory. Fails with "already" or "coins_unlock". */
    public static void unlockRemote(MinecraftServer server, Player player, Consumer<BankResult> then) {
        UUID id = player.getUUID();
        if (account(server, id).remoteShops) {
            then.accept(BankResult.fail("already"));
            return;
        }
        int cost = BSPConfig.getOr(BSPConfig.BANK_REMOTE_UNLOCK_VALUE, 16);
        if (!com.mrgregles.bsp_core.coin.CoinWallet.pay(player, cost)) {
            then.accept(BankResult.fail("coins_unlock"));
            return;
        }
        setRemoteShops(server, id, true);
        record(server, id, new BankTransaction(System.currentTimeMillis(), BankTransaction.Type.FEE, "Shatter Bank", "Shopping Network unlocked for coin value " + cost, 0, -1, 0, serverId()));
        then.accept(BankResult.ok(account(server, id).pieces));
    }

    /** Admin, season end: nobody's Portable Banker reaches the Shopping Network any more. */
    public static int resetRemoteAll(MinecraftServer server) {
        BankLedger ledger = BankLedger.get(server);
        int n = 0;
        for (BankAccount a : ledger.all().values()) {
            if (a.remoteShops) {
                a.remoteShops = false;
                n++;
            }
        }
        ledger.setDirty();
        NetworkStorage.bankResetRemote();
        return n;
    }

    /** Marks the player as seen now, so later payments count as "while away". */
    public static void touch(MinecraftServer server, UUID id) {
        BankLedger ledger = BankLedger.get(server);
        long now = System.currentTimeMillis();
        ledger.account(id).seenMs = now;
        ledger.setDirty();
        NetworkStorage.bankFlags(id, null, null, now);
    }

    // ------------------------------------------------------------------ players

    /** Finds a player by name: online here, known to this server, known to the network, or in the profile cache. */
    public static void resolve(MinecraftServer server, String name, Consumer<Map.Entry<UUID, String>> then) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            then.accept(Map.entry(online.getUUID(), online.getGameProfile().getName()));
            return;
        }
        UUID known = BankLedger.get(server).byName(name);
        if (known != null) {
            then.accept(Map.entry(known, account(server, known).name));
            return;
        }
        if (NetworkStorage.enabled()) {
            NetworkStorage.bankFind(name, hit -> {
                if (hit != null) {
                    then.accept(hit);
                } else {
                    then.accept(fromProfileCache(server, name));
                }
            });
            return;
        }
        then.accept(fromProfileCache(server, name));
    }

    @Nullable
    private static Map.Entry<UUID, String> fromProfileCache(MinecraftServer server, String name) {
        if (server.getProfileCache() == null) {
            return null;
        }
        return server.getProfileCache().get(name).map(p -> Map.entry(p.getId(), p.getName())).orElse(null);
    }

    public static String nameOf(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        String known = account(server, id).name;
        if (!known.isEmpty()) {
            return known;
        }
        if (server.getProfileCache() != null) {
            var p = server.getProfileCache().get(id);
            if (p.isPresent()) {
                return p.get().getName();
            }
        }
        return id.toString().substring(0, 8);
    }

    public static String tierName(CoinTier tier) {
        String k = tier.key;
        return Character.toUpperCase(k.charAt(0)) + k.substring(1);
    }

    // ------------------------------------------------------------------ inside

    private static BankTransaction tx(BankTransaction.Type type, String with, String detail, long pieces) {
        return new BankTransaction(System.currentTimeMillis(), type, with, detail, pieces, -1, 0, serverId());
    }

    /** Writes a log line locally and, on a network, to the database. */
    static void record(MinecraftServer server, UUID id, BankTransaction t) {
        BankLedger.get(server).record(id, t);
        NetworkStorage.bankLog(id, t);
    }

    /** Re-reads one account from the database into the cache (network only). */
    public static void refresh(MinecraftServer server, UUID id) {
        if (!NetworkStorage.enabled()) {
            return;
        }
        NetworkStorage.bankRead(id, row -> {
            if (row != null) {
                account(server, id).copyFrom(row);
                BankLedger.get(server).setDirty();
            }
        });
    }

    /** The one place balances change: a signed pieces delta and signed coin deltas, refused as a whole if anything would go below zero. */
    private static void adjust(MinecraftServer server, UUID id, long dPieces, @Nullable int[] dCoins, BankTransaction t, Consumer<BankResult> then) {
        BankLedger ledger = BankLedger.get(server);
        BankAccount a = ledger.account(id);
        int[] dc = dCoins == null ? new int[CoinTier.values().length] : dCoins;
        if (!NetworkStorage.enabled()) {
            if (a.pieces + dPieces < 0) {
                then.accept(BankResult.fail("short"));
                return;
            }
            for (int i = 0; i < dc.length; i++) {
                if (a.coins[i] + dc[i] < 0) {
                    then.accept(BankResult.fail("short"));
                    return;
                }
            }
            a.pieces += dPieces;
            for (int i = 0; i < dc.length; i++) {
                a.coins[i] += dc[i];
            }
            ledger.record(id, t);
            then.accept(BankResult.ok(a.pieces));
            return;
        }
        NetworkStorage.bankAdjust(id, dPieces, dc, row -> {
            if (row == null) {
                then.accept(BankResult.fail("short"));
                return;
            }
            a.copyFrom(row);
            ledger.setDirty();
            record(server, id, t);
            then.accept(BankResult.ok(row.pieces));
        });
    }

    private static void tellReceived(MinecraftServer server, UUID to, String from, long amount) {
        NetworkStorage.tell(server, to, Component.translatable("message.bsp_core.bank.received", format(amount), from));
    }
}
