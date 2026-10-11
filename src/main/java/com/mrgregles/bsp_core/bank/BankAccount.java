package com.mrgregles.bsp_core.bank;

import com.mrgregles.bsp_core.coin.CoinTier;
import net.minecraft.nbt.CompoundTag;

/**
 * One player's bank account: a balance of Shatter Pieces, the Shatter Coins they keep in the bank by
 * tier, and a few switches. Pieces are never an item; coins in the bank earn no interest (that is what
 * the Coin Vault is for) and cannot be stolen.
 *
 * <p>On a single server the {@link BankLedger} is the record. On a network the database is the record
 * and this object is the server's copy of it, refreshed after every change and at login.
 */
public final class BankAccount {
    public long pieces;
    public final int[] coins = new int[CoinTier.values().length];
    /** Set once the starting pieces were given, so they are never given twice. */
    public boolean granted;
    /** Whether other players may pay this one. */
    public boolean acceptPay = true;
    /** Remote shop access on the Portable Banker: bought with coins, reset by admins at season end. */
    public boolean remoteShops;
    public String name = "";
    /** When the player was last seen on any server; payments newer than this are announced at login. */
    public long seenMs;
    /** Safety deposit box: unlocked slots (0 until first rented), when the rent runs out, and what is inside. */
    public int boxSlots;
    public long boxUntilMs;
    public final net.minecraftforge.items.ItemStackHandler box = new net.minecraftforge.items.ItemStackHandler(DepositBox.SLOTS_MAX);

    public int coins(CoinTier tier) {
        return coins[tier.ordinal()];
    }

    /** Value of the banked coins in copper-coin units (the unit the totem prices use). */
    public int coinValue() {
        int v = 0;
        for (CoinTier t : CoinTier.values()) {
            v += coins[t.ordinal()] * t.value();
        }
        return v;
    }

    public void copyFrom(BankAccount o) {
        pieces = o.pieces;
        System.arraycopy(o.coins, 0, coins, 0, coins.length);
        granted = o.granted;
        acceptPay = o.acceptPay;
        remoteShops = o.remoteShops;
        name = o.name;
        seenMs = o.seenMs;
        boxSlots = o.boxSlots;
        boxUntilMs = o.boxUntilMs;
        for (int i = 0; i < box.getSlots(); i++) {
            box.setStackInSlot(i, o.box.getStackInSlot(i).copy());
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Pieces", pieces);
        tag.putIntArray("Coins", coins.clone());
        tag.putBoolean("Granted", granted);
        tag.putBoolean("AcceptPay", acceptPay);
        tag.putBoolean("RemoteShops", remoteShops);
        tag.putString("Name", name);
        tag.putLong("Seen", seenMs);
        tag.putInt("BoxSlots", boxSlots);
        tag.putLong("BoxUntil", boxUntilMs);
        tag.put("Box", box.serializeNBT());
        return tag;
    }

    public static BankAccount load(CompoundTag tag) {
        BankAccount a = new BankAccount();
        a.pieces = tag.getLong("Pieces");
        int[] c = tag.getIntArray("Coins");
        System.arraycopy(c, 0, a.coins, 0, Math.min(c.length, a.coins.length));
        a.granted = tag.getBoolean("Granted");
        a.acceptPay = !tag.contains("AcceptPay") || tag.getBoolean("AcceptPay");
        a.remoteShops = tag.getBoolean("RemoteShops");
        a.name = tag.getString("Name");
        a.seenMs = tag.getLong("Seen");
        a.boxSlots = tag.getInt("BoxSlots");
        a.boxUntilMs = tag.getLong("BoxUntil");
        if (tag.contains("Box")) {
            a.box.deserializeNBT(tag.getCompound("Box"));
        }
        return a;
    }
}
