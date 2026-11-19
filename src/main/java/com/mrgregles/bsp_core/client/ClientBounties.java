package com.mrgregles.bsp_core.client;

import com.mrgregles.bsp_core.bounty.BountySummary;

import java.util.List;

/**
 * The bounties as last sent by the server ({@link BountySummary}): what the Bounty board on Score Screens and the Bounty Board's
 * poster screens draw. Times are the server's: {@link #now()} is this client's clock moved to it.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.mrgregles.bsp_core.BSPCore.MODID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class ClientBounties {
    public static final net.minecraft.resources.ResourceLocation BOARD = new net.minecraft.resources.ResourceLocation(com.mrgregles.bsp_core.BSPCore.MODID, "bounties");
    private static volatile List<BountySummary.Entry> entries = List.of();
    /** The server's clock minus this client's, when the summary came. */
    private static volatile long offset;
    /** Counts summaries, so a renderer can tell the list changed. */
    private static volatile int serial;

    private ClientBounties() {}

    public static void accept(long serverNow, List<BountySummary.Entry> list) {
        offset = serverNow - System.currentTimeMillis();
        entries = List.copyOf(list);
        serial++;
        com.mrgregles.bsp_core.api.BspBoards.invalidate(BOARD);
    }

    /** The Bounties board on Score Screens. */
    public static void register() {
        com.mrgregles.bsp_core.api.BspBoards.register(BOARD, net.minecraft.network.chat.Component.translatable("screen.bsp_core.bounty.title"), ClientBounties::lines);
    }

    /** One target's bounties added up. */
    private static final class Target {
        final java.util.UUID id;
        final String name;
        long pieces, worth, soonest = Long.MAX_VALUE;
        final long[] coins = new long[com.mrgregles.bsp_core.coin.CoinTier.values().length];
        int count, hunters;
        boolean bonus;

        Target(java.util.UUID id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static final java.util.Map<java.util.UUID, net.minecraft.world.item.ItemStack> HEADS = new java.util.concurrent.ConcurrentHashMap<>();

    /** A player head showing this player's skin, made once. */
    static net.minecraft.world.item.ItemStack head(java.util.UUID id, String name) {
        return HEADS.computeIfAbsent(id, k -> {
            net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PLAYER_HEAD);
            // the name as a string: the item renderer looks the skin up and fills the profile into this same stack once
            stack.getOrCreateTag().putString("SkullOwner", name);
            return stack;
        });
    }

    /**
     * One line a target, the biggest total first: their head, their name (with how many bounties when more than one), everything on
     * their head added up (pieces, and coins by tier), and a note with BONUS while any bonus runs, the time left on the bounty that
     * ends soonest, and how many hunt them.
     */
    public static List<com.mrgregles.bsp_core.api.BspBoards.BoardLine> lines() {
        long now = now();
        java.util.Map<java.util.UUID, Target> by = new java.util.LinkedHashMap<>();
        long ppc = 1000;
        for (BountySummary.Entry e : entries) {
            if (e.endsMs() <= now) {
                continue;
            }
            Target t = by.computeIfAbsent(e.target(), k -> new Target(e.target(), e.targetName()));
            t.count++;
            t.hunters += e.hunters();
            t.soonest = Math.min(t.soonest, e.endsMs());
            t.bonus |= e.bonus() > 0 && now < e.bonusEndsMs();
            if (e.coinTier() == com.mrgregles.bsp_core.bounty.Bounty.PIECES) {
                t.pieces += e.amount();
                t.worth += e.amount();
            } else if (e.coinTier() >= 0 && e.coinTier() < t.coins.length) {
                t.coins[e.coinTier()] += e.amount();
                t.worth += e.amount() * ppc * (1L << e.coinTier());
            }
        }
        List<Target> sorted = new java.util.ArrayList<>(by.values());
        sorted.sort(java.util.Comparator.comparingLong((Target t) -> -t.worth));
        List<com.mrgregles.bsp_core.api.BspBoards.BoardLine> out = new java.util.ArrayList<>();
        for (Target t : sorted) {
            StringBuilder value = new StringBuilder();
            if (t.pieces > 0) {
                value.append(com.mrgregles.bsp_core.bank.Bank.format(t.pieces));
            }
            for (int i = t.coins.length - 1; i >= 0; i--) {
                if (t.coins[i] > 0) {
                    if (value.length() > 0) {
                        value.append(" + ");
                    }
                    value.append(t.coins[i]).append(' ').append(com.mrgregles.bsp_core.bank.Bank.tierName(com.mrgregles.bsp_core.coin.CoinTier.values()[i]));
                }
            }
            net.minecraft.network.chat.Component text = t.count > 1 ? net.minecraft.network.chat.Component.translatable("screen.bsp_core.bounty.times", t.name, t.count)
                    : net.minecraft.network.chat.Component.literal(t.name);
            net.minecraft.network.chat.Component note = net.minecraft.network.chat.Component.translatable(t.bonus ? "screen.bsp_core.bounty.note_bonus" : "screen.bsp_core.bounty.note",
                    com.mrgregles.bsp_core.bounty.Bounties.span(t.soonest - now), t.hunters);
            out.add(new com.mrgregles.bsp_core.api.BspBoards.BoardLine(text, net.minecraft.network.chat.Component.literal(value.toString()), head(t.id, t.name), 0, note));
        }
        return out;
    }

    /** Every running bounty, worth first. */
    public static List<BountySummary.Entry> entries() {
        return entries;
    }

    /** The server's time now, as far as this client can tell. */
    public static long now() {
        return System.currentTimeMillis() + offset;
    }

    /** "4 d 6 h left", "5 h 20 m left", "12 m left", or "ended". */
    public static net.minecraft.network.chat.Component left(long ms) {
        return ms <= 0 ? net.minecraft.network.chat.Component.translatable("gui.bsp_core.bounty.ended")
                : net.minecraft.network.chat.Component.translatable("gui.bsp_core.bounty.left", com.mrgregles.bsp_core.bounty.Bounties.span(ms));
    }

    public static int serial() {
        return serial;
    }

    /** The world was left: nothing is known any more. */
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        entries = List.of();
        serial++;
    }
}
