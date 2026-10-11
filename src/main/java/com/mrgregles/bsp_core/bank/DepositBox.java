package com.mrgregles.bsp_core.bank;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.BSPCore;
import com.mrgregles.bsp_core.coin.CoinTier;
import com.mrgregles.bsp_core.storage.NetworkStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * The rules of the safety deposit box: one box per player for the whole network, rented by the day
 * (coins or pieces), 6 slots at first and 3 more per purchase up to 18, items kept when the rent
 * runs out but locked until the rent plus a holding fee per lapsed day is paid. Shatter Coins and
 * totems (the {@code bsp_core:no_safety_deposit} tag) cannot go in.
 */
public final class DepositBox {
    private DepositBox() {}

    public static final int SLOTS_DEFAULT = 6, SLOTS_STEP = 3, SLOTS_MAX = 18;
    public static final long DAY_MS = 86_400_000L;
    public static final TagKey<Item> FORBIDDEN = ItemTags.create(new ResourceLocation(BSPCore.MODID, "no_safety_deposit"));

    public enum State { NONE, OPEN, EXPIRED }

    public static boolean allowed(ItemStack stack) {
        return !stack.is(FORBIDDEN);
    }

    public static State state(BankAccount a, long now) {
        return a.boxSlots <= 0 ? State.NONE : a.boxUntilMs > now ? State.OPEN : State.EXPIRED;
    }

    /** Whole days the box has been left unpaid (rounded up); 0 unless expired. */
    public static int lapsedDays(BankAccount a, long now) {
        return state(a, now) == State.EXPIRED ? (int) Math.min(100_000, (now - a.boxUntilMs + DAY_MS - 1) / DAY_MS) : 0;
    }

    public static int daysLeft(BankAccount a, long now) {
        return state(a, now) == State.OPEN ? (int) ((a.boxUntilMs - now + DAY_MS - 1) / DAY_MS) : 0;
    }

    public static long rentPerDay() {
        return BSPConfig.getOr(BSPConfig.BOX_RENT_PER_DAY, 50);
    }

    public static long feePerDay() {
        return BSPConfig.getOr(BSPConfig.BOX_FEE_PER_DAY, 150);
    }

    public static long slotStepPieces() {
        return BSPConfig.getOr(BSPConfig.BOX_SLOT_STEP_PIECES, 2000);
    }

    /** Pieces that {@code days} more days cost now: the rent, plus the holding fee if the box has lapsed. */
    public static long rentCost(BankAccount a, long now, int days) {
        return days * rentPerDay() + lapsedDays(a, now) * feePerDay();
    }

    /** Coins of a tier that cover {@code cost} pieces. */
    public static int coinsFor(long cost, CoinTier tier) {
        long per = Bank.piecesPerCoin(tier);
        return (int) ((cost + per - 1) / per);
    }

    // ------------------------------------------------------------------ paying

    /** Takes {@code cost} in pieces (tier -1) or in coins of a tier from the inventory, crediting any coin overpayment as pieces. */
    private static void pay(MinecraftServer server, Player player, long cost, int tier, BankTransaction.Type type, String detail, Consumer<BankResult> then) {
        UUID id = player.getUUID();
        if (cost <= 0) {
            then.accept(BankResult.ok(Bank.pieces(server, id)));
            return;
        }
        CoinTier coin = CoinTier.byOrdinal(tier);
        if (coin == null) {
            Bank.take(server, id, cost, type, "Safety deposit box", detail, then);
            return;
        }
        int n = coinsFor(cost, coin);
        if (player.getInventory().countItem(coin.coin()) < n) {
            then.accept(BankResult.fail("coins_short"));
            return;
        }
        player.getInventory().clearOrCountMatchingItems(st -> st.is(coin.coin()), n, player.inventoryMenu.getCraftSlots());
        player.inventoryMenu.broadcastChanges();
        Bank.record(server, id, new BankTransaction(System.currentTimeMillis(), type, "Safety deposit box", detail + ", paid with " + n + " " + Bank.tierName(coin) + " coins", 0, tier, -n, Bank.serverId()));
        long change = n * Bank.piecesPerCoin(coin) - cost;
        if (change > 0) {
            Bank.give(server, id, change, BankTransaction.Type.CONVERT, "Safety deposit box", "change from " + detail, r -> then.accept(BankResult.ok(r.ok() ? r.pieces() : Bank.pieces(server, id))));
        } else {
            then.accept(BankResult.ok(Bank.pieces(server, id)));
        }
    }

    /** Rents {@code days} more days (opening a new box or a lapsed one). Fails with "days" (out of range) or the payment's key. */
    public static void rent(MinecraftServer server, Player player, int days, int tier, Consumer<BankResult> then) {
        UUID id = player.getUUID();
        BankAccount a = Bank.account(server, id);
        long now = System.currentTimeMillis();
        int max = BSPConfig.getOr(BSPConfig.BOX_MAX_DAYS, 365);
        if (days <= 0 || daysLeft(a, now) + days > max) {
            then.accept(BankResult.fail("days"));
            return;
        }
        long cost = rentCost(a, now, days);
        int lapsed = lapsedDays(a, now);
        String detail = "rent " + days + " day(s)" + (lapsed > 0 ? " and " + lapsed + " lapsed day(s)" : "");
        pay(server, player, cost, tier, BankTransaction.Type.RENT, detail, r -> {
            if (!r.ok()) {
                then.accept(r);
                return;
            }
            if (state(a, now) == State.OPEN) {
                a.boxUntilMs += days * DAY_MS;
            } else {
                a.boxUntilMs = now + days * DAY_MS;
                a.boxSlots = Math.max(a.boxSlots, SLOTS_DEFAULT);
            }
            saved(server, id);
            then.accept(BankResult.ok(a.boxUntilMs));
        });
    }

    /** Buys three more slots on an open box, up to the most. Fails with "closed", "full" or the payment's key. */
    public static void addSlots(MinecraftServer server, Player player, int tier, Consumer<BankResult> then) {
        UUID id = player.getUUID();
        BankAccount a = Bank.account(server, id);
        if (state(a, System.currentTimeMillis()) != State.OPEN) {
            then.accept(BankResult.fail("closed"));
            return;
        }
        if (a.boxSlots >= SLOTS_MAX) {
            then.accept(BankResult.fail("full"));
            return;
        }
        pay(server, player, slotStepPieces(), tier, BankTransaction.Type.FEE, "3 more box slots", r -> {
            if (!r.ok()) {
                then.accept(r);
                return;
            }
            a.boxSlots = Math.min(SLOTS_MAX, a.boxSlots + SLOTS_STEP);
            saved(server, id);
            then.accept(BankResult.ok(a.boxSlots));
        });
    }

    /** Writes the box through: the local ledger is marked, the network gets the row, the client its state. */
    public static void saved(MinecraftServer server, UUID id) {
        BankLedger ledger = BankLedger.get(server);
        BankAccount a = ledger.account(id);
        ledger.setDirty();
        if (NetworkStorage.enabled()) {
            NetworkStorage.bankBoxWrite(id, a.boxSlots, a.boxUntilMs, a.box.serializeNBT().toString());
        }
        Bank.pushState(server, id);
    }

    // ------------------------------------------------------------------ opening

    /** Opens the player's box at a Safety Deposit Box block, after fetching the network's copy. */
    public static void open(ServerPlayer sp, BlockPos pos) {
        UUID id = sp.getUUID();
        if (!NetworkStorage.enabled()) {
            openNow(sp, pos);
            return;
        }
        NetworkStorage.bankBoxRead(id, row -> {
            if (row != null && !sp.hasDisconnected()) {
                BankAccount a = Bank.account(sp.server, id);
                a.boxSlots = (Integer) row[0];
                a.boxUntilMs = (Long) row[1];
                try {
                    CompoundTag tag = TagParser.parseTag((String) row[2]);
                    a.box.deserializeNBT(tag);
                } catch (Exception e) {
                    BSPCore.LOGGER.error("BSP-Core bank: could not read the deposit box of {}: {}", id, e.toString());
                }
                BankLedger.get(sp.server).setDirty();
            }
            if (!sp.hasDisconnected()) {
                openNow(sp, pos);
            }
        });
    }

    private static void openNow(ServerPlayer sp, BlockPos pos) {
        BankAccount a = Bank.account(sp.server, sp.getUUID());
        boolean open = state(a, System.currentTimeMillis()) == State.OPEN;
        sp.level().playSound(null, pos, net.minecraft.sounds.SoundEvents.IRON_TRAPDOOR_OPEN, net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.4f);
        NetworkHooks.openScreen(sp, new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("block.bsp_core.safety_deposit_box");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inv, Player p) {
                return new DepositBoxMenu(id, inv, pos, open, a.boxSlots, a.box);
            }
        }, buf -> {
            buf.writeBlockPos(pos);
            buf.writeBoolean(open);
            buf.writeVarInt(a.boxSlots);
        });
        DepositBoxMenu.sync(sp, "", null);
    }
}
