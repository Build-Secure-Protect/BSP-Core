package com.mrgregles.bsp_core.compat.regionguard;

import com.mrgregles.bsp_core.BSPCore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * BSP Region Guard's land claims, for the Land Office screen. Region Guard owns the plots, the
 * prices, the rules and the claims; this adapter only asks for listings and quotes and places
 * orders through its claim service, which charges the bank itself. Region Guard is optional and
 * reached lazily; without it the office lists nothing and says so.
 */
public final class RegionGuardClaims {
    private static final String MOD_ID = "bsp_region_guard";
    private static Boolean present;
    private static boolean warned;

    private RegionGuardClaims() {}

    /** A plot for sale, an open area, one of the player's own claims, or another player's claim nearby; ground as x/z bounds (y does not matter). */
    public record Plot(String id, String name, int minX, int minZ, int maxX, int maxZ, long price, long costPerBlock, int minSize, int maxSize, byte kind, String owner) {
        public static final byte FOR_SALE = 0, OPEN = 1, MINE = 2, OTHER = 3;
        /** How far around the player the office looks for other claims, in blocks. */
        public static final int LOOK = 160;

        public int width() {
            return maxX - minX + 1;
        }

        public int depth() {
            return maxZ - minZ + 1;
        }

        public boolean containsXZ(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        public boolean contains(int[] ground) {
            return ground[0] >= minX && ground[2] <= maxX && ground[1] >= minZ && ground[3] <= maxZ;
        }
    }

    /** Region Guard's answer: whether it went through, a sentence for the player, the claim id, the price or refund. */
    public record Result(boolean ok, String message, @Nullable String claimId, long price) {}

    public static boolean present() {
        if (present == null) {
            present = ModList.get().isLoaded(MOD_ID);
        }
        return present;
    }

    private static Result absent() {
        return new Result(false, "Region Guard is not installed on this server.", null, 0);
    }

    private static void warn(Throwable e) {
        if (!warned) {
            warned = true;
            BSPCore.LOGGER.warn("BSP-Core could not use Region Guard's claims ({}). Is Region Guard's API older than expected?", e.toString());
        }
    }

    /** Whether Region Guard prices claims in Shatter Coins ({@code claims.currency = coins}); false when absent or older than API 6. */
    public static boolean paysInCoins() {
        if (!present()) {
            return false;
        }
        try {
            return Api.claims().paysInCoins();
        } catch (LinkageError | RuntimeException e) {
            return false; // a Region Guard before API 6: pieces, as always
        }
    }

    /** An amount for a plot or open area as Region Guard words it ("5 gold coins"); null when it cannot say (older than API 6). */
    @Nullable
    public static String priceText(ServerLevel level, String regionId, long amount) {
        if (!present()) {
            return null;
        }
        try {
            return Api.claims().priceText(level, regionId, amount);
        } catch (LinkageError | RuntimeException e) {
            return null;
        }
    }

    /** Plots for sale, open areas, the player's own claims, and other players' claims within {@link Plot#LOOK} blocks of {@code around}: every plot in the world. */
    public static List<Plot> listings(ServerLevel level, UUID player, net.minecraft.core.BlockPos around) {
        return listings(level, player, around, null);
    }

    /**
     * As {@link #listings(ServerLevel, UUID, net.minecraft.core.BlockPos)}, but at a Land Office block ({@code office} not null) the plots for
     * sale and open areas are the ones Region Guard lists for that office (API 8, request RG-C15). Without an office (a Land Office opened by
     * the wand outline), or with a Region Guard older than API 8, every plot in the world.
     */
    public static List<Plot> listings(ServerLevel level, UUID player, net.minecraft.core.BlockPos around, @Nullable net.minecraft.core.BlockPos office) {
        if (!present()) {
            return List.of();
        }
        try {
            return Api.listings(level, player, around, office);
        } catch (LinkageError | RuntimeException e) {
            warn(e);
            return List.of();
        }
    }

    private static boolean warnedOld;

    /**
     * The office's own answer when there is an office and Region Guard can give one; the world-wide answer without an office, or when
     * Region Guard is older than API 8 (the office call is missing: a {@link LinkageError}). Public for the tests.
     */
    public static <T> T byOffice(@Nullable net.minecraft.core.BlockPos office, java.util.function.Supplier<T> atOffice, java.util.function.Supplier<T> worldWide) {
        if (office == null) {
            return worldWide.get();
        }
        try {
            return atOffice.get();
        } catch (LinkageError e) {
            warnOld(e);
            return worldWide.get();
        }
    }

    private static void warnOld(Throwable e) {
        if (!warnedOld) {
            warnedOld = true;
            BSPCore.LOGGER.warn("Region Guard is older than API 8 ({}): every Land Office lists every plot in the world.", e.toString());
        }
    }

    /** GameTests only: answers {@link #listedAt} in place of Region Guard (office, region id → listed there); null for Region Guard's own answer. */
    @Nullable
    private static java.util.function.BiPredicate<net.minecraft.core.BlockPos, String> listedHook;

    public static void useListed(@Nullable java.util.function.BiPredicate<net.minecraft.core.BlockPos, String> hook) {
        listedHook = hook;
    }

    /**
     * Whether the Land Office at {@code office} lists a plot or open area, so it may be quoted and sold there (API 8). True without an
     * office (the wand outline's office sells anywhere), without Region Guard (it sells nothing anyway) and with a Region Guard older than
     * API 8 (its offices list every plot).
     */
    public static boolean listedAt(ServerLevel level, @Nullable net.minecraft.core.BlockPos office, String regionId) {
        if (office == null) {
            return true;
        }
        if (listedHook != null) {
            return listedHook.test(office, regionId);
        }
        if (!present()) {
            return true;
        }
        return checkListed(() -> Api.listedAt(level, office, regionId));
    }

    /** Region Guard's answer to "listed here?": yes when it is too old to say (API before 8), no when the call fails. Public for the tests. */
    public static boolean checkListed(java.util.function.BooleanSupplier ask) {
        try {
            return ask.getAsBoolean();
        } catch (LinkageError e) {
            warnOld(e);
            return true;
        } catch (RuntimeException e) {
            warn(e);
            return false;
        }
    }

    /** The ground the player outlined with Region Guard's wand (minX, minZ, maxX, maxZ), or null. */
    @Nullable
    public static int[] selection(ServerPlayer player) {
        if (!present()) {
            return null;
        }
        try {
            return Api.selection(player);
        } catch (LinkageError | RuntimeException e) {
            warn(e);
            return null;
        }
    }

    private static boolean listening;

    /** Listens to Region Guard's claim events once, so open offices redraw when a claim is bought, grown or given up. */
    public static void ensureListeners() {
        if (listening || !present()) {
            return;
        }
        listening = true;
        try {
            Api.listen();
        } catch (LinkageError | RuntimeException e) {
            warn(e);
        }
    }

    public static boolean claimsAllowed(ServerLevel level) {
        if (!present()) {
            return false;
        }
        try {
            return Api.claimsAllowed(level);
        } catch (LinkageError | RuntimeException e) {
            warn(e);
            return false;
        }
    }

    public static Result quotePlot(ServerPlayer player, String plotId) {
        if (!present()) {
            return absent();
        }
        try {
            return Api.quotePlot(player, plotId);
        } catch (LinkageError | RuntimeException e) {
            warn(e);
            return absent();
        }
    }

    public static Result quoteOpen(ServerPlayer player, String openId, int[] ground) {
        if (!present()) {
            return absent();
        }
        try {
            return Api.quoteOpen(player, openId, ground);
        } catch (LinkageError | RuntimeException e) {
            warn(e);
            return absent();
        }
    }

    public static void purchasePlot(ServerPlayer player, String plotId, Consumer<Result> then) {
        if (!present()) {
            then.accept(absent());
            return;
        }
        try {
            Api.purchasePlot(player, plotId, then);
        } catch (LinkageError | RuntimeException e) {
            warn(e);
            then.accept(absent());
        }
    }

    public static void purchaseOpen(ServerPlayer player, String openId, int[] ground, Consumer<Result> then) {
        if (!present()) {
            then.accept(absent());
            return;
        }
        try {
            Api.purchaseOpen(player, openId, ground, then);
        } catch (LinkageError | RuntimeException e) {
            warn(e);
            then.accept(absent());
        }
    }

    public static void enlarge(ServerPlayer player, String claimId, int[] ground, Consumer<Result> then) {
        if (!present()) {
            then.accept(absent());
            return;
        }
        try {
            Api.enlarge(player, claimId, ground, then);
        } catch (LinkageError | RuntimeException e) {
            warn(e);
            then.accept(absent());
        }
    }

    public static Result abandon(ServerPlayer player, String claimId) {
        if (!present()) {
            return absent();
        }
        try {
            return Api.abandon(player, claimId);
        } catch (LinkageError | RuntimeException e) {
            warn(e);
            return absent();
        }
    }

    /** Touches Region Guard's classes only when it is installed. */
    private static final class Api {
        private static com.mrgregles.bsp_region_guard.api.ClaimService claims() {
            return com.mrgregles.bsp_region_guard.api.RegionGuardApi.get().claims();
        }

        private static com.mrgregles.bsp_region_guard.api.Cuboid cuboid(ServerLevel level, int[] g) {
            return new com.mrgregles.bsp_region_guard.api.Cuboid(Math.min(g[0], g[2]), level.getMinBuildHeight(), Math.min(g[1], g[3]), Math.max(g[0], g[2]), level.getMaxBuildHeight() - 1, Math.max(g[1], g[3]));
        }

        private static Result result(com.mrgregles.bsp_region_guard.api.ClaimResult r) {
            return new Result(r.ok(), r.message(), r.claimId(), r.price());
        }

        static List<Plot> listings(ServerLevel level, UUID player, net.minecraft.core.BlockPos around, @Nullable net.minecraft.core.BlockPos office) {
            List<Plot> out = new ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            var forSale = byOffice(office, () -> claims().plotsForSale(level, office), () -> claims().plotsForSale(level));
            var open = byOffice(office, () -> claims().openAreas(level, office), () -> claims().openAreas(level));
            for (var l : forSale) {
                out.add(new Plot(l.id(), l.name(), l.bounds().minX(), l.bounds().minZ(), l.bounds().maxX(), l.bounds().maxZ(), l.price(), l.costPerBlock(), l.minSize(), l.maxSize(), Plot.FOR_SALE, ""));
                seen.add(l.id());
            }
            for (var l : open) {
                out.add(new Plot(l.id(), l.name(), l.bounds().minX(), l.bounds().minZ(), l.bounds().maxX(), l.bounds().maxZ(), l.price(), l.costPerBlock(), l.minSize(), l.maxSize(), Plot.OPEN, ""));
                seen.add(l.id());
            }
            var regions = com.mrgregles.bsp_region_guard.api.RegionGuardApi.get().regions();
            for (String id : claims().claimsOf(level, player)) {
                var b = regions.bounds(level, id);
                if (b != null) {
                    out.add(new Plot(id, id, b.minX(), b.minZ(), b.maxX(), b.maxZ(), 0, 0, 0, 0, Plot.MINE, ""));
                    seen.add(id);
                }
            }
            // other players' claims around the player, so the map shows how close an outline lies to them
            var area = new com.mrgregles.bsp_region_guard.api.Cuboid(around.getX() - Plot.LOOK, level.getMinBuildHeight(), around.getZ() - Plot.LOOK, around.getX() + Plot.LOOK, level.getMaxBuildHeight() - 1, around.getZ() + Plot.LOOK);
            for (var n : claims().claimsNear(level, area)) {
                if (seen.add(n.id()) && "claim".equals(n.type()) && !player.equals(n.owner())) {
                    var b = n.bounds();
                    out.add(new Plot(n.id(), n.name(), b.minX(), b.minZ(), b.maxX(), b.maxZ(), 0, 0, 0, 0, Plot.OTHER, n.ownerName() == null ? "" : n.ownerName()));
                }
            }
            return out;
        }

        @Nullable
        static int[] selection(ServerPlayer player) {
            var c = claims().selection(player);
            return c == null ? null : new int[]{c.minX(), c.minZ(), c.maxX(), c.maxZ()};
        }

        static void listen() {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((com.mrgregles.bsp_region_guard.api.event.ClaimPurchasedEvent e) -> refreshOffices(e.getEntity().getServer()));
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener((com.mrgregles.bsp_region_guard.api.event.ClaimAbandonedEvent e) -> refreshOffices(e.getEntity().getServer()));
        }

        /** Every player with the office open sees the change at once. */
        private static void refreshOffices(@Nullable net.minecraft.server.MinecraftServer server) {
            if (server == null) {
                return;
            }
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                if (sp.containerMenu instanceof com.mrgregles.bsp_core.land.LandOfficeMenu) {
                    com.mrgregles.bsp_core.land.LandOfficeMenu.sync(sp, "", true);
                }
            }
        }

        static boolean listedAt(ServerLevel level, net.minecraft.core.BlockPos office, String regionId) {
            return claims().listedAt(level, office, regionId);
        }

        static boolean claimsAllowed(ServerLevel level) {
            return claims().claimsAllowedIn(level);
        }

        static Result quotePlot(ServerPlayer player, String plotId) {
            return result(claims().quotePlot(player, plotId));
        }

        static Result quoteOpen(ServerPlayer player, String openId, int[] ground) {
            return result(claims().quoteOpen(player, openId, cuboid(player.serverLevel(), ground)));
        }

        static void purchasePlot(ServerPlayer player, String plotId, Consumer<Result> then) {
            claims().purchasePlot(player, plotId, r -> then.accept(result(r)));
        }

        static void purchaseOpen(ServerPlayer player, String openId, int[] ground, Consumer<Result> then) {
            claims().purchaseOpen(player, openId, cuboid(player.serverLevel(), ground), r -> then.accept(result(r)));
        }

        static void enlarge(ServerPlayer player, String claimId, int[] ground, Consumer<Result> then) {
            claims().enlarge(player, claimId, cuboid(player.serverLevel(), ground), r -> then.accept(result(r)));
        }

        static Result abandon(ServerPlayer player, String claimId) {
            return result(claims().abandon(player, claimId));
        }
    }
}
