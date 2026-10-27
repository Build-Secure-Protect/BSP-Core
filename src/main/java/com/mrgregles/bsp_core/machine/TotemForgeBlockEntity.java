package com.mrgregles.bsp_core.machine;

import com.mrgregles.bsp_core.BSPConfig;
import com.mrgregles.bsp_core.material.MoltenMetals;
import com.mrgregles.bsp_core.material.TotemMouldItem;
import com.mrgregles.bsp_core.registry.ModBlockEntities;
import com.mrgregles.bsp_core.registry.ModBlocks;
import com.mrgregles.bsp_core.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Totem Forge's controller: a 2 by 2 by 3 multiblock (Totem Forge brief, 2026-10-27). The bottom layer
 * is the controller, the Metal Intake and two Forge Hearths; the middle layer is the hollow the mould
 * stands in and must be empty; the top layer is two Melt Tanks, the Pour Head and the Forge Cap.
 *
 * <p>Ingots of the metal bill ({@code totem_forge.bill} in the server config) go in through the intake or
 * the input slot and melt one at a time into that metal's tank, burning lava from the hearths; Molten
 * Tetrium, Magnatite and Illyrium can be piped into the intake instead. Each tank holds exactly what the
 * bill asks for, 90 mB an ingot. The mould pieces go into the hollow one by one, base first and the Totem
 * Core last. With the mould complete and every tank full, POUR starts the pour: free for a player who
 * owns no totem (it then sets in 5 days), paid in Shatter Coins for each totem owned (15 days); one pour or
 * setting per player across the network ({@link TotemForgeLedger}). The pour runs for half a minute and
 * empties the tanks; the totem then sets on real time, loaded or not. Once set, the pourer takes it out at a
 * right-click; anyone else must steal it, staying by the forge for 2.5 minutes while the pourer is warned.
 * Either way it comes out as a Forged Shatter Totem, tier I with no upgrades, owned by whoever took it.
 * {@link com.mrgregles.bsp_core.client.TotemForgeRenderer} draws it all.
 */
public class TotemForgeBlockEntity extends MultiblockControllerBlockEntity {
    public static final int MB_PER_INGOT = 90;
    /** The mould's places, in TotemForgeRenderer.PIECES order: seven pieces, then the Totem Core in the last. */
    public static final int PLACES = 8, CORE = PLACES - 1;
    private static final int INPUT = 0, LAVA = 1;
    private static final List<Slot> LAYOUT = List.of(new Slot(Role.INPUT, 44, 31), new Slot(Role.FUEL, 44, 58), new Slot(Role.RF, 152, 60));
    // rows back to front, columns left to right as seen from the front. bottom: Metal Intake behind the controller,
    // a Forge Hearth on the right of each. middle: the hollow. top: Melt Tanks on the left, Forge Cap behind the Pour Head.
    private static final String[][] PATTERN = {
            {"IH", "XH"},
            {"..", ".."},
            {"TC", "TP"}};

    /** One line of the metal bill: an item or a tag, and how many ingots of it one totem takes. */
    public record Metal(String key, @Nullable Item item, @Nullable TagKey<Item> tag, int ingots) {
        public boolean matches(ItemStack stack) {
            return !stack.isEmpty() && (item != null ? stack.is(item) : tag != null && stack.is(tag));
        }

        public int capacity() {
            return ingots * MB_PER_INGOT;
        }
    }

    /** The parsed bill and the config list it came from. */
    private record Bill(List<? extends String> source, List<Metal> lines) {}

    @Nullable
    private static volatile Bill cache;

    /**
     * The metal bill as the config gives it, read again when the config changes or the tags reload. A line whose tag
     * no item carries (a pack without Allthemodium, say) is left out with a warning, so the forge can still pour
     * (owner, 2026-10-27, audit D8); so is a line naming a missing item or a count it cannot read.
     */
    public static List<Metal> bill() {
        List<? extends String> src = BSPConfig.TFORGE_BILL.get();
        Bill c = cache;
        if (c == null || c.source() != src) {
            c = new Bill(src, parse(src));
            cache = c;
        }
        return c.lines();
    }

    /** Forgets the parsed bill, so the next call reads it again: the tags have changed. */
    public static void forgetBill() {
        cache = null;
    }

    private static List<Metal> parse(List<? extends String> src) {
        List<Metal> out = new ArrayList<>();
        for (String line : src) {
            int eq = line.lastIndexOf('=');
            int n;
            try {
                n = eq < 0 ? 0 : Integer.parseInt(line.substring(eq + 1));
            } catch (NumberFormatException e) {
                n = 0;
            }
            if (n <= 0 || n > MAX_BILL_INGOTS) {
                com.mrgregles.bsp_core.BSPCore.LOGGER.warn("Totem Forge bill: \"{}\" needs a count from 1 to {}; the line is left out", line, MAX_BILL_INGOTS);
                continue;
            }
            String what = line.substring(0, eq);
            if (what.startsWith("#")) {
                TagKey<Item> tag = TagKey.create(Registries.ITEM, new ResourceLocation(what.substring(1)));
                if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getTag(tag).map(set -> set.size() == 0).orElse(true)) {
                    com.mrgregles.bsp_core.BSPCore.LOGGER.warn("Totem Forge bill: no item carries {}; the line is left out", what);
                    continue;
                }
                out.add(new Metal(what, null, tag, n));
            } else {
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(what));
                if (item == null || item == Items.AIR) {
                    com.mrgregles.bsp_core.BSPCore.LOGGER.warn("Totem Forge bill: there is no item {}; the line is left out", what);
                    continue;
                }
                out.add(new Metal(what, item, null, n));
            }
        }
        return List.copyOf(out);
    }

    /** The most ingots one line of the bill may ask for (five digits, as the config allows). */
    public static final int MAX_BILL_INGOTS = 99_999;

    /** The parsed bill forgets itself when the tags reload, on the server and on a client alike. */
    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.mrgregles.bsp_core.BSPCore.MODID)
    public static final class BillEvents {
        private BillEvents() {}

        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onTagsUpdated(net.minecraftforge.event.TagsUpdatedEvent event) {
            forgetBill();
        }
    }

    @Nullable
    public static Metal metalFor(ItemStack stack) {
        for (Metal m : bill()) {
            if (m.matches(stack)) {
                return m;
            }
        }
        return null;
    }

    /** The bill line a molten metal fills: Molten Tetrium fills the Tetrium Ingot line, and so on. */
    @Nullable
    public static Metal metalFor(Fluid fluid) {
        for (MoltenMetals.Metal molten : MoltenMetals.Metal.values()) {
            if (MoltenMetals.of(molten).source().get().isSame(fluid)) {
                Item ingot = ForgeRegistries.ITEMS.getValue(new ResourceLocation(com.mrgregles.bsp_core.BSPCore.MODID, molten.key + "_ingot"));
                for (Metal m : bill()) {
                    if (m.item() == ingot) {
                        return m;
                    }
                }
            }
        }
        return null;
    }

    /** Molten metal in the tanks, in mB, by bill key. */
    private final Map<String, Integer> stored = new LinkedHashMap<>();
    /** The mould in the hollow, in TotemForgeRenderer.PIECES order: base, left and right foot, torso, left and right arm, head, Totem Core. */
    private final ItemStack[] mouldStacks = new ItemStack[PLACES];
    /** Mould pieces in the hollow, one bit each, in the same order. */
    private int mould;
    /** How full the metal tanks are, how far the pour has got and how far the totem has set, each 0 to 1. */
    private float metal, pour, set;
    private boolean showChanged;
    /** The player who started the pour, whether they paid, and how long the totem takes to set (fixed when the pour starts). */
    @Nullable
    private java.util.UUID pourer;
    private String pourerName = "";
    private boolean paid;
    private long setMillis, setStart;
    /** A steal of the set totem by someone other than the pourer, run like a placed totem's: who, the ticks left, the grace left outside the radius. */
    @Nullable
    private com.mrgregles.bsp_core.totem.StealState steal;
    /** The thief as they started; the player list is asked first, so a respawn or a GameTest player is found either way. */
    @Nullable
    private net.minecraft.server.level.ServerPlayer thiefPlayer;
    private float sentSet;
    private final LazyOptional<IFluidHandler> metalCap = LazyOptional.of(MetalTanks::new);

    public TotemForgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TOTEM_FORGE.get(), pos, state, LAYOUT, Fluids.LAVA);
        setSideMode(0, com.mrgregles.bsp_core.coin.CoinFactoryBlockEntity.SideMode.INPUT);
        java.util.Arrays.fill(mouldStacks, ItemStack.EMPTY);
    }

    // ------------------------------------------------------------------ what the renderer shows

    public int mouldMask() {
        return mould;
    }

    public float metalFraction() {
        return metal;
    }

    public float pourFraction() {
        return pour;
    }

    public float setFraction() {
        return set;
    }

    // ------------------------------------------------------------------ the metal tanks

    public int stored(Metal m) {
        return stored.getOrDefault(m.key(), 0);
    }

    /** Whether every tank holds its whole bill. */
    public boolean metalComplete() {
        for (Metal m : bill()) {
            if (stored(m) < m.capacity()) {
                return false;
            }
        }
        return true;
    }

    /** Adds up to {@code mb} of a metal, never past its line of the bill; returns what went in. */
    private int addMetal(Metal m, int mb, boolean simulate) {
        int room = Math.max(0, m.capacity() - stored(m)), in = Math.min(room, mb);
        if (in > 0 && !simulate) {
            stored.merge(m.key(), in, Integer::sum);
            refreshMetal();
        }
        return in;
    }

    /** Fills every tank to its line of the bill at once: for GameTests and for an admin trying the pour without gathering the metal. */
    public void fillTanks() {
        for (Metal m : bill()) {
            stored.put(m.key(), m.capacity());
        }
        refreshMetal();
    }

    private void refreshMetal() {
        long have = 0, want = 0;
        for (Metal m : bill()) {
            have += Math.min(stored(m), m.capacity());
            want += m.capacity();
        }
        metal = want == 0 ? 0 : (float) have / want;
        showChanged = true;
        setChanged();
    }

    /** What the Metal Intake offers pipes: the molten metals in, nothing out. */
    private final class MetalTanks implements IFluidHandler {
        private final MoltenMetals.Metal[] order = MoltenMetals.Metal.values();

        @Override
        public int getTanks() {
            return order.length;
        }

        @Nonnull
        @Override
        public FluidStack getFluidInTank(int tank) {
            Fluid fluid = MoltenMetals.of(order[tank]).source().get();
            Metal m = metalFor(fluid);
            return m == null || stored(m) == 0 ? FluidStack.EMPTY : new FluidStack(fluid, stored(m));
        }

        @Override
        public int getTankCapacity(int tank) {
            Metal m = metalFor(MoltenMetals.of(order[tank]).source().get());
            return m == null ? 0 : m.capacity();
        }

        @Override
        public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
            return MoltenMetals.of(order[tank]).source().get().isSame(stack.getFluid());
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            Metal m = resource.isEmpty() ? null : metalFor(resource.getFluid());
            return m == null || pour > 0 ? 0 : addMetal(m, resource.getAmount(), action.simulate());
        }

        @Nonnull
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Nonnull
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }

    @Override
    public <T> LazyOptional<T> portCapability(Capability<T> cap, BlockPos port, Block block) {
        if (block == ModBlocks.METAL_INTAKE.get() && cap == ForgeCapabilities.FLUID_HANDLER) {
            return metalCap.cast();
        }
        return super.portCapability(cap, port, block);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        metalCap.invalidate();
    }

    // ------------------------------------------------------------------ melting

    /** Ingots of {@code m} its tank still has room for, the last one counted even if only part of it fits. */
    public int ingotsNeeded(Metal m) {
        return (Math.max(0, m.capacity() - stored(m)) + MB_PER_INGOT - 1) / MB_PER_INGOT;
    }

    // the one input slot takes only ingots still needed, so a surplus never sits in it and blocks the other metals (audit item 5)
    @Override
    protected boolean isItemValid(int slot, ItemStack stack) {
        if (slot == LAVA) {
            return stack.is(Items.LAVA_BUCKET);
        }
        Metal m = slot == INPUT ? metalFor(stack) : null;
        return m != null && pour == 0 && ingotsNeeded(m) > 0;
    }

    // the intake stays an input, but the empty bucket a lava bucket leaves can be piped out, so bucket feeding keeps going
    @Override
    protected boolean alwaysExtractable(int slot, ItemStack stack) {
        return slot == LAVA && stack.is(Items.BUCKET);
    }

    @Override
    protected int stackLimit(int slot, ItemStack stack) {
        Metal m = slot == INPUT ? metalFor(stack) : null;
        return m == null ? 64 : ingotsNeeded(m);
    }

    // a tank a little short (piped metal comes 20 or 30 mB at a time) still melts its last ingot; what does not fit is lost (audit item 6)
    @Override
    protected boolean canWork() {
        Metal m = metalFor(items.getStackInSlot(INPUT));
        return m != null && pour == 0 && stored(m) < m.capacity() && tank.getFluidAmount() >= BSPConfig.TFORGE_LAVA_PER_INGOT.get();
    }

    @Override
    public int workTime() {
        return BSPConfig.TFORGE_TICKS_PER_INGOT.get();
    }

    @Override
    protected void finishJob() {
        ItemStack in = items.getStackInSlot(INPUT);
        Metal m = metalFor(in);
        if (m == null) {
            return;
        }
        drain((int) Math.round(BSPConfig.TFORGE_LAVA_PER_INGOT.get() * lavaFactor()));
        items.setStackInSlot(INPUT, in.copyWithCount(in.getCount() - 1));
        addMetal(m, MB_PER_INGOT, false);
    }

    @Override
    public void serverTick(ServerLevel level) {
        super.serverTick(level);
        if (pour > 0 && pour < 1) {
            pour = Math.min(1f, pour + 1f / (BSPConfig.TFORGE_POUR_SECONDS.get() * 20f));
            metal = 1 - pour; // the tank windows empty as the mould fills
            if (pour >= 1) {
                pourEnded(System.currentTimeMillis());
            }
            showChanged = true;
            if (pour >= 1 || level.getGameTime() % 20 == 0) {
                setChanged(); // saved once a second while pouring, not every tick
            }
        }
        if (pour >= 1 && set < 1 && level.getGameTime() % 20 == 0) {
            updateSet(level);
        }
        if (steal != null) {
            stealTick(level, 1);
        }
        checkWaiting(level);
        // pipes fill the tanks between jobs: tell the clients what the windows show, at most twice a second (the data alone, no re-mesh)
        if (showChanged && level.getGameTime() % 10 == 0) {
            showChanged = false;
            sendData();
        }
    }

    // ------------------------------------------------------------------ the pour

    /** Why a pour can or cannot start. FREE and PAID can. */
    public enum PourCheck { FREE, PAID, NOT_FORMED, MOULD, METAL, BUSY, ALREADY_POURING, COINS, NO_ANSWER, UNKNOWN, PRICE_CHANGED }

    /** The check, the totems the player owns and the coins the pour costs them. */
    public record PourPlan(PourCheck check, int owned, int coins) {
        public boolean ok() {
            return check == PourCheck.FREE || check == PourCheck.PAID;
        }
    }

    public static com.mrgregles.bsp_core.coin.CoinTier pourCoin() {
        for (var tier : com.mrgregles.bsp_core.coin.CoinTier.values()) {
            if (tier.key.equalsIgnoreCase(BSPConfig.TFORGE_COIN_TIER.get())) {
                return tier;
            }
        }
        return com.mrgregles.bsp_core.coin.CoinTier.ILLYRIUM;
    }

    /** Ticks a dropped totem still counts as its owner's after its last report (it reports every second, then places itself). */
    private static final long DROPPED_TICKS = 1200;

    /**
     * Totems the player owns anywhere (owner, audit D9): on this server those placed, carried (the cursor too), lying dropped,
     * held for a transfer or owed to them here; or as the network's score board counts them (every server's placed and
     * carried totems), whichever is more. A totem in the middle of being stolen from them is still theirs. Totems waiting for
     * them in the network's pending items come on top: the pour learns those with its network claim ({@link #requestPour}).
     */
    public static int ownedTotems(net.minecraft.server.level.ServerPlayer player) {
        java.util.UUID id = player.getUUID();
        var ledger = com.mrgregles.bsp_core.data.TotemLedger.get(player.server);
        var placed = ledger.placedFor(id);
        int local = placed == null ? 0 : placed.size();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (com.mrgregles.bsp_core.totem.TotemInventories.isTotem(player.getInventory().getItem(i))) {
                local++;
            }
        }
        if (com.mrgregles.bsp_core.totem.TotemInventories.isTotem(player.containerMenu.getCarried())) {
            local++;
        }
        local += ledger.droppedFor(id, player.server.overworld().getGameTime(), DROPPED_TICKS);
        local += com.mrgregles.bsp_core.api.BspTransfer.heldTotems(id);
        var owedHere = com.mrgregles.bsp_core.admin.SeasonData.get(player.server).pendingItems.get(id);
        if (owedHere != null) {
            local += (int) owedHere.stream().filter(com.mrgregles.bsp_core.totem.TotemInventories::isTotem).count();
        }
        int board = com.mrgregles.bsp_core.score.ScoreService.board().stream().filter(e -> e.id().equals(id)).mapToInt(e -> e.totems()).findFirst().orElse(0);
        return Math.max(local, board);
    }

    /** Ticks the network's count of a player's waiting totems is trusted after it arrives. */
    private static final long OWED_TICKS = 1200;

    /** The totems waiting for each player in the network, as the last pour claim heard, and when (game time). */
    private record Owed(int totems, long at) {}

    private final Map<java.util.UUID, Owed> owedSeen = new java.util.HashMap<>();

    private int owedInNetwork(java.util.UUID player) {
        Owed o = owedSeen.get(player);
        return o == null || level == null || level.getGameTime() - o.at() > OWED_TICKS ? 0 : o.totems();
    }

    private net.minecraft.core.GlobalPos here() {
        return net.minecraft.core.GlobalPos.of(level.dimension(), worldPosition);
    }

    /** Whether {@code player} could start the pour now, and at what cost. Server side. */
    public PourPlan plan(net.minecraft.server.level.ServerPlayer player) {
        if (!isFormed()) {
            return new PourPlan(PourCheck.NOT_FORMED, 0, 0);
        }
        if (pour > 0) {
            return new PourPlan(PourCheck.BUSY, 0, 0);
        }
        if (mouldCount() < mouldStacks.length) {
            return new PourPlan(PourCheck.MOULD, 0, 0);
        }
        if (!metalComplete()) {
            return new PourPlan(PourCheck.METAL, 0, 0);
        }
        var ledger = TotemForgeLedger.get(player.server);
        var claim = ledger.pourOf(player.getUUID());
        if (claim.isPresent() && !holdsPour(player.server, claim.get(), player.getUUID())) {
            ledger.release(player.getUUID(), claim.get()); // a claim left behind (a world edit, a forge gone without breaking): free it
            claim = java.util.Optional.empty();
        }
        if (claim.isPresent()) {
            return new PourPlan(PourCheck.ALREADY_POURING, 0, 0);
        }
        int owned = ownedTotems(player) + owedInNetwork(player.getUUID()), coins = owned * BSPConfig.TFORGE_COINS_PER_TOTEM.get();
        // any Shatter Coins pay, by value, with change (owner, audit D5)
        if (owned > 0 && com.mrgregles.bsp_core.coin.CoinWallet.totalValue(player) < coins * pourCoin().value()) {
            return new PourPlan(PourCheck.COINS, owned, coins);
        }
        return new PourPlan(owned > 0 ? PourCheck.PAID : PourCheck.FREE, owned, owned > 0 ? coins : 0);
    }

    /**
     * Whether the claimed forge at {@code at} still holds a pour by {@code player}. A claim on this server whose forge is
     * loaded and is not pouring or setting for them is stale; one in an unloaded chunk is trusted (loading it from a
     * screen's twice-a-second check would cost too much), and an operator can clear it with {@code /bsp totemforge clear}.
     */
    static boolean holdsPour(net.minecraft.server.MinecraftServer server, net.minecraft.core.GlobalPos at, java.util.UUID player) {
        ServerLevel lvl = server.getLevel(at.dimension());
        if (lvl == null) {
            return false;
        }
        if (!lvl.isLoaded(at.pos())) {
            return true;
        }
        return lvl.getBlockEntity(at.pos()) instanceof TotemForgeBlockEntity f && player.equals(f.pourer) && f.pour > 0;
    }

    /** Ticks the POUR key waits for the network before it gives up. */
    public static final int NETWORK_WAIT_TICKS = 100;
    /** The player whose POUR is waiting for the network's answer, and the game time it gives up. */
    @Nullable
    private java.util.UUID waiting;
    private long waitingUntil;

    /**
     * The POUR key. Checks here, then with network storage on claims the player's one pour in the shared state
     * table first (one key, atomic, so two servers can never both start one), then starts. The network has
     * {@link #NETWORK_WAIT_TICKS} to answer; after that the pour is called off, and a late answer frees what it claimed.
     */
    public void requestPour(net.minecraft.server.level.ServerPlayer player) {
        if (waiting != null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.bsp_core.totem_forge.pour.checking"), true);
            return;
        }
        PourPlan plan = plan(player);
        if (!plan.ok()) {
            tell(player, plan);
            return;
        }
        PourPlan shown = plan; // what the player saw when they pressed POUR
        var network = TotemForgeLedger.network();
        if (!network.enabled() || level == null) {
            startPour(player);
            return;
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.bsp_core.totem_forge.pour.checking"), true);
        java.util.UUID id = player.getUUID();
        String key = TotemForgeLedger.networkKey(id), mine = TotemForgeLedger.networkValue(here());
        waiting = id;
        waitingUntil = level.getGameTime() + NETWORK_WAIT_TICKS;
        network.claim(key, mine, id, (holder, owed) -> {
            boolean stillWaiting = id.equals(waiting) && !isRemoved();
            if (stillWaiting) {
                waiting = null;
            }
            if (!holder.equals(mine)) {
                if (stillWaiting) {
                    tell(player, new PourPlan(holder.isEmpty() ? PourCheck.NO_ANSWER : PourCheck.ALREADY_POURING, 0, 0));
                }
                return;
            }
            owedSeen.put(id, new Owed(owed, level == null ? 0 : level.getGameTime()));
            // the network row is this forge's now: start, or give it back if the pour cannot go ahead
            boolean here = stillWaiting && !player.hasDisconnected() && player.level() == level
                    && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 1.5, worldPosition.getZ() + 0.5) <= 64;
            PourPlan now = here ? plan(player) : shown;
            // totems on their way to the player can make the pour dearer than the screen said: never charge for what they did not see
            boolean dearer = now.coins() > shown.coins();
            if (here && dearer) {
                tell(player, new PourPlan(PourCheck.PRICE_CHANGED, now.owned(), now.coins()));
            }
            if (!here || dearer || !startPour(player).ok()) {
                if (TotemForgeLedger.get(player.server).pourOf(id).isEmpty()) {
                    network.release(key, mine);
                }
            }
        });
    }

    /** Called off when the network has not answered in time. */
    private void checkWaiting(ServerLevel level) {
        if (waiting != null && level.getGameTime() >= waitingUntil) {
            var p = level.getServer().getPlayerList().getPlayer(waiting);
            waiting = null;
            if (p != null) {
                tell(p, new PourPlan(PourCheck.NO_ANSWER, 0, 0));
            }
        }
    }

    /** Whether a POUR is waiting for the network's answer. */
    public boolean waitingForNetwork() {
        return waiting != null;
    }

    /** Starts the pour after checking once more (the forge or the player may have changed while the network answered). */
    public PourPlan startPour(net.minecraft.server.level.ServerPlayer player) {
        PourPlan plan = plan(player);
        if (plan.ok() && plan.check() == PourCheck.PAID && plan.coins() > 0 && !com.mrgregles.bsp_core.coin.CoinWallet.pay(player, plan.coins() * pourCoin().value())) {
            plan = new PourPlan(PourCheck.COINS, plan.owned(), plan.coins());
        }
        if (!plan.ok()) {
            tell(player, plan);
            return plan;
        }
        pourer = player.getUUID();
        pourerName = player.getGameProfile().getName();
        paid = plan.check() == PourCheck.PAID;
        setMillis = (paid ? BSPConfig.TFORGE_PAID_SET_HOURS.get() : BSPConfig.TFORGE_FREE_SET_HOURS.get()) * 3_600_000L;
        pour = 1e-4f; // started; the tick runs it to 1
        for (int i = 0; i < CORE; i++) { // the pour counts against every mould piece now; the core is used up (it becomes the orb)
            mouldStacks[i].setDamageValue(mouldStacks[i].getDamageValue() + 1);
        }
        TotemForgeLedger.get(player.server).claim(pourer, here());
        tell(player, plan);
        level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY_LAVA, net.minecraft.sounds.SoundSource.BLOCKS, 1f, 0.7f);
        setChanged();
        sendData();
        return plan;
    }

    private void tell(net.minecraft.server.level.ServerPlayer player, PourPlan plan) {
        String coin = new ItemStack(pourCoin().coin()).getHoverName().getString();
        long days = Math.round((plan.check() == PourCheck.PAID ? BSPConfig.TFORGE_PAID_SET_HOURS.get() : BSPConfig.TFORGE_FREE_SET_HOURS.get()) / 24.0);
        net.minecraft.network.chat.Component msg = switch (plan.check()) {
            case FREE -> net.minecraft.network.chat.Component.translatable("message.bsp_core.totem_forge.pour.free", days);
            case PAID -> net.minecraft.network.chat.Component.translatable("message.bsp_core.totem_forge.pour.paid", plan.coins(), coin, days);
            case NOT_FORMED -> net.minecraft.network.chat.Component.translatable("need.bsp_core.structure");
            case MOULD -> net.minecraft.network.chat.Component.translatable("message.bsp_core.totem_forge.pour.mould", nextMouldPiece().getHoverName());
            case METAL -> net.minecraft.network.chat.Component.translatable("message.bsp_core.totem_forge.pour.metal");
            case BUSY -> net.minecraft.network.chat.Component.translatable("message.bsp_core.totem_forge.pour.busy");
            case ALREADY_POURING -> net.minecraft.network.chat.Component.translatable("message.bsp_core.totem_forge.pour.already");
            case COINS -> net.minecraft.network.chat.Component.translatable("message.bsp_core.totem_forge.pour.coins", plan.owned(), plan.coins(), coin);
            case NO_ANSWER -> net.minecraft.network.chat.Component.translatable("message.bsp_core.totem_forge.pour.no_answer");
            case UNKNOWN -> net.minecraft.network.chat.Component.translatable("message.bsp_core.totem_forge.pour.checking");
            case PRICE_CHANGED -> net.minecraft.network.chat.Component.translatable("message.bsp_core.totem_forge.pour.changed", plan.owned(), plan.coins(), coin);
        };
        player.displayClientMessage(msg.copy().withStyle(plan.ok() ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.YELLOW), false);
    }

    @Nullable
    public java.util.UUID pourer() {
        return pourer;
    }

    public boolean paid() {
        return paid;
    }

    public String pourerName() {
        return pourerName;
    }

    /** Real ms the setting has left, by this side's clock (the screen gets the server's through its menu); 0 once set or before the pour has ended. */
    public long setMillisLeft() {
        return pour >= 1 && set < 1 ? Math.max(0, setStart + setMillis - System.currentTimeMillis()) : 0;
    }

    /** The mould piece in place {@code i} (renderer order), or empty. */
    public ItemStack mouldAt(int i) {
        return mouldStacks[i];
    }

    /** The item the empty place {@code i} takes. */
    public static ItemStack pieceForPlace(int i) {
        return new ItemStack(pieceFor(i));
    }

    public long setMillis() {
        return setMillis;
    }

    /**
     * The controller is going: the pourer's claim is freed and the pieces drop with the pour counted (mouldContents).
     * Before the totem has set the pour is lost. A set totem is never destroyed: it drops, owned by the pourer.
     */
    public void onBroken() {
        if (level instanceof ServerLevel sl) {
            endSteal(sl, "message.bsp_core.totem_forge.steal.broken");
        }
        if (pourer != null && level != null && level.getServer() != null) {
            TotemForgeLedger.get(level.getServer()).release(pourer, here());
            if (isSet()) {
                net.minecraft.world.Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY() + 1, worldPosition.getZ(), forgedTotem(pourer, pourerName));
                com.mrgregles.bsp_core.score.ScoreService.markDirty();
            }
        }
    }

    // ------------------------------------------------------------------ setting, taking out, stealing

    public boolean isSet() {
        return pour >= 1 && set >= 1;
    }

    /**
     * What the forge is doing, for its screen (key {@code gui.bsp_core.totem_forge.state.<this>}): a pour, a setting or a
     * set totem shows as such even while the structure is broken, since the totem still comes out of it.
     */
    public String screenState() {
        if (isSet()) {
            return "set";
        }
        if (pour > 0) {
            return pour < 1 ? "pouring" : "setting";
        }
        if (!isFormed()) {
            return "unformed";
        }
        return metalComplete() && mouldCount() == mouldStacks.length ? "ready" : "melting";
    }

    /** The setting runs on real time from the end of the pour, so it goes on while the chunk is unloaded or the server is down. */
    private void updateSet(ServerLevel level) {
        set = setMillis <= 0 ? 1 : Math.min(1f, (System.currentTimeMillis() - setStart) / (float) setMillis);
        if (set >= 1 || set - sentSet >= 0.002f) { // a client sees the cast cool in small steps, not every second
            sentSet = set;
            showChanged = true;
            setChanged(); // set is worked out again from setStart after a restart, so only these steps need saving
        }
        if (set >= 1 && pourer != null && TotemForgeLedger.get(level.getServer()).markTold(pourer)) {
            com.mrgregles.bsp_core.storage.NetworkStorage.tell(level.getServer(), pourer, setMessage(worldPosition));
        }
    }

    /** Ends the setting now: for GameTests and for an admin trying the forge. */
    public void finishSetting() {
        if (pour > 0) {
            pour = 1;
            pourEnded(System.currentTimeMillis() - setMillis);
            if (level instanceof ServerLevel sl) {
                updateSet(sl);
            }
        }
    }

    /** The pour is in the mould: the tanks are empty and the setting runs from {@code start}; the ledger learns when it will be done. */
    private void pourEnded(long start) {
        stored.clear();
        metal = 0;
        setStart = start;
        if (pourer != null && level != null && level.getServer() != null) {
            TotemForgeLedger.get(level.getServer()).readyAt(pourer, start + setMillis);
        }
    }

    /** "Your forged totem has set at x y z": told once, by the forge or by the ledger, whichever is first. */
    public static net.minecraft.network.chat.Component setMessage(BlockPos at) {
        return net.minecraft.network.chat.Component.translatable("message.bsp_core.totem_forge.set", at.getX(), at.getY(), at.getZ()).withStyle(net.minecraft.ChatFormatting.GOLD);
    }

    /** The pourer's empty-handed right-click (or TAKE TOTEM) on a set forge: the totem comes out at once. Anyone else's (or STEAL) starts or shows a steal. */
    public void takeOrSteal(net.minecraft.server.level.ServerPlayer player) {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        if (player.getUUID().equals(pourer)) {
            endSteal(sl, "message.bsp_core.totem_forge.steal.taken_back");
            hand(player);
            return;
        }
        if (steal != null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(player.getUUID().equals(steal.thief()) ? "message.bsp_core.totem_forge.steal.left"
                    : "message.bsp_core.totem_forge.steal.busy", steal.thiefName(), (steal.ticksLeft() + 19) / 20).withStyle(net.minecraft.ChatFormatting.YELLOW), true);
            return;
        }
        // the totem steal's rules (owner, 2026-10-27, audit D2): no creative or spectator thief, no teammate unless the config allows it, start within the radius
        String refused = player.isCreative() || player.isSpectator() ? "message.bsp_core.totem_forge.steal.creative"
                : pourer != null && !BSPConfig.getOr(BSPConfig.TEAM_TEAMMATES_STEAL, false) && com.mrgregles.bsp_core.team.Teams.enabled()
                && com.mrgregles.bsp_core.api.BspTeams.sameTeam(sl.getServer(), pourer, player.getUUID()) ? "message.bsp_core.steal.teammate"
                : !nearForge(player) ? "message.bsp_core.totem_forge.steal.too_far" : null;
        if (refused != null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(refused, BSPConfig.STEAL_RADIUS.get()).withStyle(net.minecraft.ChatFormatting.YELLOW), true);
            return;
        }
        int seconds = BSPConfig.TFORGE_STEAL_SECONDS.get();
        String name = player.getGameProfile().getName();
        steal = new com.mrgregles.bsp_core.totem.StealState(player.getUUID(), name, seconds * 20, seconds * 20, BSPConfig.STEAL_GRACE_SECONDS.get() * 20, false);
        thiefPlayer = player;
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.bsp_core.totem_forge.steal.started", seconds,
                BSPConfig.STEAL_RADIUS.get()).withStyle(net.minecraft.ChatFormatting.RED), false);
        // the network's raid rules and Discord's outcome message follow these events, as for a totem steal
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.mrgregles.bsp_core.api.event.TotemEvent.StealStarted(sl.getServer(), here(), pourer, player.getUUID(), name, seconds));
        com.mrgregles.bsp_core.BSPCore.LOGGER.info("{} started stealing {}'s forged totem at {}", name, pourerName, worldPosition);
        if (pourer != null) {
            com.mrgregles.bsp_core.storage.NetworkStorage.tell(sl.getServer(), pourer, net.minecraft.network.chat.Component.translatable("message.bsp_core.totem_forge.steal.warning",
                    name, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()).withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD));
            // a set totem has no upgrades, so no Herald: the pourer gets the direct Discord message on every forge steal (audit D1)
            com.mrgregles.bsp_core.discord.DiscordAlerts.warned(sl, worldPosition, pourer, 1, name, seconds);
        }
        sendStatus(sl, com.mrgregles.bsp_core.network.StealStatusPacket.OUTCOME_ACTIVE);
    }

    /** Within the steal radius of the hollow's floor. */
    private boolean nearForge(net.minecraft.server.level.ServerPlayer p) {
        double r = BSPConfig.STEAL_RADIUS.get();
        return p.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 1.5, worldPosition.getZ() + 0.5) <= r * r;
    }

    @Nullable
    private net.minecraft.server.level.ServerPlayer thief(ServerLevel level) {
        if (steal == null) {
            return null;
        }
        var online = level.getServer().getPlayerList().getPlayer(steal.thief());
        return online != null ? online : thiefPlayer;
    }

    /**
     * Runs a steal on by {@code ticks}, as a placed totem's: the timer runs only while the thief is within the steal
     * radius; outside it for longer than the steal's grace, or dead, or gone, and the steal fails. GameTests call it
     * with many ticks at once.
     */
    public void stealTick(ServerLevel level, int ticks) {
        if (steal == null) {
            return;
        }
        var p = thief(level);
        if (p == null || p.isRemoved() || p.hasDisconnected() || p.isDeadOrDying() || p.level() != level) {
            endSteal(level, "message.bsp_core.totem_forge.steal.failed");
            return;
        }
        boolean outside = !nearForge(p);
        int ticksLeft = steal.ticksLeft(), graceLeft = steal.graceLeft();
        if (outside) {
            graceLeft -= ticks;
            if (graceLeft <= 0) {
                endSteal(level, "message.bsp_core.totem_forge.steal.failed");
                return;
            }
        } else {
            graceLeft = BSPConfig.STEAL_GRACE_SECONDS.get() * 20;
            ticksLeft -= ticks;
        }
        boolean changed = outside != steal.outside();
        steal = steal.withTick(ticksLeft, graceLeft, outside);
        if (ticksLeft <= 0) {
            completeSteal(level, p);
        } else if (changed || ticks >= 20 || level.getGameTime() % 20 == 0) {
            sendStatus(level, com.mrgregles.bsp_core.network.StealStatusPacket.OUTCOME_ACTIVE); // the steal bar on both sides' screens
        }
    }

    private void completeSteal(ServerLevel level, net.minecraft.server.level.ServerPlayer p) {
        var done = steal;
        var stolenFrom = pourer;
        String from = pourerName;
        sendStatus(level, com.mrgregles.bsp_core.network.StealStatusPacket.OUTCOME_SUCCESS);
        steal = null;
        thiefPlayer = null;
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.mrgregles.bsp_core.api.event.TotemEvent.StealEnded(level.getServer(), here(), stolenFrom, done.thief(), done.thiefName(), true, ""));
        hand(p);
        com.mrgregles.bsp_core.totem.ShatterTotemBlockEntity.shield(p);
        if (stolenFrom != null) {
            com.mrgregles.bsp_core.storage.NetworkStorage.tell(level.getServer(), stolenFrom, net.minecraft.network.chat.Component.translatable("message.bsp_core.totem_forge.steal.lost",
                    done.thiefName(), worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()).withStyle(net.minecraft.ChatFormatting.RED));
        }
        com.mrgregles.bsp_core.BSPCore.LOGGER.info("{} stole {}'s forged totem at {}", done.thiefName(), from, worldPosition);
    }

    public boolean stealing() {
        return steal != null;
    }

    /** The steal in progress, if any. */
    public java.util.Optional<com.mrgregles.bsp_core.totem.StealState> getSteal() {
        return java.util.Optional.ofNullable(steal);
    }

    /** Ends a steal that failed: the thief and the pourer are told, both steal bars close, and the event lets the network and Discord know. */
    private void endSteal(ServerLevel level, String key) {
        if (steal == null) {
            return;
        }
        var done = steal;
        var p = thief(level);
        sendStatus(level, com.mrgregles.bsp_core.network.StealStatusPacket.OUTCOME_FAILED);
        steal = null;
        thiefPlayer = null;
        if (p != null) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable(key, pourerName).withStyle(net.minecraft.ChatFormatting.YELLOW), false);
        }
        if (pourer != null && !key.endsWith(".taken_back")) {
            com.mrgregles.bsp_core.storage.NetworkStorage.tell(level.getServer(), pourer, net.minecraft.network.chat.Component.translatable("message.bsp_core.steal.defended", done.thiefName())
                    .withStyle(net.minecraft.ChatFormatting.GREEN));
        }
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.mrgregles.bsp_core.api.event.TotemEvent.StealEnded(level.getServer(), here(), pourer, done.thief(), done.thiefName(), false, key));
        com.mrgregles.bsp_core.BSPCore.LOGGER.info("Steal of {}'s forged totem at {} by {} ended: {}", pourerName, worldPosition, done.thiefName(), key);
    }

    /** The steal bar for the thief and the pourer, wherever on this server they are. */
    private void sendStatus(ServerLevel level, byte outcome) {
        if (steal == null) {
            return;
        }
        var payload = outcome == com.mrgregles.bsp_core.network.StealStatusPacket.OUTCOME_ACTIVE ? steal : null;
        var p = thief(level);
        if (p != null && p.connection != null) {
            com.mrgregles.bsp_core.network.BSPNetwork.sendTo(p, new com.mrgregles.bsp_core.network.StealStatusPacket(worldPosition, pourerName, payload,
                    com.mrgregles.bsp_core.network.StealStatusPacket.ROLE_THIEF, outcome));
        }
        var owner = pourer == null ? null : level.getServer().getPlayerList().getPlayer(pourer);
        if (owner != null) {
            com.mrgregles.bsp_core.network.BSPNetwork.sendTo(owner, new com.mrgregles.bsp_core.network.StealStatusPacket(worldPosition, pourerName, payload,
                    com.mrgregles.bsp_core.network.StealStatusPacket.ROLE_OWNER, outcome));
        }
    }

    /** A new Forged Shatter Totem: tier I, no upgrades, owned by {@code owner}. */
    public static ItemStack forgedTotem(java.util.UUID owner, String name) {
        ItemStack totem = new ItemStack(ModItems.SHATTER_TOTEM.get());
        new com.mrgregles.bsp_core.totem.TotemOwner(owner, name).applyTo(totem);
        com.mrgregles.bsp_core.totem.TotemIdentity.ensure(totem);
        com.mrgregles.bsp_core.totem.TotemIdentity.setForged(totem);
        return totem;
    }

    /** Hands the set totem to {@code player}, who now owns it, and readies the forge for the next pour. */
    private void hand(net.minecraft.server.level.ServerPlayer player) {
        com.mrgregles.bsp_core.api.BspTotems.giveBack(player, forgedTotem(player.getUUID(), player.getGameProfile().getName()));
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.bsp_core.totem_forge.took_totem").withStyle(net.minecraft.ChatFormatting.GOLD), false);
        com.mrgregles.bsp_core.score.ScoreService.markDirty();
        if (level != null) {
            level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.TOTEM_USE, net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1f);
        }
        clearPour();
    }

    /** Back to an empty forge: the pourer's claim freed, the core gone, a piece on its last pour cracked, the rest left in the hollow. */
    private void clearPour() {
        if (pourer != null && level != null && level.getServer() != null) {
            TotemForgeLedger.get(level.getServer()).release(pourer, here());
        }
        mouldStacks[CORE] = ItemStack.EMPTY;
        for (int i = 0; i < CORE; i++) {
            if (!mouldStacks[i].isEmpty() && mouldStacks[i].getDamageValue() >= mouldStacks[i].getMaxDamage()) {
                mouldStacks[i] = ItemStack.EMPTY;
                if (level != null) {
                    level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.DECORATED_POT_SHATTER, net.minecraft.sounds.SoundSource.BLOCKS, 0.7f, 1f);
                }
            }
        }
        pour = 0;
        set = 0;
        sentSet = 0;
        pourer = null;
        pourerName = "";
        paid = false;
        setMillis = 0;
        setStart = 0;
        steal = null;
        thiefPlayer = null;
        refreshMould();
    }

    // ------------------------------------------------------------------ the mould

    /** The item the next empty place of the mould takes, or empty when the mould is complete. */
    public ItemStack nextMouldPiece() {
        for (int i = 0; i < mouldStacks.length; i++) {
            if (mouldStacks[i].isEmpty()) {
                return new ItemStack(pieceFor(i));
            }
        }
        return ItemStack.EMPTY;
    }

    private static Item pieceFor(int place) {
        if (place == CORE) {
            return ModItems.TOTEM_CORE.get();
        }
        for (TotemMouldItem.Piece p : TotemMouldItem.Piece.values()) {
            for (int at : p.places) {
                if (at == place) {
                    return ModItems.MOULDS.get(p).get();
                }
            }
        }
        return Items.AIR;
    }

    /** What happened when a player offered the forge a mould piece. */
    public enum Placed { PLACED, WRONG_PIECE, COMPLETE, POURED, NOT_FORMED, CRACKED }

    /** Puts one of {@code held} into the next place of the mould if it is the piece that place takes. */
    public Placed placeMould(ItemStack held) {
        if (!isFormed()) {
            return Placed.NOT_FORMED;
        }
        if (pour > 0) {
            return Placed.POURED;
        }
        for (int i = 0; i < mouldStacks.length; i++) {
            if (mouldStacks[i].isEmpty()) {
                if (!held.is(pieceFor(i))) {
                    return Placed.WRONG_PIECE;
                }
                if (held.getItem() instanceof TotemMouldItem && TotemMouldItem.poursLeft(held) <= 0) {
                    return Placed.CRACKED; // only a command can make one, but it must never go in
                }
                mouldStacks[i] = held.split(1);
                refreshMould();
                return Placed.PLACED;
            }
        }
        return Placed.COMPLETE;
    }

    /**
     * A right-click on the forge (its controller or a bottom-layer port). On a set forge an empty hand takes the
     * totem out or starts a steal. Otherwise a mould piece in hand goes in, and sneaking with an empty hand takes
     * the last piece out. Returns false when it was none of these, so the click opens the screen. Server side.
     */
    public boolean use(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        // a set forge: an empty hand takes the totem or starts a steal; anything else opens the screen, whose key does it (owner, audit D3)
        if (isSet() && held.isEmpty() && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            takeOrSteal(sp);
            return true;
        }
        boolean piece = held.getItem() instanceof TotemMouldItem || held.is(ModItems.TOTEM_CORE.get());
        if (piece) {
            String name = held.getHoverName().getString();
            Placed result = placeMould(held);
            net.minecraft.network.chat.Component msg = switch (result) {
                case PLACED -> net.minecraft.network.chat.Component.translatable("message.bsp_core.totem_forge.placed", name, mouldCount());
                case WRONG_PIECE -> net.minecraft.network.chat.Component.translatable("message.bsp_core.totem_forge.next", nextMouldPiece().getHoverName());
                case COMPLETE -> net.minecraft.network.chat.Component.translatable("message.bsp_core.totem_forge.complete");
                case POURED -> net.minecraft.network.chat.Component.translatable("message.bsp_core.totem_forge.poured");
                case NOT_FORMED -> net.minecraft.network.chat.Component.translatable("need.bsp_core.structure");
                case CRACKED -> net.minecraft.network.chat.Component.translatable("message.bsp_core.totem_forge.cracked", name);
            };
            player.displayClientMessage(msg.copy().withStyle(result == Placed.PLACED ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.YELLOW), true);
            if (result == Placed.PLACED && level != null) {
                level.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.STONE_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 1f, 0.8f + 0.05f * mouldCount());
            }
            return true;
        }
        if (player.isShiftKeyDown() && held.isEmpty() && isFormed() && mouldCount() > 0) {
            if (pour > 0) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.bsp_core.totem_forge.poured").withStyle(net.minecraft.ChatFormatting.YELLOW), true);
                return true;
            }
            ItemStack out = takeMould();
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.bsp_core.totem_forge.took", out.getHoverName()), true);
            if (!player.getInventory().add(out)) {
                player.drop(out, false);
            }
            return true;
        }
        return false;
    }

    /** Takes the last piece put in back out, before any metal is poured. Empty if there is none or the pour has begun. */
    public ItemStack takeMould() {
        if (pour > 0) {
            return ItemStack.EMPTY;
        }
        for (int i = mouldStacks.length - 1; i >= 0; i--) {
            if (!mouldStacks[i].isEmpty()) {
                ItemStack out = mouldStacks[i];
                mouldStacks[i] = ItemStack.EMPTY;
                refreshMould();
                return out;
            }
        }
        return ItemStack.EMPTY;
    }

    /** The pieces in the hollow, dropped where the controller stood when it is broken. Once poured, the core is used up and a piece on its last pour has cracked. */
    public List<ItemStack> mouldContents() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < mouldStacks.length; i++) {
            ItemStack s = mouldStacks[i];
            if (!s.isEmpty() && !(pour > 0 && (i == CORE || s.getDamageValue() >= s.getMaxDamage()))) {
                out.add(s);
            }
        }
        return out;
    }

    public int mouldCount() {
        return Integer.bitCount(mould);
    }

    private void refreshMould() {
        mould = 0;
        for (int i = 0; i < mouldStacks.length; i++) {
            if (!mouldStacks[i].isEmpty()) {
                mould |= 1 << i;
            }
        }
        setChanged();
        sendData();
    }

    // ------------------------------------------------------------------ saving

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        CompoundTag metals = new CompoundTag();
        stored.forEach(metals::putInt);
        tag.put("Metals", metals);
        ListTag pieces = new ListTag();
        for (ItemStack s : mouldStacks) {
            pieces.add(s.save(new CompoundTag()));
        }
        tag.put("MouldItems", pieces);
        tag.putInt("Mould", mould);
        tag.putFloat("Metal", metal);
        tag.putFloat("Pour", pour);
        tag.putFloat("Set", set);
        if (pourer != null) {
            tag.putUUID("Pourer", pourer);
            tag.putString("PourerName", pourerName);
        }
        tag.putBoolean("Paid", paid);
        tag.putLong("SetMillis", setMillis);
        tag.putLong("SetStart", setStart);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stored.clear();
        CompoundTag metals = tag.getCompound("Metals");
        for (String k : metals.getAllKeys()) {
            stored.put(k, metals.getInt(k));
        }
        ListTag pieces = tag.getList("MouldItems", Tag.TAG_COMPOUND);
        for (int i = 0; i < mouldStacks.length; i++) {
            mouldStacks[i] = i < pieces.size() ? ItemStack.of(pieces.getCompound(i)) : ItemStack.EMPTY;
        }
        mould = tag.getInt("Mould");
        metal = tag.getFloat("Metal");
        pour = tag.getFloat("Pour");
        set = tag.getFloat("Set");
        pourer = tag.hasUUID("Pourer") ? tag.getUUID("Pourer") : null;
        pourerName = tag.getString("PourerName");
        paid = tag.getBoolean("Paid");
        setMillis = tag.getLong("SetMillis");
        setStart = tag.getLong("SetStart");
    }

    // ------------------------------------------------------------------ the structure

    @Override
    protected boolean hidesParts() {
        return true;
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(3, 4, 3);
    }

    @Override
    protected String[][] pattern() {
        return PATTERN;
    }

    @Nullable
    @Override
    protected Block blockFor(char c) {
        return switch (c) {
            case 'I' -> ModBlocks.METAL_INTAKE.get();
            case 'H' -> ModBlocks.FORGE_HEARTH.get();
            case 'T' -> ModBlocks.MELT_TANK.get();
            case 'P' -> ModBlocks.POUR_HEAD.get();
            case 'C' -> ModBlocks.FORGE_CAP.get();
            case '.' -> Blocks.AIR; // the hollow: anything placed in it breaks the structure
            default -> null;
        };
    }

    @Override
    protected boolean isItemPort(Block block) {
        return block == ModBlocks.METAL_INTAKE.get();
    }

    @Override
    public String itemPortKey(int i) {
        return "block.bsp_core.metal_intake";
    }

    @Override
    public String fluidPortKey() {
        return "block.bsp_core.forge_hearth";
    }

    // the hearths take the lava they burn and, with an RF upgrade, power
    @Override
    protected boolean portTakesFluid(BlockPos port) {
        return level != null && level.getBlockState(port).is(ModBlocks.FORGE_HEARTH.get());
    }

    @Override
    protected boolean portTakesEnergy(BlockPos port) {
        return portTakesFluid(port);
    }

    @Override
    protected int tankCapacity() {
        return BSPConfig.TFORGE_LAVA_TANK.get();
    }

    @Override
    protected Fluid fluid() {
        return Fluids.LAVA;
    }

    @Override
    public int fluidColor() {
        return 0xFFFF7A1A;
    }

    @Override
    protected void refill() {
        fillFrom(LAVA, new ItemStack(Items.LAVA_BUCKET), bucket(), 1000);
    }

    @Override
    public List<Need> missing(int fluidMb, boolean burning) {
        List<Need> out = new ArrayList<>();
        if (!isFormed()) {
            out.add(need(ModBlocks.FORGE_HEARTH.get(), "need.bsp_core.structure"));
        }
        if (!metalComplete() && items.getStackInSlot(INPUT).isEmpty()) {
            out.add(need(ModItems.TETRIUM_INGOT.get(), "need.bsp_core.totem_forge_metal"));
        }
        if (fluidMb < BSPConfig.TFORGE_LAVA_PER_INGOT.get()) {
            out.add(need(Items.LAVA_BUCKET, "need.bsp_core.lava"));
        }
        ItemStack next = nextMouldPiece();
        if (!next.isEmpty()) {
            out.add(new Need(next, net.minecraft.network.chat.Component.translatable("need.bsp_core.totem_forge_mould", next.getHoverName())));
        }
        return out;
    }

    @Override
    public ItemStack slotIcon(int slot) {
        if (isRfSlot(slot)) {
            return new ItemStack(ModItems.RF_UPGRADES.get(0).get());
        }
        return slot == LAVA ? new ItemStack(Items.LAVA_BUCKET) : new ItemStack(ModItems.TETRIUM_INGOT.get());
    }

    @Override
    public net.minecraft.network.chat.Component slotHint(int slot) {
        if (isRfSlot(slot)) {
            return net.minecraft.network.chat.Component.translatable("hint.bsp_core.rf");
        }
        return net.minecraft.network.chat.Component.translatable(slot == LAVA ? "hint.bsp_core.lava" : "hint.bsp_core.totem_forge_input");
    }

    @Override
    public String titleKey() {
        return "block.bsp_core.totem_forge";
    }
}
